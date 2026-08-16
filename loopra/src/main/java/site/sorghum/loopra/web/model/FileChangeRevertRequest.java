package site.sorghum.loopra.web.model;

import site.sorghum.loopra.bin.agent.model.FileChange;

import java.util.List;

 /** 请求撤销单条助手回复上展示的已持久化文件差异。 */
public class FileChangeRevertRequest {
    public String workspaceHash;
    public List<FileChange> changes;
}
