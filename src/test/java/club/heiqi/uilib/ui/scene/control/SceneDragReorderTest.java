package club.heiqi.uilib.ui.scene.control;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneDragReorder 拖拽把手默认主题消费测试（G12/拖拽把手）。
 *
 * <p>覆盖契约口径：把手底色取 {@code INDICATOR} 配方 idle/hovered/pressed tint 档（把手态）、
 * 图标色取 {@code mutedForeground}（idle）/{@code accent}（hover·拖拽）；把手不装滤镜
 * （全树零 BACKDROP、不写 border/backdrop/surfaceElevation）；主题切换只重派生颜色、
 * 节点身份不变、effect 不增长、卸载回收绑定；拖拽跟手/换位提交/取消回滚行为回归。</p>
 */
public class SceneDragReorderTest {

    /** 测试画布宽度。 */
    private static final int CANVAS_WIDTH = 240;
    /** 测试画布高度。 */
    private static final int CANVAS_HEIGHT = 160;
    /** 行高（布局常量，与把手几何无关）。 */
    private static final int ROW_HEIGHT = 36;

    /** 交互注入 harness。 */
    private SceneInteractionHarness harness;
    /** 场景运行时。 */
    private SceneRuntime runtime;
    /** 场景根。 */
    private SceneNode sceneRoot;
    /** 列表视口（builder 内赋值）。 */
    private SceneNode viewport;
    /** 顺序 signal（拖拽消费者）。 */
    private Signal<List<Item>> orderSignal;
    /** 页面主题信号。 */
    private Signal<SceneTheme> pageTheme;
    /** 挂载句柄。 */
    private MountHandle mountHandle;
    /** 绘制引擎（断言全树零 BACKDROP）。 */
    private ScenePaintEngine paintEngine;
    /** 最近一次 MOVE 预览顺序。 */
    private final AtomicReference<List<Item>> lastPreview = new AtomicReference<List<Item>>();
    /** 最近一次 UP 提交顺序。 */
    private final AtomicReference<List<Item>> lastCommit = new AtomicReference<List<Item>>();
    /** 最近一次 CANCEL 回滚快照。 */
    private final AtomicReference<List<Item>> lastCancel = new AtomicReference<List<Item>>();

    /** 初始化测试场景。 */
    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        sceneRoot = SceneNode.column();
        paintEngine = new ScenePaintEngine(new FixedTextMeasurer(8, 16));
        orderSignal = Signal.create(Arrays.asList(new Item(1, "a"), new Item(2, "b"), new Item(3, "c")));
        pageTheme = Signal.create(SceneTheme.liquidGlassDark());
    }

    /** 清理运行时。 */
    @After
    public void tearDown() {
        if (mountHandle != null) {
            mountHandle.dispose();
        }
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    /**
     * 三态外观：底色 = INDICATOR 配方 idle/hovered/pressed tint 档，图标 = mutedForeground（idle）
     * /{@code accent}（hover·拖拽）；释放后 hover 残留期取 hover 档，移出后回落 idle 档。
     */
    @Test
    public void handleStatesShouldConsumeIndicatorRecipeTintsAndThemeForeground() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneSurfaceStyle indicator = dark.surface(SceneTheme.Role.INDICATOR);
        mountDragList();

        SceneNode handle = handleAt(0);
        SceneNode icon = handle.__getChildren().get(0);
        int x = centerX(handle);
        int y = centerY(handle);

        Assert.assertEquals("idle 底色 = INDICATOR 配方 idle tint",
                indicator.getIdle().getTint(), handle.getBackgroundColor());
        Assert.assertEquals("idle 图标 = 主题 mutedForeground",
                dark.mutedForeground(), icon.getTextColor());

        harness.moveAt(x, y);
        Assert.assertEquals("hover 底色 = INDICATOR 配方 hovered tint",
                indicator.getHovered().getTint(), handle.getBackgroundColor());
        Assert.assertEquals("hover 图标 = 主题 accent", dark.accent(), icon.getTextColor());

        harness.pressAt(x, y);
        Assert.assertEquals("拖拽（pressed）底色 = INDICATOR 配方 pressed tint",
                indicator.getPressed().getTint(), handle.getBackgroundColor());
        Assert.assertEquals("拖拽（pressed）图标 = 主题 accent", dark.accent(), icon.getTextColor());

        harness.releaseAt(x, y);
        Assert.assertEquals("释放后指针仍在把手上 → hover 档",
                indicator.getHovered().getTint(), handle.getBackgroundColor());
        Assert.assertEquals("释放后图标保持 accent（hover 驱动）", dark.accent(), icon.getTextColor());

        harness.moveAt(x, CANVAS_HEIGHT + 20);
        Assert.assertEquals("移出后底色回落 idle 档",
                indicator.getIdle().getTint(), handle.getBackgroundColor());
        Assert.assertEquals("移出后图标回落 mutedForeground",
                dark.mutedForeground(), icon.getTextColor());
    }

    /**
     * 把手不装滤镜、不叠玻璃：全树 PaintPlan 零 BACKDROP；把手不写 backdrop/borderWidth，
     * 圆角与宽高是布局几何常量（零改动）。
     */
    @Test
    public void handleShouldNeverInstallBackdropOrSurfaceChannels() {
        mountDragList();

        SceneNode handle = handleAt(0);
        SceneNode icon = handle.__getChildren().get(0);
        Assert.assertNull("把手节点不装滤镜", handle.getBackdrop());
        Assert.assertNull("把手图标不装滤镜", icon.getBackdrop());
        Assert.assertEquals("把手不写边框宽（外观只有底色与图标色两个通道）", 0, handle.getBorderWidth());
        Assert.assertEquals("把手圆角保持布局几何常量（契约 §4.2 RADIUS 继续使用）",
                SceneChromeTokens.RADIUS_MD, handle.getCornerRadius());
        Assert.assertEquals("把手宽度零改动", 24, handle.getPreferredWidth());
        Assert.assertEquals("把手高度零改动", SceneChromeTokens.INPUT_HEIGHT, handle.getPreferredHeight());
        Assert.assertTrue("把手仍为独立交互单元", handle.isHitTestable());

        Assert.assertEquals("默认态全树零 BACKDROP", 0, backdropInPlan());

        harness.moveAt(centerX(handle), centerY(handle));
        harness.pressAt(centerX(handle), centerY(handle));
        Assert.assertEquals("hover·拖拽态全树仍零 BACKDROP", 0, backdropInPlan());
        harness.releaseAt(centerX(handle), centerY(handle));
    }

    /**
     * 主题切换：来源主题信号变化 + flush 后把手底色/图标色重派生为新主题语义，
     * 节点身份不变、effect 数不增长、仍零 BACKDROP。
     */
    @Test
    public void themeSwitchShouldRederiveHandleColorsWithoutRebuildingNodes() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        SceneSurfaceStyle darkIndicator = dark.surface(SceneTheme.Role.INDICATOR);
        SceneSurfaceStyle lightIndicator = light.surface(SceneTheme.Role.INDICATOR);
        Assert.assertNotEquals("前置：两档 INDICATOR idle 配方必须不同，否则切换不传播",
                darkIndicator.getIdle(), lightIndicator.getIdle());

        mountDragList();
        SceneNode handle = handleAt(0);
        SceneNode icon = handle.__getChildren().get(0);
        Assert.assertEquals("初始底色 = 深色 INDICATOR idle tint",
                darkIndicator.getIdle().getTint(), handle.getBackgroundColor());
        Assert.assertEquals("初始图标 = 深色 mutedForeground", dark.mutedForeground(), icon.getTextColor());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("底色随主题重派生",
                lightIndicator.getIdle().getTint(), handle.getBackgroundColor());
        Assert.assertEquals("圆角仍为几何常量，不随主题变化",
                SceneChromeTokens.RADIUS_MD, handle.getCornerRadius());
        Assert.assertEquals("图标随主题重派生", light.mutedForeground(), icon.getTextColor());
        Assert.assertSame("主题切换不重建把手节点", handle, handleAt(0));
        Assert.assertSame("主题切换不重建图标节点", icon, handleAt(0).__getChildren().get(0));
        Assert.assertEquals("主题切换不新增 effect", effectsBefore, ReactiveTestProbe.registeredEffectCount());
        Assert.assertEquals("主题切换后仍零 BACKDROP", 0, backdropInPlan());

        // hover 态在新主题下同样取新配方档
        harness.moveAt(centerX(handle), centerY(handle));
        Assert.assertEquals("切换后 hover 底色 = 新主题 INDICATOR hovered tint",
                lightIndicator.getHovered().getTint(), handle.getBackgroundColor());
        Assert.assertEquals("切换后 hover 图标 = 新主题 accent", light.accent(), icon.getTextColor());
    }

    /**
     * 拖拽行为回归：阈值激活并 capture、跟手位移、越中线换位预览、UP 提交终序、
     * 终止后偏移复位；CANCEL 回滚到起始快照。外观迁移不得触碰以上任何语义。
     */
    @Test
    public void dragShouldFollowPointerCommitAndCancelShouldRollback() {
        mountDragList();
        SceneNode handle = handleAt(0);
        SceneNode draggedRow = rowAt(0);
        int x = centerX(handle);
        int startY = centerY(handle);
        int centerOffset = centerY(rowAt(0)) - centerY(handle);

        harness.pressAt(x, startY);
        harness.moveAt(x, startY + 6);
        // capture 探针（__getCapturedNode）为 ui.scene.input 包内 API，本包经行为间接验证：
        // 后续 MOVE 指针已在把手盒外仍持续驱动本把手重排（隐式按压捕获），见下方换位/提交断言。
        Assert.assertEquals("跟手：被拖行按指针位移浮起", 6.0f, translateY(draggedRow), 0.01f);

        int crossedY = pointerYForDraggedCenter(centerY(rowAt(1)) + 1, centerOffset);
        harness.moveAt(x, crossedY);
        Assert.assertEquals("越过相邻行中线即预览换位",
                Arrays.asList("b", "a", "c"), values(orderSignal.get()));
        Assert.assertEquals("预览回调同序", Arrays.asList("b", "a", "c"), values(lastPreview.get()));

        harness.releaseAt(x, crossedY);
        Assert.assertNotNull("UP 应提交终序", lastCommit.get());
        Assert.assertEquals("提交顺序 = 预览顺序", Arrays.asList("b", "a", "c"), values(lastCommit.get()));
        Assert.assertEquals("终止后被拖行浮起偏移复位", 0.0f, translateY(draggedRow), 0.01f);

        // 回置顺序后验证 CANCEL 回滚快照
        orderSignal.set(Arrays.asList(new Item(1, "a"), new Item(2, "b"), new Item(3, "c")));
        runtime.flush();
        lastCommit.set(null);

        harness.pressAt(x, startY);
        harness.moveAt(x, startY + 6);
        harness.moveAt(x, crossedY);
        Assert.assertEquals("取消前已有预览顺序", Arrays.asList("b", "a", "c"), values(orderSignal.get()));

        routePointer(ScenePointerAction.CANCEL, x, crossedY);
        Assert.assertNotNull("CANCEL 应回抛起始快照供消费者回滚", lastCancel.get());
        Assert.assertEquals("回滚快照 = 拖拽起始顺序", Arrays.asList("a", "b", "c"), values(lastCancel.get()));
        Assert.assertNull("CANCEL 不应触发 UP 提交", lastCommit.get());
        Assert.assertEquals("CANCEL 后浮起偏移复位", 0.0f, translateY(draggedRow), 0.01f);
    }

    /**
     * 卸载清理：把手外观绑定随 mount Owner 回收，主题更新不再写入旧节点。
     */
    @Test
    public void unmountShouldReleaseHandleBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        mountDragList();
        SceneNode handle = handleAt(0);
        Assert.assertTrue("默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > baseline);

        int colorBeforeDispose = handle.getBackgroundColor();
        mountHandle.dispose();
        mountHandle = null;
        Assert.assertEquals("卸载后外观绑定 effect 应回到基线",
                baseline, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        Assert.assertEquals("卸载后主题更新不再写入旧把手",
                colorBeforeDispose, handle.getBackgroundColor());
    }

    // ==================== 装配与探针 helper ====================

    /** 在 withTheme 作用域内挂载一个静态三行拖拽列表（每行 [handle, label]）。 */
    private void mountDragList() {
        mountHandle = runtime.mount(sceneRoot, () -> {
            SceneNode page = SceneNode.column();
            SceneNode listViewport = SceneNode.column();
            listViewport.setGap(4);
            page.appendChild(listViewport);
            // row() 闭包引用 viewport 字段，必须在构建任何行之前就位。
            viewport = listViewport;
            SceneThemes.withTheme(pageTheme, () -> {
                for (Item item : orderSignal.get()) {
                    listViewport.appendChild(row(item));
                }
            });
            return page;
        });
        runtime.flush();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    /** 构造一行：[把手, 文本标签]，拖拽消费者写回 orderSignal。 */
    private SceneNode row(Item item) {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(ROW_HEIGHT);
        row.appendChild(SceneDragReorder.buildHandle(runtime, viewport, null, item.id, orderSignal,
                candidate -> candidate.id,
                next -> {
                    lastPreview.set(next);
                    orderSignal.set(next);
                },
                committed -> lastCommit.set(committed),
                cancelled -> lastCancel.set(cancelled)));
        SceneNode label = new SceneNode();
        label.setHitTestable(false);
        label.setText(item.value);
        row.appendChild(label);
        return row;
    }

    /** @return 指定行 */
    private SceneNode rowAt(int index) {
        return viewport.__getChildren().get(index);
    }

    /** @return 指定行把手 */
    private SceneNode handleAt(int index) {
        return rowAt(index).__getChildren().get(0);
    }

    /** @return PaintPlan 中 BACKDROP 命令数（全树）。 */
    private int backdropInPlan() {
        PaintPlan plan = paintEngine.paint(sceneRoot).getPlan();
        int count = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    /** @return 节点中心 X */
    private int centerX(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getX() + box.getWidth() / 2;
    }

    /** @return 节点中心 Y */
    private int centerY(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        return box.getY() + box.getHeight() / 2;
    }

    /** @return 节点当前 translateY */
    private float translateY(SceneNode node) {
        return node.getTransform() == null ? 0.0f : node.getTransform().translateY;
    }

    /** 将目标被拖行中心 Y 换算为指针 Y。 */
    private int pointerYForDraggedCenter(int draggedCenterY, int centerOffset) {
        return draggedCenterY - centerOffset;
    }

    /** 白盒回退（harness 无 CANCEL 投递入口）：裸建 InputFrameBuilder 直投。 */
    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame frame = fb.drainFrame();
        runtime.route(sceneRoot, frame, 0, 0);
        runtime.flush();
    }

    /** @return 行的文本顺序 */
    private List<String> values(List<Item> items) {
        String[] out = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            out[i] = items.get(i).value;
        }
        return Arrays.asList(out);
    }

    /** 测试行数据。 */
    private static final class Item {
        /** 行 id。 */
        private final long id;
        /** 行文本。 */
        private final String value;

        /** 创建测试行。 */
        private Item(long id, String value) {
            this.id = id;
            this.value = value;
        }
    }
}
