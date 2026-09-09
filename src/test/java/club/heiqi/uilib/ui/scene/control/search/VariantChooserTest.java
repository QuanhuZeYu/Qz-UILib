package club.heiqi.uilib.ui.scene.control.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.ScenePaintEngine;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link VariantChooser} 单元测试（受控契约修订版 + G13 液态玻璃迁移口径）。
 *
 * <p>行为基线（必须继续通过）：开合挂载语义（有/无变体候选）、行渲染数量与 label、SELECTED
 * 点击 toggle 勾选、ALL 点击只读无副作用、提交 Selection 契约（candidateKey/mode/variantKeys）、
 * 取消回调、查询前缀过滤、模式分段受控回写。</p>
 *
 * <p>主题化验收（G13 虚拟化复用行口径，契约 §4.1 +「虚拟化复用行轻量零滤镜」裁决）：</p>
 * <ul>
 *   <li>浮层面板 = {@code OVERLAY} 配方六项由 {@code SceneSurfaceBinder} 独占（面板自身恰一颗
 *       滤镜），scrim 零玻璃、旧 {@code applyPanelChrome} 实色四件套消失；</li>
 *   <li>变体行 = {@code selectableSurface(INDICATOR, checked)} 轻量状态档（只写背景色，选中
 *       强度 0x59 明显高于 hover 档）、行子树零 BACKDROP、零边框/圆角/实体高度；</li>
 *   <li>复用/重绑不串态：换候选重建的行不带上一项选中/hover 残留；同 key 换位节点身份不变、
 *       选中跟随 key 而非位置；</li>
 *   <li>文字三件套（标题/候选名/行标签）取主题 {@code foreground}/{@code mutedForeground}/
 *       {@code disabledForeground}；勾选圆点取 {@code onAccentForeground}；</li>
 *   <li>变体物品图像不改色（反向钉住）：有图透明底 + 原图片源、无图静态占位底，主题切换前后
 *       逐字节不变、零滤镜；</li>
 *   <li>主题切换只重派生：节点身份不变、effect 数不增长、受控选中不丢；关闭与卸载回收绑定。</li>
 * </ul>
 */
public class VariantChooserTest {

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private ScenePaintEngine paintEngine;

    private static final int W = 800;
    private static final int H = 600;
    private static final float EPSILON = 0.0001F;
    /** 透明底（图像协议静态值断言用）。 */
    private static final int BG_TRANSPARENT = 0x00000000;
    /** 无图占位底色（VariantChooser 私有的图像协议静态值，反向钉住用字面量核对）。 */
    private static final int ICON_PLACEHOLDER = 0xFF454B54;

    /**
     * 库默认主题配方：默认外观唯一来源。断言引用配方值而不是硬编码色号，
     * 主题集中调参（G19）时本类自动跟随。
     */
    private static final SceneSurfaceStyle OVERLAY =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.OVERLAY);
    private static final SceneSurfaceStyle INDICATOR =
            SceneThemes.DEFAULT.surface(SceneTheme.Role.INDICATOR);
    /** 行四档：未选中/hover/pressed/disabled 取 INDICATOR 配方档。 */
    private static final int ROW_IDLE = INDICATOR.getIdle().getTint();
    private static final int ROW_HOVER = INDICATOR.getHovered().getTint();
    private static final int ROW_PRESSED = INDICATOR.getPressed().getTint();
    private static final int ROW_DISABLED = INDICATOR.getDisabled().getTint();
    /** 选中档：tint RGB 换主题强调色、强度取主题统一选中强度 0x59（高亮 0x59 &gt; hover 口径）。 */
    private static final int ROW_SELECTED = selectedTint(SceneThemes.DEFAULT.accent());
    private static final int ROW_SELECTED_HOVER = selectedTint(SceneThemes.DEFAULT.accentHover());
    /** 文字前景与勾选圆点标记色。 */
    private static final int FG = SceneThemes.DEFAULT.foreground();
    private static final int FG_MUTED = SceneThemes.DEFAULT.mutedForeground();
    private static final int FG_DISABLED = SceneThemes.DEFAULT.disabledForeground();
    private static final int DOT_ON = SceneThemes.DEFAULT.onAccentForeground();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        paintEngine = new ScenePaintEngine(measurer);
        sceneRoot = new SceneNode();
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 构建带指定变体 key 的候选（label 形如「变体i」）。 */
    private static SearchPickerData.Candidate candidate(String key, String... variantKeys) {
        List<SearchPickerData.Variant> variants = new ArrayList<>();
        for (int i = 0; i < variantKeys.length; i++) {
            variants.add(new SearchPickerData.Variant(variantKeys[i], "变体" + i));
        }
        return new SearchPickerData.Candidate(key, "候选-" + key, variants);
    }

    /** 无图视觉适配器。 */
    private static VisualAdapter adapter() {
        return new VisualAdapter() {
            @Override
            public String candidateLabel(SearchPickerData.Candidate candidate) {
                return candidate.label();
            }

            @Override
            public String variantLabel(SearchPickerData.Variant variant) {
                return variant.label();
            }
        };
    }

    /** 为指定变体 key 挂图片源的适配器（图像渲染协议用例）。 */
    private static VisualAdapter adapterWithImageFor(final String variantKey,
                                                     final SceneImageSource source) {
        return new VisualAdapter() {
            @Override
            public String candidateLabel(SearchPickerData.Candidate candidate) {
                return candidate.label();
            }

            @Override
            public String variantLabel(SearchPickerData.Variant variant) {
                return variant.label();
            }

            @Override
            public SceneImageSource variantImage(SearchPickerData.Variant variant) {
                return variantKey.equals(variant.key()) ? source : null;
            }
        };
    }

    /** 测试用图片源（渲染协议黑盒，只核对身份与不改色）。 */
    private static SceneImageSource image(final String registryKey) {
        return new SceneImageSource() {
            @Override
            public String registryKey() {
                return registryKey;
            }
        };
    }

    /**
     * 测试夹具：受控信号 + 回写回调（模拟外壳）+ 提交/取消记录 + 挂载组件。
     *
     * <p>回写与外壳同款：{@code onModeChange = mode::set}、{@code onKeysChange = selectedKeys::set}。</p>
     */
    private final class Fixture {
        final Signal<Boolean> open;
        final Signal<SearchPickerData.Candidate> candidate;
        final Signal<Boolean> enabled;
        final Signal<SearchPickerData.SelectionMode> mode;
        final Signal<List<String>> selectedKeys;
        final List<SearchPickerData.Selection> commits = new ArrayList<>();
        final int[] cancels = {0};
        /** 挂载句柄（卸载回收用例）。 */
        MountHandle handle;
        /** 局部主题信号（null = 走 runtime 默认，即库默认深色档）。 */
        Signal<SceneTheme> pageTheme;

        Fixture() {
            this(null, adapter());
        }

        Fixture(Signal<SceneTheme> pageTheme) {
            this(pageTheme, adapter());
        }

        Fixture(Signal<SceneTheme> pageTheme, VisualAdapter visualAdapter) {
            this.pageTheme = pageTheme;
            this.open = Signal.create(Boolean.FALSE);
            this.candidate = Signal.create(null);
            this.enabled = Signal.create(Boolean.TRUE);
            this.mode = Signal.create(SearchPickerData.SelectionMode.ALL);
            this.selectedKeys = Signal.create(Collections.<String>emptyList());
            VariantChooser.Props props = new VariantChooser.Props(
                    open, candidate, enabled, true, null, visualAdapter,
                    mode, mode::set, selectedKeys, selectedKeys::set,
                    commits::add, () -> cancels[0]++);
            handle = rt.mount(sceneRoot, () -> {
                if (pageTheme == null) {
                    return VariantChooser.create(rt, props);
                }
                final SceneNode[] holder = new SceneNode[1];
                SceneThemes.withTheme(pageTheme,
                        () -> holder[0] = VariantChooser.create(rt, props));
                return holder[0];
            });
            rt.flush();
            layoutAll();
        }
    }

    /** 打开浮层并布局收敛。 */
    private void open(Fixture f, SearchPickerData.Candidate cand) {
        f.candidate.set(cand);
        f.open.set(Boolean.TRUE);
        rt.flush();
        layoutAll();
        layoutAll();
    }

    /** 布局主树 + 所有 overlay 并桥接 layout epoch。 */
    private void layoutAll() {
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        for (SceneOverlayHost.Entry entry : rt.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(W, H));
        }
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    private SceneNode overlayRoot() {
        List<SceneOverlayHost.Entry> entries = rt.getOverlayHost().topFirst();
        Assert.assertTrue("缺少 overlay", !entries.isEmpty());
        return entries.get(0).getRoot();
    }

    /** 卡片 = overlay 根（scrim）的第 0 个子节点。 */
    private SceneNode card() {
        return overlayRoot().__getChildren().get(0);
    }

    /** 查询输入 = 卡片 children[1]。 */
    private SceneNode search() {
        return card().__getChildren().get(1);
    }

    /** 模式分段 = 卡片 children[2]。 */
    private SceneNode segmented() {
        return card().__getChildren().get(2);
    }

    /** 变体列表行容器 = 卡片 children[3](listHost).children[0](viewport).children[0](content)。 */
    private SceneNode list() {
        return card().__getChildren().get(3).__getChildren().get(0).__getChildren().get(0);
    }

    /** 确认按钮 = footer children[1]。 */
    private SceneNode confirmButton() {
        return card().__getChildren().get(4).__getChildren().get(1);
    }

    /** 取消按钮 = footer children[0]。 */
    private SceneNode cancelButton() {
        return card().__getChildren().get(4).__getChildren().get(0);
    }

    /** 标题节点 = header children[0]。 */
    private SceneNode titleNode() {
        return card().__getChildren().get(0).__getChildren().get(0);
    }

    /** 候选名节点 = header children[1]。 */
    private SceneNode candidateLabelNode() {
        return card().__getChildren().get(0).__getChildren().get(1);
    }

    /** 第 i 行节点。 */
    private SceneNode rowAt(int i) {
        return list().__getChildren().get(i);
    }

    /** 行的图标节点（children[0]）。 */
    private static SceneNode iconOf(SceneNode row) {
        return row.__getChildren().get(0);
    }

    /** 行的标签节点（children[1]）。 */
    private static SceneNode labelOf(SceneNode row) {
        return row.__getChildren().get(1);
    }

    /** 行的勾选圆点节点（children[2]）。 */
    private static SceneNode dotOf(SceneNode row) {
        return row.__getChildren().get(2);
    }

    /** 选中配方语义：RGB 换强调色、alpha 用主题统一选中强度 0x59（selectableSurface 口径）。 */
    private static int selectedTint(int accent) {
        return (0x59 << 24) | (accent & 0x00FFFFFF);
    }

    private static int alphaOf(int argb) {
        return (argb >>> 24) & 0xFF;
    }

    private static int rgbOf(int argb) {
        return argb & 0x00FFFFFF;
    }

    /** 节点子树内的 BACKDROP 命令数（每颗采样滤镜的表面恰好 1 条）。 */
    private static int backdropCount(ScenePaintEngine engine, SceneNode node) {
        PaintPlan plan = engine.paint(node).getPlan();
        int count = 0;
        for (PaintCommand command : plan.getCommands()) {
            if (command.getType() == PaintCommandType.BACKDROP) {
                count++;
            }
        }
        return count;
    }

    // ==================== 开合挂载语义 ====================

    @Test
    public void mountsOverlayWhenOpenAndHasVariants() {
        Fixture f = new Fixture();
        Assert.assertTrue(rt.getOverlayHost().isEmpty());
        open(f, candidate("a", "v1", "v2", "v3"));
        Assert.assertEquals(1, rt.getOverlayHost().size());
    }

    @Test
    public void noOverlayWhenCandidateHasNoVariants() {
        Fixture f = new Fixture();
        open(f, candidate("a"));
        Assert.assertTrue(rt.getOverlayHost().isEmpty());
    }

    @Test
    public void unmountsOverlayWhenClosed() {
        Fixture f = new Fixture();
        open(f, candidate("a", "v1", "v2"));
        Assert.assertEquals(1, rt.getOverlayHost().size());
        f.open.set(Boolean.FALSE);
        rt.flush();
        Assert.assertTrue(rt.getOverlayHost().isEmpty());
    }

    // ==================== 行渲染 ====================

    @Test
    public void rendersRowPerVariantWithAdapterLabels() {
        Fixture f = new Fixture();
        open(f, candidate("a", "v1", "v2", "v3"));
        SceneNode rows = list();
        Assert.assertEquals(3, rows.__getChildren().size());
        // 行 = [icon, label, indicator]；label 文本来自 visualAdapter.variantLabel
        for (int i = 0; i < 3; i++) {
            SceneNode row = rows.__getChildren().get(i);
            Assert.assertEquals("变体" + i, row.__getChildren().get(1).getText());
        }
    }

    // ==================== 勾选语义 ====================

    @Test
    public void selectedModeClickTogglesKeys() {
        Fixture f = new Fixture();
        // 受控置 SELECTED + 已选 v1
        f.mode.set(SearchPickerData.SelectionMode.SELECTED);
        f.selectedKeys.set(Collections.singletonList("v1"));
        open(f, candidate("a", "v1", "v2"));

        // 点击 v1 行：已勾选 → onKeysChange(移除 v1)
        click(list().__getChildren().get(0));
        rt.flush();
        Assert.assertTrue(f.selectedKeys.get().isEmpty());

        // 点击 v2 行：加入 v2
        click(list().__getChildren().get(1));
        rt.flush();
        Assert.assertEquals(Collections.singletonList("v2"), f.selectedKeys.get());
    }

    @Test
    public void allModeClickHasNoEffect() {
        Fixture f = new Fixture();
        // 默认 ALL
        open(f, candidate("a", "v1", "v2"));

        // 点击 v1 / v2 行：ALL 模式只读，无任何副作用
        click(list().__getChildren().get(0));
        click(list().__getChildren().get(1));
        rt.flush();
        Assert.assertEquals(SearchPickerData.SelectionMode.ALL, f.mode.get());
        Assert.assertTrue(f.selectedKeys.get().isEmpty());
    }

    // ==================== 模式分段受控回写 ====================

    @Test
    public void segmentedWritesModeChange() {
        Fixture f = new Fixture();
        open(f, candidate("a", "v1", "v2"));
        // 点击 "Selected" 段（children[1]）→ onModeChange(SELECTED)
        SceneNode seg = segmented();
        click(seg.__getChildren().get(1));
        rt.flush();
        Assert.assertEquals(SearchPickerData.SelectionMode.SELECTED, f.mode.get());
    }

    // ==================== 提交契约 ====================

    @Test
    public void commitDeliversSelectionContract() {
        Fixture f = new Fixture();
        f.mode.set(SearchPickerData.SelectionMode.SELECTED);
        f.selectedKeys.set(Arrays.asList("v1", "v2"));
        open(f, candidate("a", "v1", "v2", "v3"));
        click(confirmButton());
        rt.flush();
        Assert.assertEquals(1, f.commits.size());
        SearchPickerData.Selection s = f.commits.get(0);
        Assert.assertEquals("a", s.candidateKey());
        Assert.assertEquals(SearchPickerData.SelectionMode.SELECTED, s.mode());
        Assert.assertEquals(Arrays.asList("v1", "v2"), s.variantKeys());
    }

    @Test
    public void allModeCommitDeliversEmptyKeys() {
        Fixture f = new Fixture();
        open(f, candidate("a", "v1", "v2"));
        click(confirmButton());
        rt.flush();
        Assert.assertEquals(1, f.commits.size());
        SearchPickerData.Selection s = f.commits.get(0);
        Assert.assertEquals(SearchPickerData.SelectionMode.ALL, s.mode());
        Assert.assertTrue(s.variantKeys().isEmpty());
    }

    // ==================== 取消 ====================

    @Test
    public void cancelInvokesOnCancelOnce() {
        Fixture f = new Fixture();
        open(f, candidate("a", "v1", "v2"));
        click(cancelButton());
        rt.flush();
        Assert.assertEquals(1, f.cancels[0]);
        Assert.assertEquals("取消不提交", 0, f.commits.size());
        // 取消不写 open —— open 仍由外壳持有为 true（模块只回调）
        Assert.assertTrue(f.open.get().booleanValue());
    }

    // ==================== 查询过滤 ====================

    @Test
    public void queryFiltersRows() {
        Fixture f = new Fixture();
        open(f, candidate("a", "v1", "v2", "v3"));
        Assert.assertEquals(3, list().__getChildren().size());

        // 聚焦查询输入并写入 "v2"：按 key 大小写不敏感过滤，仅 v2 行的 key 含 "v2"
        rt.requestFocus(search());
        rt.flush();
        typeText("v2");
        layoutAll();
        Assert.assertEquals(1, list().__getChildren().size());
        Assert.assertEquals("变体1", list().__getChildren().get(0).__getChildren().get(1).getText());
    }

    // ==================== ① 浮层面板：OVERLAY 配方逐项 + 面板恰一颗滤镜 ====================

    @Test
    public void panelUsesOverlayRecipeOwnedBySurfaceBinder() {
        Fixture f = new Fixture();
        open(f, candidate("a", "v1", "v2"));
        SceneNode scrim = overlayRoot();
        SceneNode card = card();

        Assert.assertEquals("面板背景 = OVERLAY idle 染色", OVERLAY.getIdle().getTint(),
                card.getBackgroundColor());
        Assert.assertEquals("面板边框宽 = 配方独占（旧 applyPanelChrome 静态写入删除）",
                OVERLAY.getBorderWidth(), card.getBorderWidth());
        Assert.assertEquals("面板边框色 = 配方 idle 缘色", OVERLAY.getIdle().getEdge(),
                card.getBorderColor());
        Assert.assertEquals("面板圆角 = 配方独占（不再静态 RADIUS_LG）",
                OVERLAY.getCornerRadius(), card.getCornerRadius());
        Assert.assertNotNull("面板默认带液态玻璃滤镜", card.getBackdrop());
        Assert.assertEquals("面板模糊半径 = 配方", OVERLAY.getBackdrop().getBlurRadius(),
                card.getBackdrop().getBlurRadius());
        Assert.assertEquals("面板材质 = 配方", OVERLAY.getBackdrop().getEffect().getMaterial(),
                card.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("面板实体高度 = 配方 idle 档", OVERLAY.getIdle().getElevation(),
                card.__getSurfaceElevation(), EPSILON);

        // 旧静态外观写入者已删除（applyPanelChrome 实色四件套）
        Assert.assertNotEquals("不再取旧实色 BG_DEFAULT", SceneChromeTokens.BG_DEFAULT,
                card.getBackgroundColor());
        Assert.assertNotEquals("不再取旧 BORDER_DEFAULT", SceneChromeTokens.BORDER_DEFAULT,
                card.getBorderColor());

        // scrim 只负责遮罩：静态半透明底、零玻璃（契约 §4.1「遮罩只负责遮罩」）
        Assert.assertNull("scrim 不声明 backdrop", scrim.getBackdrop());
        Assert.assertEquals("scrim 保持遮罩语义静态底", 0xCC000000, scrim.getBackgroundColor());
        Assert.assertEquals("scrim 自身不新增 BACKDROP 采样（面板表面那颗挂在 card 上）",
                backdropCount(paintEngine, card), backdropCount(paintEngine, scrim));
        Assert.assertTrue("面板裁剪保持（clip 非绑定器属性，组件自持）", card.isClipChildren());
        Assert.assertEquals("面板宽度不受主题影响（主题不改布局）", 440, card.getPreferredWidth());
    }

    // ==================== ② 变体行：轻量三态档 + 行零 BACKDROP/零边框圆角 ====================

    @Test
    public void rowsUseLightweightIndicatorTiersWithoutBackdrop() {
        Fixture f = new Fixture();
        f.mode.set(SearchPickerData.SelectionMode.SELECTED);
        f.selectedKeys.set(Collections.singletonList("v1"));
        open(f, candidate("a", "v1", "v2"));
        SceneNode rowV1 = rowAt(0);
        SceneNode rowV2 = rowAt(1);

        // 选中档：RGB = 主题强调色、强度 = 统一选中强度 0x59（不只靠透明度）
        Assert.assertEquals("选中行 = 强调色选中档", ROW_SELECTED, rowV1.getBackgroundColor());
        Assert.assertEquals("选中 RGB = 主题强调色", rgbOf(SceneThemes.DEFAULT.accent()),
                rgbOf(rowV1.getBackgroundColor()));
        Assert.assertEquals("选中强度 = 0x59", 0x59, alphaOf(rowV1.getBackgroundColor()));
        Assert.assertTrue("高亮 0x59 必须明显强于 hover 档（不只靠透明度区分）",
                alphaOf(ROW_SELECTED) > alphaOf(ROW_HOVER));

        // 未选中四档：idle/hover/pressed/disabled 取 INDICATOR 配方档
        Assert.assertEquals("未选中 idle = 配方 idle 档", ROW_IDLE, rowV2.getBackgroundColor());
        pointerAtRow(1, ScenePointerAction.MOVE);
        Assert.assertEquals("hover = 配方 hovered 档", ROW_HOVER, rowV2.getBackgroundColor());
        pointerAtRow(1, ScenePointerAction.BUTTON_DOWN);
        Assert.assertEquals("pressed 压过 hovered = 配方 pressed 档", ROW_PRESSED,
                rowV2.getBackgroundColor());
        // 释放即一次点击：SELECTED 模式 toggle 勾选 v2（行为合同）+ 指针仍悬停 → 选中悬停档
        pointerAtRow(1, ScenePointerAction.BUTTON_UP);
        Assert.assertEquals("释放触发勾选（行为合同保持）", Arrays.asList("v1", "v2"),
                f.selectedKeys.get());
        Assert.assertEquals("勾选后 = 选中悬停档", ROW_SELECTED_HOVER, rowV2.getBackgroundColor());
        click(rowV2);
        Assert.assertEquals("再点取消勾选", Collections.singletonList("v1"), f.selectedKeys.get());
        Assert.assertEquals("取消后回 hovered 档（指针未移开）", ROW_HOVER,
                rowV2.getBackgroundColor());
        pointerAway();
        Assert.assertEquals("移开回 idle 档", ROW_IDLE, rowV2.getBackgroundColor());

        Assert.assertEquals("选中行 idle = 强调色选中档", ROW_SELECTED, rowV1.getBackgroundColor());
        pointerAtRow(0, ScenePointerAction.MOVE);
        Assert.assertEquals("选中 + hover = 强调悬停档", ROW_SELECTED_HOVER,
                rowV1.getBackgroundColor());
        Assert.assertNotEquals("选中行 hover 仍区别于普通 hover", ROW_HOVER,
                rowV1.getBackgroundColor());

        // 轻量档只写背景色：行不装滤镜、不写边框/圆角/实体高度
        for (int i = 0; i < 2; i++) {
            SceneNode row = rowAt(i);
            Assert.assertEquals("行[" + i + "] 子树零 BACKDROP（复用行零滤镜裁决）", 0,
                    backdropCount(paintEngine, row));
            Assert.assertNull("行[" + i + "] 不声明 backdrop", row.getBackdrop());
            Assert.assertEquals("行[" + i + "] 不写边框宽", 0, row.getBorderWidth());
            Assert.assertEquals("行[" + i + "] 不写圆角", 0, row.getCornerRadius());
            Assert.assertEquals("行[" + i + "] 不绑定实体高度（-1=普通绘制）", -1.0F,
                    row.__getSurfaceElevation(), EPSILON);
            Assert.assertNull("行内图标不采样背景", iconOf(row).getBackdrop());
            Assert.assertNull("行内标签不采样背景", labelOf(row).getBackdrop());
            Assert.assertNull("行内圆点不采样背景", dotOf(row).getBackdrop());
        }

        // 旧接缝 SceneControlChrome.bindSelectableBackground / SceneStateColors 不再被默认路径调用
        Assert.assertNotEquals("选中不再取旧 SceneStateColors.selectedBackground 档",
                SceneStateColors.selectedBackground(true, false, false),
                rowV1.getBackgroundColor());
        Assert.assertNotEquals("未选中不再取旧 SceneStateColors.standardBackground 档",
                SceneStateColors.standardBackground(true, false, false),
                rowV2.getBackgroundColor());

        // 勾选圆点：启用+选中取主题强调底前景，未选中透明露出面板玻璃底
        Assert.assertEquals("选中行圆点 = onAccentForeground", DOT_ON,
                dotOf(rowV1).getBackgroundColor());
        Assert.assertEquals("未选中行圆点透明", BG_TRANSPARENT,
                dotOf(rowV2).getBackgroundColor());
        Assert.assertEquals("圆点圆角保持组件自持几何（16×16 圆形）", 8,
                dotOf(rowV1).getCornerRadius());

        // 文字三件套前景（标题/行标签正文、候选名次要）
        Assert.assertEquals("标题取主题正文色", FG, titleNode().getTextColor());
        Assert.assertEquals("候选名取主题 mutedForeground", FG_MUTED,
                candidateLabelNode().getTextColor());
        Assert.assertEquals("行标签取主题正文色", FG, labelOf(rowV1).getTextColor());

        // 禁用档优先级最高 + 禁用点击不回调（行为保持）
        int keysBefore = f.selectedKeys.get().size();
        f.enabled.set(Boolean.FALSE);
        rt.flush();
        Assert.assertEquals("disabled 压过 selected（选中行取禁用档）", ROW_DISABLED,
                rowV1.getBackgroundColor());
        Assert.assertEquals("未选中行同样取禁用档", ROW_DISABLED, rowV2.getBackgroundColor());
        click(rowV2);
        Assert.assertEquals("禁用态点击不触发 onKeysChange", keysBefore,
                f.selectedKeys.get().size());
        Assert.assertEquals("禁用行标签取 disabledForeground", FG_DISABLED,
                labelOf(rowV1).getTextColor());
        Assert.assertEquals("禁用不显示选中标记（圆点透明，RadioGroup dot 同口径）",
                BG_TRANSPARENT, dotOf(rowV1).getBackgroundColor());

        f.enabled.set(Boolean.TRUE);
        rt.flush();
        Assert.assertEquals("恢复启用：指针自 MOVE 帧起一直悬停在 v1 行（BUTTON 帧不转移 hover）→ 选中悬停档",
                ROW_SELECTED_HOVER, rowV1.getBackgroundColor());
        Assert.assertEquals("恢复启用：v2 未悬停回 idle 档", ROW_IDLE, rowV2.getBackgroundColor());
        pointerAtRow(1, ScenePointerAction.MOVE);
        Assert.assertEquals("MOVE 帧转移 hover：v2 进 hovered 档", ROW_HOVER, rowV2.getBackgroundColor());
        Assert.assertEquals("v1 退 hover 后仍保持选中档", ROW_SELECTED, rowV1.getBackgroundColor());
        Assert.assertEquals("恢复启用：圆点标记恢复", DOT_ON, dotOf(rowV1).getBackgroundColor());
        Assert.assertEquals("恢复启用：标签回正文色", FG, labelOf(rowV1).getTextColor());
        pointerAway();
    }

    // ==================== ③ 复用/重绑：状态不串到下一项 ====================

    @Test
    public void reboundRowsDoNotCarryPreviousRowStates() {
        Fixture f = new Fixture();
        f.mode.set(SearchPickerData.SelectionMode.SELECTED);
        f.selectedKeys.set(Collections.singletonList("v1"));
        open(f, candidate("a", "v1", "v2"));
        SceneNode staleRow = rowAt(0);
        pointerAtRow(0, ScenePointerAction.MOVE);
        Assert.assertEquals("重绑前：v1 行 = 选中 + hover 强调悬停档",
                ROW_SELECTED_HOVER, staleRow.getBackgroundColor());

        // 换候选：变体集整体更替（keyed forEach 重绑场景）
        f.candidate.set(candidate("a", "x1", "x2"));
        rt.flush();
        layoutAll();
        layoutAll();

        SceneNode fresh = rowAt(0);
        Assert.assertNotSame("v1 行被回收、x1 新建", staleRow, fresh);
        Assert.assertEquals("重绑行标签", "变体0", labelOf(fresh).getText());
        Assert.assertEquals("选中不串：x1 未勾选（keys 仍为 [v1]）→ idle 档",
                ROW_IDLE, fresh.getBackgroundColor());
        Assert.assertEquals("hover 不串：指针未发新帧前不残留悬停档",
                ROW_IDLE, fresh.getBackgroundColor());
        Assert.assertNull("滤镜不串：新行零 BACKDROP", fresh.getBackdrop());
        Assert.assertEquals(0, backdropCount(paintEngine, fresh));
        Assert.assertEquals("其余行不残留状态", ROW_IDLE, rowAt(1).getBackgroundColor());
        Assert.assertEquals("圆点不串：新行未选中 → 透明", BG_TRANSPARENT,
                dotOf(fresh).getBackgroundColor());

        // 重绑行交互照常工作
        click(fresh);
        rt.flush();
        Assert.assertEquals("新行点击 toggle 自身 key", Arrays.asList("v1", "x1"),
                f.selectedKeys.get());
        Assert.assertEquals("勾选后新行进入选中档", ROW_SELECTED, fresh.getBackgroundColor());

        // 同 key 换位（复用节点移动位置）：节点身份不变、选中跟随 key 而非位置
        SceneNode x1Node = fresh;
        f.candidate.set(candidate("a", "x2", "x1"));
        rt.flush();
        layoutAll();
        layoutAll();
        Assert.assertSame("同 key 换位复用同一节点", x1Node, rowAt(1));
        Assert.assertEquals("x1 换位后保持选中（状态跟 key 不跟位置）",
                ROW_SELECTED, rowAt(1).getBackgroundColor());
        Assert.assertEquals("x2 换位后仍 idle", ROW_IDLE, rowAt(0).getBackgroundColor());
        click(rowAt(1));
        rt.flush();
        Assert.assertEquals("取消勾选后回 idle", ROW_IDLE, rowAt(1).getBackgroundColor());
        Assert.assertEquals(Collections.singletonList("v1"), f.selectedKeys.get());
    }

    // ==================== ④ 变体物品图像不改色（渲染协议反向钉住） ====================

    @Test
    public void variantImagesKeepRenderProtocolColorsAcrossThemeSwitch() {
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        SceneImageSource img = image("test:v1img:0");
        Fixture f = new Fixture(pageTheme, adapterWithImageFor("v1", img));
        open(f, candidate("a", "v1", "v2"));

        SceneNode iconV1 = iconOf(rowAt(0));
        SceneNode iconV2 = iconOf(rowAt(1));
        Assert.assertSame("有图行挂原图片源（不改渲染协议）", img, iconV1.getImageSource());
        Assert.assertEquals("有图行图标底色透明（不叠加任何主题色）", BG_TRANSPARENT,
                iconV1.getBackgroundColor());
        Assert.assertNull("无图行不挂图片源", iconV2.getImageSource());
        Assert.assertEquals("无图行回退静态占位底色", ICON_PLACEHOLDER,
                iconV2.getBackgroundColor());
        Assert.assertNull("图标不装滤镜", iconV1.getBackdrop());

        // 主题切换：图像协议值逐字节不变，而文字/行底色随主题重派生（对照组）
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：深/浅 INDICATOR 配方不同",
                INDICATOR, light.surface(SceneTheme.Role.INDICATOR));
        pageTheme.set(light);
        rt.flush();
        layoutAll();

        Assert.assertSame("主题切换不动图片源", img, iconV1.getImageSource());
        Assert.assertEquals("主题切换不重染有图行图标底", BG_TRANSPARENT,
                iconV1.getBackgroundColor());
        Assert.assertEquals("主题切换不重染占位底", ICON_PLACEHOLDER,
                iconV2.getBackgroundColor());
        Assert.assertNull("主题切换不给图标加装滤镜", iconV2.getBackdrop());
        Assert.assertEquals("对照：行标签文字随主题更新", light.foreground(),
                labelOf(rowAt(0)).getTextColor());
        Assert.assertEquals("对照：行底色随主题更新",
                light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(),
                rowAt(1).getBackgroundColor());
    }

    // ==================== ⑤ 主题切换：只重派生、身份不变、effect 不增、选中不丢 ====================

    @Test
    public void themeSwitchReDerivesWithoutRebuildAndKeepsSelection() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("测试前提：深/浅 OVERLAY 配方不同",
                dark.surface(SceneTheme.Role.OVERLAY), light.surface(SceneTheme.Role.OVERLAY));
        Assert.assertNotEquals("测试前提：深/浅 INDICATOR 配方不同",
                dark.surface(SceneTheme.Role.INDICATOR), light.surface(SceneTheme.Role.INDICATOR));
        Assert.assertNotEquals("测试前提：深/浅正文前景不同", dark.foreground(), light.foreground());
        Assert.assertNotEquals("测试前提：深/浅强调底前景不同",
                dark.onAccentForeground(), light.onAccentForeground());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        Fixture f = new Fixture(pageTheme);
        f.mode.set(SearchPickerData.SelectionMode.SELECTED);
        f.selectedKeys.set(Collections.singletonList("v1"));
        open(f, candidate("a", "v1", "v2"));

        SceneNode card = card();
        SceneNode rowV1 = rowAt(0);
        SceneNode rowV2 = rowAt(1);
        SceneNode label = labelOf(rowV1);
        SceneNode dot = dotOf(rowV1);
        SceneNode title = titleNode();
        SceneNode candidateLabel = candidateLabelNode();
        Assert.assertEquals("初始面板取深色 OVERLAY 档",
                dark.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(),
                card.getBackgroundColor());
        Assert.assertEquals("初始选中行取深色强调选中档", ROW_SELECTED, rowV1.getBackgroundColor());

        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        rt.flush();
        layoutAll();

        Assert.assertSame("主题切换不重建面板节点", card, card());
        Assert.assertSame("主题切换不重建行节点", rowV1, rowAt(0));
        Assert.assertSame("主题切换不重建标签节点", label, labelOf(rowAt(0)));
        Assert.assertSame("主题切换不重建圆点节点", dot, dotOf(rowAt(0)));
        Assert.assertSame("主题切换不重建标题节点", title, titleNode());
        Assert.assertSame("主题切换不重建候选名节点", candidateLabel, candidateLabelNode());

        Assert.assertEquals("面板底色随主题更新",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getTint(),
                card.getBackgroundColor());
        Assert.assertEquals("面板滤镜材质随主题更新",
                light.surface(SceneTheme.Role.OVERLAY).getBackdrop().getEffect().getMaterial(),
                card.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals("面板缘色随主题更新",
                light.surface(SceneTheme.Role.OVERLAY).getIdle().getEdge(),
                card.getBorderColor());
        Assert.assertEquals("选中行随主题更新且不丢选中", selectedTint(light.accent()),
                rowV1.getBackgroundColor());
        Assert.assertEquals("未选中行随主题更新",
                light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(),
                rowV2.getBackgroundColor());
        Assert.assertEquals("行标签随主题更新", light.foreground(), label.getTextColor());
        Assert.assertEquals("标题随主题更新", light.foreground(), title.getTextColor());
        Assert.assertEquals("候选名随主题更新", light.mutedForeground(),
                candidateLabel.getTextColor());
        Assert.assertEquals("圆点标记随主题更新", light.onAccentForeground(),
                dot.getBackgroundColor());
        Assert.assertEquals("行依旧零 BACKDROP（切主题不给行加装滤镜）",
                0, backdropCount(paintEngine, rowV1));
        Assert.assertNull("圆点依旧不声明 backdrop", dot.getBackdrop());

        Assert.assertEquals("受控模式不受主题切换影响",
                SearchPickerData.SelectionMode.SELECTED, f.mode.get());
        Assert.assertEquals("受控选中项不丢", Collections.singletonList("v1"),
                f.selectedKeys.get());
        Assert.assertEquals("主题切换不新增订阅",
                effectsBeforeSwitch, ReactiveTestProbe.registeredEffectCount());

        // 切换后交互照常：点击取消勾选、回调链路完好
        click(rowAt(0));
        rt.flush();
        Assert.assertTrue(f.selectedKeys.get().isEmpty());
        Assert.assertEquals("切换后取消勾选回浅色 idle 档",
                light.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(),
                rowV1.getBackgroundColor());
    }

    // ==================== ⑥ 卸载回收 ====================

    @Test
    public void closeAndDisposeReclaimAllBindings() {
        int globalBaseline = ReactiveTestProbe.registeredEffectCount();
        Fixture f = new Fixture();
        int closedBase = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("组件本体挂载应注册响应式绑定", closedBase > globalBaseline);

        open(f, candidate("a", "v1", "v2", "v3"));
        int opened = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("浮层打开注册面板/行/前景绑定", opened > closedBase);

        // 关闭：portal 子 Owner 随不可见回收，overlay 绑定不残留
        f.open.set(Boolean.FALSE);
        rt.flush();
        Assert.assertTrue(rt.getOverlayHost().isEmpty());
        int afterClose = ReactiveTestProbe.registeredEffectCount();
        Assert.assertTrue("关闭后浮层绑定大幅回收", afterClose < opened);

        // 重开再关：绑定数回到同一水平（无泄漏累积）
        open(f, candidate("a", "v1", "v2"));
        int reopened = ReactiveTestProbe.registeredEffectCount();
        f.open.set(Boolean.FALSE);
        rt.flush();
        Assert.assertEquals("重开再关不累积绑定", afterClose,
                ReactiveTestProbe.registeredEffectCount());
        Assert.assertTrue(reopened > afterClose);

        // 卸载组件：全部绑定回基线
        f.handle.dispose();
        rt.flush();
        Assert.assertEquals("卸载回收该实例全部绑定", globalBaseline,
                ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 输入注入辅助 ====================

    private void click(SceneNode node) {
        int[] center = centerOf(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, center[0], center[1]);
        routePointer(ScenePointerAction.BUTTON_UP, center[0], center[1]);
        rt.flush();
    }

    /** 在第 i 行中心注入指针帧（MOVE=悬停；BUTTON_DOWN/UP=按下/释放）。 */
    private void pointerAtRow(int i, ScenePointerAction action) {
        int[] center = centerOf(rowAt(i));
        routePointer(action, center[0], center[1]);
        rt.flush();
    }

    /** 指针移到大屏角落（仍在 scrim 内但离开所有行，触发 hover 退出）。 */
    private void pointerAway() {
        routePointer(ScenePointerAction.MOVE, W - 2, H - 2);
        rt.flush();
    }

    private void typeText(String text) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofText(text, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
        rt.flush();
    }

    private void routePointer(ScenePointerAction action, int x, int y) {
        InputFrameBuilder fb = new InputFrameBuilder(x, y);
        fb.push(RawInputEvent.ofPointer(action, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        rt.route(sceneRoot, fb.drainFrame(), 0, 0);
    }

    private int[] centerOf(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            throw new IllegalStateException("节点未布局或零尺寸，无法取中心: " + box);
        }
        return new int[]{box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2};
    }
}
