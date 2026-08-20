package site.sorghum.cutin.core.plugin;

import java.util.List;

/** 可关闭的插件扩展注册句柄。 */
@FunctionalInterface
public interface Registration extends AutoCloseable {

    /** 注销扩展；重复调用必须安全。 */
    @Override
    void close();

    /** 返回一个无操作注册句柄。 */
    static Registration noop() {
        return () -> { };
    }

    /** 将多个句柄组合成一个按逆序关闭的句柄。 */
    static Registration composite(List<Registration> registrations) {
        List<Registration> copy = List.copyOf(registrations);
        return new Registration() {
            private boolean closed;

            @Override
            public synchronized void close() {
                if (closed) {
                    return;
                }
                closed = true;
                for (int i = copy.size() - 1; i >= 0; i--) {
                    copy.get(i).close();
                }
            }
        };
    }
}