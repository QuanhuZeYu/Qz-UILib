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
 *   <li>A2/A3 P5 1080p fs=12/15/18 ≥ 75（实测 120/80/95）；</li>
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
        assertEquals("fs=12 auto 可见项（规格 §1.5.2 = 120）", 120, fs12.visibleItems());
        PickerMetrics fs15 = PickerMetrics.solve(rt, 1920, 1080, 15, PickerDensityPreference.AUTO, 2);
        assertEquals("fs=15(125%) auto 可见项（规格 §1.5.2 = 80）", 80, fs15.visibleItems());
        PickerMetrics fs18 = PickerMetrics.solve(rt, 1920, 1080, 18, PickerDensityPreference.AUTO, 2);
        assertEquals("fs=18(150%) auto 可见项（规格 §1.5.2 = 95）", 95, fs18.visibleItems());
        for (PickerMetrics m : new PickerMetrics[] {fs12, fs15, fs18}) {
            assertTrue("A2/A3 1080p 可见项必须 >= 现状 75，实际 " + m.visibleItems(),
                    m.visibleItems() >= 75);
        }
    }

    @Test
    public void a2AutoAt1080pMatchesSpecDetail() {
        PickerMetrics m = PickerMetrics.solve(rt, 1920, 1080, 12, PickerDensityPreference.AUTO, 2);
        assertEquals("面板宽 1344", 1344, m.panel().widthPx());
        assertEquals("面板高 756", 756, m.panel().heightPx());
        assertEquals("结果区宽 1116", 1116, m.panel().listWidthPx());
        assertEquals("结果区高 420", 420, m.panel().listHeightPx());
        assertEquals("顶栏高 44", 44, m.panel().headerHeightPx());
        assertEquals("导航宽 188", 188, m.panel().navWidthPx());
        assertEquals("生效比例 70%", 70, m.panel().ratioPercent());
        assertEquals("列数 20", 20, m.grid().columns());
        assertEquals("可视行数 6", 6, m.visibleRows());
        assertEquals("cellW 48", 48, m.grid().cellWidthPx());
        assertEquals("trackH 65", 65, m.grid().trackHeightPx());
        assertEquals("stride 71", 71, m.grid().stridePx());
        assertEquals("icon 40", 40, m.grid().iconSidePx());
        assertEquals("gap 6", 6, m.grid().gapY());
        assertEquals("k=1.00", 100, m.iconScalePercent());
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
        // 规格 §1.5.2「最薄裕度 +4」：1366x768 / 字号 150% / 现状 20 -> P5 24。
        int baseline = PickerMetrics.legacyBaselineItems(1366, 768);
        PickerMetrics m = PickerMetrics.solve(rt, 1366, 768, 18, PickerDensityPreference.AUTO, 2);
        assertEquals(20, baseline);
        assertEquals(24, m.visibleItems());
        assertEquals("最薄裕度 +4", 4, m.visibleItems() - baseline);
    }

    // ==================== A5 图标与不变量 ====================

    @Test
    public void a5IconStaysSquareAndNeverShrinksWithFontSize() {
        for (int fs : new int[] {11, 12, 15, 18, 21, 24}) {
            GridMetrics grid = GridMetrics.deriveDensity(rt, fs, 40, 100, 0, 1116);
            assertEquals("A5 fs=" + fs + " 图标恒为目标边长", 40, grid.iconSidePx());
            int expectedTrack = grid.iconSidePx() + 2 * grid.paddingPx()
                    + grid.lineHeightPx() + grid.labelGapPx();
            assertEquals("I-2 轨道高 = 图标 + 上下 padding + 标签行 + 间距 (fs=" + fs + ")",
                    expectedTrack, grid.trackHeightPx());
            assertTrue("I-3 cellW >= 图标 + 上下 padding (fs=" + fs + ")",
                    grid.cellWidthPx() >= grid.iconSidePx() + 2 * grid.paddingPx());
            assertEquals("I-4 stride = trackH + gap (fs=" + fs + ")",
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
        assertTier(1280, 720, 24, 30, 22);
        assertTier(1920, 1080, 120, 144, 90);
        assertTier(3840, 2160, 720, 954, 546);
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
                int fs = PickerMetrics.fontSizeFor(100, tier.explicitDensity(PickerDensity.STANDARD));
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
        // 档位基准字号：紧凑 11 / 标准 12 / 宽松 13（P5 §1.3）。
        assertEquals(11, PickerMetrics.fontSizeFor(100, PickerDensity.COMPACT));
        assertEquals(12, PickerMetrics.fontSizeFor(100, PickerDensity.STANDARD));
        assertEquals(13, PickerMetrics.fontSizeFor(100, PickerDensity.ROOMY));
        assertEquals("字号 125% 标准档 = 15", 15, PickerMetrics.fontSizeFor(125, PickerDensity.STANDARD));
        assertEquals("字号 150% 标准档 = 18", 18, PickerMetrics.fontSizeFor(150, PickerDensity.STANDARD));
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
        // 5% 安全裕度：达标解必须 >= ceil(hard*1.05)（不可达时退回第一个满足 hard 的解）。
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

    // ==================== A8 小盒降级 ====================

    @Test
    public void a8SmallBoxDegradesAndStillDominates() {
        PickerMetrics box960 = PickerMetrics.solve(rt, 960, 540, 12, PickerDensityPreference.AUTO, 2);
        assertEquals("960x540 小盒可见项（规格 §1.5.3 = 102）", 102, box960.visibleItems());
        PickerMetrics box640 = PickerMetrics.solve(rt, 640, 360, 12, PickerDensityPreference.AUTO, 2);
        assertEquals("640x360 小盒可见项（规格 §1.5.3 = 30）", 30, box640.visibleItems());
        for (PickerMetrics m : new PickerMetrics[] {box960, box640}) {
            assertTrue("小盒必须标记降级态", m.panel().smallBox());
            assertEquals("小盒面板比例 = 100%", 100, m.panel().ratioPercent());
            assertEquals("小盒强制紧凑档", PickerDensity.COMPACT, m.density());
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
        PickerMetrics base = PickerMetrics.derive(rt, 1920, 1080, 100,
                PickerDensityPreference.AUTO, 2);
        PickerMetrics scaled = PickerMetrics.derive(rt, 1920, 1080, 150,
                PickerDensityPreference.AUTO, 2);
        assertEquals("100% -> fs 12", 12, base.fontSizePx());
        assertEquals("150% -> fs 18", 18, scaled.fontSizePx());
        assertTrue("字号放大后 stride 单调增（A-11）",
                scaled.grid().stridePx() > base.grid().stridePx());
        assertTrue("字号放大后轨道高单调增（A-11 / I-2）",
                scaled.grid().trackHeightPx() > base.grid().trackHeightPx());
        // A5 的准确口径：字号不参与图标挤压（图标 = 档位目标 × k），但 auto 阶梯可以下调 k。
        // 因此约束是「图标 ∈ [最小档目标, 档位目标]」且 k 只能在阶梯取值。
        for (PickerMetrics m : new PickerMetrics[] {base, scaled}) {
            assertTrue("图标不得小于最小档目标 32（A5）", m.grid().iconSidePx()
                    >= PickerDensity.COMPACT.iconSidePx());
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
        assertEquals("紧凑档图标 32", 32, compact.grid().iconSidePx());
        assertEquals("宽松档图标 48", 48, roomy.grid().iconSidePx());
        assertTrue("紧凑档可见量 > 宽松档", compact.visibleItems() > roomy.visibleItems());
        assertEquals("紧凑档字号基准 11", 11, PickerMetrics
                .derive(rt, 1920, 1080, 100, PickerDensityPreference.COMPACT, 2).fontSizePx());
        assertEquals("宽松档字号基准 13", 13, PickerMetrics
                .derive(rt, 1920, 1080, 100, PickerDensityPreference.ROOMY, 2).fontSizePx());
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
     * 规格脚本度量替身：{@code lineHeight(fs) = round(fs*1.25)}、4 字符样本宽 {@code round(fs*3.2)}。
     *
     * <p>这两个式子是 {@code temp/p5_density_spec.py} 的验算模型（脚本自述"仅验算用；
     * 实现取 rt.lineHeight(fs)"）。用同一模型才能与规格表的数字逐值对拍；
     * 真实字体的度量由生产 {@link club.heiqi.uilib.ui.scene.TextMeasureServiceSceneAdapter} 提供。</p>
     */
    private static final class SpecMeasurer implements SceneTextMeasurer {
        @Override
        public int measureWidth(String text, int fontSizePx) {
            int len = text == null ? 0 : text.length();
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
