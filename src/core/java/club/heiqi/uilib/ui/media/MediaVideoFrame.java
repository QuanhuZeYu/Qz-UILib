package club.heiqi.uilib.ui.media;

import java.nio.ByteBuffer;
import java.util.Objects;

/** 左上原点、紧密排列的 RGBA8。发布时复制，旧 PaintPlan 不受 decoder 后续写入影响。 */
public final class MediaVideoFrame {
    public final MediaFrameKey key;
    public final long presentationNanos, durationNanos;
    public final int width, height;
    private final byte[] rgba;
    public MediaVideoFrame(MediaFrameKey key, long presentationNanos, long durationNanos,
            int width, int height, ByteBuffer pixels) {
        long bytes=(long)width*height*4;
        if (width<1 || height<1 || bytes>Integer.MAX_VALUE || pixels.remaining()!=bytes || durationNanos<0)
            throw new IllegalArgumentException("无效视频帧");
        this.key=Objects.requireNonNull(key,"key"); this.presentationNanos=presentationNanos;
        this.durationNanos=durationNanos; this.width=width; this.height=height;
        rgba=new byte[(int)bytes]; pixels.duplicate().get(rgba);
    }
    public ByteBuffer pixels() { return ByteBuffer.wrap(rgba).asReadOnlyBuffer(); }
    public long bytes() { return rgba.length; }
}
