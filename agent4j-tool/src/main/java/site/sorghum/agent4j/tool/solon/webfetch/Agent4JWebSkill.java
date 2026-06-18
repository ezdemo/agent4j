package site.sorghum.agent4j.tool.solon.webfetch;

import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.talents.web.CodeSearchTalent;
import org.noear.solon.ai.talents.web.WebfetchTalent;
import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.tool.solon.SolonToTools;

import java.util.Collection;
import java.util.stream.Stream;

@Component
public class Agent4JWebSkill implements SolonToTools {

    WebfetchTalent fetchTool = new WebfetchTalent();

    CodeSearchTalent codeSearchTool = new CodeSearchTalent();


    @Override
    public Collection<FunctionTool> getSolonTools() {
        return Stream.of(
                fetchTool,
                codeSearchTool
        ).map(
                it -> it.getTools(null)
        ).flatMap(Collection::stream).toList();
    }
}
