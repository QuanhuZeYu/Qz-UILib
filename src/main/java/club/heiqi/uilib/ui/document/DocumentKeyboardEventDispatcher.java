package club.heiqi.uilib.ui.document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjglx.input.Keyboard;

import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTextInputHandler;
import club.heiqi.uilib.ui.dom.DocumentEventControl;
import club.heiqi.uilib.ui.dom.DocumentEventPhase;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;

/**
 * HTML-like 文档键盘与文本输入事件分发器。
 *
 * <p>承载 key/textinput 传播以及 raw button 的最小浏览器式默认键盘行为。</p>
 */
final class DocumentKeyboardEventDispatcher {

    private final Host host;
    private final Map<Long, Boolean> rawButtonSpacePressed = new HashMap<Long, Boolean>();

    DocumentKeyboardEventDispatcher(Host host) {
        this.host = host;
    }

    /**
     * 分发 key 事件；若未被消费，则执行 raw button 默认键盘行为。
     *
     * @param target 当前焦点元素
     * @param event 原始键盘事件
     * @return key 事件传播是否被停止
     */
    boolean dispatchKeyAndDefault(ElementNode target, UiKeyEvent event) {
        if (event == null || target == null) {
            return false;
        }
        KeyDispatchResult dispatchResult = dispatchKey(target, event);
        if (!dispatchResult.isDefaultPrevented()) {
            dispatchNativeButtonDefaultKeyBehavior(target, event);
        } else if (dispatchResult.isDefaultPrevented()) {
            clearPreventedNativeButtonDefaultState(target, event);
        }
        return dispatchResult.isPropagationStopped();
    }

    /**
     * 分发文本输入事件。
     *
     * @param target 当前焦点元素
     * @param event 原始文本输入事件
     * @return 文本输入是否被消费
     */
    boolean dispatchTextInput(ElementNode target, UiTextInputEvent event) {
        if (target == null || event == null) {
            return false;
        }
        if (target.isDisabled()) {
            return true;
        }
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            DocumentElementTextInputHandler textInputHandler = currentElement.getTextInputHandler();
            if (textInputHandler == null) {
                continue;
            }
            if (textInputHandler.onTextInput(new DocumentElementTextInputEvent(target, currentElement, event))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 清理 raw button 的 Space 按下状态。
     *
     * @param element 待清理元素
     */
    void clearNativeButtonState(ElementNode element) {
        if (element != null) {
            rawButtonSpacePressed.remove(element.__getElementUid());
        }
    }

    private KeyDispatchResult dispatchKey(ElementNode target, UiKeyEvent event) {
        if (target == null || event == null) {
            return KeyDispatchResult.notConsumed();
        }
        DocumentEventControl eventControl = new DocumentEventControl();
        List<ElementNode> path = buildAncestorPath(target);

        eventControl.setEventPhase(DocumentEventPhase.CAPTURING);
        for (int index = path.size() - 1; index > 0; index--) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementKeyHandler captureHandler = currentElement.getCaptureKeyHandler();
            if (captureHandler != null) {
                DocumentElementKeyEvent keyEvent = new DocumentElementKeyEvent(target, currentElement, event,
                        eventControl);
                if (captureHandler.onKey(keyEvent)) {
                    eventControl.stopPropagation();
                    applyPendingFocus(keyEvent);
                }
            }
        }

        if (!eventControl.isPropagationStopped()) {
            eventControl.setEventPhase(DocumentEventPhase.AT_TARGET);
            DocumentElementKeyHandler targetCaptureHandler = target.getCaptureKeyHandler();
            if (targetCaptureHandler != null) {
                DocumentElementKeyEvent keyEvent = new DocumentElementKeyEvent(target, target, event, eventControl);
                if (targetCaptureHandler.onKey(keyEvent)) {
                    eventControl.stopPropagation();
                    applyPendingFocus(keyEvent);
                }
            }
            if (!eventControl.isImmediatePropagationStopped()) {
                DocumentElementKeyHandler targetHandler = target.getKeyHandler();
                if (targetHandler != null) {
                    DocumentElementKeyEvent keyEvent = new DocumentElementKeyEvent(target, target, event,
                            eventControl);
                    if (targetHandler.onKey(keyEvent)) {
                        eventControl.stopPropagation();
                        applyPendingFocus(keyEvent);
                    }
                }
            }
        }

        eventControl.setEventPhase(DocumentEventPhase.BUBBLING);
        for (int index = 1; index < path.size(); index++) {
            if (eventControl.isPropagationStopped()) {
                break;
            }
            ElementNode currentElement = path.get(index);
            DocumentElementKeyHandler keyHandler = currentElement.getKeyHandler();
            if (keyHandler == null) {
                continue;
            }
            DocumentElementKeyEvent keyEvent = new DocumentElementKeyEvent(target, currentElement, event,
                    eventControl);
            if (keyHandler.onKey(keyEvent)) {
                eventControl.stopPropagation();
                applyPendingFocus(keyEvent);
            }
        }
        return new KeyDispatchResult(eventControl.isPropagationStopped(), eventControl.isDefaultPrevented());
    }

    /**
     * raw button 默认键盘行为：Enter 直接触发 click，Space pressed 进入 active，Space released 触发 click。
     */
    private void dispatchNativeButtonDefaultKeyBehavior(ElementNode target, UiKeyEvent event) {
        if (target == null || !"button".equals(target.getTagName())) {
            return;
        }
        if (target.isDisabled()) {
            rawButtonSpacePressed.remove(target.__getElementUid());
            return;
        }
        int keyCode = event.getKeyCode();
        boolean isEnter = keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER;
        boolean isSpace = keyCode == Keyboard.KEY_SPACE;
        if (!isEnter && !isSpace) {
            return;
        }
        long uid = target.__getElementUid();
        if (isEnter && event.getAction() == UiKeyEvent.Action.PRESSED) {
            dispatchRawButtonClick(target, event.getTimeNanos());
            return;
        }
        if (isSpace && event.getAction() == UiKeyEvent.Action.PRESSED) {
            rawButtonSpacePressed.put(uid, Boolean.TRUE);
            return;
        }
        if (isSpace && event.getAction() == UiKeyEvent.Action.RELEASED) {
            Boolean pressed = rawButtonSpacePressed.remove(uid);
            if (Boolean.TRUE.equals(pressed)) {
                dispatchRawButtonClick(target, event.getTimeNanos());
            }
        }
    }

    private void clearPreventedNativeButtonDefaultState(ElementNode target, UiKeyEvent event) {
        if (target == null || event == null || !"button".equals(target.getTagName())) {
            return;
        }
        if (event.getKeyCode() == Keyboard.KEY_SPACE) {
            rawButtonSpacePressed.remove(target.__getElementUid());
        }
    }

    private void dispatchRawButtonClick(ElementNode target, long timeNanos) {
        if (target != null) {
            host.dispatchSyntheticClick(target, timeNanos);
        }
    }

    private void applyPendingFocus(DocumentElementKeyEvent keyEvent) {
        ElementNode pendingFocus = keyEvent.getPendingFocusTarget();
        if (pendingFocus != null) {
            host.focusElement(pendingFocus, keyEvent.isPendingFocusVisible());
        }
    }

    private static List<ElementNode> buildAncestorPath(ElementNode target) {
        List<ElementNode> path = new ArrayList<ElementNode>();
        for (DocumentNode current = target; current instanceof ElementNode; current = current.getParent()) {
            path.add((ElementNode) current);
        }
        return path;
    }

    private static final class KeyDispatchResult {

        private final boolean propagationStopped;
        private final boolean defaultPrevented;

        private KeyDispatchResult(boolean propagationStopped, boolean defaultPrevented) {
            this.propagationStopped = propagationStopped;
            this.defaultPrevented = defaultPrevented;
        }

        private static KeyDispatchResult notConsumed() {
            return new KeyDispatchResult(false, false);
        }

        private boolean isPropagationStopped() {
            return propagationStopped;
        }

        private boolean isDefaultPrevented() {
            return defaultPrevented;
        }
    }

    /** 键盘分发器需要从 widget 借用的最小能力集合。 */
    interface Host {

        /** 根据 key handler 请求切换焦点。 */
        void focusElement(ElementNode element, boolean focusVisible);

        /** 通过完整 click 分发链触发合成点击。 */
        void dispatchSyntheticClick(ElementNode element, long timeNanos);
    }
}
