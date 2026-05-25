package club.heiqi.uilib.net.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 客户端 Store 视图。
 */
public final class NetStoreView {

    private final List<NetStoreSubscriber> subscribers = new CopyOnWriteArrayList<NetStoreSubscriber>();
    private volatile NetBody snapshot;

    NetStoreView(NetBody snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * 返回当前快照 body。
     *
     * @return 快照 body
     */
    public NetBody getSnapshot() {
        return snapshot;
    }

    /**
     * 订阅快照变化。
     *
     * @param subscriber 订阅者
     */
    public void subscribe(NetStoreSubscriber subscriber) {
        subscribers.add(subscriber);
        if (snapshot != null) {
            subscriber.onSnapshot(snapshot);
        }
    }

    /**
     * 取消订阅快照变化。
     *
     * @param subscriber 订阅者
     */
    public void unsubscribe(NetStoreSubscriber subscriber) {
        if (subscriber == null) {
            return;
        }
        subscribers.remove(subscriber);
    }

    void update(NetBody snapshot) {
        this.snapshot = snapshot;
        for (NetStoreSubscriber subscriber : subscribers) {
            subscriber.onSnapshot(snapshot);
        }
    }

    /**
     * Store 订阅者。
     */
    public interface NetStoreSubscriber {

        /**
         * 接收快照 body。
         *
         * @param snapshot 快照 body
         */
        void onSnapshot(NetBody snapshot);
    }
}
