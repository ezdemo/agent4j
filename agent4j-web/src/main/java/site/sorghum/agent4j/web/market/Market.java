package site.sorghum.agent4j.web.market;

import java.nio.file.Path;
import java.util.List;

/**
 * 技能市场接口 — 抽象技能市场的浏览、搜索、安装能力。
 *
 * <p>不同市场（ClawHub、SkillHub 等）可实现此接口，由 MarketManager 注入管理。</p>
 *
 * @author Sorghum
 */
public interface Market {
    /**
     * 获取市场名称
     */
    String name();

    /**
     * 获取市场描述
     */
    default String description() {
        return "";
    }

    /**
     * 获取热门技能列表
     */
    List<MarketItem> trending(int limit);

    /**
     * 搜索技能
     */
    List<MarketItem> search(String query, int limit);

    /**
     * 获取技能详情
     */
    MarketDetail detail(String slug);

    /**
     * 安装技能 — 下载并解压到指定目录
     *
     * @return 安装的技能显示名称
     */
    String install(String slug, Path skillsDir);
}
