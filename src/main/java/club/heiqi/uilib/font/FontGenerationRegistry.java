package club.heiqi.uilib.font;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.util.FontCatalog;
import club.heiqi.uilib.font.util.FontOrderPlanner;
import club.heiqi.uilib.font.util.FontOrderSnapshot;

/** FontService 内部的 snapshot-only catalog prepare/publish owner。 */
final class FontGenerationRegistry {

    private final FontCatalog fontCatalog;
    private final FontOrderPlanner fontOrderPlanner = new FontOrderPlanner();

    FontGenerationRegistry(FontCatalog fontCatalog) {
        if (fontCatalog == null) {
            throw new IllegalArgumentException("fontCatalog 不得为 null");
        }
        this.fontCatalog = fontCatalog;
    }

    PreparedCatalog prepare(FontRuntimeSettings settings, FontResourceSnapshot resources) {
        if (settings == null || resources == null) {
            throw new IllegalArgumentException("generation registry 输入不得为 null");
        }
        List<Font> fonts = new ArrayList<Font>();
        fonts.addAll(loadAssetFonts(settings, resources));
        fonts.addAll(loadInstalledFonts(settings, resources));
        FontResourceSnapshot.assertNotInterrupted();
        String[] orderHints = settings.isFontSortConfigured() ? settings.getFontSort()
                : resources.getDefaultOrderHints();
        FontOrderSnapshot orderSnapshot = fontOrderPlanner.plan(fonts, orderHints,
                settings.isFontSortConfigured());
        return new PreparedCatalog(fontCatalog.prepareSnapshot(orderSnapshot.getOrderedFonts()), orderSnapshot);
    }

    void validate(PreparedCatalog preparedCatalog) {
        if (preparedCatalog == null) {
            throw new IllegalArgumentException("preparedCatalog 不得为 null");
        }
        fontCatalog.validate(preparedCatalog.getCatalogSnapshot());
        if (preparedCatalog.getCatalogSnapshot().getFonts().isEmpty()) {
            throw new IllegalStateException("字体 catalog candidate 不得为空");
        }
    }

    void publish(PreparedCatalog preparedCatalog) {
        fontCatalog.publish(preparedCatalog.getCatalogSnapshot());
    }

    void completePublication(PreparedCatalog preparedCatalog) {
        FontConfig.applyFontOrderSnapshot(preparedCatalog.getOrderSnapshot());
        MyMod.LOG.info("字体注册完成，可用字体数量：{}，缺失字体数量：{}",
                Integer.valueOf(fontCatalog.getFonts().size()),
                Integer.valueOf(FontConfig.getMissingFontSnapshot().length));
    }

    private List<Font> loadAssetFonts(FontRuntimeSettings settings, FontResourceSnapshot resources) {
        List<Font> fonts = new ArrayList<Font>();
        float derivedSize = (float) settings.getAwtCharSize();
        for (FontResourceSnapshot.AssetFontResource resource : resources.getAssetFonts()) {
            FontResourceSnapshot.assertNotInterrupted();
            try (ByteArrayInputStream input = resource.openContentStream()) {
                fonts.add(Font.createFont(Font.TRUETYPE_FONT, input).deriveFont(derivedSize));
            } catch (FontFormatException exception) {
                MyMod.LOG.error("字体格式无效：{}", resource.getName(), exception);
            } catch (IOException exception) {
                MyMod.LOG.error("读取字体失败：{}", resource.getName(), exception);
            }
        }
        return fonts;
    }

    private List<Font> loadInstalledFonts(FontRuntimeSettings settings, FontResourceSnapshot resources) {
        List<Font> fonts = new ArrayList<Font>();
        float derivedSize = (float) settings.getAwtCharSize();
        for (Font font : resources.getInstalledFonts()) {
            FontResourceSnapshot.assertNotInterrupted();
            fonts.add(font.deriveFont(derivedSize));
        }
        return fonts;
    }

    /** 已完成发现、排序和不可变复制的内部 catalog candidate。 */
    static final class PreparedCatalog {

        private final FontCatalog.Snapshot catalogSnapshot;
        private final FontOrderSnapshot orderSnapshot;

        private PreparedCatalog(FontCatalog.Snapshot catalogSnapshot, FontOrderSnapshot orderSnapshot) {
            this.catalogSnapshot = catalogSnapshot;
            this.orderSnapshot = orderSnapshot;
        }

        FontCatalog.Snapshot getCatalogSnapshot() {
            return catalogSnapshot;
        }

        FontOrderSnapshot getOrderSnapshot() {
            return orderSnapshot;
        }
    }
}
