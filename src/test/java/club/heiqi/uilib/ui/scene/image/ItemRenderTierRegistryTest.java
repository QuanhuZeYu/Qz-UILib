package club.heiqi.uilib.ui.scene.image;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link ItemRenderTierRegistry} 单元测试（纯状态机，无 GL）。
 *
 * <p>覆盖：追踪三次全部干净 → RENDERABLE；追踪期出现异常 → UNRENDERABLE（优先于 GL 错误）；
 * GL 错误 → NEEDS_ISOLATION；隔离态连续异常达阈值 → 升级 UNRENDERABLE、干净渲染重置计数；
 * tierOf 未知键为 TRACKING；监听器只在分级变化时收到通知；resetForTests 清零。</p>
 */
public class ItemRenderTierRegistryTest {

    @Before
    public void setUp() {
        ItemRenderTierRegistry.resetForTests();
    }

    @After
    public void tearDown() {
        ItemRenderTierRegistry.resetForTests();
    }

    @Test
    public void threeCleanAttemptsBecomeRenderable() {
        Assert.assertEquals(ItemRenderTierRegistry.Tier.TRACKING,
                ItemRenderTierRegistry.tierOf("a:b:0"));
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.OK, null);
        Assert.assertEquals("两次干净仍追踪", ItemRenderTierRegistry.Tier.TRACKING,
                ItemRenderTierRegistry.tierOf("a:b:0"));
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.OK, null);
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.OK, null);
        Assert.assertEquals("三次干净 → 可渲染", ItemRenderTierRegistry.Tier.RENDERABLE,
                ItemRenderTierRegistry.tierOf("a:b:0"));
        // 分级永久记忆：再上报异常也不回退
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
        Assert.assertEquals(ItemRenderTierRegistry.Tier.RENDERABLE,
                ItemRenderTierRegistry.tierOf("a:b:0"));
    }

    @Test
    public void exceptionDuringTrackingBecomesUnrenderable() {
        List<ItemRenderTierRegistry.Classification> changes = new ArrayList<>();
        ItemRenderTierRegistry.addListener(changes::add);
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "boom");
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.OK, null);
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.OK, null);
        Assert.assertEquals("追踪期异常优先 → 不可渲染", ItemRenderTierRegistry.Tier.UNRENDERABLE,
                ItemRenderTierRegistry.tierOf("a:b:0"));
        Assert.assertEquals(1, changes.size());
        Assert.assertEquals(ItemRenderTierRegistry.Tier.UNRENDERABLE, changes.get(0).tier());
        Assert.assertEquals("boom", changes.get(0).detail());
        Assert.assertEquals("a:b:0", changes.get(0).registryKey());
    }

    @Test
    public void glErrorDuringTrackingBecomesNeedsIsolation() {
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.GL_ERROR, "0x500");
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.OK, null);
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.OK, null);
        Assert.assertEquals("GL 错误但无异常 → 需隔离", ItemRenderTierRegistry.Tier.NEEDS_ISOLATION,
                ItemRenderTierRegistry.tierOf("a:b:0"));
    }

    @Test
    public void isolationFailuresUpgradeToUnrenderableAndCleanResets() {
        // 先进入 NEEDS_ISOLATION
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.GL_ERROR, "0x500");
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.OK, null);
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.OK, null);
        Assert.assertEquals(ItemRenderTierRegistry.Tier.NEEDS_ISOLATION,
                ItemRenderTierRegistry.tierOf("a:b:0"));
        // 连续两次异常：仍隔离
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "e1");
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "e2");
        Assert.assertEquals(ItemRenderTierRegistry.Tier.NEEDS_ISOLATION,
                ItemRenderTierRegistry.tierOf("a:b:0"));
        // 第三次异常：升级不可渲染
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "e3");
        Assert.assertEquals(ItemRenderTierRegistry.Tier.UNRENDERABLE,
                ItemRenderTierRegistry.tierOf("a:b:0"));
        // 干净渲染重置连续计数：另一键先隔离，异常两次后干净一次，再异常两次仍隔离
        ItemRenderTierRegistry.classify("c:d:0", ItemRenderTierRegistry.Outcome.GL_ERROR, "0x500");
        ItemRenderTierRegistry.classify("c:d:0", ItemRenderTierRegistry.Outcome.OK, null);
        ItemRenderTierRegistry.classify("c:d:0", ItemRenderTierRegistry.Outcome.OK, null);
        ItemRenderTierRegistry.classify("c:d:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "e1");
        ItemRenderTierRegistry.classify("c:d:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "e2");
        ItemRenderTierRegistry.classify("c:d:0", ItemRenderTierRegistry.Outcome.OK, null);
        ItemRenderTierRegistry.classify("c:d:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "e3");
        ItemRenderTierRegistry.classify("c:d:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "e4");
        Assert.assertEquals("干净渲染重置计数后两次异常仍隔离",
                ItemRenderTierRegistry.Tier.NEEDS_ISOLATION, ItemRenderTierRegistry.tierOf("c:d:0"));
    }

    @Test
    public void listenerOnlyNotifiedOnTierChange() {
        List<ItemRenderTierRegistry.Classification> changes = new ArrayList<>();
        ItemRenderTierRegistry.Listener listener = changes::add;
        ItemRenderTierRegistry.addListener(listener);
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.OK, null);
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.OK, null);
        Assert.assertTrue("追踪中不通知", changes.isEmpty());
        ItemRenderTierRegistry.classify("a:b:0", ItemRenderTierRegistry.Outcome.OK, null);
        Assert.assertEquals("分级变化只通知一次", 1, changes.size());
        // 移除监听器后不再通知
        ItemRenderTierRegistry.removeListener(listener);
        ItemRenderTierRegistry.classify("e:f:0", ItemRenderTierRegistry.Outcome.EXCEPTION, "x");
        ItemRenderTierRegistry.classify("e:f:0", ItemRenderTierRegistry.Outcome.OK, null);
        ItemRenderTierRegistry.classify("e:f:0", ItemRenderTierRegistry.Outcome.OK, null);
        Assert.assertEquals(1, changes.size());
    }

    @Test
    public void unknownKeyTracksAndNullSafe() {
        Assert.assertEquals(ItemRenderTierRegistry.Tier.TRACKING, ItemRenderTierRegistry.tierOf("x:y:0"));
        Assert.assertEquals(ItemRenderTierRegistry.Tier.TRACKING, ItemRenderTierRegistry.tierOf(null));
        ItemRenderTierRegistry.classify(null, ItemRenderTierRegistry.Outcome.OK, null);
        ItemRenderTierRegistry.classify("x:y:0", null, null);
        Assert.assertEquals("null 键/结果不影响状态", ItemRenderTierRegistry.Tier.TRACKING,
                ItemRenderTierRegistry.tierOf("x:y:0"));
    }
}
