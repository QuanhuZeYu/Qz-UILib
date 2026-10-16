package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.PerformanceSamples;
import club.heiqi.uilib.testing.gallery.PerformanceSamples.Summary;

import java.util.Arrays;

/** 固定数值验收复用测试应用；期望取自独立输入，不在测试中重写统计算法。 */
public final class PerformanceSamplesScenario extends TestApplication {
    public static void main(String[] args) throws Exception {
        new PerformanceSamplesScenario().run(args);
    }

    @Override
    protected void runTests() throws Exception {
        scenario("独立百样本分位与纳秒均值", this::knownPercentiles);
        scenario("小样本 nearest-rank", this::smallSamples);
        scenario("严格阈值及原始报告", this::strictThresholds);
        scenario("原始顺序与导出拷贝隔离", this::preservedOrder);
        scenario("满容量拒绝且保留样本", this::boundedCapacity);
        scenario("非法容量和负数拒绝", this::invalidValues);
        scenario("零样本与单个零值区分", this::emptySamples);
        scenario("大数均值不发生 long 溢出", this::largeValues);
    }

    private void knownPercentiles() {
        PerformanceSamples samples = new PerformanceSamples(100);
        // frame 输入为 100..1 ms，interval 为 201..300 ms，两口径不能串用。
        for (int index = 0; index < 100; index++) {
            samples.add((100L - index) * 1_000_000L, (201L + index) * 1_000_000L);
        }
        assertSummary(samples.frame(), 100, 50_500_000d,
                50_000_000L, 95_000_000L, 99_000_000L, 100_000_000L, 84, 67, 50);
        assertSummary(samples.interval(), 100, 250_500_000d,
                250_000_000L, 295_000_000L, 299_000_000L, 300_000_000L, 100, 100, 100);
        System.out.println("百样本 frame p50/p95/p99=50000000/95000000/99000000 ns；"
                + "interval=250000000/295000000/299000000 ns");
    }

    private void smallSamples() {
        PerformanceSamples samples = new PerformanceSamples(3);
        samples.add(9L, 2L);
        samples.add(1L, 8L);
        samples.add(5L, 0L);
        assertSummary(samples.frame(), 3, 5d, 5L, 9L, 9L, 9L, 0, 0, 0);
        assertSummary(samples.interval(), 3, 10d / 3d, 2L, 8L, 8L, 8L, 0, 0, 0);
        System.out.println("JSON_SMALL " + samples.toJson());
    }

    private void strictThresholds() {
        // 每条阈值都含 -1、相等、+1 纳秒；0 纳秒也属于合法样本。
        long[] frames = {50_000_000L, 16_667_000L, 0L, 33_333_001L, 16_666_999L,
                50_000_001L, 33_333_000L, 16_667_001L, 49_999_999L, 33_332_999L};
        long[] intervals = {33_332_999L, 49_999_999L, 16_667_001L, 33_333_000L,
                50_000_001L, 16_666_999L, 33_333_001L, 0L, 16_667_000L, 50_000_000L};
        PerformanceSamples samples = new PerformanceSamples(10);
        for (int index = 0; index < frames.length; index++) {
            samples.add(frames[index], intervals[index]);
        }
        assertSummary(samples.frame(), 10, 30_000_000d,
                33_332_999L, 50_000_001L, 50_000_001L, 50_000_001L, 7, 4, 1);
        assertSummary(samples.interval(), 10, 30_000_000d,
                33_332_999L, 50_000_001L, 50_000_001L, 50_000_001L, 7, 4, 1);
        System.out.println("JSON_THRESHOLD " + samples.toJson());
        require(Arrays.equals(samples.frames(), frames), "统计和报告改变了帧样本顺序");
        require(Arrays.equals(samples.intervals(), intervals), "统计和报告改变了帧间隔顺序");
    }

    private void preservedOrder() {
        PerformanceSamples samples = new PerformanceSamples(5);
        samples.add(40L, 2L);
        samples.add(10L, 7L);
        samples.add(30L, 1L);
        long[] frames = samples.frames();
        long[] intervals = samples.intervals();
        require(frames.length == 3 && intervals.length == 3, "导出混入尚未采样的容量");
        frames[0] = 999L;
        intervals[1] = 999L;
        Summary previous = samples.frame();
        samples.interval();
        samples.toJson();
        require(Arrays.equals(samples.frames(), new long[] {40L, 10L, 30L}),
                "帧原始序或导出拷贝不独立");
        require(Arrays.equals(samples.intervals(), new long[] {2L, 7L, 1L}),
                "帧间隔原始序或导出拷贝不独立");
        samples.add(20L, 4L);
        require(previous.count == 3 && previous.maxNanos == 40L, "已返回统计被后续采样改变");
        require(samples.size() == 4 && samples.capacity() == 5, "有效数量与容量混淆");
    }

    private void boundedCapacity() throws Exception {
        PerformanceSamples samples = new PerformanceSamples(2);
        samples.add(1L, 2L);
        samples.add(3L, 4L);
        expectRejected(IllegalStateException.class, () -> samples.add(5L, 6L));
        expectRejected(IllegalStateException.class, () -> samples.add(7L, 8L));
        require(samples.size() == 2 && samples.capacity() == 2, "拒绝后容量或样本数变化");
        require(Arrays.equals(samples.frames(), new long[] {1L, 3L}), "满容量丢弃或覆盖了帧样本");
        require(Arrays.equals(samples.intervals(), new long[] {2L, 4L}), "满容量丢弃或覆盖了帧间隔");
    }

    private void invalidValues() throws Exception {
        expectRejected(IllegalArgumentException.class, () -> new PerformanceSamples(0));
        expectRejected(IllegalArgumentException.class, () -> new PerformanceSamples(-1));
        PerformanceSamples samples = new PerformanceSamples(2);
        expectRejected(IllegalArgumentException.class, () -> samples.add(-1L, 1L));
        expectRejected(IllegalArgumentException.class, () -> samples.add(1L, -1L));
        expectRejected(IllegalArgumentException.class, () -> samples.add(Long.MIN_VALUE, 0L));
        expectRejected(IllegalArgumentException.class, () -> samples.add(0L, Long.MIN_VALUE));
        require(samples.size() == 0 && samples.frames().length == 0
                && samples.intervals().length == 0, "非法样本占用了容量");
        samples.add(0L, 0L);
        samples.add(1L, 2L);
        require(Arrays.equals(samples.frames(), new long[] {0L, 1L})
                && Arrays.equals(samples.intervals(), new long[] {0L, 2L}),
                "拒绝非法输入后无法继续正常采样");
    }

    private void emptySamples() {
        PerformanceSamples samples = new PerformanceSamples(1);
        require(samples.size() == 0 && samples.capacity() == 1, "空采样器状态错误");
        assertSummary(samples.frame(), 0, 0d, 0L, 0L, 0L, 0L, 0, 0, 0);
        assertSummary(samples.interval(), 0, 0d, 0L, 0L, 0L, 0L, 0, 0, 0);
        require(samples.frames().length == 0 && samples.intervals().length == 0, "空样本导出非空");
        System.out.println("JSON_EMPTY " + samples.toJson());
        samples.add(0L, 0L);
        assertSummary(samples.frame(), 1, 0d, 0L, 0L, 0L, 0L, 0, 0, 0);
        assertSummary(samples.interval(), 1, 0d, 0L, 0L, 0L, 0L, 0, 0, 0);
    }

    private void largeValues() {
        PerformanceSamples samples = new PerformanceSamples(2);
        samples.add(Long.MAX_VALUE, Long.MAX_VALUE);
        samples.add(Long.MAX_VALUE, 0L);
        assertSummary(samples.frame(), 2, (double) Long.MAX_VALUE,
                Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 2, 2, 2);
        assertSummary(samples.interval(), 2, Long.MAX_VALUE / 2d,
                0L, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 1, 1, 1);
        System.out.println("JSON_LARGE " + samples.toJson());
    }

    private static void assertSummary(Summary actual, int count, double mean,
            long p50, long p95, long p99, long max, int above16, int above33, int above50) {
        require(actual.count == count, "样本数错误：" + actual.count + "，期望 " + count);
        require(Double.isFinite(actual.meanNanos) && Math.abs(actual.meanNanos - mean) <= 1e-9,
                "纳秒均值错误：" + actual.meanNanos + "，期望 " + mean);
        require(actual.p50Nanos == p50 && actual.p95Nanos == p95 && actual.p99Nanos == p99,
                "nearest-rank 分位错误：" + actual.p50Nanos + "/"
                        + actual.p95Nanos + "/" + actual.p99Nanos);
        require(actual.maxNanos == max, "最大纳秒值错误：" + actual.maxNanos);
        require(actual.above16 == above16 && actual.above33 == above33 && actual.above50 == above50,
                "严格阈值计数错误：" + actual.above16 + "/" + actual.above33 + "/" + actual.above50);
    }

    private static void expectRejected(Class<? extends RuntimeException> type,
            CheckedRunnable action) throws Exception {
        try {
            action.run();
        } catch (RuntimeException error) {
            require(type.isInstance(error), "拒绝异常类型错误：" + error);
            return;
        }
        throw new AssertionError("未拒绝非法输入或满容量追加：" + type.getSimpleName());
    }
}
