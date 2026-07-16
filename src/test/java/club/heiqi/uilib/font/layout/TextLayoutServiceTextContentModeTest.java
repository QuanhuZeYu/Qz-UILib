package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * `TextLayoutService` 文本内容模式测试。
 */
public class TextLayoutServiceTextContentModeTest {

    /**
     * 验证原始文本模式不会吞掉 Minecraft 格式码字符。
     */
    @Test
    public void shouldKeepSectionCodesAsLiteralTextInUiLibRawMode() {
        TextLayoutService service = createService();

        List<TextSegment> segments = service.parseSegments("价格：§a100金币", 0xFFFFFFFF, TextContentMode.UILIB_RAW);

        Assert.assertEquals(1, segments.size());
        Assert.assertEquals("价格：§a100金币", segments.get(0).getText());
        Assert.assertEquals(0xFFFFFFFF, segments.get(0).getStyle().getColor());
    }

    /**
     * 验证 Minecraft 文本模式仍会解析 `§` 格式码。
     */
    @Test
    public void shouldParseSectionCodesInMinecraftFormattedMode() {
        TextLayoutService service = createService();

        List<TextSegment> segments = service.parseSegments("价格：§a100金币", 0xFFFFFFFF,
                TextContentMode.MINECRAFT_FORMATTED);

        Assert.assertEquals(2, segments.size());
        Assert.assertEquals("价格：", segments.get(0).getText());
        Assert.assertEquals("100金币", segments.get(1).getText());
        Assert.assertEquals(MinecraftColorTable.getColor('a', false, 255), segments.get(1).getStyle().getColor());
    }

    /**
     * 验证原始文本模式的裁剪与换行不会跳过 `§` 后续字符。
     */
    @Test
    public void shouldTreatSectionCodesAsVisibleCharactersInUiLibRawTrimAndWrap() {
        TextLayoutService service = createService('A', '§', 'a', 'B');

        String trimmed = service.trimStringToWidth("A§aB", service.getStringWidth("A§", TextContentMode.UILIB_RAW),
                TextContentMode.UILIB_RAW);
        List<String> wrapped = service.listFormattedStringToWidth("A§aB", service.getStringWidth("A§",
                TextContentMode.UILIB_RAW), TextContentMode.UILIB_RAW);

        Assert.assertEquals("A§", trimmed);
        Assert.assertEquals(Arrays.asList("A§", "aB"), wrapped);
    }

    private static TextLayoutService createService(int... fixedWidthCodepoints) {
        FontCatalog fontCatalog = new FontCatalog();
        fontCatalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        GlyphPageManager glyphPageManager = new GlyphPageManager();
        float[] normalWidths = glyphPageManager.getRuntimeTables().widthArray(FontType.NORMAL);
        for (int codepoint : fixedWidthCodepoints) {
            normalWidths[codepoint] = 1.0F;
        }
        TextLayoutService service = new TextLayoutService(new FontMatcher(fontCatalog, derivedFontCache),
                glyphPageManager, derivedFontCache);
        service.setRuntimeVersion(1);
        return service;
    }
}
