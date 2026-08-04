package site.sorghum.loopra.tool.solon.lsp;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.ai.talents.lsp.LspManager;
import org.noear.solon.ai.talents.lsp.LspTalent;
import org.noear.solon.annotation.Component;
import site.sorghum.loopra.tool.AgentTool;
import site.sorghum.loopra.tool.SolonToTools;

import java.nio.file.Paths;

/**
 * Loopra LSP 技能 —— Solon {@link LspTalent} 的薄包装，实现 {@link SolonToTools} 接口。
 * <p>
 * 通过此包装将 Solon AI 框架已有的 LSP 能力（定义跳转、引用查找、悬停提示、
 * 文档符号、调用层次等）无缝接入 loopra 的 {@link AgentTool} 体系。
 * </p>
 *
 * <h3>与 LspManageService 的协作</h3>
 * <p>
 * 本类持有 {@link LspManager} 实例，{@code LspManageService} 通过
 * {@link #getLspManager()} 获取它，调用 {@code registerServer(name, params)}
 * 将用户配置的 LSP 服务器注册到执行层。
 * </p>
 *
 * @author Sorghum
 */
@Slf4j
@Component
public class SharedLoopraLspSkill extends LspTalent {

    /**
     * 无参构造（供 Solon IoC 使用），默认工作区为用户目录。
     */
    public SharedLoopraLspSkill() {
        this(Paths.get(System.getProperty("user.home"), ".loopra").toAbsolutePath().toString());
    }

    /**
     * @param workDir 工作区根目录，用于 Language Server 的 rootUri
     */
    private SharedLoopraLspSkill(String workDir) {
        super(new LspManager(workDir), workDir);
        log.info("LoopraLspSkill 已创建，workDir={}", workDir);
    }

    /**
     * 复制
     */
    public void copyToLoopra(LspTalent loopraLspTalent){
        this.getLspManager().getServerConfigs().forEach(
                (name,server) -> {
                    loopraLspTalent.getLspManager().registerServer(
                            name,server
                    );
                }
        );

    }
}
