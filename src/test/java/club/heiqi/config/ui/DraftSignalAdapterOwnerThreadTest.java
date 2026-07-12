package club.heiqi.config.ui;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.runtime.ValidationResult;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * DraftSignalAdapter owner 线程参数化：所有 public mutator 跨线程 IllegalStateException 且状态零变化。
 *
 * <p>新增 mutator 时须同步加入 {@link #allMutators()} 清单。</p>
 */
public class DraftSignalAdapterOwnerThreadTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private interface Mutator {
        String name();
        void run(DraftSignalAdapter adapter) throws Exception;
    }

    /** 可见 mutator 清单：新增 public/package mutator 时必须登记。 */
    private static List<Mutator> allMutators() {
        List<Mutator> list = new ArrayList<Mutator>();
        list.add(named("onFieldEdit", a -> a.onFieldEdit("server.host", "worker")));
        list.add(named("setSaveFeedback", a -> a.setSaveFeedback(
                new SaveFeedback(SaveFeedback.Status.OK, "x"))));
        list.add(named("setConflictType", a -> a.setConflictType(
                SaveOutcome.ConflictType.STALE_DRAFT_BASE)));
        list.add(named("setSubmitValidation", a -> a.setSubmitValidation(
                ValidationResult.error("server.host", "e"))));
        list.add(named("applySaveFailure", a -> a.applySaveFailure(
                SaveOutcome.invalid(ValidationResult.error("server.host", "e")))));
        list.add(named("clearSubmitValidation", DraftSignalAdapter::clearSubmitValidation));
        list.add(named("seedPresentation", a -> a.seedPresentation("server.host", "seed")));
        list.add(named("seedFieldBaseline", a -> a.seedFieldBaseline("server.host", "seed-base")));
        list.add(named("resetToCurrent", DraftSignalAdapter::resetToCurrent));
        list.add(named("resetFieldToDefault", a -> a.resetFieldToDefault("server.host")));
        list.add(named("afterSaveSync", DraftSignalAdapter::afterSaveSync));
        list.add(named("replaceDraft", a -> {
            a.replaceDraft(a.draft());
        }));
        list.add(named("dispose", DraftSignalAdapter::dispose));
        return list;
    }


    private static Mutator named(String name, MutatorBody body) {
        return new Mutator() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void run(DraftSignalAdapter adapter) throws Exception {
                body.run(adapter);
            }
        };
    }

    private interface MutatorBody {
        void run(DraftSignalAdapter adapter) throws Exception;
    }

    private static void write(File file, String content) throws Exception {
        FileWriter w = new FileWriter(file);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }

    @Test
    public void everyPublicMutatorRejectsWorkerThread_zeroSideEffect() throws Exception {
        File file = tempFolder.newFile("owner-thread.yaml");
        write(file, "server:\n  host: main\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema());
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, manager.openDraft());
        try {
            adapter.onFieldEdit("server.host", "baseline");
            ReactiveScheduler.get().flush();
            Object signalBefore = adapter.draftSignal("server.host").get();
            Object draftBefore = adapter.draft().getDraft("server.host");
            SaveOutcome.ConflictType conflictBefore = adapter.conflictTypeSignal().get();
            SaveFeedback feedbackBefore = adapter.saveFeedbackSignal().get();
            boolean requiresBefore = adapter.requiresReload();

            List<Mutator> mutators = allMutators();
            Assert.assertTrue("mutator 清单不可空", mutators.size() >= 10);

            for (Mutator m : mutators) {
                // 每轮用干净 adapter 避免 dispose 污染
                DraftSignalAdapter a = new DraftSignalAdapter(null, manager.openDraft());
                a.onFieldEdit("server.host", "baseline");
                ReactiveScheduler.get().flush();
                Object sb = a.draftSignal("server.host").get();
                Object db = a.draft().getDraft("server.host");
                boolean dirtyBefore = a.draft().isDirty("server.host");

                CountDownLatch done = new CountDownLatch(1);
                AtomicReference<Throwable> err = new AtomicReference<Throwable>();
                Thread worker = new Thread(() -> {
                    try {
                        m.run(a);
                    } catch (Throwable t) {
                        err.set(t);
                    } finally {
                        done.countDown();
                    }
                }, "worker-mutator-" + m.name());
                worker.start();
                Assert.assertTrue("timeout " + m.name(), done.await(5, TimeUnit.SECONDS));
                Assert.assertNotNull(m.name() + " must throw", err.get());
                Assert.assertTrue(m.name() + " must be IllegalStateException: " + err.get(),
                        err.get() instanceof IllegalStateException);
                String msg = err.get().getMessage();
                Assert.assertTrue(m.name() + " message has expected/actual thread: " + msg,
                        msg != null && (msg.contains("owner thread") || msg.contains("worker-mutator")));

                ReactiveScheduler.get().flush();
                Assert.assertEquals(m.name() + " signal 零变化", sb, a.draftSignal("server.host").get());
                Assert.assertEquals(m.name() + " draft 零变化", db, a.draft().getDraft("server.host"));
                Assert.assertEquals(m.name() + " dirty 零变化", dirtyBefore, a.draft().isDirty("server.host"));
                // dispose 在 owner 线程再清
                try {
                    a.dispose();
                } catch (Throwable ignored) {
                }
            }

            // 原 adapter 仍基线
            Assert.assertEquals(signalBefore, adapter.draftSignal("server.host").get());
            Assert.assertEquals(draftBefore, adapter.draft().getDraft("server.host"));
            Assert.assertEquals(conflictBefore, adapter.conflictTypeSignal().get());
            Assert.assertEquals(feedbackBefore.message(), adapter.saveFeedbackSignal().get().message());
            Assert.assertEquals(requiresBefore, adapter.requiresReload());
        } finally {
            try {
                adapter.dispose();
            } catch (Throwable ignored) {
            }
        }
    }

    /** ConfigScreen managerA + adapterB fail-fast IllegalArgumentException。 */
    @Test
    public void configScreenRejectsManagerA_AdapterB() throws Exception {
        File f1 = tempFolder.newFile("scr-a.yaml");
        File f2 = tempFolder.newFile("scr-b.yaml");
        write(f1, "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n");
        write(f2, "server:\n  host: b\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager mA = ConfigManager.bootstrap(f1, UiSchemaFactory.serverSchema());
        ConfigManager mB = ConfigManager.bootstrap(f2, UiSchemaFactory.serverSchema());
        DraftSignalAdapter adapterB = new DraftSignalAdapter(null, mB.openDraft());
        try {
            new ConfigScreen(null, mA, adapterB, FieldRendererRegistry.defaultRegistry());
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("owns") || e.getMessage().contains("same ConfigManager"));
        } finally {
            adapterB.dispose();
        }
    }
}
