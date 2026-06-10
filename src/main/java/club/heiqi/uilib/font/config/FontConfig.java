package club.heiqi.uilib.font.config;

import java.io.File;
import java.util.ArrayList;
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
    public static double awtCharSize = 64.0D;
    public static double charSize = 9.0D;
    public static double fontScale = 0.9D;
    public static double spaceWidth = 4.0D;
    public static double characterSpacing = 0.1D;
    public static double shadowOffsetX = 0.5D;
    public static double shadowOffsetY = 0.5D;
    public static double lineSpacing = 0.1D;
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
    private static double lastFontScale = fontScale;
    private static double lastSpaceWidth = spaceWidth;
    private static double lastCharacterSpacing = characterSpacing;
    private static double lastLineSpacing = lineSpacing;
    private static boolean lastReplaceOrigin = replaceOrigin;
    private static boolean lastCustomInvCountFont = customInvCountFont;
    private static String[] lastFontSort = fontSort;
    private static String[] lastCharacterFontRules = characterFontRules;
    private static Configuration activeConfiguration;
    private static String activeFontCategory = CATEGORY;

    private FontConfig() {}

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
        lineSpacing = configuration.get(fontCategory, "lineSpacing", lineSpacing, "行间距", -Double.MAX_VALUE,
                Double.MAX_VALUE).getDouble();
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
        fontScale = configuration.get(FONT_SIZE_CATEGORY, "fontScale", fontScale, "字体缩放系数", 0.0D, 1.0D).getDouble();
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
                || Double.compare(lastFontScale, fontScale) != 0
                || Double.compare(lastSpaceWidth, spaceWidth) != 0
                || Double.compare(lastCharacterSpacing, characterSpacing) != 0
                || Double.compare(lastLineSpacing, lineSpacing) != 0
                || lastReplaceOrigin != replaceOrigin
                || lastCustomInvCountFont != customInvCountFont
                || !Arrays.equals(lastFontSort, fontSort)
                || !Arrays.equals(lastCharacterFontRules, characterFontRules);
    }

    /**
     * 在配置同步后刷新缓存快照。
     */
    public static void onConfigReload() {
        lastLerpMode = lerpMode;
        lastAwtCharSize = awtCharSize;
        lastCharSize = charSize;
        lastFontScale = fontScale;
        lastSpaceWidth = spaceWidth;
        lastCharacterSpacing = characterSpacing;
        lastLineSpacing = lineSpacing;
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
