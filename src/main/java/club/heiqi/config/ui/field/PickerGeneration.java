package club.heiqi.config.ui.field;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.config.ui.editor.PickerSourceVersion;

/**
 * PickerGeneration —— UILib 侧合成的四段失效代际（不可变值类型）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §2.2（R-03 闭环）。合成关系（写死）：</p>
 * <pre>
 * PickerGeneration = (v.registry(), v.name(), v.icon(), ItemRenderTierRegistry.tierGeneration())
 * 其中 v = PickerCandidateSource.version()（source 自报的三段，§1.2）
 * </pre>
 *
 * <p>两条边界：</p>
 * <ul>
 *   <li><b>tier 段永不下行到候选源</b>：分级表是 UILib 进程静态设施，只驱动「图标回退集合重派生」；</li>
 *   <li><b>缓存键一律以 {@link PickerSourceVersion} 为准</b>，本类型只用于「本帧是否需要重派生/重取图标」
 *       的判定（把 tier 混进缓存键会造成「分级变化 = 候选缓存全失效」的错误放大）。</li>
 * </ul>
 *
 * @param registry 候选清单代际
 * @param name     文本（语言/资源包 → label）代际
 * @param icon     图标（资源包）代际
 * @param tier     分级表代际（{@code ItemRenderTierRegistry.tierGeneration()}）
 */
@Desugar
public record PickerGeneration(long registry, long name, long icon, long tier) {

    /**
     * 由候选源自报的三段 + 分级代际合成四段。
     *
     * @param version        候选源自报版本（{@code source.version()}）
     * @param tierGeneration 分级表代际
     * @return 四段合成代际
     */
    public static PickerGeneration of(PickerSourceVersion version, long tierGeneration) {
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        return new PickerGeneration(version.registry(), version.name(), version.icon(), tierGeneration);
    }
}
