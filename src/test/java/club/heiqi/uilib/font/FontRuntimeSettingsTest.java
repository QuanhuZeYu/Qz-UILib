package club.heiqi.uilib.font;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontCharacterRuleSet;
import club.heiqi.uilib.font.config.FontConfig;

/** {@link FontRuntimeSettings} 的不可变捕获合同。 */
public class FontRuntimeSettingsTest {

    private int oldLerpMode;
    private double oldAtlasTextureScale;
    private double oldAwtCharSize;
    private double oldCharSize;
    private double oldSpaceWidth;
    private double oldCharacterSpacing;
    private boolean oldFontSortConfigured;
    private String[] oldFontSort;
    private String[] oldCharacterRules;

    @Before
    public void saveConfig() {
        oldLerpMode = FontConfig.lerpMode;
        oldAtlasTextureScale = FontConfig.atlasTextureScale;
        oldAwtCharSize = FontConfig.awtCharSize;
        oldCharSize = FontConfig.charSize;
        oldSpaceWidth = FontConfig.spaceWidth;
        oldCharacterSpacing = FontConfig.characterSpacing;
        oldFontSortConfigured = FontConfig.fontSortConfigured;
        oldFontSort = FontConfig.getFontSortSnapshot();
        oldCharacterRules = FontConfig.getCharacterFontRuleSnapshot();
    }

    @After
    public void restoreConfig() {
        FontConfig.lerpMode = oldLerpMode;
        FontConfig.atlasTextureScale = oldAtlasTextureScale;
        FontConfig.awtCharSize = oldAwtCharSize;
        FontConfig.charSize = oldCharSize;
        FontConfig.spaceWidth = oldSpaceWidth;
        FontConfig.characterSpacing = oldCharacterSpacing;
        FontConfig.fontSortConfigured = oldFontSortConfigured;
        FontConfig.fontSort = oldFontSort;
        FontConfig.characterFontRules = oldCharacterRules;
        FontConfig.refreshDerivedRuleSet();
    }

    @Test
    public void capturedSettingsSurviveLiveConfigMutation() {
        String[] fontSort = new String[]{"First", "Second"};
        FontConfig.lerpMode = 2;
        FontConfig.awtCharSize = 63.5D;
        FontConfig.charSize = 10.0D;
        FontConfig.spaceWidth = 5.0D;
        FontConfig.characterSpacing = 0.25D;
        FontConfig.fontSortConfigured = true;
        FontConfig.fontSort = fontSort;
        FontConfig.characterFontRules = new String[]{"A=First"};
        FontConfig.refreshDerivedRuleSet();

        FontRuntimeSettings settings = FontRuntimeSettings.capture();
        fontSort[0] = "Mutated";
        FontConfig.awtCharSize = 128.0D;
        FontConfig.charSize = 20.0D;
        FontConfig.spaceWidth = 9.0D;
        FontConfig.characterSpacing = 2.0D;
        FontConfig.fontSort = new String[]{"Other"};
        FontConfig.characterFontRules = new String[]{"A=Other"};
        FontConfig.refreshDerivedRuleSet();

        Assert.assertEquals(2, settings.getLerpMode());
        Assert.assertEquals(63.5D, settings.getAwtCharSize(), 0.0D);
        Assert.assertEquals(10.0D, settings.getCharSize(), 0.0D);
        Assert.assertEquals(5.0D, settings.getSpaceWidth(), 0.0D);
        Assert.assertEquals(0.25D, settings.getCharacterSpacing(), 0.0D);
        Assert.assertArrayEquals(new String[]{"First", "Second"}, settings.getFontSort());
        Assert.assertEquals("First", settings.getCharacterRuleSet().resolveFontName('A'));
        Assert.assertEquals(64, settings.getGlyphSize());
        Assert.assertEquals(63, settings.getPageGlyphSize());
        Assert.assertEquals(4064, settings.getTextureSize());

        String[] returnedOrder = settings.getFontSort();
        returnedOrder[0] = "Changed";
        Assert.assertArrayEquals(new String[]{"First", "Second"}, settings.getFontSort());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonFiniteGenerationMetrics() {
        new FontRuntimeSettings(3, Double.NaN, 9.0D, 4.0D, 0.1D, false, new String[0],
                FontCharacterRuleSet.empty());
    }

    @Test
    public void atlasTextureScaleDrivesTextureSizeAndSemantics() {
        FontConfig.atlasTextureScale = 32.0D;
        FontRuntimeSettings captured = FontRuntimeSettings.capture();
        Assert.assertEquals(32.0D, captured.getAtlasTextureScale(), 0.0D);
        Assert.assertEquals(Math.max(64, (int) (FontConfig.awtCharSize * 32.0D)), captured.getTextureSize());

        FontRuntimeSettings gridDefault = new FontRuntimeSettings(3, 64.0D, 9.0D, 4.0D, 0.1D, false,
                new String[0], FontCharacterRuleSet.empty());
        Assert.assertEquals(64.0D, gridDefault.getAtlasTextureScale(), 0.0D);
        Assert.assertEquals(64 * 64, gridDefault.getTextureSize());
        Assert.assertFalse("atlas 系数不同必须视为不同 runtime 语义",
                captured.hasSameRuntimeSemantics(gridDefault));
    }

    @Test
    public void resolvedFontOrderWritebackDoesNotLookLikeANewDesiredConfig() {
        FontRuntimeSettings captured = new FontRuntimeSettings(3, 64.0D, 9.0D, 4.0D, 0.1D, true,
                new String[]{"First"}, FontCharacterRuleSet.empty());
        FontRuntimeSettings writeback = new FontRuntimeSettings(3, 64.0D, 9.0D, 4.0D, 0.1D, true,
                new String[]{"First", "Second"}, FontCharacterRuleSet.empty());
        FontRuntimeSettings reordered = new FontRuntimeSettings(3, 64.0D, 9.0D, 4.0D, 0.1D, true,
                new String[]{"Second", "First"}, FontCharacterRuleSet.empty());

        Assert.assertTrue(captured.hasSameRuntimeSemantics(writeback, new String[]{"First", "Second"}));
        Assert.assertFalse(captured.hasSameRuntimeSemantics(reordered, new String[]{"First", "Second"}));
    }

    @Test
    public void publicConstructorComparesEffectiveCharacterRuleSemantics() {
        FontRuntimeSettings first = new FontRuntimeSettings(3, 64.0D, 9.0D, 4.0D, 0.1D, false,
                new String[0], FontCharacterRuleSet.parse(new String[]{"A=First"}));
        FontRuntimeSettings second = new FontRuntimeSettings(3, 64.0D, 9.0D, 4.0D, 0.1D, false,
                new String[0], FontCharacterRuleSet.parse(new String[]{"A=Second"}));
        FontRuntimeSettings equivalent = new FontRuntimeSettings(3, 64.0D, 9.0D, 4.0D, 0.1D, false,
                new String[0], FontCharacterRuleSet.parse(new String[]{"U+0041=First"}));

        Assert.assertFalse(first.hasSameRuntimeSemantics(second));
        Assert.assertTrue(first.hasSameRuntimeSemantics(equivalent));
    }
}
