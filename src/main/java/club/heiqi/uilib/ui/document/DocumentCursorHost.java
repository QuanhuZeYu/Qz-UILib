package club.heiqi.uilib.ui.document;

import club.heiqi.uilib.ui.style.UiCursor;

/**
 * HTML-like 文档系统光标宿主。
 */
interface DocumentCursorHost {

    /**
     * 应用当前命中元素解析出的光标样式。
     *
     * @param cursor 解析后的光标样式；为空时按默认光标处理
     */
    void applyCursor(UiCursor cursor);

    /**
     * 返回默认系统光标宿主。
     *
     * @return 系统光标宿主
     */
    static DocumentCursorHost system() {
        return SystemDocumentCursorHost.getInstance();
    }
}
