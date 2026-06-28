package club.heiqi.config.runtime;

import club.heiqi.config.schema.ConfigSchema;

import java.io.File;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link DraftBuffer} 测试，覆盖 from 深拷贝、setDraft/isDirty、isDirtyAny、
 * validateAll、resetToCurrent、resetFieldToDefault、commitDraftToCurrent、三态隔离。
 */
public class DraftBufferTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Authority defaultAuthority() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        return Authority.load(file, schema);
    }

    /**
     * from 深拷贝：draft=current=authority 值，且是不同 Map 实例。
     */
    @Test
    public void fromSeedsCurrentAndDraft() throws Exception {
        Authority authority = defaultAuthority();
        DraftBuffer draft = DraftBuffer.from(authority);

        assertEquals("localhost", draft.getDraft("server.host"));
        assertEquals("localhost", draft.getCurrent("server.host"));
        assertEquals(8080.0, draft.getDraft("server.port"));
        assertEquals(8080.0, draft.getCurrent("server.port"));

        // 修改 draft 不应影响 current
        draft.setDraft("server.host", "changed");
        assertEquals("changed", draft.getDraft("server.host"));
        assertEquals("localhost", draft.getCurrent("server.host"));
    }

    /**
     * setDraft 后 isDirty 为 true。
     */
    @Test
    public void setDraftMarksDirty() throws Exception {
        Authority authority = defaultAuthority();
        DraftBuffer draft = DraftBuffer.from(authority);

        assertFalse(draft.isDirty("server.host"));
        draft.setDraft("server.host", "new.host");
        assertTrue(draft.isDirty("server.host"));
    }

    /**
     * isDirtyAny：任一字段脏则 true。
     */
    @Test
    public void isDirtyAnyTrueWhenAnyFieldDirty() throws Exception {
        Authority authority = defaultAuthority();
        DraftBuffer draft = DraftBuffer.from(authority);

        assertFalse(draft.isDirtyAny());
        draft.setDraft("server.debug", true);
        assertTrue(draft.isDirtyAny());
    }

    /**
     * validateAll：NUMBER 超范围有错。
     */
    @Test
    public void validateAllNumberOutOfRange() throws Exception {
        Authority authority = defaultAuthority();
        DraftBuffer draft = DraftBuffer.from(authority);

        draft.setDraft("server.port", 99999.0); // max=65535
        ValidationResult result = draft.validateAll();
        assertTrue("超范围应有错", result.hasErrors());
        assertTrue(result.errorFor("server.port") != null);
    }

    /**
     * validateAll：CHOICE 不在 options 有错。
     */
    @Test
    public void validateAllChoiceNotInOptions() throws Exception {
        Authority authority = defaultAuthority();
        DraftBuffer draft = DraftBuffer.from(authority);

        draft.setDraft("server.mode", "unknown");
        ValidationResult result = draft.validateAll();
        assertTrue("CHOICE 不在选项应有错", result.hasErrors());
        assertTrue(result.errorFor("server.mode") != null);
    }

    /**
     * validateAll：required 空值有错。
     */
    @Test
    public void validateAllRequiredEmpty() throws Exception {
        Authority authority = defaultAuthority();
        DraftBuffer draft = DraftBuffer.from(authority);

        draft.setDraft("server.host", "");
        ValidationResult result = draft.validateAll();
        assertTrue("required 空值应有错", result.hasErrors());
        assertTrue(result.errorFor("server.host") != null);
    }

    /**
     * validateAll：合法值无错。
     */
    @Test
    public void validateAllValidValuesNoError() throws Exception {
        Authority authority = defaultAuthority();
        DraftBuffer draft = DraftBuffer.from(authority);

        draft.setDraft("server.host", "valid.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");
        ValidationResult result = draft.validateAll();
        assertFalse("合法值不应有错: " + result.errors(), result.hasErrors());
    }

    /**
     * resetToCurrent：draft 全回拷 current。
     */
    @Test
    public void resetToCurrentRestoresDraft() throws Exception {
        Authority authority = defaultAuthority();
        DraftBuffer draft = DraftBuffer.from(authority);

        draft.setDraft("server.host", "temp");
        draft.setDraft("server.port", 1.0);
        assertTrue(draft.isDirtyAny());

        draft.resetToCurrent();
        assertFalse(draft.isDirtyAny());
        assertEquals("localhost", draft.getDraft("server.host"));
    }

    /**
     * resetFieldToDefault：draft=默认值，current 不变。
     */
    @Test
    public void resetFieldToDefaultSetsDraftOnly() throws Exception {
        Authority authority = defaultAuthority();
        DraftBuffer draft = DraftBuffer.from(authority);

        draft.setDraft("server.host", "edited");
        draft.resetFieldToDefault("server.host");

        assertEquals("localhost", draft.getDraft("server.host"));
        assertEquals("localhost", draft.getCurrent("server.host"));
    }

    /**
     * commitDraftToCurrent：current=draft。
     */
    @Test
    public void commitDraftToCurrentSyncsCurrent() throws Exception {
        Authority authority = defaultAuthority();
        DraftBuffer draft = DraftBuffer.from(authority);

        draft.setDraft("server.host", "committed");
        draft.setDraft("server.port", 5000.0);
        assertTrue(draft.isDirtyAny());

        draft.commitDraftToCurrent();
        assertFalse(draft.isDirtyAny());
        assertEquals("committed", draft.getCurrent("server.host"));
        assertEquals(5000.0, draft.getCurrent("server.port"));
    }

    /**
     * 三态隔离：setDraft 后 getCurrent 不变，draftSnapshot 独立。
     */
    @Test
    public void threeStateIsolation() throws Exception {
        Authority authority = defaultAuthority();
        DraftBuffer draft = DraftBuffer.from(authority);

        draft.setDraft("server.host", "draft.value");
        assertEquals("draft.value", draft.getDraft("server.host"));
        assertEquals("localhost", draft.getCurrent("server.host"));

        Map<String, Object> snapshot = draft.draftSnapshot();
        snapshot.put("server.host", "snapshot.value");
        assertEquals("draft.value", draft.getDraft("server.host"));
    }

    /**
     * fieldPaths 返回全部 schema 字段路径。
     */
    @Test
    public void fieldPathsReturnsAllSchemaFields() throws Exception {
        Authority authority = defaultAuthority();
        DraftBuffer draft = DraftBuffer.from(authority);

        assertEquals(4, draft.fieldPaths().size());
        assertTrue(draft.fieldPaths().contains("server.host"));
        assertTrue(draft.fieldPaths().contains("server.port"));
        assertTrue(draft.fieldPaths().contains("server.debug"));
        assertTrue(draft.fieldPaths().contains("server.mode"));
    }
}
