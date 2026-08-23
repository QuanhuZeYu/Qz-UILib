package club.heiqi.uilib.internal.chat3.wiring;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;

import club.heiqi.uilib.internal.chat3.view.ChatSceneController;

/**
 * 聊天 3.0 架空原版接线盒:替换 GuiIngame.persistantChatGUI 的自有子类。
 *
 * <p><b>P7 可读性契约:Facade = 一张单行转发表</b>——每个 override 方法体只做一行委托
 * 到 {@link ChatCore},零 if/for/计算;业务逻辑全在 internal.chat3.* 内部类。原版公共方法
 * 能利用的(getSentMessages/addToSentMessages)直接继承原版实现;无法利用的(drawChat 等)
 * override 掏空重写。</p>
 */
public final class ChatFacade extends GuiNewChat {

    private final ChatCore core;

    /**
     * @param mc         客户端实例(父类构造自管其私有字段,子类不依赖)
     * @param controller 聊天场景控制器(接线层装配)
     */
    public ChatFacade(Minecraft mc, ChatSceneController controller) {
        super(mc);
        this.core = new ChatCore(controller);
    }

    /** 测试探针:内部编排器(转发断言用)。 */
    ChatCore __coreForTest() {
        return core;
    }

    @Override
    public void drawChat(int frameCounter) {
        core.render(System.currentTimeMillis());
    }

    @Override
    public void printChatMessage(IChatComponent component) {
        printChatMessageWithOptionalDeletion(component, 0);
    }

    @Override
    public void printChatMessageWithOptionalDeletion(IChatComponent component, int messageId) {
        core.appendMessage(component, messageId);
    }

    @Override
    public void clearChatMessages() {
        core.clear();
    }

    @Override
    public void deleteChatLine(int messageId) {
        core.deleteById(messageId);
    }

    @Override
    public void refreshChat() {
        core.invalidateLayout();
    }

    @Override
    public void resetScroll() {
        core.resetScroll();
    }

    @Override
    public void scroll(int amount) {
        core.scrollBy(amount);
    }

    @Override
    public boolean getChatOpen() {
        return core.getChatOpen();
    }

    @Override
    public IChatComponent func_146236_a(int x, int y) {
        return core.hitTest(x, y);
    }

    @Override
    public int func_146232_i() {
        return core.visibleLineCount();
    }

    @Override
    public int func_146246_g() {
        return core.chatHeight();
    }

    @Override
    public int func_146228_f() {
        return core.chatHeight();
    }

    @Override
    public float func_146244_h() {
        return core.chatLineHeight();
    }
}
