package site.sorghum.cutin.core.loop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoopProgramPrettyPrintTest {

    @Test
    void prettyPrintShowsStartNodesTypesAndEdges() {
        LoopProgram program = LoopProgram.builder("agent")
            .node("prepare", NodeType.CODE, "准备输入", ignored -> StepResult.Continue.INSTANCE)
            .node("model", NodeType.MODEL, "调用模型", ignored -> StepResult.Continue.INSTANCE)
            .node("output", NodeType.OUTPUT, "结束", Steps.finish())
            .next("prepare", "model", "准备完成")
            .next("model", "output", "模型完成")
            .start("prepare")
            .build();

        assertEquals("""
            LoopProgram[agent] start=prepare
            * prepare [CODE] - 准备输入 -> model {准备完成}
              model [MODEL] - 调用模型 -> output {模型完成}
              output [OUTPUT] - 结束 -> <end>
            """, program.prettyPrint());
    }
}
