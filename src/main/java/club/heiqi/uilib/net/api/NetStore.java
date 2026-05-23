package club.heiqi.uilib.net.api;

import java.util.Objects;

/**
 * 状态同步 Store。
 *
 * @param <T> 状态类型
 */
public final class NetStore<T> {

    private final NetService service;
    private final NetStoreId id;
    private final Class<T> stateType;
    private final NetStoreScope scope;
    private final AccessControl accessControl;
    private final NetStoreView<T> view;
    private T state;

    NetStore(NetService service, NetStoreId id, Class<T> stateType, NetStoreScope scope, T initial,
            AccessControl accessControl) {
        this.service = service;
        this.id = id;
        this.stateType = stateType;
        this.scope = scope;
        this.state = initial;
        this.accessControl = accessControl;
        this.view = new NetStoreView<T>(initial);
    }

    public NetStoreId getId() {
        return id;
    }

    public Class<T> getStateType() {
        return stateType;
    }

    public NetStoreScope getScope() {
        return scope;
    }

    /**
     * 返回客户端视图。
     *
     * @return 视图
     */
    public NetStoreView<T> view() {
        return view;
    }

    /**
     * 返回当前状态。
     *
     * @return 状态
     */
    public synchronized T get() {
        return state;
    }

    /**
     * 服务端修改状态并广播快照。
     *
     * @param mutator 修改函数
     */
    public synchronized void mutate(StoreMutator<T> mutator) {
        mutator.mutate(state);
        service.sendStoreSnapshot(this, NetTarget.all(), state);
    }

    boolean canAccess(Object player) {
        return accessControl == null || accessControl.canAccess(player, this);
    }

    void receiveSnapshot(T snapshot) {
        synchronized (this) {
            this.state = snapshot;
        }
        view.update(snapshot);
    }

    /**
     * Store 注册构造器。
     *
     * @param <T> 状态类型
     */
    public static final class Builder<T> {

        private final NetService service;
        private final NetStoreId id;
        private final Class<T> stateType;
        private NetStoreScope scope = NetStoreScope.GLOBAL;
        private T initial;
        private AccessControl accessControl;

        Builder(NetService service, NetStoreId id, Class<T> stateType) {
            this.service = service;
            this.id = id;
            this.stateType = stateType;
        }

        public Builder<T> scope(NetStoreScope scope) {
            this.scope = Objects.requireNonNull(scope, "scope");
            return this;
        }

        public Builder<T> initial(T initial) {
            this.initial = initial;
            return this;
        }

        public Builder<T> accessControl(AccessControl accessControl) {
            this.accessControl = accessControl;
            return this;
        }

        public NetStore<T> register() {
            return service.registerStore(new NetStore<T>(service, id, stateType, scope, initial, accessControl));
        }
    }

    /**
     * Store 修改函数。
     *
     * @param <T> 状态类型
     */
    public interface StoreMutator<T> {

        /**
         * 修改状态。
         *
         * @param state 状态
         */
        void mutate(T state);
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
        boolean canAccess(Object player, NetStore<?> store);
    }
}
