package club.heiqi.uilib.ui.image;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import club.heiqi.uilib.MyMod;

/**
 * `img[src]` 远程位图下载缓存。
 */
public final class DocumentRemoteImageCache {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_CACHE_ENTRIES = 128;
    private static final DocumentRemoteImageCache INSTANCE = new DocumentRemoteImageCache();

    private final Map<String, Entry> entries = new ConcurrentHashMap<String, Entry>();
    private final ExecutorService executorService = new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(), new RemoteImageThreadFactory());

    private DocumentRemoteImageCache() {}

    /**
     * 返回共享远程图片缓存。
     *
     * @return 远程图片缓存
     */
    public static DocumentRemoteImageCache getInstance() {
        return INSTANCE;
    }

    /**
     * 请求远程图片；未完成时返回当前缓存状态。
     *
     * @param url 图片 URL
     * @param completionCallback 首次加载完成后的回调
     * @return 缓存条目
     */
    public Entry request(String url, Runnable completionCallback) {
        String normalizedUrl = normalizeUrl(url);
        if (normalizedUrl == null) {
            return Entry.failed();
        }
        trimCacheIfNeeded();
        Entry entry = entries.get(normalizedUrl);
        if (entry == null) {
            Entry createdEntry = new Entry(normalizedUrl);
            Entry previousEntry = entries.putIfAbsent(normalizedUrl, createdEntry);
            entry = previousEntry == null ? createdEntry : previousEntry;
        }
        if (entry.markLoading()) {
            final Entry loadingEntry = entry;
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    loadRemoteImage(loadingEntry, completionCallback);
                }
            });
        }
        return entry;
    }

    /**
     * 为测试或宿主预热写入远程图片缓存。
     *
     * @param url 图片 URL
     * @param image 位图
     */
    public void putForTesting(String url, BufferedImage image) {
        String normalizedUrl = normalizeUrl(url);
        if (normalizedUrl == null || image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return;
        }
        Entry entry = new Entry(normalizedUrl);
        entry.markLoaded(image);
        entries.put(normalizedUrl, entry);
    }

    /**
     * 清空缓存，供测试隔离使用。
     */
    public void clearForTesting() {
        entries.clear();
    }

    private void loadRemoteImage(Entry entry, Runnable completionCallback) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(entry.url);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(true);
            connection.setRequestProperty("User-Agent", "Qz-UILib img");
            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                entry.markFailed();
                return;
            }
            BufferedImage image = ImageIO.read(connection.getInputStream());
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                entry.markFailed();
                return;
            }
            entry.markLoaded(image);
            if (completionCallback != null) {
                completionCallback.run();
            }
        } catch (IOException exception) {
            entry.markFailed();
            MyMod.LOG.warn("远程 img 位图加载失败: {}", entry.url, exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void trimCacheIfNeeded() {
        if (entries.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        for (String key : entries.keySet()) {
            if (entries.size() <= MAX_CACHE_ENTRIES) {
                return;
            }
            entries.remove(key);
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.regionMatches(true, 0, "http://", 0, 7)
                || trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            return trimmed;
        }
        return null;
    }

    /**
     * 远程图片缓存条目。
     */
    public static final class Entry {

        private final String url;
        private volatile BufferedImage image;
        private volatile Status status = Status.PENDING;

        private Entry(String url) {
            this.url = url;
        }

        private static Entry failed() {
            Entry entry = new Entry("");
            entry.status = Status.FAILED;
            return entry;
        }

        public BufferedImage getImage() {
            return image;
        }

        public Status getStatus() {
            return status;
        }

        private synchronized boolean markLoading() {
            if (status != Status.PENDING) {
                return false;
            }
            status = Status.LOADING;
            return true;
        }

        private void markLoaded(BufferedImage image) {
            this.image = image;
            this.status = Status.LOADED;
        }

        private void markFailed() {
            this.status = Status.FAILED;
        }
    }

    /**
     * 远程图片加载状态。
     */
    public enum Status {
        PENDING,
        LOADING,
        LOADED,
        FAILED
    }

    private static final class RemoteImageThreadFactory implements ThreadFactory {

        private final AtomicInteger index = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "QzRemoteImage-" + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
