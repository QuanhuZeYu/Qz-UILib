package club.heiqi.uilib.net.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 客户端 Store 视图。
 *
 * @param <T> 快照类型
 */
public final class NetStoreView<T> {

    private final List<NetStoreSubscriber<T>> subscribers = new CopyOnWriteArrayList<NetStoreSubscriber<T>>();
    private volatile T snapshot;

    NetStoreView(T snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * 返回当前快照。
     *
     * @return 快照
     */
    public T getSnapshot() {
        return snapshot;
    }

    /**
     * 订阅快照变化。
     *
     * @param subscriber 订阅者
     */
    public void subscribe(NetStoreSubscriber<T> subscriber) {
        subscribers.add(subscriber);
        if (snapshot != null) {
            subscriber.onSnapshot(snapshot);
        }
    }

    void update(T snapshot) {
        this.snapshot = snapshot;
        for (NetStoreSubscriber<T> subscriber : subscribers) {
            subscriber.onSnapshot(snapshot);
        }
    }

    /**
     * Store 订阅者。
     *
     * @param <T> 快照类型
     */
    public interface NetStoreSubscriber<T> {

        /**
         * 接收快照。
         *
         * @param snapshot 快照
         */
        void onSnapshot(T snapshot);
    }
}
