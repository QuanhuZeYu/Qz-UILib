package club.heiqi.uilib.ui.scene.integration;

import static club.heiqi.uilib.ui.scene.text.math.MathFont.Constant.*;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathEmbedMetrics;
import club.heiqi.uilib.ui.scene.text.math.MathLayout;
import club.heiqi.uilib.ui.scene.text.math.MathLayoutEngine;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** 正式 STIX 字体的二维几何反例；GPU 图像由父平台场景另取证。 */
public final class MathArrayScenario extends TestApplication {
    private final MathLayoutEngine engine = new MathLayoutEngine();
    private FontTextService text;

    public static void main(String[] args) throws Exception {
        new MathArrayScenario().run(args);
    }

    @Override
    protected void setup() throws Exception {
        text = own(new FontTextService(new LwjglFontEngine()));
    }

    @Override
    protected void runTests() throws Exception {
        scenario("多字号非对称Item列对齐行净空", this::alignment);
        scenario("显式行距空cell短行与分隔插入列", this::gapsAndLines);
        scenario("矩阵定界真实墨迹数学轴及外侧脚本", this::fencesAndScripts);
        scenario("模式资格separation样式标签与33环境", this::environments);
        scenario("嵌套二维分式根式和有序绘制事实", this::nestedAndEntries);
        scenario("显式文档编号输入重排与下一公式串联", this::numbering);
        scenario("顶级标签root模式资格与真实盒尺寸", this::rootTags);
        scenario("二维cell真实字族字重姿态与完整作用域", this::fontScopes);
        scenario("显式样式字族解除cramped与透明原子边界", this::declaredStyles);
    }

    private Map<String, MathEmbedMetrics> items() {
        Map<String, MathEmbedMetrics> result = new HashMap<String, MathEmbedMetrics>();
        result.put("a", new MathEmbedMetrics(2.7f, 6.4f, .4f));
        result.put("b", new MathEmbedMetrics(.6f, 1.3f, 1.2f));
        result.put("c", new MathEmbedMetrics(1.1f, 2.4f, 2.2f));
        result.put("d", new MathEmbedMetrics(3.4f, 1.2f, .7f));
        result.put("up", new MathEmbedMetrics(1.4f, 2.7f, 2.4f));
        result.put("lo", new MathEmbedMetrics(1.7f, 3.8f, .5f));
        return result;
    }

    private TextStyle style(float em) {
        return new TextStyle(
                "math",
                em,
                -1,
                null,
                club.heiqi.uilib.ui.scene.text.TextEffects.NORMAL,
                club.heiqi.uilib.ui.scene.text.TextDecoration.NONE);
    }

    private MathLayout layout(String source, float em, MathStyle mode) {
        return engine.layout(MathContent.parse(tex(source)), style(em), mode, text, items());
    }

    private static String tex(String source) {
        return source.replace('@', (char) 92).replace('#', '@');
    }

    private static MathLayout.Embed item(MathLayout value, String id) {
        for (MathLayout.Embed item : value.embeds) if (item.id.equals(id)) return item;
        throw new AssertionError("缺少Item: " + id);
    }

    private void healthy(MathLayout value) {
        require(!value.fallback, "二维式降级: " + diagnostics(value));
        require(
                Float.isFinite(value.width + value.height + value.baseline)
                        && value.width >= 0
                        && value.height >= 0,
                "几何无效");
        for (MathLayout.Embed item : value.embeds) {
            require(
                    item.x >= -.003f
                            && item.y >= -.003f
                            && item.x + item.width <= value.width + .003f
                            && item.y + item.height <= value.height + .003f,
                    "Item超出公式包络: " + item.id);
        }
        require(
                value.entries.size()
                        == value.pieces.size()
                                + value.rules.size()
                                + value.strokes.size()
                                + value.embeds.size(),
                "derived views遗漏绘制事实");
    }

    private static String diagnostics(MathLayout value) {
        StringBuilder result = new StringBuilder();
        for (MathLayout.Diagnostic d : value.diagnostics)
            result.append(d.stage).append(':').append(d.message).append(';');
        return result.toString();
    }

    private void near(float a, float b, String message) {
        require(Math.abs(a - b) < .008f, message + " actual=" + a + " expected=" + b);
    }

    private void declaredStyles() {
        boolean distinctCramped = false;
        for (float em : new float[] {18.7f, 32.3f, 51.1f}) {
            for (String suffix : new String[] {"=b", "+b", "-b"}) {
                MathLayout plain = layout("a" + suffix, em, MathStyle.TEXT),
                        declared = layout("a@textstyle" + suffix, em, MathStyle.TEXT);
                healthy(plain);
                healthy(declared);
                near(declared.width, plain.width, "STYLE fragment改变外层REL/BIN间距");
                require(declared.pieces.size() == plain.pieces.size(), "透明声明字形流数量变化");
                for (int i = 0; i < plain.pieces.size(); i++)
                    near(declared.pieces.get(i).x, plain.pieces.get(i).x, "透明声明glyph定位变化");
            }
            MathLayout grouped = layout("a{+b}", em, MathStyle.TEXT),
                    binary = layout("a+b", em, MathStyle.TEXT);
            healthy(grouped);
            require(grouped.width < binary.width, "STYLE透明处理错误展开普通分组并改变BIN资格");
            MathLayout plainCell = layout("@begin{matrix}x@end{matrix}", em, MathStyle.TEXT),
                    boldCell = layout("@mathbf{@begin{matrix}x@end{matrix}}", em, MathStyle.TEXT);
            healthy(plainCell);
            healthy(boldCell);
            require(
                    boldCell.pieces.size() == 1
                            && boldCell.pieces.get(0).text.logicalText.equals("𝑥"),
                    "array cell styling未清除父数学alphabet");
            near(boldCell.width, plainCell.width, "cell声明样式与独立matrix字形几何不同");
            MathLayout
                    denominatorCell =
                            layout("@frac{1}{@begin{matrix}x^2@end{matrix}}", em, MathStyle.TEXT),
                    independentCell = layout("@begin{matrix}x^2@end{matrix}", em, MathStyle.TEXT);
            healthy(denominatorCell);
            healthy(independentCell);
            near(
                    raise(denominatorCell, "𝑥", "2"),
                    raise(independentCell, "𝑥", "2"),
                    "cell声明样式仍继承分母cramped");
            MathLayout
                    radicandCell =
                            layout(
                                    "@sqrt{@begin{smallmatrix}x^2@end{smallmatrix}}",
                                    em,
                                    MathStyle.TEXT),
                    independentSmall =
                            layout("@begin{smallmatrix}x^2@end{smallmatrix}", em, MathStyle.TEXT);
            healthy(radicandCell);
            healthy(independentSmall);
            near(
                    raise(radicandCell, "𝑥", "2"),
                    raise(independentSmall, "𝑥", "2"),
                    "smallmatrix声明样式仍继承根内容cramped");
            MathLayout reset = layout("@mathbf{x@textstyle x}", em, MathStyle.TEXT);
            healthy(reset);
            require(
                    reset.pieces.size() == 2
                            && reset.pieces.get(0).text.logicalText.equals("𝐱")
                            && reset.pieces.get(1).text.logicalText.equals("𝑥"),
                    "显式样式未解除数学alphabet");
            MathLayout explicit = layout("@frac{1}{@scriptstyle x^2}", em, MathStyle.TEXT),
                    automatic = layout("@frac{1}{x^2}", em, MathStyle.TEXT),
                    standalone = layout("x^2", em, MathStyle.SCRIPT);
            healthy(explicit);
            healthy(automatic);
            healthy(standalone);
            float explicitRaise = raise(explicit, "𝑥", "2"),
                    automaticRaise = raise(automatic, "𝑥", "2");
            near(explicitRaise, raise(standalone, "𝑥", "2"), "显式scriptstyle未解除分母cramped");
            distinctCramped |= explicitRaise > automaticRaise + .008f;
        }
        require(distinctCramped, "多字号未体现自动分母cramped与显式非cramped的真实净空差异");
    }

    private static float raise(MathLayout layout, String base, String script) {
        float baseLine = Float.NaN, scriptLine = Float.NaN;
        for (MathLayout.TextPiece piece : layout.pieces) {
            float baseline = piece.y + piece.text.lines().get(0).baseline;
            if (piece.text.logicalText.equals(base)) baseLine = baseline;
            if (piece.text.logicalText.equals(script)) scriptLine = baseline;
        }
        return baseLine - scriptLine;
    }

    private void alignment() {
        for (float em : new float[] {18.7f, 32.3f, 51.1f})
            for (String align : new String[] {"l", "c", "r"}) {
                MathLayout value =
                        layout(
                                "@begin{array}{"
                                        + align
                                        + align
                                        + "}@embed{a}&@embed{b}@@@embed{c}&@embed{d}@end{array}",
                                em,
                                MathStyle.TEXT);
                healthy(value);
                MathLayout.Embed a = item(value, "a"),
                        b = item(value, "b"),
                        c = item(value, "c"),
                        d = item(value, "d");
                float factor = align.equals("l") ? 0 : align.equals("r") ? 1 : .5f;
                near(a.x + a.width * factor, c.x + c.width * factor, "首列未" + align + "对齐");
                near(b.x + b.width * factor, d.x + d.width * factor, "次列未" + align + "对齐");
                near(a.y + a.baseline, b.y + b.baseline, "同行未共基线");
                near(c.y + c.baseline, d.y + d.baseline, "次行未共基线");
                require(
                        Math.min(c.y, d.y) + .003f >= Math.max(a.y + a.height, b.y + b.height),
                        "非对称Item跨行相交");
                require(
                        Math.min(b.x, d.x) + .003f >= Math.max(a.x + a.width, c.x + c.width),
                        "大型Item跨列相交");
            }
    }

    private void gapsAndLines() {
        float em = 32.3f;
        String body = "@embed{a}&@embed{b}@@@embed{c}&@embed{d}";
        MathLayout plain = layout("@begin{array}{lr}" + body + "@end{array}", em, MathStyle.TEXT);
        MathLayout extra =
                layout(
                        "@begin{array}{lr}@embed{a}&@embed{b}@@[8em]@embed{c}&@embed{d}@end{array}",
                        em,
                        MathStyle.TEXT);
        MathLayout negative =
                layout(
                        "@begin{array}{lr}@embed{a}&@embed{b}@@[-8em]@embed{c}&@embed{d}@end{array}",
                        em,
                        MathStyle.TEXT);
        healthy(plain);
        healthy(extra);
        healthy(negative);
        require(
                item(extra, "c").y - item(extra, "a").y > item(plain, "c").y - item(plain, "a").y,
                "正行距未参与布局");
        float plainBaseline =
                item(plain, "c").y
                        + item(plain, "c").baseline
                        - item(plain, "a").y
                        - item(plain, "a").baseline;
        float negativeBaseline =
                item(negative, "c").y
                        + item(negative, "c").baseline
                        - item(negative, "a").y
                        - item(negative, "a").baseline;
        near(negativeBaseline - plainBaseline, -8 * em, "显式负行距未按 fixed depth+gap 完整移动基线");
        require(
                item(negative, "c").y < item(negative, "a").y + item(negative, "a").height,
                "合法负行距被再次限制为不相交");
        String[] order = {"a", "b", "c", "d"};
        int embedIndex = 0;
        for (MathLayout.Entry entry : negative.entries)
            if (entry.embed != null) {
                require(entry.embed.id.equals(order[embedIndex++]), "重叠Item绘制顺序变化");
                require(
                        tex("@begin{array}{lr}@embed{a}&@embed{b}@@[-8em]@embed{c}&@embed{d}@end{array}")
                                .substring(entry.embed.sourceStart, entry.embed.sourceEnd)
                                .equals(tex("@embed{" + entry.embed.id + "}")),
                        "重叠Item来源失真");
            }
        require(embedIndex == order.length, "重叠Item丢失唯一空间");
        MathLayout shortRows =
                layout(
                        "@begin{matrix}@embed{a}&&@embed{b}@@&@embed{c}@@@embed{d}@end{matrix}",
                        em,
                        MathStyle.TEXT);
        healthy(shortRows);
        require(shortRows.embeds.size() == 4, "短行/空cell丢失内容");
        // 完整行交接必须区分内部整空行与环境末尾整空行，不能统一按空内容丢弃。
        String adjacentRows = "@begin{matrix}@embed{a}@@@embed{b}";
        MathLayout adjacent = layout(adjacentRows + "@end{matrix}", em, MathStyle.TEXT);
        MathLayout trailing = layout(adjacentRows + "@@@end{matrix}", em, MathStyle.TEXT);
        MathLayout internal =
                layout(
                        "@begin{matrix}@embed{a}@@@@@embed{b}@end{matrix}",
                        em,
                        MathStyle.TEXT);
        healthy(adjacent);
        healthy(trailing);
        healthy(internal);
        near(trailing.width, adjacent.width, "末尾整空行改变列宽");
        near(trailing.height, adjacent.height, "末尾整空行产生额外行高");
        near(trailing.baseline, adjacent.baseline, "末尾整空行改变公式基线");
        float adjacentGap =
                item(adjacent, "b").y
                        + item(adjacent, "b").baseline
                        - item(adjacent, "a").y
                        - item(adjacent, "a").baseline;
        float internalGap =
                item(internal, "b").y
                        + item(internal, "b").baseline
                        - item(internal, "a").y
                        - item(internal, "a").baseline;
        require(internalGap > adjacentGap, "内部整空行未保留Item基线间距");
        MathLayout lines =
                layout(
                        "@begin{array}{|l:r|}@hline@embed{a}&@embed{b}@@@hdashline@embed{c}&@embed{d}@@@hline@end{array}",
                        em,
                        MathStyle.TEXT);
        healthy(lines);
        require(lines.rules.size() > 6, "实虚横竖线未实际生成");
        MathLayout inserted =
                layout(
                        "@begin{array}{l#{+}r}@embed{a}&@embed{b}@@@embed{c}&@embed{d}@end{array}",
                        em,
                        MathStyle.TEXT);
        healthy(inserted);
        int plus = 0;
        for (MathLayout.TextPiece p : inserted.pieces) if (p.text.logicalText.equals("+")) plus++;
        require(plus == 2, "插入列未每行真实布局");
        String duplicateSource = "@begin{array}{l#{@embed{a}}r}x&y@@z&w@end{array}";
        MathLayout duplicate = layout(duplicateSource, em, MathStyle.TEXT);
        require(
                duplicate.fallback
                        && duplicate.embeds.isEmpty()
                        && duplicate.sourceFallback != null,
                "插入列重复Item被发布成多个空间");
        boolean located = false;
        int sourceStart = tex(duplicateSource).indexOf(tex("@embed{a}"));
        for (MathLayout.Diagnostic diagnostic : duplicate.diagnostics)
            located |=
                    diagnostic.stage.equals("layout")
                            && diagnostic.start == sourceStart
                            && diagnostic.end >= sourceStart + tex("@embed{a}").length();
        require(located, "重复Item空间缺少实际source来源");
    }

    private void fencesAndScripts() {
        for (float em : new float[] {18.7f, 32.3f, 51.1f}) {
            MathLayout value =
                    layout(
                            "@begin{pmatrix}@embed{a}&x@@y&@embed{d}@end{pmatrix}^{@embed{up}}_{@embed{lo}}",
                            em,
                            MathStyle.TEXT);
            healthy(value);
            MathLayout.Embed up = item(value, "up"), lo = item(value, "lo");
            require(
                    lo.y - up.y - up.height + .003f
                            >= text.mathFont(style(em)).constant(SUB_SUPERSCRIPT_GAP_MIN),
                    "外侧双脚本净空未用MATH");
            float axis = value.baseline - text.mathFont(style(em)).constant(AXIS_HEIGHT);
            for (MathLayout.TextPiece piece : value.pieces)
                if (piece.text.logicalText.equals("(") || piece.text.logicalText.equals(")")) {
                    float[] ink = ink(piece);
                    near((ink[1] + ink[3]) / 2, axis, "矩阵定界墨迹中心不在数学轴");
                }
            float script = text.mathFont(style(em)).constant(SCRIPT_PERCENT_SCALE_DOWN) / 100;
            near(up.width, 1.4f * em * script, "矩阵外script Item字号");
        }
    }

    private void environments() {
        String[] names = {
            "align",
            "align*",
            "alignat",
            "alignat*",
            "aligned",
            "alignedat",
            "array",
            "bmatrix",
            "Bmatrix",
            "bmatrix*",
            "Bmatrix*",
            "cases",
            "CD",
            "darray",
            "dcases",
            "drcases",
            "equation",
            "equation*",
            "gather",
            "gather*",
            "gathered",
            "matrix",
            "matrix*",
            "pmatrix",
            "pmatrix*",
            "rcases",
            "smallmatrix",
            "split",
            "subarray",
            "vmatrix",
            "Vmatrix",
            "vmatrix*",
            "Vmatrix*"
        };
        for (String name : names) {
            String args =
                    name.equals("array") || name.equals("darray")
                            ? "{lr}"
                            : name.equals("subarray") ? "{c}" : name.contains("at") ? "{1}" : "";
            String body =
                    name.startsWith("equation")
                            ? "@embed{a}"
                            : name.equals("CD")
                                    ? "A #>>> B @@ #VVV #AAA @@ C #>>> D"
                                    : name.equals("subarray") || name.startsWith("gather")
                                            ? "@embed{a}@@@embed{b}"
                                            : "@embed{a}&x@@y&@embed{b}";
            MathLayout value =
                    layout(
                            "@begin{" + name + "}" + args + body + "@end{" + name + "}",
                            32.3f,
                            MathStyle.DISPLAY);
            healthy(value);
            require(value.height > 0 && value.width > 0, "环境只有parse没有二维几何: " + name);
        }
        MathLayout align =
                layout("@begin{aligned}a&=b&c&=d@@x&=y&z&=w@end{aligned}", 32.3f, MathStyle.TEXT);
        MathLayout alignat =
                layout(
                        "@begin{alignedat}{2}a&=b&c&=d@@x&=y&z&=w@end{alignedat}",
                        32.3f, MathStyle.TEXT);
        healthy(align);
        healthy(alignat);
        require(align.width > alignat.width, "align和alignat间距未区分");
        for (String name : new String[] {"equation*", "gather*", "alignat*", "align*", "split"}) {
            String args = name.startsWith("alignat") ? "{1}" : "";
            MathLayout rejected =
                    layout(
                            "@begin{" + name + "}" + args + "x@end{" + name + "}",
                            32.3f,
                            MathStyle.TEXT);
            require(rejected.fallback, "仅display环境行内被接受: " + name);
        }
        for (String name : new String[] {"align", "equation", "gather", "CD", "alignat"}) {
            String args = name.equals("alignat") ? "{1}" : "";
            String source =
                    "@begin{"
                            + name
                            + "}"
                            + args
                            + "x@end{"
                            + name
                            + "}+@begin{equation*}y@end{equation*}+@sum";
            MathLayout inline = layout(source, 32.3f, MathStyle.TEXT),
                    display = layout(source, 32.3f, MathStyle.DISPLAY);
            healthy(inline);
            healthy(display);
            near(inline.width, display.width, "完整公式inline自动display未影响相邻star环境/大算符");
            near(inline.height, display.height, "完整公式display尺寸不一致");
            require(
                    layout(source, 32.3f, MathStyle.SCRIPT).fallback,
                    "source自动display覆盖显式SCRIPT入口");
            require(
                    layout(source, 32.3f, MathStyle.SCRIPT_SCRIPT).fallback,
                    "source自动display覆盖显式SS入口");
        }
        healthy(layout("@begin{align}a&=b@end{align}", 32.3f, MathStyle.TEXT));
        MathLayout tagged =
                layout("@begin{align}a&=b@tag{Z}@@c&=d@notag@end{align}", 32.3f, MathStyle.DISPLAY);
        healthy(tagged);
        boolean tag = false;
        for (MathLayout.TextPiece piece : tagged.pieces)
            tag |= piece.text.originalText.contains("Z");
        require(tag, "显式tag丢失");
    }

    private void nestedAndEntries() {
        MathLayout value =
                layout(
                        "@begin{pmatrix}@frac{@begin{matrix}@embed{a}@@x@end{matrix}}{@sqrt{@embed{b}}}&x@@y&@embed{d}@end{pmatrix}",
                        32.3f,
                        MathStyle.DISPLAY);
        healthy(value);
        boolean radicalBar = false;
        for (MathLayout.TextPiece piece : value.pieces)
            for (TextLayout.Line line : piece.text.lines())
                for (TextLayout.Glyph glyph : line.glyphs()) radicalBar |= glyph.xExtension != null;
        require(
                !value.rules.isEmpty() && radicalBar && value.strokes.isEmpty(),
                "嵌套分式根式没有真实横线或混入实线");
        MathLayout ordered = layout("x+@embed{a}+@frac{y}{z}", 32.3f, MathStyle.TEXT);
        healthy(ordered);
        int item = -1, lastText = -1, lastFill = -1;
        for (int i = 0; i < ordered.entries.size(); i++) {
            MathLayout.Entry entry = ordered.entries.get(i);
            if (entry.kind == MathLayout.Entry.Kind.EMBED) item = i;
            if (entry.kind == MathLayout.Entry.Kind.TEXT) lastText = i;
            if (entry.kind == MathLayout.Entry.Kind.FILL) lastFill = i;
        }
        require(item > 0 && lastText > item && lastFill > lastText, "canonical绘制流跨Item顺序丢失");
        boolean immutable = false;
        try {
            ordered.entries.clear();
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        require(immutable, "绘制事实不是不可变快照");
    }

    private void fontScopes() {
        String[] commands = {"textrm", "textsf", "texttt"},
                aliases = {"math-text-roman", "math-text-sans", "math-text-mono"};
        for (float em : new float[] {18.7f, 32.3f})
            for (int family = 0; family < commands.length; family++) {
                for (int variant = 0; variant < 4; variant++) {
                    boolean bold = (variant & 1) != 0, italic = (variant & 2) != 0;
                    String body = "Wi";
                    if (italic) body = "@textit{" + body + "}";
                    if (bold) body = "@textbf{" + body + "}";
                    MathLayout value =
                            layout(
                                    "@begin{array}{c}@"
                                            + commands[family]
                                            + "{"
                                            + body
                                            + "}@end{array}",
                                    em,
                                    MathStyle.TEXT);
                    healthy(value);
                    require(value.pieces.size() == 1, "同样式文字未形成一个native run");
                    TextLayout run = value.pieces.get(0).text;
                    require(run.style.font.equals(aliases[family]), "文字族scope未绑定真实alias");
                    near(run.style.logicalEm, em, "字族浮点em被量化");
                    for (TextLayout.Line line : run.lines())
                        for (TextLayout.Glyph glyph : line.glyphs()) {
                            require(
                                    !glyph.replacement
                                            && glyph.glyphId != 0
                                            && glyph.source.id.startsWith(aliases[family]),
                                    "字族仍选默认/replacement glyph");
                            club.heiqi.uilib.ui.scene.text.FontStyle actual =
                                    glyph.source.actualStyle();
                            require(
                                    actual.weight == (bold ? 700 : 400)
                                            && actual.posture
                                                    == (italic
                                                            ? club.heiqi.uilib.ui.scene.text
                                                                    .FontStyle.Posture.ITALIC
                                                            : club.heiqi.uilib.ui.scene.text
                                                                    .FontStyle.Posture.NORMAL),
                                    "字重姿态没有选择真实font source");
                        }
                }
            }
        for (float em : new float[] {18.7f, 32.3f, 51.1f})
            for (String[] sample :
                    new String[][] {
                        {"𝑥", "𝑥"},
                        {"𝐀", "𝐀"},
                        {"𝛼", "𝛼"},
                        {"ℎ", "ℎ"},
                        {"𝑕", "ℎ"},
                        {"𝒝", "ℬ"},
                        {"𝒠", "ℰ"},
                        {"ℂ", "ℂ"},
                        {"𝙰", "𝙰"},
                        {"𝕬", "𝕬"}
                    }) {
                String source = tex("@textsf{" + sample[0] + "}");
                MathContent content = MathContent.parse(source);
                MathLayout value =
                        engine.layout(
                                content,
                                new TextStyle(
                                        "default",
                                        em,
                                        -1,
                                        null,
                                        club.heiqi.uilib.ui.scene.text.TextEffects.NORMAL,
                                        club.heiqi.uilib.ui.scene.text.TextDecoration.NONE),
                                MathStyle.TEXT,
                                text,
                                items());
                healthy(value);
                require(
                        content.source.equals(source)
                                && value.pieces.size() == 1
                                && value.pieces.get(0).text.logicalText.equals(sample[1]),
                        "宽数学Unicode语义或原source丢失");
                MathLayout.TextPiece piece = value.pieces.get(0);
                require(
                        piece.sourceStart == tex("@textsf{").length()
                                && piece.sourceEnd == source.length() - 1,
                        "宽字符来源未保持完整UTF16范围");
                for (TextLayout.Line line : piece.text.lines())
                    for (TextLayout.Glyph glyph : line.glyphs())
                        require(
                                !glyph.replacement
                                        && glyph.glyphId != 0
                                        && glyph.source.id.equals("stix-two-math"),
                                "宽数学字母误进入普通正文族/replacement glyph");
                for (MathLayout.Diagnostic diagnostic : value.diagnostics)
                    require(!diagnostic.stage.equals("font"), "宽数学字形仍有native缺字诊断");
            }
        String[][] symbols = {
            {"lvertneqq", "≨︀", "1816"},
            {"gvertneqq", "≩︀", "1818"},
            {"varsubsetneq", "⊊︀", "1869"},
            {"varsupsetneq", "⊋︀", "1871"},
            {"varsubsetneqq", "⫋︀", "3119"},
            {"varsupsetneqq", "⫌︀", "3121"},
            {"nleqslant", "⩽̸", ""},
            {"ngeqslant", "⩾̸", ""},
            {"nleqq", "≦̸", ""},
            {"ngeqq", "≧̸", ""},
            {"nsubseteqq", "⫅̸", ""},
            {"nsupseteqq", "⫆̸", ""},
            {"nshortmid", "∤", ""},
            {"nshortparallel", "∦", ""},
            {"imath", "𝚤", ""},
            {"jmath", "𝚥", ""}
        };
        for (float em : new float[] {18.7f, 32.3f})
            for (String[] sample : symbols) {
                String source = tex("@" + sample[0]);
                MathLayout value = layout(source, em, MathStyle.TEXT);
                healthy(value);
                require(
                        value.pieces.size() == 1
                                && value.pieces.get(0).text.logicalText.equals(sample[1]),
                        "私用符号未转为有依据的正式Unicode序列: " + sample[0]);
                TextLayout nativeRun = value.pieces.get(0).text;
                int count = 0;
                int dottedCircle =
                        text.mathFont(style(em)).text("◌").lines().get(0).glyphs().get(0).glyphId;
                for (TextLayout.Line line : nativeRun.lines())
                    for (TextLayout.Glyph glyph : line.glyphs()) {
                        count++;
                        require(
                                glyph.glyphId != 0
                                        && glyph.glyphId != dottedCircle
                                        && !glyph.replacement
                                        && glyph.source.id.equals("stix-two-math"),
                                "正规组合被画成点圈/缺字/其他族: " + sample[0]);
                        if (!sample[2].isEmpty())
                            require(
                                    glyph.glyphId == Integer.parseInt(sample[2]),
                                    "VS1未选择独立STIX cmap14变体glyph: " + sample[0]);
                    }
                require(count > 0, "正规符号没有真实glyph");
                for (MathLayout.Diagnostic diagnostic : value.diagnostics)
                    require(!diagnostic.stage.equals("font"), "正规符号仍有缺字诊断: " + sample[0]);
            }
        MathLayout mixed = layout("@textsf{A𝑥 B}", 32.3f, MathStyle.TEXT);
        healthy(mixed);
        require(
                mixed.pieces.size() == 3
                        && mixed.pieces.get(0).text.style.font.equals("math-text-sans")
                        && mixed.pieces.get(2).text.style.font.equals("math-text-sans"),
                "宽数学字符破坏邻接普通文字font scope/run");
        String[] plain = {"@text{Wi}", "@textbf{Wi}", "@textit{Wi}", "@emph{Wi}"};
        for (int index = 0; index < plain.length; index++) {
            MathLayout value =
                    engine.layout(
                            MathContent.parse(tex(plain[index])),
                            new TextStyle(
                                    "default",
                                    32.3f,
                                    -1,
                                    null,
                                    club.heiqi.uilib.ui.scene.text.TextEffects.NORMAL,
                                    club.heiqi.uilib.ui.scene.text.TextDecoration.NONE),
                            MathStyle.TEXT,
                            text,
                            items());
            healthy(value);
            for (TextLayout.Line line : value.pieces.get(0).text.lines())
                for (TextLayout.Glyph glyph : line.glyphs()) {
                    require(
                            glyph.source.id.startsWith("math-text-roman"),
                            "数学正文default未使用显式roman角色");
                    require(
                            glyph.source.actualStyle().weight == (index == 1 ? 700 : 400)
                                    && glyph.source.actualStyle().posture
                                            == (index >= 2
                                                    ? club.heiqi.uilib.ui.scene.text.FontStyle
                                                            .Posture.ITALIC
                                                    : club.heiqi.uilib.ui.scene.text.FontStyle
                                                            .Posture.NORMAL),
                            "plain text bf/it/emph未选择真实字重姿态");
                }
        }
        MathLayout i = layout("@texttt{i}", 32.3f, MathStyle.TEXT),
                w = layout("@texttt{W}", 32.3f, MathStyle.TEXT);
        healthy(i);
        healthy(w);
        near(i.pieces.get(0).text.advanceWidth, w.pieces.get(0).text.advanceWidth, "texttt非真实等宽字体");
        MathLayout romanI = layout("@textrm{i}", 32.3f, MathStyle.TEXT),
                romanW = layout("@textrm{W}", 32.3f, MathStyle.TEXT);
        healthy(romanI);
        healthy(romanW);
        require(
                romanW.pieces.get(0).text.advanceWidth > romanI.pieces.get(0).text.advanceWidth,
                "textrm仍沿等宽默认族");
        MathLayout emphasis = layout("@textsf{@emph{A@emph{B}C}}", 32.3f, MathStyle.TEXT);
        healthy(emphasis);
        require(emphasis.pieces.size() == 3, "嵌套emph文字facts丢失");
        for (int index = 0; index < 3; index++) {
            TextLayout run = emphasis.pieces.get(index).text;
            require(
                    run.style.font.equals("math-text-sans")
                            && run.style.fontStyle.posture
                                    == (index == 1
                                            ? club.heiqi.uilib.ui.scene.text.FontStyle.Posture
                                                    .NORMAL
                                            : club.heiqi.uilib.ui.scene.text.FontStyle.Posture
                                                    .ITALIC),
                    "emph没有按旧姿态toggle/局部scope泄漏");
        }
        club.heiqi.uilib.ui.scene.text.font.FontSource explicit =
                club.heiqi.uilib.ui.scene.text.font.FontSource.fromResource(
                        "task5-explicitmath",
                        1,
                        "assets/qz_uilib/fonts/math/STIXTwoMath-Regular.otf",
                        false);
        TextStyle scoped =
                new TextStyle(
                        "math",
                        32.3f,
                        -1,
                        Collections.singletonList(explicit),
                        club.heiqi.uilib.ui.scene.text.TextEffects.NORMAL,
                        club.heiqi.uilib.ui.scene.text.TextDecoration.NONE);
        MathLayout override =
                engine.layout(
                        MathContent.parse(tex("@textsf{Wi}")),
                        scoped,
                        MathStyle.TEXT,
                        text,
                        items());
        healthy(override);
        for (TextLayout.Line line : override.pieces.get(0).text.lines())
            for (TextLayout.Glyph glyph : line.glyphs())
                require(glyph.source.id.equals(explicit.id), "font alias绕过显式root候选");
    }

    private void rootTags() {
        for (float em : new float[] {18.7f, 32.3f, 51.1f}) {
            MathLayout base = layout("@embed{a}+x", em, MathStyle.DISPLAY);
            MathLayout tagged = layout("@embed{a}+x@tag{Z}", em, MathStyle.DISPLAY);
            healthy(base);
            healthy(tagged);
            require(
                    tagged.width > base.width
                            && tagged.height >= base.height
                            && tagged.embeds.size() == 1,
                    "顶级TAG root误判空或丢失body");
            boolean label = false;
            for (MathLayout.TextPiece piece : tagged.pieces)
                label |= piece.text.originalText.contains("Z");
            require(label, "顶级tag真实文字丢失");
            require(tagged.nextEquationNumber == 1, "显式顶级tag消费自动编号");
            MathLayout rejected = layout("x@tag{Z}", em, MathStyle.TEXT);
            require(rejected.fallback && rejected.sourceFallback != null, "行内顶级tag资格未真实校验");
        }
    }

    private void numbering() {
        MathContent content =
                MathContent.parse(
                        tex("@begin{align}a&=b@@c&=d@tag{Z}@@e&=f@notag@@g&=h@end{align}"));
        MathLayout first =
                engine.layout(content, style(32.3f), MathStyle.DISPLAY, text, items(), 7);
        MathLayout resized =
                engine.layout(content, style(51.1f), MathStyle.DISPLAY, text, items(), 7);
        healthy(first);
        healthy(resized);
        require(
                first.nextEquationNumber == 9 && resized.nextEquationNumber == 9,
                "手动标签/notag消费编号或重排递增输入编号");
        boolean seven = false, eight = false;
        for (MathLayout.TextPiece piece : first.pieces) {
            seven |= piece.text.originalText.equals("(7)");
            eight |= piece.text.originalText.equals("(8)");
        }
        require(seven && eight, "自动tag没有使用显式起始编号");
        MathLayout next =
                engine.layout(
                        MathContent.parse(tex("@begin{equation}x@end{equation}")),
                        style(32.3f),
                        MathStyle.DISPLAY,
                        text,
                        items(),
                        first.nextEquationNumber);
        healthy(next);
        require(next.nextEquationNumber == 10, "下一公式没有纯串联编号");
        for (int initial : new int[] {0, -1}) {
            boolean rejected = false;
            try {
                engine.layout(content, style(32.3f), MathStyle.DISPLAY, text, items(), initial);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            require(rejected, "无效起始编号未在入口校验");
        }
        // 插入项虽每行呈现，编号只先测一次；嵌套 cell 的编号必须先于所属行 tag。
        String[] equations = {
            "@begin{equation}i@end{equation}",
            "@begin{equation}a@end{equation}",
            "@begin{equation}b@end{equation}",
            "@begin{equation}c@end{equation}"
        };
        String nestedSource =
                tex("@begin{align}@begin{darray}{c#{"
                        + equations[0]
                        + "}}"
                        + equations[1]
                        + "@@"
                        + equations[2]
                        + "@end{darray}&=x@@"
                        + equations[3]
                        + "&=y@end{align}");
        MathLayout nested =
                engine.layout(
                        MathContent.parse(nestedSource),
                        style(32.3f), MathStyle.DISPLAY, text, items(), 7);
        healthy(nested);
        require(nested.nextEquationNumber == 13, "嵌套cell或插入列重复消费编号");
        int[] numbers = {7, 8, 9, 11}, appearances = {2, 1, 1, 1};
        for (int i = 0; i < equations.length; i++) {
            String equation = tex(equations[i]);
            int start = nestedSource.indexOf(equation), count = 0;
            for (MathLayout.TextPiece piece : nested.pieces)
                if (piece.sourceStart == start && piece.text.originalText.startsWith("(")) {
                    require(
                            piece.text.originalText.equals("(" + numbers[i] + ")")
                                    && piece.sourceEnd == start + equation.length(),
                            "嵌套编号顺序或来源失真: " + equation);
                    count++;
                }
            require(count == appearances[i], "插入列或cell编号呈现次数错误");
        }
        int[] rowNumbers = {10, 12};
        int rowTag = 0;
        for (MathLayout.TextPiece piece : nested.pieces)
            if (piece.sourceStart == 0 && piece.text.originalText.startsWith("(")) {
                require(
                        rowTag < rowNumbers.length
                                && piece.text.originalText.equals("(" + rowNumbers[rowTag] + ")"),
                        "外层行tag先于嵌套cell求值或全部移到末尾");
                rowTag++;
            }
        require(rowTag == rowNumbers.length, "外层自动行tag丢失");
    }

    private static float[] ink(MathLayout.TextPiece piece) {
        float left = Float.POSITIVE_INFINITY,
                top = Float.POSITIVE_INFINITY,
                right = Float.NEGATIVE_INFINITY,
                bottom = Float.NEGATIVE_INFINITY;
        for (TextLayout.Line line : piece.text.lines())
            for (TextLayout.Glyph glyph : line.glyphs()) {
                left = Math.min(left, piece.x + glyph.x + glyph.inkBounds.left);
                right = Math.max(right, piece.x + glyph.x + glyph.inkBounds.right);
                top = Math.min(top, piece.y + glyph.y + glyph.inkBounds.top);
                bottom = Math.max(bottom, piece.y + glyph.y + glyph.inkBounds.bottom);
            }
        return new float[] {left, top, right, bottom};
    }
}
