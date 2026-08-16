package site.sorghum.loopra.web.model;

 /** 当前工作区文件内容与预览可用性。 */
public record WorkingFileContentDTO(String content, boolean exists, String message) {
}
