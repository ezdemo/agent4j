package site.sorghum.cutin.core.context;

import site.sorghum.cutin.core.model.ModelGateway;
import site.sorghum.cutin.core.state.LoopSnapshot;
import site.sorghum.cutin.core.tool.ToolRegistry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 循环执行期间的统一上下文。
 *
 * <p>一个 {@code LoopContext} 承载当前循环的全部可变状态：消息列表、变量、
 * 产物、用量与预算，同时暴露模型网关和工具注册表，供 Step 与插件访问。
 * 所有修改上下文的操作都会递增 {@code stateVersion}，从而支持快照与重入。</p>
 */
public interface LoopContext {

    /** 当前循环的唯一标识。 */
    String id();

    /** 当前上下文版本号，每次状态变更后递增。 */
    long stateVersion();

    /** 只读的消息列表。 */
    List<Message> messages();

    /** 只读的变量表。 */
    Map<String, Object> variables();

    /** 只读的产物表。 */
    Map<String, Object> artifacts();

    /** 截至当前的累计用量。 */
    Usage usage();

    /** 本次循环的预算约束。 */
    Budget budget();

    /** 模型网关，负责按模型路由到具体 Provider 并执行拦截链。 */
    ModelGateway models();

    /** 工具注册表，负责查找并执行工具。 */
    ToolRegistry tools();

    /**
     * 当前循环的工作目录。
     *
     * <p>工具中的相对文件操作应以该目录为基准。返回 {@code null} 表示调用方
     * 未提供工作目录，工具应自行决定是否降级处理。</p>
     */
    default Path workingDirectory() {
        return null;
    }

    /** 生成当前状态的可持久化快照。 */
    LoopSnapshot snapshot();

    /** 追加一条消息并递增上下文版本。 */
    void appendMessage(Message message);

    /** 整体替换消息列表（例如上下文压缩后）并递增版本。 */
    void replaceMessages(List<Message> messages);

    /** 写入一个变量并递增版本。 */
    void putVariable(String key, Object value);

    /** 写入一个产物并递增版本。 */
    void putArtifact(String name, Object value);

    /** 累加用量并同时扣减预算。 */
    void addUsage(Usage usage);
}
