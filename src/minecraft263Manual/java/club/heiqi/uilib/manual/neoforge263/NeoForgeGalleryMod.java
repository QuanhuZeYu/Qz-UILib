package club.heiqi.uilib.manual.neoforge263;

import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.host.embedded.EmbeddedSceneServices;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.host.media.bilibili.BilibiliResolver;
import club.heiqi.uilib.host.neoforge263.NeoForgeSceneScreen;
import club.heiqi.uilib.host.neoforge263.NeoForgeUILib;
import club.heiqi.uilib.testing.gallery.GalleryMediaResources;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.testing.host.LocalMediaFiles;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 人工附加包仅登记入口；目录、配置、图片和媒体全用同一共享广场。 */
@Mod(value = "qz_uilib_manualtest", dist = Dist.CLIENT)
public final class NeoForgeGalleryMod {
    public NeoForgeGalleryMod() {
        NeoForge.EVENT_BUS.addListener(NeoForgeGalleryMod::commands);
    }
    private static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("qzcore").executes(context -> {
            Minecraft.getInstance().execute(NeoForgeGalleryMod::open); return 1;
        }));
    }
    private static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        EmbeddedSceneServices services = NeoForgeUILib.services();
        if (!services.ready().isDone()) { services.ready().thenRun(NeoForgeGalleryMod::open); return; }
        if (services.ready().isCompletedExceptionally()) return;
        GalleryMediaResources media = new GalleryMediaResources(new MediaService(IsolatedMediaAdapters.decoderFactory(),
                IsolatedMediaAdapters.audioSinkFactory(), MediaBudget.DEFAULT), new BilibiliResolver());
        GalleryScreen screen = null;
        try {
            TestGallery gallery = new TestGallery(media.environment(services.settings(), LocalMediaFiles::resolve,
                    LocalMediaFiles.initialDirectory(), services.images()));
            screen = new GalleryScreen(minecraft.gui.screen(), gallery, media);
            screen.closeRequests(gallery::requestClose);
            minecraft.gui.setScreen(screen);
        } catch (RuntimeException | Error failure) {
            try { if (screen != null) screen.removed(); else media.close(); }
            catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private static final class GalleryScreen extends NeoForgeSceneScreen {
        private final GalleryMediaResources media;
        private boolean mediaClosed, transferred;
        GalleryScreen(Screen parent, TestGallery gallery, GalleryMediaResources media) {
            super(parent, gallery, media.service()); this.media = media;
        }
        @Override protected void ownPageResources(ScenePageContext context) {
            context.own(media); transferred = true;
        }
        @Override public void removed() {
            Throwable failure = null;
            try { super.removed(); } catch (RuntimeException | Error next) { failure = next; }
            if (!transferred && !mediaClosed) {
                mediaClosed = true;
                try { media.close(); } catch (Exception | LinkageError next) {
                    if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next);
                }
            }
            if (failure instanceof Error error) throw error;
            if (failure instanceof RuntimeException error) throw error;
            if (failure != null) throw new IllegalStateException("测试广场媒体资源释放失败", failure);
        }
    }
}
