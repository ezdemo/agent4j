package site.sorghum.loopra.web.tool;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Param;
import site.sorghum.loopra.tool.SolonToTools;
import site.sorghum.loopra.tool.ToolContext;
import site.sorghum.loopra.web.service.RequirementManager;

import java.util.Collection;

/**
 * 需求池专属工具集 —— 仅需求执行会话（会话名以 {@code req_} 前缀）可用。
 * <p>
 * 工具全局注册（共享工具系统），但执行时校验当前会话归属：
 * 普通会话调用返回 SCOPE_ONLY 拒绝，保证「仅需求里的 Agent 可用」。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class RequirementToolProvider extends AbsToolProvider implements SolonToTools {

    /** 需求会话名前缀 */
    private static final String REQUIREMENT_SESSION_PREFIX = "req_";

    @Inject
    private RequirementManager requirementManager;

    /**
     * 测试装配入口（Solon 容器正常走 @Inject）。
     */
    void setRequirementManagerForTest(RequirementManager requirementManager) {
        this.requirementManager = requirementManager;
    }

    @ToolMapping(name = "finish_requirement", description = """
            声明当前需求执行结果并流转状态（仅需求执行会话可用）。
            完成任务后必须调用本工具：status=done 表示已完成，failed 表示失败；
            summary 填写执行总结（做了什么、改动、结果）。
            """)
    public String finishRequirement(
            @Param(name = "status", description = "执行结果：done(已完成) / failed(已失败)", required = true) String status,
            @Param(name = "summary", description = "执行总结", required = true) String summary,
            ToolContext ctx) {
        String sessionId = resolveRequirementSession(ctx);
        if (sessionId == null) {
            return "SCOPE_ONLY: finish_requirement 仅需求执行会话可用";
        }
        if (!"done".equals(status) && !"failed".equals(status)) {
            return "INVALID_STATUS: status 必须是 done 或 failed";
        }
        requirementManager.finish(sessionId.substring(REQUIREMENT_SESSION_PREFIX.length()), status, summary);
        return "REQUIREMENT_FINISHED: " + status + "，总结已记录";
    }

    @ToolMapping(name = "reply_requirement_comment", description = """
            回复用户评论（仅需求执行会话可用）。执行期间用户评论会作为消息进入本会话，
            需要回复时调用本工具，回复会展示在评论区。
            """)
    public String replyComment(
            @Param(name = "reply", description = "回复内容", required = true) String reply,
            ToolContext ctx) {
        String sessionId = resolveRequirementSession(ctx);
        if (sessionId == null) {
            return "SCOPE_ONLY: reply_requirement_comment 仅需求执行会话可用";
        }
        if (reply == null || reply.isBlank()) {
            return "EMPTY_REPLY: 回复内容不能为空";
        }
        if (!requirementManager.replyComment(sessionId.substring(REQUIREMENT_SESSION_PREFIX.length()), reply.trim())) {
            return "REPLY_FAILED: 回复写入失败";
        }
        return "REPLY_SENT: 回复已写入评论区";
    }

    @ToolMapping(name = "show_requirements", description = """
            查看当前需求详情与最近评论（仅需求执行会话可用）。
            返回需求标题、状态、优先级、描述及最近 20 条用户评论。
            """)
    public String showRequirements(ToolContext ctx) {
        String sessionId = resolveRequirementSession(ctx);
        if (sessionId == null) {
            return "SCOPE_ONLY: show_requirements 仅需求执行会话可用";
        }
        return requirementManager.requirementContextForAI(sessionId.substring(REQUIREMENT_SESSION_PREFIX.length()));
    }

    /**
     * 校验并解析当前会话是否为需求执行会话。
     *
     * @return 需求会话名（req_xxx），非需求会话返回 null
     */
    private String resolveRequirementSession(ToolContext ctx) {
        if (ctx == null) {
            return null;
        }
        String sessionId = ctx.getSessionId();
        if (sessionId == null || !sessionId.startsWith(REQUIREMENT_SESSION_PREFIX)) {
            return null;
        }
        return sessionId;
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }
}
