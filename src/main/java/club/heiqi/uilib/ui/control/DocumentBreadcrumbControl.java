package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 文档路径面包屑控件，支持点击任意路径段回跳。
 */
public final class DocumentBreadcrumbControl {

    private final UiDocument document;
    private final ElementNode element;
    private String path = "";
    private BreadcrumbSelectionHandler selectionHandler;

    /**
     * 创建面包屑控件。
     *
     * @param document 所属 HTML-like 文档
     */
    public DocumentBreadcrumbControl(UiDocument document) {
        this.document = document;
        this.element = document.div();
        configureElement();
        setPath("");
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
     * 设置当前路径。
     *
     * @param path 配置路径
     * @return 当前控件
     */
    public DocumentBreadcrumbControl setPath(String path) {
        this.path = normalizePath(path);
        refresh();
        return this;
    }

    /**
     * 返回当前路径。
     *
     * @return 配置路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 设置路径段选择回调。
     *
     * @param selectionHandler 选择回调；为 null 时清除
     * @return 当前控件
     */
    public DocumentBreadcrumbControl setSelectionHandler(BreadcrumbSelectionHandler selectionHandler) {
        this.selectionHandler = selectionHandler;
        return this;
    }

    /**
     * 选择指定路径并触发回调。
     *
     * @param path 配置路径
     * @return 当前控件
     */
    public DocumentBreadcrumbControl selectPath(String path) {
        String resolvedPath = normalizePath(path);
        setPath(resolvedPath);
        if (selectionHandler != null) {
            selectionHandler.onBreadcrumbPathSelected(resolvedPath);
        }
        return this;
    }

    private void refresh() {
        element.clearChildren();
        List<Segment> segments = buildSegments(path);
        for (int index = 0; index < segments.size(); index++) {
            final Segment segment = segments.get(index);
            DocumentButtonControl button = new DocumentButtonControl(document, segment.label)
                    .setBackgroundColors(0xFF1E293B, 0xFF334155, 0xFF1E293B)
                    .setFocusBorderColor(0xFFBFDBFE)
                    .setTextColors(0xFFE2E8F0, 0xFF64748B)
                    .setActionHandler(new DocumentButtonActionHandler() {
                        @Override
                        public void onAction(DocumentButtonActionEvent event) {
                            selectPath(segment.path);
                        }
                    });
            button.getElement().setAttribute("data-breadcrumb-segment", segment.path);
            button.getElement().style().setPadding(UiStyleLength.px(6));
            element.append(button.getElement());
            if (index + 1 < segments.size()) {
                ElementNode separator = document.span();
                separator.style().setTextColor(0xFF93C5FD);
                separator.appendText(">");
                element.append(separator);
            }
        }
    }

    private void configureElement() {
        element.setAttribute("data-document-control", "breadcrumb");
        element.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(6))
                .setRowGap(UiStyleLength.px(6))
                .setWidth(UiStyleLength.percent(1.0F));
    }

    private static List<Segment> buildSegments(String path) {
        List<Segment> segments = new ArrayList<Segment>();
        segments.add(new Segment("", "根配置"));
        String normalized = normalizePath(path);
        if (normalized.isEmpty()) {
            return segments;
        }
        StringBuilder current = new StringBuilder();
        String[] parts = normalized.split("\\.");
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (current.length() > 0) {
                current.append('.');
            }
            current.append(part);
            segments.add(new Segment(current.toString(), part));
        }
        return segments;
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.trim();
    }

    /**
     * 面包屑路径段选择回调。
     */
    public interface BreadcrumbSelectionHandler {

        /**
         * 当路径段被选中时调用。
         *
         * @param path 被选中的配置路径；根路径为空字符串
         */
        void onBreadcrumbPathSelected(String path);
    }

    private static final class Segment {

        private final String path;
        private final String label;

        private Segment(String path, String label) {
            this.path = normalizePath(path);
            this.label = label == null || label.trim().isEmpty() ? "根配置" : label.trim();
        }
    }
}
