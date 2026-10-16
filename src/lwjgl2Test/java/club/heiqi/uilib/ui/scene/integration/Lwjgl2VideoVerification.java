package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.host.client.gl.Lwjgl2GlApi;

import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaRange;
import club.heiqi.uilib.ui.media.MediaInfo;
import club.heiqi.uilib.ui.media.MediaVideoFrame;
import club.heiqi.uilib.ui.media.MediaFrameLease;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.spi.MediaDecoded;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.DisplayTestEnvironment;
import club.heiqi.uilib.testing.media.VideoGlVerification;
import club.heiqi.uilib.host.client.font.ClientFontServices;

import club.heiqi.uilib.host.font.IsolatedFontEngine;


import java.nio.ByteBuffer;
import java.net.URI;
import static org.lwjgl.opengl.GL11.*;

/** 独立Java8/LWJGL2实际Display，通过同一个shared GL和同一GPU反例消费中性视频帧。 */
public final class Lwjgl2VideoVerification extends TestApplication {
    private static final long STEP = 100_000_000L;
    private static MediaDecoder decoder() {
        return new MediaDecoder() {
            int index;
            public MediaInfo info() { return new MediaInfo(true,true,false,3*STEP,new MediaRange(0,3*STEP),null,2,1); }
            public MediaDecoded read() {
                if (index == 3) return null;
                ByteBuffer pixels = ByteBuffer.allocate(8);
                for (int i=0;i<2;i++) pixels.put((byte)(index==0?255:0)).put((byte)(index==1?255:0)).put((byte)(index==2?255:0)).put((byte)255);
                pixels.flip(); int frame=index++; return MediaDecoded.video(frame*STEP,STEP,2,1,pixels);
            }
            public void seek(long nanos) { index=(int)(nanos/STEP); }
            public void close() {}
        };
    }
    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long end=System.nanoTime()+3_000_000_000L;
        while(!condition.getAsBoolean()&&System.nanoTime()<end) Thread.sleep(2);
        require(condition.getAsBoolean(),"共同媒体SPI输入未就绪");
    }
    @Override protected void runTests() throws Exception {
        own(new DisplayTestEnvironment(320,240,"UILib Java8 LWJGL2 VIDEO"));
        Lwjgl2GlApi gl=new Lwjgl2GlApi();
        ClientFontServices fonts=own(new ClientFontServices(getClass().getClassLoader(),1,ClientFontServices.Settings.defaults(),IsolatedFontEngine.shared(),gl,GlProgramProvider.NONE));
        MediaService service=own(new MediaService((source,cancel)->decoder(),budget->{throw new AssertionError("无音轨不应初始化设备");},MediaBudget.DEFAULT));
        MediaSession session=service.open(MediaSource.of(URI.create("fixture:video")),false);
        await(()->session.snapshot().video!=null);
        MediaVideoFrame first=session.snapshot().video;
        MediaFrameLease held=own(service.retainPresentation(first.key));
        require(held!=null,"缺首帧联系");
        session.play(); await(()->!session.snapshot().video.key.equals(first.key)); session.pause();
        MediaVideoFrame second=session.snapshot().video;
        VideoGlVerification.Target target=new VideoGlVerification.Target(){
            public int width(){return 320;} public int height(){return 240;}
            public void clear(){glDisable(GL_SCISSOR_TEST);glDisable(GL_LIGHTING);glDisable(GL_ALPHA_TEST);glColorMask(true,true,true,true);glViewport(0,0,320,240);glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);}
            public int pixel(int x,int y){ByteBuffer b=ByteBuffer.allocateDirect(4);glReadPixels(x,239-y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,b);return 0xFF000000|(b.get(0)&255)<<16|(b.get(1)&255)<<8|b.get(2)&255;}
            public boolean isTexture(int id){return glIsTexture(id);}
        };
        System.out.println("LEGACY java="+System.getProperty("java.version")+" binding="+org.lwjgl.Sys.getVersion()+" GL="+glGetString(GL_VERSION));
        scenario("原LWJGL2上传与draw失败状态恢复",()->VideoGlVerification.failure(fonts,service,second,target));
        scenario("原LWJGL2双帧旧计划顺序裁剪与切代",()->VideoGlVerification.run(fonts,service,first,second,target,()->session.source(MediaSource.of(URI.create("fixture:new")))));
        session.close();require(service.activeSessions()==0,"legacy会话未释放");
    }
    public static void main(String[] args)throws Exception{new Lwjgl2VideoVerification().run(args);}
}
