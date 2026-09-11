package club.heiqi.uilib.ui.scene.control.search;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * PickerMetrics —— 选择器「尺寸 / 密度 / 网格几何」的<strong>唯一派生实现</strong>（P5 §1 + §2）。
 *
 * <h3>三分量铁律（Qz-UILib AGENTS.md:28）</h3>
 * <pre>
 *   [宿主边界 · 唯一允许接触 GUI Scale 的位置]
 *     logicalBox = hostCompose(nativeBox, guiScale)        // 见 HostViewportScale
 *   [面板内部 · 禁止读 GUI Scale / 禁止假定分辨率]
 *     fontPct = runtime.getFontScalePercent()              // 用户字号倍率（运行期可变）
 *     density = 用户偏好（AUTO/COMPACT/STANDARD/ROOMY）     // 运行期可变
 *     metrics = PickerMetrics.derive(rt, logicalBox, fontPct, density, membersRows)
 * </pre>
 * <p>本类<b>不接收</b> GUI Scale、不接收物理分辨率、不缓存跨帧结果 —— 三个输入任一变化即重派生
 * （P5 I-6「无静态快照」）。</p>
 *
 * <h3>auto 求解（P5 §1.4，规范性）</h3>
 * <pre>
 *   hard = max(现状可见量(logicalBox), 12)      // 支配性目标（红线 R1/R2）
 *   soft = ceil(hard * 1.05)                    // 5% 安全裕度，拒绝贴线通过
 *   for ratio in [70,78,84,92]:                 // 面板从小到大
 *     for density in [standard, compact]:       // 图标从大到小
 *       for k in [1.00,.95,.90,.85,.80]:        // 图标等比缩小（字号不缩）
 *         if visible >= soft: return 该组合
 *   返回第一个满足 hard 的组合；都不满足 → 取可见量最大的组合
 * </pre>
 * <p>被选中的组合同时决定：面板比例、密度档、{@code k}、网格几何、列数与可视行数。
 * 「面板能长到 92%」与「成员带可折叠」两级阶梯必须实装，否则 1080p + 字号 150% 会贴线
 * （实测：无 5% 裕度时裕度仅 +1，加裕度后 +4）。</p>
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
    private final int fontPct;
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

    private PickerMetrics(int logicalWidthPx, int logicalHeightPx, int fontPct, int fontSizePx,
                          int membersRows, int hardTargetItems, int softTargetItems,
                          PickerDensityPreference preference, PickerDensity density,
                          int iconScalePercent, int visibleRows, int visibleItems,
                          GridMetrics grid, PanelBox panel) {
        this.logicalWidthPx = logicalWidthPx;
        this.logicalHeightPx = logicalHeightPx;
        this.fontPct = fontPct;
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
     * 生产入口：由「逻辑盒 + 字号倍率 + 密度偏好 + 成员行数」派生全部几何。
     *
     * @param rt             场景运行时（提供行高与文本宽度量；非 null）
     * @param logicalWidthPx 逻辑盒宽（宿主边界已折算；&lt;1 按 1）
     * @param logicalHeightPx 逻辑盒高（宿主边界已折算；&lt;1 按 1）
     * @param fontPct        用户字号倍率（百分比；&lt;1 按 1）
     * @param preference     密度偏好（null 按 {@link PickerDensityPreference#AUTO}）
     * @param membersRows    成员行数：{@code >=0} 表示有成员带（0 = 折叠提示行），{@code <0} = 无成员带
     * @return 不可变度量快照（非 null）
     */
    public static PickerMetrics derive(SceneRuntime rt, int logicalWidthPx, int logicalHeightPx,
                                       int fontPct, PickerDensityPreference preference,
                                       int membersRows) {
        PickerDensityPreference pref = preference == null
                ? PickerDensityPreference.AUTO : preference;
        // 自动档的字号基准取标准档（P5 §1.3：标准档 = 与既有字号线一致的标准态）。
        PickerDensity fontDensity = pref.explicitDensity(PickerDensity.STANDARD);
        int fs = fontSizeFor(fontPct, fontDensity);
        return solve(rt, logicalWidthPx, logicalHeightPx, fs, pref, membersRows);
    }

    /**
     * 规格对拍入口（与 {@code temp/p5_density_spec.py} 的 {@code p5_layout(W,H,fs,pref)} 同参数化）：
     * 字号<b>显式给出</b>，不再乘档位基准字号。
     *
     * <p>存在的唯一理由是让密度证明可在 Java 侧逐值复算（A1-A8）：规格脚本按
     * {@code fs = clamp(round(12 * pct/100))} 传入字号，与档位基准字号无关。生产路径请用
     * {@link #derive}（档位基准字号参与字号派生）。</p>
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

        PickerMetrics best = null;
        PickerMetrics firstOk = null;
        for (int ratio : ratios) {
            for (PickerDensity density : order) {
                for (double scale : PickerDensityTokens.ICON_SCALE_STEPS) {
                    int iconScalePercent = GridMetrics.roundHalfEven(scale * 100.0);
                    PanelBox box = derivePanel(w, h, fs, scale, membersRows, small, ratio);
                    GridMetrics grid = GridMetrics.deriveDensity(rt, fs, density.iconSidePx(),
                            iconScalePercent, 0, box.listWidthPx());
                    int gap = grid.gapY();
                    int rows = box.listHeightPx() > 0
                            ? (box.listHeightPx() + gap) / grid.stridePx() : 0;
                    int visible = grid.columns() * rows;
                    PickerMetrics candidate = new PickerMetrics(w, h, 100, fs, membersRows,
                            hard, soft, pref, density, iconScalePercent, rows, visible, grid, box);
                    if (best == null || visible > best.visibleItems) {
                        best = candidate;
                    }
                    if (firstOk == null && visible >= hard) {
                        firstOk = candidate;
                    }
                    if (visible >= soft) {
                        return candidate;
                    }
                }
            }
        }
        return firstOk != null ? firstOk : best;
    }

    // ==================== 纯派生助手（可单测、无副作用） ====================

    /**
     * 字号派生：{@code clamp(round(base * fontPct/100), FONT_FLOOR, FONT_CEIL)}。
     *
     * @param fontPct 用户字号倍率（百分比；&lt;1 按 1）
     * @param density 档位（null 按标准档）
     * @return 生效字号（逻辑 px）
     */
    public static int fontSizeFor(int fontPct, PickerDensity density) {
        PickerDensity d = density == null ? PickerDensity.STANDARD : density;
        int pct = Math.max(1, fontPct);
        int scaled = GridMetrics.roundHalfEven(d.baseFontPx() * pct / 100.0);
        return clamp(scaled, PickerDensityTokens.FONT_FLOOR, PickerDensityTokens.FONT_CEIL);
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

    /** @return 字号倍率（%） */
    public int fontPct() { return fontPct; }

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
