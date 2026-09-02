package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * {@link TextLayoutService} 换行的字号感知测试（真机同源度量实现，非替身）。
 *
 * <h3>钉的是什么</h3>
 * <p>带 {@link TextMeasureStyle} 的换行入口必须按<b>样式字号</b>逐码点测量。历史缺陷：
 * 场景桥接层 {@code splitLines} 收到节点 {@code fontSizePx} 却调不带宽度的旧入口，测量退回
 * 基准 {@code charSize}（默认 9），而渲染按节点字号（16）绘制 —— 行宽被低估约 1.78 倍，
 * 每行实际渲染宽度溢出容器，真机表现为「弹窗长 URL 首行右侧凭空消失」，布局与拆行都不报错。
 * 同一族缺陷此前已为 {@code trimToWidth} 修过一次并留了回归用例，姊妹方法 {@code wrap} 被漏下。</p>
 */
public class TextLayoutServiceWrapFontSizeTest {

    private static final String TEN = "AAAAAAAAAA";

    /** 字号翻倍后，同一换行宽度必须折出更多行（不带宽度的旧入口恒按基准字号量）。 */
    @Test
    public void rawWrapMustMeasureAtStyleFontSize() {
        TextLayoutService service = createService('A');
        int baseSize = Math.max(1, (int) FontRuntimeSettings.capture().getCharSize());
        // 基准字号下恰好容得下 5 个 A
        int unit = service.getStringWidth("AAAAA", TextContentMode.UILIB_RAW);
        Assert.assertTrue("基准字号下 5 字符宽度为正", unit > 0);

        List<String> atBase = service.listFormattedStringToWidth(TEN, unit, TextContentMode.UILIB_RAW);
        List<String> atDouble = service.listFormattedStringToWidth(TEN, unit,
                new TextMeasureStyle(baseSize * 2, TextContentMode.UILIB_RAW,
                        UiFontWeight.NORMAL, UiFontStyle.NORMAL));

        Assert.assertEquals("基准字号：10 字符折 2 行", 2, atBase.size());
        Assert.assertTrue("字号翻倍后行数必须增加（实测 " + atDouble.size() + " 行 vs 基准 "
                + atBase.size() + " 行）", atDouble.size() > atBase.size());
    }

    /**
     * 每行宽度按<b>该字号</b>复测，不得超过换行宽 —— 溢出正是真机被裁的直接量。
     */
    @Test
    public void everyRawWrapLineMustFitWrapWidthAtItsOwnFontSize() {
        TextLayoutService service = createService('A');
        int baseSize = Math.max(1, (int) FontRuntimeSettings.capture().getCharSize());
        int wrapWidth = service.getStringWidth("AAAAA", TextContentMode.UILIB_RAW);
        int fontSize = baseSize * 2;

        List<String> lines = service.listFormattedStringToWidth(TEN, wrapWidth,
                new TextMeasureStyle(fontSize, TextContentMode.UILIB_RAW,
                        UiFontWeight.NORMAL, UiFontStyle.NORMAL));

        for (int i = 0; i < lines.size(); i++) {
            int lineWidth = service.getStringWidth(lines.get(i),
                    new TextMeasureStyle(fontSize, TextContentMode.UILIB_RAW,
                            UiFontWeight.NORMAL, UiFontStyle.NORMAL));
            Assert.assertTrue("行 " + i + " 宽 " + lineWidth + " 不得超过换行宽 " + wrapWidth,
                    lineWidth <= wrapWidth);
        }
    }

    private static TextLayoutService createService(int... fixedWidthCodepoints) {
        FontCatalog fontCatalog = new FontCatalog();
        fontCatalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        GlyphPageManager glyphPageManager = new GlyphPageManager();
        float[] normalWidths = glyphPageManager.getRuntimeTables().widthArray(FontType.NORMAL);
        float[] boldWidths = glyphPageManager.getRuntimeTables().widthArray(FontType.BOLD);
        for (int codepoint : fixedWidthCodepoints) {
            normalWidths[codepoint] = 1.0F;
            boldWidths[codepoint] = 1.0F;
        }
        TextLayoutService service = new TextLayoutService(new FontMatcher(fontCatalog, derivedFontCache),
                glyphPageManager, derivedFontCache);
        service.setRuntimeVersion(1);
        return service;
    }
}
