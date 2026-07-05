package club.heiqi.uilib.config.modern;

import java.io.File;
import java.util.Arrays;
import java.util.List;

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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link ConfigSaveListener} 的 L1 逻辑测试。
 *
 * <p>验证 listener 正确区分 {@link ConfigChangeEvent.ChangeType#BATCH_SAVE} 与其他事件类型：</p>
 * <ul>
 *   <li>BATCH_SAVE 触发 {@link ConfigValueBridge} 回灌：Authority 值写入静态字段</li>
 *   <li>SET / REMOVE / CLEAR 不触发回灌：静态字段不被覆盖</li>
 * </ul>
 *
 * <p><b>静态污染防护</b>：FontConfig / Config 字段全是 public static，全局共享。
 * setup 保存初值，teardown 恢复，避免污染其他测试（参考 ConfigValueBridgeTest:80-143）。</p>
 *
 * <p><b>不断言</b>：FontService.reload 真执行。L1 测试线程非 Client thread，
 * FontService 线程闸（{@code FontService.java:454-478}）静默丢弃 reload，
 * 测试不崩但也不真执行 reload。reload 真执行靠 C3 启动回灌的真机验证。</p>
 */
public class ConfigSaveListenerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    // ===== Config 静态字段快照 =====
    private boolean saveUseDebug;
    private boolean saveUiDebug;
    private boolean saveFontRuntimeDebug;
    private String saveNetTransport;

    // ===== FontConfig 静态字段快照 =====
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

    /**
     * 保存所有受测静态字段初值，防止测试间相互污染。
     */
    @Before
    public void saveStaticState() {
        // Config
        saveUseDebug = Config.useDebug;
        saveUiDebug = Config.uiDebug;
        saveFontRuntimeDebug = Config.fontRuntimeDebug;
        saveNetTransport = Config.netTransport;
        // FontConfig
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

    /**
     * 恢复所有受测静态字段初值。
     */
    @After
    public void restoreStaticState() {
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
        // 刷新 characterRuleSet 派生态，避免快照泄漏
        FontConfig.refreshDerivedRuleSet();
    }

    /**
     * BATCH_SAVE 触发 Bridge 回灌：Authority 中的自定义值被写入静态字段。
     *
     * <p>证明 listener 收到 BATCH_SAVE 后调用了
     * {@link ConfigValueBridge#applyFromAuthority}，Authority 的值正确落到
     * {@code FontConfig.*} / {@code Config.*} 静态字段。</p>
     */
    @Test
    public void batchSaveTriggersValuePropagation() throws Exception {
        // 1. 造 manager，DraftBuffer 写自定义值后 save（Authority 已 applyAll 最新值）
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

        // 2. 构造 listener，发 BATCH_SAVE 事件
        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));

        // 3. 断言 Authority 值已被 Bridge 回灌到静态字段
        assertTrue("useDebug 应被回灌为 true", Config.useDebug);
        assertEquals("lerpMode 应被回灌为 1", 1, FontConfig.lerpMode);
        assertEquals("brightnessGain 应被回灌为 3.5", 3.5, FontConfig.brightnessGain, 0.0);
        assertArrayEquals("fontSort 应被回灌为 [Sans]", new String[] {"Sans"}, FontConfig.fontSort);
    }

    /**
     * SET 事件不触发回灌：静态字段保持调用前的值。
     *
     * <p>证明 listener 对非 BATCH_SAVE 事件直接 return，不调 Bridge。
     * 预设 manager 带 lerpMode=1，调用前把 {@code FontConfig.lerpMode} 设为 3（非默认），
     * 发 SET 事件后断言 lerpMode 仍为 3（未被覆盖为 Authority 的 1）。</p>
     */
    @Test
    public void setEventDoesNotTriggerPropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-set.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        // DraftBuffer 写自定义 lerpMode=1（Authority 里有 1，证明"未被覆盖"可观测）
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        assertTrue(manager.save(draft).isSuccess());

        // 调用前把静态字段设为非 Authority 值（3），确保"未被覆盖"可观测
        FontConfig.lerpMode = 3;

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("fontSystem.lerpMode", Double.valueOf(3.0),
                Double.valueOf(1.0), ConfigChangeEvent.ChangeType.SET));

        // 断言未被覆盖（若误触发 Bridge，lerpMode 会变成 1）
        assertEquals("SET 事件不应触发回灌，lerpMode 应保持 3", 3, FontConfig.lerpMode);
    }

    /**
     * REMOVE 事件同样不触发回灌（用例 C：覆盖另一非 BATCH_SAVE 类型）。
     */
    @Test
    public void removeEventDoesNotTriggerPropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-remove.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.brightnessGain", Double.valueOf(3.5));
        assertTrue(manager.save(draft).isSuccess());

        // 调用前设非 Authority 值
        FontConfig.brightnessGain = 2.0;

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("fontSystem.brightnessGain",
                Double.valueOf(3.5), null, ConfigChangeEvent.ChangeType.REMOVE));

        assertEquals("REMOVE 事件不应触发回灌，brightnessGain 应保持 2.0",
                2.0, FontConfig.brightnessGain, 0.0);
    }

    /**
     * CLEAR 事件同样不触发回灌（用例 C 补充：覆盖第三种非 BATCH_SAVE 类型）。
     */
    @Test
    public void clearEventDoesNotTriggerPropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-clear.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        // 调用前设非默认值
        Config.useDebug = true;
        FontConfig.lerpMode = 3;

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.CLEAR));

        assertTrue("CLEAR 事件不应触发回灌，useDebug 应保持 true", Config.useDebug);
        assertEquals("CLEAR 事件不应触发回灌，lerpMode 应保持 3", 3, FontConfig.lerpMode);
    }

    /**
     * RELOAD 事件同样不触发回灌（用例 C 补充：覆盖第四种非 BATCH_SAVE 类型，
     * 证明 listener 严格只认 BATCH_SAVE，不与 ConfigManager 内部 RELOAD 语义误耦合）。
     */
    @Test
    public void reloadEventDoesNotTriggerPropagation() throws Exception {
        File file = tempFolder.newFile("qzuilib-listener-reload.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        // 调用前设非默认值
        FontConfig.lerpMode = 3;
        Config.netTransport = "preset";

        ConfigSaveListener listener = new ConfigSaveListener(manager);
        listener.onConfigChanged(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.RELOAD));

        assertEquals("RELOAD 事件不应触发回灌，lerpMode 应保持 3", 3, FontConfig.lerpMode);
        assertEquals("RELOAD 事件不应触发回灌，netTransport 应保持 preset",
                "preset", Config.netTransport);
    }
}
