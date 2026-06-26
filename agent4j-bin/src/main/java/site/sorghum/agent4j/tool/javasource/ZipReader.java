package site.sorghum.agent4j.tool.javasource;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * JAR/ZIP 条目读取器 —— 基于 JDK 内置 {@link ZipFile}，
 * 自动处理 STORED 与 DEFLATED 两种压缩格式。
 * <p>
 * 由于 Java 原生支持 ZIP，无需像 Node.js 版那样手动解析
 * EOCD / 中央目录 / 本地文件头，实现大幅简化。
 * </p>
 *
 * @author Sorghum
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ZipReader {

    /**
     * JAR/ZIP 文件中的单个条目。
     *
     * @param fileName 条目名，如 "com/google/common/collect/Lists.class"
     * @param data     解压后的原始字节
     */
    public record JarEntry(String fileName, byte[] data) {
    }

    /**
     * 根据精确条目名从 JAR/ZIP 文件中读取单个条目。
     *
     * @param jarPath   JAR/ZIP 文件路径
     * @param entryName 精确条目名，如 "com/google/common/collect/Lists.class"
     * @return 条目数据；未找到时返回 {@code null}
     * @throws IOException 文件无法打开或读取时抛出
     */
    public static JarEntry readJarEntry(String jarPath, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(jarPath)) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream is = zip.getInputStream(entry)) {
                byte[] data = is.readAllBytes();
                return new JarEntry(entryName, data);
            }
        }
    }

    /**
     * 从 JAR/ZIP 中读取单个条目并以 UTF-8 字符串返回。
     *
     * @param jarPath   JAR/ZIP 文件路径
     * @param entryName 精确条目名
     * @return 条目文本内容；未找到时返回 {@code null}
     * @throws IOException 文件无法打开或读取时抛出
     */
    public static String readJarEntryAsString(String jarPath, String entryName) throws IOException {
        JarEntry entry = readJarEntry(jarPath, entryName);
        if (entry == null) {
            return null;
        }
        return new String(entry.data, StandardCharsets.UTF_8);
    }

    /**
     * 列出 JAR/ZIP 文件中所有非目录条目的名称。
     *
     * @param jarPath JAR/ZIP 文件路径
     * @return 条目名列表
     * @throws IOException 文件无法打开时抛出
     */
    public static List<String> listJarEntries(String jarPath) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jarPath)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    names.add(entry.getName());
                }
            }
        }
        return names;
    }
}
