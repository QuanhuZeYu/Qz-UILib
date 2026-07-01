package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.ScenePortalHandle;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * {@link SceneHostWidget} 的 overlay 多 root layout/paint/replay 探针测试。
 */
public class SceneOverlayPipelineTest {

    private SceneHostWidget host;
    private SceneRuntime runtime;
    private RecordingBackend backend;

    @Before
    public void setUp() {
        host = new SceneHostWidget(null);
        runtime = host.__getRuntime();
        backend = new RecordingBackend();
    }

    @After
    public void tearDown() {
        host.dispose();
    }

    /** overlay 在主树后回放，因此覆盖同区域主树背景。 */
    @Test
    public void overlayShouldReplayAfterMainTree() {
        Signal<Boolean> visible = Signal.create(true);
        runtime.portal(visible, () -> overlayNode(0xFFFF0000));

        host.render(200, 120, backend, 0, 0);

        int mainIndex = backend.lastIndexOfColor(0xFF333333);
        int overlayIndex = backend.lastIndexOfColor(0xFFFF0000);
        Assert.assertTrue("应绘制主树背景", mainIndex >= 0);
        Assert.assertTrue("应绘制 overlay 背景", overlayIndex >= 0);
        Assert.assertTrue("overlay 应在主树之后绘制", overlayIndex > mainIndex);
    }

    /** overlay 不在主树 clip 作用域内回放，可跨主树 scrollable/clip 容器可见。 */
    @Test
    public void overlayShouldReplayOutsideMainClipScope() {
        SceneNode clipContainer = new SceneNode();
        clipContainer.setPreferredWidth(50);
        clipContainer.setPreferredHeight(20);
        clipContainer.setClipChildren(true);
        host.__getRoot().appendChild(clipContainer);

        Signal<Boolean> visible = Signal.create(true);
        runtime.portal(visible, () -> overlayNode(0xFF00FF00));

        host.render(200, 120, backend, 0, 0);

        int lastClipPop = backend.lastIndexOf("popClip");
        int overlayIndex = backend.lastIndexOfColor(0xFF00FF00);
        Assert.assertTrue("主树应产生 clip 作用域", lastClipPop >= 0);
        Assert.assertTrue("overlay 应产生背景命令", overlayIndex >= 0);
        Assert.assertTrue("overlay 应在主树 clip pop 之后回放", overlayIndex > lastClipPop);
    }

    /** overlay 侧滚动变化不应污染主树稳定兄弟 layout/paint 缓存。 */
    @Test
    public void overlayScrollShouldNotDirtyStableMainSiblings() {
        Signal<Boolean> visible = Signal.create(true);
        Signal<Integer> scrollOffset = Signal.create(0);
        SceneNode stableSibling = new SceneNode();
        stableSibling.setPreferredHeight(20);
        stableSibling.setBackgroundColor(0xFF123456);
        host.__getRoot().appendChild(stableSibling);
        runtime.portal(visible, () -> {
            SceneNode overlay = overlayNode(0xFFABCDEF);
            overlay.setScrollable(true);
            SceneNode child = new SceneNode();
            child.setPreferredHeight(80);
            child.setBackgroundColor(0xFF654321);
            overlay.appendChild(child);
            runtime.bind(scrollOffset, overlay::setScrollOffsetY);
            runtime.on(overlay, SceneEventType.CLICK, (event, context) -> { });
            return overlay;
        });

        host.render(200, 120, backend, 0, 0);
        backend.clear();
        scrollOffset.set(12);
        host.render(200, 120, backend, 0, 0);

        Assert.assertEquals("overlay 滚动不应触发主树 layout 重排", 0,
                host.getLastLayoutResult().getRelayoutCount());
        Assert.assertFalse("稳定主树兄弟不应被 overlay 滚动标脏", stableSibling.__isSelfLayoutDirty());
        Assert.assertFalse("稳定主树兄弟不应被 overlay 滚动重绘", stableSibling.__isSelfPaintDirty());
    }

    /** 多个 overlay root 各自使用独立 layout engine，host 约束变化时都能感知并更新尺寸。 */
    @Test
    public void multipleOverlayRootsShouldUseIndependentLayoutEngines() {
        Signal<Boolean> firstVisible = Signal.create(true);
        Signal<Boolean> secondVisible = Signal.create(true);
        SceneNode firstOverlay = fillHeightOverlay(0xFF111111);
        SceneNode secondOverlay = fillHeightOverlay(0xFF222222);

        runtime.portal(firstVisible, () -> firstOverlay);
        ScenePortalHandle secondHandle = runtime.portal(secondVisible, () -> secondOverlay);

        host.render(200, 100, backend, 0, 0);
        Assert.assertEquals("第一个 overlay 初始高度应跟随 host", 100,
                ((LayoutBox) firstOverlay.getCachedLayout()).getHeight());
        Assert.assertEquals("第二个 overlay 初始高度应跟随 host", 100,
                ((LayoutBox) secondOverlay.getCachedLayout()).getHeight());
        Assert.assertNotSame("两个 overlay 不应共享同一个 layout engine",
                host.__getOverlayLayoutEngine(firstOverlay), host.__getOverlayLayoutEngine(secondOverlay));

        host.render(200, 160, backend, 0, 0);

        Assert.assertEquals("第一个 overlay 应感知 host 高度变化", 160,
                ((LayoutBox) firstOverlay.getCachedLayout()).getHeight());
        Assert.assertEquals("第二个 overlay 应感知 host 高度变化", 160,
                ((LayoutBox) secondOverlay.getCachedLayout()).getHeight());
        Assert.assertEquals("应缓存两个 active overlay 的 layout engine", 2,
                host.__getOverlayLayoutEngineCount());

        secondHandle.dispose();
        host.render(200, 160, backend, 0, 0);

        Assert.assertEquals("移除 overlay 后应清理 stale layout engine", 1,
                host.__getOverlayLayoutEngineCount());
        Assert.assertNull("已移除 overlay 的 engine 应被清理",
                host.__getOverlayLayoutEngine(secondOverlay));
    }

    private SceneNode overlayNode(int color) {
        SceneNode overlay = new SceneNode();
        overlay.setPreferredWidth(80);
        overlay.setPreferredHeight(40);
        overlay.setBackgroundColor(color);
        return overlay;
    }

    private SceneNode fillHeightOverlay(int color) {
        SceneNode overlay = new SceneNode();
        overlay.setPreferredWidth(80);
        overlay.setFillParentHeight(true);
        overlay.setBackgroundColor(color);
        return overlay;
    }

    /** 记录回放顺序的轻量渲染后端。 */
    private static final class RecordingBackend implements UiRenderBackend {
        private final List<String> calls = new ArrayList<String>();

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {
            calls.add("fillRect:#" + Integer.toHexString(color));
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, int fillColor, int borderColor,
                int cornerRadius) {
            calls.add("drawSurface:#" + Integer.toHexString(fillColor));
        }

        @Override
        public void drawBorder(int left, int top, int right, int bottom, int color) {
            calls.add("drawBorder:#" + Integer.toHexString(color));
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
            calls.add("pushClip");
        }

        @Override
        public void popClip() {
            calls.add("popClip");
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            calls.add("drawText:" + text);
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx) {
            calls.add("drawText:" + text);
        }

        @Override
        public void pushGroupOpacity(int left, int top, int right, int bottom, float opacity) {
            calls.add("pushGroupOpacity");
        }

        @Override
        public void popGroupOpacity() {
            calls.add("popGroupOpacity");
        }

        @Override
        public void pushTransform(float translateX, float translateY, float rotateDegrees,
                float scaleX, float scaleY, float originXRatio, float originYRatio,
                int left, int top, int right, int bottom) {
            calls.add("pushTransform");
        }

        @Override
        public void popTransform() {
            calls.add("popTransform");
        }

        @Override
        public void pushTransformLayer(float translateX, float translateY, float rotateDegrees,
                float scaleX, float scaleY, float originXRatio, float originYRatio,
                int left, int top, int right, int bottom) {
            calls.add("pushTransformLayer");
        }

        @Override
        public void popTransformLayer() {
            calls.add("popTransformLayer");
        }

        private int lastIndexOfColor(int color) {
            String suffix = "#" + Integer.toHexString(color);
            for (int i = calls.size() - 1; i >= 0; i--) {
                if (calls.get(i).endsWith(suffix)) {
                    return i;
                }
            }
            return -1;
        }

        private int lastIndexOf(String call) {
            for (int i = calls.size() - 1; i >= 0; i--) {
                if (calls.get(i).equals(call)) {
                    return i;
                }
            }
            return -1;
        }

        private void clear() {
            calls.clear();
        }
    }
}
