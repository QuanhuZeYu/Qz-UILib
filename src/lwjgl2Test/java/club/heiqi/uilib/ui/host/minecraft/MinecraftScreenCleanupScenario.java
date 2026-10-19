package club.heiqi.uilib.ui.host.minecraft;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** 直接验证MC正式持有者与真实session，不注入screen私有字段或复制关闭算法。 */
public final class MinecraftScreenCleanupScenario extends TestApplication {
    @Override protected void runTests() throws Exception {
        scenario("session首错与输入桥附加Error仍全部释放且幂等", () ->
                closeFailures(new IllegalStateException("session清理失败"), new AssertionError("输入桥清理失败"), false));
        scenario("session首个Error不被后续异常覆盖", () ->
                closeFailures(new AssertionError("session原始Error"), new IllegalArgumentException("输入桥异常"), false));
        scenario("域内session实际释放先于输入桥且保首错", () ->
                closeFailures(new IllegalStateException("域内session清理失败"), new AssertionError("域内输入桥清理失败"), true));
        scenario("域内首个Error与输入桥异常顺序保持", () ->
                closeFailures(new AssertionError("域内session原始Error"), new IllegalArgumentException("域内输入桥异常"), true));
        scenario("客户端停机先页后图像字体且保留页面首错", () ->
                clientShutdown(new IllegalStateException("页面停机失败"), new AssertionError("字体桥停机失败")));
        scenario("客户端停机保留页面Error并完成其余owner", () ->
                clientShutdown(new AssertionError("页面停机Error"), new IllegalArgumentException("字体桥停机异常")));
    }

    private void closeFailures(Throwable first, Throwable second, boolean inDomain) {
        ReactiveDomain domain = ReactiveDomain.direct();
        int[] closed = {0, 0, 0};
        java.util.List<String> order = new java.util.ArrayList<>();
        SceneHost host = new SceneHost() {
            public int width() { return 80; }
            public int height() { return 60; }
            public long metricsRevision() { return 1; }
            public void replay(PaintPlan plan) {}
        };
        ScenePageSession session = ScenePageSession.open(domain, host, (text, style, width) -> { throw new AssertionError("空页面不应请求文字度量"); }, context -> {
            context.own(() -> closed[0]++);
            context.own(() -> { closed[1]++; order.add("session"); fail(first); });
        });
        MinecraftScreenResources resources = new MinecraftScreenResources(session, () -> {
            require(session.ended(), "输入桥先于session结束");
            order.add("inputs");
            closed[2]++;
            fail(second);
        });
        try {
            if (inDomain) domain.execute(resources::close); else resources.close();
            throw new AssertionError("双关闭没有失败");
        }
        catch (RuntimeException | Error actual) {
            require(actual == first, "首错identity被覆盖：actual=" + actual + " order=" + order);
            require(actual.getSuppressed().length == 1 && actual.getSuppressed()[0] == second,
                    "输入桥错误未作为首错的suppressed");
        }
        require(order.equals(java.util.Arrays.asList("session", "inputs")), "实际所属资源释放顺序错误：" + order);
        require(closed[0] == 1 && closed[1] == 1 && closed[2] == 1, "释放失败阻断后续所属资源");
        resources.close();
        session.close();
        require(closed[0] == 1 && closed[1] == 1 && closed[2] == 1, "重复结束重复释放");
    }

    private void clientShutdown(Throwable pageFailure, Throwable fontFailure) throws Exception {
        java.util.List<String> order = new java.util.ArrayList<>();
        ReactiveDomain domain = ReactiveDomain.direct();
        SceneHost host = new SceneHost() {
            public int width() { return 80; }
            public int height() { return 60; }
            public long metricsRevision() { return 1; }
            public void replay(PaintPlan plan) { throw new AssertionError("停机不能补跑帧"); }
        };
        ScenePageSession session = ScenePageSession.open(domain, host,
                (text, style, width) -> { throw new AssertionError("空页面不能请求文字度量"); },
                context -> context.own(() -> {
                    order.add("page");
                    require(club.heiqi.uilib.host.client.image.MinecraftImageServices.shared() != null,
                            "页面先于共享图像结束");
                    require(club.heiqi.uilib.host.client.font.MinecraftFontServices.shared() != null,
                            "页面先于共享字体结束");
                    fail(pageFailure);
                }));
        club.heiqi.uilib.host.client.gl.GlApi gl = (club.heiqi.uilib.host.client.gl.GlApi)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {club.heiqi.uilib.host.client.gl.GlApi.class},
                        (proxy, method, args) -> { throw new AssertionError("无设备停机场景调用GL: " + method); });
        club.heiqi.uilib.host.client.image.ClientImageServices images =
                new club.heiqi.uilib.host.client.image.ClientImageServices(gl,
                        source -> () -> { throw new AssertionError("停机不加载图像"); },
                        club.heiqi.uilib.ui.scene.image.SharedImageService.Budget.defaults());
        club.heiqi.uilib.ui.scene.image.SharedImageService imageOwner = images.shared();
        club.heiqi.uilib.host.client.font.ClientFontServices fonts =
                new club.heiqi.uilib.host.client.font.ClientFontServices(getClass().getClassLoader(), 1,
                        club.heiqi.uilib.host.client.font.ClientFontServices.Settings.defaults(),
                        club.heiqi.uilib.host.font.IsolatedFontEngine.shared(), gl,
                        club.heiqi.uilib.host.client.gl.GlProgramProvider.NONE);
        java.lang.reflect.Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        net.minecraft.client.Minecraft minecraft = (net.minecraft.client.Minecraft)
                ((sun.misc.Unsafe) access.get(null)).allocateInstance(net.minecraft.client.Minecraft.class);
        MinecraftSceneScreen screen = new MinecraftSceneScreen(context -> {});
        field(MinecraftSceneScreen.class, "session").set(screen, session);
        minecraft.currentScreen = screen;
        java.lang.reflect.Field singleton = field(net.minecraft.client.Minecraft.class, "theMinecraft");
        Object previousMinecraft = singleton.get(null);
        singleton.set(null, minecraft);
        field(club.heiqi.uilib.host.client.image.MinecraftImageServices.class, "thread").set(null, Thread.currentThread());
        field(club.heiqi.uilib.host.client.image.MinecraftImageServices.class, "shared").set(null, images);
        field(club.heiqi.uilib.host.client.font.MinecraftFontServices.class, "thread").set(null, Thread.currentThread());
        field(club.heiqi.uilib.host.client.font.MinecraftFontServices.class, "shared").set(null, fonts);
        club.heiqi.uilib.host.client.font.compat.OptionalFontHostBridge bridge =
                new club.heiqi.uilib.host.client.font.compat.OptionalFontHostBridge() {
                    public void submit(PaintPlan plan,
                            club.heiqi.uilib.host.client.font.context.FontRenderContext context, AutoCloseable lease) {
                        throw new AssertionError("停机不能提交绘制");
                    }
                    public void abort() { close(); }
                    public int pending() { return 0; }
                    public void close() {
                        require(session.ended(), "字体先于页面结束");
                        require(fieldValue(club.heiqi.uilib.host.client.image.MinecraftImageServices.class, "shared") == null,
                                "字体先于图像结束");
                        order.add("font");
                        fail(fontFailure);
                    }
                };
        field(club.heiqi.uilib.host.client.font.MinecraftVanillaFont.class, "bridge").set(null, bridge);
        java.lang.reflect.Method stopping = club.heiqi.uilib.mixin.client.MinecraftLifecycleMixin.class
                .getDeclaredMethod("qz$closeFontServices", org.spongepowered.asm.mixin.injection.callback.CallbackInfo.class);
        stopping.setAccessible(true);
        Object lifecycle = new club.heiqi.uilib.mixin.client.MinecraftLifecycleMixin() {};
        try {
            try {
                stopping.invoke(lifecycle, new Object[] {null});
                throw new AssertionError("客户端停机未传播页面首错");
            } catch (java.lang.reflect.InvocationTargetException observed) {
                require(observed.getCause() == pageFailure, "客户端停机覆盖页面首错");
                require(pageFailure.getSuppressed().length == 1 && pageFailure.getSuppressed()[0] == fontFailure,
                        "字体错误未附加在页面首错");
            }
            require(order.equals(java.util.Arrays.asList("page", "font")), "停机依赖顺序错误: " + order);
            require(imageOwner.awaitClose(5000) && fonts.awaitRasterizerClose(5000), "共享worker未结束");
            stopping.invoke(lifecycle, new Object[] {null});
            require(order.size() == 2, "重复停机重复关闭所属资源");
        } finally {
            singleton.set(null, previousMinecraft);
            field(club.heiqi.uilib.host.client.font.MinecraftVanillaFont.class, "bridge").set(null, null);
            screen.onGuiClosed();
            images.close();
            fonts.close();
        }
        System.out.println("MC_CLIENT_SHUTDOWN pageBeforeImagesBeforeFonts=true firstFailureIdentity=true suppressed=true noGL=true");
    }

    private static java.lang.reflect.Field field(Class<?> type, String name) throws Exception {
        java.lang.reflect.Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }

    private static Object fieldValue(Class<?> type, String name) {
        try { return field(type, name).get(null); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static void fail(Throwable failure) {
        if (failure instanceof Error) throw (Error) failure;
        throw (RuntimeException) failure;
    }

    public static void main(String[] args) throws Exception { new MinecraftScreenCleanupScenario().run(args); }
}
