package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * 页面作者可创建并手动派发的自定义 DOM 事件。
 *
 * <p>沿用浏览器 {@code Event} 语义：仅在 {@link #isCancelable()} 为 true 时
 * 调用 {@link #preventDefault()} 才会真正标记 default-prevented，
 * 否则视为无副作用调用，避免不可取消的事件被业务方误认为可阻止默认行为。</p>
 */
public final class DocumentCustomEvent extends AbstractDocumentElementEvent {

    private final String type;
    private final Object detail;
    private final boolean bubbles;
    private final boolean cancelable;

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
        super(target, currentTarget, eventControl);
        String resolvedType = Objects.requireNonNull(type, "type").trim();
        if (resolvedType.isEmpty()) {
            throw new IllegalArgumentException("type cannot be empty");
        }
        this.type = resolvedType;
        this.detail = detail;
        this.bubbles = bubbles;
        this.cancelable = cancelable;
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
     * 阻止默认行为。
     *
     * <p>仅在当前事件声明为 cancelable 时生效，与浏览器 {@code Event#preventDefault()} 行为一致。</p>
     */
    @Override
    public void preventDefault() {
        if (cancelable) {
            super.preventDefault();
        }
    }

    DocumentCustomEvent withDispatchTargets(ElementNode target, ElementNode currentTarget) {
        return new DocumentCustomEvent(type, detail, bubbles, cancelable, target, currentTarget, getEventControl());
    }
}
