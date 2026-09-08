package club.heiqi.uilib.font.latex.node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.MathStyleOverride;

/**
 * 花括号分组节点 {@code {...}}：子节点列表（可为空）。
 */
public final class LatexGroup extends LatexNode {

    private final List<LatexNode> children;

    /**
     * 创建分组节点（防御性拷贝）。
     *
     * @param children 子节点列表（可为空）
     */
    public LatexGroup(List<LatexNode> children) {
        this(children, MathStyleOverride.INHERIT);
    }

    /** 创建带局部数学样式声明的节点；样式不得为 null。 */
    public LatexGroup(List<LatexNode> children,
            MathStyleOverride mathStyleOverride) {
        super(Kind.GROUP, mathStyleOverride);
        this.children = children == null || children.isEmpty()
                ? Collections.<LatexNode>emptyList()
                : Collections.unmodifiableList(new ArrayList<LatexNode>(children));
    }

    /** @return 子节点列表（不可变） */
    public List<LatexNode> getChildren() {
        return children;
    }

    @Override
    public String toString() {
        return "Group(" + children + ")";
    }
}
