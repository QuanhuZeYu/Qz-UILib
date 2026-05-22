package club.heiqi.uilib.ui.dom;

import club.heiqi.uilib.ui.paint.DocumentCustomRenderer;

/**
 * 元素具名 handler 容器。
 *
 * <p>把 {@link ElementNode} 上 14 组冒泡 handler、4 组捕获 handler、自定义渲染回调和滚动 handler
 * 拆出来集中存放，避免 ElementNode 字段表持续膨胀。</p>
 *
 * <p>对外仍由 ElementNode 转发；本类不直接出现在公开 API 中。</p>
 */
final class ElementInteractionHandlers {

    // 冒泡阶段
    DocumentElementActiveHandler activeHandler;
    DocumentElementClickHandler clickHandler;
    DocumentElementDoubleClickHandler doubleClickHandler;
    DocumentElementContextMenuHandler contextMenuHandler;
    DocumentElementFocusHandler focusHandler;
    DocumentElementHoverHandler hoverHandler;
    DocumentElementDragHandler dragHandler;
    DocumentElementDragStartHandler dragStartHandler;
    DocumentElementDragOverHandler dragOverHandler;
    DocumentElementDragEndHandler dragEndHandler;
    DocumentElementKeyHandler keyHandler;
    DocumentElementTextInputHandler textInputHandler;
    DocumentElementMouseDownHandler mouseDownHandler;
    DocumentElementMouseUpHandler mouseUpHandler;
    DocumentElementFocusInHandler focusInHandler;
    DocumentElementTransitionStartHandler transitionStartHandler;
    DocumentElementTransitionEndHandler transitionEndHandler;
    DocumentElementTransitionCancelHandler transitionCancelHandler;
    DocumentElementAnimationStartHandler animationStartHandler;
    DocumentElementAnimationIterationHandler animationIterationHandler;
    DocumentElementAnimationEndHandler animationEndHandler;
    DocumentElementScrollHandler scrollHandler;

    // 捕获阶段
    DocumentElementClickHandler captureClickHandler;
    DocumentElementMouseDownHandler captureMouseDownHandler;
    DocumentElementMouseUpHandler captureMouseUpHandler;
    DocumentElementKeyHandler captureKeyHandler;

    // 自定义渲染
    DocumentCustomRenderer customRenderer;
}
