package club.heiqi.uilib.host.client.extension;

import java.util.ServiceLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.font.context.LwjglFontContextCapture;

/** 仅发现正式SPI；主仓不包含任何可选Mod类名或版本判断。服务实例不持有GL上下文。 */
public final class MinecraftHostExtensions {
    private static final List<MinecraftHostExtension> SERVICES = discover();
    private MinecraftHostExtensions() {}
    private static List<MinecraftHostExtension> discover() {
        List<MinecraftHostExtension> result = new ArrayList<>();
        for (MinecraftHostExtension service : ServiceLoader.load(MinecraftHostExtension.class,
                MinecraftHostExtensions.class.getClassLoader())) result.add(service);
        return Collections.unmodifiableList(result);
    }
    public static List<MinecraftHostExtension> services() { return SERVICES; }
    public static LwjglFontContextCapture.MatrixStateProvider matrices(ClassLoader loader, GlApi gl) {
        for (MinecraftHostExtension service : SERVICES) {
            LwjglFontContextCapture.MatrixStateProvider value = service.matrices(loader, gl);
            if (value != null) return value;
        }
        return LwjglFontContextCapture.compatibility(gl);
    }
    public static GlProgramProvider programs(ClassLoader loader, GlApi gl) {
        for (MinecraftHostExtension service : SERVICES) {
            GlProgramProvider value = service.programs(loader, gl);
            if (value != null) return value;
        }
        return GlProgramProvider.NONE;
    }
}
