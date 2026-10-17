package club.heiqi.uilib.host.forge112;

import club.heiqi.uilib.host.client.gl.Lwjgl2GlApi;
import club.heiqi.uilib.host.embedded.EmbeddedSceneServices;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;

/** 应用服务在首次打开页面时取得 GL；资源重载和退出均在 Minecraft 客户端线程处理。 */
public final class Forge112Client {
    private static EmbeddedSceneServices services;
    private static Thread thread;
    private static boolean installed;
    private Forge112Client() {}

    static void install() {
        if (installed) return;
        installed = true;
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(manager -> {
            if (services != null) services.resourcesReloaded();
        });
    }

    public static EmbeddedSceneServices services() {
        if (services != null) { checkThread(); return services; }
        Minecraft minecraft = Minecraft.getMinecraft();
        thread = Thread.currentThread();
        try {
            services = EmbeddedSceneServices.open(minecraft.mcDataDir.toPath().resolve("config/qz_uilib.yml"),
                    Forge112Client.class.getClassLoader(), IsolatedFontEngine.shared(), new Lwjgl2GlApi(),
                    id -> minecraft.getResourceManager().getResource(new ResourceLocation(id)).getInputStream());
            return services;
        } catch (IOException failure) { throw new UncheckedIOException("UILib 宿主服务初始化失败", failure); }
    }

    private static void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("UILib 服务必须在客户端 GL 线程访问");
    }

    /** 由本包 coremod 在 Display.destroy 之前调用；不在 JVM shutdown hook 上操作 GL。 */
    public static void close() {
        if (services == null) return;
        checkThread();
        EmbeddedSceneServices closing = services;
        services = null;
        try (EmbeddedSceneServices owned = closing) {
            if (Minecraft.getMinecraft().currentScreen instanceof Minecraft112SceneScreen)
                Minecraft.getMinecraft().currentScreen.onGuiClosed();
        }
    }
}
