package site.sorghum.agent4j.web.market;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 技能详情实体 — 统一封装技能的完整信息。
 *
 * @author Sorghum
 */
@Data
@Accessors(chain = true)
public class MarketDetail {
    private String slug;
    private String displayName;
    private String summary;
    private String description;
    private String ownerHandle;
    private long installs;
    private long stars;
    /** 用于安装时的实际 slug */
    private String installSlug;
}
