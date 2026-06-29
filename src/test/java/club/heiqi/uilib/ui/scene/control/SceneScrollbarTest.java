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
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * SceneScrollbar 单元测试 —— 验证派生几何算法、失效级别（I4 双轨核对 / COMPOSITE 级零重排）、边界。
 *
 * <p>测试用 {@code contentChangedSignal.set(...) + runtime.flush()} 模拟调用方在 content 高度变化时
 * bump 通知 scrollbar 重算几何。doFrame 顺序：flush（物化首帧 effect）→ layout（产出 LayoutBox）
 * → bump contentChangedSignal → flush（驱动 effect 读最新 LayoutBox）。</p>
 *
 * <p>M2 修复：{@code scrollShouldOnlyChangeTransformNotHeight} 用 {@link SceneNode#__isCompositeDirty()}
 * 与 {@link SceneNode#__isSelfLayoutDirty()} 探针断言脏级别，取代旧版仅断言值相等的假阳性写法。</p>
 */
public class SceneScrollbarTest {

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int BAR_WIDTH = 4;
    private static final int MIN_THUMB = 20;

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    /** content 高度变化通知 signal（模拟 ConfigScreen 的 activeSectionSignal）。 */
    private Signal<Integer> contentChangedSignal;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer());
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer());
        sceneRoot = new SceneNode();
        contentChangedSignal = Signal.create(Integer.valueOf(0));
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
                viewport, scrollSignal, contentChangedSignal,
                SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                BAR_WIDTH, MIN_THUMB);
        SceneScrollbar.Result sb = SceneScrollbar.create(runtime, props);
        sceneRoot.appendChild(sb.column());
        return new ScrollSetup(viewport, scrollSignal, sb);
    }

    /**
     * 执行 layout + bump contentChangedSignal + flush + layout（模拟宿主帧循环 +
     * 调用方在 content 变化时通知 scrollbar，让 effect 读到最新 LayoutBox，最后 layout 清脏标记）。
     *
     * <p>顺序对齐真实宿主 render 流（flush→layout）并补一次末尾 layout 清脏：
     * layout 产出 LayoutBox → bump contentChangedSignal → flush 驱动 effect 重跑读 LayoutBox
     * → layout 清掉 effect 写入的 selfLayoutDirty，使树回到干净态供探针断言。</p>
     */
    private void doFrame() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        // bump contentChangedSignal 驱动 scrollbar effect 重跑读最新 LayoutBox
        contentChangedSignal.set(Integer.valueOf(contentChangedSignal.get().intValue() + 1));
        runtime.flush();
        // 末尾 layout：清掉 effect 写入的 selfLayoutDirty，使树回到干净态
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
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

    // ==================== M2：column 可命中 + 滚轮转发 ====================

    @Test
    public void columnShouldBeHitTestableForScrollForwarding() {
        ScrollSetup setup = build(200, 600);
        // M2：column setHitTestable(true)，使滚轮事件命中 column 并转发到 scrollSignal
        Assert.assertTrue("column hitTestable=true（M2 滚轮转发）", setup.scrollbar.column().isHitTestable());
    }

    @Test
    public void defaultBarWidthShouldBeWidened() {
        // M2：DEFAULT_BAR_WIDTH 加宽到 8（原 4）
        Assert.assertEquals("DEFAULT_BAR_WIDTH=8", 8, SceneScrollbar.DEFAULT_BAR_WIDTH);
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

    // ==================== 验收 5：滚动零重排（COMPOSITE 级）+ I4 双轨探针断言 ====================

    /**
     * 滚动只应触发 COMPOSITE 级 transform 变化，thumb 高度不变（preferredHeight 去重不标 LAYOUT）。
     *
     * <p>M2 修复：用 {@link SceneNode#__isCompositeDirty()} 与 {@link SceneNode#__isSelfLayoutDirty()}
     * 探针断言脏级别，取代旧版仅断言 {@code getPreferredHeight()} 前后值相等的假阳性写法——
     * 旧版证明不了 effect 没每帧重算、证明不了没标 LAYOUT 脏。</p>
     *
     * <p>拆 bind 后（M1）：LAYOUT bind 只订阅 contentChangedSignal，滚动时 contentChangedSignal 不变，
     * LAYOUT bind 不跑，thumb 不标 selfLayoutDirty；COMPOSITE bind 订阅 scrollSignal，滚动时重跑，
     * setTransform 标 compositeDirty。双轨核对成立（I4）。</p>
     */
    @Test
    public void scrollShouldOnlyChangeTransformNotHeight() {
        ScrollSetup setup = build(200, 600);
        doFrame();
        int thumbHBefore = setup.scrollbar.thumb().getPreferredHeight();

        // 滚动：只写 scrollSignal，不 bump contentChangedSignal
        setup.scrollSignal.set(Integer.valueOf(200));
        runtime.flush(); // 物化 COMPOSITE bind（不跑 layout，保留脏标记供探针断言）

        // 探针断言 1：thumb compositeDirty=true（transform 应该标脏）
        Assert.assertTrue("滚动后 thumb __isCompositeDirty()=true（transform 标 COMPOSITE 脏）",
                setup.scrollbar.thumb().__isCompositeDirty());
        // 探针断言 2：thumb selfLayoutDirty=false（height 没变，LAYOUT bind 没跑，不应标 LAYOUT 脏）
        Assert.assertFalse("滚动后 thumb __isSelfLayoutDirty()=false（LAYOUT bind 未跑，height 未变不标 LAYOUT 脏）",
                setup.scrollbar.thumb().__isSelfLayoutDirty());

        // 跑 layout 清掉 layout 脏后，再断言值层面（height 不变 + translateY > 0）
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("滚动后 thumb 高不变", thumbHBefore,
                setup.scrollbar.thumb().getPreferredHeight());
        Assert.assertTrue("滚动后 thumb transform translateY > 0",
                setup.scrollbar.thumb().getTransform().translateY > 0f);
    }
}
