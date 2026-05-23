package club.heiqi.uilib.net.api;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stream 调用句柄，提供独立的结果、进度和取消生命周期。
 */
public final class NetStreamCall {

    private final NetService service;
    private final String endpointKey;
    private final long requestId;
    private final StreamFuture future;
    private final List<NetStreamProgressListener> progressListeners =
            new CopyOnWriteArrayList<NetStreamProgressListener>();
    private volatile NetStreamProgress lastProgress;

    NetStreamCall(NetService service, String endpointKey, long requestId) {
        this.service = service;
        this.endpointKey = endpointKey;
        this.requestId = requestId;
        this.future = new StreamFuture(this);
    }

    public long getRequestId() {
        return requestId;
    }

    /**
     * 返回结果 future。
     *
     * @return response future
     */
    public CompletableFuture<NetResponse> getFuture() {
        return future;
    }

    /**
     * `getFuture()` 的简写形式。
     *
     * @return response future
     */
    public CompletableFuture<NetResponse> future() {
        return future;
    }

    /**
     * 订阅下载进度。
     *
     * @param listener 监听器
     * @return 当前调用句柄
     */
    public NetStreamCall onProgress(NetStreamProgressListener listener) {
        NetStreamProgressListener safeListener = Objects.requireNonNull(listener, "listener");
        progressListeners.add(safeListener);
        NetStreamProgress current = lastProgress;
        if (current != null) {
            safeListener.onProgress(current);
        }
        return this;
    }

    /**
     * 取消本次 Stream 调用，并向远端发送取消信号。
     *
     * @return true 表示本地 future 已进入取消状态
     */
    public boolean cancel() {
        if (!future.cancelLocally()) {
            return false;
        }
        service.cancelStreamCall(endpointKey, requestId);
        return true;
    }

    void complete(NetResponse response) {
        future.complete(response);
    }

    void fail(Throwable throwable) {
        future.completeExceptionally(throwable);
    }

    void emitProgress(NetStreamProgress progress) {
        lastProgress = progress;
        for (NetStreamProgressListener listener : progressListeners) {
            listener.onProgress(progress);
        }
    }

    private static final class StreamFuture extends CompletableFuture<NetResponse> {

        private final NetStreamCall call;

        StreamFuture(NetStreamCall call) {
            this.call = call;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return call.cancel();
        }

        boolean cancelLocally() {
            return super.cancel(false);
        }
    }
}
