package club.heiqi.uilib.net.api;

import java.util.Objects;

/**
 * 内容语义状态同步 Store。
 */
public final class NetStore {

    private final NetService service;
    private final NetStoreId id;
    private final NetStoreScope scope;
    private final AccessControl accessControl;
    private final NetStoreView view;
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
     * 服务端替换状态并广播快照。
     *
     * @param next 新状态
     */
    public synchronized void set(NetBody next) {
        state = Objects.requireNonNull(next, "next");
        service.sendStoreSnapshot(this, NetTarget.all(), state);
    }

    /**
     * 基于当前 body 计算新 body 并广播。
     *
     * @param mutator 修改函数
     */
    public synchronized void mutate(StoreMutator mutator) {
        set(Objects.requireNonNull(mutator, "mutator").mutate(state));
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
