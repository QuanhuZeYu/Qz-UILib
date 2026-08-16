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
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
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
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * {@link VariantChooser} 单元测试（受控契约修订版）。
 *
 * <p>覆盖：开合挂载语义（有/无变体候选）、行渲染数量与 label、SELECTED 点击 toggle 勾选、
 * ALL 点击只读无副作用、提交 Selection 契约（candidateKey/mode/variantKeys）、
 * 取消回调、查询前缀过滤、模式分段受控回写。</p>
 */
public class VariantChooserTest {

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;

    private static final int W = 800;
    private static final int H = 600;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
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

        Fixture() {
            this.open = Signal.create(Boolean.FALSE);
            this.candidate = Signal.create(null);
            this.enabled = Signal.create(Boolean.TRUE);
            this.mode = Signal.create(SearchPickerData.SelectionMode.ALL);
            this.selectedKeys = Signal.create(Collections.<String>emptyList());
            VariantChooser.Props props = new VariantChooser.Props(
                    open, candidate, enabled, true, null, adapter(),
                    mode, mode::set, selectedKeys, selectedKeys::set,
                    commits::add, () -> cancels[0]++);
            rt.mount(sceneRoot, () -> VariantChooser.create(rt, props));
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

    /** 变体列表视口 = 卡片 children[3](listHost stackHost).children[0]。 */
    private SceneNode list() {
        return card().__getChildren().get(3).__getChildren().get(0);
    }

    /** 确认按钮 = footer children[1]。 */
    private SceneNode confirmButton() {
        return card().__getChildren().get(4).__getChildren().get(1);
    }

    /** 取消按钮 = footer children[0]。 */
    private SceneNode cancelButton() {
        return card().__getChildren().get(4).__getChildren().get(0);
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

    // ==================== 输入注入辅助 ====================

    private void click(SceneNode node) {
        int[] center = centerOf(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, center[0], center[1]);
        routePointer(ScenePointerAction.BUTTON_UP, center[0], center[1]);
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
