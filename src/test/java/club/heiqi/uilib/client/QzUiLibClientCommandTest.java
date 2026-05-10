package club.heiqi.uilib.client;

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
 */
public class QzUiLibClientCommandTest {

    /**
     * 验证命令元信息与补全结果。
     */
    @Test
    public void shouldExposeCommandMetadataAndTabCompletion() {
        QzUiLibClientCommand command = new QzUiLibClientCommand();
        RecordingSender sender = new RecordingSender();

        Assert.assertEquals("qzuilib", command.getCommandName());
        Assert.assertEquals("/qzuilib <test|hud_demo>", command.getCommandUsage(sender));
        Assert.assertEquals(0, command.getRequiredPermissionLevel());
        Assert.assertTrue(command.addTabCompletionOptions(sender, new String[] { "t" }).contains("test"));
        Assert.assertTrue(command.addTabCompletionOptions(sender, new String[] { "hud" }).contains("hud_demo"));
        Assert.assertTrue(command.addTabCompletionOptions(sender, new String[] { "test", "extra" }).isEmpty());
    }

    /**
     * 验证非法参数会返回固定用法提示。
     */
    @Test
    public void shouldRejectArgumentsOtherThanTest() {
        QzUiLibClientCommand command = new QzUiLibClientCommand();
        RecordingSender sender = new RecordingSender();

        try {
            command.processCommand(sender, new String[0]);
            Assert.fail("Expected WrongUsageException");
        } catch (WrongUsageException expected) {
            Assert.assertEquals("/qzuilib <test|hud_demo>", expected.getMessage());
        }

        try {
            command.processCommand(sender, new String[] { "inventory" });
            Assert.fail("Expected WrongUsageException");
        } catch (WrongUsageException expected) {
            Assert.assertEquals("/qzuilib <test|hud_demo>", expected.getMessage());
        }

        Assert.assertTrue(sender.messages.isEmpty());
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
