package club.heiqi.uilib.ui.scene.integration;

import java.lang.reflect.Field;
import java.util.Collections;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import club.heiqi.uilib.testing.DisplayTestEnvironment;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;

/** 实际 MC screen.drawScreen 与正式 Session；仅宿主环境注入，不启动世界或 FML。 */
public final class MinecraftScreenSwitchScenario extends TestApplication {
    private static final class Host implements SceneHost {
        int frames;
        public int width() { return 80; }
        public int height() { return 60; }
        public long metricsRevision() { return 1; }
        public void replay(PaintPlan plan) {
            if (plan.commands().stream().noneMatch(p -> p.kind == PaintCommand.Kind.RECT))
                throw new AssertionError("页面背景没有进入回放");
            frames++;
        }
    }

    private static void field(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private AutoCloseable installSession(MinecraftSceneScreen screen, ScenePageSession session) throws Exception {
        field(MinecraftSceneScreen.class, screen, "session", session);
        // 本场景绕过完整MC字体/原生文本安装，只注入宿主环境；同次会话借用仍用生产对象。
        Class<?> type = Class.forName(MinecraftSceneScreen.class.getName() + "$Inputs");
        java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor(MinecraftSceneScreen.class, ScenePageSession.class);
        constructor.setAccessible(true);
        Object inputs = constructor.newInstance(screen, session);
        field(MinecraftSceneScreen.class, screen, "inputs", inputs);
        return (AutoCloseable) inputs;
    }

    @Override
    protected void runTests() throws Exception {
        own(new DisplayTestEnvironment(80, 60, "UILib MC screen same-frame switch"));
        scenario("点击切页当帧提交新页且旧输入不转投", () -> {
            // MC构造会启动完整游戏基础设施；环境只供currentScreen身份，生产screen正常构造。
            Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            access.setAccessible(true);
            Minecraft mc = (Minecraft) ((sun.misc.Unsafe) access.get(null)).allocateInstance(Minecraft.class);
            MinecraftSceneScreen first = new MinecraftSceneScreen(context -> {});
            MinecraftSceneScreen second = new MinecraftSceneScreen(context -> {});
            field(GuiScreen.class, first, "mc", mc);
            field(GuiScreen.class, second, "mc", mc);
            Host oldHost = new Host(), newHost = new Host();
            int[] clicks = {0, 0};
            SceneTextMeasurer text = (value, style, width) -> new TextLayout(0, 0, style, Collections.emptyList());
            ScenePageSession next = own(ScenePageSession.open(ReactiveDomain.direct(), newHost, text, context ->
                    context.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).background(0xff223344)
                            .onClick(() -> clicks[1]++))));
            ScenePageSession[] previous = new ScenePageSession[1];
            previous[0] = own(ScenePageSession.open(ReactiveDomain.direct(), oldHost, text, context ->
                    context.mount(new SceneNode().size(Size.fill(1), Size.fill(1)).background(0xff112233)
                            .onClick(() -> {
                                clicks[0]++;
                                previous[0].close();
                                mc.currentScreen = second;
                            }))));
            own(installSession(first, previous[0]));
            own(installSession(second, next));
            mc.currentScreen = first;
            first.drawScreen(10, 10, 0);
            require(oldHost.frames == 1, "初始旧页未呈现");
            previous[0].pointer(ScenePointerEvent.down(0, 10, 10));
            previous[0].pointer(ScenePointerEvent.up(0, 10, 10));
            previous[0].pointer(ScenePointerEvent.down(0, 10, 10));
            previous[0].pointer(ScenePointerEvent.up(0, 10, 10));
            first.drawScreen(10, 10, 0);
            require(clicks[0] == 1 && clicks[1] == 0, "旧批次重放或转投新页");
            require(oldHost.frames == 1 && newHost.frames == 1, "换页当帧未提交新页背景");
            second.drawScreen(10, 10, 0);
            require(newHost.frames == 2, "下一正常帧不能继续呈现新页");
            System.out.println("ACTUAL_MC_DRAW oldReplay=1 newReplay=2 oldClick=1 newClick=0");
        });
        scenario("MC失焦入口按采集序列清焦点和组字", () -> {
            ReactiveDomain domain = ReactiveDomain.direct();
            club.heiqi.uilib.ui.scene.edit.SceneEditState[] declared = {null};
            domain.execute(() -> declared[0] = new club.heiqi.uilib.ui.scene.edit.SceneEditState(domain, "A"));
            club.heiqi.uilib.ui.scene.edit.SceneEditState state = declared[0];
            SceneNode[] node = {null};
            SceneTextMeasurer text = own(new club.heiqi.uilib.ui.scene.text.font.FontTextService(
                    club.heiqi.uilib.host.font.IsolatedFontEngine.shared()));
            ScenePageSession session = own(ScenePageSession.open(domain, new Host(), text, context -> {
                node[0] = club.heiqi.uilib.ui.scene.control.SceneTextField.create(context, state,
                        domain.signal(club.heiqi.uilib.ui.scene.control.SceneTheme.light()),
                        domain.signal(false), ignored -> {});
                context.mount(node[0]);
            }));
            MinecraftSceneScreen screen = new MinecraftSceneScreen(context -> {});
            AutoCloseable inputs = own(installSession(screen, session));
            session.frame();
            domain.execute(() -> session.runtime().focusRouter().request(session.runtime().captureMount(node[0])));
            session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.composition("候选", true));
            session.frame();
            domain.execute(() -> require(session.runtime().focusRouter().focused().get() == node[0]
                    && state.snapshot().get().composing, "失焦前未建立真实焦点和组字"));
            field(inputs.getClass(), inputs, "pointerWindowActive", true);
            // 只注入Display的原生可用性事实；实际Inputs/有序事件与核心焦点协议照常运行。
            Field displayField = org.lwjgl.opengl.Display.class.getDeclaredField("display_impl");
            displayField.setAccessible(true);
            Object original = displayField.get(null);
            Object inactive = java.lang.reflect.Proxy.newProxyInstance(original.getClass().getClassLoader(),
                    new Class<?>[] {displayField.getType()}, (proxy, method, arguments) -> {
                        if (method.getName().equals("isActive")) return false;
                        method.setAccessible(true);
                        return method.invoke(original, arguments);
                    });
            Field access = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            access.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) access.get(null);
            Object base = unsafe.staticFieldBase(displayField);
            long offset = unsafe.staticFieldOffset(displayField);
            java.lang.reflect.Method sync = inputs.getClass().getDeclaredMethod("syncInputAvailability");
            sync.setAccessible(true);
            try {
                unsafe.putObjectVolatile(base, offset, inactive);
                sync.invoke(inputs);
            } finally {
                unsafe.putObjectVolatile(base, offset, original);
            }
            domain.execute(() -> require(session.runtime().focusRouter().focused().get() == node[0],
                    "采集失焦事实过早改焦点"));
            session.edit(club.heiqi.uilib.ui.scene.input.SceneEditEvent.text("迟到"));
            session.frame();
            domain.execute(() -> {
                require(session.runtime().focusRouter().focused().get() == null && !state.snapshot().get().composing,
                        "MC失焦未经正式入口清焦点/组字");
                require(state.snapshot().get().text.equals("A"), "失焦后的同批文字落到旧字段");
            });
        });
    }

    public static void main(String[] args) throws Exception {
        new MinecraftScreenSwitchScenario().run();
    }
}
