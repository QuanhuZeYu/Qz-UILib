package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Snapshot;
import club.heiqi.uilib.ui.scene.edit.SceneEditState.Submission;
import club.heiqi.uilib.ui.scene.control.SceneTextField.SubmitPolicy;
import java.util.Objects;
import java.util.function.Consumer;

/** 字段共同的草稿命令、IME投影及提交协调；视觉导航和单多行Enter规则归控件。 */
final class SceneEditCommands {
    private final SceneEditState state;
    private final ReadableSignal<Boolean> readOnly;
    private final Consumer<Submission> onSubmit;
    private final SubmitPolicy policy;

    SceneEditCommands(SceneEditState state, ReadableSignal<Boolean> readOnly,
            Consumer<Submission> onSubmit, SubmitPolicy policy) {
        this.state = state;
        this.readOnly = readOnly;
        this.onSubmit = onSubmit;
        this.policy = policy;
    }

    static String display(Snapshot value) {
        if (!value.composing) return value.text;
        int start = Math.min(value.anchor, value.caret), end = Math.max(value.anchor, value.caret);
        return value.text.substring(0, start) + value.composition + value.text.substring(end);
    }

    static int toDisplay(Snapshot value, int offset) {
        if (!value.composing) return offset;
        int start = Math.min(value.anchor, value.caret), end = Math.max(value.anchor, value.caret);
        return offset < start
                ? offset
                : offset <= end
                        ? start + value.composition.length()
                        : offset - (end - start) + value.composition.length();
    }

    static int fromDisplay(Snapshot value, int offset) {
        if (!value.composing) return offset;
        int start = Math.min(value.anchor, value.caret), end = Math.max(value.anchor, value.caret);
        int preeditEnd = start + value.composition.length();
        return offset <= start
                ? offset
                : offset < preeditEnd ? start : offset - value.composition.length() + end - start;
    }

    static void copy(Snapshot value, SceneEditEvent event) {
        if (event.clipboard != null)
            event.clipboard.write(
                    value.text.substring(
                            Math.min(value.anchor, value.caret),
                            Math.max(value.anchor, value.caret)));
    }

    void inputChanged(Snapshot before) {
        if (policy == SubmitPolicy.INPUT && !before.text.equals(state.snapshot().get().text))
            onSubmit.accept(state.request(false));
    }

    void submit() {
        state.breakHistoryGroup();
        onSubmit.accept(state.request(true));
    }

    void blur(boolean eligible) {
        Snapshot before = state.snapshot().get();
        state.breakHistoryGroup();
        state.composition("", false);
        // 真实用户离开才可提交；失活、模态隔离、卸载不改变独立草稿权威。
        if (eligible && policy == SubmitPolicy.BLUR && before.active && !readOnly.get())
            onSubmit.accept(state.request(true));
    }

    /** 来源文本与候选组字优先；各模式只决定本地导航清理，不复制草稿/IME协议。 */
    boolean input(Snapshot before, SceneEditEvent event, Runnable beforeInput) {
        boolean locked = readOnly.get();
        if (event.compositionEvent) {
            beforeInput.run();
            if (!locked || !event.composing) state.composition(event.text, event.composing);
            return true;
        }
        if (event.text != null) {
            beforeInput.run();
            if (!locked) {
                if (before.composing) state.composition("", false);
                state.replaceSelection(event.text, !event.atomic && !before.composing);
                inputChanged(before);
            }
            return true;
        }
        // 方向/删除/空格等候选键归IME，不同时修改已确认草稿或提交字段。
        return event.command != null && before.composing && !locked
                && event.command != SceneEditEvent.Command.ESCAPE;
    }

    boolean command(Snapshot before, SceneEditEvent event, Runnable beforeMutation, String language) {
        boolean locked = readOnly.get();
        switch (event.command) {
            case WORD_LEFT:
            case WORD_RIGHT:
                beforeMutation.run();
                state.moveWord(event.command == SceneEditEvent.Command.WORD_LEFT ? -1 : 1,
                        event.shift, language);
                return true;
            case WORD_BACKSPACE:
            case WORD_DELETE:
                if (locked) return true;
                beforeMutation.run();
                state.deleteWord(event.command == SceneEditEvent.Command.WORD_BACKSPACE ? -1 : 1,
                        language);
                inputChanged(before);
                return true;
            case COPY:
                copy(before, event);
                return true;
            case CUT:
            case PASTE:
                if (locked || event.clipboard == null) return true;
                beforeMutation.run();
                if (event.command == SceneEditEvent.Command.CUT) {
                    copy(before, event);
                    state.replaceSelection("", false);
                } else {
                    state.composition("", false);
                    state.replaceSelection(Objects.requireNonNull(event.clipboard.read(), "clipboard text"), false);
                }
                inputChanged(before);
                return true;
            case BACKSPACE:
            case DELETE:
            case UNDO:
            case REDO:
                if (locked) return true;
                beforeMutation.run();
                switch (event.command) {
                    case BACKSPACE: state.deleteBackward(); break;
                    case DELETE: state.deleteForward(); break;
                    case UNDO: state.undo(); break;
                    case REDO: state.redo(); break;
                    default: throw new AssertionError(event.command);
                }
                inputChanged(before);
                return true;
            default:
                return false;
        }
    }
}
