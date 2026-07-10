package club.heiqi.uilib.config.modern;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.uilib.Config;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.net.core.MainThreadDispatcher;
import club.heiqi.uilib.net.transport.NetSide;
import club.heiqi.uilib.net.transport.forge.ForgeMainThreadDispatcherBridge;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link ConfigSaveListener} + {@link ModernConfigApplyCoordinator} 全局协调、
 * 线性化 Registration、no-spin / next-drain、失败 reoffer 与 generation 隔离。
 */
public class ConfigSaveListenerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private boolean saveUseDebug;
    private boolean saveUiDebug;
    private boolean saveFontRuntimeDebug;
    private String saveNetTransport;

    private int saveLerpMode;
    private int saveAaMode;
    private double saveAwtCharSize;
    private double saveCharSize;
    private double saveSpaceWidth;
    private double saveCharacterSpacing;
    private double saveShadowOffsetX;
    private double saveShadowOffsetY;
    private double saveRenderOffset;
    private double saveBrightnessGain;
    private double saveDrawStageUploadIntervalMs;
    private int saveDrawStageUploadLimitPerSecond;
    private int saveDrawStageUploadBatchSize;
    private double saveSmoothRangeMin;
    private double saveSmoothRangeMax;
    private double saveAaStrength;
    private boolean saveReplaceOrigin;
    private boolean saveCustomInvCountFont;
    private String[] saveFontSort;
    private String[] saveCharacterFontRules;
    private boolean saveFontSortConfigured;

    @Before
    public void saveStaticState() {
        drainAllClient();
        MainThreadDispatcher.getInstance().drainServer();
        ModernConfigApplyCoordinator.getInstance().resetForTest();

        saveUseDebug = Config.useDebug;
        saveUiDebug = Config.uiDebug;
        saveFontRuntimeDebug = Config.fontRuntimeDebug;
        saveNetTransport = Config.netTransport;
        saveLerpMode = FontConfig.lerpMode;
        saveAaMode = FontConfig.aaMode;
        saveAwtCharSize = FontConfig.awtCharSize;
        saveCharSize = FontConfig.charSize;
        saveSpaceWidth = FontConfig.spaceWidth;
        saveCharacterSpacing = FontConfig.characterSpacing;
        saveShadowOffsetX = FontConfig.shadowOffsetX;
        saveShadowOffsetY = FontConfig.shadowOffsetY;
        saveRenderOffset = FontConfig.renderOffset;
        saveBrightnessGain = FontConfig.brightnessGain;
        saveDrawStageUploadIntervalMs = FontConfig.drawStageUploadIntervalMs;
        saveDrawStageUploadLimitPerSecond = FontConfig.drawStageUploadLimitPerSecond;
        saveDrawStageUploadBatchSize = FontConfig.drawStageUploadBatchSize;
        saveSmoothRangeMin = FontConfig.smoothRangeMin;
        saveSmoothRangeMax = FontConfig.smoothRangeMax;
        saveAaStrength = FontConfig.aaStrength;
        saveReplaceOrigin = FontConfig.replaceOrigin;
        saveCustomInvCountFont = FontConfig.customInvCountFont;
        saveFontSort = FontConfig.fontSort;
        saveCharacterFontRules = FontConfig.characterFontRules;
        saveFontSortConfigured = FontConfig.fontSortConfigured;
    }

    @After
    public void restoreStaticState() {
        drainAllClient();
        MainThreadDispatcher.getInstance().drainServer();
        ModernConfigApplyCoordinator.getInstance().resetForTest();
        ModernConfigApplyCoordinator.TEST_APPLY_FAULT.set(null);
        ModernConfigApplyCoordinator.TEST_BEFORE_OWNER_RELEASE.set(null);
        ModernConfigApplyCoordinator.TEST_BEFORE_REOFFER_CAS.set(null);
        ModernConfigApplyCoordinator.TEST_AFTER_REOFFER_BEFORE_OWNER_RELEASE.set(null);
        ModernConfigApplyCoordinator.TEST_AFTER_REGISTRATION_PUBLISH.set(null);

        Config.useDebug = saveUseDebug;
        Config.uiDebug = saveUiDebug;
        Config.fontRuntimeDebug = saveFontRuntimeDebug;
        Config.netTransport = saveNetTransport;
        FontConfig.lerpMode = saveLerpMode;
        FontConfig.aaMode = saveAaMode;
        FontConfig.awtCharSize = saveAwtCharSize;
        FontConfig.charSize = saveCharSize;
        FontConfig.spaceWidth = saveSpaceWidth;
        FontConfig.characterSpacing = saveCharacterSpacing;
        FontConfig.shadowOffsetX = saveShadowOffsetX;
        FontConfig.shadowOffsetY = saveShadowOffsetY;
        FontConfig.renderOffset = saveRenderOffset;
        FontConfig.brightnessGain = saveBrightnessGain;
        FontConfig.drawStageUploadIntervalMs = saveDrawStageUploadIntervalMs;
        FontConfig.drawStageUploadLimitPerSecond = saveDrawStageUploadLimitPerSecond;
        FontConfig.drawStageUploadBatchSize = saveDrawStageUploadBatchSize;
        FontConfig.smoothRangeMin = saveSmoothRangeMin;
        FontConfig.smoothRangeMax = saveSmoothRangeMax;
        FontConfig.aaStrength = saveAaStrength;
        FontConfig.replaceOrigin = saveReplaceOrigin;
        FontConfig.customInvCountFont = saveCustomInvCountFont;
        FontConfig.fontSort = saveFontSort;
        FontConfig.characterFontRules = saveCharacterFontRules;
        FontConfig.fontSortConfigured = saveFontSortConfigured;
        FontConfig.refreshDerivedRuleSet();
        FontConfig.onConfigReload();
    }

    /** 多轮 drain 清空 next-drain 残留。 */
    private static void drainAllClient() {
        for (int i = 0; i < 8; i++) {
            MainThreadDispatcher.getInstance().drainClient();
            ModernConfigApplyCoordinator.getInstance().retryPendingOnce();
        }
        MainThreadDispatcher.getInstance().drainClient();
    }

    private static void drainClient() {
        MainThreadDispatcher.getInstance().drainClient();
    }

    /**
     * 模拟 Forge CLIENT END：retry → drain（与 bridge 同序）。
     */
    private static void clientEndTick() {
        ModernConfigApplyCoordinator.getInstance().retryPendingOnce();
        MainThreadDispatcher.getInstance().drainClient();
    }

    @Test
    public void batchSaveTriggersValuePropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-batch.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("general.useDebug", Boolean.TRUE);
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        draft.setDraft("fontSystem.brightnessGain", Double.valueOf(3.5));
        draft.setDraft("fontSystem.fontSort", Arrays.asList("Sans"));
        assertTrue(manager.save(draft).isSuccess());

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        drainClient();

        assertTrue("useDebug 应被回灌为 true", Config.useDebug);
        assertEquals("lerpMode 应被回灌为 1", 1, FontConfig.lerpMode);
        assertEquals("brightnessGain 应被回灌为 3.5", 3.5, FontConfig.brightnessGain, 0.0);
        assertArrayEqualsFontSort(new String[] {"Sans"}, FontConfig.fontSort);
    }

    private static void assertArrayEqualsFontSort(String[] expected, String[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }

    @Test
    public void setEventDoesNotTriggerPropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-set.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(draft).isSuccess());
        FontConfig.lerpMode = 3;
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("fontSystem.lerpMode", Double.valueOf(3.0),
                Double.valueOf(1.0), ConfigChangeEvent.ChangeType.SET));
        drainClient();
        assertEquals(3, FontConfig.lerpMode);
    }

    @Test
    public void removeEventDoesNotTriggerPropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-remove.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.brightnessGain", Double.valueOf(3.5));
        assertTrue(manager.save(draft).isSuccess());
        FontConfig.brightnessGain = 2.0;
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("fontSystem.brightnessGain",
                Double.valueOf(3.5), null, ConfigChangeEvent.ChangeType.REMOVE));
        drainClient();
        assertEquals(2.0, FontConfig.brightnessGain, 0.0);
    }

    @Test
    public void clearEventDoesNotTriggerPropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-clear.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        Config.useDebug = true;
        FontConfig.lerpMode = 3;
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.CLEAR));
        drainClient();
        assertTrue(Config.useDebug);
        assertEquals(3, FontConfig.lerpMode);
    }

    @Test
    public void reloadEventTriggersValuePropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-reload-yes.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("general.useDebug", Boolean.TRUE);
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(draft).isSuccess());
        FontConfig.lerpMode = 9;
        Config.useDebug = false;
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.RELOAD));
        drainClient();
        assertTrue(Config.useDebug);
        assertEquals(1, FontConfig.lerpMode);
    }

    @Test
    public void workerThreadPublishBatchSave_appliesOnDrain() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-worker.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(draft).isSuccess());
        FontConfig.lerpMode = 7;
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        Thread worker = new Thread(() -> listener.onConfigChanged(new ConfigChangeEvent(
                "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE)), "listener-worker");
        worker.start();
        worker.join(5000);
        assertEquals(7, FontConfig.lerpMode);
        drainClient();
        assertEquals(1, FontConfig.lerpMode);
    }

    @Test
    public void rapidFireLatestWins() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-latest.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer d1 = manager.openDraft();
        d1.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(d1).isSuccess());
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        DraftBuffer d2 = manager.openDraft();
        d2.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        assertTrue(manager.save(d2).isSuccess());
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.RELOAD));
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        FontConfig.lerpMode = 0;
        drainClient();
        assertEquals(2, FontConfig.lerpMode);
    }

    /**
     * 队列计数 / owner / pending：submit 后 owner true、队列恰 1；
     * 单次 drain coordinator 恰 1；期间新 pending 不二次 enqueue。
     */
    @Test
    public void queueCount_owner_pending_singleCoordinatorPerDrain() throws Exception {
        File file = tempFolder.newFile("listener-queue-owner.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer d = manager.openDraft();
        d.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(d).isSuccess());
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        FontConfig.lerpMode = 0;

        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        assertTrue(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
        assertTrue(ModernConfigApplyCoordinator.getInstance().hasPending());
        assertEquals(1, MainThreadDispatcher.getInstance().clientQueueSize());

        // owner true 时再 submit：只更新 pending，队列仍 1
        DraftBuffer d2 = manager.openDraft();
        d2.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        assertTrue(manager.save(d2).isSuccess());
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        assertTrue(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
        assertEquals("owner true 不得重复 enqueue", 1,
                MainThreadDispatcher.getInstance().clientQueueSize());

        long before = ModernConfigApplyCoordinator.getInstance().dispatchRunCount();
        drainClient();
        assertEquals("单次 drain coordinator 恰 1 次", before + 1,
                ModernConfigApplyCoordinator.getInstance().dispatchRunCount());
        assertEquals(2, FontConfig.lerpMode);
        assertFalse(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
        assertFalse(ModernConfigApplyCoordinator.getInstance().hasPending());
        assertEquals(0, MainThreadDispatcher.getInstance().clientQueueSize());
    }

    /**
     * A submit 未 drain → B 注册 → A 旧事件/任务不得覆盖 B 的 Authority。
     */
    @Test
    public void listenerLifecycle_oldGenerationCannotOverwriteNew() throws Exception {
        File fA = tempFolder.newFile("listener-life-a.yaml");
        File fB = tempFolder.newFile("listener-life-b.yaml");
        ConfigManager mA = ConfigManager.bootstrap(fA, QzUiLibModernSchema.create());
        DraftBuffer dA = mA.openDraft();
        dA.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(mA.save(dA).isSuccess());

        ConfigSaveListener listenerA = new ConfigSaveListener(mA);
        long genA = listenerA.generation();
        WeakReference<ConfigSaveListener> weakA = new WeakReference<ConfigSaveListener>(listenerA);
        listenerA.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        assertTrue(ModernConfigApplyCoordinator.getInstance().hasPending()
                || ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());

        ConfigManager mB = ConfigManager.bootstrap(fB, QzUiLibModernSchema.create());
        DraftBuffer dB = mB.openDraft();
        dB.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        SaveOutcome saveB = mB.save(dB);
        assertTrue("mB save 应成功: " + saveB.status() + " " + saveB.conflictType()
                        + " " + (saveB.validation() == null ? "" : saveB.validation().summary(80)),
                saveB.isSuccess());
        ConfigSaveListener listenerB = new ConfigSaveListener(mB);
        assertTrue(listenerB.generation() > genA);
        assertEquals(mB, ModernConfigApplyCoordinator.getInstance().currentManager());

        listenerA.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.RELOAD));
        FontConfig.lerpMode = 0;
        listenerB.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        // no-spin：单次 drain 最多一次 coordinator；下一 tick retry 再应用
        drainClient();
        clientEndTick();
        assertEquals(2, FontConfig.lerpMode);

        assertTrue(ModernConfigApplyCoordinator.getInstance().pendingHoldsNoListener());
        listenerA = null;
        System.gc();
        assertNotNull(listenerB);
        for (int i = 0; i < 3; i++) {
            System.gc();
            Thread.yield();
        }
        assertFalse(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
    }

    /** fault 一次 → tick retry → 成功；last snapshot 仅成功后推进。 */
    @Test
    public void applyFaultOnce_retryOnNextTick_succeeds() throws Exception {
        File file = tempFolder.newFile("listener-fault-retry.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        assertTrue(manager.save(draft).isSuccess());

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        FontConfig.lerpMode = 9;
        FontConfig.onConfigReload(); // 先对齐 last=9

        ModernConfigApplyCoordinator.TEST_APPLY_FAULT.set(new RuntimeException("inject-once"));
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        drainClient();
        // 失败：lerpMode 仍 9（未成功 apply）；needsRetry 且 pending reoffer
        assertEquals(9, FontConfig.lerpMode);
        assertTrue(ModernConfigApplyCoordinator.getInstance().needsRetry()
                || ModernConfigApplyCoordinator.getInstance().hasPending());

        // tick 重试（retry → drain）
        clientEndTick();
        assertEquals(2, FontConfig.lerpMode);
        assertFalse(ModernConfigApplyCoordinator.getInstance().needsRetry());
        assertFalse(ModernConfigApplyCoordinator.getInstance().hasPending());
    }

    /**
     * failure+new event：hook/latch 卡在 reoffer CAS 窗口；新事件优先；结果唯一。
     */
    @Test
    public void applyFault_newEventInReofferCasWindow_preferredUnique() throws Exception {
        File file = tempFolder.newFile("listener-fault-reoffer-cas.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer d1 = manager.openDraft();
        d1.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(d1).isSuccess());

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        FontConfig.lerpMode = 0;
        FontConfig.onConfigReload();

        final CountDownLatch inWindow = new CountDownLatch(1);
        final CountDownLatch releaseWindow = new CountDownLatch(1);
        final AtomicInteger hookRan = new AtomicInteger();
        ModernConfigApplyCoordinator.TEST_BEFORE_REOFFER_CAS.set(() -> {
            hookRan.incrementAndGet();
            inWindow.countDown();
            try {
                // 短超时仅防死锁；流程靠显式 release
                if (!releaseWindow.await(5, TimeUnit.SECONDS)) {
                    throw new RuntimeException("releaseWindow deadlock");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        ModernConfigApplyCoordinator.TEST_APPLY_FAULT.set(new RuntimeException("fault-first"));
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));

        Thread drainer = new Thread(() -> drainClient(), "reoffer-drain");
        drainer.start();
        assertTrue("应进入 reoffer CAS 窗口", inWindow.await(5, TimeUnit.SECONDS));

        // 窗口内：新事件改 Authority 再 submit（owner 仍 true → 只写 pending）
        DraftBuffer d2 = manager.openDraft();
        d2.setDraft("fontSystem.lerpMode", Double.valueOf(3.0));
        assertTrue(manager.save(d2).isSuccess());
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.RELOAD));
        assertTrue("窗口内应有 pending（新事件或 reoffer）",
                ModernConfigApplyCoordinator.getInstance().hasPending()
                        || ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());

        releaseWindow.countDown();
        drainer.join(3000);
        assertEquals(Thread.State.TERMINATED, drainer.getState());
        assertEquals(1, hookRan.get());
        // 失败未 apply；新值在 pending
        assertEquals(0, FontConfig.lerpMode);

        clientEndTick();
        assertEquals("结果唯一：新事件 lerpMode=3", 3, FontConfig.lerpMode);
        assertFalse(ModernConfigApplyCoordinator.getInstance().needsRetry());
        assertFalse(ModernConfigApplyCoordinator.getInstance().hasPending());
        assertFalse(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
    }

    /**
     * 失败期间新事件优先（reoffer 后 after-hook 窗口）。
     */
    @Test
    public void applyFault_newEventPreferred_notLost() throws Exception {
        File file = tempFolder.newFile("listener-fault-new-event.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer d1 = manager.openDraft();
        d1.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(d1).isSuccess());

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        FontConfig.lerpMode = 0;
        FontConfig.onConfigReload();

        ModernConfigApplyCoordinator.TEST_APPLY_FAULT.set(new RuntimeException("fault-first"));
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        drainClient();
        assertEquals(0, FontConfig.lerpMode);

        DraftBuffer d2 = manager.openDraft();
        d2.setDraft("fontSystem.lerpMode", Double.valueOf(3.0));
        assertTrue(manager.save(d2).isSuccess());
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.RELOAD));
        clientEndTick();
        assertEquals(3, FontConfig.lerpMode);
        assertFalse(ModernConfigApplyCoordinator.getInstance().needsRetry());
    }

    /** 失败后后续队列任务仍执行（MainThreadDispatcher 隔离）。 */
    @Test
    public void applyFault_doesNotBlockSubsequentQueueTasks() throws Exception {
        File file = tempFolder.newFile("listener-fault-queue.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        AtomicInteger after = new AtomicInteger();
        ModernConfigApplyCoordinator.TEST_APPLY_FAULT.set(new RuntimeException("fault"));
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        MainThreadDispatcher.getInstance().enqueue(NetSide.CLIENT, after::incrementAndGet);
        drainClient();
        assertEquals(1, after.get());
    }

    /**
     * no-spin：持续 submit 时单次 drain 中 coordinator task 有上界（≤1 次 apply 语义）；
     * 下一 tick retryPendingOnce 后最终应用最新值。
     */
    @Test
    public void continuousSubmit_singleDrainBounded_nextTickEventuallyApplies() throws Exception {
        File file = tempFolder.newFile("listener-nospin.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        ConfigSaveListener listener = new ConfigSaveListener(manager);

        DraftBuffer d = manager.openDraft();
        d.setDraft("fontSystem.lerpMode", Double.valueOf(0.0));
        assertTrue(manager.save(d).isSuccess());
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));

        for (int i = 1; i <= 3; i++) {
            DraftBuffer di = manager.openDraft();
            di.setDraft("fontSystem.lerpMode", Double.valueOf((double) i));
            assertTrue("save lerpMode=" + i + " 应成功", manager.save(di).isSuccess());
            listener.onConfigChanged(new ConfigChangeEvent(
                    "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        }
        FontConfig.lerpMode = 9;
        long before = ModernConfigApplyCoordinator.getInstance().dispatchRunCount();
        drainClient();
        long afterFirst = ModernConfigApplyCoordinator.getInstance().dispatchRunCount();
        assertTrue("单次 drain coordinator ≤1", afterFirst - before <= 1);
        if (FontConfig.lerpMode != 3) {
            assertTrue("应有 pending 或 needsRetry 等待下一 tick",
                    ModernConfigApplyCoordinator.getInstance().hasPending()
                            || ModernConfigApplyCoordinator.getInstance().needsRetry()
                            || ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
            clientEndTick();
        }
        assertEquals(3, FontConfig.lerpMode);
        assertFalse(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
        assertFalse(ModernConfigApplyCoordinator.getInstance().hasPending());
    }

    /**
     * TEST_BEFORE_OWNER_RELEASE：在真实 pending 检查→owner 释放窗口 submit 最后事件，不丢。
     */
    @Test
    public void drainReleaseRace_hookPrecise_doesNotDropLastEvent() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-race.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(draft).isSuccess());

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        AtomicInteger hookRan = new AtomicInteger();
        ModernConfigApplyCoordinator.TEST_BEFORE_OWNER_RELEASE.set(() -> {
            hookRan.incrementAndGet();
            try {
                DraftBuffer d2 = manager.openDraft();
                d2.setDraft("fontSystem.lerpMode", Double.valueOf(3.0));
                assertTrue(manager.save(d2).isSuccess());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            listener.onConfigChanged(new ConfigChangeEvent(
                    "", null, null, ConfigChangeEvent.ChangeType.RELOAD));
            // hook 内 owner 仍 true：submit 只写 pending，不 enqueue
            assertTrue("hook 窗口应有 pending 最后事件",
                    ModernConfigApplyCoordinator.getInstance().hasPending());
        });

        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        FontConfig.lerpMode = 0;
        drainClient();
        assertEquals(1, hookRan.get());
        // no-spin：hook 写入的 pending 需下一 tick
        assertTrue(ModernConfigApplyCoordinator.getInstance().hasPending()
                || FontConfig.lerpMode == 3);
        if (FontConfig.lerpMode != 3) {
            clientEndTick();
        }
        assertEquals(3, FontConfig.lerpMode);
        assertFalse(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
        assertFalse(ModernConfigApplyCoordinator.getInstance().hasPending());
        assertTrue(ModernConfigApplyCoordinator.getInstance().pendingHoldsNoListener());
    }

    /**
     * stale submit 卡 B registration 发布后再恢复 A：结果唯一（current=B）。
     */
    @Test
    public void staleSubmit_afterBRegistrationPublish_resultUnique() throws Exception {
        File fA = tempFolder.newFile("stale-reg-a.yaml");
        File fB = tempFolder.newFile("stale-reg-b.yaml");
        ConfigManager mA = ConfigManager.bootstrap(fA, QzUiLibModernSchema.create());
        ConfigManager mB = ConfigManager.bootstrap(fB, QzUiLibModernSchema.create());
        DraftBuffer dA = mA.openDraft();
        dA.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(mA.save(dA).isSuccess());
        DraftBuffer dB = mB.openDraft();
        dB.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        assertTrue(mB.save(dB).isSuccess());

        ConfigSaveListener listenerA = new ConfigSaveListener(mA);
        final CountDownLatch bPublished = new CountDownLatch(1);
        final CountDownLatch aSubmitDone = new CountDownLatch(1);
        final AtomicReference<ConfigSaveListener> listenerBRef =
                new AtomicReference<ConfigSaveListener>();

        ModernConfigApplyCoordinator.TEST_AFTER_REGISTRATION_PUBLISH.set(() -> {
            bPublished.countDown();
            try {
                // 卡在 B 发布后：A stale submit
                if (!aSubmitDone.await(5, TimeUnit.SECONDS)) {
                    throw new RuntimeException("aSubmitDone deadlock");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });

        Thread bReg = new Thread(() -> {
            ConfigSaveListener lb = new ConfigSaveListener(mB);
            listenerBRef.set(lb);
            lb.onConfigChanged(new ConfigChangeEvent(
                    "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        }, "reg-b");
        bReg.start();
        assertTrue("B registration 应已发布", bPublished.await(5, TimeUnit.SECONDS));

        // B 已发布，A stale submit（应 no-op）
        listenerA.onConfigChanged(new ConfigChangeEvent(
                "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        aSubmitDone.countDown();
        bReg.join(3000);
        assertEquals(Thread.State.TERMINATED, bReg.getState());

        FontConfig.lerpMode = 0;
        clientEndTick();
        clientEndTick();
        assertEquals(mB, ModernConfigApplyCoordinator.getInstance().currentManager());
        assertEquals(2, FontConfig.lerpMode);
        assertTrue(listenerBRef.get().generation() > listenerA.generation());
    }

    /**
     * 并发 A/B register 与 stale submit：结果唯一，current 为更大 generation。
     */
    @Test(timeout = 15000L)
    public void concurrentRegisterAndStaleSubmit_resultUnique() throws Exception {
        File fA = tempFolder.newFile("coord-race-a.yaml");
        File fB = tempFolder.newFile("coord-race-b.yaml");
        ConfigManager mA = ConfigManager.bootstrap(fA, QzUiLibModernSchema.create());
        ConfigManager mB = ConfigManager.bootstrap(fB, QzUiLibModernSchema.create());
        DraftBuffer dA = mA.openDraft();
        dA.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(mA.save(dA).isSuccess());
        DraftBuffer dB = mB.openDraft();
        dB.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        assertTrue(mB.save(dB).isSuccess());

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(4);
        AtomicReference<ConfigSaveListener> listenerA = new AtomicReference<ConfigSaveListener>();
        AtomicReference<ConfigSaveListener> listenerB = new AtomicReference<ConfigSaveListener>();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Runnable regA = () -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                ConfigSaveListener la = new ConfigSaveListener(mA);
                listenerA.set(la);
                la.onConfigChanged(new ConfigChangeEvent(
                        "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        };
        Runnable regB = () -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                ConfigSaveListener lb = new ConfigSaveListener(mB);
                listenerB.set(lb);
                lb.onConfigChanged(new ConfigChangeEvent(
                        "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        };
        new Thread(regA, "reg-a").start();
        new Thread(regB, "reg-b").start();
        new Thread(() -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                Thread.yield();
                ConfigSaveListener la = listenerA.get();
                if (la != null) {
                    la.onConfigChanged(new ConfigChangeEvent(
                            "", null, null, ConfigChangeEvent.ChangeType.RELOAD));
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        }, "stale-a").start();
        new Thread(() -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                Thread.yield();
                ConfigSaveListener lb = listenerB.get();
                if (lb != null) {
                    lb.onConfigChanged(new ConfigChangeEvent(
                            "", null, null, ConfigChangeEvent.ChangeType.RELOAD));
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        }, "stale-b").start();

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertNull(failure.get());

        for (int i = 0; i < 6; i++) {
            clientEndTick();
        }
        ConfigManager current = ModernConfigApplyCoordinator.getInstance().currentManager();
        assertNotNull(current);
        assertTrue("current 必须是 mA 或 mB", current == mA || current == mB);
        long genA = listenerA.get() == null ? -1 : listenerA.get().generation();
        long genB = listenerB.get() == null ? -1 : listenerB.get().generation();
        long curGen = ModernConfigApplyCoordinator.getInstance().currentGeneration();
        assertEquals("current generation 应等于较晚 register", Math.max(genA, genB), curGen);
        int expectedLerp = current == mB ? 2 : 1;
        assertEquals(expectedLerp, FontConfig.lerpMode);
        assertFalse(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
    }

    /** coordinator 持最新 manager 作为全局 Authority。 */
    @Test
    public void coordinatorHoldsLatestManagerAsGlobalAuthority() throws Exception {
        File f1 = tempFolder.newFile("coord-m1.yaml");
        File f2 = tempFolder.newFile("coord-m2.yaml");
        ConfigManager m1 = ConfigManager.bootstrap(f1, QzUiLibModernSchema.create());
        new ConfigSaveListener(m1);
        assertEquals(m1, ModernConfigApplyCoordinator.getInstance().currentManager());
        ConfigManager m2 = ConfigManager.bootstrap(f2, QzUiLibModernSchema.create());
        new ConfigSaveListener(m2);
        assertEquals(m2, ModernConfigApplyCoordinator.getInstance().currentManager());
        assertTrue(ModernConfigApplyCoordinator.getInstance().currentGeneration() > 0);
    }

    /**
     * Forge CLIENT END 接线：retry→drain 两 tick；coordinator apply 在 drain 线程执行。
     * 不依赖 JUnit 线程名误判；字体实机 reload 残余见 FontService 线程闸（headless 不真执行）。
     */
    @Test
    public void forgeClientEndWiring_retryThenDrain_twoTicks_applyOnDrainThread() throws Exception {
        File file = tempFolder.newFile("forge-client-end.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        assertTrue(manager.save(draft).isSuccess());
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        FontConfig.lerpMode = 0;
        FontConfig.onConfigReload();

        // 注入一次失败 → 需 tick retry
        ModernConfigApplyCoordinator.TEST_APPLY_FAULT.set(new RuntimeException("need-retry"));
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));

        // 标记「桥 drain 线程」：可观察缝 = lastDispatchThread / dispatchRunCount
        final Thread bridgeThread = Thread.currentThread();
        long before = ModernConfigApplyCoordinator.getInstance().dispatchRunCount();

        // tick1：retry + drain（失败 apply 在本线程）
        clientEndTick();
        assertEquals(0, FontConfig.lerpMode);
        assertTrue(ModernConfigApplyCoordinator.getInstance().hasPending()
                || ModernConfigApplyCoordinator.getInstance().needsRetry());
        Thread firstDispatch = ModernConfigApplyCoordinator.getInstance().lastDispatchThread();
        assertNotNull("coordinator task 应已在 bridge drain 线程执行", firstDispatch);
        assertSame("apply 在桥 drain 线程（本测试用 clientEndTick 模拟）",
                bridgeThread, firstDispatch);
        assertEquals(before + 1, ModernConfigApplyCoordinator.getInstance().dispatchRunCount());

        // tick2：retry 再排 → drain 成功
        clientEndTick();
        assertEquals(2, FontConfig.lerpMode);
        assertSame(bridgeThread, ModernConfigApplyCoordinator.getInstance().lastDispatchThread());
        assertEquals(before + 2, ModernConfigApplyCoordinator.getInstance().dispatchRunCount());
        assertFalse(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
        assertFalse(ModernConfigApplyCoordinator.getInstance().hasPending());

        // 桥单例存在（接线点）
        assertNotNull(ForgeMainThreadDispatcherBridge.getInstance());
        // 字体：headless 下 FontService.reload 线程闸可能静默；此处只断言 coordinator 执行线程可观察
    }

    /**
     * retryPendingOnce 在 owner true 时不重复 enqueue。
     */
    @Test
    public void retryPendingOnce_whileOwned_doesNotDuplicateQueue() throws Exception {
        File file = tempFolder.newFile("retry-owned.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        assertTrue(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
        int size = MainThreadDispatcher.getInstance().clientQueueSize();
        ModernConfigApplyCoordinator.getInstance().retryPendingOnce();
        assertEquals("owner true 时 retry 不得再 enqueue", size,
                MainThreadDispatcher.getInstance().clientQueueSize());
        drainClient();
    }
}
