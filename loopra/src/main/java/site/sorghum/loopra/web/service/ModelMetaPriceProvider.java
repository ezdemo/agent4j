package site.sorghum.loopra.web.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import site.sorghum.loopra.bin.model.ModelContextUtils;
import site.sorghum.loopra.bin.model.ModelPriceProvider;
import site.sorghum.loopra.web.model.meta.Cost;
import site.sorghum.loopra.web.model.meta.Model;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于 ModelMetaService 的模型价格提供者。
 * <p>
 * 从模型元数据中获取模型的价格信息，
 * 并将美元价格转换为人民币（乘以 7.3）。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class ModelMetaPriceProvider implements ModelPriceProvider {

    /**
     * 美元兑人民币汇率
     */
    private static final double USD_TO_CNY_RATE = 7.0;

    @Inject
    private ModelMetaService modelMetaService;

    /**
     * 全局价格提供者实例（供 UsageCostCalculator 等使用）
     */
    @Getter
    private static volatile ModelPriceProvider instance;

    /**
     * 初始化方法，在 Solon 容器启动后自动执行。
     */
    @Init
    public void init() {
        instance = this;
        log.info("[model-meta] 已注册 ModelMetaPriceProvider 为全局价格提供者");
    }

    @Override
    public Map<String, Double> _getModelPrice(String modelName) {
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

        if (model == null || model.cost() == null) {
            log.debug("[model-meta] 未在元数据中找到模型 '{}' 的价格信息", modelName);
            return null;
        }

        Cost cost = model.cost();
        Map<String, Double> priceMap = new HashMap<>();

        // 将美元价格转换为人民币（乘以汇率）
        double inputPrice = cost.input() * USD_TO_CNY_RATE + 0.00005;
        double outputPrice = cost.output() * USD_TO_CNY_RATE + 0.00005;
        double cacheReadPrice = cost.cache_read() * USD_TO_CNY_RATE + 0.00005;
        // 保留3位小数
        inputPrice = Math.round(inputPrice * 1000) / 1000.0;
        outputPrice = Math.round(outputPrice * 1000) / 1000.0;
        cacheReadPrice = Math.round(cacheReadPrice * 1000) / 1000.0;
        priceMap.put("input", inputPrice);
        priceMap.put("output", outputPrice);
        priceMap.put("cache", cacheReadPrice);

        log.debug("[model-meta] 从元数据获取模型 '{}' 的价格（美元→人民币，汇率={}）: input={}, output={}, cache={}",
                modelName, USD_TO_CNY_RATE, cost.input(), cost.output(), cost.cache_read());

        return priceMap;
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
