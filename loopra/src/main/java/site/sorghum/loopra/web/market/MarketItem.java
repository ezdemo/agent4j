package site.sorghum.loopra.web.market;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 技能市场列表项实体 — 统一封装技能在列表/搜索中的展示信息。
 *
 * @author Sorghum
 */
@Data
@Accessors(chain = true)
public class MarketItem {
    private String slug;
    private String name;
    private String displayName;
    private String summary;
    private String description;
    private String ownerHandle;
    /** 技能市场详情页 URL */
    private String url;
    private long installs;
    private long stars;
}
