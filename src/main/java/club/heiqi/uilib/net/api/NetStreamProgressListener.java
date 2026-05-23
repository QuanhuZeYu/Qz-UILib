package club.heiqi.uilib.net.api;

/**
 * Stream 下载进度监听器。
 */
public interface NetStreamProgressListener {

    /**
     * 接收进度更新。
     *
     * @param progress 进度快照
     */
    void onProgress(NetStreamProgress progress);
}
