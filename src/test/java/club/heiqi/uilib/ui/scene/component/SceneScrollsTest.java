package club.heiqi.uilib.ui.scene.component;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneScrolls 滚动能力封装单元测试。
 */
public class SceneScrollsTest {

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer());
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /**
     * attach 应返回初始值为 0 的 scrollSignal。
     */
    @Test
    public void attachShouldReturnInitialZeroSignal() {
        SceneNode viewport = buildViewportWithContent(200, 600);

        Signal<Integer> scrollSignal = SceneScrolls.attach(runtime, viewport);

        Assert.assertNotNull("attach 应返回非 null signal", scrollSignal);
        Assert.assertEquals("scrollSignal 初始值应为 0", Integer.valueOf(0), scrollSignal.get());
    }

    /**
     * 内容溢出时滚动应更新 signal，并经 bind 同步到 viewport.scrollOffsetY。
     */
    @Test
    public void scrollShouldUpdateSignalAndViewportOffset() {
        SceneNode viewport = buildViewportWithContent(200, 600);
        Signal<Integer> scrollSignal = SceneScrolls.attach(runtime, viewport);
        doFrame();

        routeScroll(viewport, -120);
        runtime.flush();

        Assert.assertEquals("向下滚后 signal 应增加到 120", Integer.valueOf(120), scrollSignal.get());
        Assert.assertEquals("viewport.scrollOffsetY 应由 bind 同步", 120, viewport.getScrollOffsetY());
    }

    /**
     * 到顶继续向上滚时不消费事件，外层 handler 应收到冒泡。
     */
    @Test
    public void topBoundaryShouldBubbleWithoutSignalChange() {
        SceneNode viewport = buildViewportWithContent(200, 600);
        AtomicInteger outerScrollCount = listenOuterScroll();
        Signal<Integer> scrollSignal = SceneScrolls.attach(runtime, viewport);
        doFrame();

        routeScroll(viewport, 120);
        runtime.flush();

        Assert.assertEquals("到顶向上滚 signal 不变", Integer.valueOf(0), scrollSignal.get());
        Assert.assertEquals("到顶未 stopPropagation，外层应收到冒泡", 1, outerScrollCount.get());
    }

    /**
     * 到底继续向下滚时不消费事件，外层 handler 应收到冒泡。
     */
    @Test
    public void bottomBoundaryShouldBubbleWithoutSignalChange() {
        SceneNode viewport = buildViewportWithContent(200, 600);
        AtomicInteger outerScrollCount = listenOuterScroll();
        Signal<Integer> scrollSignal = SceneScrolls.attach(runtime, viewport);
        doFrame();
        int maxScroll = SceneGeometry.maxScrollY(viewport);

        scrollSignal.set(Integer.valueOf(maxScroll));
        runtime.flush();
        routeScroll(viewport, -120);
        runtime.flush();

        Assert.assertEquals("到底向下滚 signal 不变", Integer.valueOf(maxScroll), scrollSignal.get());
        Assert.assertEquals("到底未 stopPropagation，外层应收到冒泡", 1, outerScrollCount.get());
    }

    /**
     * 内容不足视口时滚动不消费事件，外层 handler 应收到冒泡。
     */
    @Test
    public void fittingContentShouldBubbleWithoutSignalChange() {
        SceneNode viewport = buildViewportWithContent(200, 100);
        AtomicInteger outerScrollCount = listenOuterScroll();
        Signal<Integer> scrollSignal = SceneScrolls.attach(runtime, viewport);
        doFrame();

        routeScroll(viewport, -120);
        runtime.flush();

        Assert.assertEquals("无内容溢出时 signal 不变", Integer.valueOf(0), scrollSignal.get());
        Assert.assertEquals("无内容溢出未 stopPropagation，外层应收到冒泡", 1, outerScrollCount.get());
    }

    /**
     * 滚动只应走 GEOMETRY 级更新，后续 layout 应零重排。
     */
    @Test
    public void scrollShouldNotTriggerRelayout() {
        SceneNode viewport = buildViewportWithContent(200, 600);
        SceneScrolls.attach(runtime, viewport);
        LayoutResult result = doFrame();
        Assert.assertTrue("首帧应发生布局", result.getRelayoutCount() > 0);

        routeScroll(viewport, -120);
        runtime.flush();
        result = doLayout();

        Assert.assertEquals("滚动后 layout 应零重排", 0, result.getRelayoutCount());
    }

    /**
     * 构建指定视口高和内容高的滚动树。
     *
     * @param viewportHeight 视口高度
     * @param contentHeight 内容高度
     * @return 视口节点
     */
    private SceneNode buildViewportWithContent(int viewportHeight, int contentHeight) {
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(viewportHeight);
        sceneRoot.appendChild(viewport);

        SceneNode content = new SceneNode();
        content.setPreferredHeight(contentHeight);
        viewport.appendChild(content);
        return viewport;
    }

    /**
     * 监听根节点滚动事件。
     *
     * @return 外层滚动事件计数器
     */
    private AtomicInteger listenOuterScroll() {
        AtomicInteger outerScrollCount = new AtomicInteger(0);
        runtime.on(sceneRoot, SceneEventType.SCROLL, (ev, ctx) -> outerScrollCount.incrementAndGet());
        return outerScrollCount;
    }

    /** 执行响应式刷新和布局。 */
    private LayoutResult doFrame() {
        runtime.flush();
        return doLayout();
    }

    /** 执行布局。 */
    private LayoutResult doLayout() {
        return layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /**
     * 向目标节点中心投递滚轮事件。
     *
     * @param target 目标节点
     * @param wheelDelta 滚轮 delta
     */
    private void routeScroll(SceneNode target, int wheelDelta) {
        LayoutBox box = (LayoutBox) target.getCachedLayout();
        int centerX = box.getX() + box.getWidth() / 2;
        int centerY = box.getY() + box.getHeight() / 2;

        InputFrameBuilder fb = new InputFrameBuilder(centerX, centerY);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.SCROLL, centerX, centerY,
                SceneMouseButton.NONE, wheelDelta, 0, 0,
                false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
    }
}
