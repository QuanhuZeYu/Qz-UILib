package club.heiqi.uilib.host.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.host.client.image.JavaImageLoader;
import club.heiqi.uilib.host.client.font.FontResourcePreparation;
import club.heiqi.uilib.host.embedded.EmbeddedSceneServices;
import club.heiqi.uilib.ui.scene.text.font.FontResources;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

/** MC主线程只取得本代独立streams；read/hash/metadata/close全部交字体worker。
 * 捕获后不再访问当前 ResourceManager，旧代worker即使迟到也不能读到新包。
 */
public final class ResourceManagerFontSources implements EmbeddedSceneServices.FontSources {
    private static final String PREFIX = "assets/qz_uilib/";
    private final ClassLoader parent;
    private final JavaImageLoader.Resources resources;
    private final Thread thread = Thread.currentThread();
    private ResourceGeneration current;
    private FontResourcePreparation.Sources captured;
    public ResourceManagerFontSources(ClassLoader parent, JavaImageLoader.Resources resources) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.resources = Objects.requireNonNull(resources, "resources");
    }
    @Override public List<FontSource> forGeneration(long generation) throws IOException { return capture(generation).builtins; }
    @Override public FontResourcePreparation.Sources capture(long generation) throws IOException {
        checkThread();
        if (captured == null) {
            current = new ResourceGeneration();
            captured = new FontResourcePreparation.Sources(current, generation, FontTextService.defaultSources(current, generation), current);
        }
        return captured;
    }
    /** 真实重载只换捕获边界；已移交worker的旧streams仍归旧代关闭。 */
    public void resourcesReloaded() { checkThread(); captured = null; current = null; }
    private void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("字体资源捕获仅限客户端线程");
    }
    private final class ResourceGeneration extends ClassLoader implements AutoCloseable {
        private final Map<String, InputStream> streams = new LinkedHashMap<>();
        private IOException openingFailure;
        private boolean closed;
        ResourceGeneration() {
            super(parent);
            for (String path : FontResources.resourcePaths()) {
                try {
                    InputStream stream = resources.snapshot("qz_uilib:" + path.substring(PREFIX.length()));
                    if (stream == null) throw new IOException("字体资源不存在: " + path);
                    streams.put(path, stream);
                } catch (IOException failure) {
                    openingFailure = new IOException("MC字体资源捕获失败: " + path, failure);
                    break;
                }
            }
        }
        @Override public InputStream getResourceAsStream(String path) {
            if (!path.startsWith(PREFIX)) return super.getResourceAsStream(path);
            if (Thread.currentThread() == thread) throw new IllegalStateException("字体read必须交资源worker: " + path);
            if (openingFailure != null) throw new IllegalStateException(openingFailure.getMessage(), openingFailure);
            if (closed) throw new IllegalStateException("字体资源代际已结束: " + path);
            InputStream stream = streams.remove(path);
            if (stream == null) throw new IllegalStateException("本代字体stream未捕获或已消费: " + path);
            return stream;
        }
        @Override public void close() throws IOException {
            if (Thread.currentThread() == thread) throw new IllegalStateException("字体stream必须由资源worker关闭");
            closed = true;
            IOException failure = null;
            for (InputStream stream : streams.values()) try { stream.close(); }
            catch (IOException next) { if (failure == null) failure = next; else failure.addSuppressed(next); }
            streams.clear();
            if (failure != null) throw failure;
        }
    }
}
