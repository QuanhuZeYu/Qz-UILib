package club.heiqi.uilib.ui.host;

import club.heiqi.uilib.ui.style.UiCursor;

/**
 * HTML-like 文档系统光标宿主。
 *
 * <p>从 {@code ui.document} 上抽出到 {@code ui.host}，让所有"宿主能力抽象"
 * 在同一个包内承载，避免 cursor 这条宿主链路混在文档运行时实现里。</p>
 */
public interface DocumentCursorHost {

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
