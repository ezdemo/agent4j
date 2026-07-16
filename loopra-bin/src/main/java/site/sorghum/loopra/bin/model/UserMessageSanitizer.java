package site.sorghum.loopra.bin.model;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.loopra.bin.agent.model.UserMessage;
import site.sorghum.loopra.bin.vision.VisionService;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户消息清洗器 —— 根据模型的多模态支持情况清洗用户消息。
 * <p>
 * 主要职责：
 * <ul>
 *   <li>当模型不支持图片输入时，自动调用 VisionService 识别图片并拼接到文本中</li>
 *   <li>当模型不支持音频输入时，移除用户消息中的音频（预留）</li>
 *   <li>当模型不支持视频输入时，移除用户消息中的视频（预留）</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class UserMessageSanitizer {
    @Inject
    public static ModelModalityProvider modalityProvider;

    @Inject
    public static VisionService visionService;

    /**
     * 清洗用户消息，根据模型的多模态支持情况移除不支持的内容。
     *
     * @param userMessage  原始用户消息
     * @param modelName  模型名称
     * @return 清洗后的用户消息，如果无需清洗则返回原消息
     */
    public static UserMessage sanitize(UserMessage userMessage, String modelName) {
        if (userMessage == null || userMessage.isPlainText()) {
            return userMessage;
        }

        // 获取当前模型名称
        if (modelName == null || modelName.isEmpty()) {
            log.warn("[sanitizer] 无法获取当前模型名称，跳过消息清洗");
            return userMessage;
        }

        // 获取模型的多模态支持信息
        ModalitySupport modalitySupport = modalityProvider.getModalitySupport(modelName);
        if (modalitySupport == null) {
            log.debug("[sanitizer] 无法获取模型 '{}' 的多模态支持信息，跳过消息清洗", modelName);
            return userMessage;
        }

        // 清洗图片：如果模型不支持图片输入，则调用 VisionService 识别图片
        List<String> images = userMessage.getImages();
        if (!images.isEmpty() && !modalitySupport.imageInput()) {
            log.info("[sanitizer] 模型 '{}' 不支持图片输入，尝试使用 VisionService 识别 {} 张图片", 
                    modelName, images.size());
            
            // 调用 VisionService 识别图片
            List<String> imageDescriptions = recognizeImages(images);
            
            if (!imageDescriptions.isEmpty()) {
                // 将图片识别结果拼接到用户文本中
                String originalText = userMessage.getText();
                StringBuilder combinedText = new StringBuilder();
                
                if (originalText != null && !originalText.isEmpty()) {
                    combinedText.append(originalText);
                    combinedText.append("\n\n");
                }
                
                combinedText.append("【图片内容】\n");
                for (int i = 0; i < imageDescriptions.size(); i++) {
                    if (i > 0) {
                        combinedText.append("\n");
                    }
                    combinedText.append("图片 ").append(i + 1).append(": ").append(imageDescriptions.get(i));
                }
                
                log.info("[sanitizer] 已将 {} 张图片的识别结果拼接到用户消息中", imageDescriptions.size());
                return UserMessage.of(combinedText.toString());
            } else {
                // VisionService 识别失败，回退到移除图片
                log.warn("[sanitizer] VisionService 识别图片失败，移除图片");
                return UserMessage.of(userMessage.getText());
            }
        }

        // 未来可以扩展：清洗音频、视频等

        return userMessage;
    }

    /**
     * 调用 VisionService 识别图片列表。
     *
     * @param images 图片 URL 列表
     * @return 图片识别结果列表
     */
    private static List<String> recognizeImages(List<String> images) {
        List<String> descriptions = new ArrayList<>();
        
        if (visionService == null) {
            log.warn("[sanitizer] VisionService 未注入，无法识别图片");
            return descriptions;
        }
        
        for (String imageUrl : images) {
            try {
                VisionService.VisionResult result = visionService.recognize(imageUrl);
                if (result.hasContent()) {
                    descriptions.add(result.getContent());
                } else {
                    descriptions.add("无法识别图片内容");
                }
            } catch (Exception e) {
                log.error("[sanitizer] 识别图片失败: {}", e.getMessage());
                descriptions.add("图片识别失败: " + e.getMessage());
            }
        }
        
        return descriptions;
    }

    /**
     * 批量清洗用户消息列表。
     *
     * @param userMessages 用户消息列表
     * @param modelClient  模型客户端
     * @return 清洗后的用户消息列表
     */
    public static List<UserMessage> sanitize(List<UserMessage> userMessages, ModelClient modelClient) {
        if (userMessages == null || userMessages.isEmpty()) {
            return userMessages;
        }

        List<UserMessage> sanitized = new ArrayList<>(userMessages.size());
        for (UserMessage msg : userMessages) {
            sanitized.add(sanitize(msg, modelClient.getModel()));
        }
        return sanitized;
    }
}
