package site.sorghum.loopra.web.model;

 /** 撤销单条助手回复关联文件变更后的结果。 */
public record FileChangeRevertDTO(String message, int revertedFiles) {
}
