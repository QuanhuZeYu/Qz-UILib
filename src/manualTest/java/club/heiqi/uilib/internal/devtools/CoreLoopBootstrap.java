package club.heiqi.uilib.internal.devtools;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;

/** 仅开发 classpath 中注册；场景不进入发布包。 */
public final class CoreLoopBootstrap {
    private static boolean installed, pendingOpen, pendingPerformance;
    private static club.heiqi.uilib.testing.gallery.TestGallery.Page pendingPage=club.heiqi.uilib.testing.gallery.TestGallery.Page.HOME;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pendingOpen) return;
        pendingOpen = false;
        Minecraft.getMinecraft().displayGuiScreen(pendingPerformance ? new PerformanceScreen()
                : new CoreLoopScreen(pendingPage));
        pendingPerformance = false;
    }

    private CoreLoopBootstrap() {}

    public static void install() {
        if (installed) return;
        installed = true;
        CoreLoopBootstrap hooks = new CoreLoopBootstrap();
        FMLCommonHandler.instance().bus().register(hooks);
        ClientCommandHandler.instance.registerCommand(
                new CommandBase() {
                    @Override
                    public String getCommandName() {
                        return "qzcore";
                    }

                    @Override
                    public String getCommandUsage(ICommandSender sender) {
                        return "/qzcore [mailbox]";
                    }

                    @Override
                    public int getRequiredPermissionLevel() {
                        return 0;
                    }

                    @Override
                    public void processCommand(
                            ICommandSender sender,
                            String[] args) { // GuiChat 在命令返回后会关闭当前界面，必须等聊天输入收尾后再打开。
                        pendingPage=args.length>0&&"mailbox".equalsIgnoreCase(args[0])
                                ?club.heiqi.uilib.testing.gallery.TestGallery.Page.MAILBOX:club.heiqi.uilib.testing.gallery.TestGallery.Page.HOME;
                        pendingPerformance = false;
                        pendingOpen = true;
                    }
                });
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override public String getCommandName() { return "qzperf"; }
            @Override public String getCommandUsage(ICommandSender sender) { return "/qzperf"; }
            @Override public int getRequiredPermissionLevel() { return 0; }
            @Override public void processCommand(ICommandSender sender, String[] args) {
                pendingPerformance = true; pendingOpen = true;
            }
        });
    }
}
