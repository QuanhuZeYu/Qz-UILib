package club.heiqi.uilib.config;

import java.util.Arrays;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * 字体排序专用列表控件。
 */
public final class FontSortListControl extends AbstractOrderedStringListControl {

    public FontSortListControl(UiDocument document, ForgeConfigTemplateScreen ownerScreen) {
        super(document, ownerScreen);
    }

    @Override
    public FontSortListControl setChangeListener(Runnable changeListener) {
        super.setChangeListener(changeListener);
        return this;
    }

    @Override
    protected String resolveStatusText(String value) {
        if (FontConfig.isFontMissing(value)) {
            return "缺失";
        }
        if (FontConfig.isFontPresent(value)) {
            return "已发现";
        }
        return "未知";
    }

    @Override
    protected int resolveStatusTextColor(String value) {
        if (FontConfig.isFontMissing(value)) {
            return 0xFFFED7AA;
        }
        if (FontConfig.isFontPresent(value)) {
            return 0xFFDCFCE7;
        }
        return 0xFFE2E8F0;
    }

    @Override
    protected int resolveStatusBackgroundColor(String value) {
        if (FontConfig.isFontMissing(value)) {
            return 0xFF9A3412;
        }
        if (FontConfig.isFontPresent(value)) {
            return 0xFF166534;
        }
        return 0xFF475569;
    }

    @Override
    protected String buildSummaryText(String[] currentValues) {
        String[] missingFonts = FontConfig.getMissingFontSnapshot();
        if (missingFonts.length <= 0) {
            return "当前已载入 " + currentValues.length + " 个可排序字体。拖拽模式用于可视化重排，序号模式用于精确移动。";
        }
        return "当前已载入 " + currentValues.length + " 个可排序字体；本次启动未发现 " + missingFonts.length
                + " 个历史配置字体：" + Arrays.toString(missingFonts) + "。";
    }

    @Override
    protected String getEmptyStateText() {
        return "当前尚未发现可排序字体。";
    }
}
