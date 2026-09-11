package club.heiqi.uilib.ui.scene.paint;

/**
 * SceneRenderProtocolTokens —— 场景渲染协议的<b>非主题静态色</b>集中定义处。
 *
 * <h3>为什么独立成一处</h3>
 * <p>选择器家族里存在两类"看起来像颜色、其实不是主题槽位"的值（P5 §4.2 B / ADR §5.4 R-04）：</p>
 * <ul>
 *   <li><b>图像渲染协议色</b>：物品图标缺图时的占位底色、图标被平台判为不可渲染时的占位底色、
 *       单元 hover/选中轻量覆盖透明度。它们描述的是"图像管线怎么画"，<b>不随主题重染</b> ——
 *       图标本身不改色是既有契约（宿主不重染物品图像）。</li>
 *   <li><b>场景遮罩底色</b>：Dialog 与选择器浮层共用的全屏 scrim。历史上 Dialog 用
 *       {@code 0xCC121016}、变体浮层用 {@code 0xCC000000}，两值冲突（T5 实测），此处收敛为一枚。</li>
 * </ul>
 *
 * <p>集中在此还有一个工程收益：picker 相关模块（{@code ui.scene.control.search.*}、
 * {@code ScenePickerPanel}、{@code SearchPickerFieldSupport}、{@code StructuredListFieldRenderer}）
 * 内因此<b>不再出现任何 6/8 位十六进制色字面量</b>，硬编码色自查口径（P5 §4.3 条 1）可被源码守卫机械核对。</p>
 *
 * <h3>与主题派生令牌的边界（ADR A-19，不得混淆）</h3>
 * <p>「不可渲染（UNRENDERABLE）」是<b>状态语义</b>而非图像协议：它不是"没有图"，而是"平台判定这张图
 * 画不出来/已被隔离"。因此它的底色<b>必须</b>由主题派生（{@code ItemRenderFallbackKeys.unrenderableTint}），
 * 与这里的 {@link #IMAGE_PLACEHOLDER_ARGB} 在色相上可区分，且随主题切换即时重派生。
 * <b>禁止</b>把本类的占位色改成主题派生（R-04 边界）。</p>
 *
 * <h3>失效通道（P5 §4.3 条 5）</h3>
 * <p>本类全部是 {@code static final} 编译期常量，本身不随任何运行期状态变化，故不需要失效通道；
 * 需要失效通道的是<i>由它们参与派生的结果</i>（主题派生的 UNRENDERABLE 底色由 {@code SceneThemes}
 * 的主题信号承担）。</p>
 */
public final class SceneRenderProtocolTokens {

    private SceneRenderProtocolTokens() {
    }

    // ==================== 图像渲染协议（不随主题重染） ====================

    /** 「无图」占位底色：候选/成员/变体图标缺图时的静态协议值（与 SceneVirtualGrid 同值）。 */
    public static final int IMAGE_PLACEHOLDER_ARGB = 0xFF454B54;

    /** 结果单元 hover 轻量覆盖强度（主题 accent 的低透明度叠加，与 SceneAutocomplete 候选行同口径）。 */
    public static final int CELL_HOVER_ALPHA = 0x1F;

    /** 结果单元选中（高亮）覆盖强度（主题选区背景，明显强于 hover，不只靠透明度区分）。 */
    public static final int CELL_SELECTED_ALPHA = 0x59;

    /** 完全透明（协议槽位的"不写底色"值，语义与 {@link SceneChromeTokens#TRANSPARENT} 同值）。 */
    public static final int TRANSPARENT_ARGB = 0x00000000;

    /**
     * 「不可渲染（UNRENDERABLE）」底色的叠加 alpha（0x33）。
     *
     * <p>注意：这只是<b>透明度</b>协议 —— 色相部分必须来自主题（{@code SceneThemes.errorText}），
     * 由 {@code ItemRenderFallbackKeys.unrenderableTint} 派生；不得在此写死 RGB，
     * 否则「UNRENDERABLE = 主题派生状态语义」的边界（ADR §5.4 / A-19）不成立。</p>
     */
    public static final int UNRENDERABLE_TINT_ALPHA = 0x33;

    // ==================== 场景遮罩（收敛值） ====================

    /**
     * 场景遮罩统一底色（约 80% 不透明暗色）。
     *
     * <p>收敛前：{@code SceneDialog.SCRIM_ARGB = 0xCC121016}、{@code VariantChooser.OVERLAY_SCRIM = 0xCC000000}
     * 两值并存（T5 UX 实测记为不一致）。取 Dialog 值（带冷色调、与 modernconfig 遮罩同源观感），
     * 选择器浮层切换到同一枚 —— 遮罩只负责遮罩，不装玻璃、不参与表面绑定。</p>
     */
    public static final int SCRIM_ARGB = 0xCC121016;
}
