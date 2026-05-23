package club.heiqi.uilib.net.store;

/**
 * Store 内核服务集合。
 */
public final class StoreEngine {

    private static final StoreEngine INSTANCE = new StoreEngine();

    private final FieldDeltaEncoder fieldDeltaEncoder = new FieldDeltaEncoder();
    private final StoreSubscriptionRegistry subscriptionRegistry = new StoreSubscriptionRegistry();

    private StoreEngine() {}

    /**
     * 返回单例。
     *
     * @return Store 引擎
     */
    public static StoreEngine getInstance() {
        return INSTANCE;
    }

    /**
     * 返回字段增量编码器。
     *
     * @return 编码器
     */
    public FieldDeltaEncoder getFieldDeltaEncoder() {
        return fieldDeltaEncoder;
    }

    /**
     * 返回订阅表。
     *
     * @return 订阅表
     */
    public StoreSubscriptionRegistry getSubscriptionRegistry() {
        return subscriptionRegistry;
    }
}
