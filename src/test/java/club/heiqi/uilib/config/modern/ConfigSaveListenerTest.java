package club.heiqi.uilib.config.modern;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.uilib.Config;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.net.core.MainThreadDispatcher;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link ConfigSaveListener} 的 L1 逻辑测试。
 *
 * <p>验证 listener 正确区分 {@link ConfigChangeEvent.ChangeType#BATCH_SAVE} /
 * {@link ConfigChangeEvent.ChangeType#RELOAD} 与其他事件类型；事件经
 * {@link MainThreadDispatcher} CLIENT 队列主线程回灌（latest-wins）。</p>
 *
 * <p><b>静态污染防护</b>：FontConfig / Config 字段全是 public static，全局共享。
 * setup 保存初值，teardown 恢复，并清理 dispatcher 队列。</p>
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
        // 清 dispatcher，避免跨测试残留任务
        MainThreadDispatcher.getInstance().drainClient();
        MainThreadDispatcher.getInstance().drainServer();

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
        SaveOutcome outcome = manager.save(draft);
        assertTrue("保存应成功: " + outcome.status(), outcome.isSuccess());

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        // 入队后须 drain 主线程才应用
        drainClient();

        assertTrue("useDebug 应被回灌为 true", Config.useDebug);
        assertEquals("lerpMode 应被回灌为 1", 1, FontConfig.lerpMode);
        assertEquals("brightnessGain 应被回灌为 3.5", 3.5, FontConfig.brightnessGain, 0.0);
        assertArrayEquals("fontSort 应被回灌为 [Sans]", new String[] {"Sans"}, FontConfig.fontSort);
    }

    @Test
    public void setEventDoesNotTriggerPropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-set.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(draft).isSuccess());

        FontConfig.lerpMode = 3;

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("fontSystem.lerpMode", Double.valueOf(3.0),
                Double.valueOf(1.0), ConfigChangeEvent.ChangeType.SET));
        drainClient();

        assertEquals("SET 事件不应触发回灌，lerpMode 应保持 3", 3, FontConfig.lerpMode);
    }

    @Test
    public void removeEventDoesNotTriggerPropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-remove.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.brightnessGain", Double.valueOf(3.5));
        assertTrue(manager.save(draft).isSuccess());

        FontConfig.brightnessGain = 2.0;

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("fontSystem.brightnessGain",
                Double.valueOf(3.5), null, ConfigChangeEvent.ChangeType.REMOVE));
        drainClient();

        assertEquals("REMOVE 事件不应触发回灌，brightnessGain 应保持 2.0",
                2.0, FontConfig.brightnessGain, 0.0);
    }

    @Test
    public void clearEventDoesNotTriggerPropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-clear.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        Config.useDebug = true;
        FontConfig.lerpMode = 3;

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.CLEAR));
        drainClient();

        assertTrue("CLEAR 事件不应触发回灌，useDebug 应保持 true", Config.useDebug);
        assertEquals("CLEAR 事件不应触发回灌，lerpMode 应保持 3", 3, FontConfig.lerpMode);
    }

    @Test
    public void reloadEventTriggersValuePropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-reload-yes.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("general.useDebug", Boolean.TRUE);
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(draft).isSuccess());

        FontConfig.lerpMode = 9;
        Config.useDebug = false;

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.RELOAD));
        drainClient();

        assertTrue("RELOAD 应回灌 useDebug", Config.useDebug);
        assertEquals("RELOAD 应回灌 lerpMode=1", 1, FontConfig.lerpMode);
    }

    /** worker 线程发布 BATCH_SAVE → drain 后回灌。 */
    @Test
    public void workerThreadPublishBatchSave_appliesOnDrain() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-worker.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(draft).isSuccess());

        FontConfig.lerpMode = 7;
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        CountDownLatch done = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                listener.onConfigChanged(new ConfigChangeEvent(
                        "", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
            } finally {
                done.countDown();
            }
        }, "listener-worker");
        worker.start();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        // 未 drain 前不应应用
        assertEquals(7, FontConfig.lerpMode);
        drainClient();
        assertEquals(1, FontConfig.lerpMode);
    }

    /** 连发 latest-wins：只应用最后一次 reason 对应的 Authority 状态。 */
    @Test
    public void rapidFireLatestWins() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-latest.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer d1 = manager.openDraft();
        d1.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(d1).isSuccess());

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        // 连续入队：owner 只占一次，pending 覆盖
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        DraftBuffer d2 = manager.openDraft();
        d2.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));
        assertTrue(manager.save(d2).isSuccess());
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.RELOAD));
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));

        FontConfig.lerpMode = 0;
        drainClient();
        // 最终 Authority 为 2
        assertEquals(2, FontConfig.lerpMode);
    }

    /** submit/drain 竞态：drain 释放 owner 时新事件不丢。 */
    @Test
    public void drainReleaseRace_doesNotDropLastEvent() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-race.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(draft).isSuccess());

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        AtomicInteger applies = new AtomicInteger();
        // 第一次入队
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        // 模拟：在 drain 过程中再 submit（通过包内无法 hook，用连续 drain+submit）
        MainThreadDispatcher.getInstance().enqueue(
                club.heiqi.uilib.net.transport.NetSide.CLIENT, () -> {
                    // 在同一 drain 批内再发事件
                    DraftBuffer d2 = null;
                    try {
                        d2 = manager.openDraft();
                        d2.setDraft("fontSystem.lerpMode", Double.valueOf(3.0));
                        manager.save(d2);
                    } catch (Exception ignored) {
                    }
                    listener.onConfigChanged(new ConfigChangeEvent(
                            "", null, null, ConfigChangeEvent.ChangeType.RELOAD));
                    applies.incrementAndGet();
                });
        FontConfig.lerpMode = 0;
        // 多轮 drain 直到队列空
        for (int i = 0; i < 5; i++) {
            drainClient();
        }
        assertEquals("最终应回灌 Authority=3", 3, FontConfig.lerpMode);
    }
}
