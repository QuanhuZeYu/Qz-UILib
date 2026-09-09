package club.heiqi.uilib.internal.devtools.playground;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.internal.devtools.playground.pages.TextInputPage;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 「单行文本」演示页回归测试（对应真机反馈两条症状）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>「受控输入」卡片输入框与「输入类型与只读态」卡片首行输入框的 value signal 相互独立——
 *       编辑其一不得改动另一个（修复前两框共用同一 {@code name} signal，值恒等、编辑互相串扰）；</li>
 *   <li>「切换只读」按钮后，只读演示输入框的 TEXT_INPUT 不再触发 onChange——
 *       readOnly signal 必须真正绑定到 SceneTextInput Props（修复前传 null 未绑定，
 *       仅不透明度变化，输入仍可编辑）；</li>
 *   <li>G16/TextInputPage：三处实时读数文本（受控/密码/数字）取来源主题次要前景，
 *       主题切换后颜色更新、节点身份不变、页面数据不变；页面身份与结构不变。</li>
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
        // 切到「单行文本」页（注册表第 2 项）并重排。C9·7：钉落点 id——点击式切页若静默
        // miss（页增删后段位移动、画布变窄），displayed 会停在 home 而 setUp 不红。
        clickNode(navSegment(1));
        doLayout();
        Assert.assertEquals("setUp 切页必须真实落到「单行文本」页", "text-input",
                host.__getDisplayedPageId());
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

    // ==================== G16/TextInputPage：读数文本默认消费主题 ====================

    /** 宿主默认档（深色液态玻璃）。 */
    private static final SceneTheme DARK = SceneThemes.DEFAULT;
    /** 浅色档：次要前景与深色档不同，用于主题切换断言。 */
    private static final SceneTheme LIGHT = SceneTheme.liquidGlassLight();

    /** 卡片1「受控输入」的实时回读节点（直接子下标 3：title/input/hint/readout）。 */
    private SceneNode controlledReadout() {
        SceneNode card = pageRoot().__getChildren().get(0);
        Assert.assertEquals("卡片1应为受控输入卡（结构漂移防护）",
                "受控输入（文本真值由外部 signal 唯一持有）", card.__getChildren().get(0).getText());
        return card.__getChildren().get(3);
    }

    /** 卡片2 密码读数节点（直接子下标 5：title/hint/input/hint/input/readout）。 */
    private SceneNode secretReadout() {
        SceneNode card = pageRoot().__getChildren().get(1);
        Assert.assertEquals("卡片2应为输入类型与只读态卡（结构漂移防护）",
                "输入类型与只读态", card.__getChildren().get(0).getText());
        return card.__getChildren().get(5);
    }

    /** 卡片2 数字读数节点（直接子下标 8）。 */
    private SceneNode numberReadout() {
        return pageRoot().__getChildren().get(1).__getChildren().get(8);
    }

    /**
     * 默认工厂路径：三处读数文本由静态 {@code PlaygroundKit.MUTED} 改为来源主题
     * {@link SceneThemes#mutedForeground} 派生信号——颜色等于主题次要前景，字号/命中语义与
     * 读数数据（由页面 signal 派生）均不变。
     */
    @Test
    public void readoutTextsFollowSourceThemeMutedForeground() {
        SceneNode[] readouts = { controlledReadout(), secretReadout(), numberReadout() };
        for (int i = 0; i < readouts.length; i++) {
            Assert.assertEquals("读数文本取主题次要前景 #" + i,
                    DARK.mutedForeground(), readouts[i].getTextColor());
            Assert.assertEquals("读数文本字号保持 12 #" + i, 12, readouts[i].getFontSize());
            Assert.assertFalse("读数文本不可命中 #" + i, readouts[i].isHitTestable());
        }
        Assert.assertTrue("受控读数数据锚（页面 signal 派生）",
                controlledReadout().getText().startsWith("当前文本：Hello Qz UILib"));
        Assert.assertTrue("密码读数数据锚（页面 signal 派生）",
                secretReadout().getText().startsWith("真实值：p@ssw0rd"));
        Assert.assertTrue("数字读数数据锚（页面 signal 派生）",
                numberReadout().getText().startsWith("当前值：3.14159"));
    }

    /**
     * 主题切换：{@code __getThemeSignal().set(LIGHT)} + flush 后三处读数颜色更新为浅色档
     * 次要前景，节点身份不变、订阅数不增长，读数与输入框显示文本（页面数据）不变。
     */
    @Test
    public void themeSwitchUpdatesReadoutColorsWithoutRebuildOrDataLoss() {
        Assert.assertNotEquals("测试前提：深浅次要前景必须不同",
                DARK.mutedForeground(), LIGHT.mutedForeground());

        SceneNode controlled = controlledReadout();
        SceneNode secret = secretReadout();
        SceneNode number = numberReadout();
        String controlledText = controlled.getText();
        String secretText = secret.getText();
        String numberText = number.getText();
        String inputText = displayedText(controlledInput());
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        host.__getThemeSignal().set(LIGHT);
        host.__getRuntime().flush();

        Assert.assertSame("主题切换不重建受控读数节点", controlled, controlledReadout());
        Assert.assertSame("主题切换不重建密码读数节点", secret, secretReadout());
        Assert.assertSame("主题切换不重建数字读数节点", number, numberReadout());
        Assert.assertEquals("受控读数取浅色档次要前景",
                LIGHT.mutedForeground(), controlled.getTextColor());
        Assert.assertEquals("密码读数取浅色档次要前景",
                LIGHT.mutedForeground(), secret.getTextColor());
        Assert.assertEquals("数字读数取浅色档次要前景",
                LIGHT.mutedForeground(), number.getTextColor());
        Assert.assertEquals("主题切换不改受控读数数据", controlledText, controlled.getText());
        Assert.assertEquals("主题切换不改密码读数数据", secretText, secret.getText());
        Assert.assertEquals("主题切换不改数字读数数据", numberText, number.getText());
        Assert.assertEquals("主题切换不改输入框显示文本（页面数据）",
                inputText, displayedText(controlledInput()));
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    /** 页面 id/标题/说明、注册名与根结构不变（迁移只改读数取色路径）。 */
    @Test
    public void pageIdentityStructureAndRegistrationUnchanged() {
        TextInputPage page = new TextInputPage();
        Assert.assertEquals("页面 id 不变", "text-input", page.id());
        Assert.assertEquals("页面标题不变", "单行文本", page.title());
        Assert.assertEquals("页面说明不变",
                "SceneTextInput：受控契约、placeholder、限长、只读、密码/数字、实时回读",
                page.description());
        Assert.assertTrue("注册表 text-input 项仍是 TextInputPage 实例",
                PlaygroundPageRegistry.lookup("text-input") instanceof TextInputPage);
        Assert.assertEquals("宿主落点 id 不变", "text-input", host.__getDisplayedPageId());

        SceneNode root = pageRoot();
        Assert.assertEquals("页面根仍为 3 张卡片", 3, root.__getChildren().size());
        Assert.assertEquals("卡片1标题不变", "受控输入（文本真值由外部 signal 唯一持有）",
                root.__getChildren().get(0).__getChildren().get(0).getText());
        Assert.assertEquals("卡片2标题不变", "输入类型与只读态",
                root.__getChildren().get(1).__getChildren().get(0).getText());
        Assert.assertEquals("卡片3标题不变", "快捷操作",
                root.__getChildren().get(2).__getChildren().get(0).getText());
    }
}
