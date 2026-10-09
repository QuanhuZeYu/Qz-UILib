package club.heiqi.uilib.ui.scene.input;
import java.util.Objects;
/** 宿主递交真实文本/组字或编辑命令；不持焦点目标，沿页面唯一输入序列。 */
public final class SceneEditEvent {
    public enum Command { LEFT, RIGHT, HOME, END, BACKSPACE, DELETE, SELECT_ALL, COPY, CUT, PASTE, UNDO, REDO, CONFIRM, SPACE, ACTIVATE_ONLY, TAB, ESCAPE, UP, DOWN, DOCUMENT_HOME, DOCUMENT_END, SUBMIT }
    public interface Clipboard { String read(); void write(String value); }
    public final Command command; public final String text;
    public final boolean shift, composing, compositionEvent, atomic; public final Clipboard clipboard;
    private SceneEditEvent(Command command,String text,boolean shift,boolean composing,Clipboard clipboard,boolean compositionEvent,boolean atomic){this.command=command;this.text=text;this.shift=shift;this.composing=composing;this.clipboard=clipboard;this.compositionEvent=compositionEvent;this.atomic=atomic;}
    public static SceneEditEvent text(String value){return new SceneEditEvent(null,Objects.requireNonNull(value,"text"),false,false,null,false,false);}
    /** 宿主一次完整字符串提交（如IME）是独立撤销步骤，不猜测字符来源。 */
    public static SceneEditEvent committed(String value){return new SceneEditEvent(null,Objects.requireNonNull(value,"text"),false,false,null,false,true);}
    public static SceneEditEvent composition(String value,boolean active){return new SceneEditEvent(null,Objects.requireNonNull(value,"text"),false,active,null,true,false);}
    public static SceneEditEvent command(Command value,boolean shift,Clipboard clipboard){return new SceneEditEvent(Objects.requireNonNull(value,"command"),null,shift,false,clipboard,false,false);}
}
