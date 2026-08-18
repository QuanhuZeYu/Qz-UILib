package club.heiqi.uilib.font.config;

import java.util.Arrays;

import club.heiqi.uilib.font.util.FontOrderSnapshot;

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
    /**
     * 遗留配置：drawString 阶段补充上传的批大小。
     *
     * <p>上传已迁移到 RenderTick START 稳定阶段批处理（{@code FontRenderTickListener}），
     * 默认渲染路径不再读取本项；字段与配置 schema 保留以兼容既有配置。</p>
     */
    public static int drawStageUploadBatchSize = 2;
    /**
     * 渲染线程每 16ms 窗口内允许的字符宽度测量 miss 次数。
     *
     * <p>冷启动时未命中宽度缓存的字符会在渲染线程做 AWT 匹配与测量；超出预算的 miss
     * 本窗口按空格宽度近似渲染并顺延到下一窗口测量，避免首屏帧尖峰。&lt;=0 关闭预算
     * （回退到无限制测量，可随时回滚该优化）。</p>
     */
    public static int widthCacheMissBudgetPerWindow = 64;
    /**
     * 字符 slot 四周 ink 留白像素数。
     *
     * <p>留白同时承担 mipmap 降采样时的相邻 slot 渗色隔离：默认 8 可覆盖 mip 3 级；
     * 下调可进一步压缩页面积，但需真机验证缩放/阴影下无边缘渗色。0 表示无留白（仅建议调试）。</p>
     */
    public static int glyphInkPadding = 8;
    /**
     * atlas 页边长系数：页边长 = awtCharSize × 该系数（默认 64 → 4096×4096）。
     *
     * <p>紧密排列后可按需下调以压缩单页显存峰值；变化会触发字体运行时重载。</p>
     */
    public static double atlasTextureScale = 64.0D;
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

    private static int lastGlyphInkPadding = glyphInkPadding;
    private static double lastAtlasTextureScale = atlasTextureScale;
    private static int lastLerpMode = lerpMode;
    private static double lastAwtCharSize = awtCharSize;
    private static double lastCharSize = charSize;
    private static double lastSpaceWidth = spaceWidth;
    private static double lastCharacterSpacing = characterSpacing;
    private static boolean lastReplaceOrigin = replaceOrigin;
    private static boolean lastCustomInvCountFont = customInvCountFont;
    private static String[] lastFontSort = fontSort;
    private static String[] lastCharacterFontRules = characterFontRules;

    private FontConfig() {
    }

    /**
     * 判断本次配置变更是否影响字体运行时。
     *
     * @return 是否需要触发字体系统重载
     */
    public static boolean affectsFontRuntime() {
        return lastLerpMode != lerpMode
                || lastGlyphInkPadding != glyphInkPadding
                || Double.compare(lastAtlasTextureScale, atlasTextureScale) != 0
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
     * <p>派生逻辑（parse）归属 FontConfig 所有者，回灌抽象只喂原始值后调本方法。</p>
     */
    public static void refreshDerivedRuleSet() {
        characterRuleSet = FontCharacterRuleSet.parse(characterFontRules);
    }

    /**
     * 在配置同步后刷新缓存快照。
     */
    public static void onConfigReload() {
        lastGlyphInkPadding = glyphInkPadding;
        lastAtlasTextureScale = atlasTextureScale;
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
}
