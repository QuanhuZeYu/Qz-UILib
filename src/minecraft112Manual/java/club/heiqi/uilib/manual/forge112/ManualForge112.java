package club.heiqi.uilib.manual.forge112;

import club.heiqi.uilib.host.forge112.Forge112Client;
import club.heiqi.uilib.host.forge112.Minecraft112SceneScreen;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.host.media.bilibili.BilibiliResolver;
import club.heiqi.uilib.testing.gallery.GalleryMediaResources;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSource;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** 人工附加包只安装广场入口；页面、样本和媒体不进入正式宿主。 */
@Mod(modid = "qz_uilib_manualtest", name = "UILib 测试广场", version = UILibVersion.VALUE,
        dependencies = "required-after:qz_uilib", clientSideOnly = true, acceptableRemoteVersions = "*")
public final class ManualForge112 {
    private boolean pending;

    @Mod.EventHandler public void initialize(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            public String getName() { return "qzcore"; }
            public String getUsage(ICommandSender sender) { return "/qzcore"; }
            public int getRequiredPermissionLevel() { return 0; }
            public void execute(MinecraftServer server, ICommandSender sender, String[] arguments) { pending = true; }
        });
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pending) return;
        pending = false;
        Minecraft client = Minecraft.getMinecraft();
        client.displayGuiScreen(new GalleryScreen(client.currentScreen));
    }

    private static final class Content {
        final GalleryMediaResources media = new GalleryMediaResources(new MediaService(IsolatedMediaAdapters.decoderFactory(),
                IsolatedMediaAdapters.audioSinkFactory(), MediaBudget.DEFAULT), new BilibiliResolver());
        final TestGallery gallery = new TestGallery(media.environment(Forge112Client.services().settings(),
                ManualForge112::source, Paths.get(System.getProperty("user.home", ".")), Forge112Client.services().images()));
    }
    private static final class GalleryScreen extends Minecraft112SceneScreen {
        private final TestGallery gallery;
        GalleryScreen(GuiScreen parent) { this(parent, new Content()); }
        private GalleryScreen(GuiScreen parent, Content content) {
            super(parent, context -> { context.own(content.media); content.gallery.build(context); }, content.media.service());
            gallery = content.gallery;
        }
        @Override protected void requestClose() { gallery.requestClose(() -> super.requestClose()); }
    }
    private static MediaSource source(String text) {
        String value = text.trim();
        if (value.matches("^[A-Za-z]:[\\\\/].*") || value.startsWith("\\\\") || value.startsWith("/")) {
            Path file = Paths.get(value); return MediaSource.of(file.toAbsolutePath().toUri());
        }
        return MediaSource.of(URI.create(value));
    }
}
