package club.heiqi.uilib.internal.devtools;

import club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.host.client.settings.MinecraftApplicationConfiguration;

/** MC入口借用当前应用配置域；参考页面由共享广场声明。 */
public final class CoreLoopScreen extends MinecraftSceneScreen {
    public CoreLoopScreen(TestGallery.Page initial) {
        super(MinecraftApplicationConfiguration.settings().domain(),
                new TestGallery(MinecraftApplicationConfiguration::settings,initial));
    }
    @Override public boolean doesGuiPauseGame(){return false;}
}
