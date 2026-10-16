package club.heiqi.uilib.host.desktop;

import club.heiqi.uilib.ui.scene.control.SceneVideo;
import club.heiqi.uilib.ui.scene.control.SceneMediaControls;

import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.media.MediaFrameKey;
import club.heiqi.uilib.ui.media.MediaVideoFrame;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaPresentation;
import club.heiqi.uilib.ui.media.MediaFrameLease;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;



import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.lwjgl.opengl.GL11.*;

/** 正式DesktopWindow、生产HLS会话、真实GPU/设备与交换回执的有限验证消费者。 */
public final class DesktopMediaVerification extends TestApplication {
    private final List<MediaPresentation> samples = new ArrayList<>();
    private Path output;
    private MediaSession observed;
    private static void frame(DesktopWindow window, List<MediaPresentation> samples) {
        window.frame(); samples.addAll(window.mediaPresentations());
    }
    private void await(DesktopWindow window, BooleanSupplier predicate, String message) throws Exception {
        long limit = System.nanoTime() + 6_000_000_000L;
        while (!predicate.getAsBoolean() && System.nanoTime() < limit) {
            frame(window, samples);
            Thread.sleep(5);
        }
        if (!predicate.getAsBoolean()) {
            MediaSnapshot s = observed.snapshot();
            System.out.println("AWAIT state="+s.state+" generation="+s.generation+" position="+s.positionNanos+" intent="+s.playIntent+" ready="+s.outputReady
                    +" video="+(s.video==null?"null":s.video.key+" pts="+s.video.presentationNanos+" duration="+s.video.durationNanos)
                    +" decoded="+s.statistics.decodedVideoFrames+" presented="+s.statistics.presentedFrames+" failure="+(s.failure==null?"none":s.failure.cause));
            System.out.println("PLAN "+window.lastPlan.commands().stream().filter(c->c.kind==club.heiqi.uilib.ui.scene.paint.PaintCommand.Kind.VIDEO).map(c->c.video.frame.key.toString()).collect(java.util.stream.Collectors.toList()));
        }
        require(predicate.getAsBoolean(), message);
    }
    @Override protected void runTests() throws Exception {
        output = Paths.get(arguments()[1]); Files.createDirectories(output);
        scenario("真实HLS视频设备播放与交换回执、标准控制和页面拥有", () -> {
            MediaService service = own(new MediaService(IsolatedMediaAdapters.decoderFactory(), IsolatedMediaAdapters.audioSinkFactory(), MediaBudget.DEFAULT));
            MediaSession session = service.open(MediaSource.of(URI.create(arguments()[0])), false);
            observed = session;
            session.volume(.01); session.muted(true);
            SceneNode[] video = {null}, controls = {null};
            java.util.concurrent.atomic.AtomicReference<club.heiqi.uilib.ui.reactive.Signal<List<SceneNode>>> children = new java.util.concurrent.atomic.AtomicReference<>();
            DesktopWindow window = own(DesktopWindow.openWithServices(output.resolve("desktop-media.yaml"), service, false, services -> page -> {
                page.own(session);
                SceneNode root = new SceneNode().axis(SceneNode.Axis.COLUMN).gap(8).padding(10)
                        .size(Size.fill(1), Size.fill(1)).background(0xFF101010);
                video[0] = SceneVideo.create(page, session).size(Size.fill(1), Size.fixed(130));
                SceneNode secondary = SceneVideo.create(page, session).size(Size.fill(1), Size.fixed(130));
                controls[0] = SceneMediaControls.create(page, session);
                children.set(page.domain().signal(Arrays.asList(video[0], secondary, controls[0])));
                root.bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.ownedChildren(children.get())); page.mountOwned(root);
            }));
            System.out.println("GPU vendor=" + glGetString(GL_VENDOR) + " renderer=" + glGetString(GL_RENDERER) + " version=" + glGetString(GL_VERSION));
            await(window, () -> session.snapshot().video != null && session.snapshot().statistics.presentedFrames > 0, "没有正式首帧swap回执");
            require(window.lastPlan.commands().stream().filter(c -> c.kind == club.heiqi.uilib.ui.scene.paint.PaintCommand.Kind.VIDEO).count() == 2, "两个正式视图未输出VIDEO");
            require(session.snapshot().statistics.presentedFrames == 1, "共享首帧重复计数");
            MediaVideoFrame firstFrame = session.snapshot().video;
            MediaFrameLease held = service.retainPresentation(firstFrame.key); require(held != null, "无法持有首帧反例联系"); own(held);
            DesktopTestInput input = new DesktopTestInput(window);
            input.click("播放");
            long started = System.nanoTime(), limit = started + 2_500_000_000L;
            boolean brieflyAudible = false;
            // 只对本source设置安全低gain；其余短播静音，系统音量不动。
            session.muted(false);
            while (System.nanoTime() < limit) {
                frame(window, samples);
                if (System.nanoTime() - started > 200_000_000L && !brieflyAudible) { session.muted(true); brieflyAudible = true; }
                MediaSnapshot snapshot = session.snapshot();
                require(snapshot.failure == null, "媒体失败: " + (snapshot.failure == null ? "" : snapshot.failure.cause));
                Thread.sleep(5);
            }
            MediaSnapshot played = session.snapshot();
            require(samples.size() > 2 && played.positionNanos > 100_000_000L && played.statistics.peakDeviceBuffers > 0,
                    "未观察到设备消费与多个实际视频提交");
            capture(window, video[0]);
            input.click("暂停"); await(window, () -> session.snapshot().state == MediaState.PAUSED, "未暂停");
            await(window, () -> window.lastPlan.commands().stream().anyMatch(c -> c.kind == club.heiqi.uilib.ui.scene.paint.PaintCommand.Kind.VIDEO
                    && c.video.frame.key.equals(session.snapshot().video.key)), "暂停最后选帧未交给正式计划");
            frame(window, samples);
            long position = session.snapshot().positionNanos, presented = session.snapshot().statistics.presentedFrames;
            MediaFrameKey pausedKey = session.snapshot().video.key;
            for (int i = 0; i < 6; i++) { frame(window, samples); Thread.sleep(15); }
            require(session.snapshot().positionNanos == position, "暂停设备位置变化");
            require(session.snapshot().statistics.presentedFrames == presented, "暂停计数前=" + presented + " 后=" + session.snapshot().statistics.presentedFrames + " 首key=" + pausedKey + " 末key=" + session.snapshot().video.key);
            club.heiqi.uilib.testing.media.VideoGlVerification.Target target = new club.heiqi.uilib.testing.media.VideoGlVerification.Target() {
                public int width() { return window.width(); } public int height() { return window.height(); }
                public void clear() { glDisable(GL_SCISSOR_TEST); glColorMask(true,true,true,true); glViewport(0,0,width(),height()); glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT); }
                public int pixel(int x, int y) { ByteBuffer b=ByteBuffer.allocateDirect(4); glReadBuffer(GL_BACK); glReadPixels(x,height()-1-y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,b); return 0xFF000000 | (b.get(0)&255)<<16 | (b.get(1)&255)<<8 | b.get(2)&255; }
                public boolean isTexture(int id) { return glIsTexture(id); }
            };
            MediaVideoFrame secondFrame = session.snapshot().video;
            club.heiqi.uilib.testing.media.VideoGlVerification.failure(window.fonts, service, secondFrame, target);
            club.heiqi.uilib.testing.media.VideoGlVerification.run(window.fonts, service, firstFrame, secondFrame, target, () -> session.seek(1_000_000_000L));
            await(window, () -> session.snapshot().state == MediaState.PAUSED, "GPU切代后预览没有结算");
            club.heiqi.uilib.testing.media.VideoGlVerification.failure(window.fonts, service, session.snapshot().video, target);
            frame(window, samples);
            require(window.mediaViews().activeConsumers() == 3, "未按挂载登记两个视频和控制consumer");
            window.domain.execute(() -> children.get().set(Arrays.asList(video[0], controls[0])));
            frame(window, samples);
            require(window.mediaViews().activeConsumers() == 2 && service.activeSessions() == 1, "卸载独立view关闭共享会话或未解除consumer");
            SceneNode volume = controls[0].children().get(3), seek = controls[0].children().get(4);
            type(input, window, volume, "0.02");
            require(session.snapshot().volume == .02, "标准输入没有设置本会话音量");
            long generation = session.snapshot().generation;
            type(input, window, seek, "1");
            await(window, () -> session.snapshot().generation > generation && session.snapshot().state == MediaState.PAUSED, "暂停定位没有实际预览回执");
            require(!session.snapshot().playIntent, "暂停定位改写播放意图");
            require(window.lastPlan.commands().stream().filter(c -> c.kind == club.heiqi.uilib.ui.scene.paint.PaintCommand.Kind.VIDEO)
                    .allMatch(c -> c.video.frame.key.generation == session.snapshot().generation), "新代正式计划保留旧帧");
            window.page.close();
            require(window.mediaViews().activeConsumers() == 0 && service.activeSessions() == 0, "页面owned会话/consumer未释放");
            System.out.println("MEDIA decoded=" + played.statistics.decodedVideoFrames + " presented=" + played.statistics.presentedFrames
                    + " deviceBuffers=" + played.statistics.peakDeviceBuffers + " position=" + played.positionNanos + " samples=" + samples.size());
        });
        StringBuilder csv = new StringBuilder("session,generation,sequence,swapNanos,videoPTS,audioPosition,error,deviceSampleNanos\n");
        for (MediaPresentation p : samples) csv.append(p.key.sessionId).append(',').append(p.key.generation).append(',').append(p.key.sequence)
                .append(',').append(p.sampledNanos).append(',').append(p.videoPresentationNanos).append(',').append(p.audioPositionNanos)
                .append(',').append(p.synchronizationErrorNanos).append(',').append(p.deviceSampledNanos).append('\n');
        Files.write(output.resolve("presentation.csv"), csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void type(DesktopTestInput input, DesktopWindow window, SceneNode field, String text) {
        input.moveTo(field); input.mouse.invoke(window.handle(), 0, org.lwjgl.glfw.GLFW.GLFW_PRESS, 0);
        input.mouse.invoke(window.handle(), 0, org.lwjgl.glfw.GLFW.GLFW_RELEASE, 0);
        input.key.invoke(window.handle(), org.lwjgl.glfw.GLFW.GLFW_KEY_A, 0, org.lwjgl.glfw.GLFW.GLFW_PRESS, org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
        text.codePoints().forEach(code -> input.character.invoke(window.handle(), code));
        input.key.invoke(window.handle(), org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, org.lwjgl.glfw.GLFW.GLFW_PRESS, 0);
        window.frame();
    }
    private static void capture(DesktopWindow window, SceneNode video) throws Exception {
        LayoutBox box = DesktopTestInput.geometry(window, video).box;
        int w = box.width, h = box.height;
        ByteBuffer data = ByteBuffer.allocateDirect(w * h * 4);
        int readBuffer = glGetInteger(GL_READ_BUFFER);
        glReadBuffer(GL_FRONT);
        try { glReadPixels(box.x, window.height() - box.y - h, w, h, GL_RGBA, GL_UNSIGNED_BYTE, data); }
        finally { glReadBuffer(readBuffer); }
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); int colored = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int offset = ((h - y - 1) * w + x) * 4;
            int red = data.get(offset) & 255, green = data.get(offset + 1) & 255, blue = data.get(offset + 2) & 255;
            image.setRGB(x, y, 0xFF000000 | red << 16 | green << 8 | blue);
            if (Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) > 30) colored++;
        }
        require(colored > 100, "已交换视频区域没有实际彩色像素: " + colored);
        ImageIO.write(image, "png", Paths.get(System.getProperty("uilib.media.evidence"), "video-frame.png").toFile());
        System.out.println("PIXELS colored=" + colored + " box=" + w + "x" + h + " boundary=desktop-swap");
    }
    public static void main(String[] args) throws Exception { new DesktopMediaVerification().run(args); }
}
