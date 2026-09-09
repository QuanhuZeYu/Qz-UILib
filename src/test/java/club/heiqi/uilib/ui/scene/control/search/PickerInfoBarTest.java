package club.heiqi.uilib.ui.scene.control.search;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintFragment;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link PickerInfoBar} 单元测试。
 *
 * <p>覆盖：外壳结构（固定高、内边距、非命中）、文本绑定随信号变化同步、常驻空文本挂载；
 * 液态玻璃迁移后的表面事实——默认路径外壳六项逐项等于来源主题 {@code TOOLBAR} 配方
 * （条带类容器先例，同 FormActionBar）、整树恰好一条 {@code BACKDROP}（不叠第二层玻璃）、
 * 文本取主题 {@code mutedForeground} 语义前景（深色档与旧 {@code TEXT_SECONDARY} 同值，
 * 静态色回退必须被主题切换/浅色档用例抓住）；主题切换只重派生（节点身份不变、effect 不增）；
 * 关闭滤镜档不透明可读且零 BACKDROP；卸载回收全部外观绑定。</p>
 */
public class PickerInfoBarTest {

    private static final float EPSILON = 0.0001F;

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;
    private Signal<String> text;
    private Signal<Boolean> enabled;

    private static final int W = 400;
    private static final int H = 300;

    /** 库默认主题的 TOOLBAR 配方：默认路径外观唯一来源，断言引用配方值而非硬编码色号。 */
    private static final SceneSurfaceStyle TOOLBAR_SURFACE =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.TOOLBAR);

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();
        text = Signal.create("占位文本");
        enabled = Signal.create(Boolean.TRUE);
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 在 mount 作用域内构建信息条（mount 自动 append 到 sceneRoot），再布局并回刷。 */
    private SceneNode mountBar() {
        SceneNode bar = rt.mount(sceneRoot,
                () -> PickerInfoBar.create(rt, new PickerInfoBar.Props(text, enabled))).getRoot();
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
        return bar;
    }

    /** 在可切换局部主题作用域内构建信息条（不预布局），返回挂载句柄；root 经 holder 带出。 */
    private MountHandle mountBarWithTheme(Signal<SceneTheme> pageTheme, SceneNode root,
                                          SceneNode[] holder) {
        return rt.mount(root, () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = PickerInfoBar.create(
                    rt, new PickerInfoBar.Props(text, enabled)));
            return holder[0];
        });
    }

    /** 统计整棵子树 PaintPlan 中的 BACKDROP 命令数。 */
    private int backdropCount(SceneNode root) {
        PaintPlan plan = paintEngine.paint(root).getPlan();
        int count = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    /** 节点自身 PaintFragment 内的 BACKDROP 命令数（须先对含该节点的树执行过 paint）。 */
    private static int ownBackdropCount(SceneNode node) {
        Object cached = node.getCachedPaint();
        Assert.assertTrue("节点应已绘制出自身 fragment", cached instanceof PaintFragment);
        int count = 0;
        for (PaintCommand command : ((PaintFragment) cached).getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    // ==================== 既有行为合同（结构 / 文本同步 / 常驻空文本） ====================

    @Test
    public void structureHasFixedHeightBackgroundAndRadius() {
        SceneNode bar = mountBar();
        Assert.assertEquals("固定高 24", PickerInfoBar.INFO_BAR_HEIGHT, bar.getPreferredHeight());
        Assert.assertNotEquals("实底背景非透明", 0, bar.getBackgroundColor());
        Assert.assertTrue("圆角非 0", bar.getCornerRadius() > 0);
        Assert.assertFalse("信息条不参与命中", bar.isHitTestable());
        // 布局合同照旧：setPadding(top,right,bottom,left)=(PAD_SM,0,PAD_SM,0)。
        Assert.assertEquals("上内边距布局常量不变", SceneChromeTokens.PAD_SM, bar.getPaddingTop());
        Assert.assertEquals("下内边距布局常量不变", SceneChromeTokens.PAD_SM, bar.getPaddingBottom());
        Assert.assertEquals("左内边距保持 0", 0, bar.getPaddingLeft());
        Assert.assertEquals("右内边距保持 0", 0, bar.getPaddingRight());
        Assert.assertTrue("裁剪合同不变", bar.isClipChildren());
    }

    @Test
    public void textSignalUpdatesChildText() {
        SceneNode bar = mountBar();
        List<SceneNode> children = bar.__getChildren();
        Assert.assertEquals("单文本子节点", 1, children.size());
        SceneNode label = children.get(0);
        Assert.assertEquals("初始文本同步", "占位文本", label.getText());

        text.set("新的提示");
        rt.flush();
        Assert.assertEquals("文本信号变化后子节点同步", "新的提示", label.getText());
    }

    @Test
    public void emptyTextStaysMounted() {
        text.set("");
        SceneNode bar = mountBar();
        Assert.assertEquals("常驻挂载，空文本仍保留子节点", 1, bar.__getChildren().size());
        Assert.assertEquals("空文本显示空串", "", bar.__getChildren().get(0).getText());
        Assert.assertEquals("空文本仍使用次要文本色（= 库默认主题 mutedForeground，深色档与旧次要色同值）",
                SceneChromeTokens.TEXT_SECONDARY, bar.__getChildren().get(0).getTextColor());
        Assert.assertEquals("次要文本色来源为主题 mutedForeground",
                Integer.valueOf(SceneThemes.DEFAULT.mutedForeground()),
                Integer.valueOf(bar.__getChildren().get(0).getTextColor()));
    }

    // ==================== ① 表面事实：外壳六项 = TOOLBAR 配方，一颗 BACKDROP ====================

    /** 默认路径（库默认主题）：外壳 background/border/borderWidth/cornerRadius/浮雕/滤镜逐项等于 TOOLBAR 配方。 */
    @Test
    public void defaultPathShellBindsToolbarRecipeItems() {
        SceneNode bar = mountBar();
        Assert.assertNotNull("前置：TOOLBAR 配方自带滤镜", TOOLBAR_SURFACE.getBackdrop());
        Assert.assertEquals("外壳背景 = TOOLBAR idle 染色",
                TOOLBAR_SURFACE.getIdle().getTint(), bar.getBackgroundColor());
        Assert.assertEquals("外壳边框色 = TOOLBAR idle 缘色",
                TOOLBAR_SURFACE.getIdle().getEdge(), bar.getBorderColor());
        Assert.assertEquals("外壳边框宽 = TOOLBAR 配方",
                TOOLBAR_SURFACE.getBorderWidth(), bar.getBorderWidth());
        Assert.assertEquals("外壳圆角 = TOOLBAR 配方",
                TOOLBAR_SURFACE.getCornerRadius(), bar.getCornerRadius());
        Assert.assertEquals("外壳浮雕 = TOOLBAR idle 实体高度",
                TOOLBAR_SURFACE.getIdle().getElevation(), bar.__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull("外壳装 TOOLBAR 滤镜（唯一一颗表面）", bar.getBackdrop());
        Assert.assertEquals("滤镜模糊半径 = 配方",
                TOOLBAR_SURFACE.getBackdrop().getBlurRadius(), bar.getBackdrop().getBlurRadius());
        Assert.assertEquals("滤镜材质 = 配方",
                TOOLBAR_SURFACE.getBackdrop().getEffect().getMaterial(),
                bar.getBackdrop().getEffect().getMaterial());
    }

    /** 默认路径 PaintPlan：外壳自身恰好一条 BACKDROP，整树仅此一颗（文本行不叠第二层玻璃）。 */
    @Test
    public void defaultPaintPlanEmitsOneBackdropOnShellOnly() {
        SceneNode bar = mountBar();
        SceneNode label = bar.__getChildren().get(0);
        Assert.assertNull("文本行自身不装滤镜（不叠第二层玻璃）", label.getBackdrop());
        Assert.assertEquals("文本行不写表面浮雕（-1 = 普通绘制路径）",
                -1.0F, label.__getSurfaceElevation(), EPSILON);
        Assert.assertEquals("文本行不写边框宽", 0, label.getBorderWidth());
        PaintPlan plan = paintEngine.paint(sceneRoot).getPlan();
        Assert.assertEquals("外壳自身恰好一条 BACKDROP（不重复采样）", 1, ownBackdropCount(bar));
        int treeCount = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                treeCount++;
            }
        }
        Assert.assertEquals("整树 BACKDROP = 信息条 1 + 文本 0（纯展示不叠第二层）", 1, treeCount);
    }

    // ==================== ② 文字语义前景：mutedForeground 随来源主题 ====================

    /** 浅色档局部主题构建：外壳与文字即取来源主题值（证明非常量回退）。 */
    @Test
    public void localThemeScopeConsumesSourceRecipeAndForeground() {
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle lightToolbar = light.surface(SceneTheme.Role.TOOLBAR);
        Assert.assertNotEquals("前置：浅色档 TOOLBAR 配方与库默认不同",
                TOOLBAR_SURFACE.getIdle(), lightToolbar.getIdle());
        Assert.assertNotEquals("前置：浅色次要前景与深色次要前景可区分",
                SceneThemes.DEFAULT.mutedForeground(), light.mutedForeground());

        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountBarWithTheme(Signal.create(light), new SceneNode(), holder);
        rt.flush();

        SceneNode bar = holder[0];
        Assert.assertEquals("外壳背景 = 来源主题 TOOLBAR idle 染色",
                lightToolbar.getIdle().getTint(), bar.getBackgroundColor());
        Assert.assertEquals("外壳边框色 = 来源主题 TOOLBAR idle 缘色",
                lightToolbar.getIdle().getEdge(), bar.getBorderColor());
        Assert.assertEquals("外壳圆角 = 来源主题 TOOLBAR 配方",
                lightToolbar.getCornerRadius(), bar.getCornerRadius());
        Assert.assertEquals("文字 = 来源主题 mutedForeground（次要语义，非正文/禁用档）",
                Integer.valueOf(light.mutedForeground()),
                Integer.valueOf(bar.__getChildren().get(0).getTextColor()));
        Assert.assertNotEquals("文字不越级取正文档", Integer.valueOf(light.foreground()),
                Integer.valueOf(bar.__getChildren().get(0).getTextColor()));
        handle.dispose();
    }

    /** 关闭滤镜档（withoutBackdrop）：替代底色不透明可读、圆角/边框仍由同一配方给出、零 BACKDROP。 */
    @Test
    public void withoutBackdropThemeKeepsOpaqueReadableShellAndZeroBackdrop() {
        SceneTheme noBackdrop = SceneTheme.liquidGlassDark().withoutBackdrop();
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountBarWithTheme(Signal.create(noBackdrop), sceneRoot, holder);
        rt.flush();
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());

        SceneNode bar = holder[0];
        Assert.assertNull("关闭滤镜时不装滤镜", bar.getBackdrop());
        Assert.assertEquals("替代底色不透明可读", 0xFF, (bar.getBackgroundColor() >>> 24) & 0xFF);
        Assert.assertEquals("关闭滤镜时不发 BACKDROP 命令", 0, backdropCount(sceneRoot));
        Assert.assertEquals("圆角与边框仍由配方给出",
                noBackdrop.surface(SceneTheme.Role.TOOLBAR).getCornerRadius(), bar.getCornerRadius());
        handle.dispose();
    }

    // ==================== ③ 主题切换：只重派生，身份不变、effect 不增 ====================

    /** dark→light（TOOLBAR 配方与 mutedForeground 均确值不同）：外观更新、节点身份不变、订阅数不增。 */
    @Test
    public void themeSwitchRebindsWithoutRebuildingNodes() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkToolbar = dark.surface(SceneTheme.Role.TOOLBAR);
        SceneSurfaceStyle lightToolbar = light.surface(SceneTheme.Role.TOOLBAR);
        Assert.assertNotEquals("前置：两档 TOOLBAR 配方必须不同，否则切换不传播",
                darkToolbar.getIdle(), lightToolbar.getIdle());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountBarWithTheme(pageTheme, new SceneNode(), holder);
        rt.flush();

        SceneNode bar = holder[0];
        SceneNode label = bar.__getChildren().get(0);
        Assert.assertEquals("前置：深色档外壳背景", darkToolbar.getIdle().getTint(), bar.getBackgroundColor());
        Assert.assertEquals("前置：深色档文字 = mutedForeground", Integer.valueOf(dark.mutedForeground()),
                Integer.valueOf(label.getTextColor()));

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        rt.flush();

        Assert.assertEquals("切换后外壳背景 = 浅色 TOOLBAR idle 染色",
                lightToolbar.getIdle().getTint(), bar.getBackgroundColor());
        Assert.assertEquals("切换后外壳边框色 = 浅色 TOOLBAR idle 缘色",
                lightToolbar.getIdle().getEdge(), bar.getBorderColor());
        Assert.assertEquals("切换后圆角 = 浅色 TOOLBAR 配方",
                lightToolbar.getCornerRadius(), bar.getCornerRadius());
        Assert.assertEquals("切换后文字 = 浅色 mutedForeground（静态旧色回退会被本断言抓住）",
                Integer.valueOf(light.mutedForeground()), Integer.valueOf(label.getTextColor()));
        Assert.assertSame("主题切换不重建外壳节点", bar, holder[0]);
        Assert.assertEquals("主题切换不增删子节点", 1, bar.__getChildren().size());
        Assert.assertSame("文本子节点身份不变", label, bar.__getChildren().get(0));
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    // ==================== ④ 卸载回收 ====================

    /** 卸载后外观 effect 回到基线；再切主题不再写入旧节点。 */
    @Test
    public void unmountReleasesThemeSurfaceAndForegroundBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountBarWithTheme(pageTheme, new SceneNode(), holder);
        rt.flush();

        Assert.assertTrue("信息条应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > baseline);
        SceneNode bar = holder[0];
        SceneNode label = bar.__getChildren().get(0);
        int bgBeforeDispose = bar.getBackgroundColor();
        int fgBeforeDispose = label.getTextColor();

        handle.dispose();
        rt.flush();
        Assert.assertEquals("卸载后外观绑定 effect 回收",
                baseline, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassLight());
        rt.flush();
        Assert.assertEquals("卸载后主题切换不再写入旧外壳", bgBeforeDispose, bar.getBackgroundColor());
        Assert.assertEquals("卸载后主题切换不再写入旧文本", fgBeforeDispose, label.getTextColor());
    }
}
