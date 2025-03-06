package club.heiqi.qz_uilib.fontsystem;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static club.heiqi.qz_uilib.MOD_INFO.LOG;
import static org.lwjgl.opengl.GL11.*;

public class FontManager {
    public static int FONT_SIZE = 100;
    public static int FONT_PIXEL_SIZE = 64;
    /** MC根目录-- `.minecraft` */
    public final File mcDataDir;
    public File fontDir;

    public volatile List<Font> fonts = new ArrayList<>();
    public List<CharPage> pages = new ArrayList<>();
    public Map<Character, CharInfo> highwayCache = new LRUCharCache(); // 高速缓存10000个字符，不常用的自动抛弃

    public FontManager() {
        mcDataDir = Minecraft.getMinecraft().mcDataDir;
        _initFontDir();
        new Thread(() -> {
            _loadDefaultFont();
            _loadSysFont();
        }).start();
    }

    public void _initFontDir() {
        fontDir = new File(mcDataDir, "fonts");
        // 如果fonts文件夹不存在则创建
        if (!fontDir.exists()) {
            if (!fontDir.mkdirs()) {
                LOG.error("创建文件夹失败: {}", fontDir.getAbsolutePath());
            }
        }
    }

    public void _loadDefaultFont() {
        File fontFile;
        // 读取jar包内的字体文件 resources/fonts/霞鹜文楷.ttf
        // 复制到mcDataDir中
        try (InputStream fontStream = getClass().getClassLoader().getResourceAsStream("fonts/霞鹜文楷.ttf")) {
            if (fontStream == null) {
                LOG.error("无法找到字体文件: resources/fonts/霞鹜文楷.ttf");
                return;
            }
            fontFile = new File(fontDir, "霞鹜文楷.ttf");
            if (!fontFile.exists()) {
                Files.copy(fontStream, fontFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                LOG.debug("复制字体文件成功: {}", fontFile.getAbsolutePath());
            }
            Font font = _loadTTF(fontFile);
            float fontSize = _calculateFontSize(font);
            font = font.deriveFont(fontSize);
            fonts.add(font); // 该字体为默认第一位
        } catch (IOException e) {
            LOG.error("复制字体文件失败", e);
            throw new RuntimeException(e);
        } catch (FontFormatException e) {
            throw new RuntimeException(e);
        }
    }

    public void _loadSysFont() {
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
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try {
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String fileName = file.getFileName().toString().toLowerCase();
                        if (fileName.endsWith(".ttf")) {
                            File targetFile = new File(file.toString());
                            try (InputStream is = new FileInputStream(targetFile)) {
                                Font font = Font.createFont(Font.TRUETYPE_FONT, is);
                                float fontSize = _calculateFontSize(font);
                                font = font.deriveFont(fontSize);
                                fonts.add(font);
                            } catch (IOException e) {
                                LOG.error("加载 {} 时IO出错", targetFile.getAbsoluteFile());
                            } catch (FontFormatException e) {
                                LOG.error("字体 {} 格式不支持", targetFile.getName());
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        LOG.error("访问文件失败: {}", file, exc);
                        return FileVisitResult.CONTINUE;
                    }

                });
            } catch (IOException e) {
                LOG.error("遍历目录失败: {}", dir, e);
            }
        }
    }

    public Font _loadTTF(File fontFile) throws IOException, FontFormatException {
        try (InputStream is = new FileInputStream(fontFile)) {
            Font font = Font.createFont(Font.TRUETYPE_FONT, is);
            float fontSize = _calculateFontSize(font);
            font = font.deriveFont(fontSize);
            return font;
        } catch (FileNotFoundException e) {
            LOG.error("未找到字体文件: {}", fontFile.getAbsoluteFile());
            throw e;
        } catch (IOException e) {
            LOG.error("加载 {} 时出错", fontFile.getAbsoluteFile());
            throw e;
        } catch (FontFormatException e) {
            LOG.error("字体: {} 格式错误", fontFile.getAbsoluteFile());
            throw e;
        }
    }

    public int _calculateFontSize(Font font) {
        font = font.deriveFont((float)FONT_SIZE);

        int targetPixelSize = (int) (FONT_PIXEL_SIZE - ((FONT_PIXEL_SIZE)*0.45));
        int targetFontSize = FONT_SIZE;
        char sampleChar = '国';
        // 创建图像和绘图上下文
        BufferedImage i = new BufferedImage(FONT_PIXEL_SIZE, FONT_PIXEL_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = i.createGraphics();
        // 设置渲染参数（抗锯齿）
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setFont(font);
        // 获取字体尺寸前先绘制字符
        FontRenderContext frc = g2d.getFontRenderContext();
        TextLayout layout = new TextLayout(String.valueOf(sampleChar), font, frc);
        Rectangle2D bounds = layout.getBounds();
        int w = (int) bounds.getWidth(), h = (int) bounds.getHeight();
        g2d.dispose(); // 必须释放资源，否则绘制不会生效
        // 循环调整字体大小直到符合条件
        while (w > targetPixelSize || h > targetPixelSize) {
            targetFontSize--;
            font = font.deriveFont((float) targetFontSize);
            // 重新绘制字符到新图像
            i = new BufferedImage(FONT_PIXEL_SIZE, FONT_PIXEL_SIZE, BufferedImage.TYPE_INT_ARGB);
            g2d = i.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setFont(font);
            // 获取实际尺寸
            bounds = new TextLayout(String.valueOf(sampleChar), font, g2d.getFontRenderContext()).getBounds();
            w = (int) bounds.getWidth();
            h = (int) bounds.getHeight();
            // 计算绘制起点坐标
            int x = (int) (((double) (FONT_PIXEL_SIZE - w) /2)-bounds.getX());
            int y = (int) (((double) (FONT_PIXEL_SIZE - h) /2)-bounds.getY());
            g2d.drawString(String.valueOf(sampleChar), x, y);
            g2d.dispose();
            /*  if (w <= targetPixelSize && h <= targetPixelSize) { // DEBUG方法
                _saveImage(i);
            }   */
        }
        return targetFontSize;
    }

    public Font findFont(Character c) {
        for (Font font : fonts) {
            if (!font.canDisplay(c)) continue; // 跳过不支持的Font
            return font;
        }
        return fonts.get(0); // 没有找到合适的返回第一个
    }

    /**
     * 自动创建所需的纹理页和字符贴图
     * @param c 字符
     * @return 字符所对应的页
     */
    public CharPage findPage(Character c) {
        // 先从高速缓存找
        Object page = highwayCache.get(c);
        if (page != null) {
            page = ((CharInfo) page).page;
        } else {
            // 高速缓存如果没有再遍历已有的页
            for (CharPage p : pages) {
                if (p.findChar(c)) page = p;
            }
            // 如果已有的页也没有
            if (page == null) {
                // 遍历页尝试添加字符
                for (CharPage pa : pages) {
                    if (!pa.addChar(findFont(c), c)) continue;
                    page = pa;
                    break;
                }
                // 已有页都放不下
                if (page == null) {
                    page = new CharPage(); pages.add((CharPage) page);
                    ((CharPage) page).addChar(findFont(c), c);
                }
            }
        }
        return (CharPage) page;
    }

    public float renderCharAt(Character c, double x, double y) {
        CharPage page = findPage(c);
        CharInfo ci = page.getCharInfo(c);
        // 得到了对应Page后 1.绑定纹理
        glBindTexture(GL_TEXTURE_2D, page.textureID);
        glBegin(GL_QUADS);
        // 左上角
        glTexCoord2d(ci.getU1(), ci.getV1());
        glVertex3d(x, y, 0);
        // 左下角
        glTexCoord2d(ci.getU1(), ci.getV2());
        glVertex3d(x, y+8, 0);
        // 右下角
        glTexCoord2d(ci.getU2(), ci.getV2());
        glVertex3d(x+8, y+8, 0);
        // 右上角
        glTexCoord2d(ci.getU2(), ci.getV1());
        glVertex3d(x+8, y, 0);
        glEnd();
        // 绘制红色外边框
        glDisable(GL_TEXTURE_2D);
        glColor3d(1, 0, 0);
        glBegin(GL_LINE_LOOP);
        glVertex3d(x, y, 0f);
        glVertex3d(x + 8, y, 0f);
        glVertex3d(x + 8, y + 8, 0f);
        glVertex3d(x, y + 8, 0f);
        glEnd();
        glEnable(GL_TEXTURE_2D);
        return 8f;
    }

    // 记录上一次执行时间
    private long lastSaveTime = 0;
    public ThreadPoolExecutor pool = new ThreadPoolExecutor(
        4,
        4,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingDeque<>(8),
        Executors.defaultThreadFactory(),
        new ThreadPoolExecutor.DiscardPolicy()
    );
    @SubscribeEvent
    public void clientTick(TickEvent.RenderTickEvent event) {
        long currentTime = System.currentTimeMillis();
        // 每隔10秒执行一次
        if (currentTime - lastSaveTime >= 10_000) {
            lastSaveTime = currentTime;
            for (CharPage page : pages) {
                pool.execute(() -> page._saveImage(pages.indexOf(page)+".png"));
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
            for (Font font : fonts) {
                LOG.debug("已加载字体: {}", font.getName());
            }
        }
    }
    public void _registerCommon(
        @Nullable FMLPreInitializationEvent pre,
        @Nullable FMLInitializationEvent init,
        @Nullable FMLPostInitializationEvent post) {
        if (pre != null) {
        }
    }
}
