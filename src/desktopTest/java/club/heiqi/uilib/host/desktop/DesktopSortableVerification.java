package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static club.heiqi.uilib.host.desktop.DesktopTestInput.*;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;
import static club.heiqi.uilib.host.desktop.DesktopDropSortVerification.*;
import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneGrid;
import club.heiqi.uilib.ui.scene.control.SceneSortable;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 正式输入驱动排序模型，核验可见顺序与持续挂载，不借私有字段自证。 */
public final class DesktopSortableVerification extends TestApplication {
    private final Path output;
    DesktopSortableVerification(Path output) { this.output = output; }
    public static void main(String[] args) throws Exception {
        Path path = Paths.get(args.length == 0 ? "sortable-report" : args[0]); Files.createDirectories(path);
        new DesktopSortableVerification(path).run(args);
    }
    @Override protected void runTests() throws Exception {
        for (SceneSortable.Layout layout : SceneSortable.Layout.values()) scenario("真实变尺寸 " + layout, () -> layout(layout));
        scenario("跨容器及空容器保持身份与编辑", this::containers);
        scenario("键盘相邻排序与取消", this::keyboard);
        scenario("活动手势随合法条目迁移继续", this::activeMigration);
        scenario("接收目标遵循正式所属层绘制序", () -> overlappingTargets(false));
        scenario("被动所属层不拦下方接收目标", () -> overlappingTargets(true));
        scenario("动态接收过滤与禁用来源", this::policies);
        scenario("停边滚动跨出原槽后继续", this::scrolling);
    }
    private static final class Model {
        final SceneSortable.Layout layout;
        final boolean multiple, scrolling;
        boolean overlap, passiveOverlap;
        Signal<List<SceneSortable.Group<String,Integer>>> groups;
        Signal<Boolean> enabled;
        final Map<Integer,SceneNode> rows = new HashMap<>(), grips = new HashMap<>(), fields = new HashMap<>();
        final Map<Integer,SceneEditState> edits = new HashMap<>();
        final Map<String,SceneNode> lists = new HashMap<>(), roots = new HashMap<>();
        int moves, created;
        Model(SceneSortable.Layout layout, boolean multiple, boolean scrolling) { this.layout = layout; this.multiple = multiple; this.scrolling = scrolling; }
        void build(club.heiqi.uilib.ui.scene.host.ScenePageContext ctx) {
            List<Integer> values = new ArrayList<>(); for (int i=1;i<=(scrolling?24:6);i++) values.add(i);
            groups=ctx.domain().signal(multiple?Arrays.asList(group("A",1,2,3),group("B",4,5,6),group("C"),group("blocked"))
                    :Collections.singletonList(new SceneSortable.Group<>("A",values)));
            enabled=ctx.domain().signal(true);
            SceneNode board=SceneSortable.create(ctx,groups,item->item,key->{
                SceneNode list=layout==SceneSortable.Layout.GRID?SceneGrid.create(ctx,()->new SceneGrid.Policy(180,12,12))
                        :new SceneNode().axis(layout==SceneSortable.Layout.HORIZONTAL?SceneNode.Axis.ROW:SceneNode.Axis.COLUMN);
                list.size(Size.fill(1),scrolling?Size.fixed(270):Size.natural().bounds(120,Integer.MAX_VALUE)).padding(12).gap(12).background(0xfff1f5f9);
                if(scrolling||layout==SceneSortable.Layout.HORIZONTAL)list.scrollable(true).clip(true);
                lists.put(key,list);
                SceneNode column=new SceneNode().size(Size.fill(1),Size.natural()).gap(8).add(new SceneNode().text("容器 "+key)).add(list);
                SceneNode root=column;
                if(overlap&&key.equals("B")){
                    root=club.heiqi.uilib.ui.scene.control.SceneLayer.create(ctx,
                            ()->new club.heiqi.uilib.ui.scene.control.SceneLayer.Policy(0,passiveOverlap
                                    ?club.heiqi.uilib.ui.scene.control.SceneLayer.Participation.PASSIVE
                                    :club.heiqi.uilib.ui.scene.control.SceneLayer.Participation.INTERACTIVE),
                            (node,source,viewport,size)->new LayoutBox(source.x+source.width/3+12,source.y,size.width,size.height),Collections.emptyList())
                            .size(Size.fixed(240),Size.natural()).add(column);
                }
                roots.put(key,root);return new SceneSortable.Container(root,list,layout);
            },value->{
                int key=value.get();created++;
                SceneNode grip=new SceneNode().size(Size.fixed(30),Size.fixed(24)).text("::").background(0xff94a3b8);
                SceneEditState edit=new SceneEditState(ctx.domain(),"草稿 "+key);edits.put(key,edit);
                SceneNode field=SceneTextField.create(ctx,edit,()->SceneTheme.light(),()->false,s->{}).size(Size.fill(1),Size.fixed(28));
                SceneNode content=new SceneNode().size(layout==SceneSortable.Layout.HORIZONTAL?Size.fixed(150+key%3*28):Size.fill(1),Size.natural())
                        .padding(8).gap(5).background(0xffdbeafe).add(grip).add(field)
                        .add(new SceneNode().size(Size.fill(1),Size.fixed(key%2==0?40:20)).text("内容 "+key));
                rows.put(key,content);grips.put(key,grip);fields.put(key,field);
                return new SceneSortable.Row(content,grip);
            },value->new SceneNode().size(Size.fixed(180),Size.fixed(70)).text("排序预览 "+value.get()).background(0xff93c5fd),move->{
                moves++;groups.set(moved(groups.get(),move));
            },()->new SceneSortable.Options<Integer,String>().draggable(item->item!=2||enabled.get()).accepts((container,item)->!"blocked".equals(container)&&enabled.get()));
            board.gap(18);
            ctx.mountOwned(new SceneNode().size(Size.fill(1),Size.fill(1)).padding(25).add(board));
        }
    }
    private DesktopWindow open(Model model,String name) throws Exception {
        DesktopWindow w=DesktopWindow.openWithServices(output.resolve(name+".yaml"),false,services->model::build);
        glfwSetWindowSize(w.handle(),1250,1000);glfwSwapInterval(0);settle(w);return w;
    }
    private void layout(SceneSortable.Layout layout) throws Exception {
        Model model=new Model(layout,false,false);
        try(DesktopWindow w=open(model,"layout-"+layout)) {
            DesktopTestInput input=new DesktopTestInput(w);Map<Integer,MountHandle> mounts=mounts(w,model);
            List<Integer> original=order(w,model,"A");dragToAfter(w,input,model,1,3);
            require(order(w,model,"A").equals(original)&&model.moves==0,"拖中修改权威集合");
            save(w,output.resolve("sortable-"+layout+"-active.png"));release(w,input);
            require(order(w,model,"A").equals(Arrays.asList(2,3,1,4,5,6))&&model.moves==1,"插入排序错误 "+layout+" "+order(w,model,"A"));
            continuous(w,model,mounts);
            dragToAfter(w,input,model,2,6);key(w,input,GLFW_KEY_ESCAPE);release(w,input);
            require(model.moves==1&&order(w,model,"A").equals(Arrays.asList(2,3,1,4,5,6)),"取消改变顺序");
            if(layout==SceneSortable.Layout.GRID){
                glfwSetWindowSize(w.handle(),640,1000);settle(w);continuous(w,model,mounts);
                dragToAfter(w,input,model,2,6);release(w,input);
                require(order(w,model,"A").equals(Arrays.asList(3,1,4,5,6,2))&&model.moves==2,"换列后仍按旧网格排序 "+order(w,model,"A"));
                save(w,output.resolve("sortable-grid-narrow.png"));
            }
            require(glGetError()==GL_NO_ERROR,"排序GPU回放错误");
        }
    }
    private void containers() throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,true,false);
        try(DesktopWindow w=open(model,"containers")) {
            DesktopTestInput input=new DesktopTestInput(w);Map<Integer,MountHandle> mounts=mounts(w,model);
            input.moveTo(model.fields.get(1));input.mouse.invoke(w.handle(),0,GLFW_PRESS,0);input.mouse.invoke(w.handle(),0,GLFW_RELEASE,0);w.frame();
            input.character.invoke(w.handle(),'X');w.frame();String draft=draft(w,model,1);
            pickup(w,input,model.grips.get(1));pointAt(w,input,model.lists.get("C"));release(w,input);
            require(order(w,model,"C").equals(Collections.singletonList(1))&&model.moves==1,"空容器未接收条目");
            continuous(w,model,mounts);require(draft.equals(draft(w,model,1)),"跨容器丢编辑草稿");
            // 非拖动的明确模型迁移仍须保持正在编辑的目标与挂载。
            w.domain.execute(()->require(w.page.runtime().focusRouter().request(w.page.runtime().captureMount(model.fields.get(1))),"编辑焦点失败"));
            w.domain.execute(()->model.groups.set(moveKey(model.groups.get(),1,"B",5)));w.frame();
            input.character.invoke(w.handle(),'Y');w.frame();continuous(w,model,mounts);
            require(draft(w,model,1).contains("Y")&&draft(w,model,1).contains("X"),"明确移动未保持编辑联系");
            pickup(w,input,model.grips.get(1));pointAt(w,input,model.lists.get("A"));key(w,input,GLFW_KEY_ESCAPE);release(w,input);
            require(model.moves==1&&order(w,model,"B").contains(1),"取消跨容器仍移动");
            w.domain.execute(()->{
                List<Integer> outgoing=new ArrayList<>();
                for(SceneSortable.Group<String,Integer> group:model.groups.get())if(group.key.equals("A"))outgoing.addAll(group.items);
                List<SceneSortable.Group<String,Integer>> next=new ArrayList<>();
                for(SceneSortable.Group<String,Integer> group:model.groups.get())if(!group.key.equals("A")){
                    List<Integer> values=new ArrayList<>(group.items);if(group.key.equals("B"))values.addAll(outgoing);
                    next.add(new SceneSortable.Group<>(group.key,values));
                }
                model.groups.set(next);
            });w.frame();continuous(w,model,mounts);
            require(order(w,model,"B").containsAll(Arrays.asList(1,2,3,4,5,6)),"结束旧容器销毁已迁出条目");
            int prior=model.created;
            w.domain.execute(()->model.groups.set(removeKey(model.groups.get(),1)));w.frame();
            w.domain.execute(()->model.groups.set(addKey(model.groups.get(),1,"C")));w.frame();
            require(model.created==prior+1,"真实退出后返回复活旧节点");
            w.domain.execute(()->require(!mounts.get(1).active(),"真实退出未结束旧挂载"));
            save(w,output.resolve("sortable-multiple-lists.png"));
        }
    }
    private void activeMigration() throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,true,false);
        try(DesktopWindow w=open(model,"active-migration")) {
            DesktopTestInput input=new DesktopTestInput(w);Map<Integer,MountHandle> mounts=mounts(w,model);
            pickup(w,input,model.grips.get(1));pointAt(w,input,model.lists.get("C"));
            w.domain.execute(()->model.groups.set(moveKey(model.groups.get(),1,"B",5)));w.frame();continuous(w,model,mounts);
            require(geometry(w).ordered().stream().anyMatch(e->"排序预览 1".equals(e.node.text())),"合法迁移提前取消活动手势");
            pointAt(w,input,model.lists.get("C"));release(w,input);
            require(model.moves==1&&order(w,model,"C").equals(Collections.singletonList(1)),"合法迁移后UP未结束原手势");
        }
    }
    private void overlappingTargets(boolean passive) throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,true,false);model.overlap=true;model.passiveOverlap=passive;
        try(DesktopWindow w=open(model,"overlapping-targets-"+passive)) {
            DesktopTestInput input=new DesktopTestInput(w);
            LayoutBox overlap=hit(geometry(w,model.roots.get("B"))).intersect(hit(geometry(w,model.roots.get("C"))));
            require(overlap.width>0&&overlap.height>0,"所属层重叠反例未命中");
            List<club.heiqi.uilib.ui.scene.layout.SceneGeometryView.Entry> ordered=geometry(w).ordered();
            require(ordered.indexOf(geometry(w,model.roots.get("B")))>ordered.indexOf(geometry(w,model.roots.get("C"))),"正式层序前提不成立");
            pickup(w,input,model.grips.get(1));move(w,input,overlap.x+overlap.width/2,overlap.y+overlap.height/2);release(w,input);
            require(model.moves==1&&order(w,model,passive?"C":"B").contains(1)
                    &&!order(w,model,passive?"B":"C").contains(1),"重叠区域接收资格错误 passive="+passive);
            save(w,output.resolve("sortable-layer-target-"+passive+".png"));
        }
    }
    private void keyboard() throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,false,false);
        try(DesktopWindow w=open(model,"keyboard")) {
            DesktopTestInput input=new DesktopTestInput(w);focusRow(w,model.grips.get(1));
            key(w,input,GLFW_KEY_ENTER);key(w,input,GLFW_KEY_DOWN);key(w,input,GLFW_KEY_ENTER);
            require(order(w,model,"A").equals(Arrays.asList(2,1,3,4,5,6))&&model.moves==1,"一次方向键未移动相邻插槽 "+order(w,model,"A"));
            key(w,input,GLFW_KEY_SPACE);key(w,input,GLFW_KEY_DOWN);key(w,input,GLFW_KEY_ESCAPE);
            require(model.moves==1,"键盘取消提交");
        }
    }
    private void policies() throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,true,false);
        try(DesktopWindow w=open(model,"policies")) {
            DesktopTestInput input=new DesktopTestInput(w);
            pickup(w,input,model.grips.get(1));pointAt(w,input,model.lists.get("blocked"));release(w,input);
            require(model.moves==0,"拒绝容器仍接收");
            w.domain.execute(()->model.enabled.set(false));w.frame();
            pickup(w,input,model.grips.get(2));pointAt(w,input,model.lists.get("C"));release(w,input);
            require(model.moves==0,"禁用来源仍提交");
            w.domain.execute(()->model.enabled.set(true));w.frame();pickup(w,input,model.grips.get(1));pointAt(w,input,model.lists.get("C"));
            w.domain.execute(()->model.enabled.set(false));w.frame();release(w,input);
            require(model.moves==0,"静止下过滤改变仍提交");
        }
    }
    private void scrolling() throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,false,true);
        try(DesktopWindow w=open(model,"scrolling")) {
            DesktopTestInput input=new DesktopTestInput(w);pickup(w,input,model.grips.get(1));LayoutBox viewport=hit(geometry(w,model.lists.get("A")));
            move(w,input,viewport.x+viewport.width/2,viewport.y+viewport.height-2);
            for(int i=0;i<22;i++){Thread.sleep(20);w.frame();}
            require(geometry(w,model.lists.get("A")).scrollOffset>120,"静止指针未持续滚动");
            require(hit(geometry(w,model.rows.get(1))).height==0,"原槽尚未离屏，反例未命中");
            require(geometry(w).ordered().stream().anyMatch(e->"排序预览 1".equals(e.node.text())),"原槽离屏后预览提前结束");
            save(w,output.resolve("sortable-edge-scroll.png"));key(w,input,GLFW_KEY_ESCAPE);release(w,input);
            int stopped=geometry(w,model.lists.get("A")).scrollOffset;Thread.sleep(50);w.frame();
            require(geometry(w,model.lists.get("A")).scrollOffset==stopped&&model.moves==0,"取消后滚动或业务仍变化");
        }
    }
    private static void dragToAfter(DesktopWindow w,DesktopTestInput input,Model model,int source,int target) {
        pickup(w,input,model.grips.get(source));LayoutBox box=geometry(w,model.rows.get(target)).box;
        boolean vertical=model.layout==SceneSortable.Layout.VERTICAL;
        move(w,input,vertical?box.x+box.width/2:box.x+box.width-3,vertical?box.y+box.height-3:box.y+box.height/2);
    }
    private static void focusRow(DesktopWindow w,SceneNode grip) {
        w.domain.execute(()->{SceneNode n=grip;while(n!=null&&!n.focusable())n=n.parent();require(n!=null&&w.page.runtime().focusRouter().request(w.page.runtime().captureMount(n)),"排序正式焦点失败");});
    }
    private static Map<Integer,MountHandle> mounts(DesktopWindow w,Model model) {
        Map<Integer,MountHandle> result=new HashMap<>();w.domain.execute(()->model.rows.forEach((key,node)->result.put(key,w.page.runtime().captureMount(node))));return result;
    }
    private static void continuous(DesktopWindow w,Model model,Map<Integer,MountHandle> mounts) {
        w.domain.execute(()->mounts.forEach((key,mount)->require(mount.active()&&mount.node()==model.rows.get(key),"排序重建挂载 key="+key)));
    }
    private static String draft(DesktopWindow w,Model model,int key) {String[] value={null};w.domain.execute(()->value[0]=model.edits.get(key).snapshot().get().text);return value[0];}
    private static List<Integer> order(DesktopWindow w,Model model,String key) {List<Integer> value=new ArrayList<>();w.domain.execute(()->{for(SceneSortable.Group<String,Integer> group:model.groups.get())if(group.key.equals(key))value.addAll(group.items);});return value;}
    private static SceneSortable.Group<String,Integer> group(String key,Integer... items) {return new SceneSortable.Group<>(key,Arrays.asList(items));}
    private static List<SceneSortable.Group<String,Integer>> moved(List<SceneSortable.Group<String,Integer>> groups,SceneSortable.Move<Integer,String> move) {return moveKey(groups,move.key,move.containerKey,move.beforeKey);}
    private static List<SceneSortable.Group<String,Integer>> moveKey(List<SceneSortable.Group<String,Integer>> groups,int key,String container,Integer before) {return addKey(removeKey(groups,key),key,container,before);}
    private static List<SceneSortable.Group<String,Integer>> removeKey(List<SceneSortable.Group<String,Integer>> groups,int key) {
        List<SceneSortable.Group<String,Integer>> next=new ArrayList<>();for(SceneSortable.Group<String,Integer> group:groups){List<Integer> values=new ArrayList<>(group.items);values.remove(Integer.valueOf(key));next.add(new SceneSortable.Group<>(group.key,values));}return next;
    }
    private static List<SceneSortable.Group<String,Integer>> addKey(List<SceneSortable.Group<String,Integer>> groups,int key,String container) {return addKey(groups,key,container,null);}
    private static List<SceneSortable.Group<String,Integer>> addKey(List<SceneSortable.Group<String,Integer>> groups,int key,String container,Integer before) {
        List<SceneSortable.Group<String,Integer>> next=new ArrayList<>();for(SceneSortable.Group<String,Integer> group:groups){List<Integer> values=new ArrayList<>(group.items);if(Objects.equals(group.key,container)){int index=before==null?values.size():values.indexOf(before);values.add(index<0?values.size():index,key);}next.add(new SceneSortable.Group<>(group.key,values));}return next;
    }
}
