package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontRuntimeSettings;
import club.heiqi.uilib.font.config.FontConfig;

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
        PreparedCatalog preparedCatalog = prepare(FontRuntimeSettings.capture());
        publish(preparedCatalog);
        completePublication(preparedCatalog);
    }

    /**
     * 发现并规划字体目录，但不修改 active catalog 或 FontConfig。
     *
     * @param settings generation 设置
     * @return CPU-only 目录 candidate
     */
    public PreparedCatalog prepare(FontRuntimeSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings 不得为 null");
        }
        List<Font> fonts = new ArrayList<Font>();
        fonts.addAll(loadAssetFonts(settings));
        fonts.addAll(loadInstalledFonts(settings));
        String[] orderHints = settings.isFontSortConfigured() ? settings.getFontSort()
                : DefaultFontOrderHints.resolveForCurrentPlatform();
        FontOrderSnapshot orderSnapshot = fontOrderPlanner.plan(fonts, orderHints, settings.isFontSortConfigured());
        return new PreparedCatalog(fontCatalog.prepareSnapshot(orderSnapshot.getOrderedFonts()), orderSnapshot);
    }

    /**
     * 在 generation barrier 内发布已准备的 catalog 引用。
     *
     * @param preparedCatalog 已准备目录
     */
    public void publish(PreparedCatalog preparedCatalog) {
        if (preparedCatalog == null) {
            throw new IllegalArgumentException("preparedCatalog 不得为 null");
        }
        fontCatalog.publish(preparedCatalog.getCatalogSnapshot());
    }

    /**
     * 在 candidate 已验证且 generation owner 持有串行 commit 权时执行无失败引用发布。
     *
     * @param preparedCatalog 已验证目录
     */
    public void publishValidated(PreparedCatalog preparedCatalog) {
        fontCatalog.publishValidated(preparedCatalog.getCatalogSnapshot());
    }

    /**
     * 在进入破坏性 generation barrier 前验证目录 candidate。
     *
     * @param preparedCatalog 已准备目录
     */
    public void validate(PreparedCatalog preparedCatalog) {
        if (preparedCatalog == null) {
            throw new IllegalArgumentException("preparedCatalog 不得为 null");
        }
        fontCatalog.validate(preparedCatalog.getCatalogSnapshot());
        if (preparedCatalog.getCatalogSnapshot().getFonts().isEmpty()) {
            throw new IllegalStateException("字体 catalog candidate 不得为空");
        }
    }

    /**
     * generation 发布后的配置展示态与日志收尾。
     *
     * @param preparedCatalog 已发布目录
     */
    public void completePublication(PreparedCatalog preparedCatalog) {
        FontConfig.applyFontOrderSnapshot(preparedCatalog.getOrderSnapshot());

        MyMod.LOG.info("字体注册完成，可用字体数量：{}，缺失字体数量：{}", Integer.valueOf(fontCatalog.getFonts().size()),
                Integer.valueOf(FontConfig.getMissingFontSnapshot().length));
    }

    private List<Font> loadAssetFonts(FontRuntimeSettings settings) {
        List<Font> fonts = new ArrayList<Font>();
        File fontDirectory = new File(resolveGameRootDirectory(), "fonts");
        if (!fontDirectory.exists()) {
            fontDirectory.mkdirs();
        }

        File[] fontFiles = fontDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".ttf")
                || name.toLowerCase().endsWith(".otf")
                || name.toLowerCase().endsWith(".ttc"));
        if (fontFiles == null) {
            return fonts;
        }

        float derivedSize = (float) settings.getAwtCharSize();
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

    private File resolveGameRootDirectory() {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getMinecraft").invoke(null);
            if (minecraft != null) {
                Object dataDir = minecraftClass.getField("mcDataDir").get(minecraft);
                if (dataDir instanceof File) {
                    return (File) dataDir;
                }
            }
        } catch (Throwable ignored) {
            // 测试或早期启动阶段可能没有可用客户端实例，回退旧路径语义。
        }
        return new File(System.getProperty("user.dir"));
    }

    private List<Font> loadInstalledFonts(FontRuntimeSettings settings) {
        List<Font> fonts = new ArrayList<Font>();
        float derivedSize = (float) settings.getAwtCharSize();
        Font[] installed = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        for (Font font : installed) {
            fonts.add(font.deriveFont(derivedSize));
        }
        return fonts;
    }

    /** 已完成发现、排序和不可变复制的目录 candidate。 */
    public static final class PreparedCatalog {

        private final FontCatalog.Snapshot catalogSnapshot;
        private final FontOrderSnapshot orderSnapshot;

        private PreparedCatalog(FontCatalog.Snapshot catalogSnapshot, FontOrderSnapshot orderSnapshot) {
            this.catalogSnapshot = catalogSnapshot;
            this.orderSnapshot = orderSnapshot;
        }

        public FontCatalog.Snapshot getCatalogSnapshot() {
            return catalogSnapshot;
        }

        public FontOrderSnapshot getOrderSnapshot() {
            return orderSnapshot;
        }
    }
}
