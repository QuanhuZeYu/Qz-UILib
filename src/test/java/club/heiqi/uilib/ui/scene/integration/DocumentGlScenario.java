package club.heiqi.uilib.ui.scene.integration;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import club.heiqi.uilib.host.client.font.ClientFontServices;
import club.heiqi.uilib.host.client.font.FramebufferCoordinates;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;
import club.heiqi.uilib.host.client.image.ClientImageServices;
import club.heiqi.uilib.host.font.freetype.LwjglFontEngine;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.DocumentResources;
import club.heiqi.uilib.ui.scene.control.SceneDocument;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.control.SceneMath;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.SharedImageService;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/** 隐藏真实GL：共同文档唯一scene树，经正式字体、图像与PaintPlan检验像素；不代表游戏实机。 */
public final class DocumentGlScenario extends TestApplication {
    private static final int W = 640, H = 360;
    private long window, serial;
    private boolean markdown;
    private ClientFontServices fonts;
    private ClientImageServices images;
    private GlPaintPlanRenderer renderer;
    private final ReactiveDomain domain = ReactiveDomain.direct();
    private ScenePageSession page;
    private Signal<TextContent> content;
    private Signal<SceneTheme> theme;
    private Signal<ImageSource> imageSource;
    private Signal<Integer> width, height;
    private SceneNode paragraph, viewport;
    private PaintPlan plan;
    private int ready, demand;
    private Path output;
    private BufferedImage background;
    private final List<BufferedImage> frames = new ArrayList<>();
    private Signal<club.heiqi.uilib.ui.scene.text.TextPolicy> textPolicy;
    private final List<String> clicks = new ArrayList<>();
    private final StringBuilder evidence = new StringBuilder();

    private static String tex(String source) {
        return source.replace('@', (char) 92);
    }

    private static final String SOURCE =
            "正文前段 <a href=\"next\">链接文字 <latex>"
                    + tex("@frac{a}{b}")
                    + "</latex> 后缀</a> <img src=\"bitmap\" width=\"40\"/> 正文尾段与足够长的共同换行内容，继续阅读正文。";

    public static void main(String[] args) throws Exception {
        new DocumentGlScenario().run(args);
    }

    public static void runMarkdown(String[] args) throws Exception {
        DocumentGlScenario scenario = new DocumentGlScenario();
        scenario.markdown = true;
        scenario.run(args);
    }

    @Override
    protected void setup() throws Exception {
        output =
                arguments().length == 0
                        ? Paths.get("build/document-flow/gpu")
                        : Paths.get(arguments()[0]);
        Files.createDirectories(output);
        GLFWErrorCallback errors = GLFWErrorCallback.createPrint(System.err);
        errors.set();
        own(
                () -> {
                    if (window != 0) {
                        glfwMakeContextCurrent(0);
                        glfwDestroyWindow(window);
                    }
                    GL.setCapabilities(null);
                    glfwTerminate();
                    glfwSetErrorCallback(null);
                    errors.free();
                });
        require(glfwInit(), "GLFW初始化失败");
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(W, H, "DocumentGlScenario", 0, 0);
        require(window != 0, "真实GL上下文创建失败");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glViewport(0, 0, W, H);
        glDisable(GL_DEPTH_TEST);
        evidence.append("vendor=")
                .append(glGetString(GL_VENDOR))
                .append(" renderer=")
                .append(glGetString(GL_RENDERER))
                .append(" version=")
                .append(glGetString(GL_VERSION))
                .append('\n');
        GlApi gl = new Lwjgl3GlApi();
        fonts =
                own(
                        new ClientFontServices(
                                getClass().getClassLoader(),
                                1,
                                ClientFontServices.Settings.defaults(),
                                new LwjglFontEngine(),
                                gl,
                                GlProgramProvider.NONE));
        images =
                own(
                        new ClientImageServices(
                                gl,
                                source -> () -> source.bitmap,
                                SharedImageService.Budget.defaults()));
        renderer = fonts.createPlanRenderer(images.textures());
        own(() -> fonts.releasePlanRenderer(renderer));
        clear();
        background = read();
        SceneHost host =
                new SceneHost() {
                    public int width() {
                        return W;
                    }

                    public int height() {
                        return H;
                    }

                    public long metricsRevision() {
                        return fonts.fonts().generation() ^ fonts.readyVersion();
                    }

                    public void prepareResources() {
                        images.prepareResources();
                    }

                    public void replay(PaintPlan paint) {
                        plan = paint;
                        fonts.beginPresentation(++serial);
                        try (GlPaintPlanRenderer.Prepared p =
                                fonts.prepare(renderer, paint, new LayoutBox(0, 0, W, H))) {
                            ready = p.readyCount();
                            demand = p.demandCount();
                            p.render(FramebufferCoordinates.orthographic(W, H), H, false);
                        } finally {
                            fonts.endPresentation();
                        }
                    }
                };
        page =
                own(
                        ScenePageSession.open(
                                domain,
                                host,
                                fonts,
                                ctx -> {
                                    content = domain.signal(markdown
                                            ? club.heiqi.uilib.ui.scene.text.MarkdownDocument.parse(
                                                    "正文前段 [链接文字 $" + tex("@frac{a}{b}")
                                                    + "$ 后缀](next) ![位图](bitmap) 正文尾段与足够长的共同换行内容，继续阅读正文。")
                                                    .blocks().get(0).paragraph
                                            : TextContent.richTags(SOURCE));
                                    theme =
                                            domain.signal(
                                                    SceneTheme.dark()
                                                            .withTextStyle(
                                                                    new TextStyle(
                                                                            "default",
                                                                            28,
                                                                            0xffffffff)));
                                    imageSource = domain.signal(ImageSource.bitmap(bitmap(false)));
                                    textPolicy = domain.signal(club.heiqi.uilib.ui.scene.text.TextPolicy.NORMAL);
                                    width = domain.signal(500);
                                    height = domain.signal(280);
                                    Map<String, DocumentResources.ImageReference> refs =
                                            new LinkedHashMap<>();
                                    refs.put(
                                            "bitmap",
                                            new DocumentResources.ImageReference(
                                                    imageSource,
                                                    domain.signal(ImageOptions.defaults())));
                                    DocumentResources resources =
                                            new DocumentResources(
                                                    images.images(),
                                                    domain.signal("https://example.test/docs/"),
                                                    refs,
                                                    null);
                                    paragraph =
                                            SceneDocument.paragraph(
                                                            ctx,
                                                            content,
                                                            theme,
                                                            resources,
                                                            clicks::add)
                                                    .size(Size.fill(1), Size.natural());
                                    paragraph.bind(NodeBinding.value(textPolicy, (node, value) -> node.textPolicy(value)));
                                    viewport =
                                            new SceneNode()
                                                    .clip(true)
                                                    .scrollable(true)
                                                    .add(paragraph);
                                    viewport.bind(
                                            NodeBinding.value(
                                                    width,
                                                    (node, value) ->
                                                            node.size(
                                                                    Size.fixed(value),
                                                                    Size.fixed(height.get()))));
                                    viewport.bind(
                                            NodeBinding.value(
                                                    height,
                                                    (node, value) ->
                                                            node.size(
                                                                    Size.fixed(width.get()),
                                                                    Size.fixed(value))));
                                    ctx.mount(
                                            new SceneNode()
                                                    .padding(16)
                                                    .size(Size.fill(1), Size.fill(1))
                                                    .add(viewport));
                                }));
    }

    private static ImageContent bitmap(boolean green) {
        byte[] rgba = new byte[24 * 16 * 4];
        for (int i = 0; i < rgba.length; i += 4) {
            rgba[i] = (byte) (green ? 30 : 240);
            rgba[i + 1] = (byte) (green ? 230 : 30);
            rgba[i + 2] = 50;
            rgba[i + 3] = (byte) 255;
        }
        return new ImageContent(24, 16, rgba);
    }

    @Override
    protected void runTests() throws Exception {
        scenario(
                "共同正文链接分式位图正式GPU与窄宽换行",
                () -> {
                    BufferedImage wide = renderReady();
                    save("document-wide.png", wide);
                    inspect("wide", wide, false);
                    final int[] linkPoint = new int[2];
                    domain.execute(
                            () -> {
                                require(paragraph.children().size() == 2, "内联对象没有唯一同树身份");
                                SceneGeometryView.Entry entry = geometry().get(paragraph);
                                TextContent.Resolved resolved =
                                        content.get().resolve(theme.get().textStyle);
                                for (TextLayout.Line line : entry.text.lines())
                                    for (TextLayout.Cluster cluster : line.clusters()) {
                                        if (resolved.linkAt(cluster.logicalStart) != null
                                                && cluster.advance > 0) {
                                            int
                                                    x =
                                                            (int)
                                                                    (entry.box.x
                                                                            + cluster.x
                                                                            + cluster.advance / 2),
                                                    y =
                                                            (int)
                                                                    (entry.box.y
                                                                            + line.y
                                                                            + line.lineHeight / 2);
                                            linkPoint[0] = x;
                                            linkPoint[1] = y;
                                            return;
                                        }
                                    }
                                throw new AssertionError("没有可见链接簇");
                            });
                    page.pointer(ScenePointerEvent.down(0, linkPoint[0], linkPoint[1]));
                    page.pointer(ScenePointerEvent.up(0, linkPoint[0], linkPoint[1]));
                    renderReady();
                    require(
                            clicks.size() == 1
                                    && clicks.get(0).equals("https://example.test/docs/next"),
                            "正式链接激活未解析基准位置：" + clicks);
                    final SceneNode[] identities = new SceneNode[2];
                    domain.execute(
                            () -> {
                                identities[0] = paragraph.children().get(0);
                                identities[1] = paragraph.children().get(1);
                                width.set(170);
                            });
                    BufferedImage narrow = renderReady();
                    save("document-narrow.png", narrow);
                    inspect("narrow", narrow, false);
                    domain.execute(
                            () -> {
                                require(
                                        geometry().get(paragraph).text.lines().size() > 2,
                                        "窄宽没有共同换行");
                                require(
                                        paragraph.children().get(0) == identities[0]
                                                && paragraph.children().get(1) == identities[1],
                                        "换行重建内联项");
                            });
                });
        scenario(
                "同树字号图像字体重载与实际viewport剪裁",
                () -> {
                    long generation = fonts.fonts().generation();
                    domain.execute(
                            () -> {
                                theme.set(
                                        SceneTheme.light()
                                                .withTextStyle(
                                                        new TextStyle("default", 34, 0xffeeeeee)));
                                imageSource.set(ImageSource.bitmap(bitmap(true)));
                            });
                    BufferedImage changed = renderReady();
                    save("document-theme-image.png", changed);
                    inspect("theme-image", changed, true);
                    fonts.reload(getClass().getClassLoader(), System.nanoTime());
                    BufferedImage reloaded = renderReady();
                    save("document-reloaded.png", reloaded);
                    inspect("reloaded", reloaded, true);
                    domain.execute(
                            () -> {
                                require(fonts.fonts().generation() != generation, "字体重载未变代");
                                require(
                                        geometry().get(paragraph).text.generation
                                                == fonts.fonts().generation(),
                                        "正文保留旧字体代际");
                                require(
                                        geometry().get(paragraph.children().get(0)).text.generation
                                                == fonts.fonts().generation(),
                                        "公式与正文混代");
                                height.set(45);
                            });
                    BufferedImage clipped = renderReady();
                    save("document-clipped.png", clipped);
                    domain.execute(
                            () -> {
                                LayoutBox clip = geometry().get(viewport).box;
                                require(geometry().get(viewport).scrollMaximum > 0, "文档没有形成滚动内容范围");
                                int outside = 0, inside = 0;
                                for (int y = 0; y < H; y++)
                                    for (int x = 0; x < W; x++)
                                        if (clipped.getRGB(x, y) != background.getRGB(x, y)) {
                                            if (x >= clip.x
                                                    && x < clip.x + clip.width
                                                    && y >= clip.y
                                                    && y < clip.y + clip.height) inside++;
                                            else outside++;
                                        }
                                evidence.append("clipped inside=")
                                        .append(inside)
                                        .append(" outside=")
                                        .append(outside)
                                        .append('\n');
                                require(inside > 0 && outside == 0, "GPU像素突破viewport剪裁：" + outside);
                            });
                    require(glGetError() == GL_NO_ERROR, "正式GL绘制错误");
                    BufferedImage atlas =
                            new BufferedImage(W * 2, H * 3, BufferedImage.TYPE_INT_ARGB);
                    for (int i = 0; i < frames.size(); i++)
                        for (int y = 0; y < H; y++)
                            for (int x = 0; x < W; x++)
                                atlas.setRGB(
                                        (i % 2) * W + x,
                                        (i / 2) * H + y,
                                        frames.get(i).getRGB(x, y));
                    require(
                            ImageIO.write(
                                    atlas, "png", output.resolve("document-atlas.png").toFile()),
                            "atlas PNG编码失败");
                    saveEvidence();
                });
        if (!markdown) {
            scenario("扩展样式真实GPU背景顺序与上下标", this::extendedStylesGpu);
            scenario("小行高后行背景不覆盖前行字形", this::overlappingHighlightGpu);
            scenario("软连字符像素、链接来源与动态重排", this::softHyphenGpu);
        }
    }

    private void softHyphenGpu() throws Exception {
        domain.execute(() -> {
            width.set(48); height.set(180);
            textPolicy.set(club.heiqi.uilib.ui.scene.text.TextPolicy.NORMAL);
            theme.set(SceneTheme.dark().withTextStyle(new TextStyle("default", 30, 0xffffffff)));
            content.set(TextContent.richTags("<mark color=\"#FF551122\"><u><a href=\"shy-word\">ab\u00adcd</a></u></mark>"));
        });
        BufferedImage pixels = renderReady();
        save("document-soft-hyphen.png", pixels);
        int[] position = new int[2];
        domain.execute(() -> {
            SceneGeometryView.Entry entry = geometry().get(paragraph);
            require(entry.text.lines().size() == 2, "GPU样本未采用SHY断点");
            TextLayout.Line line = entry.text.lines().get(0);
            TextLayout.Cluster cluster = line.clusters().stream().filter(c -> c.logicalStart == 2
                    && c.glyphIndexStart < c.glyphIndexEnd).findFirst().orElseThrow(() -> new AssertionError("缺可见SHY命中簇"));
            TextLayout.Glyph hyphen = line.glyphs().get(cluster.glyphIndexStart);
            LayoutBox ink = bounds(entry.box.x + hyphen.x + hyphen.inkBounds.left,
                    entry.box.y + hyphen.y + hyphen.inkBounds.top,
                    hyphen.inkBounds.right - hyphen.inkBounds.left, hyphen.inkBounds.bottom - hyphen.inkBounds.top).intersect(geometry().get(viewport).box);
            int foreground = 0;
            for (int y = ink.y; y < ink.y + ink.height; y++)
                for (int x = ink.x; x < ink.x + ink.width; x++) {
                    int rgb = pixels.getRGB(x, y);
                    if ((rgb & 0xff) > 220 && (rgb >> 8 & 0xff) > 220 && (rgb >> 16 & 0xff) > 220) foreground++;
                }
            require(foreground > 0, "可见SHY没有真实GPU墨迹");
            position[0] = (int) (entry.box.x + cluster.x + cluster.advance / 2);
            position[1] = (int) (entry.box.y + line.y + line.lineHeight / 2);
            evidence.append("softHyphen foregroundPixels=").append(foreground)
                    .append(" source=").append(cluster.sourceStart).append("–").append(cluster.sourceEnd).append('\n');
        });
        int before = clicks.size();
        page.pointer(ScenePointerEvent.down(0, position[0], position[1]));
        page.pointer(ScenePointerEvent.up(0, position[0], position[1]));
        renderReady();
        require(clicks.size() == before + 1 && "https://example.test/docs/shy-word".equals(clicks.get(before)), "点击可见SHY未激活原链接：" + clicks + " at=" + position[0] + "," + position[1]);
        domain.execute(() -> width.set(240));
        renderReady();
        domain.execute(() -> require(geometry().get(paragraph).text.lines().size() == 1
                && geometry().get(paragraph).text.lines().get(0).glyphs().size() == 4, "加宽后SHY未隐藏"));
        domain.execute(() -> width.set(48));
        renderReady();
        domain.execute(() -> require(geometry().get(paragraph).text.lines().get(0).logicalEnd == 3, "缩窄后SHY未再次采用"));
        require(glGetError() == GL_NO_ERROR, "软连字符GL错误");
        saveEvidence();
    }

    private void overlappingHighlightGpu() throws Exception {
        domain.execute(() -> {
            content.set(TextContent.richTags("<mark color=\"#FF551122\">MMMMM<br/>     </mark>"));
            textPolicy.set(new club.heiqi.uilib.ui.scene.text.TextPolicy(
                    club.heiqi.uilib.ui.scene.text.TextPolicy.Direction.AUTO,
                    club.heiqi.uilib.ui.scene.text.TextPolicy.Wrap.NONE, "und", 0, 5));
        });
        BufferedImage pixels = renderReady();
        save("document-overlapping-highlight.png", pixels);
        domain.execute(() -> {
            SceneGeometryView.Entry entry = geometry().get(paragraph);
            TextLayout.Line first = entry.text.lines().get(0);
            TextLayout.Line second = entry.text.lines().get(1);
            require(second.y < first.ascent + first.descent, "反例没有形成行内容重叠");
            int foreground = 0;
            for (int y = entry.box.y + 10; y < entry.box.y + first.ascent + first.descent; y++)
                for (int x = entry.box.x; x < entry.box.x + second.advance; x++) {
                    int rgb = pixels.getRGB(x, y);
                    if ((rgb & 0xff) > 220 && (rgb >> 8 & 0xff) > 220 && (rgb >> 16 & 0xff) > 220) foreground++;
                }
            require(foreground > 0, "后行背景覆盖前行溢出字形");
            evidence.append("overlappingHighlight foregroundPixels=").append(foreground).append('\n');
        });
        saveEvidence();
    }

    private void extendedStylesGpu() throws Exception {
        domain.execute(() -> {
            width.set(420);
            height.set(180);
            theme.set(SceneTheme.dark().withTextStyle(new TextStyle("default", 30, 0xffffffff)));
            content.set(TextContent.richTags("<mark color=\"#FF551122\">发货核对 CO<sub>2</sub> / m<sup>3</sup> <spacing value=\"2\">READY</spacing></mark><br/><spacing value=\"-0.5\">紧凑文字 office אבג</spacing>"));
        });
        BufferedImage pixels = renderReady();
        save("document-extended-styles.png", pixels);
        domain.execute(() -> {
            SceneGeometryView.Entry entry = geometry().get(paragraph);
            LayoutBox clip = geometry().get(viewport).box;
            int backgroundPixels = 0, foregroundPixels = 0;
            for (TextLayout.Line line : entry.text.lines())
                for (TextLayout.Decoration d : line.decorations()) {
                    if (d.kind != TextLayout.Decoration.Kind.HIGHLIGHT) continue;
                    LayoutBox area = bounds(entry.box.x + d.x, entry.box.y + d.y, d.width, d.height).intersect(clip);
                    for (int y = area.y; y < area.y + area.height; y++)
                        for (int x = area.x; x < area.x + area.width; x++) {
                            int rgb = pixels.getRGB(x, y);
                            if ((rgb & 0xffffff) == 0x551122) backgroundPixels++;
                            if ((rgb & 0xff) > 220 && (rgb >> 8 & 0xff) > 220 && (rgb >> 16 & 0xff) > 220) foregroundPixels++;
                        }
                }
            require(backgroundPixels > 0 && foregroundPixels > 0, "背景缺GPU像素或绘制顺序覆盖白色字形");
            require(entry.text.lines().stream().flatMap(line -> line.glyphs().stream())
                    .anyMatch(g -> g.resolvedStyle.baselineShift < 0), "GPU场景未命中上标布局");
            require(entry.text.lines().stream().flatMap(line -> line.glyphs().stream())
                    .anyMatch(g -> g.resolvedStyle.baselineShift > 0), "GPU场景未命中下标布局");
            evidence.append("extendedStyles backgroundPixels=").append(backgroundPixels)
                    .append(" foregroundPixels=").append(foregroundPixels).append('\n');
        });
        require(glGetError() == GL_NO_ERROR, "扩展样式GL错误");
        saveEvidence();
    }

    private SceneGeometryView geometry() {
        return page.pipeline().current().geometry;
    }

    private void inspect(String name, BufferedImage pixels, boolean green) throws Exception {
        domain.execute(
                () -> {
                    SceneGeometryView.Entry body = geometry().get(paragraph),
                            math = geometry().get(paragraph.children().get(0));
                    require(body.text.generation == math.text.generation, "正文数学不是同代字体");
                    LayoutBox textArea = null;
                    for (TextLayout.Line line : body.text.lines())
                        for (TextLayout.Glyph glyph : line.glyphs())
                            if (textArea == null
                                    && glyph.inkBounds.known
                                    && glyph.inkBounds.right > glyph.inkBounds.left) {
                                textArea =
                                        bounds(
                                                body.box.x + glyph.x + glyph.inkBounds.left,
                                                body.box.y + glyph.y + glyph.inkBounds.top,
                                                glyph.inkBounds.right - glyph.inkBounds.left,
                                                glyph.inkBounds.bottom - glyph.inkBounds.top);
                            }
                    require(textArea != null, "正文没有真实字形墨迹");
                    int text = count(pixels, textArea, false, green), rules = 0, bitmap = 0;
                    for (PaintCommand cmd : plan.commands()) {
                        LayoutBox area = cmd.clip == null ? cmd.box : cmd.box.intersect(cmd.clip);
                        if (cmd.kind == PaintCommand.Kind.RECT
                                && cmd.box.intersect(math.box).width > 0
                                && cmd.box.intersect(math.box).height > 0)
                            rules += count(pixels, area, false, green);
                        if (cmd.kind == PaintCommand.Kind.IMAGE)
                            bitmap += count(pixels, area, true, green);
                    }
                    evidence.append(name)
                            .append(" textPixels=")
                            .append(text)
                            .append(" mathRulePixels=")
                            .append(rules)
                            .append(" bitmapPixels=")
                            .append(bitmap)
                            .append(" lines=")
                            .append(body.text.lines().size())
                            .append(" generation=")
                            .append(body.text.generation)
                            .append('\n');
                    require(
                            text > 0 && rules > 0 && bitmap > 0,
                            "正文/分式rule/位图没有实际GPU像素："
                                    + name
                                    + " "
                                    + text
                                    + "/"
                                    + rules
                                    + "/"
                                    + bitmap);
                    require(!SceneMath.layout(paragraph.children().get(0)).fallback, "有效分式降级成源文");
                });
        saveEvidence();
    }

    private int count(BufferedImage image, LayoutBox area, boolean bitmap, boolean green) {
        LayoutBox visible = area.intersect(new LayoutBox(0, 0, W, H));
        int count = 0;
        for (int y = visible.y; y < visible.y + visible.height; y++)
            for (int x = visible.x; x < visible.x + visible.width; x++) {
                int rgb = image.getRGB(x, y),
                        r = (rgb >> 16) & 255,
                        g = (rgb >> 8) & 255,
                        b = rgb & 255;
                if (bitmap
                        ? (green ? g > 150 && r < 90 : r > 150 && g < 90)
                        : r > 90 && g > 90 && b > 90) count++;
            }
        return count;
    }

    private BufferedImage renderReady() throws Exception {
        long deadline = System.nanoTime() + 8_000_000_000L;
        do {
            clear();
            page.frame();
            boolean[] loaded = {false};
            domain.execute(() -> {
                loaded[0] = true;
                for (SceneNode child : paragraph.children())
                    if (child.control() instanceof SceneImage)
                        loaded[0] &= SceneImage.state(child).get().content != null;
            });
            if (loaded[0] && ready == demand) {
                glFinish();
                return read();
            }
            Thread.sleep(4);
        } while (System.nanoTime() < deadline);
        throw new AssertionError("正式GPU资源未就绪：" + ready + "/" + demand);
    }

    private void clear() {
        glDisable(GL_SCISSOR_TEST);
        glClearColor(0.09f, 0.13f, 0.19f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    private BufferedImage read() {
        ByteBuffer bytes = MemoryUtil.memAlloc(W * H * 4);
        try {
            glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            BufferedImage result = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int i = ((H - 1 - y) * W + x) * 4;
                    result.setRGB(
                            x,
                            y,
                            (bytes.get(i + 3) & 255) << 24
                                    | (bytes.get(i) & 255) << 16
                                    | (bytes.get(i + 1) & 255) << 8
                                    | bytes.get(i + 2) & 255);
                }
            return result;
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    private void save(String name, BufferedImage image) throws Exception {
        require(ImageIO.write(image, "png", output.resolve(name).toFile()), "PNG编码失败");
        frames.add(image);
    }

    private void saveEvidence() throws Exception {
        Files.write(
                output.resolve("regions.txt"),
                evidence.toString().getBytes(StandardCharsets.UTF_8));
        System.out.print(evidence);
    }

    private static LayoutBox bounds(float x, float y, float width, float height) {
        int left = (int) Math.floor(x), top = (int) Math.floor(y);
        return new LayoutBox(
                left, top, (int) Math.ceil(x + width) - left, (int) Math.ceil(y + height) - top);
    }
}
