package club.heiqi.uilib.api.chat;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;

/**
 * 旧式兼容桥(公共 API):把 api.chat 调用映射为 chatGUI 公共方法(不 import internal.*)。
 *
 * <p>发送语义与原版发送链一致:入发送历史 → 客户端命令探测(executeCommand 返回 0 =
 * 非命令)→ 玩家发包。</p>
 */
public final class ChatBridge {

    private ChatBridge() {
    }

    /**
     * 发送一条聊天消息(原版发送链语义)。
     *
     * @param message 消息文本(空串/null 忽略)
     */
    public static void send(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.ingameGUI == null || mc.thePlayer == null) {
            return;
        }
        mc.ingameGUI.getChatGUI().addToSentMessages(message);
        if (ClientCommandHandler.instance.executeCommand(mc.thePlayer, message) == 0) {
            mc.thePlayer.sendChatMessage(message);
        }
    }
}
