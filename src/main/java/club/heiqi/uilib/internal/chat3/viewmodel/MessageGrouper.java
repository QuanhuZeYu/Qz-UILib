package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;

/**
 * 聊天 3.0 消息分组器(L2 视图模型,纯函数):历史快照 → Telegram 消息组序列。
 *
 * <p>语义(B11 合并边界):</p>
 * <ul>
 *   <li>相邻且发送者相同 → 合并为一组(气泡连排);</li>
 *   <li>发送者提取失败(null = 系统/广播)→ 每条独立成组,并切断前后合并;</li>
 *   <li>「自己」判定:发送者 == 本地玩家名(调用方传入,视图模型不依赖 Minecraft);</li>
 *   <li>输出组序 = 时间正序(旧 → 新)。</li>
 * </ul>
 */
public final class MessageGrouper {

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
        // 从最旧到最新遍历,保证「相邻」判断与组内时间正序
        for (int i = recordsNewestFirst.size() - 1; i >= 0; i--) {
            ChatLineRecord record = recordsNewestFirst.get(i);
            SenderExtractor.SenderMatch match = extractor.extract(record.getPlainText());
            String sender = match == null ? null : match.getSender();
            if (sender == null) {
                groups.add(MessageGroupModel.system(record));
                current = null;
                currentSender = null;
                continue;
            }
            if (current != null && sender.equals(currentSender)) {
                current.addLine(record, match.getRest());
            } else {
                boolean isSelf = selfName != null && sender.equals(selfName);
                current = MessageGroupModel.player(sender, isSelf, record, match.getRest());
                currentSender = sender;
                groups.add(current);
            }
        }
        return groups;
    }
}
