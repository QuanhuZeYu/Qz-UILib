package club.heiqi.uilib.ui.media;

/** 工程预算，不是规格常量。视频字节/条目含 current，时长只计未消费部分；core 与设备 PCM 分别限额。 */
public final class MediaBudget {
    public static final MediaBudget DEFAULT = new MediaBudget(4, 8, 80L*1024*1024, 300_000_000L,
            64, 2L*1024*1024, 1_000_000_000L, 16, 256L*1024, 300_000_000L);
    public final int sessions, videoFrames, audioBlocks, deviceBuffers;
    public final int deferredPackets=512, presentationRecords=128, presentationLeases=16;
    /** 顺序read忽略轨道需求时的独立raw延后存储，不冒充encoded packet。 */
    public final int deferredDecodedItems=64;
    public final long deferredDecodedBytes=8L*1024*1024, deferredDecodedNanos=2_000_000_000L;
    public final long deferredBytes=8L*1024*1024, deferredNanos=10_000_000_000L;
    public final long videoBytes, videoNanos, audioBytes, audioNanos, deviceBytes, deviceNanos;
    public MediaBudget(int sessions, int videoFrames, long videoBytes, long videoNanos,
            int audioBlocks, long audioBytes, long audioNanos,
            int deviceBuffers, long deviceBytes, long deviceNanos) {
        if (sessions<1 || videoFrames<2 || videoBytes<4 || videoNanos<1 || audioBlocks<1 || audioBytes<2
                || audioNanos<1 || deviceBuffers<1 || deviceBytes<2 || deviceNanos<1)
            throw new IllegalArgumentException("媒体预算必须有限且为正");
        this.sessions=sessions; this.videoFrames=videoFrames; this.videoBytes=videoBytes; this.videoNanos=videoNanos;
        this.audioBlocks=audioBlocks; this.audioBytes=audioBytes; this.audioNanos=audioNanos;
        this.deviceBuffers=deviceBuffers; this.deviceBytes=deviceBytes; this.deviceNanos=deviceNanos;
    }
}
