package club.heiqi.uilib.ui.scene.text;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;

/**
 * measurer 多线程并发幂等断言测试（阶段 2 并行前置 2.0）。
 *
 * <p>本测试不引入任何线程池框架/ForkJoinPool，仅用 {@link Executors#newFixedThreadPool}
 * + {@link CountDownLatch} 齐发裸并发，验证 {@link SceneTextMeasurer#measureWidth}
 * 在多线程并发下的结果幂等性：</p>
 * <ul>
 *   <li><b>冷启动 miss 路径</b>：不预热，N 线程齐发，专测 DerivedFontCache synchronized +
 *       CodepointTextCache synchronized + widthCache NaN 并发写幂等。</li>
 *   <li><b>稳态命中路径</b>：先单线程预热填满 widthCache，再 N 线程齐发，测稳态无锁路径。</li>
 * </ul>
 *
 * <p>measurer 装配复用项目现有单例：{@link DefaultTextMeasureService#getInstance()}
 * 经 {@link TextMeasureServiceSceneAdapter} 适配为 {@link SceneTextMeasurer}，与生产装配一致。
 * 不新建 measurer 实例，不绑 GL，可在纯 JUnit 无 GUI 沙箱运行。</p>
 *
 * <p>本步只固底座 + 写测试，不引入 ForkJoinPool（fork-join 在后续步骤引入）。</p>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ConcurrentMeasureWidthIdempotenceTest {

    /** 并发线程数：至少 4，上限取可用处理器数（逼出真并发）。 */
    private static final int N = Math.max(4, Runtime.getRuntime().availableProcessors());

    /** 每线程并发轮数：逼出偶发重复度量的统计窗口。 */
    private static final int ROUNDS = 1000;

    /** 度量字号（UI 像素）。 */
    private static final int FONT_SIZE_PX = 16;

    /** 等待全部线程结束的超时（秒）。 */
    private static final long AWAIT_TIMEOUT_SECONDS = 60;

    /**
     * 测试字符串集：覆盖 ASCII + 中文 + emoji 补充平面 + 格式码 + 多字符组合（共 25 个）。
     *
     * <p>设计意图：覆盖度量缓存的不同键形态——空串、单字符、ASCII、CJK、surrogate pair
     * 补充平面 emoji、Minecraft § 格式码、控制字符（\n/\t）、全角、圈号、长重复串、
     * 全类型混合——以逼出 CodepointTextCache / DerivedFontCache 各分支的并发写窗口。</p>
     */
    private static final String[] STRINGS = new String[] {
        "",                                              // 0. 空串
        " ",                                             // 1. 单空格
        "A",                                             // 2. 单 ASCII
        "AB",                                            // 3. 双 ASCII
        "Hello World",                                   // 4. ASCII 短句
        "1234567890",                                    // 5. 数字
        "The quick brown fox jumps over the lazy dog",   // 6. ASCII 长句
        "中文",                                          // 7. 中文短
        "你好世界",                                      // 8. 中文
        "中文English混合",                               // 9. 中英混合
        "😀",                                            // 10. emoji 补充平面（surrogate pair）
        "😀😂",                                          // 11. 双 emoji
        "Hello😀World",                                  // 12. ASCII + emoji
        "你好😀世界",                                    // 13. 中文 + emoji
        "§a",                                            // 14. Minecraft 格式码
        "§aHello",                                       // 15. 格式码 + ASCII
        "§a§b§cColored",                                 // 16. 多格式码
        "§rReset",                                       // 17. 重置格式码
        "\n",                                            // 18. 换行
        "Line1\nLine2",                                  // 19. 多行
        "Tab\tHere",                                     // 20. 制表符
        "Mixed §a中文😀End",                             // 21. 全类型混合
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",                // 22. 长重复串
        "ＡＢＣ",                                        // 23. 全角
        "①②③"                                           // 24. 圈号
    };

    /** measurer：复用项目现有单例装配，与生产路径一致。 */
    private SceneTextMeasurer measurer;

    private int savedWidthCacheMissBudget;

    /**
     * 装配 measurer：DefaultTextMeasureService 单例经 adapter 适配。
     *
     * <p>本测试验证并发幂等（同字符并发测量结果一致），与宽度 miss 预算（按窗口顺延测量）
     * 语义无关，故在测试期间禁用预算，避免顺延近似宽度干扰幂等断言。</p>
     */
    @Before
    public void setUp() {
        savedWidthCacheMissBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
        measurer = new TextMeasureServiceSceneAdapter(DefaultTextMeasureService.getInstance());
    }

    /**
     * 解引用本测试持有的 adapter（单例不释放）并恢复预算配置。
     */
    @After
    public void tearDown() {
        measurer = null;
        FontConfig.widthCacheMissBudgetPerWindow = savedWidthCacheMissBudget;
    }

    /**
     * 测试组 1：冷启动 miss 路径并发幂等。
     *
     * <p>不预热，N 线程 CountDownLatch 齐发，每线程 1000 轮对全部字符串 measureWidth。
     * 验证：所有线程第一轮结果互等 + 与事后单线程 baseline 相等 + 线程内后续轮稳定。
     * 专测 miss 路径并发（缓存未命中时的 synchronized 互斥与 NaN 占位并发写幂等）。</p>
     *
     * @throws Exception 线程中断或超时
     */
    @Test
    public void coldStartMissPathConcurrentMeasureIsIdempotent() throws Exception {
        runConcurrentIdempotenceCheck(false);
    }

    /**
     * 测试组 2：稳态命中路径并发幂等。
     *
     * <p>先单线程预热一遍（填满 widthCache），再 N 线程齐发 1000 轮。
     * 验证：所有线程所有轮结果 == 单线程 baseline（稳态无锁路径一致）。</p>
     *
     * @throws Exception 线程中断或超时
     */
    @Test
    public void warmSteadyHitPathConcurrentMeasureIsIdempotent() throws Exception {
        runConcurrentIdempotenceCheck(true);
    }

    /**
     * 并发幂等检查核心逻辑。
     *
     * <p>线程模型：N 个线程在 {@code startGate} 上等待，主线程放行后齐发；每线程跑
     * {@link #ROUNDS} 轮，每轮对 {@link #STRINGS} 全部字符串调用 measureWidth。
     * 第一轮结果记录用于跨线程互比与 baseline 比对；后续轮即时与第一轮比对，
     * 不一致则入错误队列。主线程在 {@code endGate} 上等待全部结束，事后单线程取 baseline，
     * 断言每线程第一轮 == baseline。</p>
     *
     * @param warmup 是否在并发前单线程预热一遍（填满 widthCache）
     * @throws Exception 线程中断或超时
     */
    private void runConcurrentIdempotenceCheck(boolean warmup) throws Exception {
        // 预热：单线程跑一遍，填满 widthCache（仅稳态组）
        if (warmup) {
            for (String s : STRINGS) {
                measurer.measureWidth(s, FONT_SIZE_PX);
            }
        }

        // 每线程第一轮结果（用于跨线程互比 + 与 baseline 比对）
        final int[][] firstRoundResults = new int[N][STRINGS.length];
        // 每线程"后续轮是否全部稳定等于第一轮"标志
        final boolean[] allConsistent = new boolean[N];
        Arrays.fill(allConsistent, true);
        // 错误收集队列（线程安全）
        final ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<String>();

        // 齐发门：所有线程就绪后统一放行，确保真正并发
        final CountDownLatch startGate = new CountDownLatch(1);
        // 完成门：主线程等待所有线程结束
        final CountDownLatch endGate = new CountDownLatch(N);

        ExecutorService pool = Executors.newFixedThreadPool(N);
        try {
            for (int t = 0; t < N; t++) {
                final int tid = t;
                pool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            startGate.await();
                            // 第一轮：记录结果
                            for (int i = 0; i < STRINGS.length; i++) {
                                firstRoundResults[tid][i] = measurer.measureWidth(STRINGS[i], FONT_SIZE_PX);
                            }
                            // 后续 ROUNDS-1 轮：即时与第一轮比对
                            for (int round = 1; round < ROUNDS; round++) {
                                for (int i = 0; i < STRINGS.length; i++) {
                                    int w = measurer.measureWidth(STRINGS[i], FONT_SIZE_PX);
                                    if (w != firstRoundResults[tid][i]) {
                                        allConsistent[tid] = false;
                                        errors.add("线程 " + tid + " 轮 " + round
                                + " 字符串[" + i + "]=\"" + STRINGS[i] + "\""
                                + " 第一轮=" + firstRoundResults[tid][i]
                                + " 本轮=" + w);
                                    }
                                }
                            }
                        } catch (Throwable e) {
                            errors.add("线程 " + tid + " 异常: " + e);
                        } finally {
                            endGate.countDown();
                        }
                    }
                });
            }
            // 放行所有线程齐发
            startGate.countDown();
            // 等待全部结束
            boolean done = endGate.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!done) {
                Assert.fail("并发线程未在 " + AWAIT_TIMEOUT_SECONDS
                        + "s 内全部结束，可能死锁或卡在字体运行时初始化");
            }
        } finally {
            pool.shutdownNow();
        }

        // 事后单线程 baseline（此时缓存已填，命中值）
        int[] baseline = new int[STRINGS.length];
        for (int i = 0; i < STRINGS.length; i++) {
            baseline[i] = measurer.measureWidth(STRINGS[i], FONT_SIZE_PX);
        }

        // 断言 1：每线程第一轮 == baseline（跨线程一致 + miss 与 hit 幂等）
        for (int t = 0; t < N; t++) {
            for (int i = 0; i < STRINGS.length; i++) {
                if (firstRoundResults[t][i] != baseline[i]) {
                    errors.add("线程 " + t + " 第一轮 字符串[" + i + "]=\"" + STRINGS[i] + "\""
                            + " = " + firstRoundResults[t][i] + " 期望 baseline=" + baseline[i]);
                }
            }
        }

        // 断言 2：每线程后续轮稳定（allConsistent 为 false 时错误已入队）
        for (int t = 0; t < N; t++) {
            if (!allConsistent[t]) {
                // 错误已入队，此处无需额外动作
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("measurer 并发幂等断言失败，共 ").append(errors.size()).append(" 处不一致：\n");
            int shown = 0;
            for (String e : errors) {
                if (shown++ >= 20) {
                    sb.append("  ...（余略）\n");
                    break;
                }
                sb.append("  ").append(e).append('\n');
            }
            Assert.fail(sb.toString());
        }
    }
}
