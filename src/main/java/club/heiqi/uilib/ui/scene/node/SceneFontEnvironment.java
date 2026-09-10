package club.heiqi.uilib.ui.scene.node;

/**
 * 字号环境（第 3 层「runtime 默认字号」与解析出口「用户倍率层」的承载者）。
 *
 * <p>由 {@link club.heiqi.uilib.ui.scene.runtime.SceneRuntime} 实现；
 * <b>写入点 = 宿主装配的唯一口径</b>
 * {@code SceneHostAssembly.attachTree(SceneRuntime, SceneNode)}，覆盖
 * mount / portalAnchored / show / HUD 窗口自建 runtime 等装配路径。
 * 节点沿父链上溯到持有者即可读到 runtime 默认字号 —— 不使用任何静态全局量，
 * 多 runtime（测试并行、多屏、HUD 每窗口自建 runtime）天然隔离。</p>
 *
 * <p>本接口只描述「环境提供了什么」，不描述「节点怎么用」；解析算法与缓存失效见
 * {@link SceneNode#effectiveFontSize()} 与 {@link SceneNode#__invalidateFontSubtree()}。</p>
 */
public interface SceneFontEnvironment {

    /**
     * runtime 默认字号（层 3）。
     *
     * <p><b>null = 该 runtime 未声明默认字号（层 3 缺席）</b>，解析继续下探层 4
     * （控件自有回落值 → 框架常量）。禁止用常驻初值 16 代替「未声明」—— 否则
     * {@link SceneNode#setFallbackFontSize} 登记的回落值永不生效，控件默认观感被静默改写。
     * 依据：WPF {@code BaseValueSource.Default} 与 {@code DefaultStyle} 是两个独立来源；
     * Android theme 未定义该属性时才落控件默认。</p>
     *
     * @return 层 3 默认字号；null = 未声明
     */
    Integer runtimeDefaultFontSize();

    /**
     * 用户级字体缩放倍率（无障碍），1.0 = 不缩放。
     *
     * <p><b>作用点 = 解析出口的正交倍率层</b>：先按四层真值解析出声明值，再乘本倍率并归一为
     * int 逻辑像素，结果<b>参与布局</b>（叶宽测量、行高、wrap 行数全部用最终值）。
     * 禁止在 paint/render 阶段乘系数。</p>
     *
     * <p>主流依据：Flutter {@code MediaQueryData.textScaler} 作用于已解析的
     * {@code Text.style.fontSize}；Android {@code Configuration.fontScale} 作用于 sp；
     * iOS {@code UIFontMetrics} 缩放已解析字号。</p>
     *
     * @return 缩放倍率，恒 &gt; 0
     */
    float fontScale();

    /**
     * 字号环境版本号：默认字号与 fontScale 任一变化即递增。
     *
     * <p>节点解析缓存以本值作缓存代；环境变更后即便未逐节点广播，读侧也会自愈重算。</p>
     *
     * @return 环境版本号
     */
    long fontEpoch();
}
