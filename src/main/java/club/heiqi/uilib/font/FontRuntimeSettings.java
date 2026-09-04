package club.heiqi.uilib.font;

import java.util.Arrays;
import java.util.List;

import club.heiqi.uilib.font.config.FontCharacterRule;
import club.heiqi.uilib.font.config.FontCharacterRuleSet;
import club.heiqi.uilib.font.config.FontConfig;

/**
 * 单个字体 generation 使用的不可变配置快照。
 *
 * <p>本类是"字体运行时能表示什么配置"的<b>唯一权威</b>：构造校验与对外判据
 * {@link #isRepresentable(String, double)} 共用同一张规则表。配置回灌侧
 * （{@code ConfigValueBridge}）只准问判据，不准复制规则——新栈配置的 schema 声明了
 * range 约束，但那份约束只在配置 UI 提交路径上生效，启动加载完全不读它（#71 同族审计 A1）。</p>
 */
public final class FontRuntimeSettings {

    /** atlas 采样模式字段名。 */
    public static final String FIELD_LERP_MODE = "lerpMode";
    /** AWT 字形生成分辨率字段名。 */
    public static final String FIELD_AWT_CHAR_SIZE = "awtCharSize";
    /** 默认显示字号字段名。 */
    public static final String FIELD_CHAR_SIZE = "charSize";
    /** 空格宽度字段名。 */
    public static final String FIELD_SPACE_WIDTH = "spaceWidth";
    /** 字间距字段名。 */
    public static final String FIELD_CHARACTER_SPACING = "characterSpacing";
    /** atlas 纹理边长倍数字段名。 */
    public static final String FIELD_ATLAS_TEXTURE_SCALE = "atlasTextureScale";

    /** lerpMode 合法下界（含）。 */
    public static final int LERP_MODE_MIN = 0;
    /** lerpMode 合法上界（含）。 */
    public static final int LERP_MODE_MAX = 3;

    /** 必须有限且大于 0 的字段。 */
    private static final String[] FIELDS_REQUIRING_POSITIVE = {
            FIELD_AWT_CHAR_SIZE, FIELD_CHAR_SIZE, FIELD_ATLAS_TEXTURE_SCALE,
    };
    /** 只要求有限的字段（0 与负值有明确语义，不得被改动）。 */
    private static final String[] FIELDS_REQUIRING_FINITE = {
            FIELD_SPACE_WIDTH, FIELD_CHARACTER_SPACING,
    };

    private final int lerpMode;
    private final double awtCharSize;
    private final double charSize;
    private final double spaceWidth;
    private final double characterSpacing;
    private final boolean fontSortConfigured;
    private final String[] fontSort;
    private final String[] characterFontRules;
    private final FontCharacterRuleSet characterRuleSet;
    private final double atlasTextureScale;

    /**
     * 创建不可变字体运行时设置。
     *
     * @param lerpMode atlas 采样模式
     * @param awtCharSize AWT 字形生成分辨率
     * @param charSize 默认显示字号
     * @param spaceWidth 空格宽度
     * @param characterSpacing 字间距
     * @param fontSortConfigured 是否显式配置字体顺序
     * @param fontSort 字体顺序提示
     * @param characterRuleSet 字符字体规则
     */
    public FontRuntimeSettings(int lerpMode, double awtCharSize, double charSize, double spaceWidth,
            double characterSpacing, boolean fontSortConfigured, String[] fontSort,
            FontCharacterRuleSet characterRuleSet) {
        this(lerpMode, awtCharSize, charSize, spaceWidth, characterSpacing, fontSortConfigured, fontSort,
                new String[0], characterRuleSet, 64.0D);
    }

    private FontRuntimeSettings(int lerpMode, double awtCharSize, double charSize, double spaceWidth,
            double characterSpacing, boolean fontSortConfigured, String[] fontSort, String[] characterFontRules,
            FontCharacterRuleSet characterRuleSet, double atlasTextureScale) {
        requireRepresentable(FIELD_AWT_CHAR_SIZE, awtCharSize);
        requireRepresentable(FIELD_CHAR_SIZE, charSize);
        requireRepresentable(FIELD_SPACE_WIDTH, spaceWidth);
        requireRepresentable(FIELD_CHARACTER_SPACING, characterSpacing);
        requireRepresentable(FIELD_ATLAS_TEXTURE_SCALE, atlasTextureScale);
        requireRepresentable(FIELD_LERP_MODE, lerpMode);
        this.lerpMode = lerpMode;
        this.awtCharSize = awtCharSize;
        this.charSize = charSize;
        this.spaceWidth = spaceWidth;
        this.characterSpacing = characterSpacing;
        this.fontSortConfigured = fontSortConfigured;
        this.fontSort = fontSort == null ? new String[0] : Arrays.copyOf(fontSort, fontSort.length);
        this.characterFontRules = characterFontRules == null ? new String[0]
                : Arrays.copyOf(characterFontRules, characterFontRules.length);
        this.characterRuleSet = characterRuleSet == null ? FontCharacterRuleSet.empty() : characterRuleSet;
        this.atlasTextureScale = atlasTextureScale;
    }

    /**
     * 一次性捕获当前 desired 配置。
     *
     * @return 不可变设置快照
     */
    public static FontRuntimeSettings capture() {
        return new FontRuntimeSettings(FontConfig.lerpMode, FontConfig.awtCharSize, FontConfig.charSize,
                FontConfig.spaceWidth, FontConfig.characterSpacing, FontConfig.fontSortConfigured,
                FontConfig.getFontSortSnapshot(), FontConfig.getCharacterFontRuleSnapshot(),
                FontConfig.getCharacterRuleSet(), FontConfig.atlasTextureScale);
    }

    public int getLerpMode() {
        return lerpMode;
    }

    public double getAwtCharSize() {
        return awtCharSize;
    }

    public double getCharSize() {
        return charSize;
    }

    public double getSpaceWidth() {
        return spaceWidth;
    }

    public double getCharacterSpacing() {
        return characterSpacing;
    }

    public boolean isFontSortConfigured() {
        return fontSortConfigured;
    }

    public String[] getFontSort() {
        return Arrays.copyOf(fontSort, fontSort.length);
    }

    public FontCharacterRuleSet getCharacterRuleSet() {
        return characterRuleSet;
    }

    public double getAtlasTextureScale() {
        return atlasTextureScale;
    }

    /**
     * 判断两个快照是否表达同一套 generation-sensitive desired state。
     *
     * @param other 另一份设置
     * @return 是否无需重新构建 generation
     */
    public boolean hasSameRuntimeSemantics(FontRuntimeSettings other) {
        return hasSameRuntimeSemantics(other, null);
    }

    boolean hasSameRuntimeSemantics(FontRuntimeSettings other, String[] publishedFontOrder) {
        if (other == null) {
            return false;
        }
        return lerpMode == other.lerpMode
                && Double.compare(awtCharSize, other.awtCharSize) == 0
                && Double.compare(charSize, other.charSize) == 0
                && Double.compare(spaceWidth, other.spaceWidth) == 0
                && Double.compare(characterSpacing, other.characterSpacing) == 0
                && Double.compare(atlasTextureScale, other.atlasTextureScale) == 0
                && fontSortConfigured == other.fontSortConfigured
                && (!fontSortConfigured || Arrays.equals(fontSort, other.fontSort)
                        || publishedFontOrder != null && Arrays.equals(publishedFontOrder, other.fontSort))
                && hasSameCharacterRuleSemantics(characterRuleSet, other.characterRuleSet);
    }

    /**
     * 匹配、测量和 worker 使用的字形格大小。
     *
     * @return 向上取整后的字形格大小
     */
    public int getGlyphSize() {
        return Math.max(8, (int) Math.ceil(awtCharSize));
    }

    /**
     * 保留现有 atlas page 几何的截断语义。
     *
     * @return page 字形格大小
     */
    public int getPageGlyphSize() {
        return Math.max(8, (int) awtCharSize);
    }

    /**
     * 计算 atlas texture 边长。
     *
     * @return texture 边长
     */
    public int getTextureSize() {
        return Math.max(64, (int) (awtCharSize * atlasTextureScale));
    }

    private static boolean hasSameCharacterRuleSemantics(FontCharacterRuleSet left, FontCharacterRuleSet right) {
        List<FontCharacterRule> leftRules = left.getRules();
        List<FontCharacterRule> rightRules = right.getRules();
        int leftIndex = 0;
        int rightIndex = 0;
        while (true) {
            leftIndex = nextActiveRuleIndex(leftRules, leftIndex);
            rightIndex = nextActiveRuleIndex(rightRules, rightIndex);
            if (leftIndex >= leftRules.size() || rightIndex >= rightRules.size()) {
                return leftIndex >= leftRules.size() && rightIndex >= rightRules.size();
            }
            FontCharacterRule leftRule = leftRules.get(leftIndex++);
            FontCharacterRule rightRule = rightRules.get(rightIndex++);
            if (leftRule.getStartCodepoint() != rightRule.getStartCodepoint()
                    || leftRule.getEndCodepoint() != rightRule.getEndCodepoint()
                    || !leftRule.getFontName().equals(rightRule.getFontName())) {
                return false;
            }
        }
    }

    private static int nextActiveRuleIndex(List<FontCharacterRule> rules, int startIndex) {
        int index = startIndex;
        while (index < rules.size() && !rules.get(index).isActive()) {
            index++;
        }
        return index;
    }

    /**
     * 判断某个配置值能否被字体运行时表示——与构造校验同源的<b>唯一判据</b>。
     *
     * <p>配置回灌侧据此决定要不要修值，因此"什么算坏值"只有一份定义。未列入规则表的
     * 字段返回 true，含义是本类不约束它，不是漏判——所以调用方只准传本类的 {@code FIELD_*}
     * 常量，不要自造字符串。</p>
     *
     * @param field 字段名，取本类 FIELD_* 常量
     * @param value 待判值
     * @return 无需修复即可写入时返回 true
     */
    public static boolean isRepresentable(String field, double value) {
        if (FIELD_LERP_MODE.equals(field)) {
            return isFiniteValue(value) && value >= LERP_MODE_MIN && value <= LERP_MODE_MAX;
        }
        if (requiresPositive(field)) {
            return isFiniteValue(value) && value > 0.0D;
        }
        if (requiresFinite(field)) {
            return isFiniteValue(value);
        }
        return true;
    }

    private static void requireRepresentable(String field, double value) {
        if (!isRepresentable(field, value)) {
            throw new IllegalArgumentException(rejectionMessage(field, value));
        }
    }

    /**
     * 拒绝文案：非有限一律"必须是有限数值"（与旧 validateFinite 优先报同一条保持一致），
     * 其余按字段要求给方向。
     */
    private static String rejectionMessage(String field, double value) {
        if (!isFiniteValue(value)) {
            return field + " 必须是有限数值";
        }
        if (requiresPositive(field)) {
            return field + " 必须大于 0";
        }
        return field + " 必须位于 " + LERP_MODE_MIN + ".." + LERP_MODE_MAX;
    }

    private static boolean requiresPositive(String field) {
        return contains(FIELDS_REQUIRING_POSITIVE, field);
    }

    private static boolean requiresFinite(String field) {
        return contains(FIELDS_REQUIRING_FINITE, field);
    }

    private static boolean contains(String[] fields, String field) {
        for (String candidate : fields) {
            if (candidate.equals(field)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFiniteValue(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
