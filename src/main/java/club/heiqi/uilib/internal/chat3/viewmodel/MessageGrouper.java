package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;

/**
 * 聊天 3.0 消息分组器(L2 视图模型,纯函数):历史快照 → Telegram 消息组序列。
 *
 * <p>语义(B11 合并边界):</p>
 * <ul>
 *   <li>相邻且发送者相同<b>且时间间隔 ≤ {@link #MERGE_WINDOW_MILLIS}</b> → 合并为一组
 *       (气泡连排;设计稿 §3.3「同发送者且时间间隔 ≤120s 才并组」);</li>
 *   <li>发送者提取失败(null = 系统/广播)→ 每条独立成组,并切断前后合并;</li>
 *   <li>「自己」判定:发送者 == 本地玩家名(调用方传入,视图模型不依赖 Minecraft);</li>
 *   <li>输出组序 = 时间正序(旧 → 新)。</li>
 *   <li><b>C8 通道③（短路，先于以上全部）</b>:{@link StructuredChatReader#rendersAsMarkdown}
 *       命中且 {@link StructuredChatReader#markdownContentOf} 取到内容的记录 ⇒ 独立成
 *       {@code MARKDOWN_LEFT} 行(左对齐、无 sender、不并组),并切断前后合并。该判定排在
 *       任何取文本调用<b>之前</b>:正则兜底要先 {@code record.getPlainText()},对
 *       {@code uilib.markdown} 键那是语言表查找、实机只返回 key 字面——markdown 记录
 *       全程零取文本(硬锁:markdown 路径渲染入口调用数恒 0)。</li>
 * </ul>
 *
 * <p><b>C7 划界：结构优先、正则兜底</b>（定案 3「玩家名称走原版解析、发送内容走 markdown、
 * 两者不混合」的落点）。每条记录先交 {@link StructuredChatReader} 读原版结构
 * （{@code chat.type.text} + {@code [sender, content]}）——命中即为玩家消息，sender 与 rest
 * 直取结构结果，<b>整条路径不碰 {@code record.getPlainText()}/{@code getFormattedText()}</b>
 * （翻译组件上这两个方法要走 StatCollector 语言表查找：多一次依赖，缺参还抛
 * {@code ChatComponentTranslationFormatException}）。未命中（自定义或改写过的格式键、
 * 系统与广播消息）才退回既有 {@link SenderExtractor} 正则口径，行为与今天完全一致。</p>
 *
 * <p><b>两条通道的内容都取自 unformatted 源</b>：结构通道取 {@code getFormatArgs()[1]}
 * 的原始字符串，兜底通道取 {@code getPlainText()} 的正则 rest；{@code getFormattedText()}
 * 一律不作 markdown 输入——{@code ChatComponentStyle.getFormattedText()} 逐组件前置
 * {@code getFormattingCode()}、尾追 {@code RESET}（实测 {@code <§rSteve§r> §r<b>hi</b>§r}），
 * 那正是旧气泡行 § 残渣的唯一来源。代价（如实登记）：上游原版链给<b>内容</b>定的颜色不再透传进
 * markdown 气泡，颜色一律由基础色/样式表/markdown 自有语法决定——与定案 3「不混合」同向。</p>
 */
public final class MessageGrouper {

    /** 并组时间窗(ms,设计稿 §3.3):相邻同发送者消息间隔 &gt; 此值即断开开新组。 */
    public static final long MERGE_WINDOW_MILLIS = 120_000L;

    private final SenderExtractor extractor;

    public MessageGrouper() {
        this(SenderExtractor.DEFAULT);
    }

    /**
     * @param extractor 发送者提取器(可配置正则)
     */
    public MessageGrouper(SenderExtractor extractor) {
        this.extractor = extractor == null ? SenderExtractor.DEFAULT : extractor;
    }

    /**
     * @param recordsNewestFirst 历史快照(index 0 = 最新)
     * @param selfName           本地玩家名(null = 无本地玩家,全部按他人处理)
     * @return 消息组序列(时间正序)
     */
    public List<MessageGroupModel> group(List<ChatLineRecord> recordsNewestFirst, String selfName) {
        List<MessageGroupModel> groups = new ArrayList<MessageGroupModel>();
        MessageGroupModel current = null;
        String currentSender = null;
        // 当前组内最新一条消息的到达时刻(时间窗断开判定基准;新组首条时重置)
        long lastArrivedMillis = 0L;
        // 从最旧到最新遍历,保证「相邻」判断与组内时间正序
        for (int i = recordsNewestFirst.size() - 1; i >= 0; i--) {
            ChatLineRecord record = recordsNewestFirst.get(i);
            // C8 通道③：markdown 递交形先于一切取文本调用短路（不读 plain/formatted、
            // 不进 SenderExtractor），独立成 markdown 系统行形并切断合并。
            // 唯一例外：args 形不合（缺位 / 非 String·ChatComponentText 槽）——不猜，退回
            // 下方既有通道（对翻译组件那是 key 字面系统行），与 reader「不合形即 null」同律。
            if (StructuredChatReader.rendersAsMarkdown(record.getComponent())) {
                String markdownContent =
                        StructuredChatReader.markdownContentOf(record.getComponent());
                if (markdownContent != null) {
                    groups.add(MessageGroupModel.markdown(record, markdownContent));
                    current = null;
                    currentSender = null;
                    continue;
                }
            }
            // C7 结构优先：命中原版 chat.type.text 结构 ⇒ sender/rest 直取结构结果，
            // 不再走 getPlainText()（翻译组件上那是语言表查找）。
            StructuredChatReader.PlayerChat structured =
                    StructuredChatReader.read(record.getComponent());
            SenderExtractor.SenderMatch match = structured == null
                    ? extractor.extract(record.getPlainText()) : null;
            String sender = structured != null ? structured.getSender()
                    : match == null ? null : match.getSender();
            if (sender == null) {
                groups.add(MessageGroupModel.system(record));
                current = null;
                currentSender = null;
                continue;
            }
            // 两条通道的消息本体恒取自 unformatted 源（结构 args[1] / 正则 rest），
            // 下游 markdown 输入不再经 § 跳跃切片。
            String rest = structured != null ? structured.getContent() : match.getRest();
            // 时间窗(设计稿 §3.3):相邻同发送者消息间隔 > 120s 断开开新组
            boolean withinWindow = current != null && sender.equals(currentSender)
                    && record.getArrivedWallMillis() - lastArrivedMillis <= MERGE_WINDOW_MILLIS;
            if (withinWindow) {
                current.addLine(record, rest, structured != null);
            } else {
                boolean isSelf = selfName != null && sender.equals(selfName);
                current = MessageGroupModel.player(sender, isSelf, record, rest,
                        structured != null);
                currentSender = sender;
                groups.add(current);
            }
            lastArrivedMillis = record.getArrivedWallMillis();
        }
        return groups;
    }
}