package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.runtime.ValidationResult;
import club.heiqi.config.schema.Values;
import club.heiqi.config.ui.editor.Codec;
import club.heiqi.config.ui.editor.ListMemberCodec;
import club.heiqi.config.ui.editor.CurrentValuePresenter;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.config.schema.SearchPickerSpec;
import club.heiqi.config.schema.StructuredListSpec;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.field.StructuredListFieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** 结构化列表默认 scene renderer 的 headless 装配回归。 */
public class StructuredListFieldRendererTest {

    private SceneInteractionHarness harness;
    private SceneRuntime runtime;
    private DraftSignalAdapter adapter;
    private SceneNode sceneRoot;
    private MountHandle mountHandle;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
    }

    @After
    public void tearDown() {
        if (adapter != null) adapter.dispose();
        if (mountHandle != null) mountHandle.dispose();
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    @Test
    public void defaultRendererMountsStructuredListControls() throws Exception {
        ConfigSchema schema = ConfigSchema.builder("test")
                .section("general")
                .structuredList("rules", Values.object(
                        Values.member("id", Values.string()),
                        Values.member("members", Values.list(Values.string()))))
                .build()
                .endSection()
                .build();
        File file = File.createTempFile("structured-list-renderer-", ".yaml");
        write(file, "general:\n  rules:\n    - id: first\n      members:\n        - alpha\n");
        Authority authority = Authority.load(file, schema);
        DraftBuffer draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        FieldSpec spec = schema.field("general.rules");

        SceneNode card = new StructuredListFieldRenderer().render(runtime, spec, adapter);
        SceneNode root = new SceneNode();
        root.appendChild(card);
        runtime.flush();
        runtime.flush();
        harness.mountRoot(root, 640, 420);

        assertTrue("结构化列表应有滚动视口", containsScrollable(card));
        assertEquals("StructuredList 应使用专用 320px 视口", 320, box(findScrollable(card)).getHeight());
        assertTrue("应有添加按钮", containsText(card, "添加"));
        assertTrue("应有上移按钮", containsText(card, "↑"));
        assertTrue("应有下移按钮", containsText(card, "↓"));
        assertTrue("应有删除按钮", containsText(card, "删除"));
        assertTrue("应渲染 id member", containsText(card, "id"));
        assertTrue("应渲染 members member", containsText(card, "members"));
        assertNotNull("应保留字段卡片", card);
    }

    @Test
    public void nestedMemberErrorIsVisibleAndRecomputedAfterReorder() throws Exception {
        SceneNode card = mountRenderer("general:\n  rules:\n    - id: first\n      members:\n        - alpha\n    - id: second\n      members:\n        - beta\n");
        java.util.LinkedHashMap<String, String> initialErrors =
                new java.util.LinkedHashMap<String, String>();
        initialErrors.put("general.rules[1].members[1]", "second member blocked");
        adapter.setSubmitValidation(ValidationResult.of(initialErrors));
        runtime.flush();
        assertEquals("second member blocked", memberError(rowAt(card, 1), "members"));
        assertEquals("", memberError(rowAt(card, 0), "members"));

        SceneNode first = rowAt(card, 0);
        SceneNode second = rowAt(card, 1);
        SceneNode moveDown = findButton(first, "↓");
        harness.click(moveDown);
        runtime.flush();
        harness.mountRoot(sceneRoot, 640, 420);
        assertEquals(Arrays.asList("second", "first"),
                Arrays.asList(listValue().get(0).get("id"), listValue().get(1).get("id")));
        assertSame("排序应复用内部 keyed 行节点", second, rowAt(card, 0));
        assertSame(first, rowAt(card, 1));

        java.util.LinkedHashMap<String, String> reorderedErrors =
                new java.util.LinkedHashMap<String, String>();
        reorderedErrors.put("general.rules[0].members[1]", "now first");
        reorderedErrors.put("general.rules[1].members[1]", "now second");
        adapter.setSubmitValidation(ValidationResult.of(reorderedErrors));
        runtime.flush();
        assertEquals("now first", memberError(rowAt(card, 0), "members"));
        assertEquals("now second", memberError(rowAt(card, 1), "members"));
    }

    @Test
    public void memberErrorFollowsRowsAcrossDeleteInsertIndexShiftAndClear() throws Exception {
        SceneNode card = mountRenderer("general:\n  rules:\n    - id: first\n      members:\n        - alpha\n    - id: broken\n      members:\n        - beta\n    - id: third\n      members:\n        - gamma\n");
        setErrors("general.rules[1].members[1]", "broken member");
        assertEquals("broken member", memberError(rowAt(card, 1), "members"));
        assertEquals("", memberError(rowAt(card, 0), "members"));
        assertEquals("", memberError(rowAt(card, 2), "members"));

        // 删除带错行后，真实编辑路径会清空旧提交错误，不能把 members[1] 黏到 third。
        runtime.requestFocus(findButton(rowAt(card, 1), "删除"));
        harness.pressKey(SceneKey.ENTER);
        runtime.flush();
        assertEquals(Arrays.asList("first", "third"), ids(listValue()));
        assertEquals("", memberError(rowAt(card, 0), "members"));
        assertEquals("", memberError(rowAt(card, 1), "members"));

        // 前插通过 draft 写回模拟外部 reset/reload 的完整值投影，再经 renderer 的动态 path 取错。
        java.util.ArrayList<Map<String, Object>> withPrefix = new java.util.ArrayList<Map<String, Object>>();
        withPrefix.add(rule("prefix", "prefix-member"));
        withPrefix.addAll(listValue());
        adapter.onFieldEdit("general.rules", withPrefix);
        runtime.flush();
        harness.mountRoot(sceneRoot, 640, 420);
        setErrors("general.rules[2].members[1]", "third member");
        assertEquals("", memberError(rowAt(card, 0), "members"));
        assertEquals("", memberError(rowAt(card, 1), "members"));
        assertEquals("third member", memberError(rowAt(card, 2), "members"));

        // 删除错误行之前的 prefix，third 从 index 2 平移到 index 1，错误随当前元素重定位。
        harness.click(findButton(rowAt(card, 0), "删除"));
        runtime.flush();
        assertEquals(Arrays.asList("first", "third"), ids(listValue()));
        assertEquals("", memberError(rowAt(card, 0), "members"));
        assertEquals("", memberError(rowAt(card, 1), "members"));
        setErrors("general.rules[1].members[1]", "third after shift");
        assertEquals("third after shift", memberError(rowAt(card, 1), "members"));
        assertEquals("", memberError(rowAt(card, 0), "members"));

        adapter.setSubmitValidation(ValidationResult.ok());
        runtime.flush();
        assertEquals("", memberError(rowAt(card, 0), "members"));
        assertEquals("", memberError(rowAt(card, 1), "members"));
    }

    @Test
    public void rendererAddDeleteEditAndResetUseDraftTransaction() throws Exception {
        SceneNode card = mountRenderer("general:\n  rules:\n    - id: first\n      members:\n        - alpha\n");
        harness.click(structuredAddButton(card));
        runtime.flush();
        assertEquals(2, listValue().size());

        harness.mountRoot(sceneRoot, 640, 420);
        harness.click(findButton(rowAt(card, 1), "删除"));
        runtime.flush();
        assertEquals(1, listValue().size());

        harness.mountRoot(sceneRoot, 640, 420);
        SceneNode idInput = memberControl(rowAt(card, 0), "id");
        harness.click(idInput);
        harness.pressKey(SceneKey.END);
        harness.typeText("-edited");
        runtime.flush();
        assertEquals("first-edited", listValue().get(0).get("id"));

        adapter.resetToCurrent();
        runtime.flush();
        assertEquals("first", listValue().get(0).get("id"));
    }

    @Test
    public void identityDeleteToEmptyAndRetypeKeepsRowInputFocusAndDraftAcrossFrames() throws Exception {
        SceneNode card = mountRenderer("general:\n  rules:\n    - id: first\n      members:\n        - alpha\n");
        SceneNode row = rowAt(card, 0);
        SceneNode idInput = memberControl(row, "id");
        runtime.requestFocus(idInput);
        assertSame(idInput, runtime.getFocusedNode());

        harness.pressKey(SceneKey.END);
        for (String expected : Arrays.asList("firs", "fir", "fi", "f", "")) {
            harness.pressKey(SceneKey.BACKSPACE);
            assertSame("逐帧退格不得重建 keyed row", row, rowAt(card, 0));
            assertSame("逐帧退格不得重建 identity input", idInput, memberControl(rowAt(card, 0), "id"));
            assertSame("逐帧退格不得丢失焦点", idInput, runtime.getFocusedNode());
            assertEquals("逐帧退格必须写入 Draft", expected, listValue().get(0).get("id"));
        }
        String expected = "";
        for (String character : Arrays.asList("a", "s", "c", "i", "i")) {
            harness.typeText(character);
            expected += character;
            assertSame("逐字符输入不得重建 keyed row", row, rowAt(card, 0));
            assertSame("逐字符输入不得重建 identity input", idInput, memberControl(rowAt(card, 0), "id"));
            assertSame("逐字符输入不得丢失焦点", idInput, runtime.getFocusedNode());
            assertEquals("逐字符输入必须写入 Draft", expected, listValue().get(0).get("id"));
        }
        assertEquals("ascii", listValue().get(0).get("id"));

        adapter.resetToCurrent();
        runtime.flush();
        harness.mountRoot(sceneRoot, 640, 420);
        assertEquals("first", listValue().get(0).get("id"));
        assertSame("reset 恢复旧 identity 仍复用 row", row, rowAt(card, 0));
        assertSame("reset 恢复旧 identity 仍复用 input", idInput, memberControl(rowAt(card, 0), "id"));
        assertSame("reset 恢复旧 identity 不猜测 refocus，原焦点仍在原节点", idInput,
                runtime.getFocusedNode());
    }

    /** 标题响应 identity、排序和 reset，且不泄漏内部 key 或重建行内输入。 */
    @Test
    public void rowHeadersReactToMoveEditAndResetWithoutReplacingNodes() throws Exception {
        SceneNode card = mountRenderer("general:\n  rules:\n    - id: first\n      members:\n        - alpha\n"
                + "    - id: second\n      members:\n        - beta\n");
        SceneNode firstRow = rowAt(card, 0);
        SceneNode secondRow = rowAt(card, 1);
        SceneNode firstInput = memberControl(firstRow, "id");
        assertEquals("first", rowHeader(firstRow));
        assertEquals("second", rowHeader(secondRow));

        harness.click(findButton(firstRow, "↓"));
        runtime.flush();
        assertSame(secondRow, rowAt(card, 0));
        assertSame(firstRow, rowAt(card, 1));
        assertSame(firstInput, memberControl(rowAt(card, 1), "id"));
        assertEquals("first", rowHeader(rowAt(card, 1)));

        runtime.requestFocus(firstInput);
        harness.pressKey(SceneKey.END);
        for (int i = 0; i < 5; i++) harness.pressKey(SceneKey.BACKSPACE);
        runtime.flush();
        assertEquals("第 2 项", rowHeader(rowAt(card, 1)));
        assertSame(firstRow, rowAt(card, 1));
        assertSame(firstInput, memberControl(rowAt(card, 1), "id"));
        assertSame(firstInput, runtime.getFocusedNode());

        adapter.resetToCurrent();
        runtime.flush();
        assertSame(firstRow, rowAt(card, 0));
        assertSame(firstInput, memberControl(rowAt(card, 0), "id"));
        assertEquals("first", rowHeader(rowAt(card, 0)));
        assertSame(firstInput, runtime.getFocusedNode());
    }

    /** 长 identity 只能占用标题剩余槽，不得挤动或覆盖固定操作按钮。 */
    @Test
    public void longIdentityHeaderUsesRemainingWidthWithoutReplacingKeyedControls() throws Exception {
        String longId = "namespace:machine_with_a_long_identity_that_must_be_clipped_inside_the_title_slot";
        SceneNode card = mountRenderer("general:\n  rules:\n    - id: " + longId
                + "\n      members:\n        - alpha\n");
        SceneNode row = rowAt(card, 0);
        SceneNode header = row.__getChildren().get(0);
        SceneNode titleSlot = header.__getChildren().get(0);
        SceneNode expand = findButton(row, "展开");
        SceneNode up = findButton(row, "↑");
        SceneNode down = findButton(row, "↓");
        SceneNode delete = findButton(row, "删除");
        int[] wideButtonWidths = buttonWidths(expand, up, down, delete);

        assertHeaderLayout(row, 640, true);
        assertEquals("长标题在宽屏应使用 260px 上限", 260, box(titleSlot).getWidth());
        assertSame(titleSlot, header.__getChildren().get(0));
        assertTrue("标题槽必须裁剪超长 identity", titleSlot.isClipChildren());

        assertHeaderLayout(row, 360, false);
        assertEquals(Arrays.toString(wideButtonWidths), Arrays.toString(buttonWidths(expand, up, down, delete)));

        assertHeaderLayout(row, 960, true);

        SceneNode idInput = memberControl(row, "id");
        runtime.requestFocus(idInput);
        harness.pressKey(SceneKey.END);
        harness.typeText("_even_longer_after_identity_update");
        runtime.flush();
        harness.mountRoot(sceneRoot, 360, 420);

        assertSame("identity 更新不得替换 keyed row", row, rowAt(card, 0));
        assertSame("identity 更新不得替换标题槽", titleSlot, header.__getChildren().get(0));
        assertSame(expand, findButton(row, "展开"));
        assertSame(up, findButton(row, "↑"));
        assertSame(down, findButton(row, "↓"));
        assertSame(delete, findButton(row, "删除"));
        assertHeaderLayout(row, 360, false);
    }

    /** 短标题在宽屏保持自然上限，按钮紧随标题，剩余空间留在 header 右侧。 */
    @Test
    public void shortIdentityHeaderKeepsButtonsNearTitleAcrossWidths() throws Exception {
        SceneNode card = mountRenderer("general:\n  rules:\n    - id: short\n      members:\n        - alpha\n");
        SceneNode row = rowAt(card, 0);
        for (int width : new int[] {360, 640, 960}) assertHeaderLayout(row, width, true);
    }

    /** 320px 是首选高度；外层窗口更短时由外层约束裁剪承载区。 */
    @Test
    public void structuredViewportPrefers320ButRespectsShortOuterConstraint() throws Exception {
        SceneNode card = mountRenderer("general:\n  rules:\n    - id: first\n      members:\n        - alpha\n");
        SceneNode viewport = findScrollable(card);
        assertEquals(320, box(viewport).getHeight());
        SceneNode outer = SceneNode.column();
        outer.setScrollable(true);
        outer.setClipChildren(true);
        outer.setPreferredHeight(220);
        outer.appendChild(sceneRoot);
        harness.mountRoot(outer, 640, 220);
        assertEquals("短窗口外层必须受 220px 约束", 220, box(outer).getHeight());
        assertEquals("外层收紧不得改写 StructuredList 专用首选高度", 320, box(viewport).getHeight());
    }

    /** 字段级 640px 视口只作用于声明字段，同屏旧字段继续保持 320px。 */
    @Test
    public void structuredViewportMetadataIsFieldLocal() throws Exception {
        ValueSpec object = Values.objectWithIdentity("id", Values.member("id", Values.string()));
        ConfigSchema schema = ConfigSchema.builder("test")
                .section("general")
                .structuredList("tall", object, new StructuredListSpec(640)).build()
                .structuredList("legacy", object).build()
                .endSection()
                .build();
        File file = File.createTempFile("structured-list-field-local-height-", ".yaml");
        write(file, "general:\n  tall: []\n  legacy: []\n");
        adapter = new DraftSignalAdapter(runtime, DraftBuffer.from(Authority.load(file, schema)));
        sceneRoot = SceneNode.column();
        final SceneNode[] cards = new SceneNode[2];
        mountHandle = runtime.mount(sceneRoot, () -> {
            SceneNode fields = SceneNode.column();
            cards[0] = new StructuredListFieldRenderer().render(
                    runtime, schema.field("general.tall"), adapter);
            cards[1] = new StructuredListFieldRenderer().render(
                    runtime, schema.field("general.legacy"), adapter);
            fields.appendChild(cards[0]);
            fields.appendChild(cards[1]);
            return fields;
        });
        runtime.flush();
        runtime.flush();
        harness.mountRoot(sceneRoot, 640, 1200);

        assertEquals("显式字段应使用 640px 视口", 640, box(findScrollable(cards[0])).getHeight());
        assertEquals("同屏旧字段不得被全局放大", 320, box(findScrollable(cards[1])).getHeight());
    }

    /** member 表单使用显示元数据，并在字体度量与窄视口变化后保持纵向不相交。 */
    @Test
    public void memberFormUsesDisplayMetadataAndStaysInsideNarrowViewportAcrossFontMetrics() throws Exception {
        assertResponsiveMemberForm(new FixedTextMeasurer(8, 16), 240,
                "最小剩余耐久度（低于该值时停止执行）", "使用逻辑像素布局，不改变 YAML key");
        disposeMountedState();
        harness.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(13, 20));
        runtime = harness.getRuntime();
        assertResponsiveMemberForm(new FixedTextMeasurer(13, 20), 240,
                "最小剩余耐久度（低于该值时停止执行）", "使用逻辑像素布局，不改变 YAML key");
        disposeMountedState();
        harness.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        runtime = harness.getRuntime();
        assertResponsiveMemberForm(new FixedTextMeasurer(8, 16), 240,
                "minimumRemainingDurability", "minimumRemainingDurability helper text");
        disposeMountedState();
        harness.dispose();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(13, 20));
        runtime = harness.getRuntime();
        assertResponsiveMemberForm(new FixedTextMeasurer(13, 20), 240,
                "minimumRemainingDurability", "minimumRemainingDurability helper text");
    }

    @Test
    public void choiceListRendersInStableOrderAndSupportsControlledMouseKeyboardResetReload() throws Exception {
        SceneNode card = mountChoiceRenderer("general:\n  rules:\n    - id: first\n      modes:\n        - beta\n");
        adapter.onFieldEdit("general.rules", Arrays.asList(choiceRule("first", "beta", "removed")));
        runtime.flush();
        harness.mountRoot(sceneRoot, 640, 420);
        SceneNode row = rowAt(card, 0);
        SceneNode choices = memberControl(row, "modes");
        assertEquals(Arrays.asList("alpha", "beta", "removed（已失效）"), directLabels(choices));
        SceneNode alpha = choices.__getChildren().get(0);
        SceneNode beta = choices.__getChildren().get(1);
        SceneNode removed = choices.__getChildren().get(2);

        harness.click(alpha);
        runtime.flush();
        assertEquals(Arrays.asList("alpha", "beta", "removed"), modes());
        assertSame("choice 编辑不得重建 keyed row", row, rowAt(card, 0));
        assertSame("受控 checkbox 应保持节点 identity", alpha,
                memberControl(rowAt(card, 0), "modes").__getChildren().get(0));

        runtime.requestFocus(beta);
        harness.pressKey(SceneKey.SPACE);
        runtime.flush();
        assertEquals(Arrays.asList("alpha", "removed"), modes());
        assertSame(beta, runtime.getFocusedNode());
        harness.pressKey(SceneKey.ENTER);
        runtime.flush();
        assertEquals(Arrays.asList("alpha", "beta", "removed"), modes());
        assertSame(beta, runtime.getFocusedNode());

        harness.mountRoot(sceneRoot, 640, 420);
        harness.click(removed);
        runtime.flush();
        assertEquals(Arrays.asList("alpha", "beta"), modes());
        assertEquals(Arrays.asList("alpha", "beta"), directLabels(memberControl(rowAt(card, 0), "modes")));

        adapter.resetToCurrent();
        runtime.flush();
        assertEquals(Arrays.asList("beta"), modes());
        assertSame("reset 应复用 row", row, rowAt(card, 0));
        adapter.onFieldEdit("general.rules", Arrays.asList(choiceRule("first", "alpha")));
        runtime.flush();
        assertEquals(Arrays.asList("alpha"), modes());
        assertSame("reload 投影应复用 row", row, rowAt(card, 0));
    }

    @Test
    public void listStringMemberKeepsSimpleListRenderer() throws Exception {
        SceneNode card = mountRenderer("general:\n  rules:\n    - id: first\n      members:\n        - alpha\n");
        SceneNode control = memberControl(rowAt(card, 0), "members");
        assertTrue("LIST<STRING> 仍应渲染原 SceneSimpleList 控件", containsText(control, "alpha"));
        assertEquals("无 picker 时编辑区仍应是唯一 column", 1, control.__getChildren().size());
    }

    @Test
    public void listStringMemberShowsRawAndPickerWithoutCrossRowStateOrIdentityLoss() throws Exception {
        SceneNode card = mountPickerRenderer("general:\n  rules:\n    - id: first\n      members:\n        - alpha\n"
                + "    - id: second\n      members:\n        - beta\n");
        SceneNode firstRow = rowAt(card, 0);
        SceneNode secondRow = rowAt(card, 1);
        SceneNode firstEditor = memberControl(firstRow, "members");
        SceneNode secondEditor = memberControl(secondRow, "members");
        assertEquals("member ROW 应只保留 label 与唯一编辑 column", 2,
                memberRow(firstRow, "members").__getChildren().size());
        assertEquals("raw 与 picker 应在编辑 column 纵向并存", 2, firstEditor.__getChildren().size());
        assertTrue(containsText(firstEditor.__getChildren().get(0), "alpha"));
        assertTrue(containsText(secondEditor.__getChildren().get(0), "beta"));

        SceneNode firstRaw = firstEditor.__getChildren().get(0);
        SceneNode firstPicker = firstEditor.__getChildren().get(1);
        assertVisibleInsideViewport(firstRaw, 640);
        assertVisibleInsideViewport(firstPicker, 640);
        harness.mountRoot(sceneRoot, 360, 420);
        assertVisibleInsideViewport(firstRaw, 360);
        assertVisibleInsideViewport(firstPicker, 360);
        SceneNode firstIdInput = memberControl(firstRow, "id");
        runtime.requestFocus(firstIdInput);
        selectPickerCandidate(secondEditor.__getChildren().get(1), 360);
        selectPickerCandidate(firstPicker, 360);
        assertEquals(Arrays.asList("alpha", "picked-alpha"), membersAt(0));
        assertEquals(Arrays.asList("beta", "picked-beta"), membersAt(1));
        assertTrue("picker 写回完整 List 后 raw 应在同次 flush 显示 canonical 项",
                containsText(firstRaw, "picked-alpha"));
        assertSame("picker 写回不得重建 keyed row", firstRow, rowAt(card, 0));
        assertSame("picker 写回不得重建 raw 控件", firstRaw,
                memberControl(rowAt(card, 0), "members").__getChildren().get(0));
        runtime.requestFocus(firstIdInput);
        runtime.flush();
        assertSame("canonical flush 后 keyed row 输入应保持 focus", firstIdInput, runtime.getFocusedNode());

        assertNotNull("picker 追加后 raw 添加入口仍应存在", findButton(firstRaw, "添加"));
        assertNotNull("picker 追加后 raw 删除入口仍应存在", findButton(firstRaw, "×"));
        assertSame(firstRow, rowAt(card, 0));
    }

    /** adapter 拒绝提交时 rows/items/editing/query 零写，重试成功后仍按稳定 id 精确替换。 */
    @Test
    public void listMembersPickerReplacesOnlyClickedDuplicateMember() throws Exception {
        ConfigSchema schema = ConfigSchema.builder("test").section("general")
                .structuredList("rules", Values.objectWithIdentity("id",
                        Values.member("id", Values.string()),
                        Values.member("members", Values.widget(Values.list(Values.string()),
                                Values.searchPicker("test:list-members", 8,
                                        SearchPickerSpec.BindingMode.LIST_MEMBERS)))))
                .build().endSection().build();
        File file = File.createTempFile("structured-list-members-picker-", ".yaml");
        write(file, "general:\n  rules:\n    - id: first\n      members:\n        - same\n        - same\n");
        adapter = new DraftSignalAdapter(runtime, DraftBuffer.from(Authority.load(file, schema)));
        sceneRoot = new SceneNode();
        mountHandle = runtime.mount(sceneRoot, () -> new StructuredListFieldRenderer(listMemberRegistry())
                .render(runtime, schema.field("general.rules"), adapter));
        runtime.flush(); runtime.flush(); harness.mountRoot(sceneRoot, 640, 420);
        SceneNode editor = memberControl(rowAt(mountHandle.getRoot(), 0), "members");
        SceneNode picker = editor.__getChildren().get(0);
        SceneNode advanced = editor.__getChildren().get(1);
        assertTrue(directTexts(editor).contains("Configured 2 items"));
        assertTrue(directTexts(editor).contains("Manage"));
        assertTrue(directTexts(editor).contains("Advanced: edit raw values"));
        assertFalse("默认折叠时不得常驻可见 raw 行", directTexts(editor).contains("same"));
        List<Object> beforeToggle = new java.util.ArrayList<Object>(membersAt(0));
        harness.click(advanced); runtime.flush();
        assertTrue("展开后应出现既有 raw editor", directTexts(editor).contains("same"));
        assertEquals("展开 raw 必须零 Draft 写", beforeToggle, membersAt(0));
        assertSame("展开 raw 不得重建 picker", picker, editor.__getChildren().get(0));
        harness.mountRoot(sceneRoot, 640, 420);
        harness.click(advanced); runtime.flush();
        assertFalse("再次折叠后 raw 行应不可见", directTexts(editor).contains("same"));
        assertEquals("折叠 raw 必须零 Draft 写", beforeToggle, membersAt(0));

        harness.mountRoot(sceneRoot, 640, 420);
        harness.click(findButton(picker, "Manage")); runtime.flush();
        SceneNode panel = panelRoot();
        layoutPanel(panel, 1000, 700);
        // 成员行容器：membersPanel[1]=rowsHost → [0]=viewport → [0]=content
        SceneNode memberRows = panel.__getChildren().get(2).__getChildren().get(1)
                .__getChildren().get(0).__getChildren().get(0);
        harness.click(memberAction(memberRows.__getChildren().get(1), 0));
        runtime.flush();
        SceneNode input = panel.__getChildren().get(0).__getChildren().get(1);
        runtime.requestFocus(input); runtime.flush(); harness.typeText("draft"); runtime.flush();
        layoutPanel(panel, 1000, 700);
        AtomicReference<Throwable> workerFailure = new AtomicReference<Throwable>();
        final SceneNode failedPanel = panel;
        Thread wrongOwner = new Thread(() -> {
            try { harness.click(gridCell(failedPanel, 0)); }
            catch (Throwable failure) { workerFailure.set(failure); }
        }, "adapter-wrong-owner");
        wrongOwner.start(); wrongOwner.join(); runtime.flush();
        assertEquals(null, workerFailure.get());
        assertEquals(Arrays.asList("same", "same"), membersAt(0));
        assertTrue(containsText(input, "draft"));
        assertTrue(containsText(panel, "Unable to save the selected value"));
        assertEquals(1, runtime.getOverlayHost().size());

        panel = panelRoot();
        layoutPanel(panel, 1000, 700);
        harness.click(gridCell(panel, 0));
        runtime.flush();
        assertEquals(Arrays.asList("same", "picked"), membersAt(0));
        assertTrue(runtime.getOverlayHost().isEmpty());
    }

    /** 两个重复成员删除第一项后，必须保留原第二项的 keyed 身份。 */
    @Test
    public void listMembersPickerDeletingFirstDuplicateKeepsSecondIdentity() throws Exception {
        SceneNode rows = openDuplicateMemberPicker(2);
        SceneNode second = rows.__getChildren().get(1);

        confirmMemberDelete(rows.__getChildren().get(0));

        assertEquals(Collections.singletonList("same"), membersAt(0));
        assertSame("删除第一项不得把第一项 id 转嫁给幸存项", second, rows.__getChildren().get(0));
    }

    /** 两个重复成员删除第二项后，必须保留原第一项的 keyed 身份。 */
    @Test
    public void listMembersPickerDeletingSecondDuplicateKeepsFirstIdentity() throws Exception {
        SceneNode rows = openDuplicateMemberPicker(2);
        SceneNode first = rows.__getChildren().get(0);

        confirmMemberDelete(rows.__getChildren().get(1));

        assertEquals(Collections.singletonList("same"), membersAt(0));
        assertSame("删除第二项不得替换第一项 id", first, rows.__getChildren().get(0));
    }

    /** 三个重复成员删除中间项后，两侧成员都保持各自 keyed 身份。 */
    @Test
    public void listMembersPickerDeletingMiddleDuplicateKeepsSurvivorIdentities() throws Exception {
        SceneNode rows = openDuplicateMemberPicker(3);
        SceneNode first = rows.__getChildren().get(0);
        SceneNode third = rows.__getChildren().get(2);

        confirmMemberDelete(rows.__getChildren().get(1));

        assertEquals(Arrays.asList("same", "same"), membersAt(0));
        assertSame(first, rows.__getChildren().get(0));
        assertSame(third, rows.__getChildren().get(1));
    }

    /** 删除提交被 owner-thread 契约拒绝时，raw、派生行身份与确认态均零推进。 */
    @Test
    public void listMembersPickerRejectedDuplicateDeleteLeavesEveryIdentityUntouched() throws Exception {
        SceneNode rows = openDuplicateMemberPicker(2);
        SceneNode first = rows.__getChildren().get(0);
        SceneNode second = rows.__getChildren().get(1);
        enterMemberDeleteConfirmation(first);
        AtomicReference<Throwable> workerFailure = new AtomicReference<Throwable>();
        Thread wrongOwner = new Thread(() -> {
            try { harness.click(memberAction(first, 1)); }
            catch (Throwable failure) { workerFailure.set(failure); }
        }, "adapter-wrong-owner-delete");

        wrongOwner.start();
        wrongOwner.join();
        runtime.flush();

        assertEquals(null, workerFailure.get());
        assertEquals(Arrays.asList("same", "same"), membersAt(0));
        assertSame(first, rows.__getChildren().get(0));
        assertSame(second, rows.__getChildren().get(1));
        assertEquals(Arrays.asList("Cancel", "Confirm remove"), directTexts(visibleMemberActions(first)));
    }

    /** CurrentValuePresenter 图片由 UILib 通用节点渲染，并随值更新或缺图清空。 */
    @Test
    public void currentValuePresentationRendersOptionalImageAndUpdatesWithValue() throws Exception {
        final SceneImageSource image = new SceneImageSource() { };
        SceneNode card = mountPickerRendererWithPresenter(
                "general:\n  rules:\n    - id: first\n      members:\n        - alpha\n", image);
        SceneNode imageNode = findImageNode(rowAt(card, 0), image);
        SceneNode presentation = imageNode.__getParent();
        assertSame(image, imageNode.getImageSource());
        assertTrue(containsText(presentation, "alpha"));

        adapter.onFieldEdit("general.rules", Arrays.asList(rule("first", "no-image")));
        runtime.flush();
        assertEquals(null, imageNode.getImageSource());
        assertTrue("值更新后 title/summary 应同步刷新", containsText(presentation, "no-image"));

        adapter.onFieldEdit("general.rules", Arrays.asList(rule("first", "updated")));
        runtime.flush();
        assertSame("后续有图值应复用展示节点并恢复图片", image, imageNode.getImageSource());
        assertTrue(containsText(presentation, "updated"));
    }

    private SceneNode mountRenderer(String yaml) throws Exception {
        ConfigSchema schema = ConfigSchema.builder("test")
                .section("general")
                .structuredList("rules", Values.objectWithIdentity("id",
                        Values.member("id", Values.string()),
                        Values.member("members", Values.list(Values.string()))))
                .build()
                .endSection()
                .build();
        File file = File.createTempFile("structured-list-renderer-test-", ".yaml");
        write(file, yaml);
        Authority authority = Authority.load(file, schema);
        adapter = new DraftSignalAdapter(runtime, DraftBuffer.from(authority));
        sceneRoot = new SceneNode();
        final FieldSpec field = schema.field("general.rules");
        mountHandle = runtime.mount(sceneRoot,
                () -> new StructuredListFieldRenderer().render(runtime, field, adapter));
        SceneNode card = mountHandle.getRoot();
        runtime.flush();
        runtime.flush();
        harness.mountRoot(sceneRoot, 640, 420);
        return card;
    }

    private SceneNode openDuplicateMemberPicker(int count) throws Exception {
        StringBuilder yaml = new StringBuilder("general:\n  rules:\n    - id: first\n      members:\n");
        for (int index = 0; index < count; index++) yaml.append("        - same\n");
        ConfigSchema schema = ConfigSchema.builder("test").section("general")
                .structuredList("rules", Values.objectWithIdentity("id",
                        Values.member("id", Values.string()),
                        Values.member("members", Values.widget(Values.list(Values.string()),
                                Values.searchPicker("test:list-members", 8,
                                        SearchPickerSpec.BindingMode.LIST_MEMBERS)))))
                .build().endSection().build();
        File file = File.createTempFile("structured-list-duplicate-delete-", ".yaml");
        write(file, yaml.toString());
        adapter = new DraftSignalAdapter(runtime, DraftBuffer.from(Authority.load(file, schema)));
        sceneRoot = new SceneNode();
        mountHandle = runtime.mount(sceneRoot, () -> new StructuredListFieldRenderer(listMemberRegistry())
                .render(runtime, schema.field("general.rules"), adapter));
        runtime.flush();
        runtime.flush();
        harness.mountRoot(sceneRoot, 640, 420);
        SceneNode picker = memberControl(rowAt(mountHandle.getRoot(), 0), "members").__getChildren().get(0);
        harness.click(findButton(picker, "Manage"));
        runtime.flush();
        SceneNode panel = panelRoot();
        layoutPanel(panel, 1000, 700);
        return panel.__getChildren().get(2).__getChildren().get(1)
                .__getChildren().get(0).__getChildren().get(0);
    }

    private void confirmMemberDelete(SceneNode row) {
        enterMemberDeleteConfirmation(row);
        harness.click(memberAction(row, 1));
        runtime.flush();
        layoutPanel(panelRoot(), 1000, 700);
    }

    private void enterMemberDeleteConfirmation(SceneNode row) {
        harness.click(memberAction(row, 1));
        runtime.flush();
        layoutPanel(panelRoot(), 1000, 700);
    }

    /** 成员行 = [icon, info, actions]；actions = [edit, remove]。 */
    private static SceneNode visibleMemberActions(SceneNode row) {
        return row.__getChildren().get(2);
    }

    private static SceneNode memberAction(SceneNode row, int index) {
        return visibleMemberActions(row).__getChildren().get(index);
    }

    private static List<String> directTexts(SceneNode node) {
        java.util.ArrayList<String> values = new java.util.ArrayList<String>();
        if (node.getText() != null && !node.getText().isEmpty()) values.add(node.getText());
        for (SceneNode child : node.__getChildren()) values.addAll(directTexts(child));
        return values;
    }

    private void assertResponsiveMemberForm(FixedTextMeasurer measurer, int viewportWidth,
                                            String displayLabel, String helper) throws Exception {
        ConfigSchema schema = ConfigSchema.builder("test").section("general")
                .structuredList("rules", Values.objectWithIdentity("minimumRemainingDurability",
                        Values.member("minimumRemainingDurability", Values.number(), displayLabel, helper)))
                .build().endSection().build();
        File file = File.createTempFile("structured-member-width-test-", ".yaml");
        write(file, "general:\n  rules:\n    - minimumRemainingDurability: 17\n");
        adapter = new DraftSignalAdapter(runtime, DraftBuffer.from(Authority.load(file, schema)));
        sceneRoot = new SceneNode();
        mountHandle = runtime.mount(sceneRoot, () -> new StructuredListFieldRenderer()
                .render(runtime, schema.field("general.rules"), adapter));
        runtime.flush();
        runtime.flush();
        new SceneLayoutEngine(measurer).layout(sceneRoot, new Constraints(viewportWidth, 420));

        SceneNode row = rowAt(mountHandle.getRoot(), 0);
        SceneNode form = memberForm(row, displayLabel);
        SceneNode labelSlot = form.__getChildren().get(0);
        SceneNode helperSlot = form.__getChildren().get(1);
        SceneNode control = form.__getChildren().get(2).__getChildren().get(0);
        assertTrue("显示名槽必须裁剪长文本", labelSlot.isClipChildren());
        assertTrue("辅助说明槽必须裁剪长文本", helperSlot.isClipChildren());
        assertTrue("标签与辅助说明不得相交", bottom(labelSlot) <= absoluteY(helperSlot));
        assertTrue("辅助说明与控件不得相交", bottom(helperSlot) <= absoluteY(control));
        assertTrue("控件不得越过视口右边界", right(control) <= viewportWidth);
        assertTrue("控件左边界不得越过视口", absoluteX(control) >= 0);
        SceneNode delete = findButton(row, "删除");
        SceneNode deleteLabel = delete.__getChildren().get(0);
        assertEquals("非 ASCII 按钮宽度必须来自当前 measurer 与真实 padding",
                measurer.measureWidth("删除", deleteLabel.getFontSize())
                        + delete.getPaddingLeft() + delete.getPaddingRight(),
                box(delete).getWidth());
        assertEquals("结构化值仍应使用原 YAML key", 17.0,
                ((Number) listValue().get(0).get("minimumRemainingDurability")).doubleValue(), 0.0);
    }

    private void disposeMountedState() {
        if (adapter != null) {
            adapter.dispose();
            adapter = null;
        }
        if (mountHandle != null) {
            mountHandle.dispose();
            mountHandle = null;
        }
    }

    private SceneNode mountChoiceRenderer(String yaml) throws Exception {
        ConfigSchema schema = ConfigSchema.builder("test").section("general")
                .structuredList("rules", Values.objectWithIdentity("id",
                        Values.member("id", Values.string()),
                        Values.member("modes", Values.list(Values.choice("alpha", "beta")))))
                .build().endSection().build();
        File file = File.createTempFile("structured-choice-renderer-test-", ".yaml");
        write(file, yaml);
        Authority authority = Authority.load(file, schema);
        adapter = new DraftSignalAdapter(runtime, DraftBuffer.from(authority));
        sceneRoot = new SceneNode();
        FieldSpec field = schema.field("general.rules");
        mountHandle = runtime.mount(sceneRoot,
                () -> new StructuredListFieldRenderer().render(runtime, field, adapter));
        SceneNode card = mountHandle.getRoot();
        runtime.flush();
        runtime.flush();
        harness.mountRoot(sceneRoot, 640, 420);
        return card;
    }

    private SceneNode mountPickerRenderer(String yaml) throws Exception {
        ConfigSchema schema = ConfigSchema.builder("test").section("general")
                .structuredList("rules", Values.objectWithIdentity("id",
                        Values.member("id", Values.string()),
                        Values.member("members", Values.widget(Values.list(Values.string()),
                                Values.searchPicker("test:list-picker", 8)))))
                .build().endSection().build();
        File file = File.createTempFile("structured-picker-renderer-test-", ".yaml");
        write(file, yaml);
        Authority authority = Authority.load(file, schema);
        adapter = new DraftSignalAdapter(runtime, DraftBuffer.from(authority));
        sceneRoot = new SceneNode();
        FieldSpec field = schema.field("general.rules");
        mountHandle = runtime.mount(sceneRoot,
                () -> new StructuredListFieldRenderer(pickerRegistry()).render(runtime, field, adapter));
        SceneNode card = mountHandle.getRoot();
        runtime.flush();
        runtime.flush();
        harness.mountRoot(sceneRoot, 640, 420);
        return card;
    }

    private SceneNode mountPickerRendererWithPresenter(String yaml, SceneImageSource image) throws Exception {
        ConfigSchema schema = ConfigSchema.builder("test").section("general")
                .structuredList("rules", Values.objectWithIdentity("id",
                        Values.member("id", Values.string()),
                        Values.member("members", Values.widget(Values.list(Values.string()),
                                Values.searchPicker("test:list-picker", 8)))))
                .build().endSection().build();
        File file = File.createTempFile("structured-picker-presentation-test-", ".yaml");
        write(file, yaml);
        adapter = new DraftSignalAdapter(runtime, DraftBuffer.from(Authority.load(file, schema)));
        sceneRoot = new SceneNode();
        mountHandle = runtime.mount(sceneRoot, () -> new StructuredListFieldRenderer(
                pickerRegistryWithPresenter(image)).render(runtime, schema.field("general.rules"), adapter));
        SceneNode card = mountHandle.getRoot();
        runtime.flush(); runtime.flush(); harness.mountRoot(sceneRoot, 640, 420);
        return card;
    }

    private static Registry pickerRegistryWithPresenter(SceneImageSource image) {
        Registry registry = new Registry();
        registry.register(new ValueEditorProvider() {
            public String id() { return "test:list-picker"; }
            public Codec codec() { return pickerProviderCodec(); }
            public SearchFunction searchFunction() { return (query, max) -> SearchPickerData.SearchResult.empty(); }
            public VisualAdapter visualAdapter() { return new VisualAdapter() {
                public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
                public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
            }; }
            public CurrentValuePresenter currentValuePresenter() { return value -> {
                List<?> values = value instanceof List ? (List<?>) value : Collections.emptyList();
                String shown = values.isEmpty() ? "empty" : String.valueOf(values.get(0));
                return new CurrentValuePresenter.Presentation(shown, "summary-" + shown,
                        "no-image".equals(shown) ? null : image);
            }; }
        });
        registry.freeze();
        return registry;
    }

    private static Codec pickerProviderCodec() {
        return new Codec() {
            public SearchPickerData.Selection decode(Object value) { return null; }
            public Object encode(Object current, SearchPickerData.Selection selection) { return current; }
        };
    }

    private static Registry pickerRegistry() {
        Registry registry = new Registry();
        registry.register(new ValueEditorProvider() {
            public String id() { return "test:list-picker"; }
            public Codec codec() { return new Codec() {
                public SearchPickerData.Selection decode(Object value) {
                    List<?> values = value instanceof List ? (List<?>) value : Collections.emptyList();
                    String key = values.isEmpty() ? "empty" : String.valueOf(values.get(0));
                    return new SearchPickerData.Selection(key, SearchPickerData.SelectionMode.ALL,
                            Collections.<String>emptyList());
                }
                public Object encode(Object current, SearchPickerData.Selection selection) {
                    java.util.ArrayList<String> next = new java.util.ArrayList<String>();
                    if (current instanceof List) for (Object item : (List<?>) current) next.add(String.valueOf(item));
                    next.add("picked-" + (next.isEmpty() ? "empty" : next.get(0)));
                    return next;
                }
            }; }
            public SearchFunction searchFunction() { return (query, max) -> new SearchPickerData.SearchResult(
                    Collections.singletonList(new SearchPickerData.Candidate("picked", "Picked",
                            Collections.<SearchPickerData.Variant>emptyList()))); }
            public VisualAdapter visualAdapter() { return new VisualAdapter() {
                public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
                public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
            }; }
        });
        registry.freeze();
        return registry;
    }

    private static Registry listMemberRegistry() {
        Registry registry = new Registry();
        registry.register(new ValueEditorProvider() {
            public String id() { return "test:list-members"; }
            public Codec codec() { return new ListMemberCodec() {
                public SearchPickerData.Selection decodeMember(Object raw) {
                    return raw instanceof String ? new SearchPickerData.Selection((String) raw,
                            SearchPickerData.SelectionMode.ALL, Collections.<String>emptyList()) : null;
                }
                public Object encodeMember(Object raw, SearchPickerData.Selection selected) {
                    return raw instanceof String ? selected.candidateKey() : null;
                }
                public SearchPickerData.Selection decode(Object value) { return null; }
                public Object encode(SearchPickerData.Selection value) { return null; }
            }; }
            public SearchFunction searchFunction() { return (query, max) -> new SearchPickerData.SearchResult(
                    Collections.singletonList(new SearchPickerData.Candidate("picked", "Picked",
                            Collections.<SearchPickerData.Variant>emptyList()))); }
            public VisualAdapter visualAdapter() { return new VisualAdapter() {
                public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
                public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
            }; }
        });
        registry.freeze();
        return registry;
    }

    private void selectPickerCandidate(SceneNode picker, int viewportWidth) {
        harness.mountRoot(sceneRoot, viewportWidth, 420);
        runtime.requestFocus(picker.__getChildren().get(0));
        runtime.flush();
        harness.pressKey(SceneKey.ENTER);
        runtime.flush();
        SceneNode panel = panelRoot();
        layoutPanel(panel, 1000, 700);
        // 第二次布局：首帧 layoutDone 后列数推导可能改写几何，再布局一次收敛（对齐宿主逐帧布局）。
        layoutPanel(panel, 1000, 700);
        harness.click(gridCell(panel, 0));
        runtime.flush();
        harness.mountRoot(sceneRoot, 640, 420);
    }

    /** 全屏面板卡片 = overlay root.children[0]（overlay root 为透明 scrim）。 */
    private SceneNode panelRoot() {
        return runtime.getOverlayHost().bottomFirst().get(0).getRoot().__getChildren().get(0);
    }

    /** 布局面板并桥接 layout epoch，虚拟网格窗口在 flush 后按最新视口挂载。 */
    private void layoutPanel(SceneNode panel, int width, int height) {
        SceneNode layoutRoot = panel.__getParent() != null ? panel.__getParent() : panel;
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        engine.layout(layoutRoot, new Constraints(width, height));
        runtime.__bridgeLayoutEpoch(engine.layoutEpoch());
        runtime.flush();
    }

    /** 结果列表单元：中栏 children = [error, stackHost, infoBar]，stackHost.children[0] = viewport。 */
    private static SceneNode gridCell(SceneNode panel, int index) {
        SceneNode viewport = panel.__getChildren().get(1).__getChildren().get(1)
                .__getChildren().get(1).__getChildren().get(0);
        SceneNode rowsContainer = viewport.__getChildren().get(0);
        for (SceneNode row : rowsContainer.__getChildren()) {
            if (index < row.__getChildren().size()) return row.__getChildren().get(index);
            index -= row.__getChildren().size();
        }
        throw new IllegalStateException("cell index out of mounted window: " + index);
    }

    /** 断言生产父链布局后的控件具备可用尺寸且不越过视口右边界。 */
    private static void assertVisibleInsideViewport(SceneNode node, int viewportWidth) {
        LayoutBox box = (LayoutBox) node.getCachedLayout();
        assertNotNull("控件应已随完整 sceneRoot 完成布局", box);
        assertTrue("控件宽高必须大于零", box.getWidth() > 0 && box.getHeight() > 0);
        assertTrue("控件右边界不得越过视口", absoluteX(node) + box.getWidth() <= viewportWidth);
    }

    private static int absoluteX(SceneNode node) {
        int x = 0;
        for (SceneNode current = node; current != null; current = current.__getParent()) {
            LayoutBox box = (LayoutBox) current.getCachedLayout();
            if (box != null) x += box.getX();
        }
        return x;
    }

    private static int absoluteY(SceneNode node) {
        int y = 0;
        for (SceneNode current = node; current != null; current = current.__getParent()) {
            LayoutBox box = (LayoutBox) current.getCachedLayout();
            if (box != null) y += box.getY();
        }
        return y;
    }

    @SuppressWarnings("unchecked")
    private List<Object> membersAt(int index) {
        return (List<Object>) listValue().get(index).get("members");
    }

    @SuppressWarnings("unchecked")
    private List<Object> modes() {
        return (List<Object>) listValue().get(0).get("modes");
    }

    private static Map<String, Object> choiceRule(String id, String... modes) {
        java.util.LinkedHashMap<String, Object> row = new java.util.LinkedHashMap<String, Object>();
        row.put("id", id);
        row.put("modes", new java.util.ArrayList<Object>(Arrays.asList(modes)));
        return row;
    }

    private static List<String> directLabels(SceneNode choices) {
        java.util.ArrayList<String> result = new java.util.ArrayList<String>();
        for (SceneNode checkbox : choices.__getChildren()) {
            result.add(checkbox.__getChildren().get(1).getText());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listValue() {
        return (List<Map<String, Object>>) adapter.draftSignal("general.rules").get();
    }

    private void setErrors(String path, String message) {
        java.util.LinkedHashMap<String, String> errors = new java.util.LinkedHashMap<String, String>();
        errors.put(path, message);
        adapter.setSubmitValidation(ValidationResult.of(errors));
        runtime.flush();
    }

    private static List<String> ids(List<Map<String, Object>> rows) {
        java.util.ArrayList<String> result = new java.util.ArrayList<String>();
        for (Map<String, Object> row : rows) result.add(String.valueOf(row.get("id")));
        return result;
    }

    private static Map<String, Object> rule(String id, String member) {
        java.util.LinkedHashMap<String, Object> row = new java.util.LinkedHashMap<String, Object>();
        row.put("id", id);
        row.put("members", Arrays.<Object>asList(member));
        return row;
    }

    private SceneNode rowAt(SceneNode card, int index) {
        SceneNode viewport = findScrollable(card);
        return viewport.__getChildren().get(index);
    }

    private static String rowHeader(SceneNode row) {
        return row.__getChildren().get(0).__getChildren().get(0).__getChildren().get(0).getText();
    }

    private void assertHeaderLayout(SceneNode row, int viewportWidth, boolean expectRightSpace) {
        harness.mountRoot(sceneRoot, viewportWidth, 420);
        SceneNode header = row.__getChildren().get(0);
        List<SceneNode> children = header.__getChildren();
        SceneNode titleSlot = children.get(0);
        int fixedWidth = 0;
        for (int i = 1; i < children.size(); i++) {
            fixedWidth += box(children.get(i)).getWidth();
            assertTrue("header 相邻槽不得重叠", right(children.get(i - 1)) <= absoluteX(children.get(i)));
        }
        int availableTitleWidth = box(header).getWidth() - fixedWidth
                - header.getGap() * (children.size() - 1);
        assertTrue("标题槽不得超过可用宽度与 260px 上限",
                box(titleSlot).getWidth() <= Math.min(260, availableTitleWidth));
        assertTrue("按钮组必须紧随标题槽", right(titleSlot) + header.getGap()
                == absoluteX(children.get(1)));
        SceneNode delete = children.get(children.size() - 1);
        assertTrue("删除按钮不得越过 card 右边界", right(delete) <= right(row));
        assertTrue("删除按钮不得越过 viewport", right(delete) <= viewportWidth);
        if (expectRightSpace) assertTrue("宽屏剩余空白应留在 header 右侧", right(delete) < right(header));
    }

    private static int[] buttonWidths(SceneNode... buttons) {
        int[] widths = new int[buttons.length];
        for (int i = 0; i < buttons.length; i++) widths[i] = box(buttons[i]).getWidth();
        return widths;
    }

    private static LayoutBox box(SceneNode node) {
        return (LayoutBox) node.getCachedLayout();
    }

    private static int right(SceneNode node) {
        return absoluteX(node) + box(node).getWidth();
    }

    private static int bottom(SceneNode node) {
        return absoluteY(node) + box(node).getHeight();
    }

    private String memberError(SceneNode row, String member) {
        SceneNode found = findMemberWrapper(row, member);
        if (found == null) {
            runtime.requestFocus(findButton(row, "展开"));
            harness.pressKey(SceneKey.ENTER);
            runtime.flush();
            found = findMemberWrapper(row, member);
        }
        if (found != null) return found.__getChildren().size() > 1
                ? found.__getChildren().get(found.__getChildren().size() - 1).getText() : "";
        throw new AssertionError("未找到 member: " + member);
    }

    private SceneNode findMemberWrapper(SceneNode node, String member) {
        for (SceneNode child : node.__getChildren()) {
            if (findDirectMemberForm(child, member) != null) {
                return child;
            }
            SceneNode nested = findMemberWrapper(child, member);
            if (nested != null) return nested;
        }
        return null;
    }

    private SceneNode memberControl(SceneNode row, String member) {
        SceneNode form = memberRow(row, member);
        SceneNode content = form.__getChildren().get(form.__getChildren().size() - 1);
        return content.__getChildren().get(0);
    }

    private SceneNode memberRow(SceneNode row, String member) {
        SceneNode wrapper = findMemberWrapper(row, member);
        if (wrapper == null) {
            runtime.requestFocus(findButton(row, "展开"));
            harness.pressKey(SceneKey.ENTER);
            runtime.flush();
            wrapper = findMemberWrapper(row, member);
        }
        if (wrapper != null) return findDirectMemberForm(wrapper, member);
        throw new AssertionError("未找到 member 控件: " + member);
    }

    private SceneNode memberForm(SceneNode row, String member) {
        return memberRow(row, member);
    }

    private static SceneNode findDirectMemberForm(SceneNode wrapper, String member) {
        for (SceneNode candidate : wrapper.__getChildren()) {
            if (candidate.__getChildren().isEmpty()) continue;
            SceneNode labelSlot = candidate.__getChildren().get(0);
            if (!labelSlot.__getChildren().isEmpty()
                    && member.equals(labelSlot.__getChildren().get(0).getText())) return candidate;
        }
        return null;
    }

    private SceneNode findButton(SceneNode node, String text) {
        if (node.__getChildren().size() > 0
                && text.equals(node.__getChildren().get(0).getText())) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findButton(child, text);
            if (found != null) return found;
        }
        return null;
    }

    private SceneNode findScrollable(SceneNode node) {
        if (node.isScrollable()) return node;
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findScrollable(child);
            if (found != null) return found;
        }
        return null;
    }

    private SceneNode structuredAddButton(SceneNode card) {
        SceneNode viewport = findScrollable(card);
        SceneNode control = viewport.__getParent();
        for (SceneNode child : control.__getChildren()) {
            if (child != viewport && hasDirectLabel(child, "添加")) return child;
        }
        throw new AssertionError("未找到结构化列表添加按钮");
    }

    private boolean hasDirectLabel(SceneNode node, String text) {
        return !node.__getChildren().isEmpty() && text.equals(node.__getChildren().get(0).getText());
    }

    private static boolean containsScrollable(SceneNode node) {
        if (node.isScrollable()) return true;
        for (SceneNode child : node.__getChildren()) {
            if (containsScrollable(child)) return true;
        }
        return false;
    }

    private static boolean containsText(SceneNode node, String text) {
        if (text.equals(node.getText())) return true;
        for (SceneNode child : node.__getChildren()) {
            if (containsText(child, text)) return true;
        }
        return false;
    }

    private static SceneNode findImageNode(SceneNode node, SceneImageSource image) {
        if (node.getImageSource() == image) return node;
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findImageNode(child, image);
            if (found != null) return found;
        }
        return null;
    }

    private static void write(File file, String text) throws Exception {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }
}
