package club.heiqi.uilib.ui.scene.control.search;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.font.layout.FontSizeLimits;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * PickerMetrics —— 选择器「尺寸 / 密度 / 网格几何」的<strong>唯一派生实现</strong>（P5 §1 + §2）。
 *
 * <h3>三分量铁律（Qz-UILib AGENTS.md:28）</h3>
 * <pre>
 *   [宿主边界 · 唯一允许接触 GUI Scale 的位置]
 *     logicalBox = hostCompose(nativeBox, guiScale)        // 见 HostViewportScale
 *   [面板内部 · 禁止读 GUI Scale / 禁止假定分辨率]
 *     panelFontSize = 面板内容根字号链的生效字号（声明值 × 用户倍率；运行期可变）
 *     density = 用户偏好（AUTO/COMPACT/STANDARD/ROOMY）     // 运行期可变
 *     metrics = PickerMetrics.derive(rt, logicalBox, panelFontSize, density, membersRows)
 * </pre>
 * <p>本类<b>不接收</b> GUI Scale、不接收物理分辨率、不缓存跨帧结果 —— 三个输入任一变化即重派生
 * （无静态快照）。</p>
 *
 * <h3>auto 求解（P5 §1.4 + 标签宽度预算，规范性）</h3>
 * <pre>
 *   hard = max(现状可见量(logicalBox), 12)      // 支配性硬目标（红线 R1/R2）
 *   soft = ceil(hard * 1.05)                    // 5% 安全裕度（基准参数下可达时保持）
 *   for degrade in [1.00,.85,.70,.55,.40,0.0]:  // 标签可读宽度预算从大到小（最外层）
 *     em = LABEL_BUDGET_EM * degrade            // 末档 0 = 关闭预算
 *     for ratio in [70,78,84,92]:               // 面板从小到大
 *       for density in [standard, compact]:     // 图标从大到小
 *         for k in [1.00,.95,.90,.85,.80]:      // 图标等比缩小（字号不缩）
 *           if visible >= soft: return 该组合
 *     该预算下存在 visible >= hard 的组合 → 采用（预算不再降）
 *   全部预算都不满足 hard → 取可见量最大的组合
 * </pre>
 * <p><b>预算为何在最外层</b>：标签能读几个字是用户直接感知的信息量，可见量硬红线是底线；
 * 两者冲突时先保红线（降预算），红线都保不住时就不可能给出更大预算 —— 顺序即优先级。
 * 代价是 <b>5% 软裕度可能让位</b>：贴线点（如 1080p/字号 150%）会停在「≥ hard 但 &lt; soft」，
 * 这是有意的产品取舍，由 {@code PickerMetricsTest.a7AutoKeepsSafetyMarginWhenPossible} 明示口径。</p>
 * <p>被选中的组合同时决定：面板比例、密度档、{@code k}、网格几何、列数与可视行数。</p>
 *
 * <h3>小盒降级（P5 §1.5.3）</h3>
 * <p>逻辑盒 &lt; 1280×720 时强制：面板 100%（满屏留 {@code PANEL_MARGIN}）、紧凑档、
 * 成员带折叠为提示行、信息条不占位、导航宽 {@code min(0.18*W, ...)}。
 * 小盒下<b>不承诺</b> 75 项（几何不成立），只承诺「支配现状 + 全部内容可滚动可达」。</p>
 *
 * <h3>信息条占位口径（P5 §1.2，偏差 D-P5-2）</h3>
 * <p>规格给出两个可选形态（「hover 才占位」或「并入 header 第二行」）。本实现取<b>常驻占位</b>，
 * 高度 {@code round(fs*2)} 随字号派生、内容永不空（悬停 -> {@code label · ID: key}；
 * 空闲 -> 操作提示 {@code hoverHint}），理由是一条可复现的缺陷：hover 才占位会让「信息条出现」
 * 与「结果区收缩 24px」互相驱动 —— 指针下的单元被顶走 -> hover 丢失 -> 信息条消失 ->
 * 单元落回指针下 -> hover 恢复 -> <b>闪烁环</b>。常驻占位把该环从结构上消除，代价是 24px。
 * 代价已量化：该口径下 1080p auto = 100 项（现状 75，仍 +33%），8 视口 × 3 字号支配失败点 0
 * （复算脚本 {@code temp/p5_model_check.py} 的「常驻占位」模型；A1-A8 断言见
 * {@code PickerMetricsTest}）。截断提示按规格 §3.3 落在顶栏统计行右侧（与统计同行），
 * 不占用信息条。</p>
 */
public final class PickerMetrics {

    /**
     * 面板盒快照（逻辑 px）。
     *
     * @param widthPx         面板宽
     * @param heightPx        面板高
     * @param ratioPercent    生效比例（%）
     * @param headerHeightPx  顶栏高
     * @param navWidthPx      左导航宽
     * @param membersHeightPx 成员带高（{@code 0} = 无成员带）
     * @param infoBarHeightPx 信息条高（小盒降级为 {@code 0}）
     * @param listWidthPx     结果区可用内宽（列数派生的输入）
     * @param listHeightPx    结果区可用内高（可视行数派生的输入）
     * @param membersCollapsed 成员带是否折叠为提示行
     * @param smallBox        是否小盒降级模式
     */
    @Desugar
    public record PanelBox(int widthPx, int heightPx, int ratioPercent, int headerHeightPx,
                           int navWidthPx, int membersHeightPx, int infoBarHeightPx,
                           int listWidthPx, int listHeightPx,
                           boolean membersCollapsed, boolean smallBox) {
    }

    private final int logicalWidthPx;
    private final int logicalHeightPx;
    private final int fontSizePx;
    private final int membersRows;
    private final int hardTargetItems;
    private final int softTargetItems;
    private final PickerDensityPreference preference;
    private final PickerDensity density;
    private final int iconScalePercent;
    private final int visibleRows;
    private final int visibleItems;
    private final GridMetrics grid;
    private final PanelBox panel;

    private PickerMetrics(int logicalWidthPx, int logicalHeightPx, int fontSizePx,
                          int membersRows, int hardTargetItems, int softTargetItems,
                          PickerDensityPreference preference, PickerDensity density,
                          int iconScalePercent, int visibleRows, int visibleItems,
                          GridMetrics grid, PanelBox panel) {
        this.logicalWidthPx = logicalWidthPx;
        this.logicalHeightPx = logicalHeightPx;
        this.fontSizePx = fontSizePx;
        this.membersRows = membersRows;
        this.hardTargetItems = hardTargetItems;
        this.softTargetItems = softTargetItems;
        this.preference = preference;
        this.density = density;
        this.iconScalePercent = iconScalePercent;
        this.visibleRows = visibleRows;
        this.visibleItems = visibleItems;
        this.grid = grid;
        this.panel = panel;
    }

    // ==================== 入口 ====================

    /**
     * 生产入口：由「逻辑盒 + 面板生效字号 + 密度偏好 + 成员行数」派生全部几何。
     *
     * <p>字号入参是<b>面板内容根真正生效的字号</b>（{@code SceneNode.effectiveFontSize()}，已乘用户倍率），
     * 不是倍率百分比也不是档位基准字号 —— 派生链与渲染链共用同一个字号真值，宽度/行高/间距才不会
     * 按一个字号算、文字按另一个字号画。</p>
     *
     * @param rt              场景运行时（提供行高与文本宽度量；非 null）
     * @param logicalWidthPx  逻辑盒宽（宿主边界已折算；&lt;1 按 1）
     * @param logicalHeightPx 逻辑盒高（宿主边界已折算；&lt;1 按 1）
     * @param panelFontSizePx 面板生效字号（夹取到字号域）
     * @param preference      密度偏好（null 按 {@link PickerDensityPreference#AUTO}）
     * @param membersRows     成员行数：{@code >=0} 表示有成员带（0 = 折叠提示行），{@code <0} = 无成员带
     * @return 不可变度量快照（非 null）
     */
    public static PickerMetrics derive(SceneRuntime rt, int logicalWidthPx, int logicalHeightPx,
                                       int panelFontSizePx, PickerDensityPreference preference,
                                       int membersRows) {
        return solve(rt, logicalWidthPx, logicalHeightPx,
                clamp(panelFontSizePx, PickerDensityTokens.FONT_FLOOR, PickerDensityTokens.FONT_CEIL),
                preference, membersRows);
    }

    /**
     * 面板声明字号的默认值来源：宿主字号链未声明字号时用档位基准字号（{@link PickerDensity#STANDARD}，
     * P5 §1.3「标准档 = 与既有字号线一致的标准态」）。
     *
     * <p>{@code ScenePickerPanel} 用它计算"面板内容根的声明字号"，再经
     * {@link #fontSizeFor(int, int)} 得到与 {@code SceneNode.effectiveFontSize()} 逐值同源的生效字号 ——
     * 派生链与渲染链不再各算一套。</p>
     *
     * @return 默认面板声明字号（未乘用户倍率；逻辑 px）
     */
    public static int defaultPanelDeclaredFontPx() {
        return PickerDensity.STANDARD.baseFontPx();
    }

    /**
     * 规格对拍入口（与 {@code temp/p5_density_spec.py} 的 {@code p5_layout(W,H,fs,pref)} 同参数化）：
     * 字号<b>显式给出</b>，不再乘任何倍率。
     *
     * <p>与 {@link #derive} 的唯一差别是"字号从哪来"：本入口由调用方（测试/规格脚本）给出 fs，
     * {@code derive} 由面板内容根的字号链给出。求解本体（含标签可读宽度预算与降级阶梯）两入口共用，
     * 不存在两套几何。</p>
     *
     * @param rt              场景运行时（非 null）
     * @param logicalWidthPx  逻辑盒宽
     * @param logicalHeightPx 逻辑盒高
     * @param fontSizePx      生效字号（夹取到字号域）
     * @param preference      密度偏好
     * @param membersRows     成员行数（{@code <0} = 无成员带）
     * @return 不可变度量快照（非 null）
     */
    public static PickerMetrics solve(SceneRuntime rt, int logicalWidthPx, int logicalHeightPx,
                                      int fontSizePx, PickerDensityPreference preference,
                                      int membersRows) {
        int w = Math.max(1, logicalWidthPx);
        int h = Math.max(1, logicalHeightPx);
        int fs = clamp(fontSizePx, PickerDensityTokens.FONT_FLOOR, PickerDensityTokens.FONT_CEIL);
        PickerDensityPreference pref = preference == null
                ? PickerDensityPreference.AUTO : preference;
        boolean small = isSmallBox(w, h);

        int hard = Math.max(legacyBaselineItems(w, h), PickerDensityTokens.HARD_TARGET_FLOOR);
        int soft = pref.isAuto()
                ? (int) Math.ceil(hard * (1.0 + PickerDensityTokens.SAFETY_MARGIN_PCT / 100.0))
                : hard;

        PickerDensity[] order = small
                ? new PickerDensity[] {PickerDensity.COMPACT}
                : (pref.isAuto()
                        ? new PickerDensity[] {PickerDensity.STANDARD, PickerDensity.COMPACT}
                        : new PickerDensity[] {pref.explicitDensity(PickerDensity.STANDARD)});
        int[] ratios = small
                ? new int[] {PickerDensityTokens.PANEL_RATIO_SMALL}
                : PickerDensityTokens.PANEL_RATIO_STEPS;

        PickerMetrics fallback = null;
        // 标签可读宽度预算的降级阶梯（最外层）：从档位预算开始求解，只有当该预算下
        // 「任何面板比例 / 档位 / k 组合」都达不到可见项下限（hard = 现状基线）时才降一档。
        // 于是「可见量不低于现状」是硬约束、标签宽度是其余空间里的最大可给量 —— 二者在同一条
        // 派生里同时成立，不需要第二个真值，也不靠调用方传"该显示几个字"。
        for (double degrade : PickerDensityTokens.LABEL_BUDGET_DEGRADE_STEPS) {
            PickerMetrics tierBest = null;
            PickerMetrics tierFirstOk = null;
            for (int ratio : ratios) {
                for (PickerDensity density : order) {
                    // 末档 degrade == 0 = 关闭预算（退回既有 cellW 口径），见 LABEL_BUDGET_DEGRADE_STEPS。
                    double labelBudgetEm = degrade <= 0.0
                            ? 0.0 : PickerDensityTokens.LABEL_BUDGET_EM * degrade;
                    for (double scale : PickerDensityTokens.ICON_SCALE_STEPS) {
                        int iconScalePercent = GridMetrics.roundHalfEven(scale * 100.0);
                        PanelBox box = derivePanel(w, h, fs, scale, membersRows, small, ratio);
                        GridMetrics grid = GridMetrics.deriveDensity(rt, fs, density.iconSidePx(),
                                iconScalePercent, 0, box.listWidthPx(), labelBudgetEm);
                        int gap = grid.gapY();
                        int rows = box.listHeightPx() > 0
                                ? (box.listHeightPx() + gap) / grid.stridePx() : 0;
                        int visible = grid.columns() * rows;
                        PickerMetrics candidate = new PickerMetrics(w, h, fs, membersRows,
                                hard, soft, pref, density, iconScalePercent, rows, visible, grid, box);
                        if (tierBest == null || visible > tierBest.visibleItems) {
                            tierBest = candidate;
                        }
                        if (tierFirstOk == null && visible >= hard) {
                            tierFirstOk = candidate;
                        }
                        if (visible >= soft) {
                            return candidate;
                        }
                    }
                }
            }
            if (tierFirstOk != null) {
                return tierFirstOk;
            }
            // 该预算下达不到可见项下限：降一档预算再试；同时留全局最优作为兜底。
            if (fallback == null || tierBest.visibleItems > fallback.visibleItems) {
                fallback = tierBest;
            }
        }
        return fallback;
    }

    // ==================== 纯派生助手（可单测、无副作用） ====================

    /**
     * 字号派生：{@code clamp(round(declared * fontScalePercent/100), FONT_FLOOR, FONT_CEIL)}。
     *
     * <p>与 {@code SceneNode.effectiveFontSize()} 的解析出口同式（声明值 × 环境倍率，再夹取到域内）：
     * 面板派生用的字号必须与内容根真正渲染的字号逐值相同，否则宽度/行高/间距会按一个字号算、
     * 文字按另一个字号画（「几何 12 / 渲染 16」的分叉即 P7 遗留 L1）。</p>
     *
     * @param declaredFontSizePx 面板内容根的<b>声明</b>字号（未乘倍率；&lt;1 按 1）
     * @param fontScalePercent   用户字号倍率（百分比；&lt;1 按 1）
     * @return 生效字号（逻辑 px）
     */
    public static int fontSizeFor(int declaredFontSizePx, int fontScalePercent) {
        int declared = Math.max(1, declaredFontSizePx);
        int pct = Math.max(1, fontScalePercent);
        // ① 先与渲染出口逐值同式：float 乘法 → Math.round → FontSizeLimits 夹取
        //    （SceneNode.effectiveFontSize() 的解析出口；取整模式必须一致，否则半值平局会差 1px）；
        // ② 再夹到本控件的字号域：派生链只在 [FONT_FLOOR, FONT_CEIL] 内有定义。
        int rendered = FontSizeLimits.clampFontSize(Math.round(declared * (pct / 100f)));
        return clamp(rendered, PickerDensityTokens.FONT_FLOOR, PickerDensityTokens.FONT_CEIL);
    }

    /**
     * 把面板内容根的<b>声明</b>字号夹取到本控件字号域（{@code [FONT_FLOOR, FONT_CEIL]}）。
     *
     * <p>写入 portal 内容根的必须是域内值：内容节点在解析出口只会按 {@link FontSizeLimits} 的
     * {@code [1,256]} 夹取，而派生链只在 {@code [11,24]} 内有定义。不先归一会让"宿主声明 30"
     * 这类输入产生"渲染 30 / 几何 24"的分叉；归一到域内后，任何
     * {@code 声明值 × 倍率 ≤ FONT_CEIL} 的输入都逐值同源（超出上限的倍率组合仍由
     * {@link #fontSizeFor} 夹到 {@code FONT_CEIL}，属已登记的域外差异）。</p>
     *
     * @param declaredFontSizePx 面板内容根的声明字号（未乘倍率；&lt;1 按 1）
     * @return 域内声明字号
     */
    public static int clampPanelDeclaredFontPx(int declaredFontSizePx) {
        return clamp(Math.max(1, declaredFontSizePx),
                PickerDensityTokens.FONT_FLOOR, PickerDensityTokens.FONT_CEIL);
    }

    /**
     * 是否进入小盒降级模式：逻辑盒宽 &lt; 1280 <b>或</b> 高 &lt; 720。
     *
     * @param logicalWidthPx  逻辑盒宽
     * @param logicalHeightPx 逻辑盒高
     * @return true = 小盒降级
     */
    public static boolean isSmallBox(int logicalWidthPx, int logicalHeightPx) {
        return logicalWidthPx < PickerDensityTokens.SMALL_BOX_MIN_WIDTH
                || logicalHeightPx < PickerDensityTokens.SMALL_BOX_MIN_HEIGHT;
    }

    /**
     * 现状基线可见项数（<b>只服务支配性对照</b>，红线 R1/R2 的参照物）。
     *
     * <p>公式 = 现状 70% 面板 + 固定 48 顶栏 / 248 成员带 / 168 导航 / 64 单元 / 8 间距 / 24 信息条。
     * 这些常量登记在 {@link PickerDensityTokens} 的 {@code LEGACY_*} 组，<b>不得</b>参与任何布局计算。</p>
     *
     * @param logicalWidthPx  逻辑盒宽
     * @param logicalHeightPx 逻辑盒高
     * @return 现状可见项数（≥0）
     */
    public static int legacyBaselineItems(int logicalWidthPx, int logicalHeightPx) {
        int pw = logicalWidthPx * PickerDensityTokens.LEGACY_PANEL_RATIO / 100;
        int ph = logicalHeightPx * PickerDensityTokens.LEGACY_PANEL_RATIO / 100;
        int iw = pw - 2 * PickerDensityTokens.LEGACY_PANEL_PADDING;
        int ih = ph - 2 * PickerDensityTokens.LEGACY_PANEL_PADDING;
        int selection = ih - PickerDensityTokens.LEGACY_TOP_BAR_HEIGHT
                - 2 * PickerDensityTokens.LEGACY_GAP - PickerDensityTokens.LEGACY_MEMBERS_HEIGHT;
        int centerW = iw - PickerDensityTokens.LEGACY_NAV_WIDTH - PickerDensityTokens.LEGACY_GAP;
        int listW = centerW - 2 * PickerDensityTokens.LEGACY_CENTER_PADDING
                - PickerDensityTokens.LEGACY_SCROLLBAR_WIDTH;
        int listH = selection - 2 * PickerDensityTokens.LEGACY_CENTER_PADDING
                - PickerDensityTokens.LEGACY_LINE_HEIGHT - 2 * PickerDensityTokens.LEGACY_GAP
                - PickerDensityTokens.LEGACY_INFO_BAR_HEIGHT;
        int cell = PickerDensityTokens.LEGACY_CELL_HEIGHT;
        int cellGap = PickerDensityTokens.LEGACY_CELL_GAP;
        int cols = listW > 0 ? Math.max(1, (listW + cellGap) / (cell + cellGap)) : 1;
        int rows = Math.max(0, (listH + cellGap) / (cell + cellGap));
        return cols * rows;
    }

    /**
     * 面板盒派生（P5 §1.2）——比例 + 字号 → 面板尺寸与各分区高宽。
     *
     * @param logicalWidthPx  逻辑盒宽
     * @param logicalHeightPx 逻辑盒高
     * @param fontSizePx      生效字号
     * @param iconScale       {@code k}（仅用于记录；成员卡等不随 {@code k} 变）
     * @param membersRows     成员行数（{@code <0} = 无成员带，0 = 折叠提示行）
     * @param smallBox        小盒降级
     * @param ratioPercent    面板比例（%）
     * @return 面板盒快照
     */
    public static PanelBox derivePanel(int logicalWidthPx, int logicalHeightPx, int fontSizePx,
                                       double iconScale, int membersRows, boolean smallBox,
                                       int ratioPercent) {
        int w = Math.max(1, logicalWidthPx);
        int h = Math.max(1, logicalHeightPx);
        int pw = Math.min(w - 2 * PickerDensityTokens.PANEL_MARGIN,
                Math.max(1, GridMetrics.roundHalfEven(w * ratioPercent / 100.0)));
        int ph = Math.min(h - 2 * PickerDensityTokens.PANEL_MARGIN,
                Math.max(1, GridMetrics.roundHalfEven(h * ratioPercent / 100.0)));
        int pad = PickerDensityTokens.PANEL_PADDING;
        int pad2 = pad * 2;
        int iw = pw - pad2;
        int ih = ph - pad2;
        int header = clamp(GridMetrics.roundHalfEven(fontSizePx * PickerDensityTokens.HEADER_RATIO),
                PickerDensityTokens.HEADER_MIN, PickerDensityTokens.HEADER_MAX);
        int nav = clamp(GridMetrics.roundHalfEven(w * PickerDensityTokens.NAV_RATIO),
                PickerDensityTokens.NAV_MIN, PickerDensityTokens.NAV_MAX);
        if (smallBox) {
            nav = Math.max(PickerDensityTokens.NAV_MIN_SMALL, Math.min(nav,
                    GridMetrics.roundHalfEven(w * PickerDensityTokens.NAV_RATIO_SMALL)));
        }
        int effectiveRows = smallBox ? 0 : membersRows;
        int members;
        if (effectiveRows < 0) {
            members = 0;
        } else if (effectiveRows == 0) {
            members = Math.max(1,
                    GridMetrics.roundHalfEven(fontSizePx * PickerDensityTokens.MEMBERS_EMPTY_RATIO));
        } else {
            int rows = Math.min(PickerDensityTokens.MEMBER_ROWS_MAX, effectiveRows);
            int cardH = clamp(GridMetrics.roundHalfEven(
                            fontSizePx * PickerDensityTokens.MEMBER_CARD_RATIO),
                    PickerDensityTokens.MEMBER_CARD_MIN, PickerDensityTokens.MEMBER_CARD_MAX);
            int memberGap = clamp(GridMetrics.roundHalfEven(
                            fontSizePx * PickerDensityTokens.MEMBER_GAP_RATIO),
                    PickerDensityTokens.MEMBER_GAP_MIN, PickerDensityTokens.MEMBER_GAP_MAX);
            members = rows * cardH + (rows - 1) * memberGap + header;
        }
        // 信息条：常驻占位（高度随字号派生），内容永不空（悬停 -> label·ID；空闲 -> 操作提示）。
        // 小盒降级下不占位（P5 §1.5.3「信息条不占位」）。
        int info = smallBox ? 0
                : Math.max(1, GridMetrics.roundHalfEven(fontSizePx * PickerDensityTokens.INFO_BAR_RATIO));
        int listW = iw - nav - pad - pad2;
        int listH = ih - header - pad - members - pad - info - pad2;
        return new PanelBox(pw, ph, ratioPercent, header, nav, members, info, listW,
                Math.max(0, listH), effectiveRows == 0, smallBox);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== 只读访问 ====================

    /** @return 逻辑盒宽（宿主边界折算后的唯一尺寸输入） */
    public int logicalWidthPx() { return logicalWidthPx; }

    /** @return 逻辑盒高 */
    public int logicalHeightPx() { return logicalHeightPx; }



    /** @return 生效字号 */
    public int fontSizePx() { return fontSizePx; }

    /** @return 成员行数入参（{@code <0} = 无成员带） */
    public int membersRows() { return membersRows; }

    /** @return 硬目标可见项数 = {@code max(现状基线, 12)} */
    public int hardTargetItems() { return hardTargetItems; }

    /** @return 软目标可见项数（自动档 = {@code ceil(hard*1.05)}；显式档 = {@code hard}） */
    public int softTargetItems() { return softTargetItems; }

    /** @return 生效密度偏好 */
    public PickerDensityPreference preference() { return preference; }

    /** @return 生效密度档 */
    public PickerDensity density() { return density; }

    /** @return {@code k} 的百分比形式（100 = 不缩） */
    public int iconScalePercent() { return iconScalePercent; }

    /** @return 可视行数（由结果区高与 stride 派生） */
    public int visibleRows() { return visibleRows; }

    /** @return 可见项数 = 列数 × 可视行数（密度的唯一口径） */
    public int visibleItems() { return visibleItems; }

    /** @return 网格几何快照（轨道高/stride/列数/图标边长的唯一来源） */
    public GridMetrics grid() { return grid; }

    /** @return 面板盒快照 */
    public PanelBox panel() { return panel; }
}
