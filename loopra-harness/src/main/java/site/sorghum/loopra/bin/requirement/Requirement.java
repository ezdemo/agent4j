package site.sorghum.loopra.bin.requirement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 需求池需求数据模型。
 * <p>
 * 每个需求绑定一个项目（workspace）和一个专属执行会话（{@code req_<id>}），
 * 状态由 AI 通过 finish_requirement 工具流转（todo → doing → done/failed），
 * 评论与执行日志均落在专属会话的消息流中。
 * </p>
 *
 * @author Sorghum
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Requirement implements Serializable {

    /** 唯一标识 */
    private volatile String id;

    /** 需求标题 */
    private volatile String title;

    /** 需求描述 / 验收标准（AI 执行的依据） */
    private volatile String description;

    /** 优先级：high / medium / low */
    private volatile String priority;

    /** 所属项目（工作区）hash */
    private volatile String projectHash;

    /** 所属项目名称（展示用） */
    private volatile String projectName;

    /** 状态：todo(待执行) / doing(执行中) / done(已完成) / failed(已失败) */
    private volatile String status;

    /** 调度方式：immediate(立即执行) / scheduled(定时执行)，旧数据缺失时按 immediate 处理 */
    private volatile String scheduleMode;

    /** 执行时使用的模型名称；为空时使用全局默认模型 */
    private volatile String model;

    /** 执行时使用的模型渠道 ID；为空时使用全局默认渠道 */
    private volatile String modelChannelId;

    /** 执行时使用的推理强度；为空时使用全局默认值 */
    private volatile String reasoningEffort;

    /** 执行时使用的审批模式：free / approval / auto；为空时使用全局默认值 */
    private volatile String hitl;

    /** 是否正等待人工审批；仅审批模式下的工具调用暂停时为 true */
    private volatile boolean approvalPending;

    /** 定时执行时间戳（ms）；仅 scheduleMode=scheduled 时有效 */
    private volatile long scheduledAt;

    /** AI 完成总结（finish_requirement 写入） */
    private volatile String summary;

    /** 专属执行会话名（req_&lt;id&gt;），评论与执行日志落在该会话 */
    private volatile String sessionName;

    /** 创建时间戳（ms） */
    private volatile long createdAt;

    /** 更新时间戳（ms） */
    private volatile long updatedAt;
}
