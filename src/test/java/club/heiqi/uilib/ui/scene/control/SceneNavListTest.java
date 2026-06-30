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
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;

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
}
