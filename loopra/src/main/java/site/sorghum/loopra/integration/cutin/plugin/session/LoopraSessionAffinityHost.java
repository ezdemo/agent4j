package site.sorghum.loopra.integration.cutin.plugin.session;

/** 会话亲和策略宿主：提供当前 Agent 会话的稳定缓存键。 */
public interface LoopraSessionAffinityHost {
    String sessionAffinity();
}