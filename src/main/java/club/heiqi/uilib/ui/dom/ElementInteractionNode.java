package club.heiqi.uilib.ui.dom;

import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;

/**
 * HTML-like 元素交互 handler 基类。
 *
 * <p>集中承载具名事件 handler 与自定义绘制回调的公开转发方法，避免 {@link ElementNode}
 * 继续堆叠纯转发样板代码。</p>
 */
abstract class ElementInteractionNode extends DocumentNode {

    protected final ElementInteractionHandlers handlers = new ElementInteractionHandlers();

    ElementInteractionNode(UiDocument ownerDocument) {
        super(ownerDocument);
    }

    /**
     * 设置元素 active 状态处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param activeHandler active 状态处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setActiveHandler(DocumentElementActiveHandler activeHandler) {
        handlers.activeHandler = activeHandler;
        return self();
    }

    /**
     * 返回元素 active 状态处理器。
     *
     * @return active 状态处理器；不存在时返回 null
     */
    public DocumentElementActiveHandler getActiveHandler() {
        return handlers.activeHandler;
    }

    /**
     * 设置元素点击处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param clickHandler 点击处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setClickHandler(DocumentElementClickHandler clickHandler) {
        handlers.clickHandler = clickHandler;
        return self();
    }

    /**
     * 返回元素点击处理器。
     *
     * @return 点击处理器；不存在时返回 null
     */
    public DocumentElementClickHandler getClickHandler() {
        return handlers.clickHandler;
    }

    /**
     * 设置元素双击处理器。
     *
     * @param doubleClickHandler 双击处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setDoubleClickHandler(DocumentElementDoubleClickHandler doubleClickHandler) {
        handlers.doubleClickHandler = doubleClickHandler;
        return self();
    }

    /**
     * 返回元素双击处理器。
     *
     * @return 双击处理器；不存在时返回 null
     */
    public DocumentElementDoubleClickHandler getDoubleClickHandler() {
        return handlers.doubleClickHandler;
    }

    /**
     * 设置元素右键菜单处理器。
     *
     * @param contextMenuHandler 右键菜单处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setContextMenuHandler(DocumentElementContextMenuHandler contextMenuHandler) {
        handlers.contextMenuHandler = contextMenuHandler;
        return self();
    }

    /**
     * 返回元素右键菜单处理器。
     *
     * @return 右键菜单处理器；不存在时返回 null
     */
    public DocumentElementContextMenuHandler getContextMenuHandler() {
        return handlers.contextMenuHandler;
    }

    /**
     * 设置元素焦点变化处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param focusHandler 焦点变化处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setFocusHandler(DocumentElementFocusHandler focusHandler) {
        handlers.focusHandler = focusHandler;
        return self();
    }

    /**
     * 返回元素焦点变化处理器。
     *
     * @return 焦点变化处理器；不存在时返回 null
     */
    public DocumentElementFocusHandler getFocusHandler() {
        return handlers.focusHandler;
    }

    /**
     * 设置元素悬停状态处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param hoverHandler 悬停状态处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setHoverHandler(DocumentElementHoverHandler hoverHandler) {
        handlers.hoverHandler = hoverHandler;
        return self();
    }

    /**
     * 返回元素悬停状态处理器。
     *
     * @return 悬停状态处理器；不存在时返回 null
     */
    public DocumentElementHoverHandler getHoverHandler() {
        return handlers.hoverHandler;
    }

    /**
     * 设置元素拖拽处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param dragHandler 拖拽处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setDragHandler(DocumentElementDragHandler dragHandler) {
        handlers.dragHandler = dragHandler;
        return self();
    }

    /**
     * 返回元素拖拽处理器。
     *
     * @return 拖拽处理器；不存在时返回 null
     */
    public DocumentElementDragHandler getDragHandler() {
        return handlers.dragHandler;
    }

    /**
     * 设置元素 dragstart 处理器。
     *
     * @param dragStartHandler dragstart 处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setDragStartHandler(DocumentElementDragStartHandler dragStartHandler) {
        handlers.dragStartHandler = dragStartHandler;
        return self();
    }

    /**
     * 返回元素 dragstart 处理器。
     *
     * @return dragstart 处理器；不存在时返回 null
     */
    public DocumentElementDragStartHandler getDragStartHandler() {
        return handlers.dragStartHandler;
    }

    /**
     * 设置元素 dragover 处理器。
     *
     * @param dragOverHandler dragover 处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setDragOverHandler(DocumentElementDragOverHandler dragOverHandler) {
        handlers.dragOverHandler = dragOverHandler;
        return self();
    }

    /**
     * 返回元素 dragover 处理器。
     *
     * @return dragover 处理器；不存在时返回 null
     */
    public DocumentElementDragOverHandler getDragOverHandler() {
        return handlers.dragOverHandler;
    }

    /**
     * 设置元素 dragend 处理器。
     *
     * @param dragEndHandler dragend 处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setDragEndHandler(DocumentElementDragEndHandler dragEndHandler) {
        handlers.dragEndHandler = dragEndHandler;
        return self();
    }

    /**
     * 返回元素 dragend 处理器。
     *
     * @return dragend 处理器；不存在时返回 null
     */
    public DocumentElementDragEndHandler getDragEndHandler() {
        return handlers.dragEndHandler;
    }

    /**
     * 设置元素键盘按键处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param keyHandler 键盘按键处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setKeyHandler(DocumentElementKeyHandler keyHandler) {
        handlers.keyHandler = keyHandler;
        return self();
    }

    /**
     * 返回元素键盘按键处理器。
     *
     * @return 键盘按键处理器；不存在时返回 null
     */
    public DocumentElementKeyHandler getKeyHandler() {
        return handlers.keyHandler;
    }

    /**
     * 设置元素文本输入处理器。
     *
     * <p>事件处理器不影响布局和绘制缓存，因此不会提升文档 mutation version。</p>
     *
     * @param textInputHandler 文本输入处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setTextInputHandler(DocumentElementTextInputHandler textInputHandler) {
        handlers.textInputHandler = textInputHandler;
        return self();
    }

    /**
     * 返回元素文本输入处理器。
     *
     * @return 文本输入处理器；不存在时返回 null
     */
    public DocumentElementTextInputHandler getTextInputHandler() {
        return handlers.textInputHandler;
    }

    /**
     * 设置元素鼠标按下处理器。
     *
     * @param mouseDownHandler 鼠标按下处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setMouseDownHandler(DocumentElementMouseDownHandler mouseDownHandler) {
        handlers.mouseDownHandler = mouseDownHandler;
        return self();
    }

    /**
     * 返回元素鼠标按下处理器。
     *
     * @return 鼠标按下处理器；不存在时返回 null
     */
    public DocumentElementMouseDownHandler getMouseDownHandler() {
        return handlers.mouseDownHandler;
    }

    /**
     * 设置元素鼠标抬起处理器。
     *
     * @param mouseUpHandler 鼠标抬起处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setMouseUpHandler(DocumentElementMouseUpHandler mouseUpHandler) {
        handlers.mouseUpHandler = mouseUpHandler;
        return self();
    }

    /**
     * 返回元素鼠标抬起处理器。
     *
     * @return 鼠标抬起处理器；不存在时返回 null
     */
    public DocumentElementMouseUpHandler getMouseUpHandler() {
        return handlers.mouseUpHandler;
    }

    /**
     * 设置元素滚轮事件处理器。
     *
     * @param wheelHandler 滚轮事件处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setWheelHandler(DocumentElementWheelHandler wheelHandler) {
        handlers.wheelHandler = wheelHandler;
        return self();
    }

    /**
     * 返回元素滚轮事件处理器。
     *
     * @return 滚轮事件处理器；不存在时返回 null
     */
    public DocumentElementWheelHandler getWheelHandler() {
        return handlers.wheelHandler;
    }

    /**
     * 设置元素焦点进入处理器（冒泡版 focus）。
     *
     * @param focusInHandler 焦点进入处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setFocusInHandler(DocumentElementFocusInHandler focusInHandler) {
        handlers.focusInHandler = focusInHandler;
        return self();
    }

    /**
     * 返回元素焦点进入处理器（冒泡版 focus）。
     *
     * @return 焦点进入处理器；不存在时返回 null
     */
    public DocumentElementFocusInHandler getFocusInHandler() {
        return handlers.focusInHandler;
    }

    /**
     * 设置元素焦点离开处理器（冒泡版 blur）。
     *
     * @param focusOutHandler 焦点离开处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setFocusOutHandler(DocumentElementFocusOutHandler focusOutHandler) {
        handlers.focusOutHandler = focusOutHandler;
        return self();
    }

    /**
     * 返回元素焦点离开处理器（冒泡版 blur）。
     *
     * @return 焦点离开处理器；不存在时返回 null
     */
    public DocumentElementFocusOutHandler getFocusOutHandler() {
        return handlers.focusOutHandler;
    }

    /**
     * 设置元素过渡开始处理器。
     *
     * @param transitionStartHandler 过渡开始处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setTransitionStartHandler(DocumentElementTransitionStartHandler transitionStartHandler) {
        handlers.transitionStartHandler = transitionStartHandler;
        return self();
    }

    /**
     * 返回元素过渡开始处理器。
     *
     * @return 过渡开始处理器；不存在时返回 null
     */
    public DocumentElementTransitionStartHandler getTransitionStartHandler() {
        return handlers.transitionStartHandler;
    }

    /**
     * 设置元素过渡结束处理器。
     *
     * @param transitionEndHandler 过渡结束处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setTransitionEndHandler(DocumentElementTransitionEndHandler transitionEndHandler) {
        handlers.transitionEndHandler = transitionEndHandler;
        return self();
    }

    /**
     * 返回元素过渡结束处理器。
     *
     * @return 过渡结束处理器；不存在时返回 null
     */
    public DocumentElementTransitionEndHandler getTransitionEndHandler() {
        return handlers.transitionEndHandler;
    }

    /**
     * 设置元素过渡取消处理器。
     *
     * @param transitionCancelHandler 过渡取消处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setTransitionCancelHandler(DocumentElementTransitionCancelHandler transitionCancelHandler) {
        handlers.transitionCancelHandler = transitionCancelHandler;
        return self();
    }

    /**
     * 返回元素过渡取消处理器。
     *
     * @return 过渡取消处理器；不存在时返回 null
     */
    public DocumentElementTransitionCancelHandler getTransitionCancelHandler() {
        return handlers.transitionCancelHandler;
    }

    /**
     * 设置元素动画开始处理器。
     *
     * @param animationStartHandler 动画开始处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setAnimationStartHandler(DocumentElementAnimationStartHandler animationStartHandler) {
        handlers.animationStartHandler = animationStartHandler;
        return self();
    }

    /**
     * 返回元素动画开始处理器。
     *
     * @return 动画开始处理器；不存在时返回 null
     */
    public DocumentElementAnimationStartHandler getAnimationStartHandler() {
        return handlers.animationStartHandler;
    }

    /**
     * 设置元素动画迭代处理器。
     *
     * @param animationIterationHandler 动画迭代处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setAnimationIterationHandler(
            DocumentElementAnimationIterationHandler animationIterationHandler) {
        handlers.animationIterationHandler = animationIterationHandler;
        return self();
    }

    /**
     * 返回元素动画迭代处理器。
     *
     * @return 动画迭代处理器；不存在时返回 null
     */
    public DocumentElementAnimationIterationHandler getAnimationIterationHandler() {
        return handlers.animationIterationHandler;
    }

    /**
     * 设置元素动画结束处理器。
     *
     * @param animationEndHandler 动画结束处理器；为 null 时清除处理器
     * @return 当前元素
     */
    public ElementNode setAnimationEndHandler(DocumentElementAnimationEndHandler animationEndHandler) {
        handlers.animationEndHandler = animationEndHandler;
        return self();
    }

    /**
     * 返回元素动画结束处理器。
     *
     * @return 动画结束处理器；不存在时返回 null
     */
    public DocumentElementAnimationEndHandler getAnimationEndHandler() {
        return handlers.animationEndHandler;
    }

    /**
     * 设置元素滚动事件处理器。
     *
     * <p>当元素内部滚动位置变化时触发。</p>
     *
     * @param scrollHandler 滚动事件处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setScrollHandler(DocumentElementScrollHandler scrollHandler) {
        handlers.scrollHandler = scrollHandler;
        return self();
    }

    /**
     * 返回元素滚动事件处理器。
     *
     * @return 滚动事件处理器；不存在时返回 null
     */
    public DocumentElementScrollHandler getScrollHandler() {
        return handlers.scrollHandler;
    }

    /**
     * 设置元素捕获阶段点击处理器。
     *
     * <p>捕获阶段 handler 在事件从根元素向目标元素传播时触发，先于冒泡阶段。</p>
     *
     * @param captureClickHandler 捕获阶段点击处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setCaptureClickHandler(DocumentElementClickHandler captureClickHandler) {
        handlers.captureClickHandler = captureClickHandler;
        return self();
    }

    /**
     * 返回元素捕获阶段点击处理器。
     *
     * @return 捕获阶段点击处理器；不存在时返回 null
     */
    public DocumentElementClickHandler getCaptureClickHandler() {
        return handlers.captureClickHandler;
    }

    /**
     * 设置元素捕获阶段鼠标按下处理器。
     *
     * @param captureMouseDownHandler 捕获阶段鼠标按下处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setCaptureMouseDownHandler(DocumentElementMouseDownHandler captureMouseDownHandler) {
        handlers.captureMouseDownHandler = captureMouseDownHandler;
        return self();
    }

    /**
     * 返回元素捕获阶段鼠标按下处理器。
     *
     * @return 捕获阶段鼠标按下处理器；不存在时返回 null
     */
    public DocumentElementMouseDownHandler getCaptureMouseDownHandler() {
        return handlers.captureMouseDownHandler;
    }

    /**
     * 设置元素捕获阶段鼠标抬起处理器。
     *
     * @param captureMouseUpHandler 捕获阶段鼠标抬起处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setCaptureMouseUpHandler(DocumentElementMouseUpHandler captureMouseUpHandler) {
        handlers.captureMouseUpHandler = captureMouseUpHandler;
        return self();
    }

    /**
     * 返回元素捕获阶段鼠标抬起处理器。
     *
     * @return 捕获阶段鼠标抬起处理器；不存在时返回 null
     */
    public DocumentElementMouseUpHandler getCaptureMouseUpHandler() {
        return handlers.captureMouseUpHandler;
    }

    /**
     * 设置元素捕获阶段滚轮处理器。
     *
     * <p>捕获阶段 handler 在默认滚动发生前，从根元素向目标元素传播。</p>
     *
     * @param captureWheelHandler 捕获阶段滚轮处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setCaptureWheelHandler(DocumentElementWheelHandler captureWheelHandler) {
        handlers.captureWheelHandler = captureWheelHandler;
        return self();
    }

    /**
     * 返回元素捕获阶段滚轮处理器。
     *
     * @return 捕获阶段滚轮处理器；不存在时返回 null
     */
    public DocumentElementWheelHandler getCaptureWheelHandler() {
        return handlers.captureWheelHandler;
    }

    /**
     * 设置元素捕获阶段文本输入处理器。
     *
     * @param captureTextInputHandler 捕获阶段文本输入处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setCaptureTextInputHandler(DocumentElementTextInputHandler captureTextInputHandler) {
        handlers.captureTextInputHandler = captureTextInputHandler;
        return self();
    }

    /**
     * 返回元素捕获阶段文本输入处理器。
     *
     * @return 捕获阶段文本输入处理器；不存在时返回 null
     */
    public DocumentElementTextInputHandler getCaptureTextInputHandler() {
        return handlers.captureTextInputHandler;
    }

    /**
     * 设置元素捕获阶段键盘处理器。
     *
     * @param captureKeyHandler 捕获阶段键盘处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setCaptureKeyHandler(DocumentElementKeyHandler captureKeyHandler) {
        handlers.captureKeyHandler = captureKeyHandler;
        return self();
    }

    /**
     * 返回元素捕获阶段键盘处理器。
     *
     * @return 捕获阶段键盘处理器；不存在时返回 null
     */
    public DocumentElementKeyHandler getCaptureKeyHandler() {
        return handlers.captureKeyHandler;
    }

    /**
     * 设置元素捕获阶段双击处理器。
     *
     * @param captureDoubleClickHandler 捕获阶段双击处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setCaptureDoubleClickHandler(DocumentElementDoubleClickHandler captureDoubleClickHandler) {
        handlers.captureDoubleClickHandler = captureDoubleClickHandler;
        return self();
    }

    /**
     * 返回元素捕获阶段双击处理器。
     *
     * @return 捕获阶段双击处理器；不存在时返回 null
     */
    public DocumentElementDoubleClickHandler getCaptureDoubleClickHandler() {
        return handlers.captureDoubleClickHandler;
    }

    /**
     * 设置元素捕获阶段右键菜单处理器。
     *
     * @param captureContextMenuHandler 捕获阶段右键菜单处理器；为 null 时清除
     * @return 当前元素
     */
    public ElementNode setCaptureContextMenuHandler(DocumentElementContextMenuHandler captureContextMenuHandler) {
        handlers.captureContextMenuHandler = captureContextMenuHandler;
        return self();
    }

    /**
     * 返回元素捕获阶段右键菜单处理器。
     *
     * @return 捕获阶段右键菜单处理器；不存在时返回 null
     */
    public DocumentElementContextMenuHandler getCaptureContextMenuHandler() {
        return handlers.captureContextMenuHandler;
    }

    /**
     * 设置元素自定义绘制回调，供控件在背景/边框之后注入额外渲染。
     *
     * <p>回调会在 paint engine 的 appendBoxCommands 中被包装为 CUSTOM 命令，
     * 在元素背景和边框绘制之后、clip/子树之前执行。</p>
     * <p>CUSTOM 属于宿主级逃生口，普通业务表面应优先使用标准 DOM / 样式 /
     * paint command 表达，不应直接依赖渲染后端手绘。</p>
     * <p>回调会影响绘制命令生成，变更时只提升文档 paint version。</p>
     *
     * @param customRenderer 自定义渲染回调
     */
    public void setCustomRenderer(DocumentCustomRenderer customRenderer) {
        if (handlers.customRenderer == customRenderer) {
            return;
        }
        handlers.customRenderer = customRenderer;
        markPaintMutated();
    }

    /**
     * 返回元素自定义绘制回调。
     *
     * @return 自定义绘制回调；不存在时返回 null
     */
    public DocumentCustomRenderer getCustomRenderer() {
        return handlers.customRenderer;
    }

    private ElementNode self() {
        return (ElementNode) this;
    }
}
