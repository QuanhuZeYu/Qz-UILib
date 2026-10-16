package club.heiqi.uilib.ui.media.spi;

/** 全部方法只在音频 owner 线程使用，不触碰宿主全局 context 或音量。 */
public interface MediaAudioSink extends AutoCloseable {
    boolean queue(MediaPcm pcm) throws Exception;
    void playing(boolean playing) throws Exception;
    void gain(double zeroToOne) throws Exception;
    void reset(long generation, long positionNanos) throws Exception;
    MediaAudioPosition position() throws Exception;
    String deviceDescription();
    @Override void close() throws Exception;
}
