package club.heiqi.uilib.host.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.host.client.image.JavaImageLoader;
import club.heiqi.uilib.host.embedded.EmbeddedSceneServices;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;

/** 游戏资源字节入口；三版宿主共享，加载器类型和资源位置构造留在各版边界。
 * 已读FontSource保留字节快照，资源重载后尚未读取的旧来源明确退休，不混读新包。
 */
public final class ResourceManagerFontSources implements EmbeddedSceneServices.FontSources {
    private static final String PREFIX = "assets/qz_uilib/";
    private final ClassLoader parent;
    private final JavaImageLoader.Resources resources;
    private final Thread thread = Thread.currentThread();
    private ResourceGeneration current;

    public ResourceManagerFontSources(ClassLoader parent, JavaImageLoader.Resources resources) {
        this.parent = Objects.requireNonNull(parent, "parent");
        this.resources = Objects.requireNonNull(resources, "resources");
        current = new ResourceGeneration();
    }

    @Override public List<FontSource> forGeneration(long generation) throws IOException {
        checkThread();
        return FontTextService.defaultSources(current, generation);
    }

    /** 仅真实资源重载结束后切换字节来源；配置校验/候选重排不改变资源代际。 */
    public void resourcesReloaded() {
        checkThread();
        current.retired = true;
        current = new ResourceGeneration();
    }

    private void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("字体资源代际仅限客户端线程");
    }

    private final class ResourceGeneration extends ClassLoader {
        private boolean retired;
        ResourceGeneration() { super(parent); }
        @Override public InputStream getResourceAsStream(String path) {
            if (!path.startsWith(PREFIX)) return super.getResourceAsStream(path);
            checkThread();
            if (retired) throw new IllegalStateException("字体资源代际已退休，禁止读取新资源包: " + path);
            try { return resources.snapshot("qz_uilib:" + path.substring(PREFIX.length())); }
            catch (IOException failure) { throw new IllegalStateException("MC字体资源读取失败: " + path, failure); }
        }
    }
}
