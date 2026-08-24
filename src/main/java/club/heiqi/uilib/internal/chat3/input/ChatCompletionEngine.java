package club.heiqi.uilib.internal.chat3.input;

import java.util.Collections;
import java.util.List;

/**
 * Tab 补全状态机(T2):idle → awaiting(requestSnapshot) → cycling(candidates, index) 三态,
 * 还原原版 1.7.10 体感:
 * <ul>
 *   <li>首 Tab 统一发 C14PacketTabComplete(光标前全文,玩家名+命令均由服务端应答);</li>
 *   <li>响应两段式:候选最长公共前缀(大小写不敏感比较)比当前词长 → 就地扩为公共前缀、不弹列表;
 *       已处于公共前缀 → 进入循环态(首候选立即入框 + 候选 ", " 连接打印进聊天区);</li>
 *   <li>循环态:Tab 依次替换候选(到尾回卷 0),Shift+Tab 反向(增强);每次以同 messageId 覆盖打印;</li>
 *   <li>stale 守卫:任意输入变化清循环/等待态;响应到达校验请求时全文快照(非布尔),不匹配丢弃;</li>
 *   <li>R1:本地 playerInfoList 只做无网络兜底(大小写不敏感匹配),绝不本地先行、服务端后到覆盖。</li>
 * </ul>
 */
final class ChatCompletionEngine {

    enum Phase {
        IDLE,
        AWAITING,
        CYCLING
    }

    /** 宿主窄端口(ChatInputBar 实现,便于 headless 单测)。 */
    interface Host {
        /** @return 当前输入全文(真值) */
        String currentText();

        /** 提交补全结果(含 caret 对齐) */
        void commit(String nextText);

        /** @return 是否可用网络发包(多人/集成服务器) */
        boolean networkAvailable();

        /** 发送 C14PacketTabComplete(光标前全文) */
        void sendRequest(String text);

        /** @return 本地玩家名列表(无网络兜底候选源) */
        List<String> localPlayerNames();

        /** 候选列表打印进聊天区(", " 连接,同 messageId 覆盖) */
        void printCandidates(List<String> candidates);
    }

    private final Host host;

    private Phase phase = Phase.IDLE;
    /** AWAITING 时的请求全文快照(stale 守卫用,非布尔)。 */
    private String requestSnapshot;
    private List<String> cyclingCandidates = Collections.emptyList();
    private int cyclingIndex;

    ChatCompletionEngine(Host host) {
        this.host = host;
    }

    /**
     * Tab/Shift+Tab 入口。
     *
     * @param direction +1 正向循环(Tab),-1 反向(Shift+Tab)
     */
    void onTab(int direction) {
        if (phase == Phase.CYCLING) {
            cycle(direction);
            return;
        }
        String text = host.currentText();
        if (text == null || text.isEmpty()) {
            return;
        }
        if (host.networkAvailable()) {
            // 原版路径:统一发 C14(玩家名+命令均由服务端应答);首 Tab 延迟为原版固有体感
            requestSnapshot = text;
            phase = Phase.AWAITING;
            host.sendRequest(text);
        } else {
            handleLocalFallback(text);
        }
    }

    /** 服务端补全响应(mixin 转交链)。 */
    void onResponse(String[] options) {
        if (phase != Phase.AWAITING) {
            return; // 非等待态响应丢弃
        }
        String snapshot = requestSnapshot;
        requestSnapshot = null;
        phase = Phase.IDLE;
        if (!snapshot.equals(host.currentText())) {
            return; // stale 守卫:请求时文本快照不匹配,丢弃
        }
        List<String> candidates = ChatCompletionState.dedupe(options);
        if (candidates.isEmpty()) {
            return;
        }
        applyCompletion(snapshot, candidates);
    }

    /** 任意输入变化(用户编辑/历史回显/外部写入)即清循环态/等待态。 */
    void onTextEdited() {
        phase = Phase.IDLE;
        requestSnapshot = null;
        cyclingCandidates = Collections.emptyList();
        cyclingIndex = 0;
    }

    /** 本地无网络兜底:同步走两段式,不经 AWAITING。 */
    private void handleLocalFallback(String text) {
        if (text.startsWith("/")) {
            return; // 沿用现有 / 开头判断:命令补全无本地来源
        }
        String word = text.substring(ChatCompletionState.wordStart(text));
        if (word.isEmpty()) {
            return;
        }
        List<String> candidates = ChatCompletionState.matchCaseInsensitive(host.localPlayerNames(), word);
        if (candidates.isEmpty()) {
            return;
        }
        applyCompletion(text, candidates);
    }

    /** 两段式(原版体感):公共前缀比当前词长 → 扩为公共前缀;已处于公共前缀 → 进入循环态。 */
    private void applyCompletion(String text, List<String> candidates) {
        int wordStart = ChatCompletionState.wordStart(text);
        String word = text.substring(wordStart);
        String prefix = ChatCompletionState.commonPrefix(candidates);
        if (prefix != null && !prefix.isEmpty()
                && ChatCompletionState.fold(prefix).startsWith(ChatCompletionState.fold(word))
                && ChatCompletionState.fold(prefix).length() > ChatCompletionState.fold(word).length()) {
            // 第一段:就地扩为公共前缀(保留前缀与 "/",修掉补全丢 / bug),不弹列表
            host.commit(text.substring(0, wordStart) + prefix);
            return;
        }
        // 第二段:已处于公共前缀 → 进入循环态,首候选立即入框 + 候选列表打印进聊天区
        cyclingCandidates = candidates;
        cyclingIndex = 0;
        phase = Phase.CYCLING;
        host.commit(text.substring(0, wordStart) + candidates.get(0));
        host.printCandidates(candidates);
    }

    /** 循环态替换:候选[index] 依次替换(到尾回卷 0;Shift+Tab 反向),每次以同 messageId 覆盖打印。 */
    private void cycle(int direction) {
        int size = cyclingCandidates.size();
        if (size == 0) {
            phase = Phase.IDLE;
            return;
        }
        cyclingIndex = ChatCompletionState.cycleIndex(cyclingIndex, direction, size);
        String text = host.currentText();
        int wordStart = ChatCompletionState.wordStart(text);
        host.commit(text.substring(0, wordStart) + cyclingCandidates.get(cyclingIndex));
        host.printCandidates(cyclingCandidates);
    }
}
