package club.heiqi.uilib.host.media;

import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import club.heiqi.uilib.ui.media.spi.MediaAudioSinkFactory;
import club.heiqi.uilib.ui.media.spi.MediaAudioSink;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;
import club.heiqi.uilib.ui.media.spi.MediaDecoderFactory;
import club.heiqi.uilib.ui.media.spi.MediaDecoderSource;

import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaSource;


/** 显式选择随包媒体后端；只共享中性SPI，首次真实open才加载native。 */
public final class IsolatedMediaAdapters {
    private static MediaBindingRuntime runtime;
    private IsolatedMediaAdapters() {}
    private static synchronized MediaBindingRuntime runtime() {
        if(runtime==null) runtime=new MediaBindingRuntime();
        return runtime;
    }
    public static MediaDecoderFactory decoderFactory() {
        return new MediaDecoderFactory() {
            @Override public MediaDecoder open(MediaSource source,MediaCancellation cancellation) throws Exception {
                return runtime().decoderFactory().open(source,cancellation);
            }
            @Override public MediaDecoderSource openSource(MediaSource source,MediaCancellation lifetime) throws Exception {
                // 来源owner保留稳定PTS锚点；不能落回旧open默认桥。
                return runtime().decoderFactory().openSource(source,lifetime);
            }
        };
    }
    public static MediaAudioSinkFactory audioSinkFactory() { return audioSinkFactory(null); }
    public static MediaAudioSinkFactory audioSinkFactory(final String deviceName) {
        return new MediaAudioSinkFactory() {
            @Override public MediaAudioSink open(MediaBudget budget) throws Exception { return runtime().audioFactory(deviceName).open(budget); }
        };
    }
    /** 查询不触发绑定初始化；关闭会话后仍可核对真实所属句柄。 */
    public static synchronized MediaNativeResources resources() {
        return runtime==null ? new MediaNativeResources(new long[12],0) : new MediaNativeResources(runtime.snapshot(),1);
    }
}
