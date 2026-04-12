package club.heiqi.uilib.ui.theme;

import club.heiqi.uilib.ui.control.DivWidget;
import club.heiqi.uilib.ui.control.DocumentShellWidget;
import club.heiqi.uilib.ui.control.LabelWidget;

/**
 * 文档页默认视觉主题。
 *
 * <p>结构由页面自行组合，视觉常量集中收口在主题层。</p>
 */
public final class UiDocumentTheme {

    private static final int SHELL_FILL_COLOR = 0xD0151C25;
    private static final int SHELL_BORDER_COLOR = 0xFF86A8F0;
    private static final int CARD_FILL_COLOR = 0xAA111721;
    private static final int CARD_BORDER_COLOR = 0xFF6E8FCB;
    private static final int TITLE_TEXT_COLOR = 0xFFFFFFFF;
    private static final int BODY_TEXT_COLOR = 0xFFD7E3FF;
    private static final int EMPHASIS_TEXT_COLOR = 0xFFF6D78E;
    private static final int SECONDARY_TEXT_COLOR = 0xFFB5D0FF;

    private UiDocumentTheme() {}

    /**
     * 应用文档壳表面样式。
     *
     * @param shell 文档壳
     * @return 文档壳
     */
    public static DocumentShellWidget applyShellSurface(DocumentShellWidget shell) {
        return shell.setShellFillColor(SHELL_FILL_COLOR).setShellBorderColor(SHELL_BORDER_COLOR);
    }

    /**
     * 应用卡片表面样式。
     *
     * @param widget 目标容器
     * @return 目标容器
     */
    public static DivWidget applyCardSurface(DivWidget widget) {
        return widget.setPadding(20).setFillColor(CARD_FILL_COLOR).setBorderColor(CARD_BORDER_COLOR);
    }

    /**
     * 应用页面标题文字样式。
     *
     * @param label 目标标签
     * @return 目标标签
     */
    public static LabelWidget applyTitleText(LabelWidget label) {
        return label.setColor(TITLE_TEXT_COLOR).setShadow(false);
    }

    /**
     * 应用正文文字样式。
     *
     * @param label 目标标签
     * @return 目标标签
     */
    public static LabelWidget applyBodyText(LabelWidget label) {
        return label.setColor(BODY_TEXT_COLOR).setShadow(false);
    }

    /**
     * 应用强调文字样式。
     *
     * @param label 目标标签
     * @return 目标标签
     */
    public static LabelWidget applyEmphasisText(LabelWidget label) {
        return label.setColor(EMPHASIS_TEXT_COLOR).setShadow(false);
    }

    /**
     * 应用次级说明文字样式。
     *
     * @param label 目标标签
     * @return 目标标签
     */
    public static LabelWidget applySecondaryText(LabelWidget label) {
        return label.setColor(SECONDARY_TEXT_COLOR).setShadow(false);
    }
}
