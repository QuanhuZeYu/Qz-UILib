package club.heiqi.uilib.ui.scene.integration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.embedded.EmbeddedSceneServices;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import club.heiqi.uilib.host.client.image.JavaImageLoader;
import club.heiqi.uilib.host.resource.ResourceManagerFontSources;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;

/** 只替换游戏ResourceManager的字节边界；真实默认来源、懒加载与快照逻辑均沿生产实现。 */
public final class ResourceManagerFontSourcesScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new ResourceManagerFontSourcesScenario().run(); }
    @Override protected void runTests() throws Exception {
        scenario("真实资源位置与两代懒来源不混读", this::resourceGenerations);
        scenario("可用来源读取错误不回退classpath", this::resourceFailure);
        scenario("资源包字体字节经正式服务重载并改变真实度量", this::resourceLayout);
    }

    private void resourceGenerations() throws Exception {
        ResourceManager manager = new ResourceManager();
        ResourceManagerFontSources sources = new ResourceManagerFontSources(getClass().getClassLoader(), manager);
        List<FontSource> first = sources.forGeneration(10);
        require(manager.reads == 0 && !first.get(0).isLoaded(), "默认来源失去懒加载");
        byte[] frozen = bytes(first.get(0));
        List<FontSource> validation = sources.forGeneration(11);
        require(new String(bytes(first.get(1)), StandardCharsets.UTF_8).startsWith("1:qz_uilib:fonts/"),
                "配置校验错误地退休了当前来源");
        require(!first.get(2).isLoaded(), "旧代未读反例未建立");
        manager.version = 2;
        sources.resourcesReloaded();
        List<FontSource> second = sources.forGeneration(12);
        require(!second.get(2).isLoaded(), "新代未读反例未建立");
        int beforeLateRead = manager.reads;
        try { bytes(first.get(2)); throw new AssertionError("退休旧来源读取了新资源包"); }
        catch (IllegalStateException retired) { require(retired.getMessage().contains("退休"), "旧代失败缺少退休诊断"); }
        require(manager.reads == beforeLateRead, "旧代迟读仍访问ResourceManager");
        require(Arrays.equals(frozen, bytes(first.get(0))), "已加载旧字体字节快照被换代覆盖");
        require(new String(bytes(second.get(2)), StandardCharsets.UTF_8).startsWith("2:qz_uilib:fonts/"),
                "新代懒加载没有读取当前资源包");
        require(validation.get(0).revision == 11 && second.get(0).revision == 12, "字体候选来源代际丢失");
        require(manager.closed == manager.reads, "资源输入流未由FontSource结束");
        System.out.println("MC_FONT_RESOURCE namespace=qz_uilib path=fonts/* loadedOldSnapshot=true unreadOldRetired=true unreadNewCurrent=true validationKeepsLive=true streamsClosed=true");
    }

    private void resourceFailure() throws Exception {
        IOException missing = new IOException("资源包缺失字体");
        ResourceManagerFontSources sources = new ResourceManagerFontSources(getClass().getClassLoader(), id -> { throw missing; });
        try { bytes(sources.forGeneration(1).get(0)); throw new AssertionError("资源失败被classpath静默覆盖"); }
        catch (IllegalStateException failure) {
            require(failure.getCause() == missing && failure.getMessage().contains("assets/qz_uilib/fonts/"),
                    "来源读取失败丢失实际资源位置或首因");
        }
    }

    private void resourceLayout() throws Exception {
        ClassLoader loader = getClass().getClassLoader();
        ResourcePack pack = new ResourcePack(loader);
        ResourceManagerFontSources sources = new ResourceManagerFontSources(loader, pack);
        GlApi gl = (GlApi) Proxy.newProxyInstance(GlApi.class.getClassLoader(), new Class<?>[] {GlApi.class},
                (proxy, method, args) -> { throw new AssertionError("资源验证不得调用GL: " + method.getName()); });
        Path directory = Files.createTempDirectory("uilib-game-font-");
        Path path = directory.resolve("settings.yaml");
        try (EmbeddedSceneServices service = EmbeddedSceneServices.open(path, loader, IsolatedFontEngine.shared(), gl, pack, sources)) {
            TextStyle style = new TextStyle("default", 18, -1);
            TextLayout old = service.fonts().layout("MW", style, 1000);
            FontSource previous = old.lines().get(0).glyphs().get(0).source;
            byte[] oldBytes = bytes(previous);
            pack.replacement = "assets/qz_uilib/fonts/noto/NotoSansDevanagari.ttf";
            sources.resourcesReloaded(); service.resourcesReloaded();
            TextLayout next = service.fonts().layout("MW", style, 1000);
            FontSource changed = next.lines().get(0).glyphs().get(0).source;
            require(previous.id.equals(changed.id) && previous != changed && next.generation > old.generation,
                    "游戏字体重载未保持来源id并建立新代实例");
            require(!Arrays.equals(oldBytes, bytes(changed)), "新代布局仍读取classpath原字体而非资源包覆盖");
            require(old.advanceWidth != next.advanceWidth, "实际字体替换未改变真实整形度量");
            require(Arrays.equals(oldBytes, bytes(previous)), "旧计划来源随资源包变更混入新字节");
            require(pack.unifontReads == 2 && !service.fonts().hasGlResources(), "资源代际未按游戏字体路径读取或发生GL调用");
            System.out.println("MC_FONT_RESOURCE_LAYOUT resourceId=qz_uilib:fonts/unifont/unifont-16.0.04.otf sameIdNewBytes=true realAdvanceChanged=true oldPlanFrozen=true noGL=true");
        } finally { Files.deleteIfExists(path); Files.deleteIfExists(directory); }
    }

    private static final class ResourcePack implements JavaImageLoader.Resources {
        final ClassLoader loader;
        String replacement;
        int unifontReads;
        ResourcePack(ClassLoader loader) { this.loader = loader; }
        public InputStream snapshot(String id) throws IOException {
            if (!id.startsWith("qz_uilib:fonts/")) throw new IOException("字体namespace/path不正确: " + id);
            String path = "assets/qz_uilib/" + id.substring("qz_uilib:".length());
            if (id.equals("qz_uilib:fonts/unifont/unifont-16.0.04.otf")) {
                unifontReads++;
                if (replacement != null) path = replacement;
            }
            InputStream stream = loader.getResourceAsStream(path);
            if (stream == null) throw new IOException("缺少真实字体样本: " + path);
            return stream;
        }
    }

    private static byte[] bytes(FontSource source) {
        final byte[][] captured = {null};
        source.openFace(new FontSession() {
            public FontFace openFace(ByteBuffer data, long faceIndex, float em) {
                captured[0] = new byte[data.remaining()]; data.get(captured[0]); return null;
            }
            public void close() { }
        }, 12);
        return captured[0];
    }

    private static final class ResourceManager implements JavaImageLoader.Resources {
        int version = 1, reads, closed;
        @Override public InputStream snapshot(String id) throws IOException {
            if (!id.startsWith("qz_uilib:fonts/")) throw new IOException("资源位置错误: " + id);
            reads++;
            return new ByteArrayInputStream((version + ":" + id).getBytes(StandardCharsets.UTF_8)) {
                @Override public void close() throws IOException { closed++; super.close(); }
            };
        }
    }
}
