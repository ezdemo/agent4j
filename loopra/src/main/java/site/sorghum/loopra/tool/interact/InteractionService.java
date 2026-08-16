package site.sorghum.loopra.tool.interact;

import org.noear.solon.annotation.Component;
import site.sorghum.loopra.tool.ErrorMessages;

import java.util.List;
import java.util.Map;

/**
 * 交互服务 —— 用户选择(ask_choice)。
 *
 * @author Sorghum
 */
@Component
public class InteractionService {

    /**
     * 获取默认会话ID（用于未指定会话ID的情况）
     */
    private static final String DEFAULT_SESSION = "__default__";

    /**
     * 渲染一个用户选择菜单。
     */
    public String askChoice(String question, List<Map<String, Object>> options,
                            Boolean allowCustom) {
        if (options == null || options.isEmpty())
            return ErrorMessages.ASK_CHOICE_REQUIRES_OPTIONS;
        StringBuilder sb = new StringBuilder();
        sb.append("┌─ ").append(question).append("\n");
        for (int i = 0; i < options.size(); i++) {
            Map<String, Object> opt = options.get(i);
            String label = (String) opt.getOrDefault("title", "option-" + (i + 1));
            String summary = (String) opt.get("summary");
            sb.append("│ ").append(i + 1).append(". ").append(label);
            if (summary != null) sb.append(" — ").append(summary);
            sb.append("\n");
        }
        if (allowCustom == Boolean.TRUE) {
            sb.append("│ 0. (type your own answer)\n");
        }
        sb.append("└─ 输入编号选择");
        return sb.toString();
    }

}
