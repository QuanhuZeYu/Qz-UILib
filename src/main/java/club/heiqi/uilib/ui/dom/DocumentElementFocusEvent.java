package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like 元素焦点变化事件。
 *
 * <p>对应浏览器原生 {@code focus} / {@code blur}：本身不冒泡，仅在目标元素上触发。
 * 仍然继承通用事件基类以保持 {@code stopPropagation()} / {@code preventDefault()} 等
 * 取消语义与其他 DOM 事件一致；调用对实际分发顺序无副作用，仅作为业务侧契约保持统一。</p>
 */
public final class DocumentElementFocusEvent extends AbstractDocumentElementEvent {

    private final boolean focused;
    private final boolean focusVisible;

    /**
     * 创建元素焦点变化事件。
     *
     * @param target 焦点变化的元素
     * @param focused 是否获得焦点
     */
    public DocumentElementFocusEvent(ElementNode target, boolean focused) {
        this(target, focused, focused);
    }

    /**
     * 创建元素焦点变化事件。
     *
     * @param target 焦点变化的元素
     * @param focused 是否获得焦点
     * @param focusVisible 是否应该显示键盘焦点提示
     */
    public DocumentElementFocusEvent(ElementNode target, boolean focused, boolean focusVisible) {
        this(target, focused, focusVisible, new DocumentEventControl());
    }

    /**
     * 创建元素焦点变化事件（共享传播控制器）。
     *
     * @param target 焦点变化的元素
     * @param focused 是否获得焦点
     * @param focusVisible 是否应该显示键盘焦点提示
     * @param eventControl 共享传播控制器
     */
    public DocumentElementFocusEvent(ElementNode target, boolean focused, boolean focusVisible,
            DocumentEventControl eventControl) {
        super(target, target, Objects.requireNonNull(eventControl, "eventControl"));
        this.focused = focused;
        this.focusVisible = focusVisible;
    }

    /**
     * 判断元素是否获得焦点。
     *
     * @return true 表示获得焦点，false 表示失去焦点
     */
    public boolean isFocused() {
        return focused;
    }

    /**
     * 判断元素是否应该显示键盘焦点提示。
     *
     * @return 是否显示焦点提示
     */
    public boolean isFocusVisible() {
        return focusVisible;
    }
}
