package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * {@link TextLayoutService#prefixWidthsRaw} 与「逐前缀 {@code getStringWidth}」慢路径的
 * 数值对拍测试（2026-09-01 布局绘制防屎山审查 D-3：生产控件走 O(N²) 逐前缀、O(N) 快路径
 * 零调用；切换前必须以数值实证两路等价性，javadoc 自证不算数）。
 *
 * <p>基建同 {@link TextLayoutServiceControlCharTest}：Dialog 字体 + runtimeTables 绑定。
 * 对拍口径：对每个测试串，快路径取整串前缀向量，慢路径逐前缀码点子串调
 * {@code getStringWidth(前缀, UILIB_RAW)}，要求两数组逐位相等。</p>
 */
public class TextLayoutPrefixWidthEquivalenceTest {

    @Test
    public void fastPrefixVectorShouldEqualSlowPerPrefixMeasurement() {
        TextLayoutService service = createService();
        List<String> mismatches = new ArrayList<String>();
        String[] cases = {
                "Hello, World! 123",
                "你好，世界（CJK 全角标点）",
                "abc你好def_下划线-连字符",
                "a\tb",
                "\u0007x",
                "a\u200Bb",
                "\u00E9 \u00E9\u0301 e\u0301",
                "\uD83D\uDE00x",
                "   leading spaces",
                "",
        };
        for (String text : cases) {
            int[] fast = service.prefixWidthsRaw(text, UiFontWeight.NORMAL, UiFontStyle.NORMAL);
            int cpCount = text.codePointCount(0, text.length());
            Assert.assertEquals("快路径长度应为码点数+1: " + quote(text), cpCount + 1, fast.length);
            int[] slow = new int[cpCount + 1];
            slow[0] = 0;
            for (int i = 1; i <= cpCount; i++) {
                slow[i] = service.getStringWidth(codepointPrefix(text, i), TextContentMode.UILIB_RAW);
            }
            if (!Arrays.equals(fast, slow)) {
                mismatches.add("case=" + quote(text) + System.lineSeparator()
                        + "  fast=" + Arrays.toString(fast) + System.lineSeparator()
                        + "  slow=" + Arrays.toString(slow));
            }
        }
        Assert.assertTrue("前缀宽度快/慢两路数值不一致（切换前必须逐条裁决）:" + System.lineSeparator()
                + String.join(System.lineSeparator(), mismatches), mismatches.isEmpty());
    }

    private static String codepointPrefix(String text, int codepoints) {
        int offset = 0;
        for (int i = 0; i < codepoints; i++) {
            offset += Character.charCount(text.codePointAt(offset));
        }
        return text.substring(0, offset);
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c < 0x7F && c != '"') {
                sb.append(c);
            } else {
                sb.append(String.format("\\u%04X", Integer.valueOf(c)));
            }
        }
        return sb.append('"').toString();
    }

    private static TextLayoutService createService() {
        FontCatalog fontCatalog = new FontCatalog();
        fontCatalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        GlyphPageManager glyphPageManager = new GlyphPageManager();
        FontMatcher fontMatcher = new FontMatcher(fontCatalog, derivedFontCache);
        fontMatcher.setRuntimeTables(1, glyphPageManager.getRuntimeTables());
        TextLayoutService service = new TextLayoutService(fontMatcher, glyphPageManager, derivedFontCache);
        service.setRuntimeVersion(1);
        return service;
    }
}