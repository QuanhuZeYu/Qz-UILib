package club.heiqi.uilib.ui.paint;

import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.render.UiRenderContext;

/**
 * 自定义渲染器回放期表面：聚合渲染上下文、命令固化的屏幕坐标内容盒，以及构建期固化的文档坐标边界/滚动态快照。
 *
 * <p>该表面是 {@link DocumentCustomRenderer#render(DocumentCustomRenderSurface)} 的唯一入参。回放期渲染器经
 * {@link #boundsOf(ElementNode)} / {@link #scrollLeftOf(ElementNode)} 读取边界与滚动偏移时，命中构建期固化的
 * {@link DocumentCustomRenderBounds} 快照，<strong>不再触发 {@code element.getDocumentBounds()} /
 * {@code element.getScrollLeft()} 全树推进动画时间线</strong>。</p>
 *
 * <p>当快照缺失时（如测试直接构造命令、或经旧 5 参 {@link DocumentCustomRenderer#render} 入口的兜底表面），
 * 边界/滚动查询回退到元素实时只读 API，保证行为正确。</p>
 *
 * @apiNote 与 {@link DocumentCustomRenderer} 同属宿主级逃生口，LTS 不承诺该渲染后端接口长期稳定。
 */
public final class DocumentCustomRenderSurface {

    private final UiRenderContext context;
    private final int contentLeft;
    private final int contentTop;
    private final int contentRight;
    private final int contentBottom;
    private final DocumentCustomRenderBounds bounds;

    private DocumentCustomRenderSurface(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
            int contentBottom, DocumentCustomRenderBounds bounds) {
        this.context = context;
        this.contentLeft = contentLeft;
        this.contentTop = contentTop;
        this.contentRight = contentRight;
        this.contentBottom = contentBottom;
        this.bounds = bounds;
    }

    /**
     * 创建固化边界表面，供绘制命令回放期使用。
     *
     * @param context 渲染上下文
     * @param contentLeft 元素内容区左边界（绝对屏幕坐标）
     * @param contentTop 元素内容区上边界（绝对屏幕坐标）
     * @param contentRight 元素内容区右边界（绝对屏幕坐标）
     * @param contentBottom 元素内容区下边界（绝对屏幕坐标）
     * @param bounds 构建期固化的文档坐标边界/滚动态快照；为 null 时回退实时查询
     * @return 表面实例
     */
    static DocumentCustomRenderSurface baked(UiRenderContext context, int contentLeft, int contentTop,
            int contentRight, int contentBottom, DocumentCustomRenderBounds bounds) {
        return new DocumentCustomRenderSurface(context, contentLeft, contentTop, contentRight, contentBottom, bounds);
    }

    /**
     * 创建实时查询表面，供旧 5 参 {@link DocumentCustomRenderer#render} 入口兜底委派使用。
     *
     * @param context 渲染上下文
     * @param contentLeft 元素内容区左边界（绝对屏幕坐标）
     * @param contentTop 元素内容区上边界（绝对屏幕坐标）
     * @param contentRight 元素内容区右边界（绝对屏幕坐标）
     * @param contentBottom 元素内容区下边界（绝对屏幕坐标）
     * @return 表面实例
     */
    public static DocumentCustomRenderSurface live(UiRenderContext context, int contentLeft, int contentTop,
            int contentRight, int contentBottom) {
        return new DocumentCustomRenderSurface(context, contentLeft, contentTop, contentRight, contentBottom, null);
    }

    public UiRenderContext getContext() {
        return context;
    }

    public int getContentLeft() {
        return contentLeft;
    }

    public int getContentTop() {
        return contentTop;
    }

    public int getContentRight() {
        return contentRight;
    }

    public int getContentBottom() {
        return contentBottom;
    }

    /**
     * 解析元素在文档局部坐标系下的布局边界。
     *
     * @param element 目标元素
     * @return 文档坐标布局边界；快照缺失时回退 {@link ElementNode#getDocumentBounds()}
     */
    public DocumentElementBounds boundsOf(ElementNode element) {
        if (bounds != null) {
            return bounds.boundsOf(element);
        }
        return element == null ? DocumentElementBounds.unavailable() : element.getDocumentBounds();
    }

    /**
     * 返回元素当前横向滚动偏移。
     *
     * @param element 目标元素
     * @return 横向滚动偏移；快照缺失时回退 {@link ElementNode#getScrollLeft()}
     */
    public int scrollLeftOf(ElementNode element) {
        if (bounds != null) {
            return bounds.scrollLeftOf(element);
        }
        return element == null ? 0 : element.getScrollLeft();
    }

    /**
     * 返回元素当前纵向滚动偏移。
     *
     * @param element 目标元素
     * @return 纵向滚动偏移；快照缺失时回退 {@link ElementNode#getScrollTop()}
     */
    public int scrollTopOf(ElementNode element) {
        if (bounds != null) {
            return bounds.scrollTopOf(element);
        }
        return element == null ? 0 : element.getScrollTop();
    }
}
