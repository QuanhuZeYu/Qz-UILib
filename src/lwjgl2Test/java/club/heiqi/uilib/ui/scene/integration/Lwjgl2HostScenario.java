package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.DisplayTestEnvironment;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.gl.Lwjgl2GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;

import java.nio.ByteBuffer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import org.lwjgl.input.Keyboard;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.host.SceneEditInputAdapter;
import club.heiqi.uilib.ui.scene.host.SceneKeyPressInputAdapter;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.Arrays;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.context.FontProjection;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.host.client.gl.GlGlyphAtlas;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL31.*;
import static org.lwjgl.opengl.GL32.*;
import static org.lwjgl.opengl.GL33.*;
import org.lwjgl.opengl.GLContext;

/** 实际原LWJGL2默认Display.create验收同一shared backend/owner；不启动MC或重建其context。 */
public final class Lwjgl2HostScenario extends TestApplication {
    private static final int W = 240, H = 160;
    private static final LayoutBox VIEW = new LayoutBox(0, 0, W, H),
            LOCAL = new LayoutBox(-16000000, -16000000, 32000000, 32000000);

    private static FontRenderContext context(
            int fbo, int lightmap, boolean alpha, float ref, boolean write, float z) {
        float[] p = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, -1.020202f, -1, 0, 0, -0.2020202f, 0};
        float[] mv = {0.016f, 0, 0, 0, 0, -0.023f, 0, 0, 0.002f, 0, 1, 0, -0.7f, 0.5f, z, 1};
        FontRenderContext.MatrixState state =
                new FontRenderContext.MatrixState(
                        p,
                        mv,
                        new float[] {0.01f, 0.8f, 0.03f, 0.5f},
                        alpha,
                        GL_GREATER,
                        ref,
                        lightmap != 0,
                        lightmap,
                        0.5f,
                        0.5f,
                        0x00f000f0,
                        new float[] {0, 0, 1},
                        new float[] {0.5f, 1, 0.25f},
                        -1);
        return new FontRenderContext(
                new FontProjection(p, mv, new int[] {13, 17, 200, 120}),
                fbo,
                H,
                true,
                GL_LESS,
                write,
                true,
                new int[] {20, 25, 170, 110},
                state,
                GLContext.getCapabilities(),
                1,
                7);
    }

    private static int[] read(int fbo) {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
        ByteBuffer b = ByteBuffer.allocateDirect(W * H * 4).order(java.nio.ByteOrder.nativeOrder());
        try {
            glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, b);
            int[] out = new int[W * H * 4];
            for (int i = 0; i < out.length; i++) out[i] = b.get(i) & 255;
            return out;
        } finally {
        }
    }

    private static int count(int[] pixels) {
        int n = 0;
        for (int i = 3; i < pixels.length; i += 4) if (pixels[i] != 0) n++;
        return n;
    }

    private static void draw(
            ClientFontServices owner,
            GlPaintPlanRenderer renderer,
            PaintPlan plan,
            FontRenderContext context) {
        // 正式owner入口统一登记visiblekeys并扣整个presentation预算，consumer不自行retain。
        owner.beginPresentation(System.nanoTime());
        try (GlPaintPlanRenderer.Prepared p = owner.prepare(renderer, plan, context)) {
            p.render(context);
        } finally {
            owner.endPresentation();
        }
    }

    private ClientFontServices owner;
    private GlPaintPlanRenderer renderer;
    private int texture, depth, fbo, light, lit, glyphPixels, frames, premultTarget;
    private PaintPlan solid, plan;
    private FontRenderContext target;
    private TextLayout text;
    private FontRenderContext.StencilFace stencil;
    private GlyphKey key;
    private GlyphBitmap bitmap;
    private long beforeBytes, rasterized;

    @Override
    protected void runTests() throws Exception {
        String[] args = arguments();
        own(new DisplayTestEnvironment(W, H, "UILib LWJGL2 default compatibility probe"));
        System.out.println(
                "LWJGL="
                        + org.lwjgl.Sys.getVersion()
                        + " renderer="
                        + glGetString(GL_RENDERER)
                        + " version="
                        + glGetString(GL_VERSION));
        ClientFontServices.Settings defaults = ClientFontServices.Settings.defaults();
        ClientFontServices.Settings settings =
                new ClientFontServices.Settings(
                        defaults.cpu,
                        args.length > 0 && "array".equals(args[0])
                                ? GlGlyphAtlas.Mode.TEXTURE_ARRAY
                                : GlGlyphAtlas.Mode.TEXTURE_2D,
                        defaults.atlas,
                        defaults.upload,
                        defaults.quadCapacity,
                        defaults.maxDemand);
        owner =
                new ClientFontServices(
                        Lwjgl2HostScenario.class.getClassLoader(),
                        1,
                        settings,
                        IsolatedFontEngine.shared(),
                        new Lwjgl2GlApi(),
                        GlProgramProvider.NONE);
        onClose(
                () -> {
                    owner.close();
                    require(owner.awaitRasterizerClose(10000), "workerclose");
                });
        renderer = owner.sharedPlanRenderer();
        texture = glGenTextures();
        depth = glGenRenderbuffers();
        fbo = glGenFramebuffers();
        light = glGenTextures();
        onClose(
                () -> {
                    glBindFramebuffer(GL_FRAMEBUFFER, 0);
                    glDeleteFramebuffers(fbo);
                    glDeleteRenderbuffers(depth);
                    glDeleteTextures(texture);
                    glDeleteTextures(light);
                });
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(
                GL_TEXTURE_2D, 0, GL_RGBA8, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindRenderbuffer(GL_RENDERBUFFER, depth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, W, H);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        glFramebufferRenderbuffer(
                GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depth);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new AssertionError("FBO");
        ByteBuffer lm = ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder());
        try {
            lm.put((byte) 128).put((byte) 64).put((byte) 255).put((byte) 255).flip();
            glBindTexture(GL_TEXTURE_2D, light);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, lm);
        } finally {
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glDisable(GL_DITHER);
        glDisable(GL_FRAMEBUFFER_SRGB);
        glDisable(GL_SCISSOR_TEST);
        glDepthMask(true);
        glClearColor(0, 0, 0, 0);
        glClearDepth(1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        scenario("光照透明度深度与宿主恢复", this::solidState);
        scenario("世界字形可见需求", this::visibleDemand);
        scenario("异步世界字形真实像素", this::readyGlyphs);
        scenario("历史blend与零alpha直色", this::historicalBlend);
        scenario("负绕序正反面剔除", this::windingCull);
        scenario("双采样view真实纹理与预算", this::samplingViews);
        scenario("单行预算交替view推进", this::alternatingViews);
        scenario("历史stencil拒绝与现场恢复", this::historicalStencil);
        scenario("Windows中文字符原生载荷与正式字段草稿", this::windowsCharacterDraft);
        scenario("LWJGL2真实repeat过滤与单次按压门", this::nativeRepeatAndPressGuards);
        require(glGetError() == GL_NO_ERROR, "GLerror");
        System.out.println(
                "LWJGL2_HOST_SCENARIO_PASS configuredAtlasMode="
                        + owner.settings().mode
                        + " solidPixels="
                        + lit
                        + " glyphPixels="
                        + glyphPixels
                        + " frames="
                        + frames
                        + " entryTextureUnit3Restore=true perspectiveNonuniform=true lightmapRGBA=true effectiveLighting=true alphaCompare=true depthWriteOcclusion=true viewportFboRestore=true behindEyeNoDemand=true DefaultDisplayContext=true realIrisEngine=false");
    }

    /** 仅反射依赖库的真实事件生产者，不构造字节协议、不调用原生poll。 */
    private static final class WindowsEvents {
        final Object keyboard;
        final Method character, key, read;

        WindowsEvents() throws Exception {
            Class<?> type = Class.forName("org.lwjgl.opengl.WindowsKeyboard");
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            keyboard = ctor.newInstance();
            character = type.getDeclaredMethod("handleChar", int.class, long.class, boolean.class);
            key =
                    type.getDeclaredMethod(
                            "handleKey",
                            int.class,
                            int.class,
                            boolean.class,
                            byte.class,
                            long.class,
                            boolean.class);
            read = type.getDeclaredMethod("read", ByteBuffer.class);
            character.setAccessible(true);
            key.setAccessible(true);
            read.setAccessible(true);
        }

        void character(char value, boolean repeat) throws Exception {
            character.invoke(keyboard, (int) value, 1L, repeat);
        }

        void press(int virtual, char value) throws Exception {
            key.invoke(keyboard, virtual, 0, false, (byte) 1, 1L, false);
            if (value != 0) character(value, false);
            for (int i = 0; i < 2; i++) {
                key.invoke(keyboard, virtual, 0, false, (byte) 1, 2L, true);
                if (value != 0) character(value, true);
            }
            key.invoke(keyboard, virtual, 0, false, (byte) 0, 3L, false);
        }

        ByteBuffer payload() throws Exception {
            ByteBuffer bytes = ByteBuffer.allocate(Keyboard.EVENT_SIZE * 32);
            read.invoke(keyboard, bytes);
            bytes.flip();
            return bytes;
        }
    }

    /** 换入独立缓冲和事件对象；原窗口未消费的历史、当前事件及repeat设置均原样恢复。 */
    private static final class KeyboardHistory implements AutoCloseable {
        final Field buffer = field("readBuffer"),
                current = field("current_event"),
                scratch = field("tmp_event");
        final Object oldBuffer, oldCurrent, oldScratch;
        final boolean oldRepeat = Keyboard.areRepeatEventsEnabled();

        KeyboardHistory() throws Exception {
            if (!Keyboard.isCreated()) throw new AssertionError("实际Display未创建Keyboard");
            oldBuffer = buffer.get(null);
            oldCurrent = current.get(null);
            oldScratch = scratch.get(null);
            Constructor<?> ctor = current.getType().getDeclaredConstructor();
            ctor.setAccessible(true);
            Object nextCurrent = ctor.newInstance(), nextScratch = ctor.newInstance();
            buffer.set(null, ByteBuffer.allocate(0));
            current.set(null, nextCurrent);
            scratch.set(null, nextScratch);
        }

        static Field field(String name) throws Exception {
            Field value = Keyboard.class.getDeclaredField(name);
            value.setAccessible(true);
            return value;
        }

        void load(ByteBuffer value) throws Exception {
            buffer.set(null, value.duplicate());
        }

        @Override
        public void close() throws Exception {
            buffer.set(null, oldBuffer);
            current.set(null, oldCurrent);
            scratch.set(null, oldScratch);
            Keyboard.enableRepeatEvents(oldRepeat);
        }
    }

    private final class InputField implements AutoCloseable {
        final ReactiveDomain domain = ReactiveDomain.direct();
        final SceneEditState[] state = {null};
        final SceneNode[] node = {null};
        final int[] submits = {0};
        final ScenePageSession page;

        InputField() {
            SceneHost host =
                    new SceneHost() {
                        public int width() {
                            return W;
                        }

                        public int height() {
                            return H;
                        }

                        public long metricsRevision() {
                            return 1;
                        }

                        public void replay(PaintPlan plan) {} // 此场景验收输入状态，字形像素由既有宿主场景验收。
                    };
            page =
                    ScenePageSession.open(
                            domain,
                            host,
                            owner,
                            ctx -> {
                                state[0] = new SceneEditState(domain, "");
                                node[0] =
                                        SceneTextField.create(
                                                ctx,
                                                state[0],
                                                domain.signal(SceneTheme.light()),
                                                domain.signal(false),
                                                s -> submits[0]++);
                                ctx.mount(node[0]);
                            });
            domain.execute(
                    () ->
                            require(
                                    page.runtime()
                                            .focusRouter()
                                            .request(page.runtime().captureMount(node[0])),
                                    "原生输入字段聚焦失败"));
        }

        String draft() {
            final String[] value = {null};
            domain.execute(() -> value[0] = state[0].snapshot().get().text);
            return value[0];
        }

        @Override
        public void close() {
            page.close();
        }
    }

    private void windowsCharacterDraft() throws Exception {
        try (KeyboardHistory history = new KeyboardHistory();
                InputField field = new InputField()) {
            WindowsEvents nativeEvents = new WindowsEvents();
            nativeEvents.character('中', false);
            nativeEvents.character('文', false);
            Keyboard.enableRepeatEvents(false);
            history.load(nativeEvents.payload());
            SceneEditInputAdapter gate = new SceneEditInputAdapter();
            gate.historyDrained(Collections.emptySet());
            StringBuilder observed = new StringBuilder();
            while (Keyboard.next()) {
                require(
                        Keyboard.getEventKey() == Keyboard.KEY_NONE
                                && !Keyboard.getEventKeyState()
                                && !Keyboard.isRepeatEvent(),
                        "真实WM_CHAR不应伪装成按键DOWN");
                char value = Keyboard.getEventCharacter();
                observed.append(value);
                require(
                        !gate.key(
                                Keyboard.getEventKey(),
                                Keyboard.getEventKeyState(),
                                Keyboard.isRepeatEvent()),
                        "旧按键门应拒绝CHAR-only载荷");
                require(gate.available(), "CHAR-only应有独立可用性门");
                // 这里只验证正式核心支持真实载荷；独立宿主不加载MC，不能冒充routeKey回归。
                field.page.edit(SceneEditEvent.text(String.valueOf(value)));
            }
            field.page.frame();
            require(
                    observed.toString().equals("中文")
                            && field.draft().equals("中文")
                            && field.submits[0] == 0,
                    "中文载荷应进入草稿且不隐含确认");
            gate.availability(false);
            require(!gate.available(), "失焦时字符门必须关闭");
            gate.availability(true);
            require(!gate.available(), "恢复焦点仍等历史耗尽");
            gate.historyDrained(Collections.emptySet());
            require(gate.available(), "全释放后字符门应恢复");
            System.out.println(
                    "WINDOWS_CHAR_PAYLOAD_PASS keyNone=true downFalse=true draft=中文 noConfirm=true minecraftRouteKeyVerified=false");
        }
    }

    private void nativeRepeatAndPressGuards() throws Exception {
        try (KeyboardHistory history = new KeyboardHistory();
                InputField field = new InputField()) {
            WindowsEvents letters = new WindowsEvents();
            letters.press(0x41, 'a');
            ByteBuffer bytes = letters.payload();
            Keyboard.enableRepeatEvents(false);
            history.load(bytes);
            int filtered = 0;
            while (Keyboard.next())
                if (Keyboard.getEventKeyState()) {
                    require(!Keyboard.isRepeatEvent(), "repeat关闭仍报告重复事件");
                    filtered++;
                }
            require(filtered == 1, "repeat关闭应只保留初次DOWN");
            Keyboard.enableRepeatEvents(true);
            history.load(bytes);
            SceneEditInputAdapter edit = new SceneEditInputAdapter();
            edit.historyDrained(Collections.emptySet());
            int delivered = 0;
            while (Keyboard.next())
                if (edit.key(
                        Keyboard.getEventKey(),
                        Keyboard.getEventKeyState(),
                        Keyboard.isRepeatEvent())) {
                    require(Keyboard.getEventKey() == Keyboard.KEY_A, "真实Windows虚拟键A映射错误");
                    field.page.edit(
                            SceneEditEvent.text(String.valueOf(Keyboard.getEventCharacter())));
                    delivered++;
                }
            field.page.frame();
            require(delivered == 3 && field.draft().equals("aaa"), "真实repeat必须多次进入编辑草稿");
            repeatCommand(history, field, edit, 0x08, SceneEditEvent.Command.BACKSPACE);
            require(field.draft().isEmpty(), "Backspace真实repeat未多次删除");
            repeatCommand(history, field, edit, 0x20, SceneEditEvent.Command.SPACE);
            require(field.draft().equals("   ") && field.submits[0] == 0, "空格真实repeat应编辑且不确认");
            for (int virtual : new int[] {0x0d, 0x20}) {
                final int[] actions = {0};
                SceneKeyPressInputAdapter press = new SceneKeyPressInputAdapter(() -> actions[0]++);
                press.availability(true);
                press.historyDrained(false);
                WindowsEvents activation = new WindowsEvents();
                activation.press(virtual, (char) 0);
                history.load(activation.payload());
                while (Keyboard.next())
                    press.key(Keyboard.getEventKeyState(), Keyboard.isRepeatEvent());
                require(actions[0] == 1, "Enter/SPACE按压门不能因开启repeat扩大激活次数");
            }
            boolean scopeVerified = repeatScope();
            System.out.println(
                    "LWJGL2_REPEAT_PAYLOAD_PASS repeatDisabledDowns="
                            + filtered
                            + " repeatEnabledDowns="
                            + delivered
                            + " backspaceRepeat=true spaceRepeat=true activationOnce=true minecraftRouteKeyVerified=false repeatScopeVerified="
                            + scopeVerified);
        }
    }

    private boolean repeatScope() throws Exception {
        final Constructor<?> constructor;
        try {
            Class<?> type =
                    Class.forName(
                            "club.heiqi.uilib.ui.host.minecraft.MinecraftSceneScreen$RepeatEvents");
            constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
        } catch (ClassNotFoundException | NoClassDefFoundError absent) {
            System.out.println("REPEAT_SCOPE_NOT_VERIFIED dependency=" + absent);
            return false;
        }
        boolean original = Keyboard.areRepeatEventsEnabled();
        try {
            for (boolean previous : new boolean[] {false, true}) {
                Keyboard.enableRepeatEvents(previous);
                AutoCloseable scope = (AutoCloseable) constructor.newInstance();
                try {
                    require(Keyboard.areRepeatEventsEnabled(), "生产repeat资源未开启重复事件");
                } finally {
                    scope.close();
                }
                require(Keyboard.areRepeatEventsEnabled() == previous, "生产repeat资源未恢复原值");
                Keyboard.enableRepeatEvents(!previous);
                scope.close();
                require(Keyboard.areRepeatEventsEnabled() != previous, "重复close覆盖了后来的宿主设置");
                Keyboard.enableRepeatEvents(previous);
                RuntimeException expected = new RuntimeException("场景主动中止页面初始化");
                try {
                    ScenePageSession.open(
                            ReactiveDomain.direct(),
                            new SceneHost() {
                                public int width() {
                                    return W;
                                }

                                public int height() {
                                    return H;
                                }

                                public long metricsRevision() {
                                    return 1;
                                }

                                public void replay(PaintPlan plan) {}
                            },
                            owner,
                            ctx -> {
                                try {
                                    ctx.own((AutoCloseable) constructor.newInstance());
                                } catch (ReflectiveOperationException failure) {
                                    throw new IllegalStateException(failure);
                                }
                                require(Keyboard.areRepeatEventsEnabled(), "页面作用域未借用repeat");
                                throw expected;
                            });
                    throw new AssertionError("初始化失败未传播");
                } catch (RuntimeException failure) {
                    require(failure == expected, "初始化失败原始异常未保持");
                }
                require(Keyboard.areRepeatEventsEnabled() == previous, "初始化失败未释放真实生产repeat资源");
            }
            return true;
        } finally {
            Keyboard.enableRepeatEvents(original);
        }
    }

    private void repeatCommand(
            KeyboardHistory history,
            InputField field,
            SceneEditInputAdapter edit,
            int virtual,
            SceneEditEvent.Command command)
            throws Exception {
        WindowsEvents events = new WindowsEvents();
        events.press(virtual, (char) 0);
        history.load(events.payload());
        while (Keyboard.next())
            if (edit.key(
                    Keyboard.getEventKey(), Keyboard.getEventKeyState(), Keyboard.isRepeatEvent()))
                field.page.edit(SceneEditEvent.command(command, false, null));
        field.page.frame();
    }

    private void solidState() throws Exception {
        solid =
                new PaintPlanBuilder()
                        .add(PaintCommand.rect(new LayoutBox(0, 0, 40, 20), LOCAL, 0xffff8040))
                        .build();
        target = context(fbo, light, false, 0, true, -2);
        // 设置与ticket不同的actual现场，验证scope只临时切target/depth/viewport，保stencil/mask。
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glViewport(2, 4, 30, 40);
        glDepthFunc(GL_GEQUAL);
        glDepthMask(false);
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_STENCIL_TEST);
        glStencilFunc(GL_ALWAYS, 3, 0xff);
        glStencilMask(0);
        glColorMask(true, true, true, true);
        glActiveTexture(GL_TEXTURE3);
        draw(owner, renderer, solid, target);
        if (glGetInteger(GL_ACTIVE_TEXTURE) != GL_TEXTURE3)
            throw new AssertionError("world入口actualunit3未恢复");
        System.out.println(
                "firstDrawGLError=" + glGetError() + " draws=" + renderer.counters().draws);
        int[] pixels = read(fbo);
        lit = count(pixels);
        if (lit == 0) throw new AssertionError("world solid留白");
        for (int i = 0; i < pixels.length; i += 4)
            if (pixels[i + 3] != 0) {
                int[] expected = {32, 16, 8, 128};
                for (int c = 0; c < 4; c++)
                    if (Math.abs(pixels[i + c] - expected[c]) > 1)
                        throw new AssertionError(
                                "lighting/lightmap/alpha计算不符 c=" + c + " v=" + pixels[i + c]);
            }
        int[] viewport = new int[4];
        new Lwjgl2GlApi().glGetIntegerv(GL_VIEWPORT, viewport);
        if (!Arrays.equals(viewport, new int[] {2, 4, 30, 40})
                || glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) != 0
                || glGetInteger(GL_DEPTH_FUNC) != GL_GEQUAL
                || glGetBoolean(GL_DEPTH_WRITEMASK)
                || glIsEnabled(GL_DEPTH_TEST)
                || !glIsEnabled(GL_STENCIL_TEST)
                || glGetInteger(GL_STENCIL_WRITEMASK) != 0)
            throw new AssertionError("world target/depth/stencil现场未恢复");
        // 同深度LESS遮挡，不可被overlay flatten或关闭depthwrite绕过。
        draw(owner, renderer, solid, target);
        if (!Arrays.equals(pixels, read(fbo))) throw new AssertionError("world重复深度未遮挡");
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
        glDisable(GL_STENCIL_TEST);
        glDepthMask(true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        draw(owner, renderer, solid, context(fbo, light, true, 0.6f, false, -2));
        if (count(read(fbo)) != 0) throw new AssertionError("alphaGREATER未discard");
        draw(owner, renderer, solid, context(fbo, light, true, 0.4f, false, -2));
        if (count(read(fbo)) == 0) throw new AssertionError("alphaGREATER错误discard");
    }

    private void visibleDemand() throws Exception {
        text = owner.layout("World 中文 😀", new TextStyle("default", 18, 0xffffffff), 400);
        plan =
                new PaintPlanBuilder()
                        .add(PaintCommand.text(new LayoutBox(0, 0, 400, 80), LOCAL, text))
                        .build();
        if (renderer.collectVisibleDemand(plan, target).isEmpty())
            throw new AssertionError("worldcaller被误当framebufferclip");
        if (!renderer.collectVisibleDemand(plan, context(fbo, light, false, 0, false, 2)).isEmpty())
            throw new AssertionError("eye后字形进入需求");
        FontRenderContext clipped =
                new FontRenderContext(
                        target.projection,
                        fbo,
                        H,
                        true,
                        GL_LESS,
                        false,
                        true,
                        new int[] {0, 0, 1, 1},
                        target.state,
                        GLContext.getCapabilities(),
                        1,
                        7);
        if (!renderer.collectVisibleDemand(plan, clipped).isEmpty())
            throw new AssertionError("scissor外字形进入需求");
        float[] crossingMv = target.state.modelView();
        crossingMv[2] = 0.012f;
        crossingMv[14] = -0.35f;
        crossingMv[12] = -0.08f;
        crossingMv[0] = 0.005f;
        crossingMv[13] = 0.08f;
        crossingMv[5] = -0.005f;
        FontProjection crossingProjection =
                new FontProjection(
                        target.state.projection(), crossingMv, new int[] {13, 17, 200, 120});
        FontRenderContext crossing =
                new FontRenderContext(
                        crossingProjection,
                        fbo,
                        H,
                        true,
                        GL_LESS,
                        false,
                        false,
                        new int[] {0, 0, W, H},
                        target.state,
                        GLContext.getCapabilities(),
                        1,
                        7);
        if (renderer.collectVisibleDemand(plan, crossing).isEmpty())
            throw new AssertionError("近裁面交错可见字形全丢失");
    }

    private void readyGlyphs() throws Exception {
        long end = System.nanoTime() + 20000000000L;
        frames = 0;
        while (true) {
            owner.pumpReady();
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
            glDepthMask(true);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
            owner.beginPresentation(frames);
            boolean ready;
            try (GlPaintPlanRenderer.Prepared p = owner.prepare(renderer, plan, target)) {
                ready = p.readyCount() == p.demandCount();
                p.render(target);
            } finally {
                owner.endPresentation();
            }
            if (ready) break;
            if (System.nanoTime() > end) throw new AssertionError("worldglyph未ready");
            Thread.sleep(2);
            frames++;
        }
        glyphPixels = count(read(fbo));
        if (glyphPixels == 0) throw new AssertionError("真实worldglyph无像素");
    }

    private void historicalBlend() throws Exception {
        stencil =
                new FontRenderContext.StencilFace(GL_ALWAYS, 0, 255, 0, GL_KEEP, GL_KEEP, GL_KEEP);
        FontRenderContext.RasterState raster =
                new FontRenderContext.RasterState(
                        true,
                        GL_SRC_ALPHA,
                        GL_ONE_MINUS_SRC_ALPHA,
                        GL_ONE,
                        GL_ONE_MINUS_SRC_ALPHA,
                        GL_FUNC_ADD,
                        GL_FUNC_ADD,
                        new float[] {0.1f, 0.2f, 0.3f, 0.4f},
                        false,
                        stencil,
                        stencil,
                        new boolean[] {true, true, true, true},
                        new float[] {0, 1},
                        false,
                        GL_BACK,
                        GL_CCW,
                        false,
                        0,
                        0);
        FontRenderContext typed =
                new FontRenderContext(
                        target.projection,
                        fbo,
                        H,
                        true,
                        GL_LESS,
                        false,
                        true,
                        target.scissor(),
                        target.state,
                        GLContext.getCapabilities(),
                        1,
                        7,
                        raster);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
        glDepthMask(true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        draw(owner, renderer, solid, typed);
        int[] typedPixels = read(fbo);
        if (count(typedPixels) != lit) throw new AssertionError("typed历史blend几何改变");
        for (int i = 0; i < typedPixels.length; i += 4)
            if (typedPixels[i + 3] != 0) {
                int[] expected = {32, 16, 8, 128};
                for (int c = 0; c < 4; c++)
                    if (Math.abs(typedPixels[i + c] - expected[c]) > 1)
                        throw new AssertionError("历史SRC_ALPHA双乘alpha");
            }
        FontRenderContext.RasterState unblendedRaster =
                new FontRenderContext.RasterState(
                        false,
                        GL_SRC_ALPHA,
                        GL_ONE_MINUS_SRC_ALPHA,
                        GL_ONE,
                        GL_ONE_MINUS_SRC_ALPHA,
                        GL_FUNC_ADD,
                        GL_FUNC_ADD,
                        new float[] {0, 0, 0, 0},
                        false,
                        stencil,
                        stencil,
                        new boolean[] {true, true, true, true},
                        new float[] {0, 1},
                        false,
                        GL_BACK,
                        GL_CCW,
                        false,
                        0,
                        0);
        FontRenderContext unblended =
                new FontRenderContext(
                        target.projection,
                        fbo,
                        H,
                        true,
                        GL_LESS,
                        false,
                        true,
                        target.scissor(),
                        target.state,
                        GLContext.getCapabilities(),
                        1,
                        7,
                        unblendedRaster);
        PaintPlan zeroAlpha =
                new PaintPlanBuilder()
                        .add(PaintCommand.rect(new LayoutBox(0, 0, 40, 20), LOCAL, 0x00ff8040))
                        .build();
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
        glDepthMask(true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        draw(owner, renderer, zeroAlpha, unblended);
        int[] zeroPixels = read(fbo);
        int zeroRgb = 0;
        for (int i = 0; i < zeroPixels.length; i += 4)
            if (zeroPixels[i] > 0) {
                zeroRgb++;
                int[] expected = {64, 32, 16, 0};
                for (int c = 0; c < 4; c++)
                    if (Math.abs(zeroPixels[i + c] - expected[c]) > 1)
                        throw new AssertionError("无blend零alpha直色丢失");
            }
        if (zeroRgb != lit) throw new AssertionError("无blend零alpha图元未输出RGB");
        System.out.println("unblendedAlphaZeroStraightRgb=true");
    }

    private void windingCull() throws Exception {
        for (int face : new int[] {GL_BACK, GL_FRONT}) {
            FontRenderContext.RasterState culledRaster =
                    new FontRenderContext.RasterState(
                            true,
                            GL_SRC_ALPHA,
                            GL_ONE_MINUS_SRC_ALPHA,
                            GL_ONE,
                            GL_ONE_MINUS_SRC_ALPHA,
                            GL_FUNC_ADD,
                            GL_FUNC_ADD,
                            new float[] {0, 0, 0, 0},
                            false,
                            stencil,
                            stencil,
                            new boolean[] {true, true, true, true},
                            new float[] {0, 1},
                            true,
                            face,
                            GL_CCW,
                            false,
                            0,
                            0);
            FontRenderContext culled =
                    new FontRenderContext(
                            target.projection,
                            fbo,
                            H,
                            true,
                            GL_LESS,
                            false,
                            true,
                            target.scissor(),
                            target.state,
                            GLContext.getCapabilities(),
                            1,
                            7,
                            culledRaster);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
            glDepthMask(true);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
            draw(owner, renderer, plan, culled);
            if (count(read(fbo)) != (face == GL_BACK ? glyphPixels : 0))
                throw new AssertionError("world字体原版负绕序cull不符 face=" + face);
            if (glIsEnabled(GL_CULL_FACE)) throw new AssertionError("caller cull未恢复入口");
        }
        System.out.println("vanillaNegativeWindingBackFrontCull=true");
    }

    private void samplingViews() throws Exception {
        TextLayout.Glyph emoji = null;
        for (TextLayout.Line line : text.lines())
            for (TextLayout.Glyph g : line.glyphs()) if (g.color) emoji = g;
        if (emoji == null) throw new AssertionError("彩色view probe没有命中emoji");
        key = GlyphKey.of(emoji);
        bitmap = owner.rasterizer().peek(key);
        if (bitmap == null) throw new AssertionError("CPU bitmap缺失");
        try (GlGlyphAtlas.Frame frame =
                owner.atlas()
                        .beginFrame(
                                Arrays.asList(key), true, GlGlyphAtlas.SamplingView.PREMULT_RGBA)) {
            frame.freeze();
            GlGlyphAtlas.Resident resident = frame.lookup(key);
            if (resident == null) throw new AssertionError("baselinepremult没ready");
            premultTarget = owner.atlas().resolve(resident.resource, resident.generation).target;
        }
        if (premultTarget
                != (owner.settings().mode == GlGlyphAtlas.Mode.TEXTURE_ARRAY
                        ? GL_TEXTURE_2D_ARRAY
                        : GL_TEXTURE_2D))
            throw new AssertionError("premult实际target被straightview改变");
        beforeBytes = owner.atlas().stats().gpuBytes;
        rasterized = owner.rasterizer().stats().rasterized;
        try (GlGlyphAtlas.Frame frame =
                owner.atlas()
                        .beginFrame(
                                Arrays.asList(key),
                                true,
                                GlGlyphAtlas.SamplingView.STRAIGHT_RGBA)) {
            frame.prepare(
                    owner.rasterizer(),
                    new GlGlyphAtlas.UploadBudget(1024 * 1024, 1000000000, 32),
                    false);
            frame.freeze();
            GlGlyphAtlas.Resident resident = frame.lookup(key);
            if (resident == null
                    || resident.samplingView != GlGlyphAtlas.SamplingView.STRAIGHT_RGBA)
                throw new AssertionError("straightview未ready");
            int textureId = owner.atlas().resolve(resident.resource, resident.generation).id;
            int page = owner.settings().atlas.pageSize;
            ByteBuffer gpu =
                    ByteBuffer.allocateDirect(page * page * 4)
                            .order(java.nio.ByteOrder.nativeOrder());
            try {
                glBindTexture(GL_TEXTURE_2D, textureId);
                glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, gpu);
                byte[] nativeBytes = bitmap.pixels();
                int bx = Math.round(resident.u0 * page), by = Math.round(resident.v0 * page);
                for (int y = 0; y < bitmap.height; y++)
                    for (int x = 0; x < bitmap.width; x++) {
                        int i = (y * bitmap.width + x) * 4, a = nativeBytes[i + 3] & 255;
                        for (int c = 0; c < 4; c++) {
                            int expected =
                                    c == 3
                                            ? a
                                            : a == 0
                                                    ? 0
                                                    : Math.min(
                                                            255,
                                                            ((nativeBytes[i + c] & 255) * 255
                                                                            + a / 2)
                                                                    / a);
                            if ((gpu.get(((by + y) * page + bx + x) * 4 + c) & 255) != expected)
                                throw new AssertionError("straightview真实纹理不同");
                        }
                    }
            } finally {
                glBindTexture(GL_TEXTURE_2D, 0);
            }
        }
        TextLayout.Glyph mono = text.lines().get(0).glyphs().get(0);
        GlyphKey monoKey = GlyphKey.of(mono);
        try (GlGlyphAtlas.Frame frame =
                owner.atlas()
                        .beginFrame(
                                Arrays.asList(monoKey),
                                true,
                                GlGlyphAtlas.SamplingView.STRAIGHT_RGBA)) {
            frame.prepare(
                    owner.rasterizer(),
                    new GlGlyphAtlas.UploadBudget(1024 * 1024, 1000000000, 32),
                    false);
            frame.freeze();
            GlGlyphAtlas.Resident resident = frame.lookup(monoKey);
            if (resident == null || resident.color)
                throw new AssertionError("mono whiteview未ready");
            int page = owner.settings().atlas.pageSize;
            ByteBuffer gpu =
                    ByteBuffer.allocateDirect(page * page * 4)
                            .order(java.nio.ByteOrder.nativeOrder());
            try {
                glBindTexture(
                        GL_TEXTURE_2D,
                        owner.atlas().resolve(resident.resource, resident.generation).id);
                glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, gpu);
                int bx = Math.round(resident.u0 * page) - 1,
                        by = Math.round(resident.v0 * page) - 1;
                for (int y = 0; y < resident.height + 2; y++)
                    for (int x = 0; x < resident.width + 2; x++)
                        for (int c = 0; c < 3; c++)
                            if ((gpu.get(((by + y) * page + bx + x) * 4 + c) & 255) != 255)
                                throw new AssertionError("MASK straight RGB/gutter不恒白");
            } finally {
                glBindTexture(GL_TEXTURE_2D, 0);
            }
        }
        if (owner.atlas().stats().gpuBytes - beforeBytes
                != 2L * owner.settings().atlas.pageSize * owner.settings().atlas.pageSize * 4)
            throw new AssertionError("straightgroup必须各只有2D一layer bytes");
        if (owner.rasterizer().stats().rasterized != rasterized
                || owner.atlas().stats().gpuBytes <= beforeBytes
                || owner.atlas().stats().gpuBytes > owner.settings().atlas.gpuBytes)
            throw new AssertionError("双view共享CPU/GPUbudget失败");
    }

    private void alternatingViews() throws Exception {
        // 真实回归：两view交替，每consumer仅一paddedrow/一call，partial必须各自推进到ready。
        try (GlGlyphAtlas alternating =
                new GlGlyphAtlas(
                        new Lwjgl2GlApi(), owner.settings().mode, owner.settings().atlas)) {
            int rowBytes = (bitmap.width + 2) * 4, hits = 0;
            int turns = 2 * (bitmap.height + 3);
            long beforeRaster = owner.rasterizer().stats().rasterized;
            for (int turn = 0; turn < turns; turn++)
                try (GlGlyphAtlas.Frame frame =
                        alternating.beginFrame(
                                Arrays.asList(key),
                                true,
                                GlGlyphAtlas.SamplingView.values()[turn % 2])) {
                    long uploaded = alternating.stats().uploadedBytes,
                            calls = alternating.stats().uploadCalls;
                    frame.prepare(
                            owner.rasterizer(),
                            new GlGlyphAtlas.UploadBudget(rowBytes, 1000000000, 1),
                            false);
                    frame.freeze();
                    if (frame.lookup(key) != null) hits++;
                    if (alternating.stats().uploadedBytes - uploaded > rowBytes
                            || alternating.stats().uploadCalls - calls > 1)
                        throw new AssertionError("跨view超单consumer预算");
                }
            if (hits < 2
                    || alternating.stats().residents != 2
                    || owner.rasterizer().stats().rasterized != beforeRaster)
                throw new AssertionError(
                        "合法一row预算交替view永久饥饿 hits="
                                + hits
                                + " residents="
                                + alternating.stats().residents);
            System.out.println(
                    "alternatingViewOneRowProgress=true sharedCpuBitmap=true partialResidentViews="
                            + alternating.stats().residents);
        }
    }

    private void historicalStencil() throws Exception {
        FontRenderContext.RasterState rejectRaster =
                new FontRenderContext.RasterState(
                        true,
                        GL_SRC_ALPHA,
                        GL_ONE_MINUS_SRC_ALPHA,
                        GL_ONE,
                        GL_ONE_MINUS_SRC_ALPHA,
                        GL_FUNC_ADD,
                        GL_FUNC_ADD,
                        new float[] {0, 0, 0, 0},
                        true,
                        new FontRenderContext.StencilFace(
                                GL_NEVER, 7, 255, 255, GL_KEEP, GL_KEEP, GL_KEEP),
                        new FontRenderContext.StencilFace(
                                GL_NEVER, 7, 255, 255, GL_KEEP, GL_KEEP, GL_KEEP),
                        new boolean[] {true, true, true, true},
                        new float[] {0, 1},
                        false,
                        GL_BACK,
                        GL_CCW,
                        false,
                        0,
                        0);
        FontRenderContext reject =
                new FontRenderContext(
                        target.projection,
                        fbo,
                        H,
                        true,
                        GL_LESS,
                        false,
                        true,
                        target.scissor(),
                        target.state,
                        GLContext.getCapabilities(),
                        1,
                        7,
                        rejectRaster);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
        glDisable(GL_STENCIL_TEST);
        glDepthMask(true);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        draw(owner, renderer, solid, reject);
        if (count(read(fbo)) != 0) throw new AssertionError("历史stencil拒绝未应用");
        if (glIsEnabled(GL_STENCIL_TEST)) throw new AssertionError("历史stencil未恢复actual入口");
        System.out.println("typedHistoricalStencilRejected=true");
        System.out.println(
                (owner.settings().mode == GlGlyphAtlas.Mode.TEXTURE_ARRAY
                                ? "MODE_ARRAY_PREMULT_AND_STRAIGHT_2D_SHARED_BUDGET"
                                : "MODE_2D_PREMULT_AND_STRAIGHT_2D_SHARED_BUDGET")
                        + " premultTarget="
                        + premultTarget
                        + " straightTarget="
                        + GL_TEXTURE_2D
                        + " GPUbytes="
                        + owner.atlas().stats().gpuBytes
                        + " CPUrasterUnchanged="
                        + (owner.rasterizer().stats().rasterized == rasterized));
        System.out.println(
                "typedHistoricalBlend=true straightViewActualTexture=true maskWhiteGutter=true nativeCacheShared=true totalGPUBytes="
                        + owner.atlas().stats().gpuBytes);
    }

    public static void main(String[] args) throws Exception {
        new Lwjgl2HostScenario().run(args);
    }
}
