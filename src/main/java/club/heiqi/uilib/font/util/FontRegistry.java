package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.util.FontOrderSnapshot;
import club.heiqi.uilib.font.util.FontOrderPlanner;

/**
 * 字体注册器，负责发现并整理可用字体。
 */
public class FontRegistry {

    private final FontCatalog fontCatalog;
    private final FontOrderPlanner fontOrderPlanner = new FontOrderPlanner();

    /**
     * 创建字体注册器。
     *
     * @param fontCatalog 字体目录
     */
    public FontRegistry(FontCatalog fontCatalog) {
        this.fontCatalog = fontCatalog;
    }

    /**
     * 刷新字体目录。
     */
    public void reload() {
        List<Font> fonts = new ArrayList<Font>();
        fonts.addAll(loadAssetFonts());
        fonts.addAll(loadInstalledFonts());
        FontOrderSnapshot snapshot = fontOrderPlanner.plan(fonts, FontConfig.fontSort);
        fontCatalog.replaceAll(snapshot.getOrderedFonts());
        FontConfig.applyFontOrderSnapshot(snapshot);

        MyMod.LOG.info("字体注册完成，可用字体数量：{}，缺失字体数量：{}", Integer.valueOf(fontCatalog.getFonts().size()),
                Integer.valueOf(FontConfig.getMissingFontSnapshot().length));
    }

    private List<Font> loadAssetFonts() {
        List<Font> fonts = new ArrayList<Font>();
        File fontDirectory = new File(System.getProperty("user.dir"), "fonts");
        if (!fontDirectory.exists()) {
            fontDirectory.mkdirs();
        }

        File[] fontFiles = fontDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".ttf")
                || name.toLowerCase().endsWith(".otf")
                || name.toLowerCase().endsWith(".ttc"));
        if (fontFiles == null) {
            return fonts;
        }

        float derivedSize = (float) (FontConfig.awtCharSize * FontConfig.fontScale);
        for (File file : fontFiles) {
            try {
                Font font = Font.createFont(Font.TRUETYPE_FONT, file).deriveFont(derivedSize);
                fonts.add(font);
            } catch (FontFormatException e) {
                MyMod.LOG.error("字体格式无效：{}", file.getAbsolutePath(), e);
            } catch (IOException e) {
                MyMod.LOG.error("读取字体失败：{}", file.getAbsolutePath(), e);
            }
        }
        return fonts;
    }

    private List<Font> loadInstalledFonts() {
        List<Font> fonts = new ArrayList<Font>();
        float derivedSize = (float) (FontConfig.awtCharSize * FontConfig.fontScale);
        Font[] installed = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        for (Font font : installed) {
            fonts.add(font.deriveFont(derivedSize));
        }
        return fonts;
    }
}
