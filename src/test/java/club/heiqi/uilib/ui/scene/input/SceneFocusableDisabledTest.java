package club.heiqi.uilib.ui.scene.input;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * disabled 控件 focusable 动态进出 Tab 环验收（兑现 package-info R9「disabled 不可聚焦」）。
 *
 * <p>验证 {@link SceneRuntime#focusable(SceneNode, club.heiqi.uilib.ui.reactive.ReadableSignal)}
 * signal 驱动重载的三条核心行为：
 * <ol>
 *   <li>enabled=false 时控件不在 Tab 环（Tab 跳过）</li>
 *   <li>enabled true→false 时若该控件正聚焦，焦点立即清失</li>
 *   <li>enabled false→true 时恢复到原 DOM 前序位置（不跑末尾）</li>
 * </ol>
 */
public class SceneFocusableDisabledTest {

    private SceneRuntime runtime;
    private SceneInputRouter router;
    private FocusManager fm;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        router = runtime.getInputRouter();
        fm = router.__getFocusManager();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 构建根 + n 个顺序子节点 */
    private SceneNode buildChain(int n) {
        SceneNode root = new SceneNode();
        for (int i = 0; i < n; i++) {
            root.appendChild(new SceneNode());
        }
        return root;
    }

    // ==================== 验收 ====================

    // 1：disabled 控件不在 Tab 环，Tab 跳过
    @Test
    public void disabledNodeShouldBeSkippedInTabRing() {
        SceneNode root = buildChain(3);
        SceneNode a = root.__getChildren().get(0);
        SceneNode b = root.__getChildren().get(1);
        SceneNode c = root.__getChildren().get(2);

        Signal<Boolean> aEn = Signal.create(Boolean.TRUE);
        Signal<Boolean> bEn = Signal.create(Boolean.FALSE);
        Signal<Boolean> cEn = Signal.create(Boolean.TRUE);

        runtime.focusable(a, aEn);
        runtime.focusable(b, bEn);
        runtime.focusable(c, cEn);
        runtime.flush(); // 首次 flush 跑 effect：a/c 注册，b 不注册

        fm.setRoot(root);

        // DOM 前序：a, b, c；但 b disabled → Tab 环 [a, c]
        fm.focusNext();
        Assert.assertSame("无焦点时聚焦首个 enabled focusable a", a, router.__getFocusedNode());

        fm.focusNext();
        Assert.assertSame("Tab 应跳过 disabled 的 b，到 c", c, router.__getFocusedNode());

        fm.focusNext();
        Assert.assertSame("循环回 a", a, router.__getFocusedNode());

        // 反向同样跳过 b
        fm.focusPrevious();
        Assert.assertSame("Shift+Tab 从 a 循环到 c", c, router.__getFocusedNode());
    }

    // 2：enabled true→false 时若该控件正聚焦，焦点立即清失
    @Test
    public void focusShouldClearWhenFocusedNodeBecomesDisabled() {
        SceneNode root = buildChain(2);
        SceneNode a = root.__getChildren().get(0);
        SceneNode b = root.__getChildren().get(1);

        Signal<Boolean> aEn = Signal.create(Boolean.TRUE);
        Signal<Boolean> bEn = Signal.create(Boolean.TRUE);

        runtime.focusable(a, aEn);
        runtime.focusable(b, bEn);
        runtime.flush();

        fm.setRoot(root);
        fm.focusNext();
        Assert.assertSame("聚焦 a", a, router.__getFocusedNode());

        // a disabled → 焦点应清失
        aEn.set(Boolean.FALSE);
        runtime.flush();

        Assert.assertNull("a disabled 后焦点应清空", router.__getFocusedNode());
        Assert.assertFalse("a 应已退出 focusables", router.__isFocusable(a));
        Assert.assertTrue("b 仍在 focusables", router.__isFocusable(b));
    }

    // 3：enabled false→true 时恢复到原 DOM 前序位置
    @Test
    public void focusableShouldRestoreAtOriginalDomPositionWhenReEnabled() {
        SceneNode root = buildChain(3);
        SceneNode a = root.__getChildren().get(0);
        SceneNode b = root.__getChildren().get(1);
        SceneNode c = root.__getChildren().get(2);

        Signal<Boolean> aEn = Signal.create(Boolean.TRUE);
        Signal<Boolean> bEn = Signal.create(Boolean.FALSE);
        Signal<Boolean> cEn = Signal.create(Boolean.TRUE);

        runtime.focusable(a, aEn);
        runtime.focusable(b, bEn);
        runtime.focusable(c, cEn);
        runtime.flush();

        fm.setRoot(root);

        // 初始 Tab 环 [a, c]
        fm.focusNext();
        Assert.assertSame("聚焦 a", a, router.__getFocusedNode());
        fm.focusNext();
        Assert.assertSame("到 c", c, router.__getFocusedNode());

        // b 恢复 enabled → Tab 环应变 [a, b, c]
        bEn.set(Boolean.TRUE);
        runtime.flush();
        Assert.assertTrue("b 应重新进入 focusables", router.__isFocusable(b));

        // 从 c 继续 Tab → 循环回 a → 再 Tab 应到 b（DOM 位置恢复，不跑末尾）
        fm.focusNext();
        Assert.assertSame("从 c 循环回 a", a, router.__getFocusedNode());
        fm.focusNext();
        Assert.assertSame("b 恢复后应在 a 之后、c 之前（DOM 前序位置）", b, router.__getFocusedNode());
        fm.focusNext();
        Assert.assertSame("再到 c", c, router.__getFocusedNode());
    }

    // 4：初始 enabled=false 时完全不注册
    @Test
    public void initiallyDisabledNodeShouldNotBeRegistered() {
        SceneNode root = buildChain(1);
        SceneNode a = root.__getChildren().get(0);

        Signal<Boolean> aEn = Signal.create(Boolean.FALSE);
        runtime.focusable(a, aEn);
        runtime.flush();

        fm.setRoot(root);
        Assert.assertFalse("初始 disabled 节点不应在 focusables", router.__isFocusable(a));

        fm.focusNext();
        Assert.assertNull("无 focusable 时焦点仍 null", router.__getFocusedNode());
    }

    // 5：disabled 节点不被隐式 POINTER_DOWN 聚焦（findDeepestFocusable 只查注册表）
    @Test
    public void disabledNodeShouldNotBeImplicitlyFocusedOnPointerDown() {
        SceneNode root = new SceneNode();
        SceneNode a = new SceneNode();
        SceneNode b = new SceneNode();
        root.appendChild(a);
        root.appendChild(b);

        // 给 a/b 几何，让 hit-test 命中
        root.setCachedLayout(new club.heiqi.uilib.ui.scene.layout.LayoutBox(0, 0, 200, 100));
        a.setCachedLayout(new club.heiqi.uilib.ui.scene.layout.LayoutBox(0, 0, 100, 100));
        b.setCachedLayout(new club.heiqi.uilib.ui.scene.layout.LayoutBox(100, 0, 100, 100));

        Signal<Boolean> aEn = Signal.create(Boolean.FALSE);
        Signal<Boolean> bEn = Signal.create(Boolean.TRUE);

        runtime.focusable(a, aEn);
        runtime.focusable(b, bEn);
        runtime.flush();

        // 点击 a 区域：a disabled 不在 focusables，命中链最深 focusable 为 null → clearFocus
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, 50, 50,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        runtime.route(root, fb.drainFrame(), 0, 0);

        Assert.assertNull("点击 disabled 的 a 不应聚焦", router.__getFocusedNode());

        // 点击 b 区域：b enabled → 聚焦 b
        InputFrameBuilder fb2 = new InputFrameBuilder(0, 0);
        fb2.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, 150, 50,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        runtime.route(root, fb2.drainFrame(), 0, 0);

        Assert.assertSame("点击 enabled 的 b 应聚焦 b", b, router.__getFocusedNode());
    }
}
