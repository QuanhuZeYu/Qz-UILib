package club.heiqi.uilib.testing.media;

import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.host.ScenePageSession;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import club.heiqi.uilib.ui.scene.text.TextLayout;

import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaRange;
import club.heiqi.uilib.ui.media.MediaInfo;
import club.heiqi.uilib.ui.media.MediaFrameKey;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.spi.MediaDecoded;
import club.heiqi.uilib.ui.media.spi.MediaDecoder;
import club.heiqi.uilib.ui.media.spi.MediaDecoderFactory;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;

import club.heiqi.uilib.testing.TestApplication;


import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.control.SceneVideo;


import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

import club.heiqi.uilib.ui.scene.video.MediaViewPump;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.function.BooleanSupplier;

/** 一个公开纯core反例：同正式layout的正文比例、frame-only与extent失效及卸载归属。 */
public final class SceneVideoLayoutScenario extends TestApplication {
    private static final class Host implements SceneHost, AutoCloseable {
        final MediaViewPump pump = new MediaViewPump();
        PaintPlan plan;
        public int width() { return 1280; }
        public int height() { return 720; }
        public long metricsRevision() { return 0; }
        public void prepareResources() { pump.prepareResources(); }
        public MediaViewPump mediaViews() { return pump; }
        public void replay(PaintPlan value) { plan = value; }
        public void close() { pump.close(); }
    }
    private static MediaDecoder decoder(MediaSource source) {
        final int width = source.toString().endsWith("square") ? 8 : 16;
        return new MediaDecoder() {
            int index;
            public MediaInfo info() { return new MediaInfo(true,true,false,800_000_000L,new MediaRange(0,800_000_000L),null,width,8); }
            public MediaDecoded read() {
                if (index == 8) return null;
                return MediaDecoded.video(index++ * 100_000_000L,100_000_000L,width,8,ByteBuffer.allocate(width*8*4));
            }
            public void seek(long nanos) { index = (int)(nanos / 100_000_000L); }
            public void close() {}
        };
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        require(condition.getAsBoolean(),"公开媒体输入未就绪");
    }
    private static void box(SceneGeometry geometry, SceneNode node, int width, int height) {
        LayoutBox actual = geometry.get(node).box;
        require(actual.width == width && actual.height == height,
                "正式布局外层尺寸 expected="+width+"x"+height+" actual="+actual.width+"x"+actual.height);
    }
    @Override protected void runTests() throws Exception {
        scenario("视频按正文约束推比例且只在extent变化重测", () -> {
            ReactiveDomain domain = ReactiveDomain.direct();
            SceneTextMeasurer text = (value,style,width) -> new TextLayout(0,0,style,Collections.emptyList());
            try (MediaService service = new MediaService((source,cancel) -> decoder(source),
                    budget -> { throw new AssertionError("纯视频不应打开音频设备"); },MediaBudget.DEFAULT);
                    Host host = new Host()) {
                MediaSession media = service.open(MediaSource.of(URI.create("fixture:wide")),false);
                await(() -> media.snapshot().video != null);
                SceneNode[] nodes = new SceneNode[4];
                ScenePageSession page = ScenePageSession.open(domain,host,text,ctx -> {
                    nodes[0] = SceneVideo.create(ctx,media).size(Size.fixed(320),Size.natural());
                    nodes[1] = SceneVideo.create(ctx,media).padding(10).size(Size.fixed(320),Size.natural());
                    nodes[2] = SceneVideo.create(ctx,media).size(Size.fixed(320).bounds(0,160),Size.natural());
                    nodes[3] = SceneVideo.create(ctx,media).padding(10).size(Size.natural(),Size.fixed(180));
                    for (SceneNode node : nodes) ctx.mountOwned(node);
                });
                try {
                    page.frame();
                    domain.execute(() -> {
                        SceneGeometry geometry = page.pipeline().current().geometry;
                        box(geometry,nodes[0],320,160); box(geometry,nodes[1],320,170);
                        box(geometry,nodes[2],160,80); box(geometry,nodes[3],340,180);
                    });
                    long[] revisions = new long[3];
                    domain.execute(() -> { revisions[0]=page.runtime().revision(); revisions[1]=page.runtime().measureRevision(); revisions[2]=page.runtime().geometryRevision(); });
                    MediaFrameKey first = media.snapshot().video.key;
                    media.play(); await(() -> !media.snapshot().video.key.equals(first)); media.pause(); page.frame();
                    domain.execute(() -> {
                        require(page.runtime().revision()>revisions[0],"新帧未使正式绘制计划失效");
                        require(page.runtime().measureRevision()==revisions[1] && page.runtime().geometryRevision()==revisions[2],"只换帧导致重测或重排");
                    });
                    long generation = media.snapshot().generation;
                    media.source(MediaSource.of(URI.create("fixture:square")));
                    await(() -> media.snapshot().video!=null && media.snapshot().generation>generation && media.snapshot().info.width==8);
                    page.frame();
                    domain.execute(() -> {
                        require(page.runtime().measureRevision()>revisions[1],"真实视频extent变化未重测");
                        SceneGeometry geometry = page.pipeline().current().geometry;
                        box(geometry,nodes[0],320,320); box(geometry,nodes[1],320,320);
                        box(geometry,nodes[2],160,160); box(geometry,nodes[3],180,180);
                    });
                    require(host.pump.activeConsumers()==4,"挂载consumer数量不符");
                } finally { page.close(); }
                require(host.pump.activeConsumers()==0 && service.activeSessions()==1,"卸载未释放consumer或误关共享会话");
                System.out.println("MEASURE plain=320x160 paddingWidth=320x170 boundedWidth=160x80 paddingHeight=340x180 frameOnlyMeasure=0 extentUpdated=true unmounted=0 sharedSession=true");
            }
        });
        scenario("decoder预读info不提前改变当前视频比例", () -> {
            java.util.concurrent.atomic.AtomicBoolean advance = new java.util.concurrent.atomic.AtomicBoolean();
            ReactiveDomain domain = ReactiveDomain.direct();
            SceneTextMeasurer text = (value,style,width) -> new TextLayout(0,0,style,Collections.emptyList());
            MediaDecoderFactory factory = (source,cancel) -> new MediaDecoder() {
                int index;
                volatile boolean ahead;
                public MediaInfo info() { return new MediaInfo(true,true,false,300_000_000L,new MediaRange(0,300_000_000L),null,ahead?8:16,8); }
                public MediaDecoded read() {
                    if (index>0 && !advance.get()) return MediaDecoded.YIELD;
                    if (index==3) return null;
                    if (index==1) ahead=true;
                    int width=index==2?8:16;
                    return MediaDecoded.video(index++*100_000_000L,100_000_000L,width,8,ByteBuffer.allocate(width*8*4));
                }
                public void seek(long at) { index=(int)(at/100_000_000L); }
                public void close() {}
            };
            try (MediaService service = new MediaService(factory,budget -> { throw new AssertionError("纯视频不能打开音频设备"); },MediaBudget.DEFAULT);
                    Host host = new Host()) {
                MediaSession media=service.open(MediaSource.of(URI.create("fixture:info-ahead")),false);
                await(() -> media.snapshot().video!=null);
                SceneNode[] video={null};
                ScenePageSession page=ScenePageSession.open(domain,host,text,ctx -> {
                    video[0]=SceneVideo.create(ctx,media).size(Size.fixed(320),Size.natural());ctx.mountOwned(video[0]);
                });
                try {
                    page.frame();long[] before={0};
                    domain.execute(() -> {box(page.pipeline().current().geometry,video[0],320,160);before[0]=page.runtime().measureRevision();});
                    advance.set(true);
                    await(() -> {
                        MediaSnapshot snapshot=media.snapshot();
                        if(snapshot.failure!=null)throw new AssertionError("预读输入失败: "+snapshot.failure.stage,snapshot.failure.cause);
                        return snapshot.info.width==8 && snapshot.video.width==16;
                    });
                    page.frame();
                    domain.execute(() -> {
                        box(page.pipeline().current().geometry,video[0],320,160);
                        require(page.runtime().measureRevision()==before[0],"只有info预读导致当前旧帧提前重测");
                        MediaViewPump.Extent initial=host.pump.view(domain,media).extent().get();
                        require(initial.width==16 && initial.height==8,"新View构造使用预读info替代当前帧尺寸");
                    });
                    media.play();await(() -> media.snapshot().video.width==8);media.pause();page.frame();
                    domain.execute(() -> {
                        box(page.pipeline().current().geometry,video[0],320,320);
                        require(page.runtime().measureRevision()>before[0],"实际新尺寸帧被选到后未重测");
                    });
                } finally {page.close();}
                require(host.pump.activeConsumers()==0 && service.activeSessions()==1,"动态extent反例卸载归属错误");
                System.out.println("EXTENT infoAhead=8x8 currentOld=16x8 oldBox=320x160 newCurrent=8x8 newBox=320x320 prematureMeasure=0 unmounted=0");
            }
        });
    }
    public static void main(String[] args) throws Exception { new SceneVideoLayoutScenario().run(args); }
}
