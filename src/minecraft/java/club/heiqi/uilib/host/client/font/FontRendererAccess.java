package club.heiqi.uilib.host.client.font;

/** 提供标准 FontRenderer 的 MC 专有事实，供入口适配转换为中性字体请求。 */
public interface FontRendererAccess {
    int qz$fontHeight();

    boolean qz$unicode();

    boolean qz$bidi();

    int[] qz$palette();

    void qz$enableAlpha();

    void qz$tailState(int color);
}
