package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathEmbedMetrics;
import club.heiqi.uilib.ui.scene.text.math.MathFont;
import club.heiqi.uilib.ui.scene.text.math.MathLayout;
import club.heiqi.uilib.ui.scene.text.math.MathLayoutEngine;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.util.LinkedHashMap;
import java.util.Map;

/** 真实字体/MATH 装饰几何，检查独立消费者事实而非 helper 内部实现。 */
public final class MathDecorationScenario extends TestApplication {
    private FontTextService text;
    private final MathLayoutEngine engine = new MathLayoutEngine();

    public static void main(String[] args) throws Exception {
        new MathDecorationScenario().run(args);
    }

    @Override
    protected void setup() throws Exception {
        text = own(new FontTextService(new LwjglFontEngine()));
    }

    @Override
    protected void runTests() throws Exception {
        scenario("全部装饰family多em真实MATH几何", this::families);
        scenario("phantom隐藏资源与局部几何identity", this::hiddenAndTransforms);
        scenario("MathChoice四样式与颜色作用域", this::styles);
        scenario("背景边框斜线与Item有序entries", this::ordered);
        scenario("真实窄accent附件点与横向brace", this::attachments);
        scenario("正文重音及合法拒绝保邻式Item", this::textAccentsAndRejected);
        scenario("否定符零advance来源与相邻Item", this::negations);
        scenario("顶层换行双入口样式可选间距与Item来源", this::lineBreaks);
    }

    private static String tex(String s) {
        return s.replace('@', (char) 92)
                .replace("html!mathml", "html@mathml")
                .replace("!not", "@not");
    }

    private MathLayout layout(String source, float em, MathStyle style) {
        Map<String, MathEmbedMetrics> slots = new LinkedHashMap<>();
        for (String id : new String[] {"a", "b", "hidden"})
            slots.put(id, new MathEmbedMetrics(1, 1, .8f));
        MathLayout b =
                engine.layout(
                        MathContent.parse(tex(source)), style(em, 0xffabcdef), style, text, slots);
        StringBuilder failure = new StringBuilder("装饰整式降级 " + source);
        for (MathLayout.Diagnostic d : b.diagnostics)
            failure.append(" [")
                    .append(d.stage)
                    .append(" ")
                    .append(d.start)
                    .append(":")
                    .append(d.end)
                    .append(" ")
                    .append(d.message)
                    .append("]");
        require(!b.fallback, failure.toString());
        require(Float.isFinite(b.width + b.height + b.baseline), "非有限装饰几何");
        return b;
    }

    private void families() {
        String[] sources = {
            "@widehat{xyz}",
            "@widecheck{xyz}",
            "@widetilde{xyz}",
            "@utilde{xyz}",
            "@vec{x}",
            "@overrightarrow{xyz}",
            "@underleftarrow{xyz}",
            "@overleftrightarrow{xyz}",
            "@overline{xy}+@underline{z}",
            "@overset{a}{x}+@underset{b}{y}+@stackrel{!}{=}",
            "@xrightarrow[b]{abc}",
            "@xleftrightharpoons[b]{abc}",
            "@xLeftarrow[b]{abc}",
            "@overbrace{xy}^{n}+@underbrace{xy}_{m}",
            "@color{red}{x}+@textcolor{blue}{y}",
            "{@Huge x}+@mathrel{a}",
            "@phantom{x}+@hphantom{x}+@vphantom{x}",
            "@boxed{x}+@colorbox{yellow}{x}+@fcolorbox{blue}{yellow}{x}",
            "@raisebox{.2em}{x}+@rule[.1em]{.5em}{.08em}",
            "@vcenter{x}+@mathllap{x}+@mathrlap{y}+@mathclap{z}",
            "@smash[t]{x}+@smash[b]{y}",
            "@cancel{x}+@bcancel{x}+@xcancel{x}+@sout{x}+@angl{x}+@phase{x}",
            "@pmb{x+@embed{a}}",
            "@html!mathml{x}{y}",
            "@verb|a b|",
            "x@tag{label}",
            "x@@y"
        };
        for (float em : new float[] {18, 31.5f})
            for (String source : sources) {
                MathLayout b = layout(source, em, MathStyle.DISPLAY);
                System.out.println(
                        "decoration em="
                                + em
                                + " source="
                                + tex(source)
                                + " width="
                                + b.width
                                + " height="
                                + b.height
                                + " entries="
                                + b.entries.size());
            }
    }

    private void hiddenAndTransforms() {
        float em = 30;
        MathLayout reference = layout("x+@embed{hidden}", em, MathStyle.TEXT);
        MathLayout full = layout("@phantom{x+@embed{hidden}}", em, MathStyle.TEXT);
        require(
                full.entries.isEmpty() && full.embeds.isEmpty() && full.pieces.isEmpty(),
                "phantom保留真实资源资格");
        near(full.width, reference.width, "phantom宽度");
        near(full.height, reference.height, "phantom高度");
        MathLayout h = layout("@hphantom{x+@embed{hidden}}", em, MathStyle.TEXT),
                v = layout("@vphantom{x+@embed{hidden}}", em, MathStyle.TEXT);
        near(h.width, reference.width, "hphantom宽度");
        near(h.height, 0, "hphantom高度");
        near(v.width, 0, "vphantom宽度");
        near(v.height, reference.height, "vphantom高度");
        for (String source :
                new String[] {
                    "@smash{@embed{a}}",
                    "@vcenter{@embed{a}}",
                    "@raisebox{.2em}{@embed{a}}",
                    "@mathllap{@embed{a}}",
                    "@pmb{@embed{a}}"
                }) {
            MathLayout b = layout(source, em, MathStyle.TEXT);
            require(
                    b.embeds.size() == 1 && b.embeds.get(0).id.equals("a"),
                    "变换复制/丢失Item " + source);
            near(b.embeds.get(0).width, em, "变换缩放Item");
        }
        MathLayout p = layout("@pmb{x}", em, MathStyle.TEXT),
                normal = layout("x", em, MathStyle.TEXT);
        TextLayout.Glyph original = normal.pieces.get(0).text.lines().get(0).glyphs().get(0);
        for (MathLayout.TextPiece piece : p.pieces) {
            TextLayout.Glyph glyph = piece.text.lines().get(0).glyphs().get(0);
            require(
                    glyph.source.equals(original.source)
                            && glyph.rasterStyle.equals(original.rasterStyle),
                    "pmb伪称真实bold/换source");
        }
    }

    private void styles() {
        String choice = "@mathchoice{@embed{a}}{@embed{b}}{x}{y}";
        for (MathStyle style : MathStyle.values()) {
            MathLayout b = layout(choice, 30, style);
            if (style == MathStyle.DISPLAY || style == MathStyle.TEXT)
                require(
                        b.embeds.size() == 1
                                && b.embeds
                                        .get(0)
                                        .id
                                        .equals(style == MathStyle.DISPLAY ? "a" : "b"),
                        "choice选错分支");
            else require(b.embeds.isEmpty(), "choice未选脚本分支");
        }
        MathLayout colors =
                layout("@textcolor{red}{x}@textcolor{currentColor}{y}z", 30, MathStyle.TEXT);
        int[] expected = {0xffff0000, 0xffabcdef, 0xffabcdef};
        int i = 0;
        for (MathLayout.TextPiece piece : colors.pieces)
            require(piece.text.style.color == expected[i++], "颜色作用域泄漏");
        require(i == expected.length, "颜色场景未命中全部glyph");
    }

    private void ordered() {
        MathLayout b =
                layout(
                        "@colorbox{yellow}{$@cancel{@embed{a}}$}x@fcolorbox{blue}{cyan}{$@embed{b}$}",
                        30,
                        MathStyle.TEXT);
        int first = -1, second = -1, background = -1, cancel = -1;
        for (int i = 0; i < b.entries.size(); i++) {
            MathLayout.Entry e = b.entries.get(i);
            if (e.embed != null) {
                if (e.embed.id.equals("a")) first = i;
                else second = i;
            }
            if (e.rule != null && e.rule.color == 0xffffff00) background = i;
            if (e.stroke != null) cancel = i;
        }
        require(
                background < first && first < cancel && cancel < second,
                "background/body/cancel顺序错误");
        long borders = b.rules.stream().filter(r -> r.color == 0xff0000ff).count();
        require(borders == 4, "fcolorbox不是四RECT边框");
    }

    private void attachments() {
        for (float em : new float[] {18, 31.5f}) {
            MathLayout narrow = layout("@hat{x}", em, MathStyle.TEXT);
            require(narrow.pieces.size() == 2, "窄accent不是真实两字形");
            MathLayout.TextPiece base = narrow.pieces.get(0), mark = narrow.pieces.get(1);
            MathFont font = text.mathFont(style(em, 0xffabcdef));
            float a = font.topAccentAttachment(base.text), b = font.topAccentAttachment(mark.text);
            require(!Float.isNaN(a) && !Float.isNaN(b), "附件点伪造/缺数据");
            near(base.x + a, mark.x + b, "实际附件点未对齐");
            MathLayout brace = layout("@overbrace{xyz}^{n}", em, MathStyle.TEXT);
            require(
                    brace.pieces.stream().anyMatch(p -> p.text.logicalText.equals("⏞")),
                    "brace未请求真实横向MATH");
        }
    }

    private void textAccentsAndRejected() {
        for (float em : new float[] {18, 31.5f}) {
            MathLayout cedilla = layout("@c{x}", em, MathStyle.TEXT);
            require(cedilla.pieces.size() == 2, "cedilla缺正文或饰件");
            MathLayout.TextPiece mark = cedilla.pieces.get(1);
            require(mark.text.logicalText.equals("¸"), "cedilla未沿固定正文spacing字形");
            for (TextLayout.Line line : mark.text.lines())
                for (TextLayout.Glyph glyph : line.glyphs())
                    require(
                            !glyph.replacement
                                    && mark.y + glyph.y + glyph.inkBounds.bottom > cedilla.baseline,
                            "cedilla未在基线下方");
            layout("@text{@.{x}@'{x}@c{x}@^{x}@u{x}@H{x}}", em, MathStyle.TEXT);
            MathLayout rejected =
                    layout("x+@href{https://example.org}{hidden}+@embed{a}", em, MathStyle.TEXT);
            require(
                    rejected.embeds.size() == 1 && rejected.embeds.get(0).id.equals("a"),
                    "合法拒绝丢相邻Item");
            StringBuilder visible = new StringBuilder();
            for (MathLayout.TextPiece piece : rejected.pieces) {
                visible.append(piece.text.logicalText);
                if (piece.text.logicalText.equals(tex("@href")))
                    require(piece.text.style.color == 0xffcc0000, "拒绝命令未取固定错误色");
            }
            require(
                    visible.indexOf(tex("@href")) >= 0
                            && visible.indexOf("https") < 0
                            && visible.indexOf("hidden") < 0,
                    "拒绝内容不是commandonly");
        }
    }

    private void negations() {
        for (float em : new float[] {18, 31.5f}) {
            MathLayout single = layout("@!not", em, MathStyle.TEXT);
            require(
                    single.width == 0 && single.strokes.size() == 1 && single.pieces.isEmpty(),
                    "否定符不是零advance明确斜线");
            MathLayout.Stroke stroke = single.strokes.get(0);
            require(stroke.x2 > stroke.x1 && stroke.y2 < stroke.y1, "否定斜线方向错误");
            require(
                    stroke.sourceStart == 0 && stroke.sourceEnd == tex("@!not").length(),
                    "否定符丢原节点来源");
            MathLayout negated = layout("@not=", em, MathStyle.TEXT),
                    equal = layout("=", em, MathStyle.TEXT);
            System.out.println(
                    "negation em="
                            + em
                            + " width="
                            + negated.width
                            + " equal="
                            + equal.width
                            + " relationX="
                            + negated.pieces.get(0).x
                            + " strokeX="
                            + negated.strokes.get(0).x1
                            + ":"
                            + negated.strokes.get(0).x2);
            MathLayout fragment =
                    layout("@html!mathml{@mathrel{@mathrlap{x}}}{y}=", em, MathStyle.TEXT);
            MathLayout classified = layout("@mathrel{@mathrlap{x}}=", em, MathStyle.TEXT);
            near(fragment.width, classified.width, "HTML分支fragment丢REL外层类别");
            near(negated.width, equal.width, "否定overlay改变后关系advance");
            require(negated.strokes.size() == 1, "not宏未保明确斜线");
            MathLayout adjacent = layout("@!not x+@not@subset+@embed{a}", em, MathStyle.TEXT);
            require(adjacent.embeds.size() == 1 && adjacent.strokes.size() == 2, "否定符破相邻内容/Item资格");
        }
    }

    private void lineBreaks() {
        for (float em : new float[] {18, 31.5f})
            for (MathStyle style : new MathStyle[] {MathStyle.TEXT, MathStyle.DISPLAY}) {
                MathLayout positive = layout("x@@[1pt]y", em, style);
                MathLayout negative = layout("x@@[-1pt]y", em, style);
                float first = pieceBaseline(positive, "x@@[1pt]y", "x"),
                        second = pieceBaseline(positive, "x@@[1pt]y", "y");
                System.out.println(
                        "linebreak em="
                                + em
                                + " style="
                                + style
                                + " baselines="
                                + first
                                + ":"
                                + second);
                require(second > first, "顶层换行未生成两个基线");
                require(pieceBaseline(negative, "x@@[-1pt]y", "y") < second, "负可选间距未消费");
                MathLayout declared = layout("@displaystyle x@@y", em, style);
                require(
                        pieceBaseline(declared, "@displaystyle x@@y", "y")
                                > pieceBaseline(declared, "@displaystyle x@@y", "x"),
                        "局部displaystyle吞顶层换行");
                MathLayout items = layout("x+@embed{a}@@[1pt]y+@embed{b}", em, style);
                require(items.embeds.size() == 2, "多行Item资格丢失");
                require(items.embeds.get(1).y > items.embeds.get(0).y, "多行Item未随所属行平移");
                for (MathLayout.Embed item : items.embeds)
                    require(
                            tex("x+@embed{a}@@[1pt]y+@embed{b}")
                                    .substring(item.sourceStart, item.sourceEnd)
                                    .startsWith(tex("@embed")),
                            "多行Item丢来源");
            }
    }

    private static float pieceBaseline(MathLayout layout, String source, String value) {
        for (MathLayout.TextPiece piece : layout.pieces)
            if (value.equals(tex(source).substring(piece.sourceStart, piece.sourceEnd)))
                return piece.y + piece.text.lines().get(0).baseline;
        throw new AssertionError("没有字形 " + value);
    }

    private static void near(float a, float b, String message) {
        require(Math.abs(a - b) < .025f, message + ": " + a + " != " + b);
    }

    private static TextStyle style(float em, int color) {
        return new TextStyle(
                "default",
                em,
                color,
                null,
                club.heiqi.uilib.ui.scene.text.TextEffects.NORMAL,
                club.heiqi.uilib.ui.scene.text.TextDecoration.NONE);
    }
}
