package club.heiqi.uilib.ui.host.minecraft;

import java.util.List;
import club.heiqi.uilib.host.client.extension.MinecraftHostExtension;
import club.heiqi.uilib.host.client.extension.MinecraftHostExtensions;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;

/** 增强原生输入的中性生命周期；外部实现只复制事实，Core维护焦点和组字。 */
public final class SceneNativeTextInput implements AutoCloseable {
    public interface Port extends AutoCloseable {
        boolean active();
        void textInput(boolean enabled);
        void availability(boolean available);
        List<NativeEvent> drain();
        void close();
    }
    private final Port port;
    public static boolean supported() {
        for (MinecraftHostExtension service : MinecraftHostExtensions.services())
            if (service.nativeInputSupported(SceneNativeTextInput.class.getClassLoader())) return true;
        return false;
    }
    public static boolean available() {
        for (MinecraftHostExtension service : MinecraftHostExtensions.services())
            if (service.nativeInputAvailable(SceneNativeTextInput.class.getClassLoader())) return true;
        return false;
    }
    public SceneNativeTextInput() {
        Port found = null;
        for (MinecraftHostExtension service : MinecraftHostExtensions.services()) {
            found = service.nativeInput(SceneNativeTextInput.class.getClassLoader());
            if (found != null) break;
        }
        if (found == null) throw new IllegalStateException("增强原生输入服务缺席");
        port = found;
    }
    public boolean active() { return port.active(); }
    public void textInput(boolean enabled) { port.textInput(enabled); }
    public void availability(boolean available) { port.availability(available); }
    public List<NativeEvent> drain() { return port.drain(); }
    public void close() { port.close(); }

    /** 自包含不可变事实；组字在采集时关联来源，drain不读取焦点或改写其所属联系。
     * pointer/focused/edit各自非空时表示对应事实，其余为键事实。 */
    public static final class NativeEvent {
        public final int key;
        public final boolean down, repeat;
        public final SceneEditEvent edit;
        public final ScenePointerEvent pointer;
        public final Boolean focused;
        public final boolean preedit;

        public NativeEvent(int key, boolean down, boolean repeat, SceneEditEvent edit) {
            this(key, down, repeat, edit, null, null);
        }
        public NativeEvent(int key, boolean down, boolean repeat, SceneEditEvent edit,
                ScenePointerEvent pointer, Boolean focused) {
            this(key, down, repeat, edit, pointer, focused, false);
        }
        public NativeEvent(int key, boolean down, boolean repeat, SceneEditEvent edit,
                ScenePointerEvent pointer, Boolean focused, boolean preedit) {
            this.key = key; this.down = down; this.repeat = repeat;
            this.edit = edit; this.pointer = pointer; this.focused = focused; this.preedit = preedit;
        }
        public static NativeEvent edit(SceneEditEvent edit) { return new NativeEvent(0, false, false, edit); }
    }

}
