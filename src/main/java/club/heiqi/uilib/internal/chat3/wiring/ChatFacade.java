package club.heiqi.uilib.internal.chat3.wiring;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;

/**
 * 聊天系统 3.0 架空原版接线盒:替换 GuiIngame.persistantChatGUI 的自有子类。
 *
 * <p><b>S0 骨架 = 空子类</b>:全部继承原版行为,替换后与原版零回归(冒烟点)。后续阶段按
 * 接线清单逐方法 override,方法体只做单行委托(设计原则 P7:Facade 零业务逻辑),内部逻辑
 * 全部落在 internal.chat3.* 内部类。</p>
 */
public final class ChatFacade extends GuiNewChat {

    /**
     * 调用原版构造(其私有字段成为无害死数据,子类不可见、不依赖)。
     *
     * @param mc 客户端实例
     */
    public ChatFacade(Minecraft mc) {
        super(mc);
    }
}
