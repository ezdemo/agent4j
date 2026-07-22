package site.sorghum.loopra.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Get;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import site.sorghum.loopra.bin.agent.core.SubAgent;
import site.sorghum.loopra.bin.builtin.SubAgentProfile;
import site.sorghum.loopra.bin.tool.ToolRegistry;
import site.sorghum.loopra.web.common.ServiceException;
import site.sorghum.loopra.web.common.WebErrorMessages;
import site.sorghum.loopra.web.model.ApiResponse;
import site.sorghum.loopra.web.model.SubAgentInfoDTO;
import site.sorghum.loopra.web.service.AgentService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Api(tags = "子代理")
@Controller
@Mapping("/api/sub-agents")
public class SubAgentController {

    @Inject
    private AgentService agentService;

    @ApiOperation(value = "列出子代理", notes = "返回内置子代理及其当前实际可用的工具")
    @Get
    @Mapping("")
    public ApiResponse<List<SubAgentInfoDTO>> list() {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        ToolRegistry registry = agentService.getSharedToolRegistry();
        registry.refresh();
        Collection<FunctionTool> enabledTools = registry.all().values();
        List<SubAgentInfoDTO> profiles = new ArrayList<>();
        for (SubAgentProfile profile : SubAgentProfile.values()) {
            profiles.add(toSubAgentInfoDTO(profile, enabledTools));
        }
        return ApiResponse.ok(profiles);
    }

    static SubAgentInfoDTO toSubAgentInfoDTO(SubAgentProfile profile, Collection<FunctionTool> enabledTools) {
        List<FunctionTool> availableTools = enabledTools == null ? List.of() : enabledTools.stream()
                .filter(tool -> !SubAgent.SUB_AGENT_DENY.contains(tool.name()))
                .toList();
        Set<String> allowedTools = profile.allowedTools(availableTools);
        List<String> tools = availableTools.stream()
                .map(FunctionTool::name)
                .filter(name -> allowedTools == null || allowedTools.contains(name))
                .sorted(Comparator.naturalOrder())
                .toList();
        return new SubAgentInfoDTO(profile.id(), profile.readOnly(), profile.instructions(), tools);
    }
}
