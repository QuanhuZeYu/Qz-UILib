package club.heiqi.uilib.font.layout;

import java.awt.Font;
import java.util.Arrays;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;
import club.heiqi.uilib.ui.text.TextContentMode;

/**
 * 宽度缓存 miss 预算测试（P1-E）。
 */
public class TextLayoutServiceWidthMissBudgetTest {

    private int savedBudget;
    private double savedSpaceWidth;
    private GlyphPageManager glyphPageManager;

    @Before
    public void setUp() {
        savedBudget = FontConfig.widthCacheMissBudgetPerWindow;
        savedSpaceWidth = FontConfig.spaceWidth;
        FontConfig.spaceWidth = 7.5D;
    }

    @After
    public void tearDown() {
        FontConfig.widthCacheMissBudgetPerWindow = savedBudget;
        FontConfig.spaceWidth = savedSpaceWidth;
    }

    @Test
    public void exhaustedBudgetDefersMeasurementAndSkipsCaching() {
        FontConfig.widthCacheMissBudgetPerWindow = 2;
        TextLayoutService service = createService();

        // 同一窗口内三个新字符：前两个测量，第三个按空格宽度顺延。
        service.getStringWidth("甲乙丙", TextContentMode.UILIB_RAW);

        Assert.assertEquals(3L, service.getWidthCacheMissCount());
        Assert.assertEquals(1L, service.getWidthCacheBudgetRejectedCount());

        // 被顺延的字符未写入缓存：再次测量仍被预算拒绝。
        double deferredWidth = service.getCodepointWidth('丙', new TextStyle());
        Assert.assertEquals(7.5D, deferredWidth, 0.0001D);
        Assert.assertEquals(2L, service.getWidthCacheBudgetRejectedCount());

        // 顺延字符仍未污染宽度缓存。
        Assert.assertTrue(Float.isNaN(glyphPageManager.getRuntimeTables().widthArray(FontType.NORMAL)['丙']));
    }

    @Test
    public void nonPositiveBudgetDisablesThrottling() {
        FontConfig.widthCacheMissBudgetPerWindow = 0;
        TextLayoutService service = createService();

        service.getStringWidth("甲乙丙丁", TextContentMode.UILIB_RAW);

        Assert.assertEquals(0L, service.getWidthCacheBudgetRejectedCount());
        Assert.assertEquals(4L, service.getWidthCacheMissCount());
        Assert.assertEquals(0L, service.getWidthCacheHitCount());
    }

    @Test
    public void windowExpiryRestoresBudget() throws InterruptedException {
        FontConfig.widthCacheMissBudgetPerWindow = 2;
        TextLayoutService service = createService();

        service.getStringWidth("甲乙丙", TextContentMode.UILIB_RAW);
        Assert.assertEquals(1L, service.getWidthCacheBudgetRejectedCount());

        Thread.sleep(25L);
        service.getStringWidth("丁", TextContentMode.UILIB_RAW);

        Assert.assertEquals("窗口过期后恢复预算并正常测量", 1L, service.getWidthCacheBudgetRejectedCount());
        Assert.assertEquals(4L, service.getWidthCacheMissCount());
    }

    private TextLayoutService createService() {
        FontCatalog fontCatalog = new FontCatalog();
        fontCatalog.replaceAll(Arrays.asList(new Font("Dialog", Font.PLAIN, 14)));
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        glyphPageManager = new GlyphPageManager();
        TextLayoutService service = new TextLayoutService(new FontMatcher(fontCatalog, derivedFontCache),
                glyphPageManager, derivedFontCache);
        service.setRuntimeVersion(1);
        return service;
    }
}
