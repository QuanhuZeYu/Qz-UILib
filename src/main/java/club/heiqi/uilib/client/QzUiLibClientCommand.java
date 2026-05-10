package club.heiqi.uilib.client;

import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.screen.UiDiagnosticsScreens;
import club.heiqi.uilib.ui.screen.UiDocumentScreens;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

/**
 * Qz UILib 客户端测试入口命令。
 */
public class QzUiLibClientCommand extends CommandBase {

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
                minecraft.displayGuiScreen(UiDiagnosticsScreens.createUiTest(
                        UiDocumentScreens.DocumentScreenEnvironment.minecraftDefaults()));
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
