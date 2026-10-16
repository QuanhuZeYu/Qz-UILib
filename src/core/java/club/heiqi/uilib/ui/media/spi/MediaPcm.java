package club.heiqi.uilib.ui.media.spi;

import java.nio.ByteBuffer;

/** core 所有的不可变 PCM；sink queue 返回前复制到自己有界的设备存储。 */
public final class MediaPcm {
    public final long generation, presentationNanos, durationNanos;
    public final int sampleRate, channels;
    private final byte[] bytes;
    public MediaPcm(long generation, long pts, int sampleRate, int channels, ByteBuffer data) {
        if (sampleRate<1 || channels<1 || channels>2 || data.remaining()%(channels*2)!=0)
            throw new IllegalArgumentException("无效 PCM");
        this.generation=generation; this.presentationNanos=pts; this.sampleRate=sampleRate; this.channels=channels;
        bytes=new byte[data.remaining()]; data.duplicate().get(bytes);
        durationNanos=(long)(bytes.length/(channels*2))*1_000_000_000L/sampleRate;
    }
    public ByteBuffer samples() { return ByteBuffer.wrap(bytes).asReadOnlyBuffer(); }
    public int bytes() { return bytes.length; }
}
