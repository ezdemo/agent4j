package site.sorghum.loopra.web.model;

import lombok.Data;

/**
 * 会话重命名请求。
 * <p>{@code title} 为新的会话显示名称（写入会话元数据），
 * 会话名（文件标识）保持不变。</p>
 */
@Data
public class SessionRenameRequest {

    /**
     * 新的会话显示名称
     */
    private String title;
}
