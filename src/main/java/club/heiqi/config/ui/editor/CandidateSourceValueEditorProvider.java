package club.heiqi.config.ui.editor;

/**
 * 惰性候选源感知的值编辑器 provider 契约（纯加法扩展，探测式接线）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.2/§1.5。接线层用
 * {@code provider instanceof CandidateSourceValueEditorProvider} 探测：未实现（或
 * {@link #candidateSource()} 返回 null）时自动回退旧 {@link ValueEditorProvider.SearchFunction}
 * 全量路径（过渡态 T-1），旧实现零改动可编译。</p>
 *
 * <p>与 {@link CategorizedValueEditorProvider} 的关系：分类维度语义沿用后者的
 * {@code categories(int)}/{@code categoryOf(int, String)}；新 SPI 路径下分类过滤由
 * {@link PickerQuery#categoryDimension()}/{@link PickerQuery#categoryKey()} 在候选源侧完成，
 * 面板侧<b>禁止</b>对窗口切片二次过滤（过渡态 T-6 的保留期仅覆盖旧路径）。</p>
 */
public interface CandidateSourceValueEditorProvider extends ValueEditorProvider {

    /**
     * @return 惰性候选源引用；null ⇒ 回退旧 {@code SearchFunction} 路径（T-1）
     */
    default PickerCandidateSource candidateSource() {
        return null;
    }

    /**
     * 搜索 lane 窗口上限（唯一真值来源是 {@code SearchPickerSpec.maxItems()}，经装配层收进本方法）。
     *
     * <p>取值链：{@code SearchPickerSpec.maxItems()} → {@code Registry.register} 收进注册快照 →
     * 本方法 → 装配层构造查询上限。浏览 lane 不受此上限约束。</p>
     *
     * @return 搜索 lane 单窗上限（正数）
     */
    default int searchMaxItems() {
        return 64;
    }

    /**
     * @return 图标源（纯函数）；null ⇒ 回退 {@link ValueEditorProvider#visualAdapter()}
     */
    default PickerIconSource iconSource() {
        return null;
    }
}
