package club.heiqi.uilib.net.core;

import org.junit.Assert;
import org.junit.Test;

/**
 * 网络层大小策略测试。
 */
public class NetPayloadLimitsTest {

    @Test
    public void shouldKeepModernLimitsWith32kCompatibilityFloor() {
        Assert.assertEquals(32766, NetPayloadLimits.COMPAT_PHYSICAL_FRAME_LIMIT);
        Assert.assertEquals(8 * 1024 * 1024, NetPayloadLimits.LARGE_MESSAGE_WARN_THRESHOLD);
        Assert.assertEquals(16 * 1024 * 1024, NetPayloadLimits.DEFAULT_LOGICAL_MESSAGE_LIMIT);
        Assert.assertEquals(256 * 1024 * 1024, NetPayloadLimits.GTNH_DEFAULT_PHYSICAL_LIMIT);
        Assert.assertEquals(1024 * 1024 * 1024, NetPayloadLimits.GTNH_HARD_PHYSICAL_LIMIT);
        Assert.assertEquals(256 * 1024 * 1024, NetPayloadLimits.DEFAULT_STREAM_CONTENT_LIMIT);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectOrdinaryMessageAboveLogicalLimit() {
        NetPayloadLimits.requireLogicalMessageSize(NetPayloadLimits.DEFAULT_LOGICAL_MESSAGE_LIMIT + 1);
    }

    @Test
    public void shouldClampStreamContentLimitToHardCap() {
        Assert.assertEquals(NetPayloadLimits.GTNH_HARD_PHYSICAL_LIMIT,
                NetPayloadLimits.clampStreamContentLimit(((long) NetPayloadLimits.GTNH_HARD_PHYSICAL_LIMIT) + 1L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectStreamContentAboveEndpointLimit() {
        NetPayloadLimits.requireStreamContentSize(NetPayloadLimits.DEFAULT_STREAM_CONTENT_LIMIT + 1L,
                NetPayloadLimits.DEFAULT_STREAM_CONTENT_LIMIT);
    }
}
