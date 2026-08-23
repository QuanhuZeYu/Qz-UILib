package club.heiqi.uilib.internal.chat3.wiring;

import net.minecraft.util.IChatComponent;

import club.heiqi.uilib.api.chat.ChatAccess;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.view.ChatSceneController;

/**
 * 聊天 3.0 内部编排器(L4):Facade 的唯一依赖;每个方法只做 1-3 行编排调用,
 * 算法与状态全在 L1 数据层/L3 渲染层(P7 可读性)。
 */
public final class ChatCore {

    private final ChatSceneController controller;

    /**
     * @param controller 聊天场景控制器(渲染/数据/命中中枢)
     */
    public ChatCore(ChatSceneController controller) {
        if (controller == null) {
            throw new IllegalArgumentException("controller 不能为空");
        }
        this.controller = controller;
    }

    /** @return 场景控制器(安装器 tick/视口写入用) */
    public ChatSceneController controller() {
        return controller;
    }

    /**
     * 消息进入(网络线程安全:历史加锁 + 脏标记主线程冲刷)。
     * 装饰器链按注册序应用,返回 null 表示丢弃(旧式 API 兼容承诺)。
     */
    public void appendMessage(IChatComponent component, int messageId) {
        IChatComponent decorated = ChatAccess.getInstance().decorate(component);
        if (decorated == null) {
            return; // 装饰器丢弃语义
        }
        controller.history().append(decorated, messageId);
        controller.markDataDirty();
    }

    /** 清空聊天。 */
    public void clear() {
        controller.history().clear();
        controller.notifyDataChanged();
    }

    /** 按消息 ID 精确删除。 */
    public boolean deleteById(int messageId) {
        boolean removed = controller.history().deleteById(messageId);
        if (removed) {
            controller.notifyDataChanged();
        }
        return removed;
    }

    /** 布局缓存失效与树重建。 */
    public void invalidateLayout() {
        controller.invalidateLayout();
    }

    /** 滚动复位。 */
    public void resetScroll() {
        controller.history().resetScroll();
        controller.notifyDataChanged();
    }

    /** 滚动偏移(原版 scroll 语义)。 */
    public void scrollBy(int amount) {
        controller.history().scrollBy(amount);
        controller.notifyDataChanged();
    }

    /** 渲染帧推进(淡出时钟/动画/形态切换;幂等)。 */
    public void render(long nowMillis) {
        controller.tick(nowMillis);
    }

    /** 命中检测(悬停/点击,返回组件供原版事件链处理)。 */
    public IChatComponent hitTest(int x, int y) {
        return controller.hitTest(x, y);
    }

    /** 聊天是否打开(输入屏)。 */
    public boolean getChatOpen() {
        return controller.isChatOpen();
    }

    /** 可视行数(func_146232_i)。 */
    public int visibleLineCount() {
        return controller.visibleLineCount();
    }

    /** 聊天高度 px(func_146246_g / func_146228_f)。 */
    public int chatHeight() {
        return controller.chatHeight();
    }

    /** 行高 px(func_146244_h)。 */
    public float chatLineHeight() {
        return (float) ChatMarkdownSettings.getChatLineHeightPx();
    }
}
