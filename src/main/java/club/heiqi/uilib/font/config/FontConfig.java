package club.heiqi.uilib.font.config;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import club.heiqi.uilib.font.util.FontOrderSnapshot;
import net.minecraftforge.common.config.Configuration;

/**
 * 字体系统配置模型。
 */
public final class FontConfig {

    public static final String CATEGORY = "fontSystem";
    public static final String FONT_SIZE_CATEGORY = "fontSizeSetting";

    public static int lerpMode = 3;
    public static int aaMode = 2;
    /**
     * atlas 生成分辨率；与 charSize 的比值是字体层缩放因子。
     */
    public static double awtCharSize = 64.0D;
    /**
     * 默认显示字号；与 awtCharSize 的比值是显示侧缩放因子。
     */
    public static double charSize = 9.0D;
    public static double spaceWidth = 4.0D;
    public static double characterSpacing = 0.1D;
    public static double shadowOffsetX = 0.5D;
    public static double shadowOffsetY = 0.5D;
    public static double renderOffset = 0.0D;
    public static double brightnessGain = 2.0D;
    public static double drawStageUploadIntervalMs = 20.0D;
    public static int drawStageUploadLimitPerSecond = 20;
    public static int drawStageUploadBatchSize = 2;
    public static double smoothRangeMin = 0.0D;
    public static double smoothRangeMax = 0.9D;
    public static double aaStrength = 12.0D;
    public static boolean replaceOrigin = false;
    public static boolean customInvCountFont = false;
    public static String[] fontSort = new String[0];
    public static String[] missingFontSort = new String[0];
    public static String[] characterFontRules = new String[0];
    public static boolean fontSortConfigured;
    private static volatile FontCharacterRuleSet characterRuleSet = FontCharacterRuleSet.empty();

    private static int lastLerpMode = lerpMode;
    private static double lastAwtCharSize = awtCharSize;
    private static double lastCharSize = charSize;
    private static double lastSpaceWidth = spaceWidth;
    private static double lastCharacterSpacing = characterSpacing;
    private static boolean lastReplaceOrigin = replaceOrigin;
    private static boolean lastCustomInvCountFont = customInvCountFont;
    private static String[] lastFontSort = fontSort;
    private static String[] lastCharacterFontRules = characterFontRules;
    private static Configuration activeConfiguration;
    private static String activeFontCategory = CATEGORY;

    private FontConfig() {
    }

    /**
     * 从 Forge 配置中装载字体系统配置。
     *
     * @param configuration Forge 配置对象
     */
    public static void load(Configuration configuration) {
        activeConfiguration = configuration;
        String fontCategory = resolveFontCategory(configuration);
        activeFontCategory = fontCategory;
        lerpMode = configuration.get(fontCategory, "lerpMode", lerpMode, "插值模式", 0, 3).getInt();
        aaMode = configuration.get(fontCategory, "aaMode", aaMode, "AA 模式", 1, 2).getInt();
        brightnessGain = configuration.get(fontCategory, "brightnessGain", readLegacyBrightnessGain(configuration,
                        fontCategory), "HSV 亮度增强，仅增强亮度并保持原有颜色倾向", -Double.MAX_VALUE,
                Double.MAX_VALUE).getDouble();
        spaceWidth = configuration.get(fontCategory, "spaceWidth", spaceWidth, "空格宽度", -Double.MAX_VALUE,
                Double.MAX_VALUE).getDouble();
        characterSpacing = configuration.get(fontCategory, "characterSpacing", characterSpacing, "字间距",
                -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        shadowOffsetX = configuration.get(fontCategory, "shadowOffsetX", shadowOffsetX, "阴影 X 偏移",
                -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        shadowOffsetY = configuration.get(fontCategory, "shadowOffsetY", shadowOffsetY, "阴影 Y 偏移",
                -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        renderOffset = configuration.get(fontCategory, "renderOffset", renderOffset, "渲染 Z 偏移", -Double.MAX_VALUE,
                Double.MAX_VALUE).getDouble();
        smoothRangeMin = configuration.get(fontCategory, "smoothRangeMin", smoothRangeMin, "平滑下界", 0.0D,
                Double.MAX_VALUE).getDouble();
        smoothRangeMax = configuration.get(fontCategory, "smoothRangeMax", smoothRangeMax, "平滑上界", 0.0D,
                Double.MAX_VALUE).getDouble();
        drawStageUploadIntervalMs = configuration.get(fontCategory, "drawStageUploadIntervalMs",
                drawStageUploadIntervalMs, "drawString 阶段补充上传的最短间隔（毫秒）", 0.0D,
                Double.MAX_VALUE).getDouble();
        drawStageUploadLimitPerSecond = configuration.get(fontCategory, "drawStageUploadLimitPerSecond",
                drawStageUploadLimitPerSecond, "drawString 阶段每秒最多补充上传次数", 0, Integer.MAX_VALUE).getInt();
        drawStageUploadBatchSize = configuration.get(fontCategory, "drawStageUploadBatchSize",
                drawStageUploadBatchSize, "drawString 阶段每次最多补充上传字符数", 0, Integer.MAX_VALUE).getInt();
        aaStrength = configuration.get(fontCategory, "aaStrength", aaStrength, "AA 强度", 1.0D, Double.MAX_VALUE).getDouble();
        replaceOrigin = configuration.get(fontCategory, "replaceOrigin", replaceOrigin, "是否替换原版字体渲染").getBoolean();
        customInvCountFont = configuration.get(fontCategory, "customInvCountFont", customInvCountFont,
                "是否接管物品数量字体").getBoolean();
        fontSortConfigured = configuration.hasKey(fontCategory, "fontSort");
        fontSort = configuration.get(fontCategory, "fontSort", fontSort, "字体排序").getStringList();
        if (fontSort == null) {
            fontSort = new String[0];
        }
        characterFontRules = configuration.get(fontCategory, "characterFontRules", characterFontRules,
                "字符字体覆盖规则，格式为 字符或范围=字体名；禁用规则使用 disabled: 前缀").getStringList();
        if (characterFontRules == null) {
            characterFontRules = new String[0];
        }
        characterRuleSet = FontCharacterRuleSet.parse(characterFontRules);

        awtCharSize = configuration.get(FONT_SIZE_CATEGORY, "awtCharSize", awtCharSize, "字符生成分辨率", 8.0D,
                Double.MAX_VALUE).getDouble();
        charSize = configuration.get(FONT_SIZE_CATEGORY, "charSize", charSize, "默认显示字号", 1.0D,
                Double.MAX_VALUE).getDouble();
    }

    /**
     * 判断本次配置变更是否影响字体运行时。
     *
     * @return 是否需要触发字体系统重载
     */
    public static boolean affectsFontRuntime() {
        return lastLerpMode != lerpMode
                || Double.compare(lastAwtCharSize, awtCharSize) != 0
                || Double.compare(lastCharSize, charSize) != 0
                || Double.compare(lastSpaceWidth, spaceWidth) != 0
                || Double.compare(lastCharacterSpacing, characterSpacing) != 0
                || lastReplaceOrigin != replaceOrigin
                || lastCustomInvCountFont != customInvCountFont
                || !Arrays.equals(lastFontSort, fontSort)
                || !Arrays.equals(lastCharacterFontRules, characterFontRules);
    }

    /**
     * 刷新 characterRuleSet 派生态。
     *
     * <p>值回灌抽象（{@code ConfigValueBridge}）喂完 {@code characterFontRules} 后调用，
     * 保证 {@code characterRuleSet} 与 {@code characterFontRules} 一致
     * （守宪章信条六/七，派生态不陈旧）。</p>
     *
     * <p>派生逻辑（parse）归属 FontConfig 所有者，回灌抽象只喂原始值后调本方法。
     * 与 {@link #load(Configuration)} 中 {@code characterRuleSet = FontCharacterRuleSet.parse(characterFontRules)}
     * 语义一致。</p>
     */
    public static void refreshDerivedRuleSet() {
        characterRuleSet = FontCharacterRuleSet.parse(characterFontRules);
    }

    /**
     * 解除对旧栈 Forge {@link Configuration} 的反向引用，关闭"applyFontOrderSnapshot →
     * persistFontSortToConfiguration 反向持久化字体排序到 .cfg"的旧链路
     * （新栈数据源切换后的反向改向子任务，A2 实现）。
     *
     * <p><b>动机</b>：新架构配置（{@code club.heiqi.config.runtime.ConfigManager} + YAML）
     * 由新栈保存链路（{@code ConfigSaveListener} → {@code ConfigValueBridge}）写盘，
     * 保留旧 Forge Configuration 反向数据库会导致：新栈保存触发 FontService.reload
     * → FontRegistry.reload → applyFontOrderSnapshot → persistFontSortToConfiguration
     * 把 FontOrderPlanner 输出的"resolved 顺序"（含运行时补齐的 fallback 字体名）反向写回
     * .cfg 老用户配置被永久污染；且与新栈 YAML 形成双写不一致。</p>
     *
     * <p><b>实现</b>：置 {@code activeConfiguration = null}，使
     * {@link #persistFontSortToConfiguration} 的 null 守卫自动 no-op。
     * 方法保留（不删），整支删除留待阶段 D/E 统一收敛旧栈 24 文件时清理。</p>
     *
     * <h3>调用方</h3>
     * <ul>
     *   <li>{@link club.heiqi.uilib.config.modern.ConfigValueBridge#applyFromAuthority}：
     *       新栈值回灌完成后调用，确保后续 FontService.reload 触发的 applyFontOrderSnapshot
     *       不触发 .cfg 反向写</li>
     * </ul>
     *
     * <h3>守硬约束</h3>
     * <ul>
     *   <li><b>I1</b>：不触碰 reload / SceneNode 属性槽，仅清字段引用</li>
     *   <li><b>I3</b>：不涉及 Computed</li>
     *   <li><b>I7</b>：不引入新重算（保留 FontConfig.fontSort = snapshot.getResolvedFontNames()
     *       的派生态写入 applyFontOrderSnapshot:214 必要行为；本方法只关掉反向 .cfg 写）</li>
     *   <li><b>不 publish BATCH_SAVE</b>：守回环（applyFontOrderSnapshot resolved 不会回灌
     *       覆盖用户原值；handoff 反向改向约束已满足）</li>
     * </ul>
     */
    public static void detachLegacyConfiguration() {
        activeConfiguration = null;
    }

    /**
     * 在配置同步后刷新缓存快照。
     */
    public static void onConfigReload() {
        lastLerpMode = lerpMode;
        lastAwtCharSize = awtCharSize;
        lastCharSize = charSize;
        lastSpaceWidth = spaceWidth;
        lastCharacterSpacing = characterSpacing;
        lastReplaceOrigin = replaceOrigin;
        lastCustomInvCountFont = customInvCountFont;
        lastFontSort = fontSort == null ? new String[0] : Arrays.copyOf(fontSort, fontSort.length);
        lastCharacterFontRules = characterFontRules == null ? new String[0]
                : Arrays.copyOf(characterFontRules, characterFontRules.length);
    }

    /**
     * 应用字体排序规划结果。
     *
     * @param snapshot 字体排序快照
     */
    public static void applyFontOrderSnapshot(FontOrderSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        fontSort = snapshot.getResolvedFontNames();
        missingFontSort = snapshot.getMissingConfiguredFontNames();
        persistFontSortToConfiguration();
    }

    /**
     * 判断当前字体名是否已经存在于有效顺序中。
     *
     * @param fontName 字体名
     * @return 是否存在
     */
    public static boolean isFontPresent(String fontName) {
        return containsIgnoreCase(fontSort, fontName);
    }

    /**
     * 判断当前字体名是否处于缺失状态。
     *
     * @param fontName 字体名
     * @return 是否缺失
     */
    public static boolean isFontMissing(String fontName) {
        return containsIgnoreCase(missingFontSort, fontName);
    }

    /**
     * 获取当前有效字体顺序快照。
     *
     * @return 字体顺序快照
     */
    public static String[] getFontSortSnapshot() {
        return fontSort == null ? new String[0] : Arrays.copyOf(fontSort, fontSort.length);
    }

    /**
     * 获取当前字符字体覆盖规则快照。
     *
     * @return 字符字体覆盖规则快照
     */
    public static String[] getCharacterFontRuleSnapshot() {
        return characterFontRules == null ? new String[0] : Arrays.copyOf(characterFontRules,
                characterFontRules.length);
    }

    /**
     * 获取当前字符字体覆盖规则集合。
     *
     * @return 字符字体覆盖规则集合
     */
    public static FontCharacterRuleSet getCharacterRuleSet() {
        return characterRuleSet;
    }

    /**
     * 获取当前缺失字体名称快照。
     *
     * @return 缺失字体名称快照
     */
    public static String[] getMissingFontSnapshot() {
        return missingFontSort == null ? new String[0] : Arrays.copyOf(missingFontSort, missingFontSort.length);
    }

    /**
     * 生成用于日志输出的简短摘要。
     *
     * @return 摘要文本
     */
    public static String buildSummary() {
        return "charSize=" + charSize
                + ", awtCharSize=" + awtCharSize
                + ", replaceOrigin=" + replaceOrigin
                + ", customInvCountFont=" + customInvCountFont
                + ", fontSort=" + Arrays.toString(fontSort)
                + ", missingFontSort=" + Arrays.toString(missingFontSort)
                + ", characterFontRules=" + Arrays.toString(characterFontRules);
    }

    private static void persistFontSortToConfiguration() {
        if (activeConfiguration == null) {
            return;
        }
        String fontCategory = activeConfiguration == null ? activeFontCategory : resolveFontCategory(activeConfiguration);
        activeFontCategory = fontCategory;
        activeConfiguration.get(fontCategory, "fontSort", fontSort, "字体排序").set(fontSort);
        File configFile = activeConfiguration.getConfigFile();
        if (activeConfiguration.hasChanged() && configFile != null) {
            activeConfiguration.save();
        }
    }

    private static String resolveFontCategory(Configuration configuration) {
        if (configuration == null) {
            return CATEGORY;
        }
        String lowerCaseCategory = CATEGORY.toLowerCase(Locale.ENGLISH);
        if (configuration.hasKey(CATEGORY, "fontSort")) {
            return CATEGORY;
        }
        if (configuration.hasKey(lowerCaseCategory, "fontSort")) {
            return lowerCaseCategory;
        }
        for (String categoryName : configuration.getCategoryNames()) {
            if (CATEGORY.equalsIgnoreCase(categoryName) && configuration.hasKey(categoryName, "fontSort")) {
                return categoryName;
            }
        }
        if (configuration.hasCategory(CATEGORY)) {
            return CATEGORY;
        }
        if (configuration.hasCategory(lowerCaseCategory)) {
            return lowerCaseCategory;
        }
        for (String categoryName : configuration.getCategoryNames()) {
            if (CATEGORY.equalsIgnoreCase(categoryName)) {
                return categoryName;
            }
        }
        return CATEGORY;
    }

    private static boolean containsIgnoreCase(String[] values, String target) {
        if (values == null || target == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target.trim())) {
                return true;
            }
        }
        return false;
    }

    private static double readLegacyBrightnessGain(Configuration configuration, String fontCategory) {
        if (configuration.hasKey(fontCategory, "brightnessGain")) {
            return brightnessGain;
        }
        if (configuration.hasKey(fontCategory, "colorGain")) {
            return configuration.get(fontCategory, "colorGain", brightnessGain).getDouble();
        }
        String legacyCategory = fontCategory == null || CATEGORY.equals(fontCategory) ? null : CATEGORY;
        if (legacyCategory != null && configuration.hasKey(legacyCategory, "colorGain")) {
            return configuration.get(legacyCategory, "colorGain", brightnessGain).getDouble();
        }
        return brightnessGain;
    }
}
