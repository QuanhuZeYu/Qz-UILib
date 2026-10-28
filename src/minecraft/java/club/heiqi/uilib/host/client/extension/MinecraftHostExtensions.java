package club.heiqi.uilib.host.client.extension;

import java.util.ServiceLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.font.context.LwjglFontContextCapture;

/** 通过 ServiceLoader 发现中性宿主 SPI，具体 Mod 识别与版本选择归外部扩展。 */
public final class MinecraftHostExtensions {
    private static final List<MinecraftHostExtension> SERVICES = discover();
    private static final java.util.function.IntUnaryOperator IDENTITY = value -> value;
    private MinecraftHostExtensions() {}
    private static List<MinecraftHostExtension> discover() {
        List<MinecraftHostExtension> result = new ArrayList<>();
        for (MinecraftHostExtension service : ServiceLoader.load(MinecraftHostExtension.class,
                MinecraftHostExtensions.class.getClassLoader())) result.add(service);
        return Collections.unmodifiableList(result);
    }
    public static List<MinecraftHostExtension> services() { return SERVICES; }
    /** 服务顺序与其他宿主能力一致；空缺时共用稳定identity实例。 */
    public static java.util.function.IntUnaryOperator textMapping() {
        for (MinecraftHostExtension service : SERVICES) {
            java.util.function.IntUnaryOperator mapping = service.textMapping();
            if (mapping != null) return mapping;
        }
        return IDENTITY;
    }
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
