package club.heiqi.uilib.net.api;

/**
 * 请求触发限流。
 */
public class NetRateLimitException extends RuntimeException {

    public NetRateLimitException(String message) {
        super(message);
    }
}
