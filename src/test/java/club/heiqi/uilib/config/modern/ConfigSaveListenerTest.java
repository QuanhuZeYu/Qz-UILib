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
import cpw.mods.fml.common.gameevent.TickEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link ConfigSaveListener} + {@link ModernConfigApplyCoordinator} 全局协调、
 * 单一 monitor 线性化、no-spin / next-drain、失败 reoffer 与 generation 隔离。
 *
 * <p>线程异常一律 {@link AtomicReference} 回传并由主线程 assert；
 * 无 5/8 秒超时假绿；hook 不得在持 monitor 时 wait。</p>
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
        ModernConfigApplyCoordinator.TEST_DURING_APPLY.set(null);

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
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        // 消费 register 自动 initial apply
        drainClient();
        FontConfig.lerpMode = 3;
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
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        drainClient();
        FontConfig.brightnessGain = 2.0;
        listener.onConfigChanged(new ConfigChangeEvent("fontSystem.brightnessGain",
                Double.valueOf(3.5), null, ConfigChangeEvent.ChangeType.REMOVE));
        drainClient();
        assertEquals(2.0, FontConfig.brightnessGain, 0.0);
    }

    @Test
    public void clearEventDoesNotTriggerPropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-clear.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        drainClient();
        Config.useDebug = true;
        FontConfig.lerpMode = 3;
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
        final AtomicReference<Throwable> workerErr = new AtomicReference<Throwable>();
        Thread worker = new Thread(() -> {
            try {
                listener.onConfigChanged(new ConfigChangeEvent(
                        "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
            } catch (Throwable t) {
                workerErr.set(t);
            }
        }, "listener-worker");
        worker.start();
        worker.join(5000);
        assertEquals(Thread.State.TERMINATED, worker.getState());
        assertNull(workerErr.get());
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
        assertFalse(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
    }

    /**
     * 严格单线程线性化：register(A) → register(B) → submit(A) → drain；只允许 B apply 一次。
     */
    @Test
    public void registerA_registerB_submitA_drain_appliesOnlyBOnce() throws Exception {
        File fA = tempFolder.newFile("listener-linearized-a.yaml");
        File fB = tempFolder.newFile("listener-linearized-b.yaml");
        ConfigManager managerA = ConfigManager.bootstrap(fA, QzUiLibModernSchema.create());
        DraftBuffer draftA = managerA.openDraft();
        draftA.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(managerA.save(draftA).isSuccess());
        ConfigManager managerB = ConfigManager.bootstrap(fB, QzUiLibModernSchema.create());
        DraftBuffer draftB = managerB.openDraft();
        draftB.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        assertTrue(managerB.save(draftB).isSuccess());

        ConfigSaveListener listenerA = new ConfigSaveListener(managerA);
        ConfigSaveListener listenerB = new ConfigSaveListener(managerB);
        ConfigManager currentBeforeStaleSubmit = ModernConfigApplyCoordinator.getInstance().currentManager();
        long generationBeforeStaleSubmit = ModernConfigApplyCoordinator.getInstance().currentGeneration();
        long dispatchBefore = ModernConfigApplyCoordinator.getInstance().dispatchRunCount();
        long beforeApply = ModernConfigApplyCoordinator.getInstance().successfulApplyCount();
        FontConfig.lerpMode = 9;

        // 不向 B submit；A 的晚到事件必须被 current Registration 过滤。
        listenerA.onConfigChanged(new ConfigChangeEvent(
                "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        assertSame(currentBeforeStaleSubmit,
                ModernConfigApplyCoordinator.getInstance().currentManager());
        assertEquals(generationBeforeStaleSubmit,
                ModernConfigApplyCoordinator.getInstance().currentGeneration());
        drainClient();

        assertEquals(managerB, ModernConfigApplyCoordinator.getInstance().currentManager());
        assertEquals(listenerB.generation(), ModernConfigApplyCoordinator.getInstance().currentGeneration());
        assertEquals("bridge 只能回灌 B", 2, FontConfig.lerpMode);
        assertEquals("单次 drain 只能执行一个 dispatch", dispatchBefore + 1,
                ModernConfigApplyCoordinator.getInstance().dispatchRunCount());
        assertEquals("B initial pending 恰 apply 一次", beforeApply + 1,
                ModernConfigApplyCoordinator.getInstance().successfulApplyCount());
        assertFalse(ModernConfigApplyCoordinator.getInstance().hasPending());
        assertFalse(ModernConfigApplyCoordinator.getInstance().needsRetry());
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
     * 失败期间新事件优先（reoffer 后）。
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
     * TEST_BEFORE_OWNER_RELEASE（monitor 外）：submit 最后事件，不丢。
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
     * 并发 register：generation 在锁内单调；结果唯一，current 为更大 generation。
     * 低世代晚入不存在（generation 锁内 ++ 后发布）。
     */
    @Test
    public void concurrentRegister_generationMonotonic_resultUnique() throws Exception {
        final int n = 8;
        File[] files = new File[n];
        ConfigManager[] managers = new ConfigManager[n];
        for (int i = 0; i < n; i++) {
            files[i] = tempFolder.newFile("coord-mono-" + i + ".yaml");
            managers[i] = ConfigManager.bootstrap(files[i], QzUiLibModernSchema.create());
        }

        final CyclicBarrier barrier = new CyclicBarrier(n);
        final CountDownLatch done = new CountDownLatch(n);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final AtomicLong[] gens = new AtomicLong[n];
        @SuppressWarnings("unchecked")
        final AtomicReference<ConfigSaveListener>[] listeners =
                (AtomicReference<ConfigSaveListener>[]) new AtomicReference[n];
        for (int i = 0; i < n; i++) {
            gens[i] = new AtomicLong(-1L);
            listeners[i] = new AtomicReference<ConfigSaveListener>();
        }

        for (int i = 0; i < n; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                    ConfigSaveListener la = new ConfigSaveListener(managers[idx]);
                    listeners[idx].set(la);
                    gens[idx].set(la.generation());
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            }, "reg-mono-" + idx).start();
        }
        assertTrue(done.await(15, TimeUnit.SECONDS));
        assertNull("worker 异常: " + failure.get(), failure.get());

        long maxGen = 0L;
        for (int i = 0; i < n; i++) {
            long g = gens[i].get();
            assertTrue("generation 必须 > 0: idx=" + i + " gen=" + g, g > 0);
            if (g > maxGen) {
                maxGen = g;
            }
        }
        // 锁内 ++：所有 generation 两两不同
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                assertTrue("generation 必须互异: " + gens[i].get() + " vs " + gens[j].get(),
                        gens[i].get() != gens[j].get());
            }
        }
        assertEquals("current 必须是最大 generation", maxGen,
                ModernConfigApplyCoordinator.getInstance().currentGeneration());
        // 低世代晚入不存在：current generation 恰为 max，无回退
        assertTrue(ModernConfigApplyCoordinator.getInstance().currentGeneration() >= n);
    }

    /**
     * apply 中同线程 reentrant register 快速抛 ISE 且协调器仍可后续工作。
     */
    @Test
    public void reentrantRegisterDuringApply_failFast_coordinatorStillWorks() throws Exception {
        File file = tempFolder.newFile("reentrant-reg.yaml");
        File file2 = tempFolder.newFile("reentrant-reg-2.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        ConfigManager manager2 = ConfigManager.bootstrap(file2, QzUiLibModernSchema.create());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(draft).isSuccess());
        DraftBuffer draft2 = manager2.openDraft();
        draft2.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        assertTrue(manager2.save(draft2).isSuccess());

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        drainAllClient();
        FontConfig.lerpMode = 0;
        FontConfig.onConfigReload();

        final AtomicReference<Throwable> reentrantErr = new AtomicReference<Throwable>();
        final AtomicInteger reentrantCaught = new AtomicInteger();
        ModernConfigApplyCoordinator.TEST_DURING_APPLY.set(() -> {
            try {
                ModernConfigApplyCoordinator.getInstance().register(manager2);
                fail("同线程 reentrant register 必须抛 ISE");
            } catch (IllegalStateException e) {
                reentrantCaught.incrementAndGet();
                reentrantErr.set(e);
            }
        });

        listener.onConfigChanged(new ConfigChangeEvent(
                "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        drainClient();
        assertEquals(1, reentrantCaught.get());
        assertNotNull(reentrantErr.get());
        assertTrue(reentrantErr.get() instanceof IllegalStateException);
        // 首次 apply 应成功（reentrant 被拒后继续）
        assertEquals(1, FontConfig.lerpMode);
        assertEquals(manager, ModernConfigApplyCoordinator.getInstance().currentManager());

        // 协调器仍可后续工作
        ConfigSaveListener listener2 = new ConfigSaveListener(manager2);
        FontConfig.lerpMode = 0;
        listener2.onConfigChanged(new ConfigChangeEvent(
                "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        drainClient();
        assertEquals(manager2, ModernConfigApplyCoordinator.getInstance().currentManager());
        assertEquals(2, FontConfig.lerpMode);
    }

    /**
     * 其他线程 register 在 apply 期间阻塞，apply 完成后成功。
     * hook 在持 monitor 时<strong>不得</strong> wait——仅 spawn 观察线程并轮询状态。
     */
    @Test
    public void otherThreadRegister_blocksDuringApply_succeedsAfter() throws Exception {
        File fA = tempFolder.newFile("block-reg-a.yaml");
        File fB = tempFolder.newFile("block-reg-b.yaml");
        ConfigManager mA = ConfigManager.bootstrap(fA, QzUiLibModernSchema.create());
        ConfigManager mB = ConfigManager.bootstrap(fB, QzUiLibModernSchema.create());
        DraftBuffer dA = mA.openDraft();
        dA.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(mA.save(dA).isSuccess());
        DraftBuffer dB = mB.openDraft();
        dB.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        assertTrue(mB.save(dB).isSuccess());

        ConfigSaveListener listenerA = new ConfigSaveListener(mA);
        drainAllClient();
        FontConfig.lerpMode = 0;
        FontConfig.onConfigReload();

        final CountDownLatch applyEntered = new CountDownLatch(1);
        final CountDownLatch allowApplyContinue = new CountDownLatch(1);
        final AtomicReference<Throwable> regBErr = new AtomicReference<Throwable>();
        final AtomicReference<ConfigSaveListener> listenerBRef =
                new AtomicReference<ConfigSaveListener>();
        final AtomicReference<Thread.State> blockedState =
                new AtomicReference<Thread.State>();

        // during-apply hook 持 monitor：不得 wait monitor；用 busy 等外部 release
        ModernConfigApplyCoordinator.TEST_DURING_APPLY.set(() -> {
            applyEntered.countDown();
            // 自旋等外部放行（不 wait 本 monitor）
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (allowApplyContinue.getCount() > 0) {
                if (System.nanoTime() > deadline) {
                    throw new AssertionError("allowApplyContinue 未在 5s 内放行");
                }
                Thread.yield();
            }
        });

        listenerA.onConfigChanged(new ConfigChangeEvent(
                "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));

        Thread drainer = new Thread(() -> drainClient(), "block-drain");
        drainer.start();
        assertTrue("应进入 apply", applyEntered.await(5, TimeUnit.SECONDS));

        Thread regB = new Thread(() -> {
            try {
                ConfigSaveListener lb = new ConfigSaveListener(mB);
                listenerBRef.set(lb);
            } catch (Throwable t) {
                regBErr.set(t);
            }
        }, "block-reg-b");
        regB.start();

        // 等 regB 进入 BLOCKED（持 monitor 被 apply 占用）
        long waitDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (regB.getState() != Thread.State.BLOCKED
                && regB.getState() != Thread.State.TERMINATED
                && System.nanoTime() < waitDeadline) {
            Thread.yield();
        }
        blockedState.set(regB.getState());
        assertEquals("其他线程 register 应在 apply 期间 BLOCKED",
                Thread.State.BLOCKED, blockedState.get());
        assertNull("register 尚未完成", listenerBRef.get());

        allowApplyContinue.countDown();
        drainer.join(5000);
        regB.join(5000);
        assertEquals(Thread.State.TERMINATED, drainer.getState());
        assertEquals(Thread.State.TERMINATED, regB.getState());
        assertNull("regB 异常: " + regBErr.get(), regBErr.get());
        assertNotNull(listenerBRef.get());
        assertEquals(mB, ModernConfigApplyCoordinator.getInstance().currentManager());
        assertTrue(listenerBRef.get().generation() > listenerA.generation());

        // B 的 initial apply 在 next tick
        clientEndTick();
        clientEndTick();
        assertEquals(2, FontConfig.lerpMode);
    }

    /**
     * TEST_BEFORE_OWNER_RELEASE 抛 AssertionError 后：owner 必须无条件 false，
     * 下一 tick submit/retry 仍可成功 apply。
     */
    @Test
    public void testHook_AssertionError_ownerReleased_nextTickApplySucceeds() throws Exception {
        File file = tempFolder.newFile("hook-assert-owner.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        assertTrue(manager.save(draft).isSuccess());
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        drainAllClient();
        FontConfig.lerpMode = 0;
        FontConfig.onConfigReload();

        ModernConfigApplyCoordinator.TEST_BEFORE_OWNER_RELEASE.set(() -> {
            throw new AssertionError("hook-must-surface-then-release-owner");
        });
        listener.onConfigChanged(new ConfigChangeEvent(
                "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        boolean threw = false;
        try {
            drainClient();
        } catch (AssertionError e) {
            threw = true;
            assertTrue(e.getMessage().contains("hook-must-surface-then-release-owner"));
        }
        assertTrue("AssertionError 不得被 hook 路径吞掉", threw);
        // 嵌套 finally：无论 Assertion 与否，owner 必须释放
        assertFalse("hook Assertion 后 enqueueOwner 必须 false",
                ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());

        // 下一 tick：submit 新事件必须可排并可成功 apply
        listener.onConfigChanged(new ConfigChangeEvent(
                "", null, null, ConfigChangeEvent.ChangeType.RELOAD));
        clientEndTick();
        assertEquals(2, FontConfig.lerpMode);
        assertFalse(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
        assertFalse(ModernConfigApplyCoordinator.getInstance().hasPending());
    }

    /**
     * 测试 hook AssertionError 必须回传（不得吞）。
     */
    @Test
    public void testHook_AssertionError_propagates() throws Exception {
        File file = tempFolder.newFile("hook-assert.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        drainAllClient();

        ModernConfigApplyCoordinator.TEST_BEFORE_OWNER_RELEASE.set(() -> {
            throw new AssertionError("hook-must-surface");
        });
        listener.onConfigChanged(new ConfigChangeEvent(
                "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        boolean threw = false;
        try {
            drainClient();
        } catch (AssertionError e) {
            threw = true;
            assertTrue(e.getMessage().contains("hook-must-surface"));
        }
        assertTrue("AssertionError 不得被 hook 路径吞掉", threw);
        assertFalse("Assertion 后 owner 必须 false",
                ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
    }

    /**
     * 并发 A/B register 与 stale submit：latch 保证 A registration 已存在、
     * B 发布后再恢复 A submit，不能 null 跳过；异常必须回主线程。
     */
    @Test
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
        // A/B registration 均已发布
        CountDownLatch bothRegistered = new CountDownLatch(2);
        // B 已 publish 后，stale-A 才允许 submit
        CountDownLatch bPublished = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(4);
        AtomicReference<ConfigSaveListener> listenerA = new AtomicReference<ConfigSaveListener>();
        AtomicReference<ConfigSaveListener> listenerB = new AtomicReference<ConfigSaveListener>();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Runnable regA = () -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                ConfigSaveListener la = new ConfigSaveListener(mA);
                listenerA.set(la);
                bothRegistered.countDown();
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
                bothRegistered.countDown();
                lb.onConfigChanged(new ConfigChangeEvent(
                        "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
                bPublished.countDown();
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        };
        new Thread(regA, "reg-a").start();
        new Thread(regB, "reg-b").start();
        // stale-A：等 A 已 register 且 B 已 publish，再 submit A（不得 null 跳过）
        new Thread(() -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                assertTrue("A 必须已 register", bothRegistered.await(5, TimeUnit.SECONDS));
                assertTrue("B 必须已 publish", bPublished.await(5, TimeUnit.SECONDS));
                ConfigSaveListener la = listenerA.get();
                assertNotNull("A registration 不得 null 跳过", la);
                la.onConfigChanged(new ConfigChangeEvent(
                        "", null, null, ConfigChangeEvent.ChangeType.RELOAD));
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        }, "stale-a").start();
        new Thread(() -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                assertTrue("A/B 必须已 register", bothRegistered.await(5, TimeUnit.SECONDS));
                ConfigSaveListener lb = listenerB.get();
                assertNotNull("B registration 不得 null 跳过", lb);
                lb.onConfigChanged(new ConfigChangeEvent(
                        "", null, null, ConfigChangeEvent.ChangeType.RELOAD));
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            } finally {
                done.countDown();
            }
        }, "stale-b").start();

        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS));
        // 异常必须回主线程
        assertNull("worker 异常必须回主线程: " + failure.get(), failure.get());
        assertNotNull("A listener 必须存在", listenerA.get());
        assertNotNull("B listener 必须存在", listenerB.get());

        for (int i = 0; i < 6; i++) {
            clientEndTick();
        }
        ConfigManager current = ModernConfigApplyCoordinator.getInstance().currentManager();
        assertNotNull(current);
        assertTrue("current 必须是 mA 或 mB", current == mA || current == mB);
        long genA = listenerA.get().generation();
        long genB = listenerB.get().generation();
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

        final Thread bridgeThread = Thread.currentThread();
        long before = ModernConfigApplyCoordinator.getInstance().dispatchRunCount();

        clientEndTick();
        assertEquals(0, FontConfig.lerpMode);
        assertTrue(ModernConfigApplyCoordinator.getInstance().hasPending()
                || ModernConfigApplyCoordinator.getInstance().needsRetry());
        Thread firstDispatch = ModernConfigApplyCoordinator.getInstance().lastDispatchThread();
        assertNotNull("coordinator task 应已在 bridge drain 线程执行", firstDispatch);
        assertSame("apply 在桥 drain 线程（本测试用 clientEndTick 模拟）",
                bridgeThread, firstDispatch);
        assertEquals(before + 1, ModernConfigApplyCoordinator.getInstance().dispatchRunCount());

        clientEndTick();
        assertEquals(2, FontConfig.lerpMode);
        assertSame(bridgeThread, ModernConfigApplyCoordinator.getInstance().lastDispatchThread());
        assertEquals(before + 2, ModernConfigApplyCoordinator.getInstance().dispatchRunCount());
        assertFalse(ModernConfigApplyCoordinator.getInstance().isEnqueueOwned());
        assertFalse(ModernConfigApplyCoordinator.getInstance().hasPending());

        assertNotNull(ForgeMainThreadDispatcherBridge.getInstance());
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

    /**
     * Forge bridge 真实 START/END 事件：仅 END 执行 retry+drain；START 不排空。
     */
    @Test
    public void forgeBridge_realStartEndEvents_onlyEndDrains() throws Exception {
        File file = tempFolder.newFile("forge-start-end.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        assertTrue(manager.save(draft).isSuccess());
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        drainAllClient();
        FontConfig.lerpMode = 0;
        FontConfig.onConfigReload();

        listener.onConfigChanged(new ConfigChangeEvent(
                "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        assertTrue(MainThreadDispatcher.getInstance().clientQueueSize() >= 1);

        TickEvent.ClientTickEvent start =
                new TickEvent.ClientTickEvent(TickEvent.Phase.START);
        TickEvent.ClientTickEvent end =
                new TickEvent.ClientTickEvent(TickEvent.Phase.END);

        ForgeMainThreadDispatcherBridge bridge = ForgeMainThreadDispatcherBridge.getInstance();
        bridge.onClientTick(start);
        assertTrue("START 后队列应仍有任务",
                MainThreadDispatcher.getInstance().clientQueueSize() >= 1);
        assertEquals(0, FontConfig.lerpMode);

        bridge.onClientTick(end);
        assertEquals(2, FontConfig.lerpMode);
        assertEquals(0, MainThreadDispatcher.getInstance().clientQueueSize());
    }
}
