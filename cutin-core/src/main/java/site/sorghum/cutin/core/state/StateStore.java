package site.sorghum.cutin.core.state;

import java.util.List;
import java.util.Optional;

/**
 * 状态存储 SPI：保存与读取循环快照。
 */
public interface StateStore {

    /** 保存一个快照。 */
    void save(LoopSnapshot snapshot);

    /** 读取某个循环的最新快照。 */
    Optional<LoopSnapshot> latest(String loopId);

    /** 读取某个循环指定版本的快照。 */
    Optional<LoopSnapshot> version(String loopId, long stateVersion);

    /** 读取某个循环的全部历史快照。 */
    List<LoopSnapshot> history(String loopId);
}
