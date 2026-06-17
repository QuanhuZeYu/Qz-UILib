package club.heiqi.uilib.ui.control;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.component.UiComponentRuntime;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 文档路径面包屑控件，支持点击任意路径段回跳。
 *
 * <p>使用响应式 keyed 列表（forEach）渲染路径段：path signal 变化时只增删移动变化段，
 * 稳定段复用 DOM 节点不重建（守 I5/I7）。</p>
 */
public final class DocumentBreadcrumbControl {

    private final UiDocument document;
    private final UiComponentRuntime runtime;
    private final ElementNode element;
    /** 影子字段，同步语义：setPath 同时写入此字段与 pathSignal，getPath 返回此字段。 */
    private String path = "";
    /** 路径 signal：驱动 segmentsSignal 派生与 forEach 列表协调。 */
    private final Signal<String> pathSignal = Signal.create("");
    /** 路径段派生列表：由 pathSignal 派生，作为 forEach 数据源。 */
    private final Computed<List<Segment>> segmentsSignal;
    private BreadcrumbSelectionHandler selectionHandler;

    /**
     * 创建面包屑控件。
     *
     * @param document 所属 HTML-like 文档
     * @param runtime  组件运行时，用于 forEach 响应式列表渲染与 onAction 事件桥接
     */
    public DocumentBreadcrumbControl(UiDocument document, UiComponentRuntime runtime) {
        this.document = document;
        this.runtime = runtime;
        this.element = document.div();
        // pathSignal 必须先于 segmentsSignal 初始化（后者依赖前者）
        this.segmentsSignal = Computed.create(() -> buildSegments(pathSignal.get()));
        configureElement();
        // 挂载响应式 keyed 列表（构造器内一次，不再重建）
        runtime.forEach(element, segmentsSignal,
                segment -> segment.path,
                (doc, segment) -> renderSegment(doc, segment));
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
     * 设置当前路径（同步写影子字段 + set signal）。
     *
     * @param path 配置路径
     * @return 当前控件
     */
    public DocumentBreadcrumbControl setPath(String path) {
        String normalized = normalizePath(path);
        this.path = normalized;
        pathSignal.set(normalized);
        return this;
    }

    /**
     * 返回当前路径（读影子字段，保证同帧 setPath→getPath 立即可见）。
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

    /**
     * 渲染单个路径段为响应式列表项（forEach itemComponent 回调）。
     *
     * @param doc     所属文档（forEach 回调签名要求）
     * @param segment 路径段数据
     * @return 路径段 wrapper 节点（挂 data-breadcrumb-segment 属性）
     */
    private ElementNode renderSegment(UiDocument doc, Segment segment) {
        ElementNode wrapper = doc.div();
        wrapper.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(6));
        wrapper.setAttribute("data-breadcrumb-segment", segment.path);

        // 非根段（path 非空）前置 ">" 分隔符
        if (!segment.path.isEmpty()) {
            ElementNode separator = doc.span();
            separator.style().setTextColor(0xFF93C5FD);
            separator.appendText(">");
            wrapper.append(separator);
        }

        // 按钮：复制原样式，事件改用 ReactiveControlBindings.onAction
        DocumentButtonControl button = new DocumentButtonControl(doc, segment.label)
                .setBackgroundColors(0xFF1E293B, 0xFF334155, 0xFF1E293B)
                .setFocusBorderColor(0xFFBFDBFE)
                .setTextColors(0xFFE2E8F0, 0xFF64748B);
        button.getElement().style().setPadding(UiStyleLength.px(6));
        ReactiveControlBindings.onAction(runtime, button, () -> selectPath(segment.path));
        wrapper.append(button.getElement());

        return wrapper;
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
