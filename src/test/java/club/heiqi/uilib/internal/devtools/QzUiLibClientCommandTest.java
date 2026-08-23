package club.heiqi.uilib.internal.devtools;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

/**
 * `QzUiLibClientCommand` 的纯 JVM 契约测试。
 *
 * <p>只锚定命令元信息、tab 补全、子命令解析与非法参数兜底;开屏路径(test/modernconfig)与
 * 接管开关路径(chatmd on/off 触发装配)依赖真机 Minecraft,headless 下不触碰(chatmd status
 * 不触 Minecraft,可测)。</p>
 */
public class QzUiLibClientCommandTest {

    /**
     * 验证命令元信息与补全结果(test + modernconfig + chatmd)。
     */
    @Test
    public void shouldExposeCommandMetadataAndTabCompletion() {
        QzUiLibClientCommand command = new QzUiLibClientCommand();
        RecordingSender sender = new RecordingSender();

        Assert.assertEquals("qzuilib", command.getCommandName());
        Assert.assertEquals("/qzuilib <test|modernconfig|chatmd on|off|status>", command.getCommandUsage(sender));
        Assert.assertEquals(0, command.getRequiredPermissionLevel());
        Assert.assertTrue(command.addTabCompletionOptions(sender, new String[] { "te" }).contains("test"));
        Assert.assertTrue(command.addTabCompletionOptions(sender, new String[] { "mod" }).contains("modernconfig"));
        Assert.assertTrue(command.addTabCompletionOptions(sender, new String[] { "ch" }).contains("chatmd"));
        Assert.assertTrue(command.addTabCompletionOptions(sender, new String[] { "chatmd", "o" }).contains("on"));
        Assert.assertTrue(command.addTabCompletionOptions(sender, new String[] { "chatmd", "" }).contains("status"));
        Assert.assertTrue(command.addTabCompletionOptions(sender, new String[] { "te", "extra" }).isEmpty());
        Assert.assertTrue(command.addTabCompletionOptions(sender, new String[] { "chatmd", "x" }).isEmpty());
    }

    /**
     * 验证子命令解析(纯函数、大小写不敏感、非法参数返回 null)。
     *
     * <p>开屏路径依赖真机 LWJGL 类,headless 不触碰(JVM 测试类路径无 LWJGL);解析逻辑
     * 拆在 {@code resolveSubcommand} 中独立可测。</p>
     */
    @Test
    public void resolveSubcommandParsesKnownAndRejectsUnknown() {
        Assert.assertEquals(QzUiLibClientCommand.Subcommand.TEST,
                QzUiLibClientCommand.resolveSubcommand(new String[] { "test" }));
        Assert.assertEquals(QzUiLibClientCommand.Subcommand.TEST,
                QzUiLibClientCommand.resolveSubcommand(new String[] { "TEST" }));
        Assert.assertEquals(QzUiLibClientCommand.Subcommand.MODERN_CONFIG,
                QzUiLibClientCommand.resolveSubcommand(new String[] { "modernconfig" }));
        Assert.assertEquals(QzUiLibClientCommand.Subcommand.MODERN_CONFIG,
                QzUiLibClientCommand.resolveSubcommand(new String[] { "ModernConfig" }));
        Assert.assertEquals(QzUiLibClientCommand.Subcommand.CHATMD_ON,
                QzUiLibClientCommand.resolveSubcommand(new String[] { "chatmd", "on" }));
        Assert.assertEquals(QzUiLibClientCommand.Subcommand.CHATMD_OFF,
                QzUiLibClientCommand.resolveSubcommand(new String[] { "chatmd", "OFF" }));
        Assert.assertEquals(QzUiLibClientCommand.Subcommand.CHATMD_STATUS,
                QzUiLibClientCommand.resolveSubcommand(new String[] { "ChatMd", "status" }));
        Assert.assertNull(QzUiLibClientCommand.resolveSubcommand(new String[0]));
        Assert.assertNull(QzUiLibClientCommand.resolveSubcommand(new String[] { "inventory" }));
        Assert.assertNull(QzUiLibClientCommand.resolveSubcommand(new String[] { "chatmd" }));
        Assert.assertNull(QzUiLibClientCommand.resolveSubcommand(new String[] { "chatmd", "toggle" }));
        Assert.assertNull(QzUiLibClientCommand.resolveSubcommand(new String[] { "test", "modernconfig" }));
        Assert.assertNull(QzUiLibClientCommand.resolveSubcommand(null));
        Assert.assertNull(QzUiLibClientCommand.resolveSubcommand(new String[] { null }));
        Assert.assertNull(QzUiLibClientCommand.resolveSubcommand(new String[] { "chatmd", null }));
    }

    /**
     * 验证非法参数会返回固定用法提示。
     */
    @Test
    public void shouldRejectArgumentsOtherThanKnownSubcommands() {
        QzUiLibClientCommand command = new QzUiLibClientCommand();
        RecordingSender sender = new RecordingSender();

        try {
            command.processCommand(sender, new String[0]);
            Assert.fail("Expected WrongUsageException");
        } catch (WrongUsageException expected) {
            Assert.assertEquals("/qzuilib <test|modernconfig|chatmd on|off|status>", expected.getMessage());
        }

        try {
            command.processCommand(sender, new String[] { "inventory" });
            Assert.fail("Expected WrongUsageException");
        } catch (WrongUsageException expected) {
            Assert.assertEquals("/qzuilib <test|modernconfig|chatmd on|off|status>", expected.getMessage());
        }

        try {
            command.processCommand(sender, new String[] { "chatmd", "toggle" });
            Assert.fail("Expected WrongUsageException");
        } catch (WrongUsageException expected) {
            Assert.assertEquals("/qzuilib <test|modernconfig|chatmd on|off|status>", expected.getMessage());
        }

        Assert.assertTrue(sender.messages.isEmpty());
    }

    /**
     * 验证 chatmd status 路径(不触 Minecraft,headless 安全)并回送诊断文本。
     */
    @Test
    public void shouldReportChatStatusWithoutTouchingMinecraft() {
        QzUiLibClientCommand command = new QzUiLibClientCommand();
        RecordingSender sender = new RecordingSender();

        command.processCommand(sender, new String[] { "chatmd", "status" });

        Assert.assertEquals(1, sender.messages.size());
        String text = sender.messages.get(0).getUnformattedText();
        Assert.assertTrue("status 报告应含开关字段", text.contains("开关="));
        Assert.assertTrue("status 报告应含接管状态字段", text.contains("接管状态="));
    }

    /**
     * 供测试使用的最小命令发送者。
     */
    private static final class RecordingSender implements ICommandSender {

        private final List<IChatComponent> messages = new ArrayList<IChatComponent>();

        @Override
        public String getCommandSenderName() {
            return "tester";
        }

        @Override
        public IChatComponent func_145748_c_() {
            return new ChatComponentText(getCommandSenderName());
        }

        @Override
        public void addChatMessage(IChatComponent message) {
            messages.add(message);
        }

        @Override
        public boolean canCommandSenderUseCommand(int permissionLevel, String command) {
            return true;
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return new ChunkCoordinates(0, 0, 0);
        }

        @Override
        public World getEntityWorld() {
            return null;
        }
    }
}
