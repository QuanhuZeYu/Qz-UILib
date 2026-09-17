package club.heiqi.uilib.ui.scene.control.search;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;

/**
 * PickerChrome —— 选择器"面板内其它随字号缩放的元素"的<strong>唯一派生实现</strong>（P5 §2.5）。
 *
 * <p>成员卡 / 变体行 / 分类行 / 触发器 / 徽章内边距 / 滚动条宽度此前都是各控件里的私有裸常量
 * （{@code 440 / 240 / 34 / 18 / 200 / 96 / 32 / 168 / 24 / 12 / 6 ...}）。规格要求它们一律
 * <b>由生效字号派生</b>：常量只保留"比例/除数/夹取边界"，且全部集中在
 * {@link PickerDensityTokens}（P5 §4.3 条 2 的机械核对口径）。</p>
 *
 * <p>取整统一走 {@link GridMetrics#roundHalfEven}（与 P5 验算脚本的 Python {@code round()} 同语义），
 * 避免字号档之间的取整分叉。字号输入只做<b>非负归一</b>（{@code fs(0) == 0}），与
 * {@code PickerMetrics.resolveFontSizePx} 同口径 —— 本类<b>不设字号下限</b>：字号 0 时随字号派生的
 * 量如实归零，各几何量自己的 {@code MIN} 是组件下限，与字号域无关。</p>
 *
 * <h3>失效通道（P5 §4.3 条 5）</h3>
 * <p>本类是纯函数集合、无状态、无缓存，因此不需要失效通道：调用方把<b>字号信号</b>接进来，
 * 字号变化即重算（{@code rt.bind(fontSizePx, ...)}）；静态常量只是比例，不随运行期状态变化。</p>
 */
public final class PickerChrome {

    private PickerChrome() {
    }

    /** @param fontSizePx 生效字号（&lt;1 按 1） @return 成员卡高（{@code clamp(round(fs*8), 72, 120)}） */
    public static int memberCardHeight(int fontSizePx) {
        return clamp(GridMetrics.roundHalfEven(fs(fontSizePx) * PickerDensityTokens.MEMBER_CARD_RATIO),
                PickerDensityTokens.MEMBER_CARD_MIN, PickerDensityTokens.MEMBER_CARD_MAX);
    }

    /** @param fontSizePx 生效字号 @return 成员卡宽（{@code clamp(round(fs*16), 176, 232)}） */
    public static int memberCardWidth(int fontSizePx) {
        return clamp(GridMetrics.roundHalfEven(
                        fs(fontSizePx) * PickerDensityTokens.MEMBER_CARD_WIDTH_RATIO),
                PickerDensityTokens.MEMBER_CARD_WIDTH_MIN, PickerDensityTokens.MEMBER_CARD_WIDTH_MAX);
    }

    /** @param fontSizePx 生效字号 @return 成员卡图标边长（{@code round(fs*2)}） */
    public static int memberIconSide(int fontSizePx) {
        return Math.max(1, GridMetrics.roundHalfEven(fs(fontSizePx) * PickerDensityTokens.MEMBER_ICON_RATIO));
    }

    /** @param fontSizePx 生效字号 @return 变体行高（{@code clamp(round(fs*2.67), 26, 40)}） */
    public static int variantRowHeight(int fontSizePx) {
        return clamp(GridMetrics.roundHalfEven(fs(fontSizePx) * PickerDensityTokens.VARIANT_ROW_RATIO),
                PickerDensityTokens.VARIANT_ROW_MIN, PickerDensityTokens.VARIANT_ROW_MAX);
    }

    /** @param fontSizePx 生效字号 @return 变体图标边长（{@code round(fs*1.5)}） */
    public static int variantIconSide(int fontSizePx) {
        return Math.max(1, GridMetrics.roundHalfEven(fs(fontSizePx) * PickerDensityTokens.VARIANT_ICON_RATIO));
    }

    /** @param fontSizePx 生效字号 @return 变体列表视口高（{@code clamp(round(fs*12), 180, 320)}） */
    public static int variantListHeight(int fontSizePx) {
        return clamp(GridMetrics.roundHalfEven(fs(fontSizePx) * PickerDensityTokens.VARIANT_LIST_RATIO),
                PickerDensityTokens.VARIANT_LIST_MIN, PickerDensityTokens.VARIANT_LIST_MAX);
    }

    /** @param panelWidthPx 面板宽 @return 变体卡宽（{@code min(round(panelW*0.42), 520)}） */
    public static int variantCardWidth(int panelWidthPx) {
        return Math.max(1, Math.min(PickerDensityTokens.VARIANT_CARD_MAX,
                GridMetrics.roundHalfEven(Math.max(1, panelWidthPx)
                        * PickerDensityTokens.VARIANT_CARD_RATIO)));
    }

    /** @param fontSizePx 生效字号 @return 分类导航行高（{@code clamp(round(fs*2.67), 24, 36)}） */
    public static int navRowHeight(int fontSizePx) {
        return clamp(GridMetrics.roundHalfEven(fs(fontSizePx) * PickerDensityTokens.NAV_ROW_RATIO),
                PickerDensityTokens.NAV_ROW_MIN, PickerDensityTokens.NAV_ROW_MAX);
    }

    /** @param fontSizePx 生效字号 @return 分类导航 pill 行左右内缩（{@code clamp(round(fs*0.5), 3, 8)}） */
    public static int navRowInset(int fontSizePx) {
        return clamp(GridMetrics.roundHalfEven(fs(fontSizePx) * PickerDensityTokens.NAV_ROW_INSET_RATIO),
                PickerDensityTokens.NAV_ROW_INSET_MIN, PickerDensityTokens.NAV_ROW_INSET_MAX);
    }

    /** @param fontSizePx 生效字号 @return 分类导航 pill 行行间纵向间距（{@code clamp(round(fs*0.25), 2, 6)}） */
    public static int navRowGap(int fontSizePx) {
        return clamp(GridMetrics.roundHalfEven(fs(fontSizePx) * PickerDensityTokens.NAV_ROW_GAP_RATIO),
                PickerDensityTokens.NAV_ROW_GAP_MIN, PickerDensityTokens.NAV_ROW_GAP_MAX);
    }

    /** @param fontSizePx 生效字号 @return 成员卡圆角（{@code clamp(round(fs*0.67), 6, 14)}） */
    public static int memberCardRadius(int fontSizePx) {
        return clamp(GridMetrics.roundHalfEven(fs(fontSizePx) * PickerDensityTokens.MEMBER_CARD_RADIUS_RATIO),
                PickerDensityTokens.MEMBER_CARD_RADIUS_MIN, PickerDensityTokens.MEMBER_CARD_RADIUS_MAX);
    }

    /** @param fontSizePx 生效字号 @return 徽章内边距（{@code round(fs/3)}） */
    public static int badgePadding(int fontSizePx) {
        return Math.max(1, GridMetrics.roundHalfEven(fs(fontSizePx) / PickerDensityTokens.BADGE_PAD_DIVISOR));
    }

    /** @param fontSizePx 生效字号 @return 触发器图标边长（{@code round(fs*1.5)}） */
    public static int triggerIconSide(int fontSizePx) {
        return Math.max(1, GridMetrics.roundHalfEven(fs(fontSizePx) * PickerDensityTokens.TRIGGER_ICON_RATIO));
    }

    /** @param fontSizePx 生效字号 @return 滚动条宽（{@code clamp(round(fs/2), 6, 10)}） */
    public static int scrollbarWidth(int fontSizePx) {
        return clamp(GridMetrics.roundHalfEven(fs(fontSizePx) * PickerDensityTokens.SCROLLBAR_WIDTH_RATIO),
                PickerDensityTokens.SCROLLBAR_WIDTH_MIN, PickerDensityTokens.SCROLLBAR_WIDTH_MAX);
    }

    /**
     * 滚动条宽度信号（P5 第六轮 U-P5-13 收口：滚动条宽度的密度/字号动态派生）。
     *
     * <p>把生效字号派生链（{@link #scrollbarWidth(int)}）包成信号供
     * {@link club.heiqi.uilib.ui.scene.control.SceneScrollbar} 消费：字号倍率或密度档变化
     * ⇒ PickerMetrics 重派生 ⇒ 本投影变化 ⇒ 滚动条只重派生几何（不重建控件）。</p>
     *
     * <p><b>工厂判据（U-P6D-3 陷阱）</b>：{@code SceneScrollbar.create} 会在同一次 flush 内同步读一次
     * 投影用于首帧几何，故必须用 {@link Computed#create(Object, java.util.function.Supplier)}
     * 注入与派生语义一致的同步初值 —— 用 {@code create(Supplier)} 会让首帧拿到 null 初值、
     * 宽度停在常量档（静默失效）。</p>
     *
     * @param metrics 派生度量信号；null = 无度量通道 ⇒ 返回 null（调用方走常量缺省，逐值不变）
     * @return 宽度信号（初值 = 当前字号的派生值）
     */
    public static ReadableSignal<Integer> scrollbarWidthSignal(ReadableSignal<PickerMetrics> metrics) {
        if (metrics == null) {
            return null;
        }
        return Computed.create(Integer.valueOf(scrollbarWidth(metrics.get().fontSizePx())),
                () -> Integer.valueOf(scrollbarWidth(metrics.get().fontSizePx())));
    }

    /**
     * 滚动条宽度信号（{@link GridMetrics} 通道重载，语义见
     * {@link #scrollbarWidthSignal(ReadableSignal)}）。
     *
     * @param metrics 网格度量信号；null = 无度量通道 ⇒ 返回 null（常量缺省）
     * @return 宽度信号（初值 = 当前字号的派生值）
     */
    public static ReadableSignal<Integer> scrollbarWidthSignalOf(ReadableSignal<GridMetrics> metrics) {
        if (metrics == null) {
            return null;
        }
        return Computed.create(Integer.valueOf(scrollbarWidth(metrics.get().fontSizePx())),
                () -> Integer.valueOf(scrollbarWidth(metrics.get().fontSizePx())));
    }

    /** 非负归一（与 {@code PickerMetrics.resolveFontSizePx} 同口径）：字号 0 时派生量如实归零。 */
    private static int fs(int fontSizePx) {
        return Math.max(0, fontSizePx);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
