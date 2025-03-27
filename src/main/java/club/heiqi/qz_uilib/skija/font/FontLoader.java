package club.heiqi.qz_uilib.skija.font;

import club.heiqi.qz_uilib.ConstField;
import club.heiqi.qz_uilib.skija.state.SkiaStore;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Typeface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL33;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FontLoader {
    public static Logger LOG = LogManager.getLogger();
    public static float FONT_SIZE = 24;
    public static List<Font> fonts = new ArrayList<>();
    public static File fontDir;
    public static boolean loaded = false;
    public static SkiaStore skiaStore = new SkiaStore();

    public static void load() {
        if (loaded) return;
        _backupGLState();

        File mcDir = ConstField.MC_DIR;
        fontDir = new File(mcDir, "fonts");
        if (!fontDir.exists()) fontDir.mkdirs();
        _loadDefault();
        _loadSystemFonts();
        loaded = true;

        _restoreGLState();
    }

    public void setFontSize(float fontSize) {
        for (Font font : fonts) {
            font.setSize(fontSize);
        }
    }

    public static Font getDefaultFont() {

        load();

        return fonts.get(0);
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
                        LOG.info("正在尝试加载: {}", file.toString());
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

    // 保存原始状态的类
    static class SamplerState {
        public int activeTextureUnit;
        public int oldSampler;
        public HashMap<Integer, Integer> intParams = new HashMap<>();
        public HashMap<Integer, Float> floatParams = new HashMap<>();
    }

    public static SamplerState samplerState = new SamplerState();
    public static void _backupGLState() {
        skiaStore.backup();
    }
    public static void _restoreGLState() {
        skiaStore.restore();
    }
}
