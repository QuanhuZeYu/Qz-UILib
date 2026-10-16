package club.heiqi.uilib.ui.media.spi;

public final class MediaAudioPosition {
    public final long positionNanos, sampledNanos, outputLatencyNanos, queuedBytes, queuedNanos, queuedEndNanos;
    public final int queuedBuffers;
    public final boolean playing;
    public MediaAudioPosition(long positionNanos, long sampledNanos, long latency, long bytes, long nanos,
            long endNanos, int buffers, boolean playing) {
        this.positionNanos=positionNanos; this.sampledNanos=sampledNanos; this.outputLatencyNanos=latency;
        this.queuedBytes=bytes; this.queuedNanos=nanos; this.queuedEndNanos=endNanos;
        this.queuedBuffers=buffers; this.playing=playing;
    }
    public long at(long nanos) {
        long value=positionNanos+(playing ? nanos-sampledNanos : 0);
        return Math.max(0, Math.min(queuedEndNanos, value));
    }
}
