package site.sorghum.loopra.bin.agent.context;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import lombok.extern.slf4j.Slf4j;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;
import site.sorghum.loopra.bin.agent.model.ToolCallEntry;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 离线估算单个请求的上下文构成。
 * <p>所有模型统一使用随包携带的 DeepSeek V3 tokenizer.json，便于横向比较上下文构成。</p>
 */
@Slf4j
public final class ContextTokenEstimator {
    private static final String DEEPSEEK_V3_RESOURCE = "/tokenizers/deepseek-v3/tokenizer.json";
    private static volatile HuggingFaceTokenizer deepSeekV3Tokenizer;
    private static volatile boolean tokenizerLoadAttempted;

    private ContextTokenEstimator() {
    }

    public static ContextTokenEstimate estimate(List<LoopraChatMessage> messages, ONode tools,
                                                String additionalSystemText) {
        boolean exact = tokenizer() != null;
        int system = 0;
        int user = 0;
        int assistant = 0;
        int toolResult = 0;
        boolean appendedSystemText = false;

        if (messages != null) {
            for (LoopraChatMessage message : messages) {
                LoopraChatMessage messageToCount = message;
                if (!appendedSystemText && additionalSystemText != null && !additionalSystemText.isEmpty()
                        && message != null && message.isSystem() && message.getContentParts() == null) {
                    messageToCount = message.copy();
                    String content = message.getContent() != null ? message.getContent() : "";
                    messageToCount.setContent(content + "\n\n" + additionalSystemText);
                    appendedSystemText = true;
                }
                int tokens = count(serializedMessage(messageToCount), exact);
                switch (message.getRole()) {
                    case "system" -> system += tokens;
                    case "user" -> user += tokens;
                    case "assistant" -> assistant += tokens;
                    case "tool" -> toolResult += tokens;
                    default -> assistant += tokens;
                }
            }
        }
        if (!appendedSystemText) system += count(additionalSystemText, exact);
        int toolDefinitions = tools == null ? 0 : count(tools.toJson(), exact);
        int total = system + toolDefinitions + user + assistant + toolResult;
        return new ContextTokenEstimate(system, toolDefinitions, user, assistant, toolResult,
                total, exact, exact ? "deepseek-v3-tokenizer" : "chars/2");
    }

    private static String serializedMessage(LoopraChatMessage message) {
        if (message == null) return "";
        if (message.isTool() && (message.getToolCallId() == null || message.getToolCallId().isEmpty())) {
            return "";
        }

        // 与 HttpModelClient.buildBody() 保持字段一致，排除仅用于本地持久化的 timestamp/snapshot_id。
        ONode payload = ONode.ofJson("{}");
        payload.set("role", message.getRole());
        boolean hasContentParts = message.getContentParts() != null && !message.getContentParts().isEmpty();
        boolean hasContent = message.hasContent();
        boolean hasToolCalls = message.hasToolCalls();
        boolean hasReasoning = message.hasReasoningContent();

        if (message.isUser() && !hasContent && !hasContentParts && !hasToolCalls && !hasReasoning) {
            return "";
        }
        if (message.isAssistant() && !hasContent && !hasToolCalls && !hasReasoning) {
            return "";
        }

        if (hasContentParts) {
            ONode parts = payload.getOrNew("content").asArray();
            for (LoopraChatMessage.ContentPart part : message.getContentParts()) {
                ONode partNode = parts.addNew();
                partNode.set("type", part.getType());
                if ("text".equals(part.getType())) {
                    partNode.set("text", part.getText() != null ? part.getText() : "");
                } else if ("image_url".equals(part.getType()) && part.getImageUrl() != null) {
                    ONode imageUrl = partNode.getOrNew("image_url");
                    imageUrl.set("url", part.getImageUrl().getUrl() != null ? part.getImageUrl().getUrl() : "");
                    if (part.getImageUrl().getDetail() != null) imageUrl.set("detail", part.getImageUrl().getDetail());
                }
            }
        } else if (hasContent) {
            payload.set("content", message.getContent());
        }

        if (hasToolCalls) {
            ONode calls = payload.getOrNew("tool_calls").asArray();
            for (ToolCallEntry call : message.getToolCalls()) {
                ONode callNode = calls.addNew();
                callNode.set("id", call.id());
                callNode.set("type", "function");
                ONode function = callNode.getOrNew("function");
                function.set("name", call.name());
                Object args = call.arguments();
                function.set("arguments", args instanceof String ? args : ONode.serialize(args != null ? args : Map.of()));
            }
        }
        if (hasReasoning) payload.set("reasoning_content", message.getReasoningContent());
        if (message.getToolCallId() != null) payload.set("tool_call_id", message.getToolCallId());
        if (!hasContent) payload.set("content", "");
        return payload.toJson();
    }

    private static int count(String value, boolean exact) {
        if (value == null || value.isEmpty()) return 0;
        if (exact) {
            return tokenizer().encode(value, false, false).getIds().length;
        }
        return (value.length() + 1) / 2;
    }

    private static HuggingFaceTokenizer tokenizer() {
        HuggingFaceTokenizer current = deepSeekV3Tokenizer;
        if (current != null || tokenizerLoadAttempted) return current;
        synchronized (ContextTokenEstimator.class) {
            if (deepSeekV3Tokenizer != null || tokenizerLoadAttempted) return deepSeekV3Tokenizer;
            tokenizerLoadAttempted = true;
            try (InputStream input = ContextTokenEstimator.class.getResourceAsStream(DEEPSEEK_V3_RESOURCE)) {
                if (input == null) {
                    log.warn("[tokenizer] 未找到 DeepSeek V3 tokenizer 资源: {}", DEEPSEEK_V3_RESOURCE);
                    return null;
                }
                // DJL 默认启用 LONGEST_FIRST，并因 tokenizer.json 不含 model_max_length 而截断到 512。
                // 上下文估算必须遍历完整输入，因此显式关闭截断。
                deepSeekV3Tokenizer = HuggingFaceTokenizer.newInstance(input,
                        Map.of("truncation", "DO_NOT_TRUNCATE", "addSpecialTokens", "false"));
                return deepSeekV3Tokenizer;
            } catch (IOException | RuntimeException e) {
                log.warn("[tokenizer] DeepSeek V3 离线 tokenizer 初始化失败，将回退字符估算: {}", e.getMessage());
                return null;
            }
        }
    }
}
