package club.heiqi.uilib.ui.scene.node;

/**
 * 节点字号的解析来源 —— 字号四层真值的溯源凭据（INV-FONT-1）。
 *
 * <p>由 {@link SceneNode#fontSizeSource()} 返回，供守卫、调试与测试断言「这个字号是谁给的」。
 * 逐层优先级见 {@link SceneNode#effectiveFontSize()}。</p>
 *
 * <h3>UNRESOLVED 的判定口径</h3>
 * <p>{@link #UNRESOLVED} 本身<b>不是违规</b>：未装配树（脱离任何 runtime 的裸节点或游离子树）
 * 落到框架常量 {@link club.heiqi.uilib.font.layout.FontSizeLimits#DEFAULT_FONT_SIZE_PX} 是合法且可预期的。
 * 违规的是<b>已装配树内</b>出现 UNRESOLVED —— 说明装配点漏挂环境或节点绕过装配路径入树。
 * 因此守卫断言应写成「在已装配树上不得出现 UNRESOLVED 的文本节点」。</p>
 */
public enum FontSource {
    /** 层 1：命中父链上某节点的显式值（{@link SceneNode#setFontSize}）。 */
    EXPLICIT,
    /** 层 2：命中父链上某节点的作用域声明（{@link SceneNode#setFontScope}）。 */
    SCOPE,
    /** 层 3：命中树根环境的 runtime 默认字号（环境未声明该层时不产生本来源）。 */
    ENVIRONMENT,
    /** 层 4a：命中父链上某节点登记的控件自有回落值（{@link SceneNode#setFallbackFontSize}）。 */
    NODE_DEFAULT,
    /** 层 4b：四层全部未命中（未装配树），落到框架常量；已装配树内出现即 INV-FONT-1 违规。 */
    UNRESOLVED
}
