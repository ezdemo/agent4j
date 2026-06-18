package site.sorghum.agent4j.bin.model;

/**
 * 模型多模态支持信息 —— 描述模型支持的输入输出模态。
 *
 * @param imageInput   是否支持图片输入
 * @param imageOutput  是否支持图片输出
 * @param audioInput   是否支持音频输入
 * @param audioOutput  是否支持音频输出
 * @param videoInput   是否支持视频输入
 * @param videoOutput  是否支持视频输出
 * @param pdfInput     是否支持 PDF 输入
 * @param textInput    是否支持文本输入
 * @param textOutput   是否支持文本输出
 */
public record ModalitySupport(
        boolean imageInput,
        boolean imageOutput,
        boolean audioInput,
        boolean audioOutput,
        boolean videoInput,
        boolean videoOutput,
        boolean pdfInput,
        boolean textInput,
        boolean textOutput
) {
    /**
     * 默认的纯文本模型支持。
     */
    public static final ModalitySupport TEXT_ONLY = new ModalitySupport(
            false, false, false, false, false, false, false, true, true
    );

    /**
     * 是否支持任何图像能力（输入或输出）。
     */
    public boolean hasImageCapability() {
        return imageInput || imageOutput;
    }

    /**
     * 是否支持任何音频能力（输入或输出）。
     */
    public boolean hasAudioCapability() {
        return audioInput || audioOutput;
    }

    /**
     * 是否支持任何视频能力（输入或输出）。
     */
    public boolean hasVideoCapability() {
        return videoInput || videoOutput;
    }

    /**
     * 是否支持多模态输入（图像、音频、视频、PDF 任意一种）。
     */
    public boolean hasMultimodalInput() {
        return imageInput || audioInput || videoInput || pdfInput;
    }

    /**
     * 是否支持多模态输出（图像、音频、视频任意一种）。
     */
    public boolean hasMultimodalOutput() {
        return imageOutput || audioOutput || videoOutput;
    }

    /**
     * 获取支持的输入模态描述字符串。
     */
    public String getInputDescription() {
        StringBuilder sb = new StringBuilder();
        if (textInput) sb.append("文本");
        if (imageInput) sb.append(sb.isEmpty() ? "图像" : ", 图像");
        if (audioInput) sb.append(sb.isEmpty() ? "音频" : ", 音频");
        if (videoInput) sb.append(sb.isEmpty() ? "视频" : ", 视频");
        if (pdfInput) sb.append(sb.isEmpty() ? "PDF" : ", PDF");
        return sb.isEmpty() ? "无" : sb.toString();
    }

    /**
     * 获取支持的输出模态描述字符串。
     */
    public String getOutputDescription() {
        StringBuilder sb = new StringBuilder();
        if (textOutput) sb.append("文本");
        if (imageOutput) sb.append(sb.isEmpty() ? "图像" : ", 图像");
        if (audioOutput) sb.append(sb.isEmpty() ? "音频" : ", 音频");
        if (videoOutput) sb.append(sb.isEmpty() ? "视频" : ", 视频");
        return sb.isEmpty() ? "无" : sb.toString();
    }
}