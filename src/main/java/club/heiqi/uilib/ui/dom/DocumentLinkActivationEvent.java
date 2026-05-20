package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 链接激活事件。
 */
public final class DocumentLinkActivationEvent {

    private final ElementNode element;
    private final String href;
    private final String target;
    private final long timeNanos;
    private boolean handled;

    /**
     * 创建链接激活事件。
     *
     * @param element 被激活的链接元素
     * @param href 链接目标
     * @param target 链接 target 属性
     * @param timeNanos 激活时间戳
     */
    public DocumentLinkActivationEvent(ElementNode element, String href, String target, long timeNanos) {
        this.element = Objects.requireNonNull(element, "element");
        this.href = href == null ? "" : href;
        this.target = target == null ? "" : target;
        this.timeNanos = timeNanos;
    }

    public ElementNode getElement() {
        return element;
    }

    public String getHref() {
        return href;
    }

    public String getTarget() {
        return target;
    }

    public long getTimeNanos() {
        return timeNanos;
    }

    /**
     * 标记该链接激活已被业务方处理。
     */
    public void markHandled() {
        handled = true;
    }

    public boolean isHandled() {
        return handled;
    }
}
