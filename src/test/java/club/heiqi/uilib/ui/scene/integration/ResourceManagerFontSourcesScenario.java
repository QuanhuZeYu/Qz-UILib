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
        scenario("主线程捕获独立流，后台读并锁定本代内容", this::resourceGenerations);
        scenario("可用来源读取错误不回退classpath", this::resourceFailure);
        scenario("资源包字体字节经正式服务重载并改变真实度量", this::resourceLayout);
    }

    private void resourceGenerations() throws Exception {
        ResourceManager manager = new ResourceManager();
        ResourceManagerFontSources sources = new ResourceManagerFontSources(getClass().getClassLoader(), manager);
        club.heiqi.uilib.host.client.font.FontResourcePreparation.Sources first = sources.capture(10);
        int captured = manager.reads;
        manager.version = 2;
        sources.resourcesReloaded();
        club.heiqi.uilib.host.client.font.FontResourcePreparation.Sources second = sources.capture(12);
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<byte[]> old = worker.submit(() -> {
                try (AutoCloseable streams = (AutoCloseable) first.loader) { return bytes(first.builtins.get(0)); }
            });
            byte[] frozen = old.get();
            require(new String(frozen, StandardCharsets.UTF_8).startsWith("1:qz_uilib:fonts/"), "旧代流迟读串入新资源包");
            require(manager.reads == captured * 2, "worker重新打开当前manager");
            byte[] current = worker.submit(() -> {
                try (AutoCloseable streams = (AutoCloseable) second.loader) { return bytes(second.builtins.get(0)); }
            }).get();
            require(new String(current, StandardCharsets.UTF_8).startsWith("2:qz_uilib:fonts/"), "新代未读取捕获内容");
            require(Arrays.equals(frozen, bytes(first.builtins.get(0))), "已准备旧source不再是只读快照");
            require(manager.closed.get() == manager.reads, "本代未消费stream没有由worker关闭");
            System.out.println("MC_FONT_RESOURCE captureOwner=true workerReadClose=true oldLateReadFrozen=true noManagerReopen=true streamsClosed=true");
        } finally { worker.shutdown(); }
    }

    private void resourceFailure() throws Exception {
        IOException missing = new IOException("资源包缺失字体");
        ResourceManagerFontSources sources = new ResourceManagerFontSources(getClass().getClassLoader(), id -> { throw missing; });
        club.heiqi.uilib.host.client.font.FontResourcePreparation.Sources captured = sources.capture(1);
        java.util.concurrent.ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.Future<?> failed = worker.submit(() -> {
                try (AutoCloseable streams = (AutoCloseable) captured.loader) { bytes(captured.builtins.get(0)); }
                catch (RuntimeException failure) { throw failure; }
                catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            try { failed.get(); throw new AssertionError("资源失败被classpath覆盖"); }
            catch (java.util.concurrent.ExecutionException expected) {
                Throwable cause = expected.getCause();
                while (cause.getCause() != null) cause = cause.getCause();
                require(cause == missing && expected.getCause().getMessage().contains("assets/qz_uilib/fonts/"), "资源位置或原始失败丢失");
            }
        } finally { worker.shutdown(); }
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
            await(service, service.ready());
            TextStyle style = new TextStyle("default", 18, -1);
            TextLayout old = service.fonts().layout("MW", style, 1000);
            FontSource previous = old.lines().get(0).glyphs().get(0).source;
            byte[] oldBytes = bytes(previous);
            // 覆盖字体必须实际包含 MW；缺字会正确转入后续候选，不能据其来源变化判断重载失败。
            pack.replacement = "assets/qz_uilib/fonts/math/STIXTwoMath-Regular.otf";
            sources.resourcesReloaded(); await(service, service.resourcesReloaded());
            TextLayout next = service.fonts().layout("MW", style, 1000);
            FontSource changed = next.lines().get(0).glyphs().get(0).source;
            for (TextLayout layout : Arrays.asList(old, next))
                for (TextLayout.Glyph glyph : layout.lines().get(0).glyphs())
                    require(!glyph.replacement && glyph.glyphId > 0, "资源包替换前后必须使用实际覆盖MW的字体字形");
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
            if (!id.startsWith("qz_uilib:fonts/") && !id.startsWith("qz_uilib:unicode/")) throw new IOException("字体namespace/path不正确: " + id);
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
        int version = 1, reads;
        final Thread owner = Thread.currentThread();
        final java.util.concurrent.atomic.AtomicInteger closed = new java.util.concurrent.atomic.AtomicInteger();
        @Override public InputStream snapshot(String id) throws IOException {
            if (!id.startsWith("qz_uilib:fonts/") && !id.startsWith("qz_uilib:unicode/")) throw new IOException("资源位置错误: " + id);
            require(Thread.currentThread() == owner, "manager开流不在主线程");
            reads++;
            return new ByteArrayInputStream((version + ":" + id).getBytes(StandardCharsets.UTF_8)) {
                @Override public synchronized int read(byte[] bytes, int offset, int length) { require(Thread.currentThread() != owner, "UI读资源包字节"); return super.read(bytes, offset, length); }
                @Override public void close() throws IOException { require(Thread.currentThread() != owner, "UI关闭资源流"); closed.incrementAndGet(); super.close(); }
            };
        }
    }
    private static void await(EmbeddedSceneServices services, java.util.concurrent.CompletableFuture<Void> future) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (!future.isDone() && System.nanoTime() < deadline) {
            services.pumpResources();
            if (!future.isDone()) Thread.sleep(1);
        }
        if (!future.isDone()) throw new AssertionError("资源准备未完成");
        future.get();
    }

}
