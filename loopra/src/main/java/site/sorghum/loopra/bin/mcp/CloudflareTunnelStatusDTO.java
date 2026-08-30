package site.sorghum.loopra.bin.mcp;

/** Cloudflare Quick Tunnel 运行状态。 */
public class CloudflareTunnelStatusDTO {

    public String state;
    public boolean running;
    public boolean installed;
    public boolean quickTunnel;
    public boolean mcpReady;
    public String platform;
    /** 设置中保存的路径；为空时表示使用 PATH 或 CLOUDFLARED_PATH。 */
    public String configuredExecutablePath;
    /** 当前实际解析出的可执行文件名或路径。 */
    public String executablePath;
    public String localUrl;
    public String publicUrl;
    public String mcpEndpoint;
    public String error;
    public long pid;

    public CloudflareTunnelStatusDTO() {
    }

    public CloudflareTunnelStatusDTO(String state, boolean running, boolean installed,
                                     boolean quickTunnel, boolean mcpReady, String platform,
                                     String configuredExecutablePath, String executablePath,
                                     String localUrl, String publicUrl,
                                     String mcpEndpoint, String error, long pid) {
        this.state = state;
        this.running = running;
        this.installed = installed;
        this.quickTunnel = quickTunnel;
        this.mcpReady = mcpReady;
        this.platform = platform;
        this.configuredExecutablePath = configuredExecutablePath;
        this.executablePath = executablePath;
        this.localUrl = localUrl;
        this.publicUrl = publicUrl;
        this.mcpEndpoint = mcpEndpoint;
        this.error = error;
        this.pid = pid;
    }
}
