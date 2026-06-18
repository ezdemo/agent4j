package site.sorghum.agent4j.bin.builtin;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Param;
import site.sorghum.agent4j.bin.workspace.SharedWorkspace;
import site.sorghum.agent4j.bin.workspace.WatchHandler;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Workspace Watch 工具 —— 阻塞等待工作区中指定模式匹配的键发生变更。
 * <p>
 * 订阅工作区事件总线，当匹配 {@code keyPattern} 的键发生 WRITE/UPDATE/DELETE 事件时，
 * 返回事件信息（key、eventType、value）。支持超时控制，超时后返回 TIMEOUT。
 * 该工具会阻塞调用线程，类似于 {@link CountDownLatch#await(long, TimeUnit)} 的等待机制。
 * </p>
 *
 * <h3>通配符规则：</h3>
 * <ul>
 *   <li><code>*</code> — 匹配单级路径（不含 /）</li>
 *   <li><code>**</code> — 匹配多级路径（含 /）</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <ul>
 *   <li>等待任意键变更：{@code workspace_watch(keyPattern="**", timeout=60)}</li>
 *   <li>等待特定前缀的键变更：{@code workspace_watch(keyPattern="user/**", timeout=30)}</li>
 * </ul>
 *
 * @author Sorghum
 */
@Component
public class WorkspaceWatchTool extends AbsToolProvider implements SolonToTools {

    @Inject
    private SharedWorkspace workspace;

    /**
     * 无参构造器 —— Solon DI 使用。
     */
    public WorkspaceWatchTool() {
    }

    /**
     * 带参构造器 —— SubAgent 手动创建时使用。
     *
     * @param workspace SharedWorkspace 实例
     */
    public WorkspaceWatchTool(SharedWorkspace workspace) {
        this.workspace = workspace;
    }

    @ToolMapping(name = "workspace_watch", description = """
                阻塞等待工作区指定模式匹配的键发生变更（WRITE/UPDATE/DELETE）。
                订阅工作区事件总线，当匹配 keyPattern 的键发生变更时返回事件信息。
                支持通配符模式：* 匹配单级路径（不含 /），** 匹配多级路径（含 /）。
                参数: keyPattern(必填, 通配符匹配模式), timeout(可选, 秒, 默认30, 最大300)。
                超时返回 TIMEOUT 状态。
                """)
    public String workspaceWatch(@Param(name = "keyPattern", description = "Wildcard pattern to match workspace keys. Use * for single-level matching (no slash), ** for multi-level matching.") String keyPattern,
                                 @Param(name = "timeout", description = "Maximum time in seconds to wait for a matching event. Default: 30, Max: 300.", required = false) Integer timeout,
                                 ToolContext ctx) {
        // 1. 获取必填参数 keyPattern
        if (keyPattern == null || keyPattern.isBlank()) {
            return "PARAM_MISSING: Missing required parameter 'keyPattern'";
        }

        // 2. 获取可选参数 timeout，默认 30 秒，限制最大 300 秒
        int timeoutValue = (timeout != null) ? timeout : 30;
        if (timeoutValue < 1) {
            timeoutValue = 1;
        } else if (timeoutValue > 300) {
            timeoutValue = 300;
        }

        // 3. 获取事件总线
        var eventBus = workspace.getEventBus();

        // 4. 创建同步原语
        CountDownLatch latch = new CountDownLatch(1);

        // 保存 subscriptionId，供 handler 中取消订阅使用（handler 执行时 subscriptionId 已赋值）
        AtomicReference<String> subscriptionIdRef = new AtomicReference<>(null);

        // 保存事件结果
        AtomicReference<Map<String, Object>> resultRef = new AtomicReference<>(null);

        // 5. 订阅事件
        WatchHandler handler = (key, type, value) -> {
            // 填充事件结果
            Map<String, Object> eventData = new LinkedHashMap<>();
            eventData.put("key", key);
            eventData.put("eventType", type.name());
            eventData.put("value", value != null ? value.toString() : null);
            resultRef.set(eventData);

            // 取消订阅（一次性消费）
            String subId = subscriptionIdRef.get();
            if (subId != null) {
                eventBus.unsubscribe(subId);
            }

            // 释放等待线程
            latch.countDown();
        };

        String subscriptionId = eventBus.subscribe(keyPattern, handler);
        subscriptionIdRef.set(subscriptionId);

        try {
            // 6. 等待事件或超时
            boolean eventReceived = latch.await(timeoutValue, TimeUnit.SECONDS);

            if (!eventReceived) {
                // 超时：取消订阅并返回 TIMEOUT
                eventBus.unsubscribe(subscriptionId);
                Map<String, Object> timeoutData = new LinkedHashMap<>();
                timeoutData.put("status", "TIMEOUT");
                timeoutData.put("keyPattern", keyPattern);
                timeoutData.put("timeout", timeoutValue);
                timeoutData.put("message", "No matching event occurred within " + timeoutValue + " seconds");
                return "TIMEOUT: No event matched pattern '" + keyPattern
                        + "' within " + timeoutValue + " seconds.";
            }

            // 7. 正常返回事件信息
            Map<String, Object> eventData = resultRef.get();
            if (eventData == null) {
                return "INTERNAL_ERROR: Event was signaled but no result data was captured";
            }

            String key = (String) eventData.get("key");
            String eventType = (String) eventData.get("eventType");
            String value = (String) eventData.get("value");

            StringBuilder sb = new StringBuilder();
            sb.append("Event detected for pattern '").append(keyPattern).append("':\n");
            sb.append("  Key:   ").append(key).append("\n");
            sb.append("  Type:  ").append(eventType).append("\n");
            sb.append("  Value: ").append(value != null ? value : "(null)");

            return sb.toString();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 被中断时清理订阅
            eventBus.unsubscribe(subscriptionId);
            return "INTERRUPTED: Watch was interrupted while waiting for events on pattern: " + keyPattern;
        }
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }

    @Override
    public String getSystemPrompt() {
        return """
                阻塞等待工作区指定模式匹配的键发生变更（WRITE/UPDATE/DELETE）。
                订阅工作区事件总线，当匹配 keyPattern 的键发生变更时返回事件信息。
                支持通配符模式：* 匹配单级路径（不含 /），** 匹配多级路径（含 /）。
                参数: keyPattern(必填, 通配符匹配模式), timeout(可选, 秒, 默认30, 最大300)。
                超时返回 TIMEOUT 状态。
                """;
    }
}