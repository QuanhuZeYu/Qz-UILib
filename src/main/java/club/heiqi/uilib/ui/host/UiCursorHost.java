package club.heiqi.uilib.ui.host;

import club.heiqi.uilib.ui.base.props.UiCursor;

/**
 * UI 系统光标宿主。
 */
public interface UiCursorHost {

    /**
     * 应用当前 UI 解析出的光标样式。
     *
     * @param cursor 解析后的光标样式；为空时按默认光标处理
     */
    void applyCursor(UiCursor cursor);

    /**
     * 强制把宿主原生光标同步到当前 UI 期望值。
     *
     * <p>仅用于宿主原生状态可能与响应式缓存或宿主缓存错位的边界收口；默认实现保持普通宿主兼容，
     * 不改变 UI 状态源。</p>
     *
     * @param cursor 当前 UI 期望的光标样式；为空时按默认光标处理
     */
    default void forceApplyCursor(UiCursor cursor) {
        applyCursor(cursor);
    }

    /**
     * 返回默认系统光标宿主。
     *
     * @return 系统光标宿主
     */
    static UiCursorHost system() {
        return SystemUiCursorHost.getInstance();
    }
}
