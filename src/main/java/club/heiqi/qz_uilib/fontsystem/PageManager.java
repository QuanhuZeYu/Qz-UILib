package club.heiqi.qz_uilib.fontsystem;

import club.heiqi.qz_uilib.Config;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.util.internal.ConcurrentSet;

import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.HashSet;
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

    public void reload(int textureSize, int charSize) {
        this.textureSize = textureSize;
        this.charSize = charSize;


        for (CharPage page : normalPage) {
            page.dispose();
        }
        for (CharPage page : boldPage) {
            page.dispose();
        }
        normalPage.clear();
        boldPage.clear();

        normalInGen.clear();
        boldInGen.clear();

        normalChar.clear();
        boldChar.clear();

        normalCache.invalidateAll();
        boldCache.invalidateAll();

    }


    public PageManager(int textureSize, int charSize) {
        this.textureSize = textureSize;
        this.charSize = charSize;
        checkCapacity();
    }

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

    public void genNormalSignal(int codepoint) {
        normalInGen.add(codepoint);
        // 该consumer会在主线程中反复尝试寻找可添加页面，直到找到可添加的页面后运行该回调
        Consumer<CharImageGenerator.ImageAndInfo> consumer = (iai) -> {
            checkCapacity();  // 添加前检查容量
            for (CharPage page : normalPage) {
                if (page.canAddChar()) {
                    page.addChar(iai.getImageByteBuffer(), iai.info);
                    genDoneNormal(codepoint, page);
                    return;
                }
            }
        };
        CharImageGenerator.getInstance().generateAsync(codepoint, NORMAL, charSize, consumer);
    }

    public void genDoneNormal(int codepoint, CharPage page) {
        normalChar.set(codepoint, true);
        normalInGen.remove(codepoint);
        // 加入缓存
        normalCache.put(codepoint, page);
    }

    public void genBoldSignal(int codepoint) {
        boldInGen.add(codepoint);
        // 该consumer会在主线程中反复尝试寻找可添加页面，直到找到可添加的页面后运行该回调
        Consumer<CharImageGenerator.ImageAndInfo> consumer = (iai) -> {
            checkCapacity();  // 添加前检查容量
            for (CharPage page : boldPage) {
                if (page.canAddChar()) {
                    page.addChar(iai.getImageByteBuffer(), iai.info);
                    genDoneBold(codepoint, page);
                    return;
                }
            }
        };
        CharImageGenerator.getInstance().generateAsync(codepoint, BOLD, charSize, consumer);
    }

    public void genDoneBold(int codepoint, CharPage page) {
        boldChar.set(codepoint, true);
        boldInGen.remove(codepoint);
        // 加入缓存
        boldCache.put(codepoint, page);
    }

    public void debug_saveImage() {
        for (CharPage page : normalPage) {
            page.debug_saveImage();
        }
        for (CharPage page : boldPage) {
            page.debug_saveImage();
        }
    }
}
