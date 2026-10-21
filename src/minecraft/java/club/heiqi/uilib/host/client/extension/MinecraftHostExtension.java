package club.heiqi.uilib.host.client.extension;

import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.font.compat.OptionalFontHostBridge;
import club.heiqi.uilib.host.client.font.context.LwjglFontContextCapture;
import club.heiqi.uilib.host.client.image.ItemStateScope;
import club.heiqi.uilib.ui.host.minecraft.SceneNativeTextInput;

/** 可选宿主只提供外部事实；布局、字体与GPU资源仍由UILib拥有。返回null表示该能力缺席。 */
public interface MinecraftHostExtension {
    default OptionalFontHostBridge fontBridge(ClassLoader loader, GlApi gl,
            OptionalFontHostBridge.ReplaySink sink, int capacity) { return null; }
    default LwjglFontContextCapture.MatrixStateProvider matrices(ClassLoader loader, GlApi gl) { return null; }
    default GlProgramProvider programs(ClassLoader loader, GlApi gl) { return null; }
    default ItemStateScope.Port itemPort(ClassLoader loader) { return null; }
    default Runnable captureItemContext(ClassLoader loader) { return null; }
    default boolean nativeInputSupported(ClassLoader loader) { return false; }
    default boolean nativeInputAvailable(ClassLoader loader) { return false; }
    default SceneNativeTextInput.Port nativeInput(ClassLoader loader) { return null; }
}
