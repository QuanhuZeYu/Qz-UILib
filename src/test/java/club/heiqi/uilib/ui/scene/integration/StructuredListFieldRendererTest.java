package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.Values;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.field.StructuredListFieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

import java.io.File;
import java.io.FileWriter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 结构化列表默认 scene renderer 的 headless 装配回归。 */
public class StructuredListFieldRendererTest {

    private SceneInteractionHarness harness;
    private SceneRuntime runtime;
    private DraftSignalAdapter adapter;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
    }

    @After
    public void tearDown() {
        if (adapter != null) adapter.dispose();
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
