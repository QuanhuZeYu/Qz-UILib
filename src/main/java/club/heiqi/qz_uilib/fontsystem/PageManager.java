package club.heiqi.qz_uilib.fontsystem;

import club.heiqi.qz_uilib.Config;
import club.heiqi.qz_uilib.eventbus.HandlerWrapper;
import club.heiqi.qz_uilib.eventbus.QZEventBus;
import club.heiqi.qz_uilib.eventbus.api.EventHandler;
import club.heiqi.qz_uilib.fontsystem.event.FontReloadEvent;
import club.heiqi.qz_uilib.fontsystem.event.GenerateCharEvent;
import club.heiqi.qz_uilib.fontsystem.event.GenerateDoneEvent;
import club.heiqi.qz_uilib.fontsystem.impl.ReplaceFontRender;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import io.netty.util.internal.ConcurrentSet;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.BufferUtils;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**获取字符的唯一入口*/
public class PageManager {
    public static PageManager instance;

    public static PageManager getInstance() {
        if (instance == null) {
            instance = new PageManager((int) (Config.awtCharSize * 64), (int) Config.awtCharSize);
        }
        return instance;
    }

    public static final int NORMAL = 0, BOLD = 1;

    /**存储的纹理集页面*/
    public final HashSet<CharPage>  normalPage = new HashSet<>(),
                                    boldPage = new HashSet<>();
    /**正在生成的字符*/
    public final ConcurrentSet<Integer> normalInGen = new ConcurrentSet<>(),
                                        boldInGen = new ConcurrentSet<>();
    /**确认已经生成完毕的*/
    public final BitSet normalChar = new BitSet(),
                        boldChar = new BitSet();
    /**存储字符和Page的映射*/
    public final Cache<Integer, CharPage> normalCache = CacheBuilder.newBuilder().maximumSize(0xffff).build(),
                                            boldCache = CacheBuilder.newBuilder().maximumSize(0xffff).build();

    public int textureSize, charSize;
    public int maintain = 3;


    public PageManager(int textureSize, int charSize) {
        register();
        this.textureSize = textureSize;
        this.charSize = charSize;
        checkCapacity();
    }

    /**
     * 3000 -> 停止字符生成
     * 3010 -> 重置字体大小和排序
     * 3020 -> 重置字符页
     */
    public HandlerWrapper onReload = new HandlerWrapper((event -> {
        FontReloadEvent fontReloadEvent = (FontReloadEvent) event;
        float fontSize = fontReloadEvent.fontSize;
        int textureSize = (int) (fontSize * 64);
        int charSize = (int) fontSize;

        this.textureSize = textureSize;
        this.charSize = charSize;


        for (CharPage page : normalPage) {
            page.close();
        }
        for (CharPage page : boldPage) {
            page.close();
        }
        normalPage.clear();
        boldPage.clear();

        normalInGen.clear();
        boldInGen.clear();

        normalChar.clear();
        boldChar.clear();

        normalCache.invalidateAll();
        boldCache.invalidateAll();
    }), 3020);

    /**
     * 该操作会创建新的纹理页 重载时需要避免调用
     */
    public void checkCapacity() {
        int canAddCount = 0;
        for (CharPage page : normalPage) {
            if (page.canAddChar()) {
                canAddCount++;
            }
        }
        if (canAddCount < maintain) {
            int toAddCount = maintain - canAddCount;
            for (int i = 0; i < toAddCount; i++) {
                normalPage.add(new CharPage(textureSize, charSize));
            }
        }
        canAddCount = 0;
        for (CharPage page : boldPage) {
            if (page.canAddChar()) {
                canAddCount++;
            }
        }
        if (canAddCount < maintain) {
            int toAddCount = maintain - canAddCount;
            for (int i = 0; i < toAddCount; i++) {
                boldPage.add(new CharPage(textureSize, charSize));
            }
        }
    }

    /**获取Page唯一入口，可以自动处理添加字符*/
    @Nullable
    public CharPage getPage(int codepoint, int type) {
        magicInit();
        if (type == NORMAL) {
            // 确认已经生成
            if (normalChar.get(codepoint)) {
                CharPage page = normalCache.getIfPresent(codepoint);
                if (page != null) return page;
                else {
                    for (CharPage charPage : normalPage) {
                        if (charPage.isCharInPage(codepoint)) {
                            normalCache.put(codepoint, charPage);
                            return charPage;
                        }
                    }
                }
            }
            // 没有生成
            else {
                // 在生成
                if (normalInGen.contains(codepoint)) {
                    return null;
                }
                // 没有生成过的字符
                else {
                    genNormalSignal(codepoint);
                    return null;
                }
            }
        }

        else if (type == BOLD) {
            // 确认已经生成
            if (boldChar.get(codepoint)) {
                CharPage page = boldCache.getIfPresent(codepoint);
                if (page != null) return page;
                else {
                    for (CharPage charPage : boldPage) {
                        if (charPage.isCharInPage(codepoint)) {
                            boldCache.put(codepoint, charPage);
                            return charPage;
                        }
                    }
                }
            }
            // 没有生成
            else {
                // 在生成
                if (boldInGen.contains(codepoint)) {
                    return null;
                }
                // 没有生成过的字符
                else {
                    genBoldSignal(codepoint);
                    return null;
                }
            }
        }
        throw new RuntimeException("不支持的字体类型参数");
    }

    public LinkedBlockingQueue<GenerateDoneEvent> genDoneEvents = new LinkedBlockingQueue<>();
    public HandlerWrapper genDone = new HandlerWrapper(event -> {
        GenerateDoneEvent e = (GenerateDoneEvent) event;
        genDoneEvents.add(e);
    });
    @SubscribeEvent
    public void onMainThread(TickEvent.RenderTickEvent event) {
        if (event != null) {
            if (event.phase != TickEvent.Phase.END) return;
        }
        while (!genDoneEvents.isEmpty()) {
            checkCapacity();
            GenerateDoneEvent e = genDoneEvents.poll();
            if (e.type == NORMAL) {
                for (CharPage page : normalPage) {
                    if (page.canAddChar()) {
                        page.addChar(getImageByteBuffer(e.image), e.info);
                        normalChar.set(e.codepoint, true);
                        normalInGen.remove(e.codepoint);
                        // 加入缓存
                        normalCache.put(e.codepoint, page);
                        break;
                    }
                }
            }
            else if (e.type == BOLD) {
                for (CharPage page : boldPage) {
                    if (page.canAddChar()) {
                        page.addChar(getImageByteBuffer(e.image), e.info);
                        boldChar.set(e.codepoint, true);
                        boldInGen.remove(e.codepoint);

                        boldCache.put(e.codepoint, page);
                        break;
                    }
                }
            }
        }
    }

    public void genNormalSignal(int codepoint) {
        // 正在重载时禁止生成
        if (ReplaceFontRender.inReload) return;
        normalInGen.add(codepoint);

        GenerateCharEvent event = new GenerateCharEvent(codepoint, NORMAL, charSize);
        QZEventBus.getInstance().post(event);
    }

    public void genBoldSignal(int codepoint) {
        // 正在重载时禁止生成
        if (ReplaceFontRender.inReload) return;
        boldInGen.add(codepoint);

        GenerateCharEvent event = new GenerateCharEvent(codepoint, BOLD, charSize);
        QZEventBus.getInstance().post(event);
    }

    public void register() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        QZEventBus.getInstance().register(GenerateDoneEvent.class, genDone);
        QZEventBus.getInstance().register(FontReloadEvent.class, onReload);
    }

    public void debug_saveImage() {
        for (CharPage page : normalPage) {
            page.debug_saveImage();
        }
        for (CharPage page : boldPage) {
            page.debug_saveImage();
        }
    }


    public static ByteBuffer getImageByteBuffer(BufferedImage image) {
        if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
            // 推荐：如果不是预期的格式，先转换为 TYPE_INT_ARGB 以确保正确的像素布局
            BufferedImage temp = new BufferedImage(
                    image.getWidth(),
                    image.getHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );
            temp.getGraphics().drawImage(image, 0, 0, null);
            image = temp;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // 1. 获取所有像素的 ARGB 整数数组
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        // 2. 创建一个用于存储 RGBA 字节的 ByteBuffer
        // 每个像素 4 个字节 (R, G, B, A)
        final int BYTES_PER_PIXEL = 4;
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * BYTES_PER_PIXEL);

        // 3. 遍历像素数组，进行 ARGB -> RGBA 的转换和写入
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 注意：BufferedImage 的 (0,0) 是左上角，而 OpenGL 纹理通常是左下角。
                // 循环时按从上到下、从左到右的顺序读取，如果需要 Y 轴翻转，
                // 可以在此处修改 y 的索引，例如：(height - 1 - y) * width + x
                int pixel = pixels[y * width + x];

                // TYPE_INT_ARGB 整数格式: [A: 24-31位] [R: 16-23位] [G: 8-15位] [B: 0-7位]

                // 提取 R, G, B, A 分量 (使用位运算和 & 0xFF 确保只取 8 位)
                byte alpha = (byte) ((pixel >> 24) & 0xFF);
                byte red = (byte) ((pixel >> 16) & 0xFF);
                byte green = (byte) ((pixel >> 8) & 0xFF);
                byte blue = (byte) ((pixel) & 0xFF);

                // 按照 RGBA 的顺序写入 ByteBuffer
                buffer.put(red);
                buffer.put(green);
                buffer.put(blue);
                buffer.put(alpha);
            }
        }

        // 4. 翻转 Buffer，将其位置 (position) 设置为 0，限制 (limit) 设置为当前位置。
        // 这样 OpenGL 就可以从头开始读取数据。
        buffer.flip();

        return buffer;
    }

    public static void magicInit() {
        ReplaceFontRender.getInstance();
        CharImageGenerator.getInstance();
    }

}
