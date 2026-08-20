package club.heiqi.uilib.font.latex;

import java.util.HashMap;
import java.util.Map;

import club.heiqi.uilib.font.latex.node.LatexAtom.AtomClass;
import club.heiqi.uilib.font.latex.node.LatexMatrix.Fence;

/**
 * LaTeX 数学命令静态表（MVP 子集，规划附录 A 的单一来源）。
 *
 * <p>符号以 Unicode 码点存储、查询时解码为文本，源文件零非 ASCII 依赖；
 * 仅提供查询，不做解析；未知命令由解析器宽容处理（字面保留）。</p>
 */
public final class LatexSymbols {

    private LatexSymbols() {
    }

    /** 符号命令 → Unicode 码点。 */
    private static final Map<String, Integer> SYMBOL_COMMANDS = createSymbolCommands();

    /** 符号命令 → 原子类别（未列出的符号命令为 ORD）。 */
    private static final Map<String, AtomClass> COMMAND_CLASSES = createCommandClasses();

    /** 大运算符（\sum \int 等，排版时上下限上下堆叠）。 */
    private static final Map<String, Boolean> BIG_OPERATORS = createBigOperators();

    /** 函数名（正体文本算子，如 \sin \log）。 */
    private static final Map<String, Boolean> FUNCTION_NAMES = createFunctionNames();

    /** 间距命令 → em × 18（\quad=18，\,=3，\!=-3）。 */
    private static final Map<String, Integer> SPACE_COMMANDS = createSpaceCommands();

    /** 重音命令 → 组合变音符码点。 */
    private static final Map<String, Integer> ACCENT_COMMANDS = createAccentCommands();

    /** 矩阵环境名 → 外围定界。 */
    private static final Map<String, Fence> MATRIX_FENCES = createMatrixFences();

    /** \text 内转义命令 → 字面字符码点。 */
    private static final Map<String, Integer> TEXT_ESCAPES = createTextEscapes();

    // ==================== 查询 ====================

    /** @return 命令名是否为已知符号命令 */
    public static boolean isSymbolCommand(String name) {
        return SYMBOL_COMMANDS.containsKey(name);
    }

    /** @return 符号命令的显示文本；未知返回 null */
    public static String symbolText(String name) {
        Integer cp = SYMBOL_COMMANDS.get(name);
        return cp == null ? null : textOf(cp.intValue());
    }

    /** @return 符号命令的原子类别；未显式分类的符号命令为 ORD */
    public static AtomClass atomClassOf(String name) {
        AtomClass cls = COMMAND_CLASSES.get(name);
        return cls == null ? AtomClass.ORD : cls;
    }

    /** @return 是否大运算符（上下限堆叠） */
    public static boolean isBigOperator(String name) {
        return Boolean.TRUE.equals(BIG_OPERATORS.get(name));
    }

    /** @return 是否函数名（正体文本算子） */
    public static boolean isFunctionName(String name) {
        return Boolean.TRUE.equals(FUNCTION_NAMES.get(name));
    }

    /** @return 间距命令宽度（em × 18）；非间距命令返回 null */
    public static Integer spaceEm18(String name) {
        return SPACE_COMMANDS.get(name);
    }

    /** @return 重音命令的组合变音符文本；非重音命令返回 null */
    public static String accentText(String name) {
        Integer cp = ACCENT_COMMANDS.get(name);
        return cp == null ? null : textOf(cp.intValue());
    }

    /** @return 矩阵环境外围定界；未知环境返回 null */
    public static Fence matrixFence(String environmentName) {
        return MATRIX_FENCES.get(environmentName);
    }

    /** @return \text 内转义字面字符；非转义返回 null */
    public static String textEscape(String name) {
        Integer cp = TEXT_ESCAPES.get(name);
        return cp == null ? null : textOf(cp.intValue());
    }

    // ==================== 表构造 ====================

    private static Map<String, Integer> createSymbolCommands() {
        Map<String, Integer> map = new HashMap<String, Integer>();
            map.put("alpha", 945);
            map.put("beta", 946);
            map.put("gamma", 947);
            map.put("delta", 948);
            map.put("epsilon", 1013);
            map.put("varepsilon", 949);
            map.put("zeta", 950);
            map.put("eta", 951);
            map.put("theta", 952);
            map.put("vartheta", 977);
            map.put("iota", 953);
            map.put("kappa", 954);
            map.put("lambda", 955);
            map.put("mu", 956);
            map.put("nu", 957);
            map.put("xi", 958);
            map.put("pi", 960);
            map.put("varpi", 982);
            map.put("rho", 961);
            map.put("varrho", 1009);
            map.put("sigma", 963);
            map.put("varsigma", 962);
            map.put("tau", 964);
            map.put("upsilon", 965);
            map.put("phi", 981);
            map.put("varphi", 966);
            map.put("chi", 967);
            map.put("psi", 968);
            map.put("omega", 969);
            map.put("Gamma", 915);
            map.put("Delta", 916);
            map.put("Theta", 920);
            map.put("Lambda", 923);
            map.put("Xi", 926);
            map.put("Pi", 928);
            map.put("Sigma", 931);
            map.put("Upsilon", 933);
            map.put("Phi", 934);
            map.put("Psi", 936);
            map.put("Omega", 937);
            map.put("pm", 177);
            map.put("mp", 8723);
            map.put("times", 215);
            map.put("div", 247);
            map.put("cdot", 8901);
            map.put("cup", 8746);
            map.put("cap", 8745);
            map.put("wedge", 8743);
            map.put("vee", 8744);
            map.put("oplus", 8853);
            map.put("otimes", 8855);
            map.put("bullet", 8729);
            map.put("circ", 8728);
            map.put("leq", 8804);
            map.put("geq", 8805);
            map.put("neq", 8800);
            map.put("approx", 8776);
            map.put("equiv", 8801);
            map.put("sim", 8764);
            map.put("propto", 8733);
            map.put("in", 8712);
            map.put("notin", 8713);
            map.put("subset", 8834);
            map.put("subseteq", 8838);
            map.put("to", 8594);
            map.put("rightarrow", 8594);
            map.put("leftarrow", 8592);
            map.put("perp", 8869);
            map.put("parallel", 8741);
            map.put("infty", 8734);
            map.put("partial", 8706);
            map.put("nabla", 8711);
            map.put("forall", 8704);
            map.put("exists", 8707);
            map.put("neg", 172);
            map.put("prime", 8242);
            map.put("emptyset", 8709);
            map.put("angle", 8736);
            map.put("star", 8902);
            map.put("ell", 8467);
            map.put("hbar", 8463);
            map.put("Re", 8476);
            map.put("Im", 8465);
            map.put("dots", 8230);
            map.put("cdots", 8943);
            map.put("lbrace", 123);
            map.put("rbrace", 125);
            map.put("langle", 10216);
            map.put("rangle", 10217);
            map.put("vert", 124);
            map.put("Vert", 8214);
            map.put("sum", 8721);
            map.put("prod", 8719);
            map.put("int", 8747);
            map.put("iint", 8748);
            map.put("oint", 8750);
        return map;
    }

    private static Map<String, AtomClass> createCommandClasses() {
        Map<String, AtomClass> map = new HashMap<String, AtomClass>();
            map.put("pm", AtomClass.BIN);
            map.put("mp", AtomClass.BIN);
            map.put("times", AtomClass.BIN);
            map.put("div", AtomClass.BIN);
            map.put("cdot", AtomClass.BIN);
            map.put("cup", AtomClass.BIN);
            map.put("cap", AtomClass.BIN);
            map.put("wedge", AtomClass.BIN);
            map.put("vee", AtomClass.BIN);
            map.put("oplus", AtomClass.BIN);
            map.put("otimes", AtomClass.BIN);
            map.put("bullet", AtomClass.BIN);
            map.put("circ", AtomClass.BIN);
            map.put("leq", AtomClass.REL);
            map.put("geq", AtomClass.REL);
            map.put("neq", AtomClass.REL);
            map.put("approx", AtomClass.REL);
            map.put("equiv", AtomClass.REL);
            map.put("sim", AtomClass.REL);
            map.put("propto", AtomClass.REL);
            map.put("in", AtomClass.REL);
            map.put("notin", AtomClass.REL);
            map.put("subset", AtomClass.REL);
            map.put("subseteq", AtomClass.REL);
            map.put("to", AtomClass.REL);
            map.put("rightarrow", AtomClass.REL);
            map.put("leftarrow", AtomClass.REL);
            map.put("perp", AtomClass.REL);
            map.put("parallel", AtomClass.REL);
            map.put("lbrace", AtomClass.OPEN);
            map.put("rbrace", AtomClass.CLOSE);
            map.put("langle", AtomClass.OPEN);
            map.put("rangle", AtomClass.CLOSE);
            map.put("vert", AtomClass.ORD);
            map.put("Vert", AtomClass.ORD);
            map.put("dots", AtomClass.INNER);
            map.put("cdots", AtomClass.INNER);
            map.put("sum", AtomClass.OP);
            map.put("prod", AtomClass.OP);
            map.put("int", AtomClass.OP);
            map.put("iint", AtomClass.OP);
            map.put("oint", AtomClass.OP);
        return map;
    }

    private static Map<String, Boolean> createBigOperators() {
        Map<String, Boolean> map = new HashMap<String, Boolean>();
            map.put("sum", Boolean.TRUE);
            map.put("prod", Boolean.TRUE);
            map.put("int", Boolean.TRUE);
            map.put("iint", Boolean.TRUE);
            map.put("oint", Boolean.TRUE);
            map.put("lim", Boolean.TRUE);
        return map;
    }

    private static Map<String, Boolean> createFunctionNames() {
        Map<String, Boolean> map = new HashMap<String, Boolean>();
            map.put("lim", Boolean.TRUE);
            map.put("log", Boolean.TRUE);
            map.put("ln", Boolean.TRUE);
            map.put("sin", Boolean.TRUE);
            map.put("cos", Boolean.TRUE);
            map.put("tan", Boolean.TRUE);
            map.put("min", Boolean.TRUE);
            map.put("max", Boolean.TRUE);
            map.put("det", Boolean.TRUE);
            map.put("gcd", Boolean.TRUE);
            map.put("mod", Boolean.TRUE);
            map.put("exp", Boolean.TRUE);
            map.put("arg", Boolean.TRUE);
        return map;
    }

    private static Map<String, Integer> createSpaceCommands() {
        Map<String, Integer> map = new HashMap<String, Integer>();
            map.put(",", 3);
            map.put(":", 4);
            map.put(";", 5);
            map.put("!", -3);
            map.put("quad", 18);
            map.put("qquad", 36);
            map.put(" ", 3);
        return map;
    }

    private static Map<String, Integer> createAccentCommands() {
        Map<String, Integer> map = new HashMap<String, Integer>();
            map.put("hat", 770);
            map.put("bar", 772);
            map.put("vec", 8407);
            map.put("dot", 775);
            map.put("ddot", 776);
            map.put("tilde", 771);
        return map;
    }

    private static Map<String, Fence> createMatrixFences() {
        Map<String, Fence> map = new HashMap<String, Fence>();
            map.put("matrix", Fence.NONE);
            map.put("pmatrix", Fence.PAREN);
            map.put("bmatrix", Fence.BRACKET);
            map.put("vmatrix", Fence.BAR);
            map.put("cases", Fence.CASES);
        return map;
    }

    private static Map<String, Integer> createTextEscapes() {
        Map<String, Integer> map = new HashMap<String, Integer>();
            map.put("{", 123);
            map.put("}", 125);
            map.put("%", 37);
            map.put("#", 35);
            map.put("$", 36);
            map.put("&", 38);
            map.put("_", 95);
            map.put("+", 43);
        return map;
    }

    // ==================== 工具 ====================

    /** 码点 → 显示文本（运行期解码，不依赖源文件编码）。 */
    private static String textOf(int codepoint) {
        return new String(Character.toChars(codepoint));
    }
}
