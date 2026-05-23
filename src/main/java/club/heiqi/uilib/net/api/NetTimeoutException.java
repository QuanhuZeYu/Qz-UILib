package club.heiqi.uilib.net.api;

/**
 * Fetch 请求超时。
 */
public class NetTimeoutException extends RuntimeException {

    public NetTimeoutException(String message) {
        super(message);
    }
}
