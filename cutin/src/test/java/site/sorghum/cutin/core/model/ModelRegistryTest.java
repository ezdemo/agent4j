package site.sorghum.cutin.core.model;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.cutin.core.context.Message;
import site.sorghum.cutin.core.context.Usage;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ModelRegistry 单元测试：候选池语义 + 能力实时匹配。
 */
class ModelRegistryTest {

    /** 能力声明在查询时实时读取：Provider 运行时切换模型后无需重新注册即可命中。 */
    @Test
    void matchesCapabilitiesAtQueryTime() {
        MutableProvider provider = new MutableProvider("model-a");
        ModelRegistry registry = new ModelRegistry();
        registry.register(provider);

        assertSame(provider, registry.resolve("model-a"));
        assertThrows(IllegalArgumentException.class, () -> registry.resolve("model-b"));

        provider.setModel("model-b"); // 模拟 LoopraModelProvider.setModel 热更新

        assertSame(provider, registry.resolve("model-b"));
        assertThrows(IllegalArgumentException.class, () -> registry.resolve("model-a"));
    }

    /** 同一模型 id 注册多个 Provider 时按注册顺序返回，用于故障切换。 */
    @Test
    void keepsRegistrationOrderForFallback() {
        MutableProvider first = new MutableProvider("shared");
        MutableProvider second = new MutableProvider("shared");
        ModelRegistry registry = new ModelRegistry();
        registry.register(first);
        registry.register(second);

        assertEquals(List.of(first, second), registry.providers("shared"));
        assertSame(first, registry.find("shared").orElseThrow());
    }

    /** unregister 按实例身份移除，不影响同一模型 id 的其他 Provider。 */
    @Test
    void unregisterRemovesOnlyThatInstance() {
        MutableProvider first = new MutableProvider("shared");
        MutableProvider second = new MutableProvider("shared");
        ModelRegistry registry = new ModelRegistry();
        registry.register(first);
        registry.register(second);

        registry.unregister(first);

        assertEquals(List.of(second), registry.providers("shared"));
        assertTrue(registry.find("shared").isPresent());

        registry.unregister(second);

        assertTrue(registry.find("shared").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> registry.resolve("shared"));
    }

    /** 多模型 Provider：注册一次即可命中声明的全部模型 id。 */
    @Test
    void providerWithMultipleModelsMatchesAll() {
        MutableProvider provider = new MutableProvider(Set.of("model-a", "model-b"));
        ModelRegistry registry = new ModelRegistry();
        registry.register(provider);

        assertSame(provider, registry.resolve("model-a"));
        assertSame(provider, registry.resolve("model-b"));
    }

    /** 模拟 LoopraModelProvider 的测试替身：能力随当前模型动态变化。 */
    static final class MutableProvider implements ModelProvider {

        private volatile Set<String> models;

        MutableProvider(String model) {
            this(Set.of(model));
        }

        MutableProvider(Set<String> models) {
            this.models = models;
        }

        void setModel(String model) {
            this.models = Set.of(model);
        }

        @Override
        public String id() {
            return "mutable";
        }

        @Override
        public ModelResponse call(ModelCallRequest request) {
            return ModelResponse.of(new Message("assistant", "ok"), new Usage(1, 1, 0));
        }

        @Override
        public Stream<StreamChunk> stream(ModelCallRequest request) {
            return Stream.of(new StreamChunk("ok", Usage.ZERO));
        }

        @Override
        public ModelCapabilities capabilities() {
            return new ModelCapabilities(models, true, true);
        }

        @Override
        public ONode buildBody(ModelCallRequest request, boolean stream) {
            return null;
        }
    }
}
