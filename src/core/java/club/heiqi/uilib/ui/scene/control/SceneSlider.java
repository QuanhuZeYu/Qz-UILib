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
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.paint.ScenePaintShapes;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
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
        public Model(double min,double max,double step,double value,boolean enabled){this(min,max,step,step,value,enabled,0,Double.NaN,Double.NaN);}
        private Model(double min,double max,double step,double keyStep,double value,boolean enabled,long token,double start,double end){
            if(!Double.isFinite(min)||!Double.isFinite(max)||max<min||!Double.isFinite(max-min)||!Double.isFinite(step)||step<=0||!Double.isFinite(value))throw new IllegalArgumentException("无效滑杆范围");
            this.min=min;this.max=max;this.step=step;this.keyStep=keyStep;this.value=clamp(value,min,max);this.enabled=enabled&&max>min;this.token=token;
            bufferStart=start;bufferEnd=end;
        }
        public Model buffered(double start,double end){if(!Double.isFinite(start)||!Double.isFinite(end)||end<start)throw new IllegalArgumentException("无效缓冲范围");return new Model(min,max,step,keyStep,value,enabled,token,start,end);}
        public Model keyboardStep(double amount){if(!Double.isFinite(amount)||amount<=0)throw new IllegalArgumentException("无效键盘步长");return new Model(min,max,step,amount,value,enabled,token,bufferStart,bufferEnd);}
        public Model token(long token){return new Model(min,max,step,keyStep,value,enabled,token,bufferStart,bufferEnd);}
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
        node.bind((runtime,target,owner)->new BindingActivation(){public void refresh(){}public void close(){control.cancel();}});
        return node;
    }
    public static ReadableSignal<Preview> preview(SceneNode node){if(!(node.control() instanceof Control))throw new IllegalArgumentException("不是滑杆");Control control=(Control)node.control();return control.preview::get;}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
    private static final class Control implements SceneControl {
        final Signal<Preview> preview;
        final Signal<Model> actual;
        final DoubleConsumer changed,committed;
        final ReadableSignal<SceneTheme> theme;
        Model model=new Model(0,1,1,0,false);
        boolean dragging;
        Control(ScenePageContext page,DoubleConsumer changed,DoubleConsumer committed,ReadableSignal<SceneTheme> theme){preview=page.domain().signal(new Preview(false,0));actual=page.domain().signal(model);this.changed=changed;this.committed=committed;this.theme=theme;}
        void configure(SceneNode node,Model next){
            Objects.requireNonNull(next,"滑杆声明");boolean cancel=dragging&&(!next.enabled||next.token!=model.token);
            model=next;actual.set(next);node.enabled(next.enabled);
            if(cancel)cancel();
            else if(!dragging)publish(false,next.value);
            else publish(true,clamp(preview.get().value,next.min,next.max));
        }
        void publish(boolean active,double value){Preview previous=preview.get();if(previous.active!=active||Double.compare(previous.value,value)!=0)preview.set(new Preview(active,value));}
        void cancel(){dragging=false;publish(false,model.value);}
        public List<ReadableSignal<?>> measureDependencies(SceneNode node){return Arrays.asList(theme);}
        public List<ReadableSignal<?>> paintDependencies(SceneNode node){return Arrays.asList(theme,preview,actual);}
        public MeasuredSize measure(SceneNode node,Constraints c,SceneTextMeasurer text){return new MeasuredSize(Math.round(theme.get().textStyle.logicalEm*10),Math.max(28,Math.round(theme.get().textStyle.logicalEm*2)));}
        public club.heiqi.uilib.ui.scene.layout.ChildPlacements arrange(SceneNode node,LayoutBox box,club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer children){return new club.heiqi.uilib.ui.scene.layout.ChildPlacements(java.util.Collections.emptyList());}
        public Object pointerTarget(SceneNode node,SceneGeometryView geometry,int x,int y){SceneGeometryView.Entry entry=geometry.get(node);return model.enabled&&entry!=null&&entry.box.contains(x,y)?this:null;}
        public boolean pointerTargetValid(SceneNode node,Object target){return model.enabled&&target==this;}
        public boolean capturesPointer(){return model.enabled;}
        public boolean cancelCaptureOnEscape(SceneNode node){return dragging;}
        public boolean passiveHover(){return true;}
        public void captureLost(SceneNode node){cancel();}
        public void focusChanged(SceneNode node,boolean focused,boolean eligible){if(!focused)cancel();}
        public void preparePointerRelease(SceneNode node,ScenePointerEvent event,SceneGeometryView geometry){if(dragging)update(node,event.x,geometry);}
        public void pointer(SceneNode node,ScenePointerEvent event,SceneGeometryView geometry){
            if(event.button!=0&&(event.kind==ScenePointerEvent.Kind.DOWN||event.kind==ScenePointerEvent.Kind.UP))return;
            if(event.kind==ScenePointerEvent.Kind.DOWN&&model.enabled){dragging=true;update(node,event.x,geometry);}
            else if(event.kind==ScenePointerEvent.Kind.MOVE&&dragging)update(node,event.x,geometry);
            else if(event.kind==ScenePointerEvent.Kind.UP&&dragging){double value=preview.get().value;dragging=false;publish(false,value);committed.accept(value);}
            else if(event.kind==ScenePointerEvent.Kind.CANCEL)cancel();
        }
        private void update(SceneNode node,int x,SceneGeometryView geometry){
            LayoutBox box=geometry.get(node).box;int inset=inset(box),width=Math.max(1,box.width-inset*2);
            double raw=model.min+(model.max-model.min)*clamp((x-(double)box.x-inset)/width,0,1);
            double value=quantize(raw);publish(true,value);changed.accept(value);
        }
        private double quantize(double value){if(value<=model.min)return model.min;if(value>=model.max)return model.max;return clamp(model.min+Math.rint((value-model.min)/model.step)*model.step,model.min,model.max);}
        public boolean edit(SceneNode node,SceneEditEvent event,SceneGeometryView geometry){
            if(!model.enabled||event.command==null)return false;
            double next=preview.get().value;
            switch(event.command){case LEFT:case DOWN:next-=model.keyStep;break;case RIGHT:case UP:next+=model.keyStep;break;case HOME:next=model.min;break;case END:next=model.max;break;case PAGE_DOWN:next-=model.keyStep*10;break;case PAGE_UP:next+=model.keyStep*10;break;case ESCAPE:if(dragging){cancel();return true;}return false;default:return false;}
            // 原指针owner直到UP/ESC结束；期间范围键不插入第二个提交，也不结束其预览。
            if(dragging)return true;
            next=quantize(next);publish(false,next);changed.accept(next);committed.accept(next);return true;
        }
        private int inset(LayoutBox box){return Math.min(Math.max(5,Math.round(theme.get().textStyle.logicalEm*.45f)),box.width/2);}
        private int position(LayoutBox box,double value){double ratio=model.max==model.min?0:(clamp(value,model.min,model.max)-model.min)/(model.max-model.min);return box.x+inset(box)+(int)Math.round((box.width-inset(box)*2)*ratio);}
        public void paint(SceneNode node,SceneGeometryView geometry,PaintPlanBuilder paint){
            SceneGeometryView.Entry entry=geometry.get(node);LayoutBox box=entry.box;SceneAppearance.Palette colors=new SceneAppearance.Palette(theme.get());
            int y=box.y+box.height/2,thickness=Math.max(3,Math.round(colors.textStyle.logicalEm*.3f)),left=box.x+inset(box),right=box.x+box.width-inset(box);
            ScenePaintShapes.rounded(paint,new LayoutBox(left,y-thickness/2,Math.max(0,right-left),thickness),entry.clip,colors.border,thickness/2);
            if(Double.isFinite(model.bufferStart)&&Double.isFinite(model.bufferEnd)){
                int start=position(box,model.bufferStart),end=position(box,model.bufferEnd);
                ScenePaintShapes.rounded(paint,new LayoutBox(start,y-thickness/2,Math.max(0,end-start),thickness),entry.clip,colors.muted,thickness/2);
            }
            int current=position(box,preview.get().value);
            ScenePaintShapes.rounded(paint,new LayoutBox(left,y-thickness/2,Math.max(0,current-left),thickness),entry.clip,model.enabled?colors.primary:colors.muted,thickness/2);
            int radius=Math.max(5,Math.round(colors.textStyle.logicalEm*.4f));
            if(node.runtime()!=null&&node.runtime().focusRouter().focusVisible(node))ScenePaintShapes.rounded(paint,new LayoutBox(current-radius-3,y-radius-3,radius*2+6,radius*2+6),entry.clip,colors.muted,radius+3);
            ScenePaintShapes.rounded(paint,new LayoutBox(current-radius,y-radius,radius*2,radius*2),entry.clip,model.enabled?colors.primary:colors.muted,radius);
            ScenePaintShapes.rounded(paint,new LayoutBox(current-radius+2,y-radius+2,Math.max(0,radius*2-4),Math.max(0,radius*2-4)),entry.clip,colors.background,Math.max(1,radius-2));
        }
    }
}
