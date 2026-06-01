package site.sorghum.agent4j.tool.search;

import org.jetbrains.annotations.NotNull;

/**
 * grep 搜索的一条匹配结果。
 *
 * @param file    文件相对路径
 * @param line    行号（1-based）
 * @param content 匹配行的文本内容（不含换行符）
 * @author Sorghum
 */
public record SearchMatch(String file, int line, String content) {

    @NotNull
    @Override
    public String toString() {
        return file + ":" + line + ": " + content;
    }
}
