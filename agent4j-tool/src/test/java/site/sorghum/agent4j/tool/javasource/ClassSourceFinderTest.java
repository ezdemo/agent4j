package site.sorghum.agent4j.tool.javasource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ClassSourceFinder} 单元测试。
 */
class ClassSourceFinderTest {

    @TempDir
    Path tempDir;

    // ── 辅助方法 ──────────────────────────────────────────────────────────

    /** 创建源码 jar 文件。 */
    private Path createSourceJar(String jarName, String entryName, String content) throws IOException {
        Path jarPath = tempDir.resolve(jarName);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();
            Files.write(jarPath, baos.toByteArray());
        }
        return jarPath;
    }

    /** 创建普通 jar 文件。 */
    private Path createRegularJar(String jarName, String entryName, byte[] data) throws IOException {
        Path jarPath = tempDir.resolve(jarName);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(data);
            zos.closeEntry();
            zos.finish();
            Files.write(jarPath, baos.toByteArray());
        }
        return jarPath;
    }

    // ── defaultRepoPaths ─────────────────────────────────────────────────

    @Test
    void 默认仓库路径返回列表() {
        List<Path> paths = ClassSourceFinder.defaultRepoPaths();
        assertNotNull(paths);
        for (Path p : paths) {
            assertTrue(p.toString().contains(".m2") || p.toString().contains(".gradle"));
        }
    }

    // ── 项目树搜索 ───────────────────────────────────────────────────

    @Test
    void 在项目树中找到Java文件() throws IOException {
        Path src = Files.createDirectories(tempDir.resolve("src/main/java/com/example"));
        String source = "package com.example;\npublic class Hello { public void greet() {} }\n";
        Files.writeString(src.resolve("Hello.java"), source);

        ClassSourceFinder finder = new ClassSourceFinder(tempDir);
        ClassSourceFinder.FindResult result = finder.findSource("com.example.Hello", "dummy-kw");

        assertTrue(result.found());
        assertEquals(ClassSourceFinder.Method.PROJECT, result.method());
        assertEquals(source, result.source());
    }

    @Test
    void 在深层嵌套项目树中找到Java文件() throws IOException {
        Path deep = Files.createDirectories(tempDir.resolve("a/b/c/d"));
        String source = "public class Deep { }\n";
        Files.writeString(deep.resolve("Deep.java"), source);

        ClassSourceFinder finder = new ClassSourceFinder(tempDir);
        ClassSourceFinder.FindResult result = finder.findSource("Deep", "dummy-kw");

        assertTrue(result.found());
        assertEquals(ClassSourceFinder.Method.PROJECT, result.method());
    }

    @Test
    void 跳过排除目录() throws IOException {
        // 在 target/ 下创建 .java —— 应被跳过
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(targetDir.resolve("Hidden.java"), "public class Hidden { }\n");

        ClassSourceFinder finder = new ClassSourceFinder(tempDir);
        ClassSourceFinder.FindResult result = finder.findSource("Hidden", "dummy-kw");

        assertFalse(result.found());
    }

    @Test
    void 项目无匹配时返回未找到() throws IOException {
        Files.writeString(tempDir.resolve("Other.java"), "public class Other { }\n");

        ClassSourceFinder finder = new ClassSourceFinder(tempDir);
        ClassSourceFinder.FindResult result = finder.findSource("NonExistent", "dummy-kw");

        assertFalse(result.found());
    }

    // ── 源码 jar 搜索 ─────────────────────────────────────────────────

    @Test
    void 在源码jar中找到源码() throws IOException {
        // 搭建模拟 .m2 仓库
        Path m2Repo = Files.createDirectories(tempDir.resolve("m2/repository/com/example/lib/1.0"));
        String sourceContent = "package com.example;\npublic class Lib { }\n";
        createSourceJar(m2Repo.resolve("lib-1.0-sources.jar").toString(),
                "com/example/Lib.java", sourceContent);

        ClassSourceFinder finder = new ClassSourceFinder(tempDir,
                List.of(tempDir.resolve("m2/repository")), "javap", 2000);
        ClassSourceFinder.FindResult result = finder.findSource("com.example.Lib", "lib");

        assertTrue(result.found());
        assertEquals(ClassSourceFinder.Method.M2_SOURCE_JAR, result.method());
        assertEquals(sourceContent, result.source());
    }

    // ── jarKeyword 过滤 ─────────────────────────────────────────────────

    @Test
    void 关键字过滤掉不匹配的jar() throws IOException {
        Path repo = Files.createDirectories(tempDir.resolve("repo"));
        String matchedContent = "package com.a;\npublic class A { }\n";
        String unmatchedContent = "package com.b;\npublic class B { }\n";

        createSourceJar(repo.resolve("matched-lib-1.0-sources.jar").toString(),
                "com/a/A.java", matchedContent);
        createSourceJar(repo.resolve("other-lib-1.0-sources.jar").toString(),
                "com/b/B.java", unmatchedContent);

        ClassSourceFinder finder = new ClassSourceFinder(tempDir, List.of(repo), "javap", 2000);

        // 关键字 "matched" —— 应该找到 A
        ClassSourceFinder.FindResult result = finder.findSource("com.a.A", "matched");
        assertTrue(result.found());
        assertEquals(matchedContent, result.source());

        // 关键字 "matched" —— 不应该找到 B（jar 被过滤掉）
        ClassSourceFinder.FindResult resultB = finder.findSource("com.b.B", "matched");
        assertFalse(resultB.found());
    }

    // ── compressJavapOutput ──────────────────────────────────────────────

    @Test
    void 剥离常量池定义行() {
        // 常量池定义行（如 "   #7 = Fieldref ..."）应该被剥离。
        // 字节码注释中的内联引用 #1 等应保留。
        String raw = """
                Compiled from "Foo.java"
                public class Foo {
                  public Foo();
                    Code:
                       0: aload_0
                       1: invokespecial #1                  // Method java/lang/Object."<init>":()V
                       4: return
                       #7 = Fieldref           #8.#9         // java/lang/System.out
                       #13 = String             #14           // hello
                """;
        String compressed = ClassSourceFinder.compressJavapOutput(raw);
        assertFalse(compressed.contains("Compiled from"));
        assertTrue(compressed.contains("public class Foo"));
        assertTrue(compressed.contains("Code:"));
        // 常量池定义行应被剥离
        assertFalse(compressed.contains("#7 = Fieldref"));
        assertFalse(compressed.contains("#13 = String"));
        // 字节码中的内联 #N 引用应保留
        assertTrue(compressed.contains("#1"));
    }

    @Test
    void 剥离调试表() {
        String raw = """
                Compiled from "Foo.java"
                public class Foo {
                  public void greet();
                    Code:
                       0: getstatic     #7                  // Field java/lang/System.out:Ljava/io/PrintStream;
                       3: ldc           #13                 // String hello
                       5: invokevirtual #15                 // Method java/io/PrintStream.println:(Ljava/lang/String;)V
                       8: return
                    LineNumberTable:
                      line 3: 0
                      line 4: 8
                    LocalVariableTable:
                      Start  Length  Slot  Name   Signature
                          0       9     0  this   LFoo;
                """;
        String compressed = ClassSourceFinder.compressJavapOutput(raw);
        assertFalse(compressed.contains("LineNumberTable"));
        assertFalse(compressed.contains("LocalVariableTable"));
        assertFalse(compressed.contains("line 3"));
        assertTrue(compressed.contains("Code:"));
    }

    @Test
    void 剥离StackMapTable() {
        String raw = """
                Compiled from "Bar.java"
                public class Bar {
                  public int test(int);
                    Code:
                       0: iload_1
                       1: ifgt          9
                       4: iconst_1
                       5: istore_2
                       6: goto          11
                       9: iconst_0
                      10: istore_2
                      11: iload_2
                      12: ireturn
                    StackMapTable: number_of_entries = 3
                      frame_type = 252 /* append */
                        offset_delta = 9
                        locals = [ int ]
                      frame_type = 1 /* same */
                """;
        String compressed = ClassSourceFinder.compressJavapOutput(raw);
        assertFalse(compressed.contains("StackMapTable"));
        assertFalse(compressed.contains("frame_type"));
        assertTrue(compressed.contains("Code:"));
    }

    @Test
    void 合并多余连续空行() {
        String raw = "public class Foo {\n\n\n\n  void m() {}\n}";
        String compressed = ClassSourceFinder.compressJavapOutput(raw);
        // 不应出现超过 2 个连续换行
        assertFalse(compressed.contains("\n\n\n"));
    }
}
