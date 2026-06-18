package site.sorghum.agent4j.bin.vision;

import lombok.extern.slf4j.Slf4j;
import org.noear.dami2.Dami;
import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.chat.tool.AbsToolProvider;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Param;
import site.sorghum.agent4j.bin.config.ConfigChangedEvent;
import site.sorghum.agent4j.bin.config.ConfigService;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.util.Collection;

/**
 * 图片识别工具 —— 让 AI 能够识别图片内容。
 * <p>
 * 支持传入图片 URL（HTTP/HTTPS 或 Base64 Data URI），返回图片识别结果。
 * 使用独立配置的视觉模型进行识别。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class VisionTool extends AbsToolProvider implements SolonToTools {

    @Inject
    private VisionService visionService;

    @Inject
    private ConfigService configService;

    /**
     * 无参构造器 —— Solon DI 使用。
     */
    public VisionTool() {
        // 监听配置变更
        Dami.bus().<ConfigChangedEvent>listen("config.changed", event -> {
            ConfigChangedEvent e = event.getPayload();
            if (e == null) return;
            if ("vision".equals(e.key())) {
                log.info("[vision] 收到配置变更事件，重新加载 VisionService");
                visionService.reloadConfig();
            }
        });
    }

    /**
     * 带参构造器 —— SubAgent 手动创建时使用。
     *
     * @param visionService VisionService 实例
     * @param configService ConfigService 实例
     */
    public VisionTool(VisionService visionService, ConfigService configService) {
        this.visionService = visionService;
        this.configService = configService;
        // 监听配置变更
        Dami.bus().<ConfigChangedEvent>listen("config.changed", event -> {
            ConfigChangedEvent e = event.getPayload();
            if (e == null) return;
            if ("vision".equals(e.key())) {
                log.info("[vision] 收到配置变更事件，重新加载 VisionService");
                visionService.reloadConfig();
            }
        });
    }

    @ToolMapping(name = "vision_recognize", description = """
                识别图片内容。支持传入图片 URL（HTTP/HTTPS 或 Base64 Data URI），返回图片识别结果。
                参数: imageBase64(必填, 图片URL), prompt(可选, 指导识别的提示词)。
                返回: 包含思考块和内容块的识别结果。
                需要在 config.json 中配置 vision 部分（baseUrl, apiKey, model）。
                """)
    public String visionRecognize(
            @Param(name = "imageBase64", description = "图片 URL（HTTP/HTTPS 或 Base64 Data URI）") String imageBase64,
            @Param(name = "prompt", description = "可选的提示词，用于指导图片识别", required = false) String prompt,
            ToolContext ctx) {
        
        // 1. 检查配置
        String visionBaseUrl = configService.getVisionBaseUrl();
        String visionApiKey = configService.getVisionApiKey();
        String visionModel = configService.getVisionModel();
        
        if (visionBaseUrl == null || visionBaseUrl.isBlank() ||
            visionApiKey == null || visionApiKey.isBlank() ||
            visionModel == null || visionModel.isBlank()) {
            return "CONFIG_MISSING: 图片识别服务未配置。请在 ~/.agent4j/config.json 中添加 vision 配置：\n" +
                   "{\n" +
                   "  \"vision\": {\n" +
                   "    \"baseUrl\": \"https://api.example.com/v1\",\n" +
                   "    \"apiKey\": \"your-api-key\",\n" +
                   "    \"model\": \"vision-model-name\"\n" +
                   "  }\n" +
                   "}";
        }

        // 2. 检查参数
        if (imageBase64 == null || imageBase64.isBlank()) {
            return "PARAM_MISSING: 缺少必填参数 'imageBase64'";
        }

        // 3. 调用识别服务
        try {
            VisionService.VisionResult result = visionService.recognize(imageBase64, prompt);
            
            StringBuilder sb = new StringBuilder();
            sb.append("图片识别完成。\n\n");
            
            if (result.hasReasoning()) {
                sb.append("【思考过程】\n");
                sb.append(result.getReasoningContent());
                sb.append("\n\n");
            }
            
            if (result.hasContent()) {
                sb.append("【图片内容】\n");
                sb.append(result.getContent());
            }
            
            return sb.toString();
        }  catch (Exception e) {
            return "ERROR: 图片识别时发生未知错误: " + e.getMessage();
        }
    }

    @Override
    public Collection<FunctionTool> getSolonTools() {
        return this.getTools();
    }

    @Override
    public String getSystemPrompt() {
        return """
                图片识别工具：支持传入图片 URL（HTTP/HTTPS 或 Base64 Data URI），返回图片识别结果。
                参数: imageUrl(必填, 图片URL), prompt(可选, 指导识别的提示词)。
                返回: 包含思考块和内容块的识别结果。
                需要在 config.json 中配置 vision 部分（baseUrl, apiKey, model）。
                """;
    }
}
