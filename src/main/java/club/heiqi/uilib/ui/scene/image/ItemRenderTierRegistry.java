package club.heiqi.uilib.ui.scene.image;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.github.bsideup.jabel.Desugar;

/**
 * ItemRenderTierRegistry —— 物品图标渲染分级注册表（平台中立，只认稳定字符串键）。
 *
 * <h3>三次追踪 → 永久分级</h3>
 * <p>每个 registryKey（物品注册名:meta 等稳定字符串）初始为 {@link Tier#TRACKING}，
 * 前 {@link #TRACK_ATTEMPTS} 次渲染带全量检查（GL 错误检测 + 异常捕获），追踪结束后永久分级：</p>
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
 * 本类不依赖 Minecraft/LWJGL 类型，键由平台适配器（{@code HostImageSource#registryKey()}）派生。
 * 分级会话内永久记忆；测试用 {@link #resetForTests()} 清零。</p>
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

    /** 分级变更通知（key + 新分级 + 变更缘由）。 */
    @Desugar
    public record Classification(String registryKey, Tier tier, String detail) {
    }

    /** 分级变更监听器。 */
    public interface Listener {

        void onClassification(Classification classification);
    }

    /** 内部条目状态（可变，同步于 {@link #entries}）。 */
    static final class Entry {

        Tier tier;
        int attemptsLeft = TRACK_ATTEMPTS;
        int exceptionAttempts;
        int glErrorAttempts;
        int consecutiveIsolationFailures;
        String detail = "";

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
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private ItemRenderTierRegistry() {
    }

    /**
     * 查询分级（不创建条目）；未知键返回 {@link Tier#TRACKING}。
     *
     * @param registryKey 稳定注册键（非 null）
     * @return 当前分级
     */
    public static Tier tierOf(String registryKey) {
        if (registryKey == null) {
            return Tier.TRACKING;
        }
        Entry entry = ENTRIES.get(registryKey);
        return entry == null ? Tier.TRACKING : entry.tier;
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
        // 逐键原子推进：Entry 可变计数在 CHM.compute 的键级锁内原地推进，
        // 分级变化时以新 Entry 替换旧条目；监听器通知在 compute 之外发布（守原语义）。
        Entry[] changed = new Entry[1];
        ENTRIES.compute(registryKey, (key, existing) -> {
            Entry entry = existing == null ? new Entry() : existing;
            Entry next = classifyEntry(entry, outcome, detail);
            if (next != null) {
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

    /** 清空全部条目与监听器（仅测试）。 */
    public static void resetForTests() {
        ENTRIES.clear();
        LISTENERS.clear();
    }
}
