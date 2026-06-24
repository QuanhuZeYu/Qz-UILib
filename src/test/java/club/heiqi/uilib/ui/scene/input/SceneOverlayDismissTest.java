package club.heiqi.uilib.ui.scene.input;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;

/**
 * overlay 输入 dismiss 请求测试。
 */
public class SceneOverlayDismissTest {

    private SceneOverlayHost overlayHost;
    private SceneInputRouter router;
    private InputFrameBuilder frameBuilder;

    @Before
    public void setUp() {
        overlayHost = new SceneOverlayHost();
        router = new SceneInputRouter(overlayHost);
        frameBuilder = new InputFrameBuilder(0, 0);
    }

    /** overlay 外点击只触发 dismiss request，不直接摘除 entry。 */
    @Test
    public void outsidePointerDownShouldRequestDismissOnly() {
        SceneNode root = root();
        AtomicInteger dismissCount = new AtomicInteger();
        overlayHost.register(overlayRoot(), OverlayDismissPolicy.DEFAULT, dismissCount::incrementAndGet);

        router.route(root, pointerFrame(ScenePointerAction.BUTTON_DOWN, 150, 150, SceneMouseButton.LEFT), 0, 0);

        Assert.assertEquals("外部点击应请求关闭", 1, dismissCount.get());
        Assert.assertEquals("router 不应直接摘除 overlay", 1, overlayHost.size());
    }

    /** overlay 内点击不触发 outside dismiss。 */
    @Test
    public void insidePointerDownShouldNotRequestOutsideDismiss() {
        SceneNode root = root();
        AtomicInteger dismissCount = new AtomicInteger();
        overlayHost.register(overlayRoot(), OverlayDismissPolicy.DEFAULT, dismissCount::incrementAndGet);

        router.route(root, pointerFrame(ScenePointerAction.BUTTON_DOWN, 30, 30, SceneMouseButton.LEFT), 0, 0);

        Assert.assertEquals("内部点击不应请求外部关闭", 0, dismissCount.get());
    }

    /** ESC 触发栈顶可 ESC dismiss overlay，并阻止继续派发给主树焦点。 */
    @Test
    public void escapeShouldDismissTopOverlayAndSkipFocusedMainHandler() {
        SceneNode root = root();
        AtomicInteger firstDismiss = new AtomicInteger();
        AtomicInteger topDismiss = new AtomicInteger();
        AtomicInteger focusedKeyDown = new AtomicInteger();
        overlayHost.register(overlayRoot(), OverlayDismissPolicy.DEFAULT, firstDismiss::incrementAndGet);
        overlayHost.register(overlayRoot(), OverlayDismissPolicy.DEFAULT, topDismiss::incrementAndGet);

        router.registerFocusable(root);
        router.requestFocus(root);
        router.on(root, SceneEventType.KEY_DOWN, (event, context) -> focusedKeyDown.incrementAndGet());

        router.route(root, keyFrame(SceneKey.ESCAPE), 0, 0);

        Assert.assertEquals("非栈顶 overlay 不应收到 ESC", 0, firstDismiss.get());
        Assert.assertEquals("栈顶 overlay 应收到 ESC", 1, topDismiss.get());
        Assert.assertEquals("ESC 不应派发给主树焦点", 0, focusedKeyDown.get());
    }

    /** 没有可 ESC dismiss 的 overlay 时，ESC 仍派发给主树焦点。 */
    @Test
    public void escapeShouldFallbackToFocusedMainHandlerWhenNoEscDismissOverlay() {
        SceneNode root = root();
        AtomicInteger focusedKeyDown = new AtomicInteger();
        overlayHost.register(overlayRoot(), OverlayDismissPolicy.NONE, () -> { });
        router.registerFocusable(root);
        router.requestFocus(root);
        router.on(root, SceneEventType.KEY_DOWN, (event, context) -> focusedKeyDown.incrementAndGet());

        router.route(root, keyFrame(SceneKey.ESCAPE), 0, 0);

        Assert.assertEquals("无可关闭 overlay 时应回退主树焦点", 1, focusedKeyDown.get());
    }

    private SceneNode root() {
        SceneNode root = new SceneNode();
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        return root;
    }

    private SceneNode overlayRoot() {
        SceneNode overlay = new SceneNode();
        overlay.setCachedLayout(new LayoutBox(20, 20, 80, 80));
        return overlay;
    }

    private SceneInputFrame pointerFrame(ScenePointerAction action, int x, int y, SceneMouseButton button) {
        frameBuilder.push(RawInputEvent.ofPointer(action, x, y, button,
                0, 0, 0, false, false, false, false, 1000L));
        return frameBuilder.drainFrame();
    }

    private SceneInputFrame keyFrame(SceneKey key) {
        frameBuilder.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        return frameBuilder.drainFrame();
    }
}
