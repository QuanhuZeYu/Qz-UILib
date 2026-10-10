package club.heiqi.uilib.ui.scene.image;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;

/** 页面显式接收服务；订阅归挂载owner，服务本身归宿主。 */
public interface ImageService {
    interface Lease extends AutoCloseable {
        ReadableSignal<ImageState> state();

        void close();
    }

    Lease acquire(ImageSource source, ReactiveDomain domain);

    void reload(ImageSource source);
}
