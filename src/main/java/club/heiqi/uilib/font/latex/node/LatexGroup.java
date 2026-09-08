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
    private final boolean transparentForAtomClass;

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
        this(children, mathStyleOverride, false);
    }

    /** 透明参数组保留样式边界，仅原子类别沿唯一子节点查找。 */
    public LatexGroup(List<LatexNode> children, MathStyleOverride mathStyleOverride,
            boolean transparentForAtomClass) {
        super(Kind.GROUP, mathStyleOverride);
        if (transparentForAtomClass && (children == null || children.size() != 1)) {
            throw new IllegalArgumentException("透明参数组必须恰有一个子节点");
        }
        this.transparentForAtomClass = transparentForAtomClass;
        this.children = children == null || children.isEmpty()
                ? Collections.<LatexNode>emptyList()
                : Collections.unmodifiableList(new ArrayList<LatexNode>(children));
    }

    /** @return 是否沿唯一子节点确定原子类别 */
    public boolean isTransparentForAtomClass() {
        return transparentForAtomClass;
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
