package club.heiqi.uilib.font;

import java.util.Arrays;
import java.util.List;

import club.heiqi.uilib.font.config.FontCharacterRule;
import club.heiqi.uilib.font.config.FontCharacterRuleSet;
import club.heiqi.uilib.font.config.FontConfig;

/**
 * 单个字体 generation 使用的不可变配置快照。
 */
public final class FontRuntimeSettings {

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
        validateFinitePositive("awtCharSize", awtCharSize);
        validateFinitePositive("charSize", charSize);
        validateFinite("spaceWidth", spaceWidth);
        validateFinite("characterSpacing", characterSpacing);
        validateFinitePositive("atlasTextureScale", atlasTextureScale);
        if (lerpMode < 0 || lerpMode > 3) {
            throw new IllegalArgumentException("lerpMode 必须位于 0..3");
        }
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

    private static void validateFinitePositive(String name, double value) {
        validateFinite(name, value);
        if (value <= 0.0D) {
            throw new IllegalArgumentException(name + " 必须大于 0");
        }
    }

    private static void validateFinite(String name, double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " 必须是有限数值");
        }
    }
}
