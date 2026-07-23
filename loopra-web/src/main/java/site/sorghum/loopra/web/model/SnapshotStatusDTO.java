package site.sorghum.loopra.web.model;

import lombok.Data;

/**
 * 快照状态 DTO。
 *
 * @author Sorghum
 */
@Data
public class SnapshotStatusDTO {

    /** 工作区是否为 Git 仓库 */
    private boolean gitRepo;

    public SnapshotStatusDTO() {}

    public SnapshotStatusDTO(boolean gitRepo) {
        this.gitRepo = gitRepo;
    }
}
