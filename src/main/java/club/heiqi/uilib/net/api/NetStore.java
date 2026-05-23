package club.heiqi.uilib.net.api;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内容语义状态同步 Store。
 */
public final class NetStore {

    private final NetService service;
    private final NetStoreId id;
    private final NetStoreScope scope;
    private final AccessControl accessControl;
    private final NetStoreView view;
    private final Map<Object, NetBody> playerStates = new ConcurrentHashMap<Object, NetBody>();
    private final Map<Integer, NetBody> dimensionStates = new ConcurrentHashMap<Integer, NetBody>();
    private NetBody state;

    NetStore(NetService service, NetStoreId id, NetStoreScope scope, NetBody initial, AccessControl accessControl) {
        this.service = service;
        this.id = id;
        this.scope = scope;
        this.state = initial == null ? NetBody.empty() : initial;
        this.accessControl = accessControl;
        this.view = new NetStoreView(this.state);
    }

    public NetStoreId getId() {
        return id;
    }

    public NetStoreScope getScope() {
        return scope;
    }

    /**
     * 返回客户端视图。
     *
     * @return 视图
     */
    public NetStoreView view() {
        return view;
    }

    /**
     * 返回当前状态 body。
     *
     * @return 状态 body
     */
    public synchronized NetBody get() {
        return state;
    }

    /**
     * 服务端替换默认状态并按 Store scope 广播快照。
     *
     * @param next 新状态
     */
    public synchronized void set(NetBody next) {
        state = Objects.requireNonNull(next, "next");
        service.sendStoreSnapshot(this, NetTarget.all(), state);
    }

    /**
     * 返回指定玩家的状态。
     *
     * @param player 玩家对象
     * @return 玩家状态；未单独设置时返回默认状态
     */
    public synchronized NetBody getForPlayer(Object player) {
        Objects.requireNonNull(player, "player");
        NetBody body = playerStates.get(player);
        return body == null ? state : body;
    }

    /**
     * 服务端替换指定玩家状态并仅向该玩家发送快照。
     *
     * @param player 玩家对象
     * @param next 新状态
     */
    public synchronized void setForPlayer(Object player, NetBody next) {
        Objects.requireNonNull(player, "player");
        NetBody resolved = Objects.requireNonNull(next, "next");
        playerStates.put(player, resolved);
        service.sendStoreSnapshot(this, NetTarget.player(player), resolved);
    }

    /**
     * 返回指定维度状态。
     *
     * @param dimensionId 维度 id
     * @return 维度状态；未单独设置时返回默认状态
     */
    public synchronized NetBody getForDimension(int dimensionId) {
        NetBody body = dimensionStates.get(Integer.valueOf(dimensionId));
        return body == null ? state : body;
    }

    /**
     * 服务端替换指定维度状态并向该维度玩家发送快照。
     *
     * @param dimensionId 维度 id
     * @param next 新状态
     */
    public synchronized void setForDimension(int dimensionId, NetBody next) {
        NetBody resolved = Objects.requireNonNull(next, "next");
        dimensionStates.put(Integer.valueOf(dimensionId), resolved);
        service.sendStoreSnapshot(this, NetTarget.dimension(dimensionId), resolved);
    }

    /**
     * 基于当前 body 计算新 body 并广播。
     *
     * @param mutator 修改函数
     */
    public synchronized void mutate(StoreMutator mutator) {
        set(Objects.requireNonNull(mutator, "mutator").mutate(state));
    }

    /**
     * 基于指定玩家状态计算新 body 并发送给该玩家。
     *
     * @param player 玩家对象
     * @param mutator 修改函数
     */
    public synchronized void mutateForPlayer(Object player, StoreMutator mutator) {
        setForPlayer(player, Objects.requireNonNull(mutator, "mutator").mutate(getForPlayer(player)));
    }

    /**
     * 基于指定维度状态计算新 body 并发送给该维度。
     *
     * @param dimensionId 维度 id
     * @param mutator 修改函数
     */
    public synchronized void mutateForDimension(int dimensionId, StoreMutator mutator) {
        setForDimension(dimensionId, Objects.requireNonNull(mutator, "mutator")
                .mutate(getForDimension(dimensionId)));
    }

    boolean hasAccessControl() {
        return accessControl != null;
    }

    boolean canAccess(Object player) {
        return accessControl == null || accessControl.canAccess(player, this);
    }

    void receiveSnapshot(NetBody snapshot) {
        synchronized (this) {
            this.state = snapshot;
        }
        view.update(snapshot);
    }

    /**
     * Store 注册构造器。
     */
    public static final class Builder {

        private final NetService service;
        private final NetStoreId id;
        private NetStoreScope scope = NetStoreScope.GLOBAL;
        private NetBody initial = NetBody.empty();
        private AccessControl accessControl;

        Builder(NetService service, NetStoreId id) {
            this.service = service;
            this.id = id;
        }

        public Builder scope(NetStoreScope scope) {
            this.scope = Objects.requireNonNull(scope, "scope");
            return this;
        }

        public Builder initial(NetBody initial) {
            this.initial = Objects.requireNonNull(initial, "initial");
            return this;
        }

        public Builder initialJson(String json) {
            return initial(NetBody.json(json));
        }

        public Builder accessControl(AccessControl accessControl) {
            this.accessControl = accessControl;
            return this;
        }

        public NetStore register() {
            return service.registerStore(new NetStore(service, id, scope, initial, accessControl));
        }
    }

    /**
     * Store 修改函数。
     */
    public interface StoreMutator {

        /**
         * 根据当前 body 返回新 body。
         *
         * @param current 当前 body
         * @return 新 body
         */
        NetBody mutate(NetBody current);
    }

    /**
     * Store 访问控制。
     */
    public interface AccessControl {

        /**
         * 判断玩家是否可访问 Store。
         *
         * @param player 玩家对象
         * @param store Store
         * @return true 表示允许访问
         */
        boolean canAccess(Object player, NetStore store);
    }
}
