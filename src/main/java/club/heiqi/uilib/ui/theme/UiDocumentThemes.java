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
                // 页面壳先只启用圆角外观，不在这一刀里接管滚动页面的内容裁剪。
                // 否则 ScrollViewport 的内部滚动/对子内容的矩形 clip 会和圆角 mask 叠加，
                // 让整个文档内容区在真实运行时更容易被错误裁空。
                new UiSurfaceStyle(0xD0151C25, 0xFF86A8F0, 18),
                // surface 只表达外观，不再承载 descendant clip。
                // 页面滚动裁剪与 overflow 结构语义继续由 ScrollViewport/Div 自身负责，
                // 让 shell / card 的圆角外观回到类似 Web 的 border-radius 心智模型。
                new UiSurfaceStyle(0xAA111721, 0xFF6E8FCB, 12),
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
