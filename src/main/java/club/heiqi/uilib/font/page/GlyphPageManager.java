package club.heiqi.uilib.font.page;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.glyph.GlyphGenerationResult;
import club.heiqi.uilib.font.glyph.GlyphInfo;

/**
 * 字符页管理器。
 */
public class GlyphPageManager {

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<GlyphCacheKey, GlyphState> glyphStates = new ConcurrentHashMap<GlyphCacheKey, GlyphState>();
    private final Map<GlyphCacheKey, GlyphPage> readyPages = new ConcurrentHashMap<GlyphCacheKey, GlyphPage>();
    private final Map<GlyphCacheKey, GlyphInfo> glyphInfos = new ConcurrentHashMap<GlyphCacheKey, GlyphInfo>();
    private final Queue<PendingGlyphUpload> pendingUploads = new ConcurrentLinkedQueue<PendingGlyphUpload>();
    private final List<GlyphPage> normalPages = new ArrayList<GlyphPage>();
    private final List<GlyphPage> boldPages = new ArrayList<GlyphPage>();

    private int textureSize;
    private int glyphSize;
    private int maintainPageCount = 3;

    /**
     * 初始化字符页管理器。
     */
    public void initialize() {
        textureSize = Math.max(64, (int) (FontConfig.awtCharSize * 64));
        glyphSize = Math.max(8, (int) FontConfig.awtCharSize);
        if (initialized.compareAndSet(false, true)) {
            ensureCapacity(FontType.NORMAL);
            ensureCapacity(FontType.BOLD);
        }
    }

    /**
     * 重置字符页状态。
     */
    public void reset() {
        if (!initialized.get()) {
            return;
        }

        closePages(normalPages);
        closePages(boldPages);
        glyphStates.clear();
        readyPages.clear();
        glyphInfos.clear();
        pendingUploads.clear();
        normalPages.clear();
        boldPages.clear();
        textureSize = Math.max(64, (int) (FontConfig.awtCharSize * 64));
        glyphSize = Math.max(8, (int) FontConfig.awtCharSize);
        ensureCapacity(FontType.NORMAL);
        ensureCapacity(FontType.BOLD);
    }

    /**
     * 尝试将字符切换到生成中状态。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 是否允许开始生成
     */
    public boolean tryMarkGenerating(int codepoint, FontType fontType) {
        GlyphCacheKey key = new GlyphCacheKey(codepoint, fontType);
        GlyphState currentState = glyphStates.get(key);
        if (currentState == GlyphState.GENERATING
                || currentState == GlyphState.UPLOAD_PENDING
                || currentState == GlyphState.READY) {
            return false;
        }

        glyphStates.put(key, GlyphState.GENERATING);
        return true;
    }

    /**
     * 标记字符生成失败。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     */
    public void markFailed(int codepoint, FontType fontType) {
        glyphStates.put(new GlyphCacheKey(codepoint, fontType), GlyphState.FAILED);
    }

    /**
     * 接收后台线程生成完成的字符结果。
     *
     * @param result 字符生成结果
     */
    public void queueUpload(GlyphGenerationResult result) {
        GlyphCacheKey key = new GlyphCacheKey(result.getCodepoint(), result.getFontType());
        pendingUploads.add(new PendingGlyphUpload(key, result));
        glyphStates.put(key, GlyphState.UPLOAD_PENDING);
    }

    /**
     * 在主线程侧刷新待上传队列。
     *
     * @param maxCount 本次最多处理的数量
     */
    public void flushPendingUploads(int maxCount) {
        int processed = 0;
        while (processed < maxCount && !pendingUploads.isEmpty()) {
            PendingGlyphUpload upload = pendingUploads.poll();
            if (upload == null) {
                break;
            }

            GlyphCacheKey key = upload.getKey();
            GlyphGenerationResult result = upload.getGenerationResult();
            GlyphPage glyphPage = allocatePage(result.getFontType(), key);
            glyphPage.allocate(key);
            glyphPage.upload(key, result.getImage());
            readyPages.put(key, glyphPage);
            glyphInfos.put(key, upload.getGenerationResult().getGlyphInfo());
            glyphStates.put(key, GlyphState.READY);
            processed++;
        }
    }

    /**
     * 查询字符是否已可用。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 是否可用
     */
    public boolean isReady(int codepoint, FontType fontType) {
        return glyphStates.get(new GlyphCacheKey(codepoint, fontType)) == GlyphState.READY;
    }

    /**
     * 获取字符状态。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 字符状态
     */
    public GlyphState getState(int codepoint, FontType fontType) {
        GlyphCacheKey key = new GlyphCacheKey(codepoint, fontType);
        GlyphState state = glyphStates.get(key);
        return state == null ? GlyphState.NEW : state;
    }

    /**
     * 获取已准备好的字符页。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 字符页，未准备好时返回 null
     */
    public GlyphPage getReadyPage(int codepoint, FontType fontType) {
        return readyPages.get(new GlyphCacheKey(codepoint, fontType));
    }

    /**
     * 获取字符度量信息。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 字符度量信息，未准备好时返回 null
     */
    public GlyphInfo getGlyphInfo(int codepoint, FontType fontType) {
        return glyphInfos.get(new GlyphCacheKey(codepoint, fontType));
    }

    /**
     * 判断是否已初始化。
     *
     * @return 是否已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 获取待上传数量。
     *
     * @return 待上传数量
     */
    public int getPendingUploadCount() {
        return pendingUploads.size();
    }

    /**
     * 获取已就绪字符数量。
     *
     * @return 已就绪字符数量
     */
    public int getReadyGlyphCount() {
        return readyPages.size();
    }

    /**
     * 获取普通字符页数量。
     *
     * @return 普通字符页数量
     */
    public int getNormalPageCount() {
        return normalPages.size();
    }

    /**
     * 获取粗体字符页数量。
     *
     * @return 粗体字符页数量
     */
    public int getBoldPageCount() {
        return boldPages.size();
    }

    private void ensureCapacity(FontType fontType) {
        List<GlyphPage> pages = getPages(fontType);
        int availableCount = 0;
        for (GlyphPage page : pages) {
            if (page.canAllocate()) {
                availableCount++;
            }
        }

        while (availableCount < maintainPageCount) {
            GlyphPage page = new GlyphPage(pages.size(), textureSize, glyphSize);
            pages.add(page);
            availableCount++;
        }
    }

    private GlyphPage allocatePage(FontType fontType, GlyphCacheKey key) {
        List<GlyphPage> pages = getPages(fontType);
        for (GlyphPage page : pages) {
            if (page.getSlotMap().containsKey(key)) {
                return page;
            }
            if (page.canAllocate()) {
                return page;
            }
        }

        GlyphPage page = new GlyphPage(pages.size(), textureSize, glyphSize);
        pages.add(page);
        MyMod.LOG.debug("字符页容量扩展，type={} pageIndex={}", fontType, Integer.valueOf(page.getPageIndex()));
        ensureCapacity(fontType);
        return page;
    }

    private List<GlyphPage> getPages(FontType fontType) {
        return fontType == FontType.BOLD ? boldPages : normalPages;
    }

    private void closePages(List<GlyphPage> pages) {
        for (GlyphPage page : pages) {
            page.close();
        }
    }
}
