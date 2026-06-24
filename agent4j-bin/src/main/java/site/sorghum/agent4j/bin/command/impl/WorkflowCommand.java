package site.sorghum.agent4j.bin.command.impl;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.command.ChatCommand;
import site.sorghum.agent4j.bin.command.ChatCommandContext;
import site.sorghum.agent4j.bin.command.MessageWrapper;
import site.sorghum.agent4j.bin.workflow.*;
import site.sorghum.agent4j.tool.LogLevel;

import java.time.Instant;

/**
 * /workflow — 设定并跟踪工作流。
 * <p>
 * 子命令：
 * /workflow create &lt;描述&gt;           创建新工作流
 * /workflow status                    查看当前工作流进度
 * /workflow show                      显示工作流结构
 * /workflow pause                     暂停工作流
 * /workflow resume                    恢复工作流
 * /workflow run                       开始执行工作流
 * /workflow node add &lt;描述&gt;        添加新节点
 * /workflow node remove &lt;节点ID&gt;    删除节点
 * /workflow node edit &lt;节点ID&gt; &lt;描述&gt; 编辑节点描述
 * /workflow node type &lt;节点ID&gt; &lt;类型&gt; 设置节点类型
 * /workflow link &lt;from&gt; &lt;to&gt;        建立依赖关系
 * /workflow unlink &lt;from&gt; &lt;to&gt;      移除依赖关系
 * /workflow branch &lt;节点ID&gt; --if &lt;条件&gt; --then &lt;节点ID&gt; --else &lt;节点ID&gt;
 *                                      添加条件分支
 * /workflow retry &lt;节点ID&gt;          重试某个节点
 * /workflow skip &lt;节点ID&gt;           跳过某个节点
 * /workflow clear                     清除当前工作流
 * /workflow help                      显示帮助
 * </p>
 *
 * @author Sorghum
 */
@Component
@Slf4j
public class WorkflowCommand implements ChatCommand {

    private static final String NO_WORKFLOW_MSG = "当前会话没有活跃工作流。使用 /workflow create <描述> 创建工作流。";
    private static final int CMD_PREFIX_LEN = "/workflow".length();

    @Inject
    private WorkflowEngine workflowEngine;

    @Override
    public String getCommand() {
        return "workflow";
    }

    @Override
    public boolean matches(String input) {
        return input != null && input.trim().toLowerCase().startsWith("/workflow");
    }

    @Override
    public String getDescription() {
        return "/workflow    设定并跟踪工作流（子命令：create/status/show/pause/resume/run/approve/deny/node/link/unbranch/retry/skip/clear）";
    }

    @Override
    public String getCommandType() {
        return "tool";
    }

    @Override
    public CommandResult execute(MessageWrapper input, ChatCommandContext ctx) throws Exception {
        String text = input.getMessage();
        String remaining = text.trim().substring(CMD_PREFIX_LEN).trim();
        String[] parts = remaining.split("\\s+", 2);
        String subCmd = parts.length > 0 ? parts[0].toLowerCase() : "help";
        String args = parts.length > 1 ? parts[1] : "";

        switch (subCmd) {
            case "create":
                return handleCreate(args, ctx, input);
            case "status":
                return handleStatus(ctx);
            case "show":
                return handleShow(ctx);
            case "pause":
                return handlePause(ctx);
            case "resume":
                return handleResume(ctx);
            case "run":
                return handleRun(ctx, input);
            case "approve":
                return handleApprove(args, ctx);
            case "deny":
                return handleDeny(args, ctx);
            case "node":
                return handleNode(args, ctx);
            case "link":
                return handleLink(args, ctx);
            case "unlink":
                return handleUnlink(args, ctx);
            case "branch":
                return handleBranch(args, ctx);
            case "retry":
                return handleRetry(args, ctx);
            case "skip":
                return handleSkip(args, ctx);
            case "clear":
                return handleClear(ctx);
            default:
                return handleHelp(ctx);
        }
    }

    private CommandResult handleCreate(String args, ChatCommandContext ctx, MessageWrapper input) throws Exception {
        if (args.isEmpty()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                    "用法: /workflow create <描述>");
            return CommandResult.CONTINUE;
        }

        // 注入 prompt，引导 LLM 使用工具创建工作流
        String prompt = """
                用户想要创建工作流：「%s」
                
                请使用 workflow_create_dag 工具来创建这个工作流。
                
                你需要：
                1. 分析目标，拆解为多个具体的步骤
                2. 确定每个步骤的类型（ACTION/CONDITION/PARALLEL）
                3. 确定步骤之间的依赖关系
                4. 调用 workflow_create_dag 工具创建工作流
                
                如果任务是简单的线性流程，可以直接创建线性结构。
                如果任务需要条件分支或并行执行，请设计相应的 DAG 结构。
                """.formatted(args);

        input.setMessage(prompt);
        return CommandResult.LOOP;
    }

    private CommandResult handleStatus(ChatCommandContext ctx) {
        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, NO_WORKFLOW_MSG);
            return CommandResult.CONTINUE;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n──────────────────────────────────────\n");
        sb.append("🔄 工作流：").append(workflow.getTitle()).append("\n");
        sb.append("状态：").append(formatStatusBadge(workflow.getStatus())).append("\n\n");
        sb.append("进度：").append(workflow.progressText()).append("\n\n");

        for (WorkflowNode node : workflow.getNodes()) {
            String icon = switch (node.getStatus()) {
                case DONE -> "✅";
                case RUNNING -> "🔵";
                case FAILED -> "❌";
                case SKIPPED -> "⏭️";
                case WAITING -> "⏸️";
                case BLOCKED -> "🔒";
                case READY -> "🟢";
                case PENDING -> "⬜";
            };
            String retryInfo = node.getRetryCount() > 0 ? " (retry:" + node.getRetryCount() + ")" : "";
            sb.append(icon).append(" ").append(node.getId()).append(". ")
                    .append(node.getDescription()).append(" (").append(node.getType()).append(")")
                    .append(retryInfo).append("\n");
            if (node.getLastError() != null && !node.getLastError().isEmpty()) {
                sb.append("   └─ ").append(node.getLastError()).append("\n");
            }
        }
        sb.append("──────────────────────────────────────");

        ctx.getAgent().getOutput().onReasoning(sb.toString());
        return CommandResult.CONTINUE;
    }

    private CommandResult handleShow(ChatCommandContext ctx) {
        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, NO_WORKFLOW_MSG);
            return CommandResult.CONTINUE;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n──────────────────────────────────────\n");
        sb.append("🔄 工作流结构：").append(workflow.getTitle()).append("\n\n");

        sb.append("节点:\n");
        for (WorkflowNode node : workflow.getNodes()) {
            sb.append("  ").append(node.getId()).append(". ").append(node.getDescription())
                    .append(" (").append(node.getType()).append(")\n");
        }

        sb.append("\n依赖关系:\n");
        for (WorkflowEdge edge : workflow.getEdges()) {
            sb.append("  ").append(edge.getFrom()).append(" -> ").append(edge.getTo());
            if (edge.getCondition() != null) {
                sb.append(" [条件: ").append(edge.getCondition()).append("]");
            }
            sb.append("\n");
        }
        sb.append("──────────────────────────────────────");

        ctx.getAgent().getOutput().onReasoning(sb.toString());
        return CommandResult.CONTINUE;
    }

    private CommandResult handlePause(ChatCommandContext ctx) throws Exception {
        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);
        workflowEngine.pause(workflow, ctx);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "⏸️ 工作流已暂停： " + workflow.getTitle());
        return CommandResult.CONTINUE;
    }

    private CommandResult handleResume(ChatCommandContext ctx) throws Exception {
        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);
        workflowEngine.resume(workflow, ctx);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "▶️ 工作流已恢复： " + workflow.getTitle());
        return CommandResult.CONTINUE;
    }

    private CommandResult handleRun(ChatCommandContext ctx, MessageWrapper input) throws Exception {
        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);

        // 设置工作流状态为 ACTIVE
        workflow.setStatus(WorkflowStatus.ACTIVE);
        workflow.setUpdatedAt(java.time.Instant.now());
        ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);

        // 生成工作流结构概览
        String structureText = workflowEngine.generateStructureText(workflow);

        // 使用引擎生成当前就绪节点的执行提示词
        String execPrompt = workflowEngine.buildExecutionPrompt(workflow);

        // 注入执行 prompt，让 LLM 开始执行
        String prompt = """
                当前会话已设定工作流：「%s」
                
                工作流结构：
                %s
                
                %s
                """
                .formatted(workflow.getTitle(), structureText,
                        execPrompt != null ? execPrompt : "工作流已完成。");

        input.setMessage(prompt);
        return CommandResult.LOOP;
    }

    private CommandResult handleApprove(String args, ChatCommandContext ctx) throws Exception {
        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);

        String nodeId = args.trim();
        if (nodeId.isEmpty()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "用法: /workflow approve <节点ID>");
            return CommandResult.CONTINUE;
        }

        WorkflowNode node = workflow.findNode(nodeId);
        if (node == null) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "节点 " + nodeId + " 不存在");
            return CommandResult.CONTINUE;
        }
        if (node.getStatus() != NodeStatus.WAITING) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "节点 " + nodeId + " 不在等待审批状态");
            return CommandResult.CONTINUE;
        }

        // 批准：标记为完成
        node.setStatus(NodeStatus.DONE);
        node.setApprovalResult("approved");
        node.setResult("人工审批通过");
        node.setCompletedAt(Instant.now());
        workflow.setUpdatedAt(Instant.now());

        // 恢复工作流
        workflow.setStatus(WorkflowStatus.ACTIVE);

        ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                "✅ 节点 " + nodeId + " 已批准。工作流已恢复执行。");

        // 注入恢复执行的 prompt
        String prompt = "工作流节点 " + nodeId + " 已获得人工批准，工作流已恢复。请继续执行下一个就绪节点。";
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, prompt);
        return CommandResult.LOOP;
    }

    private CommandResult handleDeny(String args, ChatCommandContext ctx) throws Exception {
        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);

        String nodeId = args.trim();
        if (nodeId.isEmpty()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "用法: /workflow deny <节点ID>");
            return CommandResult.CONTINUE;
        }

        WorkflowNode node = workflow.findNode(nodeId);
        if (node == null) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "节点 " + nodeId + " 不存在");
            return CommandResult.CONTINUE;
        }
        if (node.getStatus() != NodeStatus.WAITING) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "节点 " + nodeId + " 不在等待审批状态");
            return CommandResult.CONTINUE;
        }

        // 拒绝：标记为失败
        node.setStatus(NodeStatus.FAILED);
        node.setApprovalResult("rejected");
        node.setLastError("人工审批拒绝");
        workflow.setUpdatedAt(Instant.now());
        workflow.setStatus(WorkflowStatus.FAILED);

        ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                "❌ 节点 " + nodeId + " 已拒绝。工作流已标记为失败。");
        return CommandResult.CONTINUE;
    }

    private CommandResult handleNode(String args, ChatCommandContext ctx) throws Exception {
        String[] parts = args.split("\\s+", 2);
        String subCmd = parts.length > 0 ? parts[0].toLowerCase() : "";
        String subArgs = parts.length > 1 ? parts[1] : "";

        switch (subCmd) {
            case "add":
                return handleNodeAdd(subArgs, ctx);
            case "remove":
                return handleNodeRemove(subArgs, ctx);
            case "edit":
                return handleNodeEdit(subArgs, ctx);
            case "type":
                return handleNodeType(subArgs, ctx);
            default:
                ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                        "用法: /workflow node <add|remove|edit|type> [参数]");
                return CommandResult.CONTINUE;
        }
    }

    private CommandResult handleNodeAdd(String args, ChatCommandContext ctx) throws Exception {
        if (args.isEmpty()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                    "用法: /workflow node add <描述>");
            return CommandResult.CONTINUE;
        }

        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);

        workflowEngine.addNode(workflow, args, NodeType.ACTION, ctx);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "✅ 节点已添加: " + args);
        return CommandResult.CONTINUE;
    }

    private CommandResult handleNodeRemove(String args, ChatCommandContext ctx) throws Exception {
        if (args.isEmpty()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                    "用法: /workflow node remove <节点ID>");
            return CommandResult.CONTINUE;
        }

        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);

        workflowEngine.removeNode(workflow, args, ctx);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "✅ 节点已移除: " + args);
        return CommandResult.CONTINUE;
    }

    private CommandResult handleNodeEdit(String args, ChatCommandContext ctx) throws Exception {
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                    "用法: /workflow node edit <节点ID> <描述>");
            return CommandResult.CONTINUE;
        }

        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);

        WorkflowNode node = workflow.findNode(parts[0]);
        if (node == null) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "节点不存在: " + parts[0]);
            return CommandResult.CONTINUE;
        }

        node.setDescription(parts[1]);
        workflow.setUpdatedAt(Instant.now());
        ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "✅ 节点已更新: " + parts[0]);
        return CommandResult.CONTINUE;
    }

    private CommandResult handleNodeType(String args, ChatCommandContext ctx) throws Exception {
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                    "用法: /workflow node type <节点ID> <类型>");
            return CommandResult.CONTINUE;
        }

        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);

        WorkflowNode node = workflow.findNode(parts[0]);
        if (node == null) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "节点不存在: " + parts[0]);
            return CommandResult.CONTINUE;
        }

        try {
            NodeType type = NodeType.valueOf(parts[1].toUpperCase());
            node.setType(type);
            workflow.setUpdatedAt(Instant.now());
            ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "✅ 节点类型已更新: " + parts[0] + " -> " + type);
        } catch (IllegalArgumentException e) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "无效的节点类型: " + parts[1]);
        }
        return CommandResult.CONTINUE;
    }

    private CommandResult handleLink(String args, ChatCommandContext ctx) throws Exception {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                    "用法: /workflow link <from> <to>");
            return CommandResult.CONTINUE;
        }

        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);

        workflowEngine.addEdge(workflow, parts[0], parts[1], EdgeType.NORMAL, null, ctx);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "✅ 依赖关系已添加: " + parts[0] + " -> " + parts[1]);
        return CommandResult.CONTINUE;
    }

    private CommandResult handleUnlink(String args, ChatCommandContext ctx) throws Exception {
        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                    "用法: /workflow unlink <from> <to>");
            return CommandResult.CONTINUE;
        }

        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);

        workflowEngine.removeEdge(workflow, parts[0], parts[1], ctx);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "✅ 依赖关系已移除: " + parts[0] + " -> " + parts[1]);
        return CommandResult.CONTINUE;
    }

    private CommandResult handleBranch(String args, ChatCommandContext ctx) throws Exception {
        // 解析 --if, --then, --else 参数
        String ifCondition = null;
        String thenNode = null;
        String elseNode = null;

        String[] parts = args.split("\\s+");
        String nodeId = parts.length > 0 ? parts[0] : "";

        for (int i = 1; i < parts.length; i++) {
            if ("--if".equals(parts[i]) && i + 1 < parts.length) {
                ifCondition = parts[++i];
            } else if ("--then".equals(parts[i]) && i + 1 < parts.length) {
                thenNode = parts[++i];
            } else if ("--else".equals(parts[i]) && i + 1 < parts.length) {
                elseNode = parts[++i];
            }
        }

        if (nodeId.isEmpty() || ifCondition == null || thenNode == null) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                    "用法: /workflow branch <节点ID> --if <条件> --then <节点ID> [--else <节点ID>]");
            return CommandResult.CONTINUE;
        }

        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);

        // 将节点类型改为 CONDITION
        WorkflowNode node = workflow.findNode(nodeId);
        if (node == null) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "节点不存在: " + nodeId);
            return CommandResult.CONTINUE;
        }
        node.setType(NodeType.CONDITION);
        node.setCondition(ifCondition);

        // 添加条件边
        workflowEngine.addEdge(workflow, nodeId, thenNode, EdgeType.CONDITION_TRUE, ifCondition, ctx);
        if (elseNode != null) {
            workflowEngine.addEdge(workflow, nodeId, elseNode, EdgeType.CONDITION_FALSE, ifCondition, ctx);
        }

        workflow.setUpdatedAt(Instant.now());
        ctx.getAgent().getWorkspaceManager().getWorkflowStore().save(workflow);
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "✅ 条件分支已添加: " + nodeId);
        return CommandResult.CONTINUE;
    }

    /**
     * 从参数中解析节点ID，并校验存在性。返回解析后的 WorkflowNode，
     * 若解析失败或不存在则通过 output 报告错误并返回 null。
     */
    private WorkflowNode resolveNodeId(String args, ChatCommandContext ctx) throws Exception {
        if (args.isEmpty()) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "用法: /workflow retry/skip <节点ID>");
            return null;
        }

        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) {
            noWorkflowResponse(ctx);
            return null;
        }

        WorkflowNode node = workflow.findNode(args.trim());
        if (node == null) {
            ctx.getAgent().getOutput().onLog(LogLevel.INFO, "节点不存在: " + args);
            return null;
        }
        return node;
    }

    private CommandResult handleRetry(String args, ChatCommandContext ctx) throws Exception {
        WorkflowNode node = resolveNodeId(args, ctx);
        if (node == null) return CommandResult.CONTINUE;

        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        workflowEngine.retryNode(workflow, node.getId(), ctx);

        ctx.getAgent().getOutput().onReasoning(
                "🔄 节点 " + node.getId() + " 已重置为待执行状态，请继续工作。");
        return CommandResult.CONTINUE;
    }

    private CommandResult handleSkip(String args, ChatCommandContext ctx) throws Exception {
        WorkflowNode node = resolveNodeId(args, ctx);
        if (node == null) return CommandResult.CONTINUE;

        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        workflowEngine.skipNode(workflow, node.getId(), ctx);

        ctx.getAgent().getOutput().onLog(LogLevel.INFO,
                "⏭️ 已跳过节点 " + node.getId() + "：" + node.getDescription());
        return CommandResult.CONTINUE;
    }

    private CommandResult handleClear(ChatCommandContext ctx) throws Exception {
        Workflow workflow = workflowEngine.getCurrentWorkflow(ctx);
        if (workflow == null) return noWorkflowResponse(ctx);

        ctx.getAgent().getWorkspaceManager().getWorkflowStore().delete(workflow.getSessionId());
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, "🗑️ 工作流已清除：" + workflow.getTitle());
        return CommandResult.CONTINUE;
    }

    private CommandResult handleHelp(ChatCommandContext ctx) {
        ctx.getAgent().getOutput().onReasoning(
                """
                /workflow 子命令：
                  /workflow create <描述>                    创建新工作流
                  /workflow status                          查看当前工作流进度
                  /workflow show                            显示工作流结构
                  /workflow pause                           暂停工作流
                  /workflow resume                          恢复工作流
                  /workflow run                             开始执行工作流
                  /workflow node add <描述>                 添加新节点
                  /workflow node remove <节点ID>            删除节点
                  /workflow node edit <节点ID> <描述>       编辑节点描述
                  /workflow node type <节点ID> <类型>       设置节点类型
                  /workflow link <from> <to>                建立依赖关系
                  /workflow unlink <from> <to>              移除依赖关系
                  /workflow branch <节点ID> --if <条件> --then <节点ID> [--else <节点ID>]
                                                          添加条件分支
                  /workflow retry <节点ID>                  重试某个节点
                  /workflow skip <节点ID>                   跳过某个节点
                  /workflow clear                           清除当前工作流
                """);
        return CommandResult.CONTINUE;
    }

    private CommandResult noWorkflowResponse(ChatCommandContext ctx) {
        ctx.getAgent().getOutput().onLog(LogLevel.INFO, NO_WORKFLOW_MSG);
        return CommandResult.CONTINUE;
    }

    private String formatStatusBadge(WorkflowStatus status) {
        return switch (status) {
            case DRAFT -> "📝 草稿";
            case ACTIVE -> "🟢 进行中";
            case PAUSED -> "🟡 已暂停";
            case COMPLETED -> "✅ 已完成";
            case FAILED -> "🔴 失败";
        };
    }
}