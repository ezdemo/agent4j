package site.sorghum.agent4j.web.model;

import java.util.List;
import java.util.Map;

/**
 * Rewind 请求体。
 *
 * @author Sorghum
 */
public class RewindRequest {

    /** 回退到第 N 条用户消息（从 0 开始） */
    public int step;
}
