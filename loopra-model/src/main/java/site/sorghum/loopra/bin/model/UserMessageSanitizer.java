package site.sorghum.loopra.bin.model;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.loopra.bin.agent.model.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户消息清洗器 —— 根据模型的多模态支持情况清洗用户消息。
 * <p>
 * 主要职责：
 * <ul>
     *   <li>当模型不支持图片输入时，移除图片</li>
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

    /**
     * 清洗用户消息，根据模型的多模态支持情况移除不支持的内容。
     *
     * @param userMessage  原始用户消息
     * @param modelName  模型名称
     * @return 清洗后的用户消息，如果无需清洗则返回原消息
     */
    public static UserMessage sanitize(UserMessage userMessage, String modelName) {
        return sanitize(userMessage, modelName, null);
    }

    /**
     * 根据指定渠道内模型的能力清洗消息。
     */
    public static UserMessage sanitize(UserMessage userMessage, String modelName, String channelId) {
        if (userMessage == null || userMessage.isPlainText()) {
            return userMessage;
        }

        // 获取当前模型名称
        if (modelName == null || modelName.isEmpty()) {
            log.warn("[sanitizer] 无法获取当前模型名称，跳过消息清洗");
            return userMessage;
        }

        // 获取模型的多模态支持信息
        ModalitySupport modalitySupport = modalityProvider.getModalitySupport(channelId, modelName);
        if (modalitySupport == null) {
            log.debug("[sanitizer] 无法获取模型 '{}' 的多模态支持信息，跳过消息清洗", modelName);
            return userMessage;
        }

        // 模型不支持图片输入时，移除图片。
        List<String> images = userMessage.getImages();
        if (!images.isEmpty() && !modalitySupport.imageInput()) {
            log.info("[sanitizer] 模型 '{}' 不支持图片输入，移除 {} 张图片", modelName, images.size());
            return UserMessage.of(userMessage.getText());
        }

        // 未来可以扩展：清洗音频、视频等

        return userMessage;
    }

    /**
     * 使用实际发送请求的客户端确定模型和渠道，避免会话模型与全局配置串用。
     */
    public static UserMessage sanitize(UserMessage userMessage, ModelClient modelClient) {
        if (modelClient == null) return userMessage;
        return sanitize(userMessage, modelClient.getModel(), modelClient.getModelChannelId());
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
            sanitized.add(sanitize(msg, modelClient));
        }
        return sanitized;
    }
}
