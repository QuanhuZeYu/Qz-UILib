package club.heiqi.uilib.ui.scene.text.math;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 固定参照的不可变事实目录，供自有解析/宏能力消费；登记不代表生产支持或视觉验收。 数据和原始 MIT 许可位于 assets/qz_uilib/math/catalog；参照 renderer
 * 只存在于测试资料。 字符串宏保留完整源码，函数宏只保留名称、kind 与来源，不执行或携带 JavaScript 算法。
 */
public final class MathCommandCatalog {
    private MathCommandCatalog() {}

    public static List<Function> functions() {
        return Commands.FUNCTIONS;
    }

    public static List<Environment> environments() {
        return Commands.ENVIRONMENTS;
    }

    public static List<Macro> macros() {
        return Commands.MACROS;
    }

    /** 查询使用原始 token（含反斜杠）；未知名称返回 null。 */
    public static Function findFunction(String name) {
        return Commands.FUNCTION_INDEX.get(name);
    }

    public static Environment findEnvironment(String name) {
        return Commands.ENVIRONMENT_INDEX.get(name);
    }

    public static Macro findMacro(String name) {
        return Commands.MACRO_INDEX.get(name);
    }

    /** ^、_、\limits、\nolimits 等上下文 token，保留原始名称。 */
    public static Set<String> implicitCommands() {
        return Commands.IMPLICIT;
    }

    /** 固定版本/commit/hash 与 default.* 配置事实；不可变，布局不得从此推断生产能力。 */
    public static Map<String, String> reference() {
        return Reference.VALUES;
    }

    /** 各目录和两个 mode 合并后的全部反斜杠命令 token；别名和内部名称均不删减。 */
    public static Set<String> commandTokens() {
        return Tokens.ALL;
    }

    public static final class Source {
        public final String referenceVersion, commit, path, url;

        /** 固定 bundle 的登记行号；不是 path 所指源码的行号。 */
        public final int distLine;

        Source(String path, int distLine, String url) {
            this.referenceVersion = reference().get("katex");
            this.commit = reference().get("commit");
            this.path = path;
            this.distLine = distLine;
            this.url = url;
        }
    }

    public static final class Function {
        public final String name, type, visibility;
        public final int numArgs, numOptionalArgs;
        public final boolean allowedInArgument, allowedInText, allowedInMath, infix, primitive;

        /** 空列表与未指定类型由 argTypesSpecified 区分，不能丢失 primitive/raw 等参数来源。 */
        public final boolean argTypesSpecified;

        public final List<String> argTypes;
        public final Source source;

        private Function(String[] row) {
            columns(row, 15);
            name = required(row[0]);
            type = required(row[1]);
            numArgs = integer(row[2]);
            numOptionalArgs = integer(row[3]);
            allowedInArgument = bool(row[4]);
            allowedInText = bool(row[5]);
            allowedInMath = bool(row[6]);
            infix = bool(row[7]);
            primitive = bool(row[8]);
            visibility = required(row[9]);
            source = source(row, 10);
            argTypesSpecified = bool(row[13]);
            argTypes = arguments(row, 14);
        }
    }

    public static final class Environment {
        public final String name, type;
        public final int numArgs, numOptionalArgs;
        public final boolean allowedInText, argTypesSpecified;
        public final List<String> argTypes;
        public final Source source;

        private Environment(String[] row) {
            columns(row, 10);
            name = required(row[0]);
            type = required(row[1]);
            numArgs = integer(row[2]);
            numOptionalArgs = integer(row[3]);
            allowedInText = bool(row[4]);
            source = source(row, 5);
            argTypesSpecified = bool(row[8]);
            argTypes = arguments(row, 9);
        }
    }

    public static final class Macro {
        public final String name, kind, visibility;

        /** string 宏是完整 expansion（包括 # 参数、空白与换行）；function 宏为 null。 */
        public final String expansion;

        /** 保留 descriptor 接缝；此目录不携带 JS 实现，始终为 null，源实现定位见 source。 */
        public final String implementation;

        public final Source source;

        private Macro(String[] row) {
            exactColumns(row, 7);
            name = required(row[0]);
            kind = required(row[1]);
            expansion = row[2];
            visibility = required(row[3]);
            source = source(row, 4);
            implementation = null;
            if (!("string".equals(kind) && expansion != null
                    || "function".equals(kind) && expansion == null))
                throw new IllegalStateException("宏kind/payload不一致: " + name);
        }
    }

    private static final class Reference {
        static final Map<String, String> VALUES = readReference();

        private static Map<String, String> readReference() {
            Map<String, String> values = new LinkedHashMap<String, String>();
            for (String[] row : readFacts("reference.tsv")) {
                exactColumns(row, 2);
                if (values.put(required(row[0]), required(row[1])) != null)
                    throw new IllegalStateException("重复参照字段: " + row[0]);
            }
            return Collections.unmodifiableMap(values);
        }
    }

    private static final class Commands {
        static final Map<String, Function> FUNCTION_INDEX = readFunctions();
        static final List<Function> FUNCTIONS = immutableValues(FUNCTION_INDEX);
        static final Map<String, Environment> ENVIRONMENT_INDEX = readEnvironments();
        static final List<Environment> ENVIRONMENTS = immutableValues(ENVIRONMENT_INDEX);
        static final Map<String, Macro> MACRO_INDEX = readMacros();
        static final List<Macro> MACROS = immutableValues(MACRO_INDEX);
        static final Set<String> IMPLICIT = readImplicit();

        private static Map<String, Function> readFunctions() {
            Map<String, Function> values = new LinkedHashMap<String, Function>();
            for (String[] row : readFacts("functions.tsv")) {
                Function f = new Function(row);
                put(values, f.name, f);
            }
            return Collections.unmodifiableMap(values);
        }

        private static Map<String, Environment> readEnvironments() {
            Map<String, Environment> values = new LinkedHashMap<String, Environment>();
            for (String[] row : readFacts("environments.tsv")) {
                Environment e = new Environment(row);
                put(values, e.name, e);
            }
            return Collections.unmodifiableMap(values);
        }

        private static Map<String, Macro> readMacros() {
            Map<String, Macro> values = new LinkedHashMap<String, Macro>();
            for (String[] row : readFacts("macros.tsv")) {
                Macro m = new Macro(row);
                put(values, m.name, m);
            }
            return Collections.unmodifiableMap(values);
        }

        private static Set<String> readImplicit() {
            Set<String> values = new LinkedHashSet<String>();
            for (String[] row : readFacts("implicit.tsv")) {
                exactColumns(row, 1);
                if (!values.add(required(row[0])))
                    throw new IllegalStateException("重复隐式token: " + row[0]);
            }
            return Collections.unmodifiableSet(values);
        }
    }

    private static final class Tokens {
        static final Set<String> ALL = collect();

        private static Set<String> collect() {
            Set<String> tokens = new LinkedHashSet<String>();
            for (Function f : functions()) add(tokens, f.name);
            for (Macro m : macros()) add(tokens, m.name);
            for (MathSymbols.Entry e : MathSymbols.entries()) add(tokens, e.token);
            for (String token : implicitCommands()) add(tokens, token);
            return Collections.unmodifiableSet(tokens);
        }

        private static void add(Set<String> tokens, String token) {
            if (token.startsWith("\\")) tokens.add(token);
        }
    }

    // 同包符号目录复用此 UTF-8 TSV 解码；字段不 trim，反斜杠空格与宏空白具有语义。
    static List<String[]> readFacts(String file) {
        String path = "/assets/qz_uilib/math/catalog/" + file;
        InputStream stream = MathCommandCatalog.class.getResourceAsStream(path);
        if (stream == null) throw new IllegalStateException("缺少数学事实资源: " + path);
        List<String[]> rows = new ArrayList<String[]>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            int number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] row = line.split("\t", -1);
                for (int i = 0; i < row.length; i++) row[i] = decode(row[i], path, number);
                rows.add(row);
            }
        } catch (IOException error) {
            throw new IllegalStateException("读取数学事实失败: " + path, error);
        }
        return rows;
    }

    private static String decode(String field, String path, int line) {
        if ("\\N".equals(field)) return null;
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (c != '\\') {
                value.append(c);
                continue;
            }
            if (++i == field.length())
                throw new IllegalStateException("不完整TSV转义: " + path + ":" + line);
            switch (field.charAt(i)) {
                case '\\':
                    value.append('\\');
                    break;
                case 't':
                    value.append('\t');
                    break;
                case 'n':
                    value.append('\n');
                    break;
                case 'r':
                    value.append('\r');
                    break;
                default:
                    throw new IllegalStateException("未知TSV转义: " + path + ":" + line);
            }
        }
        return value.toString();
    }

    static Source source(String[] row, int offset) {
        return new Source(
                required(row[offset]), integer(row[offset + 1]), required(row[offset + 2]));
    }

    static String required(String value) {
        if (value == null) throw new IllegalStateException("数学事实必填字段为null");
        return value;
    }

    private static int integer(String value) {
        int number = Integer.parseInt(required(value));
        if (number < 0) throw new IllegalStateException("数学事实计数为负: " + value);
        return number;
    }

    private static boolean bool(String value) {
        if (!"true".equals(value) && !"false".equals(value))
            throw new IllegalStateException("数学事实boolean无效: " + value);
        return "true".equals(value);
    }

    static void exactColumns(String[] row, int count) {
        if (row.length != count)
            throw new IllegalStateException("数学事实列数不符: " + row.length + " / " + count);
    }

    private static void columns(String[] row, int min) {
        if (row.length < min)
            throw new IllegalStateException("数学事实列不足: " + row.length + " / " + min);
    }

    private static List<String> arguments(String[] row, int countOffset) {
        int count = integer(row[countOffset]);
        exactColumns(row, countOffset + 1 + count);
        List<String> values = new ArrayList<String>();
        for (int i = 0; i < count; i++) values.add(required(row[countOffset + 1 + i]));
        return Collections.unmodifiableList(values);
    }

    private static <T> List<T> immutableValues(Map<String, T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values.values()));
    }

    private static <T> void put(Map<String, T> values, String name, T value) {
        if (values.put(name, value) != null) throw new IllegalStateException("重复数学事实: " + name);
    }
}
