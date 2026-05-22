package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 链接激活事件。
 *
 * <p>统一通过 {@link AbstractDocumentElementEvent} 暴露 {@code preventDefault()} /
 * {@code isDefaultPrevented()} 等标准取消语义。原 {@link #markHandled()} 与
 * {@link #isHandled()} 已映射为 {@code preventDefault()} 的别名并标记为废弃，
 * 业务方应改用浏览器一致的 {@code preventDefault()} 与 {@code isDefaultPrevented()}。</p>
 */
public final class DocumentLinkActivationEvent extends AbstractDocumentElementEvent {

    private final ElementNode element;
    private final String href;
    private final String linkTarget;
    private final long timeNanos;

    /**
     * 创建链接激活事件。
     *
     * @param element 被激活的链接元素
     * @param href 链接目标
     * @param target 链接 target 属性
     * @param timeNanos 激活时间戳
     */
    public DocumentLinkActivationEvent(ElementNode element, String href, String target, long timeNanos) {
        this(element, href, target, timeNanos, new DocumentEventControl());
    }

    /**
     * 创建链接激活事件（共享传播控制器）。
     *
     * @param element 被激活的链接元素
     * @param href 链接目标
     * @param target 链接 target 属性
     * @param timeNanos 激活时间戳
     * @param eventControl 共享传播控制器
     */
    public DocumentLinkActivationEvent(ElementNode element, String href, String target, long timeNanos,
            DocumentEventControl eventControl) {
        super(Objects.requireNonNull(element, "element"),
                Objects.requireNonNull(element, "element"),
                Objects.requireNonNull(eventControl, "eventControl"));
        this.element = element;
        this.href = href == null ? "" : href;
        this.linkTarget = target == null ? "" : target;
        this.timeNanos = timeNanos;
    }

    /**
     * 返回被激活的链接元素。
     *
     * @return 链接元素
     */
    public ElementNode getElement() {
        return element;
    }

    /**
     * 返回链接 href 值。
     *
     * @return href
     */
    public String getHref() {
        return href;
    }

    /**
     * 返回链接 target 属性值（如 {@code _blank}），与 DOM 事件目标元素无关。
     *
     * @return target attribute
     */
    public String getLinkTarget() {
        return linkTarget;
    }

    /**
     * 返回激活时间戳。
     *
     * @return 时间戳
     */
    public long getTimeNanos() {
        return timeNanos;
    }
}
