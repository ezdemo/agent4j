package site.sorghum.agent4j.web.model.meta;

import java.util.List;

/**
 * 模型支持的输入输出模态。
 * <p>
 * JSON 结构示例：
 * <pre>
 * {
 *   "input": ["text", "image"],
 *   "output": ["text"]
 * }
 * </pre>
 * </p>
 *
 * @param input  支持的输入模态列表（如 "text", "image", "audio", "video", "pdf"）
 * @param output 支持的输出模态列表（如 "text", "image", "audio"）
 */
public record Modalities(
        List<String> input,
        List<String> output
) {
    /**
     * 检查是否支持指定的输入模态。
     *
     * @param modality 模态名称（如 "text", "image"）
     * @return true 表示支持，false 表示不支持
     */
    public boolean supportsInput(String modality) {
        return input.contains(modality);
    }

    /**
     * 检查是否支持指定的输出模态。
     *
     * @param modality 模态名称（如 "text", "image"）
     * @return true 表示支持，false 表示不支持
     */
    public boolean supportsOutput(String modality) {
        return output.contains(modality);
    }

    /**
     * 检查是否支持图像输入。
     *
     * @return true 表示支持图像输入
     */
    public boolean supportsImageInput() {
        return supportsInput("image");
    }

    /**
     * 检查是否支持图像输出。
     *
     * @return true 表示支持图像输出
     */
    public boolean supportsImageOutput() {
        return supportsOutput("image");
    }
}