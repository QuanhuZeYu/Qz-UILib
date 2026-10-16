package club.heiqi.uilib.ui.media.spi;

/** 压缩 packet 的实际延后存量，与 core 已解码队列分别计量。 */
public final class MediaDecoderBuffering {
    public static final MediaDecoderBuffering EMPTY=new MediaDecoderBuffering(0,0,0);
    public final long bytes,nanos;
    public final int packets;
    public MediaDecoderBuffering(long bytes,int packets,long nanos) {this.bytes=bytes;this.packets=packets;this.nanos=nanos;}
}
