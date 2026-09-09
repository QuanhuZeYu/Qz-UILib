package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneContextMenu;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneContextMenu 独立单元测试：overlay 挂载/锚点、点击激活与关闭、
 * ESC/外部点击关闭、↑/↓/Enter 键盘导航（跳分隔线、disabled 不激活）、Handle 幂等关闭，
 * 以及主题化后的默认 OVERLAY 浮层配方、菜单行轻量状态覆盖与切主题/关闭回收。
 *
 * <p>锚点定位由 SceneFramePipeline 的 layoutOverlays 在真机执行；测试无管线，
 * overlay root 手动全尺寸布局 + open(0,0) 沙箱（与 SceneSelectPrimitiveTest 同款假设）。</p>
 */
public class SceneContextMenuTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    private SceneInteractionHarness harness;

    private List<String> activationLog;

    private static final int CANVAS_WIDTH = 240;
    private static final int CANVAS_HEIGHT = 160;
    private static final int STUB_CHAR_WIDTH = 8;
    private static final float EPSILON = 0.0001F;

    /** 库默认主题的 OVERLAY 角色配方：菜单面板默认外观唯一来源。 */
    private static final SceneSurfaceStyle OVERLAY_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.OVERLAY);
    private static final int OVERLAY_BG_IDLE = OVERLAY_SURFACE.getIdle().getTint();
    /** 菜单行轻量覆盖强度（与实现常量同源；断言取派生色而非硬编码色号）。 */
    private static final int ITEM_BG_TRANSPARENT = 0x00000000;
    private static final int ITEM_HOVER_ALPHA = 0x1F;
    private static final int ITEM_HIGHLIGHT_ALPHA = 0x59;
    private static final int TEXT_NORMAL = SceneThemes.DEFAULT.foreground();
    private static final int TEXT_DISABLED = SceneThemes.DEFAULT.disabledForeground();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();
        activationLog = new ArrayList<>();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    private List<SceneContextMenu.MenuItem> threeItems() {
        return Arrays.asList(
                SceneContextMenu.MenuItem.of("复制", () -> activationLog.add("copy")),
                SceneContextMenu.MenuItem.of("粘贴", () -> activationLog.add("paste")),
                SceneContextMenu.MenuItem.of("删除", () -> activationLog.add("delete")));
    }

    private SceneContextMenu.Handle openAt(int x, int y, List<SceneContextMenu.MenuItem> items) {
        SceneContextMenu.Handle handle = SceneContextMenu.open(runtime, x, y, items);
        runtime.flush();
        doLayout();
        return handle;
    }

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        }
    }

    private SceneNode overlayRoot() {
        return runtime.getOverlayHost().bottomFirst().get(0).getRoot();
    }

    /** 菜单直接子节点（item 行/分隔线，按 items 顺序）。 */
    private SceneNode menuChild(int index) {
        return overlayRoot().__getChildren().get(index);
    }

    private int overlaySize() {
        return runtime.getOverlayHost().size();
    }

    private void routeKeyAndFlush(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, 0, 0, 1000L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    private void pressAt(int absX, int absY) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, absX, absY,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    /** 指针移动（MOVE → hover 切换，router 自动更新 hovered 信号）。 */
    private void moveTo(int absX, int absY) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE, absX, absY,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 2000L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    /** DOWN + UP 合成 CLICK（菜单项激活走 CLICK 事件）。 */
    private void pressAndReleaseAt(int absX, int absY) {
        pressAt(absX, absY);
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, absX, absY,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        runtime.route(sceneRoot, fb.drainFrame(), 0, 0);
        runtime.flush();
    }

    private int[] absCenter(SceneNode node) {
        LayoutBox b = (LayoutBox) node.getCachedLayout();
        int ax = b.getX();
        int ay = b.getY();
        SceneNode parent = node.__getParent();
        while (parent != null) {
            LayoutBox parentBox = (LayoutBox) parent.getCachedLayout();
            if (parentBox != null) {
                ax += parentBox.getX();
                ay += parentBox.getY();
            }
            parent = parent.__getParent();
        }
        return new int[] {ax + b.getWidth() / 2, ay + b.getHeight() / 2};
    }

    /** 在携带局部主题的挂载作用域内打开菜单（portal 内容继承来源主题）。 */
    private SceneContextMenu.Handle openAtWithTheme(Signal<SceneTheme> pageTheme,
                                                    List<SceneContextMenu.MenuItem> items) {
        final SceneContextMenu.Handle[] holder = {null};
        runtime.mount(sceneRoot, () -> {
            SceneNode page = new SceneNode();
            SceneThemes.withTheme(pageTheme, () -> holder[0] = SceneContextMenu.open(runtime, 0, 0, items));
            return page;
        });
        runtime.flush();
        doLayout();
        return holder[0];
    }

    /** 菜单行 label 节点（行内唯一子节点）。 */
    private SceneNode itemLabel(int index) {
        return menuChild(index).__getChildren().get(0);
    }

    /** 高亮行的期望背景：主题选区背景半透明覆盖。 */
    private static int highlightBg(SceneTheme theme) {
        return tint(theme.selectionBackground(), ITEM_HIGHLIGHT_ALPHA);
    }

    private PaintPlan doPaint(SceneNode root) {
        return paintEngine.paint(root).getPlan();
    }

    private static int countType(List<PaintCommand> commands, PaintCommandType type) {
        int count = 0;
        for (PaintCommand command : commands) {
            if (command.getType() == type) {
                count++;
            }
        }
        return count;
    }

    private static int indexOfType(List<PaintCommand> commands, PaintCommandType type) {
        for (int i = 0; i < commands.size(); i++) {
            if (commands.get(i).getType() == type) {
                return i;
            }
        }
        return -1;
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int tint(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    // ==================== 打开/关闭 ====================

    @Test
    public void openRegistersOverlayAndHandleState() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        Assert.assertTrue("打开后 isOpen", handle.isOpen());
        Assert.assertEquals("overlay 挂载 1 个", 1, overlaySize());
        Assert.assertEquals("菜单子节点 = 3 item 行", 3, overlayRoot().__getChildren().size());
    }

    @Test
    public void handleCloseIsIdempotentAndUnmountsOverlay() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        handle.close();
        runtime.flush();
        Assert.assertFalse("close 后 isOpen=false", handle.isOpen());
        Assert.assertEquals("close 后 overlay 卸载", 0, overlaySize());
        handle.close(); // 幂等无异常
        runtime.flush();
        Assert.assertEquals(0, overlaySize());
    }

    @Test
    public void clickItemFiresOnSelectAndCloses() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        SceneNode item = menuChild(1);
        int[] c = absCenter(item);
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("激活一次 paste", Arrays.asList("paste"), activationLog);
        Assert.assertFalse("选择后关闭", handle.isOpen());
        Assert.assertEquals(0, overlaySize());
    }

    @Test
    public void clickOutsideClosesWithoutActivation() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        // 菜单锚点 (0,0)，点击远处空白
        pressAt(CANVAS_WIDTH - 2, CANVAS_HEIGHT - 2);
        runtime.flush();
        Assert.assertEquals("外部点击不激活", 0, activationLog.size());
        Assert.assertFalse("外部点击关闭", handle.isOpen());
        Assert.assertEquals(0, overlaySize());
    }

    @Test
    public void escapeClosesMenu() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        routeKeyAndFlush(SceneKey.ESCAPE);
        Assert.assertFalse("ESC 关闭", handle.isOpen());
        Assert.assertEquals(0, overlaySize());
    }

    // ==================== 键盘导航 ====================

    @Test
    public void arrowKeysNavigateAndEnterActivates() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 无高亮 → 高亮 0（复制）
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // → 粘贴
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // → 删除
        routeKeyAndFlush(SceneKey.ARROW_UP);   // → 粘贴
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("Enter 激活高亮项（粘贴）", Arrays.asList("paste"), activationLog);
        Assert.assertFalse("激活后关闭", handle.isOpen());
    }

    @Test
    public void arrowNavigationWrapsAround() {
        openAt(0, 0, threeItems());
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 0
        routeKeyAndFlush(SceneKey.ARROW_UP);   // wrap → 2（删除）
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("上移环绕激活末项", Arrays.asList("delete"), activationLog);
    }

    @Test
    public void disabledItemNotActivatedButStillHighlighted() {
        List<SceneContextMenu.MenuItem> items = Arrays.asList(
                SceneContextMenu.MenuItem.of("复制", () -> activationLog.add("copy")),
                SceneContextMenu.MenuItem.of("粘贴", false, () -> activationLog.add("paste")),
                SceneContextMenu.MenuItem.of("删除", () -> activationLog.add("delete")));
        SceneContextMenu.Handle handle = openAt(0, 0, items);
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 高亮 0
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 高亮 1（disabled）
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("disabled 项不激活", 0, activationLog.size());
        Assert.assertFalse("Enter 仍关闭菜单", handle.isOpen());
    }

    // ==================== hover 高亮 ====================

    @Test
    public void hoverHighlightsItemAndEnterActivatesIt() {
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        // 初始无高亮：三项均为透明底（露出浮层玻璃）
        Assert.assertEquals(ITEM_BG_TRANSPARENT, menuChild(1).getBackgroundColor());
        int[] c = absCenter(menuChild(1));
        moveTo(c[0], c[1]);
        Assert.assertEquals("hover 项高亮背景 = 主题选区背景半透明",
                highlightBg(SceneThemes.DEFAULT), menuChild(1).getBackgroundColor());
        Assert.assertEquals("非 hover 项保持透明", ITEM_BG_TRANSPARENT, menuChild(0).getBackgroundColor());
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("Enter 激活 hover 项（粘贴）", Arrays.asList("paste"), activationLog);
        Assert.assertFalse("激活后关闭", handle.isOpen());
    }

    @Test
    public void hoverRetargetsHighlightAcrossItems() {
        openAt(0, 0, threeItems());
        int[] c0 = absCenter(menuChild(0));
        moveTo(c0[0], c0[1]);
        Assert.assertEquals("hover 首项高亮", highlightBg(SceneThemes.DEFAULT),
                menuChild(0).getBackgroundColor());
        int[] c2 = absCenter(menuChild(2));
        moveTo(c2[0], c2[1]);
        Assert.assertEquals("hover 换项后高亮跟随", highlightBg(SceneThemes.DEFAULT),
                menuChild(2).getBackgroundColor());
        Assert.assertEquals("原项恢复透明", ITEM_BG_TRANSPARENT, menuChild(0).getBackgroundColor());
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("Enter 激活最终 hover 项（删除）", Arrays.asList("delete"), activationLog);
    }

    @Test
    public void arrowNavigationContinuesFromHoveredItem() {
        openAt(0, 0, threeItems());
        int[] c1 = absCenter(menuChild(1));
        moveTo(c1[0], c1[1]);
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 从 hover 项（1）下移 → 2
        Assert.assertEquals("键盘高亮从 hover 项继续", highlightBg(SceneThemes.DEFAULT),
                menuChild(2).getBackgroundColor());
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("Enter 激活键盘移动后的项（删除）", Arrays.asList("delete"), activationLog);
    }

    @Test
    public void hoverDisabledItemHighlightsButEnterDoesNotActivate() {
        List<SceneContextMenu.MenuItem> items = Arrays.asList(
                SceneContextMenu.MenuItem.of("复制", () -> activationLog.add("copy")),
                SceneContextMenu.MenuItem.of("粘贴", false, () -> activationLog.add("paste")),
                SceneContextMenu.MenuItem.of("删除", () -> activationLog.add("delete")));
        SceneContextMenu.Handle handle = openAt(0, 0, items);
        int[] c1 = absCenter(menuChild(1));
        moveTo(c1[0], c1[1]);
        Assert.assertEquals("disabled 项 hover 同样高亮（与键盘语义一致）",
                highlightBg(SceneThemes.DEFAULT), menuChild(1).getBackgroundColor());
        Assert.assertEquals("disabled 行文字 = 主题禁用前景", TEXT_DISABLED, itemLabel(1).getTextColor());
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("disabled 项不激活", 0, activationLog.size());
        Assert.assertFalse("Enter 仍关闭菜单（现状语义）", handle.isOpen());
    }

    @Test
    public void separatorSkippedInNavigation() {
        List<SceneContextMenu.MenuItem> items = Arrays.asList(
                SceneContextMenu.MenuItem.of("复制", () -> activationLog.add("copy")),
                SceneContextMenu.MenuItem.divider(),
                SceneContextMenu.MenuItem.of("删除", () -> activationLog.add("delete")));
        openAt(0, 0, items);
        Assert.assertEquals("菜单子节点 = 2 item + 1 分隔线", 3, overlayRoot().__getChildren().size());
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 高亮 0（复制）
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 跳分隔线 → 删除
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("导航跳过分隔线激活删除", Arrays.asList("delete"), activationLog);
    }

    @Test
    public void disabledItemClickDoesNotActivateButCloses() {
        List<SceneContextMenu.MenuItem> items = Arrays.asList(
                SceneContextMenu.MenuItem.of("复制", () -> activationLog.add("copy")),
                SceneContextMenu.MenuItem.of("粘贴", false, null));
        SceneContextMenu.Handle handle = openAt(0, 0, items);
        SceneNode item = menuChild(1);
        int[] c = absCenter(item);
        pressAndReleaseAt(c[0], c[1]);
        Assert.assertEquals("disabled 点击不激活", 0, activationLog.size());
        Assert.assertFalse("点击仍关闭", handle.isOpen());
    }

    // ==================== 主题化：默认 OVERLAY 浮层配方 ====================

    /**
     * 默认工厂路径：菜单面板表面等于 {@link SceneThemes#DEFAULT} 的 OVERLAY 角色配方
     * （含滤镜与实体高度），面板只采样一次滤镜；菜单行不装滤镜、默认透明、文字取主题语义前景；
     * 分隔线取浮层配方缘色。
     */
    @Test
    public void defaultPanelUsesOverlayRecipeWithSingleBackdropAndRowsWithoutFilter() {
        openAt(0, 0, Arrays.asList(
                SceneContextMenu.MenuItem.of("复制", () -> activationLog.add("copy")),
                SceneContextMenu.MenuItem.divider(),
                SceneContextMenu.MenuItem.of("粘贴", false, () -> activationLog.add("paste"))));

        SceneNode panel = overlayRoot();
        Assert.assertEquals("面板背景 = OVERLAY 配方 idle 染色", OVERLAY_BG_IDLE, panel.getBackgroundColor());
        Assert.assertEquals("面板边框宽 = OVERLAY 配方", OVERLAY_SURFACE.getBorderWidth(), panel.getBorderWidth());
        Assert.assertEquals("面板圆角 = OVERLAY 配方", OVERLAY_SURFACE.getCornerRadius(), panel.getCornerRadius());
        Assert.assertEquals("面板实体高度 = OVERLAY 配方", OVERLAY_SURFACE.getIdle().getElevation(),
                panel.__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull("面板应写入 OVERLAY 配方滤镜", panel.getBackdrop());
        Assert.assertEquals("面板滤镜材质 = OVERLAY 配方",
                OVERLAY_SURFACE.getBackdrop().getEffect().getMaterial(),
                panel.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("面板模糊半径 = OVERLAY 配方", OVERLAY_SURFACE.getBackdrop().getBlurRadius(),
                panel.getBackdrop().getBlurRadius());
        // 菜单打开即聚焦承接键盘导航，缘色按表面绑定器的 focus 覆盖规则取配方 focusEdge。
        Assert.assertSame("打开后焦点在面板", panel, runtime.getFocusedNode());
        Assert.assertEquals("面板缘色 = OVERLAY 配方 focusEdge（面板承接焦点）",
                OVERLAY_SURFACE.getFocusEdge(), panel.getBorderColor());

        List<PaintCommand> commands = doPaint(panel).getCommands();
        Assert.assertEquals("一颗浮层表面只采样一次滤镜", 1,
                countType(commands, PaintCommandType.BACKDROP));
        int backgroundIndex = indexOfType(commands, PaintCommandType.BACKGROUND);
        Assert.assertTrue("浮层应有 BACKGROUND（半透明 tint 叠玻璃之上）", backgroundIndex >= 0);
        Assert.assertTrue("滤镜之上不得压不透明底盖", alpha(commands.get(backgroundIndex).getColor()) < 0xFF);

        // 菜单行不各自采样背景
        for (int i : new int[] {0, 2}) {
            Assert.assertNull("菜单行 " + i + " 不得挂滤镜", menuChild(i).getBackdrop());
            Assert.assertEquals("菜单行 " + i + " 默认背景透明（露出浮层玻璃）",
                    ITEM_BG_TRANSPARENT, menuChild(i).getBackgroundColor());
        }
        Assert.assertEquals("启用行文字 = 主题正文前景", TEXT_NORMAL, itemLabel(0).getTextColor());
        Assert.assertEquals("禁用行文字 = 主题禁用前景", TEXT_DISABLED, itemLabel(2).getTextColor());
        Assert.assertEquals("菜单项文本保持", "复制", itemLabel(0).getText());

        SceneNode separator = menuChild(1);
        Assert.assertNull("分隔线不得挂滤镜", separator.getBackdrop());
        Assert.assertEquals("分隔线取浮层配方缘色", OVERLAY_SURFACE.getIdle().getEdge(),
                separator.getBackgroundColor());
        Assert.assertEquals("分隔线高度保持 1px", 1, separator.getPreferredHeight());
        Assert.assertFalse("分隔线不参与命中", separator.isHitTestable());
    }

    // ==================== 主题化：打开中切主题 ====================

    /**
     * withTheme 打开中切主题：面板、菜单行与分隔线随主题更新，菜单项/焦点不丢，节点身份不变，
     * effect 数不增长；切主题后键盘导航与激活仍工作。
     */
    @Test
    public void themeSwitchWhileOpenUpdatesPanelAndRowsWithoutStateLoss() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：深/浅 OVERLAY 配方必须不同",
                dark.surface(SceneTheme.Role.OVERLAY), light.surface(SceneTheme.Role.OVERLAY));

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        SceneContextMenu.Handle handle = openAtWithTheme(pageTheme, Arrays.asList(
                SceneContextMenu.MenuItem.of("复制", () -> activationLog.add("copy")),
                SceneContextMenu.MenuItem.divider(),
                SceneContextMenu.MenuItem.of("粘贴", () -> activationLog.add("paste"))));
        doLayout();

        SceneNode panel = overlayRoot();
        SceneNode row0 = menuChild(0);
        SceneNode separator = menuChild(1);
        SceneNode row2 = menuChild(2);
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 高亮 0（复制）

        Assert.assertEquals("深色面板 = 深色 OVERLAY idle 染色",
                dark.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), panel.getBackgroundColor());
        Assert.assertEquals("深色面板缘色 = 深色配方 focusEdge",
                dark.surface(SceneTheme.Role.OVERLAY).getFocusEdge(), panel.getBorderColor());
        Assert.assertEquals("深色高亮行 = 深色选区背景半透明",
                highlightBg(dark), row0.getBackgroundColor());
        Assert.assertEquals("深色行文字 = 深色 foreground", dark.foreground(), itemLabel(0).getTextColor());
        Assert.assertEquals("深色分隔线 = 深色浮层缘色",
                dark.surface(SceneTheme.Role.OVERLAY).getIdle().getEdge(), separator.getBackgroundColor());

        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertSame("主题切换不重建面板", panel, overlayRoot());
        Assert.assertSame("主题切换不重建菜单行", row0, menuChild(0));
        Assert.assertSame("主题切换不重建分隔线", separator, menuChild(1));
        Assert.assertSame("主题切换不重建菜单行", row2, menuChild(2));
        Assert.assertEquals("仍打开一个浮层", 1, overlaySize());
        Assert.assertSame("焦点不丢（仍在面板）", panel, runtime.getFocusedNode());

        Assert.assertEquals("面板染色随主题更新",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(), panel.getBackgroundColor());
        Assert.assertEquals("面板缘色随主题更新",
                light.surface(SceneTheme.Role.OVERLAY).getFocusEdge(), panel.getBorderColor());
        Assert.assertEquals("面板圆角随主题更新",
                light.surface(SceneTheme.Role.OVERLAY).getCornerRadius(), panel.getCornerRadius());
        Assert.assertEquals("面板滤镜随主题更新",
                light.surface(SceneTheme.Role.OVERLAY).getBackdrop().getEffect().getMaterial(),
                panel.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("高亮行随主题更新 = 浅色选区背景半透明",
                highlightBg(light), row0.getBackgroundColor());
        Assert.assertEquals("未高亮行保持透明", ITEM_BG_TRANSPARENT, row2.getBackgroundColor());
        Assert.assertEquals("行文字随主题更新", light.foreground(), itemLabel(0).getTextColor());
        Assert.assertEquals("分隔线随主题更新",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getEdge(), separator.getBackgroundColor());
        Assert.assertEquals("菜单项文本不丢", "复制", itemLabel(0).getText());
        Assert.assertEquals("切主题不新增订阅", effectsBeforeSwitch,
                ReactiveTestProbe.registeredEffectCount());

        // 切主题后键盘导航与激活仍工作（分隔线仍被跳过）
        routeKeyAndFlush(SceneKey.ARROW_DOWN);
        Assert.assertEquals("高亮随键盘移动到下一可导航项（浅色选区背景半透明）",
                highlightBg(light), row2.getBackgroundColor());
        Assert.assertEquals("原高亮行回到透明（指针不在其上）", ITEM_BG_TRANSPARENT,
                row0.getBackgroundColor());
        routeKeyAndFlush(SceneKey.ENTER);
        Assert.assertEquals("切主题后 Enter 仍激活高亮项", Arrays.asList("paste"), activationLog);
        Assert.assertFalse("激活后关闭", handle.isOpen());
        Assert.assertEquals("激活后浮层卸载", 0, overlaySize());
    }

    // ==================== 主题化：关闭滤镜档 ====================

    /**
     * 无滤镜替代档（{@link SceneTheme#withoutBackdrop()}）：面板不写滤镜，整棵菜单树不发
     * BACKDROP 命令，面板底色不透明可读，行仍只做半透明覆盖、文字仍取主题语义色。
     */
    @Test
    public void withoutBackdropThemeEmitsNoBackdropCommandAndStaysReadable() {
        SceneTheme opaqueTheme = SceneTheme.liquidGlassDark().withoutBackdrop();
        SceneSurfaceStyle overlay = opaqueTheme.surface(SceneTheme.Role.OVERLAY);
        Assert.assertNull("无滤镜档 OVERLAY 配方不带滤镜", overlay.getBackdrop());

        openAtWithTheme(Signal.create(opaqueTheme), threeItems());
        doLayout();
        routeKeyAndFlush(SceneKey.ARROW_DOWN); // 高亮 0（复制）

        SceneNode panel = overlayRoot();
        Assert.assertNull("无滤镜档面板不写 backdrop", panel.getBackdrop());
        Assert.assertEquals("面板底色 = 无滤镜档不透明 tint", overlay.getIdle().getTint(),
                panel.getBackgroundColor());
        Assert.assertEquals("无滤镜档面板底色不透明可读", 0xFF, alpha(panel.getBackgroundColor()));
        Assert.assertEquals("面板边框宽仍取配方", overlay.getBorderWidth(), panel.getBorderWidth());
        Assert.assertNull("菜单行仍无滤镜", menuChild(1).getBackdrop());
        Assert.assertEquals("高亮行仍用选区背景半透明覆盖",
                tint(opaqueTheme.selectionBackground(), ITEM_HIGHLIGHT_ALPHA),
                menuChild(0).getBackgroundColor());
        Assert.assertEquals("文字仍取主题正文前景", opaqueTheme.foreground(), itemLabel(0).getTextColor());

        List<PaintCommand> commands = doPaint(panel).getCommands();
        Assert.assertEquals("无滤镜档整树不发 BACKDROP 命令", 0,
                countType(commands, PaintCommandType.BACKDROP));
    }

    // ==================== 主题化：关闭回收 ====================

    /**
     * 关闭后无残留监听：open 注册的 portal/表面/焦点/行绑定在 close + flush 后全部回收，
     * effect 数回到打开前基线。
     */
    @Test
    public void closeReclaimsAllBindingsAndPortal() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        SceneContextMenu.Handle handle = openAt(0, 0, threeItems());
        Assert.assertTrue("打开应注册响应式绑定",
                ReactiveTestProbe.registeredEffectCount() > baseline);

        handle.close();
        runtime.flush();
        Assert.assertFalse("close 后 isOpen=false", handle.isOpen());
        Assert.assertEquals("close 后 overlay 卸载", 0, overlaySize());
        Assert.assertEquals("关闭后无残留监听（effect 回基线）", baseline,
                ReactiveTestProbe.registeredEffectCount());
    }
}
