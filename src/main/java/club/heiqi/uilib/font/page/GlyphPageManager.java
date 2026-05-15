package club.heiqi.uilib.font.page;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
    private final Map<GlyphCacheKey, Long> glyphGenerationIds = new ConcurrentHashMap<GlyphCacheKey, Long>();
    private final Map<GlyphCacheKey, GlyphPage> readyPages = new ConcurrentHashMap<GlyphCacheKey, GlyphPage>();
    private final Map<GlyphCacheKey, GlyphInfo> glyphInfos = new ConcurrentHashMap<GlyphCacheKey, GlyphInfo>();
    private final Queue<PendingGlyphUpload> pendingUploads = new ConcurrentLinkedQueue<PendingGlyphUpload>();
    private final Set<GlyphCacheKey> recoverableRequests = Collections.newSetFromMap(
            new ConcurrentHashMap<GlyphCacheKey, Boolean>());
    private final List<GlyphPage> normalPages = new ArrayList<GlyphPage>();
    private final List<GlyphPage> boldPages = new ArrayList<GlyphPage>();

    private int textureSize;
    private int glyphSize;
    private int maintainPageCount = 3;
    private final AtomicLong generationIdSequence = new AtomicLong(0L);
    private volatile int runtimeVersion;

    /**
     * 初始化字符页管理器。
     */
    public synchronized void initialize() {
        textureSize = Math.max(64, (int) (FontConfig.awtCharSize * 64));
        glyphSize = Math.max(8, (int) FontConfig.awtCharSize);
        if (initialized.compareAndSet(false, true)) {
            ensureCapacity(FontType.NORMAL);
            ensureCapacity(FontType.BOLD);
        }
    }

    /**
     * 设置当前运行时版本。
     *
     * @param runtimeVersion 运行时版本
     */
    public synchronized void setRuntimeVersion(int runtimeVersion) {
        this.runtimeVersion = runtimeVersion;
    }

    /**
     * 重置字符页状态。
     */
    public synchronized void reset() {
        if (initialized.get()) {
            closePages(normalPages);
            closePages(boldPages);
        }
        glyphStates.clear();
        glyphGenerationIds.clear();
        readyPages.clear();
        glyphInfos.clear();
        pendingUploads.clear();
        recoverableRequests.clear();
        normalPages.clear();
        boldPages.clear();
        textureSize = Math.max(64, (int) (FontConfig.awtCharSize * 64));
        glyphSize = Math.max(8, (int) FontConfig.awtCharSize);
        if (initialized.get()) {
            ensureCapacity(FontType.NORMAL);
            ensureCapacity(FontType.BOLD);
        }
    }

    /**
     * 尝试将字符切换到生成中状态。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 是否允许开始生成
     */
    public boolean tryMarkGenerating(int codepoint, FontType fontType) {
        return tryMarkGenerating(runtimeVersion, codepoint, fontType);
    }

    /**
     * 尝试将指定运行时版本内的字符切换到生成中状态。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 是否允许开始生成
     */
    public synchronized boolean tryMarkGenerating(int runtimeVersion, int codepoint, FontType fontType) {
        if (runtimeVersion != this.runtimeVersion) {
            return false;
        }
        GlyphCacheKey key = createKey(runtimeVersion, codepoint, fontType);
        GlyphState currentState = glyphStates.get(key);
        if (currentState == GlyphState.GENERATING
                || currentState == GlyphState.UPLOAD_PENDING
                || currentState == GlyphState.READY) {
            return false;
        }

        glyphStates.put(key, GlyphState.GENERATING);
        glyphGenerationIds.put(key, Long.valueOf(generationIdSequence.incrementAndGet()));
        recoverableRequests.add(key);
        return true;
    }

    /**
     * 获取当前字符生成请求编号。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 生成请求编号，未处于生成链路时返回 0
     */
    public synchronized long getGenerationId(int runtimeVersion, int codepoint, FontType fontType) {
        if (runtimeVersion != this.runtimeVersion) {
            return 0L;
        }
        Long generationId = glyphGenerationIds.get(createKey(runtimeVersion, codepoint, fontType));
        return generationId == null ? 0L : generationId.longValue();
    }

    /**
     * 标记字符生成被当前运行时取消。
     *
     * <p>该入口用于调度器 reset、worker 迟到或任务提交被拒绝等窗口，避免字符长期停留在生成中或待上传状态。</p>
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     */
    public synchronized void markGenerationCancelled(int runtimeVersion, int codepoint, FontType fontType) {
        if (runtimeVersion != this.runtimeVersion) {
            return;
        }
        GlyphCacheKey key = createKey(runtimeVersion, codepoint, fontType);
        GlyphState state = glyphStates.get(key);
        if (state == GlyphState.GENERATING || state == GlyphState.UPLOAD_PENDING) {
            glyphStates.put(key, GlyphState.NEW);
            glyphGenerationIds.remove(key);
        }
    }

    /**
     * 标记字符生成失败。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     */
    public void markFailed(int codepoint, FontType fontType) {
        markFailed(runtimeVersion, codepoint, fontType);
    }

    /**
     * 标记指定运行时版本内的字符生成失败。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字重类型
     */
    public synchronized void markFailed(int runtimeVersion, int codepoint, FontType fontType) {
        if (runtimeVersion != this.runtimeVersion) {
            return;
        }
        GlyphCacheKey key = createKey(runtimeVersion, codepoint, fontType);
        recoverableRequests.remove(key);
        glyphGenerationIds.remove(key);
        glyphStates.put(key, GlyphState.FAILED);
    }

    /**
     * 接收后台线程生成完成的字符结果。
     *
     * @param result 字符生成结果
     */
    public synchronized void queueUpload(GlyphGenerationResult result) {
        if (result.getRuntimeVersion() != runtimeVersion) {
            return;
        }
        GlyphCacheKey key = createKey(result.getRuntimeVersion(), result.getCodepoint(), result.getFontType());
        if (glyphStates.get(key) != GlyphState.GENERATING) {
            return;
        }
        if (!isCurrentGeneration(key, result.getGenerationId())) {
            return;
        }
        pendingUploads.add(new PendingGlyphUpload(result.getRuntimeVersion(), key, result));
        glyphStates.put(key, GlyphState.UPLOAD_PENDING);
    }

    /**
     * 在主线程侧刷新待上传队列。
     *
     * @param maxCount 本次最多处理的数量
     */
    public synchronized void flushPendingUploads(int maxCount) {
        int processed = 0;
        while (processed < maxCount && !pendingUploads.isEmpty()) {
            PendingGlyphUpload upload = pendingUploads.poll();
            if (upload == null) {
                break;
            }

            GlyphCacheKey key = upload.getKey();
            GlyphGenerationResult result = upload.getGenerationResult();
            if (upload.getRuntimeVersion() != runtimeVersion || result.getRuntimeVersion() != runtimeVersion) {
                continue;
            }
            if (glyphStates.get(key) != GlyphState.UPLOAD_PENDING) {
                continue;
            }
            if (!isCurrentGeneration(key, upload.getGenerationId())) {
                continue;
            }
            GlyphPage glyphPage = allocatePage(result.getFontType(), key);
            glyphPage.allocate(key);
            glyphPage.upload(key, result.getImage());
            readyPages.put(key, glyphPage);
            glyphInfos.put(key, upload.getGenerationResult().getGlyphInfo());
            glyphStates.put(key, GlyphState.READY);
            glyphGenerationIds.remove(key);
            recoverableRequests.remove(key);
            processed++;
        }
    }

    /**
     * 快照当前仍需要在 reload 后恢复的字符请求。
     *
     * @return 可恢复字符请求快照
     */
    public synchronized List<GlyphCacheKey> snapshotRecoverableRequests() {
        return new ArrayList<GlyphCacheKey>(recoverableRequests);
    }

    /**
     * 查询字符是否已可用。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 是否可用
     */
    public synchronized boolean isReady(int codepoint, FontType fontType) {
        return glyphStates.get(createKey(codepoint, fontType)) == GlyphState.READY;
    }

    /**
     * 获取字符状态。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 字符状态
     */
    public synchronized GlyphState getState(int codepoint, FontType fontType) {
        GlyphCacheKey key = createKey(codepoint, fontType);
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
    public synchronized GlyphPage getReadyPage(int codepoint, FontType fontType) {
        return readyPages.get(createKey(codepoint, fontType));
    }

    /**
     * 获取字符度量信息。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 字符度量信息，未准备好时返回 null
     */
    public synchronized GlyphInfo getGlyphInfo(int codepoint, FontType fontType) {
        return glyphInfos.get(createKey(codepoint, fontType));
    }

    /**
     * 获取当前运行时的字符缓存键。
     *
     * @param codepoint 字符码点
     * @param fontType 字重类型
     * @return 字符缓存键
     */
    public synchronized GlyphCacheKey createKey(int codepoint, FontType fontType) {
        return createKey(runtimeVersion, codepoint, fontType);
    }

    /**
     * 判断是否已初始化。
     *
     * @return 是否已初始化
     */
    public synchronized boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 获取待上传数量。
     *
     * @return 待上传数量
     */
    public synchronized int getPendingUploadCount() {
        return pendingUploads.size();
    }

    /**
     * 获取已就绪字符数量。
     *
     * @return 已就绪字符数量
     */
    public synchronized int getReadyGlyphCount() {
        return readyPages.size();
    }

    /**
     * 获取普通字符页数量。
     *
     * @return 普通字符页数量
     */
    public synchronized int getNormalPageCount() {
        return normalPages.size();
    }

    /**
     * 获取粗体字符页数量。
     *
     * @return 粗体字符页数量
     */
    public synchronized int getBoldPageCount() {
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

    private GlyphCacheKey createKey(int runtimeVersion, int codepoint, FontType fontType) {
        return new GlyphCacheKey(runtimeVersion, codepoint, fontType);
    }

    private boolean isCurrentGeneration(GlyphCacheKey key, long generationId) {
        Long currentGenerationId = glyphGenerationIds.get(key);
        return currentGenerationId != null && currentGenerationId.longValue() == generationId;
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
