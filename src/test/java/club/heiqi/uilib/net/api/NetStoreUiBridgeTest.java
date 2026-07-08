package club.heiqi.uilib.net.api;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.net.client.NetStoreUiBridge;

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
        final StringBuilder rendered = new StringBuilder();
        final Object element = new Object();

        NetStoreUiBridge.getInstance().bind(store.view(), element, new NetStoreUiBridge.NetStoreRenderer() {
            @Override
            public void render(Object node, NetBody snapshot) {
                Assert.assertSame(element, node);
                rendered.append(snapshot.asUtf8String());
            }
        });

        Assert.assertEquals("", rendered.toString());
        service.drainClientMainThreadTasks();

        Assert.assertEquals("{\"value\":1}", rendered.toString());
    }
}
