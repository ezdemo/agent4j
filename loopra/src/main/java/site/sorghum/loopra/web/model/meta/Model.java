package site.sorghum.loopra.web.model.meta;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 模型信息。
 * <p>
 * JSON 结构示例：
 * <pre>
 * {
 *   "id": "xai/grok-4",
 *   "name": "Grok 4",
 *   "family": "grok",
 *   "attachment": true,
 *   "reasoning": true,
 *   "tool_call": true,
 *   "temperature": true,
 *   "knowledge": "2025-01",
 *   "release_date": "2025-09-09",
 *   "last_updated": "2025-09-09",
 *   "modalities": { ... },
 *   "open_weights": false,
 *   "limit": { ... },
 *   "cost": { ... }
 * }
 * </pre>
 * </p>
 *
 * @param id            模型唯一标识符（如 "xai/grok-4"）
 * @param name          模型显示名称
 * @param family        模型系列（如 "grok", "gemini-pro", "gpt"）
 * @param attachment    是否支持附件
 * @param reasoning     是否具有推理能力
 * @param tool_call     是否支持工具调用
 * @param temperature   是否支持温度参数
 * @param knowledge     知识截止日期（格式："YYYY-MM" 或 "YYYY-MM-DD"）
 * @param release_date  发布日期（格式："YYYY-MM-DD"）
 * @param last_updated  最后更新日期（格式："YYYY-MM-DD"）
 * @param modalities    支持的输入输出模态
 * @param open_weights  是否为开源模型
 * @param limit         上下文窗口和输出长度限制
 * @param cost          定价信息
 */
public record Model(
        String id,
        String name,
        String family,
        boolean attachment,
        boolean reasoning,
        boolean tool_call,
        boolean temperature,
        String knowledge,
        String release_date,
        String last_updated,
        Modalities modalities,
        boolean open_weights,
        Limit limit,
        Cost cost
) {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 获取模型 ID 中的提供商部分（如 "xai/grok-4" -> "xai"）。
     *
     * @return 提供商 ID，如果格式不正确则返回空字符串
     */
    public String getProviderId() {
        if (id == null || !id.contains("/")) {
            return "";
        }
        return id.substring(0, id.indexOf('/'));
    }

    /**
     * 获取模型 ID 中的模型名称部分（如 "xai/grok-4" -> "grok-4"）。
     *
     * @return 模型名称，如果格式不正确则返回完整 ID
     */
    public String getModelName() {
        if (id == null || !id.contains("/")) {
            return id;
        }
        return id.substring(id.indexOf('/') + 1);
    }

    /**
     * 解析发布日期为 LocalDate 对象。
     *
     * @return 发布日期，如果解析失败则返回 null
     */
    public LocalDate getReleaseDate() {
        if (release_date == null || release_date.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(release_date, DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析最后更新日期为 LocalDate 对象。
     *
     * @return 最后更新日期，如果解析失败则返回 null
     */
    public LocalDate getLastUpdatedDate() {
        if (last_updated == null || last_updated.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(last_updated, DATE_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查模型是否支持图像输入。
     *
     * @return true 表示支持图像输入
     */
    public boolean supportsImageInput() {
        return modalities != null && modalities.supportsImageInput();
    }

    /**
     * 检查模型是否支持图像输出。
     *
     * @return true 表示支持图像输出
     */
    public boolean supportsImageOutput() {
        return modalities != null && modalities.supportsImageOutput();
    }

    /**
     * 检查模型是否支持音频输入。
     *
     * @return true 表示支持音频输入
     */
    public boolean supportsAudioInput() {
        return modalities != null && modalities.supportsInput("audio");
    }

    /**
     * 检查模型是否支持视频输入。
     *
     * @return true 表示支持视频输入
     */
    public boolean supportsVideoInput() {
        return modalities != null && modalities.supportsInput("video");
    }

    /**
     * 检查模型是否支持 PDF 输入。
     *
     * @return true 表示支持 PDF 输入
     */
    public boolean supportsPdfInput() {
        return modalities != null && modalities.supportsInput("pdf");
    }

    /**
     * 获取上下文窗口大小（token 数量）。
     *
     * @return 上下文窗口大小，如果未定义则返回 0
     */
    public long getContextSize() {
        return limit != null ? limit.context() : 0;
    }

    /**
     * 获取最大输出长度（token 数量）。
     *
     * @return 最大输出长度，如果未定义则返回 0
     */
    public long getOutputSize() {
        return limit != null ? limit.output() : 0;
    }

    /**
     * 获取输入价格（每百万 token）。
     *
     * @return 输入价格，如果未定义则返回 0
     */
    public double getInputPrice() {
        return cost != null ? cost.input() : 0;
    }

    /**
     * 获取输出价格（每百万 token）。
     *
     * @return 输出价格，如果未定义则返回 0
     */
    public double getOutputPrice() {
        return cost != null ? cost.output() : 0;
    }

    /**
     * 计算指定输入 token 数量的成本（美元）。
     *
     * @param tokens token 数量
     * @return 成本（美元）
     */
    public double calculateInputCost(long tokens) {
        return cost != null ? cost.calculateInputCost(tokens) : 0;
    }

    /**
     * 计算指定输出 token 数量的成本（美元）。
     *
     * @param tokens token 数量
     * @return 成本（美元）
     */
    public double calculateOutputCost(long tokens) {
        return cost != null ? cost.calculateOutputCost(tokens) : 0;
    }

    /**
     * 检查模型是否具有推理能力。
     *
     * @return true 表示具有推理能力
     */
    public boolean hasReasoning() {
        return reasoning;
    }

    /**
     * 检查模型是否支持工具调用。
     *
     * @return true 表示支持工具调用
     */
    public boolean supportsToolCall() {
        return tool_call;
    }

    /**
     * 检查模型是否为开源模型。
     *
     * @return true 表示为开源模型
     */
    public boolean isOpenWeights() {
        return open_weights;
    }
}