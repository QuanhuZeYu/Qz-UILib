package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * U0 内部 fake composition：验证 projection occurrence 与 input owner 的最小正确模型。
 *
 * <p>同一 factory 每次投放都会创建独立 {@link SceneRuntime} 与 {@link SceneNode} 树；factory
 * 闭包可共享业务 signal。composition 是唯一 {@link PlatformInputSource#drainFrame()} 调用者，
 * 按登记顺序的反向做 top-first 只读 claim，只把事件投给一个 winner。</p>
 *
 * <p>该类型保持 package-private，不是正式 {@code UiContent}/{@code UiProjection} API；screen/overlay
 * 宿主迁移留给 U1。</p>
 */
final class SceneProjectionComposition implements AutoCloseable {

    private final PlatformInputSource inputSource;
    private final List<Occurrence> bottomFirst = new ArrayList<Occurrence>();
    private final Set<SceneMouseButton> suppressedButtons = EnumSet.noneOf(SceneMouseButton.class);

    private Occurrence gestureOwner;
    private SceneMouseButton gestureButton = SceneMouseButton.NONE;
    private Occurrence keyboardOwner;
    private Occurrence hoverOwner;
    private int lastPointerX;
    private int lastPointerY;
    private boolean lastControlDown;
    private boolean lastShiftDown;
    private boolean lastAltDown;
    private boolean lastMetaDown;
    private long lastFrameTimeNanos;
    private boolean closed;
    private boolean closing;
    private boolean closePending;
    private int routeDepth;

    SceneProjectionComposition(PlatformInputSource inputSource) {
        this.inputSource = Objects.requireNonNull(inputSource, "inputSource");
    }

    /**
     * 将同一 content factory 投放为一个新 occurrence；后登记者视觉上位于更高层。
     */
    Occurrence project(Function<SceneRuntime, SceneNode> factory, int absX, int absY) {
        Objects.requireNonNull(factory, "factory");
        if (closed) {
            throw new IllegalStateException("composition 已关闭");
        }
        SceneRuntime runtime = new SceneRuntime();
        Owner occurrenceOwner = new Owner();
        SceneNode root;
        try {
            final SceneNode[] rootHolder = new SceneNode[1];
            occurrenceOwner.run(() -> rootHolder[0] = Objects.requireNonNull(factory.apply(runtime), "projection root"));
            root = rootHolder[0];
        } catch (RuntimeException failure) {
            disposeProjectionResources(occurrenceOwner, runtime, failure);
            throw failure;
        } catch (Error failure) {
            disposeProjectionResources(occurrenceOwner, runtime, failure);
            throw failure;
        }
        if (closed) {
            IllegalStateException failure = new IllegalStateException("composition 在 projection 期间关闭");
            disposeProjectionResources(occurrenceOwner, runtime, failure);
            throw failure;
        }
        Occurrence occurrence = new Occurrence(occurrenceOwner, runtime, root, absX, absY);
        bottomFirst.add(occurrence);
        return occurrence;
    }

    /** 排空 native input 一次，并按 visual order 将每个标准事件至多投给一个 occurrence。 */
    SceneInputFrame drainAndRoute() {
        if (closed) {
            throw new IllegalStateException("composition 已关闭");
        }
        SceneInputFrame frame = inputSource.drainFrame();
        for (ScenePointerEvent event : frame.getPointerEvents()) {
            rememberPointerEvent(event);
            routePointer(frame, event);
            if (closed) {
                return frame;
            }
        }
        rememberFrameTail(frame);
        synchronizeKeyboardOwner();
        routeKeyboardAndText(frame);
        return frame;
    }

    private void routePointer(SceneInputFrame sourceFrame, ScenePointerEvent event) {
        if (closed) {
            return;
        }
        // 第二按钮从 DOWN 到 UP 整段吞掉；主按钮提前结束后仍保持这个锁。
        if (gestureOwner == null && !suppressedButtons.isEmpty()) {
            if (event.getAction() == ScenePointerAction.BUTTON_UP) {
                if (event.getButton() != SceneMouseButton.NONE) {
                    suppressedButtons.remove(event.getButton());
                }
                if (suppressedButtons.isEmpty()) {
                    reconcileHoverOnly();
                }
            } else if (event.getAction() == ScenePointerAction.CANCEL) {
                suppressedButtons.clear();
                reconcileHoverOnly();
            } else if (event.getAction() == ScenePointerAction.BUTTON_DOWN
                    && event.getButton() != SceneMouseButton.NONE) {
                suppressedButtons.add(event.getButton());
            }
            return;
        }

        switch (event.getAction()) {
            case BUTTON_DOWN:
                routeDown(sourceFrame, event);
                break;
            case MOVE:
                routeMove(sourceFrame, event);
                break;
            case SCROLL:
                routeClaimed(sourceFrame, event);
                break;
            case BUTTON_UP:
                routeUp(sourceFrame, event);
                break;
            case CANCEL:
                routeCancel(sourceFrame, event);
                break;
            default:
                break;
        }
    }

    private void routeDown(SceneInputFrame sourceFrame, ScenePointerEvent event) {
        if (gestureOwner != null) {
            if (event.getButton() != SceneMouseButton.NONE && event.getButton() != gestureButton) {
                suppressedButtons.add(event.getButton());
            }
            return;
        }

        Occurrence winner = findWinner(event);
        clearKeyboardExcept(winner);
        if (winner == null) {
            keyboardOwner = null;
            return;
        }

        if (event.getButton() != SceneMouseButton.NONE) {
            gestureOwner = winner;
            gestureButton = event.getButton();
        }
        routeOccurrence(winner, pointerFrame(sourceFrame, event));
        if (closed) {
            return;
        }
        synchronizeKeyboardOwner();
    }

    private void routeMove(SceneInputFrame sourceFrame, ScenePointerEvent event) {
        if (closed) {
            return;
        }
        if (gestureOwner != null) {
            setHoverOwner(gestureOwner);
            routeOccurrence(gestureOwner, pointerFrame(sourceFrame, event));
            return;
        }
        Occurrence winner = findWinner(event);
        setHoverOwner(winner);
        if (winner != null) {
            routeOccurrence(winner, pointerFrame(sourceFrame, event));
        }
    }

    private void routeClaimed(SceneInputFrame sourceFrame, ScenePointerEvent event) {
        if (closed) {
            return;
        }
        Occurrence winner = findWinner(event);
        if (winner != null) {
            routeOccurrence(winner, pointerFrame(sourceFrame, event));
        }
    }

    private void routeUp(SceneInputFrame sourceFrame, ScenePointerEvent event) {
        if (suppressedButtons.remove(event.getButton())) {
            if (gestureOwner == null && suppressedButtons.isEmpty()) {
                reconcileHoverOnly();
            }
            return;
        }
        if (gestureOwner == null || event.getButton() != gestureButton) {
            return;
        }

        Occurrence owner = gestureOwner;
        gestureOwner = null;
        gestureButton = SceneMouseButton.NONE;
        routeOccurrence(owner, pointerFrame(sourceFrame, event));
        if (closed) {
            return;
        }
        if (suppressedButtons.isEmpty()) {
            reconcileHoverOnly();
        }
        synchronizeKeyboardOwner();
    }

    private void routeCancel(SceneInputFrame sourceFrame, ScenePointerEvent event) {
        suppressedButtons.clear();
        if (gestureOwner == null) {
            return;
        }
        Occurrence owner = gestureOwner;
        gestureOwner = null;
        gestureButton = SceneMouseButton.NONE;
        routeOccurrence(owner, pointerFrame(sourceFrame, event));
        if (closed) {
            return;
        }
        reconcileHoverOnly();
        synchronizeKeyboardOwner();
    }

    private Occurrence findWinner(ScenePointerEvent event) {
        for (int i = bottomFirst.size() - 1; i >= 0; i--) {
            Occurrence candidate = bottomFirst.get(i);
            if (candidate.runtime.getInputRouter().claimsPointer(
                    candidate.root, event, candidate.absX, candidate.absY)) {
                return candidate;
            }
        }
        return null;
    }

    private void routeKeyboardAndText(SceneInputFrame sourceFrame) {
        for (SceneTextEvent event : sourceFrame.getTextEvents()) {
            if (closed) {
                return;
            }
            synchronizeKeyboardOwner();
            if (keyboardOwner != null) {
                routeOccurrence(keyboardOwner, keyboardFrame(sourceFrame,
                        Collections.<SceneKeyEvent>emptyList(), Collections.singletonList(event)));
            }
        }
        for (SceneKeyEvent event : sourceFrame.getKeyEvents()) {
            if (closed) {
                return;
            }
            SceneInputFrame eventFrame = keyboardFrame(sourceFrame,
                    Collections.singletonList(event), Collections.<SceneTextEvent>emptyList());
            Occurrence escapeOwner = findEscapeDismissWinner(event);
            if (escapeOwner != null) {
                routeOccurrence(escapeOwner, eventFrame);
                continue;
            }
            synchronizeKeyboardOwner();
            if (keyboardOwner == null) {
                keyboardOwner = findKeyboardFallback(eventFrame);
            }
            if (keyboardOwner != null) {
                routeOccurrence(keyboardOwner, eventFrame);
            }
        }
    }

    /** ESC dismiss 按最终 visual order 临时抢占 sticky keyboard focus owner。 */
    private Occurrence findEscapeDismissWinner(SceneKeyEvent event) {
        if (event.getAction() == SceneKeyAction.RELEASED || event.getKey() != SceneKey.ESCAPE) {
            return null;
        }
        for (int i = bottomFirst.size() - 1; i >= 0; i--) {
            Occurrence occurrence = bottomFirst.get(i);
            if (occurrence.runtime.getInputRouter().hasEscapeDismissTarget()) {
                return occurrence;
            }
        }
        return null;
    }

    private static SceneInputFrame keyboardFrame(SceneInputFrame sourceFrame,
            List<SceneKeyEvent> keyEvents, List<SceneTextEvent> textEvents) {
        return new SceneInputFrame(
                keyEvents,
                Collections.<ScenePointerEvent>emptyList(),
                textEvents,
                sourceFrame.getPointerX(), sourceFrame.getPointerY(),
                sourceFrame.isControlDown(), sourceFrame.isShiftDown(),
                sourceFrame.isAltDown(), sourceFrame.isMetaDown(),
                sourceFrame.getFrameTimeNanos());
    }

    private void clearKeyboardExcept(Occurrence nextOwner) {
        for (Occurrence occurrence : bottomFirst) {
            if (occurrence != nextOwner) {
                occurrence.runtime.getInputRouter().clearFocus();
            }
        }
        keyboardOwner = nextOwner;
    }

    /** 将 programmatic focus 变化收敛为当前 input scope 唯一 keyboard owner。 */
    private void synchronizeKeyboardOwner() {
        Occurrence selected = null;
        for (int i = bottomFirst.size() - 1; i >= 0; i--) {
            Occurrence occurrence = bottomFirst.get(i);
            if (occurrence.runtime.getFocusedNode() != null) {
                selected = occurrence;
                break;
            }
        }
        if (selected == null && isRegistered(keyboardOwner)) {
            selected = keyboardOwner;
        }
        clearKeyboardExcept(selected);
        keyboardOwner = selected;
    }

    /** 无焦点时只为 Tab/ESC 选择一个 scope owner，不把普通 key/text 广播给多个 occurrence。 */
    private Occurrence findKeyboardFallback(SceneInputFrame frame) {
        boolean tab = false;
        boolean escape = false;
        for (SceneKeyEvent event : frame.getKeyEvents()) {
            if (event.getAction() != SceneKeyAction.RELEASED && event.getKey() == SceneKey.TAB) {
                tab = true;
            }
            if (event.getAction() != SceneKeyAction.RELEASED && event.getKey() == SceneKey.ESCAPE) {
                escape = true;
            }
        }
        if (!tab && !escape) {
            return null;
        }
        for (int i = bottomFirst.size() - 1; i >= 0; i--) {
            Occurrence occurrence = bottomFirst.get(i);
            SceneInputRouter router = occurrence.runtime.getInputRouter();
            if ((tab && router.hasFocusableInCurrentTabScope(occurrence.root))
                    || (escape && router.hasEscapeDismissTarget())) {
                return occurrence;
            }
        }
        return null;
    }

    private boolean isRegistered(Occurrence occurrence) {
        return occurrence != null && bottomFirst.contains(occurrence);
    }

    private void setHoverOwner(Occurrence nextOwner) {
        if (hoverOwner != null && hoverOwner != nextOwner) {
            hoverOwner.runtime.getInputRouter().clearHoverForComposition();
        }
        hoverOwner = nextOwner;
    }

    /** terminal 没有 MOVE handler 时仍按末次指针位置重建 hover，不派发 MOVE。 */
    private void reconcileHoverOnly() {
        if (closed) {
            return;
        }
        ScenePointerEvent probe = new ScenePointerEvent(
                ScenePointerAction.MOVE,
                lastPointerX, lastPointerY,
                SceneMouseButton.NONE, 0, 0, 0,
                lastControlDown, lastShiftDown, lastAltDown, lastMetaDown,
                lastFrameTimeNanos);
        Occurrence winner = findWinner(probe);
        setHoverOwner(winner);
        if (winner != null) {
            winner.runtime.getInputRouter().reconcileHoverForComposition(
                    winner.root, lastPointerX, lastPointerY, winner.absX, winner.absY);
        }
    }

    private static SceneInputFrame pointerFrame(SceneInputFrame sourceFrame, ScenePointerEvent event) {
        return new SceneInputFrame(
                Collections.<SceneKeyEvent>emptyList(),
                Collections.singletonList(event),
                Collections.<SceneTextEvent>emptyList(),
                sourceFrame.getPointerX(), sourceFrame.getPointerY(),
                sourceFrame.isControlDown(), sourceFrame.isShiftDown(),
                sourceFrame.isAltDown(), sourceFrame.isMetaDown(),
                sourceFrame.getFrameTimeNanos());
    }

    /** CANCEL/hover reconcile 只能观察已经开始派发的 pointer event，不能偷看同帧后续事件。 */
    private void rememberPointerEvent(ScenePointerEvent event) {
        lastPointerX = event.getLogicalX();
        lastPointerY = event.getLogicalY();
        lastControlDown = event.isControlDown();
        lastShiftDown = event.isShiftDown();
        lastAltDown = event.isAltDown();
        lastMetaDown = event.isMetaDown();
        lastFrameTimeNanos = event.getTimeNanos();
    }

    /** 指针事件完整派发后再接受 native frame 的粘滞尾状态，供键盘 handler 内关闭使用。 */
    private void rememberFrameTail(SceneInputFrame frame) {
        lastPointerX = frame.getPointerX();
        lastPointerY = frame.getPointerY();
        lastControlDown = frame.isControlDown();
        lastShiftDown = frame.isShiftDown();
        lastAltDown = frame.isAltDown();
        lastMetaDown = frame.isMetaDown();
        if (!frame.isEmpty()) {
            lastFrameTimeNanos = frame.getFrameTimeNanos();
        }
    }

    private SceneInputFrame terminalFrame(ScenePointerEvent event) {
        return new SceneInputFrame(
                Collections.<SceneKeyEvent>emptyList(),
                Collections.singletonList(event),
                Collections.<SceneTextEvent>emptyList(),
                lastPointerX, lastPointerY,
                lastControlDown, lastShiftDown, lastAltDown, lastMetaDown,
                lastFrameTimeNanos);
    }

    /** handler 内 close 只置 pending；待 Router 完成 pressed/capture 收口后再发送 CANCEL 与 dispose。 */
    private void routeOccurrence(Occurrence occurrence, SceneInputFrame frame) {
        Throwable[] failure = new Throwable[1];
        routeDepth++;
        try {
            occurrence.routeDirect(frame);
        } catch (Throwable routeFailure) {
            rememberFailure(failure, routeFailure);
        } finally {
            routeDepth--;
            if (routeDepth == 0 && closePending) {
                try {
                    performClose();
                } catch (Throwable closeFailure) {
                    rememberFailure(failure, closeFailure);
                }
            }
        }
        rethrow(failure[0]);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (routeDepth > 0) {
            closePending = true;
            return;
        }
        performClose();
    }

    private void performClose() {
        if (closing) {
            return;
        }
        closing = true;
        closePending = false;
        Throwable[] firstFailure = new Throwable[1];
        List<Occurrence> occurrences = new ArrayList<Occurrence>(bottomFirst);
        Occurrence activeGesture = gestureOwner;
        SceneMouseButton activeButton = gestureButton;
        gestureOwner = null;
        gestureButton = SceneMouseButton.NONE;
        keyboardOwner = null;
        suppressedButtons.clear();
        try {
            if (activeGesture != null) {
                ScenePointerEvent cancel = new ScenePointerEvent(
                        ScenePointerAction.CANCEL,
                        lastPointerX, lastPointerY,
                        activeButton, 0, 0, 0,
                        lastControlDown, lastShiftDown, lastAltDown, lastMetaDown,
                        lastFrameTimeNanos);
                try {
                    routeOccurrence(activeGesture, terminalFrame(cancel));
                } catch (Throwable failure) {
                    rememberFailure(firstFailure, failure);
                }
            }
            hoverOwner = null;
            for (Occurrence occurrence : occurrences) {
                try {
                    occurrence.runtime.getInputRouter().clearHoverForComposition();
                    occurrence.runtime.getInputRouter().clearFocus();
                } catch (Throwable failure) {
                    rememberFailure(firstFailure, failure);
                }
            }
            for (Occurrence occurrence : occurrences) {
                try {
                    occurrence.dispose();
                } catch (Throwable failure) {
                    rememberFailure(firstFailure, failure);
                }
            }
            bottomFirst.clear();
        } finally {
            closing = false;
        }
        rethrow(firstFailure[0]);
    }

    private static void disposeProjectionResources(Owner owner, SceneRuntime runtime, Throwable primaryFailure) {
        try {
            owner.dispose();
        } catch (Throwable cleanupFailure) {
            if (cleanupFailure != primaryFailure) {
                primaryFailure.addSuppressed(cleanupFailure);
            }
        }
        try {
            runtime.dispose();
        } catch (Throwable cleanupFailure) {
            if (cleanupFailure != primaryFailure) {
                primaryFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static void rememberFailure(Throwable[] firstFailure, Throwable failure) {
        if (firstFailure[0] == null) {
            firstFailure[0] = failure;
        } else if (firstFailure[0] != failure) {
            firstFailure[0].addSuppressed(failure);
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("composition close failed", failure);
    }

    /** 单次投放的 live scene、runtime 与 host placement。 */
    static final class Occurrence {
        private final Owner owner;
        private final SceneRuntime runtime;
        private final SceneNode root;
        private final int absX;
        private final int absY;

        private Occurrence(Owner owner, SceneRuntime runtime, SceneNode root, int absX, int absY) {
            this.owner = owner;
            this.runtime = runtime;
            this.root = root;
            this.absX = absX;
            this.absY = absY;
        }

        private void routeDirect(SceneInputFrame frame) {
            runtime.route(root, frame, absX, absY);
        }

        private void dispose() {
            Throwable[] failure = new Throwable[1];
            try {
                owner.dispose();
            } catch (Throwable ownerFailure) {
                rememberFailure(failure, ownerFailure);
            }
            try {
                runtime.dispose();
            } catch (Throwable runtimeFailure) {
                rememberFailure(failure, runtimeFailure);
            }
            rethrow(failure[0]);
        }

        SceneRuntime runtime() {
            return runtime;
        }

        SceneNode root() {
            return root;
        }
    }
}
