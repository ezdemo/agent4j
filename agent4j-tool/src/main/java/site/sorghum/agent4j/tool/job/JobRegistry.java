package site.sorghum.agent4j.tool.job;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 后台作业注册表。
 *
 * @author Sorghum
 */
public class JobRegistry {

    private final Map<Integer, JobEntry> jobs = new ConcurrentHashMap<>();
    private int nextId = 1;

    public synchronized JobEntry start(String command, Path cwd) throws IOException {
        int id = nextId++;
        ProcessBuilder pb;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd", "/c", command);
        } else {
            pb = new ProcessBuilder("sh", "-c", command);
        }
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        JobEntry entry = new JobEntry(id, command, process);
        jobs.put(id, entry);
        return entry;
    }

    public JobEntry get(int id) {
        return jobs.get(id);
    }

    public List<JobEntry> list() {
        return jobs.values().stream()
                .sorted(Comparator.comparingInt(e -> e.id))
                .collect(Collectors.toList());
    }

    public ReadResult read(int id, int since, int tailLines) {
        JobEntry e = jobs.get(id);
        if (e == null) return null;
        return new ReadResult(e, since, tailLines);
    }

    public WaitResult waitForJob(int id, long timeoutMs, String waitFor) throws InterruptedException {
        JobEntry e = jobs.get(id);
        if (e == null) return null;
        long deadline = System.currentTimeMillis() + timeoutMs;

        if ("output-or-exit".equals(waitFor)) {
            int lastLen;
            synchronized (e.output) {
                lastLen = e.output.length();
            }
            while (System.currentTimeMillis() < deadline) {
                if (!e.running) break;
                synchronized (e.output) {
                    if (e.output.length() > lastLen) break;
                }
                Thread.sleep(200);
            }
        } else {
            while (System.currentTimeMillis() < deadline && e.running) {
                Thread.sleep(200);
            }
        }
        return new WaitResult(e, 2000);
    }

    public ReadResult stop(int id) {
        JobEntry e = jobs.get(id);
        if (e == null) return null;
        e.running = false;
        e.process.destroyForcibly();
        try {
            e.process.waitFor(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        return new ReadResult(e, 0, 0);
    }

    public static class JobEntry {
        public final int id;
        public final String command;
        public final Process process;
        public final StringBuffer output = new StringBuffer();
        public final long startedAt = System.currentTimeMillis();
        private final Thread readerThread;
        public volatile boolean running = true;
        public volatile Integer exitCode = null;
        public volatile String spawnError = null;

        JobEntry(int id, String command, Process process) {
            this.id = id;
            this.command = command;
            this.process = process;
            this.readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (output) {
                            output.append(line).append("\n");
                        }
                    }
                } catch (IOException ignored) {
                }
                try {
                    exitCode = process.waitFor();
                } catch (InterruptedException ignored) {
                }
                running = false;
            }, "job-" + id);
            this.readerThread.setDaemon(true);
            this.readerThread.start();
        }
    }

    public static class ReadResult {
        public final int byteLength;
        public final String output;
        public final boolean running;
        public final Integer exitCode;
        public final String command;

        ReadResult(JobEntry e, int since, int tailLines) {
            this.command = e.command;
            this.running = e.running;
            this.exitCode = e.exitCode;
            String all;
            synchronized (e.output) {
                all = e.output.toString();
            }
            this.byteLength = all.length();
            String slice = since > 0 && since < all.length() ? all.substring(since) : all;
            if (tailLines > 0) {
                String[] lines = slice.split("\n", -1);
                if (lines.length > tailLines) {
                    slice = Arrays.stream(lines).skip(lines.length - tailLines)
                            .collect(Collectors.joining("\n"));
                }
            }
            this.output = slice.trim();
        }
    }

    public static class WaitResult {
        public final boolean exited;
        public final Integer exitCode;
        public final String latestOutput;

        WaitResult(JobEntry e, int recentChars) {
            this.exitCode = e.exitCode;
            this.exited = !e.running;
            String all;
            synchronized (e.output) {
                all = e.output.toString();
            }
            if (recentChars > 0 && all.length() > recentChars) {
                this.latestOutput = all.substring(all.length() - recentChars);
            } else {
                this.latestOutput = all;
            }
        }
    }
}
