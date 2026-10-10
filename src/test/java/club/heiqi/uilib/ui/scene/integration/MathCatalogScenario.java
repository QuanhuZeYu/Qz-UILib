package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.math.MathCommandCatalog;
import club.heiqi.uilib.ui.scene.text.math.MathSymbols;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 目录消费者回归；不把登记存在当成生产 parse/layout/GPU 通过。 */
public final class MathCatalogScenario extends TestApplication {
    public static void main(String[] args) throws Exception {
        new MathCatalogScenario().run(args);
    }

    @Override
    protected void runTests() throws Exception {
        scenario("独立已知码点类别及数学文本mode", this::knownSymbols);
        scenario("全资源枚举查询与来源无遗漏", this::resources);
        scenario("完整字符串宏参数与函数kind", this::macroPayloads);
        scenario("目录不可变且未知名称无伪支持", this::immutableCatalogues);
    }

    private void knownSymbols() {
        symbol("math", "\\alpha", 0x03B1, "mathord", "main");
        symbol("math", "\\leq", 0x2264, "rel", "main");
        symbol("math", "\\sum", 0x2211, "op-token", "main");
        symbol("math", "\\varnothing", 0x2205, "textord", "ams");
        symbol("math", "-", 0x2212, "bin", "main");
        symbol("text", "-", 0x002D, "textord", "main");
        symbol("math", "\\ ", 0x00A0, "spacing", "main");
        symbol("text", "\\ ", 0x00A0, "spacing", "main");
        require(MathSymbols.find("text", "\\alpha") == null, "数学alpha误登记为text");
        for (String token : Arrays.asList("\\nobreak", "\\allowbreak")) {
            MathSymbols.Entry e = MathSymbols.find("math", token);
            require(
                    e != null && e.replace == null && "spacing".equals(e.group),
                    "零glyph语义丢失: " + token);
        }
    }

    private void symbol(String mode, String token, int cp, String group, String font) {
        MathSymbols.Entry e = MathSymbols.find(mode, token);
        require(e != null, "缺少独立已知符号: " + mode + " " + token);
        require(
                e.replace != null
                        && e.replace.codePointCount(0, e.replace.length()) == 1
                        && e.replace.codePointAt(0) == cp,
                "已知码点错误: " + token);
        require(group.equals(e.group) && font.equals(e.font), "已知group/font错误: " + token);
    }

    private void resources() throws Exception {
        Set<String> symbolKeys = new HashSet<String>();
        int math = 0, text = 0;
        for (String[] row : rows("symbols.tsv")) {
            MathSymbols.Entry e = MathSymbols.find(row[0], row[1]);
            require(e != null && MathSymbols.entries().contains(e), "资源符号不可枚举/查询: " + row[1]);
            require(symbolKeys.add(row[0] + ":" + row[1]), "符号重复: " + row[1]);
            require(
                    row[2].equals(e.font)
                            && row[3].equals(e.group)
                            && Objects.equals(row[4], e.replace)
                            && row[5].equals(e.visibility),
                    "符号facts丢失: " + row[1]);
            source(e.source, row, 6);
            if ("math".equals(e.mode)) math++;
            else if ("text".equals(e.mode)) text++;
        }
        require(symbolKeys.size() == MathSymbols.entries().size(), "枚举与资源符号不一致");
        require(math == 1487 && text == 749 && symbolKeys.size() == 2236, "固定参照符号数不符");
        require(
                MathSymbols.entries("math").size() == math
                        && MathSymbols.entries("text").size() == text,
                "mode枚举掉项");
        Set<String> names = new HashSet<String>();
        for (String[] row : rows("functions.tsv")) {
            MathCommandCatalog.Function f = MathCommandCatalog.findFunction(row[0]);
            require(
                    f != null && MathCommandCatalog.functions().contains(f) && names.add(row[0]),
                    "function目录掉项");
            require(
                    row[1].equals(f.type)
                            && Integer.parseInt(row[2]) == f.numArgs
                            && Integer.parseInt(row[3]) == f.numOptionalArgs,
                    "function参数数量丢失: " + row[0]);
            require(
                    Boolean.parseBoolean(row[4]) == f.allowedInArgument
                            && Boolean.parseBoolean(row[5]) == f.allowedInText
                            && Boolean.parseBoolean(row[6]) == f.allowedInMath
                            && Boolean.parseBoolean(row[7]) == f.infix
                            && Boolean.parseBoolean(row[8]) == f.primitive
                            && row[9].equals(f.visibility),
                    "function契约丢失");
            source(f.source, row, 10);
            require(
                    Boolean.parseBoolean(row[13]) == f.argTypesSpecified
                            && tail(row, 15).equals(f.argTypes),
                    "argTypes来源丢失");
            require(Integer.parseInt(row[14]) == f.argTypes.size(), "argTypes数量不符");
        }
        require(
                names.size() == 309 && names.size() == MathCommandCatalog.functions().size(),
                "function参照不完整");
        names.clear();
        for (String[] row : rows("environments.tsv")) {
            MathCommandCatalog.Environment e = MathCommandCatalog.findEnvironment(row[0]);
            require(
                    e != null && MathCommandCatalog.environments().contains(e) && names.add(row[0]),
                    "environment目录掉项");
            require(
                    row[1].equals(e.type)
                            && Integer.parseInt(row[2]) == e.numArgs
                            && Integer.parseInt(row[3]) == e.numOptionalArgs
                            && Boolean.parseBoolean(row[4]) == e.allowedInText,
                    "environment参数丢失");
            source(e.source, row, 5);
            require(
                    Boolean.parseBoolean(row[8]) == e.argTypesSpecified
                            && Integer.parseInt(row[9]) == e.argTypes.size()
                            && tail(row, 10).equals(e.argTypes),
                    "environment argTypes丢失");
        }
        require(
                names.size() == 33 && names.size() == MathCommandCatalog.environments().size(),
                "environment参照不完整");
        names.clear();
        int strings = 0, functions = 0;
        for (String[] row : rows("macros.tsv")) {
            MathCommandCatalog.Macro m = MathCommandCatalog.findMacro(row[0]);
            require(
                    m != null && MathCommandCatalog.macros().contains(m) && names.add(row[0]),
                    "macro目录掉项");
            require(
                    row[1].equals(m.kind)
                            && Objects.equals(row[2], m.expansion)
                            && row[3].equals(m.visibility),
                    "宏payload或kind丢失");
            source(m.source, row, 4);
            require(m.implementation == null, "运行资源不应包含JS算法");
            if ("string".equals(m.kind)) {
                require(m.expansion != null, "字符串宏缺payload");
                strings++;
            } else {
                require("function".equals(m.kind) && m.expansion == null, "函数宏伪造expansion");
                functions++;
            }
        }
        require(
                names.size() == 338
                        && names.size() == MathCommandCatalog.macros().size()
                        && strings == 318
                        && functions == 20,
                "宏种类或目录数不完整");
        names.clear();
        for (String[] row : rows("implicit.tsv")) {
            require(names.add(row[0]), "implicit重复");
        }
        require(names.equals(MathCommandCatalog.implicitCommands()), "implicit目录掉项");
        require(MathCommandCatalog.commandTokens().size() == 1077, "跨目录命令别名掉项");
        for (String token : MathCommandCatalog.commandTokens())
            require(token.startsWith("\\"), "command不是原始反斜杠token");
    }

    private void source(MathCommandCatalog.Source actual, String[] row, int offset) {
        require(
                actual != null
                        && row[offset].equals(actual.path)
                        && Integer.parseInt(row[offset + 1]) == actual.distLine
                        && row[offset + 2].equals(actual.url),
                "来源登记丢失");
        require(
                "0.16.21".equals(actual.referenceVersion)
                        && "923f2aa15692d06ef77051bfb018c17339ef1ab4".equals(actual.commit),
                "固定来源漂移");
    }

    private void macroPayloads() {
        require(
                "\\mathinner{\\langle{#1}|}"
                        .equals(MathCommandCatalog.findMacro("\\bra").expansion),
                "bra参数或分组截断");
        require(
                "\\TextOrMath{\\kern#1#3}{\\mskip#1#2}\\relax"
                        .equals(MathCommandCatalog.findMacro("\\tmspace").expansion),
                "tmspace三个参数/模式分支丢失");
        require(
                "\\textcolor{##28ae7b}{#1}"
                        .equals(MathCommandCatalog.findMacro("\\green").expansion),
                "转义##被错误吃掉");
        require(
                "\\bra@ket{\\left\\langle}{\\,\\middle\\vert\\,}{\\,\\middle\\vert\\,}{\\right\\rangle}"
                        .equals(MathCommandCatalog.findMacro("\\Braket").expansion),
                "Braket依赖的函数宏或空白token被改写");
        MathCommandCatalog.Macro first = MathCommandCatalog.findMacro("\\@firstoftwo");
        require(
                first != null
                        && "function".equals(first.kind)
                        && first.expansion == null
                        && "internal".equals(first.visibility),
                "内部函数宏必须独立保留");
        MathCommandCatalog.Function sqrt = MathCommandCatalog.findFunction("\\sqrt");
        require(sqrt.numArgs == 1 && sqrt.numOptionalArgs == 1 && sqrt.allowedInMath, "sqrt可选参数丢失");
        MathCommandCatalog.Function accent = MathCommandCatalog.findFunction("\\.");
        require(
                accent.argTypesSpecified
                        && accent.argTypes.equals(Arrays.asList("primitive"))
                        && accent.allowedInText,
                "重音primitive参数来源丢失");
        require(
                "false".equals(MathCommandCatalog.reference().get("default.trust"))
                        && "true".equals(MathCommandCatalog.reference().get("default.globalGroup")),
                "插件默认配置丢失");
    }

    private void immutableCatalogues() {
        immutable(() -> MathSymbols.entries().clear());
        immutable(() -> MathSymbols.entries("math").clear());
        immutable(() -> MathCommandCatalog.functions().clear());
        immutable(() -> MathCommandCatalog.environments().clear());
        immutable(() -> MathCommandCatalog.macros().clear());
        immutable(() -> MathCommandCatalog.implicitCommands().clear());
        immutable(() -> MathCommandCatalog.commandTokens().clear());
        immutable(() -> MathCommandCatalog.reference().clear());
        immutable(() -> MathCommandCatalog.findFunction("\\.").argTypes.clear());
        require(
                MathSymbols.find("unknown", "x") == null
                        && MathSymbols.entries("unknown").isEmpty(),
                "未知mode伪支持");
        require(
                MathCommandCatalog.findMacro("\\notACommand") == null
                        && MathCommandCatalog.findFunction("alpha") == null
                        && MathCommandCatalog.findEnvironment("document") == null,
                "未知或无反斜杠命令误匹配");
    }

    private void immutable(Runnable mutation) {
        try {
            mutation.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("目录暴露可变集合");
    }

    private static List<String> tail(String[] row, int offset) {
        return Arrays.asList(Arrays.copyOfRange(row, offset, row.length));
    }

    // 独立的正则解码用于核对完整资源，生产解码使用逐字符状态机；已知码点/宏期望在上面独立给出。
    private static final Pattern ESCAPE = Pattern.compile("\\\\([\\\\tnr])");

    private static String decode(String field) {
        if ("\\N".equals(field)) return null;
        Matcher matcher = ESCAPE.matcher(field);
        StringBuffer text = new StringBuffer();
        while (matcher.find()) {
            String c = matcher.group(1);
            String replacement =
                    "t".equals(c) ? "\t" : "n".equals(c) ? "\n" : "r".equals(c) ? "\r" : "\\";
            matcher.appendReplacement(text, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(text);
        return text.toString();
    }

    private static List<String[]> rows(String name) throws Exception {
        InputStream stream =
                MathCatalogScenario.class.getResourceAsStream(
                        "/assets/qz_uilib/math/catalog/" + name);
        require(stream != null, "测试缺少正式资源: " + name);
        List<String[]> rows = new ArrayList<String[]>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] fields = line.split("\t", -1);
                for (int i = 0; i < fields.length; i++) fields[i] = decode(fields[i]);
                rows.add(fields);
            }
        }
        return rows;
    }
}
