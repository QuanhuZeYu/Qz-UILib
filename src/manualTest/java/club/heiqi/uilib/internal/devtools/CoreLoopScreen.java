package club.heiqi.uilib.internal.devtools;

import club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.testing.gallery.GalleryMediaResources;
import club.heiqi.uilib.host.client.settings.MinecraftApplicationConfiguration;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaService;

/** MC入口借用当前应用配置域；只有用户打开媒体才创建实际native会话。 */
public final class CoreLoopScreen extends MinecraftSceneScreen {
    public CoreLoopScreen(TestGallery.Page initial) {
        this(initial,new GalleryMediaResources(new MediaService(IsolatedMediaAdapters.decoderFactory(),
                IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT)));
    }
    private CoreLoopScreen(TestGallery.Page initial,GalleryMediaResources media) {
        super(MinecraftApplicationConfiguration.settings().domain(),context -> {
            context.own(media);
            new TestGallery(media.environment(MinecraftApplicationConfiguration.settings(),
                    club.heiqi.uilib.testing.host.LocalMediaFiles::resolve,
                    club.heiqi.uilib.testing.host.LocalMediaFiles.initialDirectory()),initial).build(context);
        },media.service());
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
