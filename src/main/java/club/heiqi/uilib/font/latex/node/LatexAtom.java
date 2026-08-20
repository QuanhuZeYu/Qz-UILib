package club.heiqi.uilib.font.latex.node;

import club.heiqi.uilib.font.latex.LatexNode;

/**
 * 数学原子：一段不可再分的显示文本（单个字符、符号命令映射结果、函数名或 \text 内容）。
 */
public final class LatexAtom extends LatexNode {

    /**
     * TeX 8 类原子（简化）：决定数学间距与算子排版。
     */
    public enum AtomClass {
        /** 普通（Ord）：字母、数字、普通符号。 */
        ORD,
        /** 算子（Op）：\sum \int \prod \lim 与 \sin \log 等函数名（正体）。 */
        OP,
        /** 二元运算符（Bin）：两侧中距。 */
        BIN,
        /** 关系符（Rel）：两侧粗距。 */
        REL,
        /** 开括号（Open）。 */
        OPEN,
        /** 闭括号（Close）。 */
        CLOSE,
        /** 标点（Punct）。 */
        PUNCT,
        /** 内部（Inner）：\dots \cdots 等。 */
        INNER,
        /** 正体文本（\text{...} 内容）。 */
        TEXT,
    }

    /**
     * 算子排版模式：决定上下标是侧挂还是上下堆叠、大运算符符号是否按数学轴居中。
     * （TeX SCRIPT_NORMAL/SCRIPT_LIMITS 的 text 口径三分。）
     */
    public enum OperatorMode {
        /** 普通原子与函数名（\sin \log 等）：上下标侧挂。 */
        NONE,
        /** 大运算符符号（\sum \int \prod 等）：text 口径上下标侧挂 + 符号轴居中。 */
        BIG_OPERATOR,
        /** limits 算子（\lim \max \min 等）：上下限恒上下堆叠。 */
        LIMITS_OPERATOR,
    }

    private final String text;
    private final AtomClass atomClass;
    private final OperatorMode operatorMode;

    /**
     * 创建数学原子（普通原子/函数名）。
     *
     * @param text      显示文本（一个或多个码点；可含中文）
     * @param atomClass 原子类别
     */
    public LatexAtom(String text, AtomClass atomClass) {
        this(text, atomClass, OperatorMode.NONE);
    }

    /**
     * 创建数学原子。
     *
     * @param text         显示文本（一个或多个码点；可含中文）
     * @param atomClass    原子类别
     * @param operatorMode 算子排版模式（BIG_OPERATOR / LIMITS_OPERATOR / NONE）
     */
    public LatexAtom(String text, AtomClass atomClass, OperatorMode operatorMode) {
        super(Kind.ATOM);
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        if (atomClass == null) {
            throw new IllegalArgumentException("atomClass 不能为空");
        }
        this.text = text;
        this.atomClass = atomClass;
        this.operatorMode = operatorMode == null ? OperatorMode.NONE : operatorMode;
    }

    /** @return 显示文本 */
    public String getText() {
        return text;
    }

    /** @return 原子类别 */
    public AtomClass getAtomClass() {
        return atomClass;
    }

    /** @return 算子排版模式 */
    public OperatorMode getOperatorMode() {
        return operatorMode;
    }

    @Override
    public String toString() {
        return "Atom(" + text + "," + atomClass + ")";
    }
}
