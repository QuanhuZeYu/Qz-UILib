package club.heiqi.uilib.config.modern;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.uilib.Config;
import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.config.FontConfig;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ModernConfigBootstrap#bootstrapAndApply} 的 L1 逻辑测试。
 *
 * <p>验证启动加载首次回灌完整路径：</p>
 * <ol>
 *   <li>用例 A：空 YAML bootstrap + 回灌默认值（证明 bootstrap + Bridge 生效）</li>
 *   <li>用例 B：自定义值 YAML bootstrap + 回灌自定义值（证明持久化值正确回灌）</li>
 *   <li>用例 C：非法 YAML 容错（bootstrap 失败不中断，静态字段保持调用前值）</li>
 * </ol>
 *
 * <p><b>静态污染防护</b>：FontConfig / Config 字段全是 public static，全局共享。
 * setup 保存初值，teardown 恢复 + refreshDerivedRuleSet + onConfigReload
 * （参考 {@link ConfigSaveListenerTest} C2 P2 收尾，防 {@code last*} 快照跨测试漂移）。</p>
 *
 * <p><b>不断言</b>：reload 真执行——L1 测试线程非 Client/Server thread，
 * {@link club.heiqi.uilib.font.FontService#reload} 线程闸静默丢弃，不崩但不真执行。
 * 也不测 {@link club.heiqi.uilib.font.FontService#initialize}（#71 起它是
 * {@code ClientProxy.preInit} 的职责，专用服务端根本不引导渲染骨架，L1 难覆盖）。</p>
 */
public class ModernConfigBootstrapTest {

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
     * 恢复所有受测静态字段初值，防 {@code last*} 快照跨测试漂移。
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
        // 同步 last* 私有快照到恢复后的 public 值（bootstrapAndApply 末段会触发
        // onConfigReload 更新 last*，@After 恢复 public 后需重新同步 last*，防跨测试漂移——同 C2 P2）
        FontConfig.onConfigReload();
    }

    /**
     * 用例 A：空 YAML bootstrap + 回灌，断言全部字段 == schema 默认值。
     *
     * <p>证明 {@link ModernConfigBootstrap#bootstrapAndApply} 正确执行
     * bootstrap（空文件等效全新 mod，Authority 全部走 schema 默认值）
     * + Bridge 回灌到静态字段。</p>
     */
    @Test
    public void bootstrapAndApplyPopulatesAllDefaults() throws Exception {
        File file = tempFolder.newFile("qzuilib-bootstrap-default.yaml");
        ModernConfigBootstrap.bootstrapAndApply(file);

        // general section
        assertFalse("useDebug 默认 false", Config.useDebug);
        assertFalse("uiDebug 默认 false", Config.uiDebug);
        assertFalse("fontRuntimeDebug 默认 false", Config.fontRuntimeDebug);
        assertEquals("netTransport 默认 vanilla", "vanilla", Config.netTransport);

        // fontSystem section
        assertEquals("lerpMode 默认 3", 3, FontConfig.lerpMode);
        assertEquals("aaMode 默认 2", 2, FontConfig.aaMode);
        assertEquals("brightnessGain 默认 2.0", 2.0, FontConfig.brightnessGain, 0.0);
        assertEquals("spaceWidth 默认 4.0", 4.0, FontConfig.spaceWidth, 0.0);
        assertEquals("characterSpacing 默认 0.1", 0.1, FontConfig.characterSpacing, 0.0);
        assertEquals("shadowOffsetX 默认 0.5", 0.5, FontConfig.shadowOffsetX, 0.0);
        assertEquals("shadowOffsetY 默认 0.5", 0.5, FontConfig.shadowOffsetY, 0.0);
        assertEquals("renderOffset 默认 0.0", 0.0, FontConfig.renderOffset, 0.0);
        assertEquals("smoothRangeMin 默认 0.0", 0.0, FontConfig.smoothRangeMin, 0.0);
        assertEquals("smoothRangeMax 默认 0.9", 0.9, FontConfig.smoothRangeMax, 0.0);
        assertEquals("drawStageUploadIntervalMs 默认 20.0", 20.0, FontConfig.drawStageUploadIntervalMs, 0.0);
        assertEquals("drawStageUploadLimitPerSecond 默认 20", 20, FontConfig.drawStageUploadLimitPerSecond);
        assertEquals("drawStageUploadBatchSize 默认 2", 2, FontConfig.drawStageUploadBatchSize);
        assertEquals("aaStrength 默认 12.0", 12.0, FontConfig.aaStrength, 0.0);
        assertFalse("replaceOrigin 默认 false", FontConfig.replaceOrigin);
        assertFalse("customInvCountFont 默认 false", FontConfig.customInvCountFont);
        // fontSortConfigured：空 yaml bootstrap 后 fontSort 为空 → false（需求 3：按非空判断）
        assertFalse("fontSort 为空时 fontSortConfigured 应为 false", FontConfig.fontSortConfigured);
        // fontSort / characterFontRules 默认空 list → 空 String[]
        assertArrayEquals("fontSort 默认空数组", new String[0], FontConfig.fontSort);
        assertArrayEquals("characterFontRules 默认空数组", new String[0], FontConfig.characterFontRules);

        // fontSizeSetting section
        assertEquals("awtCharSize 默认 64.0", 64.0, FontConfig.awtCharSize, 0.0);
        assertEquals("charSize 默认 9.0", 9.0, FontConfig.charSize, 0.0);
    }

    /**
     * 用例 B：自定义值 YAML bootstrap + 回灌，断言 Authority 持久化的自定义值正确落到静态字段。
     *
     * <p>先用 {@link ConfigManager#bootstrap} + DraftBuffer + save 写自定义值到 YAML，
     * 再调 {@link ModernConfigBootstrap#bootstrapAndApply} 重新加载并回灌，
     * 断言每字段 == 持久化的自定义值。</p>
     */
    @Test
    public void bootstrapAndApplyPropagatesCustomValues() throws Exception {
        File file = tempFolder.newFile("qzuilib-bootstrap-custom.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        // 1. 第一次 bootstrap 写自定义值并保存
        ConfigManager writer = ConfigManager.bootstrap(file, schema);
        DraftBuffer draft = writer.openDraft();
        draft.setDraft("general.useDebug", Boolean.TRUE);
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        draft.setDraft("fontSystem.brightnessGain", Double.valueOf(3.5));
        draft.setDraft("fontSystem.fontSort", Arrays.asList("Sans"));
        draft.setDraft("fontSizeSetting.awtCharSize", Double.valueOf(96.0));
        SaveOutcome outcome = writer.save(draft);
        assertTrue("保存应成功: " + outcome.status(), outcome.isSuccess());

        // 2. 调 bootstrapAndApply 重新 bootstrap（读刚保存的 YAML）+ Bridge 回灌 + 补刀
        ModernConfigBootstrap.bootstrapAndApply(file);

        // 3. 断言自定义值已回灌
        assertTrue("useDebug 应为 true", Config.useDebug);
        assertEquals("lerpMode 应为 1", 1, FontConfig.lerpMode);
        assertEquals("brightnessGain 应为 3.5", 3.5, FontConfig.brightnessGain, 0.0);
        assertArrayEquals("fontSort 应为 [Sans]", new String[] {"Sans"}, FontConfig.fontSort);
        assertEquals("awtCharSize 应为 96.0", 96.0, FontConfig.awtCharSize, 0.0);
    }

    /**
     * 用例 C：非法 YAML 容错——bootstrap 失败不中断启动。
     *
     * <p>造一个非法 YAML 文件（写入乱码内容），调 {@link ModernConfigBootstrap#bootstrapAndApply}
     * 应：</p>
     * <ul>
     *   <li><b>不抛异常</b>（方法内部 catch ConfigException）</li>
     *   <li>静态字段保持调用前的值（bootstrap 失败 return，未走 Bridge 回灌）</li>
     * </ul>
     *
     * <p>造非法 YAML 方式：用 {@link java.io.PrintWriter} 写一段明显语法错乱的字符串
     * （{@code "!!!invalid yaml::: [[["}），使 YAML 解析器在 {@link ConfigManager#bootstrap}
     * 阶段抛 {@link club.heiqi.config.ConfigException}。</p>
     */
    @Test
    public void bootstrapAndApplyToleratesInvalidYaml() throws Exception {
        File file = tempFolder.newFile("qzuilib-bootstrap-invalid.yaml");
        // 写明显非法的 YAML：未闭合的 flow sequence + 非法映射键
        try (java.io.PrintWriter pw = new java.io.PrintWriter(file, "UTF-8")) {
            pw.write("!!!invalid yaml::: [[[\n  key: : :\n  - 123: 456: 789\n??? incomplete flow");
        }

        // 调用前给静态字段设非默认值，确保"未被回灌覆盖"可观测
        FontConfig.lerpMode = 2;
        Config.useDebug = true;
        Config.netTransport = "preset";

        // 方法应内部 catch，不抛异常
        ModernConfigBootstrap.bootstrapAndApply(file);

        // 断言：bootstrap 失败 → return → 未走 Bridge → 静态字段保持调用前值
        assertEquals("非法 YAML 不应触发回灌，lerpMode 应保持 2", 2, FontConfig.lerpMode);
        assertTrue("非法 YAML 不应触发回灌，useDebug 应保持 true", Config.useDebug);
        assertEquals("非法 YAML 不应触发回灌，netTransport 应保持 preset",
                "preset", Config.netTransport);
    }

    /**
     * 用例 D：手改文件里的越界值不得把启动带走（#71 同族审计 A1）。
     *
     * <p>新栈 schema 声明了 range，但那份约束只被配置 UI 的提交路径消费；启动加载走
     * {@code DraftValidator.noop()}，于是越界值一路直写 FontConfig 静态字段，最后撞在
     * {@link FontRuntimeSettings} 的构造校验上——而它是在 {@code FontService} 饿汉单例的
     * 类初始化里执行的，后果是先 {@code ExceptionInInitializerError}、此后每次
     * {@code getInstance()} 都是 {@code NoClassDefFoundError}，客户端与专用服务端同时崩。</p>
     *
     * <p>场景只能用真实 YAML 文本复现：{@code draft.setDraft + save} 会经过提交路径的内置校验，
     * 根本产不出越界的 Authority。断言分两层——回灌本身不抛，以及真实漏斗
     * {@link FontRuntimeSettings#capture()} 接受修完的值。</p>
     */
    @Test
    public void bootstrapAndApplyRepairsUnrepresentableValues() throws Exception {
        File file = tempFolder.newFile("qzuilib-bootstrap-out-of-range.yaml");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(file, "UTF-8")) {
            pw.write("fontSystem:\n  lerpMode: 9\n");
            pw.write("fontSizeSetting:\n  charSize: 0\n  awtCharSize: -5\n");
        }

        ModernConfigBootstrap.bootstrapAndApply(file);

        assertEquals("lerpMode 越上界应钳到 schema 声明的上界 3", 3, FontConfig.lerpMode);
        assertEquals("charSize=0 无法表示，应钳到 schema 下界 1", 1.0D, FontConfig.charSize, 0.0D);
        assertEquals("awtCharSize 负值无法表示，应钳到 schema 下界 8", 8.0D, FontConfig.awtCharSize, 0.0D);
        FontRuntimeSettings settings = FontRuntimeSettings.capture();
        assertEquals("capture() 必须接受修完的值（这一句以前会抛 ExceptionInInitializerError）",
                3, settings.getLerpMode());
        assertEquals(1.0D, settings.getCharSize(), 0.0D);
        assertEquals(8.0D, settings.getAwtCharSize(), 0.0D);
    }

    /**
     * 用例 E：产品能表示的高值必须原样保留（A1 选定语义的钉子）。
     *
     * <p>修复只针对"产品无法表示"的值，而不是"超出 UI 滑条范围"的值。{@code charSize: 90}
     * 超出 schema 声明的 1..72，但字体运行时完全能表示它；若图省事按 schema 统一钳位，
     * 等于用一次崩溃修复悄悄没收用户特意手改的大字号。这条断言把取舍钉死：
     * 谁改成按 schema 范围钳，这里立刻红。</p>
     */
    @Test
    public void bootstrapAndApplyKeepsLegalValuesBeyondUiRanges() throws Exception {
        File file = tempFolder.newFile("qzuilib-bootstrap-legal-high.yaml");
        try (java.io.PrintWriter pw = new java.io.PrintWriter(file, "UTF-8")) {
            pw.write("fontSizeSetting:\n  charSize: 90\n  awtCharSize: 300\n");
        }

        ModernConfigBootstrap.bootstrapAndApply(file);

        assertEquals("charSize=90 超出 UI 上限但产品可表示，不得被钳成 72",
                90.0D, FontConfig.charSize, 0.0D);
        assertEquals("awtCharSize=300 同理，必须保持 300", 300.0D, FontConfig.awtCharSize, 0.0D);
        assertEquals("capture() 读到的就是用户配的值",
                90.0D, FontRuntimeSettings.capture().getCharSize(), 0.0D);
    }
}