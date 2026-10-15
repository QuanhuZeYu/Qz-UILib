package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.math.MathCommandCatalog;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathLexer;
import club.heiqi.uilib.ui.scene.text.math.MathMacroExpander;
import club.heiqi.uilib.ui.scene.text.math.MathMacros;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 宏语义与来源独立于盒布局；期望来自固定 KaTeX 源码及 bundle，而非生产展开实现。 */
public final class MathMacroScenario extends TestApplication {
    public static void main(String[] args) throws Exception {
        new MathMacroScenario().run(args);
    }

    @Override
    protected void runTests() throws Exception {
        scenario("控制词控制符注释UTF16与纯token边界", this::lexer);
        scenario("定义定界参数作用域global及文档上下文", this::definitions);
        scenario("edef保护expandafter与let快照futurelet", this::protection);
        scenario("二十真实function宏参数模式及日志", this::functions);
        scenario("环境单元格宏作用域与行tag状态", this::environments);
        scenario("全部内置string宏可消费与参数替换", this::catalog);
        scenario("展开界限诊断来源config与Item唯一身份", this::sources);
        scenario("有限与递归实参倍增在资源界限内定位失败", this::expansionResources);
        scenario("评审I1严格展开保留let永久快照", this::reviewProtection);
        scenario("评审I2星号matrix无scope语法头", this::reviewMatrixHeader);
        scenario("评审I3行距raw窗口顺序及relax空白边界", this::reviewRowSize);
        scenario("评审I4逐环境arraystretch读取窗口与身份", this::reviewArrayStretch);
    }

    private static String tex(String s) {
        return s.replace('@', (char) 92).replace('§', '@');
    }

    private static String joined(List<MathLexer.Token> tokens) {
        StringBuilder s = new StringBuilder();
        for (MathLexer.Token t : tokens) s.append(t.text);
        return s.toString();
    }

    private MathMacroExpander.Result good(String s) {
        MathMacroExpander.Result r = MathMacroExpander.expand(tex(s));
        require(
                r.diagnostics.isEmpty(),
                s + " -> " + (r.diagnostics.isEmpty() ? "" : r.diagnostics.get(0).message));
        return r;
    }

    private void expect(String s, String expected) {
        String actual = joined(good(s).tokens);
        require(
                actual.equals(tex(expected)),
                s + " expected=" + tex(expected) + " actual=" + actual);
    }

    private void lexer() {
        List<MathLexer.Token> ts = MathLexer.lex(tex("@alpha x%comment\n😀@%@@@ "));
        require(ts.get(0).text.equals(tex("@alpha")) && ts.get(0).sourceEnd == 7, "控制词或尾空格来源丢失");
        require(
                ts.get(2).text.equals("😀") && ts.get(2).sourceEnd - ts.get(2).sourceStart == 2,
                "代理对被切开");
        expect("@def@a{@alpha}@a x", "@alphax");
        List<MathLexer.Token> separated = good("@def@a{@alpha}@a x").tokens;
        require(
                separated.size() == 2
                        && separated.get(0).text.equals(tex("@alpha"))
                        && separated.get(1).text.equals("x"),
                "展开token被合成控制词");
        expect("@def@a{@alpha}@a{}x", "@alpha{}x");
        List<MathLexer.Token> verb = MathLexer.lex(tex("@verb|a%@def|@verb*AbA@verbatim @verb§x§"));
        require(verb.size() == 4
                        && verb.get(0).text.equals(tex("@verb|a%@def|"))
                        && verb.get(1).text.equals(tex("@verb*AbA"))
                        && verb.get(2).text.equals(tex("@verbatim"))
                        && verb.get(3).text.equals(tex("@verb§x§")),
                "verb定界正文、星号字母定界或普通控制词边界改变");
        String whitespace = tex("@ \r\n \tx\n");
        List<MathLexer.Token> spaces = MathLexer.lex(whitespace);
        require(spaces.get(0).text.equals(tex("@ "))
                        && spaces.get(0).sourceEnd == whitespace.indexOf('x'),
                "控制空格未保留一次换行及相邻空白范围");
        List<MathLexer.Token> combined = MathLexer.lex("😀\u0301x");
        require(combined.size() == 2 && combined.get(0).text.equals("😀\u0301")
                        && combined.get(0).sourceEnd == combined.get(1).sourceStart,
                "Unicode标量与组合符被拆开");
        MathContent invalidControl = MathContent.parse(tex("@😀"));
        require(!invalidControl.diagnostics.isEmpty()
                        && invalidControl.diagnostics.get(0).start == 0
                        && invalidControl.diagnostics.get(0).end == tex("@😀").length(),
                "非法补充字符控制符诊断未覆盖完整代理对");
    }

    private void definitions() {
        expect("@def@f#1,#2;{#2+#1}@f {a,b},c;", "c+a,b");
        expect("@def@f x#1x{#1}@f xax", "a");
        expect("@def@f#1;{#1}@f ;", "");
        expect("@def@a{a}{@def@a{b}@a}@a", "{b}a");
        expect("@def@a{a}{@gdef@a{b}}@a", "{}b");
        expect("{@global@long@def@a{x}}@a", "{}x");
        expect("@newcommand{@f}[2]{#2#1}@f ab", "ba");
        expect("@providecommand{@f}{a}@providecommand{@f}{b}@renewcommand{@f}{c}@f", "c");
        MathMacros first = good("@def@a{a}").nextMacros;
        require(
                joined(MathMacroExpander.expand(tex("@a"), first).tokens).equals("a"),
                "nextMacros不延续文档");
        require(
                joined(MathMacroExpander.expand(tex("@a")).tokens).equals(tex("@a")),
                "普通parse污染其它文档");
        require(!MathMacros.empty().contains(tex("@a")), "输入上下文被改变");
        require(
                !MathMacroExpander.expand(tex("@renewcommand{@unknown}{a}")).diagnostics.isEmpty(),
                "renew未定义未失败");
        require(!MathMacroExpander.expand(tex("@def@f#2{x}")).diagnostics.isEmpty(), "乱序参数未失败");
        require(
                !MathMacroExpander.expand(tex("@begingroup x}")).diagnostics.isEmpty(),
                "异类group闭符未失败");
        expect("@def@a{a}@begingroup@def@a{b}@a@endgroup@a", "@begingroupb@endgroupa");
    }

    private void protection() {
        expect("@def@a{a}@edef@b{@a}@def@a{c}@b", "a");
        expect("@def@a{a}@edef@b{@noexpand@a}@def@a{c}@b", "c");
        expect("@def@a{a}@noexpand@a", "@relax");
        expect("@def@a{a}@expandafter x@a", "xa");
        expect("@def@a{a}@let@b=@a@def@a{c}@b", "a");
        expect("@let@a=@alpha@def@alpha{x}@a", "@alpha");
        expect("@def@f{@a}@futurelet@a@f x", "xx");
        expect("{@xdef@a{x}}@a", "{}x");
    }

    private void functions() {
        expect("@§firstoftwo{a}{b}", "a");
        expect("@§secondoftwo{a}{b}", "b");
        expect("@§ifnextchar *{a}{b} *", "a*");
        expect("@§ifstar{a}{b}*", "a");
        expect("@TextOrMath{t}{m}", "m");
        expect("@text{@TextOrMath{t}{m}}", "@text{t}");
        expect("@text{$@TextOrMath{t}{m}$@TextOrMath{t}{m}}", "@text{$m$t}");
        expect("@text{@(@TextOrMath{t}{m}@)}", "@text{@(m@)}");
        expect(
                "@char65@char'101@char\"41@char" + (char) 96 + "A",
                "@§char{65}@§char{65}@§char{65}@§char{65}");
        expect("@dotso x", "@ldotsx");
        expect("@dotsc,", "@ldots,");
        expect("@cdots x", "@§cdotsx");
        expect("@dots+", "@§cdots+");
        expect("@bra§ket{L}{S}{D}{R}{a|b||c}", "LaSbDcR");
        expect("@bra§set{L}{S}{D}{R}{a|b|c}", "LaSb|cR");
        MathMacroExpander.Result tag = good("x@tag{1}");
        require(
                joined(tag.tokens).equals("x") && joined(tag.tagTokens).equals(tex("@text{({1})}")),
                "tag提取丢失");
        require(!tag.nextMacros.contains(tex("@df§tag")), "tag泄露下一公式");
        require(!MathMacroExpander.expand(tex("@tag{1}@tag{2}")).diagnostics.isEmpty(), "重复tag未失败");
        MathMacroExpander.Result messages = good("@message{a}@errmessage{b}@show@alpha x");
        require(
                messages.messages.size() == 3
                        && messages.messages.get(0).text.equals("a")
                        && joined(messages.tokens).equals("x"),
                "日志宏未消费原参数");
        expect("@def@url{a}@href{@url%20~}{x}", "@href{a%20~}{x}");
        expect("@def@c{red}@textcolor{@c}{x}", "@textcolor{red}{x}");
        expect("@color{red}@current§color", "@color{red}red");
        expect("@color{aBc123}@current§color", "@color{aBc123}#aBc123");
        expect(
                "@color{red}{@color{blue}@current§color}@current§color",
                "@color{red}{@color{blue}blue}red");
        require(!good("@color{red}").nextMacros.contains(tex("@current§color")), "color宏泄露下一公式");
        expect("@verb|@def@a{x}%|", "@verb|@def@a{x}%|");
    }

    private void environments() {
        MathMacroExpander.Result cells =
                good("@def@a{z}@begin{matrix}@def@a{x}@a&@a@@@a@end{matrix}@a");
        String body = joined(cells.tokens);
        require(body.equals(tex("@begin{matrix}x&z@@z@end{matrix}z")), "单元格宏定义泄露: " + body);
        expect("@def@a{z}@left(@def@a{x}@a@right)@a", "@left(x@right)x");
        MathMacroExpander.Result tags = good("@begin{align}a@tag{1}@@b@tag*{B}@end{align}");
        require(tags.rowTags.size() == 2 && tags.tagTokens.isEmpty(), "环境行tag未单独捕获");
        require(
                joined(tags.rowTags.get(0).tokens).equals(tex("@text{({1})}"))
                        && joined(tags.rowTags.get(1).tokens).equals(tex("@text{B}")),
                "行tag内容错误");
        require(
                tags.rowTags.get(1).row == 1 && tags.rowTags.get(0).environmentStart == 0,
                "行tag原始定位错误");
        tags = good("@begin{align}a@nonumber@@b@end{align}");
        require(!tags.rowTags.get(0).automatic && tags.rowTags.get(1).automatic, "nonumber跨行泄露");
        expect("@begin{matrix}a@cr b@end{matrix}", "@begin{matrix}a@@@relaxb@end{matrix}");
        require(!good("@begin{matrix}x@end{matrix}").nextMacros.contains(tex("@cr")), "环境cr泄露下一公式");
        expect("@verb*AhiA", "@verb*AhiA");
        String twin =
                tex(
                        "@def@both{@begin{equation}a@tag{1}@end{equation}@begin{equation}b@tag{2}@end{equation}}@both");
        MathMacroExpander.Result repeated = MathMacroExpander.expand(twin);
        require(repeated.diagnostics.isEmpty() && repeated.rowTags.size() == 2, "同源环境未生成两个独立标签");
        require(
                repeated.rowTags.get(0).environmentStart == repeated.rowTags.get(1).environmentStart
                        && repeated.rowTags.get(0).environmentOrdinal
                                != repeated.rowTags.get(1).environmentOrdinal,
                "同源范围被误当环境身份");
        int count = 0;
        for (MathLexer.Token token : repeated.tokens)
            if (token.text.equals(tex("@begin")))
                require(token.environmentOrdinal == count++, "环境token身份未同步rowTags");
    }

    private void catalog() {
        int strings = 0, functions = 0;
        for (MathCommandCatalog.Macro m : MathCommandCatalog.macros()) {
            if (m.kind.equals("function")) {
                functions++;
                continue;
            }
            strings++;
            String stripped = m.expansion.replace("##", "");
            int n = 0;
            while (stripped.contains("#" + (n + 1))) n++;
            StringBuilder source = new StringBuilder(m.name).append(' ');
            for (int i = 0; i < n + 4; i++) source.append("{x}");
            // tag和换行是合法宏输出；是否可在具体数学环境呈现归 parser/布局场景。
            if (m.name.equals(tex("@bgroup"))) source.append('}');
            if (m.name.equals(tex("@egroup"))) source.insert(0, '{');
            MathMacroExpander.Result r = MathMacroExpander.expand(source.toString());
            require(
                    r.diagnostics.isEmpty(),
                    "内置宏不能展开: "
                            + m.name
                            + " -> "
                            + (r.diagnostics.isEmpty() ? "" : r.diagnostics.get(0).message));
        }
        require(strings == 318 && functions == 20, "固定宏事实计数错误");
    }

    private void reviewProtection() {
        expect("@let@foo=@frac@edef@bar{@foo}@def@frac{x}@bar{a}{b}", "@frac{a}{b}");
        expect("@let@foo=@frac@xdef@bar{@foo}@def@frac{x}@bar{a}{b}", "@frac{a}{b}");
        expect("@def@a{a}@edef@b{@noexpand@a}@def@a{c}@b", "c");
    }

    private void reviewMatrixHeader() {
        String[] names = {"matrix*", "pmatrix*", "bmatrix*", "Bmatrix*", "vmatrix*", "Vmatrix*"};
        for (String name : names) {
            MathMacroExpander.Result r =
                    good("@begin{" + name + "}[@newcommand@foo{r}@foo]x@end{" + name + "}@foo");
            require(
                    joined(r.tokens).equals(tex("@begin{" + name + "}[r]x@end{" + name + "}r"))
                            && r.nextMacros.contains(tex("@foo")),
                    "starred handler头部被错误局部化: " + name);
        }
        MathMacroExpander.Result primitive = good("@begin{matrix*}[@def@foo{r}r]x@end{matrix*}");
        require(
                !primitive.nextMacros.contains(tex("@foo"))
                        && joined(primitive.tokens).contains(tex("@def@foo")),
                "星号matrix头部执行primitive assignment");
    }

    private void reviewRowSize() {
        MathMacroExpander.Result raw = good("@begin{matrix}a@@[@def@foo{x}1em]@foo@end{matrix}");
        require(
                joined(raw.tokens).contains(tex("[@def@foo{x}1em]@foo")),
                "行距参数执行primitive assignment");
        raw = good("a@@[@def@foo{x}1em]@foo");
        require(joined(raw.tokens).equals(tex("a@@[@def@foo{x}1em]@foo")), "顶层cr未保持raw size窗口");
        raw = good("@begin{matrix}a@cr[@def@foo{x}1em]@foo@end{matrix}");
        require(joined(raw.tokens).contains(tex("@@@relax[1em]x")), "cr relax后误读size窗口");
        raw = good("@begin{matrix}a@@ [@def@foo{x}1em]@foo@end{matrix}");
        require(joined(raw.tokens).contains(" [1em]x"), "leading空白后误读size窗口");
        raw = good("@begin{align}a@@[@renewcommand@§eqnsw{0}1em]b@end{align}");
        require(
                raw.rowTags.size() == 2
                        && !raw.rowTags.get(0).automatic
                        && raw.rowTags.get(1).automatic,
                "size函数宏副作用落在错误行结算窗口");
    }

    private void reviewArrayStretch() {
        MathMacroExpander.Result r =
                good("@def@arraystretch{@newcommand@foo{x}2}@begin{matrix}@foo@end{matrix}");
        require(
                joined(r.tokens).equals(tex("@begin{matrix}x@end{matrix}"))
                        && !r.nextMacros.contains(tex("@foo")),
                "arraystretch读取副作用缺失或group错误");
        require(
                r.environmentHeaders.size() == 1
                        && r.environmentHeaders.get(0).arrayStretchText.equals("2")
                        && r.environmentHeaders.get(0).arrayStretchFromMacro,
                "arraystretch header丢失");
        r =
                good(
                        "{@def@arraystretch{2}@begin{matrix}x@end{matrix}}{@def@arraystretch{3}@begin{matrix}y@end{matrix}}");
        require(
                r.environmentHeaders.size() == 2
                        && r.environmentHeaders.get(0).arrayStretchText.equals("2")
                        && r.environmentHeaders.get(1).arrayStretchText.equals("3")
                        && !r.nextMacros.contains(tex("@arraystretch")),
                "不同局部arraystretch被final上下文覆盖");
        r =
                good(
                        "@def@arraystretch{@message{read}2}@def@twice{@begin{matrix}x@end{matrix}@begin{matrix}y@end{matrix}}@twice");
        require(
                r.messages.size() == 2
                        && r.environmentHeaders.size() == 2
                        && r.environmentHeaders.get(0).environmentOrdinal
                                != r.environmentHeaders.get(1).environmentOrdinal,
                "同源环境必须每次读取头部并保持独立ordinal");
        String[] names = {"smallmatrix", "subarray", "cases", "dcases", "rcases", "drcases", "CD"};
        for (String name : names) {
            String arg = name.equals("subarray") ? "{c}" : "";
            r =
                    good(
                            "@def@arraystretch{@message{forbidden}9}@begin{"
                                    + name
                                    + "}"
                                    + arg
                                    + "x@end{"
                                    + name
                                    + "}");
            String value =
                    name.equals("smallmatrix") || name.equals("subarray")
                            ? "0.5"
                            : name.equals("CD") ? "1" : "1.2";
            require(
                    r.messages.isEmpty()
                            && r.environmentHeaders.get(0).arrayStretchText.equals(value)
                            && !r.environmentHeaders.get(0).arrayStretchFromMacro,
                    "固定常量环境错误执行arraystretch: " + name);
        }
    }

    private void expansionResources() {
        String plain = String.join("", java.util.Collections.nCopies(16384, "x"));
        require(good(plain).tokens.size() == plain.length(), "源长界限内的普通正文被累计工作误拒绝");
        MathMacroExpander.Result legal = good(doublingChain(8));
        require(legal.tokens.size() == 256, "合法有限宏展开被资源预算误拒绝");
        for (MathLexer.Token token : legal.tokens) require(token.text.equals("x"), "合法倍增内容改变");
        String finite = tex(doublingChain(20));
        resourceFailure(finite, finite.lastIndexOf(tex("@auditA")));
        String recursive = tex("@def@a#1{@a{#1#1}}@a{x}");
        resourceFailure(recursive, recursive.lastIndexOf(tex("@a")));
        String indirect = tex("@def@a#1{@b{#1#1}}@def@b#1{@a{#1}}@a{x}");
        resourceFailure(indirect, indirect.lastIndexOf(tex("@a")));
        // raw 模式也必须共享每式预算，不能因临时输入窗口重置累计量。
        String raw = tex("@def@a#1{@a{#1#1}}@href{@a{x}}{x}");
        resourceFailure(raw, raw.lastIndexOf(tex("@a")));
        // 单个 verb token 的正文可能很长，少量 token 也不能逃过文本复制工作量。
        String longToken = tex(doublingChain(8)).replace("{x}", tex("{@verb|")
                + String.join("", java.util.Collections.nCopies(2048, "x")) + "|}");
        resourceFailure(longToken, longToken.lastIndexOf(tex("@auditA")));
        require(MathContent.parse("x").diagnostics.isEmpty(), "资源失败污染下一独立公式");
    }

    /** 与审计 JVM 反例使用同一有限链，期望规模直接来自每级复制两次。 */
    private static String doublingChain(int rounds) {
        StringBuilder source = new StringBuilder();
        for (int i = 0; i < rounds; i++)
            source.append("@def@audit").append((char) ('A' + i)).append("#1{@audit")
                    .append((char) ('A' + i + 1)).append("{#1#1}}");
        return source.append("@def@audit").append((char) ('A' + rounds))
                .append("#1{#1}@auditA{x}").toString();
    }

    private void resourceFailure(String source, int callStart) {
        MathMacroExpander.Result result = MathMacroExpander.expand(source);
        require(!result.diagnostics.isEmpty(), "危险展开没有资源诊断");
        MathMacroExpander.Diagnostic diagnostic = result.diagnostics.get(0);
        require(diagnostic.sourceStart == callStart && diagnostic.sourceEnd > callStart,
                "资源诊断未定位原宏调用：" + diagnostic.message);
        MathContent content = MathContent.parse(source);
        require(content.source.equals(source) && !content.diagnostics.isEmpty()
                        && content.diagnostics.get(0).stage.equals("macro"),
                "资源失败未沿 macro 阶段保留整式原文");
    }

    private void sources() {
        String source = tex("😀@def@loop{@loop}@loop");
        MathMacroExpander.Result r = MathMacroExpander.expand(source);
        require(
                !r.diagnostics.isEmpty()
                        && r.diagnostics.get(0).sourceStart == source.lastIndexOf(tex("@loop")),
                "循环未定位真实调用");
        Map<String, String> config = new HashMap<String, String>();
        config.put(tex("@f"), tex("@alpha#1"));
        String call = tex("@f{x}");
        r = MathMacroExpander.expand(call, MathMacros.of(config));
        require(
                r.tokens.get(0).sourceStart == 0
                        && r.tokens.get(0).sourceEnd == 2
                        && r.tokens.get(1).sourceStart == 3,
                "配置宏/参数来源错误");
        MathContent valid = MathContent.parse(tex("@def@i#1{@embed{#1}}@i{a}@i{b}"));
        require(valid.diagnostics.isEmpty(), "宏独立Item拒绝");
        String repeated = tex("@def@i{@embed{a}}@i@i");
        MathContent invalid = MathContent.parse(repeated);
        require(
                !invalid.diagnostics.isEmpty()
                        && invalid.diagnostics.get(0).start == repeated.lastIndexOf(tex("@i")),
                "重复Item来源不在调用处");
        require(invalid.source.equals(repeated), "source被展开覆盖");
    }
}
