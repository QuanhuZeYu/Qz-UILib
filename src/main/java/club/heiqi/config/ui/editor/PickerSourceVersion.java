package club.heiqi.config.ui.editor;

import com.github.bsideup.jabel.Desugar;

/**
 * 候选源三段版本号的对象级载体（不可变值类型）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.2/§1.6(b)/§2.2。语义 =
 * <b>候选源已摄入的环境代际快照</b>，不是「当前环境 epoch」：</p>
 * <ul>
 *   <li>{@code registry}：候选清单（成员/顺序/Block 实例）变化时自增；</li>
 *   <li>{@code name}：显示名/分类标签（语言、资源包）变化时自增，唯一来源 = UILib 推送的
 *       {@code PickerEnvironment.nameEpoch}（经 {@link PickerCandidateSource#onEnvironmentChanged}); </li>
 *   <li>{@code icon}：图标（资源包）变化时自增，唯一来源 = 推送的 {@code resourceEpoch}。</li>
 * </ul>
 *
 * <p>单调性：同一 source 实例内单调不减；<b>跨实例不可比</b>（不能把两个 source 的版本号相减）。
 * 不推送（{@code onEnvironmentChanged} 未被调用）时本值必须恒等——这是 Z-4 下行不变式的可测判据。</p>
 *
 * <p>本 record 不含 {@code tier} 段：分级代际是 UILib 进程静态表的失效，永不下行到候选源；
 * 四段合成值见 {@code club.heiqi.config.ui.field.PickerGeneration}。</p>
 *
 * @param registry 候选清单版本号
 * @param name     文本（名称/标签）版本号
 * @param icon     图标版本号
 */
@Desugar
public record PickerSourceVersion(long registry, long name, long icon) {

    /** @return 三段皆为 0 的初始版本（source 尚未摄入任何环境代际） */
    public static PickerSourceVersion initial() {
        return new PickerSourceVersion(0L, 0L, 0L);
    }
}
