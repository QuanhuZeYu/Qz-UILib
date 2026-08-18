package club.heiqi.uilib.font.page;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;

/**
 * 惰性生命周期重置测试（P0-B）：generation 换代只清门控数组，其余几何数组靠 location 门控惰性失效。
 */
public class GlyphRuntimeLazyLifecycleResetTest {

    @Test
    public void lifecycleResetOnlyClearsGatingArrays() {
        GlyphRuntimeTables tables = new GlyphRuntimeTables();
        dirtyTables(tables);

        tables.resetGlyphLifecycle();

        // 门控数组必须清零
        Assert.assertEquals(GlyphRuntimeTables.STATE_ABSENT, tables.stateNormal['A']);
        Assert.assertEquals(GlyphRuntimeTables.STATE_ABSENT, tables.stateBold['A']);
        Assert.assertEquals(GlyphRuntimeTables.LOCATION_NOT_READY, tables.locationNormal['A']);
        Assert.assertEquals(GlyphRuntimeTables.LOCATION_NOT_READY, tables.locationBold['A']);
        Assert.assertTrue(Float.isNaN(tables.widthNormal['A']));
        Assert.assertTrue(Float.isNaN(tables.widthBold['A']));
        Assert.assertEquals(GlyphRuntimeTables.FONT_INDEX_UNRESOLVED, tables.matchedFontNormal['A']);
        Assert.assertEquals(GlyphRuntimeTables.FONT_INDEX_UNRESOLVED, tables.matchedFontBold['A']);
        Assert.assertEquals(0.0F, tables.ascentNormal, 0.0F);
        Assert.assertEquals(0.0F, tables.descentBold, 0.0F);
        Assert.assertEquals(0.0F, tables.leadingNormal, 0.0F);
        Assert.assertEquals(0, tables.normalPageCount);
        Assert.assertEquals(0, tables.boldPageCount);
        Assert.assertNull(tables.normalPages[0]);

        // 非门控数组保留旧值：location 已清，渲染侧不会读取它们；claim 时会按需重置
        Assert.assertEquals(123L, tables.requestIdNormal['A']);
        Assert.assertEquals(456L, tables.requestIdBold['A']);
        Assert.assertEquals(GlyphRuntimeTables.GLYPH_FLAG_COLORED, tables.flagsNormal['A']);
        Assert.assertEquals(GlyphRuntimeTables.GLYPH_FLAG_HAS_BITMAP, tables.flagsBold['A']);
        Assert.assertEquals(42, tables.slotXNormal['A']);
        Assert.assertEquals(8, tables.slotWidthNormal['A']);
        Assert.assertEquals(8, tables.slotHeightNormal['A']);
        Assert.assertEquals(1, tables.atlasBaselineXNormal['A']);
        Assert.assertEquals(2, tables.atlasBaselineYNormal['A']);
        Assert.assertEquals(48, tables.lineBaselineYNormal['A']);
        Assert.assertEquals(5, tables.inkWidthNormal['A']);
        Assert.assertEquals(6, tables.inkHeightNormal['A']);
        Assert.assertEquals(-1, tables.bearingXNormal['A']);
        Assert.assertEquals(3, tables.bearingYNormal['A']);
    }

    @Test
    public void fullResetStillClearsEveryArray() {
        GlyphRuntimeTables tables = new GlyphRuntimeTables();
        dirtyTables(tables);

        tables.resetGlyphRuntime();

        Assert.assertEquals(GlyphRuntimeTables.STATE_ABSENT, tables.stateNormal['A']);
        Assert.assertEquals(GlyphRuntimeTables.LOCATION_NOT_READY, tables.locationNormal['A']);
        Assert.assertEquals(0L, tables.requestIdNormal['A']);
        Assert.assertEquals(0, tables.flagsNormal['A']);
        Assert.assertEquals(0, tables.slotXNormal['A']);
        Assert.assertEquals(0, tables.slotWidthNormal['A']);
        Assert.assertEquals(0, tables.slotHeightNormal['A']);
        Assert.assertEquals(0, tables.atlasBaselineXNormal['A']);
        Assert.assertEquals(0, tables.atlasBaselineYNormal['A']);
        Assert.assertEquals(0, tables.lineBaselineYNormal['A']);
        Assert.assertEquals(0, tables.inkWidthNormal['A']);
        Assert.assertEquals(0, tables.inkHeightNormal['A']);
        Assert.assertEquals(0, tables.bearingXNormal['A']);
        Assert.assertEquals(0, tables.bearingYNormal['A']);
    }

    private static void dirtyTables(GlyphRuntimeTables tables) {
        tables.stateNormal['A'] = GlyphRuntimeTables.STATE_RESIDENT;
        tables.stateBold['A'] = GlyphRuntimeTables.STATE_RESIDENT;
        tables.locationNormal['A'] = GlyphRuntimeTables.packLocation(0, 5);
        tables.locationBold['A'] = GlyphRuntimeTables.packLocation(1, 3);
        tables.widthNormal['A'] = 7.25F;
        tables.widthBold['A'] = 8.5F;
        tables.matchedFontNormal['A'] = 2;
        tables.matchedFontBold['A'] = 1;
        tables.requestIdNormal['A'] = 123L;
        tables.requestIdBold['A'] = 456L;
        tables.flagsNormal['A'] = GlyphRuntimeTables.GLYPH_FLAG_COLORED;
        tables.flagsBold['A'] = GlyphRuntimeTables.GLYPH_FLAG_HAS_BITMAP;
        tables.slotXNormal['A'] = 42;
        tables.slotWidthNormal['A'] = 8;
        tables.slotHeightNormal['A'] = 8;
        tables.atlasBaselineXNormal['A'] = 1;
        tables.atlasBaselineYNormal['A'] = 2;
        tables.lineBaselineYNormal['A'] = 48;
        tables.inkWidthNormal['A'] = 5;
        tables.inkHeightNormal['A'] = 6;
        tables.bearingXNormal['A'] = -1;
        tables.bearingYNormal['A'] = 3;
        tables.ascentNormal = 40.0F;
        tables.descentNormal = 15.0F;
        tables.leadingNormal = 9.0F;
        tables.ascentBold = 41.0F;
        tables.descentBold = 16.0F;
        tables.leadingBold = 10.0F;
        tables.setPage(FontType.NORMAL, 0, new GlyphPage(1, 0, 64, 64));
        tables.setPage(FontType.BOLD, 0, new GlyphPage(1, 0, 64, 64));
    }
}
