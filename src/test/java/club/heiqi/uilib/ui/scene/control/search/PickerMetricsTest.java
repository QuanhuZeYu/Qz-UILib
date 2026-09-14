package club.heiqi.uilib.ui.scene.control.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.layout.FontSizeLimits;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;

/**
 * P5 尺寸/密度派生证明（P5 §1.5.1-§1.5.3、§2.1-§2.3；红线 R1/R2/R3）。
 *
 * <p>本类把 {@code temp/p5_density_spec.py} 的断言 A1-A8 逐条搬到 Java 侧复算，
 * 用 {@link SpecMeasurer} 复刻脚本的度量模型（{@code lineHeight(fs)=round(fs*1.25)}、
 * 4 字符样本宽 {@code round(fs*3.2)}），从而与规格表的<b>逐值</b>对拍：</p>
 * <ul>
 *   <li>A1 现状基线 1920×1080 = 75（T5 对拍）；</li>
 *   <li>A2/A3 P5 1080p fs=12/15/18 ≥ 75（常驻信息条口径实测 100/80/95）；</li>
 *   <li>A4 auto 逐点支配（8 视口 × 3 字号，失败点 0）；</li>
 *   <li>A5 图标正方形不变量（fs=12/24 均 40）；</li>
 *   <li>A6/A7 三档均 ≥ 现状（失败点 0）；</li>
 *   <li>A8 小盒降级 ≥ 现状（960×540 → 102、640×360 → 30）。</li>
 * </ul>
 *
 * <p>另一条红线（R3 动态化）由 {@code pickerSizeChainNeverTouchesGuiScale} 承担：
 * 派生链源码内不得出现 GUI Scale / 物理分辨率符号 —— 三分量中「GUI Scale」只在宿主边界合成。</p>
 */
public class PickerMetricsTest {

    /** 规格脚本的 8 个对照视口。 */
    private static final int[][] VIEWPORTS = {
            {1280, 720}, {1366, 768}, {1600, 900}, {1920, 1080}, {1920, 1200},
            {2560, 1440}, {3440, 1440}, {3840, 2160},
    };
    /** 规格脚本的现状基线可见项数（§1.5.1 表）。 */
    private static final int[] BASELINE = {9, 20, 36, 75, 90, 176, 240, 510};
    /** 规格脚本的字号档（100/125/150%）。 */
    private static final int[] FONT_SIZES = {12, 15, 18};

    private SceneRuntime rt;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        rt = new SceneRuntime(new SpecMeasurer());
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== A1 现状基线 ====================

    @Test
    public void a1LegacyBaselineMatchesSpecTable() {
        for (int i = 0; i < VIEWPORTS.length; i++) {
            int visible = PickerMetrics.legacyBaselineItems(VIEWPORTS[i][0], VIEWPORTS[i][1]);
            assertEquals("A1 现状基线 " + VIEWPORTS[i][0] + "x" + VIEWPORTS[i][1],
                    BASELINE[i], visible);
        }
        assertEquals("A1 1080p 基线 = 15x5", 75, PickerMetrics.legacyBaselineItems(1920, 1080));
    }

    // ==================== A2/A3 1080p 绝对底线 ====================

    @Test
    public void a2A3P5At1080pBeatsBaselineAtEveryFontTier() {
        PickerMetrics fs12 = PickerMetrics.solve(rt, 1920, 1080, 12, PickerDensityPreference.AUTO, 2);
        // 常驻信息条口径（D-P5-2）：比规格脚本的「hover 才占位」模型低一行量级，
        // 但三档仍全部 ≥ 现状 75（支配性见 a4AutoDominatesLegacyAtEveryViewportAndFontTier）。
        // 加入标签可读宽度预算（LABEL_BUDGET_EM）后的快照：cellW 由标签预算主导，列数下降，
        // 可见项从 100/80/95 变为 84/88/77 —— 三档仍全部 >= 现状 75（下方红线断言）。
        assertEquals("fs=12 auto 可见项（常驻信息条口径）", 84, fs12.visibleItems());
        PickerMetrics fs15 = PickerMetrics.solve(rt, 1920, 1080, 15, PickerDensityPreference.AUTO, 2);
        assertEquals("fs=15(125%) auto 可见项", 88, fs15.visibleItems());
        PickerMetrics fs18 = PickerMetrics.solve(rt, 1920, 1080, 18, PickerDensityPreference.AUTO, 2);
        assertEquals("fs=18(150%) auto 可见项", 77, fs18.visibleItems());
        for (PickerMetrics m : new PickerMetrics[] {fs12, fs15, fs18}) {
            assertTrue("A2/A3 1080p 可见项必须 >= 现状 75，实际 " + m.visibleItems(),
                    m.visibleItems() >= 75);
        }
    }

    @Test
    public void a2AutoAt1080pMatchesSpecDetail() {
        PickerMetrics m = PickerMetrics.solve(rt, 1920, 1080, 12, PickerDensityPreference.AUTO, 2);
        assertEquals("面板宽 1498（比例阶梯升到 78% 以吸收标签预算的列数代价）", 1498, m.panel().widthPx());
        assertEquals("面板高 842", 842, m.panel().heightPx());
        assertEquals("结果区宽 1270", 1270, m.panel().listWidthPx());
        assertEquals("结果区高 482", 482, m.panel().listHeightPx());
        assertEquals("信息条高 24", 24, m.panel().infoBarHeightPx());
        assertEquals("顶栏高 44", 44, m.panel().headerHeightPx());
        assertEquals("导航宽 188", 188, m.panel().navWidthPx());
        assertEquals("生效比例 78%", 78, m.panel().ratioPercent());
        assertEquals("列数 12", 12, m.grid().columns());
        assertEquals("可视行数 7", 7, m.visibleRows());
        // cellW = 标签可读宽度预算主导（2*pad + round(fs*7.5) = 8 + 90 = 98 > icon+2*pad = 48）。
        assertEquals("cellW 98", 98, m.grid().cellWidthPx());
        assertEquals("标签预算 98", 98, m.grid().labelBudgetPx());
        assertEquals("标签净宽 90（= cellW - 2*pad）", 90,
                m.grid().cellWidthPx() - 2 * m.grid().paddingPx());
        assertEquals("trackH 63", 63, m.grid().trackHeightPx());
        assertEquals("stride 69", 69, m.grid().stridePx());
        assertEquals("icon 38（auto 阶梯下调一档 k 保可见量）", 38, m.grid().iconSidePx());
        assertEquals("gap 6", 6, m.grid().gapY());
        assertEquals("k=95", 95, m.iconScalePercent());
        assertEquals("密度档 standard", PickerDensity.STANDARD, m.density());
    }

    // ==================== A4 逐点支配 ====================

    @Test
    public void a4AutoDominatesLegacyAtEveryViewportAndFontTier() {
        StringBuilder failures = new StringBuilder();
        int checked = 0;
        for (int[] viewport : VIEWPORTS) {
            int baseline = PickerMetrics.legacyBaselineItems(viewport[0], viewport[1]);
            for (int fs : FONT_SIZES) {
                int visible = PickerMetrics.solve(rt, viewport[0], viewport[1], fs,
                        PickerDensityPreference.AUTO, 2).visibleItems();
                checked++;
                if (visible < baseline) {
                    failures.append(' ').append(viewport[0]).append('x').append(viewport[1])
                            .append("/fs=").append(fs).append(':').append(visible).append('<')
                            .append(baseline);
                }
            }
        }
        assertEquals(24, checked);
        assertEquals("A4 支配性失败点必须为 0：" + failures, 0, failures.length());
    }

    @Test
    public void a4ThinnestMarginMatchesSpec() {
        // 最薄裕度点：1366x768 / 字号 150% / 现状 20。加入标签预算后该点只余 +1 裕度
        // （标签宽度优先于密度，预算一直给到贴线为止）—— 红线仍是"具支配性"，只是余量变薄。
        int baseline = PickerMetrics.legacyBaselineItems(1366, 768);
        PickerMetrics m = PickerMetrics.solve(rt, 1366, 768, 18, PickerDensityPreference.AUTO, 2);
        assertEquals(20, baseline);
        assertEquals(21, m.visibleItems());
        assertEquals("最薄裕度 +1（仍 >= 0）", 1, m.visibleItems() - baseline);
    }

    // ==================== A5 图标与不变量 ====================

    @Test
    public void a5IconStaysSquareAndNeverShrinksWithFontSize() {
        for (int fs : new int[] {11, 12, 15, 18, 21, 24}) {
            GridMetrics grid = GridMetrics.deriveDensity(rt, fs, 40, 100, 0, 1116);
            assertEquals("A5 fs=" + fs + " 图标恒为目标边长", 40, grid.iconSidePx());
            int expectedTrack = grid.iconSidePx() + 2 * grid.paddingPx()
                    + grid.lineHeightPx() + grid.labelGapPx();
            assertEquals("轨道高 = 图标 + 上下 padding + 标签行 + 间距 (fs=" + fs + ")",
                    expectedTrack, grid.trackHeightPx());
            assertTrue("cellW >= 图标 + 上下 padding (fs=" + fs + ")",
                    grid.cellWidthPx() >= grid.iconSidePx() + 2 * grid.paddingPx());
            assertEquals("stride = trackH + gap (fs=" + fs + ")",
                    grid.trackHeightPx() + grid.gapY(), grid.stridePx());
            assertEquals("列行同源：gapX == gapY (fs=" + fs + ")", grid.gapX(), grid.gapY());
        }
    }

    @Test
    public void a5DerivationTableMatchesSpecSection2_3() {
        // 规格 §2.3 表（标准档 icon=40, k=1.0）：逐值对拍脚本 derive 模式。
        int[][] expected = {
                // fs, lineH, pad, labelGap, trackH, stride, icon, gap
                {11, 14, 4, 2, 64, 70, 40, 6},
                {12, 15, 4, 2, 65, 71, 40, 6},
                {15, 19, 5, 2, 71, 79, 40, 8},
                {18, 22, 6, 3, 77, 86, 40, 9},
                {21, 26, 6, 4, 82, 92, 40, 10},
                {24, 30, 6, 4, 86, 96, 40, 10},
        };
        for (int[] row : expected) {
            GridMetrics g = GridMetrics.deriveDensity(rt, row[0], 40, 100, 0, 1116);
            String tag = "fs=" + row[0];
            assertEquals(tag + " lineH", row[1], g.lineHeightPx());
            assertEquals(tag + " pad", row[2], g.paddingPx());
            assertEquals(tag + " labelGap", row[3], g.labelGapPx());
            assertEquals(tag + " trackH", row[4], g.trackHeightPx());
            assertEquals(tag + " stride", row[5], g.stridePx());
            assertEquals(tag + " icon", row[6], g.iconSidePx());
            assertEquals(tag + " gap", row[7], g.gapY());
        }
    }

    @Test
    public void a5IconScaleStepsFollowTheLadder() {
        // k 只缩图标与由图标派生的轨道高，不动字号（P5 §1.4）。
        int[] expectedIcons = {40, 38, 36, 34, 32};
        for (int i = 0; i < PickerDensityTokens.ICON_SCALE_STEPS.length; i++) {
            int percent = GridMetrics.roundHalfEven(PickerDensityTokens.ICON_SCALE_STEPS[i] * 100.0);
            GridMetrics g = GridMetrics.deriveDensity(rt, 12, 40, percent, 0, 1116);
            assertEquals("k=" + percent + "% 图标", expectedIcons[i], g.iconSidePx());
            assertEquals("k 不缩字号", 12, g.fontSizePx());
        }
    }

    // ==================== A6/A7 三档支配 ====================

    @Test
    public void a6ThreeTiersMatchSpecAtKeyViewports() {
        // 标签可读宽度预算统一（不随档位变）后，档位差异体现在图标边长与轨道高，不再体现在列数上：
        // 列数由同一份 cellW 主导 ⇒ 1080p 及以下三档列数相同、可见项相同；4K 下图标/步长差异
        // 才重新分出可见项差。三档各自都仍 >= 现状基线（a7 逐点支配另测）。
        assertTier(1280, 720, 12, 12, 12);
        assertTier(1920, 1080, 84, 84, 84);
        assertTier(3840, 2160, 520, 546, 532);
    }

    private void assertTier(int w, int h, int standard, int compact, int roomy) {
        String tag = w + "x" + h;
        assertEquals(tag + " standard", standard, PickerMetrics
                .solve(rt, w, h, 12, PickerDensityPreference.STANDARD, 2).visibleItems());
        assertEquals(tag + " compact", compact, PickerMetrics
                .solve(rt, w, h, 12, PickerDensityPreference.COMPACT, 2).visibleItems());
        assertEquals(tag + " roomy", roomy, PickerMetrics
                .solve(rt, w, h, 12, PickerDensityPreference.ROOMY, 2).visibleItems());
    }

    @Test
    public void a7EveryTierDominatesLegacyWithItsOwnBaseFont() {
        PickerDensityPreference[] tiers = {PickerDensityPreference.STANDARD,
                PickerDensityPreference.COMPACT, PickerDensityPreference.ROOMY};
        StringBuilder failures = new StringBuilder();
        for (int[] viewport : VIEWPORTS) {
            int baseline = PickerMetrics.legacyBaselineItems(viewport[0], viewport[1]);
            for (PickerDensityPreference tier : tiers) {
                int fs = tier.explicitDensity(PickerDensity.STANDARD).baseFontPx();
                int visible = PickerMetrics.solve(rt, viewport[0], viewport[1], fs, tier, 2)
                        .visibleItems();
                if (visible < baseline) {
                    failures.append(' ').append(viewport[0]).append('x').append(viewport[1])
                            .append('/').append(tier).append(':').append(visible).append('<')
                            .append(baseline);
                }
            }
        }
        assertEquals("A6/A7 三档必须全部支配现状：" + failures, 0, failures.length());
        // 档位基准字号：紧凑 11 / 标准 12 / 宽松 13（P5 §1.3）——现语义是"宿主未声明字号时的
        // 面板默认声明值"，生效字号 = 声明值 × 用户倍率（与 SceneNode.effectiveFontSize() 同式）。
        assertEquals(11, PickerDensity.COMPACT.baseFontPx());
        assertEquals(12, PickerDensity.STANDARD.baseFontPx());
        assertEquals(13, PickerDensity.ROOMY.baseFontPx());
        assertEquals("默认面板声明字号 = 标准档基准", 12, PickerMetrics.defaultPanelDeclaredFontPx());
        assertEquals("字号 125% 标准档 = 15", 15,
                PickerMetrics.fontSizeFor(PickerDensity.STANDARD.baseFontPx(), 125));
        assertEquals("字号 150% 标准档 = 18", 18,
                PickerMetrics.fontSizeFor(PickerDensity.STANDARD.baseFontPx(), 150));
    }

    @Test
    public void a7RoomyNeverAutoSelected() {
        // Q6：auto 只在 standard -> compact 内向下求解，不自动升档到 roomy。
        for (int[] viewport : VIEWPORTS) {
            PickerMetrics m = PickerMetrics.solve(rt, viewport[0], viewport[1], 12,
                    PickerDensityPreference.AUTO, 2);
            assertTrue("auto 不得选中 roomy（" + viewport[0] + "x" + viewport[1] + "）",
                    m.density() == PickerDensity.STANDARD || m.density() == PickerDensity.COMPACT);
        }
    }

    @Test
    public void a7AutoKeepsSafetyMarginWhenPossible() {
        // 硬红线：每个观测点都必须 >= hard。5% 软裕度不是硬约束 —— 标签可读宽度预算生效后，
        // 预算只在"连 hard 都达不到"时才让路（见 LABEL_BUDGET_DEGRADE_STEPS），故贴线点
        // （1080p/fs18 = 77 < soft 79）会停在 ≥ hard 且 < soft：这是"可读优先"的有意取舍。
        // 该用例因此断言硬红线与 soft 口径本身，不断言 soft 必然可达。
        for (int[] viewport : VIEWPORTS) {
            for (int fs : FONT_SIZES) {
                PickerMetrics m = PickerMetrics.solve(rt, viewport[0], viewport[1], fs,
                        PickerDensityPreference.AUTO, 2);
                int hard = Math.max(PickerMetrics.legacyBaselineItems(viewport[0], viewport[1]), 12);
                int soft = (int) Math.ceil(hard * 1.05);
                assertTrue("auto 解必须 >= hard（" + viewport[0] + "x" + viewport[1] + " fs=" + fs + "）",
                        m.visibleItems() >= hard);
                assertEquals("软目标口径", soft, m.softTargetItems());
            }
        }
    }

    // ==================== 标签可读宽度预算（本次改动的主守卫） ====================

    /**
     * 标签槽必须容得下"可读的"全角字符数，且预算生效时 cellW 不得把它压回去。
     *
     * <p>这条守卫直面用户症状「每个物品只显示第一个字」：只要 {@code labelBudgetPx > 0}
     * （预算未被降级阶梯让光），槽宽 {@code cellW - 2*pad} 就必须等于预算净宽，且减去省略号后
     * 仍放得下 <b>至少 2 个全角字符</b>（预算下限 3.0em = 3 个字宽，留 0.75em 给 "..."）。
     * 预算为 0 时表示该观测点连红线都难保、求解器已按阶梯让路（退回既有口径），不做断言。</p>
     */
    @Test
    public void labelBudgetNeverLetsCellWidthStarveTheLabel() {
        int checked = 0;
        for (int[] viewport : VIEWPORTS) {
            for (int fs : FONT_SIZES) {
                PickerMetrics m = PickerMetrics.solve(rt, viewport[0], viewport[1], fs,
                        PickerDensityPreference.AUTO, 2);
                GridMetrics g = m.grid();
                if (g.labelBudgetPx() <= 0) {
                    continue;
                }
                checked++;
                String tag = viewport[0] + "x" + viewport[1] + "/fs=" + fs;
                assertTrue(tag + " cellW 必须 >= 标签预算",
                        g.cellWidthPx() >= g.labelBudgetPx());
                int slot = g.cellWidthPx() - 2 * g.paddingPx();
                int budgetSlot = g.labelBudgetPx() - 2 * g.paddingPx();
                // cellW 取三项下界的 max ⇒ 槽宽只能保证"≥ 预算净宽"（图标/样本更宽时由它们主导）；
                // 断言等值会在合法的降级档（预算小于图标宽）上误报，故这里是单向不等式。
                assertTrue(tag + " 槽宽不得小于预算净宽（slot=" + slot + " budget=" + budgetSlot + "）",
                        slot >= budgetSlot);
                int ellipsis = rt.measureTextWidth("...", fs);
                assertTrue(tag + " 槽内必须放得下 >=2 个全角字符 + 省略号（槽=" + slot
                                + " 省略号=" + ellipsis + " fs=" + fs + "）",
                        slot - ellipsis >= 2 * fs);
            }
        }
        assertTrue("至少要有观测点真正吃到了标签预算（否则本守卫是空断言）", checked > 0);
    }

    /**
     * 降级阶梯的末档必须是 0（关闭预算）：这是"加了标签预算在任何逻辑盒/字号下都不可能比加之前更差"
     * 的结构保证 —— 最坏也只退回既有 cellW 口径，红线因此不需要靠人裁量维持。
     */
    @Test
    public void labelBudgetLadderEndsAtZeroSoItCanNeverBeWorseThanNoBudget() {
        double[] steps = PickerDensityTokens.LABEL_BUDGET_DEGRADE_STEPS;
        assertTrue("阶梯必须非空", steps.length > 0);
        assertEquals("首档必须为 1.00（优先给最大预算）", 1.0, steps[0], 0.0);
        assertEquals("末档必须为 0（关闭预算 = 退回既有口径）", 0.0, steps[steps.length - 1], 0.0);
        for (int i = 1; i < steps.length; i++) {
            assertTrue("阶梯必须严格递减（" + steps[i - 1] + " -> " + steps[i] + "）",
                    steps[i] < steps[i - 1]);
        }
        assertTrue("全局预算令牌必须为正（否则本机制空转）",
                PickerDensityTokens.LABEL_BUDGET_EM > 0.0);
    }

    /**
     * 字号真值同源：生效字号 = 面板声明字号 × 用户倍率（与 {@code SceneNode.effectiveFontSize()} 同式）。
     *
     * <p>钉住"派生链消费的字号就是渲染链画出来的字号"：宿主显式声明多少，面板就派多少；
     * 未声明时由 {@link PickerMetrics#defaultPanelDeclaredFontPx()} 给默认值。</p>
     */
    @Test
    public void fontSizeFollowsDeclaredValueAndScaleOnly() {
        assertEquals("声明 12 × 100% = 12", 12, PickerMetrics.fontSizeFor(12, 100));
        assertEquals("声明 12 × 125% = 15", 15, PickerMetrics.fontSizeFor(12, 125));
        assertEquals("声明 12 × 150% = 18", 18, PickerMetrics.fontSizeFor(12, 150));
        assertEquals("宿主声明 20 时面板跟随 20（不是档位基准 12）",
                20, PickerMetrics.fontSizeFor(20, 100));
        assertEquals("声明 6 被归一到字号下限 11", 11, PickerMetrics.fontSizeFor(6, 100));
        assertEquals("默认面板声明字号 = 标准档基准", PickerDensity.STANDARD.baseFontPx(),
                PickerMetrics.defaultPanelDeclaredFontPx());
        assertEquals("面板声明归一到域上限：30 → 24", PickerDensityTokens.FONT_CEIL,
                PickerMetrics.clampPanelDeclaredFontPx(30));
        assertEquals("面板声明归一到域下限：6 → 11", PickerDensityTokens.FONT_FLOOR,
                PickerMetrics.clampPanelDeclaredFontPx(6));
    }

    /**
     * 字体真值同源 oracle：{@code fontSizeFor} 在<b>字号域内</b>必须与渲染出口逐值相等。
     *
     * <p>渲染出口 = {@code SceneNode.effectiveFontSize()} 的
     * {@code FontSizeLimits.clampFontSize(Math.round(declared * pct/100f))}；派生侧若改取整模式
     * （{@code Math.rint} 与 {@code Math.round} 在半值平局上不同）或改域，就会出现"文字按 17 画、
     * 几何按 16 算"的分叉。域外（渲染值 &lt; FONT_FLOOR 或 &gt; FONT_CEIL）由本控件域夹取，
     * 断言的是夹取结果本身。</p>
     */
    @Test
    public void fontSizeForMatchesRendererExitInsideFontDomain() {
        int inside = 0;
        for (int declared = 1; declared <= 40; declared++) {
            for (int pct = 100; pct <= 200; pct += 5) {
                int rendered = FontSizeLimits.clampFontSize(Math.round(declared * (pct / 100f)));
                int derived = PickerMetrics.fontSizeFor(declared, pct);
                String tag = "declared=" + declared + " pct=" + pct;
                if (rendered < PickerDensityTokens.FONT_FLOOR) {
                    assertEquals(tag + " 渲染值低于控件域下限 ⇒ 夹到下限",
                            PickerDensityTokens.FONT_FLOOR, derived);
                } else if (rendered > PickerDensityTokens.FONT_CEIL) {
                    assertEquals(tag + " 渲染值高于控件域上限 ⇒ 夹到上限",
                            PickerDensityTokens.FONT_CEIL, derived);
                } else {
                    assertEquals(tag + " 域内必须与渲染出口逐值相等", rendered, derived);
                    inside++;
                }
            }
        }
        assertTrue("域内同源样本必须足够多（否则守卫空转）", inside > 100);
    }

    // ==================== A8 小盒降级 ====================

    @Test
    public void a8SmallBoxDegradesAndStillDominates() {
        PickerMetrics box960 = PickerMetrics.solve(rt, 960, 540, 12, PickerDensityPreference.AUTO, 2);
        // 小盒的"支配现状"基线为 0（现状几何在小盒下不成立，见 legacyBaselineItems），故标签预算
        // 可以在小盒里放开用：可见项 102 -> 42 是格子变宽的代价，换取标签槽 90px（约 6 个全角字）。
        assertEquals("960x540 小盒可见项", 42, box960.visibleItems());
        PickerMetrics box640 = PickerMetrics.solve(rt, 640, 360, 12, PickerDensityPreference.AUTO, 2);
        assertEquals("640x360 小盒可见项", 12, box640.visibleItems());
        for (PickerMetrics m : new PickerMetrics[] {box960, box640}) {
            assertTrue("小盒必须标记降级态", m.panel().smallBox());
            assertEquals("小盒面板比例 = 100%", 100, m.panel().ratioPercent());
            assertEquals("小盒强制紧凑档", PickerDensity.COMPACT, m.density());
            assertEquals("小盒信息条不占位", 0, m.panel().infoBarHeightPx());
            assertTrue("小盒成员带折叠为提示行", m.panel().membersCollapsed());
            assertTrue("小盒仍支配现状（现状 = 0）",
                    m.visibleItems() >= PickerMetrics.legacyBaselineItems(
                            m.logicalWidthPx(), m.logicalHeightPx()));
        }
    }

    @Test
    public void a8BoundaryIsInclusiveAt1280x720() {
        assertFalse("1280x720 不算小盒", PickerMetrics.isSmallBox(1280, 720));
        assertTrue("1279x720 属小盒", PickerMetrics.isSmallBox(1279, 720));
        assertTrue("1280x719 属小盒", PickerMetrics.isSmallBox(1280, 719));
    }

    // ==================== 三分量动态性（R3） ====================

    @Test
    public void fontScalePercentDrivesEveryGeometryComponent() {
        PickerMetrics base = PickerMetrics.derive(rt, 1920, 1080,
                PickerMetrics.fontSizeFor(PickerMetrics.defaultPanelDeclaredFontPx(), 100),
                PickerDensityPreference.AUTO, 2);
        PickerMetrics scaled = PickerMetrics.derive(rt, 1920, 1080,
                PickerMetrics.fontSizeFor(PickerMetrics.defaultPanelDeclaredFontPx(), 150),
                PickerDensityPreference.AUTO, 2);
        assertEquals("100% -> fs 12", 12, base.fontSizePx());
        assertEquals("150% -> fs 18", 18, scaled.fontSizePx());
        assertTrue("字号放大后 stride 单调增（A-11）",
                scaled.grid().stridePx() > base.grid().stridePx());
        assertTrue("字号放大后轨道高单调增（A-11）",
                scaled.grid().trackHeightPx() > base.grid().trackHeightPx());
        // A5 的准确口径：字号不参与图标挤压（图标 = 档位目标 × k），但 auto 阶梯可以下调 k。
        // 因此约束是「图标 ∈ [最小档目标, 档位目标]」且 k 只能在阶梯取值。
        for (PickerMetrics m : new PickerMetrics[] {base, scaled}) {
            assertTrue("图标不得低于 k 阶梯下限（图标 = 档位目标 × k，k>=0.80；A5）"
                            + " density=" + m.density() + " k=" + m.iconScalePercent()
                            + " icon=" + m.grid().iconSidePx(),
                    m.grid().iconSidePx()
                            >= GridMetrics.roundHalfEven(m.density().iconSidePx() * 0.80));
            assertTrue("图标不得大于档位目标（A5）",
                    m.grid().iconSidePx() <= m.density().iconSidePx());
            assertTrue("k 必须取自阶梯",
                    m.iconScalePercent() == 100 || m.iconScalePercent() == 95
                            || m.iconScalePercent() == 90 || m.iconScalePercent() == 85
                            || m.iconScalePercent() == 80);
        }
        assertTrue("字号放大仍支配现状", scaled.visibleItems()
                >= PickerMetrics.legacyBaselineItems(1920, 1080));
    }

    @Test
    public void logicalBoxIsTheOnlySizeInputAndPrefersBiggerPanelWhenNeeded() {
        // 同一字号下逻辑盒变小 -> 面板比例单调不减（阶梯生效），且永不越出逻辑盒。
        int previousRatio = 0;
        for (int[] viewport : VIEWPORTS) {
            PickerMetrics m = PickerMetrics.solve(rt, viewport[0], viewport[1], 18,
                    PickerDensityPreference.AUTO, 2);
            assertTrue("面板宽不得越出逻辑盒（" + viewport[0] + "）",
                    m.panel().widthPx() <= viewport[0] - 2 * PickerDensityTokens.PANEL_MARGIN);
            assertTrue("面板高不得越出逻辑盒（" + viewport[1] + "）",
                    m.panel().heightPx() <= viewport[1] - 2 * PickerDensityTokens.PANEL_MARGIN);
            previousRatio = m.panel().ratioPercent();
            assertTrue("比例必须取自阶梯（含小盒 100%）",
                    previousRatio == 100 || previousRatio == 70 || previousRatio == 78
                            || previousRatio == 84 || previousRatio == 92);
        }
    }

    @Test
    public void memberRowCountDrivesMembersBandAndNeverStealsListHeight() {
        PickerMetrics noMembers = PickerMetrics.solve(rt, 1920, 1080, 12,
                PickerDensityPreference.AUTO, -1);
        PickerMetrics collapsed = PickerMetrics.solve(rt, 1920, 1080, 12,
                PickerDensityPreference.AUTO, 0);
        PickerMetrics twoRows = PickerMetrics.solve(rt, 1920, 1080, 12,
                PickerDensityPreference.AUTO, 2);
        assertEquals("无成员带 -> 成员区高 0", 0, noMembers.panel().membersHeightPx());
        assertTrue("折叠态成员区高 < 两行态", collapsed.panel().membersHeightPx()
                < twoRows.panel().membersHeightPx());
        assertTrue("折叠态结果区高于两行态（折叠优先，不挤掉结果区）",
                collapsed.panel().listHeightPx() > twoRows.panel().listHeightPx());
        assertTrue("折叠态标记 collapsed", collapsed.panel().membersCollapsed());
        assertFalse("两行态不标记 collapsed", twoRows.panel().membersCollapsed());
        for (PickerMetrics m : new PickerMetrics[] {noMembers, collapsed, twoRows}) {
            assertTrue("任何成员形态都必须 >= 现状 75", m.visibleItems() >= 75);
        }
    }

    @Test
    public void densityPreferenceChangeRecomputesWithoutRebuild() {
        PickerMetrics compact = PickerMetrics.solve(rt, 1920, 1080, 12,
                PickerDensityPreference.COMPACT, 2);
        PickerMetrics roomy = PickerMetrics.solve(rt, 1920, 1080, 12,
                PickerDensityPreference.ROOMY, 2);
        // 档位语义 = 图标密度（标签预算已统一到 LABEL_BUDGET_EM，不再随档位变）：紧凑档图标最小、
        // 宽松档图标最大即为可区分；可见项数在 1080p 下三档相同（同一 cellW ⇒ 同列数），
        // 故"可见量递减"不再是档位的判据，改为断言"两档各自仍支配现状"。
        assertEquals("紧凑档图标 32", 32, compact.grid().iconSidePx());
        assertTrue("宽松档图标必须大于紧凑档（实际 " + roomy.grid().iconSidePx() + "）",
                roomy.grid().iconSidePx() > compact.grid().iconSidePx());
        int baseline1080 = PickerMetrics.legacyBaselineItems(1920, 1080);
        assertTrue("紧凑档仍支配现状", compact.visibleItems() >= baseline1080);
        assertTrue("宽松档仍支配现状", roomy.visibleItems() >= baseline1080);
        assertEquals("紧凑档字号基准 11", 11,
                PickerMetrics.fontSizeFor(PickerDensity.COMPACT.baseFontPx(), 100));
        assertEquals("宽松档字号基准 13", 13,
                PickerMetrics.fontSizeFor(PickerDensity.ROOMY.baseFontPx(), 100));
    }

    @Test
    public void invalidInputsDegradeInsteadOfThrowing() {
        PickerMetrics zero = PickerMetrics.solve(rt, 0, 0, 0, null, 2);
        assertNotNull(zero);
        assertTrue("退化输入必须给出至少 1 列的合法快照", zero.grid().columns() >= 1);
        assertTrue("退化输入不得抛异常且可视行为 >= 0", zero.visibleItems() >= 0);
        assertEquals("null 偏好回落 AUTO", PickerDensityPreference.AUTO, zero.preference());
    }

    // ==================== 三分量铁律源码守卫（R3） ====================

    @Test
    public void pickerSizeChainNeverTouchesGuiScale() throws IOException {
        String[] files = {
                "src/main/java/club/heiqi/uilib/ui/scene/control/search/PickerMetrics.java",
                "src/main/java/club/heiqi/uilib/ui/scene/control/search/PickerDensityTokens.java",
                "src/main/java/club/heiqi/uilib/ui/scene/control/search/PickerDensity.java",
                "src/main/java/club/heiqi/uilib/ui/scene/control/search/GridMetrics.java",
        };
        String[] forbidden = {"guiScale", "gameSettings", "displayWidth", "displayHeight",
                "ScaledResolution", "scaleFactor"};
        StringBuilder hits = new StringBuilder();
        for (String file : files) {
            String code = stripComments(read(file));
            for (String needle : forbidden) {
                if (code.contains(needle)) {
                    hits.append(' ').append(file).append("->").append(needle);
                }
            }
        }
        assertEquals("派生链不得接触 GUI Scale / 物理分辨率（AGENTS.md:28 铁律）：" + hits,
                0, hits.length());
        // 正锚：文件确实读到了派生链本体，杜绝"读空文件"的真空真。
        String metrics = stripComments(read(
                "src/main/java/club/heiqi/uilib/ui/scene/control/search/PickerMetrics.java"));
        assertTrue("正锚：PickerMetrics 含 auto 阶梯", metrics.contains("ICON_SCALE_STEPS"));
        assertTrue("正锚：PickerMetrics 含支配性目标", metrics.contains("legacyBaselineItems"));
    }

    private static String read(String relative) throws IOException {
        Path path = Paths.get(relative);
        assertTrue("源码文件必须存在于工作目录：" + relative, Files.exists(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 去掉行注释与块注释后再扫描，避免注释里的说明性用词造成误报。 */
    private static String stripComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') i++;
            } else if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) i++;
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * 规格脚本度量替身：{@code lineHeight(fs) = round(fs*1.25)}、4 字符样本宽 {@code round(fs*3.2)}，
     * <b>外加全角（CJK）宽度模型</b>。
     *
     * <p>前两个式子是 {@code temp/p5_density_spec.py} 的验算模型（脚本自述"仅验算用；
     * 实现取 rt.lineHeight(fs)"）。用同一模型才能与规格表的数字逐值对拍；
     * 真实字体的度量由生产 {@link club.heiqi.uilib.ui.scene.TextMeasureServiceSceneAdapter} 提供。</p>
     *
     * <p><b>为什么必须补 CJK 宽度</b>：原实现按 {@code length} 一刀切（{@code len>=4 ⇒ fs*3.2}），
     * 与字符内容无关 —— 那样"标签可读宽度预算按全角字数表达"的任何 bug 在 Java 侧都不可观测
     * （把样本换成中文而长度不变时测试全绿、真机 cellW 翻倍）。CJK 码点按 {@code 1.0*fs}
     * （全角约等于字号）、其余按 {@code 0.5*fs} 计，使"槽里放得下几个汉字"成为可断言事实。</p>
     */
    private static final class SpecMeasurer implements SceneTextMeasurer {
        @Override
        public int measureWidth(String text, int fontSizePx) {
            int len = text == null ? 0 : text.length();
            int wide = 0;
            for (int i = 0; i < len; i++) {
                if (text.charAt(i) > 0x2E80) {
                    wide++;
                }
            }
            if (wide > 0) {
                return GridMetrics.roundHalfEven(fontSizePx * (wide + (len - wide) * 0.5));
            }
            if (len >= 4) {
                return GridMetrics.roundHalfEven(fontSizePx * 3.2);
            }
            return len * 8;
        }

        @Override
        public int lineHeight(int fontSizePx) {
            return Math.max(1, GridMetrics.roundHalfEven(fontSizePx * 1.25));
        }

        @Override
        public int ascent(int fontSizePx) {
            return Math.max(1, fontSizePx * 3 / 4);
        }

        @Override
        public int descent(int fontSizePx) {
            return Math.max(1, fontSizePx / 4);
        }

        @Override
        public int lineGap(int fontSizePx) {
            return 0;
        }

        @Override
        public int epoch() {
            return 0;
        }
    }
}
