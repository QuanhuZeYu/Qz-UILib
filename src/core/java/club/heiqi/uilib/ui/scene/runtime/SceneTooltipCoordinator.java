package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.control.SceneTooltip;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.IdentityHashMap;
import java.util.Map;

/** 页面唯一提示：所属注册、当前触发与有限期限；不拥有点击/焦点/媒体等业务状态。 */
final class SceneTooltipCoordinator implements SceneTransitions.Step,AutoCloseable {
    static final class Entry {
        final SceneNode node;
        final MountHandle mount;
        SceneTooltip.Hint hint;
        Entry(SceneRuntime runtime,SceneNode node){this.node=node;mount=runtime.captureMount(node);}
    }
    private final SceneRuntime runtime;
    private final Map<SceneNode,Entry> registrations=new IdentityHashMap<>();
    private final SceneTooltipFrame.Layout layout=new SceneTooltipFrame.Layout();
    private Entry current,pending,previousHover,previousFocus,suppressed;
    private long waitStarted,waitDelay,closeStarted,closeDelay,lastClosed,lastPointer;
    private boolean closing,closed,warm;
    private SceneTooltipFrame frame;
    SceneTooltipCoordinator(SceneRuntime runtime){this.runtime=runtime;}
    Entry register(SceneNode node){
        if(registrations.containsKey(node))throw new IllegalArgumentException("同一触发器不能重复绑定Tooltip");
        Entry entry=new Entry(runtime,node);registrations.put(node,entry);return entry;
    }
    void remove(Entry entry){
        if(registrations.get(entry.node)==entry)registrations.remove(entry.node);
        if(closed)return;
        if(current==entry||pending==entry)hide();
        if(previousHover==entry)previousHover=null;
        if(previousFocus==entry)previousFocus=null;
        if(suppressed==entry)suppressed=null;
    }
    private Entry explicit(SceneNode node){
        for(SceneNode n=node;n!=null;n=n.parent()){Entry entry=registrations.get(n);if(entry!=null)return entry;}
        return null;
    }
    private Entry source(SceneNode node,SceneGeometryView geometry){
        if(node==null)return null;
        if(!runtime.interactive(node))return null;
        Entry entry=explicit(node);
        if(entry==null&&node.control()!=null){
            SceneTooltip.Hint hint=runtime.domain().readOnly(()->node.control().tooltip(node,geometry));
            if(hint!=null){entry=reuse(node);entry.hint=hint;}
        }
        return valid(entry,geometry)?entry:null;
    }
    private Entry reuse(SceneNode node){
        for(Entry entry:new Entry[]{current,pending,previousHover,previousFocus,suppressed})if(entry!=null&&entry.node==node&&entry.mount.active())return entry;
        return new Entry(runtime,node);
    }
    private boolean refresh(Entry entry,SceneGeometryView geometry){
        if(entry==null||!entry.mount.active()||!runtime.interactive(entry.node))return false;
        // 指针移入popup后仍读取自动来源的当前事实；名称/主题/物品变化或不再省略可撤销旧提示。
        if(registrations.get(entry.node)!=entry)entry.hint=entry.node.control()==null?null:runtime.domain().readOnly(()->entry.node.control().tooltip(entry.node,geometry));
        return valid(entry,geometry);
    }
    private boolean valid(Entry entry,SceneGeometryView geometry){
        if(entry==null||!entry.mount.active()||entry.hint==null||!entry.hint.options.enabled||!runtime.interactive(entry.node))return false;
        if(entry.hint.content.text.source.isEmpty()&&entry.hint.content.shortcut.isEmpty())return false;
        SceneGeometryView.Entry located=geometry.get(entry.node);
        if(located==null)return false;
        LayoutBox visible=located.box.intersect(geometry.viewport());
        if(located.clip!=null)visible=visible.intersect(located.clip);
        return visible.width>0&&visible.height>0;
    }
    void prepare(SceneGeometryView geometry,SceneTextMeasurer text,long metrics){
        if(closed)return;
        Entry hover=hoverSource(geometry),focus=focusSource(geometry);
        refreshPresentations(geometry);
        if(keepsHover())hover=current;
        selectRequest(hover,focus);
        retain(hover,focus);
        advance(runtime.transitions().now());
        updateFrame(geometry,text,metrics);
        schedule();
    }
    private Entry hoverSource(SceneGeometryView geometry){
        Entry hover=source(runtime.pointerRouter().tooltipHovered(geometry),geometry);
        // 中性兴趣可能来自滚动条；只有其确有提示声明才优先，否则使用同次控件投影。
        return hover==null?source(runtime.pointerRouter().passiveHovered(geometry),geometry):hover;
    }
    private Entry focusSource(SceneGeometryView geometry){
        SceneNode focused=runtime.focusRouter().focused().get();
        return focused!=null&&runtime.focusRouter().focusVisible(focused)?source(focused,geometry):null;
    }
    private void refreshPresentations(SceneGeometryView geometry){
        if(current!=null&&!refresh(current,geometry))hide();
        if(pending!=null&&!refresh(pending,geometry))pending=null;
    }
    private boolean keepsHover(){
        return current!=null&&current.hint.options.hoverable&&frame!=null
                &&runtime.pointerRouter().observesTooltip(frame.anchor,frame.box);
    }
    private void selectRequest(Entry hover,Entry focus){
        boolean focusEntered=focus!=null&&focus!=previousFocus;
        boolean hoverEntered=hover!=null&&hover!=previousHover;
        long pointer=runtime.pointerRouter().positionRevision();
        // 同批新键盘焦点优先；纯重排不能抢走仍有效的当前提示。
        Entry requested=focusEntered?focus:hoverEntered&&(pointer!=lastPointer||current==null&&pending==null)?hover:null;
        if(requested!=null){
            if(requested==suppressed)suppressed=null;
            request(requested,focusEntered);
        }
        previousHover=hover;previousFocus=focus;lastPointer=pointer;
    }
    private void retain(Entry hover,Entry focus){
        if(pending!=null&&pending!=hover&&pending!=focus)pending=null;
        if(current==null)return;
        if(current==hover||current==focus){closing=false;return;}
        if(!closing){closing=true;closeStarted=runtime.transitions().now();closeDelay=current.hint.options.closeDelayNanos;}
    }
    private void request(Entry entry,boolean keyboard){
        if(entry==current){closing=false;return;}
        if(entry==pending)return;
        if(current!=null)hide();
        pending=entry;waitStarted=runtime.transitions().now();
        boolean quick=warm&&waitStarted-lastClosed<=entry.hint.options.skipDelayNanos;
        waitDelay=keyboard||quick?0:entry.hint.options.openDelayNanos;
    }
    @Override public boolean advance(long now){
        if(closed)return false;
        if(pending!=null&&now-waitStarted>=waitDelay){current=pending;pending=null;closing=false;runtime.invalidatePaint();}
        if(current!=null&&closing&&now-closeStarted>=closeDelay)hide();
        return pending!=null||closing;
    }
    private void schedule(){if(pending!=null||closing)runtime.transitions().start(this);else runtime.transitions().stop(this);}
    private void updateFrame(SceneGeometryView geometry,SceneTextMeasurer text,long metrics){
        SceneTooltipFrame next=null;
        if(current!=null&&valid(current,geometry)){
            SceneGeometryView.Entry entry=geometry.get(current.node);
            LayoutBox anchor=entry.box.intersect(geometry.viewport());if(entry.clip!=null)anchor=anchor.intersect(entry.clip);
            final LayoutBox actualAnchor=anchor;
            next=runtime.domain().readOnly(()->layout.prepare(actualAnchor,geometry.viewport(),current.hint,text,metrics));
        }
        if(frame==null?next!=null:!frame.same(next)){frame=next;runtime.invalidatePaint();}
    }
    private void hide(){
        if(current!=null){lastClosed=runtime.transitions().now();warm=true;}
        current=null;pending=null;closing=false;
        if(frame!=null){frame=null;if(!closed)runtime.invalidatePaint();}
    }
    boolean dismiss(){
        if(closed||current==null&&pending==null)return false;
        suppressed=current==null?pending:current;hide();runtime.transitions().stop(this);return true;
    }
    void pointerPressed(){dismiss();}
    void paint(SceneNode source,PaintPlanBuilder target){if(frame!=null&&current!=null&&current.node==source)frame.paint(target);}
    @Override public void close(){closed=true;runtime.transitions().stop(this);registrations.clear();current=null;pending=null;previousHover=null;previousFocus=null;suppressed=null;frame=null;}
}
