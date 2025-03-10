package club.heiqi.qz_uilib.fontsystem;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.Typeface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class FontManager {
    public static Logger LOG = LogManager.getLogger();
    public static float FONT_SCALE = 0.9f;
    public static float FONT_SIZE = 64*FONT_SCALE;
    /** MC根目录-- `.minecraft` */
    public final File mcDataDir;
    public final File fontDir;

    public final List<Font> fonts = new CopyOnWriteArrayList<>();
    public final List<CharPage> pages = new CopyOnWriteArrayList<>();
    public static List<String> GLOBAL_CACHE = new CopyOnWriteArrayList<>();
    public final Map<String, CharPage> highwayCache = new LRUCharCache(); // 高速缓存10000个字符，不常用的自动抛弃

    public FontManager() {
        mcDataDir = new File(System.getProperty("user.dir"));
        fontDir = new File(mcDataDir, "fonts");
        if (!fontDir.exists()) fontDir.mkdirs();
        _loadDefault();
        _loadSystemFonts();
        _genPreTexture();
    }

    public void _loadDefault() {
        _loadResourceFont("LXGWWenKai-Regular.ttf");
        _loadResourceFont("seguiemj.ttf");
        _loadTTF(fontDir.toPath());
    }

    public void _loadResourceFont(String name) {
        try (InputStream is = FontManager.class.getClassLoader().getResourceAsStream("fonts/"+name)) {
            if (is == null) throw new IOException();
            File fontFile = new File(fontDir, name);
            if (fontFile.exists()) return;
            Files.copy(is, fontFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("载入 {} 时出现错误 {}", name, e);
        }
    }

    public void _loadSystemFonts() {
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

    public void _loadTTF(Path dir) {
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
                        io.github.humbleui.skija.Font font = new io.github.humbleui.skija.Font(tf, FONT_SIZE);
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

    public Font findValidFont(int codepoint) {
        for (Font font : fonts) {
            if (font.getUTF32Glyph(codepoint) != 0) return font;
        }
        return fonts.get(0);
    }

    @NotNull
    public CharPage findPageCanAdd() {
        for (CharPage page : pages) {
            if (page.canAdd()) return page;
        }
        CharPage page = new CharPage(this);
        pages.add(page);
        return page;
    }

    @Nullable
    public CharPage findPage(String t) {
        CharPage page = highwayCache.get(t);
        if (page == null) {
            for (CharPage p : pages) {
                if (p.cache.containsKey(t)) {
                    return p;
                }
            }
        }
        return null;
    }

    @Nullable
    public CharPage findPageAndAdd(String t) {
        CharPage page = highwayCache.get(t);
        if (page == null) {
            for (CharPage p : pages) {
                if (p.cache.containsKey(t)) {
                    return p;
                }
            }
            int codepoint = Character.codePointAt(t.toCharArray(), 0);
            page = addChar(codepoint);
        }
        return page;
    }

    ExecutorService pool = Executors.newFixedThreadPool(2, new DaemonThreadFactory());
    public List<Future<?>> futures = new CopyOnWriteArrayList<>();
    public CharPage addChar(int codepoint) {
        if (!Character.isValidCodePoint(codepoint)) {
            LOG.error("无效代码点: {}", codepoint);
            return null;
        }
        char[] chars = Character.toChars(codepoint);
        String t = new String(chars);
        if (GLOBAL_CACHE.contains(t)) return null;
        GLOBAL_CACHE.add(t);
        Font font = findValidFont(codepoint);
        CharPage page = findPageCanAdd(); // 返回的page一定是没有竞态的纹理页
        Future<?> future = null;
        try {
            LOG.debug("正在提交 {} 的字符生成任务", t);
            future = pool.submit(() -> {
                LOG.debug("已提交 {} 的生成任务", t);
                if (!page.addChar(t,font)) {
                    LOG.error("{} 生成失败", t);
                    GLOBAL_CACHE.remove(t);
                }
            });
        } catch (Exception e) {
            LOG.error("添加任务时出现错误!");
            GLOBAL_CACHE.remove(t);
        }
        futures.add(future);
        return page;
    }
    public void waitAddDone() {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("等待过程中报错: {}", e);
            }
        }
    }

    public volatile boolean genDone = false;
    public void _genPreTexture() {
        List<Integer> preL = Arrays.asList(
            (Integer) UnicodeRecorder.CATE.get(UnicodeRecorder.UnicodeType.英文.类型名).get(0),
            (Integer) UnicodeRecorder.CATE.get(UnicodeRecorder.UnicodeType.英文.类型名).get(1),
            (Integer) UnicodeRecorder.CATE.get(UnicodeRecorder.UnicodeType.英文扩展.类型名).get(0),
            (Integer) UnicodeRecorder.CATE.get(UnicodeRecorder.UnicodeType.英文扩展.类型名).get(1),
            (Integer) UnicodeRecorder.CATE.get(UnicodeRecorder.UnicodeType.中文Unicode.类型名).get(0),
            (Integer) UnicodeRecorder.CATE.get(UnicodeRecorder.UnicodeType.中文Unicode.类型名).get(1),
            (Integer) UnicodeRecorder.CATE.get(UnicodeRecorder.UnicodeType.表情符号.类型名).get(0),
            (Integer) UnicodeRecorder.CATE.get(UnicodeRecorder.UnicodeType.表情符号.类型名).get(1),
            (Integer) UnicodeRecorder.CATE.get(UnicodeRecorder.UnicodeType.扩展表情符号.类型名).get(0),
            (Integer) UnicodeRecorder.CATE.get(UnicodeRecorder.UnicodeType.扩展表情符号.类型名).get(1)
        );
        Iterator<Integer> it = preL.iterator();
        while (it.hasNext()) {
            int start = it.next();
            int end = it.next();
            // 多线程生成纹理贴图
            List<Integer> codepoints = new ArrayList<>();
            for (int i = start; i <= end; i++) {
                if (!Character.isValidCodePoint(i)) continue;
                codepoints.add(i);
                if (codepoints.size() == CharPage.MAX_CHARS) {
                    CharPage page = new CharPage(this);
                    List<Integer> finalCodepoints = codepoints;
                    Future<?> future = pool.submit(() -> {
                        page.addChars(finalCodepoints);
                        pages.add(page);
                    });
                    futures.add(future);
                    codepoints = new ArrayList<>();
                }
            }
            if (!codepoints.isEmpty()) {
                CharPage page = new CharPage(this);
                List<Integer> finalCodepoints = codepoints;
                Future<?> future = pool.submit(() -> {
                    page.addChars(finalCodepoints);
                    pages.add(page);
                });
                futures.add(future);
            }
        }
        waitAddDone();
        for (CharPage page : pages) {
            page.savePNG(String.valueOf(pages.indexOf(page)));
            page.uploadGPU();
        }
        genDone = true;
    }

    public float renderCharAt(String t, double x, double y, double z, float size) {
        CharPage page = findPageAndAdd(t);
        if (page == null) return 4f;
        CharInfo info = page.getCharInfo(t);
        if (info == null) return 4f;
        page.renderCharAt(t, x, y, z, size);
        highwayCache.put(t, page);
        return ((info.width/ CharPage.GRID_SIZE)*8f)+2f;
    }

    // 记录上一次执行时间
    private long lastSaveTime = 0;
    private long savePngTime = 0;
    @SubscribeEvent
    public void clientTick(TickEvent.RenderTickEvent event) {
        long currentTime = System.currentTimeMillis();
        // 每隔10秒执行一次
        if (currentTime - lastSaveTime >= 1_000) {
            lastSaveTime = currentTime;
            for (CharPage page : pages) {
                if (page.isDirty.get()) page.uploadGPU();
            }
        }
        if (System.currentTimeMillis() - savePngTime > 10_000) {
            savePngTime = System.currentTimeMillis();
            for (CharPage page : pages) {
                pool.execute(() -> {
                    page.savePNG(String.valueOf(pages.indexOf(page)));
                });
            }
        }
    }
    public void _registerClient(
        @Nullable FMLPreInitializationEvent pre,
        @Nullable FMLInitializationEvent init,
        @Nullable FMLPostInitializationEvent post) {
        if (pre != null) {
            FMLCommonHandler.instance().bus().register(this);
        }
        if (init != null) {
        }
        if (post != null) {
        }
    }
    public void _registerCommon(
        @Nullable FMLPreInitializationEvent pre,
        @Nullable FMLInitializationEvent init,
        @Nullable FMLPostInitializationEvent post) {
        if (pre != null) {
        }
    }







    public static class DaemonThreadFactory implements ThreadFactory {
        private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread thread = defaultFactory.newThread(r);
            thread.setDaemon(true); // 设置为守护线程
            return thread;
        }
    }
}
