package club.heiqi.uilib.font;

import org.junit.Assert;
import org.junit.Test;
import club.heiqi.uilib.font.config.FontConfig;

public class FontRuntimeMathPaddingTest {
    @Test
    public void mathematicalPaddingIsFrozenAndParticipatesInGenerationSemantics() {
        int previous = FontConfig.glyphInkPadding;
        try {
            FontConfig.glyphInkPadding = 5;
            FontRuntimeSettings first = FontRuntimeSettings.capture();
            FontConfig.glyphInkPadding = 6;
            FontRuntimeSettings second = FontRuntimeSettings.capture();
            Assert.assertEquals(5, first.getGlyphInkPadding());
            Assert.assertEquals(6, second.getGlyphInkPadding());
            Assert.assertFalse(first.hasSameRuntimeSemantics(second));
            FontConfig.glyphInkPadding = -1;
            Assert.assertEquals(0, FontRuntimeSettings.capture().getGlyphInkPadding());
            FontConfig.glyphInkPadding = 100;
            Assert.assertEquals(32, FontRuntimeSettings.capture().getGlyphInkPadding());
        } finally {
            FontConfig.glyphInkPadding = previous;
        }
    }
}
