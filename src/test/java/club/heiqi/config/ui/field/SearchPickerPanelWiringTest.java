package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.schema.SearchPickerSpec;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.ui.editor.Codec;
import club.heiqi.config.ui.editor.CurrentValuePresenter;
import club.heiqi.config.ui.editor.ListMemberCodec;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneSimpleList;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 行触发器 → 居中 70% 面板 → 提交 → 行更新闭环集成测试（宿主桥接范式）。
 *
 * <p>照 {@code ScenePickerPanelTest} 的 host 桥接手法：直接 new SceneRuntime + 布局引擎 +
 * 编程注入帧。覆盖 SINGLE_VALUE 与 LIST_MEMBERS 两条完整链路：行触发器打开受控面板、
 * 面板内选择/新增提交写回宿主值、行展示随值刷新、关闭后焦点恢复。</p>
 */
public class SearchPickerPanelWiringTest {

    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private SceneNode sceneRoot;
    private MountHandle mountHandle;

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
        if (mountHandle != null) mountHandle.dispose();
        rt.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== SINGLE_VALUE 闭环 ====================

    /** 行触发器展示当前值 → Enter 打开面板 → 网格提交写回宿主值 → 行展示刷新且焦点恢复。 */
    @Test
    public void singleValueRowTriggerPanelCommitUpdatesRowAndRestoresFocus() {
        Signal<Object> value = Signal.<Object>create("before");
        Codec codec = new Codec() {
            @Override
            public SearchPickerData.Selection decode(Object raw) {
                return selection(String.valueOf(raw));
            }

            @Override
            public Object encode(Object current, SearchPickerData.Selection selected) {
                return selected.candidateKey();
            }
        };
        Registry registry = registry("test:picker", codec, presenter());
        SceneNode[] pickerHolder = new SceneNode[1];
        mountHandle = rt.mount(sceneRoot, () -> {
            pickerHolder[0] = SearchPickerFieldSupport.createControlledIfPresent(rt,
                    ValueSpec.string().withWidget(new SearchPickerSpec("test:picker", 8)),
                    value, registry, value::set);
            return pickerHolder[0];
        });
        rt.flush();
        SceneNode trigger = pickerHolder[0].__getChildren().get(0);
        assertTrue("行触发器应展示当前值", containsText(trigger, "before"));

        rt.requestFocus(trigger);
        pressKey(SceneKey.ENTER);
        Assert.assertEquals("Enter 应打开全屏面板", 1, rt.getOverlayHost().size());
        layoutAll();
        layoutAll();

        click(gridCell(panelRoot(), 0));
        Assert.assertEquals("提交应写回宿主值", "picked", value.get());
        Assert.assertTrue("成功提交应关闭面板", rt.getOverlayHost().isEmpty());
        Assert.assertSame("关闭后焦点应恢复到行触发器", trigger, rt.getFocusedNode());
        assertTrue("行展示应随宿主值刷新", containsText(trigger, "picked"));
    }

    // ==================== LIST_MEMBERS 闭环 ====================

    /** 行摘要与管理入口 → 面板内新增成员 → 宿主列表写回 → 面板重武装 → 摘要刷新 → ESC 关闭。 */
    @Test
    public void listMembersRowTriggerPanelAddMemberUpdatesSummaryAndStaysOpen() {
        Signal<Object> raw = Signal.<Object>create(new ArrayList<Object>(Collections.singletonList("raw:a")));
        Signal<List<SceneSimpleList.ListItem>> items = Signal.create(new ArrayList<SceneSimpleList.ListItem>(
                Collections.singletonList(new SceneSimpleList.ListItem("raw:a"))));
        Registry registry = registry("test:picker",
                new ListMemberCodec() {
                    @Override
                    public SearchPickerData.Selection decodeMember(Object rawMember) {
                        if (!(rawMember instanceof String)) return null;
                        String text = (String) rawMember;
                        int split = text.indexOf(':');
                        return selection(split < 0 ? text : text.substring(0, split));
                    }

                    @Override
                    public Object encodeMember(Object current, SearchPickerData.Selection selected) {
                        return selected.candidateKey() + ":";
                    }

                    @Override
                    public SearchPickerData.Selection decode(Object rawValue) { return null; }

                    @Override
                    public Object encode(SearchPickerData.Selection selection) { return null; }
                }, null);
        java.util.function.Consumer<Object> onChange = published -> {
            raw.set(published);
            items.set(toItems(published));
        };
        SceneNode[] pickerHolder = new SceneNode[1];
        mountHandle = rt.mount(sceneRoot, () -> {
            pickerHolder[0] = SearchPickerFieldSupport.createListMembersIfPresent(rt,
                    ValueSpec.list(ValueSpec.string()).withWidget(new SearchPickerSpec("test:picker", 8,
                            SearchPickerSpec.BindingMode.LIST_MEMBERS)), raw, items,
                    registry, onChange);
            return pickerHolder[0];
        });
        rt.flush();
        SceneNode picker = pickerHolder[0];
        SceneNode management = picker.__getChildren().get(0);
        SceneNode manage = management.__getChildren().get(0);
        assertTrue("摘要应显示已配置数量", containsText(picker, "Configured 1 items"));

        layoutAll();
        click(manage);
        Assert.assertEquals("管理按钮应打开全屏面板", 1, rt.getOverlayHost().size());
        layoutAll();
        SceneNode panel = panelRoot();
        SceneNode membersPanel = panel.__getChildren().get(2);
        Assert.assertEquals("底部横带应渲染一个当前成员行", 1,
                membersPanel.__getChildren().get(1).__getChildren().size());

        click(membersPanel.__getChildren().get(0).__getChildren().get(2));
        layoutAll();
        click(gridCell(panel, 0));
        Assert.assertEquals("新增成员应写回宿主列表", Arrays.asList("raw:a", "picked:"), raw.get());
        Assert.assertEquals("新增成功后面板保持展开重新武装", 1, rt.getOverlayHost().size());
        assertTrue("行摘要应随新增刷新", containsText(picker, "Configured 2 items"));

        pressKey(SceneKey.ESCAPE);
        Assert.assertTrue("ESC 应关闭面板", rt.getOverlayHost().isEmpty());
        Assert.assertSame("关闭后焦点应恢复到管理按钮", manage, rt.getFocusedNode());
        Assert.assertEquals("ESC 的 onCancel 必须复位编辑目标且零写", Arrays.asList("raw:a", "picked:"), raw.get());
    }

    // ==================== 宿主桥接助手 ====================

    private void layoutAll() {
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        for (SceneOverlayHost.Entry entry : rt.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(W, H));
        }
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    /** 主面板 overlay root = 透明 scrim；测试结构定位使用 children[0] 卡片。 */
    private SceneNode panelRoot() {
        return rt.getOverlayHost().bottomFirst().get(0).getRoot().__getChildren().get(0);
    }

    /** 结果列表单元：中栏 children = [error, list, infoBar]；viewport = [topSpacer, rowsContainer, bottomSpacer]。 */
    private static SceneNode gridCell(SceneNode panel, int index) {
        SceneNode viewport = panel.__getChildren().get(1).__getChildren().get(1).__getChildren().get(1);
        SceneNode rowsContainer = viewport.__getChildren().get(1);
        for (SceneNode row : rowsContainer.__getChildren()) {
            if (index < row.__getChildren().size()) return row.__getChildren().get(index);
            index -= row.__getChildren().size();
        }
        throw new IllegalStateException("cell index out of mounted list: " + index);
    }

    private void click(SceneNode node) {
        int[] center = centerOf(node);
        routePointer(ScenePointerAction.BUTTON_DOWN, center[0], center[1]);
        routePointer(ScenePointerAction.BUTTON_UP, center[0], center[1]);
        rt.flush();
    }

    private void pressKey(SceneKey key) {
        InputFrameBuilder fb = new InputFrameBuilder(0, 0);
        fb.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED,
                false, false, false, false, 0, 0, 1000L));
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

    // ==================== 夹具 ====================

    private static Registry registry(String id, Codec codec, CurrentValuePresenter presenter) {
        Registry registry = new Registry();
        registry.register(new ValueEditorProvider() {
            @Override
            public String id() { return id; }

            @Override
            public Codec codec() { return codec; }

            @Override
            public VisualAdapter visualAdapter() {
                return new VisualAdapter() {
                    @Override
                    public String candidateLabel(SearchPickerData.Candidate candidate) { return candidate.label(); }

                    @Override
                    public String variantLabel(SearchPickerData.Variant variant) { return variant.label(); }
                };
            }

            @Override
            public SearchFunction searchFunction() {
                return (query, max) -> new SearchPickerData.SearchResult(Collections.singletonList(
                        new SearchPickerData.Candidate("picked", "Picked",
                                Collections.<SearchPickerData.Variant>emptyList())));
            }

            @Override
            public CurrentValuePresenter currentValuePresenter() { return presenter; }
        });
        registry.freeze();
        return registry;
    }

    private static CurrentValuePresenter presenter() {
        return value -> new CurrentValuePresenter.Presentation(String.valueOf(value),
                "summary-" + value, null);
    }

    private static SearchPickerData.Selection selection(String key) {
        return new SearchPickerData.Selection(key, SearchPickerData.SelectionMode.ALL,
                Collections.<String>emptyList());
    }

    private static List<SceneSimpleList.ListItem> toItems(Object value) {
        List<SceneSimpleList.ListItem> result = new ArrayList<SceneSimpleList.ListItem>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                result.add(new SceneSimpleList.ListItem(String.valueOf(item)));
            }
        }
        return result;
    }

    private static boolean containsText(SceneNode node, String expected) {
        if (expected.equals(node.getText())) return true;
        for (SceneNode child : node.__getChildren()) {
            if (containsText(child, expected)) return true;
        }
        return false;
    }

    private static void assertTrue(String message, boolean condition) {
        Assert.assertTrue(message, condition);
    }
}
