package site.sorghum.cutin.core.plugin;

import java.util.List;

/**
 * 插件市场 SPI：列出可安装的插件包。
 */
public interface PluginMarketplace {

    /** 返回市场中的全部插件包信息。 */
    List<PluginPackageInfo> list();
}
