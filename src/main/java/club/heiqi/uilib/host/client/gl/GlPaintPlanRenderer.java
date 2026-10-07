package club.heiqi.uilib.host.client.gl;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;

import java.util.LinkedHashSet;
import java.util.Objects;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Quad;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.State;
import club.heiqi.uilib.ui.render.batch.OrderedQuadCollector;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.font.glyph.AsyncGlyphRasterizer;
import club.heiqi.uilib.ui.scene.text.font.glyph.GlyphKey;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;

/** 正式 PaintPlan 回放：位置只消费不可变 Glyph，不重新布局、读取 scene 或等待字形。
 * 调用方先 prepare（资源上传/失效通知边界），再 replay（固定MVP/depth/pass），最后关闭 Prepared。
 * plan.box/clip、glyph定位及 prepare.viewport 均为原生 framebuffer 像素（左上原点）。
 * 旧UI重载MVP将framebuffer XY映到clip，不再次GUI scale。FontRenderContext重载直接接受caller XY，
 * 保留P/MV/viewport/FBO与齐次w；只使用context outer scissor，不把callerclip矩形近似成screenrect。
 * CPU与atlas由宿主持有，可跨renderer复用；本类仅拥有自己的quad backend。
 */
public final class GlPaintPlanRenderer implements AutoCloseable {
    private final GlProgramProvider programs;
    private final AsyncGlyphRasterizer cpu;private final GlGlyphAtlas atlas;private final GlQuadRenderer quads;
    private final int capacity,maxDemand;
    private long clippedGlyphs,demandOverflow,blankGlyphs,drawnGlyphs;
    public GlPaintPlanRenderer(GlApi gl,AsyncGlyphRasterizer cpu,GlGlyphAtlas atlas,int quadCapacity,int maxDemand,GlProgramProvider programs) {
        this.programs=Objects.requireNonNull(programs,"programs");
        if(maxDemand<1)throw new IllegalArgumentException("无效需求预算");
        this.cpu=Objects.requireNonNull(cpu,"cpu");this.atlas=Objects.requireNonNull(atlas,"atlas");
        capacity=quadCapacity;this.maxDemand=maxDemand;quads=new GlQuadRenderer(gl,quadCapacity,atlas,programs);
    }
    public long clippedGlyphs(){return clippedGlyphs;}
    public long demandOverflow(){return demandOverflow;}
    public long blankGlyphs(){return blankGlyphs;}
    public long drawnGlyphs(){return drawnGlyphs;}
    public GlQuadRenderer.Counters counters(){return quads.counters();}
    /** 可见需求与回放共用实际墨迹判定，供共享owner累积同帧有界union。 */
    public java.util.Set<GlyphKey> collectVisibleDemand(PaintPlan plan,LayoutBox viewport) {
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
        return java.util.Collections.unmodifiableSet(demand);
    }
    /** caller空间的world需求：真实墨迹通过齐次clip与实际viewport/scissor裁决，不重布局。 */
    public java.util.Set<GlyphKey> collectVisibleDemand(PaintPlan plan,FontRenderContext context) {
        Objects.requireNonNull(plan,"plan");Objects.requireNonNull(context,"context");
        LinkedHashSet<GlyphKey> demand=new LinkedHashSet<GlyphKey>();
        for(PaintCommand command:plan.commands())if(command.kind==PaintCommand.Kind.TEXT)
            for(TextLayout.Line line:command.text.lines())for(TextLayout.Glyph glyph:line.glyphs()){
                if(!GlFontVisibility.visible(command,glyph,context)){clippedGlyphs++;continue;}
                GlyphKey key=GlyphKey.of(glyph);if(demand.size()<maxDemand||demand.contains(key))demand.add(key);else demandOverflow++;
            }
        return java.util.Collections.unmodifiableSet(demand);
    }
    /** owner传同epoch裁决资源key与剩余预算；此入口不重新扩张需求或取消其他consumer。 */
    public Prepared prepareDemandContext(PaintPlan plan,FontRenderContext context,GlGlyphAtlas.UploadBudget budget,
            java.util.Collection<GlyphKey> admitted,Runnable onClose) {
        Objects.requireNonNull(context,"context");return prepareDemandView(plan,GlFontVisibility.viewport(context),budget,admitted,false,onClose,resolveSamplingView(context));
    }
    public GlGlyphAtlas.SamplingView resolveSamplingView(FontRenderContext context) {
        return !programs.preview(context)?GlGlyphAtlas.SamplingView.PREMULT_RGBA:GlGlyphAtlas.SamplingView.STRAIGHT_RGBA;
    }
    public Prepared prepare(PaintPlan plan,LayoutBox viewport,GlGlyphAtlas.UploadBudget budget) {
        return prepareDemand(plan,viewport,budget,collectVisibleDemand(plan,viewport),true,null);
    }
    /** 显式资源准备入口；共享owner传入已裁决的epoch需求与剩余预算，不能重新扩张需求。
     * onClose仅归还owner生命周期，不控制渲染或上传。 */
    public Prepared prepareDemand(PaintPlan plan,LayoutBox viewport,GlGlyphAtlas.UploadBudget budget,
            java.util.Collection<GlyphKey> demand,boolean retainPending,Runnable onClose) {
        return prepareDemandView(plan,viewport,budget,demand,retainPending,onClose,GlGlyphAtlas.SamplingView.PREMULT_RGBA);
    }
    private Prepared prepareDemandView(PaintPlan plan,LayoutBox viewport,GlGlyphAtlas.UploadBudget budget,
            java.util.Collection<GlyphKey> demand,boolean retainPending,Runnable onClose,GlGlyphAtlas.SamplingView view) {
        Objects.requireNonNull(plan,"plan");Objects.requireNonNull(viewport,"viewport");
        boolean needsSolid=false;for(PaintCommand command:plan.commands())if(command.kind==PaintCommand.Kind.RECT)needsSolid=true;else for(TextLayout.Line line:command.text.lines())if(!line.decorations().isEmpty())needsSolid=true;
        GlGlyphAtlas.Frame frame=atlas.beginFrame(demand,!retainPending,view,needsSolid);
        try {frame.prepare(cpu,budget,retainPending);frame.freeze();return new Prepared(plan,viewport,frame,onClose);}
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
        private final Runnable onClose;
        private Prepared(PaintPlan plan,LayoutBox viewport,GlGlyphAtlas.Frame frame,Runnable onClose){this.plan=plan;this.viewport=viewport;this.frame=frame;this.onClose=onClose;}
        public GlGlyphAtlas.Resident resident(TextLayout.Glyph glyph){check();return frame.lookup(GlyphKey.of(glyph));}
        public int demandCount(){check();return frame.demand().size();}
        public int readyCount(){check();int n=0;for(GlyphKey key:frame.demand())if(frame.lookup(key)!=null)n++;return n;}
        private void check(){if(closed)throw new IllegalStateException("prepared 已关闭");}
        public Replay begin(float[] mvp,int framebufferHeight,boolean depthEnabled) {
            check();if(active!=null)throw new IllegalStateException("回放范围重入");
            Replay replay=new Replay(this,mvp.clone(),framebufferHeight,depthEnabled);active=replay;return replay;
        }
        public Replay begin(FontRenderContext context) {
            check();if(active!=null)throw new IllegalStateException("回放范围重入");
            Replay replay=new Replay(this,Objects.requireNonNull(context,"context"));active=replay;return replay;
        }
        public void render(FontRenderContext context) {
            try(Replay replay=begin(context)){for(PaintCommand command:plan.commands())replay.draw(command);}
        }
        public void render(float[] mvp,int framebufferHeight,boolean depthEnabled) {
            try(Replay replay=begin(mvp,framebufferHeight,depthEnabled)){for(PaintCommand command:plan.commands())replay.draw(command);}
        }
        @Override public void close(){if(closed)return;if(active!=null)throw new IllegalStateException("先结束回放");frame.close();closed=true;if(onClose!=null)onClose.run();}
    }
    public final class Replay implements AutoCloseable {
        private final Prepared prepared;private final float[] mvp;private final int height;private final boolean depth;
        private FontRenderContext context;
        private GlQuadRenderer.Scope scope;private OrderedQuadCollector collector;private boolean closed;
        private Replay(Prepared prepared,float[] mvp,int height,boolean depth){this.prepared=prepared;this.mvp=mvp;this.height=height;this.depth=depth;open();}
        private Replay(Prepared prepared,FontRenderContext context){this.prepared=prepared;this.context=context;this.mvp=context.projection.mvp();this.height=context.sourceFramebufferHeight;this.depth=context.depthTest;open();}
        private void open(){if(context!=null&&prepared.frame.view!=resolveSamplingView(context))throw new IllegalStateException("prepare/replay atlas采样view与activepass不一致");scope=context==null?quads.begin(mvp,height,depth):quads.begin(context);collector=new OrderedQuadCollector(capacity,scope::consumeBatch);}
        public void draw(PaintCommand command) {
            if(closed)throw new IllegalStateException("回放已结束");LayoutBox clip=context==null?clip(command.clip,prepared.viewport):null;
            // world不能把framebuffer scissor交给会按callerXY裁决的collector；scope单独处理outerclip。
            if(clip!=null&&(clip.width==0||clip.height==0))return;
            if(command.kind==PaintCommand.Kind.RECT) {
                solid(command.box.x,command.box.y,command.box.width,command.box.height,command.color,clip);
            }else for(TextLayout.Line line:command.text.lines()) {
                for(TextLayout.Glyph glyph:line.glyphs()) {
                    if(context==null?!visible(command,glyph,clip):!GlFontVisibility.visible(command,glyph,context))continue;
                    GlGlyphAtlas.Resident r=prepared.frame.lookup(GlyphKey.of(glyph));
                    if(r==null){blankGlyphs++;continue;}if(r.width==0||r.height==0)continue;
                    // resident bearing/尺寸属于采样face；布局位置与墨迹界已是logical单位，只映回一次。
                    float ratio=glyph.rasterToLayout;
                    float x=command.box.x+glyph.x+r.bearingX*ratio,y=command.box.y+glyph.y-r.bearingY*ratio;
                    int color=glyph.resolvedStyle==null?command.color:glyph.resolvedStyle.color;
                    collector.add(new State(r.color?GlQuadRenderer.COLOR_PREMULT:GlQuadRenderer.MONOCHROME_COVERAGE,r.resource,r.generation,clip),
                        new Quad(x,y,r.width*r.pixelScale*ratio,r.height*r.pixelScale*ratio,r.u0,r.v0,r.u1,r.v1,r.layer,color));drawnGlyphs++;
                }
                for(TextLayout.Decoration decoration:line.decorations()) {
                    solid(command.box.x+decoration.x,command.box.y+decoration.y,decoration.width,decoration.height,decoration.color,clip);
                }
            }
        }
        private void solid(float x,float y,float width,float height,int color,LayoutBox clip){
            if(prepared.frame.view==GlGlyphAtlas.SamplingView.STRAIGHT_RGBA){GlGlyphAtlas.Resident white=prepared.frame.solid();if(white==null)return;
                collector.add(new State(GlQuadRenderer.MONOCHROME_COVERAGE,white.resource,white.generation,clip),new Quad(x,y,width,height,white.u0,white.v0,white.u1,white.v1,0,color));
            }else collector.add(new State(GlQuadRenderer.SOLID,0,0,clip),new Quad(x,y,width,height,0,0,0,0,0,color));
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
