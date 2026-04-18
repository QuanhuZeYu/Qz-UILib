package club.heiqi.uilib.ui.theme;

import club.heiqi.uilib.ui.control.UiControlTheme;

/**
 * 文档主题边界入口。
 */
public final class UiDocumentThemes {

    private static final UiDocumentTheme CURRENT_THEME = createCurrentTheme();

    private UiDocumentThemes() {}

    /**
     * 在主题边界装配当前默认文档主题。
     *
     * <p>`UiDocumentTheme` 现在只保留纯值对象语义，
     * 默认控件样式来源统一收口在这个边界类里。</p>
     */
    private static UiDocumentTheme createCurrentTheme() {
        return new UiDocumentTheme(
                new UiSurfaceStyle(0xD0151C25, 0xFF86A8F0),
                new UiSurfaceStyle(0xAA111721, 0xFF6E8FCB),
                new UiControlTheme.LabelStyle(0xFFFFFFFF, false),
                new UiControlTheme.LabelStyle(0xFFD7E3FF, false),
                new UiControlTheme.LabelStyle(0xFFF6D78E, false),
                new UiControlTheme.LabelStyle(0xFFB5D0FF, false),
                UiControlTheme.defaultButtonStyle(),
                UiControlTheme.defaultToggleSwitchStyle(),
                UiControlTheme.defaultSegmentedSelectorStyle(),
                UiControlTheme.defaultTextInputStyle(),
                UiControlTheme.defaultScrollbarStyle(),
                UiControlTheme.defaultInventorySlotGridStyle(),
                16,
                20,
                12,
                12,
                16,
                20,
                12,
                16,
                156
        );
    }

    /**
     * 获取当前默认文档主题。
     *
     * <p>本轮只提供极薄边界，避免入口层继续直接依赖 `UiDocumentTheme.defaultTheme()`。</p>
     *
     * @return 当前主题
     */
    public static UiDocumentTheme current() {
        return CURRENT_THEME;
    }
}
