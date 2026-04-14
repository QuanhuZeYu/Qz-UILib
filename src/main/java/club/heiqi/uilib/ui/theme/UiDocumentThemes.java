package club.heiqi.uilib.ui.theme;

/**
 * 文档主题边界入口。
 */
public final class UiDocumentThemes {

    private UiDocumentThemes() {}

    /**
     * 获取当前默认文档主题。
     *
     * <p>本轮只提供极薄边界，避免入口层继续直接依赖 `UiDocumentTheme.defaultTheme()`。</p>
     *
     * @return 当前主题
     */
    public static UiDocumentTheme current() {
        return UiDocumentTheme.defaultTheme();
    }
}
