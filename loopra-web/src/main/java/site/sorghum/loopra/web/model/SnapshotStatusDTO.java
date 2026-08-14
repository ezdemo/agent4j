package site.sorghum.loopra.web.model;

import lombok.Data;

/**
 * 快照状态 DTO。
 *
 * @author Sorghum
 */
@Data
public class SnapshotStatusDTO {

    /** 项目是否为 Git 仓库 */
    private boolean gitRepo;

    public SnapshotStatusDTO() {}

    public SnapshotStatusDTO(boolean gitRepo) {
        this.gitRepo = gitRepo;
    }
}
