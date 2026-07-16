package site.sorghum.agent4j.web.service;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.web.common.ServiceException;
import site.sorghum.agent4j.web.model.WorkspaceFileEntryDTO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 工作区文件浏览服务。仅暴露工作区内的单层目录项，供前端按需展开。
 */
@Component
public class WorkspaceFileService {

    private static final int MAX_ENTRIES = 200;

    @Inject
    private AgentService agentService;

    public List<WorkspaceFileEntryDTO> list(String workspaceHash, String relativePath) {
        Path workspace = resolveWorkspace(workspaceHash);
        Path directory = resolveDirectory(workspace, relativePath);

        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(this::isVisible)
                    .sorted(Comparator
                            .comparing((Path path) -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                            .thenComparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .limit(MAX_ENTRIES)
                    .map(path -> new WorkspaceFileEntryDTO(
                            path.getFileName().toString(),
                            workspace.relativize(path).toString().replace('\\', '/'),
                            Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)))
                    .toList();
        } catch (IOException e) {
            throw new ServiceException("无法读取目录: " + e.getMessage());
        }
    }

    private Path resolveWorkspace(String workspaceHash) {
        String workspacePath = agentService.resolveWorkspaceHashOrThrow(workspaceHash);
        try {
            Path workspace = Path.of(workspacePath).toRealPath();
            if (!Files.isDirectory(workspace)) {
                throw new ServiceException("工作区不是目录");
            }
            return workspace;
        } catch (IOException e) {
            throw new ServiceException("工作区不可访问");
        }
    }

    private Path resolveDirectory(Path workspace, String relativePath) {
        String value = relativePath == null ? "" : relativePath.trim();
        Path requested = value.isEmpty() ? workspace : workspace.resolve(value).normalize();
        if (!requested.startsWith(workspace)) {
            throw new ServiceException("无效的文件路径");
        }
        try {
            Path directory = requested.toRealPath();
            if (!directory.startsWith(workspace) || !Files.isDirectory(directory)) {
                throw new ServiceException("无效的文件路径");
            }
            return directory;
        } catch (IOException e) {
            throw new ServiceException("目录不存在或不可访问");
        }
    }

    private boolean isVisible(Path path) {
        try {
            Path realPath = path.toRealPath();
            return realPath.startsWith(path.getParent().toRealPath())
                    && Files.isReadable(path);
        } catch (IOException e) {
            return false;
        }
    }
}
