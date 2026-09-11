package club.heiqi.uilib.ui.diagnostic;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import club.heiqi.uilib.Config;

/**
 * {@link UiPerformanceMonitor} 守卫测试：采样开关、配对、重入、有界性与按界面分组历史。
 *
 * <h3>为什么必须钉住这些不变量</h3>
 * <ul>
 *   <li><b>debug=false 零成本</b>：采样器位于每帧热路径，若关闭时仍建会话/建表，
 *       等于给全部 UI 宿主加了常驻开销。用「统计快照仍是 {@link UiRuntimeStats#empty()}
 *       同一实例」作为"未建会话"的可判定证据。</li>
 *   <li><b>配对与异常安全</b>：宿主必须以 {@code finally} 调 {@code finishFrame}；
 *       本测试覆盖异常路径与「只 finish 不 begin」两种边界。</li>
 *   <li><b>重入不重复计数</b>：屏幕宿主与 HUD 是同帧内两条帧入口，聊天输入面还可能被
 *       HUD 间接触发 → 嵌套时只有最外层拥有本帧。</li>
 *   <li><b>按界面分组的帧历史不得被交替调用清空</b>（历史缺陷：切换 screen 即 clear，
 *       导致均值/最大值/慢帧计数永远只反映最后一帧）。</li>
 *   <li><b>有界</b>：界面分组、单帧阶段表、单帧计数表、帧外折叠桶四者都有上限。</li>
 * </ul>
 *
 * <p>本类操作单例的全局状态，故每个用例使用<b>专属界面名</b>并在前后复位采样开关；
 * 用例之间不依赖执行顺序（分组上限为 8，而任一新用例至多同时持有 2 个分组）。</p>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UiPerformanceMonitorTest {

    private static final String SCREEN_ON = "guard-on";
    private static final String SCREEN_A = "guard-A";
    private static final String SCREEN_B = "guard-B";

    private boolean originalUseDebug;

    private static UiPerformanceMonitor monitor() {
        return UiPerformanceMonitor.getInstance();
    }

    @Before
    public void setUp() {
        originalUseDebug = Config.useDebug;
        monitor().resetHistory(SCREEN_ON);
        monitor().resetHistory(SCREEN_A);
        monitor().resetHistory(SCREEN_B);
        monitor().resetHistory("");
    }

    @After
    public void tearDown() {
        // 不留下活跃会话，也不把开关状态泄漏给其它测试
        monitor().finishFrame();
        Config.useDebug = originalUseDebug;
        monitor().resetHistory(SCREEN_ON);
        monitor().resetHistory(SCREEN_A);
        monitor().resetHistory(SCREEN_B);
        monitor().resetHistory("");
    }

    /** debug=false：不得创建会话、不得产出计数（快照恒为 empty 单例）。 */
    @Test
    public void a1_debugOffCreatesNoSession() {
        Config.useDebug = false;
        monitor().beginFrame(SCREEN_ON, 100, 200, 400, 800);
        monitor().recordPhase(UiPerfMarkers.PHASE_PICKER_GRID_TRANSFORM, 5_000_000L);
        monitor().recordCounter(UiPerfMarkers.COUNTER_PICKER_CANDIDATES, 4988L);
        monitor().finishFrame();

        Assert.assertSame("debug=false 时不得创建 FrameSession（快照必须仍是 empty 单例）",
                UiRuntimeStats.empty(), monitor().getRuntimeStats());
        Assert.assertEquals("计数摘要必须为空", "", monitor().getRuntimeStats().getCounterSummary());
    }

    /** debug=true：一帧正常结算，阶段与计数都出现在快照里。 */
    @Test
    public void a2_debugOnReportsFramePhaseAndCounter() {
        Config.useDebug = true;
        monitor().beginFrame(SCREEN_ON, 100, 200, 400, 800);
        monitor().recordPhase(UiPerfMarkers.PHASE_PICKER_GRID_TRANSFORM, 5_000_000L);
        monitor().recordCounter(UiPerfMarkers.COUNTER_PICKER_CANDIDATES, 4988L);
        monitor().recordCounter(UiPerfMarkers.COUNTER_PICKER_CANDIDATES, 12L);
        monitor().finishFrame();

        UiRuntimeStats stats = monitor().getRuntimeStats();
        Assert.assertEquals(SCREEN_ON, stats.getScreenName());
        Assert.assertEquals(1, stats.getSampledFrameCount());
        Assert.assertEquals(400, stats.getNativeWidth());
        Assert.assertEquals(800, stats.getNativeHeight());
        Assert.assertTrue("阶段摘要应含网格派生阶段", stats.getPhaseSummary().contains(UiPerfMarkers.PHASE_PICKER_GRID_TRANSFORM));
        Assert.assertTrue("计数摘要应含候选总数（累加 4988+12=5000，单次峰值 4988）",
                stats.getCounterSummary().contains(UiPerfMarkers.COUNTER_PICKER_CANDIDATES + "=5000/4988x2"));
    }

    /** 只 finish 不 begin：安全返回，不得产生任何副作用。 */
    @Test
    public void a3_finishWithoutBeginIsSafe() {
        Config.useDebug = true;
        monitor().finishFrame();
        monitor().finishFrame();
        Assert.assertSame(UiRuntimeStats.empty(), monitor().getRuntimeStats());
    }

    /** 重复 begin：只有最外层 finish 结算；嵌套层既不新建会话也不提前结算。 */
    @Test
    public void a4_repeatedBeginOnlyOutermostFinishes() {
        Config.useDebug = true;
        monitor().beginFrame(SCREEN_ON, 10, 10, 10, 10);
        monitor().beginFrame(SCREEN_ON, 10, 10, 10, 10);
        monitor().beginFrame(SCREEN_ON, 10, 10, 10, 10);
        monitor().finishFrame();
        monitor().finishFrame();
        Assert.assertSame("内层 finish 不得结算（会话仍归最外层）",
                UiRuntimeStats.empty(), monitor().getRuntimeStats());

        monitor().finishFrame();
        Assert.assertEquals("最外层 finish 才结算一帧", 1, monitor().getRuntimeStats().getSampledFrameCount());
    }

    /** 异常路径：调用方 finally 里的 finish 必须完成结算，且嵌套深度正确回收。 */
    @Test
    public void a5_exceptionPathStillFinishes() {
        Config.useDebug = true;
        try {
            monitor().beginFrame(SCREEN_ON, 10, 10, 10, 10);
            monitor().beginFrame(SCREEN_ON, 10, 10, 10, 10);
            try {
                throw new IllegalStateException("simulated render failure");
            } finally {
                monitor().finishFrame();
            }
        } catch (IllegalStateException expected) {
            // 预期
        } finally {
            monitor().finishFrame();
        }

        Assert.assertEquals("异常路径同样必须结算", 1, monitor().getRuntimeStats().getSampledFrameCount());
        // 深度必须回归 0：后续普通帧应能再次正常结算（若深度残留，本帧不会产生快照）
        frame(SCREEN_ON);
        Assert.assertEquals("重入深度必须回归 0", 2, monitor().getRuntimeStats().getSampledFrameCount());
    }

    /**
     * 核心回归：A→B→A 交替调用后，两个界面的历史各自独立且<b>不被清空</b>。
     *
     * <p>旧实现每次 begin 遇到新界面名就清空全局历史，导致第二次进入 A 时采样帧数回到 1。</p>
     */
    @Test
    public void a6_perScreenHistorySurvivesAlternatingScreens() {
        Config.useDebug = true;
        for (int round = 0; round < 3; round++) {
            frame(SCREEN_A);
            frame(SCREEN_B);
        }
        UiRuntimeStats statsB = monitor().getRuntimeStats();
        Assert.assertEquals(SCREEN_B, statsB.getScreenName());
        Assert.assertEquals("B 的三帧必须累计", 3, statsB.getSampledFrameCount());

        frame(SCREEN_A);
        UiRuntimeStats statsA = monitor().getRuntimeStats();
        Assert.assertEquals(SCREEN_A, statsA.getScreenName());
        Assert.assertEquals("A 的历史不得被 B 的帧清空（旧实现会归 1）", 4, statsA.getSampledFrameCount());
        Assert.assertTrue("平均帧时间应为正值", statsA.getAverageFrameTimeNanos() > 0L);
    }

    /** 界面分组历史有界：超出上限按 LRU 淘汰最久未使用者。 */
    @Test
    public void a7_screenHistoryIsBoundedWithLruEviction() {
        Config.useDebug = true;
        int overflow = UiPerformanceMonitor.MAX_SCREEN_HISTORIES + 3;
        for (int index = 1; index <= overflow; index++) {
            frame("guard-bound-" + index);
        }
        // 最早的 guard-bound-1 已被淘汰：重新进入只有 1 帧
        frame("guard-bound-1");
        Assert.assertEquals("最久未使用的界面历史必须被淘汰（缓存有界）",
                1, monitor().getRuntimeStats().getSampledFrameCount());
        // 最新进入的 guard-bound-11 仍在：第二次进入应累计 2 帧
        frame("guard-bound-" + overflow);
        frame("guard-bound-" + overflow);
        Assert.assertEquals("近期使用的界面历史必须保留",
                3, monitor().getRuntimeStats().getSampledFrameCount());
    }

    /** 计数表按帧重建且有上限：上一帧的计数不得残留，超额计数名被截断。 */
    @Test
    public void a8_counterTableIsRebuiltPerFrameAndBounded() {
        Config.useDebug = true;
        monitor().beginFrame(SCREEN_ON, 1, 1, 1, 1);
        monitor().recordCounter(UiPerfMarkers.COUNTER_PICKER_MEMBERS, 3L);
        monitor().finishFrame();
        Assert.assertTrue(monitor().getRuntimeStats().getCounterSummary()
                .contains(UiPerfMarkers.COUNTER_PICKER_MEMBERS + "=3/3x1"));

        monitor().beginFrame(SCREEN_ON, 1, 1, 1, 1);
        for (int index = 0; index < 40; index++) {
            monitor().recordCounter("guard-overflow-" + index, 1L);
        }
        monitor().finishFrame();
        String summary = monitor().getRuntimeStats().getCounterSummary();
        Assert.assertFalse("上一帧的计数不得残留（计数表按帧重建）",
                summary.contains(UiPerfMarkers.COUNTER_PICKER_MEMBERS));
        int keys = summary.isEmpty() ? 0 : summary.split("；", -1).length;
        Assert.assertTrue("单帧计数键必须被截断到上限，实际 " + keys, keys <= 32 && keys < 40);
    }

    /** 帧外样本（候选枚举、面板构建）折叠进下一帧，不得被丢弃。 */
    @Test
    public void a9_offFrameSamplesFoldIntoNextFrame() {
        Config.useDebug = true;
        // 无会话时记录（等价于配置屏构造期的候选枚举）
        monitor().recordCounter(UiPerfMarkers.COUNTER_PICKER_CANDIDATES, 4988L);
        monitor().recordPhase(UiPerfMarkers.PHASE_PICKER_OPEN_MAIN, 2_000_000L);

        frame(SCREEN_ON);
        String summary = monitor().getRuntimeStats().getCounterSummary();
        Assert.assertTrue("帧外计数必须折叠进下一帧", summary.contains(UiPerfMarkers.COUNTER_PICKER_CANDIDATES + "=4988/4988x1"));
        Assert.assertTrue("帧外阶段必须折叠进下一帧",
                monitor().getRuntimeStats().getPhaseSummary().contains(UiPerfMarkers.PHASE_PICKER_OPEN_MAIN));
    }

    /** 帧外折叠桶有界：超量写入不得无界增长（单帧上限仍然生效）。 */
    @Test
    public void b1_pendingBucketIsBounded() {
        Config.useDebug = true;
        for (int index = 0; index < 200; index++) {
            monitor().recordCounter("guard-pending-" + index, 1L);
        }
        frame(SCREEN_ON);
        String summary = monitor().getRuntimeStats().getCounterSummary();
        int keys = summary.isEmpty() ? 0 : summary.split("；", -1).length;
        Assert.assertTrue("帧外折叠桶必须有界，实际 " + keys, keys <= 32 && keys < 200);
    }

    /** debug=false 时帧外记录同样不得留下任何待折叠样本。 */
    @Test
    public void b2_debugOffLeavesNoPendingSamples() {
        Config.useDebug = false;
        monitor().recordCounter(UiPerfMarkers.COUNTER_PICKER_CANDIDATES, 4988L);
        monitor().recordPhase(UiPerfMarkers.PHASE_PICKER_OPEN_MAIN, 2_000_000L);
        Config.useDebug = true;
        frame(SCREEN_ON);
        Assert.assertFalse("debug=false 期间的记录不得被采样",
                monitor().getRuntimeStats().getCounterSummary().contains(UiPerfMarkers.COUNTER_PICKER_CANDIDATES));
    }

    private static void frame(String screenName) {
        monitor().beginFrame(screenName, 1280, 765, 2560, 1529);
        monitor().finishFrame();
    }
}
