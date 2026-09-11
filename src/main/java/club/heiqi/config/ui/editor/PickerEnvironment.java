package club.heiqi.config.ui.editor;

import com.github.bsideup.jabel.Desugar;

/**
 * UILib → 候选源的环境代际推送载体（不可变值类型）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.2(Z-4)/§2.1/§2.2。这是环境代际
 * （名称/资源）下行的<b>唯一</b>通道：候选源不轮询、不自注册 {@code IResourceManagerReloadListener}、
 * 不依赖 {@code uilib.ui.reactive}（保持对 UILib 的零反向依赖，A2/A9）。</p>
 *
 * <p>纯值类型：无 signal、无跨线程发布。推送在主线程同步发生
 * （{@code PickerRevisionBridge} 每帧比对后仅在有变化时推送）。</p>
 *
 * @param nameEpoch     文本代际（语言/资源包 → label）；由 {@code club.heiqi.uilib.i18n.LanguageEpochService} 发布
 * @param resourceEpoch 资源代际（资源包重载 → 图标/翻译资源）；由 {@code club.heiqi.uilib.resource.ResourceReloadService} 发布
 */
@Desugar
public record PickerEnvironment(long nameEpoch, long resourceEpoch) {
}
