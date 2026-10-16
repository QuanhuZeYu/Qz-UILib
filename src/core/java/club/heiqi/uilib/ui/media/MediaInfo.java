package club.heiqi.uilib.ui.media;

/** durationNanos=-1 明确表示未知；ready 表示已经发现实际轨道，和输出就绪分别表达。 */
public final class MediaInfo {
    public static final MediaInfo UNKNOWN = new MediaInfo(false, false, false, -1, null, null, 0, 0);
    public final boolean ready, hasVideo, hasAudio;
    public final long durationNanos;
    public final MediaRange seekable, buffered;
    public final int width, height;
    public MediaInfo(boolean ready, boolean hasVideo, boolean hasAudio, long durationNanos,
            MediaRange seekable, MediaRange buffered, int width, int height) {
        if (durationNanos < -1 || width < 0 || height < 0) throw new IllegalArgumentException("无效媒体事实");
        this.ready=ready; this.hasVideo=hasVideo; this.hasAudio=hasAudio;
        this.durationNanos=durationNanos; this.seekable=seekable; this.buffered=buffered;
        this.width=width; this.height=height;
    }
    public boolean durationKnown() { return durationNanos >= 0; }
}
