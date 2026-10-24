package club.heiqi.uilib.ui.host.minecraft;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collections;
import javax.imageio.ImageIO;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.geometry.SceneTransform;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePage;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.hud.SceneHud;
import club.heiqi.uilib.ui.scene.hud.SceneHudPlacement;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/**
 * 无窗口验证正式 HUD 更新、Forge/FML 入口与真实 Session；仅替换设备和回放边界。
 * 反射只布置已有会话和宿主事实，不向生产代码添加测试工厂或重写生命周期算法。
 */
public final class MinecraftHudScenario extends TestApplication {
    private static final class SoftwareHost implements SceneHost, SceneTextMeasurer {
        int width = 160, height = 90, prepares, frames, closes;
        long metrics, time;
        BufferedImage image;
        public int width() { return width; }
        public int height() { return height; }
        public long metricsRevision() { return metrics; }
        public long frameNanos() { return time += 20_000_000L; }
        public void prepareResources() { prepares++; }
        public TextLayout layout(String text, TextStyle style, int availableWidth) {
            require(text.isEmpty(), "此场景只验证无字控件，不伪造字体度量");
            return new TextLayout(0, 0, style, Collections.emptyList());
        }
        public void replay(PaintPlan plan) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, 0xff101010);
            for (PaintCommand command : plan.commands()) {
                if (command.kind == PaintCommand.Kind.ORDER_BARRIER) continue;
                if (command.kind == PaintCommand.Kind.TEXT) {
                    require(command.text.lines().isEmpty(), "软件回放不伪造文字像素");
                    continue;
                }
                require(command.kind == PaintCommand.Kind.RECT || command.kind == PaintCommand.Kind.PATH,
                        "软件场景遇到未声明载荷：" + command.kind);
                SceneTransform inverse = command.transform.inverse();
                for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                    double px = x + 0.5, py = y + 0.5;
                    if (!command.effectiveClip().contains(px, py)) continue;
                    double lx = inverse.x(px, py), ly = inverse.y(px, py);
                    boolean inside = command.kind == PaintCommand.Kind.PATH ? command.mesh.contains(lx, ly)
                            : lx >= command.box.x && ly >= command.box.y
                                    && lx < command.box.x + command.box.width && ly < command.box.y + command.box.height;
                    if (inside) image.setRGB(x, y, over(command.color, image.getRGB(x, y)));
                }
            }
            frames++;
        }
        private static int over(int source, int destination) {
            int alpha = source >>> 24, inverse = 255 - alpha;
            int red = (((source >>> 16) & 255) * alpha + ((destination >>> 16) & 255) * inverse + 127) / 255;
            int green = (((source >>> 8) & 255) * alpha + ((destination >>> 8) & 255) * inverse + 127) / 255;
            int blue = ((source & 255) * alpha + (destination & 255) * inverse + 127) / 255;
            return 0xff000000 | red << 16 | green << 8 | blue;
        }
    }

    private final class Page implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final SoftwareHost host = new SoftwareHost();
        final Object world = new Object(), connection = new Object();
        final MinecraftSceneHud hud;
        Signal<Boolean> visible;
        Signal<Integer> color;
        SceneNode button;
        final SceneHudPlacement placement = new SceneHudPlacement(SceneHudPlacement.Anchor.TOP_LEFT, 12.4f, 15.6f, 1.5f);
        ScenePageSession session;
        int builds, releases, animations, actions;
        Throwable releaseFailure;
        final ScenePage page = context -> {
            builds++;
            button = SceneButton.create(context, () -> "", context.theme(), () -> actions++)
                    .size(Size.fixed(64), Size.fixed(24));
            context.mount(new SceneNode().size(Size.fill(1), Size.fill(1))
                    .bind(NodeBinding.value(color, SceneNode::background)).add(SceneHud.create(context, button, () -> placement)));
            context.own(context.runtime().animate(now -> { animations++; return true; }));
            context.own(() -> {
                releases++;
                if (releaseFailure instanceof Error) throw (Error) releaseFailure;
                if (releaseFailure != null) throw (RuntimeException) releaseFailure;
            });
        };
        Page() throws Exception {
            domain.execute(() -> { visible = domain.signal(true); color = domain.signal(0xff174a63); });
            hud = new MinecraftSceneHud(domain, page, () -> visible.get());
            hud.update(world, connection, true, false);
            install();
        }
        void install() throws Exception {
            session = ScenePageSession.open(domain, host, host, context -> {
                context.own(() -> host.closes++);
                page.build(context);
            });
            field(MinecraftSceneHud.class, "session").set(hud, session);
        }
        void frame() { hud.update(world, connection, true, true); }
        public void close() { hud.close(); }
    }

    @Override protected void runTests() throws Exception {
        scenario("正式会话软件像素、动态环境与隐藏停止动效", this::softwareAndVisibility);
        scenario("世界与连接替换、断线、失败清理及重新进入", this::lifecycle);
        scenario("Forge Post ALL、F1、菜单与FML tick登记对称", () -> {
            try (FmlOwner ignored = new FmlOwner()) { forgeLifecycle(); }
        });
        scenario("真实GameSettings切换GUI Scale不改变正式HUD盒", this::guiScaleIndependence);
        scenario("被动HUD完整保留原生键鼠历史和repeat设置", this::inputIsolation);
        scenario("编辑薄宿主关闭不提交业务草稿", this::editorOwnership);
    }

    private void softwareAndVisibility() throws Exception {
        try (Page page = new Page()) {
            page.frame();
            require(page.host.frames == 1 && page.animations > 0, "真实Session与动画探针没有触达");
            require(page.host.image.getRGB(150, 80) == 0xff174a63, "正式PaintPlan背景未软件绘制");
            SceneNode button = page.button;
            int frames = page.host.frames, prepares = page.host.prepares, animations = page.animations;
            page.domain.execute(() -> { page.visible.set(false); page.color.set(0xff73512b); });
            for (int index = 0; index < 200; index++) page.frame();
            require(page.host.frames == frames && page.host.prepares == prepares && page.animations == animations,
                    "业务隐藏仍推进呈现、资源或动效");
            for (int index = 0; index < 200; index++) page.hud.update(page.world, page.connection, false, true);
            require(page.host.frames == frames && page.animations == animations, "F1/菜单隐藏仍推进页面");
            require(!page.session.ended() && page.releases == 0 && page.builds == 1, "隐藏重建或释放页面");
            page.domain.execute(() -> page.visible.set(true));
            page.host.width = 212; page.host.height = 118; page.host.metrics++;
            page.frame();
            require(page.builds == 1 && page.button == button, "重显重建控件或绑定");
            require(page.host.image.getWidth() == 212 && page.host.image.getRGB(205, 112) == 0xff73512b,
                    "重显未按新环境与业务状态绘制");
            File image = new File("build/hud-verification/hud-software.png");
            require(image.getParentFile().isDirectory() || image.getParentFile().mkdirs(), "无法创建图像目录");
            ImageIO.write(page.host.image, "png", image);
            System.out.println("HUD_SOFTWARE session=true sceneButton=true image=" + image
                    + " hiddenFrames=400 hiddenPrepare=0 hiddenAnimation=0 retainedIdentity=true resized=212x118");
        }
    }

    private void lifecycle() throws Exception {
        try (Page page = new Page()) {
            page.frame();
            ScenePageSession first = page.session;
            page.hud.update(new Object(), page.connection, false, false);
            require(first.ended() && page.releases == 1 && page.host.closes == 1, "隐藏世界替换未结束旧会话");
            page.hud.update(page.world, page.connection, false, false);
            page.install(); page.frame();
            require(!page.session.ended() && page.builds == 2, "重新进入不能重建正式会话");
            ScenePageSession second = page.session;
            page.hud.update(page.world, new Object(), false, false);
            require(second.ended() && page.releases == 2, "相同世界的连接替换未结束旧会话");
            page.hud.update(page.world, page.connection, false, false);
            page.install();
            IllegalStateException failure = new IllegalStateException("HUD所属释放失败");
            page.releaseFailure = failure;
            try {
                page.hud.update(null, null, false, false);
                throw new AssertionError("断线未传播所属资源失败");
            } catch (IllegalStateException actual) { require(actual == failure, "断线覆盖原始错误"); }
            require(page.session.ended() && page.host.closes == 3, "首错阻断其余所属资源释放");
            page.hud.close(); page.hud.close();
            page.hud.update(page.world, page.connection, true, true);
            require(page.releases == 3 && page.host.frames == 2, "重复结束或迟到更新访问旧页");
            page.domain.execute(() -> { page.color.set(0xff224466); require(page.color.get() == 0xff224466, "结束关闭了业务状态"); });
            System.out.println("HUD_LIFECYCLE worldReplace=true connectionReplace=true disconnect=true releases=3 firstFailure=true independentState=true");
        }
    }

    /** 只布置MC环境事实；生产Forge/FML总线和事件处理者保持原路径。 */
    private void forgeLifecycle() throws Exception {
        sun.misc.Unsafe access = unsafe();
        Minecraft minecraft = (Minecraft) access.allocateInstance(Minecraft.class);
        minecraft.gameSettings = (GameSettings) access.allocateInstance(GameSettings.class);
        minecraft.theWorld = (net.minecraft.client.multiplayer.WorldClient) access.allocateInstance(net.minecraft.client.multiplayer.WorldClient.class);
        minecraft.thePlayer = (net.minecraft.client.entity.EntityClientPlayerMP) access.allocateInstance(net.minecraft.client.entity.EntityClientPlayerMP.class);
        field(net.minecraft.client.entity.EntityClientPlayerMP.class, "sendQueue").set(minecraft.thePlayer,
                access.allocateInstance(net.minecraft.client.network.NetHandlerPlayClient.class));
        Field singleton = field(Minecraft.class, "theMinecraft");
        Object previous = singleton.get(null);
        singleton.set(null, minecraft);
        try (Page page = new Page()) {
            page.hud.update(minecraft.theWorld, minecraft.getNetHandler(), true, false);
            page.install();
            require(page.hud.register() == page.hud && page.hud.register() == page.hud, "登记不是幂等链式返回");
            RenderGameOverlayEvent event = new RenderGameOverlayEvent(0, null, 0, 0);
            MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Post(event, RenderGameOverlayEvent.ElementType.TEXT));
            require(page.host.frames == 0, "子元素Post提前绘制");
            MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Post(event, RenderGameOverlayEvent.ElementType.ALL));
            require(page.host.frames == 1, "Post ALL未恰好绘制一次");
            minecraft.gameSettings.hideGUI = true;
            MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Post(event, RenderGameOverlayEvent.ElementType.ALL));
            minecraft.gameSettings.hideGUI = false; minecraft.currentScreen = new GuiScreen();
            MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Post(event, RenderGameOverlayEvent.ElementType.ALL));
            require(page.host.frames == 1, "F1或打开菜单仍绘制HUD");
            ScenePageSession active = page.session;
            minecraft.theWorld = null;
            FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            require(active.ended(), "无世界时FML tick未释放页面");
            page.hud.close();
            singleton.set(null, null);
            MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Post(event, RenderGameOverlayEvent.ElementType.ALL));
            FMLCommonHandler.instance().bus().post(new TickEvent.ClientTickEvent(TickEvent.Phase.END));
            require(page.host.frames == 1, "注销后仍接收外部事件");
            System.out.println("HUD_FORGE postAllOnce=true f1=true currentScreen=true noWorldRelease=true registerIdempotent=true unregister=true");
        } finally { singleton.set(null, previous); }
    }

    private void guiScaleIndependence() throws Exception {
        Field singleton = field(Minecraft.class, "theMinecraft");
        Object previous = singleton.get(null);
        Minecraft minecraft = (Minecraft) unsafe().allocateInstance(Minecraft.class);
        minecraft.gameSettings = (GameSettings) unsafe().allocateInstance(GameSettings.class);
        singleton.set(null, minecraft);
        try (Page page = new Page()) {
            page.frame();
            LayoutBox[] baseline = new LayoutBox[1];
            page.domain.execute(() -> baseline[0] = page.session.pipeline().current().geometry.get(page.button).box);
            require(baseline[0].x == 12 && baseline[0].y == 16
                            && baseline[0].width == 96 && baseline[0].height == 36,
                    "显式内容缩放或原生像素偏移错误");
            for (int guiScale : new int[] {0, 1, 2, 3, 4}) {
                minecraft.gameSettings.guiScale = guiScale;
                page.frame();
                page.domain.execute(() -> require(SceneGeometryView.sameBox(baseline[0],
                        page.session.pipeline().current().geometry.get(page.button).box),
                        "宿主GUI Scale改变了HUD几何：" + guiScale));
                require(page.host.image.getRGB(60, 34) != 0xff174a63
                                && page.host.image.getRGB(109, 34) == 0xff174a63,
                        "GUI Scale改变了正式PaintPlan软件像素");
            }
            System.out.println("HUD_PIXEL_UNITS guiScaleIndependent=true explicitScale=1.5 offset=12.4,15.6 box=12,16,96,36");
        } finally { singleton.set(null, previous); }
    }

    private void inputIsolation() throws Exception {
        require(!Display.isCreated() && !Keyboard.isCreated() && !Mouse.isCreated(), "无窗口场景不能创建原生设备");
        Field keyboardCreated = field(Keyboard.class, "created"), keyboardBuffer = field(Keyboard.class, "readBuffer");
        Field mouseCreated = field(Mouse.class, "created"), mouseBuffer = field(Mouse.class, "readBuffer");
        Object oldKeyboard = keyboardBuffer.get(null), oldMouse = mouseBuffer.get(null);
        boolean repeat = Keyboard.areRepeatEventsEnabled();
        ByteBuffer keys = ByteBuffer.allocate(Keyboard.EVENT_SIZE);
        keys.putInt(Keyboard.KEY_E).put((byte) 1).putInt('e').putLong(1).put((byte) 0).flip();
        ByteBuffer mouse = ByteBuffer.allocate(Mouse.EVENT_SIZE);
        mouse.put((byte) 0).put((byte) 1).putInt(5).putInt(6).putInt(120).putLong(1).flip();
        keyboardBuffer.set(null, keys); keyboardCreated.setBoolean(null, true);
        mouseBuffer.set(null, mouse); mouseCreated.setBoolean(null, true);
        try (Page page = new Page()) {
            page.frame(); page.hud.update(page.world, page.connection, false, true); page.frame();
            require(keys.position() == 0 && mouse.position() == 0 && page.actions == 0, "被动HUD消费了原生历史或激活控件");
            require(Keyboard.areRepeatEventsEnabled() == repeat, "被动HUD更改键盘repeat所有权");
            require(Keyboard.next() && Keyboard.getEventKey() == Keyboard.KEY_E && Keyboard.getEventKeyState(), "游戏未能继续读取原键盘事件");
            require(Mouse.next() && Mouse.getEventButton() == 0 && Mouse.getEventButtonState(), "游戏未能继续读取原鼠标事件");
            System.out.println("HUD_INPUT keyboardUnconsumed=true mouseUnconsumed=true repeatUnchanged=true actions=0 noWindow=true");
        } finally {
            keyboardBuffer.set(null, oldKeyboard); keyboardCreated.setBoolean(null, false);
            mouseBuffer.set(null, oldMouse); mouseCreated.setBoolean(null, false);
        }
    }

    private void editorOwnership() throws Exception {
        try (Page page = new Page()) {
            MinecraftHudEditorScreen editor = new MinecraftHudEditorScreen(page.domain, page.page);
            ScenePageSession session = page.session;
            field(MinecraftSceneHud.class, "session").set(page.hud, null);
            field(MinecraftSceneScreen.class, "session").set(editor, session);
            require(!editor.doesGuiPauseGame(), "HUD编辑暂停了游戏");
            editor.onGuiClosed(); editor.onGuiClosed();
            require(session.ended() && page.releases == 1 && page.actions == 0, "关闭编辑页隐式提交或未释放页面");
            page.domain.execute(() -> require(page.color.get() == 0xff174a63, "编辑关闭改写了独立业务值"));
            System.out.println("HUD_EDITOR inheritedSession=true closeNoSave=true businessStateRetained=true");
        }
    }

    /** 只补FML登记所需的当前Mod事实，不运行Loader、Mod扫描或游戏启动。 */
    private static final class FmlOwner implements AutoCloseable {
        final Field singleton = field(cpw.mods.fml.common.Loader.class, "instance");
        final Object previous = singleton.get(null);
        FmlOwner() throws Exception {
            cpw.mods.fml.common.Loader loader = (cpw.mods.fml.common.Loader) unsafe().allocateInstance(cpw.mods.fml.common.Loader.class);
            cpw.mods.fml.common.LoadController controller = (cpw.mods.fml.common.LoadController) unsafe().allocateInstance(cpw.mods.fml.common.LoadController.class);
            cpw.mods.fml.common.ModMetadata metadata = new cpw.mods.fml.common.ModMetadata();
            metadata.modId = "hud-verification"; metadata.name = "HUD无窗口验证"; metadata.version = "1";
            field(cpw.mods.fml.common.LoadController.class, "activeContainer").set(controller,
                    new cpw.mods.fml.common.DummyModContainer(metadata));
            field(cpw.mods.fml.common.Loader.class, "modController").set(loader, controller);
            singleton.set(null, loader);
        }
        public void close() throws Exception { singleton.set(null, previous); }
    }

    /** 沿Forge自己的转换器准备事件默认构造与ListenerList；不复制或模拟事件总线。 */
    private static final class EventRuntime extends java.net.URLClassLoader {
        EventRuntime(java.net.URL[] urls) { super(urls, ClassLoader.getSystemClassLoader().getParent()); }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.startsWith("net.minecraftforge.client.event.RenderGameOverlayEvent")
                    && !name.startsWith("cpw.mods.fml.common.gameevent.TickEvent")) return super.findClass(name);
            try (java.io.InputStream input = getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (input == null) throw new ClassNotFoundException(name);
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
                Class<?> type = loadClass("cpw.mods.fml.common.asm.transformers.EventSubscriptionTransformer");
                Object transformer = type.getConstructor().newInstance();
                byte[] data = (byte[]) type.getMethod("transform", String.class, String.class, byte[].class)
                        .invoke(transformer, name, name, output.toByteArray());
                return defineClass(name, data, 0, data.length);
            } catch (ReflectiveOperationException | java.io.IOException failure) {
                throw new ClassNotFoundException(name, failure);
            }
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field result = type.getDeclaredField(name); result.setAccessible(true); return result;
    }
    private static sun.misc.Unsafe unsafe() throws Exception {
        return (sun.misc.Unsafe) field(sun.misc.Unsafe.class, "theUnsafe").get(null);
    }
    public static void main(String[] args) throws Exception {
        // Java8场景的隔离类加载器只运行本应用；Forge事件仍使用实际发行转换算法。
        if (MinecraftHudScenario.class.getClassLoader().getClass().getName().equals(EventRuntime.class.getName())) {
            new MinecraftHudScenario().run(args);
            return;
        }
        java.net.URL[] urls = ((java.net.URLClassLoader) ClassLoader.getSystemClassLoader()).getURLs();
        try (EventRuntime runtime = new EventRuntime(urls)) {
            try {
                runtime.loadClass(MinecraftHudScenario.class.getName()).getMethod("main", String[].class)
                        .invoke(null, (Object) args);
            } catch (java.lang.reflect.InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Error) throw (Error) cause;
                throw (Exception) cause;
            }
        }
    }
}
