package club.heiqi.config.runtime;

import club.heiqi.config.schema.ConfigSchema;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link DraftBuffer} 边界用例测试，覆盖 null/同值/多次 setDraft、非 Schema 字段、
 * NUMBER/STRING/CHOICE 边界校验、required、reset/commit 隔离等。
 */
public class DraftBufferEdgeCaseTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Authority defaultAuthority() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        return Authority.load(file, schema);
    }

    private static void write(File file, String content) throws Exception {
        FileWriter w = new FileWriter(file);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }

    /**
     * setDraft null 值：isDirty 为 true（null != current）。
     */
    @Test
    public void setDraftNullMarksDirty() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        draft.setDraft("server.host", null);
        assertTrue(draft.isDirty("server.host"));
    }

    /**
     * setDraft 同值不 dirty。
     */
    @Test
    public void setDraftSameValueNotDirty() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        draft.setDraft("server.host", "localhost");
        assertFalse(draft.isDirty("server.host"));
    }

    /**
     * 多次 setDraft 同一字段：最后一次为准。
     */
    @Test
    public void multipleSetDraftLastWins() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        draft.setDraft("server.host", "a");
        draft.setDraft("server.host", "b");
        draft.setDraft("server.host", "c");
        assertEquals("c", draft.getDraft("server.host"));
    }

    /**
     * setDraft 非 Schema 字段。
     * 验证当前行为：setDraft 不校验 path 是否在 schema，直接 put 到 draftValues。
     * isDirtyAny 只遍历 schema 字段，不反映非 Schema 字段改动。
     */
    @Test
    public void setDraftNonSchemaField() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: localhost\n  port: 8080\n  debug: false\n  mode: online\n" +
                "extra:\n  key: value\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);
        DraftBuffer draft = DraftBuffer.from(authority);

        // 验证当前行为：非 Schema path 可 setDraft，不抛异常
        draft.setDraft("extra.custom", "x");
        assertEquals("x", draft.getDraft("extra.custom"));
        // isDirtyAny 不反映非 Schema 字段
        assertFalse(draft.isDirtyAny());
    }

    /**
     * validateAll 空 schema：无错误。
     */
    @Test
    public void validateAllEmptySchemaNoError() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        Authority authority = Authority.load(file, SchemaTestFactory.emptySchema());
        DraftBuffer draft = DraftBuffer.from(authority);

        ValidationResult result = draft.validateAll();
        assertFalse(result.hasErrors());
        assertFalse(draft.hasError());
    }

    /**
     * validateAll 部分非法：1 个字段非法，hasError true，error(path) 返回错误，其他字段 error 返回 null。
     */
    @Test
    public void validateAllPartialInvalid() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        draft.setDraft("server.port", 99999.0); // 超范围
        draft.setDraft("server.host", "valid.host"); // 合法

        ValidationResult result = draft.validateAll();
        assertTrue(result.hasErrors());
        assertNotNull(result.errorFor("server.port"));
        assertNull(result.errorFor("server.host"));
    }

    /**
     * NUMBER 边界值：draft=min 通过，draft=max 通过，draft=min-epsilon 失败，draft=max+epsilon 失败。
     */
    @Test
    public void numberBoundaryValues() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        // server.port range(1, 65535)
        draft.setDraft("server.port", 1.0);
        assertFalse(draft.validateAll().hasErrors());

        draft.setDraft("server.port", 65535.0);
        assertFalse(draft.validateAll().hasErrors());

        draft.setDraft("server.port", 0.0);
        assertTrue(draft.validateAll().hasErrors());

        draft.setDraft("server.port", 65536.0);
        assertTrue(draft.validateAll().hasErrors());
    }

    /** NUMBER 的 NaN、Infinity 与对应字符串均 fail-closed。 */
    @Test
    public void numberNonFiniteValuesAreInvalid() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        Object[] invalid = new Object[] {
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
                "NaN",
                "Infinity",
                "-Infinity"
        };
        for (Object value : invalid) {
            draft.setDraft("server.port", value);
            assertTrue("非有限 NUMBER 应失败: " + value, draft.validateAll().hasErrors());
        }
    }

    /**
     * NUMBER 负数：range(-100, 100)，draft=-50 通过，draft=-101 失败。
     */
    @Test
    public void numberNegativeInRange() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s")
                .number("n").range(-100, 100).defaultValue(0.0).build()
            .endSection()
            .build();
        Authority authority = Authority.load(file, schema);
        DraftBuffer draft = DraftBuffer.from(authority);

        draft.setDraft("s.n", -50.0);
        assertFalse(draft.validateAll().hasErrors());

        draft.setDraft("s.n", -101.0);
        assertTrue(draft.validateAll().hasErrors());
    }

    /**
     * STRING maxLength=0：空串通过，非空串也通过。
     * 验证当前行为：validateField 中 maxLength>0 才校验，maxLength=0 等于不限制。
     */
    @Test
    public void stringMaxLengthZeroNoValidation() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s")
                .string("str").maxLength(0).defaultValue("").build()
            .endSection()
            .build();
        Authority authority = Authority.load(file, schema);
        DraftBuffer draft = DraftBuffer.from(authority);

        // maxLength=0 现在校验（>= 0），空串通过
        draft.setDraft("s.str", "");
        assertFalse(draft.validateAll().hasErrors());

        // maxLength=0 时非空串失败
        draft.setDraft("s.str", "very long string exceeds zero");
        assertTrue(draft.validateAll().hasErrors());
    }

    /**
     * STRING 超长：maxLength=10，draft 11 字符失败，10 字符通过。
     */
    @Test
    public void stringOverMaxLength() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s")
                .string("str").maxLength(10).defaultValue("ok").build()
            .endSection()
            .build();
        Authority authority = Authority.load(file, schema);
        DraftBuffer draft = DraftBuffer.from(authority);

        draft.setDraft("s.str", "12345678901"); // 11 字符
        assertTrue(draft.validateAll().hasErrors());
        assertNotNull(draft.validateAll().errorFor("s.str"));

        draft.setDraft("s.str", "1234567890"); // 10 字符
        assertFalse(draft.validateAll().hasErrors());
    }

    /**
     * CHOICE 空字符串不在 options：required 字段 draft="" 失败（required 校验先触发）。
     */
    @Test
    public void choiceEmptyStringNotInOptions() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        draft.setDraft("server.mode", "");

        ValidationResult result = draft.validateAll();
        assertTrue(result.hasErrors());
        assertNotNull(result.errorFor("server.mode"));
    }

    /**
     * CHOICE null draft（非 required）。
     * 验证当前行为：value==null 时 required 校验跳过，随后 `if (value==null) return null` 直接无错。
     */
    @Test
    public void choiceNullDraftNotRequiredNoError() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s")
                .choice("c").options("A", "B").defaultValue("A").build()
            .endSection()
            .build();
        Authority authority = Authority.load(file, schema);
        DraftBuffer draft = DraftBuffer.from(authority);

        draft.setDraft("s.c", null);
        // 验证当前行为：非 required CHOICE + null draft 不报错
        assertFalse(draft.validateAll().hasErrors());
    }

    /**
     * CHOICE null draft（required）：失败。
     */
    @Test
    public void choiceNullDraftRequiredError() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = ConfigSchema.builder("mod")
            .section("s")
                .choice("c").options("A", "B").defaultValue("A").required().build()
            .endSection()
            .build();
        Authority authority = Authority.load(file, schema);
        DraftBuffer draft = DraftBuffer.from(authority);

        draft.setDraft("s.c", null);
        assertTrue(draft.validateAll().hasErrors());
    }

    /**
     * required + null draft：失败。
     */
    @Test
    public void requiredNullDraftError() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        draft.setDraft("server.host", null);
        assertTrue(draft.validateAll().hasErrors());
    }

    /**
     * required + 空串 draft：失败。
     */
    @Test
    public void requiredEmptyStringError() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        draft.setDraft("server.host", "");
        assertTrue(draft.validateAll().hasErrors());
    }

    /**
     * resetFieldToDefault 后 isDirty 为 true（当 current != 默认值时）。
     */
    @Test
    public void resetFieldToDefaultMarksDirtyWhenCurrentDiffers() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: real.host\n  port: 8080\n  debug: false\n  mode: online\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);
        DraftBuffer draft = DraftBuffer.from(authority);

        // current = real.host，默认值 = localhost
        assertEquals("real.host", draft.getCurrent("server.host"));
        draft.setDraft("server.host", "edited");
        draft.resetFieldToDefault("server.host");

        assertEquals("localhost", draft.getDraft("server.host"));
        assertEquals("real.host", draft.getCurrent("server.host"));
        assertTrue(draft.isDirty("server.host"));
    }

    /**
     * resetToCurrent 后 isDirtyAny 为 false。
     */
    @Test
    public void resetToCurrentClearsDirtyAny() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        draft.setDraft("server.host", "x");
        draft.setDraft("server.port", 1.0);
        assertTrue(draft.isDirtyAny());

        draft.resetToCurrent();
        assertFalse(draft.isDirtyAny());
    }

    /**
     * commitDraftToCurrent 后 isDirtyAny 为 false。
     */
    @Test
    public void commitDraftToCurrentClearsDirtyAny() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        draft.setDraft("server.host", "committed");
        assertTrue(draft.isDirtyAny());

        draft.commitDraftToCurrent();
        assertFalse(draft.isDirtyAny());
    }

    /**
     * commitDraftToCurrent 后 getCurrent 返回 draft 值。
     */
    @Test
    public void commitDraftToCurrentReturnsDraftValue() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        draft.setDraft("server.host", "new.value");
        draft.setDraft("server.port", 7000.0);

        draft.commitDraftToCurrent();
        assertEquals("new.value", draft.getCurrent("server.host"));
        assertEquals(7000.0, draft.getCurrent("server.port"));
    }

    /**
     * draftSnapshot 修改不影响 DraftBuffer（含 current）。
     */
    @Test
    public void draftSnapshotModificationDoesNotAffectBuffer() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        draft.setDraft("server.host", "draft.value");

        Map<String, Object> snapshot = draft.draftSnapshot();
        snapshot.put("server.host", "mutated");
        snapshot.remove("server.port");

        assertEquals("draft.value", draft.getDraft("server.host"));
        assertEquals(8080.0, draft.getDraft("server.port"));
        assertEquals("localhost", draft.getCurrent("server.host"));
    }

    /**
     * from 后 current 不可变：draftSnapshot 修改不影响 getCurrent。
     */
    @Test
    public void currentImmutableAfterFrom() throws Exception {
        DraftBuffer draft = DraftBuffer.from(defaultAuthority());
        Object port = draft.getCurrent("server.port");
        assertEquals(8080.0, port);

        // 修改 draftSnapshot 不影响 getCurrent
        Map<String, Object> snapshot = draft.draftSnapshot();
        snapshot.put("server.port", 9999.0);

        assertEquals(8080.0, draft.getCurrent("server.port"));
    }
}
