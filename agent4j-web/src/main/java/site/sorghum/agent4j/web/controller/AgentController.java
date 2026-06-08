package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.noear.solon.ai.talents.mount.SkillDir;
import org.noear.solon.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.sorghum.agent4j.tool.solon.common.Agent4JSkillProvider;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.model.*;
import site.sorghum.agent4j.web.service.AgentService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 状态查询 API。
 *
 * @author Sorghum
 */
@Api(tags = "Agent 控制")
@Controller
@Mapping("/api/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    @Inject
    private AgentService agentService;

    @ApiOperation(value = "获取 Agent 状态", notes = "返回当前 Agent 的运行状态，包括模型、工作区、会话等信息")
    @Get
    @Mapping("/status")
    public ApiResponse<AgentStatusDTO> status() {
        if (!agentService.isReady()) {
            throw new ServiceException("Agent 未初始化，请检查 ~/.agent4j/config.json 配置");
        }
        return ApiResponse.ok(agentService.getStatus());
    }

    @ApiOperation(value = "获取历史消息", notes = "根据工作区 hash 和会话名称获取历史消息列表")
    @Get
    @Mapping("/history")
    public ApiResponse<List<?>> history(
            @ApiParam(value = "工作区 hash", required = true) @Param(value = "workspaceHash", required = true) String workspaceHash,
            @ApiParam(value = "会话名称", required = true) @Param(value = "sessionName", required = true) String sessionName) {
        if (!agentService.isReady()) {
            throw new ServiceException("Agent 未初始化");
        }
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        return ApiResponse.ok(agentService.getHistory(workspacePath, sessionName));
    }

    @ApiOperation(value = "获取可用命令列表", notes = "返回所有可用的聊天命令（如 /help、/retry、/compact 等）")
    @Get
    @Mapping("/commands")
    public ApiResponse<List<CommandMetaDTO>> commands() {
        if (!agentService.isReady()) {
            throw new ServiceException("Agent 未初始化");
        }
        return ApiResponse.ok(agentService.getCommandMetaList());
    }

    @ApiOperation(value = "获取可用 skill 列表", notes = "返回当前已注册的所有 skill")
    @Get
    @Mapping("/skills")
    public ApiResponse<List<SkillMetaDTO>> skills() {
        if (!agentService.isReady()) {
            throw new ServiceException("Agent 未初始化");
        }
        try {
            Agent4JSkillProvider skillProvider = Agent4JSkillProvider.getOrCreate("~");
            Collection<SkillDir> skills = skillProvider.getPoolManager().getSkills();

            List<SkillMetaDTO> result = skills.stream()
                    .map(s -> new SkillMetaDTO(
                            s.getName(),
                            s.getDescription(),
                            s.getAliasPath(),
                            ""
                    ))
                    .collect(Collectors.toList());

            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.warn("获取 skill 列表失败: {}", e.getMessage());
            return ApiResponse.ok(Collections.emptyList());
        }
    }

    /**
     * 从 SKILL.md 文件中读取描述（YAML frontmatter 中的 description 字段）
     */
    private String readSkillDescription(Path skillFile) {
        try {
            List<String> lines = Files.readAllLines(skillFile);
            boolean inFrontmatter = false;
            for (String line : lines) {
                if (line.trim().equals("---")) {
                    if (!inFrontmatter) {
                        inFrontmatter = true;
                    } else {
                        break;
                    }
                } else if (inFrontmatter && line.startsWith("description:")) {
                    return line.substring("description:".length()).trim();
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return "";
    }

    @ApiOperation(value = "获取当前会话的系统提示词", notes = "返回完整的 PromptPrefix 内容（含基础提示词 + 工具定义 + Skill 索引 + Plan Mode 说明）")
    @Get
    @Mapping("/prompt")
    public ApiResponse<PromptDTO> prompt(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @ApiParam(value = "会话名称") @Param(value = "sessionName", required = false) String sessionName) {
        if (!agentService.isReady()) {
            throw new ServiceException("Agent 未初始化");
        }
        String workspacePath = workspaceHash != null ? agentService.resolveWorkspacePath(workspaceHash) : null;
        return ApiResponse.ok(agentService.getSystemPrompt(workspacePath, sessionName));
    }
}
