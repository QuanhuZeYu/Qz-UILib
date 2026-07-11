package club.heiqi.config.ui.field;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
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

    /** 旧单参 codec 仍由双参默认方法兼容调用。 */
    @Test
    public void legacyCodecRemainsCompatible() {
        AtomicReference<SearchPickerData.Selection> encodedSelection = new AtomicReference<SearchPickerData.Selection>();
        Codec codec = new Codec() {
            public SearchPickerData.Selection decode(Object value) { return selection(String.valueOf(value)); }
            @Deprecated public Object encode(SearchPickerData.Selection selection) {
                encodedSelection.set(selection);
                return selection.candidateKey();
            }
        };
        PickerFixture fixture = fixture(codec, Signal.<Object>create("before"), ignored -> { });
        fixture.selectCandidate();
        assertEquals("picked", encodedSelection.get().candidateKey());
        fixture.dispose();
    }

    /** 双参 codec 收到确认瞬间 current，且成功写回后清空 query。 */
    @Test
    public void currentValueIsReadAtConfirmationAndSuccessClearsQuery() {
        Signal<Object> value = Signal.<Object>create("initial");
        AtomicReference<Object> encodedCurrent = new AtomicReference<Object>();
        AtomicReference<Object> changed = new AtomicReference<Object>();
        Codec codec = statelessCodec((current, selected) -> {
            encodedCurrent.set(current);
            return current + ":" + selected.candidateKey();
        });
        PickerFixture fixture = fixture(codec, value, changed::set);
        fixture.type("draft");
        value.set("instant");
        ReactiveScheduler.get().flush();
        fixture.selectCandidate();
        assertEquals("instant", encodedCurrent.get());
        assertEquals("instant:picked", changed.get());
        assertEquals("", textOf(fixture.input));
        fixture.dispose();
    }

    /** 两个结构行共享同一无状态 codec，交错确认不会串用另一行当前值。 */
    @Test
    public void sharedCodecInterleavedRowsDoNotLeakCurrentValue() {
        Codec codec = statelessCodec((current, selected) -> current + ":" + selected.candidateKey());
        Signal<Object> first = Signal.<Object>create("row-a");
        Signal<Object> second = Signal.<Object>create("row-b");
        AtomicReference<Object> firstWrite = new AtomicReference<Object>();
        AtomicReference<Object> secondWrite = new AtomicReference<Object>();
        PickerFixture firstFixture = fixture(codec, first, firstWrite::set);
        PickerFixture secondFixture = fixture(codec, second, secondWrite::set);
        first.set("row-a-now");
        secondFixture.selectCandidate();
        second.set("row-b-now");
        firstFixture.selectCandidate();
        assertEquals("row-a-now:picked", firstWrite.get());
        assertEquals("row-b:picked", secondWrite.get());
        firstFixture.dispose();
        secondFixture.dispose();
    }

    /** canonical decode 不进入 query；reset/reload 等价的外部值更新不覆盖用户草稿。 */
    @Test
    public void canonicalAndExternalValueChangesDoNotPopulateOrOverwriteQuery() {
        Signal<Object> value = Signal.<Object>create("canonical");
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> selected), value, ignored -> { });
        assertEquals("Search", textOf(fixture.input));
        fixture.type("draft");
        value.set("reset-value");
        ReactiveScheduler.get().flush();
        assertEquals("draft", textOf(fixture.input));
        value.set("reload-value");
        ReactiveScheduler.get().flush();
        assertEquals("draft", textOf(fixture.input));
        fixture.dispose();
    }

    /** encode 异常、null 及 onChange 异常均零写并保留 Draft。 */
    @Test
    public void failedEncodingDoesNotWriteOrClearDraft() {
        assertFailedEncodingKeepsDraft(statelessCodec((current, selected) -> null));
        assertFailedEncodingKeepsDraft(statelessCodec((current, selected) -> {
            throw new IllegalStateException("encode");
        }));
        AtomicInteger writes = new AtomicInteger();
        PickerFixture fixture = fixture(statelessCodec((current, selected) -> "encoded"),
                Signal.<Object>create("current"), ignored -> {
                    writes.incrementAndGet();
                    throw new IllegalStateException("onChange");
                });
        fixture.type("draft");
        fixture.selectCandidate();
        assertEquals(1, writes.get());
        assertEquals("draft", textOf(fixture.input));
        fixture.dispose();
    }

    private static void assertFailedEncodingKeepsDraft(Codec codec) {
        AtomicInteger writes = new AtomicInteger();
        PickerFixture fixture = fixture(codec, Signal.<Object>create("current"), ignored -> writes.incrementAndGet());
        fixture.type("draft");
        fixture.selectCandidate();
        assertEquals(0, writes.get());
        assertEquals("draft", textOf(fixture.input));
        fixture.dispose();
    }

    private static PickerFixture fixture(Codec codec, Signal<Object> value, java.util.function.Consumer<Object> onChange) {
        SceneInteractionHarness harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        SceneRuntime runtime = harness.getRuntime();
        SceneNode picker = SearchPickerFieldSupport.createControlledIfPresent(runtime, spec(), value,
                registry(codec), onChange);
        harness.mountRoot(picker, 320, 240);
        ReactiveScheduler.get().flush();
        return new PickerFixture(harness, runtime, picker, picker.__getChildren().get(0));
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
            public SearchFunction searchFunction() { return (query, max) -> new SearchPickerData.SearchResult(
                    Collections.singletonList(new SearchPickerData.Candidate("picked", "Picked",
                            Collections.<SearchPickerData.Variant>emptyList()))); }
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
            if (child.getText() != null && !child.getText().isEmpty()) return child.getText();
            String nested = textOf(child);
            if (!nested.isEmpty()) return nested;
        }
        return "";
    }

    private interface Encoder {
        Object encode(Object current, SearchPickerData.Selection selection);
    }

    private static Codec statelessCodec(final Encoder encoder) {
        return new Codec() {
            public SearchPickerData.Selection decode(Object value) { return selection(String.valueOf(value)); }
            public Object encode(Object currentValue, SearchPickerData.Selection selection) {
                return encoder.encode(currentValue, selection);
            }
        };
    }

    private static final class PickerFixture {
        private final SceneInteractionHarness harness;
        private final SceneRuntime runtime;
        private final SceneNode picker;
        private final SceneNode input;
        private final SceneLayoutEngine layout = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));

        private PickerFixture(SceneInteractionHarness harness, SceneRuntime runtime, SceneNode picker, SceneNode input) {
            this.harness = harness;
            this.runtime = runtime;
            this.picker = picker;
            this.input = input;
        }

        private void type(String value) {
            runtime.requestFocus(input);
            ReactiveScheduler.get().flush();
            harness.typeText(value);
        }

        private void selectCandidate() {
            layout.layout(picker, new Constraints(320, 240));
            harness.click(input);
            SceneNode portal = runtime.getOverlayHost().bottomFirst().get(0).getRoot();
            layout.layout(portal, new Constraints(320, 240));
            SceneNode item = portal
                    .__getChildren().get(0).__getChildren().get(0);
            harness.click(item);
            ReactiveScheduler.get().flush();
        }

        private void dispose() { runtime.dispose(); }
    }
}
