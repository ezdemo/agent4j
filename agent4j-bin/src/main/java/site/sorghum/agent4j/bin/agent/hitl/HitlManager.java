package site.sorghum.agent4j.bin.agent.hitl;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import site.sorghum.agent4j.bin.agent.model.HitlState;
import site.sorghum.agent4j.bin.agent.model.ToolCallEntry;
import site.sorghum.agent4j.tool.AgentOutput;
import site.sorghum.agent4j.tool.ChoiceOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * HITL (Human-In-The-Loop) 管理器 —— 负责 HITL 状态管理和用户交互提示。
 * <p>
 * 管理两种 HITL 模式：
 * <ul>
 *   <li><b>普通 HITL</b>（受 {@code hitlMode} 开关控制）：非只读工具执行前需用户审批</li>
 *   <li><b>沙箱越界 HITL</b>（强制审批）：文件访问越界时强制用户审批，不受 {@code hitlMode} 影响</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
public class HitlManager {

    // ==================== 普通 HITL 状态 ====================

    /** HITL 模式开关（true = 执行非只读工具前需用户审批） */
    @Setter
    @Getter
    private volatile boolean hitlMode;

    /** HITL 当前审批状态 */
    private volatile HitlState hitlState = HitlState.NONE;

    /** HITL 暂存的工具调用（ONode 数组） */
    private volatile ONode pendingHITLToolCalls;

    /** HITL 暂存的 assistant content */
    private volatile String pendingHITLContent;

    /** HITL 暂存的 reasoning_content */
    private volatile String pendingHITLReasoning;

    /** HITL 暂存的解析后工具调用列表 */
    @Getter
    private volatile List<ToolCallEntry> pendingHITTcList;

    // ==================== 沙箱越界 HITL（强制审批，不受 hitlMode 影响） ====================

    /** 沙箱越界 HITL 暂存：完整的工具调用 ONode（供重放） */
    private volatile ONode pendingSandboxHITToolCalls;

    /** 沙箱越界 HITL 暂存：assistant content */
    private volatile String pendingSandboxHITContent;

    /** 沙箱越界 HITL 暂存：reasoning_content */
    private volatile String pendingSandboxHITReasoning;

    /** 沙箱越界 HITL 暂存：越界详情（展示给用户） */
    private volatile String pendingSandboxHITDetails;

    public HitlManager() {
        this(false);
    }

    public HitlManager(boolean hitlDefault) {
        this.hitlMode = hitlDefault;
    }

    // ==================== 基础状态控制 ====================

    /** 切换 HITL 模式 */
    public synchronized void toggleHitl() {
        hitlMode = !hitlMode;
    }

    /** 批准待执行的工具调用 */
    public void approveHITL() {
        hitlState = HitlState.APPROVED;
    }

    /** 拒绝待执行的工具调用 */
    public void denyHITL() {
        hitlState = HitlState.DENIED;
    }

    /** 是否有待审批的工具调用 */
    public boolean hasPendingHITL() {
        return hitlState == HitlState.PENDING;
    }

    /** 获取当前审批状态 */
    public HitlState getState() {
        return hitlState;
    }

    /** 重置审批状态为 NONE */
    public void resetState() {
        hitlState = HitlState.NONE;
    }

    // ==================== 沙箱越界状态查询 ====================

    /** 是否有沙箱越界待审批 */
    public boolean hasSandboxPending() {
        return pendingSandboxHITToolCalls != null;
    }

    /** 暂存沙箱越界的 content/reasoning（AgentLoop 在 executeToolCalls 后调用） */
    public void storeSandboxContent(String content, String reasoningContent) {
        this.pendingSandboxHITContent = content;
        this.pendingSandboxHITReasoning = reasoningContent;
    }

    // ==================== 普通 HITL 拦截 ====================

    /**
     * 免审批的纯交互/控制流工具名称集合。
     * 这些工具不修改文件系统，仅用于对话控制和用户交互，
     * 在 HITL 模式下直接放行，无需用户审批。
     */
    private static final java.util.Set<String> HITL_EXEMPT_TOOLS = java.util.Set.of("finish", "ask_choice");

    /**
     * 判断工具调用列表是否全部为免审批工具。
     */
    private static boolean allToolsExempt(List<ToolCallEntry> tcList) {
        return !tcList.isEmpty() && tcList.stream()
                .allMatch(tc -> HITL_EXEMPT_TOOLS.contains(tc.name()));
    }

    /**
     * HITL 拦截：暂存工具调用，通过 {@code output} 向用户发送审批提示。
     * <p>如果本轮工具调用全部为免审批工具（finish/ask_choice），直接放行返回 {@code null}。</p>
     *
     * @param toolCalls       工具调用 ONode 数组
     * @param content         assistant 消息的 content
     * @param reasoningContent assistant 消息的 reasoning_content
     * @param output          输出接口（用于发送审批提示）
     * @return 审批提示文本，全部免审批时返回 {@code null}
     */
    public String interceptForHITL(ONode toolCalls, String content, String reasoningContent,
                                   AgentOutput output) {
        List<ToolCallEntry> tcList = parseToolCalls(toolCalls);

        // 免审批工具直接放行
        if (allToolsExempt(tcList)) {
            log.debug("[hitl] 工具全部免审批，直接放行: {}", tcList.stream().map(ToolCallEntry::name).toList());
            return null;
        }

        // 暂存状态
        this.pendingHITLToolCalls = toolCalls;
        this.pendingHITLContent = content;
        this.pendingHITLReasoning = reasoningContent;
        this.pendingHITTcList = tcList;
        this.hitlState = HitlState.PENDING;

        // 构建 title（工具名）和 description（工具参数）
        StringBuilder titleSb = new StringBuilder();
        StringBuilder descSb = new StringBuilder();
        for (int i = 0; i < tcList.size(); i++) {
            ToolCallEntry tc = tcList.get(i);
            if (i > 0) {
                titleSb.append("、");
                descSb.append("\n");
            }
            titleSb.append(tc.name());
            Object argsObj = tc.arguments();
            String args = argsObj != null ? argsObj.toString() : null;
            if (args != null && !args.isEmpty() && !"{}".equals(args)) {
                String display = args.length() > 200 ? args.substring(0, 200) + "..." : args;
                descSb.append(tc.name()).append(" ").append(display);
            }
        }
        String title = titleSb.toString();
        String description = !descSb.isEmpty() ? descSb.toString() : null;

        // 发送审批选项（工具信息通过 title/description 传递，不再发 HITL 文本）
        try {
            output.onChoice(Arrays.asList(
                    new ChoiceOption("/agree", "同意执行"),
                    new ChoiceOption("/deny", "拒绝执行")
            ), title, description);
        } catch (Exception e) {
            log.debug("[hitl] output.onChoice异常(SSE可能已断开): {}", e.getMessage());
        }
        try {
            output.onContentComplete();
        } catch (Exception e) {
            log.debug("[hitl] output.onContentComplete异常(SSE可能已断开): {}", e.getMessage());
        }

        // 构建返回值文本（供 AgentLoop 日志/降级使用，不再通过 SSE 发送）
        StringBuilder sb = new StringBuilder();
        sb.append("⏸️  **HITL 模式：以下工具调用需要审批**\n\n");
        for (ToolCallEntry tc : tcList) {
            String name = tc.name();
            Object argsObj = tc.arguments();
            String args = argsObj != null ? argsObj.toString() : null;
            sb.append("- `").append(name).append("`");
            if (args != null && !args.isEmpty() && !"{}".equals(args)) {
                String display = args.length() > 200 ? args.substring(0, 200) + "..." : args;
                sb.append(" ").append(display);
            }
            sb.append("\n");
        }
        sb.append("\n请选择：");
        return sb.toString();
    }

    // ==================== 沙箱越界 HITL 拦截 ====================

    /**
     * 沙箱越界 HITL 拦截：向用户展示越界详情，等待审批。
     *
     * @param output 输出接口（用于发送审批提示）
     * @return 审批提示文本
     */
    public String interceptForSandboxHITL(AgentOutput output) {
        String details = this.pendingSandboxHITDetails != null
                ? this.pendingSandboxHITDetails : "未知路径越界";

        String message = "⏸️  **沙箱越界 — 需要审批**\n\n" +
                "检测到工具试图访问工作区之外的路径：\n\n" +
                "> " + details + "\n\n" +
                "请选择：";
        try {
            output.onChoice(Arrays.asList(
                    new ChoiceOption("/agree", "同意执行"),
                    new ChoiceOption("/deny", "拒绝执行")
            ), "沙箱越界", details);
        } catch (Exception e) {
            log.debug("[sandbox-hitl] output.onChoice异常: {}", e.getMessage());
        }
        try {
            output.onContentComplete();
        } catch (Exception e) {
            log.debug("[sandbox-hitl] output.onContentComplete异常(SSE可能已断开): {}", e.getMessage());
        }
        return message;
    }

    // ==================== 状态读取（供 AgentLoop resume 方法使用） ====================

    /**
     * 获取并清除普通 HITL 暂存状态。
     * 返回的快照包含所有暂存数据，状态被清空以避免重复使用。
     */
    public PendingHITLState drainPendingHITL() {
        PendingHITLState state = new PendingHITLState(
                this.pendingHITLToolCalls,
                this.pendingHITLContent,
                this.pendingHITLReasoning,
                this.pendingHITTcList
        );
        this.pendingHITLToolCalls = null;
        this.pendingHITLContent = null;
        this.pendingHITLReasoning = null;
        this.pendingHITTcList = null;
        return state;
    }

    /**
     * 获取并清除沙箱越界 HITL 暂存状态。
     * 返回的快照包含所有沙箱暂存数据，状态被清空。
     */
    public PendingSandboxState drainSandboxHITL() {
        PendingSandboxState state = new PendingSandboxState(
                this.pendingSandboxHITToolCalls,
                this.pendingSandboxHITContent,
                this.pendingSandboxHITReasoning,
                this.pendingSandboxHITDetails
        );
        this.pendingSandboxHITToolCalls = null;
        this.pendingSandboxHITContent = null;
        this.pendingSandboxHITReasoning = null;
        this.pendingSandboxHITDetails = null;
        return state;
    }

    /**
     * 设置沙箱越界暂存（由 ToolExecutionResult 处理时调用）。
     *
     * @param toolCalls 触发越界的工具调用
     * @param details   越界详情
     */
    public void setSandboxPending(ONode toolCalls, String details) {
        this.pendingSandboxHITToolCalls = toolCalls;
        this.pendingSandboxHITDetails = details;
        this.hitlState = HitlState.PENDING;
    }

    // ==================== 内部方法 ====================

    /**
     * 解析 ONode 工具调用为 ToolCallEntry 列表。
     */
    private static List<ToolCallEntry> parseToolCalls(ONode toolCalls) {
        List<ToolCallEntry> tcList = new ArrayList<>();
        if (toolCalls == null || !toolCalls.isArray()) return tcList;
        for (ONode tc : toolCalls.getArray()) {
            String tcId = tc.get("id").getString();
            ONode func = tc.get("function");
            String tcName = func.get("name").getString();
            if (tcName == null || tcName.isEmpty()) continue;
            String tcArgs = func.get("arguments").getString();
            if (tcArgs == null) tcArgs = "{}";
            tcList.add(new ToolCallEntry(tcId, tcName, tcArgs));
        }
        return tcList;
    }

    // ==================== 内部数据类 ====================

    /** 普通 HITL 暂存状态的快照 */
    public record PendingHITLState(ONode toolCalls, String content,
                                   String reasoningContent, List<ToolCallEntry> tcList) {
    }

    /** 沙箱越界 HITL 暂存状态的快照 */
    public record PendingSandboxState(ONode toolCalls, String content,
                                      String reasoningContent, String details) {
    }
}
