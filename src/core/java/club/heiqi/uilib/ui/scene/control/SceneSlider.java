package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.motion.ElasticOptions;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.DoubleConsumer;

/** 通用水平范围控件；拖动预览、外部值和最终提交分开，沿唯一捕获与焦点路由。 */
public final class SceneSlider {
    private SceneSlider(){}
    public static final class Model {
        public final double min,max,step,keyStep,value,bufferStart,bufferEnd;
        public final boolean enabled;
        public final long token;
        public final ElasticOptions elastic;
        public Model(double min,double max,double step,double value,boolean enabled){this(min,max,step,step,value,enabled,0,Double.NaN,Double.NaN,null);}
        private Model(double min,double max,double step,double keyStep,double value,boolean enabled,long token,double start,double end,ElasticOptions elastic){
            if(!Double.isFinite(min)||!Double.isFinite(max)||max<min||!Double.isFinite(max-min)||!Double.isFinite(step)||step<=0||!Double.isFinite(value))throw new IllegalArgumentException("无效滑杆范围");
            this.min=min;this.max=max;this.step=step;this.keyStep=keyStep;this.value=clamp(value,min,max);this.enabled=enabled&&max>min;this.token=token;
            bufferStart=start;bufferEnd=end;this.elastic=elastic;
        }
        public Model buffered(double start,double end){if(!Double.isFinite(start)||!Double.isFinite(end)||end<start)throw new IllegalArgumentException("无效缓冲范围");return new Model(min,max,step,keyStep,value,enabled,token,start,end,elastic);}
        public Model keyboardStep(double amount){if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("无效键盘步长");return new Model(min,max,step,amount,value,enabled,token,bufferStart,bufferEnd,elastic);}
        public Model token(long token){return new Model(min,max,step,keyStep,value,enabled,token,bufferStart,bufferEnd,elastic);}
        /** 显式启用轨道软边缘；null 撤销形变，数值范围和提交方式保持不变。 */
        public Model elastic(ElasticOptions options){return new Model(min,max,step,keyStep,value,enabled,token,bufferStart,bufferEnd,options);}
    }
    public static final class Preview {
        public final boolean active;
        public final double value;
        Preview(boolean active,double value){this.active=active;this.value=value;}
    }
    public static SceneNode create(ScenePageContext page,ReadableSignal<Model> model,DoubleConsumer onChange,DoubleConsumer onCommit,ReadableSignal<SceneTheme> theme){
        Objects.requireNonNull(page).domain().checkWrite();
        Control control=new Control(page,Objects.requireNonNull(onChange),Objects.requireNonNull(onCommit),Objects.requireNonNull(theme));
        SceneNode node=new SceneNode().control(control).size(Size.fill(1),Size.natural()).focusable(true).clickFocus(true);
        node.bind(SceneControlBindings.declaration(model,(target,next)->control.configure(target,next)));
        node.bind(SceneControlBindings.declaration(()->{
            control.runtime.motionPolicy().get();control.runtime.interactionRevision().get();
            return control.actual.get();
        },(target,next)->control.refreshElastic(target)));
        node.bind((runtime,target,owner)->{
            control.runtime=runtime;
            return new BindingActivation(){public void refresh(){}public void close(){control.cancel();}};
        });
        return node;
    }
    public static ReadableSignal<Preview> preview(SceneNode node){if(!(node.control() instanceof Control))throw new IllegalArgumentException("不是滑杆");Control control=(Control)node.control();return control.preview::get;}
    /** 只读的有符号轨道形变，单位为 scene 像素；读取不会推进回弹。 */
    public static ReadableSignal<Double> elasticOffset(SceneNode node){if(!(node.control() instanceof Control))throw new IllegalArgumentException("不是滑杆");Control control=(Control)node.control();return control.elasticOffset::get;}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
    private static final class Control implements SceneControl {
        SceneRuntime runtime;
        final Signal<Preview> preview;
        final Signal<Model> actual;
        final Signal<Double> elasticOffset;
        final DoubleConsumer changed,committed;
        final ReadableSignal<SceneTheme> theme;
        final MotionValue rebound=new MotionValue();
        Model model=new Model(0,1,1,0,false);
        boolean dragging;
        int pointerX,elasticEdge;
        double overshoot,releaseOrigin,pointerAdjustment,elasticInputScale=1;
        Track pickedTrack;
        AutoCloseable scheduled;

        Control(ScenePageContext page,DoubleConsumer changed,DoubleConsumer committed,ReadableSignal<SceneTheme> theme){
            runtime=page.runtime();preview=page.domain().signal(new Preview(false,0));
            actual=page.domain().signal(model);elasticOffset=page.domain().signal(0d);
            this.changed=changed;this.committed=committed;this.theme=theme;
        }
        void configure(SceneNode node,Model next){
            Objects.requireNonNull(next,"滑杆声明");boolean invalid=!next.enabled||next.token!=model.token;
            model=next;actual.set(next);node.enabled(next.enabled);
            if(invalid)cancel();
            else if(!dragging)publish(false,next.value);
            else publish(true,clamp(preview.get().value,next.min,next.max));
            if(next.elastic==null)clearElastic();
        }
        void publish(boolean active,double value){
            Preview previous=preview.get();
            if(previous.active!=active||Double.compare(previous.value,value)!=0)preview.set(new Preview(active,value));
        }
        void cancel(){dragging=false;overshoot=0;clearElastic();publish(false,model.value);}
        void refreshElastic(SceneNode node){
            if(!runtime.interactive(node)){cancel();return;}
            if(model.elastic==null){clearElastic();return;}
            long now=runtime.inputNanos();
            if(dragging){followElastic();return;}
            if(!rebound.active())return;
            double limit=Math.abs(model.elastic.displacement(Math.abs(overshoot),runtime.motionPolicy().get()));
            rebound.constrain(-limit,limit,now);
            rebound.retarget(0,runtime.motionPolicy().get().resolve(model.elastic.releaseMotion,true),now);
            elasticOffset.set(rebound.value());
            if(!rebound.active())clearElastic();
        }
        private void stopScheduled(){
            if(scheduled==null)return;
            try{scheduled.close();}catch(Exception failure){throw new IllegalStateException("滑杆回弹释放失败",failure);}
            finally{scheduled=null;}
        }
        private void clearElastic(){
            stopScheduled();rebound.snap(0,runtime.inputNanos());elasticOffset.set(0d);elasticEdge=0;releaseOrigin=0;
            pointerAdjustment=0;elasticInputScale=1;pickedTrack=null;
        }
        private void followElastic(){
            double offset=model.elastic==null?0:model.elastic.displacement(overshoot,runtime.motionPolicy().get());
            stopScheduled();rebound.snap(offset,runtime.inputNanos());elasticOffset.set(offset);
            if(pickedTrack==null)elasticEdge=Double.compare(overshoot,0);releaseOrigin=0;
        }
        private void releaseElastic(SceneNode node){
            if(model.elastic==null){clearElastic();return;}
            releaseOrigin=elasticOffset.get();
            MotionSpec strategy=runtime.motionPolicy().get().resolve(model.elastic.releaseMotion,true);
            rebound.release(releaseOrigin,0,0,strategy,runtime.inputNanos());
            elasticOffset.set(rebound.value());
            if(rebound.active())scheduled=runtime.animate(now->advanceElastic(node,now));
            else clearElastic();
        }
        private boolean advanceElastic(SceneNode node,long now){
            if(!runtime.interactive(node)){cancel();return false;}
            boolean active=rebound.advance(now);elasticOffset.set(rebound.value());
            if(!active){scheduled=null;elasticEdge=0;releaseOrigin=0;}
            return active;
        }
        public List<ReadableSignal<?>> measureDependencies(SceneNode node){return Arrays.asList(theme);}
        public List<ReadableSignal<?>> paintDependencies(SceneNode node){return Arrays.asList(theme,preview,actual,elasticOffset);}
        public MeasuredSize measure(SceneNode node,Constraints c,SceneTextMeasurer text){return new MeasuredSize(Math.round(theme.get().textStyle.logicalEm*10),Math.max(28,Math.round(theme.get().textStyle.logicalEm*2)));}
        public club.heiqi.uilib.ui.scene.layout.ChildPlacements arrange(SceneNode node,LayoutBox box,club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer children){return new club.heiqi.uilib.ui.scene.layout.ChildPlacements(java.util.Collections.emptyList());}
        public Object pointerTarget(SceneNode node,SceneGeometryView geometry,int x,int y){
            SceneGeometryView.Entry entry=geometry.get(node);
            if(!model.enabled||entry==null)return null;
            return entry.box.contains(x,y)||model.elastic!=null&&track(entry.box).containsThumb(model,preview.get().value,x,y)?this:null;
        }
        public boolean pointerTargetValid(SceneNode node,Object target){return model.enabled&&target==this;}
        public boolean capturesPointer(){return model.enabled;}
        public boolean cancelCaptureOnEscape(SceneNode node){return dragging;}
        public boolean passiveHover(){return true;}
        public void captureLost(SceneNode node){cancel();}
        public void focusChanged(SceneNode node,boolean focused,boolean eligible){if(!focused)cancel();}
        public void inputFrameCancelled(SceneNode node){cancel();}
        public long nextInputNanos(SceneNode node){
            Model declaration=actual.get();Preview current=preview.get();double offset=elasticOffset.get();
            return declaration.elastic!=null&&(current.active||offset!=0)?0:Long.MAX_VALUE;
        }
        public void inputFrame(SceneNode node,SceneGeometryView geometry,long now){synchronizeElastic(node,geometry);}
        public void inputGeometryChanged(SceneNode node,SceneGeometryView geometry){synchronizeElastic(node,geometry);}
        private void synchronizeElastic(SceneNode node,SceneGeometryView geometry){
            if(model.elastic==null||!dragging&&!rebound.active())return;
            SceneGeometryView.Entry entry=geometry.get(node);
            if(entry==null||!runtime.interactive(node)){cancel();return;}
            Track track=track(entry.box);
            if(dragging){
                // 窗口/主题重排只更新内部预览，不把测量结果写回业务值。
                if(pickedTrack!=null&&pickedTrack.baseLeft==track.baseLeft&&pickedTrack.baseRight==track.baseRight)return;
                pickedTrack=null;double x=pointerX+pointerAdjustment;publish(true,pointerValue(track,x));stretch(track,x);
            }else{
                overshoot=clamp(overshoot,-track.baseWidth,track.baseWidth);
                double limit=Math.abs(model.elastic.displacement(track.baseWidth,runtime.motionPolicy().get()));
                rebound.constrain(-limit,limit,runtime.inputNanos());elasticOffset.set(rebound.value());
                if(!rebound.active())clearElastic();
            }
        }
        public void preparePointerRelease(SceneNode node,ScenePointerEvent event,SceneGeometryView geometry){if(dragging)update(node,event.x,geometry);}
        public void pointer(SceneNode node,ScenePointerEvent event,SceneGeometryView geometry){
            if(event.button!=0&&(event.kind==ScenePointerEvent.Kind.DOWN||event.kind==ScenePointerEvent.Kind.UP))return;
            if(event.kind==ScenePointerEvent.Kind.DOWN&&model.enabled){
                Track current=track(geometry.get(node).box);
                if(model.elastic!=null&&rebound.active()&&current.containsThumb(model,preview.get().value,event.x,event.y))pickThumb(current,event.x);
                else{clearElastic();dragging=true;update(node,event.x,geometry);}
            }
            else if(event.kind==ScenePointerEvent.Kind.MOVE&&dragging)update(node,event.x,geometry);
            else if(event.kind==ScenePointerEvent.Kind.UP&&dragging){
                double value=preview.get().value;dragging=false;pickedTrack=null;publish(false,value);releaseElastic(node);committed.accept(value);
            }
            else if(event.kind==ScenePointerEvent.Kind.CANCEL)cancel();
        }
        private void pickThumb(Track track,int x){
            double value=preview.get().value,offset=elasticOffset.get();
            stopScheduled();rebound.snap(offset,runtime.inputNanos());pickedTrack=track;pointerX=x;dragging=true;
            double position=track.exactPosition(model,value);pointerAdjustment=position-x;
            overshoot=rawElasticOffset(offset,track.baseWidth);
            double outside=position-clamp(position,track.baseLeft,track.baseRight);
            elasticInputScale=outside*offset>0?overshoot/outside:1;
            publish(true,value);changed.accept(value);
        }
        private double rawElasticOffset(double offset,int width){
            if(offset==0)return 0;
            double low=0,high=width,target=Math.abs(offset);
            // 拾取时反求当前形变对应的共享阻力输入，不复制 ElasticOptions 的曲线公式。
            for(int i=0;i<48;i++){
                double middle=(low+high)*.5;
                if(model.elastic.displacement(middle,runtime.motionPolicy().get())<target)low=middle;else high=middle;
            }
            return Math.copySign((low+high)*.5,offset);
        }
        private void update(SceneNode node,int x,SceneGeometryView geometry){
            if(pickedTrack!=null&&x==pointerX){changed.accept(preview.get().value);return;}
            pickedTrack=null;pointerX=x;Track track=track(geometry.get(node).box);double position=x+pointerAdjustment;
            double value=pointerValue(track,position);publish(true,value);stretch(track,position);changed.accept(value);
        }
        private double pointerValue(Track track,double x){
            double raw=model.min+(model.max-model.min)*clamp((x-(double)track.baseLeft)/Math.max(1,track.baseWidth),0,1);
            return quantize(raw);
        }
        private void stretch(Track track,double x){
            // 极远捕获仍受当前轨道尺寸限制；阻力与策略上限统一由 ElasticOptions 映射。
            overshoot=clamp((x-clamp(x,track.baseLeft,track.baseRight))*elasticInputScale,-track.baseWidth,track.baseWidth);
            followElastic();
        }
        private double quantize(double value){if(value<=model.min)return model.min;if(value>=model.max)return model.max;return clamp(model.min+Math.rint((value-model.min)/model.step)*model.step,model.min,model.max);}
        public boolean edit(SceneNode node,SceneEditEvent event,SceneGeometryView geometry){
            if(!model.enabled||event.command==null)return false;
            double next=preview.get().value;
            switch(event.command){case LEFT:case DOWN:next-=model.keyStep;break;case RIGHT:case UP:next+=model.keyStep;break;case HOME:next=model.min;break;case END:next=model.max;break;case PAGE_DOWN:next-=model.keyStep*10;break;case PAGE_UP:next+=model.keyStep*10;break;case ESCAPE:if(dragging){cancel();return true;}return false;default:return false;}
            // 原指针 owner 直到 UP/ESC 结束；期间范围键不插入第二个提交，也不结束其预览。
            if(dragging)return true;
            clearElastic();next=quantize(next);publish(false,next);changed.accept(next);committed.accept(next);return true;
        }
        private Track track(LayoutBox box){
            float em=theme.get().textStyle.logicalEm;
            int inset=Math.min(Math.max(5,Math.round(em*.45f)),box.width/2);
            int thickness=Math.max(3,Math.round(em*.3f)),radius=Math.max(5,Math.round(em*.4f));
            double offset=elasticOffset.get();
            if(model.elastic!=null){
                inset=Math.min(Math.max(radius+6,box.width/10),box.width/2);
                double resting=clamp(em*.85,12,16),active=clamp(em*1.2,18,22);
                double engagement=dragging?1:releaseOrigin==0?0:Math.min(1,Math.abs(offset/releaseOrigin));
                double stretch=Math.min(1,Math.abs(offset)/model.elastic.maximumDisplacement);
                thickness=Math.max(1,(int)Math.round((resting+(active-resting)*engagement)*(1-.22*stretch)));
                radius=Math.max(radius,(thickness+1)/2+1);
            }
            int baseLeft=box.x+inset,baseRight=box.x+box.width-inset;
            // 回弹穿过零点时仍保留原边缘，不能把左端形变突然移到右端。
            int left=baseLeft+(elasticEdge<0?(int)Math.round(offset):0);
            int right=baseRight+(elasticEdge>0?(int)Math.round(offset):0);
            return new Track(baseLeft,baseRight,left,right,box.y+box.height/2,thickness,radius);
        }
        public void paint(SceneNode node,SceneGeometryView geometry,PaintPlanBuilder paint){
            SceneGeometryView.Entry entry=geometry.get(node);Track track=track(entry.box);
            SceneAppearance.Palette colors=new SceneAppearance.Palette(theme.get());
            int y=track.y,thickness=track.thickness,left=track.left,right=track.right;
            ScenePaintShapes.rounded(paint,new LayoutBox(left,y-thickness/2,Math.max(0,right-left),thickness),entry.clip,colors.border,thickness/2);
            if(Double.isFinite(model.bufferStart)&&Double.isFinite(model.bufferEnd)){
                int start=track.position(model,model.bufferStart),end=track.position(model,model.bufferEnd);
                ScenePaintShapes.rounded(paint,new LayoutBox(start,y-thickness/2,Math.max(0,end-start),thickness),entry.clip,colors.muted,thickness/2);
            }
            int current=track.position(model,preview.get().value);
            ScenePaintShapes.rounded(paint,new LayoutBox(left,y-thickness/2,Math.max(0,current-left),thickness),entry.clip,model.enabled?colors.primary:colors.muted,thickness/2);
            int radius=track.radius;
            if(node.runtime()!=null&&node.runtime().focusRouter().focusVisible(node))ScenePaintShapes.rounded(paint,new LayoutBox(current-radius-3,y-radius-3,radius*2+6,radius*2+6),entry.clip,colors.muted,radius+3);
            ScenePaintShapes.rounded(paint,new LayoutBox(current-radius,y-radius,radius*2,radius*2),entry.clip,model.enabled?colors.primary:colors.muted,radius);
            ScenePaintShapes.rounded(paint,new LayoutBox(current-radius+2,y-radius+2,Math.max(0,radius*2-4),Math.max(0,radius*2-4)),entry.clip,colors.background,Math.max(1,radius-2));
        }
    }
    private static final class Track {
        final int baseLeft,baseRight,baseWidth,left,right,y,thickness,radius;
        Track(int baseLeft,int baseRight,int left,int right,int y,int thickness,int radius){
            this.baseLeft=baseLeft;this.baseRight=baseRight;this.baseWidth=baseRight-baseLeft;
            this.left=left;this.right=right;this.y=y;this.thickness=thickness;this.radius=radius;
        }
        double exactPosition(Model model,double value){
            double ratio=model.max==model.min?0:(clamp(value,model.min,model.max)-model.min)/(model.max-model.min);
            return left+(right-left)*ratio;
        }
        int position(Model model,double value){return (int)Math.round(exactPosition(model,value));}
        boolean containsThumb(Model model,double value,int x,int y){
            double dx=x+.5-position(model,value),dy=y+.5-this.y;
            return dx*dx+dy*dy<=radius*(double)radius;
        }
    }
}
