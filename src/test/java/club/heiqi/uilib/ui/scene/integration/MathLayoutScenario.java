package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.TextDecoration;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
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

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.lwjgl.util.harfbuzz.HarfBuzz;
import org.lwjgl.util.harfbuzz.OpenType;
import org.lwjgl.util.harfbuzz.hb_ot_math_glyph_variant_t;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
        scenario("同分组定界配对与UTF16完整源诊断", this::delimiterFailures);
        scenario("真实定界字形数学轴多middle与嵌套Item", this::delimiters);
        scenario("定界token矩阵及不可见边界四样式", this::delimiterTokens);
        scenario("二项式真实stack净空居中与外侧脚本", this::binomial);
        scenario("真实定界字形原点平移后墨迹仍居数学轴", this::delimiterOrigins);
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
        // smallint 必须保普通 STIX 字形；仅成功显示 U222B 不能证明算子尺寸语义正确。
        MathLayout smallIntegral = layout(tex("@smallint"), MathStyle.DISPLAY, slots());
        healthy(smallIntegral);
        TextLayout.Glyph smallGlyph =
                smallIntegral.pieces.get(0).text.lines().get(0).glyphs().get(0);
        require(
                smallGlyph.glyphId == normalId && smallGlyph.glyphId != actual.glyphId,
                "smallint错误选择display积分large variant");
        near(
                smallGlyph.inkBounds.bottom - smallGlyph.inkBounds.top,
                normal.lines().get(0).glyphs().get(0).inkBounds.bottom
                        - normal.lines().get(0).glyphs().get(0).inkBounds.top,
                "smallint没有保持普通积分墨迹");
        near(
                smallIntegral.pieces.get(0).text.advanceWidth,
                normal.advanceWidth,
                "smallint没有保持普通积分advance");
        require(
                smallIntegral.pieces.get(0).sourceStart == 0
                        && smallIntegral.pieces.get(0).sourceEnd == tex("@smallint").length(),
                "smallint完整控制词来源丢失");
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
        // 根号延长横线保留浮点绘制；验证真实厚度、净空和覆盖，不要求矩形表示。
        MathLayout.Stroke rootRule = root.strokes.get(0);
        near(degree.width, EM * percent(1), "根指数应使用scriptscript");
        near(rootRule.width, metric(51, EM), "根式横线厚度");
        near(rootRule.y1, rootRule.y2, "根式横线必须水平");
        require(body.y - rootRule.y1 - rootRule.width / 2 + 0.002f >= metric(49, EM), "根式真实净空");
        require(rootRule.x1 <= body.x && rootRule.x2 >= body.x + body.width, "根式横线未覆盖被开方盒");
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
        require(nested.rules.size() == 1 && nested.strokes.size() == 1, "嵌套根式分式丢横线");
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
                        .replace((char) 0x00a0, ' ')
                        .equals(" 中文 α "),
                "text组丢失Unicode或空格");
        TextLayout actualText = mathFonts.pieces.get(mathFonts.pieces.size() - 1).text;
        TextLayout nativeText =
                text.layout(" 中文 α ", actualText.style, Integer.MAX_VALUE, TextPolicy.NO_WRAP);
        near(
                actualText.advanceWidth,
                nativeText.advanceWidth,
                "text Unicode/空格未沿同一真实native整形run测量");
        require(actualText.logicalText.equals(nativeText.logicalText), "native text run语义不一致");
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
                "macro",
                0,
                unknownControl.length());
        // 完整参数合同接受 primitive：缺绑定是 layout 错误，缺第二 frac 参数在 macro 层诊断；TEXT Unicode 交原生字体覆盖。
        String bareEmbed = tex("@embed") + emoji, missingArgument = tex("@frac") + emoji;
        fallback(
                layout(bareEmbed, MathStyle.TEXT, slots()),
                bareEmbed,
                "layout",
                0,
                bareEmbed.length());
        MathLayout boundUnicode = layout(bareEmbed, MathStyle.TEXT, slots(emoji));
        healthy(boundUnicode);
        require(
                boundUnicode.embeds.size() == 1 && boundUnicode.embeds.get(0).id.equals(emoji),
                "Unicode primitive Item标识丢失");
        fallback(
                layout(missingArgument, MathStyle.TEXT, slots()),
                missingArgument,
                "macro",
                missingArgument.length(),
                missingArgument.length());
        MathLayout unicodeText = layout(tex("@text") + emoji, MathStyle.TEXT, slots());
        healthy(unicodeText);
        require(
                unicodeText.pieces.size() == 1
                        && unicodeText.pieces.get(0).text.originalText.equals(emoji),
                "primitive text Unicode未交真实native整形");
        for (MathLayout.Diagnostic diagnostic : unicodeText.diagnostics) {
            require(
                    diagnostic.start <= diagnostic.end
                            && diagnostic.start >= 0
                            && diagnostic.end <= (tex("@text") + emoji).length(),
                    "Unicode native覆盖诊断越界");
        }
        String duplicate = tex("@embed{ore}+@embed{ore}");
        fallback(
                layout(duplicate, MathStyle.TEXT, slots("ore")),
                duplicate,
                "parse",
                duplicate.lastIndexOf((char) 92),
                duplicate.lastIndexOf((char) 92) + tex("@embed").length());
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
        hugeSlot.put("huge", new MathEmbedMetrics(1, Float.MAX_VALUE, 0.8f));
        fallback(
                layout(huge, MathStyle.TEXT, hugeSlot),
                huge,
                "layout",
                huge.indexOf(tex("@embed")),
                huge.length() - 1);
        for (String malformed : new String[] {"x^2^3", "x_1_2", "{x", "@frac{x}", "@left(x"}) {
            String source = tex(malformed);
            MathLayout result = layout(source, MathStyle.TEXT, slots());
            require(
                    result.fallback && result.sourceFallback.originalText.equals(source),
                    "错误式丢失完整原文: " + source);
        }
        String missingGlyph = "1+" + new String(Character.toChars(0x10ffff));
        MathLayout missingText = layout(missingGlyph, MathStyle.TEXT, slots());
        require(!missingText.fallback, "匿名Unicode文字缺覆盖被误判为数学整式失败");
        boolean nativeWarning = false, replacement = false, completeSource = false;
        for (MathLayout.TextPiece piece : missingText.pieces)
            if (piece.sourceStart == 2 && piece.sourceEnd == missingGlyph.length()) {
                completeSource = piece.text.originalText.equals(missingGlyph.substring(2));
                for (TextLayout.Diagnostic diagnostic : piece.text.diagnostics())
                    nativeWarning |=
                            diagnostic.kind == TextLayout.Diagnostic.Kind.MISSING_GLYPH
                                    && diagnostic.sourceStart == 0
                                    && diagnostic.sourceEnd == missingGlyph.substring(2).length();
                for (TextLayout.Line line : piece.text.lines())
                    for (TextLayout.Glyph glyph : line.glyphs()) replacement |= glyph.replacement;
            }
        require(
                completeSource && nativeWarning && replacement,
                "匿名文字缺覆盖未保留原source/native诊断/稳定替代事实");
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
        // 宏作用域前置检查与 parser row 的计数不同，保两层实际边界及精确原文来源，不只检查任意 fallback。
        for (int levels : new int[] {63, 64, 65}) {
            StringBuilder nested = new StringBuilder();
            for (int i = 0; i < levels; i++) nested.append('{');
            nested.append('x');
            for (int i = 0; i < levels; i++) nested.append('}');
            MathLayout bounded = layout(nested.toString(), MathStyle.TEXT, slots());
            if (levels == 63) healthy(bounded);
            else
                fallback(
                        bounded,
                        nested.toString(),
                        levels == 64 ? "parse" : "macro",
                        levels,
                        levels + 1);
        }
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

    /** 这些反例经过生产解析和整式降级入口，不访问内部 AST。 */
    private void delimiterFailures() {
        String[][] cases = {
            {"𝑥@left", "@left", "macro", "缺少定界token"},
            {"𝑥@left😀x@right)", "😀", "parse", "无效码点token"},
            {"@left a x@right)", "a", "parse", "普通字母不能作token"},
            {"@left@unknown x@right)", "@unknown ", "parse", "未知命令token包含lexer消费的尾随空白"},
            {"x@middle|y", "@middle", "parse", "middle没有本组left"},
            {"x@right)", "@right", "parse", "right没有本组left"},
            {"@left({x@right)}", "@right", "parse", "right不可跨花括号"},
            {"{@left(x}@right)", "@left", "parse", "left缺少本组right归发起token"},
            {"@sqrt[@left(x]@right){y}", "@right", "macro", "根指数先消费后right缺少参数"},
            {"@left(x@middle@right)", "@right", "parse", "middle缺少token"},
            {"@left(x@right", "@right", "macro", "right缺少token"},
            {"@left(x@middle|y", "@left", "parse", "围合组未闭合归发起token"},
            {"@left(@left[x@right)@right]@right)", "@right", "parse", "嵌套结束后多余right"}
        };
        for (String[] item : cases) {
            String source = tex(item[0]), token = tex(item[1]);
            int start = token.isEmpty() ? source.length() : source.lastIndexOf(token);
            int end = token.isEmpty() ? start : start + token.length();
            // 宏前置参数检查与配对解析分别归属真实控制词/token；fallback仍保完整原输入和UTF16来源。
            fallback(layout(source, MathStyle.TEXT, slots()), source, item[2], start, end);
        }
        String missing = tex("@left(@embed{missing}@right)");
        int start = missing.indexOf(tex("@embed"));
        fallback(
                layout(missing, MathStyle.TEXT, slots()),
                missing,
                "layout",
                start,
                missing.indexOf(tex("@right")));
        String duplicate = tex("@left(@embed{ore}@middle|@embed{ore}@right)");
        int repeated = duplicate.lastIndexOf(tex("@embed"));
        fallback(
                layout(duplicate, MathStyle.TEXT, slots("ore")),
                duplicate,
                "parse",
                repeated,
                repeated + tex("@embed").length());
        healthy(layout("@left(x@right)", MathStyle.TEXT, slots()));
        // fixed允许middle穿过普通花括号作用于当前left；不是必须拒绝的反例。
        healthy(layout("@left({x@middle|y}@right)", MathStyle.TEXT, slots()));
    }

    private void delimiters() {
        Map<String, MathEmbedMetrics> items = slots("deep", "up", "lo");
        items.put("deep", new MathEmbedMetrics(1.3f, 9, 1.2f));
        String source =
                tex(
                        "a@left(@embed{deep}@middle|x@middle@Vert"
                                + " y@right@rfloor^{@embed{up}}_{@embed{lo}}");
        MathLayout layout = layout(source, MathStyle.TEXT, items);
        healthy(layout);
        float axis = layout.baseline - metric(5, EM);
        MathLayout.Embed deep = embed(layout, "deep");
        float maxDist = Math.max(axis - deep.y, deep.y + deep.height - axis);
        // 固定KaTeX0.16.21 delimiter.js独立公式；实际glyph可以超过建议高度。
        float target = Math.max(maxDist / 500 * 901, 2 * maxDist - 0.5f * EM);
        for (String command : new String[] {"@left", "@middle|", "@middle@Vert", "@right"}) {
            MathLayout.TextPiece p = piece(layout, source.indexOf(tex(command)));
            float[] bounds = ink(p);
            near((bounds[1] + bounds[3]) / 2, axis, "每个真实glyph必须各自居中数学轴");
            require(bounds[3] - bounds[1] + 0.003f >= target, "真实定界墨迹未覆盖共同目标");
            require(p.text.lines().get(0).glyphs().size() > 1, "高Item未进入真实assembly");
        }
        MathLayout.TextPiece left = piece(layout, source.indexOf(tex("@left")));
        MathLayout.TextPiece right = piece(layout, source.indexOf(tex("@right")));
        require(
                Math.abs((ink(left)[3] - ink(left)[1]) - (ink(right)[3] - ink(right)[1])) > 0.003f,
                "此反例没有触发左右实际height不同");
        MathLayout.Embed upper = embed(layout, "up"), lower = embed(layout, "lo");
        require(
                upper.x >= ink(right)[2] - 0.003f && lower.x >= ink(right)[2] - 0.003f,
                "脚本未跟随整个围合结构");
        require(lower.y - upper.y - upper.height + 0.003f >= metric(15, EM), "围合组双脚本净空不足");
        near(left.x - layout("a", MathStyle.TEXT, slots()).width, EM * 3 / 18, "围合结构外侧不是INNER间距");

        String simple = tex("@left(@embed{deep}x y@right)");
        String middle = tex("@left(@embed{deep}@middle|x@middle@Vert y@right)");
        MathLayout before = layout(simple, MathStyle.TEXT, items),
                after = layout(middle, MathStyle.TEXT, items);
        healthy(before);
        healthy(after);
        near(
                ink(piece(before, 0))[3] - ink(piece(before, 0))[1],
                ink(piece(after, 0))[3] - ink(piece(after, 0))[1],
                "middle参与了自身定高");
        MathLayout.TextPiece mid = piece(after, middle.indexOf(tex("@middle|")));
        near(
                mid.x - embed(after, "deep").x - embed(after, "deep").width,
                EM * 5 / 18,
                "middle前不是REL间距");
        MathLayout.TextPiece x = piece(after, middle.indexOf('x'));
        float xOrigin = layout("x", MathStyle.TEXT, slots()).pieces.get(0).x;
        near(x.x - xOrigin - mid.x - mid.text.advanceWidth, EM * 5 / 18, "middle后不是REL间距");
        MathLayout nested =
                layout(
                        "@left(@frac{@left[@embed{deep}@middle|x@right]}{1+@frac{x}{y}}@right)",
                        MathStyle.DISPLAY,
                        items);
        healthy(nested);
        require(nested.rules.size() == 2 && nested.embeds.size() == 1, "嵌套分式围合结构丢失内部内容");
        require(
                ink(piece(nested, 0))[3] - ink(piece(nested, 0))[1]
                        > ink(piece(nested, tex("@left(@frac{").length()))[3]
                                - ink(piece(nested, tex("@left(@frac{").length()))[1],
                "外层未覆盖内部围合结构");
        System.out.println(
                "delimiter target="
                        + target
                        + " axis="
                        + axis
                        + " leftHeight="
                        + (ink(left)[3] - ink(left)[1])
                        + " rightHeight="
                        + (ink(right)[3] - ink(right)[1]));
        nestedMiddle();
    }

    private void nestedMiddle() {
        // fixed只有left直接可见的middle被重定高；普通group/frac/cell包住的middle保size1并贡献真实墨迹。
        for (float em : new float[] {18.7f, 32.3f, 51.1f}) {
            MathLayout big =
                    engine.layout(
                            MathContent.parse(tex("@big|")),
                            style(em),
                            MathStyle.TEXT,
                            text,
                            slots());
            healthy(big);
            MathLayout.TextPiece expected = big.pieces.get(0);
            float[] expectedInk = ink(expected);
            for (String body :
                    new String[] {
                        "{x@middle|y}",
                        "@frac{x@middle|y}{z}",
                        "@begin{matrix}x@middle|y@end{matrix}"
                    }) {
                String source = tex("@left(" + body + "@right)");
                MathLayout result =
                        engine.layout(
                                MathContent.parse(source),
                                style(em),
                                MathStyle.TEXT,
                                text,
                                slots());
                healthy(result);
                int start = source.indexOf(tex("@middle"));
                MathLayout.TextPiece actual = piece(result, start);
                float[] actualInk = ink(actual);
                require(
                        actual.sourceEnd == start + tex("@middle|").length(),
                        "nestedmiddle来源丢失真实参数token");
                near(
                        actual.text.style.logicalEm,
                        em,
                        "nestedmiddle fixed baseStyle被script/cell比例再次缩小");
                near(
                        actual.text.advanceWidth,
                        expected.text.advanceWidth,
                        "nestedmiddle未复用size1真实glyph宽度");
                near(
                        actualInk[3] - actualInk[1],
                        expectedInk[3] - expectedInk[1],
                        "nestedmiddle变成外共同target或零墨迹");
                for (TextLayout.Line line : actual.text.lines())
                    for (TextLayout.Glyph glyph : line.glyphs())
                        require(
                                glyph.glyphId != 0
                                        && !glyph.replacement
                                        && glyph.source.id.equals("stix-two-math"),
                                "nestedmiddle未真实画STIX字形");
                require(
                        result.rules.size() == (body.startsWith("@frac") ? 1 : 0),
                        "nestedmiddle丢分式横线");
            }
            Map<String, MathEmbedMetrics> items = slots("deep");
            items.put("deep", new MathEmbedMetrics(1.3f, 9, 1.2f));
            String mixed =
                    tex("@left({x@middle|y}@left[@embed{deep}@middle|z@right]@middle|a@right)");
            MathLayout scopes =
                    engine.layout(MathContent.parse(mixed), style(em), MathStyle.TEXT, text, items);
            healthy(scopes);
            require(
                    scopes.embeds.size() == 1 && scopes.embeds.get(0).id.equals("deep"),
                    "nearestfence丢Item唯一空间");
            MathLayout.TextPiece grouped = piece(scopes, mixed.indexOf(tex("@middle"))),
                    outer = piece(scopes, mixed.lastIndexOf(tex("@middle")));
            near(
                    ink(grouped)[3] - ink(grouped)[1],
                    expectedInk[3] - expectedInk[1],
                    "普通group泄漏nearest外target");
            require(
                    ink(outer)[3] - ink(outer)[1] > ink(grouped)[3] - ink(grouped)[1],
                    "外directmiddle未使用外共同target");
            System.out.println(
                    "PASS nestedmiddle em="
                            + em
                            + " size1Ink="
                            + (expectedInk[3] - expectedInk[1])
                            + " grouped="
                            + grouped.sourceStart
                            + ":"
                            + grouped.sourceEnd
                            + " directOuterInk="
                            + (ink(outer)[3] - ink(outer)[1]));
        }
    }

    private void delimiterTokens() {
        String[] tokens = {
            "(",
            ")",
            "[",
            "]",
            "@lparen",
            "@rparen",
            "@lbrack",
            "@rbrack",
            "@{",
            "@}",
            "@lbrace",
            "@rbrace",
            "|",
            "@vert",
            "@lvert",
            "@rvert",
            "@|",
            "@Vert",
            "@lVert",
            "@rVert",
            "<",
            ">",
            "@lt",
            "@gt",
            "⟨",
            "⟩",
            "@langle",
            "@rangle",
            "@lfloor",
            "@rfloor",
            "@lceil",
            "@rceil",
            "@uparrow",
            "@downarrow",
            "@updownarrow",
            "@Uparrow",
            "@Downarrow",
            "@Updownarrow"
        };
        for (String token : tokens) {
            String source = tex("@left" + token + " x@right" + token);
            MathLayout result = layout(source, MathStyle.TEXT, slots());
            healthy(result);
            require(result.pieces.size() == 3, "合法token没有实际glyph: " + token);
            // lexer的控制词token含吞掉的尾随分隔空白；控制符与literal不包含另一个空格token。
            int delimiterEnd =
                    tex("@left" + token).length() + (token.matches("@[A-Za-z]+") ? 1 : 0);
            require(piece(result, 0).sourceEnd == delimiterEnd, "定界token来源范围不完整: " + token);
            // 空/短围合可以选择基本字形，必须仍以真实墨迹中线居轴，不能把基线空白当墨迹。
            for (MathStyle currentStyle : MathStyle.values()) {
                float currentEm =
                        EM
                                * (currentStyle == MathStyle.SCRIPT
                                        ? percent(0)
                                        : currentStyle == MathStyle.SCRIPT_SCRIPT ? percent(1) : 1);
                try (RawMath raw =
                        new RawMath(
                                resource("assets/qz_uilib/fonts/math/STIXTwoMath-Regular.otf"),
                                currentEm)) {
                    float rawAxis = OpenType.hb_ot_math_get_constant(raw.font, 5) * raw.scale;
                    for (String body : new String[] {"", "x"}) {
                        String small = tex("@left" + token + " " + body + "@right" + token);
                        MathLayout actual = layout(small, currentStyle, slots());
                        healthy(actual);
                        for (int marker : new int[] {0, small.indexOf(tex("@right"))}) {
                            float[] realInk = ink(piece(actual, marker));
                            near(
                                    (realInk[1] + realInk[3]) / 2,
                                    actual.baseline - rawAxis,
                                    "小围合真实墨迹未居轴: " + token + " " + currentStyle + " body=" + body);
                        }
                    }
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
            }
        }
        for (MathStyle style : MathStyle.values()) {
            String source = tex("a@left.x@middle.y@right.b");
            MathLayout result = layout(source, style, slots());
            MathLayout plain = layout("axyb", style, slots());
            healthy(result);
            healthy(plain);
            float em =
                    EM
                            * (style == MathStyle.SCRIPT
                                    ? percent(0)
                                    : style == MathStyle.SCRIPT_SCRIPT ? percent(1) : 1);
            float mu = style == MathStyle.DISPLAY || style == MathStyle.TEXT ? 16f / 18 : 0;
            // 固定KaTeX的null边界重置到基准em；INNER/REL间距仍随当前style。
            near(result.width - plain.width, 3 * 0.12f * EM + em * mu, "null定界符间距或样式缩放错误");
            require(result.pieces.size() == 4, "null定界符产生墨迹");
            Map<String, MathEmbedMetrics> tall = slots("tall");
            tall.put("tall", new MathEmbedMetrics(1, 6, 1.2f));
            MathLayout stretched = layout("@left(@embed{tall}@right)", style, tall);
            healthy(stretched);
            try (RawMath current =
                    new RawMath(
                            resource("assets/qz_uilib/fonts/math/STIXTwoMath-Regular.otf"), em)) {
                float axis = OpenType.hb_ot_math_get_constant(current.font, 5) * current.scale;
                float distance = Math.max(1.2f * em - axis, 4.8f * em + axis);
                float target = Math.max(distance / 500 * 901, 2 * distance - 0.5f * EM);
                MathFont expectedFont = text.mathFont(style(em));
                TextLayout expected = expectedFont.stretch('(', target);
                float[] expectedInk = ink(expected);
                float[] actualInk = ink(piece(stretched, 0));
                near(
                        actualInk[3] - actualInk[1],
                        expectedInk[3] - expectedInk[1],
                        "四style的共同目标未使用基准em短缺量");
                near(
                        (actualInk[1] + actualInk[3]) / 2,
                        stretched.baseline - axis,
                        "四style定界符未按当前MATH轴居中");
                if (style == MathStyle.SCRIPT || style == MathStyle.SCRIPT_SCRIPT) {
                    float wrongTarget = Math.max(distance / 500 * 901, 2 * distance - 0.5f * em);
                    TextLayout wrong = expectedFont.stretch('(', wrongTarget);
                    float[] wrongInk = ink(wrong);
                    require(
                            Math.abs(wrongInk[3] - wrongInk[1] - expectedInk[3] + expectedInk[1])
                                    > 0.003f,
                            "此反例没有区分当前em与基准em短缺量");
                }
                System.out.println(
                        "delimiter style="
                                + style
                                + " em="
                                + em
                                + " target="
                                + target
                                + " nullWidth="
                                + 0.12f * EM);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        }
    }

    private void delimiterOrigins() {
        // 保留正式FT/HB字形source、ink与尺寸，仅移动stretch的局部原点到基线一侧。
        // 定界居中是墨迹性质，不能因字体局部原点不同而改变最终墨迹中线。
        for (final float dy : new float[] {-4 * EM, 4 * EM}) {
            club.heiqi.uilib.ui.scene.text.SceneTextMeasurer shifted =
                    new club.heiqi.uilib.ui.scene.text.SceneTextMeasurer() {
                        public TextLayout layout(String value, TextStyle style, int width) {
                            return text.layout(value, style, width);
                        }

                        public MathFont mathFont(TextStyle style) {
                            final MathFont real = text.mathFont(style);
                            return new MathFont() {
                                public float constant(Constant c) {
                                    return real.constant(c);
                                }

                                public TextLayout text(String value) {
                                    return real.text(value);
                                }

                                public TextLayout stretch(int cp, float height) {
                                    return shiftedGlyphs(real.stretch(cp, height), dy);
                                }

                                public TextLayout displayOperator(int cp, float height) {
                                    return real.displayOperator(cp, height);
                                }

                                public float italicCorrection(TextLayout value) {
                                    return 0;
                                }
                            };
                        }
                    };
            for (String source :
                    new String[] {
                        "@left(x@middle|y@right)",
                        "@binom{x}{y}",
                        "@left(x@middle|y@right)^{@embed{up}}_{@embed{lo}}",
                        "@binom{x}{y}^{@embed{up}}_{@embed{lo}}"
                    }) {
                MathLayout actual =
                        engine.layout(
                                MathContent.parse(tex(source)),
                                style(EM),
                                MathStyle.TEXT,
                                shifted,
                                slots("up", "lo"));
                healthy(actual);
                MathLayout unshifted = layout(source, MathStyle.TEXT, slots("up", "lo"));
                healthy(unshifted);
                System.out.println(
                        "delimiter origin dy="
                                + dy
                                + " height="
                                + actual.height
                                + " referenceHeight="
                                + unshifted.height
                                + " baseline="
                                + actual.baseline
                                + " referenceBaseline="
                                + unshifted.baseline);
                near(actual.height, unshifted.height, "定界原点改变了公式height");
                near(actual.baseline, unshifted.baseline, "定界原点改变了公式baseline");
                for (int i = 0; i < actual.embeds.size(); i++) {
                    near(actual.embeds.get(i).x, unshifted.embeds.get(i).x, "定界原点改变了脚本x");
                    near(actual.embeds.get(i).y, unshifted.embeds.get(i).y, "定界原点改变了脚本y");
                }
                for (MathLayout.TextPiece piece : actual.pieces) {
                    if (!"()|".contains(piece.text.logicalText)) continue;
                    float[] bounds = ink(piece);
                    near(
                            (bounds[1] + bounds[3]) / 2,
                            actual.baseline - metric(5, EM),
                            "单侧墨迹定界符未居轴 dy=" + dy);
                }
            }
        }
    }

    private static TextLayout shiftedGlyphs(TextLayout original, float dy) {
        java.util.List<TextLayout.Line> lines = new java.util.ArrayList<>();
        for (TextLayout.Line line : original.lines()) {
            java.util.List<TextLayout.Glyph> glyphs = new java.util.ArrayList<>();
            for (TextLayout.Glyph g : line.glyphs())
                glyphs.add(
                        new TextLayout.Glyph(
                                g.source,
                                g.size,
                                g.glyphId,
                                g.logicalStart,
                                g.logicalEnd,
                                g.sourceStart,
                                g.sourceEnd,
                                g.x,
                                g.y + dy,
                                g.xAdvance,
                                g.yAdvance,
                                g.color,
                                g.replacement,
                                g.inkBounds));
            lines.add(
                    new TextLayout.Line(
                            line.text,
                            line.x,
                            line.y,
                            line.logicalStart,
                            line.logicalEnd,
                            line.sourceStart,
                            line.sourceEnd,
                            line.advance,
                            line.baseline,
                            line.ascent,
                            line.descent,
                            line.lineHeight,
                            glyphs,
                            line.clusters(),
                            line.decorations(),
                            line.paragraphRtl));
        }
        return new TextLayout(
                original.width,
                original.height,
                original.advanceWidth,
                original.advanceHeight,
                original.style,
                lines,
                original.logicalText,
                original.originalText,
                original.generation,
                original.diagnostics(),
                original.input);
    }

    private void binomial() {
        for (MathStyle style : MathStyle.values()) {
            Map<String, MathEmbedMetrics> items = slots("top", "bottom", "up", "lo");
            items.put("top", new MathEmbedMetrics(1.7f, 4, 3.1f));
            items.put("bottom", new MathEmbedMetrics(0.9f, 3, 1.3f));
            String source = tex("a@binom{@embed{top}}{@embed{bottom}}^{@embed{up}}_{@embed{lo}}b");
            MathLayout result = layout(source, style, items);
            healthy(result);
            require(result.rules.isEmpty(), "binom生成了分式横线");
            MathLayout.Embed top = embed(result, "top"), bottom = embed(result, "bottom");
            near(top.x + top.width / 2, bottom.x + bottom.width / 2, "binom上下部未居中");
            float parentEm =
                    EM
                            * (style == MathStyle.SCRIPT
                                    ? percent(0)
                                    : style == MathStyle.SCRIPT_SCRIPT ? percent(1) : 1);
            float childEm =
                    EM
                            * (style == MathStyle.DISPLAY
                                    ? 1
                                    : style == MathStyle.TEXT ? percent(0) : percent(1));
            near(top.width, 1.7f * childEm, "binom未使用fraction子style");
            // raw实例独立读MATH stack；em变化时重新建立FT/HB探针。
            try (RawMath current =
                    new RawMath(
                            resource("assets/qz_uilib/fonts/math/STIXTwoMath-Regular.otf"),
                            parentEm)) {
                int topId = style == MathStyle.DISPLAY ? 23 : 22;
                int bottomId = style == MathStyle.DISPLAY ? 25 : 24;
                int gapId = style == MathStyle.DISPLAY ? 27 : 26;
                float minimum =
                        OpenType.hb_ot_math_get_constant(current.font, gapId) * current.scale;
                require(bottom.y - top.y - top.height + 0.003f >= minimum, "binom stack净空不足");
                float up = result.baseline - top.y - top.baseline;
                float down = bottom.y + bottom.baseline - result.baseline;
                require(
                        up + 0.003f
                                >= OpenType.hb_ot_math_get_constant(current.font, topId)
                                        * current.scale,
                        "binom上移小于stack常量");
                require(
                        down + 0.003f
                                >= OpenType.hb_ot_math_get_constant(current.font, bottomId)
                                        * current.scale,
                        "binom下移小于stack常量");
                float axis =
                        result.baseline
                                - OpenType.hb_ot_math_get_constant(current.font, 5) * current.scale;
                int start = source.indexOf(tex("@binom"));
                int delimiters = 0;
                for (MathLayout.TextPiece p : result.pieces)
                    if (p.sourceStart == start) {
                        float[] bounds = ink(p);
                        near((bounds[1] + bounds[3]) / 2, axis, "binom括号未居中轴");
                        require(
                                bounds[1] <= top.y + 0.003f
                                        && bounds[3] + 0.003f >= bottom.y + bottom.height,
                                "binom括号未覆盖实际stack");
                        delimiters++;
                    }
                require(delimiters == 2, "binom缺少两侧真实括号");
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            MathLayout bare = layout("a@binom{x}{y}b", style, slots());
            MathLayout alone = layout("@binom{x}{y}", style, slots());
            MathLayout ab = layout("ab", style, slots());
            healthy(bare);
            healthy(alone);
            near(bare.width, alone.width + ab.width, "binom外侧不是ORD间距");
        }
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
        float[] bounds = ink(piece.text);
        bounds[0] += piece.x;
        bounds[2] += piece.x;
        bounds[1] += piece.y;
        bounds[3] += piece.y;
        return bounds;
    }

    private static float[] ink(TextLayout layout) {
        float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
        for (TextLayout.Line line : layout.lines())
            for (TextLayout.Glyph glyph : line.glyphs()) {
                InkBounds ink = glyph.inkBounds;
                require(ink.known, "没有真实已知字形墨迹");
                left = Math.min(left, glyph.x + ink.left);
                right = Math.max(right, glyph.x + ink.right);
                top = Math.min(top, glyph.y + ink.top);
                bottom = Math.max(bottom, glyph.y + ink.bottom);
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
