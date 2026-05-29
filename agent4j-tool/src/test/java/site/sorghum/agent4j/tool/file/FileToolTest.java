package site.sorghum.agent4j.tool.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import site.sorghum.agent4j.tool.HitlRequiredException;
import site.sorghum.agent4j.tool.ToolContext;
import site.sorghum.agent4j.tool.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FileTool} 单元测试——文件操作工具。
 *
 * @author Sorghum
 */
@DisplayName("FileTool 文件操作工具测试")
class FileToolTest {

    @Nested
    @DisplayName("delete_file 删除文件")
    class DeleteFile {

        @Test
        @DisplayName("删除文件应触发 HITL 审批")
        void deleteFile_shouldTriggerHITL(@TempDir Path tempDir) throws IOException {
            // 创建测试文件
            Path file = tempDir.resolve("test.txt");
            Files.write(file, "content".getBytes());

            // 创建 ToolContext
            Map<String, Object> params = new HashMap<>();
            params.put("action", "delete_file");
            params.put("path", "test.txt");
            ToolContext ctx = new ToolContext(params, tempDir);

            // 执行删除，应触发 HITL
            FileTool tool = new FileTool();
            assertThrows(HitlRequiredException.class, () -> tool.execute(ctx));
            
            // 验证文件仍然存在（因为 HITL 未审批）
            assertTrue(Files.exists(file));
        }

        @Test
        @DisplayName("沙箱旁路模式下删除文件应成功")
        void deleteFile_withSandboxBypass_shouldSucceed(@TempDir Path tempDir) throws IOException {
            // 创建测试文件
            Path file = tempDir.resolve("test.txt");
            Files.write(file, "content".getBytes());

            // 创建 ToolContext
            Map<String, Object> params = new HashMap<>();
            params.put("action", "delete_file");
            params.put("path", "test.txt");
            ToolContext ctx = new ToolContext(params, tempDir);

            // 设置沙箱旁路模式
            ToolContext.enableSandboxBypass();
            try {
                // 执行删除，应成功
                FileTool tool = new FileTool();
                ToolResult result = tool.execute(ctx);
                
                // 验证删除成功
                assertTrue(result.success());
                assertFalse(Files.exists(file));
            } finally {
                // 清理沙箱旁路模式
                ToolContext.disableSandboxBypass();
            }
        }

        @Test
        @DisplayName("删除不存在的文件应返回错误")
        void deleteFile_notFound_shouldReturnError(@TempDir Path tempDir) {
            // 创建 ToolContext
            Map<String, Object> params = new HashMap<>();
            params.put("action", "delete_file");
            params.put("path", "nonexistent.txt");
            ToolContext ctx = new ToolContext(params, tempDir);

            // 执行删除，应返回错误
            FileTool tool = new FileTool();
            ToolResult result = tool.execute(ctx);
            
            // 验证返回 NOT_FOUND 错误
            assertFalse(result.success());
            assertTrue(result.errorCode().contains("NOT_FOUND"));
        }

        @Test
        @DisplayName("删除目录应返回错误")
        void deleteFile_isDir_shouldReturnError(@TempDir Path tempDir) throws IOException {
            // 创建测试目录
            Path dir = tempDir.resolve("testdir");
            Files.createDirectories(dir);

            // 创建 ToolContext
            Map<String, Object> params = new HashMap<>();
            params.put("action", "delete_file");
            params.put("path", "testdir");
            ToolContext ctx = new ToolContext(params, tempDir);

            // 执行删除，应返回错误
            FileTool tool = new FileTool();
            ToolResult result = tool.execute(ctx);
            
            // 验证返回 IS_DIR 错误
            assertFalse(result.success());
            assertTrue(result.errorCode().contains("IS_DIR"));
        }
    }

    @Nested
    @DisplayName("delete_dir 删除目录")
    class DeleteDir {

        @Test
        @DisplayName("删除目录应触发 HITL 审批")
        void deleteDir_shouldTriggerHITL(@TempDir Path tempDir) throws IOException {
            // 创建测试目录和文件
            Path dir = tempDir.resolve("testdir");
            Files.createDirectories(dir);
            Files.write(dir.resolve("file.txt"), "content".getBytes());

            // 创建 ToolContext
            Map<String, Object> params = new HashMap<>();
            params.put("action", "delete_dir");
            params.put("path", "testdir");
            ToolContext ctx = new ToolContext(params, tempDir);

            // 执行删除，应触发 HITL
            FileTool tool = new FileTool();
            assertThrows(HitlRequiredException.class, () -> tool.execute(ctx));
            
            // 验证目录仍然存在（因为 HITL 未审批）
            assertTrue(Files.exists(dir));
            assertTrue(Files.exists(dir.resolve("file.txt")));
        }

        @Test
        @DisplayName("沙箱旁路模式下删除目录应成功")
        void deleteDir_withSandboxBypass_shouldSucceed(@TempDir Path tempDir) throws IOException {
            // 创建测试目录和文件
            Path dir = tempDir.resolve("testdir");
            Files.createDirectories(dir);
            Files.write(dir.resolve("file.txt"), "content".getBytes());

            // 创建 ToolContext
            Map<String, Object> params = new HashMap<>();
            params.put("action", "delete_dir");
            params.put("path", "testdir");
            ToolContext ctx = new ToolContext(params, tempDir);

            // 设置沙箱旁路模式
            ToolContext.enableSandboxBypass();
            try {
                // 执行删除，应成功
                FileTool tool = new FileTool();
                ToolResult result = tool.execute(ctx);
                
                // 验证删除成功
                assertTrue(result.success());
                assertFalse(Files.exists(dir));
            } finally {
                // 清理沙箱旁路模式
                ToolContext.disableSandboxBypass();
            }
        }

        @Test
        @DisplayName("删除不存在的目录应返回错误")
        void deleteDir_notFound_shouldReturnError(@TempDir Path tempDir) {
            // 创建 ToolContext
            Map<String, Object> params = new HashMap<>();
            params.put("action", "delete_dir");
            params.put("path", "nonexistent");
            ToolContext ctx = new ToolContext(params, tempDir);

            // 执行删除，应返回错误
            FileTool tool = new FileTool();
            ToolResult result = tool.execute(ctx);
            
            // 验证返回 NOT_FOUND 错误
            assertFalse(result.success());
            assertTrue(result.errorCode().contains("NOT_FOUND"));
        }

        @Test
        @DisplayName("删除文件应返回错误")
        void deleteDir_isFile_shouldReturnError(@TempDir Path tempDir) throws IOException {
            // 创建测试文件
            Path file = tempDir.resolve("test.txt");
            Files.write(file, "content".getBytes());

            // 创建 ToolContext
            Map<String, Object> params = new HashMap<>();
            params.put("action", "delete_dir");
            params.put("path", "test.txt");
            ToolContext ctx = new ToolContext(params, tempDir);

            // 执行删除，应返回错误
            FileTool tool = new FileTool();
            ToolResult result = tool.execute(ctx);
            
            // 验证返回 NOT_DIR 错误
            assertFalse(result.success());
            assertTrue(result.errorCode().contains("NOT_DIR"));
        }
    }

    @Nested
    @DisplayName("路径越界")
    class PathTraversal {

        @Test
        @DisplayName("路径越界应触发 HITL 审批")
        void pathTraversal_shouldTriggerHITL(@TempDir Path tempDir) {
            // 创建 ToolContext
            Map<String, Object> params = new HashMap<>();
            params.put("action", "stat");
            params.put("path", "../outside.txt");
            ToolContext ctx = new ToolContext(params, tempDir);

            // 执行操作，应触发 HITL
            FileTool tool = new FileTool();
            assertThrows(HitlRequiredException.class, () -> tool.execute(ctx));
        }

        @Test
        @DisplayName("沙箱旁路模式下路径越界应成功")
        void pathTraversal_withSandboxBypass_shouldSucceed(@TempDir Path tempDir) throws IOException {
            // 创建外部文件
            Path outside = tempDir.getParent().resolve("outside.txt");
            Files.write(outside, "content".getBytes());

            // 创建 ToolContext
            Map<String, Object> params = new HashMap<>();
            params.put("action", "stat");
            params.put("path", "../outside.txt");
            ToolContext ctx = new ToolContext(params, tempDir);

            // 设置沙箱旁路模式
            ToolContext.enableSandboxBypass();
            try {
                // 执行操作，应成功
                FileTool tool = new FileTool();
                ToolResult result = tool.execute(ctx);
                
                // 验证操作成功
                assertTrue(result.success());
            } finally {
                // 清理沙箱旁路模式
                ToolContext.disableSandboxBypass();
                // 清理外部文件
                try { Files.deleteIfExists(outside); } catch (IOException ignored) {}
            }
        }
    }
}
