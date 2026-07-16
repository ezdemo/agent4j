package site.sorghum.agent4j.web.model;

/** Result of reversing the file changes associated with one assistant reply. */
public record FileChangeRevertDTO(String message, int revertedFiles) {
}
