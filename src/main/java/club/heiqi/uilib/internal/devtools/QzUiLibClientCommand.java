package club.heiqi.uilib.internal.devtools;

import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.config.modern.ModernConfigEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

/**
 * Qz UILib 内部开发工具客户端命令。
 *
 * <p>scene 演示测试台（{@code test}/{@code scene_test} 子命令与 pages 包）已移除，
 * 现仅保留新架构配置页调试入口 {@code modernconfig}。</p>
 */
final class QzUiLibClientCommand extends CommandBase {

    private static final String COMMAND_NAME = "qzuilib";
    private static final String SUBCOMMAND_MODERN_CONFIG = "modernconfig";

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/qzuilib modernconfig";
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

        if (SUBCOMMAND_MODERN_CONFIG.equalsIgnoreCase(args[0])) {
            openModernConfig(sender);
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
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
            return getListOfStringsMatchingLastWord(args, SUBCOMMAND_MODERN_CONFIG);
        }
        return Collections.emptyList();
    }
}
