package club.heiqi.uilib.net.api;

/**
 * 网络断开导致请求失败。
 */
public class NetDisconnectedException extends RuntimeException {

    public NetDisconnectedException(String message) {
        super(message);
    }
}
