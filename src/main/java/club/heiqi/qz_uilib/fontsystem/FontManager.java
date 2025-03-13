package club.heiqi.qz_uilib.fontsystem;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
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
    public static List<String> GLOBAL_REGULAR_CACHE = new CopyOnWriteArrayList<>();
    public static List<String> GLOBAL_BOLD_CACHE = new CopyOnWriteArrayList<>();
    public final StyleRecorder style = new StyleRecorder();
    /** MC根目录-- `.minecraft` */
    public final File mcDataDir;
    public final File fontDir;

    public final List<Font> fonts = new CopyOnWriteArrayList<>();
    public final List<CharPage> regularPages = new CopyOnWriteArrayList<>();
    public final List<CharPage> boldPages = new CopyOnWriteArrayList<>();
    public final Map<String, CharPage> regularCache = new LRUCharCache(); // 高速缓存10000个字符，不常用的自动抛弃
    public final Map<String, CharPage> boldCache = new LRUCharCache();

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

    public volatile boolean genDone = false;
    public void _genPreTexture() {
        List<UnicodeRecorder.UnicodeType> types = Arrays.asList(
            UnicodeRecorder.UnicodeType.BASIC_LATIN,
            UnicodeRecorder.UnicodeType.LATIN1_SUPPLEMENT,
            UnicodeRecorder.UnicodeType.GENERAL_PUNCTUATION,
            UnicodeRecorder.UnicodeType.CJK_UNIFIED_IDEOGRAPHS,
            UnicodeRecorder.UnicodeType.EMOTICONS
        );
        Iterator<UnicodeRecorder.UnicodeType> it = types.iterator();
        while (it.hasNext()) {
            UnicodeRecorder.UnicodeType type = it.next();
            int start = type.start;
            int end = type.end;
            // 多线程生成纹理贴图
            List<Integer> codepoints = new ArrayList<>();
            for (int i = start; i <= end; i++) {
                if (!Character.isValidCodePoint(i)) continue;
                codepoints.add(i);
                if (codepoints.size() == CharPage.MAX_CHARS) {
                    CharPage page = new CharPage(this);
                    List<Integer> finalCodepoints = codepoints;
                    FutureTask<?> future = new FutureTask<>(() -> {
                        page.addChars(finalCodepoints);
                        LOG.debug("字符页 字符{}个 添加完毕", finalCodepoints.size());
                        regularPages.add(page);
                        LOG.debug("当前页已放入{}位索引", regularPages.indexOf(page));
                        return null;
                    });
                    new Thread(future).start();
                    futures.add(future);
                    codepoints = new ArrayList<>();
                }
            }
            if (!codepoints.isEmpty()) {
                CharPage page = new CharPage(this);
                List<Integer> finalCodepoints = codepoints;
                FutureTask<?> future = new FutureTask<>(() -> {
                    page.addChars(finalCodepoints);
                    LOG.debug("字符页 字符{}个 添加完毕", finalCodepoints.size());
                    regularPages.add(page);
                    LOG.debug("当前页已放入{}位索引", regularPages.indexOf(page));
                    return null;
                });
                new Thread(future).start();
                futures.add(future);
            }
        }
        waitAddDone();
        for (CharPage page : regularPages) {
            new Thread(() -> page.savePNG(String.valueOf(regularPages.indexOf(page)))).start();
            page.uploadGPU();
        }
        LOG.debug("已上传至GPU");
        genDone = true;
    }

    public Font findValidFont(int codepoint) {
        for (Font font : fonts) {
            if (font.getUTF32Glyph(codepoint) != 0) return font;
        }
        return fonts.get(0);
    }
    @NotNull
    public Font findValidBoldFont(int codepoint) {
        Font firstFont = null;
        for (Font font : fonts) {
            if (Objects.requireNonNull(font.getTypeface()).isBold()) if (font.getUTF32Glyph(codepoint) != 0) return font;
            if (firstFont == null) if (font.getUTF32Glyph(codepoint) != 0) firstFont = font;
        }
        return firstFont != null
            ? firstFont
            : fonts.get(0);
    }

    @Nullable
    public CharPage findRegularPage(String t) {
        for (CharPage page : regularPages) {
            if (page.cache.containsKey(t)) return page;
        }
        return null; // 没找到
    }
    @Nullable
    public CharPage findBoldPage(String t) {
        for (CharPage page : boldPages) {
            if (page.cache.containsKey(t)) return page;
        }
        return null;
    }

    @NotNull
    public CharPage findPageCanAdd() {
        for (CharPage page : regularPages) {
            if (page.canAdd()) return page;
        }
        CharPage page = new CharPage(this);
        regularPages.add(page);
        return page;
    }
    @NotNull
    public CharPage findPageCanAddBold() {
        for (CharPage page : boldPages) {
            if (page.canAdd()) return page;
        }
        CharPage page = new CharPage(this);
        boldPages.add(page);
        return page;
    }

    @Nullable
    public CharPage findPageAutoAdd(String t) {
        CharPage page = regularCache.get(t);
        if (page == null) {
            if (GLOBAL_REGULAR_CACHE.contains(t)) return findRegularPage(t);
            int codepoint = Character.codePointAt(t, 0);
            page = addChar(codepoint); // autoAdd
        }
        return page;
    }
    public CharPage findPageAutoAddBold(String t) {
        CharPage page = boldCache.get(t);
        if (page == null) {
            if (GLOBAL_BOLD_CACHE.contains(t)) return findBoldPage(t);
            int codepoint = Character.codePointAt(t, 0);
            page = addCharBold(codepoint); // autoAdd
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
        if (GLOBAL_REGULAR_CACHE.contains(t)) return null;
        GLOBAL_REGULAR_CACHE.add(t);
        Font font = findValidFont(codepoint);
        CharPage page = findPageCanAdd(); // 返回的page一定是没有竞态的纹理页
        Future<?> future = null;
        try {
            future = pool.submit(() -> {
                if (!page.addChar(t,font)) {
                    LOG.error("{} 生成失败", t);
                    GLOBAL_REGULAR_CACHE.remove(t);
                }
            });
        } catch (Exception e) {
            LOG.error("添加任务时出现错误!");
            GLOBAL_REGULAR_CACHE.remove(t);
        }
        futures.add(future);
        return page;
    }
    public CharPage addCharBold(int codepoint) {
        if (!Character.isValidCodePoint(codepoint)) {
            LOG.error("无效代码点: {}", codepoint);
            return null;
        }
        char[] chars = Character.toChars(codepoint);
        String t = new String(chars);
        if (GLOBAL_BOLD_CACHE.contains(t)) return null;
        GLOBAL_BOLD_CACHE.add(t);
        Font font = findValidBoldFont(codepoint);
        CharPage page = findPageCanAddBold(); // 返回的page一定是没有竞态的纹理页
        Future<?> future = null;
        try {
            future = pool.submit(() -> {
                if (!page.addChar(t,font)) {
                    LOG.error("{} 生成失败", t);
                    GLOBAL_REGULAR_CACHE.remove(t);
                }
            });
        } catch (Exception e) {
            LOG.error("添加任务时出现错误!");
            GLOBAL_REGULAR_CACHE.remove(t);
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

    public float renderCharAt(@NotNull String t, double x, double y, double z, float size) {
        // 1. 空格字符直接返回默认宽度
        if (t.equals(" ")) {
            return size / 2;
        }
        // 2. 根据粗体标志选择字体页
        CharPage page = style.isBold ? findPageAutoAddBold(t) : findPageAutoAdd(t);
        if (page == null) {
            LOG.warn("未找到支持字符 '{}' 的字体页", t);
            return size / 2;
        }
        // 3. 获取字符信息
        CharInfo info = page.getCharInfo(t);
        if (info == null) {
            LOG.warn("字符 '{}' 在字体页中无元数据", t);
            return size / 2;
        }
        // 4. 计算动态字间距（粗体额外增加间距）
        float baseSpacing = (size / 16) * 2.5f;
        float letterSpacing = style.isBold ? baseSpacing + (size / 16) : baseSpacing;
        // 5. 渲染字符
        page.renderCharAt(t, x, y, z, size);
        // 6. 缓存字符页（优化后续查找性能）
        Map<String, CharPage> targetCache = style.isBold ? boldCache : regularCache;
        targetCache.put(t, page);
        // 7. 计算实际渲染宽度：字符宽度 + 动态间距
        float charWidth = (info.right - info.left) / CharPage.GRID_SIZE * size;
        return charWidth + letterSpacing;
    }

    public void tryUpload() {
        lastSaveTime = System.currentTimeMillis();
        for (CharPage page : regularPages) {
            if (page.isDirty.get()) page.uploadGPU();
        }
        for (CharPage page : boldPages) {
            if (page.isDirty.get()) page.uploadGPU();
        }
    }

    public float getCharWidth(String t) {
        float size = 8f;
        if (t.equals(" ")) return size / 2;
        CharPage page = findPageAutoAdd(t);
        // 1.根据粗体标志选择字体页
        if (page == null) {
            LOG.warn("未找到支持字符 '{}' 的字体页", t);
            return size / 2;
        }
        // 2. 获取字符信息
        CharInfo info = page.getCharInfo(t);
        if (info == null) {
            LOG.warn("字符 '{}' 在字体页中无元数据", t);
            return size / 2;
        }
        float width = info.right - info.left;
        return (width / CharPage.GRID_SIZE) * size;
    }

    // 记录上一次执行时间
    private long lastSaveTime = 0;
    private long savePngTime = 0;
    @SubscribeEvent
    public void clientTick(TickEvent.RenderTickEvent event) {
        // 每隔10秒执行一次
        if (System.currentTimeMillis() - lastSaveTime >= 1_000) tryUpload();
        if (System.currentTimeMillis() - savePngTime > 10_000) {
            savePngTime = System.currentTimeMillis();
//            for (CharPage page : regularPages) {
//                new Thread(() -> {
//                    page.savePNG(String.valueOf(regularPages.indexOf(page)));
//                }).start();
//            }
//            for (CharPage page : boldPages) {
//                new Thread(() -> {
//                    page.savePNG(boldPages.indexOf(page)+"-bold");
//                }).start();
//            }
        }
    }
    @SubscribeEvent
    public void onLogOutWorld(PlayerEvent.PlayerLoggedOutEvent event) {
        pool.shutdownNow();
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







    public static class StyleRecorder {
        public boolean isBold = false;
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
