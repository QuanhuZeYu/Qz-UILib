package club.heiqi.uilib.testing.gallery;

import java.util.Arrays;

/**
 * 性能压测的有界纳秒样本；宿主在帧计时区内只追加，运行边界再计算或导出。
 * 采样口径见 specs/2026-10-14-uilib-performance-testing-spec.md。
 * 实例由单个宿主线程持有，完成或中断状态由所属运行管理，不能由样本数推断。
 */
public final class PerformanceSamples {
    private static final long THRESHOLD_16 = 16_666_667L;
    private static final long THRESHOLD_33 = 33_333_333L;
    private static final long THRESHOLD_50 = 50_000_000L;

    private final long[] frameNanos;
    private final long[] intervalNanos;
    private int size;

    public PerformanceSamples(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("样本容量必须大于零");
        frameNanos = new long[capacity];
        intervalNanos = new long[capacity];
    }

    /** 拒绝非法或超容量输入且保留已有样本；正常采样只写预分配数组与索引。 */
    public void add(long frameNanos, long intervalNanos) {
        if (frameNanos < 0 || intervalNanos < 0) {
            throw new IllegalArgumentException("纳秒样本不能为负数");
        }
        if (size == capacity()) throw new IllegalStateException("样本容量已满");
        this.frameNanos[size] = frameNanos;
        this.intervalNanos[size] = intervalNanos;
        size++;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return frameNanos.length;
    }

    /** 在计时区外调用；只对有效样本的副本排序，不改变采集顺序。 */
    public Summary frame() {
        return summarize(frames());
    }

    /** 在计时区外调用；帧间隔独立统计，不混入宿主处理耗时。 */
    public Summary interval() {
        return summarize(intervals());
    }

    public long[] frames() {
        return Arrays.copyOf(frameNanos, size);
    }

    public long[] intervals() {
        return Arrays.copyOf(intervalNanos, size);
    }

    /** 保留原始顺序和纳秒单位；只导出采样事实，不宣称运行完成。 */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"frameNanos\":");
        appendSamples(json, frameNanos, size);
        json.append(",\"intervalNanos\":");
        appendSamples(json, intervalNanos, size);
        json.append(",\"frame\":");
        appendSummary(json, frame());
        json.append(",\"interval\":");
        appendSummary(json, interval());
        return json.append('}').toString();
    }

    private static Summary summarize(long[] samples) {
        int count = samples.length;
        if (count == 0) return new Summary(0, 0d, 0L, 0L, 0L, 0L, 0, 0, 0);
        Arrays.sort(samples);
        // 使用 double 累加，避免合法 long 样本之和溢出成负值。
        double sum = 0d;
        int above16 = 0;
        int above33 = 0;
        int above50 = 0;
        for (long sample : samples) {
            sum += sample;
            if (sample > THRESHOLD_16) above16++;
            if (sample > THRESHOLD_33) above33++;
            if (sample > THRESHOLD_50) above50++;
        }
        return new Summary(
                count,
                sum / count,
                nearestRank(samples, 50),
                nearestRank(samples, 95),
                nearestRank(samples, 99),
                samples[count - 1],
                above16,
                above33,
                above50);
    }

    private static long nearestRank(long[] sorted, int percent) {
        // ceil(percent * count / 100) - 1；long 中间值避免索引乘法溢出。
        int index = (int) ((percent * (long) sorted.length + 99L) / 100L) - 1;
        return sorted[index];
    }

    private static void appendSamples(StringBuilder json, long[] samples, int count) {
        json.append('[');
        for (int index = 0; index < count; index++) {
            if (index > 0) json.append(',');
            json.append(samples[index]);
        }
        json.append(']');
    }

    private static void appendSummary(StringBuilder json, Summary summary) {
        json.append("{\"count\":").append(summary.count)
                .append(",\"meanNanos\":").append(summary.meanNanos)
                .append(",\"p50Nanos\":").append(summary.p50Nanos)
                .append(",\"p95Nanos\":").append(summary.p95Nanos)
                .append(",\"p99Nanos\":").append(summary.p99Nanos)
                .append(",\"maxNanos\":").append(summary.maxNanos)
                .append(",\"above16\":").append(summary.above16)
                .append(",\"above33\":").append(summary.above33)
                .append(",\"above50\":").append(summary.above50)
                .append('}');
    }

    /** 不可变统计结果；count=0 的全零值表示尚无样本，不代表完成。 */
    public static final class Summary {
        public final int count;
        public final int above16;
        public final int above33;
        public final int above50;
        public final double meanNanos;
        public final long p50Nanos;
        public final long p95Nanos;
        public final long p99Nanos;
        public final long maxNanos;

        private Summary(
                int count,
                double meanNanos,
                long p50Nanos,
                long p95Nanos,
                long p99Nanos,
                long maxNanos,
                int above16,
                int above33,
                int above50) {
            this.count = count;
            this.meanNanos = meanNanos;
            this.p50Nanos = p50Nanos;
            this.p95Nanos = p95Nanos;
            this.p99Nanos = p99Nanos;
            this.maxNanos = maxNanos;
            this.above16 = above16;
            this.above33 = above33;
            this.above50 = above50;
        }
    }
}
