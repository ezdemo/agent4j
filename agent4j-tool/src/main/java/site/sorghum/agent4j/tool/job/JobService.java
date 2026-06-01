package site.sorghum.agent4j.tool.job;

import org.noear.solon.annotation.Component;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 后台作业服务 —— 管理 shell 后台进程的生命周期。
 *
 * @author Sorghum
 */
@Component
public class JobService {

    public static final JobRegistry JOB_REGISTRY = new JobRegistry();

    public String runBackground(Path root, String cmd, String cwdStr, Integer waitSec) throws IOException {
        if (cmd == null || cmd.isEmpty()) throw new IOException("run_background: empty command");
        Path cwd = cwdStr != null ? root.resolve(cwdStr).normalize() : root;
        if (!cwd.startsWith(root)) cwd = root;
        JobRegistry.JobEntry entry = JOB_REGISTRY.start(cmd, cwd);
        String preview = null;
        if (waitSec != null && waitSec > 0) {
            try {
                Thread.sleep(waitSec * 1000L);
            } catch (InterruptedException ignored) {
            }
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

    public String jobOutput(int id, Integer since, Integer tailLines) {
        JobRegistry.ReadResult r = JOB_REGISTRY.read(id, since != null ? since : 0,
                tailLines != null ? tailLines : 80);
        if (r == null) return "job " + id + ": not found (use list_jobs)";
        String status = r.running ? "running" : r.exitCode != null ? "exited " + r.exitCode : "stopped";
        return "[job " + id + " · " + status + " · byteLength=" + r.byteLength + "]\n$ " + r.command
                + (r.output.isEmpty() ? "" : "\n" + r.output);
    }

    public String waitForJob(int id, Integer timeoutMs, String waitFor) throws InterruptedException {
        long ms = timeoutMs != null ? Math.min(timeoutMs, 300000) : 5000;
        JobRegistry.WaitResult r = JOB_REGISTRY.waitForJob(id, ms, waitFor);
        if (r == null) return "job " + id + ": not found (use list_jobs)";
        return "{\"jobId\":" + id + ",\"exited\":" + r.exited + ",\"exitCode\":" + r.exitCode
                + ",\"latestOutput\":\"" + r.latestOutput.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    public String stopJob(int id) {
        JobRegistry.ReadResult r = JOB_REGISTRY.stop(id);
        if (r == null) return "job " + id + ": not found";
        return "[job " + id + " stopped · exit " + r.exitCode + "]\n$ " + r.command
                + (r.output.isEmpty() ? "" : "\n" + r.output);
    }

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
