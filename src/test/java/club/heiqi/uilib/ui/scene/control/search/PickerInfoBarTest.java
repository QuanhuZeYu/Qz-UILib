package club.heiqi.uilib.ui.scene.control.search;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * {@link PickerInfoBar} 单元测试。
 *
 * <p>覆盖：外壳结构（固定高、实底背景、圆角、非命中）、文本绑定随信号变化同步、常驻空文本挂载。</p>
 */
public class PickerInfoBarTest {

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private Signal<String> text;
    private Signal<Boolean> enabled;

    private static final int W = 400;
    private static final int H = 300;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        text = Signal.create("占位文本");
        enabled = Signal.create(Boolean.TRUE);
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 在 mount 作用域内构建信息条（mount 自动 append 到 sceneRoot），再布局并回刷。 */
    private SceneNode mountBar() {
        SceneNode bar = rt.mount(sceneRoot,
                () -> PickerInfoBar.create(rt, new PickerInfoBar.Props(text, enabled))).getRoot();
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
        return bar;
    }

    @Test
    public void structureHasFixedHeightBackgroundAndRadius() {
        SceneNode bar = mountBar();
        Assert.assertEquals("固定高 24", PickerInfoBar.INFO_BAR_HEIGHT, bar.getPreferredHeight());
        Assert.assertNotEquals("实底背景非透明", 0, bar.getBackgroundColor());
        Assert.assertTrue("圆角非 0", bar.getCornerRadius() > 0);
        Assert.assertFalse("信息条不参与命中", bar.isHitTestable());
    }

    @Test
    public void textSignalUpdatesChildText() {
        SceneNode bar = mountBar();
        List<SceneNode> children = bar.__getChildren();
        Assert.assertEquals("单文本子节点", 1, children.size());
        SceneNode label = children.get(0);
        Assert.assertEquals("初始文本同步", "占位文本", label.getText());

        text.set("新的提示");
        rt.flush();
        Assert.assertEquals("文本信号变化后子节点同步", "新的提示", label.getText());
    }

    @Test
    public void emptyTextStaysMounted() {
        text.set("");
        SceneNode bar = mountBar();
        Assert.assertEquals("常驻挂载，空文本仍保留子节点", 1, bar.__getChildren().size());
        Assert.assertEquals("空文本显示空串", "", bar.__getChildren().get(0).getText());
        Assert.assertEquals("空文本仍使用次要文本色", SceneChromeTokens.TEXT_SECONDARY,
                bar.__getChildren().get(0).getTextColor());
    }
}
