package club.heiqi.uilib.font;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.event.FontReloadRequest;

/**
 * 字体重载请求合并测试。
 */
public class FontReloadDebouncerTest {

    /**
     * 验证首个请求可立即执行，快速重复请求会被合并。
     */
    @Test
    public void shouldCoalesceRapidReloadRequests() {
        FontReloadDebouncer debouncer = new FontReloadDebouncer(100L, 500L);

        Assert.assertNotNull(debouncer.request(new FontReloadRequest("first"), 1000L));
        Assert.assertNull(debouncer.request(new FontReloadRequest("second"), 1020L));
        Assert.assertNull(debouncer.request(new FontReloadRequest("third"), 1040L));

        Assert.assertEquals(2, debouncer.getPendingCount());
        Assert.assertNull(debouncer.pollReady(1100L));
        FontReloadRequest readyRequest = debouncer.pollReady(1140L);
        Assert.assertNotNull(readyRequest);
        Assert.assertTrue(readyRequest.getReason().contains("third"));
        Assert.assertTrue(readyRequest.getReason().contains("coalesced=2"));
    }

    /**
     * 验证持续抖动时最大延迟会强制执行 pending 请求。
     */
    @Test
    public void shouldFlushPendingReloadWhenMaxDelayElapsed() {
        FontReloadDebouncer debouncer = new FontReloadDebouncer(100L, 250L);

        Assert.assertNotNull(debouncer.request(new FontReloadRequest("first"), 1000L));
        Assert.assertNull(debouncer.request(new FontReloadRequest("second"), 1020L));
        Assert.assertNull(debouncer.request(new FontReloadRequest("third"), 1100L));
        Assert.assertNull(debouncer.request(new FontReloadRequest("fourth"), 1200L));

        FontReloadRequest readyRequest = debouncer.pollReady(1270L);
        Assert.assertNotNull(readyRequest);
        Assert.assertTrue(readyRequest.getReason().contains("fourth"));
        Assert.assertEquals(0, debouncer.getPendingCount());
    }
}
