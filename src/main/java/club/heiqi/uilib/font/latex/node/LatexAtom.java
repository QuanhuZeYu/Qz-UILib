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

    private final String text;
    private final AtomClass atomClass;

    /**
     * 创建数学原子。
     *
     * @param text      显示文本（一个或多个码点；可含中文）
     * @param atomClass 原子类别
     */
    public LatexAtom(String text, AtomClass atomClass) {
        super(Kind.ATOM);
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        if (atomClass == null) {
            throw new IllegalArgumentException("atomClass 不能为空");
        }
        this.text = text;
        this.atomClass = atomClass;
    }

    /** @return 显示文本 */
    public String getText() {
        return text;
    }

    /** @return 原子类别 */
    public AtomClass getAtomClass() {
        return atomClass;
    }

    @Override
    public String toString() {
        return "Atom(" + text + "," + atomClass + ")";
    }
}
