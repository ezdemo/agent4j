package site.sorghum.loopra.bin.agent.model;

/** A compact, persisted summary of one file changed by the AI. */
public record FileChange(String path, int additions, int deletions, boolean created, String diff) {
}
