package club.heiqi.uilib.font.page;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontCharacterRuleSet;

/** 完整 Unicode table 只由 manager 分配一次并跨 generation 转移。 */
public class GlyphRuntimeTableOwnershipTest {

    @Test
    public void successfulGenerationChangeReusesAndClearsTableStorage() {
        GlyphPageManager manager = new GlyphPageManager();
        FontRuntimeSettings oldSettings = settings(64.0D, 9.0D);
        FontRuntimeSettings newSettings = settings(96.0D, 12.0D);
        manager.setGeneration(1, oldSettings);
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        tables.widthNormal['A'] = 7.0F;
        Assert.assertTrue(manager.tryMarkGenerating(1, 'A', FontType.NORMAL));

        manager.setGeneration(2, newSettings);

        Assert.assertSame(tables, manager.getRuntimeTables());
        Assert.assertTrue(Float.isNaN(tables.widthNormal['A']));
        Assert.assertEquals(GlyphState.NEW, manager.getState('A', FontType.NORMAL));
        Assert.assertTrue(manager.tryMarkGenerating(2, 'A', FontType.NORMAL));
    }

    @Test
    public void pageRetirementFailureDoesNotInterruptStorageTransfer() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setGeneration(1, settings(64.0D, 9.0D));
        manager.initialize();
        GlyphRuntimeTables tables = manager.getRuntimeTables();
        FailingOnceCloseGlyphPage failingPage = new FailingOnceCloseGlyphPage();
        tables.normalPages[0] = failingPage;

        manager.setGeneration(2, settings(64.0D, 10.0D));

        Assert.assertSame(tables, manager.getRuntimeTables());
        Assert.assertTrue(manager.tryMarkGenerating(2, 'A', FontType.NORMAL));
        Assert.assertEquals(1, failingPage.closeCount);

        manager.setGeneration(3, settings(64.0D, 11.0D));

        Assert.assertEquals("失败页必须由 manager 保留所有权并在后续换代重试", 2, failingPage.closeCount);
    }

    private FontRuntimeSettings settings(double awtCharSize, double charSize) {
        return new FontRuntimeSettings(3, awtCharSize, charSize, 4.0D, 0.1D, false, new String[0],
                FontCharacterRuleSet.empty());
    }

    private static final class FailingOnceCloseGlyphPage extends GlyphPage {

        private int closeCount;

        private FailingOnceCloseGlyphPage() {
            super(1, 0, 64, 64, 3);
        }

        @Override
        public void close() {
            closeCount++;
            if (closeCount == 1) {
                throw new IllegalStateException("page close failure");
            }
            super.close();
        }
    }
}
