package club.heiqi.uilib.ui.media;

public final class MediaSnapshot {
    public final MediaState state;
    public final MediaSource source;
    public final MediaInfo info;
    public final long positionNanos, generation, sampledNanos;
    public final boolean playIntent, muted, outputReady;
    public final double volume;
    public final MediaVideoFrame video;
    public final MediaFailure failure;
    public final MediaStatistics statistics;
    MediaSnapshot(MediaState state, MediaSource source, MediaInfo info, long positionNanos, long generation,
            long sampledNanos, boolean playIntent, boolean muted, boolean outputReady, double volume,
            MediaVideoFrame video, MediaFailure failure, MediaStatistics statistics) {
        this.state=state; this.source=source; this.info=info; this.positionNanos=positionNanos; this.generation=generation;
        this.sampledNanos=sampledNanos; this.playIntent=playIntent; this.muted=muted; this.outputReady=outputReady;
        this.volume=volume; this.video=video; this.failure=failure; this.statistics=statistics;
    }
}
