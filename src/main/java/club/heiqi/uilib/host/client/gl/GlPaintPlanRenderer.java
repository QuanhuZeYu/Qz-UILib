package club.heiqi.uilib.host.client.gl;

import java.util.LinkedHashSet;
import java.util.Objects;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Quad;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.State;
import club.heiqi.uilib.ui.render.batch.OrderedQuadCollector;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.font.glyph.AsyncGlyphRasterizer;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import club.heiqi.uilib.ui.scene.text.font.nativefont.NativeFontFace.InkBounds;

/** 正式 PaintPlan 回放：位置只消费不可变 Glyph，不重新布局、读取 scene 或等待字形。
 * 调用方先 prepare（资源上传/失效通知边界），再 replay（固定MVP/depth/pass），最后关闭 Prepared。
 * plan.box/clip、glyph定位及 prepare.viewport 均为原生 framebuffer 像素（左上原点）。
 * MVP 将该 framebuffer XY 映射到 clip space，不得再次应用 GUI scale；world/GUI 调用者
 * 坐标及其 clip 的转换属于上游 adapter。本 consumer 不直接接受这些宿主坐标。
 * CPU与atlas由宿主持有，可跨renderer复用；本类仅拥有自己的quad backend。
 */
public final class GlPaintPlanRenderer implements AutoCloseable {
    private final AsyncGlyphRasterizer cpu;private final GlGlyphAtlas atlas;private final GlQuadRenderer quads;
    private final int capacity,maxDemand;
    private long clippedGlyphs,demandOverflow,blankGlyphs,drawnGlyphs;
    public GlPaintPlanRenderer(AsyncGlyphRasterizer cpu,GlGlyphAtlas atlas,int quadCapacity,int maxDemand) {
        if(maxDemand<1)throw new IllegalArgumentException("无效需求预算");
        this.cpu=Objects.requireNonNull(cpu,"cpu");this.atlas=Objects.requireNonNull(atlas,"atlas");
        capacity=quadCapacity;this.maxDemand=maxDemand;quads=new GlQuadRenderer(quadCapacity,atlas);
    }
    public long clippedGlyphs(){return clippedGlyphs;}
    public long demandOverflow(){return demandOverflow;}
    public long blankGlyphs(){return blankGlyphs;}
    public long drawnGlyphs(){return drawnGlyphs;}
    public GlQuadRenderer.Counters counters(){return quads.counters();}
    public Prepared prepare(PaintPlan plan,LayoutBox viewport,GlGlyphAtlas.UploadBudget budget) {
        Objects.requireNonNull(plan,"plan");Objects.requireNonNull(viewport,"viewport");
        LinkedHashSet<GlyphKey> demand=new LinkedHashSet<GlyphKey>();
        for(PaintCommand command:plan.commands())if(command.kind==PaintCommand.Kind.TEXT) {
            LayoutBox clip=clip(command.clip,viewport);if(clip.width==0||clip.height==0)continue;
            for(TextLayout.Line line:command.text.lines())for(TextLayout.Glyph glyph:line.glyphs()) {
                if(!visible(command,glyph,clip)){clippedGlyphs++;continue;}
                GlyphKey key=GlyphKey.of(glyph);
                if(demand.size()<maxDemand||demand.contains(key))demand.add(key);else demandOverflow++;
            }
        }
        GlGlyphAtlas.Frame frame=atlas.beginFrame(demand);
        try {frame.prepare(cpu,budget);frame.freeze();return new Prepared(plan,viewport,frame);}
        catch(RuntimeException|Error failure){frame.close();throw failure;}
    }
    private static boolean visible(PaintCommand command,TextLayout.Glyph glyph,LayoutBox clip) {
        InkBounds bounds=glyph.inkBounds;if(!bounds.known)return true;
        double x=(double)command.box.x+glyph.x,y=(double)command.box.y+glyph.y;
        return x+bounds.left<(long)clip.x+clip.width&&x+bounds.right>clip.x
            &&y+bounds.top<(long)clip.y+clip.height&&y+bounds.bottom>clip.y;
    }
    private static LayoutBox clip(LayoutBox requested,LayoutBox viewport) {
        if(requested==null)return viewport;
        long left=Math.max((long)requested.x,viewport.x),top=Math.max((long)requested.y,viewport.y);
        long right=Math.min((long)requested.x+requested.width,(long)viewport.x+viewport.width);
        long bottom=Math.min((long)requested.y+requested.height,(long)viewport.y+viewport.height);
        return new LayoutBox((int)left,(int)top,(int)Math.max(0,right-left),(int)Math.max(0,bottom-top));
    }
    public final class Prepared implements AutoCloseable {
        public final PaintPlan plan;public final LayoutBox viewport;
        private final GlGlyphAtlas.Frame frame;private boolean closed;private Replay active;
        private Prepared(PaintPlan plan,LayoutBox viewport,GlGlyphAtlas.Frame frame){this.plan=plan;this.viewport=viewport;this.frame=frame;}
        public GlGlyphAtlas.Resident resident(TextLayout.Glyph glyph){check();return frame.lookup(GlyphKey.of(glyph));}
        public int demandCount(){check();return frame.demand().size();}
        public int readyCount(){check();int n=0;for(GlyphKey key:frame.demand())if(frame.lookup(key)!=null)n++;return n;}
        private void check(){if(closed)throw new IllegalStateException("prepared 已关闭");}
        public Replay begin(float[] mvp,int framebufferHeight,boolean depthEnabled) {
            check();if(active!=null)throw new IllegalStateException("回放范围重入");
            Replay replay=new Replay(this,mvp.clone(),framebufferHeight,depthEnabled);active=replay;return replay;
        }
        public void render(float[] mvp,int framebufferHeight,boolean depthEnabled) {
            try(Replay replay=begin(mvp,framebufferHeight,depthEnabled)){for(PaintCommand command:plan.commands())replay.draw(command);}
        }
        @Override public void close(){if(closed)return;if(active!=null)throw new IllegalStateException("先结束回放");frame.close();closed=true;}
    }
    public final class Replay implements AutoCloseable {
        private final Prepared prepared;private final float[] mvp;private final int height;private final boolean depth;
        private GlQuadRenderer.Scope scope;private OrderedQuadCollector collector;private boolean closed;
        private Replay(Prepared prepared,float[] mvp,int height,boolean depth){this.prepared=prepared;this.mvp=mvp;this.height=height;this.depth=depth;open();}
        private void open(){scope=quads.begin(mvp,height,depth);collector=new OrderedQuadCollector(capacity,scope::consumeBatch);}
        public void draw(PaintCommand command) {
            if(closed)throw new IllegalStateException("回放已结束");LayoutBox clip=clip(command.clip,prepared.viewport);
            if(clip.width==0||clip.height==0)return;
            if(command.kind==PaintCommand.Kind.RECT) {
                collector.add(new State(GlQuadRenderer.SOLID,0,0,clip),new Quad(command.box.x,command.box.y,
                    command.box.width,command.box.height,0,0,0,0,0,command.color));
            }else for(TextLayout.Line line:command.text.lines())for(TextLayout.Glyph glyph:line.glyphs()) {
                if(!visible(command,glyph,clip))continue;
                GlGlyphAtlas.Resident r=prepared.frame.lookup(GlyphKey.of(glyph));
                if(r==null){blankGlyphs++;continue;}if(r.width==0||r.height==0)continue;
                float x=command.box.x+glyph.x+r.bearingX,y=command.box.y+glyph.y-r.bearingY;
                collector.add(new State(r.color?GlQuadRenderer.COLOR_PREMULT:GlQuadRenderer.MONOCHROME_COVERAGE,r.resource,r.generation,clip),
                    new Quad(x,y,r.width*r.pixelScale,r.height*r.pixelScale,r.u0,r.v0,r.u1,r.v1,r.layer,command.color));drawnGlyphs++;
            }
        }
        /** 外部绘制前提交并退出quad状态范围，委托后重新观测宿主状态，不复用状态猜测。 */
        public void delegate(Runnable delegated) {
            if(closed)throw new IllegalStateException("回放已结束");
            try {collector.flush();}finally{scope.close();scope=null;}
            try {Objects.requireNonNull(delegated,"delegate").run();}
            finally {
                try {open();}catch(RuntimeException|Error failure){closed=true;prepared.active=null;throw failure;}
            }
        }
        @Override public void close(){if(closed)return;closed=true;
            try{collector.flush();}finally{if(scope!=null)scope.close();prepared.active=null;}}
    }
    @Override public void close(){quads.close();}
}
