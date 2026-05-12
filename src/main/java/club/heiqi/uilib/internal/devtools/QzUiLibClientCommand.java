package club.heiqi.uilib.internal.devtools;

import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.screen.UiDocumentScreens;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

/**
 * Qz UILib 内部开发工具客户端命令。
 */
final class QzUiLibClientCommand extends CommandBase {

    private static final String COMMAND_NAME = "qzuilib";
    private static final String SUBCOMMAND_TEST = "test";
    private static final String SUBCOMMAND_HUD_DEMO = "hud_demo";

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/qzuilib <test|hud_demo>";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.emptyList();
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        if (SUBCOMMAND_TEST.equalsIgnoreCase(args[0])) {
            openDiagnosticsMenu(sender);
            return;
        }
        if (SUBCOMMAND_HUD_DEMO.equalsIgnoreCase(args[0])) {
            toggleHudDemo(sender);
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private void openDiagnosticsMenu(ICommandSender sender) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            sender.addChatMessage(new ChatComponentText("Qz UILib: 当前客户端不可用。"));
            return;
        }

        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                try {
                    GuiScreen screen = DevToolsScreenLauncher.createDiagnosticsMenu(
                            UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults());
                    minecraft.displayGuiScreen(screen);
                } catch (IllegalStateException exception) {
                    MyMod.LOG.error("无法打开内部诊断菜单", exception);
                    sender.addChatMessage(new ChatComponentText("Qz UILib: 内部诊断菜单当前不可用。"));
                }
            }
        });
    }

    private void toggleHudDemo(ICommandSender sender) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            sender.addChatMessage(new ChatComponentText("Qz UILib: 当前客户端不可用。"));
            return;
        }

        boolean enabled = UiHudDemoController.getInstance().toggle();
        sender.addChatMessage(new ChatComponentText(enabled
                ? "Qz UILib: HUD 双层示例已启用。纯 HUD 层会在背包/菜单中隐藏；交互层可在容器界面上方继续显示。"
                : "Qz UILib: HUD 双层示例已关闭。"));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMAND_TEST, SUBCOMMAND_HUD_DEMO);
        }
        return Collections.emptyList();
    }
}
