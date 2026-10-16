package club.heiqi.uilib.ui.media;

/** 同一来源时间线上的闭区间，单位 ns。null 区间表示当前没有该能力/事实。 */
public final class MediaRange {
    public final long startNanos, endNanos;
    public MediaRange(long startNanos, long endNanos) {
        if (startNanos < 0 || endNanos < startNanos) throw new IllegalArgumentException("无效媒体区间");
        this.startNanos = startNanos; this.endNanos = endNanos;
    }
    public boolean contains(long nanos) { return nanos >= startNanos && nanos <= endNanos; }
}
