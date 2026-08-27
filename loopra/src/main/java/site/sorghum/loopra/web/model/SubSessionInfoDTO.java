package site.sorghum.loopra.web.model;

/**
 * 子代理会话信息（挂在主代理会话下的子会话）。
 */
public record SubSessionInfoDTO(
        /** 子代理会话唯一标识（文件标识，跨重启稳定） */
        String subSessionId,
        /** 会话名称（name + 任务首句；旧数据为纯任务描述） */
        String task,
        /** 子代理名字（人名/二次元名字等，旧数据可能为 null） */
        String name,
        /** 会话标题（任务首句，旧数据可能为 null） */
        String title,
        /** 子代理角色 */
        String profile,
        /** 状态：completed / aborted / error / running */
        String status,
        /** 开始时间戳 */
        long startedAt,
        /** 结束时间戳（运行中为 0） */
        long endedAt,
        /** 已落盘事件数 */
        int eventCount,
        /** 文件最后修改时间 */
        long mtime
) {
}
