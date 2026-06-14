package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 文档树形导航控件，支持节点折叠、展开和当前节点高亮。
 */
public final class DocumentTreeViewControl {

    private final UiDocument document;
    private final ElementNode element;
    private final ElementNode treeElement;
    private final List<TreeNode> nodes = new ArrayList<TreeNode>();
    private final Set<String> collapsedPaths = new HashSet<String>();
    private String currentPath = "";
    private TreeSelectionHandler selectionHandler;

    /**
     * 创建树形导航控件。
     *
     * @param document 所属 HTML-like 文档
     * @param nodes 根节点集合
     */
    public DocumentTreeViewControl(UiDocument document, List<TreeNode> nodes) {
        this.document = Objects.requireNonNull(document, "document");
        this.element = document.div();
        this.treeElement = document.div();
        configureRoot();
        element.append(treeElement);
        setNodes(nodes);
    }

    /**
     * 返回控件根元素。
     *
     * @return 根元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 替换树节点集合。
     *
     * @param nodes 根节点集合
     * @return 当前控件
     */
    public DocumentTreeViewControl setNodes(List<TreeNode> nodes) {
        this.nodes.clear();
        if (nodes != null) {
            this.nodes.addAll(nodes);
        }
        refresh();
        return this;
    }

    /**
     * 设置当前选中的配置路径。
     *
     * @param path 配置路径
     * @return 当前控件
     */
    public DocumentTreeViewControl setCurrentPath(String path) {
        this.currentPath = normalizePath(path);
        expandPath(this.currentPath);
        refresh();
        return this;
    }

    /**
     * 返回当前选中的配置路径。
     *
     * @return 配置路径
     */
    public String getCurrentPath() {
        return currentPath;
    }

    /**
     * 设置节点选择回调。
     *
     * @param selectionHandler 选择回调；为 null 时清除
     * @return 当前控件
     */
    public DocumentTreeViewControl setSelectionHandler(TreeSelectionHandler selectionHandler) {
        this.selectionHandler = selectionHandler;
        return this;
    }

    /**
     * 选择指定路径并触发回调。
     *
     * @param path 配置路径
     * @return 当前控件
     */
    public DocumentTreeViewControl selectPath(String path) {
        String resolvedPath = normalizePath(path);
        setCurrentPath(resolvedPath);
        if (selectionHandler != null) {
            selectionHandler.onTreePathSelected(resolvedPath);
        }
        return this;
    }

    /**
     * 折叠或展开指定节点。
     *
     * @param path 配置路径
     * @return 当前控件
     */
    public DocumentTreeViewControl toggleCollapsed(String path) {
        String resolvedPath = normalizePath(path);
        if (collapsedPaths.contains(resolvedPath)) {
            collapsedPaths.remove(resolvedPath);
        } else {
            collapsedPaths.add(resolvedPath);
        }
        refresh();
        return this;
    }

    /**
     * 展开指定节点及其父级路径。
     *
     * @param path 配置路径
     * @return 当前控件
     */
    public DocumentTreeViewControl expandPath(String path) {
        String resolvedPath = normalizePath(path);
        collapsedPaths.remove(resolvedPath);
        while (!resolvedPath.isEmpty()) {
            resolvedPath = parentPath(resolvedPath);
            collapsedPaths.remove(resolvedPath);
        }
        refresh();
        return this;
    }

    /**
     * 判断指定节点是否处于折叠状态。
     *
     * @param path 配置路径
     * @return 是否折叠
     */
    public boolean isCollapsed(String path) {
        return collapsedPaths.contains(normalizePath(path));
    }

    private void refresh() {
        treeElement.clearChildren();
        for (TreeNode node : nodes) {
            appendNode(node, 0);
        }
    }

    private void appendNode(final TreeNode node, int depth) {
        ElementNode row = document.div();
        boolean current = Objects.equals(currentPath, node.getPath());
        row.setAttribute("data-tree-node-path", node.getPath());
        row.setAttribute("data-tree-node-current", Boolean.toString(current));
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(6))
                .setPadding(UiStyleLength.px(5))
                .setBackgroundColor(current ? 0xFF1D4ED8 : 0xFF0F172A)
                .setBorderColor(current ? 0xFF93C5FD : 0xFF334155)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(8))
                .setTextColor(current ? 0xFFFFFFFF : 0xFFE2E8F0);

        DocumentButtonControl toggleButton = new DocumentButtonControl(document, resolveToggleLabel(node))
                .setBackgroundColors(0xFF1E293B, 0xFF334155, 0xFF1E293B)
                .setFocusBorderColor(0xFFBFDBFE)
                .setTextColors(0xFFE2E8F0, 0xFF64748B)
                .setEnabled(!node.getChildren().isEmpty())
                .setActionHandler(new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        toggleCollapsed(node.getPath());
                    }
                });
        toggleButton.getElement().setAttribute("data-tree-toggle-path", node.getPath());
        toggleButton.getElement().style().setPadding(UiStyleLength.px(4));
        row.append(toggleButton.getElement());

        DocumentButtonControl labelButton = new DocumentButtonControl(document, indentation(depth) + node.getLabel())
                .setBackgroundColors(current ? 0xFF1D4ED8 : 0xFF0F172A, 0xFF334155, 0xFF0F172A)
                .setFocusBorderColor(0xFFBFDBFE)
                .setTextColors(current ? 0xFFFFFFFF : 0xFFE2E8F0, 0xFF64748B)
                .setActionHandler(new DocumentButtonActionHandler() {
                    @Override
                    public void onAction(DocumentButtonActionEvent event) {
                        selectPath(node.getPath());
                    }
                });
        labelButton.getElement().style().setFlexGrow(1.0F).setPadding(UiStyleLength.px(5));
        row.append(labelButton.getElement());
        treeElement.append(row);

        if (!collapsedPaths.contains(node.getPath())) {
            for (TreeNode child : node.getChildren()) {
                appendNode(child, depth + 1);
            }
        }
    }

    private String resolveToggleLabel(TreeNode node) {
        if (node.getChildren().isEmpty()) {
            return " ";
        }
        return collapsedPaths.contains(node.getPath()) ? "+" : "-";
    }

    private void configureRoot() {
        element.setAttribute("data-document-control", "tree-view");
        element.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(6))
                .setWidth(UiStyleLength.percent(1.0F));
        treeElement.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(5))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO);
    }

    private static String indentation(int depth) {
        StringBuilder builder = new StringBuilder(depth * 2);
        for (int index = 0; index < depth; index++) {
            builder.append("  ");
        }
        return builder.toString();
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.trim();
    }

    private static String parentPath(String path) {
        String normalized = normalizePath(path);
        int dotIndex = normalized.lastIndexOf('.');
        return dotIndex <= 0 ? "" : normalized.substring(0, dotIndex);
    }

    /**
     * 树节点选择回调。
     */
    public interface TreeSelectionHandler {

        /**
         * 当树节点被选中时调用。
         *
         * @param path 被选中的配置路径；根路径为空字符串
         */
        void onTreePathSelected(String path);
    }

    /**
     * 树形导航节点模型。
     */
    public static final class TreeNode {

        private final String path;
        private final String label;
        private final List<TreeNode> children;

        /**
         * 创建树节点。
         *
         * @param path 配置路径；根路径为空字符串
         * @param label 展示标签
         * @param children 子节点集合
         */
        public TreeNode(String path, String label, List<TreeNode> children) {
            this.path = normalizePath(path);
            this.label = label == null || label.trim().isEmpty() ? this.path : label.trim();
            this.children = children == null ? Collections.<TreeNode>emptyList()
                    : Collections.unmodifiableList(new ArrayList<TreeNode>(children));
        }

        /**
         * 返回配置路径。
         *
         * @return 配置路径
         */
        public String getPath() {
            return path;
        }

        /**
         * 返回展示标签。
         *
         * @return 展示标签
         */
        public String getLabel() {
            return label;
        }

        /**
         * 返回子节点集合。
         *
         * @return 子节点集合
         */
        public List<TreeNode> getChildren() {
            return children;
        }
    }
}
