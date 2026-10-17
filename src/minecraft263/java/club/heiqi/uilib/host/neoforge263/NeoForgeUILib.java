package club.heiqi.uilib.host.neoforge263;

import java.io.IOException;
import java.io.UncheckedIOException;
import club.heiqi.uilib.host.embedded.EmbeddedSceneServices;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 26.3 只持有嵌入宿主资源；页面和算法归共同输出，见多版本宿主规格。 */
@Mod(value = "qz_uilib", dist = Dist.CLIENT)
public final class NeoForgeUILib {
    private static EmbeddedSceneServices services;

    public NeoForgeUILib(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(NeoForgeUILib::renderFrame);
        NeoForge.EVENT_BUS.addListener(NeoForgeUILib::stopping);
        modBus.addListener(NeoForgeUILib::reloadListeners);
    }

    public static EmbeddedSceneServices services() {
        RenderSystem.assertOnRenderThread();
        if (services == null) {
            Minecraft minecraft = Minecraft.getInstance();
            try {
                services = EmbeddedSceneServices.open(FMLPaths.CONFIGDIR.get().resolve("qz_uilib.yaml"),
                        NeoForgeUILib.class.getClassLoader(), IsolatedFontEngine.shared(), new Lwjgl3GlApi(),
                        id -> minecraft.getResourceManager().open(Identifier.parse(id)));
            } catch (IOException failure) {
                throw new UncheckedIOException("UILib 26.3 应用服务初始化失败", failure);
            }
        }
        return services;
    }

    private static void renderFrame(RenderFrameEvent.Post event) {
        if (Minecraft.getInstance().gui.screen() instanceof NeoForgeSceneScreen screen) screen.present();
    }

    private static void reloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath("qz_uilib", "embedded_resources"),
                (ResourceManagerReloadListener) manager -> {
                    EmbeddedSceneServices current = services;
                    if (current != null) Minecraft.getInstance().execute(() -> {
                        if (services == current) current.resourcesReloaded();
                    });
                });
    }

    private static void stopping(ClientStoppingEvent event) {
        Throwable failure = null;
        try {
            if (Minecraft.getInstance().gui.screen() instanceof NeoForgeSceneScreen screen) screen.removed();
        } catch (RuntimeException | Error next) { failure = next; }
        EmbeddedSceneServices current = services;
        services = null;
        try { if (current != null) current.close(); }
        catch (RuntimeException | Error next) {
            if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next);
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) throw error;
    }
}
