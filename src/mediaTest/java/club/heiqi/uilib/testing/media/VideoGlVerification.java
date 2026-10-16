package club.heiqi.uilib.testing.media;

import club.heiqi.uilib.ui.scene.image.ImageContent;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImagePlacement;
import club.heiqi.uilib.host.client.font.FramebufferCoordinates;
import club.heiqi.uilib.host.client.font.ClientFontServices;

import club.heiqi.uilib.ui.media.MediaVideoFrame;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.GlGlyphAtlas;
import club.heiqi.uilib.host.client.gl.GlImageResources;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.host.client.gl.GlVideoSubmissions;
import club.heiqi.uilib.host.client.gl.GlVideoResources;





import club.heiqi.uilib.ui.scene.layout.LayoutBox;

import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.video.VideoPlacement;
import java.nio.ByteBuffer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import static club.heiqi.uilib.host.client.gl.GlTokens.*;

/** 两种真实GL binding共用的消费者反例；像素期望从发布帧独立采样，不复制渲染算法。 */
public final class VideoGlVerification {
    public interface Target { int width(); int height(); void clear(); int pixel(int x, int y); boolean isTexture(int id); }
    private static final class Injected extends RuntimeException {}
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static PaintCommand video(MediaVideoFrame frame, LayoutBox box, LayoutBox clip, VideoPlacement.Fit fit) {
        return PaintCommand.video(box, clip, VideoPlacement.fit(frame, box, fit, .5f, .5f));
    }
    private static int[] state(GlApi gl) {
        int[] result = new int[10];
        int[] names = {GL_ACTIVE_TEXTURE, GL_TEXTURE_BINDING_2D, GL_PIXEL_UNPACK_BUFFER_BINDING, GL_UNPACK_ALIGNMENT,
                GL_UNPACK_ROW_LENGTH, GL_UNPACK_SKIP_PIXELS, GL_UNPACK_SKIP_ROWS};
        for (int i = 0; i < names.length; i++) result[i] = gl.glGetInteger(names[i]);
        result[7] = gl.glIsEnabled(GL_SCISSOR_TEST) ? 1 : 0;
        ByteBuffer mask = ByteBuffer.allocateDirect(4); gl.glGetBooleanv(GL_COLOR_WRITEMASK, mask);
        for (int i = 0; i < 4; i++) result[8] |= (mask.get(i) == 0 ? 0 : 1 << i);
        result[9] = gl.glGetInteger(GL_CURRENT_PROGRAM); return result;
    }
    private static GlApi proxy(GlApi actual, Set<Integer> textures, AtomicBoolean fail, String method) {
        return (GlApi) Proxy.newProxyInstance(GlApi.class.getClassLoader(), new Class<?>[]{GlApi.class}, (p, called, args) -> {
            if (called.getName().equals(method) && fail.compareAndSet(true, false)) throw new Injected();
            try {
                Object result = called.invoke(actual, args);
                if (called.getName().equals("glGenTextures")) textures.add((Integer) result);
                return result;
            } catch (InvocationTargetException error) { throw error.getCause(); }
        });
    }
    private static void render(GlPaintPlanRenderer renderer, ClientFontServices fonts, PaintPlan plan, Target target, boolean delegate) {
        try (GlPaintPlanRenderer.Prepared prepared = renderer.prepare(plan, new LayoutBox(0, 0, target.width(), target.height()),
                new GlGlyphAtlas.UploadBudget(4L * 1024 * 1024, 100_000_000L, 32))) {
            try (GlPaintPlanRenderer.Replay replay = prepared.begin(FramebufferCoordinates.orthographic(target.width(), target.height()), target.height(), false)) {
                for (PaintCommand command : plan.commands()) { replay.draw(command); if (delegate && command.kind == PaintCommand.Kind.VIDEO) replay.delegate(() -> {}); }
            }
            GlVideoSubmissions submissions = new GlVideoSubmissions(16);
            try { submissions.add(prepared.takeVideoSubmissions()); submissions.complete(System.nanoTime()); }
            finally { submissions.close(); }
        }
    }
    private static void expected(Target target, MediaVideoFrame frame, int origin, int x, int y) {
        ByteBuffer source = frame.pixels();
        double sx = ((x - origin + .5) / 120d) * frame.width - .5, sy = ((y + .5) / 80d) * frame.height - .5;
        int left = (int) Math.floor(sx), top = (int) Math.floor(sy); double dx = sx - left, dy = sy - top;
        int actual = target.pixel(x, y);
        for (int channel = 0; channel < 3; channel++) {
            double value = 0;
            for (int yy = 0; yy < 2; yy++) for (int xx = 0; xx < 2; xx++) {
                int px = Math.max(0, Math.min(frame.width - 1, left + xx)), py = Math.max(0, Math.min(frame.height - 1, top + yy));
                value += (source.get((py * frame.width + px) * 4 + channel) & 255) * (xx == 0 ? 1 - dx : dx) * (yy == 0 ? 1 - dy : dy);
            }
            int received = actual >>> (16 - 8 * channel) & 255;
            require(Math.abs(received - value) <= 4, "视频纹理身份/像素不符 key=" + frame.key + " xy=" + x + "," + y + " channel=" + channel + " actual=" + received + " expected=" + value);
        }
    }
    public static void run(ClientFontServices fonts, MediaService service, MediaVideoFrame first, MediaVideoFrame second,
            Target target, Runnable invalidate) throws Exception {
        GlApi gl = fonts.gl(); Set<Integer> textures = new HashSet<>(); AtomicBoolean fail = new AtomicBoolean();
        GlApi videoGl = proxy(gl, textures, fail, "glTexImage2D");
        long limit = Math.addExact(first.bytes(), second.bytes());
        try (GlVideoResources videos = new GlVideoResources(videoGl, service, 2, limit);
                GlImageResources images = new GlImageResources(gl, 2, 1024)) {
            GlPaintPlanRenderer renderer = new GlPaintPlanRenderer(gl, fonts.rasterizer(), fonts.atlas(), 2, 1024, GlProgramProvider.NONE, images, videos);
            try {
                LayoutBox viewport = new LayoutBox(0, 0, target.width(), target.height());
                ImageContent blue = new ImageContent(1, 1, new byte[]{0, 0, (byte)255, (byte)255});
                PaintPlan old = new PaintPlanBuilder().add(video(first, new LayoutBox(0, 0, 120, 80), new LayoutBox(10, 10, 100, 60), VideoPlacement.Fit.STRETCH)).build();
                PaintPlan plan = new PaintPlanBuilder().add(old.commands().get(0))
                        .add(video(second, new LayoutBox(120, 0, 120, 80), viewport, VideoPlacement.Fit.STRETCH))
                        .add(PaintCommand.image(new LayoutBox(20, 20, 20, 20), viewport, ImagePlacement.fit(blue, new LayoutBox(20, 20, 20, 20), ImageOptions.defaults().fit(ImageOptions.Fit.STRETCH))))
                        .add(PaintCommand.rect(new LayoutBox(180, 20, 20, 20), viewport, 0xFFFFFF00))
                        .add(PaintCommand.text(new LayoutBox(2, 90, 80, 40), viewport, fonts.layout("VIDEO", new TextStyle("default", 20, 0xFFFFFFFF), 80)))
                        .add(video(first, new LayoutBox(0, 140, 100, 80), new LayoutBox(0, 140, 100, 80), VideoPlacement.Fit.CONTAIN))
                        .add(video(second, new LayoutBox(120, 140, 100, 80), new LayoutBox(120, 140, 100, 80), VideoPlacement.Fit.COVER)).build();
                for (int i = 0; i < 40; i++) { target.clear(); fonts.pumpReady(); render(renderer, fonts, plan, target, true); if (renderer.drawnGlyphs() > 0) break; Thread.sleep(5); }
                expected(target, first, 0, 50, 50); expected(target, second, 120, 150, 50);
                require((target.pixel(25, 25) & 0xFFFFFF) == 0x0000FF, "IMAGE没有保留VIDEO之后的顺序");
                require((target.pixel(185, 25) & 0xFFFFFF) == 0xFFFF00, "RECT没有保留VIDEO之后的顺序");
                require((target.pixel(5, 5) & 0xFFFFFF) == 0, "视频clip外出现像素");
                require(renderer.drawnGlyphs() > 0, "正式TEXT未绘制");
                require((target.pixel(50,141) & 0xFFFFFF) == 0 && (target.pixel(50,180) & 0xFFFFFF) != 0, "CONTAIN未保留上下空白及视频正文");
                require((target.pixel(119,180) & 0xFFFFFF) == 0 && (target.pixel(170,180) & 0xFFFFFF) != 0, "COVER未保持容器裁剪及正文");
                target.clear(); render(renderer, fonts, old, target, false); expected(target, first, 0, 50, 50);
                long retain = videos.retainedBytes(); require(retain <= limit && videos.textureCount() == 2 && videos.scratchBytes() <= limit, "GPU预算失控");
                // 在真正draw前切代，旧纯值像素仍安全，但所属呈现联系已失效。
                try (GlPaintPlanRenderer.Prepared prepared = renderer.prepare(old, viewport, new GlGlyphAtlas.UploadBudget(1024, 100_000_000L, 8))) {
                    invalidate.run(); target.clear(); prepared.render(FramebufferCoordinates.orthographic(target.width(), target.height()), target.height(), false);
                    require(prepared.takeVideoSubmissions().isEmpty() && (target.pixel(50, 50) & 0xFFFFFF) == 0, "切代后仍提交旧帧");
                }
                System.out.println("VIDEO_GL twoKeys=true oldPlan=true orderedImageText=true clipFit=true delegate=true budgetBytes=" + retain + " texturePeak=" + videos.peakTextures());
            } finally { renderer.close(); }
        }
        for (int texture : textures) require(!target.isTexture(texture), "所属视频纹理未删除: " + texture);
        System.out.println("VIDEO_GL textureBaseline=true deleted=" + textures.size());
    }
    /** 独立、仍有效的新代帧：CPU上传及实际draw抛错时保持GL进入态，不补呈现。 */
    public static void failure(ClientFontServices fonts, MediaService service, MediaVideoFrame frame, Target target) {
        GlApi gl = fonts.gl(); Set<Integer> textures = new HashSet<>();
        int foreign = gl.glGenTextures(), pbo = gl.glGenBuffers();
        try {
            gl.glBindTexture(GL_TEXTURE_2D, foreign); gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo); gl.glBufferData(GL_PIXEL_UNPACK_BUFFER, 1024L, GL_STREAM_DRAW);
            gl.glPixelStorei(GL_UNPACK_ALIGNMENT, 8); gl.glPixelStorei(GL_UNPACK_ROW_LENGTH, 7); gl.glPixelStorei(GL_UNPACK_SKIP_PIXELS, 2);
            gl.glColorMask(true, false, true, false); gl.glEnable(GL_SCISSOR_TEST); gl.glScissor(3, 4, 200, 100);
            int[] before = state(gl); PaintPlan plan = new PaintPlanBuilder().add(video(frame, new LayoutBox(0, 0, 120, 80), null, VideoPlacement.Fit.STRETCH)).build();
            for (String stage : new String[]{"glTexImage2D", "glDrawElements"}) {
                AtomicBoolean armed = new AtomicBoolean(stage.equals("glTexImage2D")); GlApi port = proxy(gl, textures, armed, stage);
                try (GlVideoResources videos = new GlVideoResources(port, service, 2, frame.bytes())) {
                    GlPaintPlanRenderer renderer = new GlPaintPlanRenderer(port, fonts.rasterizer(), fonts.atlas(), 2, 1024, GlProgramProvider.NONE, null, videos);
                    try {
                        boolean injected = false;
                        try (GlPaintPlanRenderer.Prepared prepared = renderer.prepare(plan, new LayoutBox(0, 0, target.width(), target.height()), new GlGlyphAtlas.UploadBudget(1024, 100_000_000L, 8))) {
                            armed.set(true); prepared.render(FramebufferCoordinates.orthographic(target.width(), target.height()), target.height(), false);
                        } catch (Injected expected) { injected = true; }
                        require(injected, "未命中实际GPU错误边界: " + stage);
                    } finally { renderer.close(); }
                }
                require(Arrays.equals(before, state(gl)), "GL错误路径未恢复进入态: " + stage);
            }
        } finally {
            gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0); gl.glDeleteBuffers(pbo); gl.glBindTexture(GL_TEXTURE_2D, 0); gl.glDeleteTextures(foreign);
            gl.glPixelStorei(GL_UNPACK_ALIGNMENT, 4); gl.glPixelStorei(GL_UNPACK_ROW_LENGTH, 0); gl.glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
            gl.glColorMask(true, true, true, true); gl.glDisable(GL_SCISSOR_TEST);
        }
        for (int texture : textures) require(!target.isTexture(texture), "异常路径视频纹理未删除");
        System.out.println("VIDEO_GL uploadDrawFailureRestore=true");
    }
}
