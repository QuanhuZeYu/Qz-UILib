package club.heiqi.uilib.testing.media;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;
import club.heiqi.uilib.ui.media.spi.MediaDecoderSource;
import club.heiqi.uilib.ui.media.spi.MediaDecoded;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** 独立输入、必需HTTP头和EOF尾部的真实FFmpeg回归。 */
public final class MediaNetworkScenario extends TestApplication {
    public static void main(String[] args)throws Exception{new MediaNetworkScenario().run(args);}
    protected void runTests()throws Exception{
        scenario("HTTP头不可变并拒绝字段注入",()->{
            Map<String,String> headers=new LinkedHashMap<>();headers.put("Referer","https://fixture.example/watch");
            MediaSource source=MediaSource.http(java.net.URI.create("https://example.test/video"),headers);headers.clear();require(source.headers.size()==1,"请求头没有冻结");
            boolean failed=false;try{source.headers.clear();}catch(UnsupportedOperationException expected){failed=true;}require(failed,"请求头可被外部修改");
            headers.put("Referer","value\r\nInjected: yes");failed=false;try{MediaSource.http(source.uri,headers);}catch(IllegalArgumentException expected){failed=true;}require(failed,"HTTP换行未被拒绝");
        });
        scenario("新来源尚未打开时不能提前结算定位",()->{
            try(MediaNetworkFixture fixture=new MediaNetworkFixture()){
                java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.atomic.AtomicInteger opens=new java.util.concurrent.atomic.AtomicInteger();
                club.heiqi.uilib.ui.media.spi.MediaDecoderFactory nativeFactory=IsolatedMediaAdapters.decoderFactory();
                club.heiqi.uilib.ui.media.spi.MediaDecoderFactory gated=(source,token)->{
                    if(opens.incrementAndGet()>1){entered.countDown();while(!release.await(10,java.util.concurrent.TimeUnit.MILLISECONDS))token.check();}
                    return nativeFactory.open(source,token);
                };
                try(club.heiqi.uilib.ui.media.MediaService service=new club.heiqi.uilib.ui.media.MediaService(gated,IsolatedMediaAdapters.audioSinkFactory(),club.heiqi.uilib.ui.media.MediaBudget.DEFAULT)){
                    club.heiqi.uilib.ui.media.MediaSession session=service.open(fixture.split(false),false);
                    MediaTestSupport.await(session,s->s.info.ready&&s.outputReady,5000);
                    session.source(fixture.split(false),500_000_000L);
                    try{
                        require(entered.await(2,java.util.concurrent.TimeUnit.SECONDS),"新来源读取没有进入门控");
                        long until=System.nanoTime()+200_000_000L;
                        while(session.snapshot().state==club.heiqi.uilib.ui.media.MediaState.SEEKING&&System.nanoTime()<until)Thread.sleep(2);
                        require(session.snapshot().state==club.heiqi.uilib.ui.media.MediaState.SEEKING,"新来源事实未知时提前完成定位："+session.snapshot().state);
                    }finally{release.countDown();}
                }
            }
        });
        scenario("同字节数的真实动态视频尺寸刷新行跨度",()->{
            java.nio.file.Path file=java.nio.file.Files.createTempFile("uilib-equal-area-",".mjpg");
            try{
                java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
                int[][] images={{64,96,0xffcc2222},{96,64,0xff2244cc}};
                for(int[] image:images){
                    java.awt.image.BufferedImage frame=new java.awt.image.BufferedImage(image[0],image[1],java.awt.image.BufferedImage.TYPE_INT_RGB);
                    for(int y=0;y<image[1];y++)for(int x=0;x<image[0];x++)frame.setRGB(x,y,image[2]);
                    javax.imageio.ImageIO.write(frame,"jpeg",bytes);
                }
                java.nio.file.Files.write(file,bytes.toByteArray());
                try(MediaDecoder decoder=IsolatedMediaAdapters.decoderFactory().open(MediaSource.of(file.toUri()),()->false)){
                    MediaDecoded first=decoder.read();require(first!=null&&first.video&&first.width==64&&first.height==96,"未读取首个真实窄帧");
                    MediaDecoded second=decoder.read();require(second!=null&&second.video&&second.width==96&&second.height==64,"真实MJPEG未变尺寸");
                    int pixel=(second.width*second.height-1)*4;
                    int red=second.data.get(pixel)&255,blue=second.data.get(pixel+2)&255;
                    System.out.println("RGBA_STRIDE equalBytes="+second.data.remaining()+" first=64x96 second=96x64 lastRed="+red+" lastBlue="+blue);
                    require(blue>red+80,"同字节数变宽后末像素仍来自旧帧，行跨度未更新");
                }
            }finally{java.nio.file.Files.deleteIfExists(file);}
        });
        scenario("真实Range双输入不同尾部及定位取消",()->{
            try(MediaNetworkFixture fixture=new MediaNetworkFixture()){
                for(boolean shorter:new boolean[]{false,true}){
                    AtomicBoolean cancelled=new AtomicBoolean();
                    try(MediaDecoderSource source=IsolatedMediaAdapters.decoderFactory().openSource(fixture.split(shorter),cancelled::get)){
                        try(MediaDecoder decoder=source.open(0,false,cancelled::get)){
                            require(decoder.info().hasVideo&&decoder.info().hasAudio,"分轨没有合并媒体事实");
                            int videos=0,audios=0;long videoEnd=0,audioEnd=0;
                            for(int i=0;i<10000;i++){
                                MediaDecoded item=decoder.read();if(item==null)break;if(item==MediaDecoded.YIELD)continue;
                                if(item.video){videos++;videoEnd=Math.max(videoEnd,item.presentationNanos+item.durationNanos);}else{audios++;audioEnd=Math.max(audioEnd,item.presentationNanos+item.durationNanos);}
                            }
                            require(videos==20&&audios>0,"一轨EOF截断了另一轨");require(shorter?videoEnd>audioEnd:audioEnd>videoEnd,"独立尾部未完整解码");
                            System.out.println("NETWORK_SPLIT shorterAudio="+shorter+" frames="+videos+" audioBlocks="+audios+" videoEnd="+videoEnd+" audioEnd="+audioEnd);
                        }
                        try(MediaDecoder decoder=source.open(500_000_000L,true,cancelled::get)){
                            boolean video=false,audio=false;
                            for(int i=0;i<1000&&(!video||!audio);i++){MediaDecoded item=decoder.read();if(item==null)break;if(item!=MediaDecoded.YIELD&&item.presentationNanos+item.durationNanos>500_000_000L){if(item.video)video=true;else audio=true;}}
                            require(video&&audio,"双输入定位后未取得目标数据");cancelled.set(true);boolean failed=false;try{decoder.read();}catch(java.util.concurrent.CancellationException expected){failed=true;}require(failed,"native读取取消未生效");
                        }
                    }
                }
                try(MediaDecoderSource source=IsolatedMediaAdapters.decoderFactory().openSource(fixture.source("/vod.m3u8"),()->false);MediaDecoder decoder=source.open(0,false,()->false)){
                    require(decoder.read()!=null,"HLS列表/片段未携带请求头");
                }
                require(fixture.accepted.get()>0&&fixture.rejected.get()==0,"真实请求缺失声明的头");
                System.out.println("NETWORK_HEADERS accepted="+fixture.accepted+" rejected="+fixture.rejected);
            }
        });
    }
}
