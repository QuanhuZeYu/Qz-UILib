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
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ConfigManager} 事务与 {@link ConfigEventBus} 边界测试，覆盖 save 后状态一致性、
 * IO 失败零提交、事件总线多监听器/异常隔离/重复订阅、bootstrap 幂等等。
 */
public class ConfigManagerTransactionTest {

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

    /**
     * save 成功后 openDraft 是新副本：新 draft 的 current = 保存后的值。
     */
    @Test
    public void openDraftAfterSaveReflectsNewValues() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "saved.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");
        manager.save(draft);

        // 再次 openDraft，新 draft 的 current 应为保存后的值
        DraftBuffer newDraft = manager.openDraft();
        assertEquals("saved.host", newDraft.getCurrent("server.host"));
        assertEquals(3000.0, newDraft.getCurrent("server.port"));
        assertFalse(newDraft.isDirtyAny());
    }

    /**
     * save 成功后 DraftBuffer.current 已更新。
     */
    @Test
    public void saveUpdatesDraftCurrent() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "committed.host");
        draft.setDraft("server.mode", "test");
        manager.save(draft);

        assertEquals("committed.host", draft.getCurrent("server.host"));
        assertEquals("test", draft.getCurrent("server.mode"));
        assertFalse(draft.isDirtyAny());
    }

    /**
     * save 无改动时仍返回 OK。
     */
    @Test
    public void saveNoChangesReturnsOk() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        // 无任何 setDraft
        SaveOutcome outcome = manager.save(draft);

        assertTrue(outcome.isSuccess());
        assertEquals(SaveOutcome.Status.OK, outcome.status());
    }

    /**
     * save 后再次 save 同一 draft：第二次无 dirty，返回 OK。
     */
    @Test
    public void saveTwiceSameDraftSecondNoDirty() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "once.host");
        draft.setDraft("server.mode", "test");
        SaveOutcome first = manager.save(draft);
        assertTrue(first.isSuccess());

        // 第二次 save 同一 draft，current 已 commit，无 dirty
        assertFalse(draft.isDirtyAny());
        SaveOutcome second = manager.save(draft);
        assertTrue(second.isSuccess());
        assertEquals(SaveOutcome.Status.OK, second.status());
    }

    /**
     * save 校验失败后 authority 不变。
     */
    @Test
    public void saveValidationFailureAuthorityUnchanged() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: original.host\n  port: 8080\n  debug: false\n  mode: online\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.port", 99999.0);
        manager.save(draft);

        assertEquals("original.host", manager.authority().getString("server.host"));
        assertEquals(8080.0, manager.authority().getNumber("server.port"), 0.0);
    }

    /**
     * save 校验失败后文件不变。
     */
    @Test
    public void saveValidationFailureFileUnchanged() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: original.host\n  port: 8080\n  debug: false\n  mode: online\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.port", 99999.0);
        manager.save(draft);

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("original.host", reloaded.get("server.host").asString());
        assertEquals(8080, reloaded.get("server.port").asInt());
    }

    /**
     * save IO 失败后 DraftBuffer 不变：commitDraftToCurrent 未执行，current 仍为原值。
     */
    @Test
    public void saveIoFailureDraftBufferUnchanged() throws Exception {
        File dir = tempFolder.newFolder("not_a_file");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(dir, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "should.not.persist");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");
        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.IO_FAILED, outcome.status());
        // current 未被 commit，仍为原值
        assertEquals("localhost", draft.getCurrent("server.host"));
        assertEquals(8080.0, draft.getCurrent("server.port"));
    }

    /**
     * save IO 失败后 eventBus 无事件。
     */
    @Test
    public void saveIoFailureNoEventPublished() throws Exception {
        File dir = tempFolder.newFolder("not_a_file");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(dir, schema);

        AtomicReference<ConfigChangeEvent> received = new AtomicReference<ConfigChangeEvent>();
        manager.eventBus().subscribe(new ConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigChangeEvent event) {
                received.set(event);
            }
        });

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "x");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");
        manager.save(draft);

        assertNull("IO 失败不应发布事件", received.get());
    }

    /**
     * flushRaw 无改动时文件可 load 且含默认值（round-trip 等价）。
     */
    @Test
    public void flushRawNoChangesRoundTrip() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        manager.flushRaw();

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("localhost", reloaded.get("server.host").asString());
        assertEquals(8080, reloaded.get("server.port").asInt());
    }

    /**
     * bootstrap 空文件后 save：空文件启动，改字段，save，文件写入正确。
     */
    @Test
    public void bootstrapEmptyFileThenSave() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        // 空文件
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "first.host");
        draft.setDraft("server.port", 4000.0);
        draft.setDraft("server.mode", "offline");
        SaveOutcome outcome = manager.save(draft);

        assertTrue(outcome.isSuccess());
        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("first.host", reloaded.get("server.host").asString());
        assertEquals(4000, reloaded.get("server.port").asInt());
        assertEquals("offline", reloaded.get("server.mode").asString());
    }

    /**
     * 多次 bootstrap 同一文件：两次 authority.get 值一致。
     */
    @Test
    public void multipleBootstrapSameFileConsistent() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: stable.host\n  port: 9000\n  debug: true\n  mode: test\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        ConfigManager m1 = ConfigManager.bootstrap(file, schema);
        ConfigManager m2 = ConfigManager.bootstrap(file, schema);

        assertEquals(m1.authority().getString("server.host"), m2.authority().getString("server.host"));
        assertEquals(m1.authority().getNumber("server.port"), m2.authority().getNumber("server.port"), 0.0);
        assertEquals(m1.authority().getBool("server.debug"), m2.authority().getBool("server.debug"));
        assertEquals("stable.host", m1.authority().getString("server.host"));
    }

    /**
     * ConfigEventBus 多监听器：3 个监听器都收到事件。
     */
    @Test
    public void eventBusMultipleListenersAllNotified() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        final AtomicInteger count = new AtomicInteger(0);
        for (int i = 0; i < 3; i++) {
            manager.eventBus().subscribe(new ConfigChangeListener() {
                @Override
                public void onConfigChanged(ConfigChangeEvent event) {
                    count.incrementAndGet();
                }
            });
        }

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "x");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");
        manager.save(draft);

        assertEquals(3, count.get());
    }

    /**
     * ConfigEventBus 监听器异常隔离：一个抛异常，其他仍收到事件。
     */
    @Test
    public void eventBusListenerExceptionIsolated() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        final AtomicReference<ConfigChangeEvent> secondReceived = new AtomicReference<ConfigChangeEvent>();
        manager.eventBus().subscribe(new ConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigChangeEvent event) {
                throw new RuntimeException("故意抛异常");
            }
        });
        manager.eventBus().subscribe(new ConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigChangeEvent event) {
                secondReceived.set(event);
            }
        });

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "x");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");
        manager.save(draft);

        assertNotNull("异常监听器不应阻断其他监听器", secondReceived.get());
    }

    /**
     * ConfigEventBus unsubscribe：取消后不再收到事件。
     */
    @Test
    public void eventBusUnsubscribeStopsNotification() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        final AtomicInteger count = new AtomicInteger(0);
        ConfigChangeListener listener = new ConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigChangeEvent event) {
                count.incrementAndGet();
            }
        };
        manager.eventBus().subscribe(listener);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "a");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");
        manager.save(draft);
        assertEquals(1, count.get());

        manager.eventBus().unsubscribe(listener);
        DraftBuffer draft2 = manager.openDraft();
        draft2.setDraft("server.host", "b");
        draft2.setDraft("server.port", 4000.0);
        draft2.setDraft("server.mode", "test");
        manager.save(draft2);
        assertEquals("取消订阅后不应再收到事件", 1, count.get());
    }

    /**
     * ConfigEventBus 重复 subscribe 同一监听器实例。
     * 验证当前行为：subscribe 用 contains 去重，同一实例只注册一次，事件只收到一次。
     */
    @Test
    public void eventBusDuplicateSubscribeOnlyOnce() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        final AtomicInteger count = new AtomicInteger(0);
        ConfigChangeListener listener = new ConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigChangeEvent event) {
                count.incrementAndGet();
            }
        };
        manager.eventBus().subscribe(listener);
        manager.eventBus().subscribe(listener); // 重复

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "x");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");
        manager.save(draft);

        // 验证当前行为：去重，只收到一次
        assertEquals(1, count.get());
    }

    /**
     * openDraft 后 authority 不变：编辑 draft 不影响 authority。
     */
    @Test
    public void openDraftDoesNotAffectAuthority() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "draft.only");
        draft.setDraft("server.port", 1.0);

        assertEquals("draft.only", draft.getDraft("server.host"));
        assertEquals("localhost", manager.authority().getString("server.host"));
        assertEquals(8080.0, manager.authority().getNumber("server.port"), 0.0);
    }

    /** validator 完全锁外：并发 draft 编辑先完成，外层 save 冲突 INVALID 且保留编辑。 */
    @Test(timeout = 10000L)
    public void concurrentDraftEditCompletesDuringValidationAndCausesConflict() throws Exception {
        File file = tempFolder.newFile("concurrent-draft.yaml");
        final CountDownLatch validatorEntered = new CountDownLatch(1);
        final CountDownLatch releaseValidator = new CountDownLatch(1);
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView view) {
                        validatorEntered.countDown();
                        try {
                            if (!releaseValidator.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("validator release timed out");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("validator interrupted", e);
                        }
                        return ValidationResult.ok();
                    }
                });
        final DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "captured.host");

        final AtomicReference<SaveOutcome> saved = new AtomicReference<SaveOutcome>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread saver = new Thread(() -> {
            try {
                saved.set(manager.save(draft));
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "config-save");
        saver.start();
        assertTrue(validatorEntered.await(2, TimeUnit.SECONDS));

        final CountDownLatch editDone = new CountDownLatch(1);
        Thread editor = new Thread(() -> {
            try {
                draft.setDraft("server.host", "edited.during.validation");
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            } finally {
                editDone.countDown();
            }
        }, "config-edit");
        editor.start();
        assertTrue("validator 期间并发编辑应完成", editDone.await(2, TimeUnit.SECONDS));

        releaseValidator.countDown();
        saver.join(5000L);
        editor.join(5000L);
        assertFalse(saver.isAlive());
        assertFalse(editor.isAlive());
        assertNull(failure.get());
        assertNotNull(saved.get());
        assertEquals(SaveOutcome.Status.INVALID, saved.get().status());
        assertEquals("localhost", manager.authority().getString("server.host"));
        assertEquals("localhost", draft.getCurrent("server.host"));
        assertEquals("edited.during.validation", draft.getDraft("server.host"));
        assertTrue(draft.isDirty("server.host"));
    }

    /** validator 内可等待 worker 读取同一 draft、Authority 与 openDraft，确定性证明回调不持锁。 */
    @Test(timeout = 10000L)
    public void validatorCanWaitForWorkerReadingAllSources() throws Exception {
        File file = tempFolder.newFile("unlocked-validator.yaml");
        final AtomicReference<ConfigManager> managerRef = new AtomicReference<ConfigManager>();
        final AtomicReference<DraftBuffer> draftRef = new AtomicReference<DraftBuffer>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView view) {
                        final CountDownLatch readsDone = new CountDownLatch(1);
                        Thread reader = new Thread(() -> {
                            try {
                                assertEquals("saved.host", draftRef.get().getDraft("server.host"));
                                assertEquals("localhost", managerRef.get().authority().getString("server.host"));
                                DraftBuffer opened = managerRef.get().openDraft();
                                assertEquals("localhost", opened.getCurrent("server.host"));
                            } catch (Throwable e) {
                                failure.compareAndSet(null, e);
                            } finally {
                                readsDone.countDown();
                            }
                        }, "validator-source-reader");
                        reader.start();
                        try {
                            if (!readsDone.await(2, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("source reader blocked by validator locks");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("validator interrupted", e);
                        }
                        if (failure.get() != null) {
                            throw new IllegalStateException("source reader failed", failure.get());
                        }
                        return ValidationResult.ok();
                    }
                });
        managerRef.set(manager);
        DraftBuffer draft = manager.openDraft();
        draftRef.set(draft);
        draft.setDraft("server.host", "saved.host");

        SaveOutcome saved = manager.save(draft);

        assertNull(failure.get());
        assertTrue(saved.isSuccess());
        assertEquals("saved.host", manager.authority().getString("server.host"));
    }

    /** 两个同源 draft 依次保存时，后保存者 stale INVALID，不能覆盖先提交值。 */
    @Test
    public void laterStaleDraftCannotOverwriteFirstCommit() throws Exception {
        File file = tempFolder.newFile("stale-drafts.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer first = manager.openDraft();
        DraftBuffer stale = manager.openDraft();
        first.setDraft("server.host", "first.host");
        stale.setDraft("server.host", "stale.host");

        assertTrue(manager.save(first).isSuccess());
        SaveOutcome second = manager.save(stale);

        assertEquals(SaveOutcome.Status.INVALID, second.status());
        assertEquals(SaveOutcome.ConflictType.STALE_DRAFT_BASE, second.conflictType());
        assertTrue(second.requiresReload());
        assertEquals("first.host", manager.authority().getString("server.host"));
        assertEquals("stale.host", stale.getDraft("server.host"));
        assertEquals("localhost", stale.getCurrent("server.host"));
        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("first.host", reloaded.get("server.host").asString());
    }

    /** Persistence 的 SecurityException 映射 IO_FAILED，Authority/current/disk/event 均无副作用。 */
    @Test
    public void persistenceUncheckedFailureHasNoTransactionSideEffects() throws Exception {
        File realFile = tempFolder.newFile("security-fault.yaml");
        write(realFile, "server:\n  host: original.host\n  port: 8080\n  debug: false\n  mode: online\n");
        final byte[] before = Files.readAllBytes(realFile.toPath());
        File faultFile = new File(realFile.getPath()) {
            @Override
            public Path toPath() {
                throw new SecurityException("denied by test");
            }
        };
        ConfigManager manager = ConfigManager.bootstrap(faultFile, SchemaTestFactory.serverSchema());
        final AtomicInteger events = new AtomicInteger(0);
        manager.eventBus().subscribe(event -> events.incrementAndGet());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "edited.host");

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.IO_FAILED, outcome.status());
        assertEquals("original.host", manager.authority().getString("server.host"));
        assertEquals("original.host", draft.getCurrent("server.host"));
        assertEquals("edited.host", draft.getDraft("server.host"));
        assertArrayEquals(before, Files.readAllBytes(realFile.toPath()));
        assertEquals(0, events.get());
    }

    /** 通知期间同步重入 save 稳定 INVALID；AssertionError 不阻断后续监听器，外层仍 OK。 */
    @Test
    public void batchSaveNotificationRejectsReentryAndIsolatesAssertionError() throws Exception {
        File file = tempFolder.newFile("event-reentry.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        final AtomicInteger batchCount = new AtomicInteger(0);
        final AtomicReference<SaveOutcome> nested = new AtomicReference<SaveOutcome>();
        final AtomicInteger afterAssertion = new AtomicInteger(0);
        manager.eventBus().subscribe(event -> {
            batchCount.incrementAndGet();
            nested.set(manager.save(manager.openDraft()));
        });
        manager.eventBus().subscribe(event -> {
            throw new AssertionError("listener assertion");
        });
        manager.eventBus().subscribe(event -> afterAssertion.incrementAndGet());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "event.host");

        SaveOutcome outer = manager.save(draft);

        assertEquals(SaveOutcome.Status.OK, outer.status());
        assertNotNull(nested.get());
        assertEquals(SaveOutcome.Status.INVALID, nested.get().status());
        assertEquals(SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION, nested.get().conflictType());
        assertEquals(1, batchCount.get());
        assertEquals(1, afterAssertion.get());
    }

    /** BATCH_SAVE 在事务锁外发布，监听器可等待另一线程读取 manager。 */
    @Test(timeout = 5000L)
    public void batchSaveEventPublishedOutsideTransactionLocks() throws Exception {
        File file = tempFolder.newFile("event-lock.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        final AtomicReference<Throwable> listenerFailure = new AtomicReference<Throwable>();
        final AtomicInteger batchCount = new AtomicInteger(0);
        manager.eventBus().subscribe(new ConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigChangeEvent event) {
                batchCount.incrementAndGet();
                final CountDownLatch readDone = new CountDownLatch(1);
                Thread reader = new Thread(() -> {
                    try {
                        manager.openDraft();
                    } catch (Throwable e) {
                        listenerFailure.compareAndSet(null, e);
                    } finally {
                        readDone.countDown();
                    }
                }, "event-authority-read");
                reader.start();
                try {
                    if (!readDone.await(2, TimeUnit.SECONDS)) {
                        listenerFailure.compareAndSet(null,
                                new AssertionError("event was published while transaction lock was held"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    listenerFailure.compareAndSet(null, e);
                }
            }
        });
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "event.host");

        SaveOutcome outcome = manager.save(draft);

        assertTrue(outcome.isSuccess());
        assertNull(listenerFailure.get());
        assertEquals(1, batchCount.get());
    }

    /** 监听器等待 worker 保存时，worker 跨线程快速 INVALID，且只发布外层一次事件。 */
    @Test(timeout = 10000L)
    public void batchSaveNotificationRejectsWorkerSaveWithoutDeadlock() throws Exception {
        File file = tempFolder.newFile("event-worker-reentry.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        final AtomicInteger batchCount = new AtomicInteger(0);
        final AtomicReference<SaveOutcome> workerOutcome = new AtomicReference<SaveOutcome>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        manager.eventBus().subscribe(event -> {
            if (batchCount.incrementAndGet() != 1) {
                return;
            }
            Thread worker = new Thread(() -> {
                try {
                    workerOutcome.set(manager.save(manager.openDraft()));
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }, "event-worker-save");
            worker.start();
            try {
                worker.join(2000L);
                if (worker.isAlive()) {
                    worker.interrupt();
                    failure.compareAndSet(null, new AssertionError("worker save deadlocked"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure.compareAndSet(null, e);
            }
        });
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "event.worker.host");

        SaveOutcome outer = manager.save(draft);

        assertEquals(SaveOutcome.Status.OK, outer.status());
        assertNull(failure.get());
        assertNotNull(workerOutcome.get());
        assertEquals(SaveOutcome.Status.INVALID, workerOutcome.get().status());
        assertEquals(SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION, workerOutcome.get().conflictType());
        assertEquals(1, batchCount.get());
    }

    /** 两个已完成 capture 的 saver 竞争提交：通知窗口内 loser final verify 必须 INVALID。 */
    @Test(timeout = 15000L)
    public void concurrentCapturedSaversAllowOneCommitDuringNotificationWindow() throws Exception {
        File file = tempFolder.newFile("captured-saver-race.yaml");
        final CountDownLatch validatorsEntered = new CountDownLatch(2);
        final CountDownLatch releaseValidators = new CountDownLatch(1);
        final CountDownLatch loserReturned = new CountDownLatch(1);
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView view) {
                        validatorsEntered.countDown();
                        try {
                            if (!releaseValidators.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("validator release timed out");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("validator interrupted", e);
                        }
                        return ValidationResult.ok();
                    }
                });
        final AtomicInteger batchCount = new AtomicInteger(0);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        manager.eventBus().subscribe(event -> {
            batchCount.incrementAndGet();
            try {
                if (!loserReturned.await(5, TimeUnit.SECONDS)) {
                    failure.compareAndSet(null,
                            new AssertionError("loser did not finish during notification"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure.compareAndSet(null, e);
            }
        });

        final DraftBuffer firstDraft = manager.openDraft();
        final DraftBuffer secondDraft = manager.openDraft();
        firstDraft.setDraft("server.host", "first.concurrent.host");
        secondDraft.setDraft("server.host", "second.concurrent.host");
        final AtomicReference<SaveOutcome> firstOutcome = new AtomicReference<SaveOutcome>();
        final AtomicReference<SaveOutcome> secondOutcome = new AtomicReference<SaveOutcome>();

        Thread firstSaver = new Thread(() -> runConcurrentSave(
                manager, firstDraft, firstOutcome, loserReturned, failure), "first-captured-saver");
        Thread secondSaver = new Thread(() -> runConcurrentSave(
                manager, secondDraft, secondOutcome, loserReturned, failure), "second-captured-saver");
        firstSaver.start();
        secondSaver.start();
        assertTrue("两个 saver 均应完成 capture 并进入 validator",
                validatorsEntered.await(5, TimeUnit.SECONDS));
        releaseValidators.countDown();
        firstSaver.join(7000L);
        secondSaver.join(7000L);

        assertFalse(firstSaver.isAlive());
        assertFalse(secondSaver.isAlive());
        assertNull(failure.get());
        assertNotNull(firstOutcome.get());
        assertNotNull(secondOutcome.get());
        assertEquals(1, batchCount.get());
        int successes = (firstOutcome.get().isSuccess() ? 1 : 0)
                + (secondOutcome.get().isSuccess() ? 1 : 0);
        assertEquals(1, successes);

        DraftBuffer loserDraft = firstOutcome.get().isSuccess() ? secondDraft : firstDraft;
        SaveOutcome loserOutcome = firstOutcome.get().isSuccess() ? secondOutcome.get() : firstOutcome.get();
        String loserValue = firstOutcome.get().isSuccess()
                ? "second.concurrent.host" : "first.concurrent.host";
        String winnerValue = firstOutcome.get().isSuccess()
                ? "first.concurrent.host" : "second.concurrent.host";
        assertEquals(SaveOutcome.Status.INVALID, loserOutcome.status());
        // 竞争窗口：双方均已 capture；loser final verify 为 AUTHORITY_MODIFIED 或 SAVE_DURING_NOTIFICATION
        // （本窗口不会是 STALE_DRAFT_BASE——capture 时 base 均有效）
        SaveOutcome.ConflictType loserType = loserOutcome.conflictType();
        assertTrue("loser 应为 AUTHORITY_MODIFIED 或 SAVE_DURING_NOTIFICATION，实际=" + loserType,
                loserType == SaveOutcome.ConflictType.AUTHORITY_MODIFIED_DURING_SAVE
                        || loserType == SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION);
        assertFalse(loserType == SaveOutcome.ConflictType.STALE_DRAFT_BASE);
        assertFalse(loserType == SaveOutcome.ConflictType.DRAFT_OWNER_MISMATCH);
        assertEquals("localhost", loserDraft.getCurrent("server.host"));
        assertEquals(loserValue, loserDraft.getDraft("server.host"));
        assertTrue(loserDraft.isDirty("server.host"));
        assertEquals(winnerValue, manager.authority().getString("server.host"));
        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals(winnerValue, reloaded.get("server.host").asString());
    }


    /** 执行并发保存，并在 INVALID 返回时释放通知 listener。 */
    private static void runConcurrentSave(
            ConfigManager manager,
            DraftBuffer draft,
            AtomicReference<SaveOutcome> outcomeRef,
            CountDownLatch loserReturned,
            AtomicReference<Throwable> failure) {
        try {
            SaveOutcome outcome = manager.save(draft);
            outcomeRef.set(outcome);
            if (outcome.status() == SaveOutcome.Status.INVALID) {
                loserReturned.countDown();
            }
        } catch (Throwable e) {
            failure.compareAndSet(null, e);
            loserReturned.countDown();
        }
    }
}
