package club.heiqi.uilib.internal.chat3.data;

import net.minecraft.util.IChatComponent;

/**
 * 聊天 3.0 行记录(不可变)。
 *
 * <p>持有原版 {@link IChatComponent} 引用(样式/事件链原样保留,命中检测经原版组件回投),
 * 纯文本惰性缓存(行切分/发送者提取的输入)。10s 存活窗口与 HH:mm 时间戳以
 * {@link #getArrivedWallMillis()} 为时钟基准。</p>
 */
public final class ChatLineRecord {

    private final IChatComponent component;
    private final int messageId;
    private final long arrivedWallMillis;

    /** 纯文本惰性缓存(volatile:任意线程读取,值不可变)。 */
    private volatile String plainText;

    /**
     * @param component 消息组件(非空,样式/事件链随引用保留)
     * @param messageId 原版消息 ID(deleteChatLine 精确删除用)
     * @param arrivedWallMillis 到达时刻(System.currentTimeMillis 口径)
     */
    public ChatLineRecord(IChatComponent component, int messageId, long arrivedWallMillis) {
        if (component == null) {
            throw new IllegalArgumentException("component 不能为空");
        }
        this.component = component;
        this.messageId = messageId;
        this.arrivedWallMillis = arrivedWallMillis;
    }

    /** @return 消息组件(样式/事件链原样保留) */
    public IChatComponent getComponent() {
        return component;
    }

    /** @return 原版消息 ID */
    public int getMessageId() {
        return messageId;
    }

    /** @return 到达时刻(System.currentTimeMillis 口径,10s 存活窗口基准) */
    public long getArrivedWallMillis() {
        return arrivedWallMillis;
    }

    /**
     * @return 纯文本(惰性缓存;首次调用后不再触碰组件)
     */
    public String getPlainText() {
        String cached = plainText;
        if (cached == null) {
            cached = component.getUnformattedText();
            plainText = cached;
        }
        return cached;
    }
}
