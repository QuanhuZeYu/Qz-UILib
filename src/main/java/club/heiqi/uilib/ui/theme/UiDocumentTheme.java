package club.heiqi.uilib.ui.theme;

import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.control.DocumentShellWidget;
import club.heiqi.uilib.ui.control.LabelWidget;
import club.heiqi.uilib.ui.control.UiControlTheme;

/**
 * 文档页默认视觉主题。
 *
 * <p>结构由页面自行组合，视觉常量集中收口在主题层。</p>
 */
public final class UiDocumentTheme {

    private static final UiSurfaceStyle SHELL_SURFACE = new UiSurfaceStyle(0xD0151C25, 0xFF86A8F0);
    private static final UiSurfaceStyle CARD_SURFACE = new UiSurfaceStyle(0xAA111721, 0xFF6E8FCB);
    private static final UiControlTheme.LabelStyle TITLE_LABEL_STYLE = new UiControlTheme.LabelStyle(0xFFFFFFFF, false);
    private static final UiControlTheme.LabelStyle BODY_LABEL_STYLE = new UiControlTheme.LabelStyle(0xFFD7E3FF, false);
    private static final UiControlTheme.LabelStyle EMPHASIS_LABEL_STYLE = new UiControlTheme.LabelStyle(0xFFF6D78E, false);
    private static final UiControlTheme.LabelStyle SECONDARY_LABEL_STYLE = new UiControlTheme.LabelStyle(0xFFB5D0FF, false);

    private UiDocumentTheme() {}

    /**
     * 应用文档壳表面样式。
     *
     * @param shell 文档壳
     * @return 文档壳
     */
    public static DocumentShellWidget applyShellSurface(DocumentShellWidget shell) {
        return shell.setShellSurfaceStyle(SHELL_SURFACE);
    }

    /**
     * 应用卡片表面样式。
     *
     * @param widget 目标容器
     * @return 目标容器
     */
    public static DivWidget applyCardSurface(DivWidget widget) {
        return widget.setPadding(20).setSurfaceStyle(CARD_SURFACE);
    }

    /**
     * 应用页面标题文字样式。
     *
     * @param label 目标标签
     * @return 目标标签
     */
    public static LabelWidget applyTitleText(LabelWidget label) {
        return label.setStyle(TITLE_LABEL_STYLE);
    }

    /**
     * 应用正文文字样式。
     *
     * @param label 目标标签
     * @return 目标标签
     */
    public static LabelWidget applyBodyText(LabelWidget label) {
        return label.setStyle(BODY_LABEL_STYLE);
    }

    /**
     * 应用强调文字样式。
     *
     * @param label 目标标签
     * @return 目标标签
     */
    public static LabelWidget applyEmphasisText(LabelWidget label) {
        return label.setStyle(EMPHASIS_LABEL_STYLE);
    }

    /**
     * 应用次级说明文字样式。
     *
     * @param label 目标标签
     * @return 目标标签
     */
    public static LabelWidget applySecondaryText(LabelWidget label) {
        return label.setStyle(SECONDARY_LABEL_STYLE);
    }
}
