package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * 页面作者可创建并手动派发的自定义 DOM 事件。
 */
public final class DocumentCustomEvent {

    private final String type;
    private final Object detail;
    private final boolean bubbles;
    private final boolean cancelable;
    private final ElementNode target;
    private final ElementNode currentTarget;
    private final DocumentEventControl eventControl;

    /**
     * 创建默认不冒泡、不可取消的自定义事件。
     *
     * @param type 事件类型
     */
    public DocumentCustomEvent(String type) {
        this(type, null, false, false);
    }

    /**
     * 创建默认不冒泡、不可取消的自定义事件。
     *
     * @param type 事件类型
     * @param detail 事件负载
     */
    public DocumentCustomEvent(String type, Object detail) {
        this(type, detail, false, false);
    }

    /**
     * 创建自定义事件。
     *
     * @param type 事件类型
     * @param detail 事件负载
     * @param bubbles 是否冒泡
     * @param cancelable 是否允许 preventDefault
     */
    public DocumentCustomEvent(String type, Object detail, boolean bubbles, boolean cancelable) {
        this(type, detail, bubbles, cancelable, null, null, new DocumentEventControl());
    }

    DocumentCustomEvent(String type, Object detail, boolean bubbles, boolean cancelable, ElementNode target,
            ElementNode currentTarget, DocumentEventControl eventControl) {
        String resolvedType = Objects.requireNonNull(type, "type").trim();
        if (resolvedType.isEmpty()) {
            throw new IllegalArgumentException("type cannot be empty");
        }
        this.type = resolvedType;
        this.detail = detail;
        this.bubbles = bubbles;
        this.cancelable = cancelable;
        this.target = target;
        this.currentTarget = currentTarget;
        this.eventControl = Objects.requireNonNull(eventControl, "eventControl");
    }

    /**
     * 返回事件类型。
     *
     * @return 事件类型
     */
    public String getType() {
        return type;
    }

    /**
     * 返回事件负载。
     *
     * @return 事件负载
     */
    public Object getDetail() {
        return detail;
    }

    /**
     * 返回事件目标元素。
     *
     * @return 目标元素；未派发前返回 null
     */
    public ElementNode getTarget() {
        return target;
    }

    /**
     * 返回当前正在处理事件的元素。
     *
     * @return 当前处理元素；未派发前返回 null
     */
    public ElementNode getCurrentTarget() {
        return currentTarget;
    }

    /**
     * 返回是否允许冒泡。
     *
     * @return 是否冒泡
     */
    public boolean isBubbles() {
        return bubbles;
    }

    /**
     * 返回是否允许阻止默认行为。
     *
     * @return 是否可取消
     */
    public boolean isCancelable() {
        return cancelable;
    }

    /**
     * 返回当前传播阶段。
     *
     * @return 传播阶段
     */
    public DocumentEventPhase getEventPhase() {
        return eventControl.getEventPhase();
    }

    /**
     * 阻止事件继续传播。
     */
    public void stopPropagation() {
        eventControl.stopPropagation();
    }

    /**
     * 阻止事件继续传播，并阻止当前元素上的后续 listener。
     */
    public void stopImmediatePropagation() {
        eventControl.stopImmediatePropagation();
    }

    /**
     * 阻止默认行为。
     *
     * <p>仅在当前事件声明为 cancelable 时生效。</p>
     */
    public void preventDefault() {
        if (cancelable) {
            eventControl.preventDefault();
        }
    }

    /**
     * 判断事件传播是否已停止。
     *
     * @return 是否已停止传播
     */
    public boolean isPropagationStopped() {
        return eventControl.isPropagationStopped();
    }

    /**
     * 判断默认行为是否已被阻止。
     *
     * @return 是否已阻止默认行为
     */
    public boolean isDefaultPrevented() {
        return eventControl.isDefaultPrevented();
    }

    DocumentCustomEvent withDispatchTargets(ElementNode target, ElementNode currentTarget) {
        return new DocumentCustomEvent(type, detail, bubbles, cancelable, target, currentTarget, eventControl);
    }

    DocumentEventControl getEventControl() {
        return eventControl;
    }
}
