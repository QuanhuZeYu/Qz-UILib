package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigChangeListener;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSource;
import club.heiqi.config.schema.ConfigSchema;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 结构化 {@link SaveOutcome.ConflictType} 与事务 base 回归。
 *
 * <p>断言读 conflictType / requiresReload，禁止依赖英文字符串匹配。</p>
 */
public class ConfigStaleDraftRecoveryTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static void write(File file, String content) throws Exception {
        FileWriter w = new FileWriter(file);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }

    /** 两个同源 draft：后保存者 STALE_DRAFT_BASE，requiresReload=true，保留编辑。 */
    @Test
    public void staleDraftBaseConflictTypeAndRequiresReload() throws Exception {
        File file = tempFolder.newFile("stale-base.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer first = manager.openDraft();
        DraftBuffer stale = manager.openDraft();
        first.setDraft("server.host", "first.host");
        stale.setDraft("server.host", "stale.host");

        assertTrue(manager.save(first).isSuccess());
        SaveOutcome second = manager.save(stale);

        assertEquals(SaveOutcome.Status.INVALID, second.status());
        assertTrue(second.isConflict());
        assertEquals(SaveOutcome.ConflictType.STALE_DRAFT_BASE, second.conflictType());
        assertTrue(second.requiresReload());
        assertEquals("first.host", manager.authority().getString("server.host"));
        assertEquals("stale.host", stale.getDraft("server.host"));
        assertEquals("localhost", stale.getCurrent("server.host"));
        // base 仍为 open 时 Authority，不因 setDraft 改变
        assertEquals("localhost", stale.getBase("server.host"));
    }

    /** 同 draft 连续 save：第二次无 dirty 仍 OK；中间再编辑后仍可保存。 */
    @Test
    public void sameDraftConsecutiveSavesOk() throws Exception {
        File file = tempFolder.newFile("consecutive.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "once.host");
        draft.setDraft("server.mode", "test");
        assertTrue(manager.save(draft).isSuccess());
        assertFalse(draft.isDirtyAny());
        assertTrue(manager.save(draft).isSuccess());

        draft.setDraft("server.host", "twice.host");
        SaveOutcome third = manager.save(draft);
        assertTrue(third.isSuccess());
        assertEquals("twice.host", manager.authority().getString("server.host"));
        // 成功 commit 后 base 推进
        assertEquals("twice.host", draft.getBase("server.host"));
        assertEquals("twice.host", draft.getCurrent("server.host"));
    }

    /** validator 改 draft → DRAFT_MODIFIED_DURING_SAVE，requiresReload=false，保留闭包编辑。 */
    @Test(timeout = 5000L)
    public void draftModifiedDuringSaveConflictType() throws Exception {
        File file = tempFolder.newFile("draft-mod.yaml");
        write(file, "server:\n  host: original.host\n  port: 8080\n  debug: false\n  mode: online\n");
        final DraftBuffer[] holder = new DraftBuffer[1];
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView view) {
                        holder[0].setDraft("server.host", "mutated-by-validator");
                        return ValidationResult.ok();
                    }
                });
        DraftBuffer draft = manager.openDraft();
        holder[0] = draft;
        draft.setDraft("server.host", "user.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertTrue(outcome.isConflict());
        assertEquals(SaveOutcome.ConflictType.DRAFT_MODIFIED_DURING_SAVE, outcome.conflictType());
        assertFalse(outcome.requiresReload());
        assertEquals("mutated-by-validator", draft.getDraft("server.host"));
        assertEquals("original.host", manager.authority().getString("server.host"));
    }

    /** validator 经 legacy 改 Authority → AUTHORITY_MODIFIED_DURING_SAVE，requiresReload=true。 */
    @Test
    public void authorityModifiedDuringSaveConflictType() throws Exception {
        File file = tempFolder.newFile("auth-mod.yaml");
        write(file, "server:\n  host: original.host\n  port: 8080\n  debug: false\n  mode: online\n");
        final ConfigManager[] mgr = new ConfigManager[1];
        mgr[0] = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView view) {
                        try {
                            mgr[0].authority().legacy().setRawJson("extra", "nested:\n  value: hijack\n");
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        return ValidationResult.ok();
                    }
                });
        DraftBuffer draft = mgr[0].openDraft();
        draft.setDraft("server.host", "user.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = mgr[0].save(draft);
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertTrue(outcome.isConflict());
        assertEquals(SaveOutcome.ConflictType.AUTHORITY_MODIFIED_DURING_SAVE, outcome.conflictType());
        assertTrue(outcome.requiresReload());
        assertEquals("user.host", draft.getDraft("server.host"));
        assertEquals("original.host", mgr[0].authority().getString("server.host"));
    }

    /** BATCH_SAVE 通知期内 save → SAVE_DURING_NOTIFICATION，requiresReload=false。 */
    @Test
    public void saveDuringNotificationConflictType() throws Exception {
        File file = tempFolder.newFile("notify.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        final AtomicReference<SaveOutcome> nested = new AtomicReference<SaveOutcome>();
        manager.eventBus().subscribe(new ConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigChangeEvent event) {
                nested.set(manager.save(manager.openDraft()));
            }
        });
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "event.host");
        assertTrue(manager.save(draft).isSuccess());

        assertNotNull(nested.get());
        assertEquals(SaveOutcome.Status.INVALID, nested.get().status());
        assertTrue(nested.get().isConflict());
        assertEquals(SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION, nested.get().conflictType());
        assertFalse(nested.get().requiresReload());
    }

    /** setDraftAndCurrent 不改事务 base；capture base 仍对齐 open 时 Authority。 */
    @Test
    public void setDraftAndCurrentDoesNotMoveTransactionBase() throws Exception {
        File file = tempFolder.newFile("base-stable.yaml");
        write(file, "server:\n  host: original.host\n  port: 8080\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        assertEquals("original.host", draft.getBase("server.host"));

        draft.setDraftAndCurrent("server.host", "prefill.host");
        assertEquals("prefill.host", draft.getDraft("server.host"));
        assertEquals("prefill.host", draft.getCurrent("server.host"));
        assertEquals("original.host", draft.getBase("server.host"));
        assertFalse(draft.isDirty("server.host"));

        // 另一 draft 先提交改变 Authority → 本 draft 仍 stale（base 未动）
        DraftBuffer other = manager.openDraft();
        other.setDraft("server.host", "other.host");
        other.setDraft("server.mode", "test");
        assertTrue(manager.save(other).isSuccess());

        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");
        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.ConflictType.STALE_DRAFT_BASE, outcome.conflictType());
        assertTrue(outcome.requiresReload());
    }

    /** 成功 commit 后 base/current/draft 三份对齐。 */
    @Test
    public void successfulCommitAdvancesBaseCurrentAndDraft() throws Exception {
        File file = tempFolder.newFile("commit-three.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "committed.host");
        draft.setDraft("server.mode", "test");
        assertTrue(manager.save(draft).isSuccess());
        assertEquals("committed.host", draft.getBase("server.host"));
        assertEquals("committed.host", draft.getCurrent("server.host"));
        assertEquals("committed.host", draft.getDraft("server.host"));
        assertFalse(draft.isDirtyAny());
    }

    /** 普通校验失败 conflictType=NONE，isConflict=false。 */
    @Test
    public void validationFailureIsNotConflict() throws Exception {
        File file = tempFolder.newFile("validation.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.port", 99999.0);
        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertFalse(outcome.isConflict());
        assertEquals(SaveOutcome.ConflictType.NONE, outcome.conflictType());
        assertFalse(outcome.requiresReload());
    }
}
