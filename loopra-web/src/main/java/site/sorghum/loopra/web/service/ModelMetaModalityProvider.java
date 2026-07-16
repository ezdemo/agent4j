package site.sorghum.loopra.web.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import site.sorghum.loopra.bin.model.ModalitySupport;
import site.sorghum.loopra.bin.model.ModelContextUtils;
import site.sorghum.loopra.bin.model.ModelModalityProvider;
import site.sorghum.loopra.web.model.meta.Modalities;
import site.sorghum.loopra.web.model.meta.Model;

import java.util.List;

/**
 * 基于 ModelMetaService 的模型多模态支持提供者。
 * <p>
 * 从模型元数据中获取模型的多模态支持信息，
 * 判断模型是否支持图像、音频、视频等输入输出。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class ModelMetaModalityProvider implements ModelModalityProvider {

    @Inject
    private ModelMetaService modelMetaService;

    /**
     * 全局多模态提供者实例
     */
    @Getter
    private static volatile ModelModalityProvider instance;

    /**
     * 初始化方法，在 Solon 容器启动后自动执行。
     */
    @Init
    public void init() {
        instance = this;
        log.info("[model-meta] 已注册 ModelMetaModalityProvider 为全局多模态提供者");
    }

    @Override
    public ModalitySupport _getModalitySupport(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return null;
        }

        // 首先尝试直接匹配（如 "openai/gpt-5"）
        Model model = modelMetaService.findModelById(modelName);
        if (model == null) {
            // 如果直接匹配失败，尝试去除后缀再匹配
            String strippedName = stripContextSizeSuffix(modelName);
            if (!strippedName.equals(modelName)) {
                model = modelMetaService.findModelById(strippedName);
            }
        }

        if (model == null) {
            log.debug("[model-meta] 未在元数据中找到模型 '{}' 的多模态信息", modelName);
            return null;
        }

        Modalities modalities = model.modalities();
        if (modalities == null) {
            log.debug("[model-meta] 模型 '{}' 没有多模态信息，默认为纯文本", modelName);
            return ModalitySupport.TEXT_ONLY;
        }

        List<String> input = modalities.input();
        List<String> output = modalities.output();

        boolean imageInput = input.contains("image");
        boolean imageOutput = output.contains("image");
        boolean audioInput = input.contains("audio");
        boolean audioOutput = output.contains("audio");
        boolean videoInput = input.contains("video");
        boolean videoOutput = output.contains("video");
        boolean pdfInput = input.contains("pdf");
        boolean textInput = input.contains("text");
        boolean textOutput = output.contains("text");

        ModalitySupport support = new ModalitySupport(
                imageInput, imageOutput,
                audioInput, audioOutput,
                videoInput, videoOutput,
                pdfInput,
                textInput, textOutput
        );

        log.debug("[model-meta] 从元数据获取模型 '{}' 的多模态支持: 输入={}, 输出={}",
                modelName, support.getInputDescription(), support.getOutputDescription());

        return support;
    }

    /**
     * 剥离模型名称中的上下文大小后缀。
     * 例如："mimo-v2.5[512k]" → "mimo-v2.5"
     *
     * @param modelName 模型名称
     * @return 剥离后缀后的模型名称
     */
    private String stripContextSizeSuffix(String modelName) {
        return ModelContextUtils.stripContextSizeSuffix(modelName);
    }
}