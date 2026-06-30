package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.LayoutResult;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.AnchorProvider;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;

/**
 * 锚定 overlay 的布局偏移与输入命中链路测试。
 */
public class SceneAnchoredOverlayPipelineTest {

    private SceneHostWidget host;
    private SceneRuntime runtime;
    private NoopBackend backend;

    @Before
    public void setUp() {
        host = new SceneHostWidget(null);
        runtime = host.__getRuntime();
        backend = new NoopBackend();
    }

    @After
    public void tearDown() {
        host.dispose();
    }

    /** 锚点解析后的 entry 偏移应参与 overlay hit-test。 */
    @Test
    public void anchoredOverlayShouldHitTestAtResolvedOffset() {
        Signal<Boolean> visible = Signal.create(true);
        List<String> log = new ArrayList<String>();
        SceneNode overlay = new SceneNode();
        overlay.setPreferredHeight(30);
        overlay.setBackgroundColor(0xFF00AAFF);
        runtime.on(overlay, SceneEventType.CLICK, (event, context) -> log.add("overlay"));

        runtime.portalAnchored(visible, () -> overlay, OverlayDismissPolicy.DEFAULT, null,
                () -> new AnchorRect(30, 40, 90, 20));

        host.render(200, 120, backend, 0, 0);
        SceneOverlayHost.Entry entry = runtime.getOverlayHost().bottomFirst().get(0);

        Assert.assertEquals("anchorX 应来自 resolveDown 的 X", 30, entry.getAnchorX());
        Assert.assertEquals("anchorY 应位于 trigger 底边", 60, entry.getAnchorY());

        runtime.route(host.__getRoot(), pointerFrame(ScenePointerAction.BUTTON_DOWN, 35, 65, SceneMouseButton.LEFT), 0, 0);
        runtime.route(host.__getRoot(), pointerFrame(ScenePointerAction.BUTTON_UP, 35, 65, SceneMouseButton.LEFT), 0, 0);

        Assert.assertEquals("点击解析后偏移内区域应命中 overlay", 1, log.size());
        Assert.assertEquals("overlay", log.get(0));
    }

    /** trigger 完全滚出 scrollable viewport 时，应请求关闭锚定 overlay。 */
    @Test
    public void anchoredOverlayShouldDismissWhenTriggerFullyClipped() {
        SceneNode viewport = new SceneNode();
        SceneNode trigger = new SceneNode();
        host.__getRoot().appendChild(viewport);
        viewport.appendChild(trigger);
        viewport.setScrollable(true);
        viewport.setPreferredHeight(40);
        trigger.setPreferredWidth(80);
        trigger.setPreferredHeight(20);
        viewport.setScrollOffsetY(80);

        Signal<Boolean> visible = Signal.create(true);
        AtomicInteger dismissCount = new AtomicInteger(0);
        runtime.portalAnchored(visible, () -> overlayNode(), OverlayDismissPolicy.DEFAULT,
                () -> {
                    dismissCount.incrementAndGet();
                    visible.set(Boolean.FALSE);
                }, AnchorProvider.forNode(trigger));

        host.render(200, 120, backend, 0, 0);
        runtime.flush();
        Assert.assertEquals("完全裁掉时应请求关闭一次", 1, dismissCount.get());
        Assert.assertFalse("完全裁掉时 dismissRequest 应写 false", visible.get());
    }

    /** trigger 仍有部分可见时，不应关闭 overlay，避免半遮闪关。 */
    @Test
    public void anchoredOverlayShouldStayWhenTriggerPartiallyVisible() {
        SceneNode viewport = new SceneNode();
        SceneNode trigger = new SceneNode();
        host.__getRoot().appendChild(viewport);
        viewport.appendChild(trigger);
        viewport.setScrollable(true);
        viewport.setPreferredHeight(40);
        trigger.setPreferredWidth(80);
        trigger.setPreferredHeight(20);
        viewport.setScrollOffsetY(10);

        Signal<Boolean> visible = Signal.create(true);
        runtime.portalAnchored(visible, () -> overlayNode(), OverlayDismissPolicy.DEFAULT,
                () -> visible.set(Boolean.FALSE), AnchorProvider.forNode(trigger));

        host.render(200, 120, backend, 0, 0);
        Assert.assertTrue("部分可见时 overlay 应保持", visible.get());
    }

    /** trigger 完全位于 viewport 内时，overlay 正常保持。 */
    @Test
    public void anchoredOverlayShouldStayWhenTriggerFullyVisible() {
        SceneNode viewport = new SceneNode();
        SceneNode trigger = new SceneNode();
        host.__getRoot().appendChild(viewport);
        viewport.appendChild(trigger);
        viewport.setScrollable(true);
        viewport.setPreferredHeight(40);
        trigger.setPreferredWidth(80);
        trigger.setPreferredHeight(20);

        Signal<Boolean> visible = Signal.create(true);
        runtime.portalAnchored(visible, () -> overlayNode(), OverlayDismissPolicy.DEFAULT,
                () -> visible.set(Boolean.FALSE), AnchorProvider.forNode(trigger));

        host.render(200, 120, backend, 0, 0);
        Assert.assertTrue("完全可见时 overlay 应保持", visible.get());
        Assert.assertEquals("overlay entry 应仍存在", 1, runtime.getOverlayHost().size());
    }

    /** trigger 位于底部时，锚定 overlay 应翻到上方并贴住 trigger 顶边。 */
    @Test
    public void anchoredOverlayShouldFlipAboveBottomTrigger() {
        Signal<Boolean> visible = Signal.create(true);
        SceneNode overlay = overlayNode(80, 90);
        runtime.portalAnchored(visible, () -> overlay, OverlayDismissPolicy.DEFAULT, null,
                () -> new AnchorRect(30, 100, 80, 20));

        host.render(200, 130, backend, 0, 0);
        SceneOverlayHost.Entry entry = runtime.getOverlayHost().bottomFirst().get(0);
        LayoutBox overlayBox = (LayoutBox) overlay.getCachedLayout();

        Assert.assertTrue("翻转后 anchorY 应位于 trigger 上方", entry.getAnchorY() < 100);
        Assert.assertEquals("listbox 底边应贴住 trigger 顶边", 100, entry.getAnchorY() + overlayBox.getHeight());
    }

    /** overlay 二次布局后，高度约束未实质改变的 item 应跳过重算。 */
    @Test
    public void anchoredOverlaySecondLayoutShouldSkipStableItems() throws Exception {
        Signal<Boolean> visible = Signal.create(true);
        SceneNode listbox = new SceneNode();
        listbox.setScrollable(true);
        listbox.setClipChildren(true);
        List<SceneNode> items = appendItems(listbox, 3, 20);
        runtime.portalAnchored(visible, () -> listbox, OverlayDismissPolicy.DEFAULT, null,
                () -> new AnchorRect(30, 40, 80, 20));

        host.render(200, 200, backend, 0, 0);
        LayoutResult overlayResult = host.getOverlayLayoutResult(listbox);

        for (SceneNode item : items) {
            Assert.assertFalse("二次布局同高约束下 item 不应重算", overlayResult.getRelayoutedNodes().contains(item));
        }
    }

    /** 翻转后 listbox 内部应可命中，原向下区域应触发 outside dismiss。 */
    @Test
    public void flippedOverlayShouldHitTestAboveAndDismissBelow() {
        Signal<Boolean> visible = Signal.create(true);
        AtomicInteger itemClickCount = new AtomicInteger(0);
        AtomicInteger dismissCount = new AtomicInteger(0);
        SceneNode listbox = new SceneNode();
        SceneNode item = overlayNode(80, 90);
        runtime.on(item, SceneEventType.CLICK, (event, context) -> itemClickCount.incrementAndGet());
        listbox.appendChild(item);
        runtime.portalAnchored(visible, () -> listbox, OverlayDismissPolicy.DEFAULT,
                () -> {
                    dismissCount.incrementAndGet();
                    visible.set(Boolean.FALSE);
                }, () -> new AnchorRect(30, 100, 80, 20));

        host.render(200, 130, backend, 0, 0);
        runtime.route(host.__getRoot(), pointerFrame(ScenePointerAction.BUTTON_DOWN, 35, 55, SceneMouseButton.LEFT), 0, 0);
        runtime.route(host.__getRoot(), pointerFrame(ScenePointerAction.BUTTON_UP, 35, 55, SceneMouseButton.LEFT), 0, 0);
        Assert.assertEquals("翻转后 listbox 内点击应命中 item", 1, itemClickCount.get());
        Assert.assertEquals("内部点击不应 outside dismiss", 0, dismissCount.get());

        runtime.route(host.__getRoot(), pointerFrame(ScenePointerAction.BUTTON_DOWN, 35, 125, SceneMouseButton.LEFT), 0, 0);
        runtime.flush();
        Assert.assertEquals("原向下区域点击应触发 outside dismiss", 1, dismissCount.get());
        Assert.assertFalse("outside dismiss 应关闭 visible signal", visible.get());
    }

    /** 长列表两侧都不够时应按较大侧 cap，并保留可滚动内容。 */
    @Test
    public void longFlippedOverlayShouldBeScrollableWhenCapped() {
        Signal<Boolean> visible = Signal.create(true);
        SceneNode listbox = new SceneNode();
        listbox.setScrollable(true);
        listbox.setClipChildren(true);
        appendItems(listbox, 10, 20);
        runtime.portalAnchored(visible, () -> listbox, OverlayDismissPolicy.DEFAULT, null,
                () -> new AnchorRect(30, 80, 80, 20));

        host.render(200, 130, backend, 0, 0);
        SceneOverlayHost.Entry entry = runtime.getOverlayHost().bottomFirst().get(0);
        LayoutBox listboxBox = (LayoutBox) listbox.getCachedLayout();

        Assert.assertEquals("向上 cap 时应贴 host 顶部", 0, entry.getAnchorY());
        Assert.assertEquals("listbox 高度应 cap 到上方可用空间", 80, listboxBox.getHeight());
        Assert.assertTrue("cap 后 listbox 应可滚动", SceneGeometry.maxScrollY(listbox) > 0);
    }

    private SceneInputFrame pointerFrame(ScenePointerAction action, int x, int y, SceneMouseButton button) {
        InputFrameBuilder builder = new InputFrameBuilder(0, 0);
        builder.push(RawInputEvent.ofPointer(action, x, y, button,
                0, 0, 0, false, false, false, false, 1000L));
        return builder.drainFrame();
    }

    private SceneNode overlayNode() {
        return overlayNode(80, 30);
    }

    private SceneNode overlayNode(int width, int height) {
        SceneNode overlay = new SceneNode();
        overlay.setPreferredWidth(width);
        overlay.setPreferredHeight(height);
        overlay.setBackgroundColor(0xFF00AAFF);
        return overlay;
    }

    private List<SceneNode> appendItems(SceneNode listbox, int count, int height) {
        List<SceneNode> items = new ArrayList<SceneNode>();
        for (int i = 0; i < count; i++) {
            SceneNode item = new SceneNode();
            item.setPreferredHeight(height);
            listbox.appendChild(item);
            items.add(item);
        }
        return items;
    }

    private SceneLayoutEngine overlayEngineFor(SceneNode root) {
        return host.__getOverlayLayoutEngine(root);
    }

    /** 不记录绘制输出的轻量渲染后端。 */
    private static final class NoopBackend implements UiRenderBackend {
        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, int fillColor, int borderColor,
                                int cornerRadius) {
        }

        @Override
        public void drawBorder(int left, int top, int right, int bottom, int color) {
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
        }

        @Override
        public void popClip() {
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx) {
        }

        @Override
        public void pushGroupOpacity(int left, int top, int right, int bottom, float opacity) {
        }

        @Override
        public void popGroupOpacity() {
        }

        @Override
        public void pushTransform(float translateX, float translateY, float rotateDegrees,
                                  float scaleX, float scaleY, float originXRatio, float originYRatio,
                                  int left, int top, int right, int bottom) {
        }

        @Override
        public void popTransform() {
        }

        @Override
        public void pushTransformLayer(float translateX, float translateY, float rotateDegrees,
                                       float scaleX, float scaleY, float originXRatio, float originYRatio,
                                       int left, int top, int right, int bottom) {
        }

        @Override
        public void popTransformLayer() {
        }
    }
}
