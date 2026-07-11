package club.heiqi.config.ui.field;

import java.util.Collections;

import org.junit.After;
import org.junit.Test;

import club.heiqi.config.schema.SearchPickerSpec;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.ui.editor.Codec;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

import static org.junit.Assert.*;

/** SearchPickerFieldSupport 的注册表边界测试。 */
public class SearchPickerFieldSupportTest {
    /** 清理无 owner 的响应式测试状态。 */
    @After public void tearDown() { ReactiveScheduler.get().reset(); }

    /** 搜索 widget 引用缺失 provider 时必须在装配点 fail-fast。 */
    @Test
    public void missingProviderFailsFast() {
        Registry registry = new Registry();
        registry.freeze();
        ValueSpec spec = ValueSpec.string().withWidget(new SearchPickerSpec("test:missing", 8));

        try {
            SearchPickerFieldSupport.createIfPresent(null, spec, "", registry, value -> { });
            fail("expected missing provider failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("test:missing"));
        }
    }

    /** 外部有效选择才同步 query；无效 decode 与用户输入均保持，且不破坏节点和焦点。 */
    @Test
    public void controlledSelectionSyncsWithoutOverwritingDraftQuery() {
        Signal<Object> value = Signal.create(selection("stone"));
        MutableCodec codec = new MutableCodec();
        Registry registry = registry(codec);
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime runtime = harness.getRuntime();
        SceneNode picker = SearchPickerFieldSupport.createControlledIfPresent(runtime, spec(), value,
                registry, ignored -> { });
        harness.mountRoot(picker, 320, 240);
        SceneNode input = picker.__getChildren().get(0);
        runtime.requestFocus(input);
        ReactiveScheduler.get().flush();
        assertEquals("stone", textOf(input));

        value.set(selection("dirt"));
        ReactiveScheduler.get().flush();
        assertSame(input, picker.__getChildren().get(0));
        assertSame(input, runtime.getFocusedNode());
        assertEquals("dirt", textOf(input));

        harness.typeText("x");
        value.set(selection("dirt"));
        ReactiveScheduler.get().flush();
        assertEquals("x", textOf(input));
        codec.returnNull = true;
        value.set("null-decode");
        ReactiveScheduler.get().flush();
        assertEquals("x", textOf(input));
        codec.returnNull = false;
        codec.failDecode = true;
        value.set("failed-decode");
        ReactiveScheduler.get().flush();
        assertEquals("x", textOf(input));
        runtime.dispose();
    }

    private static ValueSpec spec() {
        return ValueSpec.string().withWidget(new SearchPickerSpec("test:picker", 8));
    }

    private static Registry registry(final Codec codec) {
        Registry registry = new Registry();
        registry.register(new ValueEditorProvider() {
            public String id() { return "test:picker"; }
            public Codec codec() { return codec; }
            public VisualAdapter visualAdapter() { return new VisualAdapter() {
                public String candidateLabel(SearchPickerData.Candidate value) { return value.label(); }
                public String variantLabel(SearchPickerData.Variant value) { return value.label(); }
            }; }
            public SearchFunction searchFunction() { return (query, max) -> SearchPickerData.SearchResult.empty(); }
        });
        registry.freeze();
        return registry;
    }

    private static SearchPickerData.Selection selection(String key) {
        return new SearchPickerData.Selection(key, SearchPickerData.SelectionMode.ALL,
                Collections.<String>emptyList());
    }

    private static String textOf(SceneNode node) {
        for (SceneNode child : node.__getChildren()) {
            if (!child.getText().isEmpty()) return child.getText();
            String nested = textOf(child);
            if (!nested.isEmpty()) return nested;
        }
        return "";
    }

    private static final class MutableCodec implements Codec {
        private boolean returnNull;
        private boolean failDecode;
        public SearchPickerData.Selection decode(Object value) {
            if (failDecode) throw new IllegalStateException("decode");
            return returnNull ? null : (SearchPickerData.Selection) value;
        }
        public Object encode(SearchPickerData.Selection selection) { return selection; }
    }
}
