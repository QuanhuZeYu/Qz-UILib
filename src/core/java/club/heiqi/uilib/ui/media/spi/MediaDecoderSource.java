package club.heiqi.uilib.ui.media.spi;

import club.heiqi.uilib.ui.media.MediaInfo;

/** 同一会话一次来源生命周期；跨输出代保留。全部方法在 decoder owner 调用，close 不关闭其他来源。 */
public interface MediaDecoderSource extends AutoCloseable {
    MediaDecoder open(long positionNanos, boolean seeking, MediaCancellation cancellation) throws Exception;
    /** 首次打开后实际选定的来源时间，用于直播初始join；不触发额外定位。 */
    default long initialPositionNanos() { return 0; }
    /** 无动态来源不安排唤醒；截止时间属于 System.nanoTime 时钟域。 */
    default long refreshDeadlineNanos() { return Long.MAX_VALUE; }
    /** 只刷新媒体事实，不推进解码流；调用不持会话锁。 */
    default MediaInfo refresh(MediaInfo current, MediaCancellation cancellation) throws Exception { return current; }
    @Override default void close() throws Exception {}
}
