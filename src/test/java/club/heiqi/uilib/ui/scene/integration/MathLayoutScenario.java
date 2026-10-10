package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.TextDecoration;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathEmbedMetrics;
import club.heiqi.uilib.ui.scene.text.math.MathFont;
import club.heiqi.uilib.ui.scene.text.math.MathLayout;
import club.heiqi.uilib.ui.scene.text.math.MathLayoutEngine;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.lwjgl.util.harfbuzz.HarfBuzz;
import org.lwjgl.util.harfbuzz.OpenType;
import org.lwjgl.util.harfbuzz.hb_ot_math_glyph_variant_t;

/** 真正的 FT/HB 字形与数学布局；净空期望独立读取随包字体 MATH 表，不复制布局算法。 */
public final class MathLayoutScenario extends TestApplication {
    private final MathLayoutEngine engine = new MathLayoutEngine();
    private FontTextService text;
    private ByteBuffer sfnt;
    private int constants, unitsPerEm;
    private RawMath raw;
    private final Map<Integer, Float> nativeMetrics = new HashMap<Integer, Float>();
    private static final float EM = 32.3f;

    public static void main(String[] args) throws Exception {
        new MathLayoutScenario().run(args);
    }

    @Override
    protected void setup() throws Exception {
        text = own(new FontTextService(new LwjglFontEngine()));
        byte[] bytes = resource("assets/qz_uilib/fonts/math/STIXTwoMath-Regular.otf");
        sfnt = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int math = table(bytes, "MATH");
        constants = math + (sfnt.getShort(math + 4) & 65535);
        unitsPerEm = sfnt.getShort(table(bytes, "head") + 18) & 65535;
        require(unitsPerEm > 0, "字体没有有效em单位");
        require(text.mathFont(style(EM)) != null, "正式字体未加载");
        raw = own(new RawMath(bytes, EM));
    }

    @Override
    protected void runTests() throws Exception {
        scenario("积分分式嵌入与真实字形闭环", this::representative);
        scenario("MATH净空脚本层级根指数及作用域", this::geometry);
        scenario("源码复用动态主题字号原子间距", this::dynamic);
        scenario("完整源文降级UTF16定位与邻式隔离", this::failures);
    }

    private void representative() {
        String source = tex("@int_0^@infty @frac{@embed{ore}}{1+x^2}@,@mathrm{d}x");
        MathLayout layout = layout(source, MathStyle.DISPLAY, slots("ore"));
        healthy(layout);
        require(layout.embeds.size() == 1 && layout.rules.size() == 1, "代表式没有分式与唯一嵌入");
        MathLayout.Embed ore = embed(layout, "ore");
        near(ore.width, EM, "display分子TEXT样式应与根em相同");
        MathLayout.Rule rule = layout.rules.get(0);
        near(rule.height, metric(38, EM), "分式rule厚度未来自真实MATH");
        near(layout.baseline - (rule.y + rule.height / 2), metric(5, EM), "分式未落数学轴");
        require(rule.y - (ore.y + ore.height) + 0.002f >= metric(37, EM), "分子真实净空不足");
        require(rule.x <= ore.x && rule.x + rule.width >= ore.x + ore.width, "分式rule没有覆盖分子");
        MathLayout.TextPiece integral = piece(layout, 0);
        TextLayout.Glyph actual = integral.text.lines().get(0).glyphs().get(0);
        TextLayout normal = text.mathFont(style(EM)).text("∫");
        int normalId = normal.lines().get(0).glyphs().get(0).glyphId;
        require(actual.glyphId != normalId, "display积分未选择真实variant");
        require(
                actual.inkBounds.bottom - actual.inkBounds.top
                        > normal.lines().get(0).glyphs().get(0).inkBounds.bottom
                                - normal.lines().get(0).glyphs().get(0).inkBounds.top,
                "积分墨迹没有变大");
        for (MathLayout.TextPiece p : layout.pieces)
            require(
                    p.sourceStart >= 0
                            && p.sourceEnd <= source.length()
                            && p.sourceEnd > p.sourceStart,
                    "字形丢失公式源范围");
        System.out.println(
                "representative width="
                        + layout.width
                        + " height="
                        + layout.height
                        + " baseline="
                        + layout.baseline
                        + " integralGlyph="
                        + actual.glyphId);
    }

    private void geometry() {
        require(
                sfnt.getShort(constants) == 70 && sfnt.getShort(constants + 2) == 55,
                "真实STIX脚本事实不是70/55");
        MathLayout scripts =
                layout("x^{@embed{up}}_{@embed{down}}", MathStyle.TEXT, slots("up", "down"));
        healthy(scripts);
        MathLayout.Embed up = embed(scripts, "up"), down = embed(scripts, "down");
        near(up.width, EM * percent(0), "第一层脚本字号");
        near(down.width, EM * percent(0), "下标字号");
        require(down.y - (up.y + up.height) + 0.002f >= metric(15, EM), "双脚本实际净空不足");
        MathLayout deep =
                layout(
                        "x^{@embed{a}^{@embed{b}^{@embed{c}}}}",
                        MathStyle.TEXT,
                        slots("a", "b", "c"));
        healthy(deep);
        near(embed(deep, "a").width, EM * percent(0), "第一层脚本em");
        near(embed(deep, "b").width, EM * percent(1), "第二层脚本em");
        near(embed(deep, "c").width, EM * percent(1), "第三层不得无限指数缩小");
        MathLayout fraction =
                layout("@frac{@embed{n}}{@embed{d}}", MathStyle.TEXT, slots("n", "d"));
        healthy(fraction);
        MathLayout.Rule rule = fraction.rules.get(0);
        MathLayout.Embed numerator = embed(fraction, "n"), denominator = embed(fraction, "d");
        near(numerator.width, EM * percent(0), "text分式转script");
        require(rule.y - numerator.y - numerator.height + 0.002f >= metric(36, EM), "text分子净空");
        require(denominator.y - rule.y - rule.height + 0.002f >= metric(39, EM), "text分母净空");
        MathLayout root =
                layout(
                        "@sqrt[@embed{degree}]{@embed{body}}",
                        MathStyle.TEXT,
                        slots("degree", "body"));
        healthy(root);
        MathLayout.Embed body = embed(root, "body"), degree = embed(root, "degree");
        MathLayout.Rule rootRule = root.rules.get(0);
        near(degree.width, EM * percent(1), "根指数应使用scriptscript");
        near(rootRule.height, metric(51, EM), "根式rule厚度");
        require(body.y - rootRule.y - rootRule.height + 0.002f >= metric(49, EM), "根式真实净空");
        require(
                rootRule.x <= body.x && rootRule.x + rootRule.width >= body.x + body.width,
                "根式rule未覆盖被开方盒");
        require(degree.x + degree.width < body.x + 0.002f, "根指数进入被开方盒");
        float[] rootSign = ink(piece(root, 0));
        near(degree.x, metric(53, EM), "根指数before kern");
        near(rootSign[0] - degree.x - degree.width, metric(54, EM), "根指数after kern");
        near(
                rootSign[3] - degree.y - degree.height,
                (rootSign[3] - rootSign[1]) * metric(55, EM) / 100f,
                "根指数raise百分比");
        MathLayout nested = layout("@sqrt{1+@frac{@embed{n}}{2}}", MathStyle.TEXT, slots("n"));
        healthy(nested);
        require(nested.rules.size() == 2, "嵌套根式分式丢rule");
        MathLayout scoped =
                layout(
                        "{@scriptstyle @embed{small}}@embed{large}",
                        MathStyle.TEXT,
                        slots("small", "large"));
        healthy(scoped);
        near(embed(scoped, "small").width, EM * percent(0), "样式声明未生效");
        near(embed(scoped, "large").width, EM, "分组样式未恢复");
        verifyLimits();
    }

    private void verifyLimits() {
        MathLayout limits =
                layout("@sum_{@embed{lo}}^{@embed{hi}}", MathStyle.DISPLAY, slots("lo", "hi"));
        healthy(limits);
        MathLayout.TextPiece sum = piece(limits, 0);
        float[] ink = ink(sum);
        require(
                sum.text.lines().get(0).glyphs().get(0).glyphId == raw.largestVariant(0x2211),
                "sum没有选择rawMATH最大完整variant");
        near((ink[1] + ink[3]) / 2, limits.baseline - metric(5, EM), "sum未居中数学轴");
        MathLayout prod =
                layout("@prod_{@embed{lo}}^{@embed{hi}}", MathStyle.DISPLAY, slots("lo", "hi"));
        healthy(prod);
        float[] prodInk = ink(piece(prod, 0));
        require(
                piece(prod, 0).text.lines().get(0).glyphs().get(0).glyphId
                        == raw.largestVariant(0x220f),
                "prod没有选择rawMATH最大完整variant");
        near((prodInk[1] + prodInk[3]) / 2, prod.baseline - metric(5, EM), "prod未居中数学轴");
        require(
                prodInk[1] - embed(prod, "hi").y - embed(prod, "hi").height + 0.002f
                        >= metric(18, EM),
                "prod上限净空");
        require(embed(prod, "lo").y - prodInk[3] + 0.002f >= metric(20, EM), "prod下限净空");
        MathLayout.Embed upper = embed(limits, "hi"), lower = embed(limits, "lo");
        require(ink[1] - upper.y - upper.height + 0.002f >= metric(18, EM), "sum上限净空");
        require(lower.y - ink[3] + 0.002f >= metric(20, EM), "sum下限净空");
        MathLayout side =
                layout(
                        "@sum@nolimits_{@embed{lo}}^{@embed{hi}}",
                        MathStyle.DISPLAY,
                        slots("lo", "hi"));
        healthy(side);
        require(embed(side, "lo").x >= ink(piece(side, 0))[2] - 0.002f, "nolimits未侧置");
        MathLayout integral =
                layout("@int_{@embed{lo}}^{@embed{hi}}", MathStyle.DISPLAY, slots("lo", "hi"));
        healthy(integral);
        require(embed(integral, "lo").x >= ink(piece(integral, 0))[2] - 0.002f, "积分默认应侧置");
        MathLayout forced =
                layout(
                        "@int@limits_{@embed{lo}}^{@embed{hi}}",
                        MathStyle.DISPLAY,
                        slots("lo", "hi"));
        healthy(forced);
        require(
                embed(forced, "hi").y + embed(forced, "hi").height <= ink(piece(forced, 0))[1],
                "limits未强制上置");
        int integralId = piece(forced, 0).text.lines().get(0).glyphs().get(0).glyphId;
        float correction =
                OpenType.hb_ot_math_get_glyph_italics_correction(raw.font, integralId) * raw.scale;
        MathLayout.Embed forcedUpper = embed(forced, "hi"), forcedLower = embed(forced, "lo");
        near(
                forcedUpper.x + forcedUpper.width / 2 - forcedLower.x - forcedLower.width / 2,
                correction,
                "limits未使用variant倾斜修正");
        near(embed(integral, "hi").x - embed(integral, "lo").x, correction, "侧脚本未使用variant倾斜修正");
        MathLayout lim = layout("@lim_{@embed{lo}}", MathStyle.DISPLAY, slots("lo"));
        healthy(lim);
        require(
                embed(lim, "lo").y >= ink(piece(lim, 0))[3] + metric(20, EM) - 0.002f,
                "lim默认下限未中置");
    }

    private void dynamic() {
        MathContent content = MathContent.parse(tex("@int@frac{@embed{ore}}{1+x^2}@,@mathrm{d}x"));
        String source = content.source;
        MathLayout first = engine.layout(content, style(EM), MathStyle.DISPLAY, text, slots("ore"));
        TextStyle enlarged = style(EM * 1.5f).withColor(0xff123456);
        MathLayout next = engine.layout(content, enlarged, MathStyle.DISPLAY, text, slots("ore"));
        healthy(first);
        healthy(next);
        // native grid-fit 墨迹和 variant 选择不保证线性缩放；嵌入 em 则必须精确缩放。
        require(next.width > first.width && next.baseline > first.baseline, "同一内容对象没有按新字号重排");
        require(
                piece(next, 0).text.lines().get(0).glyphs().get(0).logicalEm == enlarged.logicalEm,
                "重新布局仍持有旧字号variant");
        near(embed(next, "ore").width, embed(first, "ore").width * 1.5f, "嵌入字号重排");
        require(content.source == source && content.diagnostics.isEmpty(), "动态排版修改了内容对象");
        for (MathLayout.TextPiece piece : next.pieces)
            require(piece.text.style.color == enlarged.color, "字形没有消费动态颜色");
        MathLayout add =
                layout("a+b", MathStyle.TEXT, Collections.<String, MathEmbedMetrics>emptyMap());
        MathLayout adjacent =
                layout("ab", MathStyle.TEXT, Collections.<String, MathEmbedMetrics>emptyMap());
        MathLayout unary =
                layout("+a", MathStyle.TEXT, Collections.<String, MathEmbedMetrics>emptyMap());
        MathLayout relation =
                layout("a=+b", MathStyle.TEXT, Collections.<String, MathEmbedMetrics>emptyMap());
        healthy(add);
        healthy(adjacent);
        healthy(unary);
        healthy(relation);
        require(piece(add, 1).x > ink(piece(add, 0))[2], "BIN没有正常间距");
        float plusWidth =
                layout("+", MathStyle.TEXT, Collections.<String, MathEmbedMetrics>emptyMap()).width;
        near(
                unary.width,
                plusWidth
                        + layout(
                                        "a",
                                        MathStyle.TEXT,
                                        Collections.<String, MathEmbedMetrics>emptyMap())
                                .width,
                "开头BIN没有转ORD");
        require(piece(relation, 3).x - ink(piece(relation, 2))[2] < EM / 18f, "REL后BIN未转ORD");
        MathLayout mathFonts =
                layout(
                        "h@mathrm{h}@mathbf{h}@mathit{h}@mathnormal{h}@text{ 中文 α }",
                        MathStyle.TEXT,
                        Collections.<String, MathEmbedMetrics>emptyMap());
        healthy(mathFonts);
        require(
                piece(mathFonts, 0).text.logicalText.codePointAt(0) == 0x210e,
                "数学italic h未映射真实hole");
        require(mathFonts.pieces.get(1).text.logicalText.equals("h"), "mathrm没有普通Roman");
        require(
                mathFonts.pieces.get(2).text.logicalText.codePointAt(0) == 0x1d421,
                "mathbf没有真实bold glyph");
        require(
                mathFonts
                        .pieces
                        .get(mathFonts.pieces.size() - 1)
                        .text
                        .originalText
                        .equals(" 中文 α "),
                "text组丢失Unicode或空格");
        for (MathLayout.TextPiece piece : mathFonts.pieces)
            if (piece.sourceStart < tex("h@mathrm{h}@mathbf{h}@mathit{h}@mathnormal{h}").length())
                require(
                        piece.text.style.fontStyle.posture
                                == club.heiqi.uilib.ui.scene.text.FontStyle.Posture.NORMAL,
                        "Unicode数学italic叠加了fontitalic");
    }

    private void failures() throws Exception {
        String unknown = tex("𝑥@unknown{a}");
        MathLayout bad = layout(unknown, MathStyle.TEXT, slots());
        fallback(bad, unknown, "parse", 2, 10);
        String emoji = new String(Character.toChars(0x1f600));
        String unknownControl = tex("@") + emoji;
        fallback(
                layout(unknownControl, MathStyle.TEXT, slots()),
                unknownControl,
                "parse",
                0,
                unknownControl.length());
        for (String prefix : new String[] {tex("@embed"), tex("@text"), tex("@frac")}) {
            String source = prefix + emoji;
            MathLayout rejected = layout(source, MathStyle.TEXT, slots());
            fallback(rejected, source, "parse", prefix.length(), source.length());
            for (MathLayout.Diagnostic diagnostic : rejected.diagnostics) {
                if (!diagnostic.stage.equals("parse")) continue;
                require(
                        !(diagnostic.start > 0
                                && diagnostic.start < source.length()
                                && Character.isHighSurrogate(source.charAt(diagnostic.start - 1))
                                && Character.isLowSurrogate(source.charAt(diagnostic.start))),
                        "诊断起点切开代理对");
                require(
                        !(diagnostic.end > 0
                                && diagnostic.end < source.length()
                                && Character.isHighSurrogate(source.charAt(diagnostic.end - 1))
                                && Character.isLowSurrogate(source.charAt(diagnostic.end))),
                        "诊断终点切开代理对");
            }
        }
        String duplicate = tex("@embed{ore}+@embed{ore}");
        fallback(
                layout(duplicate, MathStyle.TEXT, slots("ore")),
                duplicate,
                "parse",
                duplicate.lastIndexOf((char) 92),
                duplicate.length());
        String absent = tex("1+@embed{missing}");
        fallback(layout(absent, MathStyle.TEXT, slots()), absent, "layout", 2, absent.length());
        Map<String, MathEmbedMetrics> invalid = slots("ore");
        invalid.put("ore", new MathEmbedMetrics(Float.NaN, 1, 0.8f));
        String embed = tex("@embed{ore}");
        fallback(layout(embed, MathStyle.TEXT, invalid), embed, "layout", 0, embed.length());
        String fontSource = tex("x^2");
        TextStyle noFont =
                new TextStyle(
                        "default",
                        EM,
                        -1,
                        Collections.singletonList(text.defaultCandidates().get(0)),
                        TextEffects.NORMAL,
                        TextDecoration.NONE);
        MathLayout noMath =
                engine.layout(MathContent.parse(fontSource), noFont, MathStyle.TEXT, text, slots());
        fallback(noMath, fontSource, "font", 0, 1);
        TextStyle emptyCandidates =
                new TextStyle(
                        "default",
                        EM,
                        -1,
                        Collections.<FontSource>emptyList(),
                        TextEffects.NORMAL,
                        TextDecoration.NONE);
        require(text.mathFont(emptyCandidates) == null, "显式空链偷偷追加数学字体");
        // 空链对普通文字也没有资源；保留既有字体严格失败，不伪造可见字形或追加默认字体。
        boolean rejected = false;
        try {
            engine.layout(
                    MathContent.parse(fontSource), emptyCandidates, MathStyle.TEXT, text, slots());
        } catch (IllegalStateException failure) {
            rejected = failure.getMessage().contains("候选列表无有效文字字体");
        }
        require(rejected, "全空候选没有保持普通文字严格失败");
        String huge = tex("@sqrt{@embed{huge}}");
        Map<String, MathEmbedMetrics> hugeSlot = slots("huge");
        hugeSlot.put("huge", new MathEmbedMetrics(1, 100, 80));
        fallback(layout(huge, MathStyle.TEXT, hugeSlot), huge, "font", 0, huge.length());
        for (String malformed :
                new String[] {"x^2^3", "x_1_2", "{x", "@frac{x}", "@left(x@right)"}) {
            String source = tex(malformed);
            MathLayout result = layout(source, MathStyle.TEXT, slots());
            require(
                    result.fallback && result.sourceFallback.originalText.equals(source),
                    "错误式丢失完整原文: " + source);
        }
        String missingGlyph = "1+" + new String(Character.toChars(0x10ffff));
        fallback(layout(missingGlyph, MathStyle.TEXT, slots()), missingGlyph, "font", 2, 4);
        for (MathEmbedMetrics metrics :
                new MathEmbedMetrics[] {
                    new MathEmbedMetrics(Float.MAX_VALUE, 1, 0.8f),
                    new MathEmbedMetrics(0, 1, 0.8f),
                    new MathEmbedMetrics(1, -1, 0),
                    new MathEmbedMetrics(1, 1, Float.POSITIVE_INFINITY),
                    new MathEmbedMetrics(1, 1, 2)
                }) {
            invalid.put("ore", metrics);
            fallback(layout(embed, MathStyle.TEXT, invalid), embed, "layout", 0, embed.length());
        }
        StringBuilder tooDeep = new StringBuilder();
        for (int i = 0; i < 65; i++) tooDeep.append('{');
        tooDeep.append('x');
        for (int i = 0; i < 65; i++) tooDeep.append('}');
        MathLayout bounded = layout(tooDeep.toString(), MathStyle.TEXT, slots());
        require(bounded.fallback && bounded.diagnostics.get(0).stage.equals("parse"), "解析深度没有边界");
        FontSource validMath =
                text.mathFont(style(EM)).text("x").lines().get(0).glyphs().get(0).source;
        FontSource missing =
                FontSource.fromResource(
                        "math-layout-warning",
                        1,
                        "assets/qz_uilib/fonts/math/missing-layout-font.otf",
                        false);
        TextStyle candidates =
                new TextStyle(
                        "default",
                        EM,
                        -1,
                        Arrays.asList(missing, validMath),
                        TextEffects.NORMAL,
                        TextDecoration.NONE);
        String warningSource = tex("1+@alpha");
        MathLayout warned =
                engine.layout(
                        MathContent.parse(warningSource),
                        candidates,
                        MathStyle.TEXT,
                        text,
                        slots());
        healthy(warned);
        boolean located = false;
        for (MathLayout.Diagnostic diagnostic : warned.diagnostics)
            located |=
                    diagnostic.stage.equals("resource")
                            && diagnostic.start == 2
                            && diagnostic.end == warningSource.length()
                            && diagnostic.message.contains(missing.id);
        require(located, "成功字体warning没有映射到公式命令来源");
        MathLayout valid = layout("x+1", MathStyle.TEXT, slots());
        healthy(valid);
        MathLayout empty = layout("", MathStyle.TEXT, slots());
        require(
                !empty.fallback
                        && empty.width == 0
                        && empty.embeds.isEmpty()
                        && empty.pieces.isEmpty(),
                "空式不合法");
    }

    private MathLayout layout(
            String source, MathStyle style, Map<String, MathEmbedMetrics> embeds) {
        return engine.layout(MathContent.parse(tex(source)), style(EM), style, text, embeds);
    }

    private static String tex(String source) {
        return source.replace('@', (char) 92);
    }

    private static TextStyle style(float em) {
        return new TextStyle("default", em, -1, null, TextEffects.NORMAL, TextDecoration.NONE);
    }

    private static Map<String, MathEmbedMetrics> slots(String... ids) {
        Map<String, MathEmbedMetrics> slots = new HashMap<String, MathEmbedMetrics>();
        for (String id : ids) slots.put(id, new MathEmbedMetrics(1, 1, 0.8f));
        return slots;
    }

    private static MathLayout.Embed embed(MathLayout layout, String id) {
        for (MathLayout.Embed embed : layout.embeds) if (embed.id.equals(id)) return embed;
        throw new AssertionError("丢失嵌入 " + id);
    }

    private static MathLayout.TextPiece piece(MathLayout layout, int sourceStart) {
        for (MathLayout.TextPiece piece : layout.pieces)
            if (piece.sourceStart == sourceStart) return piece;
        throw new AssertionError("丢失来源字形 " + sourceStart);
    }

    private static float[] ink(MathLayout.TextPiece piece) {
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (TextLayout.Line line : piece.text.lines())
            for (TextLayout.Glyph glyph : line.glyphs()) {
                InkBounds ink = glyph.inkBounds;
                require(ink.known, "没有真实已知字形墨迹");
                left = Math.min(left, piece.x + glyph.x + ink.left);
                right = Math.max(right, piece.x + glyph.x + ink.right);
                top = Math.min(top, piece.y + glyph.y + ink.top);
                bottom = Math.max(bottom, piece.y + glyph.y + ink.bottom);
            }
        return new float[] {left, top, right, bottom};
    }

    private static void healthy(MathLayout layout) {
        require(!layout.fallback, "有效式降级: " + diagnosticMessages(layout));
        require(
                Float.isFinite(layout.width)
                        && Float.isFinite(layout.height)
                        && Float.isFinite(layout.baseline)
                        && layout.width >= 0
                        && layout.baseline >= 0
                        && layout.baseline <= layout.height,
                "无效几何");
        for (MathLayout.TextPiece piece : layout.pieces) {
            float[] ink = ink(piece);
            require(
                    ink[0] >= -0.002f
                            && ink[1] >= -0.002f
                            && ink[2] <= layout.width + 0.002f
                            && ink[3] <= layout.height + 0.002f,
                    "实际墨迹超出公式extent");
        }
        for (MathLayout.Embed embed : layout.embeds)
            require(
                    embed.x >= -0.002f
                            && embed.y >= -0.002f
                            && embed.x + embed.width <= layout.width + 0.002f
                            && embed.y + embed.height <= layout.height + 0.002f,
                    "嵌入超出extent");
        for (MathLayout.Rule rule : layout.rules)
            require(
                    rule.x >= -0.002f
                            && rule.y >= -0.002f
                            && rule.x + rule.width <= layout.width + 0.002f
                            && rule.y + rule.height <= layout.height + 0.002f,
                    "rule超出extent");
    }

    private static String diagnosticMessages(MathLayout layout) {
        StringBuilder result = new StringBuilder();
        for (MathLayout.Diagnostic d : layout.diagnostics)
            result.append(d.stage)
                    .append('[')
                    .append(d.start)
                    .append(',')
                    .append(d.end)
                    .append("] ")
                    .append(d.message)
                    .append(';');
        return result.toString();
    }

    private static void fallback(
            MathLayout layout, String source, String stage, int start, int end) {
        require(
                layout.fallback
                        && layout.sourceFallback != null
                        && layout.sourceFallback.originalText.equals(source),
                "降级未保留完整原文");
        require(
                layout.embeds.isEmpty() && layout.rules.isEmpty() && layout.pieces.size() == 1,
                "降级残留半解析placement");
        require(
                layout.width >= 0 && layout.baseline >= 0 && Float.isFinite(layout.width),
                "降级不可测量");
        for (MathLayout.Diagnostic diagnostic : layout.diagnostics)
            if (diagnostic.stage.equals(stage)
                    && diagnostic.start == start
                    && diagnostic.end == end) return;
        throw new AssertionError(
                "缺少精确诊断 " + stage + "[" + start + "," + end + "]: " + diagnosticMessages(layout));
    }

    private float metric(int id, float em) {
        require(em == EM, "raw探针必须与布局em相同");
        Float cached = nativeMetrics.get(id);
        if (cached != null) return cached;
        int design =
                id == 55
                        ? sfnt.getShort(constants + 212) & 65535
                        : id <= 3
                                ? sfnt.getShort(constants + id * 2) & 65535
                                : sfnt.getShort(constants + 8 + (id - 4) * 4);
        int position = OpenType.hb_ot_math_get_constant(raw.font, id);
        float actual = id == 55 ? position : position * raw.scale;
        float expected = id == 55 ? design : design * em / unitsPerEm;
        float halfStep = id == 55 ? 0 : raw.scale / 2;
        require(
                Math.abs(actual - expected) <= halfStep + 0.00001f,
                "SFNT与rawHB常量超出26.6量化误差: " + id + " design=" + expected + " raw=" + actual);
        System.out.println(
                "MATH oracle id="
                        + id
                        + " design="
                        + design
                        + " designLogical="
                        + expected
                        + " raw26.6="
                        + position
                        + " nativeLogical="
                        + actual
                        + " halfStep="
                        + halfStep);
        nativeMetrics.put(id, actual);
        return actual;
    }

    private float percent(int id) {
        return sfnt.getShort(constants + id * 2) / 100f;
    }

    private static void near(float actual, float expected, String message) {
        require(
                Float.isFinite(actual) && Math.abs(actual - expected) <= 0.003f,
                message + ": actual=" + actual + " expected=" + expected);
    }

    private static byte[] resource(String path) throws Exception {
        try (InputStream in = MathLayoutScenario.class.getClassLoader().getResourceAsStream(path)) {
            require(in != null, "缺随包字体 " + path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            for (int n; (n = in.read(chunk)) != -1; ) out.write(chunk, 0, n);
            return out.toByteArray();
        }
    }

    /** 与正式布局独立的 raw FT/HB 探针；不调用 MathFont.constant 生成自己的期望。 */
    private static final class RawMath implements AutoCloseable {
        private long library, font;
        private FT_Face face;
        private ByteBuffer memory;
        final float scale;

        RawMath(byte[] bytes, float em) {
            long size = Math.max(64L, Math.round(em * 64.0));
            scale = em / (size / 64.0f) / 64.0f;
            try {
                memory = MemoryUtil.memAlloc(bytes.length);
                memory.put(bytes).flip();
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    PointerBuffer out = stack.mallocPointer(1);
                    check(FreeType.FT_Init_FreeType(out));
                    library = out.get(0);
                    check(FreeType.FT_New_Memory_Face(library, memory, 0, out));
                    face = FT_Face.create(out.get(0));
                    check(FreeType.FT_Set_Char_Size(face, 0, size, 72, 72));
                    font = HarfBuzz.hb_ft_font_create_referenced(face.address());
                    require(font != 0, "raw HB font创建失败");
                    HarfBuzz.hb_ft_font_set_load_flags(
                            font, FreeType.FT_LOAD_DEFAULT | FreeType.FT_LOAD_COLOR);
                    require(
                            OpenType.hb_ot_math_has_data(HarfBuzz.hb_font_get_face(font)),
                            "raw字体没有MATH");
                }
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        int largestVariant(int cp) {
            int glyph = FreeType.FT_Get_Char_Index(face, cp);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer count = stack.ints(0);
                int total =
                        OpenType.hb_ot_math_get_glyph_variants(
                                font, glyph, HarfBuzz.HB_DIRECTION_TTB, 0, count, null);
                require(total > 0, "raw算子没有variant");
                int selected = 0, maximum = -1;
                try (hb_ot_math_glyph_variant_t.Buffer values =
                        hb_ot_math_glyph_variant_t.malloc(total)) {
                    count.put(0, total);
                    OpenType.hb_ot_math_get_glyph_variants(
                            font, glyph, HarfBuzz.HB_DIRECTION_TTB, 0, count, values);
                    for (int i = 0; i < count.get(0); i++) {
                        if (values.get(i).advance() > maximum) {
                            maximum = values.get(i).advance();
                            selected = values.get(i).glyph();
                        }
                    }
                }
                require(
                        maximum * scale < OpenType.hb_ot_math_get_constant(font, 3) * scale,
                        "此反例未触发display建议高度超过算子最大完整variant");
                System.out.println(
                        "display operator U+"
                                + Integer.toHexString(cp)
                                + " largestGlyph="
                                + selected
                                + " maxLogical="
                                + maximum * scale
                                + " preferredLogical="
                                + OpenType.hb_ot_math_get_constant(font, 3) * scale);
                return selected;
            }
        }

        private static void check(int code) {
            require(code == 0, "raw FT error=" + code);
        }

        public void close() {
            if (font != 0) {
                HarfBuzz.hb_font_destroy(font);
                font = 0;
            }
            if (face != null) {
                check(FreeType.FT_Done_Face(face));
                face = null;
            }
            if (library != 0) {
                check(FreeType.FT_Done_FreeType(library));
                library = 0;
            }
            if (memory != null) {
                MemoryUtil.memFree(memory);
                memory = null;
            }
        }
    }

    private static int table(byte[] bytes, String tag) {
        ByteBuffer sfnt = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int count = sfnt.getShort(4) & 65535;
        for (int i = 0; i < count; i++) {
            int offset = 12 + i * 16;
            if (new String(bytes, offset, 4, StandardCharsets.US_ASCII).equals(tag))
                return sfnt.getInt(offset + 8);
        }
        throw new AssertionError("字体缺少表 " + tag);
    }
}
