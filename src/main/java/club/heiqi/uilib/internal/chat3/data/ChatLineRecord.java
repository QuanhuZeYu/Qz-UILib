package club.heiqi.uilib.internal.chat3.data;

import net.minecraft.util.IChatComponent;

/**
 * 聊天 3.0 行记录(不可变)。
 *
 * <p>持有原版 {@link IChatComponent} 引用(样式/事件链原样保留,命中检测经原版组件回投),
 * 纯文本惰性缓存(行切分/发送者提取的输入)。12s 存活窗口与 HH:mm 时间戳以
 * {@link #getArrivedWallMillis()} 为时钟基准。</p>
 */
public final class ChatLineRecord {

    private final IChatComponent component;
    private final int messageId;
    private final long arrivedWallMillis;
    /** 进程内唯一递增序列号(入史时由 ChatHistory 分配;组 key 稳定身份)。 */
    private final long sequenceId;

    /** 纯文本惰性缓存(volatile:任意线程读取,值不可变)。 */
    private volatile String plainText;

    /** 格式化文本惰性缓存(含 § 样式码,渲染切分/样式解析输入)。 */
    private volatile String formattedText;

    /**
     * 便捷构造(sequenceId = 0;入史时由 ChatHistory 重新分配)。
     *
     * @param component 消息组件(非空,样式/事件链随引用保留)
     * @param messageId 原版消息 ID(deleteChatLine 精确删除用)
     * @param arrivedWallMillis 到达时刻(System.currentTimeMillis 口径)
     */
    public ChatLineRecord(IChatComponent component, int messageId, long arrivedWallMillis) {
        this(component, messageId, arrivedWallMillis, 0L);
    }

    /**
     * @param component 消息组件(非空)
     * @param messageId 原版消息 ID
     * @param arrivedWallMillis 到达时刻
     * @param sequenceId 进程内唯一递增序列号
     */
    public ChatLineRecord(IChatComponent component, int messageId, long arrivedWallMillis,
            long sequenceId) {
        if (component == null) {
            throw new IllegalArgumentException("component 不能为空");
        }
        this.component = component;
        this.messageId = messageId;
        this.arrivedWallMillis = arrivedWallMillis;
        this.sequenceId = sequenceId;
    }

    /** @return 携带新序列号的等价记录(ChatHistory 入史分配用) */
    ChatLineRecord withSequence(long newSequenceId) {
        return new ChatLineRecord(component, messageId, arrivedWallMillis, newSequenceId);
    }

    /** @return 消息组件(样式/事件链原样保留) */
    public IChatComponent getComponent() {
        return component;
    }

    /** @return 原版消息 ID */
    public int getMessageId() {
        return messageId;
    }

    /** @return 到达时刻(System.currentTimeMillis 口径,12s 存活窗口基准) */
    public long getArrivedWallMillis() {
        return arrivedWallMillis;
    }

    /** @return 进程内唯一递增序列号(入史后非 0;组 key 稳定身份) */
    public long getSequenceId() {
        return sequenceId;
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

    /**
     * @return 格式化文本(含 § 样式码;惰性缓存;原版解析链的系统行切分与样式解析输入)
     *
     * <p><b>C7 收口边界</b>：本方法<b>不得</b>作 markdown 输入——{@code ChatComponentStyle
     * .getFormattedText()} 由客户端逐组件前置样式码、尾追 RESET（实测
     * {@code <§rSteve§r> §r<b>hi</b>§r}），是旧气泡 § 残渣的唯一来源；markdown 通道的内容
     * 一律取 unformatted 源（结构 {@code getFormatArgs()[1]} 或 plain 正则 rest），
     * 见 {@code ChatCardComposer#displayText} 与规划 §二之八 C7。另注：对翻译组件调用本方法
     * 会走 {@code ensureInitialized -> StatCollector} 语言表查找，非纯函数。</p>
     */
    public String getFormattedText() {
        String cached = formattedText;
        if (cached == null) {
            cached = component.getFormattedText();
            formattedText = cached;
        }
        return cached;
    }
}
