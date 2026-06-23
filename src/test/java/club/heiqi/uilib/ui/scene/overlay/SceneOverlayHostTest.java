package club.heiqi.uilib.ui.scene.overlay;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link SceneOverlayHost} 的注册、摘除和栈序测试。
 */
public class SceneOverlayHostTest {

    /**
     * 验证注册后 bottom-first 保持注册顺序，top-first 反向返回栈顶优先顺序。
     */
    @Test
    public void shouldExposeBottomFirstAndTopFirstSnapshots() {
        SceneOverlayHost host = new SceneOverlayHost();
        SceneNode first = new SceneNode();
        SceneNode second = new SceneNode();
        SceneNode third = new SceneNode();

        host.register(first);
        host.register(second);
        host.register(third);

        List<SceneOverlayHost.Entry> bottomFirst = host.bottomFirst();
        List<SceneOverlayHost.Entry> topFirst = host.topFirst();

        Assert.assertSame(first, bottomFirst.get(0).getRoot());
        Assert.assertSame(second, bottomFirst.get(1).getRoot());
        Assert.assertSame(third, bottomFirst.get(2).getRoot());
        Assert.assertSame(third, topFirst.get(0).getRoot());
        Assert.assertSame(second, topFirst.get(1).getRoot());
        Assert.assertSame(first, topFirst.get(2).getRoot());
    }

    /**
     * 验证 {@link OverlayHandle#dispose()} 幂等，重复调用只摘除一次。
     */
    @Test
    public void disposeShouldBeIdempotent() {
        SceneOverlayHost host = new SceneOverlayHost();
        OverlayHandle handle = host.register(new SceneNode());

        handle.dispose();
        handle.dispose();

        Assert.assertTrue(handle.isDisposed());
        Assert.assertTrue(host.isEmpty());
    }

    /**
     * 验证快照不可变，且不暴露 host 内部列表。
     */
    @Test(expected = UnsupportedOperationException.class)
    public void snapshotsShouldBeImmutable() {
        SceneOverlayHost host = new SceneOverlayHost();
        host.register(new SceneNode());

        host.bottomFirst().clear();
    }

    /**
     * 验证旧快照不随后续注册变化，避免外部持有内部可变列表。
     */
    @Test
    public void snapshotsShouldNotExposeMutableInternalList() {
        SceneOverlayHost host = new SceneOverlayHost();
        List<SceneOverlayHost.Entry> snapshot = host.bottomFirst();

        host.register(new SceneNode());

        Assert.assertTrue(snapshot.isEmpty());
        Assert.assertEquals(1, host.size());
    }

    /**
     * 验证关闭请求只调用注册方回调，不直接摘除 entry。
     */
    @Test
    public void dismissRequestShouldNotRemoveEntryDirectly() {
        SceneOverlayHost host = new SceneOverlayHost();
        AtomicInteger requestCount = new AtomicInteger();
        OverlayHandle handle = host.register(new SceneNode(), OverlayDismissPolicy.DEFAULT, requestCount::incrementAndGet);

        handle.getEntry().requestDismiss();

        Assert.assertEquals(1, requestCount.get());
        Assert.assertEquals(1, host.size());
    }
}
