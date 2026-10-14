package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.ui.scene.text.TextScript;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.GlfwTestEnvironment;
import club.heiqi.uilib.testing.GlfwTestEnvironment.Profile;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlGlyphAtlas;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.glyph.AsyncGlyphRasterizer;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontEngine;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;
import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.FramebufferCoordinates;
import club.heiqi.uilib.host.client.font.FramebufferSceneRenderer;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.SceneFramePipeline;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.ScenePresentation;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import static org.lwjgl.opengl.GL32C.*;

/** 不启动MC，使用生产共享owner/scene pipeline/GLconsumer验证原生像素与生命周期。 */
public final class ClientFontServicesScenario extends TestApplication {
    private static final int W = 301, H = 181;

    private static final class GenerationFonts extends ClassLoader {
        boolean retired;

        GenerationFonts() {
            super(ClientFontServicesScenario.class.getClassLoader());
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (retired) throw new IllegalStateException("退休源不能读取新包");
            return super.getResourceAsStream(name);
        }
    }

    private static final class Host implements SceneHost {
        final ClientFontServices owner;
        final FramebufferSceneRenderer screen;
        int width = W, height = H;

        Host(ClientFontServices owner) {
            this.owner = owner;
            screen = new FramebufferSceneRenderer(owner);
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public long metricsRevision() {
            return owner.fonts().generation();
        }

        @Override
        public void replay(PaintPlan plan) {
            screen.replay(plan, width, height);
        }
    }

    private static int[] read() {
        ByteBuffer buffer = MemoryUtil.memAlloc(W * H * 4);
        try {
            glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            int[] values = new int[W * H];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = ((H - 1 - y) * W + x) * 4;
                    values[y * W + x] =
                            ((buffer.get(i + 3) & 255) << 24)
                                    | ((buffer.get(i) & 255) << 16)
                                    | ((buffer.get(i + 1) & 255) << 8)
                                    | (buffer.get(i + 2) & 255);
                }
            return values;
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private static void image(File file, int[] values) throws Exception {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, W, H, values, 0, W);
        ImageIO.write(image, "png", file);
    }

    private static void clear() {
        boolean enabled = glIsEnabled(GL_SCISSOR_TEST);
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        if (enabled) glEnable(GL_SCISSOR_TEST);
    }

    private static void coordinates() {
        for (int height : new int[] {1, 181, 2161})
            for (int y = 0; y < height; y++)
                if (FramebufferCoordinates.mouseY(height, height - y - 1) != y)
                    throw new AssertionError("raw Mouse Y丢原生像素");
        float[] m = FramebufferCoordinates.orthographic(W, H);
        float left = m[12], top = m[13], right = m[0] * W + m[12], bottom = m[5] * H + m[13];
        if (Math.abs(left + 1) > 0.00001
                || Math.abs(top - 1) > 0.00001
                || Math.abs(right - 1) > 0.00001
                || Math.abs(bottom + 1) > 0.00001) throw new AssertionError("正交映射不是framebuffer");
        LayoutBox clip =
                FramebufferCoordinates.viewportClip(W, H, true, new int[] {-2, H - 8, 7, 10});
        if (clip.x != 0 || clip.y != 0 || clip.width != 5 || clip.height != 8)
            throw new AssertionError("scissor交错");
        if (FramebufferCoordinates.viewportClip(
                                W,
                                H,
                                true,
                                new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE, 30, 40})
                        .width
                != 0) throw new AssertionError("clip溢出");
    }

    private GlfwTestEnvironment environment;
    private ClientFontServices owner;
    private GenerationFonts initial;
    private TextStyle style;
    private GlyphKey old;
    private Host host;
    private final int[] clicked = {0};
    private ReactiveDomain domain;
    private SceneRuntime runtime;
    private ScenePageSession session;
    private SceneNode[] nodes;
    private SceneFramePipeline pipeline;
    private final ScenePresentation[] presentation = {null};
    private PaintPlan white;
    private int lit, frames;
    private long cached;
    private File output;

    @Override
    protected void setup() throws Exception {
        // 无GL构造/度量先执行；宿主的释放位先登记，实际窗口随后按需创建。
        onClose(
                () -> {
                    if (environment != null) environment.close();
                });
        initial = new GenerationFonts();
        owner =
                new ClientFontServices(
                        initial,
                        1,
                        ClientFontServices.Settings.defaults(),
                        IsolatedFontEngine.shared(),
                        new Lwjgl3GlApi(),
                        GlProgramProvider.NONE);
        onClose(
                () -> {
                    owner.close();
                    require(owner.awaitRasterizerClose(10000), "退出worker未清理");
                });
    }

    @Override
    protected void runTests() throws Exception {
        String[] args = arguments();
        output = new File(args.length == 0 ? "build/font-gl-probe/client-output" : args[0]);
        require(output.isDirectory() || output.mkdirs(), "不能建输出目录");
        scenario("原生像素坐标换算", ClientFontServicesScenario::coordinates);
        scenario("无context度量与组件文本policy", this::lazyMetrics);
        environment = new GlfwTestEnvironment();
        environment.window(W, H, "client shared fonts", Profile.CORE, 2, false, false);
        initializeScene();
        scenario("单像素scissor与宿主恢复", this::onePixel);
        scenario("ready通知无需重布局", this::readyWithoutLayout);
        scenario("焦点响应绑定经真实GPU像素观察", this::focusReplay);
        scenario("范围进入与返回经真实GPU回放", this::scopeReplay);
        scenario("Tab历史适配与导航高亮经真实GPU观察", this::navigationReplay);
        scenario("ESC消费关闭及焦点恢复经真实GPU观察", this::escapeReplay);
        scenario("标准控件浅深主题与限高全文提示真实像素", this::standardControls);
        scenario("导航显露后的正式按钮真实像素与命中", this::focusReveal);
        scenario("单行字段真实整形与选区光标GPU像素", this::textFieldGeometry);
        scenario("单行字段IME与只读状态GPU像素", this::textFieldImeReadOnly);
        scenario("多行真实字体硬换行软换行末尾空行与源簇", this::textAreaFontLines);
        scenario("多行跨行拖选视觉导航及光标真实GPU像素", this::textAreaSelection);
        scenario("多行IME只读滚动裁剪动态宽度与生命周期GPU像素", this::textAreaImeScroll);
        scenario("点击滚动与动态resize", this::inputAndResize);
        scenario("字体来源退休与旧glyph重开", this::reloadSources);
        scenario("screen关闭保留sharedowner", this::screenOwnership);
        scenario("共享资源关闭失败仍清理并保留首错", this::closeFailures);
        scenario("应用退出与幂等owner关闭", this::closeOwner);
    }

    private void lazyMetrics() throws Exception {
        if (owner.hasGlResources() || owner.rasterizer().stats().started)
            throw new AssertionError("init启动GL/worker");
        style = new TextStyle("default", 20, 0xfff0d050);
        TextLayout warm = owner.fonts().layout("中文 e\u0301 👩🏽‍💻", style, W);
        if (owner.hasGlResources() || owner.rasterizer().stats().started)
            throw new AssertionError("measure启动GL/worker");
        // facade必须保留字体相关意图解析；公式直接消费这个入口，不会再由layout补解析。
        for (TextScript intent :
                new TextScript[] {
                    TextScript.NONE.appendEm(9),
                    TextScript.NONE.appendScript(true)
                }) {
            TextStyle requested = style.withScript(intent);
            TextStyle expected = owner.fonts().resolveStyle(requested);
            TextStyle actual = owner.resolveStyle(requested);
            require(expected.logicalEm < requested.logicalEm && expected.script.steps().isEmpty(),
                    "真实字体对照应解析字号/上标意图");
            require(actual.equals(expected), "共享facade丢失实际字号、基线或字体代际事实");
        }
        old = GlyphKey.of(warm.lines().get(0).glyphs().get(0));
        TextLayout normal = owner.layout("中文 中文 中文", style, 40, TextPolicy.NORMAL);
        TextLayout single = owner.layout("中文 中文 中文", style, 40, TextPolicy.SINGLE_LINE);
        if (normal.lines().size() <= 1 || single.lines().size() != 1)
            throw new AssertionError("共享facade丢失组件4argpolicy");
    }

    private void initializeScene() throws Exception {
        // owner首次GLconsumer自行创建caps；此前构造和真实measure均无context。
        host = new Host(owner);
        domain = ReactiveDomain.direct();
        nodes = new SceneNode[3];
        session =
                ScenePageSession.open(
                        domain,
                        host,
                        owner,
                        ctx -> {
                            runtime = ctx.runtime();
                            ctx.own(host.screen);
                            nodes[0] =
                                    new SceneNode()
                                            .size(Size.fill(1), Size.fill(1))
                                            .background(0xff182030);
                            nodes[1] =
                                    new SceneNode()
                                            .text("中文 click e\u0301 👩🏽‍💻")
                                            .style(style)
                                            .size(Size.fill(1), Size.natural())
                                            .padding(4)
                                            .onClick(() -> clicked[0]++);
                            nodes[1].focusable(true)
                                    .bind(
                                            club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(
                                                    runtime.focusRouter().focused(),
                                                    (node, target) ->
                                                            node.background(
                                                                    target == node
                                                                            ? 0xff28b060
                                                                            : 0)));
                            nodes[2] =
                                    new SceneNode()
                                            .size(Size.fill(1), Size.fill(1))
                                            .scrollable(true)
                                            .scrollStep(32);
                            for (int i = 0; i < 30; i++)
                                nodes[2].add(
                                        new SceneNode()
                                                .text("行 " + i + " 原生像素")
                                                .style(style)
                                                .size(Size.fill(1), Size.natural()));
                            nodes[0].add(nodes[1]).add(nodes[2]);
                            ctx.mount(nodes[0]);
                        });
        pipeline = session.pipeline();
        onClose(session::close);
        owner.atlas();
        System.out.println(
                "vendor="
                        + glGetString(GL_VENDOR)
                        + " renderer="
                        + glGetString(GL_RENDERER)
                        + " version="
                        + glGetString(GL_VERSION)
                        + " Java="
                        + System.getProperty("java.version"));
        int texture = glGenTextures(), fbo = glGenFramebuffers();
        onClose(
                () -> {
                    glBindFramebuffer(GL_FRAMEBUFFER, 0);
                    glDeleteFramebuffers(fbo);
                    glDeleteTextures(texture);
                });
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(
                GL_TEXTURE_2D, 0, GL_RGBA8, W, H, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new AssertionError("FBO失败");
        glDisable(GL_DITHER);
        glDisable(GL_FRAMEBUFFER_SRGB);
        glViewport(7, 9, 80, 60);
        glEnable(GL_SCISSOR_TEST);
        glScissor(3, H - 70, 230, 65);
    }

    private void onePixel() throws Exception {
        int[] viewport = {7, 9, 80, 60}, beforeClip = {3, H - 70, 230, 65};
        domain.execute(() -> presentation[0] = pipeline.current());
        if (presentation[0].geometry.get(nodes[0]).box.width != W
                || presentation[0].geometry.get(nodes[0]).box.height != H)
            throw new AssertionError("布局使用scaledGUI尺寸");
        white =
                new PaintPlanBuilder()
                        .add(
                                PaintCommand.rect(
                                        new LayoutBox(0, 0, W, H), new LayoutBox(4, 0, 1, H), -1))
                        .build();
        clear();
        host.screen.replay(white, W, H);
        int[] pixels = read();
        lit = 0;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                boolean expected = x == 4 && y >= 5 && y < 70;
                if ((pixels[y * W + x] != 0) != expected)
                    throw new AssertionError("scissor/framebuffer onePixel不符 x=" + x + " y=" + y);
                if (pixels[y * W + x] != 0) lit++;
            }
        int[] actualViewport = new int[4], actualClip = new int[4];
        glGetIntegerv(GL_VIEWPORT, actualViewport);
        glGetIntegerv(GL_SCISSOR_BOX, actualClip);
        if (!Arrays.equals(viewport, actualViewport)
                || !Arrays.equals(beforeClip, actualClip)
                || !glIsEnabled(GL_SCISSOR_TEST))
            throw new AssertionError("宿主viewport/scissor恢复失败");
    }

    private void readyWithoutLayout() throws Exception {
        glDisable(GL_SCISSOR_TEST);
        long end = System.nanoTime() + 15000000000L;
        frames = 0;
        for (; ; ) {
            clear();
            session.frame();
            domain.execute(
                    () -> {
                        if (pipeline.current() != presentation[0])
                            throw new AssertionError("ready导致重布局");
                    });
            if (owner.rasterizer().stats().pending == 0
                    && owner.rasterizer().stats().rasterized > 0
                    && owner.atlas().stats().residents > 0) {
                session.frame();
                break;
            }
            if (System.nanoTime() > end) throw new AssertionError("shared字形未ready");
            Thread.sleep(2);
            frames++;
        }
        if (owner.readyVersion() == 0) throw new AssertionError("宿主未显式pump ready");
        image(new File(output, "scene-native.png"), read());
    }

    private void focusReplay() throws Exception {
        domain.execute(
                () ->
                        require(
                                runtime.focusRouter().request(runtime.captureMount(nodes[1])),
                                "GPU焦点请求"));
        clear();
        session.frame();
        int[] focused = read();
        require(focused[W + 1] == 0xff28b060, "焦点绑定未进入真实FBO像素");
        domain.execute(() -> runtime.focusRouter().clear());
        clear();
        session.frame();
        int[] cleared = read();
        require(cleared[W + 1] == 0xff182030, "清除焦点未恢复背景");
        require(clicked[0] == 0, "焦点请求不能激活业务");
    }

    private void scopeReplay() throws Exception {
        final club.heiqi.uilib.ui.scene.runtime.MountHandle[] layer = {null};
        domain.execute(
                () -> {
                    runtime.focusRouter().request(runtime.captureMount(nodes[1]));
                    SceneNode target =
                            new SceneNode().focusable(true).size(Size.fixed(3), Size.fixed(3));
                    SceneNode overlay =
                            new SceneNode()
                                    .modal(true)
                                    .initialFocus(target)
                                    .size(Size.fill(1), Size.fill(1))
                                    .bind(
                                            club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(
                                                    runtime.focusRouter().focused(),
                                                    (node, focus) ->
                                                            node.background(
                                                                    focus == target
                                                                            ? 0xff4028b0
                                                                            : 0xff404040)))
                                    .add(target);
                    layer[0] = runtime.attach(null, overlay);
                });
        try {
            clear();
            session.frame();
            require(read()[W + 1] == 0xff4028b0, "模态初始选择未进入FBO");
        } finally {
            domain.execute(() -> layer[0].detach());
        }
        clear();
        session.frame();
        require(read()[W + 1] == 0xff28b060, "返回原焦点未恢复实际像素");
        domain.execute(() -> runtime.focusRouter().clear());
        clear();
        session.frame();
    }

    private void navigationReplay() throws Exception {
        final club.heiqi.uilib.ui.scene.runtime.MountHandle[] second = {null};
        domain.execute(
                () -> {
                    runtime.focusRouter().request(runtime.captureMount(nodes[1]));
                    second[0] =
                            runtime.attach(
                                    null,
                                    new SceneNode()
                                            .focusable(true)
                                            .size(Size.fixed(1), Size.fixed(1)));
                });
        club.heiqi.uilib.ui.scene.host.SceneTabInputAdapter keys =
                new club.heiqi.uilib.ui.scene.host.SceneTabInputAdapter(session::navigation);
        keys.availability(true);
        keys.historyDrained(false, false, false);
        try {
            keys.tab(true, false);
            clear();
            session.frame();
            require(read()[W + 1] == 0xff182030, "Tab离开原目标后实际高亮未消失");
            keys.shift(true, true);
            keys.tab(true, true);
            clear();
            session.frame();
            require(read()[W + 1] == 0xff28b060, "Shift+Tab历史repeat未恢复高亮");
            keys.availability(false);
            keys.availability(true);
            keys.tab(true, true);
            clear();
            session.frame();
            require(read()[W + 1] == 0xff28b060, "失焦尾部repeat不能导航");
        } finally {
            domain.execute(
                    () -> {
                        second[0].detach();
                        runtime.focusRouter().clear();
                    });
        }
        clear();
        session.frame();
    }

    private void escapeReplay() throws Exception {
        final club.heiqi.uilib.ui.scene.runtime.MountHandle[] layer = {null};
        final club.heiqi.uilib.ui.reactive.Signal<Boolean>[] visible =
                new club.heiqi.uilib.ui.reactive.Signal[1];
        final int[] fallback = {0};
        domain.execute(
                () -> {
                    visible[0] = domain.signal(true);
                    runtime.focusRouter().request(runtime.captureMount(nodes[1]));
                    SceneNode target =
                            new SceneNode().focusable(true).size(Size.fixed(3), Size.fixed(3));
                    SceneNode overlay =
                            new SceneNode()
                                    .modal(true)
                                    .initialFocus(target)
                                    .size(Size.fill(1), Size.fill(1))
                                    .bind(
                                            club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(
                                                    visible[0], SceneNode::visible))
                                    .bind(
                                            club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(
                                                    runtime.focusRouter().focused(),
                                                    (node, focus) ->
                                                            node.background(
                                                                    focus == target
                                                                            ? 0xff4028b0
                                                                            : 0xff404040)))
                                    .onEscape(
                                            () -> {
                                                visible[0].set(false);
                                                return true;
                                            })
                                    .add(target);
                    layer[0] = runtime.attach(null, overlay);
                });
        club.heiqi.uilib.ui.scene.host.SceneEscapeInputAdapter keys =
                new club.heiqi.uilib.ui.scene.host.SceneEscapeInputAdapter(
                        () -> session.escape(() -> fallback[0]++));
        keys.availability(true);
        keys.historyDrained(false);
        try {
            clear();
            session.frame();
            require(read()[W + 1] == 0xff4028b0, "ESC前模态高亮");
            keys.key(true, false);
            clear();
            session.frame();
            require(read()[W + 1] == 0xff28b060 && fallback[0] == 0, "消费ESC应恢复原焦点而不fallback");
            keys.key(true, true);
            clear();
            session.frame();
            require(fallback[0] == 0, "repeat不能关闭页面");
            keys.key(false, false);
            keys.key(true, false);
            clear();
            session.frame();
            require(fallback[0] == 1, "新的未消费ESC才fallback");
        } finally {
            domain.execute(
                    () -> {
                        layer[0].detach();
                        runtime.focusRouter().clear();
                    });
        }
        clear();
        session.frame();
    }

    private void focusReveal() throws Exception {
        ReactiveDomain local = ReactiveDomain.direct();
        SceneNode[] n = new SceneNode[3];
        int[] clicks = {0};
        ScenePageSession page =
                ScenePageSession.open(
                        local,
                        host,
                        owner,
                        ctx -> {
                            n[0] =
                                    new SceneNode()
                                            .size(Size.fixed(120), Size.fixed(60))
                                            .scrollable(true);
                            n[1] =
                                    club.heiqi.uilib.ui.scene.control.SceneButton.create(
                                                    ctx,
                                                    () -> "首项",
                                                    () ->
                                                            club.heiqi.uilib.ui.scene.control
                                                                    .SceneTheme.dark(),
                                                    () -> {})
                                            .size(Size.fixed(120), Size.fixed(40));
                            n[2] =
                                    club.heiqi.uilib.ui.scene.control.SceneButton.create(
                                                    ctx,
                                                    () -> "后项",
                                                    () ->
                                                            club.heiqi.uilib.ui.scene.control
                                                                    .SceneTheme.light(),
                                                    () -> clicks[0]++)
                                            .size(Size.fixed(120), Size.fixed(40));
                            ctx.mount(n[0].add(n[1]).add(n[2]));
                        });
        try {
            page.frame();
            page.navigation(club.heiqi.uilib.ui.scene.input.SceneNavigationEvent.NEXT);
            clear();
            page.frame();
            local.execute(
                    () ->
                            require(
                                    page.pipeline().current().geometry.get(n[0]).scrollOffset == 20,
                                    "GPU导航最小滚动错误"));
            require(
                    read()[55 * W + 110]
                            == club.heiqi.uilib.ui.scene.control.SceneTheme.light().button,
                    "显露按钮未在实际像素呈现");
            page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 110, 55));
            page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 110, 55));
            page.frame();
            require(clicks[0] == 1, "显露后GPU输入未使用同代几何");
        } finally {
            page.close();
        }
    }

    private void standardControls() throws Exception {
        ReactiveDomain local = ReactiveDomain.direct();
        club.heiqi.uilib.ui.reactive.Signal<club.heiqi.uilib.ui.scene.control.SceneTheme>[] theme =
                new club.heiqi.uilib.ui.reactive.Signal[1];
        SceneNode[] button = {null};
        final String caption = "标准按钮长文案：浅色深色切换、完整行省略与全文提示";
        ScenePageSession page =
                ScenePageSession.open(
                        local,
                        host,
                        owner,
                        ctx -> {
                            theme[0] =
                                    local.signal(
                                            club.heiqi.uilib.ui.scene.control.SceneTheme.dark());
                            button[0] =
                                    club.heiqi.uilib.ui.scene.control.SceneButton.create(
                                                    ctx, () -> caption, theme[0], () -> {})
                                            .size(Size.fixed(145), Size.fixed(44));
                            ctx.mount(button[0]);
                        });
        try {
            clear();
            page.frame();
            int dark = read()[10 * W + 10];
            local.execute(
                    () -> {
                        page.runtime().focusRouter().clear();
                        theme[0].set(club.heiqi.uilib.ui.scene.control.SceneTheme.light());
                    });
            clear();
            page.frame();
            int light = read()[10 * W + 10];
            require(dark != light, "主题切换未改变实际GL颜色");
            page.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.move(20, 20));
            clear();
            page.frame();
            final ScenePresentation[] rendered = {null};
            local.execute(() -> rendered[0] = page.pipeline().current());
            require(
                    rendered[0].paint.commands().stream()
                            .anyMatch(
                                    c ->
                                            c.kind == PaintCommand.Kind.TEXT
                                                    && c.text.originalText.equals(caption)
                                                    && c.box.y >= 44),
                    "完整文案提示未进入实际生产绘制计划");
            local.execute(
                    () -> {
                        TextLayout displayed = rendered[0].geometry.get(button[0]).text;
                        require(
                                displayed.lines().stream()
                                        .anyMatch(
                                                l ->
                                                        l.glyphs().stream()
                                                                .anyMatch(
                                                                        g ->
                                                                                g.sourceStart
                                                                                                == g.sourceEnd
                                                                                        && g.logicalStart
                                                                                                == g.logicalEnd)),
                                "真实字体限高缺少合成省略glyph");
                        require(displayed.originalText.equals(caption), "省略修改了完整源文案");
                    });
            page.frame();
            if (glGetError() != GL_NO_ERROR) throw new AssertionError("标准控件GL error");
        } finally {
            page.close();
        }
    }

    /** 新页面只借用共享字体owner，不取得screen或编辑状态的所有权。 */
    private final class FieldFixture implements AutoCloseable {
        final ReactiveDomain local = ReactiveDomain.direct();
        final SceneEditState[] state = {null};
        final SceneNode[] field = {null};
        final Signal<SceneTheme>[] theme = new Signal[1];
        final Signal<Boolean>[] readOnly = new Signal[1];
        final int[] submits = {0};
        final String[] submitted = {null};
        final ScenePageSession page;
        final int previousWidth = host.width, previousHeight = host.height;

        FieldFixture() {
            host.width = W;
            host.height = H;
            page =
                    ScenePageSession.open(
                            local,
                            host,
                            owner,
                            ctx -> {
                                state[0] = new SceneEditState(local, "");
                                theme[0] = local.signal(SceneTheme.light());
                                readOnly[0] = local.signal(false);
                                field[0] =
                                        SceneTextField.create(
                                                ctx,
                                                state[0],
                                                theme[0],
                                                readOnly[0],
                                                request -> {
                                                    submits[0]++;
                                                    submitted[0] = request.text;
                                                });
                                ctx.mount(field[0]);
                            });
            local.execute(
                    () ->
                            require(
                                    page.runtime()
                                            .focusRouter()
                                            .request(page.runtime().captureMount(field[0])),
                                    "GPU字段聚焦失败"));
        }

        ScenePresentation current() {
            final ScenePresentation[] value = {null};
            local.execute(() -> value[0] = page.pipeline().current());
            return value[0];
        }

        SceneGeometryView.Entry entry() {
            return current().geometry.get(field[0]);
        }

        SceneEditState.Snapshot snapshot() {
            final SceneEditState.Snapshot[] value = {null};
            local.execute(() -> value[0] = state[0].snapshot().get());
            return value[0];
        }

        // 宿主typed输入先进入pipeline队列；观察状态/clipboard前先frame结算，再读真实FBO。
        int[] pixels() throws Exception {
            long deadline = System.nanoTime() + 15000000000L;
            for (; ; ) {
                clear();
                page.frame();
                if (owner.rasterizer().stats().pending == 0) {
                    clear();
                    page.frame();
                    return read();
                }
                require(
                        System.nanoTime() < deadline && owner.rasterizer().failures().isEmpty(),
                        "字段真实字形未ready");
                Thread.sleep(2);
            }
        }

        void command(SceneEditEvent.Command command) {
            page.edit(SceneEditEvent.command(command, false, null));
        }

        @Override
        public void close() {
            page.close();
            host.width = previousWidth;
            host.height = previousHeight;
        }
    }

    private static int colorPixels(int[] pixels, LayoutBox box, int color) {
        int count = 0;
        for (int y = Math.max(0, box.y); y < Math.min(H, box.y + box.height); y++)
            for (int x = Math.max(0, box.x); x < Math.min(W, box.x + box.width); x++)
                if (pixels[y * W + x] == color) count++;
        return count;
    }

    private static LayoutBox fieldInner(SceneGeometryView.Entry entry, int padding) {
        return new LayoutBox(
                entry.box.x + padding,
                entry.box.y + padding,
                Math.max(0, entry.box.width - padding * 2),
                Math.max(0, entry.box.height - padding * 2));
    }

    private static TextLayout.Cluster sourceCluster(TextLayout text, int sourceOffset) {
        for (TextLayout.Line line : text.lines())
            for (TextLayout.Cluster cluster : line.clusters())
                if (cluster.sourceStart == sourceOffset) return cluster;
        throw new AssertionError("真实排版缺少源簇 " + sourceOffset);
    }

    private void textFieldGeometry() throws Exception {
        try (FieldFixture f = new FieldFixture()) {
            final String input = "fi中文😀e\u0301";
            final String[] clipboard = {input};
            SceneEditEvent.Clipboard clip =
                    new SceneEditEvent.Clipboard() {
                        public String read() {
                            return clipboard[0];
                        }

                        public void write(String value) {
                            clipboard[0] = value;
                        }
                    };
            f.page.edit(SceneEditEvent.command(SceneEditEvent.Command.PASTE, false, clip));
            int[] pixels = f.pixels();
            SceneGeometryView.Entry entry = f.entry();
            LayoutBox inner = fieldInner(entry, SceneTheme.light().padding);
            require(f.snapshot().text.equals(input) && f.submits[0] == 0, "粘贴不应隐含确认提交");
            require(
                    entry.text.originalText.equals(input)
                            && entry.text.lines().stream()
                                    .anyMatch(line -> !line.glyphs().isEmpty()),
                    "字段未使用真实整形glyph");
            // FBO中必须有正文墨迹，不能仅凭PaintCommand出现便声称GPU文字绘制成功。
            int ink = 0;
            for (int y = inner.y; y < inner.y + inner.height; y++)
                for (int x = inner.x; x < inner.x + inner.width; x++) {
                    int pixel = pixels[y * W + x];
                    if (pixel != SceneTheme.light().panel && pixel != SceneTheme.light().focus)
                        ink++;
                }
            require(ink > 0, "真实字段正文没有GPU墨迹");
            f.page.edit(SceneEditEvent.command(SceneEditEvent.Command.LEFT, true, null));
            f.page.edit(SceneEditEvent.command(SceneEditEvent.Command.COPY, false, clip));
            pixels = f.pixels();
            require(clipboard[0].equals("e\u0301"), "Shift+LEFT/COPY拆开组合字符");
            require(colorPixels(pixels, inner, SceneTheme.light().hover) > 0, "选区未进入真实FBO颜色");
            f.command(SceneEditEvent.Command.HOME);
            f.pixels();
            entry = f.entry();
            inner = fieldInner(entry, SceneTheme.light().padding);
            TextLayout.Cluster chinese = sourceCluster(entry.text, 2);
            int clickX = inner.x + Math.round(chinese.x),
                    clickY =
                            inner.y
                                    + Math.max(
                                            1,
                                            Math.round(entry.text.lines().get(0).lineHeight / 2));
            f.page.pointer(ScenePointerEvent.down(0, clickX, clickY));
            f.pixels();
            require(f.snapshot().caret == 2 && f.snapshot().anchor == 2, "DOWN未按生产中文簇源边界定位");
            TextLayout.Cluster end = sourceCluster(entry.text, 4);
            f.page.pointer(ScenePointerEvent.move(inner.x + Math.round(end.x), clickY));
            f.page.pointer(ScenePointerEvent.up(0, inner.x + Math.round(end.x), clickY));
            pixels = f.pixels();
            require(f.snapshot().anchor == 2 && f.snapshot().caret == 4, "MOVE/UP未保留DOWN anchor");
            require(colorPixels(pixels, inner, SceneTheme.light().hover) > 0, "生产簇拖选未绘入GPU");
            // 光标变化与真实区域变化分别消费一次显露意图；真实GPU核对长文与缩窄结果。
            f.command(SceneEditEvent.Command.END);
            f.page.edit(SceneEditEvent.text(" 中文长文用于水平滚动 中文长文用于水平滚动"));
            host.width = 110;
            f.pixels();
            f.command(SceneEditEvent.Command.HOME);
            f.command(SceneEditEvent.Command.END);
            pixels = f.pixels();
            entry = f.entry();
            inner = fieldInner(entry, SceneTheme.light().padding);
            int last = f.snapshot().text.length();
            TextLayout.Cluster trailing = null;
            for (TextLayout.Line line : entry.text.lines())
                for (TextLayout.Cluster c : line.clusters()) if (c.sourceEnd == last) trailing = c;
            require(trailing != null, "长文缺少末簇");
            int caretX = inner.x + (int) Math.floor(trailing.x + trailing.advance);
            require(inner.contains(caretX, inner.y), "末尾caret未显露在真实viewport");
            LayoutBox caretRegion = new LayoutBox(caretX, inner.y, 1, inner.height);
            require(
                    colorPixels(pixels, caretRegion, SceneTheme.light().focus) > 0,
                    "显露caret区域没有真实GPU焦点像素");
            require(
                    colorPixels(pixels, new LayoutBox(110, 0, W - 110, H), SceneTheme.light().focus)
                            == 0,
                    "字段caret越过viewport裁剪");
            f.local.execute(() -> f.theme[0].set(SceneTheme.dark()));
            host.width = 150;
            pixels = f.pixels();
            entry = f.entry();
            inner = fieldInner(entry, SceneTheme.dark().padding);
            require(
                    colorPixels(pixels, inner, SceneTheme.dark().panel) > 0
                            && colorPixels(pixels, inner, SceneTheme.light().panel) == 0,
                    "theme/resize未更新实际GPU颜色");
            TextLayout.Cluster resizedEnd = null;
            for (TextLayout.Line line : entry.text.lines())
                for (TextLayout.Cluster c : line.clusters())
                    if (c.sourceEnd == last) resizedEnd = c;
            require(resizedEnd != null, "主题resize丢失末簇");
            int resizedCaretX = inner.x + (int) Math.floor(resizedEnd.x + resizedEnd.advance);
            require(
                    inner.contains(resizedCaretX, inner.y)
                            && colorPixels(
                                            pixels,
                                            new LayoutBox(resizedCaretX, inner.y, 1, inner.height),
                                            SceneTheme.dark().focus)
                                    > 0,
                    "主题resize后的末尾caret没有留在真实GPU内区");
            require(f.snapshot().text.endsWith("水平滚动") && f.submits[0] == 0, "主题resize修改编辑状态或提交");
            host.width = 72;
            pixels = f.pixels();
            entry = f.entry();
            inner = fieldInner(entry, SceneTheme.dark().padding);
            int endX =
                    inner.x
                            + Math.round(
                                    entry.text
                                                    .lines()
                                                    .get(0)
                                                    .clusters()
                                                    .get(
                                                            entry.text
                                                                            .lines()
                                                                            .get(0)
                                                                            .clusters()
                                                                            .size()
                                                                    - 1)
                                                    .x
                                            + entry.text
                                                    .lines()
                                                    .get(0)
                                                    .clusters()
                                                    .get(
                                                            entry.text
                                                                            .lines()
                                                                            .get(0)
                                                                            .clusters()
                                                                            .size()
                                                                    - 1)
                                                    .advance);
            require(endX >= inner.x && endX < inner.x + inner.width, "缩窄窗口未一次显露稳定光标");
            require(colorPixels(pixels, inner, SceneTheme.dark().focus) > 0, "缩窄窗口光标被真实GPU裁掉");
            image(new File(output, "text-field-geometry.png"), pixels);
        }
    }

    private void textFieldImeReadOnly() throws Exception {
        try (FieldFixture f = new FieldFixture()) {
            f.page.edit(SceneEditEvent.text("fi中文😀e\u0301"));
            f.pixels();
            String draft = f.snapshot().text;
            f.page.edit(SceneEditEvent.composition("输入法", true));
            f.command(SceneEditEvent.Command.CONFIRM);
            int[] preedit = f.pixels();
            SceneGeometryView.Entry entry = f.entry();
            LayoutBox inner = fieldInner(entry, SceneTheme.light().padding);
            require(
                    f.submits[0] == 0 && f.snapshot().text.equals(draft) && f.snapshot().composing,
                    "组字确认写入业务或清掉preedit");
            require(entry.text.originalText.endsWith("输入法"), "preedit没有进入生产排版");
            TextLayout.Cluster preeditStart = sourceCluster(entry.text, draft.length());
            TextLayout.Line preeditLine = entry.text.lines().get(0);
            LayoutBox underline =
                    new LayoutBox(
                            inner.x + (int) Math.floor(preeditStart.x),
                            inner.y + (int) Math.floor(preeditLine.baseline + preeditLine.descent),
                            Math.max(1, (int) Math.ceil(preeditStart.advance)),
                            1);
            require(
                    colorPixels(preedit, underline, SceneTheme.light().focus) > 1,
                    "preedit下划线没有进入真实GPU区域");
            f.page.edit(SceneEditEvent.committed("整段中文"));
            f.command(SceneEditEvent.Command.CONFIRM);
            f.pixels();
            require(
                    f.submits[0] == 1
                            && f.submitted[0].equals(draft + "整段中文")
                            && !f.snapshot().composing,
                    "IME提交后的后续CONFIRM不是一次完整提交");
            f.command(SceneEditEvent.Command.UNDO);
            f.pixels();
            require(f.snapshot().text.equals(draft), "IME整段不是独立撤销步骤");
            f.command(SceneEditEvent.Command.REDO);
            f.pixels();
            f.local.execute(
                    () -> {
                        f.readOnly[0].set(true);
                        f.theme[0].set(SceneTheme.dark());
                    });
            SceneEditState identity = f.state[0];
            SceneEditState.Snapshot before = f.snapshot();
            f.page.edit(SceneEditEvent.text("拒绝写入"));
            f.command(SceneEditEvent.Command.BACKSPACE);
            f.command(SceneEditEvent.Command.PASTE);
            f.command(SceneEditEvent.Command.CONFIRM);
            f.pixels();
            require(
                    f.snapshot().text.equals(before.text)
                            && f.snapshot().revision == before.revision
                            && f.submits[0] == 1,
                    "只读写入或提交");
            f.command(SceneEditEvent.Command.SELECT_ALL);
            final String[] copied = {null};
            f.page.edit(
                    SceneEditEvent.command(
                            SceneEditEvent.Command.COPY,
                            false,
                            new SceneEditEvent.Clipboard() {
                                public String read() {
                                    throw new AssertionError("只读不能读clipboard");
                                }

                                public void write(String value) {
                                    copied[0] = value;
                                }
                            }));
            int[] pixels = f.pixels();
            entry = f.entry();
            inner = fieldInner(entry, SceneTheme.dark().padding);
            require(
                    copied[0].equals(before.text)
                            && colorPixels(pixels, inner, SceneTheme.dark().hover) > 0,
                    "只读复制或真实GPU选区失败");
            require(f.state[0] == identity, "只读主题切换替换了业务state实例");
            SceneEditState.Snapshot selected = f.snapshot();
            f.page.close();
            require(
                    f.state[0] == identity
                            && f.snapshot().text.equals(selected.text)
                            && f.snapshot().anchor == selected.anchor
                            && f.snapshot().caret == selected.caret
                            && f.snapshot().active == selected.active,
                    "卸载修改业务state草稿或选区");
            require(f.submits[0] == 1, "卸载隐含提交");
            image(new File(output, "text-field-ime-readonly.png"), pixels);
        }
    }

    /** 共用真实owner/页面/FBO；仅声明页面及观测，未另建编辑器或输入路由。 */
    private final class AreaFixture implements AutoCloseable {
        final ReactiveDomain local = ReactiveDomain.direct();
        final SceneEditState[] state = {null};
        final SceneNode[] area = {null};
        final Signal<Boolean>[] readOnly = new Signal[1];
        final Signal<SceneTheme>[] theme = new Signal[1];
        final int[] submits = {0};
        final int previousWidth = host.width, previousHeight = host.height;
        final ScenePageSession page;

        AreaFixture(String text) {
            host.width = W;
            host.height = H;
            page =
                    ScenePageSession.open(
                            local,
                            host,
                            owner,
                            ctx -> {
                                state[0] =
                                        new SceneEditState(
                                                local, text, SceneEditState.Mode.MULTI_LINE);
                                theme[0] = local.signal(SceneTheme.light());
                                readOnly[0] = local.signal(false);
                                area[0] =
                                        SceneTextArea.create(
                                                        ctx,
                                                        state[0],
                                                        theme[0],
                                                        readOnly[0],
                                                        request -> submits[0]++)
                                                .size(Size.fill(1), Size.fixed(140));
                                ctx.mount(new SceneNode().initialFocus(area[0]).add(area[0]));
                            });
        }

        SceneGeometryView.Entry entry() {
            final SceneGeometryView.Entry[] e = {null};
            local.execute(() -> e[0] = page.pipeline().current().geometry.get(area[0]));
            return e[0];
        }

        SceneEditState.Snapshot snapshot() {
            final SceneEditState.Snapshot[] s = {null};
            local.execute(() -> s[0] = state[0].snapshot().get());
            return s[0];
        }

        void command(SceneEditEvent.Command command) {
            page.edit(SceneEditEvent.command(command, false, null));
        }

        int[] pixels() throws Exception {
            long deadline = System.nanoTime() + 15000000000L;
            for (; ; ) {
                clear();
                page.frame();
                if (owner.rasterizer().stats().pending == 0) {
                    clear();
                    page.frame();
                    return read();
                }
                require(
                        System.nanoTime() < deadline && owner.rasterizer().failures().isEmpty(),
                        "Area真实字形未ready");
                Thread.sleep(2);
            }
        }

        public void close() {
            page.close();
            host.width = previousWidth;
            host.height = previousHeight;
        }
    }

    private void textAreaFontLines() throws Exception {
        textAreaSeparatorHit();
        textAreaControlMarkerHit();
        String[] inputs = {"", "\n", "a\n", "\n\n", "a\n\n"};
        int[] counts = {1, 2, 2, 3, 3};
        for (int i = 0; i < inputs.length; i++) {
            TextLayout text =
                    owner.layout(inputs[i], SceneTheme.light().textStyle, 240, TextPolicy.NORMAL);
            require(
                    text.lines().size() == counts[i],
                    "真实字体丢空行 input=" + inputs[i] + " count=" + text.lines().size());
            TextLayout.Line last = text.lines().get(text.lines().size() - 1);
            require(
                    last.sourceStart == inputs[i].length() && last.sourceEnd == inputs[i].length(),
                    "真实字体末尾空行源位置错误");
            require(last.lineHeight > 0, "真实字体空行没有可编辑高度");
        }
        try (AreaFixture f = new AreaFixture("fi中文 e\u0301 👩🏽‍💻\nאבג العربية\n")) {
            f.command(SceneEditEvent.Command.DOCUMENT_HOME);
            f.pixels();
            TextLayout wide = f.entry().text;
            require(
                    wide.lines().size() >= 3
                            && wide.lines().stream().anyMatch(line -> !line.glyphs().isEmpty()),
                    "Area未使用真实多行glyph");
            require(sourceCluster(wide, 0).sourceEnd > 0, "真实字体缺源簇");
            int combining = f.snapshot().text.indexOf("e\u0301");
            TextLayout.Cluster c = sourceCluster(wide, combining);
            require(c.sourceEnd >= combining + 2, "生产整形拆开组合字符簇");
            String draft = f.snapshot().text;
            host.width = 92;
            f.pixels();
            TextLayout narrow = f.entry().text;
            require(
                    narrow.lines().size() > wide.lines().size() && f.snapshot().text.equals(draft),
                    "真实字体软换行没有响应宽度或写回草稿");
            require(
                    narrow.lines().get(narrow.lines().size() - 1).sourceStart == draft.length(),
                    "缩窄后丢末尾空行");
        }
    }

    private void textAreaSeparatorHit() throws Exception {
        String input = "אבג\u2028ABC ( ) \u2029DEF [ ] \n";
        try (AreaFixture f = new AreaFixture(input)) {
            f.command(SceneEditEvent.Command.DOCUMENT_HOME);
            f.pixels();
            SceneGeometryView.Entry e = f.entry();
            LayoutBox inner = fieldInner(e, SceneTheme.light().padding);
            require(
                    f.snapshot().text.equals(input) && e.text.originalText.equals(input),
                    "Unicode行分隔符源串不应改写为LF");
            for (TextLayout.Line sourceLine : e.text.lines())
                require(
                        sourceLine.sourceStart >= 0
                                && sourceLine.sourceStart <= sourceLine.sourceEnd
                                && sourceLine.sourceEnd <= input.length(),
                        "Unicode分隔符排版行的源范围越界");
            // 字体提供实际行/簇；ABC/DEF和其括号属于明确的LTR短语，左缘应映射源起点。
            for (String marker : new String[] {"ABC", "(", "DEF", "["}) {
                int source = input.indexOf(marker);
                TextLayout.Cluster c = sourceCluster(e.text, source);
                TextLayout.Line line = null;
                for (TextLayout.Line candidate : e.text.lines())
                    if (candidate.clusters().contains(c)) {
                        line = candidate;
                        break;
                    }
                require(line != null && line.y >= 0 && line.y < inner.height, "真实分隔符行缺少可见簇");
                int x = inner.x + Math.round(c.x),
                        y = inner.y + line.y + Math.round(line.lineHeight / 2);
                f.page.pointer(ScenePointerEvent.down(0, x, y));
                f.pixels();
                f.page.pointer(ScenePointerEvent.up(0, x, y));
                f.pixels();
                require(
                        f.snapshot().caret == c.sourceStart && f.snapshot().anchor == c.sourceStart,
                        "RTL后Unicode分隔符的LTR短语命中方向错误 marker=" + marker);
            }
        }
    }

    /** NEL 在正文保留，但字体显示为可见中性标记；末尾括号按实际 RTL 段落定位。 */
    private void textAreaControlMarkerHit() throws Exception {
        String input = "אבג \u0085 ABC (";
        try (AreaFixture f = new AreaFixture(input)) {
            f.command(SceneEditEvent.Command.DOCUMENT_HOME);
            f.pixels();
            SceneGeometryView.Entry e = f.entry();
            LayoutBox inner = fieldInner(e, SceneTheme.light().padding);
            require(e.text.lines().size() == 1 && f.snapshot().text.equals(input), "可见控制符不能变成业务换行");
            TextLayout.Cluster c = sourceCluster(e.text, input.length() - 1);
            TextLayout.Line line = e.text.lines().get(0);
            int x = inner.x + Math.round(c.x + c.advance * 0.75f),
                    y = inner.y + line.y + Math.round(line.lineHeight / 2);
            f.page.pointer(ScenePointerEvent.down(0, x, y));
            f.pixels();
            f.page.pointer(ScenePointerEvent.up(0, x, y));
            f.pixels();
            require(
                    f.snapshot().caret == c.sourceStart,
                    "字体中性控制标记后的RTL括号命中方向错误 actual="
                            + f.snapshot().caret
                            + " expected="
                            + c.sourceStart);
        }
    }

    private void textAreaSelection() throws Exception {
        try (AreaFixture f = new AreaFixture("fi中文 e\u0301\nsecond 中文\nthird")) {
            f.command(SceneEditEvent.Command.DOCUMENT_HOME);
            f.pixels();
            SceneGeometryView.Entry e = f.entry();
            LayoutBox inner = fieldInner(e, SceneTheme.light().padding);
            TextLayout.Line first = e.text.lines().get(0), second = e.text.lines().get(1);
            TextLayout.Cluster start = sourceCluster(e.text, 2), end = second.clusters().get(2);
            int x0 = inner.x + Math.round(start.x),
                    y0 = inner.y + first.y + Math.round(first.lineHeight / 2);
            int x1 = inner.x + Math.round(end.x),
                    y1 = inner.y + second.y + Math.round(second.lineHeight / 2);
            f.page.pointer(ScenePointerEvent.down(0, x0, y0));
            f.pixels();
            require(f.snapshot().caret == start.sourceStart, "真实Area点击没有按源簇定位");
            f.page.pointer(ScenePointerEvent.move(x1, y1));
            f.page.pointer(ScenePointerEvent.up(0, x1, y1));
            int[] selected = f.pixels();
            require(
                    f.snapshot().anchor == start.sourceStart
                            && f.snapshot().caret == end.sourceStart,
                    "真实Area跨行拖选没有保留anchor");
            require(
                    colorPixels(
                                            selected,
                                            new LayoutBox(
                                                    inner.x,
                                                    inner.y + first.y,
                                                    inner.width,
                                                    (int) Math.ceil(first.lineHeight)),
                                            SceneTheme.light().hover)
                                    > 0
                            && colorPixels(
                                            selected,
                                            new LayoutBox(
                                                    inner.x,
                                                    inner.y + second.y,
                                                    inner.width,
                                                    (int) Math.ceil(second.lineHeight)),
                                            SceneTheme.light().hover)
                                    > 0,
                    "跨行选区没有进入两行FBO像素");
            final String[] copied = {null};
            f.page.edit(
                    SceneEditEvent.command(
                            SceneEditEvent.Command.COPY,
                            false,
                            new SceneEditEvent.Clipboard() {
                                public String read() {
                                    throw new AssertionError("复制不读剪贴板");
                                }

                                public void write(String text) {
                                    copied[0] = text;
                                }
                            }));
            f.pixels();
            require(
                    copied[0].equals(
                                    f.snapshot().text.substring(start.sourceStart, end.sourceStart))
                            && copied[0].contains("\n"),
                    "真实跨行复制丢LF");
            f.command(SceneEditEvent.Command.HOME);
            f.pixels();
            require(f.snapshot().caret == second.sourceStart, "真实视觉Home跳全文首");
            f.command(SceneEditEvent.Command.END);
            int[] pixels = f.pixels();
            e = f.entry();
            inner = fieldInner(e, SceneTheme.light().padding);
            TextLayout.Line line = e.text.lines().get(1);
            TextLayout.Cluster tail = line.clusters().get(line.clusters().size() - 1);
            int caretX = inner.x + (int) Math.floor(tail.x + tail.advance);
            require(
                    f.snapshot().caret == line.sourceEnd
                            && colorPixels(
                                            pixels,
                                            new LayoutBox(
                                                    caretX,
                                                    inner.y + line.y,
                                                    1,
                                                    (int) Math.ceil(line.lineHeight)),
                                            SceneTheme.light().focus)
                                    > 0,
                    "真实视觉End光标没有落入该行GPU区域");
            image(new File(output, "text-area-selection.png"), pixels);
        }
    }

    private void textAreaImeScroll() throws Exception {
        StringBuilder draft = new StringBuilder("fi中文\n");
        for (int i = 0; i < 18; i++) draft.append("scroll 中文 ").append(i).append('\n');
        try (AreaFixture f = new AreaFixture(draft.toString())) {
            f.command(SceneEditEvent.Command.DOCUMENT_HOME);
            f.pixels();
            f.page.edit(SceneEditEvent.composition("组字中文", true));
            f.command(SceneEditEvent.Command.CONFIRM);
            f.command(SceneEditEvent.Command.SUBMIT);
            int[] preedit = f.pixels();
            require(
                    f.submits[0] == 0
                            && f.snapshot().composing
                            && f.snapshot().text.equals(draft.toString()),
                    "多行IME确认换行或提交");
            SceneGeometryView.Entry e = f.entry();
            LayoutBox inner = fieldInner(e, SceneTheme.light().padding);
            TextLayout.Line line = e.text.lines().get(0);
            TextLayout.Cluster c = sourceCluster(e.text, 0);
            require(
                    colorPixels(
                                    preedit,
                                    new LayoutBox(
                                            inner.x + (int) Math.floor(c.x),
                                            inner.y
                                                    + (int)
                                                            Math.floor(
                                                                    line.baseline + line.descent),
                                            Math.max(1, (int) Math.ceil(c.advance)),
                                            1),
                                    SceneTheme.light().focus)
                            > 0,
                    "多行IME下划线没有真实像素");
            f.page.edit(SceneEditEvent.committed("确认\r\n"));
            f.pixels();
            f.command(SceneEditEvent.Command.UNDO);
            f.pixels();
            require(f.snapshot().text.equals(draft.toString()), "多行IME提交不是原子历史");
            f.local.execute(
                    () -> {
                        f.readOnly[0].set(true);
                        f.theme[0].set(SceneTheme.dark());
                    });
            f.pixels();
            f.command(SceneEditEvent.Command.SELECT_ALL);
            int[] selected = f.pixels();
            e = f.entry();
            inner = fieldInner(e, SceneTheme.dark().padding);
            require(colorPixels(selected, inner, SceneTheme.dark().hover) > 0, "只读跨行选区没有GPU像素");
            f.command(SceneEditEvent.Command.SUBMIT);
            f.command(SceneEditEvent.Command.CONFIRM);
            f.pixels();
            require(f.submits[0] == 0 && f.snapshot().text.equals(draft.toString()), "只读多行提交或换行");
            f.command(SceneEditEvent.Command.DOCUMENT_HOME);
            int[] beforeScroll = f.pixels();
            e = f.entry();
            inner = fieldInner(e, SceneTheme.dark().padding);
            int beforeY = e.text.lines().get(0).y;
            f.page.pointer(ScenePointerEvent.scroll(inner.x + 2, inner.y + 2, -1));
            int[] scrolled = f.pixels();
            int scrollY = f.entry().text.lines().get(0).y;
            require(scrollY < beforeY && !Arrays.equals(beforeScroll, scrolled), "内部滚动没进入真实像素/几何");
            f.pixels();
            require(f.entry().text.lines().get(0).y == scrollY, "稳定GPU帧拉回用户滚动");
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                    if (!inner.contains(x, y)) {
                        boolean border = e.box.contains(x, y);
                        if (!border)
                            require(scrolled[y * W + x] == 0, "Area正文越过box裁剪 x=" + x + " y=" + y);
                    }
            SceneEditState identity = f.state[0];
            SceneEditState.Snapshot before = f.snapshot();
            host.width = 128;
            int[] resized = f.pixels();
            require(
                    f.state[0] == identity
                            && f.snapshot().text.equals(before.text)
                            && f.snapshot().caret == before.caret
                            && f.snapshot().anchor == before.anchor,
                    "resize替换state或选择");
            require(f.entry().box.width == 128, "动态宽度未进入正式geometry");
            f.command(SceneEditEvent.Command.SELECT_ALL);
            resized = f.pixels();
            require(
                    colorPixels(
                                            resized,
                                            fieldInner(f.entry(), SceneTheme.dark().padding),
                                            SceneTheme.dark().hover)
                                    > 0
                            && colorPixels(
                                            resized,
                                            new LayoutBox(128, 0, W - 128, H),
                                            SceneTheme.dark().hover)
                                    == 0,
                    "动态宽度没裁剪GPU选区");
            SceneEditState.Snapshot retained = f.snapshot();
            f.page.close();
            require(
                    f.state[0] == identity
                            && f.snapshot().text.equals(retained.text)
                            && f.snapshot().anchor == retained.anchor
                            && f.snapshot().caret == retained.caret
                            && f.snapshot().active == retained.active
                            && f.submits[0] == 0,
                    "Area卸载丢草稿选择或提交");
            image(new File(output, "text-area-ime-scroll.png"), resized);
        }
    }

    private void inputAndResize() throws Exception {
        int rawY = FramebufferCoordinates.mouseY(H, H - 6);
        session.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.down(0, 5, rawY));
        session.frame();
        if (clicked[0] != 0) throw new AssertionError("raw像素DOWN不应激活");
        session.pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.up(0, 5, rawY));
        session.frame();
        if (clicked[0] != 1) throw new AssertionError("raw像素UP未命中原owner");
        domain.execute(() -> presentation[0] = pipeline.current());
        LayoutBox scroll = presentation[0].geometry.get(nodes[2]).box;
        session.pointer(
                club.heiqi.uilib.ui.scene.input.ScenePointerEvent.scroll(
                        scroll.x + 5,
                        FramebufferCoordinates.mouseY(H, H - (scroll.y + 5) - 1),
                        -1));
        session.frame();
        domain.execute(
                () -> {
                    if (pipeline.current().geometry.get(nodes[2]).scrollOffset != 32)
                        throw new AssertionError("scroll非声明32原生px");
                });
        host.width = 333;
        host.height = 199;
        domain.execute(
                () -> {
                    LayoutBox box = pipeline.current().geometry.get(nodes[0]).box;
                    if (box.width != 333 || box.height != 199)
                        throw new AssertionError("resize不是动态framebuffer布局");
                });
    }

    private void reloadSources() throws Exception {
        long generation = owner.fonts().generation();
        owner.reload(new GenerationFonts(), 2);
        initial.retired = true;
        domain.execute(
                () -> {
                    if (pipeline.current().paint == presentation[0].paint)
                        throw new AssertionError("reload未失效布局");
                });
        if (owner.fonts().generation() <= generation) throw new AssertionError("reload代际未变化");
        GlyphKey oldUncached = new GlyphKey(old.source, 26, old.glyphId);
        owner.rasterizer().request(oldUncached);
        long end = System.nanoTime() + 10000000000L;
        while (owner.rasterizer().peek(oldUncached) == null) {
            owner.pumpReady();
            if (System.nanoTime() > end || !owner.rasterizer().failures().isEmpty())
                throw new AssertionError("退休包旧glyph不可重开");
            Thread.sleep(2);
        }
    }

    private void screenOwnership() throws Exception {
        cached = owner.rasterizer().stats().bitmapBytes;
        host.screen.close();
        if (owner.rasterizer().stats().closed
                || owner.rasterizer().stats().bitmapBytes != cached
                || owner.fonts().layout("仍可用", style, 100).lines().isEmpty())
            throw new AssertionError("screen关闭释放sharedowner");
        try (FramebufferSceneRenderer reopened = new FramebufferSceneRenderer(owner)) {
            clear();
            reopened.replay(white, W, H);
        }
        GlPaintPlanRenderer shared = owner.sharedPlanRenderer();
        try (GlPaintPlanRenderer.Prepared prepared =
                shared.prepare(white, new LayoutBox(0, 0, W, H), owner.settings().upload)) {
            prepared.render(FramebufferCoordinates.orthographic(W, H), H, false);
        }
    }

    private void closeFailures() throws Exception {
        closeFailure(new IllegalStateException("首个 renderer 关闭失败"));
        closeFailure(new AssertionError("首个 renderer 关闭 Error"));
    }

    /** 只在正式 GL/native 边界注入失败，仍创建和关闭真实 renderer、字体会话及 worker。 */
    private void closeFailure(Throwable first) throws Exception {
        RuntimeException second = new IllegalStateException("后续 renderer 关闭失败");
        RuntimeException nativeFailure = new IllegalStateException("native 会话关闭失败");
        Thread ownerThread = Thread.currentThread();
        boolean[] armed = {false};
        int[] rendererCloses = {0}, nativeCloses = {0};
        GlApi actualGl = new Lwjgl3GlApi();
        List<Integer> programs = new ArrayList<>(), buffers = new ArrayList<>(), textures = new ArrayList<>();
        GlApi failingGl =
                (GlApi) Proxy.newProxyInstance(
                        GlApi.class.getClassLoader(), new Class<?>[] {GlApi.class},
                        (proxy, method, args) -> {
                            Object result;
                            try {
                                result = method.invoke(actualGl, args);
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                            if (method.getName().equals("glCreateProgram")) programs.add((Integer) result);
                            if (method.getName().equals("glGenBuffers")) buffers.add((Integer) result);
                            if (method.getName().equals("glGenTextures")) textures.add((Integer) result);
                            if (armed[0] && method.getName().equals("glDeleteVertexArrays"))
                                throw ++rendererCloses[0] == 1 ? first : second;
                            return result;
                        });
        FontEngine failingEngine = () -> {
            FontSession actual = IsolatedFontEngine.shared().openSession();
            return (FontSession) Proxy.newProxyInstance(
                    FontSession.class.getClassLoader(), new Class<?>[] {FontSession.class},
                    (proxy, method, args) -> {
                        Object result;
                        try {
                            result = method.invoke(actual, args);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                        if (method.getName().equals("close") && Thread.currentThread() == ownerThread) {
                            nativeCloses[0]++;
                            if (armed[0]) throw nativeFailure;
                        }
                        return result;
                    });
        };
        ClientFontServices failingOwner = new ClientFontServices(
                getClass().getClassLoader(), 1, ClientFontServices.Settings.defaults(),
                failingEngine, failingGl, GlProgramProvider.NONE);
        List<GlPaintPlanRenderer> consumers = new ArrayList<>();
        try {
            consumers.add(failingOwner.createPlanRenderer());
            consumers.add(failingOwner.createPlanRenderer());
            GlGlyphAtlas atlas = failingOwner.atlas();
            FontTextService fonts = failingOwner.fonts();
            AsyncGlyphRasterizer worker = failingOwner.rasterizer();
            TextLayout text = fonts.layout("x", new TextStyle("default", 20, -1), 100);
            worker.request(GlyphKey.of(text.lines().get(0).glyphs().get(0)));
            try (GlGlyphAtlas.Frame frame = atlas.beginFrame(Collections.<GlyphKey>emptyList(), false, GlGlyphAtlas.SamplingView.STRAIGHT_RGBA, true)) {
                frame.prepare(null, new GlGlyphAtlas.UploadBudget(4096, 1000000000, 8));
            }
            require(!programs.isEmpty() && !buffers.isEmpty() && !textures.isEmpty(), "真实所属GL资源未创建");
            require(worker.stats().started, "失败清理未覆盖实际 worker");
            armed[0] = true;
            Throwable caught = null;
            try {
                failingOwner.close();
            } catch (RuntimeException | Error failure) {
                caught = failure;
            }
            require(caught == first, "后续 native 错误替换了首个关闭异常");
            for (int program : programs) require(!glIsProgram(program), "单次owner关闭遗漏shader program");
            for (int buffer : buffers) require(!glIsBuffer(buffer), "单次owner关闭遗漏buffer");
            for (int texture : textures) require(!glIsTexture(texture), "单次owner关闭遗漏atlas texture");
            require(Arrays.equals(first.getSuppressed(), new Throwable[] {second, nativeFailure}),
                    "后续 renderer/native 关闭错误未依序保留");
            require(rendererCloses[0] == consumers.size() && nativeCloses[0] == 1,
                    "单项失败中断了其余所属关闭");
            require(worker.stats().closed && failingOwner.awaitRasterizerClose(10000),
                    "native 关闭失败后 worker 未结束");
            boolean atlasClosed = false, fontsClosed = false;
            try { atlas.stats(); } catch (IllegalStateException expected) { atlasClosed = true; }
            try { fonts.layout("x", new TextStyle("default", 20, -1), 100); }
            catch (IllegalStateException expected) { fontsClosed = true; }
            require(atlasClosed && fontsClosed, "失败后共享 atlas/字体仍可使用");
            failingOwner.close();
            require(rendererCloses[0] == consumers.size() && nativeCloses[0] == 1,
                    "重复关闭重新执行失败资源");
        } finally {
            armed[0] = false;
            failingOwner.close();
            // 失败断言也释放测试持有的资源；正式断言在此之前已确认单次owner关闭完成全部删除。
            for (GlPaintPlanRenderer consumer : consumers) consumer.close();
            require(failingOwner.awaitRasterizerClose(10000), "失败场景 worker 未清理");
        }
    }

    private void closeOwner() throws Exception {
        session.close();
        owner.close();
        owner.close();
        host.screen.close();
        if (!owner.awaitRasterizerClose(10000)) throw new AssertionError("退出worker未清理");
        if (glGetError() != GL_NO_ERROR) throw new AssertionError("GL error");
        System.out.println(
                "CLIENT_FONT_SERVICES_SCENARIO_PASS native="
                        + W
                        + "x"
                        + H
                        + " onePixelScissorLit="
                        + lit
                        + " readyFrames="
                        + frames
                        + " screenCloseSharedBytes="
                        + cached
                        + " click=1 scroll=32 resize=333x199 reloadOldSource=true lazyGL=true readyNoRelayout=true viewportRestore=true workerClose=true");
    }

    public static void main(String[] args) throws Exception {
        new ClientFontServicesScenario().run(args);
    }
}
