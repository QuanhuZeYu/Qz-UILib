package club.heiqi.uilib.net.api;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.net.client.NetStoreUiBridge;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * `NetStoreUiBridge` 的客户端主线程投递测试。
 */
public class NetStoreUiBridgeTest {

    private NetService service;

    @Before
    public void setUp() {
        service = NetService.getInstance();
        service.resetForTests();
    }

    @After
    public void tearDown() {
        service.resetForTests();
    }

    @Test
    public void shouldRenderStoreSnapshotOnClientMainThreadQueue() {
        NetStore store = service.store(NetStoreId.of("test", "dom"))
                .initialJson("{\"value\":1}")
                .register();
        UiDocument document = UiDocument.create();
        final ElementNode element = document.div();

        NetStoreUiBridge.getInstance().bind(store.view(), element, new NetStoreUiBridge.NetStoreRenderer() {
            @Override
            public void render(ElementNode element, NetBody snapshot) {
                element.setAttribute("data-rendered", snapshot.asUtf8String());
            }
        });

        Assert.assertNull(element.getAttribute("data-rendered"));
        service.drainClientMainThreadTasks();

        Assert.assertEquals("{\"value\":1}", element.getAttribute("data-rendered"));
    }
}
