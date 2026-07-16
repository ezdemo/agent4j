package site.sorghum.agent4j.web.model;

/** Current workspace file content and preview availability. */
public record WorkingFileContentDTO(String content, boolean exists, String message) {
}
