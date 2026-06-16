package club.heiqi.uilib.ui.reactive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 中央事务日志（信条四：所有状态写入收口到中央事务）。
 *
 * <p>一次 {@link ReactiveScheduler#flush()} 内应用的所有 signal 写入合并为一个原子 {@link Transaction}
 * （I9 批处理在数据结构上的体现），记入一个<b>有界环形缓冲</b>。每个事务携带单调序号、时间戳、可选标签，
 * 与逐条 {@link Entry}（signal、before、after），共同构成信条四要求的<b>单一审计路径</b>——
 * 永远能回答「谁、何时、因何改了它」。</p>
 *
 * <p><b>游标时间旅行</b>（信条四②）：{@link #cursor()} 把日志分为「已应用」段 {@code [0, cursor)} 与
 * 「可重做的未来」段 {@code [cursor, size)}。撤销 = 游标后退并由调度器回退该事务的 before 值；
 * 重做 = 游标前进并重新应用 after 值。游标位置即一个逻辑状态快照——无需为每个 signal 存全量值快照
 * （这正是用户拍板的「仅日志 + 游标」模型，不引入 signal 全局注册表）。</p>
 *
 * <p><b>有界</b>：超出 {@link #getCapacity()} 时丢弃最老事务（环形淘汰），换来「常驻但可控」的内存代价
 * （第 6 节内存预算判据：日志换来时间旅行调试与可追溯）。在游标中段提交新事务会截断其后的「未来」段
 * （标准 undo/redo 语义：新分支覆盖旧重做分支）。</p>
 *
 * <p><b>单线程假设</b>：与调度器一致，仅在 UI 线程访问，未加同步。</p>
 */
public final class TransactionLog {

    /** 默认环形缓冲容量（事务条数）。 */
    public static final int DEFAULT_CAPACITY = 256;

    /** 单条写入记录：某 signal 在本事务内从 {@code before} 变为 {@code after}。 */
    public static final class Entry {
        private final Signal<?> signal;
        private final Object before;
        private final Object after;

        Entry(Signal<?> signal, Object before, Object after) {
            this.signal = signal;
            this.before = before;
            this.after = after;
        }

        /** 被写入的 signal（审计：改了哪个状态）。 */
        public Signal<?> signal() { return signal; }
        /** 事务前的旧值（undo 目标）。 */
        public Object before() { return before; }
        /** 事务后的新值（redo 目标）。 */
        public Object after() { return after; }

        @Override
        public String toString() {
            return before + " -> " + after;
        }
    }

    /** 一次 flush 提交的原子事务：序号 + 时间戳 + 可选标签 + 若干 {@link Entry}。 */
    public static final class Transaction {
        private final long sequence;
        private final long timestampMillis;
        private final String label;
        private final List<Entry> entries;

        Transaction(long sequence, long timestampMillis, String label, List<Entry> entries) {
            this.sequence = sequence;
            this.timestampMillis = timestampMillis;
            this.label = label;
            this.entries = Collections.unmodifiableList(entries);
        }

        /** 单调递增序号（审计：发生先后）。 */
        public long sequence() { return sequence; }
        /** 提交时刻（审计：何时）。 */
        public long timestampMillis() { return timestampMillis; }
        /** 可选标签（审计：因何而改），无标签为 {@code null}。 */
        public String label() { return label; }
        /** 本事务的写入记录（不可变）。 */
        public List<Entry> entries() { return entries; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('#').append(sequence);
            if (label != null) sb.append(" [").append(label).append(']');
            sb.append(" {");
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(entries.get(i));
            }
            sb.append('}');
            return sb.toString();
        }
    }

    private int capacity = DEFAULT_CAPACITY;
    private boolean enabled = true;
    private long nextSequence = 1;
    private final ArrayList<Transaction> transactions = new ArrayList<>();
    /** 已应用事务数：{@code [0, cursor)} 已应用，{@code [cursor, size)} 为可重做的未来。 */
    private int cursor = 0;

    /** 日志是否记录写入。关闭后 flush 不再建事务（零额外开销），已有日志保留。 */
    public boolean isEnabled() { return enabled; }

    /** 开关日志记录。 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** 当前环形缓冲容量（事务条数）。 */
    public int getCapacity() { return capacity; }

    /**
     * 设置环形缓冲容量；若现有事务超出新容量，立即从最老端淘汰。
     *
     * @param capacity 容量，必须 {@code >= 1}
     */
    public void setCapacity(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("容量必须 >= 1：" + capacity);
        }
        this.capacity = capacity;
        evictToCapacity();
    }

    /**
     * 由 {@link ReactiveScheduler} 调用：提交一个新事务。
     *
     * <p>先截断游标之后的「未来」段（覆盖旧重做分支），追加新事务并前移游标，再按容量从最老端淘汰。</p>
     *
     * @param timestampMillis 提交时刻
     * @param label           可选标签
     * @param entries         写入记录（非空）
     * @return 新建的事务
     */
    Transaction commit(long timestampMillis, String label, List<Entry> entries) {
        // 截断「未来」段：在游标中段提交意味着放弃原重做分支
        while (transactions.size() > cursor) {
            transactions.remove(transactions.size() - 1);
        }
        Transaction txn = new Transaction(nextSequence++, timestampMillis, label, entries);
        transactions.add(txn);
        cursor++;
        evictToCapacity();
        return txn;
    }

    private void evictToCapacity() {
        while (transactions.size() > capacity) {
            transactions.remove(0);
            if (cursor > 0) cursor--;
        }
    }

    /** 是否可撤销（游标之前还有已应用事务）。 */
    public boolean canUndo() { return cursor > 0; }

    /** 是否可重做（游标之后还有未来事务）。 */
    public boolean canRedo() { return cursor < transactions.size(); }

    /**
     * 由调度器调用：游标后退一格，返回需回退的事务（保留在日志中供重做）；不可撤销时返回 {@code null}。
     */
    Transaction stepBack() {
        if (cursor <= 0) return null;
        cursor--;
        return transactions.get(cursor);
    }

    /**
     * 由调度器调用：返回需重新应用的事务并前移游标；不可重做时返回 {@code null}。
     */
    Transaction stepForward() {
        if (cursor >= transactions.size()) return null;
        Transaction txn = transactions.get(cursor);
        cursor++;
        return txn;
    }

    /** 当前日志中的事务总数（含可重做的未来段）。 */
    public int size() { return transactions.size(); }

    /** 当前游标位置（= 已应用事务数）。 */
    public int cursor() { return cursor; }

    /** 当前已应用的最新事务（游标处），无则 {@code null}。 */
    public Transaction current() {
        return cursor > 0 ? transactions.get(cursor - 1) : null;
    }

    /** 全部事务的不可变快照视图（审计/调试用）。 */
    public List<Transaction> transactions() {
        return Collections.unmodifiableList(new ArrayList<>(transactions));
    }

    /** 清空日志条目与游标，保留单调序号（审计计数器不回退）与容量/开关设置。 */
    public void clear() {
        transactions.clear();
        cursor = 0;
    }

    /** 仅供单元测试 setUp/tearDown：完全复位（含序号归 1）。 */
    void resetForTest() {
        transactions.clear();
        cursor = 0;
        nextSequence = 1;
        capacity = DEFAULT_CAPACITY;
        enabled = true;
    }
}
