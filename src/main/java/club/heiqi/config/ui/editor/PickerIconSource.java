package club.heiqi.config.ui.editor;

import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/**
 * 选择器图标源：<b>以候选域 key 为入口</b>的纯函数（不要求先物化 Candidate/Variant）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.2/§5.1/§5.5。实现要点：</p>
 * <ul>
 *   <li>返回的 {@link SceneImageSource} 必须覆写<b>既有</b> {@code SceneImageSource#registryKey()}
 *       返回候选域键（{@code PickerIconKey.candidate(key)} 或 {@code PickerIconKey.variant(key, meta)}
 *       的返回值），<b>不新增 {@code tierKey()}</b>——{@code registryKey()} 是渲染分级唯一消费入口；</li>
 *   <li>本接口是纯函数（无缓存、无状态）：图标缓存归 UILib（有界 LRU，键 = {@code registryKey()} 原样值），
 *       实现方不得自建无界缓存；</li>
 *   <li>只允许客户端主线程调用（物化可能触碰 Item/ItemStack 等宿主设施）。</li>
 * </ul>
 */
public interface PickerIconSource {

    /**
     * 候选级图标。
     *
     * @param candidateKey 候选键（非 null）
     * @return 图标源；无图返回 null
     */
    SceneImageSource candidateIcon(String candidateKey);

    /**
     * 变体级图标。
     *
     * @param candidateKey   候选键（非 null）
     * @param variantKeyOrNull 变体 key；null = 候选整体
     * @return 图标源；无图返回 null
     */
    SceneImageSource variantIcon(String candidateKey, String variantKeyOrNull);
}
