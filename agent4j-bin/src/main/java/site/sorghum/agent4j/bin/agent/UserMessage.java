package site.sorghum.agent4j.bin.agent;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * 用户消息 —— 统一表示文本 + 可选图片的多模态输入。
 * <p>
 * 替代 {@code String message} 作为 Agent 输入载体，向后兼容：
 * <ul>
 *   <li>纯文本消息：{@code UserMessage.of("你好")}</li>
 *   <li>多模态消息：{@code UserMessage.of("描述图片", List.of(url1, url2))}</li>
 * </ul>
 * </p>
 *
 * @author Sorghum
 */
public class UserMessage {

    /**
     * -- GETTER --
     * 文本内容（可能为 null）
     */
    @Getter
    private final String text;
    /**
     * -- GETTER --
     * 图片 URL 列表（不可变，不会为 null）
     */
    @Getter
    private final List<String> images;

    private UserMessage(String text, List<String> images) {
        this.text = text;
        this.images = images != null ? List.copyOf(images) : Collections.emptyList();
    }

    // ==================== 工厂方法 ====================

    /**
     * 纯文本消息
     */
    public static UserMessage of(String text) {
        return new UserMessage(text, null);
    }

    /**
     * 多模态消息（文本 + 图片）
     */
    public static UserMessage of(String text, List<String> images) {
        return new UserMessage(text, images);
    }

    // ==================== 便捷判断 ====================

    /**
     * 是否为纯文本（无图片）
     */
    public boolean isPlainText() {
        return images.isEmpty();
    }

    /**
     * 是否包含图片
     */
    public boolean hasImages() {
        return !images.isEmpty();
    }


    /**
     * 是否包含内容（文本或图片）
     */
    public boolean hasContent() {
        return text != null && !text.isEmpty() && images != null && !images.isEmpty();
    }

    @Override
    public String toString() {
        if (isPlainText()) {
            return text != null ? text : "";
        }
        return text + " [" + images.size() + " images]";
    }
}
