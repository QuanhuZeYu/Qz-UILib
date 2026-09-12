package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.GridProps;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.Props;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.Result;
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
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * {@link ScenePickerPanel} 成员编辑态公开入口（{@code Builder#memberEditRequest}）的 L3 运行时测试。
 *
 * <p>覆盖公开契约的四个面：① <b>初值语义</b>——以既有成员的目标态（候选/选择模式/变体 key）打开编辑态，
 * 与点击成员卡 [编辑] 同一通路（onEditCurrent 回传目标 id）；② <b>提交语义</b>——编辑态抑制「点击候选即
 * 隐式新增」的武装，提交仍走唯一原子边界 selectionCommit，宿主据此原位替换（而非删除 + 重新添加）；
 * ③ <b>取消语义</b>——ESC/dismiss 先 onCancel 再关闭，成员数据零变化；④ <b>非法 memberId</b>——
 * 未命中成员为无操作，面板保持普通（隐式新增）语义。</p>
 *
 * <p>宿主侧事务在本测试里按 {@code SearchPickerListBinding.confirm} 的语义实现：onEditCurrent 记录
 * 编辑目标 ⇒ 提交时原位替换该槽位，未武装则追加。这是「原位替换 vs 追加」的最终断言对象
 * （面板自己不写成员数据）。</p>
 */
public class ScenePickerPanelMemberEditRequestTest {

    private static final int W = 800;
    private static final int H = 600;

    private SceneNode sceneRoot;
    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;

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

    // ==================== 夹具 ====================

    /** 宿主夹具：受控开合 + 成员编辑目标事务 + 编辑态请求信号。 */
    private final class Fixture {
        final Signal<String> query = Signal.create("");
        final Signal<SearchPickerData.SearchResult> results;
        final Signal<List<SearchPickerData.CurrentMember>> members =
                Signal.create(Collections.<SearchPickerData.CurrentMember>emptyList());
        /** 宿主写入的成员编辑态请求（值 = 成员稳定 id；null = 无请求）。 */
        final Signal<Long> editRequest = Signal.create(null);
        final Signal<Boolean> openSignal = Signal.create(Boolean.FALSE);
        final List<SearchPickerData.Selection> commits = new ArrayList<SearchPickerData.Selection>();
        /** onEditCurrent 回传的成员 id（进入编辑态的唯一外部凭证）。 */
        final List<Long> edits = new ArrayList<Long>();
        /** 宿主当前编辑目标（onEditCurrent 记录；提交成功后清空，同 SearchPickerListBinding）。 */
        final AtomicReference<Long> editTarget = new AtomicReference<Long>();
        /** 宿主成员原始表；成员 id 按下标分配 ⇒ 编辑目标 id 即槽位下标。 */
        final List<String> raws = new ArrayList<String>();
        final AtomicInteger beginAdds = new AtomicInteger();
        final AtomicInteger cancels = new AtomicInteger();
        final AtomicInteger closeRequests = new AtomicInteger();
        final boolean[] commitResult = {true};
        final Props props;
        final Result result;

        Fixture(List<SearchPickerData.Candidate> initialCandidates,
                List<SearchPickerData.CurrentMember> initialMembers, boolean wireEditRequest) {
            results = Signal.create(new SearchPickerData.SearchResult(initialCandidates));
            members.set(initialMembers);
            for (SearchPickerData.CurrentMember member : initialMembers) {
                raws.add(member.selection() == null ? "" : member.selection().candidateKey());
            }
            Props.Builder builder = Props.builder(query, results, Signal.create(Boolean.TRUE),
                    query::set, ignored -> { }, visualAdapter());
            builder.currentMembers(members, memberId -> {
                edits.add(Long.valueOf(memberId));
                editTarget.set(Long.valueOf(memberId));
            });
            if (wireEditRequest) {
                builder.memberEditRequest(editRequest);
            }
            builder.selectionCommit(selection -> {
                commits.add(selection);
                if (!commitResult[0]) {
                    return false;
                }
                // 宿主侧事务（与 SearchPickerListBinding.confirm 同语义）：武装了编辑目标就原位替换，
                // 否则追加——面板不参与该决策，只提供「编辑态已武装」与目标 id 两个事实。
                Long target = editTarget.get();
                if (target == null) {
                    raws.add(selection.candidateKey());
                } else {
                    raws.set(target.intValue(), selection.candidateKey());
                }
                editTarget.set(null);
                return true;
            });
            builder.onBeginAdd(beginAdds::incrementAndGet);
            builder.onCancel(() -> {
                // 与 SearchPickerListBinding.cancel 一致：宿主取消时清自己的编辑目标，不改配置值。
                cancels.incrementAndGet();
                editTarget.set(null);
            });
            builder.open(openSignal);
            builder.onCloseRequest(() -> {
                closeRequests.incrementAndGet();
                openSignal.set(Boolean.FALSE);
            });
            builder.grid(GridProps.of(3, 64, 64, 8, 8, 3));
            props = builder.build();
            result = create(rt, props);
        }
    }

    private Result create(SceneRuntime runtime, Props props) {
        Result result = ScenePickerPanel.create(runtime, props);
        sceneRoot.appendChild(result.root());
        return result;
    }

    private static VisualAdapter visualAdapter() {
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

    private static SearchPickerData.Candidate candidate(String key) {
        return new SearchPickerData.Candidate(key, key + ":label",
                Collections.<SearchPickerData.Variant>emptyList());
    }

    private static SearchPickerData.Candidate candidateWithVariants(String key, String... variantKeys) {
        ArrayList<SearchPickerData.Variant> variants = new ArrayList<SearchPickerData.Variant>();
        for (String variantKey : variantKeys) {
            variants.add(new SearchPickerData.Variant(variantKey, variantKey + ":label"));
        }
        return new SearchPickerData.Candidate(key, key + ":label", variants);
    }

    private static SearchPickerData.CurrentMember member(long id, String candidateKey) {
        return new SearchPickerData.CurrentMember(id,
                new SearchPickerData.Selection(candidateKey, SearchPickerData.SelectionMode.ALL,
                        Collections.<String>emptyList()),
                candidate(candidateKey), true);
    }

    private static SearchPickerData.CurrentMember variantMember(long id, String candidateKey,
                                                                 SearchPickerData.SelectionMode mode,
                                                                 List<String> variantKeys) {
        return new SearchPickerData.CurrentMember(id,
                new SearchPickerData.Selection(candidateKey, mode, variantKeys),
                candidateWithVariants(candidateKey, "v1", "v2"), true);
    }

    // ==================== 通用辅助（与 ScenePickerPanelTest 同范式） ====================

    private void layoutAll() {
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        for (SceneOverlayHost.Entry entry : rt.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(W, H));
        }
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    private void openPanel(Fixture f) {
        f.openSignal.set(Boolean.TRUE);
        rt.flush();
        layoutAll();
        layoutAll();
    }

    private SceneNode overlayRoot(int fromTop) {
        List<SceneOverlayHost.Entry> entries = rt.getOverlayHost().topFirst();
        Assert.assertTrue("缺少 overlay", fromTop < entries.size());
        return entries.get(fromTop).getRoot();
    }

    /** 主面板 overlay root = 透明 scrim；children[0] 才是 70% 卡片。 */
    private SceneNode panelCard(SceneNode overlayRoot) {
        return overlayRoot.__getChildren().get(0);
    }

    /** 结果列表视口 = 卡片 children[1](selectionArea).children[1](center).children[1](stackHost).children[0]。 */
    private SceneNode gridViewport(SceneNode panelRoot) {
        return panelCard(panelRoot).__getChildren().get(1).__getChildren().get(1)
                .__getChildren().get(1).__getChildren().get(0);
    }

    private static SceneNode gridRows(SceneNode viewport) {
        return viewport.__getChildren().get(0).__getChildren().get(1);
    }

    /** 窗口内列表单元（按行序平铺）。 */
    private SceneNode gridCell(SceneNode viewport, int index) {
        SceneNode rowsContainer = gridRows(viewport);
        for (SceneNode row : rowsContainer.__getChildren()) {
            if (index < row.__getChildren().size()) {
                return row.__getChildren().get(index);
            }
            index -= row.__getChildren().size();
        }
        throw new IllegalStateException("cell index out of mounted window: " + index);
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

    // ==================== ① 初值 + ② 提交：原位替换 ====================

    /** 打开边沿以既有成员为初值进入编辑态；提交走唯一原子边界并由宿主原位替换（非删除 + 追加）。 */
    @Test
    public void openEdgeEntersEditStateWithExistingMemberAsInitialValue() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")),
                Arrays.asList(member(0L, "a")), true);
        // 宿主同帧写「请求 + open」：帧末批处理合并后按打开边沿生效
        f.editRequest.set(Long.valueOf(0L));
        openPanel(f);

        Assert.assertEquals("打开边沿应经 onEditCurrent 回传编辑目标成员 0",
                Collections.singletonList(Long.valueOf(0L)), f.edits);
        Assert.assertFalse("无变体成员不预开变体浮层",
                f.result.variantsOpen().get().booleanValue());
        Assert.assertEquals("进编辑态本身不改变成员数据",
                Collections.singletonList("a"), f.raws);

        // 提交新目标：编辑态抑制隐式新增武装 ⇒ 一次提交即走宿主编辑事务并请求关闭
        click(gridCell(f.result.grid().get(), 1));
        Assert.assertEquals("编辑提交只走 selectionCommit 一次", 1, f.commits.size());
        Assert.assertEquals("b", f.commits.get(0).candidateKey());
        Assert.assertEquals("编辑态不得重新武装新增", 0, f.beginAdds.get());
        Assert.assertEquals("提交成功后请求关闭（不留在面板）", 1, f.closeRequests.get());
        Assert.assertFalse(f.result.open().get().booleanValue());
        Assert.assertEquals("提交后原位替换，而不是追加成第二个成员",
                Collections.singletonList("b"), f.raws);
    }

    /** 带变体成员以编辑态打开：初值 = 该成员既有选择模式 + 变体 key（与点击其卡片 [编辑] 一致）。 */
    @Test
    public void openEdgeRestoresVariantStateOfEditedMember() {
        Fixture f = new Fixture(Arrays.asList(candidateWithVariants("a", "v1", "v2"), candidate("b")),
                Arrays.asList(variantMember(0L, "a", SearchPickerData.SelectionMode.SELECTED,
                        Collections.singletonList("v2"))), true);
        f.editRequest.set(Long.valueOf(0L));
        openPanel(f);

        Assert.assertEquals(Collections.singletonList(Long.valueOf(0L)), f.edits);
        Assert.assertTrue("带变体成员预开变体浮层",
                f.result.variantsOpen().get().booleanValue());
        Assert.assertEquals("初值 = 成员既有选择模式", SearchPickerData.SelectionMode.SELECTED,
                f.result.variantMode().get());
        Assert.assertEquals("初值 = 成员既有变体 key", Collections.singletonList("v2"),
                f.result.variantKeys().get());
        Assert.assertNotNull("浮层持有该成员候选本体", f.result.activeCandidate().get());
    }

    // ==================== ③ 取消：数据零变化 ====================

    /** ESC/dismiss 链路：变体浮层 ESC 只退回主面板；主面板 ESC 先 onCancel 再关闭，成员数据零变化。 */
    @Test
    public void escapeDuringRequestedEditCancelsWithoutChangingMemberData() {
        Fixture f = new Fixture(Arrays.asList(candidateWithVariants("a", "v1", "v2")),
                Arrays.asList(variantMember(0L, "a", SearchPickerData.SelectionMode.SELECTED,
                        Collections.singletonList("v1"))), true);
        f.editRequest.set(Long.valueOf(0L));
        openPanel(f);
        Assert.assertTrue(f.result.variantsOpen().get().booleanValue());

        pressKey(SceneKey.ESCAPE);
        Assert.assertFalse("第一下 ESC 只收起变体浮层",
                f.result.variantsOpen().get().booleanValue());
        Assert.assertEquals("退回主面板不算取消", 0, f.cancels.get());

        pressKey(SceneKey.ESCAPE);
        Assert.assertEquals("ESC 应先走 onCancel", 1, f.cancels.get());
        Assert.assertEquals("ESC 应请求关闭", 1, f.closeRequests.get());
        Assert.assertTrue("取消不产生提交", f.commits.isEmpty());
        Assert.assertEquals("取消不改变成员数据", Collections.singletonList("a"), f.raws);
        Assert.assertNull("取消后不残留编辑目标", f.editTarget.get());
    }

    // ==================== 打开期间请求变化：换目标 ====================

    @Test
    public void requestChangeWhileOpenSwitchesEditTargetAndResetsTransientState() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidateWithVariants("c", "v1", "v2")),
                Arrays.asList(member(0L, "a"),
                        variantMember(1L, "c", SearchPickerData.SelectionMode.ALL,
                                Collections.<String>emptyList())), true);
        openPanel(f);
        Assert.assertTrue("未写请求：普通打开不进编辑态", f.edits.isEmpty());

        f.editRequest.set(Long.valueOf(1L));
        rt.flush();
        Assert.assertEquals("打开期间请求变化即进入编辑态",
                Collections.singletonList(Long.valueOf(1L)), f.edits);
        Assert.assertTrue("切换到带变体成员预开浮层",
                f.result.variantsOpen().get().booleanValue());

        f.editRequest.set(Long.valueOf(0L));
        rt.flush();
        Assert.assertEquals("换目标即换编辑态（同一通路，不叠加第二份状态）",
                Arrays.asList(Long.valueOf(1L), Long.valueOf(0L)), f.edits);
        Assert.assertFalse("切到无变体成员收起变体浮层",
                f.result.variantsOpen().get().booleanValue());
        Assert.assertNull("切到无变体成员清空浮层候选", f.result.activeCandidate().get());
    }

    // ==================== ④ 非法 memberId：无操作 ====================

    /** 未命中成员 = 无操作：不进编辑态、不回调、不回写请求信号，面板保持普通（隐式新增）语义。 */
    @Test
    public void unknownMemberIdIsNoOpAndPanelKeepsAddSemantics() {
        Fixture f = new Fixture(Arrays.asList(candidate("a"), candidate("b")),
                Arrays.asList(member(0L, "a")), true);
        f.editRequest.set(Long.valueOf(999L));
        openPanel(f);

        Assert.assertTrue("非法 id 不进入编辑态（不回调 onEditCurrent）", f.edits.isEmpty());
        Assert.assertFalse(f.result.variantsOpen().get().booleanValue());
        Assert.assertEquals("面板不回写宿主请求信号", Long.valueOf(999L), f.editRequest.get());

        click(gridCell(f.result.grid().get(), 1));
        Assert.assertEquals("未进编辑态：点击候选仍隐式武装新增（隐式 + 重新武装）",
                2, f.beginAdds.get());
        Assert.assertEquals("未进编辑态：提交按追加落地（宿主的编辑目标未被武装）",
                Arrays.asList("a", "b"), f.raws);
        Assert.assertEquals("新增后留在面板", 0, f.closeRequests.get());
        Assert.assertTrue(f.result.open().get().booleanValue());
    }

    // ==================== 打开边沿 = 每次打开都按当前请求值生效 ====================

    /** 请求值不变也能在每次打开边沿重新生效（宿主无需为「再次编辑同一成员」复位信号）。 */
    @Test
    public void sameRequestValueAppliesAgainOnEachOpenEdge() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), Arrays.asList(member(0L, "a")), true);
        f.editRequest.set(Long.valueOf(0L));
        rt.flush();
        Assert.assertTrue("未打开：请求不生效", f.edits.isEmpty());

        openPanel(f);
        Assert.assertEquals(Collections.singletonList(Long.valueOf(0L)), f.edits);
        click(gridCell(f.result.grid().get(), 0));
        Assert.assertEquals(1, f.commits.size());
        Assert.assertFalse(f.result.open().get().booleanValue());

        openPanel(f);
        Assert.assertEquals("同值请求随新的打开边沿再次生效",
                Arrays.asList(Long.valueOf(0L), Long.valueOf(0L)), f.edits);
    }

    // ==================== 未接线：零行为 ====================

    /** 不接线 = Props 读回 null，面板行为与接线前逐值一致（点击候选即隐式新增并留在面板）。 */
    @Test
    public void unwiredRequestIsInertAndAddSemanticsStayUnchanged() {
        Fixture f = new Fixture(Arrays.asList(candidate("a")), Arrays.asList(member(0L, "a")), false);
        Assert.assertNull("未接线：Props 读回 null", f.props.memberEditRequest());
        openPanel(f);
        Assert.assertTrue(f.edits.isEmpty());

        click(gridCell(f.result.grid().get(), 0));
        Assert.assertEquals("未接线：点击候选仍隐式新增并重新武装", 2, f.beginAdds.get());
        Assert.assertEquals(0, f.closeRequests.get());
        Assert.assertTrue(f.result.open().get().booleanValue());
    }
}
