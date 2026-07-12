package club.heiqi.config.ui;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.ValidationResult;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.Values;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** keyed 行排序后，结构化 member 错误信号按当前 index 重新映射。 */
public class StructuredListErrorSignalTest {

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    @Test
    public void dynamicPathSignalFollowsCurrentRowIndex() throws Exception {
        ConfigSchema schema = ConfigSchema.builder("test")
                .section("general")
                .structuredList("rules", Values.object(
                        Values.member("id", Values.string()),
                        Values.member("members", Values.list(Values.string()))))
                .build()
                .endSection()
                .build();
        DraftSignalAdapter adapter = new DraftSignalAdapter(
                null, DraftBuffer.from(Authority.load((File) null, schema)));
        try {
            Signal<String> path = Signal.create("general.rules[0].members[1]");
            Map<String, String> errors = new LinkedHashMap<String, String>();
            errors.put("general.rules[0].members[1]", "first");
            errors.put("general.rules[1].members[1]", "second");
            adapter.setSubmitValidation(ValidationResult.of(errors));
            ReactiveScheduler.get().flush();
            ReadableSignal<String> error = adapter.errorSignalForPath(path::get);
            ReactiveScheduler.get().flush();
            assertEquals("first", error.get());
            path.set("general.rules[1].members[1]");
            ReactiveScheduler.get().flush();
            assertEquals("second", error.get());
        } finally {
            adapter.dispose();
        }
    }

    @Test
    public void descendantSignalAggregatesListElementError() throws Exception {
        ConfigSchema schema = ConfigSchema.builder("test")
                .section("general")
                .structuredList("rules", Values.object(
                        Values.member("id", Values.string()),
                        Values.member("members", Values.list(Values.string()))))
                .build()
                .endSection()
                .build();
        DraftSignalAdapter adapter = new DraftSignalAdapter(
                null, DraftBuffer.from(Authority.load((File) null, schema)));
        try {
            Signal<String> prefix = Signal.create("general.rules[0].members");
            Map<String, String> errors = new LinkedHashMap<String, String>();
            errors.put("general.rules[0].members[1]", "element blocked");
            adapter.setSubmitValidation(ValidationResult.of(errors));
            ReactiveScheduler.get().flush();

            ReadableSignal<String> error = adapter.errorSignalForPathAndDescendants(prefix::get);
            ReactiveScheduler.get().flush();
            assertEquals("element blocked", error.get());
        } finally {
            adapter.dispose();
        }
    }
}
