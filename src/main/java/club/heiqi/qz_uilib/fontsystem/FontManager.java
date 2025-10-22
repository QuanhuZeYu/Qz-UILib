package club.heiqi.qz_uilib.fontsystem;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.eventbus.HandlerWrapper;
import club.heiqi.qz_uilib.eventbus.QZEventBus;
import club.heiqi.qz_uilib.fontsystem.event.FontReloadEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class FontManager {
    public static FontManager instance;
    public static FontManager getInstance() {
        synchronized ("创建字体管理器单例锁") {
            if (instance == null) {
                instance = new FontManager((float) (Config.awtCharSize * Config.fontScale));
            }
        }
        return instance;
    }
    public static Logger LOG = LogManager.getLogger();
    public float fontSize;
    /**存储所有可用的awt字体对象*/
    public final LinkedHashSet<Font> fonts = new LinkedHashSet<>();

    public FontManager(float fontSize) {
        this.fontSize = fontSize;

        loadAssetsFontsTTF();
        loadInstalledFontsTTF();
        register();
    }

    /**
     * onReload事件3000优先级为 CharImageGenerator 订阅停止生成任何字符 <br/>
     * 监听重载事件，在 CharImageGenerator 之后 <br/>
     * 重新载入时设置字符大小 <br/>
     */
    public HandlerWrapper onReload = new HandlerWrapper(event -> {
        FontReloadEvent fontReloadEvent = (FontReloadEvent) event;
        float fontSize = (float) (fontReloadEvent.fontSize * Config.fontScale);
        this.fontSize = fontSize;
        // ----- 设置所有字符大小为fontSize -----
        ArrayList<Font> collect = new ArrayList<>();
        for (Font font : fonts) {
            font = font.deriveFont(fontSize);
            collect.add(font);
        }
        fonts.clear();
        fonts.addAll(collect);
        // ----- 设置所有字符大小为fontSize -----
        sortFont();  // 重新排序
    }, 3010);

    public Font findSuitable(int codepoint, int type) {
        for (Font font : fonts) {
            if (type == PageManager.NORMAL && !font.getName().toLowerCase().contains("bold") && checkFontCanDisplay(font, codepoint)) {
                return font;
            }
            if (type == PageManager.BOLD && font.getName().toLowerCase().contains("bold") && checkFontCanDisplay(font, codepoint)) {
                return font;
            }
        }

        // 兜底显示
        for (Font font : fonts) {
            if (font.canDisplay(codepoint)) {
                return font;
            }
        }
        return (Font) fonts.toArray()[0];
    }

    /**
     * 加载资源文件中的字体，放在链表最前面，优先级最高
     */
    public void loadAssetsFontsTTF() {
        File fontDir = new File(System.getProperty("user.dir"), "fonts");
        if (!fontDir.exists() || !fontDir.isDirectory()) {
            boolean mkdirs = fontDir.mkdirs();
        }

        File[] fontFiles = fontDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".ttf") ||
                        name.toLowerCase().endsWith(".otf") ||
                        name.toLowerCase().endsWith(".ttc"));

        if (fontFiles != null) {
            loadTTF(fontFiles);
        }
    }

    /**
     * 加载系统中所有已安装的字体
     */
    public void loadInstalledFontsTTF() {
        // 获取系统图形环境
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

        // 获取所有已安装字体（包括TTF和其他格式）
        Font[] allFonts = ge.getAllFonts();
        ArrayList<Font> result = new ArrayList<>();
        for (Font font : allFonts) {
            font = font.deriveFont(fontSize);
            result.add(font);
        }

        // 筛选TTF字体并存入列表
        fonts.addAll(result);
    }

    public void loadTTF(File[] files) {
        for (File fontFile : files) {
            try {
                Font font = Font.createFont(Font.TRUETYPE_FONT, fontFile);
                font = font.deriveFont(fontSize);
                fonts.add(font);
            } catch (FontFormatException | IOException e) {
                LOG.error(e);
            }
        }
    }

    public boolean checkFontCanDisplay(Font font, int codepoint) {
        if (!font.canDisplay(codepoint)) return false;

        FontRenderContext frc = new FontRenderContext(new AffineTransform(), true, true);
        // 字形信息
        GlyphVector glyphVector = font.createGlyphVector(frc, new String(Character.toChars(codepoint)));

        // 检查字形代码 - 如果为0，通常表示缺失字形
        int glyphCode = glyphVector.getGlyphCode(0);
        if (glyphCode == 0 || glyphCode == font.getMissingGlyphCode()) {
            return false;
        }

        // 检查字形轮廓
        if (glyphVector.getGlyphOutline(0) == null) {
            return false;
        }

        return true;
    }

    /**
     * 尽可能按照 Config.fontSort 数组中定义的顺序对字体进行排序。
     * 排序目标中的字体会排在前面，并按目标顺序排列。
     * 不在目标中的字体会排在后面，并保持相对顺序（稳定排序）。
     */
    public void sortFont() {
        // 如果配置中没有值不进行排序
        if (Config.fontSort.length < 1) return;

        String[] sortTarget = Config.fontSort;
        // 将目标转换为小写
        for (int i = 0; i < sortTarget.length; i++) {
            sortTarget[i] = sortTarget[i].toLowerCase();
        }
        // 待排序数组，排序完后将fonts字段设置为排序后的
        ArrayList<Font> toSort = new ArrayList<>(fonts);

        // 1. 创建一个映射，将目标字体名称映射到它们的期望顺序（索引）
        Map<String, Integer> sortOrder = new HashMap<>();
        for (int i = 0; i < sortTarget.length; i++) {
            // 索引 i + 1 作为排序权重，因为 0 可能会与未在目标中的字体混淆
            sortOrder.put(sortTarget[i], i + 1);
        }

        // 2. 使用自定义 Comparator 对 toSort 列表进行排序
        toSort.sort((font1, font2) -> {
            String name1 = font1.getName().toLowerCase();
            String name2 = font2.getName().toLowerCase();

            // 获取两个字体的排序权重。如果不在 sortOrder 中，则权重设为 0（或一个很大的数）
            // 约定：目标字体权重从 1 开始，非目标字体权重为 Integer.MAX_VALUE
            int weight1 = sortOrder.getOrDefault(name1, Integer.MAX_VALUE);
            int weight2 = sortOrder.getOrDefault(name2, Integer.MAX_VALUE);

            // 比较权重
            if (weight1 != weight2) {
                // 权重较小的（在目标数组中索引靠前的）排在前面
                return Integer.compare(weight1, weight2);
            } else {
                // 如果权重相同 (都属于目标字体，但不在目标数组中，或者都不是目标字体)

                // 特别地：如果它们都是目标字体 (weight1 != Integer.MAX_VALUE)，
                // 它们应该已经在上面的比较中按照目标顺序排列了。
                // 如果它们都不是目标字体 (weight1 == Integer.MAX_VALUE)，
                // 保持它们在原列表中的相对顺序（为了实现稳定排序，可以使用字体名称进行二次排序，
                // 或依赖于 Java 8+ 的 List.sort/Collections.sort 的稳定特性，
                // 但这里我们使用名称作为后备比较）

                // 以字体名称的字典序作为次要排序键，确保排序结果的一致性。
                return name1.compareTo(name2);
            }
        });

        // 3. 更新类中的 fonts 字段为排序后的列表
        this.fonts.clear();
        this.fonts.addAll(toSort);
    }

    /**
     * 订阅所有需要的事件
     */
    public void register() {
        QZEventBus.getInstance().register(FontReloadEvent.class, onReload);
    }
}