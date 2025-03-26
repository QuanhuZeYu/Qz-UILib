package club.heiqi.skija.font;

import club.heiqi.qz_blockinfo.ConstField;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Typeface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public class FontLoader {
    public static Logger LOG = LogManager.getLogger();
    public static float FONT_SCALE = 0.9f;
    public static float FONT_SIZE = 12*FONT_SCALE;
    public static List<Font> fonts = new ArrayList<>();
    public static File fontDir;
    public static boolean loaded = false;

    public static void load() {
        if (loaded) return;
        File mcDir = ConstField.MC_DIR;
        fontDir = new File(mcDir, "fonts");
        if (!fontDir.exists()) fontDir.mkdirs();
        _loadDefault();
        _loadSystemFonts();
        loaded = true;
    }

    public static void _loadDefault() {
        _loadResourceFont("LXGWWenKai-Regular.ttf");
        _loadResourceFont("seguiemj.ttf");
        _loadTTF(fontDir.toPath());
    }

    public static void _loadResourceFont(String name) {
        try (InputStream is = FontLoader.class.getClassLoader().getResourceAsStream("fonts/"+name)) {
            if (is == null) throw new IOException();
            File fontFile = new File(fontDir, name);
            if (fontFile.exists()) return;
            Files.copy(is, fontFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("载入 {} 时出现错误 {}", name, e);
        }
    }

    public static void _loadSystemFonts() {
        String os = System.getProperty("os.name").toLowerCase();
        List<Path> systemFontDirs = new ArrayList<>();
        // 根据操作系统设置字体目录
        if (os.contains("win")) {
            String systemRoot = System.getenv("SystemRoot");
            if (systemRoot != null) {
                systemFontDirs.add(Paths.get(systemRoot, "Fonts"));
            }
        } else if (os.contains("mac")) {
            systemFontDirs.add(Paths.get("/Library/Fonts"));
            systemFontDirs.add(Paths.get(System.getProperty("user.home"), "Library/Fonts"));
            systemFontDirs.add(Paths.get("/System/Library/Fonts"));
        } else if (os.contains("nux") || os.contains("nix") || os.contains("aix")) {
            systemFontDirs.add(Paths.get("/usr/share/fonts"));
            systemFontDirs.add(Paths.get(System.getProperty("user.home"), ".fonts"));
            systemFontDirs.add(Paths.get("/usr/local/share/fonts"));
        } else {
            LOG.error("不支持的操作系统: {}", os);
            return;
        }
        for (Path dir : systemFontDirs) {
            _loadTTF(dir);
        }
    }

    public static void _loadTTF(Path dir) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString().toLowerCase();
                    if (!fileName.endsWith(".ttf")) return FileVisitResult.CONTINUE;
                    try {
                        LOG.debug("正在尝试加载: {}", file.toString());
                        try (InputStream is = new FileInputStream(file.toFile())) {
                            java.awt.Font fontAWT = java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, is); // 使用java awt加载引发错误来跳过无法加载的字体
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        Typeface tf = Typeface.makeFromFile(file.toString());
                        Font font = new Font(tf, FONT_SIZE);
                        fonts.add(font);
                    } catch (Exception e) {
                        LOG.error("无法加载:{}", file.getFileName().toString());
                    }
                    return FileVisitResult.CONTINUE;
                }

            });
        } catch (IOException e) {
            LOG.error("遍历文件夹: {} 出错。 {}", dir, e);
        }
    }

    public static Font getDefaultFont() {
        load();
        return fonts.get(0);
    }
}
