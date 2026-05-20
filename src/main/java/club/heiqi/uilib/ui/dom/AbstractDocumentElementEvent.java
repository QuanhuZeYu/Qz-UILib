package club.heiqi.uilib.ui.dom;

import java.util.Objects;

/**
 * HTML-like DOM 元素事件抽象基类。
 *
 * <p>统一承载事件传播控制器、初始目标元素与当前处理元素，避免每个事件类
 * 重复抄写 {@code stopPropagation} / {@code preventDefault} / {@code isPropagationStopped}
 * 等模板代码。</p>
 *
 * <p>子类只需在构造方法中传入 {@code target}、{@code currentTarget} 与 {@link DocumentEventControl}，
 * 并按业务需要补充自身字段；统一的取消语义、阶段查询与传播控制均从此基类继承。</p>
 *
 * <p>该类不向页面作者直接暴露内部 {@link DocumentEventControl}，
 * 由框架内部的事件分发链路通过包级 {@link #getEventControl()} 访问。</p>
 */
public abstract class AbstractDocumentElementEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final DocumentEventControl eventControl;

    /**
     * 创建 DOM 元素事件。
     *
     * <p>{@code target} 与 {@code currentTarget} 允许为 {@code null}，
     * 例如 {@link DocumentCustomEvent} 在派发前尚未确定目标。</p>
     *
     * @param target 事件初始目标元素
     * @param currentTarget 当前正在处理事件的元素
     * @param eventControl 共享传播控制器，不允许为 null
     */
    protected AbstractDocumentElementEvent(ElementNode target, ElementNode currentTarget,
            DocumentEventControl eventControl) {
        this.target = target;
        this.currentTarget = currentTarget;
        this.eventControl = Objects.requireNonNull(eventControl, "eventControl");
    }

    /**
     * 返回事件初始目标元素。
     *
     * @return 初始目标元素
     */
    public ElementNode getTarget() {
        return target;
    }

    /**
     * 返回当前正在处理事件的元素。
     *
     * @return 当前处理元素
     */
    public ElementNode getCurrentTarget() {
        return currentTarget;
    }

    /**
     * 返回当前事件传播阶段。
     *
     * @return 事件阶段
     */
    public DocumentEventPhase getEventPhase() {
        return eventControl.getEventPhase();
    }

    /**
     * 阻止事件继续向后续元素传播。
     */
    public void stopPropagation() {
        eventControl.stopPropagation();
    }

    /**
     * 阻止事件继续传播，并阻止当前元素上的后续 handler 执行。
     */
    public void stopImmediatePropagation() {
        eventControl.stopImmediatePropagation();
    }

    /**
     * 阻止事件的默认行为。
     */
    public void preventDefault() {
        eventControl.preventDefault();
    }

    /**
     * 判断传播是否已被阻止。
     *
     * @return 是否已阻止传播
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

    /**
     * 返回共享传播控制器（框架内部使用）。
     *
     * @return 传播控制器
     */
    DocumentEventControl getEventControl() {
        return eventControl;
    }
}
