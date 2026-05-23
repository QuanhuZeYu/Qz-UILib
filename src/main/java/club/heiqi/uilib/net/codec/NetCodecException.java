package club.heiqi.uilib.net.codec;

/**
 * 网络 codec 编解码失败。
 */
public class NetCodecException extends RuntimeException {

    /**
     * 创建 codec 异常。
     *
     * @param message 错误信息
     */
    public NetCodecException(String message) {
        super(message);
    }

    /**
     * 创建 codec 异常。
     *
     * @param message 错误信息
     * @param cause 原始异常
     */
    public NetCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
