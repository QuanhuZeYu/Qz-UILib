package club.heiqi.uilib.ui.media;

public final class MediaFailure {
    public final MediaSource source;
    public final String stage;
    public final Throwable cause;
    public MediaFailure(MediaSource source, String stage, Throwable cause) {
        this.source=source; this.stage=stage; this.cause=cause;
    }
}
