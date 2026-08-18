package club.heiqi.uilib.internal.devtools;

import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.config.modern.ModernConfigEntry;
import club.heiqi.uilib.internal.devtools.playground.TestPlaygroundEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

/**
 * Qz UILib 内部开发工具客户端命令。
 *
 * <p>子命令：</p>
 * <ul>
 *   <li>{@code test} —— 打开 scene 测试场地（{@link TestPlaygroundEntry#open()}），
 *       在游戏内验证文本输入/浮层/响应式能力；</li>
 *   <li>{@code modernconfig} —— 打开新架构配置页调试入口（{@link ModernConfigEntry#open()}）。</li>
 * </ul>
 */
final class QzUiLibClientCommand extends CommandBase {

    private static final String COMMAND_NAME = "qzuilib";
    private static final String SUBCOMMAND_TEST = "test";
    private static final String SUBCOMMAND_MODERN_CONFIG = "modernconfig";

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/qzuilib <test|modernconfig>";
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
        Subcommand subcommand = resolveSubcommand(args);
        if (subcommand == null) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        switch (subcommand) {
            case TEST:
                openTestPlayground(sender);
                break;
            case MODERN_CONFIG:
                openModernConfig(sender);
                break;
            default:
                throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    /**
     * 解析子命令（纯逻辑、无 MC/LWJGL 依赖）。
     *
     * <p>拆出独立静态方法有两个目的：一是让命令解析成为可 headless 单测的纯函数
     * （JVM 测试不触碰 LWJGL 类）；二是 processCommand 保持薄壳。</p>
     *
     * @param args 命令参数（可为 null/空）
     * @return 命中的子命令；参数非法返回 null
     */
    static Subcommand resolveSubcommand(String[] args) {
        if (args == null || args.length != 1 || args[0] == null) {
            return null;
        }
        if (SUBCOMMAND_TEST.equalsIgnoreCase(args[0])) {
            return Subcommand.TEST;
        }
        if (SUBCOMMAND_MODERN_CONFIG.equalsIgnoreCase(args[0])) {
            return Subcommand.MODERN_CONFIG;
        }
        return null;
    }

    /**
     * 打开 scene 测试场地。
     *
     * @param sender 命令发送者，用于客户端不可用时提示
     */
    private void openTestPlayground(ICommandSender sender) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            sender.addChatMessage(new ChatComponentText("Qz UILib: 当前客户端不可用。"));
            return;
        }
        TestPlaygroundEntry.open();
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
            return getListOfStringsMatchingLastWord(args, SUBCOMMAND_TEST, SUBCOMMAND_MODERN_CONFIG);
        }
        return Collections.emptyList();
    }

    /**
     * 已注册子命令枚举（供解析与测试引用）。
     */
    enum Subcommand {
        /** 打开 scene 测试场地。 */
        TEST,
        /** 打开新架构配置页调试入口。 */
        MODERN_CONFIG
    }
}