package club.heiqi.uilib.ui.scene.input;

import java.util.Objects;

/** 宿主递交文本、带真实来源的组字或编辑命令，沿页面唯一输入序列。 */
public final class SceneEditEvent {
    public enum Command {
        LEFT,
        RIGHT,
        WORD_LEFT,
        WORD_RIGHT,
        WORD_BACKSPACE,
        WORD_DELETE,
        HOME,
        END,
        BACKSPACE,
        DELETE,
        SELECT_ALL,
        COPY,
        CUT,
        PASTE,
        UNDO,
        REDO,
        CONFIRM,
        SPACE,
        ACTIVATE_ONLY,
        TAB,
        ESCAPE,
        UP,
        DOWN,
        DOCUMENT_HOME,
        DOCUMENT_END,
        SUBMIT
    }

    public enum CompositionPhase {
        BEGIN, UPDATE, COMMIT, CANCEL
    }

    public interface Clipboard {
        String read();

        void write(String value);
    }

    public final Command command;
    public final String text;
    public final boolean shift, composing, compositionEvent, atomic;
    public final Clipboard clipboard;
    public final SceneCompositionSource compositionSource;
    public final CompositionPhase compositionPhase;

    private SceneEditEvent(
            Command command,
            String text,
            boolean shift,
            boolean composing,
            Clipboard clipboard,
            boolean compositionEvent,
            boolean atomic) {
        this(command, text, shift, composing, clipboard, compositionEvent, atomic, null, null);
    }

    private SceneEditEvent(
            Command command, String text, boolean shift, boolean composing, Clipboard clipboard,
            boolean compositionEvent, boolean atomic, SceneCompositionSource source,
            CompositionPhase phase) {
        this.compositionSource = source;
        this.compositionPhase = phase;
        this.command = command;
        this.text = text;
        this.shift = shift;
        this.composing = composing;
        this.clipboard = clipboard;
        this.compositionEvent = compositionEvent;
        this.atomic = atomic;
    }

    public static SceneEditEvent text(String value) {
        return new SceneEditEvent(
                null, Objects.requireNonNull(value, "text"), false, false, null, false, false);
    }

    /** 原子普通文本是独立撤销步骤；不表达组字来源或动态目标隔离。 */
    public static SceneEditEvent committed(String value) {
        return new SceneEditEvent(
                null, Objects.requireNonNull(value, "text"), false, false, null, false, true);
    }

    /** 旧宿主兼容入口缺少来源事实，不能承诺失效组字隔离；迁移到带来源的接口。 */
    @Deprecated
    public static SceneEditEvent composition(String value, boolean active) {
        return new SceneEditEvent(
                null, Objects.requireNonNull(value, "text"), false, active, null, true, false);
    }

    /** 真实 BEGIN 到达时绑定当时合法目标；source 只能开始一次。 */
    public static SceneEditEvent compositionBegin(SceneCompositionSource source, String text) {
        return sourced(source, text, true, CompositionPhase.BEGIN);
    }

    /** active=false 仅清 preedit 呈现；联系仍等待真实 COMMIT/CANCEL。 */
    public static SceneEditEvent compositionUpdate(
            SceneCompositionSource source, String text, boolean active) {
        return sourced(source, text, active, CompositionPhase.UPDATE);
    }

    public static SceneEditEvent compositionCommit(SceneCompositionSource source, String text) {
        return sourced(source, text, false, CompositionPhase.COMMIT);
    }

    public static SceneEditEvent compositionCancel(SceneCompositionSource source) {
        return sourced(source, "", false, CompositionPhase.CANCEL);
    }

    private static SceneEditEvent sourced(
            SceneCompositionSource source, String text, boolean active, CompositionPhase phase) {
        return new SceneEditEvent(
                null, Objects.requireNonNull(text, "text"), false, active, null,
                phase != CompositionPhase.COMMIT, phase == CompositionPhase.COMMIT,
                Objects.requireNonNull(source, "source"), phase);
    }

    public static SceneEditEvent command(Command value, boolean shift, Clipboard clipboard) {
        return new SceneEditEvent(
                Objects.requireNonNull(value, "command"),
                null,
                shift,
                false,
                clipboard,
                false,
                false);
    }
}
