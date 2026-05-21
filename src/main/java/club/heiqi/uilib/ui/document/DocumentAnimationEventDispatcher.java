package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationEndEvent;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionEndEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionEndHandler;
import club.heiqi.uilib.ui.dom.DocumentEventControl;
import club.heiqi.uilib.ui.dom.DocumentEventPhase;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * HTML-like 文档动画生命周期事件分发器。
 *
 * <p>承载 transitionend 与 animationend 的冒泡分发，避免 widget 主体持有具体事件构造细节。</p>
 */
final class DocumentAnimationEventDispatcher {

    private DocumentAnimationEventDispatcher() {}

    /**
     * 分发时间线裁剪后产生的动画完成事件。
     *
     * @param pruneResult 时间线裁剪结果
     * @param currentTimeNanos 当前动画时间
     * @param attachedChecker 元素挂载状态检查器
     */
    static void dispatchCompletedAnimationEvents(DocumentAnimationTimeline.PruneResult pruneResult,
            long currentTimeNanos, AttachedChecker attachedChecker) {
        if (pruneResult == null) {
            return;
        }
        for (DocumentAnimationTimeline.TransitionEndRecord record : pruneResult.getTransitionEndRecords()) {
            dispatchTransitionEnd(record, currentTimeNanos, attachedChecker);
        }
        for (DocumentAnimationTimeline.AnimationEndRecord record : pruneResult.getAnimationEndRecords()) {
            dispatchAnimationEnd(record, currentTimeNanos, attachedChecker);
        }
    }

    private static boolean dispatchTransitionEnd(DocumentAnimationTimeline.TransitionEndRecord record, long timeNanos,
            AttachedChecker attachedChecker) {
        ElementNode target = record == null ? null : record.getElement();
        if (target == null || !attachedChecker.isAttached(target)) {
            return false;
        }
        DocumentEventControl eventControl = new DocumentEventControl();
        List<ElementNode> path = buildAncestorPath(target);

        eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
        DocumentElementTransitionEndHandler targetHandler = target.getTransitionEndHandler();
        if (targetHandler != null) {
            DocumentElementTransitionEndEvent event = new DocumentElementTransitionEndEvent(target, target,
                    record.getProperty(), record.getElapsedTimeNanos(), timeNanos, eventControl);
            if (targetHandler.onTransitionEnd(event)) {
                eventControl.stopPropagation();
            }
        }

        eventControl.setEventPhase(DocumentEventPhase.BUBBLING);
        for (int index = 1; index < path.size(); index++) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementTransitionEndHandler handler = currentElement.getTransitionEndHandler();
            if (handler == null) {
                continue;
            }
            DocumentElementTransitionEndEvent event = new DocumentElementTransitionEndEvent(target, currentElement,
                    record.getProperty(), record.getElapsedTimeNanos(), timeNanos, eventControl);
            if (handler.onTransitionEnd(event)) {
                eventControl.stopPropagation();
            }
        }
        return eventControl.isPropagationStopped();
    }

    private static boolean dispatchAnimationEnd(DocumentAnimationTimeline.AnimationEndRecord record, long timeNanos,
            AttachedChecker attachedChecker) {
        ElementNode target = record == null ? null : record.getElement();
        if (target == null || !attachedChecker.isAttached(target)) {
            return false;
        }
        DocumentEventControl eventControl = new DocumentEventControl();
        List<ElementNode> path = buildAncestorPath(target);

        eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
        DocumentElementAnimationEndHandler targetHandler = target.getAnimationEndHandler();
        if (targetHandler != null) {
            DocumentElementAnimationEndEvent event = new DocumentElementAnimationEndEvent(target, target,
                    record.getAnimationName(), record.getElapsedTimeNanos(), timeNanos, eventControl);
            if (targetHandler.onAnimationEnd(event)) {
                eventControl.stopPropagation();
            }
        }

        eventControl.setEventPhase(DocumentEventPhase.BUBBLING);
        for (int index = 1; index < path.size(); index++) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementAnimationEndHandler handler = currentElement.getAnimationEndHandler();
            if (handler == null) {
                continue;
            }
            DocumentElementAnimationEndEvent event = new DocumentElementAnimationEndEvent(target, currentElement,
                    record.getAnimationName(), record.getElapsedTimeNanos(), timeNanos, eventControl);
            if (handler.onAnimationEnd(event)) {
                eventControl.stopPropagation();
            }
        }
        return eventControl.isPropagationStopped();
    }

    private static List<ElementNode> buildAncestorPath(ElementNode target) {
        List<ElementNode> path = new ArrayList<ElementNode>();
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            path.add((ElementNode) current);
        }
        return path;
    }

    /** 检查元素是否仍挂载于当前文档。 */
    interface AttachedChecker {
        boolean isAttached(ElementNode element);
    }
}
