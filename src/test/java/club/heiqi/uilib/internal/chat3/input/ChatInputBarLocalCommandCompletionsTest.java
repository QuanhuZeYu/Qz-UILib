package club.heiqi.uilib.internal.chat3.input;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.client.ClientCommandHandler;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * ChatInputBar 本地客户端命令候选静态函数测试(方案 A 宿主侧):
 * 对接真实 Forge ClientCommandHandler 注册表(手动注册测试命令),验证
 * 命令名补 "/" 前缀、子命令不补、非 "/" 文本/无效玩家返回空。
 */
public class ChatInputBarLocalCommandCompletionsTest {

    private static final String TEST_COMMAND = "qzuilibcompletest";

    /** 测试命令:命令名补全 + 子命令 addTabCompletionOptions(原版 getListOfStringsMatchingLastWord 过滤)。 */
    private static ICommand testCommand() {
        return new CommandBase() {
            @Override
            public String getCommandName() {
                return TEST_COMMAND;
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/" + TEST_COMMAND;
            }

            @Override
            public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
                if (args.length == 1) {
                    return getListOfStringsMatchingLastWord(args, "test", "modernconfig");
                }
                return Collections.emptyList();
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) {
                // 测试桩:不执行
            }
        };
    }

    /** 假命令发送者(headless):权限恒可补。 */
    private final ICommandSender sender = new ICommandSender() {
        @Override
        public String getCommandSenderName() {
            return "TestSender";
        }

        @Override
        public IChatComponent func_145748_c_() {
            return new ChatComponentText(getCommandSenderName());
        }

        @Override
        public void addChatMessage(IChatComponent message) {
            // 测试桩:忽略
        }

        @Override
        public boolean canCommandSenderUseCommand(int permissionLevel, String commandName) {
            return true;
        }

        @Override
        public World getEntityWorld() {
            return null;
        }

        @Override
        public ChunkCoordinates getPlayerCoordinates() {
            return new ChunkCoordinates(0, 0, 0);
        }
    };

    @BeforeClass
    public static void registerTestCommand() {
        if (!ClientCommandHandler.instance.getCommands().containsKey(TEST_COMMAND)) {
            ClientCommandHandler.instance.registerCommand(testCommand());
        }
    }

    @AfterClass
    public static void unregisterTestCommand() {
        ClientCommandHandler.instance.getCommands().remove(TEST_COMMAND);
    }

    @Test
    public void commandNameCandidatesGetSlashPrefix() {
        Assert.assertEquals("正在补命令名(无空格):候选补 / 前缀",
                Arrays.asList("/" + TEST_COMMAND),
                ChatInputBar.localCommandCompletions(sender, "/qzuilibcomple"));
    }

    @Test
    public void subcommandCandidatesKeepNoSlashPrefix() {
        // 子命令候选不含 "/",且经原版 getListOfStringsMatchingLastWord 前缀过滤("te" 只命中 test)
        Assert.assertEquals("补子命令(有空格):候选不含 /",
                Arrays.asList("test"),
                ChatInputBar.localCommandCompletions(sender, "/" + TEST_COMMAND + " te"));
    }

    @Test
    public void invalidInputReturnsEmpty() {
        Assert.assertTrue("非 / 文本不查询",
                ChatInputBar.localCommandCompletions(sender, "qzuilibcomple").isEmpty());
        Assert.assertTrue("null 文本防御",
                ChatInputBar.localCommandCompletions(sender, null).isEmpty());
        Assert.assertTrue("无玩家防御",
                ChatInputBar.localCommandCompletions(null, "/qzuilibcomple").isEmpty());
    }
}
