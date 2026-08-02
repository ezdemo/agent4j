package site.sorghum.loopra.web.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.SneakyThrows;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.noear.dami2.Dami;
import org.noear.snack4.ONode;
import org.noear.solon.annotation.*;
import site.sorghum.loopra.bin.config.ConfigChangedEvent;
import site.sorghum.loopra.bin.config.ConfigService;
import site.sorghum.loopra.bin.config.LoopraConfig;
import site.sorghum.loopra.web.common.ServiceException;
import site.sorghum.loopra.web.common.WebErrorMessages;
import site.sorghum.loopra.web.model.*;
import site.sorghum.loopra.web.service.AgentService;
import site.sorghum.loopra.web.service.DashboardService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        LoopraConfig cfg = configService.getConfig();
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
                cfg.price(),
                cfg.activePet(),
                cfg.terminateOnNoToolCall(),
                cfg.modelChannels().stream().map(channel -> new ConfigDTO.ModelChannelConfig(
                        channel.id(), channel.name(), channel.baseUrl(), maskApiKey(channel.apiKey()),
                        channel.apiProtocol(), channel.modelEntries().stream().map(entry -> new ConfigDTO.ModelConfig(
                                entry.name(), entry.contextTokens(), entry.imageInput(), entry.price()
                        )).collect(Collectors.toList())
                )).collect(Collectors.toList()),
                cfg.modelChannels().stream().anyMatch(channel -> !channel.apiKey().isBlank()),
                cfg.modelChannelId(),
                cfg.validationModel(),
                cfg.validationModelChannelId()
        );
        return ApiResponse.ok(data);
    }

    @ApiOperation(value = "更新配置", notes = "合并不为空的字段进行更新，支持更新 model、hitl 等运行时配置")
    @SneakyThrows
    @Put
    @Mapping("/config")
    public ApiResponse<String> updateConfig(@ApiParam(value = "配置项 Map") @Body Map<String, Object> body) {
        configService.updateConfig(body);

        // 渠道/API 变更 → 销毁重建（因 HttpModelClient 的 apiUrl/apiKey 为 final）。
        // 首次保存模型渠道时 Agent 尚未就绪，仍需初始化。
        boolean agentReinitialized = false;
        if (body.containsKey("baseUrl") || body.containsKey("apiKey")
                || body.containsKey("modelChannels") || body.containsKey("modelChannelId")
                || body.containsKey("validationModel") || body.containsKey("validationModelChannelId")) {
            agentService.reinitialize();
            agentReinitialized = true;
        }

        // model、hitl 等运行时配置 → 通过 DamiBus 广播，由监听者处理
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            // baseUrl/apiKey 已在上方处理，跳过
            if ("baseUrl".equals(key) || "apiKey".equals(key)
                    || "modelChannels".equals(key) || "modelChannelId".equals(key)
                    || "validationModel".equals(key) || "validationModelChannelId".equals(key)) continue;
            // 只发布已知的运行时配置键
            if ("model".equals(key) || "reasoningEffort".equals(key) || "hitl".equals(key)
                    || "terminateOnNoToolCall".equals(key) || "disabledTools".equals(key)) {
                Dami.bus().send("config.changed", new ConfigChangedEvent(key, entry.getValue()));
            }
        }

        return ApiResponse.ok(agentReinitialized ? "模型渠道已更新，Agent 已重新初始化" : "配置已更新");
    }

    @ApiOperation(value = "获取可用模型列表", notes = "返回配置中声明的所有可用模型及当前使用的模型")
    @SneakyThrows
    @Get
    @Mapping("/models")
    public ApiResponse<ModelListDTO> getModels() {
        LoopraConfig cfg = configService.getConfig();
        String currentModel = cfg.model();
        String currentChannelId = cfg.modelChannelId();
        List<ModelInfoDTO> models = new ArrayList<>();
        for (LoopraConfig.ModelChannel channel : cfg.modelChannels()) {
            LinkedHashSet<String> names = new LinkedHashSet<>(channel.models());
            if (channel.id().equals(currentChannelId)) names.add(currentModel);
            for (String name : names) {
                models.add(new ModelInfoDTO(name,
                        name.equals(currentModel) && channel.id().equals(currentChannelId),
                        channel.id(), channel.name()));
            }
        }
        return ApiResponse.ok(new ModelListDTO(currentModel, currentChannelId, models));
    }

    @ApiOperation(value = "从远程 API 获取模型列表", notes = "调用配置的 API 地址 + /models，携带 API Key 获取远程模型列表")
    @SneakyThrows
    @Get
    @Mapping("/remote-models")
    public ApiResponse<List<String>> getRemoteModels(@Param(value = "channelId", required = false) String channelId) {
        LoopraConfig cfg = configService.getConfig();
        LoopraConfig.ModelChannel channel = channelId == null || channelId.isBlank()
                ? cfg.activeModelChannel() : cfg.modelChannel(channelId);
        if (channel == null) return ApiResponse.fail("模型渠道不存在");
        return fetchRemoteModels(channel.baseUrl(), channel.apiKey());
    }

    @ApiOperation(value = "使用临时渠道配置获取模型列表", notes = "仅探测当前提交的 API 地址和密钥，不写入配置文件")
    @Post
    @Mapping("/remote-models")
    public ApiResponse<List<String>> probeRemoteModels(@Body Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        LoopraConfig.ModelChannel savedChannel = configService.getConfig().modelChannel(stringValue(request, "channelId"));
        String baseUrl = stringValue(request, "baseUrl");
        String apiKey = stringValue(request, "apiKey");
        if (baseUrl.isBlank() && savedChannel != null) baseUrl = savedChannel.baseUrl();
        if (apiKey.isBlank() && savedChannel != null) apiKey = savedChannel.apiKey();
        return fetchRemoteModels(baseUrl, apiKey);
    }

    private ApiResponse<List<String>> fetchRemoteModels(String baseUrl, String apiKey) {

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
                .header("User-Agent", "opencode/1.14.21 ai-sdk/provider-utils/4.0.23 runtime/bun/1.3.13")
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

    private String stringValue(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : value.toString().trim();
    }

    private String maskApiKey(String apiKey) {
        if (apiKey != null && apiKey.length() > MASK_MIN_LENGTH) {
            return apiKey.substring(0, MASK_KEEP_LENGTH) + "****" + apiKey.substring(apiKey.length() - MASK_KEEP_LENGTH);
        }
        return apiKey == null || apiKey.isBlank() ? "" : "****";
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

    @ApiOperation(value = "保存工作区排序", notes = "将工作区 hash 数组按顺序保存到配置中")
    @Put
    @Mapping("/workspaces/order")
    public ApiResponse<String> saveWorkspaceOrder(@ApiParam(value = "工作区 hash 有序列表") @Body List<String> order) {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        if (order == null) throw new ServiceException("排序数据不能为空");
        configService.updateConfig(Collections.singletonMap("workspaceOrder", order));
        return ApiResponse.ok("排序已保存");
    }

    @ApiOperation(value = "获取工作区排序", notes = "返回保存的工作区 hash 排序数组")
    @Get
    @Mapping("/workspaces/order")
    public ApiResponse<List<String>> getWorkspaceOrder() {
        if (!agentService.isReady()) throw new ServiceException(WebErrorMessages.AGENT_NOT_READY);
        try {
            Path configPath = Paths.get(System.getProperty("user.home"), ".loopra", "config.json");
            if (Files.exists(configPath)) {
                String json = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
                ONode node = ONode.ofJson(json);
                ONode orderNode = node.get("workspaceOrder");
                if (orderNode != null && orderNode.isArray()) {
                    List<String> order = new ArrayList<>();
                    for (ONode item : orderNode.getArray()) {
                        String s = item.getString();
                        if (s != null) order.add(s);
                    }
                    return ApiResponse.ok(order);
                }
            }
        } catch (Exception ignored) {
        }
        return ApiResponse.ok(new ArrayList<>());
    }

    // ============ loopra.md 编辑 ============

    @ApiOperation(value = "获取 loopra.md 内容", notes = "读取 ~/.loopra/loopra.md 文件内容")
    @Get
    @Mapping("/loopra-md")
    @SneakyThrows
    public ApiResponse<String> getLoopraMd() {
        Path path = Paths.get(System.getProperty("user.home"), ".loopra", "loopra.md");
        if (Files.exists(path)) {
            String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            return ApiResponse.ok(content);
        }
        return ApiResponse.ok("");
    }

    @ApiOperation(value = "更新 loopra.md 内容", notes = "写入 ~/.loopra/loopra.md 文件，保存后自动重新初始化 Agent")
    @Put
    @Mapping("/loopra-md")
    @SneakyThrows
    public ApiResponse<String> updateLoopraMd(@ApiParam(value = "Markdown 内容") @Body String content) {
        Path dir = Paths.get(System.getProperty("user.home"), ".loopra");
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path path = dir.resolve("loopra.md");
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
        // 触发 Agent 重新初始化，使新提示词生效（新会话会读取更新后的内容）
        if (agentService.isReady()) {
            agentService.reinitialize();
        }
        return ApiResponse.ok("loopra.md 已保存，Agent 已重新初始化，新会话将使用更新后的提示词");
    }

    // ============ 数据面板 ============

    @Inject
    private DashboardService dashboardService;

    @ApiOperation(value = "获取数据面板", notes = "返回最近 N 天的 Token 用量数据面板，包含按天和按模型的统计")
    @Get
    @Mapping("/usage/dashboard")
    public ApiResponse<DashboardDTO> getDashboard(
            @ApiParam(value = "统计天数，默认 7") @Param(value = "days", required = false) Integer days) {
        int n = (days != null && days > 0) ? days : 7;
        return ApiResponse.ok(dashboardService.getDashboard(n));
    }
}
