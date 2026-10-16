package club.heiqi.uilib.ui.media.spi;

/** core 对两条轨道的背压。适配可有界延后另一轨的压缩包，不能无限读取。 */
public final class MediaDecodeRequest {
    public final boolean video, audio;
    public final long deferredBytes, deferredNanos;
    public final int deferredPackets;
    public MediaDecodeRequest(boolean video,boolean audio,long bytes,int packets,long nanos) {
        this.video=video; this.audio=audio; deferredBytes=bytes; deferredPackets=packets; deferredNanos=nanos;
    }
}
