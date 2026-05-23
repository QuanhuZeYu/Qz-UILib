package club.heiqi.uilib.net.store;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store 订阅表。
 */
public final class StoreSubscriptionRegistry {

    private final ConcurrentHashMap<String, Set<Object>> subscriptions = new ConcurrentHashMap<String, Set<Object>>();

    /**
     * 添加订阅者。
     *
     * @param storeKey Store key
     * @param player 玩家
     */
    public void subscribe(String storeKey, Object player) {
        subscriptions.computeIfAbsent(storeKey, key -> Collections.synchronizedSet(new HashSet<Object>()))
                .add(player);
    }

    /**
     * 移除订阅者。
     *
     * @param storeKey Store key
     * @param player 玩家
     */
    public void unsubscribe(String storeKey, Object player) {
        Set<Object> players = subscriptions.get(storeKey);
        if (players != null) {
            players.remove(player);
        }
    }

    /**
     * 查询订阅者。
     *
     * @param storeKey Store key
     * @return 订阅者集合
     */
    public Set<Object> subscribersOf(String storeKey) {
        Set<Object> players = subscriptions.get(storeKey);
        if (players == null) {
            return Collections.emptySet();
        }
        synchronized (players) {
            return new HashSet<Object>(players);
        }
    }
}
