package club.heiqi.uilib.api.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * 聊天工具栏动作注册表（候选 API，规划《聊天工具栏与HUD布局编辑》P1）。
 *
 * <p>把按钮装配从 {@code ChatInputBar} 解耦：业务 Mod 只依赖本入口，不碰
 * {@code internal/chat3}。重复 id 明确拒绝（不静默覆盖）；注册与注销限客户端主线程；
 * 跨聊天开关保持注册，每次打开重新挂载 UI。内置「编辑 HUD」走同一注册链。</p>
 */
public final class ChatActionService {

    private static final ChatActionService INSTANCE = new ChatActionService();

    private static final Comparator<Entry> ORDER = new Comparator<Entry>() {
        @Override
        public int compare(Entry a, Entry b) {
            int byOrder = Integer.compare(a.action.getOrder(), b.action.getOrder());
            return byOrder != 0 ? byOrder : Long.compare(a.sequence, b.sequence);
        }
    };

    private final List<Entry> entries = new ArrayList<Entry>();
    private final Signal<Integer> revision = Signal.create(Integer.valueOf(0));
    private long nextSequence;
    private int revisionValue;

    private ChatActionService() {
    }

    /** @return 全局注册表单例 */
    public static ChatActionService getInstance() {
        return INSTANCE;
    }

    /**
     * 注册动作。
     *
     * @param action 动作（不可为 null；id 重复立即失败）
     * @return 注销句柄
     */
    public synchronized ChatActionRegistration register(ChatAction action) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        for (Entry entry : entries) {
            if (entry.action.getId().equals(action.getId())) {
                throw new IllegalArgumentException("duplicate chat action id: " + action.getId());
            }
        }
        final Entry entry = new Entry(action, nextSequence++);
        entries.add(entry);
        bump();
        return new ChatActionRegistration(new Runnable() {
            @Override
            public void run() {
                remove(entry);
            }
        });
    }

    /** @return 按 order + 注册序排序的动作快照（不可变） */
    public synchronized List<ChatAction> actions() {
        List<Entry> snapshot = new ArrayList<Entry>(entries);
        Collections.sort(snapshot, ORDER);
        List<ChatAction> result = new ArrayList<ChatAction>(snapshot.size());
        for (Entry entry : snapshot) {
            result.add(entry.action);
        }
        return Collections.unmodifiableList(result);
    }

    /** @return 注册表版本（增删时 +1，工具栏 Computed 依赖点） */
    public ReadableSignal<Integer> revision() {
        return revision;
    }

    /** 清空注册表（测试与接管关闭清理用）。 */
    public synchronized void clear() {
        entries.clear();
        bump();
    }

    private synchronized void remove(Entry entry) {
        if (entries.remove(entry)) {
            bump();
        }
    }

    private void bump() {
        revisionValue++;
        revision.set(Integer.valueOf(revisionValue));
    }

    private static final class Entry {
        final ChatAction action;
        final long sequence;
        Entry(ChatAction action, long sequence) {
            this.action = action;
            this.sequence = sequence;
        }
    }
}
