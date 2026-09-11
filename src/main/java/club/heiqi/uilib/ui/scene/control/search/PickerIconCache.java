package club.heiqi.uilib.ui.scene.control.search;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import club.heiqi.config.ui.editor.PickerCandidateSource;
import club.heiqi.config.ui.editor.PickerIconSource;
import club.heiqi.config.ui.field.PickerSourceGuard;
import club.heiqi.uilib.Config;
import club.heiqi.uilib.ui.diagnostic.UiPerfMarkers;
import club.heiqi.uilib.ui.diagnostic.UiPerformanceMonitor;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/**
 * PickerIconCache —— 选择器图标源的有界两层缓存（UILib 统一持有）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §2.5/§5.5（A8/P6/P11）。要点：</p>
 * <ul>
 *   <li><b>键 = {@code registryKey()} 原样值</b>（即 {@link PickerIconKey} 的返回值）：与渲染分级
 *       同一 key 空间，杜绝「缓存命中」与「分级查询」出现两套键族；</li>
 *   <li><b>两层独立 LRU</b>：候选级 {@link #DEFAULT_CANDIDATE_CAPACITY} + 变体级
 *       {@link #DEFAULT_VARIANT_CAPACITY}（键空间混用是既有缺陷，必须分层）；</li>
 *   <li><b>失效</b>：{@link #invalidateAll(String)}（图标代际，剔一次清空两层）与
 *       {@link #onTierGenerationChanged(long)}（分级代际：只丢弃当前判为 UNRENDERABLE 的键，
 *       其余按 key 重取）；</li>
 *   <li><b>释放</b>：{@link #release()}（屏级释放链；释放后再次请求仍可用，不进坏态）。</li>
 * </ul>
 *
 * <p><b>线程</b>：仅客户端主线程（图标源物化可能触碰 ItemStack 等宿主设施）；入口经
 * {@link PickerSourceGuard} 断言。内部为访问序 {@link LinkedHashMap}，不额外加锁。</p>
 */
public final class PickerIconCache {

    /** 候选级缓存条数上限（按窗口可见单元 ≤90 的 ≥5 倍余量）。 */
    public static final int DEFAULT_CANDIDATE_CAPACITY = 512;
    /** 变体级缓存条数上限。 */
    public static final int DEFAULT_VARIANT_CAPACITY = 1024;

    private final Map<String, SceneImageSource> candidateIcons;
    private final Map<String, SceneImageSource> variantIcons;
    private long createdCount;
    private long hitCount;

    /** 按默认容量创建空缓存（条目在首个请求时产出）。 */
    public PickerIconCache() {
        this(DEFAULT_CANDIDATE_CAPACITY, DEFAULT_VARIANT_CAPACITY);
    }

    /**
     * @param candidateCapacity 候选级上限（正数）
     * @param variantCapacity   变体级上限（正数）
     */
    public PickerIconCache(int candidateCapacity, int variantCapacity) {
        if (candidateCapacity < 1 || variantCapacity < 1) {
            throw new IllegalArgumentException("capacities must be positive");
        }
        this.candidateIcons = newLru(candidateCapacity);
        this.variantIcons = newLru(variantCapacity);
    }

    /**
     * 取候选级图标（未命中则经 {@link PickerIconSource#candidateIcon(String)} 物化并入缓存）。
     *
     * @param candidateKey 候选键（非 null）
     * @param source       图标源纯函数（非 null）
     * @return 图标源；无图返回 null（不缓存缺省结论）
     */
    public SceneImageSource candidateIcon(String candidateKey, PickerIconSource source) {
        PickerSourceGuard.requireMainThread("candidateIcon");
        String key = PickerIconKey.candidate(candidateKey);
        return cached(candidateIcons, key, candidateKey, null, source);
    }

    /**
     * 取变体级图标（未命中则经 {@link PickerIconSource#variantIcon(String, String)} 物化并入缓存）。
     *
     * @param candidateKey     候选键（非 null）
     * @param variantKeyOrNull 变体 key；null = 候选整体
     * @param source           图标源纯函数（非 null）
     * @return 图标源；无图返回 null
     */
    public SceneImageSource variantIcon(String candidateKey, String variantKeyOrNull, PickerIconSource source) {
        PickerSourceGuard.requireMainThread("variantIcon");
        String key = PickerIconKey.variant(candidateKey, variantKeyOrNull);
        return cached(variantIcons, key, candidateKey, variantKeyOrNull, source);
    }

    private SceneImageSource cached(Map<String, SceneImageSource> layer, String key, String candidateKey,
                                    String variantKeyOrNull, PickerIconSource source) {
        // 访问序 LRU：get 本身即更新最近访问顺序（LinkedHashMap accessOrder=true），无需二次触碰。
        SceneImageSource hit = layer.get(key);
        if (hit != null) {
            hitCount++;
            record(UiPerfMarkers.COUNTER_PICKER_ICON_CACHED);
            return hit;
        }
        SceneImageSource created = variantKeyOrNull == null
                ? source.candidateIcon(candidateKey) : source.variantIcon(candidateKey, variantKeyOrNull);
        createdCount++;
        record(UiPerfMarkers.COUNTER_PICKER_ICON_CREATED);
        if (created != null) {
            layer.put(key, created);
        }
        return created;
    }

    private static void record(String marker) {
        if (!Config.useDebug) {
            return;
        }
        UiPerformanceMonitor.getInstance().recordCounter(marker, 1L);
    }

    /**
     * 图标代际失效：清空两层（资源包重载后按 key 重取）。
     *
     * @param reason 失效缘由（诊断用）
     */
    public void invalidateAll(String reason) {
        candidateIcons.clear();
        variantIcons.clear();
    }

    /**
     * 分级代际变化：丢弃当前判为 {@link ItemRenderTierRegistry.Tier#UNRENDERABLE} 的条目（按 key 重取），
     * 其余条目保持（分级变化不改图标内容，只改「是否绘制」）。
     *
     * @param tierGeneration 新的分级代际（仅诊断/追踪用）
     */
    public void onTierGenerationChanged(long tierGeneration) {
        dropUnrenderable(candidateIcons);
        dropUnrenderable(variantIcons);
    }

    private static void dropUnrenderable(Map<String, SceneImageSource> layer) {
        Iterator<Map.Entry<String, SceneImageSource>> iterator = layer.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SceneImageSource> entry = iterator.next();
            if (ItemRenderTierRegistry.tierOf(entry.getKey()) == ItemRenderTierRegistry.Tier.UNRENDERABLE) {
                iterator.remove();
            }
        }
    }

    /** 释放（屏级释放链）：清空两层；释放后再次请求仍可用（不进坏态）。 */
    public void release() {
        candidateIcons.clear();
        variantIcons.clear();
    }

    /** @return 候选级缓存条数（有界性探针） */
    public int candidateSize() {
        return candidateIcons.size();
    }

    /** @return 变体级缓存条数（有界性探针） */
    public int variantSize() {
        return variantIcons.size();
    }

    /** @return 累计新建图标源次数（命中率口径：created / (created + hit)） */
    public long createdCount() {
        return createdCount;
    }

    /** @return 累计缓存命中次数 */
    public long hitCount() {
        return hitCount;
    }

    private static Map<String, SceneImageSource> newLru(final int capacity) {
        return new LinkedHashMap<String, SceneImageSource>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SceneImageSource> eldest) {
                return size() > capacity;
            }
        };
    }
}
