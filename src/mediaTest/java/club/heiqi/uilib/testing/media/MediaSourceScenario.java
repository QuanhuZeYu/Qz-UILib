package club.heiqi.uilib.testing.media;

import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.media.MediaRange;
import club.heiqi.uilib.ui.media.MediaInfo;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.spi.MediaCancellation;
import club.heiqi.uilib.ui.media.spi.MediaDecoded;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;
import club.heiqi.uilib.ui.media.spi.MediaDecoderFactory;
import club.heiqi.uilib.ui.media.spi.MediaDecoderSource;

import club.heiqi.uilib.testing.TestApplication;


import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 新source SPI的独立生命周期反例；原factory lambda由原core十五场景覆盖。 */
public final class MediaSourceScenario extends TestApplication {
    @Override protected void runTests()throws Exception{
        scenario("同source跨seek保存owner上下文且暂停按deadline刷新",()->{
            AtomicInteger contexts=new AtomicInteger(),closed=new AtomicInteger(),refreshes=new AtomicInteger();
            MediaDecoderFactory factory=new MediaDecoderFactory(){
                public MediaDecoder open(MediaSource source,MediaCancellation token){throw new AssertionError("不应绕过来源上下文");}
                public MediaDecoderSource openSource(MediaSource source,MediaCancellation lifetime){
                    contexts.incrementAndGet();Thread owner=Thread.currentThread();
                    return new MediaDecoderSource(){long due=System.nanoTime()+50_000_000L;
                        public long refreshDeadlineNanos(){return due;}
                        public MediaInfo refresh(MediaInfo current,MediaCancellation token){require(Thread.currentThread()==owner,"refresh逃逸owner");refreshes.incrementAndGet();due=System.nanoTime()+50_000_000L;return current;}
                        public MediaDecoder open(long target,boolean seek,MediaCancellation token){require(Thread.currentThread()==owner,"open逃逸owner");return new MediaDecoder(){long pts=target;
                            public MediaInfo info(){return new MediaInfo(true,true,false,2_000_000_000L,new MediaRange(0,2_000_000_000L),null,2,2);}
                            public MediaDecoded read(){token.check();long p=pts;pts+=50_000_000L;return MediaDecoded.video(p,50_000_000L,2,2,ByteBuffer.wrap(new byte[16]));}
                            public void seek(long n){throw new AssertionError("source已收目标不能二次seek");}public void close(){}
                        };}
                        public void close(){require(Thread.currentThread()==owner,"close逃逸owner");closed.incrementAndGet();}
                    };
                }
            };
            MediaService service=own(new MediaService(factory,b->{throw new AssertionError("纯视频不打开音频");},MediaBudget.DEFAULT));
            MediaSource source=MediaSource.of(URI.create("test://same/source"));MediaSession first=service.open(source,false);
            MediaTestSupport.await(first,s->s.video!=null,2000);Thread.sleep(180);require(refreshes.get()>1,"暂停背压停止了deadline事实刷新");
            first.seek(1_000_000_000L);MediaTestSupport.await(first,s->s.generation==2&&s.video!=null,2000);require(contexts.get()==1,"seek重建来源上下文");
            MediaSession second=service.open(source,false);MediaTestSupport.await(second,s->s.video!=null,2000);require(contexts.get()==2,"同URL两个会话共享上下文");
            first.source(source);MediaTestSupport.await(first,s->s.generation==3&&s.video!=null,2000);require(contexts.get()==3&&closed.get()==1,"显式source不关闭旧上下文");
            first.close();second.close();require(closed.get()==3,"来源上下文未逐个释放");
        });
        scenario("暂停刷新在途play须先验证旧暂停位置",()->pausedRefreshPlay(true));
        scenario("有效暂停位置待刷新确认后继续原代输出",()->pausedRefreshPlay(false));
    }
    private void pausedRefreshPlay(boolean expired)throws Exception {
        AtomicReference<GateSource> selected=new AtomicReference<>();
        MediaDecoderFactory factory=new MediaDecoderFactory(){
            public MediaDecoder open(MediaSource source,MediaCancellation operation){throw new AssertionError("须走source");}
            public MediaDecoderSource openSource(MediaSource source,MediaCancellation lifetime){GateSource next=new GateSource(expired);selected.set(next);return next;}
        };
        MediaService service=own(new MediaService(factory,b->{throw new AssertionError("纯视频不打开音频");},MediaBudget.DEFAULT));
        MediaSession session=service.open(MediaSource.of(URI.create("test://paused-refresh/"+expired)),false);
        GateSource gate=null;
        try {
            MediaSnapshot initial=MediaTestSupport.await(session,s->s.video!=null&&s.state==MediaState.PAUSED,2000);
            gate=selected.get();gate.due=System.nanoTime();session.volume(.5);
            require(gate.entered.await(2,TimeUnit.SECONDS),"未命中在途刷新");
            long reads=gate.reads.get();session.play();Thread.sleep(80);
            require(session.snapshot().positionNanos==initial.positionNanos&&gate.reads.get()==reads,"窗口验证前恢复输出或读流");
            gate.release.countDown();
            if(expired){
                MediaSnapshot failed=MediaTestSupport.await(session,s->s.state==MediaState.FAILED,2000);
                require(failed.failure.stage.equals("live-window")&&failed.generation==initial.generation
                        &&failed.positionNanos==initial.positionNanos&&gate.reads.get()==reads,"迟到range允许过期暂停位置推进");
                require(failed.info.seekable.startNanos==6_000_000_000L,"失效后丢失新range");
                long target=failed.info.seekable.startNanos+500_000_000L;session.seek(target);
                MediaSnapshot prepared=MediaTestSupport.await(session,s->s.generation>failed.generation&&s.video!=null,2000);
                require(prepared.failure==null&&prepared.positionNanos>=target,"显式当前范围定位未重新准备");
                System.out.println("REFRESH_GATE expired=true priorPosition="+initial.positionNanos+" failedPosition="+failed.positionNanos
                        +" failedGen="+failed.generation+" newGen="+prepared.generation+" readsBefore="+reads+" window="+failed.info.seekable.startNanos+".."+failed.info.seekable.endNanos);
            } else {
                MediaSnapshot resumed=MediaTestSupport.await(session,s->s.positionNanos>initial.positionNanos&&s.state==MediaState.PLAYING,2000);
                require(resumed.failure==null&&resumed.generation==initial.generation,"有效恢复更换代际或失败");
                System.out.println("REFRESH_GATE expired=false position="+resumed.positionNanos+" gen="+resumed.generation);
            }
        }finally{if(gate!=null)gate.release.countDown();session.close();}
    }
    private static final class GateSource implements MediaDecoderSource {
        final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        final AtomicLong reads=new AtomicLong();final boolean expired;
        long due=Long.MAX_VALUE;
        MediaInfo facts=new MediaInfo(true,true,false,-1,new MediaRange(0,11_999_999_999L),null,2,2);
        GateSource(boolean expired){this.expired=expired;}
        public long refreshDeadlineNanos(){return due;}
        public MediaInfo refresh(MediaInfo current,MediaCancellation token)throws Exception{
            entered.countDown();if(!release.await(3,TimeUnit.SECONDS))throw new AssertionError("未释放在途gate");token.check();
            facts=new MediaInfo(true,true,false,-1,new MediaRange(expired?6_000_000_000L:0,17_999_999_999L),null,2,2);due=Long.MAX_VALUE;return facts;
        }
        public MediaDecoder open(long target,boolean seeking,MediaCancellation token){return new MediaDecoder(){long pts=target;
            public MediaInfo info(){return facts;}
            public MediaDecoded read(){token.check();reads.incrementAndGet();long p=pts;pts+=50_000_000L;return MediaDecoded.video(p,50_000_000L,2,2,ByteBuffer.wrap(new byte[16]));}
            public void seek(long value){pts=value;}public void close(){}
        };}
    }
    public static void main(String[] args)throws Exception{new MediaSourceScenario().run(args);}
}
