package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.runtime.ValidationResult;
import club.heiqi.config.schema.Values;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.field.StructuredListFieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
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
        assertTrue("应有添加按钮", containsText(card, "添加"));
        assertTrue("应有上移按钮", containsText(card, "上移"));
        assertTrue("应有下移按钮", containsText(card, "下移"));
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
        SceneNode moveDown = findButton(first, "下移");
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

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listValue() {
        return (List<Map<String, Object>>) adapter.draftSignal("general.rules").get();
    }

    private SceneNode rowAt(SceneNode card, int index) {
        SceneNode viewport = findScrollable(card);
        return viewport.__getChildren().get(index);
    }

    private String memberError(SceneNode row, String member) {
        for (SceneNode child : row.__getChildren()) {
            if (child.__getChildren().isEmpty()) continue;
            SceneNode memberRow = child.__getChildren().get(0);
            if (!memberRow.__getChildren().isEmpty()
                    && member.equals(memberRow.__getChildren().get(0).getText())) {
                return child.__getChildren().size() > 1
                        ? child.__getChildren().get(1).getText() : "";
            }
        }
        throw new AssertionError("未找到 member: " + member);
    }

    private SceneNode memberControl(SceneNode row, String member) {
        for (SceneNode child : row.__getChildren()) {
            if (child.__getChildren().isEmpty()) continue;
            SceneNode memberRow = child.__getChildren().get(0);
            if (!memberRow.__getChildren().isEmpty()
                    && member.equals(memberRow.__getChildren().get(0).getText())) {
                return memberRow.__getChildren().get(1);
            }
        }
        throw new AssertionError("未找到 member 控件: " + member);
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

    private static void write(File file, String text) throws Exception {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }
}
