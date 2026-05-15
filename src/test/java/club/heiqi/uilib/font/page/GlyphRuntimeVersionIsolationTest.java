package club.heiqi.uilib.font.page;

import java.awt.image.BufferedImage;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphInfo;

/**
 * 字符运行时版本隔离测试。
 */
public class GlyphRuntimeVersionIsolationTest {

    /**
     * 验证不同运行时版本下的同一字符不会复用同一个缓存键。
     */
    @Test
    public void shouldSeparateGlyphCacheKeyByRuntimeVersion() {
        GlyphCacheKey oldKey = new GlyphCacheKey(1, 'A', FontType.NORMAL);
        GlyphCacheKey newKey = new GlyphCacheKey(2, 'A', FontType.NORMAL);

        Assert.assertNotEquals(oldKey, newKey);
        Assert.assertNotEquals(oldKey.hashCode(), newKey.hashCode());
    }

    /**
     * 验证旧运行时生成结果不会进入新运行时的上传队列。
     */
    @Test
    public void shouldRejectStaleGenerationResultBeforeUploadQueue() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(2);

        manager.queueUpload(result(1, 'A'));

        Assert.assertEquals(0, manager.getPendingUploadCount());
        Assert.assertEquals(GlyphState.NEW, manager.getState('A', FontType.NORMAL));
    }

    /**
     * 验证连续版本切换后，旧版本状态不会阻止新版本同码点重新生成。
     */
    @Test
    public void shouldAllowSameCodepointAfterRuntimeVersionChanges() {
        GlyphPageManager manager = new GlyphPageManager();
        manager.setRuntimeVersion(1);

        Assert.assertTrue(manager.tryMarkGenerating(1, 'A', FontType.NORMAL));
        Assert.assertEquals(GlyphState.GENERATING, manager.getState('A', FontType.NORMAL));

        manager.setRuntimeVersion(2);

        Assert.assertEquals(GlyphState.NEW, manager.getState('A', FontType.NORMAL));
        Assert.assertTrue(manager.tryMarkGenerating(2, 'A', FontType.NORMAL));
        Assert.assertEquals(GlyphState.GENERATING, manager.getState('A', FontType.NORMAL));
    }

    private static GlyphGenerationResult result(int runtimeVersion, int codepoint) {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        GlyphInfo glyphInfo = new GlyphInfo(codepoint, 8, 8, 6.0F, 6.0F, 8.0F, false);
        return new GlyphGenerationResult(runtimeVersion, codepoint, FontType.NORMAL, image, glyphInfo);
    }
}
