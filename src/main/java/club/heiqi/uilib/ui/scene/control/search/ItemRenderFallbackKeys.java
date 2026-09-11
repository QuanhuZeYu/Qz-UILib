package club.heiqi.uilib.ui.scene.control.search;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry;
import club.heiqi.uilib.ui.scene.paint.SceneRenderProtocolTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * ItemRenderFallbackKeys —— 物品图标不可渲染回退键集合的共享装配。
 *
 * <p>把「订阅 {@link ItemRenderTierRegistry} → UNRENDERABLE 分级 → 增量写入回退键集合 →
 * owner 清理」样板收敛为单点：结果列表与变体列表共用同一语义（已分级不可渲染的条目回退占位样式，
 * 不再尝试渲染）。</p>
 *
 * <h3>键空间（唯一：候选域键，不做任何字符串拆分）</h3>
 * <p>分级键 = 选择器图标源覆写 {@code SceneImageSource#registryKey()} 返回的
 * {@link PickerIconKey} 值（候选级 {@code candidateKey} / 变体级 {@code candidateKey@meta}），
 * 消费端把该值<b>原样</b>当条目 key 使用：调用形态恒为
 * {@code ItemRenderFallbackKeys.track(registryKey -> registryKey)}。</p>
 *
 * <p><b>为什么禁止拆键</b>：候选域键里 {@code minecraft:stone} 是<b>整体</b>（无 meta），
 * 按冒号拆分会把方块名当 meta、拼出的键与候选 key 永不相等 ⇒ 回退集合恒空、UNRENDERABLE 静默失效
 * （历史缺陷 S-1 的翻版）。旧 helper {@code splitRegistryKey} 已按契约删除，无替代者。</p>
 */
public final class ItemRenderFallbackKeys {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/ItemRenderFallbackKeys");

    private ItemRenderFallbackKeys() {
    }

    /**
     * 在当前 Owner 作用域内装配不可渲染回退键集合。
     *
     * @param registryKeyToItemKey registryKey → 条目 key 的映射（查不到返回 null）
     * @return 不可渲染条目 key 集合 signal（初始为空；随分级变更增量写入）
     */
    public static Signal<Set<Object>> track(Function<String, Object> registryKeyToItemKey) {
        Signal<Set<Object>> unrenderableKeys = Signal.create(Collections.<Object>emptySet());
        ItemRenderTierRegistry.Listener listener = classification -> {
            if (classification.tier() != ItemRenderTierRegistry.Tier.UNRENDERABLE) {
                return;
            }
            Object itemKey = registryKeyToItemKey.apply(classification.registryKey());
            if (itemKey == null) {
                return;
            }
            Set<Object> current = unrenderableKeys.get();
            if (current.contains(itemKey)) {
                return;
            }
            Set<Object> next = new HashSet<>(current);
            next.add(itemKey);
            unrenderableKeys.set(Collections.unmodifiableSet(next));
            LOG.warn("[qz-picker-icon] 物品 {} 渲染失败，已标记不可渲染并回退占位样式：{}",
                    itemKey, classification.detail());
        };
        ItemRenderTierRegistry.addListener(listener);
        Owner owner = Owner.current();
        if (owner != null) {
            owner.onCleanup(() -> ItemRenderTierRegistry.removeListener(listener));
        }
        return unrenderableKeys;
    }

    /**
     * UNRENDERABLE 状态底色 —— <b>主题派生</b>令牌（ADR §5.4 / A-19 边界）。
     *
     * <p>与「无图」协议色（{@link SceneRenderProtocolTokens#IMAGE_PLACEHOLDER_ARGB}，静态不随主题）
     * 是两种可区分状态：本令牌色相取自主题 {@code errorText}，主题切换即时重派生（Computed 记忆化，
     * 不重建节点），并叠加固定 alpha 以保持与图标内容的对比。</p>
     *
     * <p><b>禁止</b>把「无图」协议色改成主题派生，也<b>禁止</b>把本令牌写死为协议色 ——
     * 两条边界由 {@code ScenePickerRenderStateTest} 的令牌不等断言与主题切换断言钉住。</p>
     *
     * @param rt 场景运行时（提供主题作用域）
     * @return UNRENDERABLE 底色只读信号（ARGB）
     */
    public static ReadableSignal<Integer> unrenderableTint(SceneRuntime rt) {
        final ReadableSignal<Integer> errorText = SceneThemes.errorText(rt);
        return Computed.create(() -> Integer.valueOf(
                (SceneRenderProtocolTokens.UNRENDERABLE_TINT_ALPHA << 24)
                        | (errorText.get().intValue() & 0x00FFFFFF)));
    }
}
