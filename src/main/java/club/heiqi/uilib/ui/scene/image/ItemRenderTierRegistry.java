package club.heiqi.uilib.ui.scene.image;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import com.github.bsideup.jabel.Desugar;

/**
 * ItemRenderTierRegistry —— 物品图标渲染分级注册表（平台中立，只认稳定字符串键）。
 *
 * <h3>三次追踪 → 永久分级</h3>
 * <p>每个 registryKey（候选域键：候选 {@code candidateKey} 或变体 {@code candidateKey@meta}，
 * 由选择器图标源覆写 {@code SceneImageSource#registryKey()} 提供给分级）初始为
 * {@link Tier#TRACKING}，前 {@link #TRACK_ATTEMPTS} 次渲染带全量检查（GL 错误检测 + 异常捕获），
 * 追踪结束后永久分级：</p>
 * <ol>
 *   <li>{@link Tier#RENDERABLE}：追踪期全部干净 → 快路径，不再逐帧 GL 检查；</li>
 *   <li>{@link Tier#UNRENDERABLE}：追踪期出现过渲染异常 → 停止渲染，宿主回退占位样式；</li>
 *   <li>{@link Tier#NEEDS_ISOLATION}：渲染可用但遗留 GL 错误 → 每次渲染状态隔离 + 排空错误。</li>
 * </ol>
 * <p>{@link Tier#NEEDS_ISOLATION} 连续 {@link #ISOLATION_FAILURE_LIMIT} 次渲染异常会升级为
 * {@link Tier#UNRENDERABLE}（隔离仍失败 = 稳定不可渲染）。</p>
 *
 * <h3>职责边界</h3>
 * <p>平台渲染层（{@code ui.render}）在每次物品图标绘制前后调用 {@link #classify} 上报结果；
 * 分级变化经 {@link Listener} 通知宿主（如选择器列表把不可渲染项回退占位样式）。
 * 本类不依赖 Minecraft/LWJGL 类型，键由图片源自己的 {@code registryKey()} 派生。</p>
 *
 * <h3>有界性与失效（生产语义，取代「会话内永久记忆」）</h3>
 * <ul>
 *   <li><b>活跃表 LRU</b>：上限 {@link #MAX_ACTIVE_ENTRIES}；超限淘汰<b>最久未访问</b>条目，
 *       访问序由条目内的 {@code lastAccessTick}（{@link #tierOf}/{@link #classify} 时更新）承担，
 *       淘汰时才做选择，不持有全局锁、不加 synchronized（读路径保持无锁）。</li>
 *   <li><b>UNRENDERABLE 不可复活（tombstone）</b>：被判 {@link Tier#UNRENDERABLE} 的键
 *       <b>永不逐出</b>——被淘汰时移入 {@link #TOMBSTONES}（上限 {@link #MAX_TOMBSTONES}），
 *       {@link #tierOf} 命中 tombstone 直接返回 UNRENDERABLE，绝不回到 TRACKING
 *       （否则该物品会被重新送进渲染路径做带 GL 检测的追踪渲染，复现本来被隔离的失败）。
 *       tombstone 满额时<b>不淘汰既有 tombstone</b>，新的 UNRENDERABLE 键退化为「留在活跃表内不淘汰」。</li>
 *   <li><b>生产失效入口</b>：{@link #invalidateAll(String)} 清两张表 + {@link #tierGeneration()} 自增 +
 *       <b>一次</b>代际广播；{@link #tierGeneration()} 只读用于「该帧分级结论作废」判定
 *       （逐键回调会引发 N 次重取风暴，故不逐键通知）。</li>
 *   <li>写路径（{@link #classify}/{@link #invalidateAll}）只允许客户端主线程；读路径
 *       （{@link #tierOf}）在渲染线程无锁读取（两张表均为 {@code ConcurrentHashMap}，可见性有保证）。</li>
 * </ul>
 * <p>测试用 {@link #resetForTests()} 清零（同时清两张表、代际与监听器）。</p>
 */
public final class ItemRenderTierRegistry {

    /** 渲染分级。 */
    public enum Tier {
        /** 追踪中：前 {@link #TRACK_ATTEMPTS} 次渲染带全量检查。 */
        TRACKING,
        /** 可渲染：追踪期全部干净，快路径。 */
        RENDERABLE,
        /** 不可渲染：停止渲染，宿主回退占位样式。 */
        UNRENDERABLE,
        /** 需要状态隔离：渲染可用但会遗留 GL 错误。 */
        NEEDS_ISOLATION
    }

    /** 单次渲染结果。 */
    public enum Outcome {
        /** 渲染完成且无 GL 错误。 */
        OK,
        /** 渲染完成但遗留 GL 错误（需状态隔离）。 */
        GL_ERROR,
        /** 渲染抛异常（可能不可渲染）。 */
        EXCEPTION
    }

    /** 追踪渲染次数上限。 */
    public static final int TRACK_ATTEMPTS = 3;
    /** 隔离态连续异常升级为不可渲染的阈值。 */
    public static final int ISOLATION_FAILURE_LIMIT = 3;
    /** 活跃表条目上限（LRU 淘汰边界）。 */
    public static final int MAX_ACTIVE_ENTRIES = 8192;
    /** UNRENDERABLE tombstone 条目上限（不可逐出集合自身必须有界）。 */
    public static final int MAX_TOMBSTONES = 1024;
    /** 超限时一次淘汰到的低水位与上限之差（摊薄排序成本；稳态容量仍严格 ≤ 上限）。 */
    private static final int EVICT_BATCH = 512;

    /** 分级变更通知（key + 新分级 + 变更缘由）。 */
    @Desugar
    public record Classification(String registryKey, Tier tier, String detail) {
    }

    /** 分级变更监听器。 */
    public interface Listener {

        void onClassification(Classification classification);

        /**
         * 整表失效广播（{@link #invalidateAll(String)} 后<b>只调用一次</b>，不逐键回调）。
         *
         * <p>需要逐键处理的订阅方自行比对 {@link #tierGeneration()}（O(1) long 读）。</p>
         *
         * @param reason 失效缘由
         */
        default void onInvalidated(String reason) {
        }
    }

    /** 内部条目状态（可变，同步于 {@link #ENTRIES}）。 */
    static final class Entry {

        Tier tier;
        int attemptsLeft = TRACK_ATTEMPTS;
        int exceptionAttempts;
        int glErrorAttempts;
        int consecutiveIsolationFailures;
        String detail = "";
        /** 最近访问顺序（LRU 用；只在写路径与读路径原地更新，不参与 equals/hashCode）。 */
        volatile long lastAccessTick;

        Entry() {
            this.tier = Tier.TRACKING;
        }

        Entry(Tier tier, String detail) {
            this.tier = tier;
            this.attemptsLeft = 0;
            this.detail = detail == null ? "" : detail;
        }
    }

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    /** UNRENDERABLE 墓碑：值 = 最近访问顺序（不可逐出，绝不回到 TRACKING）。 */
    private static final Map<String, Long> TOMBSTONES = new ConcurrentHashMap<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final AtomicLong ACCESS_TICK = new AtomicLong();
    private static volatile long tierGeneration;

    private ItemRenderTierRegistry() {
    }

    /**
     * 查询分级（不创建条目）；未知键返回 {@link Tier#TRACKING}，tombstone 命中恒返回
     * {@link Tier#UNRENDERABLE}。
     *
     * @param registryKey 稳定注册键（非 null）
     * @return 当前分级
     */
    public static Tier tierOf(String registryKey) {
        if (registryKey == null) {
            return Tier.TRACKING;
        }
        if (TOMBSTONES.containsKey(registryKey)) {
            return Tier.UNRENDERABLE;
        }
        Entry entry = ENTRIES.get(registryKey);
        if (entry == null) {
            return Tier.TRACKING;
        }
        entry.lastAccessTick = ACCESS_TICK.incrementAndGet();
        return entry.tier;
    }

    /**
     * 上报一次渲染结果并推进状态机；分级变化时通知全部监听器。
     *
     * @param registryKey 稳定注册键（null 忽略）
     * @param outcome     单次渲染结果
     * @param detail      变更缘由（异常类名/GL 错误码等，可为 null）
     */
    public static void classify(String registryKey, Outcome outcome, String detail) {
        if (registryKey == null || outcome == null) {
            return;
        }
        if (TOMBSTONES.containsKey(registryKey)) {
            // 已判 UNRENDERABLE 的键是终态：既不复活也不重复通知（进程重启/资源重载经 invalidateAll 复位）。
            return;
        }
        // 逐键原子推进：Entry 可变计数在 CHM.compute 的键级锁内原地推进，
        // 分级变化时以新 Entry 替换旧条目；监听器通知在 compute 之外发布（守原语义）。
        Entry[] changed = new Entry[1];
        long tick = ACCESS_TICK.incrementAndGet();
        ENTRIES.compute(registryKey, (key, existing) -> {
            Entry entry = existing == null ? new Entry() : existing;
            entry.lastAccessTick = tick;
            Entry next = classifyEntry(entry, outcome, detail);
            if (next != null) {
                next.lastAccessTick = tick;
                changed[0] = next;
                return next;
            }
            return entry;
        });
        Entry next = changed[0];
        if (next != null) {
            Classification classification = new Classification(registryKey, next.tier, next.detail);
            for (Listener listener : LISTENERS) {
                try {
                    listener.onClassification(classification);
                } catch (RuntimeException ignored) {
                    // 监听器故障不得影响渲染分级推进。
                }
            }
        }
        evictToBound();
    }

    /**
     * 纯状态机：按 outcome 推进条目；分级变化时返回新 Entry（携带变更缘由），否则返回 null。
     *
     * @param entry   当前条目（原地推进计数）
     * @param outcome 单次渲染结果
     * @param detail  变更缘由
     * @return 分级变化后的新 Entry；未变化返回 null
     */
    static Entry classifyEntry(Entry entry, Outcome outcome, String detail) {
        String safeDetail = detail == null ? "" : detail;
        switch (entry.tier) {
            case TRACKING: {
                entry.attemptsLeft--;
                if (outcome == Outcome.EXCEPTION) {
                    entry.exceptionAttempts++;
                    entry.detail = safeDetail;
                } else if (outcome == Outcome.GL_ERROR) {
                    entry.glErrorAttempts++;
                    entry.detail = safeDetail;
                }
                if (entry.attemptsLeft > 0) {
                    return null;
                }
                if (entry.exceptionAttempts > 0) {
                    return new Entry(Tier.UNRENDERABLE, entry.detail);
                }
                if (entry.glErrorAttempts > 0) {
                    return new Entry(Tier.NEEDS_ISOLATION, entry.detail);
                }
                return new Entry(Tier.RENDERABLE, entry.detail);
            }
            case NEEDS_ISOLATION: {
                if (outcome == Outcome.EXCEPTION) {
                    entry.consecutiveIsolationFailures++;
                    entry.detail = safeDetail;
                    if (entry.consecutiveIsolationFailures >= ISOLATION_FAILURE_LIMIT) {
                        return new Entry(Tier.UNRENDERABLE, safeDetail);
                    }
                } else {
                    entry.consecutiveIsolationFailures = 0;
                }
                return null;
            }
            default:
                return null;
        }
    }

    /** 注册分级变更监听器。 */
    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    /** 注销分级变更监听器。 */
    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    /**
     * 生产失效入口：清空活跃表与 tombstone，代际自增并<b>一次</b>广播给全部监听器。
     *
     * <p>触发点：资源重载（{@code ResourceReloadService}）、世界退出/客户端断开（与候选源
     * {@code release()} 同批）。不逐键回调 {@link Listener#onClassification}。</p>
     *
     * <p>并发语义：先自增代际（读路径以代际变化作废「本帧结果」），再分开 clear 两张表；
     * 读路径允许读到「清前/清后」两种一致态，不需要在清空过程中阻塞渲染线程。</p>
     *
     * @param reason 失效缘由（诊断用，可为 null）
     */
    public static void invalidateAll(String reason) {
        tierGeneration++;
        ENTRIES.clear();
        TOMBSTONES.clear();
        for (Listener listener : LISTENERS) {
            try {
                listener.onInvalidated(reason);
            } catch (RuntimeException ignored) {
                // 监听器故障不得影响失效推进。
            }
        }
    }

    /** @return 分级代际（单调不减；只读无锁，消费方以此判定「该帧分级结论作废」） */
    public static long tierGeneration() {
        return tierGeneration;
    }

    /** @return 活跃表条数（诊断/测试探针；不含 tombstone） */
    public static int size() {
        return ENTRIES.size();
    }

    /** @return tombstone（不可逐出 UNRENDERABLE）条数（诊断/测试探针） */
    public static int tombstoneSize() {
        return TOMBSTONES.size();
    }

    /** 清空全部条目、tombstone、代际与监听器（仅测试）。 */
    public static void resetForTests() {
        ENTRIES.clear();
        TOMBSTONES.clear();
        LISTENERS.clear();
        tierGeneration = 0L;
        ACCESS_TICK.set(0L);
    }

    // ==================== 有界淘汰（写路径调用；读路径无锁） ====================

    /**
     * 把活跃表压回 {@link #MAX_ACTIVE_ENTRIES} 以内（只在超限时发生；未超限是 O(1) 早退）。
     *
     * <p>淘汰策略（与「UNRENDERABLE 绝不复活」契约一致的取舍）：</p>
     * <ol>
     *   <li>优先淘汰<b>最久未访问的可淘汰条目</b>（TRACKING/RENDERABLE/NEEDS_ISOLATION）——
     *       UNRENDERABLE 条目体积极小且不可复活，保留它们比丢一条可用条目更划算；</li>
     *   <li>当活跃表全是 UNRENDERABLE 时，把最久未访问者移入 tombstone（保持有界）；
     *       tombstone 满额则不再淘汰（有界性让位于「UNRENDERABLE 绝不复活」，
     *       既有 tombstone 不淘汰、新键留在活跃表）。</li>
     * </ol>
     * <p>每次淘汰代价 = 一次 O(n) 最久未访问选择（无全局锁；n ≤ 上限），不排序、不建快照。</p>
     */
    private static void evictToBound() {
        if (ENTRIES.size() <= MAX_ACTIVE_ENTRIES) {
            return;
        }
        // 超限才排序（一次 O(n log n)），并一次淘汰到低水位以摊薄成本：
        // 稳态容量严格 ≤ MAX_ACTIVE_ENTRIES（高水位），不会出现「每次插入排一次序」的每写成本。
        int lowWater = MAX_ACTIVE_ENTRIES - EVICT_BATCH;
        List<String> byLastAccess = new ArrayList<>(ENTRIES.keySet());
        Collections.sort(byLastAccess, (left, right) -> {
            Entry a = ENTRIES.get(left);
            Entry b = ENTRIES.get(right);
            long ta = a == null ? Long.MIN_VALUE : a.lastAccessTick;
            long tb = b == null ? Long.MIN_VALUE : b.lastAccessTick;
            return Long.compare(ta, tb);
        });
        for (String key : byLastAccess) {
            if (ENTRIES.size() <= lowWater) {
                return;
            }
            Entry entry = ENTRIES.get(key);
            if (entry == null) {
                continue;
            }
            if (entry.tier == Tier.UNRENDERABLE) {
                if (TOMBSTONES.size() < MAX_TOMBSTONES && ENTRIES.remove(key, entry)) {
                    TOMBSTONES.put(key, Long.valueOf(entry.lastAccessTick));
                }
                // tombstone 满额：不淘汰既有条目（该键留在活跃表），见类 javadoc 有界性小节。
                continue;
            }
            ENTRIES.remove(key, entry);
        }
    }
}
