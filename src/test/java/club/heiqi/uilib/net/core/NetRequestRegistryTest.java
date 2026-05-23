package club.heiqi.uilib.net.core;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.net.api.NetTimeoutException;

/**
 * `NetRequestRegistry` 请求生命周期测试。
 */
public class NetRequestRegistryTest {

    @Test
    public void shouldRemovePendingRequestWhenFutureIsCancelled() {
        NetRequestRegistry registry = new NetRequestRegistry();
        NetRequestRegistry.PendingRequest<String> pending = registry.register(5_000L);

        Assert.assertEquals(1, registry.size());
        Assert.assertTrue(pending.getFuture().cancel(false));

        Assert.assertEquals(0, registry.size());
        registry.complete(pending.getRequestId(), "late");
        Assert.assertTrue(pending.getFuture().isCancelled());
    }

    @Test
    public void shouldNotTimeoutCancelledRequest() {
        NetRequestRegistry registry = new NetRequestRegistry();
        NetRequestRegistry.PendingRequest<String> pending = registry.register(0L);
        pending.getFuture().cancel(false);

        registry.expireTimedOut();

        Assert.assertEquals(0, registry.size());
        Assert.assertTrue(pending.getFuture().isCancelled());
    }

    @Test
    public void shouldTimeoutLiveRequest() {
        NetRequestRegistry registry = new NetRequestRegistry();
        NetRequestRegistry.PendingRequest<String> pending = registry.register(0L);

        registry.expireTimedOut();

        Assert.assertEquals(0, registry.size());
        Assert.assertTrue(pending.getFuture().isCompletedExceptionally());
        try {
            pending.getFuture().join();
            Assert.fail("超时请求应异常完成");
        } catch (RuntimeException expected) {
            Assert.assertTrue(expected.getCause() instanceof NetTimeoutException);
        }
    }
}
