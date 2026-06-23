package club.heiqi.uilib.ui.scene.input;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;

/**
 * overlay root 在输入路由中的优先命中测试。
 */
public class SceneOverlayHitTestTest {

    private SceneOverlayHost overlayHost;
    private SceneInputRouter router;
    private InputFrameBuilder frameBuilder;

    @Before
    public void setUp() {
        overlayHost = new SceneOverlayHost();
        router = new SceneInputRouter(overlayHost);
        frameBuilder = new InputFrameBuilder(0, 0);
    }

    /** overlay 覆盖主树节点时，同一点点击应派发给 overlay。 */
    @Test
    public void overlayShouldWinWhenCoveringMainTree() {
        SceneNode root = rootWithChild();
        SceneNode mainChild = root.__getChildren().get(0);
        SceneNode overlay = overlayRoot();
        overlayHost.register(overlay);

        List<String> log = new ArrayList<String>();
        router.on(mainChild, SceneEventType.CLICK, (event, context) -> log.add("main"));
        router.on(overlay, SceneEventType.CLICK, (event, context) -> log.add("overlay"));

        router.route(root, pointerFrame(ScenePointerAction.BUTTON_DOWN, 30, 30, SceneMouseButton.LEFT), 0, 0);
        router.route(root, pointerFrame(ScenePointerAction.BUTTON_UP, 30, 30, SceneMouseButton.LEFT), 0, 0);

        Assert.assertEquals("应只点击 overlay", 1, log.size());
        Assert.assertEquals("overlay", log.get(0));
    }

    /** overlay host 为空时，主树点击行为保持不变。 */
    @Test
    public void emptyOverlayHostShouldKeepMainTreeBehavior() {
        SceneNode root = rootWithChild();
        SceneNode mainChild = root.__getChildren().get(0);
        List<String> log = new ArrayList<String>();
        router.on(mainChild, SceneEventType.CLICK, (event, context) -> log.add("main"));

        router.route(root, pointerFrame(ScenePointerAction.BUTTON_DOWN, 30, 30, SceneMouseButton.LEFT), 0, 0);
        router.route(root, pointerFrame(ScenePointerAction.BUTTON_UP, 30, 30, SceneMouseButton.LEFT), 0, 0);

        Assert.assertEquals("主树点击不应受空 host 影响", 1, log.size());
        Assert.assertEquals("main", log.get(0));
    }

    /** overlay root 无 cachedLayout 时跳过 overlay 命中，主树保持原行为。 */
    @Test
    public void overlayWithoutLayoutShouldBeSkipped() {
        SceneNode root = rootWithChild();
        SceneNode mainChild = root.__getChildren().get(0);
        overlayHost.register(new SceneNode());
        List<String> log = new ArrayList<String>();
        router.on(mainChild, SceneEventType.CLICK, (event, context) -> log.add("main"));

        router.route(root, pointerFrame(ScenePointerAction.BUTTON_DOWN, 30, 30, SceneMouseButton.LEFT), 0, 0);
        router.route(root, pointerFrame(ScenePointerAction.BUTTON_UP, 30, 30, SceneMouseButton.LEFT), 0, 0);

        Assert.assertEquals("无布局 overlay 应跳过", 1, log.size());
        Assert.assertEquals("main", log.get(0));
    }

    /** overlay hover 使用同一套交互状态。 */
    @Test
    public void overlayMoveShouldUpdateHoverState() {
        SceneNode root = rootWithChild();
        SceneNode overlay = overlayRoot();
        overlayHost.register(overlay);
        router.interactionState(overlay).hovered();

        router.route(root, pointerFrame(ScenePointerAction.MOVE, 30, 30, SceneMouseButton.NONE), 0, 0);

        Assert.assertSame("hover 节点应为 overlay", overlay, router.__getHoveredNode());
    }

    private SceneNode rootWithChild() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        child.setCachedLayout(new LayoutBox(20, 20, 80, 80));
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
}
