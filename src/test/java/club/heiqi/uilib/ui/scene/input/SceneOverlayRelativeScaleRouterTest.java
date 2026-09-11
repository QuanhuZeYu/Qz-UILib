package club.heiqi.uilib.ui.scene.input;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.overlay.OverlayHandle;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;

/**
 * overlay 相对倍率 s 的输入口径测试。
 *
 * <p>覆盖四条换算路径：overlay 优先命中、外部点击 dismiss、主体/CANCEL/CLICK 派发坐标、
 * 指针捕获后的 MOVE 派发；并锚定 s == 1.0F 时与既有路径逐位一致（不扩大命中范围）。</p>
 */
public class SceneOverlayRelativeScaleRouterTest {

    private SceneOverlayHost overlayHost;
    private SceneInputRouter router;
    private InputFrameBuilder frameBuilder;

    @Before
    public void setUp() {
        overlayHost = new SceneOverlayHost();
        router = new SceneInputRouter(overlayHost);
        frameBuilder = new InputFrameBuilder(0, 0);
    }

    /** 命中与派发：画布坐标 ÷ s 后落在 overlay 逻辑盒内，且事件坐标全部为 overlay 逻辑坐标。 */
    @Test
    public void relativeScaleShouldMapCanvasPointIntoOverlaySpaceForHitAndDispatch() {
        SceneNode root = mainTree();
        SceneNode overlay = overlayTree(60, 40);
        OverlayHandle handle = overlayHost.register(overlay);
        handle.setRelativeScale(2.0F);
        SceneNode overlayChild = overlay.__getChildren().get(0);
        SceneNode mainChild = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        int[] raw = {-1, -1};
        int[] treeAbs = {-1, -1};
        int[] local = {-1, -1};
        router.on(mainChild, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("main"));
        router.on(overlayChild, SceneEventType.POINTER_DOWN, (evt, ctx) -> {
            raw[0] = evt.getRawPointerX();
            raw[1] = evt.getRawPointerY();
            treeAbs[0] = ctx.getTreeRootAbsX();
            treeAbs[1] = ctx.getTreeRootAbsY();
            local[0] = ctx.getLocalPointerX();
            local[1] = ctx.getLocalPointerY();
        });

        // 画布 (60,50)：s=2 → overlay 逻辑 (30,25)，落在 overlay 逻辑盒 (0,0,60,40) 内；
        // 若不换算，(60,50) 已在盒外，会错误落到主树。
        router.route(root, frame(ScenePointerAction.BUTTON_DOWN, 60, 50, SceneMouseButton.LEFT), 0, 0);

        Assert.assertEquals("命中必须落在 overlay 内，不得回退主树", 0, log.size());
        Assert.assertEquals(30, raw[0]);
        Assert.assertEquals(25, raw[1]);
        Assert.assertEquals(0, treeAbs[0]);
        Assert.assertEquals(0, treeAbs[1]);
        Assert.assertEquals("overlay 逻辑局部 X = 30 - 5", 25, local[0]);
        Assert.assertEquals("overlay 逻辑局部 Y = 25 - 5", 20, local[1]);
    }

    /** 宿主偏移同步 ÷ s：treeAbs = round((rootAbs + anchor) / s)。 */
    @Test
    public void relativeScaleShouldMapHostOffsetIntoOverlaySpace() {
        SceneNode root = mainTree();
        SceneNode overlay = overlayTree(60, 40);
        OverlayHandle handle = overlayHost.register(overlay);
        handle.setRelativeScale(2.0F);
        SceneNode overlayChild = overlay.__getChildren().get(0);

        int[] raw = {-1, -1};
        int[] treeAbs = {-1, -1};
        int[] local = {-1, -1};
        router.on(overlayChild, SceneEventType.POINTER_DOWN, (evt, ctx) -> {
            raw[0] = evt.getRawPointerX();
            raw[1] = evt.getRawPointerY();
            treeAbs[0] = ctx.getTreeRootAbsX();
            treeAbs[1] = ctx.getTreeRootAbsY();
            local[0] = ctx.getLocalPointerX();
            local[1] = ctx.getLocalPointerY();
        });

        // 宿主偏移 (10,6) 且 s=2 → overlay 树绝对原点 (5,3)；画布 (60,50) → 逻辑 (30,25)。
        router.route(root, frame(ScenePointerAction.BUTTON_DOWN, 60, 50, SceneMouseButton.LEFT), 10, 6);

        Assert.assertEquals(30, raw[0]);
        Assert.assertEquals(25, raw[1]);
        Assert.assertEquals(5, treeAbs[0]);
        Assert.assertEquals(3, treeAbs[1]);
        Assert.assertEquals("local = 30 - absoluteBox(child, 5, 3).x", 20, local[0]);
        Assert.assertEquals("local = 25 - absoluteBox(child, 5, 3).y", 17, local[1]);
    }

    /** 指针捕获：MOVE 越出 overlay 盒仍投递 capturedNode，坐标按捕获 entry 的 s 换算。 */
    @Test
    public void capturedOverlayPointerMoveShouldUseSameRelativeScale() {
        SceneNode root = mainTree();
        SceneNode overlay = overlayTree(60, 40);
        OverlayHandle handle = overlayHost.register(overlay);
        handle.setRelativeScale(2.0F);
        SceneNode overlayChild = overlay.__getChildren().get(0);

        int[] moveRaw = {-1, -1};
        int[] moveLocal = {-1, -1};
        router.on(overlayChild, SceneEventType.POINTER_DOWN, (evt, ctx) -> ctx.requestPointerCapture());
        router.on(overlayChild, SceneEventType.POINTER_MOVE, (evt, ctx) -> {
            moveRaw[0] = evt.getRawPointerX();
            moveRaw[1] = evt.getRawPointerY();
            moveLocal[0] = ctx.getLocalPointerX();
            moveLocal[1] = ctx.getLocalPointerY();
        });

        router.route(root, frame(ScenePointerAction.BUTTON_DOWN, 60, 50, SceneMouseButton.LEFT), 0, 0);
        // 画布 (150,90) → 逻辑 (75,45) 已在 overlay 逻辑盒 (60x40) 外，但 capture 仍投递 capturedNode。
        router.route(root, frame(ScenePointerAction.MOVE, 150, 90, SceneMouseButton.NONE), 0, 0);

        Assert.assertEquals(75, moveRaw[0]);
        Assert.assertEquals(45, moveRaw[1]);
        Assert.assertEquals("local = 75 - 5", 70, moveLocal[0]);
        Assert.assertEquals("local = 45 - 5", 40, moveLocal[1]);
    }

    /** s == 1.0F 零回归：坐标原样，命中范围不被放宽。 */
    @Test
    public void identityRelativeScaleShouldKeepLegacyCanvasCoordinates() {
        SceneNode root = mainTree();
        SceneNode overlay = overlayTree(60, 40);
        overlayHost.register(overlay);
        SceneNode overlayChild = overlay.__getChildren().get(0);
        SceneNode mainChild = root.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        int[] raw = {-1, -1};
        int[] local = {-1, -1};
        router.on(overlayChild, SceneEventType.POINTER_DOWN, (evt, ctx) -> {
            raw[0] = evt.getRawPointerX();
            raw[1] = evt.getRawPointerY();
            local[0] = ctx.getLocalPointerX();
            local[1] = ctx.getLocalPointerY();
        });
        router.on(mainChild, SceneEventType.POINTER_DOWN, (evt, ctx) -> log.add("main"));

        router.route(root, frame(ScenePointerAction.BUTTON_DOWN, 30, 25, SceneMouseButton.LEFT), 0, 0);
        Assert.assertEquals(0, log.size());
        Assert.assertEquals(30, raw[0]);
        Assert.assertEquals(25, raw[1]);
        Assert.assertEquals(25, local[0]);
        Assert.assertEquals(20, local[1]);

        // 画布 (60,50) 在 overlay 逻辑盒 (60x40) 外 → 必须落到主树。
        router.route(root, frame(ScenePointerAction.BUTTON_DOWN, 60, 50, SceneMouseButton.LEFT), 0, 0);
        Assert.assertEquals("s == 1.0F 不得扩大 overlay 命中范围", 1, log.size());
    }

    /** CLICK 合成坐标与主体 dispatch 同口径（LCA 归属 overlay 时按该 entry 的 s 换算）。 */
    @Test
    public void synthesizedClickShouldUseOverlayRelativeScale() {
        SceneNode root = mainTree();
        SceneNode overlay = overlayTree(60, 40);
        OverlayHandle handle = overlayHost.register(overlay);
        handle.setRelativeScale(2.0F);

        int[] clickRaw = {-1, -1};
        int[] clickTreeAbs = {-1, -1};
        router.on(overlay, SceneEventType.CLICK, (evt, ctx) -> {
            clickRaw[0] = evt.getRawPointerX();
            clickRaw[1] = evt.getRawPointerY();
            clickTreeAbs[0] = ctx.getTreeRootAbsX();
            clickTreeAbs[1] = ctx.getTreeRootAbsY();
        });

        // DOWN (60,50) → 逻辑 (30,25) 命中 overlay 子节点；
        // UP (118,72) → 逻辑 (59,36) 命中 overlay 根（LCA = overlay 根）。
        router.route(root, frame(ScenePointerAction.BUTTON_DOWN, 60, 50, SceneMouseButton.LEFT), 0, 0);
        router.route(root, frame(ScenePointerAction.BUTTON_UP, 118, 72, SceneMouseButton.LEFT), 0, 0);

        Assert.assertEquals("CLICK 合成坐标按 s 换算", 59, clickRaw[0]);
        Assert.assertEquals(36, clickRaw[1]);
        Assert.assertEquals(0, clickTreeAbs[0]);
        Assert.assertEquals(0, clickTreeAbs[1]);
    }

    /** CANCEL 按其所属 overlay 的 s 换算。 */
    @Test
    public void pointerCancelShouldUseOverlayRelativeScale() {
        SceneNode root = mainTree();
        SceneNode overlay = overlayTree(60, 40);
        OverlayHandle handle = overlayHost.register(overlay);
        handle.setRelativeScale(2.0F);
        SceneNode overlayChild = overlay.__getChildren().get(0);

        int[] cancelRaw = {-1, -1};
        int[] cancelTreeAbs = {-1, -1};
        router.on(overlayChild, SceneEventType.POINTER_CANCEL, (evt, ctx) -> {
            cancelRaw[0] = evt.getRawPointerX();
            cancelRaw[1] = evt.getRawPointerY();
            cancelTreeAbs[0] = ctx.getTreeRootAbsX();
            cancelTreeAbs[1] = ctx.getTreeRootAbsY();
        });

        router.route(root, frame(ScenePointerAction.BUTTON_DOWN, 60, 50, SceneMouseButton.LEFT), 0, 0);
        router.route(root, frame(ScenePointerAction.CANCEL, 100, 90, SceneMouseButton.NONE), 0, 0);

        Assert.assertEquals("CANCEL 坐标按 pressed overlay 的 s 换算", 50, cancelRaw[0]);
        Assert.assertEquals(45, cancelRaw[1]);
        Assert.assertEquals(0, cancelTreeAbs[0]);
        Assert.assertEquals(0, cancelTreeAbs[1]);
    }

    /** 外部点击 dismiss 判定用各 entry 自己的 s：底层 overlay 不得被误判为外部点击。 */
    @Test
    public void outsideDismissShouldUseEachOverlayOwnRelativeScale() {
        SceneNode root = mainTree();
        int[] dismissCount = {0};
        SceneNode lower = overlayTree(60, 40);
        OverlayHandle lowerHandle = overlayHost.register(lower, OverlayDismissPolicy.DEFAULT,
                () -> dismissCount[0]++);
        lowerHandle.setRelativeScale(2.0F);
        SceneNode upper = overlayTree(60, 40);
        OverlayHandle upperHandle = overlayHost.register(upper);
        upperHandle.setRelativeScale(2.0F);

        // 画布 (60,50) → 逻辑 (30,25)：落在两层 overlay 内部；顶层优先命中，
        // 底层换算后也在自己盒内 → 不得请求 dismiss。
        router.route(root, frame(ScenePointerAction.BUTTON_DOWN, 60, 50, SceneMouseButton.LEFT), 0, 0);
        Assert.assertEquals("换算后位于底层 overlay 内部，不得误判外部点击", 0, dismissCount[0]);

        // 画布 (140,100) → 逻辑 (70,50)：两层 overlay (60x40) 之外 → 底层请求 dismiss。
        router.route(root, frame(ScenePointerAction.BUTTON_DOWN, 140, 100, SceneMouseButton.LEFT), 0, 0);
        Assert.assertEquals("真正的外部点必须请求 dismiss", 1, dismissCount[0]);
    }

    private SceneNode mainTree() {
        SceneNode root = new SceneNode();
        SceneNode child = new SceneNode();
        root.appendChild(child);
        root.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        child.setCachedLayout(new LayoutBox(0, 0, 200, 200));
        return root;
    }

    private SceneNode overlayTree(int width, int height) {
        SceneNode overlay = new SceneNode();
        SceneNode child = new SceneNode();
        overlay.appendChild(child);
        overlay.setCachedLayout(new LayoutBox(0, 0, width, height));
        child.setCachedLayout(new LayoutBox(5, 5, width - 10, height - 10));
        return overlay;
    }

    private SceneInputFrame frame(ScenePointerAction action, int x, int y, SceneMouseButton button) {
        frameBuilder.push(RawInputEvent.ofPointer(action, x, y, button,
                0, 0, 0, false, false, false, false, 1000L));
        return frameBuilder.drainFrame();
    }
}
