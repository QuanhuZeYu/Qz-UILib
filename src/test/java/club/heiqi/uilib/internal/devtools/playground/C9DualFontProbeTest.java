package club.heiqi.uilib.internal.devtools.playground;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontConfig;

/**
 * C9 双字体模拟探针（生产度量面）：把 {@code FontConfig.fontSort} 扰动为指定物理字体后，
 * 重跑 {@link PlaygroundPageRegistryTest} 的三条 markdown 页像素锁与列表列锁——证明
 * C9·4 的「同 JVM 独立量出」判据在第二套度量下同样为真（平台鲁棒化自证，规划 §二之八 C9）。
 *
 * <p><b>为什么 @Ignore 常驻而非删除</b>：扰动只在 FontService/DefaultTextMeasureService
 * <b>首次初始化之前</b>设置才生效（settings 在 capture 时快照 fontSort），与常规套件同 JVM
 * 共跑时初始化时序不可控、且会污染其它真度量测试——故本类默认跳过，作自证入口保留：
 * 手动 {@code gradlew cleanTest :test --tests '*C9DualFontProbeTest*' --offline}（单独
 * fork 的 test JVM 内独占执行）。kit 侧（MarkdownListContinuationLockTest 等）的模拟走
 * 另一条钩子：{@code $env:QZ_C9_TEST_FONT='Verdana'} 后按类过滤重跑（默认不设变量时
 * 行为与改动前逐字相同）。</p>
 *
 * <p>最近一次执行结果登记于规划 §二之八 C9 细账（Windows 主机，扰动字体 Verdana——
 * 其「• 」@16 列宽 17 恰与 Linux CI DejaVu 实测同值，是有效的第二度量样本）。</p>
 */
@Ignore("C9 手动双字体自证入口：扰动只在 FontService 首初始化前生效，禁与常规套件同 JVM 共跑；"
        + "单独 --tests '*C9DualFontProbeTest*' 执行。最近结果（2026-09-07，Verdana：adv(•)@14 "
        + "7.791≠Dialog 5.844，列宽 15 与 Linux CI 同值）：三条 markdown 页锁全 PASS，登记规划 §二之八 C9。")
public class C9DualFontProbeTest {

    /** 扰动目标字体（Windows 必装；换环境时改这里即可）。 */
    private static final String PROBE_FONT = "Verdana";

    private String[] savedFontSort;
    private boolean savedConfigured;

    @Before
    public void perturbFontSort() {
        savedFontSort = FontConfig.getFontSortSnapshot();
        savedConfigured = FontConfig.fontSortConfigured;
        FontConfig.fontSort = new String[] {PROBE_FONT};
        FontConfig.fontSortConfigured = true;
    }

    @After
    public void restoreFontSort() {
        FontConfig.fontSort = savedFontSort;
        FontConfig.fontSortConfigured = savedConfigured;
    }

    /**
     * 扰动生效自检：以「•」推进宽为准（列表正文列的唯一构成因子）。Verdana 自带
     * U+2022（@14≈7.79），Dialog→Microsoft Sans Serif（@14≈5.85）——两者必居其一，
     * 与 Dialog 读数差 &gt;0.5 才算扰动真的进了度量路径。CJK 不参与本判据：Verdana 无
     * CJK 覆盖时 matcher 回退到同一无衬线 CJK 字体，「甲」宽不变是预期行为（本探针
     * 模拟的正是 Linux 那类「拉丁/符号度量变、CJK 覆盖情况变」的扰动面）。
     */
    @Test
    public void probeFontActuallyActive() {
        PlaygroundPageRegistryTest t = new PlaygroundPageRegistryTest();
        t.setUp();
        try {
            double bullet = club.heiqi.uilib.font.FontService.getInstance().getTextLayoutService()
                    .resolveAdvance(0x2022, new club.heiqi.uilib.font.layout.TextStyle(), 14);
            System.out.println("C9PROBE adv(bullet)@14 under " + PROBE_FONT + " = " + bullet);
            // Dialog 基线读数 5.844（temp 探针 2026-09-07 登记）；偏离 >0.5 即扰动生效
            Assert.assertTrue("扰动未生效（adv(•)@14=" + bullet + " ≈ Dialog 基线 5.844）",
                    Math.abs(bullet - 5.8444444D) > 0.5D);
        } finally {
            t.tearDown();
        }
    }

    /** 列表正文列像素锁（#8 本尊）：第二套度量下相对判据必须同样成立。 */
    @Test
    public void listContinuationUnderProbeFont() {
        runTarget("markdownPageListContinuationAlignsToContentColumn");
    }

    /** 围栏底色连续块像素锁：行高/带宽派生地板在第二套度量下必须同样成立。 */
    @Test
    public void codeBackdropUnderProbeFont() {
        runTarget("markdownPageCodeBackdropIsOneContinuousPixelBlock");
    }

    /** 引用竖条连续列像素锁：barWidth×行高×层数派生地板在第二套度量下必须同样成立。 */
    @Test
    public void quoteBarUnderProbeFont() {
        runTarget("markdownPageQuoteBarIsOneContinuousColumn");
    }

    private void runTarget(String methodName) {
        PlaygroundPageRegistryTest t = new PlaygroundPageRegistryTest();
        t.setUp();
        try {
            if ("markdownPageListContinuationAlignsToContentColumn".equals(methodName)) {
                t.markdownPageListContinuationAlignsToContentColumn();
            } else if ("markdownPageCodeBackdropIsOneContinuousPixelBlock".equals(methodName)) {
                t.markdownPageCodeBackdropIsOneContinuousPixelBlock();
            } else {
                t.markdownPageQuoteBarIsOneContinuousColumn();
            }
            System.out.println("C9PROBE " + methodName + " under " + PROBE_FONT + " = PASS");
        } catch (RuntimeException e) {
            Assert.fail(methodName + " 在扰动字体 " + PROBE_FONT + " 下失败：" + e);
        } finally {
            t.tearDown();
        }
    }
}
