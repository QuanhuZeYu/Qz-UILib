package club.heiqi.uilib.internal.devtools;

import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.config.modern.ModernConfigEntry;
import club.heiqi.uilib.internal.devtools.pages.SceneTestHubScreen;
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
    private static final String SUBCOMMAND_LEGACY_TEST = "legacy_test";
    private static final String SUBCOMMAND_SCENE_TEST = "scene_test";
    private static final String SUBCOMMAND_HUD_DEMO = "hud_demo";
    private static final String SUBCOMMAND_MODERN_CONFIG = "modernconfig";

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/qzuilib <test|legacy_test|scene_test|hud_demo|modernconfig>";
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
            openSceneTestHub(sender);
            return;
        }
        if (SUBCOMMAND_LEGACY_TEST.equalsIgnoreCase(args[0])) {
            openDiagnosticsMenu(sender);
            return;
        }
        if (SUBCOMMAND_SCENE_TEST.equalsIgnoreCase(args[0])) {
            openSceneTestHub(sender);
            return;
        }
        if (SUBCOMMAND_HUD_DEMO.equalsIgnoreCase(args[0])) {
            toggleHudDemo(sender);
            return;
        }
        if (SUBCOMMAND_MODERN_CONFIG.equalsIgnoreCase(args[0])) {
            openModernConfig(sender);
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    /**
     * 打开旧 HTML-like test 视觉矩阵，仅作为 legacy/deprecated 参考回归入口，不再作为实际业务入口扩展。
     *
     * @param sender 命令发送者，用于客户端不可用时提示
     */
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

    /**
     * 打开 scene 新栈 test 首页。
     *
     * @param sender 命令发送者，用于客户端不可用时提示
     */
    private void openSceneTestHub(ICommandSender sender) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            sender.addChatMessage(new ChatComponentText("Qz UILib: 当前客户端不可用。"));
            return;
        }
        SceneTestHubScreen.openHub();
    }

    /**
     * 打开新架构配置页（实验性）。
     *
     * <p>uilib 作为新架构配置页的第一个真实使用方，经 {@link ModernConfigEntry} 接入。
     * 接入代码位于 {@code uilib.config.modern} 专门包，依据决策 {@code ee1e181d}
     * 可直接 import {@code config.ui.*}（含 ConfigUI.open），不再需要反射。</p>
     *
     * @param sender 命令发送者，用于客户端不可用时提示
     */
    private void openModernConfig(ICommandSender sender) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            sender.addChatMessage(new ChatComponentText("Qz UILib: 当前客户端不可用。"));
            return;
        }
        ModernConfigEntry.open();
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMAND_TEST, SUBCOMMAND_LEGACY_TEST,
                    SUBCOMMAND_SCENE_TEST, SUBCOMMAND_HUD_DEMO, SUBCOMMAND_MODERN_CONFIG);
        }
        return Collections.emptyList();
    }
}
