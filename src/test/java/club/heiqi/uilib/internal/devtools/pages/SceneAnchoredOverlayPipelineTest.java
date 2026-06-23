package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;

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
                () -> new SceneAnchorResolver.AnchorRect(30, 40, 90, 20));

        host.render(200, 120, backend, 0, 0);
        SceneOverlayHost.Entry entry = runtime.getOverlayHost().bottomFirst().get(0);

        Assert.assertEquals("anchorX 应来自 resolveDown 的 X", 30, entry.getAnchorX());
        Assert.assertEquals("anchorY 应位于 trigger 底边", 60, entry.getAnchorY());

        runtime.route(host.__getRoot(), pointerFrame(ScenePointerAction.BUTTON_DOWN, 35, 65, SceneMouseButton.LEFT), 0, 0);
        runtime.route(host.__getRoot(), pointerFrame(ScenePointerAction.BUTTON_UP, 35, 65, SceneMouseButton.LEFT), 0, 0);

        Assert.assertEquals("点击解析后偏移内区域应命中 overlay", 1, log.size());
        Assert.assertEquals("overlay", log.get(0));
    }

    private SceneInputFrame pointerFrame(ScenePointerAction action, int x, int y, SceneMouseButton button) {
        InputFrameBuilder builder = new InputFrameBuilder(0, 0);
        builder.push(RawInputEvent.ofPointer(action, x, y, button,
                0, 0, 0, false, false, false, false, 1000L));
        return builder.drainFrame();
    }

    /** 不记录绘制输出的轻量渲染后端。 */
    private static final class NoopBackend implements UiRenderBackend {
        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, int fillColor, int borderColor,
                                UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
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
        public void pushPaintContext(int left, int top, int right, int bottom, float opacity) {
        }

        @Override
        public void popPaintContext() {
        }

        @Override
        public void pushTransform(float translateX, float translateY, float rotateDegrees,
                                  float scaleX, float scaleY, float originXRatio, float originYRatio,
                                  int left, int top, int right, int bottom) {
        }

        @Override
        public void popTransform() {
        }
    }
}
