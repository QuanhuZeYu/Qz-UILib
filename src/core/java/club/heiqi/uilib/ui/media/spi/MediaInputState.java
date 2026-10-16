package club.heiqi.uilib.ui.media.spi;

/** 输入终结的已证实事实，和已解码队列/设备是否消费完分开。-1端点表示未取得真实PTS。 */
public final class MediaInputState {
    public static final MediaInputState UNKNOWN=new MediaInputState(false,false,false,-1,-1);
    public final boolean inputEnded,audioEnded,videoEnded;
    public final long audioEndNanos,videoEndNanos;
    public MediaInputState(boolean inputEnded,boolean audioEnded,boolean videoEnded,long audioEndNanos,long videoEndNanos){
        this.inputEnded=inputEnded;this.audioEnded=audioEnded;this.videoEnded=videoEnded;
        this.audioEndNanos=audioEndNanos;this.videoEndNanos=videoEndNanos;
    }
}
