package site.sorghum.agent4j.bin.vision;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.chat.ChatModel;
import org.noear.solon.ai.chat.ChatResponse;
import org.noear.solon.ai.chat.content.ImageBlock;
import org.noear.solon.ai.chat.message.AssistantMessage;
import org.noear.solon.ai.chat.message.ChatMessage;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.config.ConfigService;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * 图片识别服务 —— 使用视觉模型识别图片内容。
 * <p>
 * 支持传入图片（URL 或 Base64），返回思考块和图片内容块。
 * 使用独立的 apiUrl、apiKey、model 配置。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class VisionService {

    @Inject
    private ConfigService configService;

    private volatile ChatModel visionModel;

    /**
     * 识别图片内容。
     *
     * @param imageUrl 图片 URL（支持 HTTP/HTTPS 或 Base64 Data URI）
     * @return 图片识别结果，包含思考块和内容块
     */
    public VisionResult recognize(String imageUrl) {
        return recognize(imageUrl, null);
    }

    /**
     * 识别图片内容。
     *
     * @param imageBase64 图片 URL（支持 HTTP/HTTPS 或 Base64 Data URI）
     * @param prompt   可选的提示词，用于指导图片识别
     * @return 图片识别结果，包含思考块和内容块
     */
    public VisionResult recognize(String imageBase64, String prompt){
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new IllegalArgumentException("图片 URL 不能为空");
        }

        ChatModel model = getModel();
        if (model == null) {
            throw new IllegalStateException("图片识别服务未配置，请在 config.json 中配置 vision 部分");
        }

        // 构建用户消息（包含图片）
        String userText = prompt != null && !prompt.isBlank() ? prompt : "描述图片所有内容 提供给别的模型使用。";
        
        // 使用 Solon AI 的多模态支持
        ChatMessage userMessage = ChatMessage.ofUser(userText, ImageBlock.ofUrl(imageBase64));
        
        // 调用 API
        try {
            ChatResponse response = model.prompt(userMessage).call();
            
            // 获取响应内容
            AssistantMessage message = response.getMessage();
            if (message == null) {
                log.error("[vision] API 返回空响应，请检查视觉模型配置和图片格式，图片: {}",
                        imageBase64.length() > 100 ? imageBase64.substring(0, 100) + "..." : imageBase64);
                throw new IllegalStateException("图片识别失败: API 返回空响应，请检查视觉模型配置和图片格式");
            }
            String content = message.getContent();
            
            // 获取思考内容
            String reasoningContent = message.getReasoning();
            if (reasoningContent != null && reasoningContent.isBlank()) {
                reasoningContent = null;
            }
            
            VisionResult result = new VisionResult();
            result.setReasoningContent(reasoningContent);
            result.setContent(content);
            result.setImageUrl(imageBase64);
            
            log.info("[vision] 图片识别完成，图片: {}, 内容长度: {}", 
                    imageBase64.length() > 50 ? imageBase64.substring(0, 50) + "..." : imageBase64,
                    content != null ? content.length() : 0);
            
            return result;
        } catch (Exception e) {
            log.error("[vision] 图片识别失败: {}", e.getMessage(), e);
            throw new IllegalStateException("图片识别失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取或创建视觉模型。
     */
    private ChatModel getModel() {
        if (visionModel == null) {
            synchronized (this) {
                if (visionModel == null) {
                    String baseUrl = configService.getVisionBaseUrl();
                    String apiKey = configService.getVisionApiKey();
                    String model = configService.getVisionModel();

                    if (baseUrl == null || baseUrl.isBlank() || 
                        apiKey == null || apiKey.isBlank() || 
                        model == null || model.isBlank()) {
                        log.warn("[vision] 图片识别服务配置不完整，请检查 config.json 中的 vision 部分");
                        return null;
                    }

                    // 如果 baseUrl 已经包含完整的 API 路径，则直接使用
                    // 否则自动拼接 /chat/completions
                    String apiUrl;
                    if (baseUrl.contains("/chat/completions")) {
                        apiUrl = baseUrl;
                    } else if (baseUrl.endsWith("/")) {
                        apiUrl = baseUrl + "chat/completions";
                    } else {
                        apiUrl = baseUrl + "/chat/completions";
                    }

                    visionModel = ChatModel.of(apiUrl)
                            .apiKey(apiKey)
                            .model(model)
                            .timeout(Duration.of(10 * 60, ChronoUnit.SECONDS))
                            .modelOptions(
                                    modelOptions -> modelOptions.optionSet("enable_thinking",false)
                            )
                            .build();
                    
                    log.info("[vision] 已创建图片识别模型，模型: {}, API: {}", model, apiUrl);
                }
            }
        }
        return visionModel;
    }

    /**
     * 重新加载配置（配置变更时调用）。
     */
    public void reloadConfig() {
        synchronized (this) {
            visionModel = null;
            log.info("[vision] 已清除图片识别模型缓存，下次调用将重新创建");
        }
    }

    /**
     * 图片识别结果。
     */
    @Data
    public static class VisionResult {
        /**
         * 思考块内容（模型推理过程）。
         */
        private String reasoningContent;

        /**
         * 图片内容描述。
         */
        private String content;

        /**
         * 原始图片 URL。
         */
        private String imageUrl;

        /**
         * 是否包含思考内容。
         */
        public boolean hasReasoning() {
            return reasoningContent != null && !reasoningContent.isBlank();
        }

        /**
         * 是否包含内容。
         */
        public boolean hasContent() {
            return content != null && !content.isBlank();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (hasReasoning()) {
                sb.append("【思考】\n").append(reasoningContent).append("\n\n");
            }
            if (hasContent()) {
                sb.append("【内容】\n").append(content);
            }
            return sb.toString();
        }
    }
}
