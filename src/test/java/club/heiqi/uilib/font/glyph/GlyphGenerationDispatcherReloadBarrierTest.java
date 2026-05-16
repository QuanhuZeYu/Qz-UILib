package club.heiqi.uilib.font.glyph;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.page.GlyphPageManager;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontMatcher;

/**
 * 字形生成调度器重载屏障测试。
 */
public class GlyphGenerationDispatcherReloadBarrierTest {

    /**
     * 验证暂停后的生成请求会被重载屏障丢弃，不会污染字符状态。
     */
    @Test
    public void shouldDropSubmitWhileReloading() {
        GlyphPageManager pageManager = new GlyphPageManager();
        FontCatalog fontCatalog = new FontCatalog();
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        pageManager.setRuntimeVersion(1);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(new FontMatcher(fontCatalog, derivedFontCache), pageManager, derivedFontCache,
                pageManager::queueUpload);

        dispatcher.pause();
        dispatcher.submit(new GlyphGenerationTask(1, '中', FontType.NORMAL, 16, GlyphGenerationPriority.HIGH));

        Assert.assertTrue(dispatcher.isReloading());
        Assert.assertEquals(0, pageManager.snapshotRecoverableRequests().length);
    }

    /**
     * 验证 reset 后重新 initialize 会重新打开生成入口。
     */
    @Test
    public void shouldReopenSubmitGateAfterInitialize() {
        GlyphPageManager pageManager = new GlyphPageManager();
        FontCatalog fontCatalog = new FontCatalog();
        DerivedFontCache derivedFontCache = new DerivedFontCache(fontCatalog);
        pageManager.setRuntimeVersion(1);
        GlyphGenerationDispatcher dispatcher = new GlyphGenerationDispatcher();
        dispatcher.setRuntimeVersion(1);
        dispatcher.initialize(new FontMatcher(fontCatalog, derivedFontCache), pageManager, derivedFontCache,
                pageManager::queueUpload);

        dispatcher.reset();
        Assert.assertTrue(dispatcher.isReloading());

        dispatcher.setRuntimeVersion(2);
        pageManager.setRuntimeVersion(2);
        dispatcher.initialize(new FontMatcher(fontCatalog, derivedFontCache), pageManager, derivedFontCache,
                pageManager::queueUpload);

        Assert.assertFalse(dispatcher.isReloading());
    }
}
