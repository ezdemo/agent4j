package site.sorghum.loopra.integration.cutin.plugin.cancel;

/**
  * cutin 循环被取消时通知的宿主切片。
 */
public interface LoopraCancelHost {

    void onCutinCancel(String reason);
}
