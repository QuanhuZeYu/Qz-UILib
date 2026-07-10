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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ConfigManager} 事务与 {@link ConfigEventBus} 边界测试，覆盖 save 后状态一致性、
 * IO 失败回滚、事件总线多监听器/异常隔离/重复订阅、bootstrap 幂等等。
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

    /** save 持有完整 draft 锁；并发合法编辑在 commit 后继续且不会丢失。 */
    @Test(timeout = 10000L)
    public void concurrentDraftEditSerializesAfterSaveWithoutLoss() throws Exception {
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

        final CountDownLatch editAttempted = new CountDownLatch(1);
        final CountDownLatch editDone = new CountDownLatch(1);
        Thread editor = new Thread(() -> {
            try {
                editAttempted.countDown();
                draft.setDraft("server.host", "edited.after.save");
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            } finally {
                editDone.countDown();
            }
        }, "config-edit");
        editor.start();
        assertTrue(editAttempted.await(2, TimeUnit.SECONDS));
        assertFalse("save 期间并发编辑必须等待 draft 锁", editDone.await(200, TimeUnit.MILLISECONDS));

        releaseValidator.countDown();
        saver.join(5000L);
        editor.join(5000L);
        assertFalse(saver.isAlive());
        assertFalse(editor.isAlive());
        assertNull(failure.get());
        assertNotNull(saved.get());
        assertTrue(saved.get().isSuccess());
        assertEquals("captured.host", manager.authority().getString("server.host"));
        assertEquals("captured.host", draft.getCurrent("server.host"));
        assertEquals("edited.after.save", draft.getDraft("server.host"));
        assertTrue(draft.isDirty("server.host"));
    }

    /** openDraft、Legacy 写与 flushRaw 均不能越过正在进行的 save。 */
    @Test(timeout = 10000L)
    public void authorityOperationsShareSaveTransactionLock() throws Exception {
        File file = tempFolder.newFile("shared-lock.yaml");
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
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "saved.host");

        final AtomicReference<SaveOutcome> saved = new AtomicReference<SaveOutcome>();
        final AtomicReference<DraftBuffer> opened = new AtomicReference<DraftBuffer>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final CountDownLatch openAttempted = new CountDownLatch(1);
        final CountDownLatch legacyAttempted = new CountDownLatch(1);
        final CountDownLatch flushAttempted = new CountDownLatch(1);
        final CountDownLatch openDone = new CountDownLatch(1);
        final CountDownLatch legacyDone = new CountDownLatch(1);
        final CountDownLatch flushDone = new CountDownLatch(1);
        Thread saver = new Thread(() -> {
            try {
                saved.set(manager.save(draft));
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            }
        }, "shared-lock-save");
        saver.start();
        assertTrue(validatorEntered.await(2, TimeUnit.SECONDS));

        Thread opener = new Thread(() -> {
            try {
                openAttempted.countDown();
                opened.set(manager.openDraft());
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            } finally {
                openDone.countDown();
            }
        }, "shared-lock-open");
        Thread legacyWriter = new Thread(() -> {
            try {
                legacyAttempted.countDown();
                manager.authority().legacy().setRawJson("custom", "value: legacy\n");
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            } finally {
                legacyDone.countDown();
            }
        }, "shared-lock-legacy");
        Thread flusher = new Thread(() -> {
            try {
                flushAttempted.countDown();
                manager.flushRaw();
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            } finally {
                flushDone.countDown();
            }
        }, "shared-lock-flush");
        opener.start();
        legacyWriter.start();
        flusher.start();
        assertTrue(openAttempted.await(2, TimeUnit.SECONDS));
        assertTrue(legacyAttempted.await(2, TimeUnit.SECONDS));
        assertTrue(flushAttempted.await(2, TimeUnit.SECONDS));
        assertFalse(openDone.await(150, TimeUnit.MILLISECONDS));
        assertFalse(legacyDone.await(150, TimeUnit.MILLISECONDS));
        assertFalse(flushDone.await(150, TimeUnit.MILLISECONDS));

        releaseValidator.countDown();
        saver.join(5000L);
        opener.join(5000L);
        legacyWriter.join(5000L);
        flusher.join(5000L);
        assertFalse(saver.isAlive());
        assertFalse(opener.isAlive());
        assertFalse(legacyWriter.isAlive());
        assertFalse(flusher.isAlive());
        assertNull(failure.get());
        assertTrue(saved.get().isSuccess());
        assertNotNull(opened.get());
        assertEquals("saved.host", opened.get().getCurrent("server.host"));
        assertTrue(manager.authority().legacy().getRawJson("custom").contains("legacy"));

        // 竞争顺序不约束 legacy 与并发 flush 的先后；显式最终 flush 后验证两类写入均保留。
        manager.flushRaw();
        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("saved.host", reloaded.get("server.host").asString());
        assertEquals("legacy", reloaded.get("custom.value").asString());
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
}
