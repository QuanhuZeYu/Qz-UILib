package club.heiqi.uilib.internal.devtools;

import net.minecraft.client.Minecraft;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;

/** 仅开发 classpath 中注册；场景不进入发布包。 */
public final class CoreLoopBootstrap {
    private static boolean installed, pendingOpen, pendingLanguage, pendingRich, pendingMath;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pendingOpen) return;
        pendingOpen = false;
        Minecraft.getMinecraft()
                .displayGuiScreen(
                        pendingMath
                                ? new CoreMathPage()
                                : pendingRich
                                        ? new CoreRichTextPage()
                                        : pendingLanguage
                                                ? new CoreLocalizationPage()
                                                : new CoreLoopScreen());
    }

    private CoreLoopBootstrap() {}

    public static void install() {
        if (installed) return;
        installed = true;
        FMLCommonHandler.instance().bus().register(new CoreLoopBootstrap());
        ClientCommandHandler.instance.registerCommand(
                new CommandBase() {
                    @Override
                    public String getCommandName() {
                        return "qzcore";
                    }

                    @Override
                    public String getCommandUsage(ICommandSender sender) {
                        return "/qzcore [lang|rich|math]";
                    }

                    @Override
                    public int getRequiredPermissionLevel() {
                        return 0;
                    }

                    @Override
                    public void processCommand(
                            ICommandSender sender,
                            String[] args) { // GuiChat 在命令返回后会关闭当前界面，必须等聊天输入收尾后再打开。
                        pendingLanguage = args.length > 0 && "lang".equals(args[0]);
                        pendingRich = args.length > 0 && "rich".equals(args[0]);
                        pendingMath = args.length > 0 && "math".equals(args[0]);
                        pendingOpen = true;
                    }
                });
    }
}
