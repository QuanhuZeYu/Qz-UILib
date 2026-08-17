package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * SceneNavList preferredHeight prop 透传测试 —— 滚动 API 易用性优化第一批收尾（Oracle C P1 补全）。
 *
 * <p>验证 Oracle A 规划的 NavList preferredHeight prop 契约：
 * 非 null 时透传 {@code root.setPreferredHeight}，null 时不设、由布局链决定。
 * Oracle A 裁决：NavList 不内置自动推算（纵向 N 项高度随项数变化，强行推算与 fill 语义冲突），
 * 故高度由调用方经此 prop 显式提供，或交布局链推导。</p>
 */
public class SceneNavListTest {

    private SceneRuntime runtime;
    private static final List<String> OPTIONS = Arrays.asList("General", "Video", "Audio");

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer(8, 16));
    }

    @After
    public void tearDown() {
        if (runtime != null) {
            runtime.dispose();
        }
        ReactiveScheduler.get().reset();
    }

    /** preferredHeight 非 null 时应透传到 root。 */
    @Test
    public void nonNullPreferredHeightShouldPassThroughToRoot() {
        SceneNode parent = new SceneNode();
        SceneNavList.Props props = new SceneNavList.Props(
                Signal.create(Integer.valueOf(0)),
                OPTIONS,
                Signal.create(Boolean.TRUE),
                idx -> { },
                Integer.valueOf(200));
        MountHandle handle = runtime.mount(parent, SceneNavList.create(runtime, props));
        runtime.flush();
        Assert.assertEquals("preferredHeight=200 应透传到 root", 200, handle.getRoot().getPreferredHeight());
    }

    /** preferredHeight 为 null 时 root 保持默认 0（不设，由布局链决定）。 */
    @Test
    public void nullPreferredHeightShouldLeaveRootDefault() {
        SceneNode parent = new SceneNode();
        SceneNavList.Props props = new SceneNavList.Props(
                Signal.create(Integer.valueOf(0)),
                OPTIONS,
                Signal.create(Boolean.TRUE),
                idx -> { },
                null);
        MountHandle handle = runtime.mount(parent, SceneNavList.create(runtime, props));
        runtime.flush();
        Assert.assertEquals("preferredHeight=null 时 root 保持默认 0", 0, handle.getRoot().getPreferredHeight());
    }

    @Test
    public void selectionMotionShouldMoveIndicatorAndLabelAtStandardDuration() {
        runtime.__enableMotion();
        Signal<Integer> selected = Signal.create(Integer.valueOf(0));
        SceneNode parent = new SceneNode();
        SceneNavList.Props props = new SceneNavList.Props(
                selected, OPTIONS, Signal.create(Boolean.TRUE), idx -> { }, null);
        SceneNode root = runtime.mount(parent, SceneNavList.create(runtime, props)).getRoot();
        runtime.flush();
        SceneNode first = root.__getChildren().get(0);
        SceneNode second = root.__getChildren().get(1);
        SceneNode firstIndicator = first.__getChildren().get(0);
        SceneNode firstLabel = first.__getChildren().get(1);
        SceneNode secondIndicator = second.__getChildren().get(0);
        SceneNode secondLabel = second.__getChildren().get(1);
        Assert.assertEquals(SceneChromeTokens.ACCENT, first.getBackgroundColor());
        Assert.assertEquals(SceneChromeTokens.BG_DEFAULT, second.getBackgroundColor());
        Assert.assertEquals(1.0f, firstIndicator.getTransform().scaleY, 0.0001f);
        Assert.assertEquals(0.0f, secondIndicator.getTransform().scaleY, 0.0001f);
        Assert.assertEquals(4.0f, firstLabel.getTransform().translateX, 0.0001f);
        Assert.assertEquals(0.0f, secondLabel.getTransform().translateX, 0.0001f);
        Assert.assertTrue("交互命中保持在完整 item", first.isHitTestable());
        Assert.assertFalse("indicator 不得截获 item 点击", firstIndicator.isHitTestable());
        Assert.assertFalse("位移 label 不得改变 item 点击目标", firstLabel.isHitTestable());

        selected.set(Integer.valueOf(1));
        runtime.flush();
        runtime.__sampleMotion(1_000_000L);
        runtime.__sampleMotion(81_000_000L);
        Assert.assertNotEquals(SceneChromeTokens.ACCENT, first.getBackgroundColor());
        Assert.assertNotEquals(SceneChromeTokens.BG_DEFAULT, second.getBackgroundColor());
        Assert.assertEquals("旧 indicator 半程收起", 0.5f,
                firstIndicator.getTransform().scaleY, 0.0001f);
        Assert.assertEquals("新 indicator 半程展开", 0.5f,
                secondIndicator.getTransform().scaleY, 0.0001f);
        Assert.assertEquals("旧 label 半程归位", 2.0f,
                firstLabel.getTransform().translateX, 0.0001f);
        Assert.assertEquals("新 label 半程移入", 2.0f,
                secondLabel.getTransform().translateX, 0.0001f);

        runtime.__sampleMotion(161_000_000L);
        Assert.assertEquals(SceneChromeTokens.BG_DEFAULT, first.getBackgroundColor());
        Assert.assertEquals(SceneChromeTokens.ACCENT, second.getBackgroundColor());
        Assert.assertEquals(0.0f, firstIndicator.getTransform().scaleY, 0.0001f);
        Assert.assertEquals(1.0f, secondIndicator.getTransform().scaleY, 0.0001f);
        Assert.assertEquals(0.0f, firstLabel.getTransform().translateX, 0.0001f);
        Assert.assertEquals(4.0f, secondLabel.getTransform().translateX, 0.0001f);
    }
}
