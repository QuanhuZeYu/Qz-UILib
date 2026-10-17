package club.heiqi.uilib.manual.forge116;

import club.heiqi.uilib.host.embedded.EmbeddedSceneServices;
import club.heiqi.uilib.host.forge116.Forge116Client;
import club.heiqi.uilib.host.forge116.Minecraft116SceneScreen;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.host.media.bilibili.BilibiliResolver;
import club.heiqi.uilib.testing.gallery.GalleryMediaResources;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSource;
import java.net.URI;
import java.nio.file.Paths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 共享广场单独附加；主菜单按钮与客户端命令打开同一目录。 */
@Mod("qz_uilib_manualtest")
public final class ManualForge116 {
    private boolean pending;
    public ManualForge116() { MinecraftForge.EVENT_BUS.register(this); }

    @SubscribeEvent public void menu(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.getGui() instanceof MainMenuScreen) {
            Screen menu = event.getGui();
            event.addWidget(new Button(menu.width - 164, menu.height - 28, 156, 20,
                    new StringTextComponent("UILib 测试广场"), button -> pending = true));
        }
    }
    @SubscribeEvent public void command(ClientChatEvent event) {
        if ("/qzcore".equals(event.getMessage().trim())) { event.setCanceled(true); pending = true; }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pending) return;
        pending = false;
        Minecraft client = Minecraft.getInstance();
        client.displayGuiScreen(new GalleryScreen(client.currentScreen));
    }
    private static final class Content {
        final GalleryMediaResources media = new GalleryMediaResources(new MediaService(IsolatedMediaAdapters.decoderFactory(),
                IsolatedMediaAdapters.audioSinkFactory(), MediaBudget.DEFAULT), new BilibiliResolver());
        final TestGallery gallery;
        Content() {
            EmbeddedSceneServices services = Forge116Client.services();
            gallery = new TestGallery(media.environment(services.settings(), ManualForge116::source,
                    Paths.get(System.getProperty("user.home", ".")), services.images()));
        }
    }
    private static final class GalleryScreen extends Minecraft116SceneScreen {
        private final TestGallery gallery;
        GalleryScreen(Screen parent) { this(parent, new Content()); }
        private GalleryScreen(Screen parent, Content content) {
            super(parent, context -> { context.own(content.media); content.gallery.build(context); }, content.media.service());
            gallery = content.gallery;
        }
        @Override protected void requestClose() { gallery.requestClose(() -> super.requestClose()); }
    }
    private static MediaSource source(String text) {
        String value = text.trim();
        if (value.matches("^[A-Za-z]:[\\\\/].*") || value.startsWith("\\\\") || value.startsWith("/"))
            return MediaSource.of(Paths.get(value).toAbsolutePath().toUri());
        return MediaSource.of(URI.create(value));
    }
}
