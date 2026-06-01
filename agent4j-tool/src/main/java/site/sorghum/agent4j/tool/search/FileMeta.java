package site.sorghum.agent4j.tool.search;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * 单个文件的轻量元数据——工作区索引的缓存单元。
 *
 * @param relativePath 相对于工作区根目录的路径
 * @param size         文件大小（字节）
 * @param lastModified 最后修改时间戳（毫秒）
 * @param directory    是否为目录
 * @param textFile     是否为文本文件（可 grep）
 * @author Sorghum
 */
public record FileMeta(String relativePath, long size, long lastModified, boolean directory, boolean textFile) {

    @NotNull
    @Contract(pure = true)
    @Override
    public String toString() {
        return relativePath + (directory ? "/" : "") + " (" + size + "B)";
    }
}
