package site.sorghum.agent4j.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.SneakyThrows;
import org.noear.solon.annotation.*;
import site.sorghum.agent4j.bin.config.Agent4jConfig;
import site.sorghum.agent4j.bin.config.ConfigService;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.common.WebErrorMessages;
import site.sorghum.agent4j.web.model.*;
import site.sorghum.agent4j.web.service.AgentService;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.noear.snack4.ONode;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 配置与用量 API 控制器。
 *
 * @author Sorghum
 */
@Api(tags = "配置与工作区")
@Controller
@Mapping("/api")
public class ConfigController {

    @Inject
    private AgentService agentService;

    @Inject
    private ConfigService configService;

    // 脱敏相关常量
    private static final int MASK_MIN_LENGTH = 8;
    private static final int MASK_KEEP_LENGTH = 4;
    // 超时相关常量
    private static final int REMOTE_CONNECT_TIMEOUT_SEC = 15;
    private static final int REMOTE_READ_TIMEOUT_SEC = 30;
    @SneakyThrows
    @Get
    @Mapping("/config")
    public ApiResponse<ConfigDTO> getConfig() {
        Agent4jConfig cfg = configService.getConfig();
        String workspace = null;
        if (agentService.isReady()) {
            workspace = agentService.getWorkspace();
        }

        String apiKey = cfg.apiKey();
        String maskedKey;
        if (apiKey != null && apiKey.length() > MASK_MIN_LENGTH) {
            maskedKey = apiKey.substring(0, MASK_KEEP_LENGTH) + "****" + apiKey.substring(apiKey.length() - MASK_KEEP_LENGTH);
        } else {
            maskedKey = "****";
        }

        ConfigDTO data = new ConfigDTO(
                cfg.baseUrl(),
                cfg.model(),
                cfg.availableModels(),
                workspace,
                cfg.editMode(),
                cfg.reasoningEffort(),
                cfg.lang(),
                cfg.hitl(),
                configService.getDisabledTools(),
                cfg.blockedPaths(),
                maskedKey,
                cfg.price()
        );
        return ApiResponse.ok(data);
    }

    @ApiOperation(value = "更新配置", notes = "合并不为空的字段进行更新，支持更新 model、hitl 等运行时配置")
    @SneakyThrows
    @Put
    @Mapping("/config")
    public ApiResponse<String> updateConfig(@ApiParam(value = "配置项 Map") @Body Map<String, Object> body) {
        configService.updateConfig(body);

        // baseUrl 或 apiKey 变更 → 销毁重建（因 HttpModelClient 的 apiUrl/apiKey 为 final）
        if ((body.containsKey("baseUrl") || body.containsKey("apiKey")) && agentService.isReady()) {
            agentService.reinitialize();
            return ApiResponse.ok("API 地址/密钥已更新，Agent 已重新初始化");
        }

        if (body.containsKey("model") && agentService.isReady()) {
            String newModel = body.get("model").toString();
            agentService.updateModel(newModel);
        }

        if (body.containsKey("hitl") && agentService.isReady()) {
            Object hitlVal = body.get("hitl");
            boolean newHitl = hitlVal instanceof Boolean ? (Boolean) hitlVal : Boolean.parseBoolean(hitlVal.toString());
            agentService.updateHitlMode(newHitl);
        }

        return ApiResponse.ok("配置已更新");
    }

    @ApiOperation(value = "获取可用模型列表", notes = "返回配置中声明的所有可用模型及当前使用的模型")
    @SneakyThrows
    @Get
    @Mapping("/models")
    public ApiResponse<ModelListDTO> getModels() {
        Agent4jConfig cfg = configService.getConfig();
        String currentModel = cfg.model();
        List<String> available = cfg.availableModels();

        Set<String> modelSet = new LinkedHashSet<>();
        modelSet.add(currentModel);
        modelSet.addAll(available);

        List<ModelInfoDTO> models = modelSet.stream()
                .map(m -> new ModelInfoDTO(m, m.equals(currentModel)))
                .collect(Collectors.toList());

        return ApiResponse.ok(new ModelListDTO(currentModel, models));
    }

    @ApiOperation(value = "从远程 API 获取模型列表", notes = "调用配置的 API 地址 + /models，携带 API Key 获取远程模型列表")
    @SneakyThrows
    @Get
    @Mapping("/remote-models")
    public ApiResponse<List<String>> getRemoteModels() {
        Agent4jConfig cfg = configService.getConfig();
        String baseUrl = cfg.baseUrl();
        String apiKey = cfg.apiKey();

        if (baseUrl == null || baseUrl.isEmpty()) {
            return ApiResponse.fail("API 地址未配置");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            return ApiResponse.fail("API 密钥未配置");
        }

        // 构造 /models URL
        String modelsUrl = baseUrl.replaceAll("/+$", "") + "/models";

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(REMOTE_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(REMOTE_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder()
                .url(modelsUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                return ApiResponse.fail("远程 API 返回错误 (" + response.code() + "): " + body);
            }

            String json = response.body() != null ? response.body().string() : "[]";
            ONode root = ONode.ofJson(json);
            ONode dataArr = root.select("$.data");

            List<String> modelNames = new ArrayList<>();
            if (dataArr != null && dataArr.isArray()) {
                for (ONode item : dataArr.getArray()) {
                    String id = item.get("id").getString();
                    if (id != null && !id.isEmpty()) {
                        modelNames.add(id);
                    }
                }
            }

            // 按字母排序
            Collections.sort(modelNames);
            return ApiResponse.ok(modelNames);
        } catch (Exception e) {
            return ApiResponse.fail("获取远程模型列表失败: " + e.getMessage());
        }
    }

    @ApiOperation(value = "获取 Token 用量统计", notes = "根据工作区和会话查询 Token 用量")
    @Get
    @Mapping("/usage")
    public ApiResponse<UsageDTO> getUsage(
            @ApiParam(value = "工作区 hash") @Param(value = "workspaceHash", required = false) String workspaceHash,
            @ApiParam(value = "会话名称") @Param(value = "sessionName", required = false) String sessionName) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        if (workspaceHash == null){
            return ApiResponse.fail("workspaceHash 不能为空");
        }
        String workspacePath = agentService.resolveWorkspacePath(workspaceHash);
        if (workspacePath == null) workspacePath = agentService.getWorkspace();
        return ApiResponse.ok(agentService.getSessionUsageMap(workspacePath, sessionName));
    }

    @ApiOperation(value = "获取当前工作目录", notes = "返回当前 Agent 使用的工作目录路径")
    @Get
    @Mapping("/workspace")
    public ApiResponse<String> getWorkspace() {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        return ApiResponse.ok(agentService.getWorkspace());
    }

    @ApiOperation(value = "切换工作目录", notes = "切换到指定路径的工作目录")
    @Post
    @Mapping("/workspace")
    public ApiResponse<WorkspaceSwitchDTO> switchWorkspace(
            @ApiParam @Body Map<String, String> body) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        String path = body.get("path");
        if (path == null || path.isEmpty()) {
            throw new ServiceException("路径不能为空");
        }
        boolean ok = agentService.switchWorkspace(path);
        if (ok) {
            WorkspaceSwitchDTO data = new WorkspaceSwitchDTO(
                    "工作目录已切换", agentService.getWorkspace(), null);
            return ApiResponse.ok(data);
        }
        throw new ServiceException("无效的工作目录路径: " + path);
    }

    @ApiOperation(value = "获取工作区列表", notes = "返回所有历史工作区记录")
    @Get
    @Mapping("/workspaces")
    public ApiResponse<List<WorkspaceInfoDTO>> listWorkspaces() {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        return ApiResponse.ok(agentService.listWorkspaces());
    }

    @ApiOperation(value = "切换到指定工作区", notes = "根据 hash 切换到对应工作区")
    @Post
    @Mapping("/workspaces/switch")
    public ApiResponse<WorkspaceSwitchDTO> switchToWorkspace(
            @ApiParam(value = "{\"hash\":\"...\"}") @Body Map<String, String> body) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        String hash = body.get("hash");
        if (hash == null || hash.isEmpty()) {
            throw new ServiceException(WebErrorMessages.WORKSPACE_HASH_REQUIRED);
        }
        boolean ok = agentService.switchToWorkspaceByHash(hash);
        if (ok) {
            WorkspaceSwitchDTO data = new WorkspaceSwitchDTO(
                    "工作区已切换", agentService.getWorkspace(), agentService.getCurrentSession());
            return ApiResponse.ok(data);
        }
        throw new ServiceException("切换工作区失败: " + hash);
    }

    @ApiOperation(value = "删除工作区", notes = "根据 hash 删除指定工作区记录")
    @Delete
    @Mapping("/workspaces/{hash}")
    public ApiResponse<String> deleteWorkspace(@ApiParam(value = "工作区 hash") @Param("hash") String hash) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        if (hash == null || hash.isEmpty()) {
            throw new ServiceException(WebErrorMessages.WORKSPACE_HASH_REQUIRED);
        }
        boolean ok = agentService.deleteWorkspace(hash);
        if (ok) {
            return ApiResponse.ok("工作区已删除");
        }
        throw new ServiceException("删除工作区失败");
    }
}
