package club.heiqi.uilib.font.event;

/**
 * 字体系统重载请求。
 */
public class FontReloadRequest {

    private final String reason;

    /**
     * 创建一条重载请求。
     *
     * @param reason 重载原因
     */
    public FontReloadRequest(String reason) {
        this.reason = reason;
    }

    /**
     * 获取重载原因。
     *
     * @return 重载原因
     */
    public String getReason() {
        return reason;
    }
}
