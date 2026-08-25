package club.heiqi.uilib.internal.chat3.viewmodel;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * GTNH 欢迎消息真机换行丢字符复现测试(K3 三轮评审 任务 A)。
 *
 * <p>与 {@code ChatSceneController.uiLibMeasure} 同构的<b>真实字体度量</b>
 * (TextLayoutService.parseSegments + getSegmentWidth,Dialog 字体真 advance 口径,
 * 无固定码点宽注入),在 maxLineWidthPx=340(= chatWidth 360 − 2×气泡内边距 10)与
 * 269/297/300 诊断宽度上跑 HEAD {@link ChatLineLayouter#splitLines},逐行打印输出并断言:</p>
 * <ul>
 *   <li>零可见字符丢失(剥 § 格式码对与空白后完整保序);</li>
 *   <li>纯散文消息(§e 黄/§9 蓝)每个行 junction 都在空白处——任何"词中间断行"
 *       (真机"hotkey t|use it."、"check yo|keybindings." 型)都无法由 HEAD 产生。</li>
 * </ul>
 *
 * <p>无空格 URL(§6 GitHub issues 行)按设计稿 §5.4 word-break:anywhere 允许字符级断行,
 * 只断言零丢失("GT-New-H|ns-Modpack" 丢 "orizo" 型不可能)。</p>
 */
public class GtnhWelcomeLineBreakRealMetricsTest {

    /** 真机同款 ground truth(评审摘要 §7,含 § 格式码)。 */
    private static final String[] GTNH_WELCOME = {
            "\u00a76\u00a7m-----------------------------------------------------",
            "\u00a7lWelcome to GregTech: New Horizons \u00a722.9.x",
            "\u00a7eSee what's new in the guide! Bind a hotkey to use it.",
            "\u00a79The Quest Book has a shortcut key, check your keybindings.",
            "\u00a72GTNH Wiki link: https://wiki.gtnewhorizons.com/wiki/",
            "\u00a7aPlease report bugs on GitHub:",
            "\u00a76https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues",
            "\u00a79Visit our Discord at https://discord.gg/gtnh"
    };

    /** 逐词断言断行点必须落在空白处的消息下标(纯散文;混 URL 行允许字符断不参与)。 */
    private static final int[] PROSE_MESSAGE_INDEXES = { 1, 2, 3 };

    /** 复现主宽度(chatWidth 360 − 2×10)。 */
    private static final int MAIN_WIDTH = 340;

    /** 诊断宽度:269 = maxBubble(289)−2×paddingX 钳宽;297 ≈ 真机实测定行宽;300。 */
    private static final int[] DIAGNOSTIC_WIDTHS = { 269, 297, 300, 340 };

    private static TextLayoutService service;

    private static synchronized TextLayoutService service() {
        if (service == null) {
            FontCatalog fontCatalog = new FontCatalog();
            fontCatalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
            DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
            GlyphPageManager glyphPageManager = new GlyphPageManager();
            service = new TextLayoutService(new FontMatcher(fontCatalog, derivedFontCache),
                    glyphPageManager, derivedFontCache);
            service.setRuntimeVersion(1);
        }
        return service;
    }

    /** 与 ChatSceneController.uiLibMeasure 同构的生产度量口径。 */
    private static ChatLineLayouter.Measure realMeasure() {
        final TextLayoutService s = service();
        return new ChatLineLayouter.Measure() {
            @Override
            public float advance(String text, int fontSizePx) {
                float total = 0.0F;
                for (TextSegment segment : s.parseSegments(text, 0xFFFFFFFF)) {
                    total += (float) s.getSegmentWidth(segment, fontSizePx);
                }
                return total;
            }

            @Override
            public int epoch() {
                return 0;
            }
        };
    }

    @Test
    public void headSplitLinesNeverLosesCharactersNorBreaksMidWordAt340() {
        ChatLineLayouter.Measure measure = realMeasure();
        for (int idx = 0; idx < GTNH_WELCOME.length; idx++) {
            String text = GTNH_WELCOME[idx];
            for (String indent : new String[] { "", "     " }) {
                String sample = indent + text;
                List<String> lines = ChatLineLayouter.splitLines(sample, MAIN_WIDTH, measure, 13);
                System.out.println("=== msg[" + idx + "] indent=" + indent.length()
                        + " width=" + MAIN_WIDTH + " ===");
                for (String line : lines) {
                    System.out.println("  L:[" + line + "] w="
                            + Math.round(measure.advance(line, 13) * 10.0F) / 10.0F);
                }
                Assert.assertEquals("零可见字符丢失(msg" + idx + ", indent " + indent.length() + ")",
                        compact(sample), compact(join(lines)));
                for (String line : lines) {
                    Assert.assertFalse("任何行可见内容不得为空(msg" + idx + "):" + line,
                            compact(line).isEmpty());
                }
                if (isProse(idx)) {
                    Assert.assertTrue("散文断行必须落在空白处(msg" + idx + ")",
                            proseBreaksAtWhitespace(sample, lines));
                }
            }
        }
    }

    @Test
    public void headSplitLinesDiagnosticSweepAcrossWidths() {
        ChatLineLayouter.Measure measure = realMeasure();
        for (int width : DIAGNOSTIC_WIDTHS) {
            for (int idx = 0; idx < GTNH_WELCOME.length; idx++) {
                String text = GTNH_WELCOME[idx];
                for (String indent : new String[] { "", "     " }) {
                    String sample = indent + text;
                    List<String> lines = ChatLineLayouter.splitLines(sample, width, measure, 13);
                    Assert.assertEquals("零丢失(msg" + idx + " w" + width + " i" + indent.length() + ")",
                            compact(sample), compact(join(lines)));
                    if (isProse(idx)) {
                        Assert.assertTrue("散文词边界断行(msg" + idx + " w" + width + " i"
                                + indent.length() + ")", proseBreaksAtWhitespace(sample, lines));
                    }
                }
            }
        }
        // 打印 297px(真机实测行宽)下的关键行供对照
        System.out.println("=== 诊断对照: width=297 indent=5 ===");
        for (int idx : new int[] { 2, 3, 6 }) {
            List<String> lines = ChatLineLayouter.splitLines(
                    "     " + GTNH_WELCOME[idx], 297, measure, 13);
            for (String line : lines) {
                System.out.println("  msg" + idx + " L:[" + line + "] w="
                        + Math.round(measure.advance(line, 13) * 10.0F) / 10.0F);
            }
        }
        System.out.println("=== 诊断对照: width=340 indent=5 ===");
        for (int idx : new int[] { 2, 3, 6 }) {
            List<String> lines = ChatLineLayouter.splitLines(
                    "     " + GTNH_WELCOME[idx], 340, measure, 13);
            for (String line : lines) {
                System.out.println("  msg" + idx + " L:[" + line + "] w="
                        + Math.round(measure.advance(line, 13) * 10.0F) / 10.0F);
            }
        }
    }

    private static boolean isProse(int idx) {
        for (int prose : PROSE_MESSAGE_INDEXES) {
            if (prose == idx) {
                return true;
            }
        }
        return false;
    }

    /** 剥 § 格式码对与全部空白,仅留可见字符序列(丢字符判定口径,与 ChatLineLayouterTest 同)。 */
    private static String compact(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String join(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append(line);
        }
        return out.toString();
    }

    /**
     * 词边界不变量:剥 § 后各行按序串接(行间允许任意空白 = 行尾空白丢弃语义),
     * 必须与原文(剥 §)完全匹配;任何"词中间断行"(junction 处丢失/跳过非空白字符)
     * 都无法匹配。
     */
    private static boolean proseBreaksAtWhitespace(String text, List<String> lines) {
        String visible = stripSections(text);
        StringBuilder pattern = new StringBuilder();
        for (String line : lines) {
            if (pattern.length() > 0) {
                pattern.append("\\s*");
            }
            pattern.append(Pattern.quote(stripSections(line)));
        }
        return Pattern.compile(pattern.toString()).matcher(visible).matches();
    }

    private static String stripSections(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }
}
