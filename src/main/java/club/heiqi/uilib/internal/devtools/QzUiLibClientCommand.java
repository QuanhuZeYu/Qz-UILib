package club.heiqi.uilib.internal.devtools;

import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.config.modern.ModernConfigEntry;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.wiring.ChatMarkdownInstaller;
import club.heiqi.uilib.internal.devtools.glass.GlassLabEntry;
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
 *   <li>{@code glass} —— 打开磨玻璃实验室（{@link GlassLabEntry#open()}），
 *       backdrop-filter 仿 iOS 磨玻璃观感与渲染路径验收；</li>
 *   <li>{@code modernconfig} —— 打开新架构配置页调试入口（{@link ModernConfigEntry#open()}）；</li>
 *   <li>{@code chatmd on|off|status} —— 聊天 3.0 接管开关与状态诊断（on 启用/off 逃生舱回退原版/status 查看接管状态）。</li>
 * </ul>
 */
final class QzUiLibClientCommand extends CommandBase {

    private static final String COMMAND_NAME = "qzuilib";
    private static final String SUBCOMMAND_TEST = "test";
    private static final String SUBCOMMAND_GLASS = "glass";
    private static final String SUBCOMMAND_MODERN_CONFIG = "modernconfig";
    private static final String SUBCOMMAND_CHATMD = "chatmd";
    private static final String ARG_ON = "on";
    private static final String ARG_OFF = "off";
    private static final String ARG_STATUS = "status";
    private static final String COMMAND_USAGE = "/qzuilib <test|glass|modernconfig|chatmd on|off|status>";

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return COMMAND_USAGE;
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
            case GLASS:
                openGlassLab(sender);
                break;
            case MODERN_CONFIG:
                openModernConfig(sender);
                break;
            case CHATMD_ON:
                enableChatTakeover(sender);
                break;
            case CHATMD_OFF:
                disableChatTakeover(sender);
                break;
            case CHATMD_STATUS:
                reportChatStatus(sender);
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
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        if (args.length == 1) {
            if (SUBCOMMAND_TEST.equalsIgnoreCase(args[0])) {
                return Subcommand.TEST;
            }
            if (SUBCOMMAND_GLASS.equalsIgnoreCase(args[0])) {
                return Subcommand.GLASS;
            }
            if (SUBCOMMAND_MODERN_CONFIG.equalsIgnoreCase(args[0])) {
                return Subcommand.MODERN_CONFIG;
            }
            return null;
        }
        if (args.length == 2 && SUBCOMMAND_CHATMD.equalsIgnoreCase(args[0]) && args[1] != null) {
            if (ARG_ON.equalsIgnoreCase(args[1])) {
                return Subcommand.CHATMD_ON;
            }
            if (ARG_OFF.equalsIgnoreCase(args[1])) {
                return Subcommand.CHATMD_OFF;
            }
            if (ARG_STATUS.equalsIgnoreCase(args[1])) {
                return Subcommand.CHATMD_STATUS;
            }
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
     * 打开磨玻璃实验室。
     *
     * @param sender 命令发送者，用于客户端不可用时提示
     */
    private void openGlassLab(ICommandSender sender) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            sender.addChatMessage(new ChatComponentText("Qz UILib: 当前客户端不可用。"));
            return;
        }
        GlassLabEntry.open();
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

    /**
     * 启用聊天 3.0 接管(开关置开并立即装配一次;后续渲染帧幂等维持)。
     *
     * @param sender 命令发送者
     */
    private void enableChatTakeover(ICommandSender sender) {
        ChatMarkdownSettings.setEnabled(true);
        ChatMarkdownInstaller.installIfNeeded();
        sender.addChatMessage(new ChatComponentText("Qz UILib: 聊天 3.0 接管已启用。"));
    }

    /**
     * 关闭聊天 3.0 接管(开关置关并立即回退原版实例;逃生舱)。
     *
     * @param sender 命令发送者
     */
    private void disableChatTakeover(ICommandSender sender) {
        ChatMarkdownSettings.setEnabled(false);
        ChatMarkdownInstaller.installIfNeeded();
        sender.addChatMessage(new ChatComponentText("Qz UILib: 聊天 3.0 接管已关闭,回退原版对话框。"));
    }

    /**
     * 输出接管状态诊断(开关/接管状态)。
     *
     * @param sender 命令发送者
     */
    private void reportChatStatus(ICommandSender sender) {
        StringBuilder report = new StringBuilder("Qz UILib 聊天 3.0:开关=")
                .append(ChatMarkdownSettings.isEnabled() ? "开" : "关")
                .append(" | 接管状态=")
                .append(ChatMarkdownInstaller.isInstalled() ? "已接管" : "未接管");
        if (ChatMarkdownSettings.isEnabled() && !ChatMarkdownInstaller.isInstalled()) {
            report.append("(等待渲染帧装配)");
        }
        sender.addChatMessage(new ChatComponentText(report.toString()));
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, SUBCOMMAND_TEST, SUBCOMMAND_GLASS,
                    SUBCOMMAND_MODERN_CONFIG, SUBCOMMAND_CHATMD);
        }
        if (args.length == 2 && SUBCOMMAND_CHATMD.equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, ARG_ON, ARG_OFF, ARG_STATUS);
        }
        return Collections.emptyList();
    }

    /**
     * 已注册子命令枚举（供解析与测试引用）。
     */
    enum Subcommand {
        /** 打开 scene 测试场地。 */
        TEST,
        /** 打开磨玻璃实验室。 */
        GLASS,
        /** 打开新架构配置页调试入口。 */
        MODERN_CONFIG,
        /** 聊天 3.0 接管开。 */
        CHATMD_ON,
        /** 聊天 3.0 接管关(逃生舱)。 */
        CHATMD_OFF,
        /** 聊天 3.0 接管状态。 */
        CHATMD_STATUS
    }
}
