package club.heiqi.uilib.ui.dom.control;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.style.UiDisplay;
import club.heiqi.uilib.ui.style.UiOverflow;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * 基于 HTML-like 元素实现的宿主图片控件。
 *
 * <p>该控件默认表现为纯装饰 `img`：只负责在元素内容区绘制宿主贴图，不拦截命中与焦点。
 * 需要点击、拖拽等交互时，建议把它挂到现有按钮、卡片或绝对定位容器中，由外层 HTML-like 元素负责交互。</p>
 */
public final class DocumentHostImageControl {

    private final ElementNode element;
    private HostImageSource source;
    private int sourceRevision;

    /**
     * 创建宿主图片控件。
     *
     * @param document 所属文档
     * @param source 初始图片源
     */
    public DocumentHostImageControl(UiDocument document, HostImageSource source) {
        this.element = Objects.requireNonNull(document, "document").element("img");
        this.source = Objects.requireNonNull(source, "source");
        configureElement();
        installRenderer();
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
     * 返回当前图片源。
     *
     * @return 当前图片源
     */
    public HostImageSource getSource() {
        return source;
    }

    /**
     * 设置图片源。
     *
     * @param source 图片源
     * @return 当前控件
     */
    public DocumentHostImageControl setSource(HostImageSource source) {
        this.source = Objects.requireNonNull(source, "source");
        sourceRevision++;
        // 通过内部修订号属性驱动文档重新生成绘制命令，避免把宿主图片更新暴露成额外公开底层 API。
        element.setAttribute("data-host-image-revision", String.valueOf(sourceRevision));
        return this;
    }

    /**
     * 便捷设置物品图片尺寸。
     *
     * @param size 像素尺寸
     * @return 当前控件
     */
    public DocumentHostImageControl setSize(int size) {
        int resolvedSize = Math.max(1, size);
        element.style()
                .setWidth(UiStyleLength.px(resolvedSize))
                .setHeight(UiStyleLength.px(resolvedSize));
        return this;
    }

    private void configureElement() {
        element.setAttribute("role", "presentation")
                .setAttribute("aria-hidden", "true")
                .setAttribute("data-hit-test-hidden", "true")
                .setAttribute("data-host-image", "true")
                .setAttribute("data-host-image-revision", "0");
        element.style()
                .setDisplay(UiDisplay.BLOCK)
                .setWidth(UiStyleLength.px(16))
                .setHeight(UiStyleLength.px(16))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
    }

    private void installRenderer() {
        element.setCustomRenderer(new DocumentCustomRenderer() {
            @Override
            public void render(UiRenderContext context, int contentLeft, int contentTop, int contentRight,
                    int contentBottom) {
                if (source == null) {
                    return;
                }
                context.drawHostImage(source, contentLeft, contentTop, contentRight, contentBottom);
            }
        });
    }
}
