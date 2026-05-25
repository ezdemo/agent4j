package site.sorghum.agent4j.tool.search;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 单个文件的轻量元数据——工作区索引的缓存单元。
 *
 * @author Sorghum
 */
@Getter
@AllArgsConstructor
public class FileMeta {

    /** 相对于工作区根目录的路径 */
    private final String relativePath;

    /** 文件大小（字节） */
    private final long size;

    /** 最后修改时间戳（毫秒） */
    private final long lastModified;

    /** 是否为目录 */
    private final boolean directory;

    /** 是否为文本文件（可 grep） */
    private final boolean textFile;

    @Override
    public String toString() {
        return relativePath + (directory ? "/" : "") + " (" + size + "B)";
    }
}
