package club.heiqi.uilib.ui.media.spi;

import club.heiqi.uilib.ui.media.MediaInfo;

/** 除 cancellation 外均在同一 decoder owner 线程调用；read 返回 null 是正常 EOF。 */
public interface MediaDecoder extends AutoCloseable {
    MediaInfo info();
    MediaDecoded read() throws Exception;
    /** 无需求意识的顺序实现可返回任一轨；core立即冻结并按独立raw延后预算保存，不在该项上等待。 */
    default MediaDecoded read(MediaDecodeRequest request) throws Exception { return read(); }
    default MediaInputState inputState() { return MediaInputState.UNKNOWN; }
    default MediaDecoderBuffering buffering() { return MediaDecoderBuffering.EMPTY; }
    void seek(long positionNanos) throws Exception;
    @Override void close() throws Exception;
}
