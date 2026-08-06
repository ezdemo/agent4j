package site.sorghum.loopra.bin.agent;

import org.junit.jupiter.api.Test;
import org.noear.snack4.ONode;
import site.sorghum.loopra.bin.agent.context.ContextTokenEstimate;
import site.sorghum.loopra.bin.agent.context.ContextTokenEstimator;
import site.sorghum.loopra.bin.agent.model.LoopraChatMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextTokenEstimatorTest {

    @Test
    void usesBundledDeepSeekV3TokenizerAndSeparatesContextParts() {
        List<LoopraChatMessage> messages = List.of(
                LoopraChatMessage.ofSystem("You are a helpful coding assistant."),
                LoopraChatMessage.ofUser("请统计这个请求的 token。"),
                LoopraChatMessage.assistant("我会先检查上下文。", null, "Thinking about the request."),
                LoopraChatMessage.tool("call-1", "tool output")
        );
        ONode tools = ONode.ofJson("[{\"type\":\"function\",\"function\":{\"name\":\"read\",\"parameters\":{}}}]");

        ContextTokenEstimate estimate = ContextTokenEstimator.estimate(messages, tools, null);

        assertTrue(estimate.exactTokenizer());
        assertTrue(estimate.systemTokens() > 0);
        assertTrue(estimate.toolDefinitionTokens() > 0);
        assertTrue(estimate.userTokens() > 0);
        assertTrue(estimate.assistantTokens() > 0);
        assertTrue(estimate.toolResultTokens() > 0);
        assertEquals(estimate.systemTokens() + estimate.toolDefinitionTokens() + estimate.userTokens()
                        + estimate.assistantTokens() + estimate.toolResultTokens(),
                estimate.totalTokens());
    }

    @Test
    void usesTheSameTokenizerForEveryModel() {
        ContextTokenEstimate estimate = ContextTokenEstimator.estimate(
                List.of(LoopraChatMessage.ofUser("hello")), null, null);

        assertTrue(estimate.exactTokenizer());
        assertEquals("deepseek-v3-tokenizer", estimate.estimator());
    }

    @Test
    void doesNotTruncateLongContextDuringEstimation() {
        String longContent = "token ".repeat(2_000);

        ContextTokenEstimate estimate = ContextTokenEstimator.estimate(
                List.of(LoopraChatMessage.ofUser(longContent)), null, null);

        assertTrue(estimate.userTokens() > 512);
    }

    @Test
    void ignoresLocalOnlyTimestampAndSnapshotMetadata() {
        LoopraChatMessage withMetadata = LoopraChatMessage.ofUser("same request content");
        withMetadata.setSnapshotId("snapshot-123");
        withMetadata.setTimestamp(1_753_000_000_000L);
        LoopraChatMessage withoutMetadata = LoopraChatMessage.ofUser("same request content");
        withoutMetadata.setTimestamp(null);

        ContextTokenEstimate withMetadataEstimate = ContextTokenEstimator.estimate(List.of(withMetadata), null, null);
        ContextTokenEstimate withoutMetadataEstimate = ContextTokenEstimator.estimate(List.of(withoutMetadata), null, null);

        assertEquals(withoutMetadataEstimate.userTokens(), withMetadataEstimate.userTokens());
    }
}
