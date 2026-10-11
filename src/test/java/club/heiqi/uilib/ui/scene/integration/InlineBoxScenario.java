package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.InlineBox;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

import java.util.Arrays;
import java.util.Collections;

/** 公共内联盒真实FT/HB场景；直接验收Unicode、Bidi及行度量，不复制排版算法。 */
public final class InlineBoxScenario extends TestApplication {
    static AttributedText input(
            String text, TextStyle style, float width, float height, float baseline) {
        return AttributedText.plain(TextInput.string(text), style)
                .withInlineBoxes(
                        Collections.singletonList(
                                new InlineBox(text.indexOf('￼'), width, height, baseline)));
    }

    static TextLayout check(
            FontTextService fonts,
            String name,
            String text,
            float available,
            float width,
            float height,
            float baseline,
            TextPolicy policy,
            TextStyle style) {
        AttributedText request = input(text, style, width, height, baseline);
        TextLayout value = fonts.layout(request, available, policy);
        int offset = text.indexOf('￼'), count = 0;
        for (TextLayout.Line line : value.lines()) {
            for (TextLayout.Glyph g : line.glyphs())
                require(!(g.logicalStart <= offset && g.logicalEnd > offset), "对象错误进入glyph");
            for (TextLayout.Cluster c : line.clusters())
                if (c.logicalStart == offset) {
                    count++;
                    require(c.logicalEnd == offset + 1, "对象被组合字符吞并");
                    require(c.glyphIndexStart == c.glyphIndexEnd, "对象伪造glyph");
                    require(c.advance == width, "对象宽度未保留");
                    float top = line.baseline - baseline;
                    // 显式行高允许真实对象溢出；只有默认行高需要容纳本行联合度量。
                    if (policy.lineHeight > 0) {
                        require(line.lineHeight == policy.lineHeight, "对象隐式覆盖显式行高");
                    } else {
                        require(
                                top >= line.y - 0.001f
                                        && top + height <= line.y + line.lineHeight + 1.001f,
                                "默认行高未容纳对象");
                    }
                    require(
                            line.ascent >= baseline && line.descent >= height - baseline,
                            "对象ascent/descent遗漏");
                }
        }
        require(count == 1, "对象应只有一个不可拆分cluster");
        for (TextLayout.Diagnostic d : value.diagnostics())
            require(d.kind != TextLayout.Diagnostic.Kind.MISSING_GLYPH, "对象引发字体缺字诊断");
        TextLayout projected = fonts.obfuscate(value, request, 1, 1);
        int projectedCount = 0;
        for (TextLayout.Line l : projected.lines())
            for (TextLayout.Cluster c : l.clusters())
                if (c.logicalStart == offset) {
                    projectedCount++;
                    require(c.advance == width && c.glyphIndexStart == c.glyphIndexEnd, "混淆投影损坏对象");
                }
        require(projectedCount == 1, "混淆投影遗失对象");
        TextLayout inferred = fonts.obfuscate(value, 1, 1);
        int inferredCount = 0;
        for (TextLayout.Line l : inferred.lines())
            for (TextLayout.Cluster c : l.clusters())
                if (c.logicalStart == offset) {
                    inferredCount++;
                    require(
                            c.advance == width && c.glyphIndexStart == c.glyphIndexEnd,
                            "无request混淆损坏对象");
                }
        require(inferredCount == 1, "无request混淆遗失对象");
        System.out.println(
                "inline-box "
                        + name
                        + " lines="
                        + value.lines().size()
                        + " advance="
                        + value.advanceWidth
                        + " height="
                        + value.height
                        + " generation="
                        + value.generation);
        return value;
    }

    public static void main(String[] args) throws Exception {
        new InlineBoxScenario().run(args);
    }

    private FontTextService fonts;
    private TextStyle style;

    @Override
    protected void setup() throws Exception {
        System.out.println("java=" + System.getProperty("java.version"));
        fonts =
                own(
                        new FontTextService(
                                FontTextService.defaultSources(getClass().getClassLoader(), 1),
                                new LwjglFontEngine(),
                                4,
                                8,
                                4096));
        style = new TextStyle("default", 24, -1);
    }

    @Override
    protected void runTests() throws Exception {
        TextPolicy rtl =
                new TextPolicy(TextPolicy.Direction.RTL, TextPolicy.Wrap.EMERGENCY, "he", 0, 0);
        TextPolicy tight =
                new TextPolicy(TextPolicy.Direction.AUTO, TextPolicy.Wrap.EMERGENCY, "und", 0, 3);
        scenario(
                "LTR对象度量与无字形簇",
                () -> check(fonts, "LTR", "ab ￼ cd", 500, 43, 60, 47, TextPolicy.NORMAL, style));
        scenario(
                "RTL对象视觉顺序",
                () -> {
                    TextLayout order =
                            check(fonts, "RTL-visual-order", "א￼ב", 500, 43, 60, 47, rtl, style);
                    TextLayout.Cluster before = null, object = null, after = null;
                    for (TextLayout.Cluster c : order.lines().get(0).clusters()) {
                        if (c.logicalStart == 0) before = c;
                        else if (c.logicalStart == 1) object = c;
                        else if (c.logicalStart == 2) after = c;
                    }
                    require(
                            before != null
                                    && object != null
                                    && after != null
                                    && object.rtl
                                    && after.x < object.x
                                    && object.x < before.x,
                            "对象未参与RTL视觉重排");
                });
        scenario(
                "RTL段落对象换行", () -> check(fonts, "RTL", "שלום ￼ עולם", 100, 43, 60, 47, rtl, style));
        scenario(
                "组合字符邻接对象原子边界",
                () ->
                        check(
                                fonts,
                                "combining",
                                "á ￼́ b",
                                90,
                                43,
                                60,
                                47,
                                TextPolicy.NORMAL,
                                style));
        scenario(
                "Emoji序列邻接对象",
                () ->
                        check(
                                fonts,
                                "emoji",
                                "👩‍💻 ￼ x",
                                120,
                                43,
                                60,
                                47,
                                TextPolicy.NORMAL,
                                style));
        scenario(
                "超宽对象保持完整advance",
                () -> {
                    TextLayout over =
                            check(
                                    fonts,
                                    "oversized",
                                    "x ￼ y",
                                    10,
                                    73,
                                    80,
                                    20,
                                    TextPolicy.NORMAL,
                                    style);
                    require(over.advanceWidth >= 73, "超宽对象被裁成小advance");
                });
        scenario(
                "显式小行高保留声明与真实溢出对象",
                () -> {
                    TextStyle large = new TextStyle("default", 28, -1);
                    TextLayout plain =
                            fonts.layout(
                                    AttributedText.plain(TextInput.string("xy"), large),
                                    500,
                                    tight);
                    TextLayout explicit =
                            check(
                                    fonts,
                                    "explicit-line-height",
                                    "x￼y",
                                    500,
                                    20,
                                    75,
                                    10,
                                    tight,
                                    large);
                    TextLayout automatic =
                            check(
                                    fonts,
                                    "default-line-height",
                                    "x￼y",
                                    500,
                                    20,
                                    75,
                                    10,
                                    TextPolicy.NORMAL,
                                    large);
                    TextLayout.Line e = explicit.lines().get(0), a = automatic.lines().get(0);
                    require(
                            plain.height == 3
                                    && explicit.height == 3
                                    && explicit.advanceHeight == 3,
                            "显式行高因对象改变布局高度");
                    require(
                            e.ascent == a.ascent
                                    && e.descent == a.descent
                                    && e.baseline == a.baseline,
                            "显式行高压缩真实度量或基线");
                    require(e.glyphs().size() == a.glyphs().size(), "显式行高改变字形数量");
                    for (int i = 0; i < e.glyphs().size(); i++) {
                        TextLayout.Glyph eg = e.glyphs().get(i), ag = a.glyphs().get(i);
                        require(
                                eg.glyphId == ag.glyphId
                                        && eg.x == ag.x
                                        && eg.y == ag.y
                                        && eg.inkBounds.known == ag.inkBounds.known
                                        && eg.inkBounds.left == ag.inkBounds.left
                                        && eg.inkBounds.top == ag.inkBounds.top
                                        && eg.inkBounds.right == ag.inkBounds.right
                                        && eg.inkBounds.bottom == ag.inkBounds.bottom,
                                "显式行高改变真实字形位置或墨迹边界");
                    }
                    InlineBox box = input("x￼y", large, 20, 75, 10).inlineBoxes().get(0);
                    float top = e.baseline - box.baseline, bottom = top + box.height;
                    require(
                            box.height == 75 && bottom - top == 75 && bottom > e.y + e.lineHeight,
                            "溢出对象未保留真实高度");
                    require(a.lineHeight >= a.ascent + a.descent, "默认行高遗漏联合度量");
                    System.out.println(
                            "line-height-regression input=x\uFFFCy em=28 explicit=3"
                                    + " box=(1,20,75,10) plainHeight="
                                    + plain.height
                                    + " explicitHeight="
                                    + explicit.height
                                    + " defaultHeight="
                                    + automatic.height
                                    + " ascent="
                                    + e.ascent
                                    + " descent="
                                    + e.descent
                                    + " baseline="
                                    + e.baseline
                                    + " boxHeight="
                                    + box.height
                                    + " visibleTop="
                                    + top
                                    + " visibleBottom="
                                    + bottom);
                });
        scenario(
                "Arabic连接文字与对象断行",
                () ->
                        check(
                                fonts,
                                "arabic",
                                "السلام ￼ عليكم",
                                110,
                                43,
                                60,
                                47,
                                new TextPolicy(
                                        TextPolicy.Direction.RTL,
                                        TextPolicy.Wrap.EMERGENCY,
                                        "ar",
                                        0,
                                        0),
                                style));
        scenario(
                "混淆样式复制保留对象",
                () ->
                        check(
                                fonts,
                                "supported-copy-obfuscation",
                                "x ￼ y",
                                110,
                                43,
                                60,
                                47,
                                TextPolicy.NORMAL,
                                style.withEffects(
                                        new TextEffects(false, false, false, false, true, 0, 0))));
        scenario(
                "相邻对象联合基线与行高",
                () -> {
                    AttributedText pair =
                            AttributedText.plain(TextInput.string("￼￼"), style)
                                    .withInlineBoxes(
                                            Arrays.asList(
                                                    new InlineBox(0, 30, 50, 45),
                                                    new InlineBox(1, 40, 60, 5)));
                    TextLayout explicit = fonts.layout(pair, 100, tight);
                    TextLayout value = fonts.layout(pair, 100, TextPolicy.NORMAL);
                    TextLayout.Line e = explicit.lines().get(0), a = value.lines().get(0);
                    require(
                            e.lineHeight == 3
                                    && explicit.height == 3
                                    && e.ascent == a.ascent
                                    && e.descent == a.descent
                                    && e.baseline == a.baseline,
                            "相邻对象覆盖显式行高或损坏联合度量");
                    require(
                            value.lines().size() == 1
                                    && value.lines().get(0).clusters().size() == 2
                                    && value.lines().get(0).advance == 70
                                    && value.lines().get(0).lineHeight >= 100,
                            "相邻对象advance或联合ascent/descent丢失");
                });
        scenario(
                "零宽对象保留独立簇",
                () -> check(fonts, "zero-width", "x￼y", 100, 0, 0, 0, TextPolicy.NORMAL, style));
        scenario(
                "字面FFFC不推断对象",
                () -> {
                    TextLayout literal =
                            fonts.layout(
                                    AttributedText.plain(TextInput.string("￼"), style),
                                    100,
                                    TextPolicy.NORMAL);
                    require(!literal.lines().get(0).glyphs().isEmpty(), "字面FFFC自动成对象");
                });
        scenario(
                "字体资源重载后对象重排",
                () -> {
                    fonts.reload(fonts.defaultCandidates());
                    check(fonts, "reload", "a ￼ b", 100, 40, 70, 50, TextPolicy.NORMAL, style);
                });
        scenario(
                "声明拒绝重复位置与非占位字符",
                () -> {
                    try {
                        input("x￼", style, 5, 5, 3)
                                .withInlineBoxes(
                                        Arrays.asList(
                                                new InlineBox(1, 5, 5, 3),
                                                new InlineBox(1, 5, 5, 3)));
                        throw new AssertionError("重复offset接受");
                    } catch (IllegalArgumentException expected) {
                    }
                    try {
                        AttributedText.plain(TextInput.string("x"), style)
                                .withInlineBoxes(
                                        Collections.singletonList(new InlineBox(0, 5, 5, 3)));
                        throw new AssertionError("非FFFC接受");
                    } catch (IllegalArgumentException expected) {
                    }
                });
    }
}
