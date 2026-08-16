package site.sorghum.loopra.bin.agent.model;

 /** AI 修改一个文件后的紧凑持久化摘要。 */
public record FileChange(String path, int additions, int deletions, boolean created, String diff) {
}
