package club.heiqi.config.ui;

import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldType;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * schema 兼容性纯判定：同 owner 前提下路径/类型；不被 owner 截断。
 *
 * <p>文档：constraints/default/widget 随 bootstrap owner 冻结，无 manager 内 schema reload。</p>
 */
public class SchemaReplaceCompatibilityTest {

    @Test
    public void missingPathFailsAtomically() {
        ConfigSchema current = ConfigSchema.builder("a")
                .section("s")
                .title("S")
                .string("host").defaultValue("h").label("H").build()
                .string("port").defaultValue("1").label("P").build()
                .endSection()
                .build();
        ConfigSchema next = ConfigSchema.builder("b")
                .section("s")
                .title("S")
                .string("host").defaultValue("h").label("H").build()
                .endSection()
                .build();
        try {
            SchemaReplaceCompatibility.checkCompatible(current, next);
            fail("expected missing path");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("missing path"));
            assertTrue(e.getMessage().contains("s.port"));
        }
    }

    @Test
    public void typeMismatchFailsAtomically() {
        ConfigSchema current = ConfigSchema.builder("a")
                .section("s")
                .title("S")
                .string("host").defaultValue("h").label("H").build()
                .endSection()
                .build();
        ConfigSchema next = ConfigSchema.builder("b")
                .section("s")
                .title("S")
                .number("host").defaultValue(1.0).label("H").build()
                .endSection()
                .build();
        try {
            SchemaReplaceCompatibility.checkCompatible(current, next);
            fail("expected type mismatch");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("type mismatch"));
            assertTrue(e.getMessage().contains("s.host"));
            assertTrue(e.getMessage().contains(FieldType.STRING.name())
                    || e.getMessage().contains("STRING"));
        }
    }

    @Test
    public void compatibleSameShapePasses() {
        ConfigSchema current = ConfigSchema.builder("a")
                .section("s")
                .title("S")
                .string("host").defaultValue("h").label("H").maxLength(10).build()
                .endSection()
                .build();
        // 约束不同但 path/type 同 → 兼容（constraints 随 owner 冻结，不在此比较）
        ConfigSchema next = ConfigSchema.builder("b")
                .section("s")
                .title("S")
                .string("host").defaultValue("other").label("H2").maxLength(100).build()
                .endSection()
                .build();
        SchemaReplaceCompatibility.checkCompatible(current, next);
    }

    @Test
    public void unexpectedPathFails() {
        ConfigSchema current = ConfigSchema.builder("a")
                .section("s")
                .title("S")
                .string("host").defaultValue("h").label("H").build()
                .endSection()
                .build();
        ConfigSchema next = ConfigSchema.builder("b")
                .section("s")
                .title("S")
                .string("host").defaultValue("h").label("H").build()
                .string("extra").defaultValue("x").label("E").build()
                .endSection()
                .build();
        try {
            SchemaReplaceCompatibility.checkCompatible(current, next);
            fail("expected unexpected path");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("unexpected path"));
        }
    }
}
