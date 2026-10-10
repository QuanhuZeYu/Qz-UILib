package club.heiqi.uilib.ui.scene.integration;

import static org.lwjgl.opengl.GL32C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL32C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL32C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL32C.GL_BLEND;
import static org.lwjgl.opengl.GL32C.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL32C.GL_BLEND_EQUATION_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_BLEND_EQUATION_RGB;
import static org.lwjgl.opengl.GL32C.GL_BLEND_SRC_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL32C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL32C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL32C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL32C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL32C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL32C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL32C.GL_DITHER;
import static org.lwjgl.opengl.GL32C.GL_DST_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL32C.GL_ELEMENT_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL32C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL32C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL32C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL32C.GL_FRAMEBUFFER_SRGB;
import static org.lwjgl.opengl.GL32C.GL_FUNC_REVERSE_SUBTRACT;
import static org.lwjgl.opengl.GL32C.GL_FUNC_SUBTRACT;
import static org.lwjgl.opengl.GL32C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL32C.GL_MAX_ARRAY_TEXTURE_LAYERS;
import static org.lwjgl.opengl.GL32C.GL_NEAREST;
import static org.lwjgl.opengl.GL32C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL32C.GL_ONE;
import static org.lwjgl.opengl.GL32C.GL_RENDERER;
import static org.lwjgl.opengl.GL32C.GL_RGBA;
import static org.lwjgl.opengl.GL32C.GL_RGBA8;
import static org.lwjgl.opengl.GL32C.GL_SCISSOR_BOX;
import static org.lwjgl.opengl.GL32C.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL32C.GL_SHADING_LANGUAGE_VERSION;
import static org.lwjgl.opengl.GL32C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE3;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_2D_ARRAY;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_BINDING_2D_ARRAY;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL32C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL32C.GL_VENDOR;
import static org.lwjgl.opengl.GL32C.GL_VERSION;
import static org.lwjgl.opengl.GL32C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL32C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL32C.GL_ZERO;
import static org.lwjgl.opengl.GL32C.glActiveTexture;
import static org.lwjgl.opengl.GL32C.glAttachShader;
import static org.lwjgl.opengl.GL32C.glBindBuffer;
import static org.lwjgl.opengl.GL32C.glBindFramebuffer;
import static org.lwjgl.opengl.GL32C.glBindTexture;
import static org.lwjgl.opengl.GL32C.glBindVertexArray;
import static org.lwjgl.opengl.GL32C.glBlendEquationSeparate;
import static org.lwjgl.opengl.GL32C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL32C.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL32C.glClear;
import static org.lwjgl.opengl.GL32C.glClearColor;
import static org.lwjgl.opengl.GL32C.glCompileShader;
import static org.lwjgl.opengl.GL32C.glCreateProgram;
import static org.lwjgl.opengl.GL32C.glCreateShader;
import static org.lwjgl.opengl.GL32C.glDeleteBuffers;
import static org.lwjgl.opengl.GL32C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL32C.glDeleteProgram;
import static org.lwjgl.opengl.GL32C.glDeleteShader;
import static org.lwjgl.opengl.GL32C.glDeleteTextures;
import static org.lwjgl.opengl.GL32C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL32C.glDisable;
import static org.lwjgl.opengl.GL32C.glEnable;
import static org.lwjgl.opengl.GL32C.glFinish;
import static org.lwjgl.opengl.GL32C.glFramebufferTexture2D;
import static org.lwjgl.opengl.GL32C.glGenBuffers;
import static org.lwjgl.opengl.GL32C.glGenFramebuffers;
import static org.lwjgl.opengl.GL32C.glGenTextures;
import static org.lwjgl.opengl.GL32C.glGenVertexArrays;
import static org.lwjgl.opengl.GL32C.glGetError;
import static org.lwjgl.opengl.GL32C.glGetInteger;
import static org.lwjgl.opengl.GL32C.glGetIntegerv;
import static org.lwjgl.opengl.GL32C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL32C.glGetProgrami;
import static org.lwjgl.opengl.GL32C.glGetString;
import static org.lwjgl.opengl.GL32C.glIsEnabled;
import static org.lwjgl.opengl.GL32C.glLinkProgram;
import static org.lwjgl.opengl.GL32C.glReadPixels;
import static org.lwjgl.opengl.GL32C.glScissor;
import static org.lwjgl.opengl.GL32C.glShaderSource;
import static org.lwjgl.opengl.GL32C.glTexImage2D;
import static org.lwjgl.opengl.GL32C.glTexImage3D;
import static org.lwjgl.opengl.GL32C.glTexParameteri;
import static org.lwjgl.opengl.GL32C.glUseProgram;
import static org.lwjgl.opengl.GL32C.glViewport;

import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.GlQuadRenderer;
import club.heiqi.uilib.host.client.gl.GlQuadRenderer.Counters;
import club.heiqi.uilib.host.client.gl.GlQuadRenderer.Texture;
import club.heiqi.uilib.host.client.gl.Lwjgl3GlApi;
import club.heiqi.uilib.testing.GlfwTestEnvironment;
import club.heiqi.uilib.testing.GlfwTestEnvironment.Profile;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Quad;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.State;
import club.heiqi.uilib.ui.render.batch.OrderedQuadCollector;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;

import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * 独立 hidden GLFW 上下文，直接消费生产 collector/backend；不启动 MC。 参考器按原图元顺序在 premult 字节域执行 SrcOver；允许每通道
 * 2/255，覆盖 RGBA8 framebuffer 每次混合的定点舍入及驱动浮点转换差异，不容许移动图元次序。
 */
public final class GlBatchScenario extends TestApplication {
    private static final int W = 96, H = 72, CAPACITY = 4, TOLERANCE = 2;
    private static final float[] MVP = {2f / W, 0, 0, 0, 0, -2f / H, 0, 0, 0, 0, 1, 0, -1, 1, 0, 1};
    private static final int[][] MONO = {{128, 128, 128, 128}, {192, 192, 192, 192}};
    private static final int[][] COLOR = {{96, 16, 32, 128}, {12, 120, 40, 160}};

    private static final class Item {
        final int material, page;
        final Quad quad;
        final LayoutBox clip;

        Item(int material, int page, Quad quad, LayoutBox clip) {
            this.material = material;
            this.page = page;
            this.quad = quad;
            this.clip = clip;
        }
    }

    private static Quad q(int x, int y, int w, int h, int layer, int color) {
        return new Quad(x, y, w, h, 0, 0, 1, 1, layer, color);
    }

    private static List<Item> scene() {
        List<Item> result = new ArrayList<>();
        result.add(new Item(1, 0, q(4, 4, 42, 36, 0, 0xa0ff2010), null));
        result.add(new Item(1, 1, q(12, 10, 42, 36, 1, 0xb020ff40), null));
        result.add(new Item(1, 0, q(20, 16, 42, 36, 0, 0xc03040ff), null));
        result.add(new Item(0, 0, q(0, 0, W, H, 0, 0x8050c0f0), new LayoutBox(6, 50, 22, 9)));
        // 彩色资源必须忽略顶点 RGB；页层 A/B/A 仍保持原序。
        result.add(new Item(2, 0, q(48, 6, 32, 30, 0, 0xb000ff00), null));
        result.add(new Item(2, 1, q(56, 14, 32, 30, 1, 0x90ff0000), new LayoutBox(58, 18, 22, 20)));
        result.add(new Item(2, 0, q(52, 22, 32, 30, 0, 0xd00000ff), null));
        // 空 clip 不得进入实际上传。
        result.add(new Item(0, 0, q(0, 0, W, H, 0, 0xffffffff), new LayoutBox(0, 0, 0, 0)));
        return result;
    }

    private static ByteBuffer pixels(int[][] colors) {
        ByteBuffer buffer = MemoryUtil.memAlloc(colors.length * 4);
        for (int[] color : colors) for (int c : color) buffer.put((byte) c);
        buffer.flip();
        return buffer;
    }

    private static int texture(boolean array, int[][] colors) {
        int id = glGenTextures(), target = array ? GL_TEXTURE_2D_ARRAY : GL_TEXTURE_2D;
        glBindTexture(target, id);
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        ByteBuffer data = pixels(colors);
        try {
            if (array)
                glTexImage3D(
                        target,
                        0,
                        GL_RGBA8,
                        1,
                        1,
                        colors.length,
                        0,
                        GL_RGBA,
                        GL_UNSIGNED_BYTE,
                        data);
            else glTexImage2D(target, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        } finally {
            MemoryUtil.memFree(data);
        }
        return id;
    }

    private static int[] reference(List<Item> items) {
        int[] result = new int[W * H * 4];
        for (Item item : items) {
            Quad q = item.quad;
            float a = (q.color >>> 24) / 255f;
            float[] src = new float[4];
            if (item.material == 2) {
                for (int c = 0; c < 4; c++) src[c] = COLOR[item.page][c] * a;
            } else {
                float coverage = item.material == 0 ? 1 : MONO[item.page][3] / 255f;
                src[3] = 255 * a * coverage;
                src[0] = (q.color >>> 16 & 255) * a * coverage;
                src[1] = (q.color >>> 8 & 255) * a * coverage;
                src[2] = (q.color & 255) * a * coverage;
            }
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    if (!covers(q, x + .5, y + .5)
                            || item.clip != null && !item.clip.contains(x, y)) continue;
                    int offset = (y * W + x) * 4;
                    for (int c = 0; c < 4; c++)
                        result[offset + c] =
                                Math.round(src[c] + result[offset + c] * (1 - src[3] / 255f));
                }
        }
        return result;
    }

    /** 与正式EBO共用0-1-2、0-2-3拓扑；像素中心按top-left边归属，仅混合一次。 */
    private static boolean covers(Quad q, double x, double y) {
        return triangle(q.x0, q.y0, q.x1, q.y1, q.x2, q.y2, x, y)
                || triangle(q.x0, q.y0, q.x2, q.y2, q.x3, q.y3, x, y);
    }

    private static boolean triangle(
            double ax,
            double ay,
            double bx,
            double by,
            double cx,
            double cy,
            double px,
            double py) {
        double area = edge(ax, ay, bx, by, cx, cy);
        if (area == 0) return false;
        if (area < 0) return triangle(ax, ay, cx, cy, bx, by, px, py);
        return included(ax, ay, bx, by, px, py)
                && included(bx, by, cx, cy, px, py)
                && included(cx, cy, ax, ay, px, py);
    }

    private static double edge(double ax, double ay, double bx, double by, double px, double py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    private static boolean included(
            double ax, double ay, double bx, double by, double px, double py) {
        double value = edge(ax, ay, bx, by, px, py);
        return value > 0 || value == 0 && (by < ay || by == ay && bx > ax);
    }

    private static int[] readback() {
        ByteBuffer data = MemoryUtil.memAlloc(W * H * 4);
        try {
            glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, data);
            int[] result = new int[W * H * 4];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                    for (int c = 0; c < 4; c++)
                        result[(y * W + x) * 4 + c] = data.get(((H - 1 - y) * W + x) * 4 + c) & 255;
            return result;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static void image(File file, int[] data) throws Exception {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int i = (y * W + x) * 4, a = data[i + 3];
                // PNG 存直通 RGB；读回比较始终使用未经转换的 premult 字节。
                int r = a == 0 ? 0 : Math.min(255, Math.round(data[i] * 255f / a));
                int g = a == 0 ? 0 : Math.min(255, Math.round(data[i + 1] * 255f / a));
                int b = a == 0 ? 0 : Math.min(255, Math.round(data[i + 2] * 255f / a));
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        ImageIO.write(image, "png", file);
    }

    private static void compare(String name, int[] expected, int[] actual) {
        int max = 0, mismatch = 0;
        for (int i = 0; i < actual.length; i++) {
            int delta = Math.abs(expected[i] - actual[i]);
            max = Math.max(max, delta);
            if (delta > TOLERANCE) mismatch++;
        }
        System.out.println(
                name
                        + " readback maxChannelDelta="
                        + max
                        + " mismatchedChannels="
                        + mismatch
                        + " tolerance="
                        + TOLERANCE
                        + "/255");
        if (mismatch != 0) throw new AssertionError(name + " 原序像素不匹配");
        int offset = (24 * W + 30) * 4;
        System.out.println(
                name
                        + " A/B/A overlap(30,24)="
                        + Arrays.toString(Arrays.copyOfRange(actual, offset, offset + 4))
                        + " software="
                        + Arrays.toString(Arrays.copyOfRange(expected, offset, offset + 4)));
    }

    private static int[] state() {
        int[] names = {
            GL_CURRENT_PROGRAM,
            GL_VERTEX_ARRAY_BINDING,
            GL_ARRAY_BUFFER_BINDING,
            GL_ACTIVE_TEXTURE,
            GL_BLEND_EQUATION_RGB,
            GL_BLEND_EQUATION_ALPHA,
            GL_BLEND_SRC_RGB,
            GL_BLEND_DST_RGB,
            GL_BLEND_SRC_ALPHA,
            GL_BLEND_DST_ALPHA
        };
        int[] result = new int[20];
        for (int i = 0; i < names.length; i++) result[i] = glGetInteger(names[i]);
        int[] box = new int[4];
        glGetIntegerv(GL_SCISSOR_BOX, box);
        System.arraycopy(box, 0, result, 10, 4);
        int[] caps = {GL_BLEND, GL_SCISSOR_TEST, GL_CULL_FACE, GL_DEPTH_TEST};
        for (int i = 0; i < 4; i++) result[14 + i] = glIsEnabled(caps[i]) ? 1 : 0;
        int unit = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        result[18] = glGetInteger(GL_TEXTURE_BINDING_2D);
        result[19] = glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);
        glActiveTexture(unit);
        return result;
    }

    private static void restored(int[] before) {
        if (!Arrays.equals(before, state()))
            throw new AssertionError(
                    "GL 状态未恢复: " + Arrays.toString(before) + " -> " + Arrays.toString(state()));
    }

    private static int seedProgram() {
        int vs = glCreateShader(GL_VERTEX_SHADER),
                fs = glCreateShader(GL_FRAGMENT_SHADER),
                p = glCreateProgram();
        glShaderSource(vs, "#version 150\nvoid main(){gl_Position=vec4(0);}");
        glCompileShader(vs);
        glShaderSource(fs, "#version 150\nout vec4 c;void main(){c=vec4(1);}");
        glCompileShader(fs);
        glAttachShader(p, vs);
        glAttachShader(p, fs);
        glLinkProgram(p);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(p, GL_LINK_STATUS) == 0) throw new AssertionError(glGetProgramInfoLog(p));
        return p;
    }

    private static int[] route(boolean array, File output, List<Item> items, int[] expected)
            throws Exception {
        int[] ids = new int[array ? 2 : 4];
        final int[] seeds = new int[4];
        try (AutoCloseable cleanup =
                () -> {
                    glUseProgram(0);
                    glBindVertexArray(0);
                    glBindBuffer(GL_ARRAY_BUFFER, 0);
                    glDeleteProgram(seeds[3]);
                    glDeleteVertexArrays(seeds[0]);
                    glDeleteBuffers(seeds[1]);
                    glDeleteBuffers(seeds[2]);
                    for (int id : ids) glDeleteTextures(id);
                }) {
            if (array) {
                ids[0] = texture(true, MONO);
                ids[1] = texture(true, COLOR);
            } else
                for (int i = 0; i < 4; i++)
                    ids[i] = texture(false, new int[][] {i < 2 ? MONO[i] : COLOR[i - 2]});
            int seedVao = seeds[0] = glGenVertexArrays(),
                    seedVbo = seeds[1] = glGenBuffers(),
                    seedEbo = seeds[2] = glGenBuffers(),
                    seed = seeds[3] = seedProgram();
            glBindVertexArray(seedVao);
            glBindBuffer(GL_ARRAY_BUFFER, seedVbo);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, seedEbo);
            glUseProgram(seed);
            glEnable(GL_BLEND);
            glBlendEquationSeparate(GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_SUBTRACT);
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_DST_ALPHA, GL_ZERO, GL_ONE);
            glEnable(GL_CULL_FACE);
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_SCISSOR_TEST);
            glScissor(3, 5, 7, 11);
            glActiveTexture(GL_TEXTURE3);
            int[] before = state();
            String name = array ? "array" : "2d";
            try (GlQuadRenderer renderer =
                    new GlQuadRenderer(
                            new Lwjgl3GlApi(),
                            CAPACITY,
                            (resource, generation) -> {
                                if (generation != 7) throw new IllegalArgumentException("过期资源代际");
                                return new Texture(
                                        array ? GL_TEXTURE_2D_ARRAY : GL_TEXTURE_2D,
                                        ids[Math.toIntExact(resource) - 1]);
                            },
                            GlProgramProvider.NONE)) {
                restored(before);
                if (glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING) != seedEbo)
                    throw new AssertionError("构造污染 EBO");
                glDisable(GL_SCISSOR_TEST);
                glClearColor(0, 0, 0, 0);
                glClear(GL_COLOR_BUFFER_BIT);
                glEnable(GL_SCISSOR_TEST);
                List<OrderedQuadBatch> segments = new ArrayList<>();
                try (GlQuadRenderer.Scope scope = renderer.begin(MVP, H, false)) {
                    OrderedQuadCollector collector =
                            new OrderedQuadCollector(
                                    CAPACITY,
                                    batch -> {
                                        segments.add(batch);
                                        scope.consumeBatch(batch);
                                    });
                    for (Item item : items) {
                        long resource =
                                item.material == 0
                                        ? 0
                                        : array
                                                ? (item.material == 1 ? 1 : 2)
                                                : (item.material == 1 ? 1 : 3) + item.page;
                        Quad src = item.quad;
                        Quad quad =
                                array
                                        ? src
                                        : q(
                                                (int) src.x,
                                                (int) src.y,
                                                (int) src.width,
                                                (int) src.height,
                                                0,
                                                src.color);
                        collector.add(new State(item.material, resource, 7, item.clip), quad);
                    }
                    collector.flush();
                }
                restored(before);
                glFinish();
                int[] actual = readback();
                compare(name, expected, actual);
                image(new File(output, name + "-readback.png"), actual);
                Counters c = renderer.counters();
                if (c.vertexUploads != 2
                        || c.indexUploads != 1
                        || c.vertexBytes != 7 * 4 * 36
                        || segments.size() != 2
                        || c.draws != (array ? 5 : 7)
                        || c.textureBindings != (array ? 2 : 6))
                    throw new AssertionError("容量/上传合同失败");
                System.out.println(
                        name
                                + " correctness draws="
                                + c.draws
                                + " uploads="
                                + c.vertexUploads
                                + " bytes="
                                + c.vertexBytes
                                + " textureBindings="
                                + c.textureBindings
                                + " indexUploads="
                                + c.indexUploads);
                // 真实异常退出：资源换代未准备及未知材质均不得污染宿主进入态。
                for (int failure = 0; failure < 2; failure++) {
                    final int mode = failure;
                    try (GlQuadRenderer.Scope scope = renderer.begin(MVP, H, false)) {
                        OrderedQuadCollector collector =
                                new OrderedQuadCollector(CAPACITY, scope::consumeBatch);
                        collector.add(
                                new State(mode == 0 ? 1 : 99, 1, mode == 0 ? 8 : 7, null),
                                q(0, 0, 1, 1, 0, -1));
                        collector.flush();
                        throw new AssertionError("异常探针没有触发");
                    } catch (IllegalArgumentException expectedFailure) {
                        restored(before);
                    }
                }
                long[] timings = new long[3];
                try (GlQuadRenderer.Scope scope = renderer.begin(MVP, H, false)) {
                    for (int i = 0; i < 300; i++)
                        for (OrderedQuadBatch batch : segments) scope.consumeBatch(batch);
                    glFinish();
                    for (int sample = 0; sample < timings.length; sample++) {
                        long start = System.nanoTime();
                        for (int i = 0; i < 2000; i++)
                            for (OrderedQuadBatch batch : segments) scope.consumeBatch(batch);
                        glFinish();
                        timings[sample] = System.nanoTime() - start;
                    }
                }
                restored(before);
                Arrays.sort(timings);
                System.out.println(
                        name
                                + " probe 2000 replays submit+finish ns="
                                + Arrays.toString(timings)
                                + " medianNsPerReplay="
                                + (timings[1] / 2000.0)
                                + " (含 CPU提交与GPU完成，无独立GPU计时；仅初步probe，不裁定胜出)");
                int error = glGetError();
                if (error != GL_NO_ERROR) throw new AssertionError("GL error=" + error);
                return actual;
            }
        }
    }

    @Override
    protected void runTests() throws Exception {
        String[] args = arguments();
        File output = new File(args.length == 0 ? "build/font-gl-probe/output" : args[0]);
        if (!output.isDirectory() && !output.mkdirs()) throw new IllegalStateException("不能创建出图目录");
        GlfwTestEnvironment environment = own(new GlfwTestEnvironment());
        own(environment.window(W, H, "GlBatchScenario", Profile.CORE, 2, true, true));
        System.out.println(
                "LWJGL="
                        + org.lwjgl.Version.getVersion()
                        + " Java="
                        + System.getProperty("java.version"));
        System.out.println(
                "vendor="
                        + glGetString(GL_VENDOR)
                        + " renderer="
                        + glGetString(GL_RENDERER)
                        + " version="
                        + glGetString(GL_VERSION)
                        + " GLSL="
                        + glGetString(GL_SHADING_LANGUAGE_VERSION));
        System.out.println(
                "GL_MAX_ARRAY_TEXTURE_LAYERS="
                        + glGetInteger(GL_MAX_ARRAY_TEXTURE_LAYERS)
                        + " testAllocatedLayers=2 format=RGBA8 1x1");
        int target = glGenTextures(), fbo = glGenFramebuffers();
        onClose(
                () -> {
                    glBindFramebuffer(GL_FRAMEBUFFER, 0);
                    glDeleteFramebuffers(fbo);
                    glDeleteTextures(target);
                });
        {
            glBindTexture(GL_TEXTURE_2D, target);
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_RGBA8,
                    W,
                    H,
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    (ByteBuffer) null);
            glBindFramebuffer(GL_FRAMEBUFFER, fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
            require(
                    glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE,
                    "FBO incomplete");
            glViewport(0, 0, W, H);
            glDisable(GL_DITHER);
            glDisable(GL_FRAMEBUFFER_SRGB);
            List<Item> items = scene();
            int[] expected = reference(items);
            image(new File(output, "software-reference.png"), expected);
            int[][] routes = new int[2][];
            scenario("2D原序像素与状态恢复", () -> routes[0] = route(false, output, items, expected));
            scenario("array原序像素与状态恢复", () -> routes[1] = route(true, output, items, expected));
            scenario("2D与array参考一致", () -> compare("2d-vs-array", routes[0], routes[1]));
            System.out.println(
                    "GL_BATCH_SCENARIO_PASS stateRestore=normal+exception capacity=4 segments=2");
        }
    }

    public static void main(String[] args) throws Exception {
        new GlBatchScenario().run(args);
    }
}
