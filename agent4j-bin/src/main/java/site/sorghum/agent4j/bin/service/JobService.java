package site.sorghum.agent4j.bin.service;

import org.noear.solon.annotation.Component;
import site.sorghum.agent4j.bin.job.JobRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 后台作业服务 —— 管理 shell 后台进程的生命周期。
 * <p>
 * 从 Tools.java 中抽出，提供后台作业的启动、输出读取、等待、停止、列表功能。
 * </p>
 *
 * @author Sorghum
 */
@Component
public class JobService {

    public static final JobRegistry JOB_REGISTRY = new JobRegistry();

    /**
     * 启动后台 shell 作业，进程在独立线程中运行。
     *
     * @param root    工作区根目录
     * @param cmd     shell 命令
     * @param cwdStr  工作子目录（可选）
     * @param waitSec 等待启动的秒数（可选）
     * @return 作业启动状态和预览输出
     */
    public String runBackground(Path root, String cmd, String cwdStr, Integer waitSec) throws IOException {
        if (cmd == null || cmd.isEmpty()) throw new IOException("run_background: empty command");
        Path cwd = cwdStr != null ? root.resolve(cwdStr).normalize() : root;
        if (!cwd.startsWith(root)) cwd = root;
        JobRegistry.JobEntry entry = JOB_REGISTRY.start(cmd, cwd);
        String preview = null;
        if (waitSec != null && waitSec > 0) {
            try { Thread.sleep(waitSec * 1000L); } catch (InterruptedException ignored) {}
            JobRegistry.ReadResult r = JOB_REGISTRY.read(entry.id, 0, 10);
            preview = r != null ? r.output : null;
        }
        String status = entry.running
                ? "[job " + entry.id + " started · running]"
                : entry.exitCode != null
                ? "[job " + entry.id + " · exit " + entry.exitCode + "]"
                : "[job " + entry.id + " failed to start]";
        return preview != null ? status + "\n" + preview : status;
    }

    /**
     * 读取后台作业的最新输出。
     *
     * @param id         作业 ID
     * @param since      字节偏移（增量读取）
     * @param tailLines  返回尾部的行数
     * @return 作业输出文本
     */
    public String jobOutput(int id, Integer since, Integer tailLines) {
        JobRegistry.ReadResult r = JOB_REGISTRY.read(id, since != null ? since : 0,
                tailLines != null ? tailLines : 80);
        if (r == null) return "job " + id + ": not found (use list_jobs)";
        String status = r.running ? "running" : r.exitCode != null ? "exited " + r.exitCode : "stopped";
        return "[job " + id + " · " + status + " · byteLength=" + r.byteLength + "]\n$ " + r.command
                + (r.output.isEmpty() ? "" : "\n" + r.output);
    }

    /**
     * 阻塞等待后台作业完成，支持超时控制。
     *
     * @param id        作业 ID
     * @param timeoutMs 超时毫秒数（默认 5000，上限 300000）
     * @param waitFor   等待策略："exit" 或 "output-or-exit"
     * @return JSON 格式的作业状态
     */
    public String waitForJob(int id, Integer timeoutMs, String waitFor) throws InterruptedException {
        long ms = timeoutMs != null ? Math.min(timeoutMs, 300000) : 5000;
        JobRegistry.WaitResult r = JOB_REGISTRY.waitForJob(id, ms, waitFor);
        if (r == null) return "job " + id + ": not found (use list_jobs)";
        return "{\"jobId\":" + id + ",\"exited\":" + r.exited + ",\"exitCode\":" + r.exitCode
                + ",\"latestOutput\":\"" + r.latestOutput.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    /**
     * 停止后台作业（先 SIGTERM 再 SIGKILL）。
     *
     * @param id 作业 ID
     * @return 作业停止状态和最终输出
     */
    public String stopJob(int id) {
        JobRegistry.ReadResult r = JOB_REGISTRY.stop(id);
        if (r == null) return "job " + id + ": not found";
        return "[job " + id + " stopped · exit " + r.exitCode + "]\n$ " + r.command
                + (r.output.isEmpty() ? "" : "\n" + r.output);
    }

    /**
     * 列出当前会话的所有后台作业。
     *
     * @return 格式化作业列表（ID、状态、运行时间、命令）
     */
    public String listJobs() {
        java.util.List<JobRegistry.JobEntry> all = JOB_REGISTRY.list();
        if (all.isEmpty()) return "(no background jobs started this session)";
        StringBuilder sb = new StringBuilder();
        for (JobRegistry.JobEntry e : all) {
            double age = (System.currentTimeMillis() - e.startedAt) / 1000.0;
            String state = e.running ? "running" : e.exitCode != null ? "exit " + e.exitCode : "stopped";
            sb.append(String.format("  %3d  %-20s  %.1fs ago   $ %s\n", e.id, state, age, e.command));
        }
        return sb.toString().trim();
    }
}
