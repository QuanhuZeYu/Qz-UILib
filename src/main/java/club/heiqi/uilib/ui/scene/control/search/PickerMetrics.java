package club.heiqi.uilib.ui.scene.control.search;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.font.layout.FontSizeLimits;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;


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
     * @param panelFontSizePx 面板生效字号（{@code <=0} = 显式退化，见 {@link #resolveFontSizePx(int)}；
     *                        负值按 0）
     * @param preference      密度偏好（null 按 {@link PickerDensityPreference#AUTO}）
     * @param membersRows     成员行数：{@code >=0} 表示有成员带（0 = 折叠提示行），{@code <0} = 无成员带
     * @return 不可变度量快照（非 null）
     */
    public static PickerMetrics derive(SceneRuntime rt, int logicalWidthPx, int logicalHeightPx,
                                       int panelFontSizePx, PickerDensityPreference preference,
                                       int membersRows) {
        return solve(rt, logicalWidthPx, logicalHeightPx, resolveFontSizePx(panelFontSizePx),
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
     * @param fontSizePx      生效字号（{@code <=0} = 显式退化；负值按 0）
     * @param preference      密度偏好
     * @param membersRows     成员行数（{@code <0} = 无成员带）
     * @return 不可变度量快照（非 null）
     */
    public static PickerMetrics solve(SceneRuntime rt, int logicalWidthPx, int logicalHeightPx,
                                      int fontSizePx, PickerDensityPreference preference,
                                      int membersRows) {
        int w = Math.max(1, logicalWidthPx);
        int h = Math.max(1, logicalHeightPx);
        int fs = resolveFontSizePx(fontSizePx);
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
     * 面板字号解析：只保留「非负」这一数学前提（负字号在派生链里没有定义，{@code fs/3} 之类
     * 会产出负几何），<b>本控件不设自有字号域</b>。
     *
     * <p>历史口径是把正值夹到 {@code [FONT_FLOOR=11, FONT_CEIL=24]}（解析出口夹一次、写入 portal
     * 声明前再夹一次），副作用有<b>两类不同命题</b>，不要混为一例（独立复核指出原 javadoc 混了）：</p>
     * <ul>
     *   <li><b>渲染字号 ≠ 派生几何字号</b>：声明 16 × 200% ⇒ 文字 32 而几何按 24 算（同一声明在
     *       域内相乘、在域外被截）。这类组合占（声明 × 倍率）样本的七成上下 —— 不写死精确对数：
     *       它随复算口径（是否对 0 特判、倍率步长、取整模式）在 2~4% 内浮动，写死即可漂移；</li>
     *   <li><b>设定 ≠ 产出</b>：声明 8 × 100% ⇒ 产出 11（此时渲染 11 / 几何 11，两者同值，
     *       问题出在用户设的 8 被改写）。</li>
     * </ul>
     * <p>字号域的唯一边界是 {@link FontSizeLimits} 的 {@code [0, 256]}，本控件不再叠加第二套区间 ——
     * <b>设置过小是使用方的问题，几何如实跟随</b>。</p>
     *
     * <p>本方法仍是<b>唯一</b>解析出口（{@link #derive}/{@link #solve} 共用），因此不存在
     * 「下游某处又夹一次」的分叉面。</p>
     */
    private static int resolveFontSizePx(int fontSizePx) {
        return Math.max(0, fontSizePx);
    }

    /**
     * 字号派生：与渲染出口 {@link FontSizeLimits#effectiveFontSizePx(int, float)} <b>同一个方法</b>。
     *
     * <p>面板派生用的字号必须与内容根真正渲染的字号逐值相同，否则宽度/行高/间距会按一个字号算、
     * 文字按另一个字号画（「几何 12 / 渲染 16」的分叉即 P7 遗留 L1）。同源靠「调用同一个出口」
     * 保证，而不是靠「本控件再夹一次区间」—— 后者只在区间内同源，区间外即分叉。</p>
     *
     * @param declaredFontSizePx 面板内容根的<b>声明</b>字号（未乘倍率；负值按 0）
     * @param fontScalePercent   用户字号倍率（百分比；负值按 0）
     * @return 生效字号（逻辑 px；0 = 文本不占空间且不上屏）
     */
    public static int fontSizeFor(int declaredFontSizePx, int fontScalePercent) {
        return FontSizeLimits.effectiveFontSizePx(Math.max(0, declaredFontSizePx),
                Math.max(0, fontScalePercent) / 100f);
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
