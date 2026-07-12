package club.heiqi.config.ui.field;

import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.Values;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** 默认 registry 暴露结构化列表 renderer 的测试。 */
public class StructuredListRegistryTest {

    @Test
    public void defaultRegistryResolvesStructuredListRenderer() {
        ConfigSchema schema = ConfigSchema.builder("test")
                .section("general")
                .structuredList("rules", Values.object(
                        Values.member("id", Values.string()),
                        Values.member("members", Values.list(Values.string()))))
                .build()
                .endSection()
                .build();

        FieldRenderer renderer = FieldRendererRegistry.defaultRegistry()
                .resolve(schema.field("general.rules"));
        assertTrue(renderer instanceof StructuredListFieldRenderer);
    }
}
