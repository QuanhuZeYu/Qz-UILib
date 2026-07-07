package club.heiqi.uilib.ui.scene.input;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneInputRouter CLICK 合成 LCA（最近公共祖先）容差守卫测试（P1-4，2026-07）。
 *
 * <p><b>背景</b>：CLICK 合成原用严格身份相等（{@code hitTarget == pressedNode}），
 * 在 keyed diff 重建节点 / layout 位移 / hover 命中盒变化时会丢 CLICK（P1 真因）。
 * P1 升级为 LCA 祖先容差：DOWN 的 pressedNode 与 UP 的 hitTarget 的最近公共祖先非空时，
 * 合成 CLICK 派发到该 LCA。算法复刻旧栈 {@code DocumentClickEventDispatcher#findNearestCommonInclusiveAncestor}。</p>
 *
 * <p>本测试覆盖三场景：
 * <ul>
 *   <li>LCA 容差：DOWN/UP 命中同 parent 的不同子节点 → CLICK 合成到 parent（改前红、改后绿）。</li>
 *   <li>严格相等向后兼容：DOWN/UP 命中同节点 → CLICK 合成到该节点（LCA=自身）。</li>
 *   <li>跨树边界：UP 命中树外（hitTarget=null）→ 不合成（LCA=null）。</li>
 * </ul>
 *
 * <p>属 input 子系统单元测试（裸 router + setCachedLayout + buildFrame），不经 runtime/signal，
 * 与 {@link SceneInputRouterTest} T14/T15 同款风格。</p>
 */
public class SceneInputRouterLcaClickTest {

    private SceneInputRouter router;
    private InputFrameBuilder frameBuilder;

    @Before
    public void setUp() {
        router = new SceneInputRouter();
        frameBuilder = new InputFrameBuilder(0, 0);
    }

    /** 辅助：构建含 root → parent → [childA, childB] 三层树，各节点 LayoutBox 分离。 */
    private SceneNode buildSiblingTree() {
        SceneNode root = new SceneNode();
        SceneNode parent = new SceneNode();
        SceneNode childA = new SceneNode();
        SceneNode childB = new SceneNode();
        root.appendChild(parent);
        parent.appendChild(childA);
        parent.appendChild(childB);

        // 树构造完成后设置 LayoutBox（hit-test 读 LayoutBox 判命中）
        root.setCachedLayout(new LayoutBox(0, 0, 300, 300));
        parent.setCachedLayout(new LayoutBox(10, 10, 200, 200));
        // childA 在 parent 左上，childB 在 parent 右下，坐标分离不重叠
        childA.setCachedLayout(new LayoutBox(20, 20, 50, 50));   // 屏幕坐标 ~ (30..80, 30..80)
        childB.setCachedLayout(new LayoutBox(100, 100, 50, 50)); // 屏幕坐标 ~ (110..160, 110..160)
        return root;
    }

    /** 辅助：构建帧（仅含一个指针事件）。 */
    private SceneInputFrame buildFrame(ScenePointerAction action, int x, int y, SceneMouseButton button) {
        frameBuilder.push(RawInputEvent.ofPointer(action, x, y,
                button, 0, 0, 0,
                false, false, false, false, 1000L));
        return frameBuilder.drainFrame();
    }

    // ===== LCA-1：兄弟节点 DOWN/UP → CLICK 合成到公共祖先 parent =====

    /**
     * LCA 容差核心用例：DOWN 命中 childA，UP 命中 childB（兄弟，同 parent）。
     *
     * <p>改前（严格相等 hitTarget == pressedNode）：childA != childB → 不合成 →
     * parent CLICK handler 不触发（红）。
     * 改后（LCA）：LCA(childA, childB) = parent → 合成 CLICK 到 parent，派发 target+bubble
     * → parent CLICK handler 触发（绿）。</p>
     */
    @Test
    public void lcaClickSynthesizesToCommonAncestorWhenDownUpHitSiblings() {
        SceneNode root = buildSiblingTree();
        SceneNode parent = root.__getChildren().get(0);
        SceneNode childA = parent.__getChildren().get(0);
        SceneNode childB = parent.__getChildren().get(1);

        List<String> log = new ArrayList<String>();
        // parent 注册 CLICK handler —— LCA 合成到此
        router.on(parent, SceneEventType.CLICK, (evt, ctx) -> {
            log.add("click:parent");
            lastClickTarget = evt.getTarget();
        });
        // childA/childB 也注册 CLICK —— LCA 合成到 parent 时不应触发子节点 handler（CLICK 不向下派发）
        router.on(childA, SceneEventType.CLICK, (evt, ctx) -> log.add("click:childA"));
        router.on(childB, SceneEventType.CLICK, (evt, ctx) -> log.add("click:childB"));

        // DOWN 命中 childA 区域（屏幕 ~50,50）
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 50, 50, SceneMouseButton.LEFT), 0, 0);
        // UP 命中 childB 区域（屏幕 ~130,130）
        router.route(root, buildFrame(ScenePointerAction.BUTTON_UP, 130, 130, SceneMouseButton.LEFT), 0, 0);

        Assert.assertEquals("LCA 合成应触发 parent CLICK 一次", 1, log.size());
        Assert.assertTrue("CLICK target 应为 parent（LCA）", log.get(0).contains("click:parent"));
        Assert.assertEquals("CLICK target 应为 parent 节点", parent, lastClickTarget);
    }

    // ===== LCA-2：严格相等向后兼容（同节点 DOWN/UP） =====

    /**
     * 向后兼容：DOWN/UP 命中同一节点 → LCA=该节点本身 → 合成 CLICK 到该节点。
     * 验证 LCA 化不破坏既有 T14 同节点 CLICK 合成语义。
     */
    @Test
    public void lcaClickSynthesizesToSameNodeWhenDownUpHitIdenticalTarget() {
        SceneNode root = buildSiblingTree();
        SceneNode parent = root.__getChildren().get(0);
        SceneNode childA = parent.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(childA, SceneEventType.CLICK, (evt, ctx) -> {
            log.add("click:childA");
            lastClickTarget = evt.getTarget();
        });

        // DOWN 与 UP 均命中 childA
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 50, 50, SceneMouseButton.LEFT), 0, 0);
        router.route(root, buildFrame(ScenePointerAction.BUTTON_UP, 50, 50, SceneMouseButton.LEFT), 0, 0);

        Assert.assertEquals("同节点 DOWN/UP 应合成 CLICK 到 childA", 1, log.size());
        Assert.assertEquals("CLICK target 应为 childA（LCA=自身）", childA, lastClickTarget);
    }

    // ===== LCA-3：UP 命中树外 → 不合成（LCA=null） =====

    /**
     * 边界：DOWN 命中 childA，UP 命中树外（hitTarget=null）→ resolveClickTarget 返回 null → 不合成。
     * 验证 LCA 化保留了「出界 UP 不合成」语义。
     */
    @Test
    public void lcaClickNotSynthesizedWhenUpOutsideTree() {
        SceneNode root = buildSiblingTree();
        SceneNode parent = root.__getChildren().get(0);
        SceneNode childA = parent.__getChildren().get(0);

        List<String> log = new ArrayList<String>();
        router.on(childA, SceneEventType.CLICK, (evt, ctx) -> log.add("click:childA"));
        router.on(parent, SceneEventType.CLICK, (evt, ctx) -> log.add("click:parent"));

        // DOWN 命中 childA
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 50, 50, SceneMouseButton.LEFT), 0, 0);
        // UP 命中树外（root bounds 外）
        router.route(root, buildFrame(ScenePointerAction.BUTTON_UP, 500, 500, SceneMouseButton.LEFT), 0, 0);

        Assert.assertTrue("UP 出界不应合成 CLICK", log.isEmpty());
    }

    // ===== LCA-4：深层 LCA —— DOWN/UP 命中不同子树，LCA 在更高层祖先 =====

    /**
     * 深层 LCA：构造 root → [parentA, parentB]，parentA→childA，parentB→childB。
     * DOWN 命中 childA，UP 命中 childB → LCA=根节点 root → CLICK 合成到 root。
     */
    @Test
    public void lcaClickSynthesizesToRootWhenDownUpHitDifferentSubtrees() {
        SceneNode root = new SceneNode();
        SceneNode parentA = new SceneNode();
        SceneNode parentB = new SceneNode();
        SceneNode childA = new SceneNode();
        SceneNode childB = new SceneNode();
        root.appendChild(parentA);
        root.appendChild(parentB);
        parentA.appendChild(childA);
        parentB.appendChild(childB);

        root.setCachedLayout(new LayoutBox(0, 0, 400, 400));
        parentA.setCachedLayout(new LayoutBox(0, 0, 150, 150));
        parentB.setCachedLayout(new LayoutBox(200, 200, 150, 150));
        childA.setCachedLayout(new LayoutBox(10, 10, 50, 50));   // 屏幕 ~ (60..110, 60..110)
        childB.setCachedLayout(new LayoutBox(10, 10, 50, 50));   // 屏幕 ~ (260..310, 260..310)

        List<String> log = new ArrayList<String>();
        router.on(root, SceneEventType.CLICK, (evt, ctx) -> {
            log.add("click:root");
            lastClickTarget = evt.getTarget();
        });

        // DOWN 命中 childA（~80,80），UP 命中 childB（~280,280）
        router.route(root, buildFrame(ScenePointerAction.BUTTON_DOWN, 80, 80, SceneMouseButton.LEFT), 0, 0);
        router.route(root, buildFrame(ScenePointerAction.BUTTON_UP, 280, 280, SceneMouseButton.LEFT), 0, 0);

        Assert.assertEquals("LCA=root 应合成 CLICK 一次", 1, log.size());
        Assert.assertEquals("CLICK target 应为 root", root, lastClickTarget);
    }

    // ===== 共享状态：捕获最近一次 CLICK 的 target 节点 =====
    private SceneNode lastClickTarget = null;
}
