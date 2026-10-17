package club.heiqi.uilib.host.embedded;

import java.util.Set;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.host.SceneEditInputAdapter;
import club.heiqi.uilib.ui.scene.host.SceneEscapeInputAdapter;
import club.heiqi.uilib.ui.scene.host.SceneKeyPressInputAdapter;
import club.heiqi.uilib.ui.scene.host.SceneTabInputAdapter;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;

/** 版本输入只提供语义键、物理段和历史耗尽事实；焦点、范围、手势仍归正式session。 */
public final class EmbeddedSceneInputs {
    public enum Key { OTHER, TAB, ENTER, KEYPAD_ENTER, SPACE, ESCAPE, LEFT_SHIFT, RIGHT_SHIFT,
        LEFT, RIGHT, UP, DOWN, HOME, END, PAGE_UP, PAGE_DOWN, BACKSPACE, DELETE, A, C, X, V, Z, Y }
    private final ScenePageSession page;
    private final SceneEditEvent.Clipboard clipboard;
    private final SceneEditInputAdapter edits = new SceneEditInputAdapter();
    private final SceneTabInputAdapter tab;
    private final SceneEscapeInputAdapter escape;
    private final SceneKeyPressInputAdapter enter, keypadEnter, space;
    private boolean active, pointerSuspended = true, control, shift;
    private char pendingHigh;
    private Object pendingOwner;

    public EmbeddedSceneInputs(ScenePageSession page, SceneEditEvent.Clipboard clipboard, Runnable closeRequest) {
        this.page = java.util.Objects.requireNonNull(page, "page");
        this.clipboard = java.util.Objects.requireNonNull(clipboard, "clipboard");
        tab = new SceneTabInputAdapter(direction -> page.editOrNavigate(
                SceneEditEvent.command(SceneEditEvent.Command.TAB, direction == SceneNavigationEvent.PREVIOUS, clipboard), direction));
        escape = new SceneEscapeInputAdapter(() -> page.escape(closeRequest));
        enter = new SceneKeyPressInputAdapter(this::confirm);
        keypadEnter = new SceneKeyPressInputAdapter(this::confirm);
        space = new SceneKeyPressInputAdapter(() -> page.editOrNavigate(
                SceneEditEvent.command(SceneEditEvent.Command.ACTIVATE_ONLY, shift, clipboard), SceneNavigationEvent.ACTIVATE));
    }
    private void confirm() {
        page.editOrNavigate(SceneEditEvent.command(control ? SceneEditEvent.Command.SUBMIT : SceneEditEvent.Command.CONFIRM,
                shift, clipboard), SceneNavigationEvent.ACTIVATE);
    }
    public void availability(boolean available) {
        if (active && !available) { page.focusLost(); pointerSuspended = true; pendingHigh = 0; }
        active = available;
        edits.availability(available); tab.availability(available); escape.availability(available);
        enter.availability(available); keypadEnter.availability(available); space.availability(available);
    }
    /** 消费者完成本批历史后调用，不能用采集前的未来物理状态越过旧尾部。 */
    public void historyDrained(Set<Integer> physical, Set<Key> special, boolean buttonsReleased) {
        edits.historyDrained(physical);
        tab.historyDrained(special.contains(Key.TAB), special.contains(Key.LEFT_SHIFT), special.contains(Key.RIGHT_SHIFT));
        escape.historyDrained(special.contains(Key.ESCAPE));
        enter.historyDrained(special.contains(Key.ENTER));
        keypadEnter.historyDrained(special.contains(Key.KEYPAD_ENTER));
        space.historyDrained(special.contains(Key.SPACE));
        if (active && pointerSuspended && buttonsReleased) { page.pointer(ScenePointerEvent.allButtonsReleased()); pointerSuspended = false; }
    }
    public void pointer(ScenePointerEvent event) {
        pendingHigh = 0;
        if (active && !pointerSuspended || event.kind == ScenePointerEvent.Kind.UP) page.pointer(event);
    }
    public void key(int physical, Key key, boolean down, boolean repeat, boolean control, boolean shift) {
        this.control = control; this.shift = shift;
        boolean accepted = edits.key(physical, down, repeat);
        switch (key) {
            case LEFT_SHIFT: tab.shift(true, down); return;
            case RIGHT_SHIFT: tab.shift(false, down); return;
            case TAB: pendingHigh = 0; tab.tab(down, repeat); return;
            case ENTER: pendingHigh = 0; enter.key(down, repeat); return;
            case KEYPAD_ENTER: pendingHigh = 0; keypadEnter.key(down, repeat); return;
            case ESCAPE: pendingHigh = 0; escape.key(down, repeat); return;
            case SPACE: pendingHigh = 0; space.key(down, repeat); return;
            default: break;
        }
        if (!accepted) return;
        SceneEditEvent.Command command = command(key, control, shift);
        if (command != null) { pendingHigh = 0; page.edit(SceneEditEvent.command(command, shift, clipboard)); }
    }
    private static SceneEditEvent.Command command(Key key, boolean control, boolean shift) {
        if (control) switch (key) {
            case LEFT: return SceneEditEvent.Command.WORD_LEFT;
            case RIGHT: return SceneEditEvent.Command.WORD_RIGHT;
            case BACKSPACE: return SceneEditEvent.Command.WORD_BACKSPACE;
            case DELETE: return SceneEditEvent.Command.WORD_DELETE;
            case HOME: return SceneEditEvent.Command.DOCUMENT_HOME;
            case END: return SceneEditEvent.Command.DOCUMENT_END;
            case A: return SceneEditEvent.Command.SELECT_ALL;
            case C: return SceneEditEvent.Command.COPY;
            case X: return SceneEditEvent.Command.CUT;
            case V: return SceneEditEvent.Command.PASTE;
            case Z: return shift ? SceneEditEvent.Command.REDO : SceneEditEvent.Command.UNDO;
            case Y: return SceneEditEvent.Command.REDO;
            default: break;
        }
        switch (key) {
            case LEFT: return SceneEditEvent.Command.LEFT;
            case RIGHT: return SceneEditEvent.Command.RIGHT;
            case UP: return SceneEditEvent.Command.UP;
            case DOWN: return SceneEditEvent.Command.DOWN;
            case HOME: return SceneEditEvent.Command.HOME;
            case END: return SceneEditEvent.Command.END;
            case PAGE_UP: return SceneEditEvent.Command.PAGE_UP;
            case PAGE_DOWN: return SceneEditEvent.Command.PAGE_DOWN;
            case BACKSPACE: return SceneEditEvent.Command.BACKSPACE;
            case DELETE: return SceneEditEvent.Command.DELETE;
            default: return null;
        }
    }
    /** 字符事件与按键事件分开；UTF-16宿主可逐单元递交，代理对只留在同一编辑目标。 */
    public void character(char character) {
        if (!edits.available() || character < 32 || control) return;
        Object[] current = {null};
        page.runtime().domain().execute(() -> current[0] = page.runtime().focusRouter().focused().get());
        if (pendingOwner != current[0]) { pendingHigh = 0; pendingOwner = current[0]; }
        if (Character.isHighSurrogate(character)) { pendingHigh = character; return; }
        if (Character.isLowSurrogate(character)) {
            if (pendingHigh != 0) { page.edit(SceneEditEvent.text(new String(new char[] {pendingHigh, character}))); pendingHigh = 0; }
        } else { pendingHigh = 0; page.edit(SceneEditEvent.text(String.valueOf(character))); }
    }
    /** 原生整段文本入口复用相同物理段资格，组字身份由宿主桥继续递交核心。 */
    public boolean acceptsNativeText() { return edits.available() && !control; }
    public void text(String text) { for (int i = 0; i < text.length(); i++) character(text.charAt(i)); }
    public void composition(String text, boolean active) {
        pendingHigh = 0;
        if (edits.available()) page.edit(SceneEditEvent.composition(text, active));
    }
}
