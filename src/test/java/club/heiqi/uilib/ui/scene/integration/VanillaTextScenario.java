package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import club.heiqi.uilib.host.client.font.vanilla.VanillaTextService;
import club.heiqi.uilib.host.client.font.vanilla.VanillaTextService.Settings;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;

/** 原版适配CPU真实集成，替代一次标准文字API实机测量；不宣称GL/MC性能验收。 */
public final class VanillaTextScenario extends TestApplication {

    private static List<TextLayout.Glyph> glyphs(TextLayout l) {
        List<TextLayout.Glyph> result = new ArrayList<TextLayout.Glyph>();
        for (TextLayout.Line line : l.lines()) result.addAll(line.glyphs());
        return result;
    }

    private static int[] palette() {
        int[] result = new int[32];
        for (int i = 0; i < 32; i++) {
            int boost = (i >> 3 & 1) * 85,
                    r = (i >> 2 & 1) * 170 + boost,
                    g = (i >> 1 & 1) * 170 + boost,
                    b = (i & 1) * 170 + boost;
            if (i == 6) r += 85;
            if (i >= 16) {
                r /= 4;
                g /= 4;
                b /= 4;
            }
            result[i] = r << 16 | g << 8 | b;
        }
        return result;
    }

    private static void mappingSourceRanges() {
        String raw = "§l\uE007§r\uD834\uDD1E\uE008";
        club.heiqi.uilib.ui.scene.text.AttributedText parsed =
                club.heiqi.uilib.host.text.MinecraftFormat.parse(raw, new TextStyle("minecraft", 12, -1),
                        new club.heiqi.uilib.host.text.MinecraftFormat.Context() {
                            public int paletteColor(int index) { return palette()[index]; }
                            public int codePoint(int cp) {
                                return cp == 0xE007 ? 0x1D11E : cp == 0x1D11E ? 'B' : cp == 0xE008 ? 'A' : cp;
                            }
                            public club.heiqi.uilib.ui.scene.text.TextEffects effects(
                                    boolean b, boolean i, boolean u, boolean s, boolean k) {
                                return new club.heiqi.uilib.ui.scene.text.TextEffects(b, i, u, s, k, 0, 0);
                            }
                            public club.heiqi.uilib.ui.scene.text.TextDecoration decoration() {
                                return club.heiqi.uilib.ui.scene.text.TextDecoration.NONE;
                            }
                        });
        require(parsed.input.text.equals("\uD834\uDD1EBA"), "格式解析没有应用显示码点映射");
        require(parsed.originalText.equals(raw), "映射改写了原始业务文本");
        require(parsed.input.sourceStart(0) == 2 && parsed.input.sourceEnd(1) == 3
                        && parsed.input.sourceStart(2) == 5 && parsed.input.sourceEnd(2) == 7
                        && parsed.input.sourceStart(3) == 7 && parsed.input.sourceEnd(3) == 8,
                "映射前后UTF16长度变化丢失原始完整来源范围");
        require(parsed.styleAt(1).effects.bold && !parsed.styleAt(2).effects.bold,
                "格式状态未按映射后的逻辑范围归属");
    }

    private static void mappedVanilla(VanillaTextService adapter, Settings base) {
        java.util.function.IntUnaryOperator mapping = cp -> cp == 0xE007 ? 'W' : cp == 0xE008 ? 'i' : cp;
        Settings on = new Settings(base.logicalEm, base.lineHeight, base.baseline,
                base.bidi, base.unicode, palette(), mapping);
        Settings same = new Settings(base.logicalEm, base.lineHeight, base.baseline,
                base.bidi, base.unicode, palette(), mapping);
        require(on.equals(same) && on.hashCode() == same.hashCode()
                        && !on.equals(base) && on.shadow().textMapping == mapping,
                "映射快照未进入设置身份或阴影丢失快照");
        adapter.clear();
        String raw = "§l\uE007§r\uE008";
        TextLayout mapped = adapter.layout(raw, on, -1, 1000, TextPolicy.SINGLE_LINE);
        TextLayout unmapped = adapter.layout(raw, base, -1, 1000, TextPolicy.SINGLE_LINE);
        require(mapped.logicalText.equals("Wi") && unmapped.logicalText.equals("\uE007\uE008")
                        && mapped.originalText.equals(raw) && adapter.cachedLayoutCount() == 2,
                "开关变化复用了旧解析或几何缓存");
        require(adapter.layout(raw, same, -1, 1000, TextPolicy.SINGLE_LINE) == mapped,
                "稳定映射快照未复用热缓存");
        require(adapter.width(raw, on) == adapter.width("§lW§ri", base)
                        && adapter.charWidth('\uE007', on) == adapter.charWidth('W', base),
                "测宽/charWidth未使用同一显示映射");
        int limit = (int) Math.ceil(adapter.layout("§lW", base, -1, 1000, TextPolicy.SINGLE_LINE).advanceWidth);
        require(adapter.trim(raw, limit, false, on).equals("§l\uE007")
                        && adapter.sizeToWidth(raw, limit, on) == 3,
                "裁剪返回映射文字或丢失原始PUA/token范围");
        int plainLimit = (int) Math.ceil(adapter.layout("W", base, -1, 1000, TextPolicy.SINGLE_LINE).advanceWidth);
        List<String> lines = adapter.list("§n\uE007 \uE007", plainLimit, on);
        require(lines.size() == 2, "映射换行反例未实际换行");
        for (String line : lines)
            require(line.contains("\uE007") && !line.contains("W")
                            && adapter.layout(line, on, -1, 1000, TextPolicy.SINGLE_LINE).logicalText.equals("W"),
                    "换行结果未保留原始PUA源切片/活动格式");
        TextLayout shadow = adapter.layout(raw, on.shadow(), -1, 1000, TextPolicy.SINGLE_LINE);
        require(shadow.logicalText.equals("Wi") && shadow.advanceWidth == mapped.advanceWidth
                        && mapped.logicalText.equals("Wi"), "阴影映射或旧不可变计划被开关污染");
        System.out.println("VANILLA_MAPPING settingsSnapshot=true shadow=true widthChar=true rawTrimWrap=true cacheIsolation=true");
    }

    private static void calibration(FontTextService font) {
        for (int em : new int[] {8, 9, 10, 12}) {
            TextStyle style = new TextStyle("minecraft", em, -1);
            for (String raw : new String[] {"AiW .中", "Hello, world!", "بـب क्षि 👩‍💻"}) {
                TextLayout l = font.layout(raw, style, 1000, TextPolicy.SINGLE_LINE);
                StringBuilder detail = new StringBuilder();
                for (TextLayout.Glyph g : glyphs(l))
                    detail.append(g.xAdvance)
                            .append('/')
                            .append(g.inkBounds.top)
                            .append("..")
                            .append(g.inkBounds.bottom)
                            .append(' ');
                System.out.println(
                        "CALIBRATION em="
                                + em
                                + " raw="
                                + raw
                                + " advance="
                                + l.advanceWidth
                                + " actual="
                                + detail);
            }
        }
    }

    private static void actualAlpha(FontTextService font) throws Exception {
        try (FontSession nativeService = new LwjglFontEngine().openSession()) {
            for (int em : new int[] {8, 12})
                for (TextLayout.Glyph glyph :
                        glyphs(
                                font.layout(
                                        "AiW中",
                                        new TextStyle("minecraft", em, -1),
                                        1000,
                                        TextPolicy.SINGLE_LINE))) {
                    try (FontFace face = glyph.source.openFace(nativeService, em)) {
                        GlyphBitmap bitmap = face.rasterize(glyph.glyphId);
                        byte[] pixels = bitmap.pixels();
                        int minY = bitmap.height, maxY = -1;
                        for (int y = 0; y < bitmap.height; y++)
                            for (int x = 0; x < bitmap.width; x++)
                                if (pixels[(y * bitmap.width + x) * 4 + 3] != 0) {
                                    minY = Math.min(minY, y);
                                    maxY = Math.max(maxY, y);
                                }
                        System.out.println(
                                "ALPHA em="
                                        + em
                                        + " source="
                                        + glyph.logicalStart
                                        + " advance="
                                        + glyph.xAdvance
                                        + " actualtop="
                                        + (-bitmap.bearingY + minY * bitmap.pixelScale)
                                        + " actualbottom="
                                        + (-bitmap.bearingY + (maxY + 1) * bitmap.pixelScale));
                    }
                }
        }
    }

    private static void semantics(VanillaTextService adapter, Settings s) {
        require(adapter.charWidth('§', s) == -1 && adapter.width(null, s) == 0, "原版格式字符/null宽度");
        int a = adapter.width("A", s);
        require(adapter.width("§lA§rA", s) == adapter.width("§lA", s) + a, "reset未清bold同度量");
        for (String raw :
                new String[] {
                    "A§cB",
                    "§lABC§r",
                    "A👩‍💻B",
                    "Aक्षिB",
                    "بـبA",
                    "אבג A",
                    "\uD800§cA",
                    "A§zB§",
                    "A\tB"
                }) {
            TextLayout l = adapter.layout(raw, s, 0x7f112233, 1000, TextPolicy.SINGLE_LINE);
            require(adapter.width(raw, s) == (int) l.advanceWidth, "width/draw度量分叉");
            for (int maximum = 0; maximum < adapter.width(raw, s) + 2; maximum++)
                for (boolean reverse : new boolean[] {false, true}) {
                    String trimmed = adapter.trim(raw, maximum, reverse, s);
                    require(
                            adapter.width(trimmed, s) <= maximum,
                            "trim超宽 raw=" + raw + " max=" + maximum + " got=" + trimmed);
                    require(!trimmed.endsWith("\u200d"), "trim拆ZWJ");
                }
        }
        require(adapter.trim("A§cB", a, false, s).equals("A"), "prefix吞邻接token");
        String coloredSuffix = adapter.trim("A§cB", a, true, s);
        TextLayout suffixLayout = adapter.layout(coloredSuffix, s, -1, 1000, TextPolicy.SINGLE_LINE);
        require(suffixLayout.logicalText.equals("B") && coloredSuffix.endsWith("B")
                        && suffixLayout.lines().get(0).clusters().get(0).style.color
                                == adapter.layout("A§cB", s, -1, 1000, TextPolicy.SINGLE_LINE).lines().get(0).clusters().get(1).style.color,
                "suffix未保原正文和活动颜色");
        for (String flags : new String[] {"§l", "§o", "§l§o", "§c§l§n§m"}) {
            String source = flags + "ABCD";
            String suffix = adapter.trim(source, adapter.width(flags + "D", s), true, s);
            TextLayout original = adapter.layout(source, s, -1, 1000, TextPolicy.SINGLE_LINE);
            TextLayout selected = adapter.layout(suffix, s, -1, 1000, TextPolicy.SINGLE_LINE);
            require(selected.logicalText.equals("D"), "reverse trim改写原片段正文");
            TextLayout.Cluster expected = original.lines().get(0).clusters().get(3);
            TextLayout.Cluster actual = selected.lines().get(0).clusters().get(0);
            require(actual.style.color == expected.style.color && actual.style.effects.equals(expected.style.effects),
                    "reverse trim丢失活动颜色或样式: " + flags);
            require(suffix.endsWith(source.substring(expected.sourceStart, expected.sourceEnd)),
                    "reverse trim未保原source正文");
        }
        for (String cluster : new String[] {"e\u0301", "👩🏽‍🚀", "🇨🇳"}) {
            String suffix = adapter.trim("§l§nA" + cluster, adapter.width("§l§n" + cluster, s), true, s);
            TextLayout selected = adapter.layout(suffix, s, -1, 1000, TextPolicy.SINGLE_LINE);
            require(selected.logicalText.equals(cluster), "reverse trim拆分整簇: " + cluster);
            TextLayout.Cluster actual = selected.lines().get(0).clusters().get(0);
            require(actual.style.effects.bold && actual.style.effects.underline, "整簇后缀丢失继承样式");
            require(suffix.endsWith(cluster), "整簇后缀改写原source正文");
        }
        String[][] colorSources = {
            {"AB", "B"}, {"§lAB", "§lB"}, {"§nAB", "§nB"},
            {"A§fB", "§fB"}, {"A§cB", "§cB"}, {"§cA§rB", "B"}, {"§cA§r§nB", "§nB"}
        };
        for (String[] source : colorSources) {
            int limit = adapter.width(source[1], s);
            String suffix = adapter.trim(source[0], limit, true, s);
            String wrapSource = source[0].substring(0, source[0].length() - 1) + " B";
            List<String> wrapped = adapter.list(wrapSource, limit, s);
            require(wrapped.size() == 2, "颜色来源场景未实际换行: " + source[0]);
            for (String fragment : new String[] {suffix, wrapped.get(1)}) {
                for (int base : new int[] {0x7f12a34b, 0xcc5088dd}) {
                    TextLayout original = adapter.layout(source[0], s, base, 1000, TextPolicy.SINGLE_LINE);
                    TextLayout selected = adapter.layout(fragment, s, base, 1000, TextPolicy.SINGLE_LINE);
                    require(selected.logicalText.equals("B") && fragment.endsWith("B"), "片段未保原正文");
                    TextLayout.Cluster actual = selected.lines().get(0).clusters().get(0);
                    TextLayout.Cluster expected = original.lines().get(0).clusters().get(1);
                    int expectedColor = base;
                    if (source[0].equals("A§fB")) expectedColor = (base & 0xff000000) | (s.paletteColor(15) & 0xffffff);
                    if (source[0].equals("A§cB")) expectedColor = (base & 0xff000000) | (s.paletteColor(12) & 0xffffff);
                    require(actual.style.color == expectedColor && actual.style.effects.equals(expected.style.effects),
                            "截取/换行丢失继承基色、绝对颜色或效果: " + source[0] + " -> " + fragment);
                }
            }
        }
        String raw = "§lAB §cCD §nEF\n§oGH";
        List<String> lines = adapter.list(raw, a * 3 + 1, s);
        require(lines.size() > 1, "wrap无实际行");
        for (String line : lines)
            require(adapter.width(line, s) <= a * 3 + 1, "wrap未恢复活动style或超宽: " + line);
        require(
                adapter.splitHeight(raw, a * 3 + 1, s) == (int) (lines.size() * s.lineHeight),
                "splitheight未用caller FONT_HEIGHT");
        Settings ltr =
                new Settings(s.logicalEm, s.lineHeight, s.baseline, false, s.unicode, palette());
        require(adapter.bidiString("אבג A", ltr).equals("אבג A"), "falsebidi未LTR");
        TextLayout colored = adapter.layout("A§fB§nC", s, 0x7f123456, 1000, TextPolicy.SINGLE_LINE);
        List<TextLayout.Glyph> g = glyphs(colored);
        require(
                g.get(0).resolvedStyle.color == 0x7f123456
                        && g.get(1).resolvedStyle.color == 0x7fffffff,
                "basecolor/explicitwhite合并污染");
        TextLayout normal = adapter.layout("ABC", s, -1, 1000, TextPolicy.SINGLE_LINE);
        for (TextLayout.Line line : normal.lines())
            require(
                    line.lineHeight == s.lineHeight && line.baseline == s.baseline,
                    "原版callerbaseline/lineheight");
        System.out.println(
                "PASS vanilla width/char/raw safe trim/token ownership/formatwrap/bidi/fixedbaseline/palette");
    }

    private static void decorations(VanillaTextService adapter) {
        for (float em : new float[] {9, 18.5f, 27})
            for (float[] frame : new float[][] {{9, 9}, {23, 5}}) {
                Settings settings = new Settings(em, frame[0], frame[1], false, false, palette());
                TextLayout plain = adapter.layout("ABC\n世界", settings, -1, 1000, TextPolicy.NO_WRAP);
                TextLayout marked = adapter.layout("§n§mABC\n世界", settings, -1, 1000, TextPolicy.NO_WRAP);
                require(marked.advanceWidth == plain.advanceWidth && marked.advanceHeight == plain.advanceHeight,
                        "装饰线改变了标准文字测量");
                require(marked.lines().size() == 2, "装饰回归未覆盖多行文本");
                for (int lineIndex = 0; lineIndex < marked.lines().size(); lineIndex++) {
                    TextLayout.Line line = marked.lines().get(lineIndex);
                    require(line.baseline == frame[1] + lineIndex * frame[0]
                                    && line.lineHeight == frame[0], "装饰线改动了 caller 基线或行高");
                    boolean underline = false, strike = false;
                    for (TextLayout.Decoration d : line.decorations()) {
                        if (d.kind == TextLayout.Decoration.Kind.UNDERLINE) {
                            underline = true;
                            require(d.y > line.baseline, "下划线压在字形基线：em=" + em + " y=" + d.y + " baseline=" + line.baseline);
                        } else if (d.kind == TextLayout.Decoration.Kind.STRIKE) {
                            strike = true;
                            require(d.y + d.height < line.baseline && d.y > line.baseline - em,
                                    "删除线没有落在当前字号的字身内部");
                        }
                    }
                    require(underline && strike, "格式转换丢失下划线或删除线");
                }
                for (float density : new float[] {1, 1.5f, 2, 3}) {
                    TextLayout sampled = adapter.sample(marked, new RasterSampling(density, 1), 0, 0);
                    for (int i = 0; i < marked.lines().size(); i++) {
                        List<TextLayout.Decoration> expected = marked.lines().get(i).decorations();
                        List<TextLayout.Decoration> actual = sampled.lines().get(i).decorations();
                        require(expected.size() == actual.size(), "光栅采样改变了装饰线数量");
                        for (int j = 0; j < expected.size(); j++) {
                            TextLayout.Decoration a = expected.get(j), b = actual.get(j);
                            require(a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height,
                                    "GUI 采样密度重复缩放了装饰几何");
                        }
                    }
                }
                System.out.println("DECORATION em=" + em + " callerHeight=" + frame[0] + " callerBaseline=" + frame[1]
                        + " underlineBelowBaseline=true strikeInsideEm=true sampleDensity=1,1.5,2,3");
            }
    }

    private static void sampling(VanillaTextService adapter, Settings s) {
        String raw = "§k§lAB §cC §kبـب क्षि 👩‍💻";
        TextLayout logical = adapter.layout(raw, s, -1, 1000, TextPolicy.SINGLE_LINE);
        TextLayout first = null;
        boolean changed = false;
        for (long tick = 0; tick < 8; tick++) {
            TextLayout plan =
                    adapter.sample(
                            raw,
                            s,
                            -1,
                            1000,
                            TextPolicy.SINGLE_LINE,
                            new RasterSampling(2, 1),
                            tick,
                            7);
            require(
                    plan.advanceWidth == logical.advanceWidth
                            && plan.advanceHeight == logical.advanceHeight
                            && plan.logicalText.equals(logical.logicalText),
                    "混淆改变度量/原文");
            require(
                    adapter.sample(
                                    raw,
                                    s,
                                    -1,
                                    1000,
                                    TextPolicy.SINGLE_LINE,
                                    new RasterSampling(2, 1),
                                    tick,
                                    7)
                            == plan,
                    "hot sample未复用实际计划");
            for (TextLayout.Glyph g : glyphs(plan))
                require(
                        g.resolvedStyle != null
                                && g.sourceStart < g.sourceEnd
                                && g.logicalStart < g.logicalEnd,
                        "混淆丢真实身份/source");
            if (first == null) first = plan;
            else if (glyphs(first).get(0).glyphId != glyphs(plan).get(0).glyphId) changed = true;
        }
        require(changed, "tick混淆未变实际glyph");
        TextLayout frozen = adapter.layout("§kA§rB§c", s, 0x7f123456, 1000, TextPolicy.SINGLE_LINE);
        require(
                glyphs(frozen).get(0).resolvedStyle.effects.obfuscated
                        && frozen.lines().get(0).clusters().get(0).style.effects.obfuscated,
                "冻结plan丢混淆flag");
        TextLayout replay = adapter.sample(frozen, new RasterSampling(3, 12), 4, 2);
        require(
                adapter.sample(frozen, new RasterSampling(3, 12), 4, 2) == replay
                        && replay.advanceWidth == frozen.advanceWidth,
                "冻结plan重放采样不稳定");
        require(
                adapter.terminalColor("A§c", s, 0x7f123456) == (0x7f000000 | s.paletteColor(12))
                        && adapter.terminalColor("", s, 0x7f123456) == 0x7f123456,
                "terminal尾token/empty错");
        require(
                adapter.bidiString("אבג", s).equals("גבא")
                        && adapter.bidiString("\nA", s).equals("\nA"),
                "视觉字符串未用完整簇或丢首空行");
        String emoji = "§k👩‍💻";
        TextLayout changedEmoji =
                adapter.sample(
                        emoji, s, -1, 1000, TextPolicy.SINGLE_LINE, RasterSampling.LOGICAL, 3, 1);
        require(
                glyphs(changedEmoji).size() == 1
                        && !glyphs(changedEmoji).get(0).color
                        && glyphs(changedEmoji).get(0).logicalEnd == 5,
                "emoji混淆拆簇或silent忽略");
        TextLayout ar =
                adapter.sample(
                        "§kبـب", s, -1, 1000, TextPolicy.SINGLE_LINE, RasterSampling.LOGICAL, 3, 1);
        require(
                glyphs(ar).size() == 1
                        && glyphs(ar).get(0).logicalStart == 0
                        && glyphs(ar).get(0).logicalEnd == 3,
                "Arabic必要连接range混淆半簇");
        TextLayout partial =
                adapter.sample(
                        "§kب§rـب",
                        s,
                        -1,
                        1000,
                        TextPolicy.SINGLE_LINE,
                        RasterSampling.LOGICAL,
                        3,
                        1);
        require(
                glyphs(partial).size() == 1 && !partial.diagnostics().isEmpty(),
                "必要range混淆未logicalleading裁决/诊断");
        System.out.println(
                "PASS vanilla true obfuscation same tick identity/tick change/whole emoji/necessary Arabic/source/advance");
    }

    private static void shadowReuse(VanillaTextService adapter, Settings normal) {
        adapter.clear();
        Settings shadow = normal.shadow();
        String raw = "§lA§fB§cC§rD §nבـب";
        TextLayout first = adapter.layout(raw, normal, 0x7f123456, 1000, TextPolicy.SINGLE_LINE),
                second = adapter.layout(raw, shadow, 0x7f123456, 1000, TextPolicy.SINGLE_LINE);
        require(
                adapter.cachedLayoutCount() == 1 && first.advanceWidth == second.advanceWidth,
                "阴影palette重复建立几何布局");
        List<TextLayout.Glyph> a = glyphs(first), b = glyphs(second);
        for (int i = 0; i < a.size(); i++) {
            TextLayout.Glyph x = a.get(i), y = b.get(i);
            require(
                    x.inkBounds == y.inkBounds
                            && x.source == y.source
                            && x.glyphId == y.glyphId
                            && x.x == y.x
                            && x.y == y.y
                            && x.xAdvance == y.xAdvance
                            && x.rasterStyle == y.rasterStyle,
                    "normal/shadow未复用实际定位与variant身份");
        }
        require(
                a.get(1).resolvedStyle.color == (0x7f000000 | normal.paletteColor(15))
                        && b.get(1).resolvedStyle.color == (0x7f000000 | shadow.paletteColor(15))
                        && a.get(2).resolvedStyle.color != (b.get(2).resolvedStyle.color),
                "阴影palette paintkey污染");
        TextLayout
                sampleA =
                        adapter.sample(
                                raw,
                                normal,
                                0x7f123456,
                                1000,
                                TextPolicy.SINGLE_LINE,
                                new RasterSampling(2, 1),
                                0,
                                0),
                sampleB =
                        adapter.sample(
                                raw,
                                shadow,
                                0x7f123456,
                                1000,
                                TextPolicy.SINGLE_LINE,
                                new RasterSampling(2, 1),
                                0,
                                0);
        require(
                glyphs(sampleA).get(1).resolvedStyle.color
                                != glyphs(sampleB).get(1).resolvedStyle.color
                        && adapter.cachedLayoutCount() == 1,
                "samplekey未保留阴影palette");
        require(
                adapter.layout(raw, normal, 0x7f123456, 1000, TextPolicy.SINGLE_LINE) == first
                        && adapter.layout(raw, shadow, 0x7f123456, 1000, TextPolicy.SINGLE_LINE)
                                == second,
                "颜色绑定hotcache身份分叉");
        String animated = "§kAB§c§kCD";
        TextLayout
                normalPlan =
                        adapter.layout(animated, normal, 0x7fabcdef, 1000, TextPolicy.SINGLE_LINE),
                shadowPlan =
                        adapter.layout(animated, shadow, 0x7f223344, 1000, TextPolicy.SINGLE_LINE);
        long sourceSalt = normalPlan.input.text.hashCode();
        require(sourceSalt == shadowPlan.input.text.hashCode(), "normal/shadow源动画salt不一致");
        for (long tick = 0; tick < 8; tick++) {
            List<TextLayout.Glyph>
                    normalGlyphs =
                            glyphs(
                                    adapter.sample(
                                            normalPlan,
                                            new RasterSampling(2, 1),
                                            tick,
                                            sourceSalt)),
                    shadowGlyphs =
                            glyphs(
                                    adapter.sample(
                                            shadowPlan,
                                            new RasterSampling(2, 1),
                                            tick,
                                            sourceSalt));
            require(normalGlyphs.size() == shadowGlyphs.size(), "阴影混淆glyph数量不同");
            for (int i = 0; i < normalGlyphs.size(); i++) {
                TextLayout.Glyph x = normalGlyphs.get(i), y = shadowGlyphs.get(i);
                require(
                        x.glyphId == y.glyphId
                                && x.source == y.source
                                && x.x == y.x
                                && x.y == y.y
                                && x.xAdvance == y.xAdvance
                                && x.rasterStyle.equals(y.rasterStyle),
                        "同源同tick阴影混淆错形");
            }
            require(
                    normalGlyphs.get(0).resolvedStyle.color == 0x7fabcdef
                            && shadowGlyphs.get(0).resolvedStyle.color == 0x7f223344
                            && normalGlyphs.get(2).resolvedStyle.color
                                    == (0x7f000000 | normal.paletteColor(12))
                            && shadowGlyphs.get(2).resolvedStyle.color
                                    == (0x7f000000 | shadow.paletteColor(12)),
                    "同步混淆丢base/显式palette阴影颜色");
        }
        System.out.println(
                "PASS vanilla same-source same-tick normal/shadow obfuscation glyph pattern with distinct colors");
        System.out.println(
                "PASS vanilla normal/shadow share actual geometry variant/ink identities; distinct palette paint/sample");
    }

    private static void performance(VanillaTextService adapter, Settings s) {
        String raw = "§lHello §cworld§r بـب 👩‍💻";
        for (int i = 0; i < 300; i++) {
            adapter.width(raw, s);
            adapter.sample(
                    raw, s, -1, 1000, TextPolicy.SINGLE_LINE, new RasterSampling(2, 1), 0, 0);
        }
        long[] ns = new long[1000];
        for (int i = 0; i < ns.length; i++) {
            long start = System.nanoTime();
            adapter.width(raw, s);
            adapter.sample(
                    raw, s, -1, 1000, TextPolicy.SINGLE_LINE, new RasterSampling(2, 1), 0, 0);
            ns[i] = System.nanoTime() - start;
        }
        Arrays.sort(ns);
        System.out.println(
                "CPU HOT width+sample iterations="
                        + ns.length
                        + " p50us="
                        + ns[499] / 1000.0
                        + " p95us="
                        + ns[949] / 1000.0
                        + " p99us="
                        + ns[989] / 1000.0
                        + " (非MC门槛)");
        for (int i = 0; i < 256; i++)
            adapter.sample(
                    "A" + i, s, -1, 1000, TextPolicy.SINGLE_LINE, RasterSampling.LOGICAL, i, 0);
        require(
                adapter.cachedLayoutCount() <= 128
                        && adapter.cachedParseCount() <= 128
                        && adapter.cachedSampleCount() <= 128,
                "adapter cache无界");
    }

    public static void main(String[] args) throws Exception {
        new VanillaTextScenario().run(args);
    }

    private List<FontSource> fonts;
    private FontTextService font;
    private VanillaTextService adapter;
    private Settings settings;

    @Override
    protected void setup() throws Exception {
        fonts = FontTextService.defaultSources(VanillaTextScenario.class.getClassLoader(), 1);
        font = own(new FontTextService(fonts, new LwjglFontEngine(), 8, 32, 8192));
        adapter = new VanillaTextService(font);
        settings = new Settings(12, 9, 9, true, false, palette());
    }

    @Override
    protected void runTests() throws Exception {
        scenario("格式后码点映射保持原始来源与代理对范围", VanillaTextScenario::mappingSourceRanges);
        scenario("映射快照统一测绘、裁剪、换行与开关缓存", () -> mappedVanilla(adapter, settings));
        scenario("字体度量校准", () -> calibration(font));
        scenario("原生alpha墨迹观测", () -> actualAlpha(font));
        scenario("标准文字API语义", () -> semantics(adapter, settings));
        scenario("装饰线相对字形基线且不受旧行框与采样密度影响", () -> decorations(adapter));
        scenario("完整簇混淆采样", () -> sampling(adapter, settings));
        scenario("普通阴影几何共享", () -> shadowReuse(adapter, settings));
        scenario("CPU热缓存及容量边界", () -> performance(adapter, settings));
        scenario(
                "资源代际失效缓存",
                () -> {
                    TextLayout before =
                            adapter.sample(
                                    "ABC",
                                    settings,
                                    -1,
                                    1000,
                                    TextPolicy.SINGLE_LINE,
                                    RasterSampling.LOGICAL,
                                    0,
                                    0);
                    font.reload(fonts);
                    TextLayout after =
                            adapter.sample(
                                    "ABC",
                                    settings,
                                    -1,
                                    1000,
                                    TextPolicy.SINGLE_LINE,
                                    RasterSampling.LOGICAL,
                                    0,
                                    0);
                    require(
                            after != before && after.generation > before.generation,
                            "资源generation没有失效缓存");
                });
        scenario(
                "旧冻结计划跨服务重放",
                () -> {
                    final FontTextService[] holder = {
                        own(new FontTextService(fonts, new LwjglFontEngine(), 8, 32, 8192))
                    };
                    VanillaTextService adapter = new VanillaTextService(() -> holder[0]);
                    Settings settings = new Settings(12, 9, 9, true, false, palette());
                    TextLayout frozen =
                            adapter.layout("§k👩‍💻", settings, -1, 1000, TextPolicy.SINGLE_LINE);
                    FontSource expected = frozen.style.candidates().get(0);
                    // 此处关闭是跨服务代际重放的真实前置条件；应用仍兜底关闭幂等服务。
                    holder[0].close();
                    holder[0] =
                            own(
                                    new FontTextService(
                                            FontTextService.defaultSources(
                                                    VanillaTextScenario.class.getClassLoader(), 2),
                                            new LwjglFontEngine(),
                                            8,
                                            32,
                                            8192));
                    TextLayout replay = adapter.sample(frozen, new RasterSampling(2, 3), 9, 0);
                    require(glyphs(replay).get(0).source == expected, "旧混淆plan重放偷用新代际候选");
                    System.out.println(
                            "PASS vanilla old frozen plan new service keeps explicit source snapshot");
                });
    }
}
