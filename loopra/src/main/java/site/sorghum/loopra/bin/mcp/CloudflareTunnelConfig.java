package site.sorghum.loopra.bin.mcp;

/**
 * Cloudflare Quick Tunnel 本地配置。
 *
 * <p>Quick Tunnel 本身是临时隧道，不保存 Cloudflare 账号信息；这里只保存可选的
 * {@code cloudflared} 可执行文件路径。路径为空时，服务会按当前操作系统从 PATH 中查找。</p>
 */
public class CloudflareTunnelConfig {

    /** 可选。为空时使用 PATH 中的 cloudflared。 */
    public String executablePath = "";

    public CloudflareTunnelConfig() {
    }
}
