package club.heiqi.uilib.font;

import java.awt.Font;
import java.lang.reflect.Method;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontCharacterRuleSet;
import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;

/** {@link ActiveFontGeneration} 的 envelope 与 lifecycle 合同。 */
public class ActiveFontGenerationTest {

    @Test
    public void generationPublishesOneConsistentEnvelope() {
        FontCatalog catalog = new FontCatalog();
        catalog.replaceAll(Collections.singletonList(new Font("Dialog", Font.PLAIN, 16)));
        FontRuntimeSettings settings = new FontRuntimeSettings(3, 64.0D, 9.0D, 4.0D, 0.1D, false,
                new String[0], FontCharacterRuleSet.empty());
        GlyphRuntimeTables tables = new GlyphRuntimeTables();
        FontRuntimeMetrics metrics = FontRuntimeMetrics.prepare(settings, catalog.snapshot());

        ActiveFontGeneration generation = new ActiveFontGeneration(7, 11, settings, catalog.snapshot(),
                new String[]{"Dialog"}, tables, metrics);

        Assert.assertEquals(7, generation.getRuntimeVersion());
        Assert.assertEquals(11, generation.getTextMeasureEpoch());
        Assert.assertSame(settings, generation.getSettings());
        Assert.assertSame(catalog.snapshot(), generation.getCatalogSnapshot());
        Assert.assertSame(tables, generation.getRuntimeTables());
        Assert.assertSame(metrics, generation.getMetrics());
        Assert.assertEquals(64.0F, metrics.getAscent(FontType.NORMAL)
                + metrics.getDescent(FontType.NORMAL) + metrics.getLeading(FontType.NORMAL), 0.01F);
        Assert.assertEquals(64.0F, metrics.getAscent(FontType.BOLD)
                + metrics.getDescent(FontType.BOLD) + metrics.getLeading(FontType.BOLD), 0.01F);
        Assert.assertTrue(generation.isActive());

        generation.retire();
        Assert.assertFalse(generation.isActive());
        Assert.assertEquals(ActiveFontGeneration.Lifecycle.RETIRED, generation.getLifecycle());
    }

    @Test
    public void frameLeaseClosesRetirementAdmissionUntilReleased() {
        FontCatalog catalog = new FontCatalog();
        catalog.replaceAll(Collections.singletonList(new Font("Dialog", Font.PLAIN, 16)));
        FontRuntimeSettings settings = new FontRuntimeSettings(3, 64.0D, 9.0D, 4.0D, 0.1D, false,
                new String[0], FontCharacterRuleSet.empty());
        ActiveFontGeneration generation = new ActiveFontGeneration(7, 11, settings, catalog.snapshot(),
                new String[] { "Dialog" }, new GlyphRuntimeTables(),
                FontRuntimeMetrics.prepare(settings, catalog.snapshot()));

        ActiveFontGeneration.GenerationLease lease = generation.tryAcquireFrameLease();

        Assert.assertNotNull(lease);
        Assert.assertEquals(1, generation.getLeaseCount());
        Assert.assertFalse(generation.closeLeaseAdmissionIfIdle());
        Assert.assertTrue(generation.isLeaseAdmissionOpen());

        lease.close();
        lease.close();
        Assert.assertEquals(0, generation.getLeaseCount());
        Assert.assertTrue(generation.closeLeaseAdmissionIfIdle());
        Assert.assertFalse(generation.isLeaseAdmissionOpen());
        Assert.assertNull(generation.tryAcquireFrameLease());

        generation.reopenLeaseAdmission();
        ActiveFontGeneration.GenerationLease reopenedLease = generation.tryAcquireFrameLease();
        Assert.assertNotNull(reopenedLease);
        reopenedLease.close();
        generation.retire();
    }

    @Test
    public void publicGenerationApiDoesNotExposeMutableStorage() {
        for (Method method : ActiveFontGeneration.class.getMethods()) {
            if (method.getDeclaringClass() != ActiveFontGeneration.class) {
                continue;
            }
            Assert.assertNotEquals(GlyphRuntimeTables.class, method.getReturnType());
            Assert.assertNotEquals(DerivedFontCache.class, method.getReturnType());
            Assert.assertFalse(method.getReturnType().isArray());
        }
        for (Method method : FontService.class.getMethods()) {
            Assert.assertNotEquals(ActiveFontGeneration.class, method.getReturnType());
            Assert.assertNotEquals(GlyphRuntimeTables.class, method.getReturnType());
        }
        for (Method method : GlyphRuntimeTablesView.class.getMethods()) {
            Assert.assertNotEquals(GlyphRuntimeTables.class, method.getReturnType());
            Assert.assertFalse(method.getReturnType().isArray());
        }
        for (Method method : FontRuntimeDiagnosticsView.class.getMethods()) {
            Assert.assertNotEquals(ActiveFontGeneration.class, method.getReturnType());
            Assert.assertNotEquals(GlyphRuntimeTables.class, method.getReturnType());
            Assert.assertFalse(method.getReturnType().isArray());
        }
    }

    @Test
    public void singletonDiagnosticsRejectGenerationMutationAndRawStorage() {
        FontService service = new FontService(new FontReloadSignal(0L, 0L, 0L, System::nanoTime));

        Assert.assertSame(service.getRuntimeDiagnostics(), service.getRuntimeDiagnostics());
        assertOwnerGuard(() -> service.getGlyphPageManager().setRuntimeVersion(91));
        assertOwnerGuard(() -> service.getGlyphPageManager().getRuntimeTables());
        assertOwnerGuard(() -> service.getFontMatcher().setRuntimeTables(new GlyphRuntimeTables()));
        assertOwnerGuard(() -> service.getGlyphGenerationDispatcher().setRuntimeVersion(91));
        assertOwnerGuard(() -> service.getGlyphGenerationDispatcher().reset());
        assertOwnerGuard(() -> service.getTextLayoutService().setRuntimeVersion(91));

        service.shutdown();
    }

    private void assertOwnerGuard(Runnable operation) {
        try {
            operation.run();
            Assert.fail("singleton 诊断对象不得允许外部 generation/storage 写入");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("runtime owner"));
        }
    }
}
