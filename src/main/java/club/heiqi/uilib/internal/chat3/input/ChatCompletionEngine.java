package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tab 补全状态机(T2):idle → awaiting(requestSnapshot) → cycling(candidates, index) 三态,
 * 还原原版 1.7.10 体感:
 * <ul>
 *   <li>首 Tab 统一发 C14PacketTabComplete(光标前全文,玩家名+命令均由服务端应答);</li>
 *   <li>客户端命令本地候选(forge 补丁同构):"/" 开头文本在请求时同步调
 *       {@link Host#localCommandCompletions(String)} 并随请求快照缓存,响应到达时
 *       本地在前合并去重(dedupe(concat(local, server)))——本地不先行覆盖服务端,首 Tab RTT 体感不变;</li>
 *   <li>响应两段式:候选最长公共前缀(大小写不敏感比较)比当前词长 → 就地扩为公共前缀、不弹列表;
 *       已处于公共前缀 → 进入循环态(首候选立即入框 + 候选 ", " 连接打印进聊天区);</li>
 *   <li>候选打印仅 size>1(原版 GuiChat:244):单候选直达/公共前缀直达不刷聊天区;</li>
 *   <li>循环态:Tab 依次替换候选(到尾回卷 0),Shift+Tab 反向(增强);每次以同 messageId 覆盖打印;</li>
 *   <li>stale 守卫:任意输入变化清循环/等待态与本地候选缓存;响应到达校验请求时全文快照(非布尔),不匹配丢弃;</li>
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

        /**
         * @return 本地客户端命令候选(Forge ClientCommandHandler 注册表,同步纯函数):
         *         候选已可直接入框(正在补命令名时含 "/" 前缀);非 "/" 文本/无候选返回空
         */
        List<String> localCommandCompletions(String text);

        /** 候选列表打印进聊天区(", " 连接,同 messageId 覆盖) */
        void printCandidates(List<String> candidates);
    }

    private final Host host;

    private Phase phase = Phase.IDLE;
    /** AWAITING 时的请求全文快照(stale 守卫用,非布尔)。 */
    private String requestSnapshot;
    /** AWAITING 时缓存的本地客户端命令候选(随请求快照同生命周期,响应时本地在前合并)。 */
    private List<String> requestLocalOptions = Collections.emptyList();
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
            // 原版路径:统一发 C14(玩家名+命令均由服务端应答);首 Tab 延迟为原版固有体感。
            // forge 补丁同构:请求时同步算本地客户端命令候选并缓存,响应到达时本地在前合并,
            // 本地绝不先行覆盖服务端。
            requestSnapshot = text;
            requestLocalOptions = text.startsWith("/")
                    ? new ArrayList<String>(host.localCommandCompletions(text))
                    : Collections.<String>emptyList();
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
        List<String> local = requestLocalOptions;
        requestSnapshot = null;
        requestLocalOptions = Collections.emptyList();
        phase = Phase.IDLE;
        if (!snapshot.equals(host.currentText())) {
            return; // stale 守卫:请求时文本快照不匹配,丢弃
        }
        List<String> candidates = mergeLocalFirst(local, options);
        if (candidates.isEmpty()) {
            return;
        }
        applyCompletion(snapshot, candidates);
    }

    /** 本地在前合并 + 保序去重(与 forge ObjectArrays.concat(latestAutoComplete, server) 同序)。 */
    private static List<String> mergeLocalFirst(List<String> local, String[] server) {
        List<String> merged = new ArrayList<String>(local == null ? 0 : local.size());
        if (local != null) {
            for (String option : local) {
                if (option != null && !merged.contains(option)) {
                    merged.add(option);
                }
            }
        }
        if (server != null) {
            for (String option : server) {
                if (option != null && !merged.contains(option)) {
                    merged.add(option);
                }
            }
        }
        return merged;
    }

    /** 任意输入变化(用户编辑/历史回显/外部写入)即清循环态/等待态与本地候选缓存。 */
    void onTextEdited() {
        phase = Phase.IDLE;
        requestSnapshot = null;
        requestLocalOptions = Collections.emptyList();
        cyclingCandidates = Collections.emptyList();
        cyclingIndex = 0;
    }

    /** 本地无网络兜底:同步走两段式,不经 AWAITING。 */
    private void handleLocalFallback(String text) {
        if (text.startsWith("/")) {
            // 无网络时不存在服务端应答,本地命令候选直接走两段式(不违反 R1 本地不先行);
            // 候选来源 = Forge ClientCommandHandler 注册表(覆盖全部 mod 客户端命令)
            List<String> local = host.localCommandCompletions(text);
            if (local.isEmpty()) {
                return;
            }
            applyCompletion(text, local);
            return;
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
        // 第二段:已处于公共前缀 → 进入循环态,首候选立即入框;候选打印仅 size>1(原版 GuiChat:244)
        cyclingCandidates = candidates;
        cyclingIndex = 0;
        phase = Phase.CYCLING;
        host.commit(text.substring(0, wordStart) + candidates.get(0));
        if (candidates.size() > 1) {
            host.printCandidates(candidates);
        }
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
        if (size > 1) {
            host.printCandidates(cyclingCandidates);
        }
    }
}
