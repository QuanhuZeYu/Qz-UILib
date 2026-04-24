package club.heiqi.uilib.ui.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * HTML-like 文档树基础节点。
 *
 * <p>节点只承载稳定树关系，不直接感知底层 Widget、布局或 OpenGL 渲染状态。</p>
 */
public abstract class DocumentNode {

    private final UiDocument ownerDocument;
    private final List<DocumentNode> children = new ArrayList<DocumentNode>();
    private DocumentNode parent;

    protected DocumentNode(UiDocument ownerDocument) {
        this.ownerDocument = Objects.requireNonNull(ownerDocument, "ownerDocument");
    }

    /**
     * 返回当前节点类型。
     *
     * @return 节点类型
     */
    public abstract DocumentNodeType getNodeType();

    /**
     * 返回节点所属文档。
     *
     * @return 所属文档
     */
    public final UiDocument getOwnerDocument() {
        return ownerDocument;
    }

    /**
     * 返回父节点。
     *
     * @return 父节点；根节点返回 null
     */
    public final DocumentNode getParent() {
        return parent;
    }

    /**
     * 返回只读子节点列表。
     *
     * @return 子节点列表
     */
    public final List<DocumentNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * 返回子节点数量。
     *
     * @return 子节点数量
     */
    public final int getChildCount() {
        return children.size();
    }

    /**
     * 返回第一个子节点。
     *
     * @return 第一个子节点；没有子节点时返回 null
     */
    public final DocumentNode getFirstChild() {
        return children.isEmpty() ? null : children.get(0);
    }

    /**
     * 返回最后一个子节点。
     *
     * @return 最后一个子节点；没有子节点时返回 null
     */
    public final DocumentNode getLastChild() {
        return children.isEmpty() ? null : children.get(children.size() - 1);
    }

    /**
     * 追加子节点。
     *
     * <p>同一文档内已有父节点的子节点会按 DOM appendChild 语义移动到新父节点末尾。</p>
     *
     * @param child 子节点
     * @return 当前节点
     */
    public DocumentNode appendChild(DocumentNode child) {
        if (!allowsChildren()) {
            throw new UnsupportedOperationException("This node type cannot contain children");
        }
        DocumentNode resolvedChild = Objects.requireNonNull(child, "child");
        validateAppendChild(resolvedChild);

        if (resolvedChild.parent == this && children.get(children.size() - 1) == resolvedChild) {
            return this;
        }
        if (resolvedChild.parent != null) {
            resolvedChild.parent.children.remove(resolvedChild);
        }
        resolvedChild.parent = this;
        children.add(resolvedChild);
        ownerDocument.recordMutation();
        return this;
    }

    /**
     * 移除直接子节点。
     *
     * @param child 子节点
     * @return 是否实际移除
     */
    public final boolean removeChild(DocumentNode child) {
        if (child == null || child.parent != this) {
            return false;
        }
        boolean removed = children.remove(child);
        if (removed) {
            child.parent = null;
            ownerDocument.recordMutation();
        }
        return removed;
    }

    /**
     * 清空全部直接子节点。
     */
    public final void clearChildren() {
        if (children.isEmpty()) {
            return;
        }
        for (DocumentNode child : children) {
            child.parent = null;
        }
        children.clear();
        ownerDocument.recordMutation();
    }

    /**
     * 判断当前节点是否允许拥有子节点。
     *
     * @return 是否允许子节点
     */
    protected boolean allowsChildren() {
        return true;
    }

    /**
     * 通知所属文档当前节点自身状态已变化。
     */
    protected final void markMutated() {
        ownerDocument.recordMutation();
    }

    private void validateAppendChild(DocumentNode child) {
        if (child == this) {
            throw new IllegalArgumentException("A node cannot be appended to itself");
        }
        if (child == ownerDocument.getRootElement()) {
            throw new IllegalArgumentException("Cannot append the document root element");
        }
        if (child.ownerDocument != ownerDocument) {
            throw new IllegalArgumentException("Cannot append a node from another UiDocument");
        }
        for (DocumentNode current = this; current != null; current = current.parent) {
            if (current == child) {
                throw new IllegalArgumentException("Cannot append an ancestor as a child");
            }
        }
    }
}
