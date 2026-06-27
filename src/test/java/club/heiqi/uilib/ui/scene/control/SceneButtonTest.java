package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;

/**
 * SceneButton 端到端单元测试 —— 第 0 段地基总验收试金石（8 试金石）。
 *
 * <p>构造 SceneRuntime + SceneLayoutEngine + ScenePaintEngine 三件套，端到端验证
 * SceneButton 撞齐 scene 全部新地基能力：水平居中 flex、padding、边框、胶囊圆角、
 * 子节点裁剪、非白文字色、四态背景切换（且交互态切换只 PAINT 不 LAYOUT）、键盘激活。</p>
 *
 * <h3>测试沙箱 pipeline（对照 SceneHostWidget）</h3>
 * <pre>
 *   signal.set / route → runtime.flush() → layout → paint → PaintPlan 断言
 * </pre>
 */
public class SceneButtonTest {

    /** 场景根：button 作为子节点 mount 到此（route/layout/paint 入口） */
    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

    /** button 的 label 文本 signal（可写，测试驱动） */
    private Signal<String> labelSignal;
    /** button 的 enabled signal（可写，测试驱动四态） */
    private Signal<Boolean> enabledSignal;
    /** onClick 触发计数器 */
    private AtomicInteger clickCount;

    /** mount 句柄，持有 button 根节点引用 */
    private MountHandle handle;
    /** button 根节点（SceneButton.create 产出的 root） */
    private SceneNode buttonRoot;

    /** 沙箱约束宽度 */
    private static final int CANVAS_WIDTH = 200;
    /** 沙箱约束高度 */
    private static final int CANVAS_HEIGHT = 100;

    private static final int BG_ENABLED = SceneChromeTokens.BG_DEFAULT;
    private static final int BG_HOVER = SceneChromeTokens.BG_HOVER;
    private static final int BG_PRESSED = SceneChromeTokens.BG_PRESSED;
    private static final int BG_DISABLED = SceneChromeTokens.BG_DISABLED;
    private static final int TEXT_ENABLED = SceneChromeTokens.TEXT_PRIMARY;
    private static final int TEXT_DISABLED = SceneChromeTokens.TEXT_DISABLED;
    private static final int PADDING = SceneChromeTokens.PAD_MD;
    private static final int BUTTON_RADIUS = SceneChromeTokens.RADIUS_MD;
    /** FixedTextMeasurer 每字符固定宽度（与 setUp 注入的 stub 保持一致） */
    private static final int STUB_CHAR_WIDTH = 8;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();

        labelSignal = Signal.create("OK");
        enabledSignal = Signal.create(Boolean.TRUE);
        clickCount = new AtomicInteger(0);

        SceneButton.Props props = new SceneButton.Props(
                labelSignal, enabledSignal, clickCount::incrementAndGet);
        handle = runtime.mount(sceneRoot, SceneButton.create(runtime, props));
        buttonRoot = handle.getRoot();

        // 首帧 flush：让所有 bind 的 effect 首次执行（应用初始样式/文本）
        runtime.flush();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    /** 跑一帧 layout（吸收 LAYOUT 级脏） */
    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** 跑一帧 paint，返回 PaintPlan */
    private PaintPlan doPaint() {
        return paintEngine.paint(sceneRoot).getPlan();
    }

    /** flush + layout + paint 完整一帧 */
    private PaintPlan frame() {
        runtime.flush();
        doLayout();
        return doPaint();
    }

    /** button 根节点的 LayoutBox */
    private LayoutBox rootBox() {
        return (LayoutBox) buttonRoot.getCachedLayout();
    }

    /** label 子节点 */
    private SceneNode labelNode() {
        return buttonRoot.__getChildren().get(0);
    }

    /** label 子节点的 LayoutBox */
    private LayoutBox labelBox() {
        return (LayoutBox) labelNode().getCachedLayout();
    }

    /** 在 PaintPlan 中找首个指定类型命令 */
    private static PaintCommand firstOfType(List<PaintCommand> cmds, PaintCommandType type) {
        for (PaintCommand cmd : cmds) {
            if (cmd.getType() == type) {
                return cmd;
            }
        }
        return null;
    }

    /** 统计指定类型命令数 */
    private static int countType(List<PaintCommand> cmds, PaintCommandType type) {
        int count = 0;
        for (PaintCommand cmd : cmds) {
            if (cmd.getType() == type) {
                count++;
            }
        }
        return count;
    }

    /** 索引指定类型首次出现位置 */
    private static int indexOfType(List<PaintCommand> cmds, PaintCommandType type) {
        for (int i = 0; i < cmds.size(); i++) {
            if (cmds.get(i).getType() == type) {
                return i;
            }
        }
        return -1;
    }

    /** 构造单指针事件帧并 route 到 sceneRoot（rootAbs=0,0） */
    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame f = fb.drainFrame();
        runtime.route(sceneRoot, f, 0, 0);
    }

    /** 构造单键盘事件帧并 route 到 sceneRoot */
    private void routeKey(SceneKey key, SceneKeyAction action) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, action, false, false, false, false, 0, 0, 1000L));
        SceneInputFrame f = fb.drainFrame();
        runtime.route(sceneRoot, f, 0, 0);
    }

    // ==================== 试金石 1：label 居中 ====================

    /**
     * 试金石 1：flex-row + 主/交叉轴 CENTER 生效，label 在内容区内真正居中（偏离 1 已解除）。
     *
     * <p>接入真实字体度量后，叶节点 label 走 shrink-to-fit：宽=label.length()*charWidth（stub 公式），
     * 不再被 cross-align STRETCH 改写为撑满内容宽。故 ROW + 主轴 CENTER 产生<b>非 0</b>可见偏移：
     * label.x = padLeft + (内容宽 - label 宽)/2 &gt; padLeft。这是偏离 1 解除的直接证明。
     * 同时 label 仍严格落在内容区 [padLeft, 容器宽-padRight] 内。</p>
     */
    @Test
    public void labelShouldBeCenteredWithinContentBox() {
        doLayout();
        LayoutBox root = rootBox();
        LayoutBox label = labelBox();
        Assert.assertNotNull("root 应有 box", root);
        Assert.assertNotNull("label 应有 box", label);

        // 容器宽度撑满约束
        Assert.assertEquals("容器宽撑满约束", CANVAS_WIDTH, root.getWidth());

        // label 走 shrink-to-fit：宽 = "OK".length()*8（stub charWidth=8）= 16
        int expectedLabelWidth = labelNode().getText().length() * STUB_CHAR_WIDTH;
        Assert.assertEquals("label 宽 = label.length()*charWidth（shrink-to-fit）",
                expectedLabelWidth, label.getWidth());

        // ROW + 主轴 CENTER 偏移非 0（偏离 1 解除铁证）：label.x 应大于纯 padding 缩进
        int innerWidth = CANVAS_WIDTH - 2 * PADDING;
        int expectedX = PADDING + (innerWidth - expectedLabelWidth) / 2;
        Assert.assertEquals("label.x = padLeft + 居中偏移", expectedX, label.getX());
        Assert.assertTrue("ROW+CENTER 主轴偏移非 0（偏离 1 解除）", label.getX() > PADDING);

        // label 仍落在内容区内：[padLeft, 容器宽-padRight]
        Assert.assertTrue("label 左边界 >= padLeft", label.getX() >= PADDING);
        Assert.assertTrue("label 右边界 <= 容器宽 - padRight",
                label.getX() + label.getWidth() <= CANVAS_WIDTH - PADDING);
        // label 顶部至少缩进 padTop（交叉轴 CENTER：行高撑满交叉轴时 y==padTop）
        Assert.assertTrue("label.y >= padTop", label.getY() >= PADDING);
    }

    // ==================== 试金石 2：padding 10 ====================

    /**
     * 试金石 2：padding:10 使内容区四向缩进 10px。
     */
    @Test
    public void paddingShouldInsetContentByTen() {
        doLayout();
        LayoutBox root = rootBox();
        LayoutBox label = labelBox();

        // 左 padding：shrink-to-fit + 主轴 CENTER 下 label 居中，左边界 >= padLeft
        Assert.assertTrue("label 左边界 >= 左 padding", label.getX() >= PADDING);
        // 右 padding：label 右边界 <= 容器右边界 - 10（居中后仍在右 padding 内）
        Assert.assertTrue("label 右边界 <= 容器宽 - 右 padding",
                label.getX() + label.getWidth() <= root.getWidth() - PADDING);
        // 上 padding：label.y >= 10
        Assert.assertTrue("上 padding>=10", label.getY() >= PADDING);
        // 容器高度 = label 高 + 上下 padding（ROW 容器高 = crossMax + padV）
        Assert.assertEquals("容器高 = label 高 + 2*padding",
                label.getHeight() + 2 * PADDING, root.getHeight());
    }

    // ==================== 试金石 3：边框 + 胶囊圆角 ====================

    /**
     * 试金石 3：paint plan 含 BORDER 命令且 cornerRadius==RADIUS_MD（标准圆角）。
     */
    @Test
    public void paintPlanShouldContainBorderWithCapsuleRadius() {
        doLayout();
        PaintPlan plan = doPaint();
        List<PaintCommand> cmds = plan.getCommands();

        PaintCommand border = firstOfType(cmds, PaintCommandType.BORDER);
        Assert.assertNotNull("应含 BORDER 命令", border);
        Assert.assertEquals("标准圆角 cornerRadius==RADIUS_MD", BUTTON_RADIUS, border.getCornerRadius());
        Assert.assertEquals("边框宽度 1", 1, border.getBorderWidth());

        // 背景命令也应带胶囊圆角
        PaintCommand bg = firstOfType(cmds, PaintCommandType.BACKGROUND);
        Assert.assertNotNull("应含 BACKGROUND 命令", bg);
        Assert.assertEquals("背景同样带标准圆角", BUTTON_RADIUS, bg.getCornerRadius());
    }

    // ==================== 试金石 4：overflow:hidden ====================

    /**
     * 试金石 4：clipChildren=true → paint plan 含 CLIP_PUSH/CLIP_POP 包裹子树。
     */
    @Test
    public void paintPlanShouldWrapChildrenWithClip() {
        labelSignal.set("Clip");
        PaintPlan plan = frame();
        List<PaintCommand> cmds = plan.getCommands();

        int pushIdx = indexOfType(cmds, PaintCommandType.CLIP_PUSH);
        int popIdx = indexOfType(cmds, PaintCommandType.CLIP_POP);
        int textIdx = indexOfType(cmds, PaintCommandType.TEXT);

        Assert.assertTrue("应含 CLIP_PUSH", pushIdx >= 0);
        Assert.assertTrue("应含 CLIP_POP", popIdx >= 0);
        Assert.assertTrue("CLIP_PUSH 在 TEXT(子命令) 之前", pushIdx < textIdx);
        Assert.assertTrue("CLIP_POP 在 TEXT(子命令) 之后", popIdx > textIdx);
        // CLIP_PUSH 携带胶囊圆角（圆角裁剪）
        Assert.assertEquals("CLIP_PUSH 携带胶囊圆角",
                BUTTON_RADIUS, cmds.get(pushIdx).getCornerRadius());
    }

    // ==================== 试金石 5：文本色（非白） ====================

    /**
     * 试金石 5：disabled 态下 TEXT 命令的 TextStyle.color == TEXT_DISABLED（证明文本色可控非写死白）。
     */
    @Test
    public void disabledTextColorShouldBeNonWhite() {
        // 先确认 enabled 态文本白
        labelSignal.set("Btn");
        PaintPlan planEnabled = frame();
        PaintCommand textEnabled = firstOfType(planEnabled.getCommands(), PaintCommandType.TEXT);
        Assert.assertNotNull("enabled 态应有 TEXT 命令", textEnabled);
        Assert.assertEquals("enabled 文本色白", TEXT_ENABLED, textEnabled.getTextStyle().getColor());

        // 切 disabled
        enabledSignal.set(Boolean.FALSE);
        PaintPlan planDisabled = frame();
        PaintCommand textDisabled = firstOfType(planDisabled.getCommands(), PaintCommandType.TEXT);
        Assert.assertNotNull("disabled 态应有 TEXT 命令", textDisabled);
        Assert.assertEquals("disabled 文本色应为暗灰（非白）",
                TEXT_DISABLED, textDisabled.getTextStyle().getColor());
        Assert.assertNotEquals("disabled 文本色绝不等于白",
                TEXT_ENABLED, textDisabled.getTextStyle().getColor());
    }

    // ==================== 试金石 6：四态背景切换 + 终极断言 R-D1 ====================

    /**
     * 试金石 6（终极断言 R-D1）：交互态切换只触发 PAINT 不触发 LAYOUT。
     *
     * <p>验证四态背景切换正确，且每次状态切换帧 {@code __getRelayoutCount()==0}——
     * 这是"控件契约没把交互态误做成布局级"的终极证明。enabled 切换走 control 自持的
     * enabledSignal，pressed 切换走 route 真实 POINTER_DOWN 驱动 Router 写 pressed signal。</p>
     */
    @Test
    public void interactionStateSwitchShouldOnlyPaintNotLayout() {
        // 初始 enabled 态：背景 BG_ENABLED
        doLayout();
        Assert.assertEquals("初始 enabled 背景", BG_ENABLED, buttonRoot.getBackgroundColor());

        // ① enabled → disabled：背景切 BG_DISABLED，且零重排
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("disabled 背景", BG_DISABLED, buttonRoot.getBackgroundColor());
        Assert.assertEquals("R-D1: enabled→disabled 切换零重排", 0, layoutEngine.__getRelayoutCount());

        // ② disabled → enabled：背景回 BG_ENABLED，且零重排
        enabledSignal.set(Boolean.TRUE);
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("回 enabled 背景", BG_ENABLED, buttonRoot.getBackgroundColor());
        Assert.assertEquals("R-D1: disabled→enabled 切换零重排", 0, layoutEngine.__getRelayoutCount());

        // ③ 模拟 pressed：route 真实 POINTER_DOWN 命中 label 几何中心 → Router 写 pressed=true。
        //    核心验收（偏离 2 修复）：labelNode 已 setHitTestable(false)，命中穿透到 buttonRoot，
        //    故点 label 文字时最深命中目标恒为 buttonRoot，按钮正确进入 pressed 态——
        //    证明"点文字按钮也 pressed"，不再依赖"点 padding 区避开 label"的测试技巧。
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        LayoutBox label = labelBox();
        int cx = label.getX() + label.getWidth() / 2;  // label 几何中心
        int cy = label.getY() + label.getHeight() / 2;
        routePointer(ScenePointerAction.BUTTON_DOWN, cx, cy);
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("pressed 背景", BG_PRESSED, buttonRoot.getBackgroundColor());
        Assert.assertEquals("R-D1: pressed 切换零重排", 0, layoutEngine.__getRelayoutCount());

        // ④ 释放 pressed：route POINTER_UP → Router 写 pressed=false，背景回 enabled
        routePointer(ScenePointerAction.BUTTON_UP, cx, cy);
        runtime.flush();
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertEquals("释放后回 enabled 背景", BG_ENABLED, buttonRoot.getBackgroundColor());
        Assert.assertEquals("R-D1: 释放 pressed 零重排", 0, layoutEngine.__getRelayoutCount());
    }

    // ==================== 试金石 7：Enter/Space 激活 ====================

    /**
     * 试金石 7：Enter/Space 键盘激活触发 onClick；enabled=false 时点击/键盘均不触发。
     */
    @Test
    public void keyboardAndClickActivation() {
        doLayout();
        // 聚焦按钮（KEY_DOWN 需要焦点目标）
        runtime.requestFocus(buttonRoot);

        // ① Enter 激活
        int before = clickCount.get();
        routeKey(SceneKey.ENTER, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("Enter 应触发一次 onClick", before + 1, clickCount.get());

        // ② Space 激活
        before = clickCount.get();
        routeKey(SceneKey.SPACE, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("Space 应触发一次 onClick", before + 1, clickCount.get());

        // ③ CLICK（指针）激活：DOWN+UP 同节点合成 CLICK
        before = clickCount.get();
        LayoutBox box = rootBox();
        int cx = box.getX() + box.getWidth() / 2;
        int cy = box.getY() + box.getHeight() / 2;
        routePointer(ScenePointerAction.BUTTON_DOWN, cx, cy);
        routePointer(ScenePointerAction.BUTTON_UP, cx, cy);
        runtime.flush();
        Assert.assertEquals("指针 CLICK 应触发一次 onClick", before + 1, clickCount.get());

        // ④ disabled 态：Enter / CLICK 均不触发
        enabledSignal.set(Boolean.FALSE);
        runtime.flush();
        before = clickCount.get();
        routeKey(SceneKey.ENTER, SceneKeyAction.PRESSED);
        runtime.flush();
        Assert.assertEquals("disabled 态 Enter 不触发", before, clickCount.get());

        routePointer(ScenePointerAction.BUTTON_DOWN, cx, cy);
        routePointer(ScenePointerAction.BUTTON_UP, cx, cy);
        runtime.flush();
        Assert.assertEquals("disabled 态 CLICK 不触发", before, clickCount.get());
    }

    // ==================== 试金石 8（软约束）：opacity + clip 嵌套顺序 ====================

    /**
     * 试金石 8（oracle 软约束）：root 同开 opacity<1 + clipChildren 时，
     * PaintPlan 命令序列严格嵌套 [PUSH_OPACITY, CLIP_PUSH, ...子树.., CLIP_POP, POP_OPACITY]
     * （opacity 外层、clip 内层）。这是 overflow:hidden + hover 半透明真实会撞的组合。
     */
    @Test
    public void opacityAndClipShouldNestOpacityOuterClipInner() {
        labelSignal.set("X");
        // 给 button root 叠加 opacity<1（COMPOSITE 级，不重排）
        buttonRoot.setOpacity(0.5f);
        PaintPlan plan = frame();
        List<PaintCommand> cmds = plan.getCommands();

        int pushOpacity = indexOfType(cmds, PaintCommandType.PUSH_OPACITY);
        int clipPush = indexOfType(cmds, PaintCommandType.CLIP_PUSH);
        int clipPop = indexOfType(cmds, PaintCommandType.CLIP_POP);
        int popOpacity = indexOfType(cmds, PaintCommandType.POP_OPACITY);

        Assert.assertTrue("应含 PUSH_OPACITY", pushOpacity >= 0);
        Assert.assertTrue("应含 CLIP_PUSH", clipPush >= 0);
        Assert.assertTrue("应含 CLIP_POP", clipPop >= 0);
        Assert.assertTrue("应含 POP_OPACITY", popOpacity >= 0);

        // 严格嵌套：PUSH_OPACITY < CLIP_PUSH < CLIP_POP < POP_OPACITY
        Assert.assertTrue("opacity 外层：PUSH_OPACITY 在 CLIP_PUSH 之前", pushOpacity < clipPush);
        Assert.assertTrue("clip 内层先闭合：CLIP_POP 在 POP_OPACITY 之前", clipPop < popOpacity);
        Assert.assertTrue("CLIP_PUSH 在 CLIP_POP 之前", clipPush < clipPop);

        // 仅各一对
        Assert.assertEquals("仅 1 个 PUSH_OPACITY", 1, countType(cmds, PaintCommandType.PUSH_OPACITY));
        Assert.assertEquals("仅 1 个 CLIP_PUSH", 1, countType(cmds, PaintCommandType.CLIP_PUSH));
    }
}
