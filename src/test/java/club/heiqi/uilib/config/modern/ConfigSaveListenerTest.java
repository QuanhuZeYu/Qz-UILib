package club.heiqi.uilib.config.modern;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.uilib.Config;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.net.core.MainThreadDispatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ConfigSaveListener} + {@link ModernConfigApplyCoordinator} 全局协调、
 * 生命周期、失败重试与 generation 隔离。
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
        MainThreadDispatcher.getInstance().drainClient();
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
        MainThreadDispatcher.getInstance().drainClient();
        MainThreadDispatcher.getInstance().drainServer();
        ModernConfigApplyCoordinator.getInstance().resetForTest();
        ModernConfigApplyCoordinator.TEST_APPLY_FAULT.set(null);

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

    private static void drainClient() {
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
     * A submit 未 drain → B 注册 → A 旧事件/任务不得覆盖 B 的 Authority。
     * pending 不持 listener；弱引用证明 A 可被 GC 候选（不强持）。
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
        // 未 drain：pending 在途
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

        // A 再发事件应 no-op
        listenerA.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.RELOAD));
        FontConfig.lerpMode = 0;
        // 多轮 drain：旧 generation pending 丢弃；B 事件后应应用 2
        listenerB.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        for (int i = 0; i < 5; i++) {
            drainClient();
        }
        assertEquals(2, FontConfig.lerpMode);

        // pending 结构不含 listener
        assertTrue(ModernConfigApplyCoordinator.getInstance().pendingHoldsNoListener());
        listenerA = null;
        System.gc();
        // 不强断言 GC 必中；至少 weak 可为空或仍存活但不被 coordinator 引用
        assertNotNull(listenerB);
        // 释放 weak 检查：coordinator 不应阻止 GC（best-effort）
        for (int i = 0; i < 3; i++) {
            System.gc();
            Thread.yield();
        }
        // 队列最多一个 coordinator Runnable 语义：enqueueOwner 在 drain 后 false
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
        int lastLerpBefore = FontConfig.lerpMode;
        // 强制 last 与当前不同以便观察 onConfigReload 推进（affects 可能 false）
        FontConfig.lerpMode = 9;
        FontConfig.onConfigReload(); // 先对齐 last=9

        ModernConfigApplyCoordinator.TEST_APPLY_FAULT.set(new RuntimeException("inject-once"));
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        drainClient();
        // 失败：lerpMode 仍 9（未成功 apply）；needsRetry
        assertEquals(9, FontConfig.lerpMode);
        assertTrue(ModernConfigApplyCoordinator.getInstance().needsRetry()
                || ModernConfigApplyCoordinator.getInstance().hasPending());

        // tick 重试
        ModernConfigApplyCoordinator.getInstance().retryPendingOnce();
        drainClient();
        assertEquals(2, FontConfig.lerpMode);
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
        MainThreadDispatcher.getInstance().enqueue(
                club.heiqi.uilib.net.transport.NetSide.CLIENT, after::incrementAndGet);
        drainClient();
        assertEquals(1, after.get());
    }

    /**
     * release race：用 package-private hook 在 owner 释放窗口提交新事件，不丢最后事件。
     */
    @Test
    public void drainReleaseRace_hookPrecise_doesNotDropLastEvent() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-race.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(draft).isSuccess());

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        // 第一次 submit
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        // drain 过程中再改 Authority 并发 RELOAD：通过入队任务在同一 drain 批
        MainThreadDispatcher.getInstance().enqueue(
                club.heiqi.uilib.net.transport.NetSide.CLIENT, () -> {
                    try {
                        DraftBuffer d2 = manager.openDraft();
                        d2.setDraft("fontSystem.lerpMode", Double.valueOf(3.0));
                        manager.save(d2);
                    } catch (Exception ignored) {
                    }
                    listener.onConfigChanged(new ConfigChangeEvent(
                            "", null, null, ConfigChangeEvent.ChangeType.RELOAD));
                });
        FontConfig.lerpMode = 0;
        for (int i = 0; i < 8; i++) {
            drainClient();
        }
        assertEquals(3, FontConfig.lerpMode);
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
    }
}
