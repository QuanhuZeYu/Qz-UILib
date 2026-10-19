package club.heiqi.uilib.host.forge116;

import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;
import club.heiqi.uilib.host.embedded.EmbeddedSceneServices;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.resource.ResourceManagerFontSources;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraft.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** 惰性应用服务；资源与退出回调在 Minecraft 的呈现线程执行。 */
public final class Forge116Client {
    private static EmbeddedSceneServices services;
    private static ResourceManagerFontSources fontSources;
    private static Thread thread;
    private Forge116Client() {}

    public static void install() {
        net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus().addListener(Forge116Client::initialize);
    }
    private static void initialize(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ((IReloadableResourceManager) Minecraft.getInstance().getResourceManager())
                .addReloadListener((IResourceManagerReloadListener) manager -> {
                    if (services != null) { fontSources.resourcesReloaded(); services.resourcesReloaded(); }
                }));
    }
    public static EmbeddedSceneServices services() {
        if (services != null) { checkThread(); return services; }
        Minecraft minecraft = Minecraft.getInstance();
        thread = Thread.currentThread();
        try {
            fontSources = new ResourceManagerFontSources(Forge116Client.class.getClassLoader(),
                    id -> minecraft.getResourceManager().getResource(new ResourceLocation(id)).getInputStream());
            services = EmbeddedSceneServices.open(minecraft.gameDir.toPath().resolve("config/qz_uilib.yml"),
                    Forge116Client.class.getClassLoader(), IsolatedFontEngine.shared(), new Lwjgl3GlApi(),
                    id -> minecraft.getResourceManager().getResource(new ResourceLocation(id)).getInputStream(), fontSources);
            return services;
        } catch (IOException failure) { throw new UncheckedIOException("UILib 宿主服务初始化失败", failure); }
    }
    private static void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("UILib 服务必须在客户端 GL 线程访问");
    }
    /** Forge coremod 在 Minecraft.close 入口调用，早于窗口与上下文销毁。 */
    public static void close() {
        if (services == null) return;
        checkThread();
        EmbeddedSceneServices closing = services;
        services = null;
        try (EmbeddedSceneServices owned = closing) {
            if (Minecraft.getInstance().currentScreen instanceof Minecraft116SceneScreen)
                Minecraft.getInstance().currentScreen.onClose();
        }
    }
}
