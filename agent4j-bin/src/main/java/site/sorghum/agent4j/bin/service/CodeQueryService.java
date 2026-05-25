package site.sorghum.agent4j.bin.service;

import org.noear.solon.annotation.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * 代码查询服务 —— 符号大纲提取、标识符查找、Java 源码定位。
 * <p>
 * 从 Tools.java 中抽出。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class CodeQueryService {

    /** 获取文件的符号大纲（类/方法/字段） */
    public String getSymbols(Path root, String pathStr) throws IOException {
        Path abs = new FileSystemService().resolveSafe(root, pathStr);
        String content = new String(Files.readAllBytes(abs), StandardCharsets.UTF_8);
        List<String> symbols = new ArrayList<>();
        Pattern classPat = Pattern.compile(
                "(?:public|private|protected|static|abstract|final|sealed|non-sealed)?\\s*"
                        + "(?:class|interface|enum|@interface|record)\\s+(\\w+)");
        Matcher cm = classPat.matcher(content);
        while (cm.find()) {
            int line = content.substring(0, cm.start()).split("\n", -1).length;
            symbols.add(line + ": class " + cm.group(1));
        }
        Pattern methodPat = Pattern.compile(
                "(?:public|private|protected|static|abstract|final|synchronized|native|default)\\s+"
                        + "(?:<[^>]+>\\s+)?(?:\\w+(?:<[^>]+>)?\\s+)?(\\w+)\\s*\\([^)]*\\)\\s*"
                        + "(?:throws\\s+[^{]+)?\\s*\\{");
        Matcher mm = methodPat.matcher(content);
        while (mm.find()) {
            int line = content.substring(0, mm.start()).split("\n", -1).length;
            symbols.add(line + ": method " + mm.group(1) + "()");
        }
        Pattern fieldPat = Pattern.compile(
                "(?:public|private|protected|static|final|volatile|transient)\\s+\\w+(?:<[^>]+>)?\\s+"
                        + "(\\w+)\\s*(?:=|;)");
        Matcher fm = fieldPat.matcher(content);
        while (fm.find()) {
            int line = content.substring(0, fm.start()).split("\n", -1).length;
            symbols.add(line + ": field " + fm.group(1));
        }
        return symbols.isEmpty() ? "(no symbols found)" : String.join("\n", symbols);
    }

    /** 在文件中查找标识符 */
    public String findInCode(Path root, String pathStr, String name) throws IOException {
        Path abs = new FileSystemService().resolveSafe(root, pathStr);
        String content = new String(Files.readAllBytes(abs), StandardCharsets.UTF_8);
        List<String> matches = new ArrayList<>();
        String stripped = content.replaceAll("//[^\n]*", "\n")
                .replaceAll("/\\*[\\s\\S]*?\\*/", " ")
                .replaceAll("\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"", "\"\"")
                .replaceAll("'[^'\\\\]*(?:\\\\.[^'\\\\]*)*'", "''");
        String[] lines = stripped.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(name)) {
                int idx = lines[i].indexOf(name);
                String context = lines[i].substring(Math.max(0, idx - 40),
                        Math.min(lines[i].length(), idx + name.length() + 40));
                matches.add(abs.getFileName() + ":" + (i + 1) + ": " + context.trim());
            }
        }
        return matches.isEmpty() ? "(not found)"
                : String.join("\n", matches.subList(0, Math.min(matches.size(), 30)));
    }

    /** Java 源码查找（项目内 + Maven 仓库 jar） */
    public String javaSource(Path root, String className, String jarKeyword) throws IOException {
        String pathPattern = className.replace('.', '/') + ".java";
        Path start = root;
        try (java.util.stream.Stream<Path> walk = Files.walk(start)) {
            Optional<Path> found = walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().replace('\\', '/').endsWith(pathPattern))
                    .findFirst();
            if (found.isPresent()) {
                String content = new String(Files.readAllBytes(found.get()), StandardCharsets.UTF_8);
                return content.substring(0, Math.min(content.length(), 50000));
            }
        }
        String home = System.getProperty("user.home");
        Path m2 = Paths.get(home, ".m2", "repository");
        if (Files.isDirectory(m2) && jarKeyword != null) {
            try (java.util.stream.Stream<Path> walk = Files.walk(m2)) {
                Optional<Path> jar = walk.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith("-sources.jar"))
                        .filter(p -> p.toString().toLowerCase()
                                .contains(jarKeyword.toLowerCase()))
                        .findFirst();
                if (jar.isPresent()) {
                    try (ZipFile zf = new ZipFile(jar.get().toFile())) {
                        java.util.zip.ZipEntry entry = zf.getEntry(pathPattern);
                        if (entry != null) {
                            String content = FileSystemService.readFully(zf.getInputStream(entry));
                            return content.substring(0, Math.min(content.length(), 50000));
                        }
                    }
                }
            }
        }
        return "// " + className + " not found";
    }
}
