package site.sorghum.loopra.web.model;

import lombok.Data;

/**
 * 会话隔离分支模式切换请求。
 * <p>{@code worktreeMode} 与 {@code mergeMode} 均可选，但至少提供一个。</p>
 */
@Data
public class SessionWorktreeModeRequest {

    /**
     * 是否开启隔离分支模式
     */
    private Boolean worktreeMode;

    /**
     * 隔离分支合并模式：manual / ai-auto / ai-auto-approve
     */
    private String mergeMode;
}
