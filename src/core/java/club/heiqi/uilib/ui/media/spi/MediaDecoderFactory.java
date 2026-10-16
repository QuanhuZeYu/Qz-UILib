package club.heiqi.uilib.ui.media.spi;

import club.heiqi.uilib.ui.media.MediaSource;

public interface MediaDecoderFactory {
    MediaDecoder open(MediaSource source, MediaCancellation cancellation) throws Exception;
    /** 默认桥保留原 factory lambda；只有需要同来源状态的适配覆盖本入口。 */
    default MediaDecoderSource openSource(final MediaSource source, MediaCancellation lifetime) throws Exception {
        return new MediaDecoderSource() {
            @Override public MediaDecoder open(long position, boolean seeking, MediaCancellation operation) throws Exception {
                MediaDecoder decoder=MediaDecoderFactory.this.open(source,operation);
                try { if(seeking) decoder.seek(position); return decoder; }
                catch(Throwable error) {
                    try { decoder.close(); } catch(Throwable cleanup) { error.addSuppressed(cleanup); }
                    if(error instanceof Error)throw(Error)error;
                    if(error instanceof Exception)throw(Exception)error;
                    throw new RuntimeException(error);
                }
            }
        };
    }
}
