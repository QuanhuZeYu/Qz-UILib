package club.heiqi.uilib.ui.scene.control;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.component.SceneScrolls;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * SceneScrollbar 单元测试 —— 验证派生几何算法、失效级别（COMPOSITE 级零重排）、边界。
 *
 * <p>测试用 {@code runtime.bumpLayoutEpoch() + runtime.flush()} 模拟宿主帧循环中
 * layout 后的纪元 bump，让 scrollbar effect 读到最新 LayoutBox 算派生几何。</p>
 */
public class SceneScrollbarTest {

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int BAR_WIDTH = 4;
    private static final int MIN_THUMB = 20;

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer());
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer());
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 滚动树 + scrollbar 构建产物。 */
    private static final class ScrollSetup {
        final SceneNode viewport;
        final Signal<Integer> scrollSignal;
        final SceneScrollbar.Result scrollbar;

        ScrollSetup(SceneNode viewport, Signal<Integer> scrollSignal, SceneScrollbar.Result scrollbar) {
            this.viewport = viewport;
            this.scrollSignal = scrollSignal;
            this.scrollbar = scrollbar;
        }
    }

    /**
     * 构建指定视口高和内容高的滚动树 + scrollbar，返回含 scrollSignal 引用的产物。
     *
     * @param viewportHeight 视口高度
     * @param contentHeight 内容高度
     * @return 滚动树构建产物
     */
    private ScrollSetup build(int viewportHeight, int contentHeight) {
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(viewportHeight);
        sceneRoot.appendChild(viewport);

        SceneNode content = new SceneNode();
        content.setPreferredHeight(contentHeight);
        viewport.appendChild(content);

        Signal<Integer> scrollSignal = SceneScrolls.attach(runtime, viewport);
        SceneScrollbar.Props props = new SceneScrollbar.Props(
                viewport, scrollSignal,
                SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                BAR_WIDTH, MIN_THUMB);
        SceneScrollbar.Result sb = SceneScrollbar.create(runtime, props);
        sceneRoot.appendChild(sb.column());
        return new ScrollSetup(viewport, scrollSignal, sb);
    }

    /** 执行 flush + layout + bumpLayoutEpoch + flush（模拟宿主帧循环，让 scrollbar effect 读到 LayoutBox）。 */
    private void doFrame() {
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        runtime.bumpLayoutEpoch();
        runtime.flush();
    }

    // ==================== 验收 1：结构 ====================

    @Test
    public void createShouldReturnColumnAndThumb() {
        ScrollSetup setup = build(200, 600);
        SceneScrollbar.Result sb = setup.scrollbar;
        Assert.assertNotNull("column 非空", sb.column());
        Assert.assertNotNull("thumb 非空", sb.thumb());
        Assert.assertEquals("column 含 thumb", 1, sb.column().__getChildren().size());
        Assert.assertSame("column 第一个子是 thumb", sb.thumb(), sb.column().__getChildren().get(0));
    }

    @Test
    public void columnShouldHaveBarWidthAndFillHeightAndClip() {
        ScrollSetup setup = build(200, 600);
        SceneScrollbar.Result sb = setup.scrollbar;
        Assert.assertEquals("column 宽 = barWidth", BAR_WIDTH, sb.column().getPreferredWidth());
        Assert.assertTrue("column fillParentHeight", sb.column().isFillParentHeight());
        Assert.assertTrue("column clipChildren", sb.column().isClipChildren());
    }

    @Test
    public void thumbShouldHaveBarWidthAndThumbColor() {
        ScrollSetup setup = build(200, 600);
        SceneScrollbar.Result sb = setup.scrollbar;
        Assert.assertEquals("thumb 宽 = barWidth", BAR_WIDTH, sb.thumb().getPreferredWidth());
        Assert.assertEquals("thumb 色 = ACCENT", SceneChromeTokens.ACCENT, sb.thumb().getBackgroundColor());
    }

    // ==================== 验收 2：派生几何（有溢出） ====================

    /**
     * viewport=200, content=600 → maxScroll=400, thumbH=200²/600≈66, trackRange=134.
     * scroll=0 → thumbTop=0; scroll=200 → thumbTop=67; scroll=400 → thumbTop=134.
     */
    @Test
    public void thumbGeometryShouldReflectScrollPosition() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int maxScroll = SceneGeometry.maxScrollY(setup.viewport);
        Assert.assertEquals("maxScroll=400", 400, maxScroll);

        int expectedThumb = 200 * 200 / (200 + maxScroll);
        Assert.assertEquals("thumb 高 = 200²/600 = 66", expectedThumb, setup.scrollbar.thumb().getPreferredHeight());

        // scroll=0 → thumbTop=0
        Assert.assertEquals("scroll=0 thumbTop=0", 0f, setup.scrollbar.thumb().getTransform().translateY, 0.5f);

        // 滚动到 200 → thumbTop=134*200/400=67
        setup.scrollSignal.set(Integer.valueOf(200));
        doFrame();
        int trackRange = 200 - expectedThumb;
        Assert.assertEquals("scroll=200 thumbTop=67",
                trackRange * 200 / (float) maxScroll,
                setup.scrollbar.thumb().getTransform().translateY, 0.5f);

        // 滚动到 maxScroll → thumbTop=trackRange=134
        setup.scrollSignal.set(Integer.valueOf(maxScroll));
        doFrame();
        Assert.assertEquals("scroll=max thumbTop=trackRange",
                (float) trackRange, setup.scrollbar.thumb().getTransform().translateY, 0.5f);
    }

    // ==================== 验收 3：无溢出时 thumb 占满 track ====================

    @Test
    public void noOverflowThumbShouldFillTrack() {
        ScrollSetup setup = build(200, 100);
        doFrame();
        LayoutBox vpBox = (LayoutBox) setup.viewport.getCachedLayout();
        Assert.assertEquals("无溢出 thumb 占满 track", vpBox.getHeight(),
                setup.scrollbar.thumb().getPreferredHeight());
        Assert.assertEquals("无溢出 thumbTop=0", 0f,
                setup.scrollbar.thumb().getTransform().translateY, 0.5f);
    }

    // ==================== 验收 4：minThumbHeight 下限 ====================

    /**
     * viewport=200, content=10000 → thumbH=200²/10200≈3 < minThumb=20 → thumb=20.
     */
    @Test
    public void thumbShouldClampToMinThumbHeight() {
        ScrollSetup setup = build(200, 10000);
        doFrame();
        Assert.assertEquals("thumb 高 clamp 到 minThumb=20", MIN_THUMB,
                setup.scrollbar.thumb().getPreferredHeight());
    }

    // ==================== 验收 5：滚动零重排（COMPOSITE 级） ====================

    /**
     * 滚动只应触发 COMPOSITE 级 transform 变化，thumb 高度不变（preferredHeight 去重不标 LAYOUT）。
     */
    @Test
    public void scrollShouldOnlyChangeTransformNotHeight() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int thumbHBefore = setup.scrollbar.thumb().getPreferredHeight();

        setup.scrollSignal.set(Integer.valueOf(200));
        doFrame();

        Assert.assertEquals("滚动后 thumb 高不变", thumbHBefore,
                setup.scrollbar.thumb().getPreferredHeight());
        Assert.assertTrue("滚动后 thumb transform translateY > 0",
                setup.scrollbar.thumb().getTransform().translateY > 0f);
    }
}
