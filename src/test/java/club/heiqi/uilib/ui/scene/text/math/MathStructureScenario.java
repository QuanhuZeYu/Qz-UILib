package club.heiqi.uilib.ui.scene.text.math;

import static club.heiqi.uilib.ui.scene.text.math.MathContent.*;

import club.heiqi.uilib.testing.TestApplication;

import java.util.ArrayList;
import java.util.List;

/** 真实 AST 消费者，只验语义/错误/来源，不声称布局或 GPU。 */
public final class MathStructureScenario extends TestApplication {
    public static void main(String[] args) throws Exception {
        new MathStructureScenario().run(args);
    }

    @Override
    protected void runTests() throws Exception {
        scenario("二维空cell嵌套行线与行距", this::arrays);
        scenario("全部33环境结构及模式资格", this::environments);
        scenario("CD方向双标签消费终点与原文来源", this::cdConsumption);
        scenario("广义分式infix尺寸及显式style", this::fractions);
        scenario("atomgroup算子primes后覆limits及literalstyled", this::symbols);
        scenario("字体文本数学模式与primitive参数", this::fonts);
        scenario("饰件尺寸颜色box及choice消费者", this::decorations);
        scenario("Trustfalse合法扫描与malformed诊断", this::trust);
        scenario("原文范围宏复制嵌入唯一与邻式隔离", this::sources);
        scenario("同源环境ordinal与行标签消费者", this::tags);
        scenario("完整functiontype真实上下文消费者", this::functionTypes);
        scenario("309函数及33环境独立结构语料consumer", this::fixtures);
        scenario("评审infix模式和数组声明与小数列约束", this::reviewContracts);
    }

    private MathContent parse(String value) {
        MathContent c = MathContent.parse(value);
        require(
                c.diagnostics.isEmpty(),
                "parse失败 "
                        + value
                        + ": "
                        + (c.diagnostics.isEmpty() ? "" : c.diagnostics.get(0).message));
        require(
                c.root != null
                        && c.source.equals(value)
                        && c.root.start == 0
                        && c.root.end == value.length(),
                "原文根范围");
        return c;
    }

    private Node find(Node n, Kind kind) {
        if (n == null) return null;
        if (n.kind == kind) return n;
        for (Node c : n.children) {
            Node r = find(c, kind);
            if (r != null) return r;
        }
        Node r = find(n.first, kind);
        if (r != null) return r;
        r = find(n.second, kind);
        return r == null ? find(n.third, kind) : r;
    }

    private List<Node> all(Node n, Kind kind) {
        List<Node> out = new ArrayList<Node>();
        collect(n, kind, out);
        return out;
    }

    private void collect(Node n, Kind kind, List<Node> out) {
        if (n == null) return;
        if (n.kind == kind) out.add(n);
        for (Node c : n.children) collect(c, kind, out);
        collect(n.first, kind, out);
        collect(n.second, kind, out);
        collect(n.third, kind, out);
    }

    private void bad(String value) {
        MathContent c = MathContent.parse(value);
        require(
                c.root == null && !c.diagnostics.isEmpty() && c.source.equals(value),
                "错式被半解析 " + value);
        for (MathLayout.Diagnostic d : c.diagnostics)
            require(d.start >= 0 && d.end >= d.start && d.end <= value.length(), "诊断原文范围");
    }

    private void reviewContracts() {
        MathContent infix = MathContent.parse("\\text{a\\over b}");
        MathContent extra = MathContent.parse("\\begin{array}{c}a&b\\end{array}");
        MathContent tiny = MathContent.parse("\\begin{alignedat}{.25}a\\end{alignedat}");
        boolean modeGuard = infix.root == null && !infix.diagnostics.isEmpty();
        Node array = find(extra.root, Kind.ARRAY);
        boolean extraKept =
                array != null
                        && ((ArraySpec) array.payload).rows.get(0).cellCount == 2
                        && ((ArraySpec) array.payload).columns.size() == 1
                        && array.children.size() == 2
                        && ((ArraySpec) array.payload).columns.get(0).align.equals("c");
        boolean tinyGuard = tiny.root == null && !tiny.diagnostics.isEmpty();
        require(
                modeGuard && extraKept && tinyGuard,
                "infixTextGuard="
                        + modeGuard
                        + ", arrayExtraCellsKept="
                        + extraKept
                        + ", fractionalConstraintGuard="
                        + tinyGuard);
        bad("\\text{a\\atop b}");
        bad("\\text{a\\choose b}");
        bad("\\begin{alignat}{.25}a\\end{alignat}");
        bad("\\begin{alignedat}{.49}a\\end{alignedat}");
        require(
                ((ArraySpec)
                                find(
                                                parse("\\begin{alignedat}{}a\\end{alignedat}").root,
                                                Kind.ARRAY)
                                        .payload)
                        .separation.equals("align"),
                "空参数非动态模式");
        require(
                ((ArraySpec)
                                find(
                                                parse("\\begin{alignedat}{0}a\\end{alignedat}")
                                                        .root,
                                                Kind.ARRAY)
                                        .payload)
                        .separation.equals("align"),
                "零参数非动态模式");
        parse("\\begin{alignedat}{.5}a\\end{alignedat}");
        Node darray = find(parse("\\begin{darray}{r}a&b\\end{darray}").root, Kind.ARRAY);
        require(
                ((ArraySpec) darray.payload).rows.get(0).cellCount == 2
                        && ((ArraySpec) darray.payload).columns.size() == 1,
                "darray额外cell丢失");
    }

    private void arrays() {
        Node n =
                find(
                        parse(
                                        "\\begin{array}{|l:c|}\\hline a&\\begin{pmatrix}b&c\\\\d&e\\end{pmatrix}\\\\[2pt]\\hdashline &f\\end{array}")
                                .root,
                        Kind.ARRAY);
        ArraySpec a = (ArraySpec) n.payload;
        require(
                a.rows.size() == 2 && a.rows.get(1).cellCount == 2 && a.rows.get(1).firstCell == 2,
                "flat二维索引");
        require(
                a.rows.get(0).gapAfter.number == 2 && a.rows.get(0).gapAfter.unit.equals("pt"),
                "rowgap单位");
        require(
                a.lines.size() == 2
                        && a.lines.get(0).beforeRow == 0
                        && a.lines.get(1).beforeRow == 1
                        && a.lines.get(1).dashed,
                "行线");
        require(a.columns.size() == 5 && a.columns.get(2).type == ColumnSpec.Type.DASHED, "列竖线");
        require(n.children.get(2).children.isEmpty(), "空cell未保留");
        Node at = find(parse("\\begin{array}{l@{+}r}a&b\\end{array}").root, Kind.ARRAY);
        ArraySpec spec = (ArraySpec) at.payload;
        require(
                spec.columns.get(1).type == ColumnSpec.Type.INSERT
                        && spec.columns.get(1).insertChild >= 2,
                "列插入内容未用flat索引");
        bad("\\begin{matrix}a&b\\end{pmatrix}");
        bad("a&b");
        bad("\\hline x");
        bad("\\begin{split}a&b&c\\end{split}");
        bad("\\begin{subarray}{r}x\\end{subarray}");
        MathContent stretch =
                parse(
                        "\\def\\arraystretch{2}\\begin{matrix}a&\\def\\arraystretch{3}\\begin{matrix}b\\end{matrix}\\end{matrix}");
        List<Node> matrices = all(stretch.root, Kind.ARRAY);
        require(
                ((ArraySpec) matrices.get(0).payload).stretch == 2
                        && ((ArraySpec) matrices.get(1).payload).stretch == 3,
                "arraystretch入口快照");
        Node small =
                find(
                        parse("\\def\\arraystretch{0}\\begin{smallmatrix}a\\end{smallmatrix}").root,
                        Kind.ARRAY);
        require(((ArraySpec) small.payload).stretch == .5, "固定环境被arraystretch污染");
        bad("\\def\\arraystretch{0}\\begin{matrix}a\\end{matrix}");
        bad("\\def\\arraystretch{NaN}\\begin{matrix}a\\end{matrix}");
    }

    private void environments() {
        int count = 0;
        for (MathCommandCatalog.Environment e : MathCommandCatalog.environments()) {
            String args =
                    e.name.equals("array") || e.name.equals("darray")
                            ? "{lc}"
                            : e.name.equals("subarray")
                                    ? "{c}"
                                    : e.name.startsWith("alignat") || e.name.equals("alignedat")
                                            ? "{2}"
                                            : "";
            String body =
                    e.name.equals("CD")
                            ? "A@>f>>B\\\\@VVgV@AAhA\\\\C@=D"
                            : e.name.startsWith("equation") || e.name.equals("subarray")
                                    ? "a"
                                    : "a&b\\\\c&d";
            Node n =
                    find(
                            parse("\\begin{" + e.name + "}" + args + body + "\\end{" + e.name + "}")
                                    .root,
                            Kind.ARRAY);
            require(
                    n != null && ((ArraySpec) n.payload).environment.equals(e.name),
                    "环境掉项: " + e.name);
            count++;
        }
        require(count == 33, "固定环境数量漂移");
        ArraySpec inline =
                (ArraySpec) find(parse("\\begin{align}a&=b\\end{align}").root, Kind.ARRAY).payload;
        require(
                inline.displayRequired
                        && inline.inlineAutoDisplay
                        && inline.rows.get(0).automaticTag,
                "align模式资格");
        Node cd = find(parse("\\begin{CD}A@>f>>\\\\@VVgV@AAhA\\end{CD}").root, Kind.ARRAY);
        ArraySpec cds = (ArraySpec) cd.payload;
        require(
                cds.rows.get(0).cellCount == 3
                        && cds.rows.get(1).cellCount == 3
                        && cds.columns.get(0).preGap == .25,
                "CD独立行格语义");
        Node arrow = find(cd, Kind.XARROW);
        require(
                ((DecorationSpec) arrow.payload).command.equals("cdrightarrow")
                        && arrow.first != null,
                "CD方向与标签facts");
        ArraySpec fractional =
                (ArraySpec)
                        find(parse("\\begin{alignat}{1.2}a&b\\end{alignat}").root, Kind.ARRAY)
                                .payload;
        require(
                fractional.columns.size() == 3 && fractional.separation.equals("alignat"),
                "alignat Number参照非整数列事实");
        require(
                ((ArraySpec)
                                find(parse("\\begin{alignat}{}a&b\\end{alignat}").root, Kind.ARRAY)
                                        .payload)
                        .separation.equals("align"),
                "alignat空值不是动态aligned");
        bad("\\begin{alignat}{1.2}a&b&c\\end{alignat}");
        bad("\\begin{alignat}{-1}a\\end{alignat}");
        bad("\\begin{alignat}{x}a\\end{alignat}");
        require(
                !((ArraySpec)
                                find(parse("\\begin{align*}a&b\\end{align*}").root, Kind.ARRAY)
                                        .payload)
                        .inlineAutoDisplay,
                "align*被错判插件自动display");
        require(
                ((ArraySpec)
                                find(parse("\\begin{equation}a\\end{equation}").root, Kind.ARRAY)
                                        .payload)
                        .inlineAutoDisplay,
                "插件equation自动display资格丢失");
        bad("\\begin{multline}x\\end{multline}");
    }

    private void cdConsumption() {
        String[][] arrows = {
            {">", "cdrightarrow"}, {"<", "cdleftarrow"}, {"A", "cduparrow"},
            {"V", "cddownarrow"}, {"=", "cdequal"}, {"|", "cdparallel"}, {".", "cdnone"}
        };
        for (String[] entry : arrows) {
            String direction = entry[0];
            boolean labeled = "<>AV".contains(direction);
            String source = "\\begin{CD}L@" + direction
                    + (labeled ? "u" + direction + "v" + direction : "") + "R\\end{CD}";
            Node array = find(parse(source).root, Kind.ARRAY);
            List<Node> found = all(array, Kind.XARROW);
            require(found.size() == 1, "CD箭头边界吞入后续对象或重复生成");
            Node arrow = found.get(0);
            require(arrow.value.equals(entry[1]) && arrow.start == source.indexOf('@')
                            && arrow.end == source.indexOf('R'),
                    "CD方向或完整箭头来源错误: " + direction);
            require(((ArraySpec) array.payload).rows.get(0).cellCount == 3
                            && array.children.get(2).children.get(0).value.equals("R"),
                    "CD标签闭合后未恢复独立对象cell: " + direction);
            if (labeled) {
                require(arrow.first.children.get(0).value.equals("u")
                                && arrow.second.children.get(0).value.equals("v")
                                && arrow.first.start == arrow.start && arrow.second.start == arrow.start
                                && arrow.first.end == source.indexOf('u') + 2
                                && arrow.second.end == arrow.end,
                        "CD双标签内容或各自闭合来源错误: " + direction);
            } else require(arrow.first == null && arrow.second == null, "无标签CD方向生成了草稿标签");
        }
        String[][] malformed = {
            {"L@", "CD箭头缺少方向"}, {"L@qR", "Expected one of <>AV=|. after @"},
            {"L@>u>v", "CD label缺少结束 >"}, {"L@>u@=R", "CD label缺少结束 >"}
        };
        for (String[] entry : malformed) {
            String source = "\\begin{CD}" + entry[0] + "\\end{CD}";
            MathContent failed = MathContent.parse(source);
            require(failed.root == null && !failed.diagnostics.isEmpty()
                            && failed.diagnostics.get(0).message.contains(entry[1])
                            && failed.source.equals(source),
                    "CD未闭合标签或非法方向丢失原失败: " + entry[0]);
        }
    }

    private void fractions() {
        String angleSource = "\\left\\lt x\\right\\gt";
        Node angle = find(parse(angleSource).root, Kind.DELIMITED);
        require(
                angle.first.value.equals("⟨")
                        && angle.third.value.equals("⟩")
                        && ((DelimiterSpec) angle.first.payload).originalToken.equals("\\lt")
                        && ((DelimiterSpec) angle.third.payload).originalToken.equals("\\gt"),
                "lt/gt中立角定界事实");
        MathContent angleContent = parse(angleSource);
        for (MathLexer.Token token : angleContent.expandedTokens)
            if (token.text.equals("\\lt") || token.text.equals("\\gt")) {
                Node fencePart = token.text.equals("\\lt") ? angle.first : angle.third;
                require(
                        fencePart.end == token.sourceEnd
                                && fencePart.start <= token.sourceStart
                                && angleSource
                                        .substring(token.sourceStart, token.sourceEnd)
                                        .trim()
                                        .equals(token.text),
                        "angle完整token来源范围丢失");
            }
        require(
                angle.first.start == 0
                        && angle.first.end == angleSource.indexOf('x')
                        && angle.third.start == angleSource.indexOf("\\right")
                        && angle.third.end == angleSource.length(),
                "alias原文command+delimiter范围");
        for (String style :
                new String[] {
                    "\\displaystyle", "\\textstyle", "\\scriptstyle", "\\scriptscriptstyle"
                })
            for (String size : new String[] {"\\bigl", "\\Bigl", "\\biggl", "\\Biggl"}) {
                Node sized = find(parse(style + size + "\\lt x").root, Kind.DELIMITER);
                require(
                        sized.value.equals("⟨")
                                && ((DelimiterSpec) sized.payload).originalToken.equals("\\lt"),
                        "四style角别名丢失");
                Node rightSized =
                        find(parse(style + size.replace('l', 'r') + "\\gt x").root, Kind.DELIMITER);
                require(
                        rightSized.value.equals("⟩")
                                && ((DelimiterSpec) rightSized.payload)
                                        .originalToken.equals("\\gt"),
                        "四style右角别名丢失");
            }
        require(
                find(parse("\\left(a\\middle\\gt b\\right)").root, Kind.MIDDLE).value.equals("⟩"),
                "middle角别名");
        require(
                ((GlyphSpec) find(parse("\\lt x\\gt y").root, Kind.GLYPH).payload)
                        .atomGroup.equals("rel"),
                "普通关系lt语义改变");
        bad("\\left a x\\right)");
        bad("\\big a");
        Node n = find(parse("\\genfrac{[}{]}{.4pt}{2}{a}{b}").root, Kind.GENFRAC);
        FractionSpec f = (FractionSpec) n.payload;
        require(
                f.hasBar
                        && f.thickness.number == .4
                        && f.style.equals("script")
                        && f.leftDelimiter.equals("["),
                "genfrac事实");
        Node over = find(parse("{a+b\\over c+d}").root, Kind.FRAC);
        require(over != null, "infix分式");
        require(
                !((FractionSpec) find(parse("a\\atop b").root, Kind.GENFRAC).payload).hasBar,
                "atop错误有线");
        require(
                !((FractionSpec) find(parse("\\genfrac{}{}{}{0}{a}{b}").root, Kind.GENFRAC).payload)
                                .thickness
                                .blank
                        == false,
                "空尺寸事实");
        String groupedMiddle = "\\left({x\\middle|y}\\right)";
        Node groupedFence = find(parse(groupedMiddle).root, Kind.DELIMITED);
        Node middle = find(groupedFence.second, Kind.MIDDLE);
        require(
                middle != null
                        && groupedFence.second.children.get(0).kind == Kind.ROW
                        && middle.start == groupedMiddle.indexOf("\\middle")
                        && middle.end == groupedMiddle.indexOf('|') + 1
                        && ((DelimiterSpec) middle.payload).atomGroup.equals("rel"),
                "分组middle上下文或原文范围丢失");
        require(
                find(parse("\\left(\\frac{x\\middle|y}{z}\\right)").root, Kind.MIDDLE) != null,
                "函数参数middle丢失");
        bad("{x\\middle|y}");
        bad("\\left({x\\right)}");
        bad("{\\left(x}\\right)");
        bad("\\left(\\text{x\\middle|y}\\right)");
        Node outerFence =
                find(
                        parse("\\left(\\left[x\\middle|y\\right]\\middle|z\\right)").root,
                        Kind.DELIMITED);
        Node innerFence = outerFence.second.children.get(0);
        require(
                innerFence.kind == Kind.DELIMITED
                        && all(innerFence.second, Kind.MIDDLE).size() == 1
                        && outerFence.second.children.get(1).kind == Kind.MIDDLE,
                "nestedleft没有最近fence作用域或退出恢复");
        require(
                find(
                                parse("\\left(\\begin{matrix}x\\middle|y\\end{matrix}\\right)")
                                        .root,
                                Kind.MIDDLE)
                        != null,
                "环境参数middle活动上下文丢失");
        bad("\\left(x\\right)\\middle|y");
        parse("a\\above{0pt}b");
        parse("\\cfrac[l]{a}{b}");
        require(
                ((FractionSpec)
                                find(parse("\\genfrac{}{}{1pt}{9}{a}{b}").root, Kind.GENFRAC)
                                        .payload)
                        .style.equals("auto"),
                "越界style没有继承");
        require(
                ((FractionSpec)
                                find(parse("\\genfrac{}{}{1pt}{12}{a}{b}").root, Kind.GENFRAC)
                                        .payload)
                        .style.equals("text"),
                "style未按首textord消费");
        parse("\\genfrac{}{}{1pt}{x}{a}{b}");
        bad("\\genfrac{}{}{1pt}{{}}{a}{b}");
        bad("a\\over b\\over c");
        bad("\\genfrac{}{}{2zz}{0}{a}{b}");
    }

    private void symbols() {
        MathContent rq = MathContent.parse("\\rq  y");
        StringBuilder expanded = new StringBuilder();
        for (MathLexer.Token token : rq.expandedTokens)
            expanded.append(token.text)
                    .append('@')
                    .append(token.sourceStart)
                    .append(':')
                    .append(token.sourceEnd)
                    .append(' ');
        require(rq.root != null && rq.diagnostics.isEmpty(), "rq无base prime未解析: " + expanded);
        Node standalonePrime = find(rq.root, Kind.SCRIPTS);
        require(
                standalonePrime != null
                        && standalonePrime.first.kind == Kind.ROW
                        && standalonePrime.first.children.isEmpty()
                        && standalonePrime.second.children.size() == 1
                        && ((GlyphSpec) standalonePrime.second.children.get(0).payload)
                                .originalToken.equals("\\prime"),
                "rq固定supsub空base+prime语义");
        require(
                all(rq.root, Kind.GLYPH).size() == 2
                        && rq.root.start == 0
                        && rq.root.end == rq.source.length(),
                "rq邻字与根来源");
        parse("'");
        parse("''_a");
        parse("\\text{\\rq  y}");
        bad("x^'");
        bad("{}^'");
        Node s = find(parse("\\sum\\limits\\nolimits_{i}'^2").root, Kind.SCRIPTS);
        require(s.value.equals("nolimits") && ((OperatorSpec) s.payload).symbol, "limits后覆");
        List<Node> symbols = all(parse("\\alpha-\\varnothing𝑨𝛂漢").root, Kind.GLYPH);
        require(((GlyphSpec) symbols.get(1).payload).atomGroup.equals("bin"), "目录atomgroup");
        require(((GlyphSpec) symbols.get(2).payload).catalogFont.equals("ams"), "目录font");
        require(
                ((GlyphSpec) symbols.get(3).payload).styledFont.equals("boldsymbol")
                        && symbols.get(3).value.equals("𝑨"),
                "styledliteral丢失");
        OperatorSpec sumFacts = (OperatorSpec) find(parse("\\sum").root, Kind.OPERATOR).payload;
        require(
                sumFacts.symbolFacts != null
                        && sumFacts.symbolFacts.atomGroup.equals("op-token")
                        && sumFacts.symbolFacts.catalogFont.equals("main"),
                "op-token目录facts丢失");
        SpaceSpec spaceFacts = (SpaceSpec) find(parse("x\\nobreak y").root, Kind.SPACE).payload;
        require(
                spaceFacts.symbolFacts != null
                        && spaceFacts.symbolFacts.atomGroup.equals("spacing")
                        && spaceFacts.symbolFacts.replacement == null,
                "spacing nullglyph事实丢失");
        Node unicode = find(parse("x₂₃⁴").root, Kind.SCRIPTS);
        require(
                unicode != null && unicode.second != null && unicode.third != null,
                "Unicode脚本被当普通glyph");
        require(
                all(unicode.third, Kind.GLYPH).get(0).value.equals("2")
                        && all(unicode.third, Kind.GLYPH).get(1).value.equals("3"),
                "Unicode脚本顺序");
        require(find(parse("é+í+\\text{é}").root, Kind.ACCENT) != null, "Unicode组合重音被当完整glyph");
        require(
                ((GlyphSpec) find(parse("漢").root, Kind.GLYPH).payload).mode.equals("text"),
                "匿名Unicode误归数学字形");
        Node integral = find(parse("∫_0^1").root, Kind.OPERATOR);
        require(!((OperatorSpec) integral.payload).defaultLimits, "literal积分默认limits错误");
        parse("^2+_a");
        bad("x\\limits");
        bad("x^a^b");
        bad("\\notARegisteredCommand");
    }

    private void fonts() {
        MathContent c = parse("\\textbf{hello $x-1$} + \\mathbb{R} + \\sqrt\\frac{a}{b} + \\hat x");
        Node text = find(c.root, Kind.TEXT);
        require(
                text.first != null && ((FontSpec) text.payload).font.equals("textbf"),
                "textfontmode");
        require(
                find(text, Kind.STYLE) != null
                        && ((GlyphSpec) find(text, Kind.GLYPH).payload).mode.equals("text"),
                "text内嵌math");
        require(find(c.root, Kind.ROOT).first.kind == Kind.FRAC, "sqrt原子参数盲组");
        parse("\\text{\\'e \\textit{ok}}\\rm a+\\it b");
        bad("\\text{\\alpha}");
        List<Node> ligatures = all(parse("\\text{-- --- `` ''}").root, Kind.GLYPH);
        require(
                ligatures.size() == 4
                        && ligatures.get(0).value.equals("–")
                        && ligatures.get(1).value.equals("—"),
                "text ligatures事实");
        bad("\\sqrt\\left(x\\right)");
    }

    private void decorations() {
        MathContent c =
                parse(
                        "\\textcolor{RebeccaPurple}{\\widehat{x}}+\\rule[-1pt]{2em}{.2pt}+\\fcolorbox{#abc}{FF00AA}{t}+\\raisebox{2ex}{x}+\\mathchoice{a}{b}{c}{d}");
        require(
                ((ColorSpec) find(c.root, Kind.COLOR).payload).css.equals("RebeccaPurple"),
                "CSS色名被缩为子集");
        BoxSpec rule = (BoxSpec) find(c.root, Kind.RULE).payload;
        require(rule.width.unit.equals("em") && rule.shift.number == -1, "rule尺寸事实");
        BoxSpec box = (BoxSpec) find(c.root, Kind.BOX).payload;
        require(box.background.equals("#FF00AA") && box.border.equals("#abc"), "框颜色顺序");
        require(find(c.root, Kind.MATHCHOICE).children.size() == 4, "choice四个分支");
        Node over = find(parse("\\overset{a}{b}").root, Kind.OVERUNDER);
        require(
                over.first.value.equals("b") || over.first.children.get(0).value.equals("b"),
                "overunder base顺序");
        parse(
                "\\phantom{x}\\hphantom{x}\\vphantom{x}\\smash[t]{x}\\mathrlap{x}\\pmb{x}\\vcenter{x}\\cancel{x}\\html@mathml{x}{y}\\verb|a%b|");
        parse("\\kern 1pt \\mkern 2mu");
        bad("\\rule{NaNem}{2pt}");
        bad("\\textcolor{rgba(0,0,0,1)}{x}");
    }

    private void trust() {
        MathContent c =
                parse(
                        "\\href{https://a%20~}{x}\\url{file:///a}\\htmlClass{q}{x}\\includegraphics[width=2em,height=3pt]{a.png}");
        require(all(c.root, Kind.UNSUPPORTED).size() == 4, "defaultdenied不是显式红命令node");
        for (Node n : all(c.root, Kind.UNSUPPORTED))
            require(
                    n.first == null
                            && n.children.isEmpty()
                            && ((UnsupportedSpec) n.payload).reason.equals("trust=false"),
                    "拒绝节点携带host执行内容");
        parse("\\htmlData{a=b,c=d}{x}");
        bad("\\htmlData{bad}{x}");
        parse("\\includegraphics[alt={x]}]{a.png}");
        bad("\\href{a}{");
        bad("\\includegraphics[width=2zz]{x}");
        bad("\\includegraphics[bogus=2pt]{x}");
    }

    private void sources() {
        String ligatureSource = "\\text{\\def\\f#1{-#1-}\\f{-}}";
        MathContent ligature = parse(ligatureSource);
        int expectedStart = ligatureSource.length(), expectedEnd = 0, parts = 0;
        StringBuilder spans = new StringBuilder();
        for (MathLexer.Token token : ligature.expandedTokens)
            if (token.text.equals("-")) {
                expectedStart = Math.min(expectedStart, token.sourceStart);
                expectedEnd = Math.max(expectedEnd, token.sourceEnd);
                parts++;
                spans.append(token.sourceStart).append("..").append(token.sourceEnd).append(' ');
            }
        Node joined = find(ligature.root, Kind.GLYPH);
        require(
                parts == 3
                        && ((GlyphSpec) joined.payload).originalToken.equals("---")
                        && joined.value.equals("—"),
                "三dash连字语义");
        require(
                joined.start == expectedStart && joined.end == expectedEnd,
                "连字来源漏中间参数: expanded="
                        + spans
                        + ", joined="
                        + joined.start
                        + ".."
                        + joined.end
                        + ", expected="
                        + expectedStart
                        + ".."
                        + expectedEnd);
        MathContent c = parse("\\def\\f#1{\\frac{#1}{#1}}\\f{x}");
        for (Node n : all(c.root, Kind.GLYPH))
            require(n.start >= 0 && n.end <= c.source.length(), "macro原文映射");
        bad("\\def\\twice#1{#1#1}\\twice{\\embed{slot}}");
        parse("\\embed{a}\\embed{b}");
        bad("\\left(x");
        parse("x+1");
    }

    private void tags() {
        MathContent c =
                parse(
                        "\\def\\both{\\begin{equation}a\\tag{A}\\end{equation}\\begin{equation}b\\tag*{B}\\end{equation}}\\both");
        List<Node> nodes = all(c.root, Kind.ARRAY);
        require(nodes.size() == 2 && c.rowTags.size() == 2, "宏复制环境标签");
        for (Node n : nodes) require(((ArraySpec) n.payload).rows.get(0).tagChild >= 0, "标签未绑定环境");
        require(
                c.rowTags.get(0).environmentOrdinal != c.rowTags.get(1).environmentOrdinal,
                "同源环境身份冲突");
        require(find(parse("x\\tag{A}").root, Kind.TAG) != null, "独立tagTokens未消费");
        bad("x\\tag{a}\\tag{b}");
    }

    private void functionTypes() {
        for (String input :
                new String[] {
                    "\\xrightarrow[b]{a}",
                    "\\overbrace{x}^n",
                    "\\underleftarrow{x}",
                    "\\overline{x}",
                    "\\underline{x}",
                    "\\mathord{x}\\mathrel{x}\\mathop{x}\\operatorname{foo}",
                    "\\Bigl( x \\Bigr)",
                    "\\left(a\\middle|b\\right)",
                    "{\\scriptscriptstyle x}{\\Huge x}{\\color{red} x}",
                    "\\text{a\\hbox{b}}",
                    "\\@char{65}",
                    "x\\\\[1pt]y",
                    "\\relax x",
                    "\\stackrel{a}{b}"
                }) parse(input);
        int count = 0;
        List<String> failures = new ArrayList<String>();
        for (MathCommandCatalog.Function f : MathCommandCatalog.functions()) {
            String input = sample(f);
            if (input == null) {
                if (f.type.equals("internal")) {
                    try {
                        new MathParser(
                                        java.util.Collections.singletonList(
                                                new MathLexer.Token(
                                                        f.name, 0, f.name.length(), f.name)),
                                        f.name.length())
                                .parse(java.util.Collections.<MathLexer.Token>emptyList());
                        throw new AssertionError("internal命令未守context接缝: " + f.name);
                    } catch (MathParser.Failure expected) {
                        require(expected.getMessage().contains("展开层"), "internal没有精确接缝诊断");
                    }
                } else bad(f.name);
                count++;
                continue;
            }
            MathContent c = MathContent.parse(input);
            if (!c.diagnostics.isEmpty())
                failures.add(
                        f.name
                                + " type="
                                + f.type
                                + " input="
                                + input
                                + " -> "
                                + c.diagnostics.get(0).message);
            count++;
        }
        require(count == 309 && failures.isEmpty(), "完整登记消费者失败: " + failures);
    }

    private void fixtures() throws Exception {
        int functions = 0, environments = 0;
        for (String[] row : fixture("function-consumers.tsv")) {
            if (row[2].equals("token-context-guard")) {
                try {
                    new MathParser(
                                    java.util.Collections.singletonList(
                                            new MathLexer.Token(
                                                    row[0], 0, row[0].length(), row[0])),
                                    row[0].length())
                            .parse(java.util.Collections.<MathLexer.Token>emptyList());
                    throw new AssertionError("internal token被当glyph: " + row[0]);
                } catch (MathParser.Failure expected) {
                    require(expected.getMessage().contains("展开层"), "context错误分类");
                }
            } else if (row[2].equals("context-guard")) bad(row[4]);
            else {
                MathContent c = parse(row[4]);
                Node typed = find(c.root, Kind.valueOf(row[3]));
                require(typed != null, "登记typedkind丢失: " + row[0] + " / " + row[3]);
                if (row[2].equals("source-origin-context")) {
                    if (row[1].equals("genfrac"))
                        require(typed.payload instanceof FractionSpec, "真实infix来源没有分式facts");
                    else
                        require(
                                typed.payload instanceof DecorationSpec
                                        && ((DecorationSpec) typed.payload)
                                                .command.startsWith("cd"),
                                "真实CD来源缺少方向facts");
                }
            }
            functions++;
        }
        for (String[] row : fixture("environment-consumers.tsv")) {
            Node n = find(parse(row[1]).root, Kind.ARRAY);
            require(((ArraySpec) n.payload).environment.equals(row[0]), "环境identity丢失");
            environments++;
        }
        require(functions == 309 && environments == 33, "独立结构语料不完整");
    }

    private List<String[]> fixture(String name) throws Exception {
        java.io.InputStream input = getClass().getResourceAsStream("/math/structure/" + name);
        require(input != null, "结构语料未随正式平台资源注册");
        List<String[]> out = new ArrayList<String[]>();
        try (java.io.BufferedReader r =
                new java.io.BufferedReader(
                        new java.io.InputStreamReader(
                                input, java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.isEmpty()) continue;
                String[] values = line.split("\t", -1);
                for (int n = 0; n < values.length; n++) values[n] = unescape(values[n]);
                out.add(values);
            }
        }
        return out;
    }

    private String unescape(String value) {
        StringBuilder out = new StringBuilder();
        for (int n = 0; n < value.length(); n++) {
            char ch = value.charAt(n);
            if (ch == '\\' && n + 1 < value.length()) {
                char escaped = value.charAt(++n);
                out.append(
                        escaped == 't'
                                ? '\t'
                                : escaped == 'n' ? '\n' : escaped == 'r' ? '\r' : escaped);
            } else out.append(ch);
        }
        return out.toString();
    }

    private String sample(MathCommandCatalog.Function f) {
        String name = f.name;
        if (f.type.equals("internal")) {
            if (name.equals("\\relax")) return name + " x";
            if (name.startsWith("\\\\")) return null;
            if (name.equals("\\let")) return name + "\\a=x\\a";
            if (name.equals("\\futurelet")) return name + "\\a\\relax x\\a";
            if (name.equals("\\global") || name.equals("\\long")) return name + "\\def\\a{x}\\a";
            return name + "\\a{x}\\a";
        }
        if (f.type.equals("environment")) return "\\begin{matrix}a\\end{matrix}";
        if (name.equals("\\hline") || name.equals("\\hdashline"))
            return "\\begin{matrix}" + name + " a\\end{matrix}";
        if (name.equals("\\(") || name.equals("\\)")) return "\\text{\\(a\\)}";
        if (name.equals("\\]")) return null;
        if (name.equals("$")) return "\\text{$a$}";
        if (f.type.equals("leftright")
                || f.type.equals("middle")
                || f.type.equals("leftright-right")) return "\\left(a\\middle|b\\right)";
        if (f.type.equals("infix"))
            return "a" + name + (name.equals("\\above") ? "{1pt}" : " ") + "b";
        if (f.type.equals("genfrac") && name.equals("\\genfrac"))
            return name + "{(}{)}{1pt}{0}{a}{b}";
        if (f.type.equals("delimsizing")) return name + "(";
        if (f.type.equals("verb")) return "\\verb|a%b|";
        StringBuilder s = new StringBuilder(name);
        for (int n = 0; n < f.numOptionalArgs + f.numArgs; n++) {
            boolean optional = n < f.numOptionalArgs;
            String type = n < f.argTypes.size() ? f.argTypes.get(n) : "original";
            String value =
                    type.equals("size")
                            ? "1pt"
                            : type.equals("color")
                                    ? "red"
                                    : type.equals("url")
                                            ? "https://a%20"
                                            : type.equals("raw")
                                                    ? f.type.equals("includegraphics") && optional
                                                            ? "width=1pt"
                                                            : f.name.equals("\\htmlData")
                                                                    ? "key=value"
                                                                    : "q"
                                                    : f.type.equals("smash") && optional
                                                            ? "t"
                                                            : f.type.equals("textord")
                                                                    ? "65"
                                                                    : f.name.equals("\\htmlData")
                                                                                    && n == 0
                                                                            ? "key=value"
                                                                            : "x";
            s.append(optional ? '[' : '{').append(value).append(optional ? ']' : '}');
        }
        if (f.numArgs == 0
                && (f.type.equals("font") || f.type.equals("sizing") || f.type.equals("styling")))
            s.append(" x");
        return f.allowedInMath ? s.toString() : "\\text{" + s + "}";
    }
}
