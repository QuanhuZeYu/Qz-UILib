package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Router cursorSignal 单元测试（I4c cursor 投影——Router 接通验证）。
 *
 * <p>覆盖：hover 进按钮→cursorSignal 变 POINTER / 移出→变 DEFAULT /
 * cursorSignal 初始值 / route+flush 时序 / 零标脏回归。</p>
 */
public class SceneRouterCursorTest {

    private SceneInputRouter router;
    private InputFrameBuilder frameBuilder;
    private SceneRuntime runtime;

    /** mock CursorBackend：记录最后一次 apply 的 cursor 值 */
    private final List<SceneCursor> appliedCursors = new ArrayList<>();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        router = runtime.getInputRouter();
        frameBuilder = new InputFrameBuilder(0, 0);
        appliedCursors.clear();

        // 绑定 mock backend，记录 apply 调用
        runtime.bindCursor(cursor -> appliedCursors.add(cursor));
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    private SceneInputFrame buildMoveFrame(int x, int y) {
        frameBuilder.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE, x, y,
                SceneMouseButton.NONE, 0, 0, 0,
                false, false, false, false, 1000L));
        return frameBuilder.drainFrame();
    }

    private SceneNode buildTreeWithCursors() {
        SceneNode root = new SceneNode();
        SceneNode btn = new SceneNode();
        root.appendChild(btn);

        root.setCachedLayout(new LayoutBox(0, 0, 300, 300));
        btn.setCachedLayout(new LayoutBox(50, 50, 100, 40));

        // 按钮声明 POINTER，根未声明（将回退 DEFAULT）
        btn.setCursor(SceneCursor.POINTER);
        return root;
    }

    // ==================== 初始值 ====================

    @Test
    public void shouldHaveDefaultCursorSignalInitially() {
        ReadableSignal<SceneCursor> sig = runtime.cursorSignal();
        Assert.assertEquals("初始 cursorSignal 应为 DEFAULT", SceneCursor.DEFAULT, sig.get());
    }

    // ==================== hover 进按钮 → cursorSignal 变 POINTER ====================

    @Test
    public void shouldSetCursorToPointerWhenHoverButton() {
        SceneNode root = buildTreeWithCursors();

        // 首帧 flush 让 effect 首跑
        runtime.flush();
        appliedCursors.clear();

        // MOVE 进按钮
        runtime.route(root, buildMoveFrame(60, 60), 0, 0);
        runtime.flush();

        // signal 应变为 POINTER
        Assert.assertEquals("cursorSignal 应为 POINTER", SceneCursor.POINTER, runtime.cursorSignal().get());

        // mock backend 应收到 POINTER
        Assert.assertFalse("backend 应收到 apply 调用", appliedCursors.isEmpty());
        Assert.assertEquals("backend 应收到 POINTER", SceneCursor.POINTER, appliedCursors.get(appliedCursors.size() - 1));
    }

    // ==================== hover 移出按钮 → cursorSignal 变 DEFAULT ====================

    @Test
    public void shouldSetCursorToDefaultWhenMoveOutOfButton() {
        SceneNode root = buildTreeWithCursors();

        // 先 MOVE 进按钮
        runtime.route(root, buildMoveFrame(60, 60), 0, 0);
        runtime.flush();
        Assert.assertEquals("进入后应为 POINTER", SceneCursor.POINTER, runtime.cursorSignal().get());
        appliedCursors.clear();

        // MOVE 到 btn 外但仍在 root 内（root 无 cursor 声明）
        runtime.route(root, buildMoveFrame(200, 200), 0, 0);
        runtime.flush();

        Assert.assertEquals("移出按钮后应为 DEFAULT", SceneCursor.DEFAULT, runtime.cursorSignal().get());
        Assert.assertFalse("backend 应收到 apply 调用", appliedCursors.isEmpty());
        Assert.assertEquals("backend 应收到 DEFAULT", SceneCursor.DEFAULT, appliedCursors.get(appliedCursors.size() - 1));
    }

    // ==================== hover 完全移出整树 → DEFAULT ====================

    @Test
    public void shouldSetCursorToDefaultWhenMoveOutOfTree() {
        SceneNode root = buildTreeWithCursors();

        // 先 MOVE 进按钮
        runtime.route(root, buildMoveFrame(60, 60), 0, 0);
        runtime.flush();
        appliedCursors.clear();

        // MOVE 移出整树
        runtime.route(root, buildMoveFrame(500, 500), 0, 0);
        runtime.flush();

        Assert.assertEquals("移出整树后 cursorSignal 应为 DEFAULT", SceneCursor.DEFAULT, runtime.cursorSignal().get());
        Assert.assertFalse("backend 应收到 apply 调用", appliedCursors.isEmpty());
        Assert.assertEquals("backend 应收到 DEFAULT", SceneCursor.DEFAULT, appliedCursors.get(appliedCursors.size() - 1));
    }

    // ==================== 按钮间切换 ====================

    @Test
    public void shouldSwitchCursorBetweenTwoButtons() {
        SceneNode root = new SceneNode();
        SceneNode btnA = new SceneNode();
        SceneNode btnB = new SceneNode();
        root.appendChild(btnA);
        root.appendChild(btnB);

        root.setCachedLayout(new LayoutBox(0, 0, 300, 300));
        btnA.setCachedLayout(new LayoutBox(10, 10, 80, 40));
        btnB.setCachedLayout(new LayoutBox(150, 150, 80, 40));

        btnA.setCursor(SceneCursor.POINTER);
        btnB.setCursor(SceneCursor.TEXT);

        // MOVE 进 btnA
        runtime.route(root, buildMoveFrame(30, 20), 0, 0);
        runtime.flush();
        Assert.assertEquals("btnA hover → POINTER", SceneCursor.POINTER, runtime.cursorSignal().get());
        appliedCursors.clear();

        // MOVE 切换到 btnB
        runtime.route(root, buildMoveFrame(170, 160), 0, 0);
        runtime.flush();
        Assert.assertEquals("btnB hover → TEXT", SceneCursor.TEXT, runtime.cursorSignal().get());
        Assert.assertFalse("backend 应收到 apply", appliedCursors.isEmpty());
        Assert.assertEquals("backend 应收到 TEXT", SceneCursor.TEXT, appliedCursors.get(appliedCursors.size() - 1));
    }

    // ==================== cursorSignal 去重（相同值不反复 set） ====================

    @Test
    public void shouldNotApplySameCursorAgain() {
        SceneNode root = buildTreeWithCursors();

        // 首帧 flush 让 effect 首跑
        runtime.flush();
        appliedCursors.clear();

        // 连续两次 MOVE 都命中按钮
        runtime.route(root, buildMoveFrame(60, 60), 0, 0);
        runtime.flush();
        appliedCursors.clear();

        // 再次 MOVE 仍在按钮内
        runtime.route(root, buildMoveFrame(70, 65), 0, 0);
        runtime.flush();

        // cursorSignal 值未变（POINTER→POINTER），Signal.set 内部去重跳过写入，
        // effect 不应重新执行
        Assert.assertTrue("重复 POINTER 不应触发 backend apply",
                appliedCursors.isEmpty());
    }

    // ==================== ★零标脏回归（D 系列） ====================

    @Test
    public void shouldNotDirtyNodesAfterCursorRoute() {
        SceneNode root = buildTreeWithCursors();

        runtime.flush();
        clearAllDirtyRecursive(root);

        // route MOVE
        runtime.route(root, buildMoveFrame(60, 60), 0, 0);

        // flush 前零脏（queueWrite 未 apply）
        assertAllClean(root, "flush 前");

        runtime.flush();

        // flush 后 cursor effect 只调 backend.apply，不打节点脏
        // 但 hover signal 变化可能触发其他 bind（本测试无其他 bind），所以仍应干净
        assertAllClean(root, "flush 后");
    }

    // ==================== cursorSignal 在 flush 前保持旧值 ====================

    @Test
    public void shouldReturnOldValueBeforeFlush() {
        SceneNode root = buildTreeWithCursors();

        runtime.flush();
        Assert.assertEquals("flush 后初始值 DEFAULT", SceneCursor.DEFAULT, runtime.cursorSignal().get());

        // MOVE 进按钮
        runtime.route(root, buildMoveFrame(60, 60), 0, 0);

        // flush 前 signal get 返回旧值
        Assert.assertEquals("flush 前 get 返回旧值 DEFAULT", SceneCursor.DEFAULT, runtime.cursorSignal().get());

        runtime.flush();
        Assert.assertEquals("flush 后 get 返回新值 POINTER", SceneCursor.POINTER, runtime.cursorSignal().get());
    }

    // ==================== Router __getHoveredNode 辅助断言 ====================

    @Test
    public void shouldUpdateHoveredNodeOnMove() {
        SceneNode root = buildTreeWithCursors();
        SceneNode btn = root.__getChildren().get(0);

        runtime.route(root, buildMoveFrame(60, 60), 0, 0);
        runtime.flush();

        Assert.assertSame("hoveredNode 应为 btn", btn, router.__getHoveredNode());
        Assert.assertEquals("cursorSignal 应为 POINTER", SceneCursor.POINTER, runtime.cursorSignal().get());
    }

    // ==================== 辅助方法 ====================

    private void clearAllDirtyRecursive(SceneNode node) {
        if (node == null) return;
        node.clearDirtyFlags();
        node.clearGeometryDirty();
        for (SceneNode child : node.__getChildren()) {
            clearAllDirtyRecursive(child);
        }
    }

    private void assertAllClean(SceneNode root, String phase) {
        boolean[] probes = collectAllProbes(root);
        String[] names = {"selfLayout", "descLayout", "selfPaint", "descPaint",
                "composite", "selfGeom", "descGeom"};
        for (int i = 0; i < 7; i++) {
            Assert.assertFalse(phase + " 标记 " + names[i] + " 应为 false", probes[i]);
        }
    }

    private boolean[] collectAllProbes(SceneNode node) {
        boolean[] probes = new boolean[7];
        collectAllProbesRecursive(node, probes);
        return probes;
    }

    private void collectAllProbesRecursive(SceneNode node, boolean[] probes) {
        if (node == null || probes[0] && probes[1] && probes[2] && probes[3]
                && probes[4] && probes[5] && probes[6]) return;
        probes[0] |= node.__isSelfLayoutDirty();
        probes[1] |= node.__isDescendantLayoutDirty();
        probes[2] |= node.__isSelfPaintDirty();
        probes[3] |= node.__isDescendantPaintDirty();
        probes[4] |= node.__isCompositeDirty();
        probes[5] |= node.__isSelfGeometryDirty();
        probes[6] |= node.__isDescendantGeometryDirty();
        for (SceneNode child : node.__getChildren()) {
            collectAllProbesRecursive(child, probes);
        }
    }
}
