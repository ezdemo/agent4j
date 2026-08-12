package site.sorghum.loopra.web.model;

/**
 * bash 后台会话累积输出日志。
 *
 * @param sessionId 命令会话 ID（bash_start 返回的 session_id）
 * @param workspace 所属工作区绝对路径
 * @param command   启动时执行的命令
 * @param workdir   实际工作目录
 * @param status    运行状态：running / completed
 * @param output    自启动以来累积的输出日志（bash_start 初始输出 + bash_wait/stdin 增量输出）
 */
public record BashSessionLogDTO(
        String sessionId,
        String workspace,
        String command,
        String workdir,
        String status,
        String output
) {
}
