package site.sorghum.agent4j.web.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import site.sorghum.agent4j.web.market.impl.ClawhubMarket;
import site.sorghum.agent4j.web.market.impl.SkillhubMarket;

import java.util.*;

/**
 * 技能市场管理器 — 管理多个 Market 适配器，根据前端传入的 marketName 选择对应的市场。
 *
 * <p>默认注册 ClawHub 和 SkillHub 两个市场，支持运行时动态添加。</p>
 *
 * @author Sorghum
 */
public class MarketManager {
    private static final Logger LOG = LoggerFactory.getLogger(MarketManager.class);

    private final Map<String, Market> markets = new LinkedHashMap<>();
    private Market defaultMarket;

    public MarketManager() {
        Market skillhub = new SkillhubMarket();
        register(skillhub);

        Market clawhub = new ClawhubMarket();
        register(clawhub);

        this.defaultMarket = skillhub;
    }

    /**
     * 注册一个市场适配器
     */
    public void register(Market market) {
        markets.put(market.name(), market);
        LOG.info("MarketManager: registered market -> {}", market.name());
    }

    /**
     * 根据名称获取市场适配器，找不到则返回默认市场
     */
    public Market getMarketByName(String name) {
        if (name == null || name.isEmpty()) {
            return defaultMarket;
        }
        Market m = markets.get(name);
        return m != null ? m : defaultMarket;
    }

    /**
     * 获取所有已注册市场的名称列表
     */
    public List<String> getMarketNames() {
        return new ArrayList<>(markets.keySet());
    }

    /**
     * 获取所有已注册市场的信息（用于前端下拉选择）
     */
    public List<MarketInfo> getMarketInfos() {
        List<MarketInfo> infos = new ArrayList<>();
        for (Market m : markets.values()) {
            infos.add(new MarketInfo(m.name(), m.description()));
        }
        return infos;
    }

    /**
     * 获取默认市场
     */
    public Market getDefaultMarket() {
        return defaultMarket;
    }

    /**
     * 市场信息实体
     */
    public static class MarketInfo {
        private final String name;
        private final String description;

        public MarketInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }
    }
}
