package club.heiqi.uilib.ui.media.spi;

import java.nio.ByteBuffer;

/** decoder 借出的单项数据，只在下次 read/seek/close 前有效。core 必须在该边界前复制。 */
public final class MediaDecoded {
    public static final MediaDecoded YIELD=new MediaDecoded(false,0,0,0,0,0,0,ByteBuffer.allocate(0));
    public final boolean video;
    public final long presentationNanos, durationNanos;
    public final int width, height, sampleRate, channels;
    public final ByteBuffer data;
    private MediaDecoded(boolean video, long pts, long duration, int width, int height,
            int sampleRate, int channels, ByteBuffer data) {
        this.video=video; this.presentationNanos=pts; this.durationNanos=duration; this.width=width; this.height=height;
        this.sampleRate=sampleRate; this.channels=channels; this.data=data.asReadOnlyBuffer();
    }
    public static MediaDecoded video(long pts, long duration, int width, int height, ByteBuffer rgba) {
        return new MediaDecoded(true, pts, duration, width, height, 0, 0, rgba);
    }
    /** PCM 为 native endian、交错 signed16，channels 仅 1 或 2。 */
    public static MediaDecoded audio(long pts, int sampleRate, int channels, ByteBuffer pcm) {
        if (sampleRate<1 || channels<1 || channels>2 || pcm.remaining()%(channels*2)!=0)
            throw new IllegalArgumentException("无效 PCM");
        long duration=(long)(pcm.remaining()/(channels*2))*1_000_000_000L/sampleRate;
        return new MediaDecoded(false, pts, duration, 0, 0, sampleRate, channels, pcm);
    }
}
