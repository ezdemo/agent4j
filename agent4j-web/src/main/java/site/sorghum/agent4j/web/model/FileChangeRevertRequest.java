package site.sorghum.agent4j.web.model;

import site.sorghum.agent4j.bin.agent.model.FileChange;

import java.util.List;

/** Request to reverse the persisted file diffs shown on one assistant reply. */
public class FileChangeRevertRequest {
    public String workspaceHash;
    public List<FileChange> changes;
}
