package club.heiqi.uilib.font.page;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/**
 * {@link GlyphPageManager} 请求状态机契约测试（审查 4.1 安全网）：
 * claim → QUEUED → RASTERIZING → CANCELLED_STALE/FAILED 的转换、防重、优先级与输入校验。
 * 无 owner 独立对象（FontRuntimeAccess.isActive(null)=true），纯 headless。
 */
public class GlyphPageManagerStateMachineTest {

    private static GlyphPageManager newManager() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.initialize();
        manager.setGeneration(1, FontRuntimeSettings.capture());
        return manager;
    }

    @Test
    public void claimAssignsQueuedStateAndRejectsDuplicate() {
        GlyphPageManager manager = newManager();

        GlyphRequestToken token = manager.claimRequest(1, 'A', FontType.NORMAL);

        Assert.assertNotNull(token);
        Assert.assertEquals(GlyphState.QUEUED, manager.getTokenState(token));
        Assert.assertEquals(GlyphState.QUEUED, manager.getState('A', FontType.NORMAL));
        Assert.assertTrue(manager.hasActiveDemand(1, 'A', FontType.NORMAL));
        Assert.assertNull("活动请求存在时不得重复 claim",
                manager.claimRequest(1, 'A', FontType.NORMAL));
    }

    @Test
    public void rasterizingTransitionRequiresQueuedState() {
        GlyphPageManager manager = newManager();
        GlyphRequestToken token = manager.claimRequest(1, 'A', FontType.NORMAL);

        Assert.assertTrue(manager.markRasterizing(token));
        Assert.assertEquals(GlyphState.RASTERIZING, manager.getTokenState(token));
        Assert.assertFalse("已 RASTERIZING 不得重复从 QUEUED 转换", manager.markRasterizing(token));
    }

    @Test
    public void cancelledSettlesToStaleAndAllowsReclaim() {
        GlyphPageManager manager = newManager();
        GlyphRequestToken token = manager.claimRequest(1, 'A', FontType.NORMAL);
        Assert.assertTrue(manager.markRasterizing(token));

        Assert.assertTrue(manager.markCancelled(token, GlyphState.RASTERIZING));
        Assert.assertEquals(GlyphState.CANCELLED_STALE, manager.getTokenState(token));
        Assert.assertFalse(manager.hasActiveDemand(1, 'A', FontType.NORMAL));
        Assert.assertFalse("expected 不匹配不得取消",
                manager.markCancelled(token, GlyphState.RASTERIZING));

        GlyphRequestToken reclaimed = manager.claimRequest(1, 'A', FontType.NORMAL);
        Assert.assertNotNull("CANCELLED_STALE 非 active/ready，应可重新 claim", reclaimed);
        Assert.assertEquals(GlyphState.QUEUED, manager.getTokenState(reclaimed));
    }

    @Test
    public void failedSettlesAndAllowsReclaim() {
        GlyphPageManager manager = newManager();
        GlyphRequestToken token = manager.claimRequest(1, 'A', FontType.NORMAL);
        Assert.assertTrue(manager.markRasterizing(token));

        Assert.assertTrue(manager.markFailed(token, GlyphState.RASTERIZING));
        Assert.assertEquals(GlyphState.FAILED, manager.getTokenState(token));
        Assert.assertFalse(manager.hasActiveDemand(1, 'A', FontType.NORMAL));
        Assert.assertNotNull("FAILED 非 active/ready，应可重新 claim",
                manager.claimRequest(1, 'A', FontType.NORMAL));
    }

    @Test
    public void promoteDemandOnlyRaisesPriority() {
        GlyphPageManager manager = newManager();
        GlyphRequestToken token = manager.claimRequest(1, 'B', FontType.NORMAL, 1);
        Assert.assertNotNull(token);

        GlyphRequestToken raised = manager.promoteDemand(1, 'B', FontType.NORMAL, 3);
        Assert.assertSame("提升优先级返回同一 token", token, raised);
        Assert.assertNull("同级不返回 token", manager.promoteDemand(1, 'B', FontType.NORMAL, 1));
        Assert.assertNull("无 active 请求不返回 token", manager.promoteDemand(1, 'C', FontType.NORMAL, 3));
    }

    @Test
    public void claimRejectsInvalidInputs() {
        GlyphPageManager manager = newManager();

        Assert.assertNull("过期 generation 拒绝", manager.claimRequest(999, 'A', FontType.NORMAL));
        Assert.assertNull("非法码点拒绝", manager.claimRequest(1, -1, FontType.NORMAL));
        Assert.assertNull("null 字重拒绝", manager.claimRequest(1, 'A', null));
        Assert.assertNull("非法优先级拒绝", manager.claimRequest(1, 'A', FontType.NORMAL, -7));
    }
}
