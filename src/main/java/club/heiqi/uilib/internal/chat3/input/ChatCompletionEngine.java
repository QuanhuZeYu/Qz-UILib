package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tab 补全状态机(T2):idle → awaiting(pending FIFO 队列) → cycling(candidates, index) 三态,
 * 还原原版 1.7.10 体感:
 * <ul>
 *   <li>首 Tab 统一发 C14PacketTabComplete(光标前全文,玩家名+命令均由服务端应答);</li>
 *   <li>客户端命令本地候选(forge 补丁同构):"/" 开头文本在请求时同步调
 *       {@link Host#localCommandCompletions(String)} 并随请求快照缓存,响应到达时
 *       本地在前合并去重(dedupe(concat(local, server)))——本地不先行覆盖服务端,首 Tab RTT 体感不变;</li>
 *   <li>I4 跨请求错配修复:单槽请求快照升级为 FIFO 快照队列,AWAITING 期重复 Tab
 *       追加请求(每次 Tab 都发请求,原版体感),绝不覆盖既有请求;响应按同连接 FIFO 配对
 *       (1.7.10 S3APacketTabComplete 无 transaction id,同连接响应顺序=请求顺序,只能 FIFO);</li>
 *   <li>双守卫解耦:队列负责「哪个响应对应哪个请求」,快照守卫负责「请求是否过期」
 *       (队首快照 != 当前文本 → 丢弃该响应,队列逐条自对齐);</li>
 *   <li>响应两段式:候选最长公共前缀(大小写不敏感比较)比当前词长 → 就地扩为公共前缀、不弹列表;
 *       已处于公共前缀 → 进入循环态(首候选立即入框 + 候选 ", " 连接打印进聊天区);</li>
 *   <li>候选打印仅 size>1(原版 GuiChat:244):单候选直达/公共前缀直达不刷聊天区;</li>
 *   <li>循环态:Tab 依次替换候选(到尾回卷 0),Shift+Tab 反向(增强);每次以同 messageId 覆盖打印;</li>
 *   <li>stale 守卫:任意输入变化清循环/等待态与 pending 队列;响应到达校验请求时全文快照(非布尔),不匹配丢弃;</li>
 *   <li>断线边界:AWAITING 期 Tab 检测 networkAvailable()==false → 清空 pending 后走本地兜底
 *       (残留响应回来无 pending 可配对,被 phase 守卫丢弃);</li>
 *   <li>R1:本地 playerInfoList 只做无网络兜底(大小写不敏感匹配),绝不本地先行、服务端后到覆盖。</li>
 * </ul>
 *
 * <p>边界分析:同 TCP 连接 C14/S3A 响应 FIFO 严格成立(1.7.10 协议保证);断线残留 pending 由
 * 下次 Tab 的 networkAvailable() 检测清空;非标准服务端乱序响应 → 队首快照不匹配被丢弃,
 * 队列逐条自对齐;与 I5(请求屏蔽)交互:屏蔽只减少请求数,不改变队列配对语义。</p>
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
    /**
     * AWAITING 期在途请求 FIFO 队列(每份请求含发出时的全文快照 + 本地命令候选缓存)。
     * 不变量:phase == AWAITING ⟺ pending 非空。
     */
    private final ArrayDeque<PendingRequest> pending = new ArrayDeque<PendingRequest>();
    private List<String> cyclingCandidates = Collections.emptyList();
    private int cyclingIndex;

    /**
     * 单份在途请求快照:发出时的输入全文(stale 快照守卫,非布尔)
     * + 随请求缓存的本地客户端命令候选(响应时本地在前合并,同生命周期)。
     */
    private static final class PendingRequest {
        final String snapshot;
        final List<String> localOptions;

        PendingRequest(String snapshot, List<String> localOptions) {
            this.snapshot = snapshot;
            this.localOptions = localOptions;
        }
    }

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
            // AWAITING 期重复 Tab 同样发请求(FIFO 追加不覆盖),每次 Tab 都发请求保持原版体感;
            // forge 补丁同构:请求时同步算本地客户端命令候选并随快照缓存,本地绝不先行覆盖服务端。
            pending.addLast(capture(text));
            phase = Phase.AWAITING;
            host.sendRequest(text);
        } else {
            // 断线兜底:在途请求已随连接作废 → 清空 pending 再走本地兜底
            // (旧请求残留响应回来时无 pending 可配对,被 phase 守卫丢弃)。
            pending.clear();
            phase = Phase.IDLE;
            handleLocalFallback(text);
        }
    }

    /** 服务端补全响应(mixin 转交链)。 */
    void onResponse(String[] options) {
        if (phase != Phase.AWAITING) {
            return; // 非等待态响应丢弃
        }
        PendingRequest req = pending.pollFirst();
        if (req == null) {
            // 防御:不变量被破坏时的自愈(理论不可达;不 NPE、不留下空转的 AWAITING)
            phase = Phase.IDLE;
            return;
        }
        // 双守卫解耦:队列(FIFO)决定「哪个响应对应哪个请求」,快照守卫决定「请求是否过期」。
        // 队首快照 != 当前文本 → 该请求已过期,丢弃此响应;队列仍有在途请求则继续等待后续响应。
        if (!req.snapshot.equals(host.currentText())) {
            phase = pending.isEmpty() ? Phase.IDLE : Phase.AWAITING;
            return;
        }
        // 快照匹配即应用是安全充分条件:候选只依赖请求快照文本,快照 == 当前文本 ⟹ 候选按当前文本计算。
        List<String> candidates = mergeLocalFirst(req.localOptions, options);
        if (candidates.isEmpty()) {
            phase = pending.isEmpty() ? Phase.IDLE : Phase.AWAITING;
            return;
        }
        // 应用会改变输入(commit),使队列中剩余请求的快照全部过期:继续等待只会逐个丢弃,
        // 此处直接清空(与 onTextEdited 同构),并避免 applyCompletion 的 CYCLING 副作用被覆盖。
        pending.clear();
        phase = Phase.IDLE;
        applyCompletion(req.snapshot, candidates); // 第二段会覆盖 phase=CYCLING,第一段保持 IDLE
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

    /** 任意输入变化(用户编辑/历史回显/外部写入)即清循环态/等待态与 pending 队列。 */
    void onTextEdited() {
        phase = Phase.IDLE;
        pending.clear();
        cyclingCandidates = Collections.emptyList();
        cyclingIndex = 0;
    }

    /** 请求快照捕获:发出时全文 + 随请求缓存的本地客户端命令候选。 */
    private PendingRequest capture(String text) {
        return new PendingRequest(text, text.startsWith("/")
                ? new ArrayList<String>(host.localCommandCompletions(text))
                : Collections.<String>emptyList());
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
