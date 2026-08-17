package club.heiqi.uilib.font.page;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontRuntimeAccess;
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
        Assert.assertNotNull(manager.claimRequest(1, 'A', FontType.NORMAL));

        manager.setGeneration(2, newSettings);

        Assert.assertSame(tables, manager.getRuntimeTables());
        Assert.assertTrue(Float.isNaN(tables.widthNormal['A']));
        Assert.assertEquals(GlyphState.ABSENT, manager.getState('A', FontType.NORMAL));
        Assert.assertNotNull(manager.claimRequest(2, 'A', FontType.NORMAL));
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
        Assert.assertNotNull(manager.claimRequest(2, 'A', FontType.NORMAL));
        Assert.assertEquals(1, failingPage.closeCount);

        manager.setGeneration(3, settings(64.0D, 11.0D));

        Assert.assertEquals("失败页必须由 manager 保留所有权并在后续换代重试", 2, failingPage.closeCount);
    }

    @Test
    public void retainedGenerationBlocksRetiringAnotherActiveGeneration() {
        final Object ownerToken = new Object();
        final GlyphPageManager manager = new GlyphPageManager(ownerToken);
        FontRuntimeAccess.run(ownerToken, () -> {
            manager.setGeneration(1, settings(64.0D, 9.0D));
            manager.initialize();
            GlyphRuntimeTables tables = manager.getRuntimeTables();
            AlwaysFailingCloseGlyphPage failingPage = new AlwaysFailingCloseGlyphPage();
            tables.normalPages[0] = failingPage;

            manager.setGeneration(2, settings(64.0D, 10.0D));
            Assert.assertNotNull(manager.claimRequest(2, 'A', FontType.NORMAL));
            Assert.assertEquals(1, manager.getResidentAtlasPageCount());

            try {
                manager.setGeneration(3, settings(64.0D, 11.0D));
                Assert.fail("retiring generation 未释放时不得退休当前 active generation");
            } catch (IllegalStateException expected) {
                Assert.assertTrue(expected.getMessage().contains("retiring generation"));
            }

            Assert.assertNotNull("拒绝第三代后第二代 tables 必须继续服务",
                    manager.claimRequest(2, 'B', FontType.NORMAL));
            int attemptsBeforeTick = failingPage.closeCount;
            try {
                manager.flushPendingUploads(0);
                Assert.fail("retiring ownership 未释放时 housekeeping 应报告 transfer defer");
            } catch (java.util.concurrent.RejectedExecutionException expected) {
                Assert.assertTrue(expected.getMessage().contains("retiring generation"));
            }
            Assert.assertEquals("render tick 必须重试唯一 retiring generation", attemptsBeforeTick + 1,
                    failingPage.closeCount);
            Assert.assertEquals(1, manager.getResidentAtlasPageCount());

            failingPage.allowClose();
            manager.flushPendingUploads(0);
            Assert.assertEquals(0, manager.getResidentAtlasPageCount());
            manager.setGeneration(3, settings(64.0D, 11.0D));
            Assert.assertNotNull(manager.claimRequest(3, 'C', FontType.NORMAL));
        });
    }

    @Test
    public void independentManagerZeroBudgetKeepsExistingFlushSemantics() throws Exception {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setGeneration(1, settings(64.0D, 9.0D));
        manager.initialize();
        FailingOnceCloseGlyphPage failingPage = new FailingOnceCloseGlyphPage();
        manager.getRuntimeTables().normalPages[0] = failingPage;
        manager.setGeneration(2, settings(64.0D, 10.0D));
        Field exhaustedCount = GlyphPageManager.class.getDeclaredField("uploadAttemptBudgetExhaustedCount");
        exhaustedCount.setAccessible(true);
        long attemptsBeforeFlush = exhaustedCount.getLong(manager);

        manager.flushPendingUploads(0);

        Assert.assertEquals("独立公开 manager 的零预算 flush 不执行额外 housekeeping", 1,
                failingPage.closeCount);
        Assert.assertEquals("独立公开 manager 必须保留既有零预算诊断计数", attemptsBeforeFlush + 1L,
                exhaustedCount.getLong(manager));
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

    private static final class AlwaysFailingCloseGlyphPage extends GlyphPage {

        private int closeCount;
        private boolean closeAllowed;
        private boolean ownsTexture = true;

        private AlwaysFailingCloseGlyphPage() {
            super(1, 0, 64, 64, 3);
        }

        @Override
        public void close() {
            closeCount++;
            if (!closeAllowed) {
                throw new IllegalStateException("page close failure");
            }
            ownsTexture = false;
        }

        @Override
        boolean hasTextureOwnership() {
            return ownsTexture;
        }

        private void allowClose() {
            closeAllowed = true;
        }
    }
}
