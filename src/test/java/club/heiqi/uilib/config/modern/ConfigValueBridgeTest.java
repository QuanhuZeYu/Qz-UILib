package club.heiqi.uilib.config.modern;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import java.awt.Font;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.uilib.Config;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.util.FontOrderPlanner;
import net.minecraftforge.common.config.Configuration;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ConfigValueBridge} 的 L1 逻辑测试。
 *
 * <p>验证 Bridge 把 {@link Authority} 的值正确回灌到 {@code Config.*} / {@code FontConfig.*}
 * 静态字段，覆盖三类断言：</p>
 * <ol>
 *   <li>默认值回灌：空 YAML bootstrap 后调 applyFromAuthority，断言每字段 == schema 默认</li>
 *   <li>类型边界：int 字段非 3.0 而是 3；空 SIMPLE_LIST 回灌为非 null 的空数组</li>
 *   <li>characterRuleSet 派生刷新：喂规则后 characterRuleSet 非 empty</li>
 * </ol>
 *
 * <p><b>静态污染防护</b>：FontConfig / Config 字段全是 public static，全局共享。
 * setup 保存初值，teardown 恢复，避免污染其他测试。</p>
 *
 * <p>不测 affectsFontRuntime / FontService.reload / onConfigReload —— 那些是 C2 listener 的范围。</p>
 */
public class ConfigValueBridgeTest {

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
     * 空 YAML bootstrap 后回灌，断言全部字段 == schema 默认值。
     */
    @Test
    public void applyFromAuthorityPopulatesAllDefaults() throws Exception {
        Authority authority = bootstrapEmpty();
        ConfigValueBridge.applyFromAuthority(authority);

        // general section
        assertFalse(Config.useDebug);
        assertFalse(Config.uiDebug);
        assertFalse(Config.fontRuntimeDebug);
        assertEquals("vanilla", Config.netTransport);

        // fontSystem section
        assertEquals(3, FontConfig.lerpMode);
        assertEquals(2, FontConfig.aaMode);
        assertEquals(2.0, FontConfig.brightnessGain, 0.0);
        assertEquals(4.0, FontConfig.spaceWidth, 0.0);
        assertEquals(0.1, FontConfig.characterSpacing, 0.0);
        assertEquals(0.5, FontConfig.shadowOffsetX, 0.0);
        assertEquals(0.5, FontConfig.shadowOffsetY, 0.0);
        assertEquals(0.0, FontConfig.renderOffset, 0.0);
        assertEquals(0.0, FontConfig.smoothRangeMin, 0.0);
        assertEquals(0.9, FontConfig.smoothRangeMax, 0.0);
        assertEquals(20.0, FontConfig.drawStageUploadIntervalMs, 0.0);
        assertEquals(20, FontConfig.drawStageUploadLimitPerSecond);
        assertEquals(2, FontConfig.drawStageUploadBatchSize);
        assertEquals(12.0, FontConfig.aaStrength, 0.0);
        assertFalse(FontConfig.replaceOrigin);
        assertFalse(FontConfig.customInvCountFont);
        // fontSortConfigured：bootstrap 空_yaml 后 fontSort 为空数组 → false（用户未配置字体顺序）
        // 对应需求 3：Bridge 按 fontSort 非空设此字段，FontRegistry.reload 据此走系统字体优先级提示分支
        assertFalse("fontSort 为空时 fontSortConfigured 应为 false（走 DefaultFontOrderHints）",
                FontConfig.fontSortConfigured);

        // fontSizeSetting section
        assertEquals(64.0, FontConfig.awtCharSize, 0.0);
        assertEquals(9.0, FontConfig.charSize, 0.0);
    }

    /**
     * 类型边界：int 字段写的是 int（如 lerpMode==3 而非 3.0），空 SIMPLE_LIST 写非 null 空数组。
     */
    @Test
    public void intFieldsAndEmptyListsHandledCorrectly() throws Exception {
        Authority authority = bootstrapEmpty();
        ConfigValueBridge.applyFromAuthority(authority);

        // int 字段类型正确（Math.round(double)→long→int 强转，无浮点残留）
        assertEquals(3, FontConfig.lerpMode);
        assertEquals(2, FontConfig.aaMode);
        assertEquals(20, FontConfig.drawStageUploadLimitPerSecond);
        assertEquals(2, FontConfig.drawStageUploadBatchSize);

        // 空列表 → 非 null 的空 String[]
        assertNotNull("空 fontSort 不应为 null", FontConfig.fontSort);
        assertEquals(0, FontConfig.fontSort.length);
        assertNotNull("空 characterFontRules 不应为 null", FontConfig.characterFontRules);
        assertEquals(0, FontConfig.characterFontRules.length);

        // 默认 characterRuleSet 应为 empty
        assertTrue("默认 characterRuleSet 应为 empty",
                FontConfig.getCharacterRuleSet().isEmpty());
    }

    /**
     * 喂入非空 characterFontRules 后，characterRuleSet 派生态被刷新为非 empty。
     *
     * <p>验证 Bridge 写完 characterFontRules 后调用了 {@link FontConfig#refreshDerivedRuleSet()}，
     * 守宪章派生态不陈旧。</p>
     */
    @Test
    public void characterRuleSetRefreshedAfterFeedingRules() throws Exception {
        File file = tempFolder.newFile("qzuilib-rules.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        // 通过 DraftBuffer + save 写入一条合法字符规则，再重新 bootstrap 让 Authority 持久值生效
        DraftBuffer draft = manager.openDraft();
        List<String> rules = Arrays.asList("a=Sans");
        draft.setDraft("fontSystem.characterFontRules", rules);
        SaveOutcome outcome = manager.save(draft);
        assertTrue("保存应成功: " + outcome.status(), outcome.isSuccess());

        // 重新 bootstrap 让 Authority 持久值刷新（save 后 Authority 也应已更新，但保险起见重新读）
        ConfigManager reloaded = ConfigManager.bootstrap(file, schema);
        ConfigValueBridge.applyFromAuthority(reloaded.authority());

        // characterFontRules 已喂入
        assertNotNull(FontConfig.characterFontRules);
        assertEquals(1, FontConfig.characterFontRules.length);
        assertEquals("a=Sans", FontConfig.characterFontRules[0]);
        // characterRuleSet 派生态被刷新：非 empty
        assertFalse("characterRuleSet 应被刷新为非 empty",
                FontConfig.getCharacterRuleSet().isEmpty());
    }

    /**
     * 喂入自定义标量值，断言每个字段都正确从 Authority 取到。
     */
    @Test
    public void customValuesPropagatedToStaticFields() throws Exception {
        File file = tempFolder.newFile("qzuilib-custom.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("general.useDebug", Boolean.TRUE);
        draft.setDraft("general.netTransport", "forge");
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        draft.setDraft("fontSystem.brightnessGain", Double.valueOf(3.5));
        draft.setDraft("fontSizeSetting.charSize", Double.valueOf(12.0));
        draft.setDraft("fontSizeSetting.awtCharSize", Double.valueOf(96.0));
        assertTrue(manager.save(draft).isSuccess());

        ConfigValueBridge.applyFromAuthority(manager.authority());

        assertTrue(Config.useDebug);
        assertEquals("forge", Config.netTransport);
        assertEquals(1, FontConfig.lerpMode);
        assertEquals(3.5, FontConfig.brightnessGain, 0.0);
        assertEquals(12.0, FontConfig.charSize, 0.0);
        assertEquals(96.0, FontConfig.awtCharSize, 0.0);
    }

    /**
     * 喂入 fontSort 列表，断言 String[] 顺序与内容正确。
     */
    @Test
    public void fontSortListConvertedToStringArray() throws Exception {
        File file = tempFolder.newFile("qzuilib-fontsort.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("fontSystem.fontSort", Arrays.asList("Sans", "Serif", "Mono"));
        assertTrue(manager.save(draft).isSuccess());

        ConfigValueBridge.applyFromAuthority(manager.authority());

        assertArrayEquals(new String[] {"Sans", "Serif", "Mono"}, FontConfig.fontSort);
        // 需求 3：fontSort 非空 → fontSortConfigured = true（FontRegistry.reload 走用户配置分支）
        assertTrue("fontSort 非空时 fontSortConfigured 应为 true（走用户配置）",
                FontConfig.fontSortConfigured);
    }

    /**
     * 残缺 Authority（path 全部不存在）下 applyFromAuthority 走降级路径。
     *
     * <p>对应 C1 P2.3：用空 schema（无任何 section/field）bootstrap 出的 Authority，
     * 其 getNumber/getString/getBool/get 在所有 path 上均返降级默认值（0.0/null/false/null），
     * 验证这些值被直接写入对应静态字段，SIMPLE_LIST 经 listToStringArray null 守卫转空数组不 NPE。</p>
     *
     * <p>构造方式选"空 schema bootstrap"（方式 1）：ConfigSchema.builder(name).build() 在
     * SchemaTestFactory:44 / UiSchemaFactory:44 已有合法用法，无字段亦可 bootstrap，
     * 比反射清 typedValues 或 mock Authority 更干净。</p>
     */
    @Test
    public void degradedValuesWhenAuthorityPathMissing() throws Exception {
        // 1. 先把静态字段设为"非降级值"，确保降级写入可被观测
        FontConfig.lerpMode = 3;
        FontConfig.brightnessGain = 2.0;
        Config.fontRuntimeDebug = true;
        Config.netTransport = "vanilla";
        FontConfig.fontSort = new String[]{"existing"};
        FontConfig.characterFontRules = new String[]{"a=Sans"};

        // 2. 造残缺 Authority：空 schema bootstrap，authority 几乎没字段，所有 path 不存在
        File file = tempFolder.newFile("qzuilib-degraded.yaml");
        ConfigSchema emptySchema = ConfigSchema.builder("empty").build();
        Authority degraded = ConfigManager.bootstrap(file, emptySchema).authority();

        // 3. 调 applyFromAuthority，残缺 path 全部走降级
        ConfigValueBridge.applyFromAuthority(degraded);

        // 4. 断言降级值已写入
        // getNumber 降级 0.0 → Math.round(0.0)=0 → int 0
        assertEquals(0, FontConfig.lerpMode);
        // getNumber 降级 0.0
        assertEquals(0.0, FontConfig.brightnessGain, 0.0);
        // getBool 降级 false
        assertFalse(Config.fontRuntimeDebug);
        // getString 降级 null
        assertNull(Config.netTransport);
        // get 返 null → listToStringArray 转 new String[0]，不 NPE
        assertNotNull("fontSort null 守卫应转空数组", FontConfig.fontSort);
        assertEquals(0, FontConfig.fontSort.length);
        assertNotNull("characterFontRules null 守卫应转空数组", FontConfig.characterFontRules);
        assertEquals(0, FontConfig.characterFontRules.length);
    }

    /**
     * 验证 applyFromAuthority 调 FontConfig.detachLegacyConfiguration 后,
     * applyFontOrderSnapshot 的反向持久化链路（persistFontSortToConfiguration）no-op。
     *
     * <p>对应 C 后续反向改向子任务 A2：行为观测构造——
     * 先 FontConfig.load(configuration) 令 activeConfiguration = configuration，
     * 再调 applyFromAuthority 触发 detach，最后调 applyFontOrderSnapshot，
     * 断言 .cfg 内 fontSort 仍为 load 写入的原值（["Bravo", "Alpha"]）,
     * 未被 FontOrderPlanner 输出的 resolved 顺序反向写覆盖。</p>
     *
     * <p>本测试不依赖反射破 private，纯行为观测，与 FontConfigCategoryTest 老用例（测 FontConfig
     * 字面行为不配 Bridge）形成互补：C2/C3 新栈路径下走 Bridge 必 detach，老路径下不 detach。</p>
     */
    @Test
    public void detachLegacyConfigurationAfterApplyFromAuthority() throws Exception {
        // 1. 准备 Forge Configuration，写入 fontSort = ["Bravo", "Alpha"]
        Configuration configuration = new Configuration();
        configuration.get("fontsystem", "fontSort", new String[] {"Bravo", "Alpha"}, "字体排序");
        // 2. FontConfig.load 令 activeConfiguration = configuration
        FontConfig.load(configuration);

        // 3. 调 applyFromAuthority 触发 detach → activeConfiguration = null
        Authority authority = bootstrapEmpty();
        ConfigValueBridge.applyFromAuthority(authority);

        // 4. 模拟 FontRegistry.reload 内的调用：applyFontOrderSnapshot
        FontConfig.applyFontOrderSnapshot(new FontOrderPlanner().plan(Arrays.asList(
                new Font("Alpha", Font.PLAIN, 14),
                new Font("Bravo", Font.PLAIN, 14)),
                new String[] {"Alpha", "Bravo", "Missing"}));

        // 5. 断言 .cfg 内 fontSort 仍为 load 写入的原值（未被反向写覆盖为 resolved 顺序）
        Assert.assertArrayEquals(new String[] {"Bravo", "Alpha"},
                configuration.get("fontsystem", "fontSort", new String[0], "字体排序").getStringList());
    }

    /**
     * 构造一个空 YAML bootstrap 出的 Authority。
     */
    private Authority bootstrapEmpty() throws Exception {
        File file = tempFolder.newFile("qzuilib-empty.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        return ConfigManager.bootstrap(file, schema).authority();
    }
}
