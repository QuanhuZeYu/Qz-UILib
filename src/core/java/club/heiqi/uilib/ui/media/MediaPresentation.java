package club.heiqi.uilib.ui.media;

/** 正式回放提交的值记录。deviceSampledNanos 给出设备原始采样时刻；位置映射到 sampledNanos。 */
public final class MediaPresentation {
    public final MediaFrameKey key;
    public final long sampledNanos, videoPresentationNanos, audioPositionNanos, synchronizationErrorNanos, deviceSampledNanos;
    public final boolean hasAudio;
    MediaPresentation(MediaFrameKey key, long sampledNanos, long pts, long position, long deviceSampledNanos, boolean hasAudio) {
        this.key=key; this.sampledNanos=sampledNanos; this.videoPresentationNanos=pts;
        this.audioPositionNanos=position; this.synchronizationErrorNanos=pts-position;
        this.deviceSampledNanos=deviceSampledNanos; this.hasAudio=hasAudio;
    }
}
