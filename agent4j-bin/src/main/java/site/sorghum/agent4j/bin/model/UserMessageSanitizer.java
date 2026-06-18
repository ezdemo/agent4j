package site.sorghum.agent4j.bin.model;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.agent.model.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户消息清洗器 —— 根据模型的多模态支持情况清洗用户消息。
 * <p>
 * 主要职责：
 * <ul>
 *   <li>当模型不支持图片输入时，移除用户消息中的图片</li>
 *   <li>当模型不支持音频输入时，移除用户消息中的音频（预留）</li>
 *   <li>当模型不支持视频输入时，移除用户消息中的视频（预留）</li>
 *   <li>为未来对接第三方图片解析工具预留扩展点</li>
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

        // 清洗图片：如果模型不支持图片输入，则移除图片
        List<String> images = userMessage.getImages();
        if (!images.isEmpty() && !modalitySupport.imageInput()) {
            log.info("[sanitizer] 模型 '{}' 不支持图片输入，移除 {} 张图片", modelName, images.size());
            // 为未来对接第三方图片解析工具预留：
            // 这里可以调用图片解析工具，将图片转换为文本描述
            // List<String> parsedTexts = ImageParser.parse(images);
            // String combinedText = combineTextAndParsed(userMessage.getText(), parsedTexts);
            // return UserMessage.of(combinedText);
            return UserMessage.of(userMessage.getText());
        }

        // 未来可以扩展：清洗音频、视频等

        return userMessage;
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