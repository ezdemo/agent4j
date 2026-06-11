package site.sorghum.agent4j.web.service;

import lombok.extern.slf4j.Slf4j;
import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Init;
import org.noear.solon.annotation.Inject;
import site.sorghum.agent4j.bin.schedule.ScheduleService;
import site.sorghum.agent4j.bin.schedule.ScheduleStore;
import site.sorghum.agent4j.bin.schedule.ScheduledTask;

import java.util.List;
import java.util.Set;

/**
 * Web 层定时任务管理器 —— 将定时任务引擎组装到 Solon 容器中。
 */
@Slf4j
@Component
public class ScheduleManager {

    @Inject
    private AgentService agentService;

    private ScheduleService scheduleService;
    private ScheduleStore scheduleStore;

    @Init
    public void init() {
        log.info("[schedule] 初始化定时任务引擎...");

        this.scheduleStore = new ScheduleStore();
        this.scheduleService = new ScheduleService(scheduleStore,
                agentService::executeScheduledTask);

        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[schedule] JVM 关闭，停止定时任务引擎...");
            if (scheduleService != null) {
                scheduleService.stop();
            }
        }, "schedule-shutdown"));

        // 启动调度引擎
        scheduleService.start();

        log.info("[schedule] 定时任务引擎初始化完成");
    }

    // ==================== 委托方法 ====================

    public List<ScheduledTask> list(String workspaceHash) {
        return scheduleService.list(workspaceHash);
    }

    public ScheduledTask get(String workspaceHash, String taskId) {
        return scheduleService.get(workspaceHash, taskId);
    }

    public ScheduledTask create(String workspaceHash, ScheduledTask task) {
        return scheduleService.create(workspaceHash, task);
    }

    public ScheduledTask update(String workspaceHash, String taskId, ScheduledTask update) {
        return scheduleService.update(workspaceHash, taskId, update);
    }

    public ScheduledTask toggle(String workspaceHash, String taskId) {
        return scheduleService.toggle(workspaceHash, taskId);
    }

    public void delete(String workspaceHash, String taskId) {
        scheduleService.delete(workspaceHash, taskId);
    }

    public String runNow(String workspaceHash, String taskId) {
        return scheduleService.runNow(workspaceHash, taskId);
    }

    public Set<String> getActiveWorkspaceHashes() {
        return scheduleService.getActiveWorkspaceHashes();
    }

    public ScheduleService getScheduleService() {
        return scheduleService;
    }
}
