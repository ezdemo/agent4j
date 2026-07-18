package site.sorghum.loopra.web.model;

import lombok.Data;

/** Electron 客户端登记本机浏览器桥接服务时使用的请求体。 */
@Data
public class BrowserBridgeRequest {
    private String address;
}
