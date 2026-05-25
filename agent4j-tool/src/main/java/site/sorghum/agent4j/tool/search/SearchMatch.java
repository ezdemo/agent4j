package site.sorghum.agent4j.tool.search;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * grep 搜索的一条匹配结果。
 *
 * @author Sorghum
 */
@Getter
@AllArgsConstructor
public class SearchMatch {

    /** 文件相对路径 */
    private final String file;

    /** 行号（1-based） */
    private final int line;

    /** 匹配行的文本内容（不含换行符） */
    private final String content;

    @Override
    public String toString() {
        return file + ":" + line + ": " + content;
    }
}
