package club.heiqi.uilib.ui.scene.control.search;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.image.ItemRenderTierRegistry;

/**
 * ItemRenderFallbackKeys —— 物品图标不可渲染回退键集合的共享装配。
 *
 * <p>把「订阅 {@link ItemRenderTierRegistry} → UNRENDERABLE 分级 → 增量写入回退键集合 →
 * owner 清理」样板收敛为单点：结果列表与变体列表共用同一语义（已分级不可渲染的条目回退占位样式，
 * 不再尝试渲染）。</p>
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
     * 拆分级注册键 {@code 注册名:meta}（如 {@code minecraft:stone:0}）为 {"注册名", "meta"}。
     *
     * <p>注册名本身含一个冒号（modid:name），meta 在最后一个冒号之后；非法形态返回 null。</p>
     *
     * @param registryKey 分级注册键
     * @return 两段数组，或非法时 null
     */
    public static String[] splitRegistryKey(String registryKey) {
        if (registryKey == null) {
            return null;
        }
        int separator = registryKey.lastIndexOf(':');
        if (separator <= 0 || separator == registryKey.length() - 1) {
            return null;
        }
        return new String[] { registryKey.substring(0, separator), registryKey.substring(separator + 1) };
    }
}
