package site.sorghum.loopra.web.model;

/**
 * bash_start/bash_wait 后台命令会话镜像信息。
 *
 * @param sessionId 命令会话 ID（bash_start 返回的 session_id）
 * @param workspace 所属项目绝对路径
 * @param command   启动时执行的命令
 * @param workdir   实际工作目录
 * @param startedAt 启动时间戳（毫秒）
 * @param status    运行状态：running / completed
 */
public record BashSessionDTO(
        String sessionId,
        String workspace,
        String command,
        String workdir,
        long startedAt,
        String status
) {
}
