package club.heiqi.uilib.host.client.font;

/** 标准FontRenderer的MC专有事实，止于入口适配；不向文本核心传递MC对象。 */
public interface FontRendererAccess {
    int qz$fontHeight();

    boolean qz$unicode();

    boolean qz$bidi();

    int[] qz$palette();

    void qz$enableAlpha();

    void qz$tailState(int color);
}
