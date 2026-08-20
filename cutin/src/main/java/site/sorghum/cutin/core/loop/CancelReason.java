package site.sorghum.cutin.core.loop;

/**
 * 取消原因，内置用户取消与预算取消两种常用原因。
 */
public record CancelReason(String reason) {

    /** 用户主动取消。 */
    public static final CancelReason USER = new CancelReason("user");

    /** 预算超限取消。 */
    public static final CancelReason BUDGET = new CancelReason("budget");
}
