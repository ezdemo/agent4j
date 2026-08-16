package site.sorghum.loopra.web.model;

/**
 * 斜杠命令元数据。
 */
public record CommandMetaDTO(
        String cmd,
        String desc,
        String args
) {
}
