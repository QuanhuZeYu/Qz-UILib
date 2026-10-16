package club.heiqi.uilib.host.desktop;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.GalleryMediaResources;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.testing.media.MediaFixture;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.scene.control.SceneVideo;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSize;

/** 用户从目录进入媒体页，经正式GLFW输入操作与actualswap验证，共用广场、正式媒体服务及短样本。 */
public final class DesktopGalleryMediaVerification extends TestApplication {
    private DesktopWindow window;
    private DesktopTestInput input;
    private long swaps;
    private void frames(int count) { for(int i=0;i<count;i++){window.frame();swaps+=window.mediaPresentations().size();} }
    private void await(BooleanSupplier condition,String reason) throws Exception {
        long deadline=System.nanoTime()+5_000_000_000L;
        while(!condition.getAsBoolean()&&System.nanoTime()<deadline){frames(1);Thread.sleep(5);}
        if(!condition.getAsBoolean()) {
            MediaSnapshot s=snapshot();System.out.println("WAIT reason="+reason+" snapshot="+(s==null?"null":s.state+" "+s.positionNanos+" fail="+(s.failure==null?"none":s.failure.cause))+" native="+IsolatedMediaAdapters.resources());
            System.out.println("TEXT "+window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.TEXT).map(c->c.text.logicalText).collect(Collectors.joining(" | ")));
        }
        require(condition.getAsBoolean(),reason);
    }
    private MediaSnapshot snapshot() {
        final MediaSnapshot[] value={null};
        window.domain.execute(()->DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.control() instanceof SceneVideo)
                .findFirst().ifPresent(e->value[0]=SceneVideo.snapshot(e.node).get()));return value[0];
    }
    private long sessionId() {return window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.VIDEO).findFirst().map(c->c.video.frame.key.sessionId).orElse(-1L);}
    private boolean text(String value) {return window.lastPlan.commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.contains(value));}
    private List<SceneNode> fields() {return DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.control() instanceof club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget).map(e->e.node).collect(Collectors.toList());}
    private void type(SceneNode field,String value,boolean enter) {
        input.moveTo(field);input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);
        input.key.invoke(window.handle(),GLFW_KEY_A,0,GLFW_PRESS,GLFW_MOD_CONTROL);value.codePoints().forEach(c->input.character.invoke(window.handle(),c));
        if(enter)input.key.invoke(window.handle(),GLFW_KEY_ENTER,0,GLFW_PRESS,0);frames(1);
    }
    private void baseline(MediaService service,String stage) {
        require(service.activeSessions()==0,stage+"会话未关闭");
        require(IsolatedMediaAdapters.resources().activeHandles()==0,stage+"native未归零: "+IsolatedMediaAdapters.resources());
        require(window.mediaViews().activeConsumers()==0,stage+"consumer未解除");
        club.heiqi.uilib.host.client.gl.GlVideoResources.Snapshot gpu=window.mediaResources();
        System.out.println("BASELINE stage="+stage+" sessions="+service.activeSessions()+" native="+IsolatedMediaAdapters.resources()+" textures="+gpu.textureCount+" bytes="+gpu.retainedBytes+" scratch="+gpu.scratchBytes);
        require(gpu.textureCount==0&&gpu.retainedBytes==0&&gpu.scratchBytes==0,stage+"视频GPU资源仍被持有");
        require(Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&(t.getName().startsWith("uilib-media-decoder-")||t.getName().startsWith("uilib-media-output-"))),stage+"worker未关闭");
    }
    @Override protected void runTests() throws Exception {
        Path output=Paths.get(arguments()[0]);Files.createDirectories(output);
        scenario("真实共享广场媒体入口、示例、控件和离页关闭",()->{
            Path sample;
            final Path[] localFile={null};
            java.util.concurrent.atomic.AtomicReference<club.heiqi.uilib.ui.media.MediaSource> selection=new java.util.concurrent.atomic.AtomicReference<>();
            java.util.concurrent.atomic.AtomicInteger chooserCalls=new java.util.concurrent.atomic.AtomicInteger();
            try(GalleryMediaResources media=new GalleryMediaResources(new MediaService(IsolatedMediaAdapters.decoderFactory(),IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT));
                    MediaFixture fixture=new MediaFixture();
                    AutoCloseable localCleanup=()->{if(localFile[0]!=null)Files.deleteIfExists(localFile[0]);}) {
                MediaService service=media.service();
                try(DesktopWindow opened=DesktopWindow.openWithServices(output.resolve("gallery.yaml"),service,true,
                        services->DesktopGallery.createGallery(services,TestGallery.Page.HOME,media,()->{chooserCalls.incrementAndGet();return selection.getAndSet(null);}))) {
                    window=opened;glfwSetWindowSize(window.handle(),1200,1000);frames(12);input=new DesktopTestInput(window);
                    require(service.activeSessions()==0&&IsolatedMediaAdapters.resources().runtimeLoaders==0&&chooserCalls.get()==0,"目录预热了媒体native或自动弹文件选择");
                    input.click("打开媒体播放");frames(2);
                    require(service.activeSessions()==0&&text("媒体播放"),"进入媒体页自动打开或未呈现");
                    input.click("内置示例");await(()->snapshot()!=null&&snapshot().statistics.presentedFrames>0&&snapshot().positionNanos>30_000_000L,"示例未actualswap/device播放");
                    require(snapshot().muted,"示例没有初始静音");sample=Paths.get(media.exampleSource().uri);require(Files.exists(sample),"示例未提取");
                    System.out.println("FILE source="+media.exampleSource()+" exists="+Files.exists(sample)+" nativeInput="+sample);
                    long firstSwaps=swaps;input.click("暂停");await(()->snapshot().state==MediaState.PAUSED,"暂停未生效");
                    type(fields().get(1),"0.01",true);input.click("取消静音");await(()->!snapshot().muted,"静音控制未生效");input.click("静音");
                    long generation=snapshot().generation;type(fields().get(2),"0",true);await(()->snapshot().generation>generation&&snapshot().state==MediaState.PAUSED,"合法零秒定位未预览");
                    input.click("播放");await(()->swaps>firstSwaps+2&&snapshot().positionNanos>100_000_000L,"恢复播放无新swap");
                    DesktopGalleryEvidence.saveGeometry(window,output.resolve("media.geometry.json"));
                    javax.imageio.ImageIO.write(capture(),"png",output.resolve("media.png").toFile());
                    localFile[0]=Files.createTempFile("qz 用户 +video-",arguments().length>1?".MP4":".ViDeO");
                    Files.copy(arguments().length>1?Paths.get(arguments()[1]):sample,localFile[0],java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    club.heiqi.uilib.ui.media.MediaSource chosen=club.heiqi.uilib.testing.host.LocalMediaFiles.resolve(localFile[0].toString());
                    selection.set(chosen);input.click("选择本地视频");await(()->snapshot()!=null&&snapshot().statistics.presentedFrames>0&&snapshot().positionNanos>50_000_000L,"选择任意本地视频未实际播放");
                    require(chooserCalls.get()==1&&text(chosen.uri.toString()),"选择文件后地址未同步");
                    long beforeCancel=sessionId();input.click("选择本地视频");frames(2);
                    require(chooserCalls.get()==2&&sessionId()==beforeCancel&&text(chosen.uri.toString()),"取消选择更换了会话或地址");
                    type(fields().get(0),localFile[0].toString(),false);input.click("打开媒体");
                    await(()->snapshot()!=null&&snapshot().statistics.presentedFrames>0&&sessionId()!=beforeCancel&&snapshot().positionNanos>50_000_000L,"本地绝对路径不能播放");
                    System.out.println("LOCAL_VIDEO source="+chosen+" absolute="+localFile[0]+" chooserCalls="+chooserCalls.get()+" cancelKeptSession=true audioPlayed="+snapshot().positionNanos);
                    DesktopGalleryEvidence.saveGeometry(window,output.resolve("local-media.geometry.json"));
                    javax.imageio.ImageIO.write(capture(),"png",output.resolve("local-media.png").toFile());
                    input.click("停止");frames(3);baseline(service,"stop");
                    type(fields().get(0),"bad address",false);input.click("打开媒体");frames(2);require(text("无法打开媒体"),"非法地址未显示反馈");
                    type(fields().get(0),sample.resolveSibling("missing-media.ts").toString(),false);input.click("打开媒体");await(()->snapshot()!=null&&snapshot().state==MediaState.FAILED,"缺失文件未显示失败");
                    require(IsolatedMediaAdapters.resources().activeHandles()==0,"缺失文件失败未清理native");
                    type(fields().get(0),fixture.uri().toString(),false);input.click("打开媒体");await(()->snapshot()!=null&&snapshot().statistics.presentedFrames>0&&snapshot().positionNanos>50_000_000L,"HTTP HLS没有正式播放");
                    long oldSession=window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.VIDEO).findFirst().get().video.frame.key.sessionId;
                    input.click("打开媒体");await(()->window.lastPlan.commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.VIDEO&&c.video.frame.key.sessionId!=oldSession),"页内地址重开未换会话");
                    input.click("返回场景概览");frames(3);baseline(service,"home");
                    input.click("打开媒体播放");frames(2);require(service.activeSessions()==0&&text(fixture.uri().toString()),"返回媒体页未保留地址或自动播放");input.click("内置示例");await(()->snapshot()!=null&&snapshot().statistics.presentedFrames>0,"重复进入不能再次播放");
                    await(()->snapshot().state==MediaState.ENDED,"短示例未结束");input.click("内置示例");await(()->snapshot()!=null&&snapshot().state==MediaState.PLAYING&&snapshot().statistics.presentedFrames>0,"结束后不能重开示例");
                }
                require(service.activeSessions()==0&&IsolatedMediaAdapters.resources().activeHandles()==0,"关窗后资源残留");
                require(window.mediaResources().closed&&window.mediaResources().textureCount==0,"关窗GPU未关闭");
            }
            require(!Files.exists(sample)&&!Files.exists(localFile[0]),"示例或任意本地临时文件未删除");
            Files.write(output.resolve("media-summary.json"),("{\"actualSwap\":"+swaps+",\"chooserCalls\":"+chooserCalls.get()+",\"cancelKeptSession\":true,\"absolutePathPlayed\":true,\"httpHlsPlayed\":true,\"stopHomeReleased\":true,\"tempDeleted\":true}\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("GALLERY_MEDIA actualSwap="+swaps+" sampleAndHls=true stopped=true homeClosed=true reopened=true tempDeleted=true");
        });
    }
    private java.awt.image.BufferedImage capture() {
        int width=window.width(),height=window.height();java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocateDirect(width*height*4);
        int previous=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_READ_BUFFER);
        try{org.lwjgl.opengl.GL11.glReadBuffer(org.lwjgl.opengl.GL11.GL_FRONT);org.lwjgl.opengl.GL11.glReadPixels(0,0,width,height,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,bytes);}
        finally{org.lwjgl.opengl.GL11.glReadBuffer(previous);}
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=(y*width+x)*4;image.setRGB(x,height-1-y,0xFF000000|(bytes.get(i)&255)<<16|(bytes.get(i+1)&255)<<8|bytes.get(i+2)&255);}return image;
    }
    public static void main(String[] args) throws Exception {new DesktopGalleryMediaVerification().run(args);}
}
