package site.sorghum.loopra.integration.cutin.plugin.httplog;

import lombok.extern.slf4j.Slf4j;
import site.sorghum.cutin.core.context.LoopContext;
import site.sorghum.cutin.core.json.JsonSupport;
import site.sorghum.cutin.core.loop.InterceptContext;
import site.sorghum.cutin.core.loop.InterceptDecision;
import site.sorghum.cutin.core.loop.InterceptPoint;
import site.sorghum.cutin.core.model.ModelCallRequest;
import site.sorghum.cutin.core.plugin.AgentPlugin;
import site.sorghum.cutin.core.plugin.LoopPlugin;
import site.sorghum.cutin.core.plugin.LoopRegistrar;
import site.sorghum.loopra.bin.model.LoopraModelProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 在模型请求发送前将 Cutin 模型请求写入 Loopra HTTP 日志目录。
 *
 * <p>日志按会话分文件保存于 {@code ~/.loopra/logs/http}，每个会话仅保留最近两条，
 * 与 Cutin 重构前的 HTTP 请求日志策略一致。</p>
 */
@Slf4j
@AgentPlugin(id = "loopra-http-log")
public final class LoopraHttpLogPlugin implements LoopPlugin {

    private static final String SESSION_ID_KEY = "sessionId";
    private static final String SEPARATOR = "================================================================================\n";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault());

    private final String apiUrl;
    private final UnaryOperator<ModelCallRequest> requestPreparer;
    private final Path logDirectory;
    private final Clock clock;

    /** 使用默认 Loopra HTTP 日志目录创建插件。 */
    public LoopraHttpLogPlugin(String apiUrl) {
        this(apiUrl, UnaryOperator.identity(), Path.of(System.getProperty("user.home"), ".loopra", "logs", "http"), Clock.systemDefaultZone());
    }

    /** 使用 Loopra Provider 的运行期选项补全逻辑创建插件。 */
    public LoopraHttpLogPlugin(LoopraModelProvider modelProvider) {
        this(
            modelProvider.apiUrl(),
            modelProvider::prepareRequest,
            Path.of(System.getProperty("user.home"), ".loopra", "logs", "http"),
            Clock.systemDefaultZone()
        );
    }

    LoopraHttpLogPlugin(String apiUrl, Path logDirectory, Clock clock) {
        this(apiUrl, UnaryOperator.identity(), logDirectory, clock);
    }

    LoopraHttpLogPlugin(
        String apiUrl,
        UnaryOperator<ModelCallRequest> requestPreparer,
        Path logDirectory,
        Clock clock
    ) {
        this.apiUrl = apiUrl;
        this.requestPreparer = requestPreparer;
        this.logDirectory = logDirectory;
        this.clock = clock;
    }

    @Override
    public String id() {
        return "loopra-http-log";
    }

    @Override
    public void register(LoopRegistrar registrar) {
        registrar.addInterceptor(InterceptPoint.BEFORE_MODEL, 2000, this::writeRequestLog);
    }

    private InterceptDecision writeRequestLog(InterceptContext context) {
        if (context.payload() instanceof ModelCallRequest request) {
            write(context.context(), requestPreparer.apply(request));
        }
        return InterceptDecision.pass();
    }

    private void write(LoopContext context, ModelCallRequest request) {
        String sessionName = sessionName(context);
        Path logFile = logDirectory.resolve(sanitizeFileName(sessionName) + ".log");
        try {
            Files.createDirectories(logDirectory);
            String existing = Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.UTF_8) : "";
            Files.writeString(
                logFile,
                retainLatestEntry(existing) + formatEntry(request),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException exception) {
            log.warn("[api-log] 写入 AI 调用日志失败: {}", exception.getMessage());
        }
    }

    private String formatEntry(ModelCallRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.modelId());
        body.put("messages", request.messages());
        body.put("tools", request.tools());
        if (!request.options().isEmpty()) {
            body.put("options", request.options());
        }
        return SEPARATOR
            + ">>> AI API Call : " + TIMESTAMP_FORMAT.format(Instant.now(clock)) + '\n'
            + ">>> URL         : " + apiUrl + '\n'
            + ">>> Request     :\n"
            + JsonSupport.write(body) + '\n'
            + "<<< END\n\n";
    }

    private static String retainLatestEntry(String existing) {
        String[] entries = existing.split("(?m)^={60,}$");
        for (int index = entries.length - 1; index >= 0; index--) {
            String entry = entries[index].trim();
            if (!entry.isEmpty()) {
                return SEPARATOR + entry + '\n';
            }
        }
        return "";
    }

    private static String sessionName(LoopContext context) {
        Object value = context.variables().get(SESSION_ID_KEY);
        return value instanceof String sessionId && !sessionId.isBlank() ? sessionId : "unknown";
    }

    private static String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
