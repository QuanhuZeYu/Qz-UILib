package club.heiqi.qz_uilib.fontsystem;

import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.List;

import static club.heiqi.qz_uilib.MOD_INFO.LOG;
import static club.heiqi.qz_uilib.fontsystem.FontTexturePage.FONT_PIXEL_SIZE;

public class FontManager {
    public static int FONT_SIZE = 100;
    public static File outPutDir;
    private final File mcDataDir;
    public File fontDir;
    public List<File> fontList = new ArrayList<>();
    public List<Font> fonts = new ArrayList<>();
    public Font select;

    public List<FontTexturePage> pages = new ArrayList<>();

    public FontManager() {
        mcDataDir = Minecraft.getMinecraft().mcDataDir;
        outPutDir = new File(mcDataDir, "图像输出");
        _loadDefaultFont();
        _loadSysFont();
        LOG.debug("字体文件列表: {}", fontList);
        select = fonts.get(0);
        FONT_SIZE = _calculateFontSize(select);
        LOG.debug("计算合适字体尺寸为 {}", FONT_SIZE);
        _genPage();
    }

    // ==================== 方法组：载入字体 ====================
    public void _loadDefaultFont() {
        fontDir = new File(mcDataDir, "fonts");
        File fontFile;
        // 如果fonts文件夹不存在则创建
        if (!fontDir.exists()) {
            if (!fontDir.mkdirs()) {
                LOG.error("创建文件夹失败: {}", fontDir.getAbsolutePath());
            }
        }
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
                fontList.add(fontFile);
            }
            Font font = _loadTTF(fontFile);
            fonts.add(font);
        } catch (IOException e) {
            LOG.error("复制字体文件失败", e);
            throw new RuntimeException(e);
        } catch (FontFormatException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 寻找系统字体并加入fontList
     */
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
                            fontList.add(targetFile);
                            try (InputStream is = new FileInputStream(targetFile)) {
                                Font font = Font.createFont(Font.TRUETYPE_FONT, is);
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
            return Font.createFont(Font.TRUETYPE_FONT, is);
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

    /**
     * 动态调整字体尺寸，直到满足FONT_PIXEL_SIZE
     * @param font 选择的字体
     * @return 满足的字体大小
     */
    public int _calculateFontSize(Font font) {
        font = font.deriveFont((float)FONT_SIZE);

        int targetPixelSize = (int) (FONT_PIXEL_SIZE - ((FONT_PIXEL_SIZE)*0.2));
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

    // ==================== 方法组：图像操作 ====================
    public void _saveImage(BufferedImage image) {
        if (!outPutDir.exists()) {
            if (!outPutDir.mkdirs()) {
                LOG.error("创建目录失败: {}", outPutDir.getAbsoluteFile());
            }
        }
        File imageFile = new File(outPutDir, "tmp.png");
        try {
            ImageIO.write(image, "png", imageFile);
        } catch (IOException e) {
            LOG.error("保存图像失败\n栈输出: ", e);
        }
    }

    // 生成纹理页方法
    public void _genPage() {
        for (Map.Entry<String, List<Integer>> entry : UnicodeRecorder.CATE.entrySet()) {
            String cate = entry.getKey();
            LOG.debug("正在处理 {} 分类", cate);
            int start = entry.getValue().get(0);
            int startCopy = start;
            int end = entry.getValue().get(1);
            int allCount = end - startCopy + 1; // 总共的数量
            int count = (int) Math.ceil((double) allCount / FontTexturePage.CHAR_COUNT);
            // 遍历页数次将字符分页装进纹理图
            for (int i = 0; i < count; i++) {
                int charCount = end - start + 1;
                int startCopy2 = start;
                int cs = Math.min(FontTexturePage.CHAR_COUNT, charCount); // 计算需要装入的数量
                // 为当前页添加字符
                FontTexturePage page = new FontTexturePage();
                for (int j = start; j < startCopy2 + cs; j++) { // 遍历方式为：以开始指针为起点遍历需要装入数量次
                    page.addCharToPage(select, j, FONT_SIZE);
                    start++; // 移动指针，用于下次循环
                }
                String fileName = cate + "_" + i + ".png"; // 文件名为 分类_分页
                page._saveImage(fileName);
                pages.add(page);
            }
        }
    }

    // ==================== 方法组：寻找Page和index ====================
    /**
     * 寻找码点所在的纹理页
     * @param codepoint 码点
     * @return 所在的TexturePage，未找到时会返回null
     */
    public FontTexturePage findPage(int codepoint) {
        for (FontTexturePage page : pages) {
            int start = page.start;
            int end = page.end;
            if (codepoint < start || codepoint > end) continue;
            if (codepoint >= start || codepoint <= end) {
                return page;
            }
        }
        return null; // 没有找到时返回空
    }
}
