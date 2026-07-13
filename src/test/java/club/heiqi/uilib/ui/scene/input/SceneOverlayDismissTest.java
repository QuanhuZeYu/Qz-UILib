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

    /** active overlay 存在时 Tab/Shift+Tab 只在其 root 内环绕。 */
    @Test
    public void tabShouldWrapInsideActiveOverlay() {
        SceneNode root = root();
        SceneNode main = focusableChild(root);
        SceneNode overlay = overlayRoot();
        SceneNode first = focusableChild(overlay);
        SceneNode second = focusableChild(overlay);
        overlayHost.register(overlay, OverlayDismissPolicy.DEFAULT, () -> { });
        router.registerFocusable(main);
        router.registerFocusable(first);
        router.registerFocusable(second);
        router.requestFocus(first);

        router.route(root, keyFrame(SceneKey.TAB, false), 0, 0);
        Assert.assertSame("Tab 应前进到浮层第二项", second, router.getFocusedNode());
        router.route(root, keyFrame(SceneKey.TAB, false), 0, 0);
        Assert.assertSame("Tab 应在浮层内回绕", first, router.getFocusedNode());
        router.route(root, keyFrame(SceneKey.TAB, true), 0, 0);
        Assert.assertSame("Shift+Tab 应在浮层内反向回绕", second, router.getFocusedNode());
        Assert.assertNotSame("Tab 不得逃回主树", main, router.getFocusedNode());
    }

    /** 两层 overlay 同时存在时只有栈顶 root 参与 Tab 环。 */
    @Test
    public void tabShouldUseOnlyTopOverlayScope() {
        SceneNode root = root();
        SceneNode main = focusableChild(root);
        SceneNode lower = overlayRoot();
        SceneNode lowerFocus = focusableChild(lower);
        SceneNode top = overlayRoot();
        SceneNode topFirst = focusableChild(top);
        SceneNode topSecond = focusableChild(top);
        AtomicInteger mainTabCount = new AtomicInteger();
        AtomicInteger lowerTabCount = new AtomicInteger();
        overlayHost.register(lower, OverlayDismissPolicy.DEFAULT, () -> { });
        overlayHost.register(top, OverlayDismissPolicy.DEFAULT, () -> { });
        router.registerFocusable(main);
        router.registerFocusable(lowerFocus);
        router.registerFocusable(topFirst);
        router.registerFocusable(topSecond);
        router.on(main, SceneEventType.KEY_DOWN, (event, context) -> {
            mainTabCount.incrementAndGet();
            context.stopPropagation();
        });
        router.on(lowerFocus, SceneEventType.KEY_DOWN, (event, context) -> {
            lowerTabCount.incrementAndGet();
            context.stopPropagation();
        });

        router.requestFocus(main);
        router.route(root, keyFrame(SceneKey.TAB, false), 0, 0);
        Assert.assertEquals("主树旧焦点不得收到栈顶 scope 的 Tab", 0, mainTabCount.get());
        Assert.assertSame("主树旧焦点不在栈顶 scope 时应进入栈顶首项", topFirst, router.getFocusedNode());

        router.requestFocus(lowerFocus);
        router.route(root, keyFrame(SceneKey.TAB, true), 0, 0);
        Assert.assertEquals("下层浮层旧焦点不得收到栈顶 scope 的 Shift+Tab", 0, lowerTabCount.get());
        Assert.assertSame("下层焦点不在栈顶 scope 时应反向进入栈顶末项", topSecond, router.getFocusedNode());
    }

    /** 栈顶 overlay 内焦点仍先收到 Tab，并可阻断默认遍历。 */
    @Test
    public void tabHandlerInsideTopOverlayShouldStillBlockTraversal() {
        SceneNode root = root();
        SceneNode top = overlayRoot();
        SceneNode first = focusableChild(top);
        SceneNode second = focusableChild(top);
        AtomicInteger topTabCount = new AtomicInteger();
        overlayHost.register(top, OverlayDismissPolicy.DEFAULT, () -> { });
        router.registerFocusable(first);
        router.registerFocusable(second);
        router.on(first, SceneEventType.KEY_DOWN, (event, context) -> {
            topTabCount.incrementAndGet();
            context.stopPropagation();
        });
        router.requestFocus(first);

        router.route(root, keyFrame(SceneKey.TAB, false), 0, 0);

        Assert.assertEquals("栈顶 scope 内 handler 应收到 Tab", 1, topTabCount.get());
        Assert.assertSame("栈顶 handler stopPropagation 应阻断默认遍历", first, router.getFocusedNode());
    }

    /** 无 overlay 时继续以主 root 为 Tab scope。 */
    @Test
    public void tabShouldKeepMainRootScopeWithoutOverlay() {
        SceneNode root = root();
        SceneNode first = focusableChild(root);
        SceneNode second = focusableChild(root);
        router.registerFocusable(first);
        router.registerFocusable(second);
        router.requestFocus(first);

        router.route(root, keyFrame(SceneKey.TAB, false), 0, 0);

        Assert.assertSame("无浮层时应继续遍历主树", second, router.getFocusedNode());
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

    private SceneNode focusableChild(SceneNode parent) {
        SceneNode child = new SceneNode();
        parent.appendChild(child);
        return child;
    }

    private SceneInputFrame pointerFrame(ScenePointerAction action, int x, int y, SceneMouseButton button) {
        frameBuilder.push(RawInputEvent.ofPointer(action, x, y, button,
                0, 0, 0, false, false, false, false, 1000L));
        return frameBuilder.drainFrame();
    }

    private SceneInputFrame keyFrame(SceneKey key) {
        return keyFrame(key, false);
    }

    private SceneInputFrame keyFrame(SceneKey key, boolean shiftDown) {
        frameBuilder.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, shiftDown, false, false, RawInputEvent.NATIVE_NONE, RawInputEvent.NATIVE_NONE, 1000L));
        return frameBuilder.drainFrame();
    }
}
