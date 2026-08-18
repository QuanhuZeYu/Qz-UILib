package club.heiqi.uilib.internal.devtools.playground;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 「单行文本」演示页回归测试（对应真机反馈两条症状）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>「受控输入」卡片输入框与「输入类型与只读态」卡片首行输入框的 value signal 相互独立——
 *       编辑其一不得改动另一个（修复前两框共用同一 {@code name} signal，值恒等、编辑互相串扰）；</li>
 *   <li>「切换只读」按钮后，只读演示输入框的 TEXT_INPUT 不再触发 onChange——
 *       readOnly signal 必须真正绑定到 SceneTextInput Props（修复前传 null 未绑定，
 *       仅不透明度变化，输入仍可编辑）。</li>
 * </ul>
 *
 * <p>headless 构造（input=null）+ 真实布局引擎 + 真实 route/flush 事件管线，
 * 与 {@link TestPlaygroundHostTest} 同口径。</p>
 */
public class PlaygroundTextInputPageTest {

    private static final int CANVAS_WIDTH = 720;
    /** 取高画布：单行文本页三张卡片纵向叠放，需避免视口裁剪影响命中测试。 */
    private static final int CANVAS_HEIGHT = 1100;

    private TestPlaygroundHost host;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new TestPlaygroundHost(null);
        doLayout();
        // 切到「单行文本」页（注册表第 2 项）并重排。
        clickNode(navSegment(1));
        doLayout();
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    /** 用宿主布局引擎对齐主树（与真机 render 前的主树 layout 同口径）。 */
    private void doLayout() {
        SceneLayoutEngine engine = host.getLayoutEngine();
        engine.layout(host.__getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** 取第 index 个导航段节点（segmented root 的第 index 个子节点）。 */
    private SceneNode navSegment(int index) {
        SceneNode segmentedRoot = host.__getNavBar().__getChildren().get(0);
        return segmentedRoot.__getChildren().get(index);
    }

    /** 在指定节点中心合成 CLICK（DOWN+UP 一帧 route + flush + 重排）。 */
    private void clickNode(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int x = box.getX() + box.getWidth() / 2;
        int y = box.getY() + box.getHeight() / 2;
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        host.__getRuntime().route(host.__getRoot(), builder.drainFrame(), 0, 0);
        host.__getRuntime().flush();
        doLayout();
    }

    /** 向当前焦点节点注入文本输入（一帧 TEXT_INPUT + flush + 重排）。 */
    private void typeText(String text) {
        InputFrameBuilder builder = new InputFrameBuilder(0, 0);
        builder.push(RawInputEvent.ofText(text, 1000L));
        host.__getRuntime().route(host.__getRoot(), builder.drainFrame(), 0, 0);
        host.__getRuntime().flush();
        doLayout();
    }

    // ==================== 页面树探针 ====================

    private SceneNode pageRoot() {
        return host.__getDisplayedPageRoot();
    }

    /** 卡片1「受控输入」的输入框根节点（直接子下标 1）。 */
    private SceneNode controlledInput() {
        SceneNode card = pageRoot().__getChildren().get(0);
        Assert.assertEquals("卡片1应为受控输入卡（结构漂移防护）",
                "受控输入（文本真值由外部 signal 唯一持有）", card.__getChildren().get(0).getText());
        return card.__getChildren().get(1);
    }

    /** 卡片2「输入类型与只读态」首行输入框根节点（直接子下标 2）。 */
    private SceneNode readOnlyDemoInput() {
        SceneNode card = pageRoot().__getChildren().get(1);
        Assert.assertEquals("卡片2应为输入类型与只读态卡（结构漂移防护）",
                "输入类型与只读态", card.__getChildren().get(0).getText());
        return card.__getChildren().get(2);
    }

    /** 快捷操作卡片「切换只读」按钮根节点（opsRow 第 3 个按钮）。 */
    private SceneNode toggleReadOnlyButton() {
        SceneNode opsCard = pageRoot().__getChildren().get(2);
        SceneNode opsRow = opsCard.__getChildren().get(1);
        return opsRow.__getChildren().get(2);
    }

    /**
     * 读取受控输入框当前显示的全部文本。
     *
     * <p>五节点结构（prefix/caret/highlight/caretAfter/suffix）下，真实文本 = prefix + highlight
     * + suffix 三叶拼接（caret 两槽为空文本，只负责分割显示位置，拼接不丢字）。</p>
     */
    private static String displayedText(SceneNode inputRoot) {
        Assert.assertEquals("TextInput 应为五节点结构（prefix/caret/highlight/caretAfter/suffix）",
                5, inputRoot.__getChildren().size());
        String prefix = inputRoot.__getChildren().get(0).getText();
        String highlight = inputRoot.__getChildren().get(2).getText();
        String suffix = inputRoot.__getChildren().get(4).getText();
        return (prefix == null ? "" : prefix) + (highlight == null ? "" : highlight)
                + (suffix == null ? "" : suffix);
    }

    // ==================== 回归用例 ====================

    /**
     * Bug1：受控输入与只读演示输入框不得共用 value signal。
     *
     * <p>断言初始显示值各不相同，且编辑「受控输入」不影响「只读演示输入框」。
     * 修复前两框共用 {@code name} signal：初始值恒等、任何编辑经 onChange 回写同一 signal，
     * 两框被联动改写（真机症状：两个输入框的值始终相等）。</p>
     */
    @Test
    public void controlledAndReadOnlyDemoInputsHaveIndependentValueSignals() {
        SceneNode controlled = controlledInput();
        SceneNode readOnlyDemo = readOnlyDemoInput();

        String controlledBefore = displayedText(controlled);
        String demoBefore = displayedText(readOnlyDemo);
        Assert.assertNotEquals("两个输入框初始值应相互独立（修复前共用 name signal 恒等）",
                controlledBefore, demoBefore);

        // 聚焦受控输入并输入字符 → onChange 回写其自身 signal。
        clickNode(controlled);
        typeText("Q");
        String controlledAfter = displayedText(controlled);
        String demoAfter = displayedText(readOnlyDemo);

        Assert.assertNotEquals("受控输入应响应输入", controlledBefore, controlledAfter);
        Assert.assertEquals("编辑受控输入不得影响只读演示输入框（修复前共用 signal 被联动改写）",
                demoBefore, demoAfter);
    }

    /**
     * Bug2：点击「切换只读」后，只读演示输入框必须真正只读——
     * TEXT_INPUT 不得再触发 onChange（显示文本保持不变）。
     *
     * <p>修复前 {@code mountInput} 对只读演示输入框传入 readOnlyIn=null，readOnly signal
     * 从未绑定到 SceneTextInput Props（仅绑定了不透明度），故切换后输入仍可编辑。</p>
     */
    @Test
    public void toggleReadOnlyBlocksTextInput() {
        SceneNode readOnlyDemo = readOnlyDemoInput();
        SceneNode toggleButton = toggleReadOnlyButton();

        // 基线：切换前输入框可编辑（把输入成功写入显示文本）。
        clickNode(readOnlyDemo);
        typeText("A");
        String editable = displayedText(readOnlyDemo);
        Assert.assertNotEquals("切换前只读演示输入框应可编辑（基线前置条件）",
                "只读内容", editable);

        // 切换只读后再聚焦并输入：显示文本必须保持不变。
        clickNode(toggleButton);
        clickNode(readOnlyDemo);
        typeText("B");
        Assert.assertEquals("切换只读后 TEXT_INPUT 不得改写文本（修复前 readOnly 未绑定 Props）",
                editable, displayedText(readOnlyDemo));
    }
}
