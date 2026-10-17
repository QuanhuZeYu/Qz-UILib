package club.heiqi.uilib.host.desktop;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.GalleryEnvironment;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.testing.media.MediaLiveFixture;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.scene.control.SceneMediaControls;
import club.heiqi.uilib.ui.scene.control.SceneVideo;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;

/** 自包含音频、直播窗口和过期恢复；只用正式媒体会话、页面控件与真实GLFW。 */
public final class DesktopMediaUxVerification extends TestApplication {
    private Path output;
    public static void main(String[] args)throws Exception{
        new DesktopMediaUxVerification().run(args);
        try(MediaLiveFixture fixture=new MediaLiveFixture()){
            DesktopLiveMediaVerification.main(new String[]{fixture.uri().toString(),Paths.get(args[0]).resolve("live-core").toString()});
        }
    }
    protected void runTests()throws Exception{
        output=Paths.get(arguments()[0]);Files.createDirectories(output);
        scenario("真实横竖视频完整观看与窗口适配",()->{
            for(int[] size:new int[][]{{320,180},{180,320}}){
                Path sample=output.resolve("sample-"+size[0]+"x"+size[1]+".avi");Files.write(sample,MediaLiveFixture.video(size[0],size[1]));
                try(MediaService service=new MediaService(IsolatedMediaAdapters.decoderFactory(),IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT);
                    DesktopWindow window=DesktopWindow.openWithServices(output.resolve("view-"+size[0]+".yaml"),service,false,services->new TestGallery(new GalleryEnvironment(){public club.heiqi.uilib.ui.settings.SettingsConfiguration settings(){return services.settings();}public MediaService media(){return service;}public MediaSource exampleSource(){return MediaSource.of(sample.toUri());}},TestGallery.Page.MEDIA))){
                    DesktopTestInput input=new DesktopTestInput(window);DesktopTestFrames.settle(window);input.click("内置示例");await(window,()->window.lastPlan.commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.VIDEO),8000,"横竖样片没有实际视频输出");
                    input.click("暂停");DesktopTestFrames.settle(window);
                    for(int width:new int[]{1000,640}){
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.handle(),width,800);DesktopTestFrames.settle(window);
                        PaintCommand video=window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.VIDEO).findFirst().get();
                        require(video.video.frame.width==size[0]&&video.video.frame.height==size[1],"真实轨道尺寸错误");
                        require(video.video.width<=video.box.width+.1f&&video.video.height<=video.box.height+.1f&&Math.abs(video.video.width*size[1]-video.video.height*size[0])<1,"完整观看比例被拉伸或裁掉");
                        java.awt.image.BufferedImage image=DesktopTestReadback.readFront(window);int x=Math.round(video.video.x+video.video.width/2),y=Math.round(video.video.y+video.video.height/2);require((image.getRGB(x,y)&0xffffff)!=0x09090b,"真实视频中心没有像素");
                        ImageIO.write(image,"png",output.resolve("video-"+size[0]+"x"+size[1]+"-"+width+".png").toFile());
                    }
                }finally{Files.deleteIfExists(sample);}
            }
        });
        scenario("纯音频沿共享广场显示紧凑状态并可操作",()->{
            Path audio=output.resolve("sample-audio.wav");Files.write(audio,MediaLiveFixture.audio());
            try(MediaService service=new MediaService(IsolatedMediaAdapters.decoderFactory(),IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT);
                DesktopWindow window=DesktopWindow.openWithServices(output.resolve("audio.yaml"),service,false,services->{
                    TestGallery gallery=new TestGallery(new GalleryEnvironment(){public club.heiqi.uilib.ui.settings.SettingsConfiguration settings(){return services.settings();}public MediaService media(){return service;}public MediaSource exampleSource(){return MediaSource.of(audio.toUri());}},TestGallery.Page.MEDIA);
                    services.onCloseRequested(gallery::requestClose);return gallery;
                })){
                DesktopTestInput input=new DesktopTestInput(window);DesktopTestFrames.settle(window);input.click("内置示例");await(window,()->text(window,"正在播放音频")&&text(window,"正在播放"),8000,"音频轨道没有进入紧凑态");
                require(window.lastPlan.commands().stream().noneMatch(c->c.kind==PaintCommand.Kind.VIDEO),"音频保留了黑色视频输出");
                input.click("暂停");await(window,()->text(window,"已暂停"),3000,"音频暂停失败");input.click("播放");await(window,()->text(window,"正在播放"),3000,"音频恢复失败");
                DesktopTestFrames.settle(window);ImageIO.write(DesktopTestReadback.readFront(window),"png",output.resolve("audio-compact.png").toFile());
                input.click("停止");window.frame();require(service.activeSessions()==0,"音频停止没有释放会话");
            }finally{Files.deleteIfExists(audio);}
        });
        scenario("真实直播窗口、位置过期和显式回到直播",()->{
            try(MediaLiveFixture fixture=new MediaLiveFixture();MediaService service=new MediaService(IsolatedMediaAdapters.decoderFactory(),IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT)){
                MediaSession session=service.open(MediaSource.of(fixture.uri()),false);session.muted(true);session.play();
                try(DesktopWindow window=DesktopWindow.openWithServices(output.resolve("live-ui.yaml"),service,false,services->ctx->{
                    ctx.own(session);SceneNode video=SceneVideo.create(ctx,session).size(Size.fill(1),Size.fill(1));ctx.mountOwned(new SceneNode().size(Size.fill(1),Size.fill(1)).padding(16).add(video).add(SceneMediaControls.create(ctx,session).padding(12)));
                })){
                    DesktopTestInput input=new DesktopTestInput(window);await(window,()->session.snapshot().state==MediaState.PLAYING&&session.snapshot().statistics.presentedFrames>0&&text(window,"直播"),8000,"直播首帧/窗口说明缺失");
                    input.click("暂停");await(window,()->session.snapshot().state==MediaState.PAUSED,3000,"直播暂停失败");long fixed=session.snapshot().positionNanos,generation=session.snapshot().generation;
                    await(window,()->session.snapshot().state==MediaState.FAILED&&text(window,"暂停位置已离开直播窗口"),22000,"有限窗口退出没有明确过期或正文提示");require(session.snapshot().positionNanos==fixed&&"live-window".equals(session.snapshot().failure.stage)&&text(window,"暂停位置已离开直播窗口"),"过期自动跳位或没有操作说明：fixed="+fixed+" actual="+session.snapshot().positionNanos+" failure="+session.snapshot().failure.stage);
                    DesktopTestFrames.settle(window);ImageIO.write(DesktopTestReadback.readFront(window),"png",output.resolve("live-expired.png").toFile());
                    input.click("回到直播");await(window,()->session.snapshot().generation>generation&&session.snapshot().state==MediaState.PLAYING&&session.snapshot().positionNanos>fixed,8000,"明确回到直播未恢复真实输出");
                    DesktopTestFrames.settle(window);ImageIO.write(DesktopTestReadback.readFront(window),"png",output.resolve("live-restored.png").toFile());
                    System.out.println("MEDIA_UX audioCompact=true liveWindow=true expiredPositionPreserved=true explicitLiveRecovery=true actualCallbacks=true actualSwap=true");
                }
                require(service.activeSessions()==0,"直播窗口结束没有释放会话");
            }
        });
    }
    private static boolean text(DesktopWindow window,String value){return window.lastPlan!=null&&window.lastPlan.commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.contains(value));}
    private static void await(DesktopWindow window,BooleanSupplier condition,long millis,String reason)throws Exception{long until=System.nanoTime()+millis*1_000_000L;while(!condition.getAsBoolean()&&System.nanoTime()<until){window.frame();Thread.sleep(5);}require(condition.getAsBoolean(),reason);}
}
