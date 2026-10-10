package club.heiqi.uilib.ui.scene.paint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public final class PaintPlan {
    private final List<PaintCommand> commands;
    PaintPlan(List<PaintCommand> commands) { this.commands = Collections.unmodifiableList(new ArrayList<>(commands)); }
    public List<PaintCommand> commands() { return commands; }
    /** 唯一资源优先序：本层所有主命令及readyOverlay闭包先取得预算，pending/failed只进入下一层。
     * 字体需求和图像pin/upload共享这些层。只遍历值片段，不执行分支，也不遍历branchSource。
     * 同一片段引用只在最早可达层处理一次；资源owner仍按GlyphKey/内容identity去重。
     */
    public List<List<PaintCommand>> resourceLayers() {
        List<List<PaintCommand>> layers=new ArrayList<>();
        java.util.Set<PaintPlan> visited=Collections.newSetFromMap(new java.util.IdentityHashMap<PaintPlan,Boolean>());
        List<PaintPlan> segments=new ArrayList<>();segments.add(this);
        while(!segments.isEmpty()){
            List<PaintCommand> layer=new ArrayList<>();List<PaintPlan> next=new ArrayList<>();
            // readyOverlay追加到当前队列：所有根主命令在前，所有成功覆盖层在备用之前。
            for(int i=0;i<segments.size();i++){
                PaintPlan segment=segments.get(i);if(!visited.add(segment))continue;
                layer.addAll(segment.commands);
                for(PaintCommand command:segment.commands){
                    if(command.readyOverlay!=null)segments.add(command.readyOverlay);
                    if(command.pendingPaint!=null)next.add(command.pendingPaint);
                    if(command.failedPaint!=null&&command.failedPaint!=command.pendingPaint)next.add(command.failedPaint);
                }
            }
            if(!layer.isEmpty())layers.add(Collections.unmodifiableList(layer));
            segments=next;
        }
        return Collections.unmodifiableList(layers);
    }
    public void visitResources(java.util.function.Consumer<PaintCommand> visitor) {
        for(List<PaintCommand> layer:resourceLayers())for(PaintCommand command:layer)visitor.accept(command);
    }
    public List<PaintCommand> resourceCommands() {
        List<PaintCommand> resources = new ArrayList<>();
        visitResources(resources::add);
        return Collections.unmodifiableList(resources);
    }
}
