package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.DisplayTestEnvironment;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.MinecraftFontServices;
import club.heiqi.uilib.host.client.font.MinecraftVanillaFont;
import club.heiqi.uilib.host.client.font.compat.FontHostOrderQueue;
import club.heiqi.uilib.host.client.font.compat.OptionalFontHostBridge;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.Lwjgl2GlApi;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.glyph.AsyncGlyphRasterizer;

import java.lang.reflect.Field;

/** 实际MC共享关闭入口和CPU/GL owner；只注入可选桥的失败票据，不启动世界。 */
public final class MinecraftFontShutdownScenario extends TestApplication {
    public static void main(String[] args) throws Exception { new MinecraftFontShutdownScenario().run(args); }

    private static void field(Class<?> type, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    @Override protected void runTests() throws Exception {
        own(new DisplayTestEnvironment(80, 60, "UILib MC shared font shutdown"));
        scenario("桥票据失败仍关闭CPU与GL字体owner并保留首错", () -> verify(new IllegalStateException("source lease")));
        scenario("桥票据Error仍关闭字体owner", () -> verify(new AssertionError("source lease error")));
    }

    private void verify(Throwable failure) throws Exception {
        ClientFontServices owner = own(new ClientFontServices(getClass().getClassLoader(), 1,
                ClientFontServices.Settings.defaults(), IsolatedFontEngine.shared(), new Lwjgl2GlApi(),
                GlProgramProvider.NONE));
        // 真实native face与GPU消费者被创建；不能只观察一个无资源的关闭helper。
        owner.layout("A", new TextStyle("default", 16, 0xff000000), 100);
        owner.createPlanRenderer();
        AsyncGlyphRasterizer cpu = owner.rasterizer();
        FontHostOrderQueue queue = new FontHostOrderQueue(2);
        int[] released = {0};
        queue.submit(() -> { throw new AssertionError("关闭不能回放票据"); }, () -> {
            released[0]++;
            if (failure instanceof RuntimeException) throw (RuntimeException) failure;
            throw (Error) failure;
        });
        OptionalFontHostBridge bridge = new OptionalFontHostBridge() {
            public void submit(PaintPlan plan, FontRenderContext context, AutoCloseable lease) {
                throw new AssertionError("本场景不提交新文字");
            }
            public void abort() { queue.close(); }
            public int pending() { return queue.pending(); }
            public void close() { queue.close(); }
        };
        field(MinecraftFontServices.class, "thread", Thread.currentThread());
        field(MinecraftFontServices.class, "shared", owner);
        MinecraftVanillaFont.install();
        field(MinecraftVanillaFont.class, "bridge", bridge);
        try {
            MinecraftFontServices.closeShared();
            throw new AssertionError("桥失败未传播");
        } catch (RuntimeException | Error observed) {
            require(observed == failure, "资源退出覆盖了首个桥异常");
        } finally {
            // 只清理测试注入的静态环境；实际owner必须已由生产closeShared结束。
            field(MinecraftVanillaFont.class, "bridge", null);
            MinecraftVanillaFont.close();
        }
        require(released[0] == 1 && queue.pending() == 0, "来源票据未精确释放");
        require(cpu.stats().closed && owner.awaitRasterizerClose(5000), "桥失败跳过共享CPU/native worker关闭");
        try {
            owner.fonts();
            throw new AssertionError("桥失败后字体owner仍开放");
        } catch (IllegalStateException expected) {}
        MinecraftFontServices.closeShared();
        require(released[0] == 1, "幂等退出重复释放票据");
    }
}
