package club.heiqi.config.schema;

import java.util.Collection;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * ConfigSchema + SectionSpec + FieldSpec + FieldConstraints 测试。
 * 覆盖基础构建、全路径索引、contains 查询、默认值推断、类型校验、
 * Builder 链式调用、不可变性、多 section 保序。
 */
public class ConfigSchemaTest {

    /**
     * 构建一个包含 4 种字段类型的标准 schema，供多测试用例复用。
     */
    private ConfigSchema buildStandardSchema() {
        return ConfigSchema.builder("my_mod")
            .section("general")
                .string("name").defaultValue("MyMod").build()
                .number("scale").range(0, 10).defaultValue(1.0).build()
                .bool("enabled").defaultValue(true).build()
                .choice("mode").options("A", "B", "C").defaultValue("A").build()
            .endSection()
            .section("advanced")
                .number("timeout").defaultValue(30).build()
            .endSection()
            .build();
    }

    /**
     * 用例 1：基础构建——4 种字段类型各一个，build 后 allFields/sections/field(path) 正确。
     */
    @Test
    public void testBasicBuild() {
        ConfigSchema schema = buildStandardSchema();

        assertEquals("my_mod", schema.modId());
        assertEquals(2, schema.sections().size());

        // 字段总数：general 4 + advanced 1 = 5
        Collection<FieldSpec> all = schema.allFields();
        assertEquals(5, all.size());

        // 各类型字段正确
        assertEquals(FieldType.STRING, schema.field("general.name").type());
        assertEquals(FieldType.NUMBER, schema.field("general.scale").type());
        assertEquals(FieldType.BOOLEAN, schema.field("general.enabled").type());
        assertEquals(FieldType.CHOICE, schema.field("general.mode").type());
        assertEquals(FieldType.NUMBER, schema.field("advanced.timeout").type());
    }

    /**
     * 用例 2：全路径索引——field("general.scale") 返回正确 FieldSpec。
     */
    @Test
    public void testPathIndex() {
        ConfigSchema schema = buildStandardSchema();

        FieldSpec scale = schema.field("general.scale");
        assertNotNull(scale);
        assertEquals("general.scale", scale.path());
        assertEquals(FieldType.NUMBER, scale.type());
        assertEquals(1.0, ((Number) scale.defaultValue()).doubleValue(), 0.0);
        // range 约束
        assertEquals(0.0, scale.constraints().min(), 0.0);
        assertEquals(10.0, scale.constraints().max(), 0.0);

        // 不存在的路径返回 null
        assertNull(schema.field("general.notexist"));
        assertNull(schema.field("nonexistent.key"));
    }

    /**
     * 用例 3：containsPath / containsTopLevel 正确返回 true/false。
     */
    @Test
    public void testContains() {
        ConfigSchema schema = buildStandardSchema();

        // containsPath
        assertTrue(schema.containsPath("general.name"));
        assertTrue(schema.containsPath("advanced.timeout"));
        assertFalse(schema.containsPath("general.notexist"));
        assertFalse(schema.containsPath("nonexistent.key"));

        // containsTopLevel —— 顶层 key 即分类名
        assertTrue(schema.containsTopLevel("general"));
        assertTrue(schema.containsTopLevel("advanced"));
        assertFalse(schema.containsTopLevel("name"));
        assertFalse(schema.containsTopLevel("general.scale"));
        assertFalse(schema.containsTopLevel("nonexistent"));
    }

    /**
     * 用例 4：默认值——未设 default 时按类型给合理默认。
     */
    @Test
    public void testDefaultInference() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s")
                .string("str").build()
                .number("num").build()
                .bool("flag").build()
                .choice("ch").options("X", "Y").build()
            .endSection()
            .build();

        assertEquals("", schema.field("s.str").defaultValue());
        assertEquals(0.0, ((Number) schema.field("s.num").defaultValue()).doubleValue(), 0.0);
        assertEquals(Boolean.FALSE, schema.field("s.flag").defaultValue());
        assertEquals("X", schema.field("s.ch").defaultValue());
    }

    /**
     * 用例 5a：类型校验——STRING 的 default 传非 String 抛异常。
     */
    @Test(expected = IllegalArgumentException.class)
    public void testTypeValidationStringRejectsNonString() {
        ConfigSchema.builder("mod")
            .section("s")
                .string("k").defaultValue(42).build()
            .endSection()
            .build();
    }

    /**
     * 用例 5b：类型校验——NUMBER 的 default 传非 Number 抛异常。
     */
    @Test(expected = IllegalArgumentException.class)
    public void testTypeValidationNumberRejectsNonNumber() {
        ConfigSchema.builder("mod")
            .section("s")
                .number("k").defaultValue("not a number").build()
            .endSection()
            .build();
    }

    /**
     * 用例 5c：类型校验——BOOLEAN 的 default 传非 Boolean 抛异常。
     */
    @Test(expected = IllegalArgumentException.class)
    public void testTypeValidationBooleanRejectsNonBoolean() {
        ConfigSchema.builder("mod")
            .section("s")
                .bool("k").defaultValue("yes").build()
            .endSection()
            .build();
    }

    /**
     * 用例 5d：类型校验——CHOICE 的 default 不在 options 内抛异常。
     */
    @Test(expected = IllegalArgumentException.class)
    public void testTypeValidationChoiceDefaultNotInOptions() {
        ConfigSchema.builder("mod")
            .section("s")
                .choice("k").options("A", "B").defaultValue("Z").build()
            .endSection()
            .build();
    }

    /**
     * 用例 5e：类型校验——CHOICE 未声明 options 且未设 default 抛异常（无法推断）。
     */
    @Test(expected = IllegalArgumentException.class)
    public void testTypeValidationChoiceWithoutOptions() {
        ConfigSchema.builder("mod")
            .section("s")
                .choice("k").build()
            .endSection()
            .build();
    }

    /**
     * 用例 6：Builder 链式调用——section→string→build→endSection→section→...→build 流程正确。
     */
    @Test
    public void testBuilderChaining() {
        ConfigSchema.Builder schemaBuilder = ConfigSchema.builder("chain_mod");
        // section() 返回 SectionSpec.Builder
        SectionSpec.Builder sectionBuilder = schemaBuilder.section("sec1");
        assertNotNull(sectionBuilder);
        // string() 返回 FieldSpec.Builder
        FieldSpec.Builder fieldBuilder = sectionBuilder.string("a");
        assertNotNull(fieldBuilder);
        // FieldSpec.Builder.build() 返回 SectionSpec.Builder
        SectionSpec.Builder backToSection = fieldBuilder.defaultValue("v").build();
        assertSame(sectionBuilder, backToSection);
        // endSection() 返回 ConfigSchema.Builder
        ConfigSchema.Builder backToSchema = backToSection.endSection();
        assertSame(schemaBuilder, backToSchema);

        ConfigSchema schema = backToSchema.build();
        assertEquals("chain_mod", schema.modId());
        assertEquals(1, schema.sections().size());
        assertEquals("v", schema.field("sec1.a").defaultValue());
    }

    /**
     * 用例 7a：不可变性——build 后 sections() 返回不可修改 List。
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testSectionsImmutable() {
        ConfigSchema schema = buildStandardSchema();
        schema.sections().add(new SectionSpec("x", "x", new java.util.ArrayList<FieldSpec>()));
    }

    /**
     * 用例 7b：不可变性——SectionSpec.fields() 返回不可修改 List。
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testSectionFieldsImmutable() {
        ConfigSchema schema = buildStandardSchema();
        schema.sections().get(0).fields().add(
            new FieldSpec("general.x", FieldType.STRING, "", FieldConstraints.none(), null, null, null));
    }

    /**
     * 用例 7c：不可变性——FieldSpec 的 accessor 返回值与构造一致（record 不可变）。
     */
    @Test
    public void testFieldSpecImmutable() {
        ConfigSchema schema = buildStandardSchema();
        FieldSpec f = schema.field("general.name");
        assertEquals("general.name", f.path());
        assertEquals(FieldType.STRING, f.type());
        assertEquals("MyMod", f.defaultValue());
        // 多次调用返回一致
        assertSame(f.path(), f.path());
        assertSame(f.type(), f.type());
    }

    /**
     * 用例 7d：不可变性——FieldConstraints.choices() 返回不可修改 List。
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testFieldConstraintsChoicesImmutable() {
        ConfigSchema schema = buildStandardSchema();
        FieldSpec mode = schema.field("general.mode");
        mode.constraints().choices().add("D");
    }

    /**
     * 用例 8：多 section 保序——sections() 返回顺序与声明顺序一致。
     */
    @Test
    public void testSectionOrderPreserved() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("zeta")
                .string("a").build()
            .endSection()
            .section("alpha")
                .string("b").build()
            .endSection()
            .section("middle")
                .string("c").build()
            .endSection()
            .build();

        List<SectionSpec> sections = schema.sections();
        assertEquals("zeta", sections.get(0).name());
        assertEquals("alpha", sections.get(1).name());
        assertEquals("middle", sections.get(2).name());

        // allFields 顺序也与声明顺序一致
        Object[] paths = schema.allFields().stream().map(FieldSpec::path).toArray();
        assertEquals("zeta.a", paths[0]);
        assertEquals("alpha.b", paths[1]);
        assertEquals("middle.c", paths[2]);
    }

    /**
     * 用例 9：title 缺省回退到 name，显式设置时生效。
     */
    @Test
    public void testTitleDefaultAndExplicit() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("no_title")
                .string("a").build()
            .endSection()
            .section("with_title")
                .title("显示名")
                .string("b").build()
            .endSection()
            .build();

        assertEquals("no_title", schema.sections().get(0).title());
        assertEquals("显示名", schema.sections().get(1).title());
    }

    /**
     * 用例 10：label / helper / required / maxLength 约束正确传递。
     */
    @Test
    public void testMetadataAndConstraints() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s")
                .string("name").maxLength(50).label("名称").helper("请输入名称").required().build()
            .endSection()
            .build();

        FieldSpec f = schema.field("s.name");
        assertEquals("名称", f.label());
        assertEquals("请输入名称", f.helper());
        assertTrue(f.constraints().required());
        assertEquals(50, f.constraints().maxLength());
    }

    // ===== 边界用例追加 =====

    /**
     * 重复 section 名（不同字段）：sections 列表含两个同名 section，byPath 各自索引。
     * 验证当前行为：Builder 不去重 section 名，sections 保序保留两个。
     */
    @Test
    public void testDuplicateSectionNameDifferentFields() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s")
                .string("a").defaultValue("a1").build()
            .endSection()
            .section("s")
                .string("b").defaultValue("b1").build()
            .endSection()
            .build();

        assertEquals(2, schema.sections().size());
        assertEquals("s", schema.sections().get(0).name());
        assertEquals("s", schema.sections().get(1).name());
        assertNotNull(schema.field("s.a"));
        assertNotNull(schema.field("s.b"));
        assertEquals(2, schema.allFields().size());
    }

    /**
     * 重复 section 名（同字段路径）：byPath 后者覆盖前者。
     * 验证当前行为：byPath 是 LinkedHashMap，put 后者覆盖。
     */
    @Test
    public void testDuplicateSectionNameSameFieldPath() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s")
                .string("a").defaultValue("first").build()
            .endSection()
            .section("s")
                .string("a").defaultValue("second").build()
            .endSection()
            .build();

        assertEquals(2, schema.sections().size());
        // byPath 后者覆盖
        assertEquals("second", schema.field("s.a").defaultValue());
        // allFields 只含一个 s.a
        assertEquals(1, schema.allFields().size());
    }

    /**
     * 重复字段路径（同 section 下两个同 key 字段）：fields 列表含两个，byPath 后者覆盖。
     */
    @Test
    public void testDuplicateFieldPathInSameSection() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s")
                .string("a").defaultValue("first").build()
                .string("a").defaultValue("second").build()
            .endSection()
            .build();

        assertEquals(1, schema.sections().size());
        // section.fields 含两个同名 a
        assertEquals(2, schema.sections().get(0).fields().size());
        // byPath 后者覆盖
        assertEquals("second", schema.field("s.a").defaultValue());
        assertEquals(1, schema.allFields().size());
    }

    /**
     * 空 section：无字段，endSection + build 成功，allFields 不含该 section 字段。
     */
    @Test
    public void testEmptySectionBuildsSuccessfully() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("empty")
            .endSection()
            .section("other")
                .string("a").build()
            .endSection()
            .build();

        assertEquals(2, schema.sections().size());
        assertEquals(0, schema.sections().get(0).fields().size());
        assertEquals(1, schema.allFields().size());
        assertTrue(schema.containsTopLevel("empty"));
        assertFalse(schema.containsPath("empty.anything"));
    }

    /**
     * 超长路径：10 级点号嵌套，field(path) 正确返回。
     */
    @Test
    public void testDeepNestedPath() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("a")
                .string("b.c.d.e.f.g.h.i.j").defaultValue("deep").build()
            .endSection()
            .build();

        FieldSpec f = schema.field("a.b.c.d.e.f.g.h.i.j");
        assertNotNull(f);
        assertEquals("a.b.c.d.e.f.g.h.i.j", f.path());
        assertEquals("deep", f.defaultValue());
        assertTrue(schema.containsPath("a.b.c.d.e.f.g.h.i.j"));
    }

    /**
     * 特殊字符路径：字段 key 含中文、空格、连字符，schema 索引正常。
     */
    @Test
    public void testSpecialCharacterPath() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("服务器")
                .string("主机-地址").defaultValue("127.0.0.1").build()
                .string("主机 地址").defaultValue("0.0.0.0").build()
                .string("端口").defaultValue("8080").build()
            .endSection()
            .build();

        assertNotNull(schema.field("服务器.主机-地址"));
        assertNotNull(schema.field("服务器.主机 地址"));
        assertNotNull(schema.field("服务器.端口"));
        assertTrue(schema.containsPath("服务器.主机-地址"));
        assertEquals("127.0.0.1", schema.field("服务器.主机-地址").defaultValue());
    }

    /**
     * 多 section allFields 保序：3 个 section 各 2 字段，
     * 顺序 = s1.f1, s1.f2, s2.f1, s2.f2, s3.f1, s3.f2。
     */
    @Test
    public void testAllFieldsOrderMultiSection() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s1")
                .string("a").build()
                .string("b").build()
            .endSection()
            .section("s2")
                .string("c").build()
                .string("d").build()
            .endSection()
            .section("s3")
                .string("e").build()
                .string("f").build()
            .endSection()
            .build();

        Object[] paths = schema.allFields().stream().map(FieldSpec::path).toArray();
        assertEquals(6, paths.length);
        assertEquals("s1.a", paths[0]);
        assertEquals("s1.b", paths[1]);
        assertEquals("s2.c", paths[2]);
        assertEquals("s2.d", paths[3]);
        assertEquals("s3.e", paths[4]);
        assertEquals("s3.f", paths[5]);
    }

    /**
     * containsTopLevel 多 section：对两个 section 都 true，对不存在 false。
     */
    @Test
    public void testContainsTopLevelMultiSection() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("alpha")
                .string("a").build()
            .endSection()
            .section("beta")
                .string("b").build()
            .endSection()
            .build();

        assertTrue(schema.containsTopLevel("alpha"));
        assertTrue(schema.containsTopLevel("beta"));
        assertFalse(schema.containsTopLevel("gamma"));
        assertFalse(schema.containsTopLevel("alpha.a"));
    }

    /**
     * field(null) 安全性：传 null 不抛异常，返回 null。
     */
    @Test
    public void testFieldNullSafe() {
        ConfigSchema schema = buildStandardSchema();
        assertNull(schema.field(null));
    }

    /**
     * field("") 空字符串：返回 null。
     */
    @Test
    public void testFieldEmptyString() {
        ConfigSchema schema = buildStandardSchema();
        assertNull(schema.field(""));
    }

    /**
     * containsPath(null) 安全性：返回 false，不抛异常。
     */
    @Test
    public void testContainsPathNullSafe() {
        ConfigSchema schema = buildStandardSchema();
        assertFalse(schema.containsPath(null));
    }

    /**
     * Builder 可重用性：同一 Builder 多次 section/build。
     * 验证当前行为：Builder 内部 sections 列表累积，第二次 build 包含全部累积 section。
     * 这意味着 Builder 实际不可重用，多次 build 会叠加内容。
     */
    @Test
    public void testBuilderReuseAccumulatesSections() {
        ConfigSchema.Builder b = ConfigSchema.builder("mod");
        b.section("s1").string("a").build().endSection();
        ConfigSchema first = b.build();

        b.section("s2").string("b").build().endSection();
        ConfigSchema second = b.build();

        // 第一次 build 只含 s1
        assertEquals(1, first.sections().size());
        // 验证当前行为：第二次 build 含 s1 + s2（累积）
        assertEquals(2, second.sections().size());
        assertNotNull(second.field("s1.a"));
        assertNotNull(second.field("s2.b"));
    }

    /**
     * CHOICE options 为空数组 new String[0]：build 抛异常（无法推断默认值）。
     */
    @Test(expected = IllegalArgumentException.class)
    public void testChoiceEmptyOptionsArray() {
        ConfigSchema.builder("mod")
            .section("s")
                .choice("k").options(new String[0]).build()
            .endSection()
            .build();
    }

    /**
     * NUMBER range min=max：default=min 时 build 通过。
     * 验证当前行为：build 阶段不校验 range，只校验类型。
     */
    @Test
    public void testNumberRangeMinMaxDefaultAtMin() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s")
                .number("k").range(5, 5).defaultValue(5).build()
            .endSection()
            .build();

        FieldSpec f = schema.field("s.k");
        assertEquals(5.0, ((Number) f.defaultValue()).doubleValue(), 0.0);
        assertEquals(5.0, f.constraints().min(), 0.0);
        assertEquals(5.0, f.constraints().max(), 0.0);
    }

    /**
     * NUMBER range min=max，default=min-0.001：build 抛异常（range 校验在 build 阶段拦截）。
     */
    @Test(expected = IllegalArgumentException.class)
    public void testNumberRangeMinMaxDefaultBelowMinBuildsAnyway() {
        ConfigSchema.builder("mod")
            .section("s")
                .number("k").range(5, 5).defaultValue(4.999).build()
            .endSection()
            .build();
    }

    /**
     * STRING maxLength=0：build 成功，constraints.maxLength()==0。
     * 验证当前行为：build 不校验长度，maxLength=0 被原样存储。
     * 注意：DraftBuffer.validateAll 中 maxLength>0 才校验，maxLength=0 等于不限制。
     */
    @Test
    public void testStringMaxLengthZeroBuilds() {
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s")
                .string("k").maxLength(0).defaultValue("").build()
            .endSection()
            .build();

        assertEquals(0, schema.field("s.k").constraints().maxLength());
    }

    /**
     * FieldConstraints.none() 默认值：min=负无穷、max=正无穷、maxLength=-1、choices=null、required=false。
     */
    @Test
    public void testFieldConstraintsNoneDefaults() {
        FieldConstraints c = FieldConstraints.none();
        assertEquals(Double.NEGATIVE_INFINITY, c.min(), 0.0);
        assertEquals(Double.POSITIVE_INFINITY, c.max(), 0.0);
        assertEquals(-1, c.maxLength());
        assertNull(c.choices());
        assertFalse(c.required());
    }

    // ===== ConfigSchema title 字段（m2）=====

    /**
     * ConfigSchema title 缺省回退 modId。
     */
    @Test
    public void testSchemaTitleDefaultFallbackModId() {
        ConfigSchema schema = ConfigSchema.builder("my_mod")
            .section("s")
                .string("a").build()
            .endSection()
            .build();
        assertEquals("title 缺省回退 modId", "my_mod", schema.title());
    }

    /**
     * ConfigSchema title 显式设置生效。
     */
    @Test
    public void testSchemaTitleExplicit() {
        ConfigSchema schema = ConfigSchema.builder("my_mod")
            .title("我的模组配置")
            .section("s")
                .string("a").build()
            .endSection()
            .build();
        assertEquals("title 显式设置生效", "我的模组配置", schema.title());
        assertEquals("modId 不受 title 影响", "my_mod", schema.modId());
    }

    /**
     * ConfigSchema title 传 null 时回退 modId。
     */
    @Test
    public void testSchemaTitleNullFallbackModId() {
        ConfigSchema schema = new ConfigSchema("mod_id", null, java.util.Collections.emptyList());
        assertEquals("title=null 回退 modId", "mod_id", schema.title());
    }
}
