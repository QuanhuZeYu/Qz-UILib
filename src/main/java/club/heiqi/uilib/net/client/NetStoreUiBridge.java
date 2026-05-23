package club.heiqi.uilib.net.client;

import club.heiqi.uilib.net.api.NetBody;
import club.heiqi.uilib.net.api.NetStoreView;
import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * Store 到 HTML-like DOM 的客户端桥。
 */
public final class NetStoreUiBridge {

    private static final NetStoreUiBridge INSTANCE = new NetStoreUiBridge();

    private volatile boolean initialized;

    private NetStoreUiBridge() {}

    /**
     * 返回单例。
     *
     * @return 桥接器
     */
    public static NetStoreUiBridge getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化客户端桥。
     */
    public void initialize() {
        initialized = true;
    }

    /**
     * 将 Store 视图绑定到 DOM 元素。
     *
     * @param view Store 视图
     * @param element DOM 元素
     * @param renderer 渲染器
     */
    public void bind(NetStoreView view, final ElementNode element, final NetStoreRenderer renderer) {
        if (!initialized) {
            initialize();
        }
        view.subscribe(new NetStoreView.NetStoreSubscriber() {
            @Override
            public void onSnapshot(NetBody snapshot) {
                renderer.render(element, snapshot);
            }
        });
    }

    /**
     * Store 快照到 DOM 的渲染函数。
     */
    public interface NetStoreRenderer {

        /**
         * 渲染快照。
         *
         * @param element DOM 元素
         * @param snapshot 快照
         */
        void render(ElementNode element, NetBody snapshot);
    }
}
