package club.heiqi.uilib.font.config;

import java.util.Arrays;

import net.minecraftforge.common.config.Configuration;

/**
 * 字体系统配置模型。
 */
public final class FontConfig {

    public static final String CATEGORY = "fontSystem";
    public static final String FONT_SIZE_CATEGORY = "fontSizeSetting";

    public static int lerpMode = 0;
    public static int aaMode = 2;
    public static double awtCharSize = 64.0D;
    public static double charSize = 9.0D;
    public static double fontScale = 1.0D;
    public static double spaceWidth = 4.0D;
    public static double characterSpacing = 0.1D;
    public static double shadowOffsetX = 0.5D;
    public static double shadowOffsetY = 0.5D;
    public static double lineSpacing = 0.1D;
    public static double renderOffset = 0.0D;
    public static double brightnessGain = 10.0D;
    public static double drawStageUploadIntervalMs = 20.0D;
    public static int drawStageUploadLimitPerSecond = 20;
    public static int drawStageUploadBatchSize = 2;
    public static double smoothRangeMin = 0.0D;
    public static double smoothRangeMax = 0.9D;
    public static double aaStrength = 12.0D;
    public static boolean replaceOrigin = false;
    public static boolean customInvCountFont = false;
    public static String[] fontSort = new String[0];

    private static double lastAwtCharSize = awtCharSize;
    private static double lastCharSize = charSize;
    private static double lastFontScale = fontScale;
    private static boolean lastReplaceOrigin = replaceOrigin;
    private static boolean lastCustomInvCountFont = customInvCountFont;
    private static String[] lastFontSort = fontSort;

    private FontConfig() {}

    /**
     * 从 Forge 配置中装载字体系统配置。
     *
     * @param configuration Forge 配置对象
     */
    public static void load(Configuration configuration) {
        lerpMode = configuration.get(CATEGORY, "lerpMode", lerpMode, "插值模式", 0, 3).getInt();
        aaMode = configuration.get(CATEGORY, "aaMode", aaMode, "AA 模式", 1, 2).getInt();
        brightnessGain = configuration.get(CATEGORY, "brightnessGain", readLegacyBrightnessGain(configuration), "HSV 亮度增强，仅增强亮度并保持原有颜色倾向", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        spaceWidth = configuration.get(CATEGORY, "spaceWidth", spaceWidth, "空格宽度", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        characterSpacing = configuration.get(CATEGORY, "characterSpacing", characterSpacing, "字间距", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        shadowOffsetX = configuration.get(CATEGORY, "shadowOffsetX", shadowOffsetX, "阴影 X 偏移", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        shadowOffsetY = configuration.get(CATEGORY, "shadowOffsetY", shadowOffsetY, "阴影 Y 偏移", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        lineSpacing = configuration.get(CATEGORY, "lineSpacing", lineSpacing, "行间距", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        renderOffset = configuration.get(CATEGORY, "renderOffset", renderOffset, "渲染 Z 偏移", -Double.MAX_VALUE, Double.MAX_VALUE).getDouble();
        smoothRangeMin = configuration.get(CATEGORY, "smoothRangeMin", smoothRangeMin, "平滑下界", 0.0D, Double.MAX_VALUE).getDouble();
        smoothRangeMax = configuration.get(CATEGORY, "smoothRangeMax", smoothRangeMax, "平滑上界", 0.0D, Double.MAX_VALUE).getDouble();
        drawStageUploadIntervalMs = configuration.get(CATEGORY, "drawStageUploadIntervalMs", drawStageUploadIntervalMs, "drawString 阶段补充上传的最短间隔（毫秒）", 0.0D, Double.MAX_VALUE).getDouble();
        drawStageUploadLimitPerSecond = configuration.get(CATEGORY, "drawStageUploadLimitPerSecond", drawStageUploadLimitPerSecond, "drawString 阶段每秒最多补充上传次数", 0, Integer.MAX_VALUE).getInt();
        drawStageUploadBatchSize = configuration.get(CATEGORY, "drawStageUploadBatchSize", drawStageUploadBatchSize, "drawString 阶段每次最多补充上传字符数", 0, Integer.MAX_VALUE).getInt();
        aaStrength = configuration.get(CATEGORY, "aaStrength", aaStrength, "AA 强度", 1.0D, Double.MAX_VALUE).getDouble();
        replaceOrigin = configuration.get(CATEGORY, "replaceOrigin", replaceOrigin, "是否替换原版字体渲染").getBoolean();
        customInvCountFont = configuration.get(CATEGORY, "customInvCountFont", customInvCountFont, "是否接管物品数量字体").getBoolean();
        fontSort = configuration.get(CATEGORY, "fontSort", fontSort, "字体排序").getStringList();

        awtCharSize = configuration.get(FONT_SIZE_CATEGORY, "awtCharSize", awtCharSize, "字符生成分辨率", 8.0D, Double.MAX_VALUE).getDouble();
        charSize = configuration.get(FONT_SIZE_CATEGORY, "charSize", charSize, "默认显示字号", 1.0D, Double.MAX_VALUE).getDouble();
        fontScale = configuration.get(FONT_SIZE_CATEGORY, "fontScale", fontScale, "字体缩放系数", 0.0D, 1.0D).getDouble();
    }

    /**
     * 判断本次配置变更是否影响字体运行时。
     *
     * @return 是否需要触发字体系统重载
     */
    public static boolean affectsFontRuntime() {
        return Double.compare(lastAwtCharSize, awtCharSize) != 0
                || Double.compare(lastCharSize, charSize) != 0
                || Double.compare(lastFontScale, fontScale) != 0
                || lastReplaceOrigin != replaceOrigin
                || lastCustomInvCountFont != customInvCountFont
                || !Arrays.equals(lastFontSort, fontSort);
    }

    /**
     * 在配置同步后刷新缓存快照。
     */
    public static void onConfigReload() {
        lastAwtCharSize = awtCharSize;
        lastCharSize = charSize;
        lastFontScale = fontScale;
        lastReplaceOrigin = replaceOrigin;
        lastCustomInvCountFont = customInvCountFont;
        lastFontSort = fontSort == null ? new String[0] : Arrays.copyOf(fontSort, fontSort.length);
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
                + ", fontSort=" + Arrays.toString(fontSort);
    }

    private static double readLegacyBrightnessGain(Configuration configuration) {
        if (configuration.hasKey(CATEGORY, "brightnessGain")) {
            return brightnessGain;
        }
        if (configuration.hasKey(CATEGORY, "colorGain")) {
            return configuration.get(CATEGORY, "colorGain", brightnessGain).getDouble();
        }
        return brightnessGain;
    }
}
