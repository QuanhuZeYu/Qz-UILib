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
import club.heiqi.uilib.ui.scene.motion.MotionPolicy;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
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
        scenario("拖中让位、反向与独立落下动画", this::feedback);
        scenario("关闭、自定义过渡与业务拒绝", this::transitionPolicies);
        scenario("显式弹簧反向与三种用户策略", () -> motionPolicies(true));
        scenario("原有限过渡遵循动态用户限制", () -> motionPolicies(false));
        scenario("有限曲线来源重求值不延长轨迹", this::finiteSource);
        for (SceneSortable.Layout layout : SceneSortable.Layout.values()) scenario("真实变尺寸 " + layout, () -> layout(layout));
        scenario("跨容器及空容器保持身份与编辑", this::containers);
        scenario("键盘相邻排序与取消", this::keyboard);
        scenario("活动手势随合法条目迁移继续", this::activeMigration);
        scenario("接收目标遵循正式所属层绘制序", () -> overlappingTargets(false));
        scenario("被动所属层不拦下方接收目标", () -> overlappingTargets(true));
        scenario("动态接收过滤与禁用来源", this::policies);
        scenario("原内容禁用后不拾取且活动手势结束", this::contentEligibility);
        scenario("窄内容与让位中再次拾取保持真实反馈盒", this::contentGeometry);
        scenario("停边滚动让位与取消停止", this::scrolling);
    }
    private static final class Model {
        final SceneSortable.Layout layout;
        final boolean multiple, scrolling;
        boolean overlap, passiveOverlap;
        Signal<List<SceneSortable.Group<String,Integer>>> groups;
        Signal<Boolean> enabled;
        Signal<SceneSortable.Transition> transition;
        Signal<MotionSpec> reorderMotion, dropMotion;
        Signal<MotionPolicy> motionPolicy;
        SceneNode preview;
        boolean rejectMove, noHandles, narrowContent, freshTransition;
        Signal<Boolean> contentEnabled;
        final Map<Integer,SceneNode> rows = new HashMap<>(), grips = new HashMap<>(), fields = new HashMap<>();
        final Map<Integer,SceneEditState> edits = new HashMap<>();
        final Map<String,SceneNode> lists = new HashMap<>(), roots = new HashMap<>();
        int moves, created;
        Model(SceneSortable.Layout layout, boolean multiple, boolean scrolling) { this.layout = layout; this.multiple = multiple; this.scrolling = scrolling; }
        void build(club.heiqi.uilib.ui.scene.host.ScenePageContext ctx) {
            List<Integer> values = new ArrayList<>(); for (int i=1;i<=(scrolling?24:6);i++) values.add(i);
            groups=ctx.domain().signal(multiple?Arrays.asList(group("A",1,2,3),group("B",4,5,6),group("C"),group("blocked"))
                    :Collections.singletonList(new SceneSortable.Group<>("A",values)));
            enabled=ctx.domain().signal(true);contentEnabled=ctx.domain().signal(true);
            transition=ctx.domain().signal(new SceneSortable.Transition(250,.25,1,.5,1));
            reorderMotion=ctx.domain().signal(null);dropMotion=ctx.domain().signal(null);
            motionPolicy=ctx.domain().signal(MotionPolicy.NORMAL);ctx.motionPolicy(motionPolicy);
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
                SceneNode content=new SceneNode().size(layout==SceneSortable.Layout.HORIZONTAL?Size.fixed(150+key%3*28):narrowContent?Size.fixed(200):Size.fill(1),Size.natural())
                        .padding(8).gap(5).background(0xffdbeafe).add(grip).add(field)
                        .add(new SceneNode().size(Size.fill(1),Size.fixed(key%2==0?40:20)).text("内容 "+key));
                rows.put(key,content);grips.put(key,grip);fields.put(key,field);
                content.bind(club.heiqi.uilib.ui.scene.runtime.NodeBinding.value(contentEnabled,SceneNode::enabled));
                return noHandles?new SceneSortable.Row(content):new SceneSortable.Row(content,grip);
            },value->{preview=new SceneNode().size(Size.fixed(180),Size.fixed(70)).text("排序预览 "+value.get()).background(0xff93c5fd);return preview;},move->{
                moves++;if(!rejectMove)groups.set(moved(groups.get(),move));
            },()->new SceneSortable.Options<Integer,String>().draggable(item->item!=2||enabled.get()).accepts((container,item)->!"blocked".equals(container)&&enabled.get()).transition(freshTransition?new SceneSortable.Transition(250,.25,1,.5,1):transition.get()).reorderMotion(reorderMotion.get()).dropMotion(dropMotion.get()));
            board.gap(18);
            ctx.mountOwned(new SceneNode().size(Size.fill(1),Size.fill(1)).padding(25).add(board));
        }
    }
    private DesktopWindow open(Model model,String name) throws Exception {
        DesktopWindow w=DesktopWindow.openWithServices(output.resolve(name+".yaml"),false,services->model::build);
        glfwSetWindowSize(w.handle(),1250,1000);glfwSwapInterval(0);settle(w);return w;
    }
    private void feedback() throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,false,false);
        try(DesktopWindow w=open(model,"feedback")) {
            DesktopTestInput input=new DesktopTestInput(w);Map<Integer,MountHandle> mounts=mounts(w,model);
            w.domain.execute(()->model.transition.set(new SceneSortable.Transition(600,p->p)));w.frame();
            LayoutBox source=geometry(w,model.rows.get(1)).box,before=geometry(w,model.rows.get(2)).box;
            dragToAfter(w,input,model,1,3);advance(w,90);
            LayoutBox middle=geometry(w,model.rows.get(2)).box,preview=geometry(w,model.preview).box;
            require(preview.width==source.width&&preview.height==source.height,"预览没有保持原项尺寸");
            advance(w,650);LayoutBox after=geometry(w,model.rows.get(2)).box;
            System.out.println("排序让位观测：beforeY="+before.y+", middleY="+middle.y+", afterY="+after.y+", moves="+model.moves);
            require(after.y<middle.y&&middle.y<before.y&&model.moves==0,"排序未呈现真实让位中间帧");
            save(w,output.resolve("sortable-feedback-active.png"));continuous(w,model,mounts);
            LayoutBox slot=geometry(w,model.rows.get(1)).box,from=geometry(w,model.preview).box;
            advance(w,100);require(geometry(w,model.rows.get(1)).box.y==slot.y,"静止悬停候选振荡");
            release(w,input);require(model.moves==1&&geometry(w).get(model.preview)!=null,"放下立即移除了反馈");
            advance(w,70);LayoutBox dropping=geometry(w,model.preview).box;
            require(Math.abs((long)dropping.y-slot.y)<Math.abs((long)from.y-slot.y),"落下没有向实际插槽靠近");
            advance(w,350);require(geometry(w).get(model.preview)==null,"落下完成后预览仍存在");
            settle(w);Object plan=w.lastPlan;w.frame();w.frame();require(w.lastPlan==plan,"排序结束未恢复静态绘制复用");
            continuous(w,model,mounts);
            dragToAfter(w,input,model,2,6);advance(w,80);
            LayoutBox visible=geometry(w,model.rows.get(3)).box;
            LayoutBox original=before;move(w,input,original.x+original.width/2,source.y+4);
            LayoutBox reversed=geometry(w,model.rows.get(3)).box;
            require(Math.abs((long)reversed.y-visible.y)<12,"快速反向从旧端点跳变");
            key(w,input,GLFW_KEY_ESCAPE);release(w,input);advance(w,750);
            require(model.moves==1&&order(w,model,"A").equals(Arrays.asList(2,3,1,4,5,6)),"取消过渡写入业务集合");
        }
    }
    private void transitionPolicies() throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,false,false);model.rejectMove=true;
        try(DesktopWindow w=open(model,"transition-policies")) {
            DesktopTestInput input=new DesktopTestInput(w);w.domain.execute(()->model.transition.set(null));w.frame();
            int before=geometry(w,model.rows.get(2)).box.y;dragToAfter(w,input,model,1,3);
            require(geometry(w,model.rows.get(2)).box.y<before,"关闭过渡后未立即让位");
            release(w,input);require(model.moves==1&&geometry(w).get(model.preview)!=null,"关闭排序过渡误关独立落下动画");
            advance(w,350);require(order(w,model,"A").equals(Arrays.asList(1,2,3,4,5,6))&&geometry(w,model.rows.get(2)).box.y==before,
                    "业务未接受Move时展示序未收束");
            w.domain.execute(()->model.transition.set(new SceneSortable.Transition(300,.34,1.56,.64,1)));w.frame();
            dragToAfter(w,input,model,1,3);advance(w,350);key(w,input,GLFW_KEY_ESCAPE);release(w,input);advance(w,350);
            require(model.moves==1,"自定义回弹过渡取消仍提交");
        }
    }
    private void finiteSource() throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,false,false);model.rejectMove=true;model.freshTransition=true;
        try(DesktopWindow w=open(model,"finite-source")) {
            DesktopTestInput input=new DesktopTestInput(w);int target=geometry(w,model.rows.get(1)).box.y;
            int original=geometry(w,model.rows.get(2)).box.y;
            dragToAfter(w,input,model,1,3);advance(w,350);
            require(geometry(w,model.rows.get(2)).box.y==target,"重复读取有限曲线来源延长了让位");
            key(w,input,GLFW_KEY_ESCAPE);release(w,input);advance(w,600);
            require(model.moves==0&&geometry(w).get(model.preview)==null&&geometry(w,model.rows.get(2)).box.y==original,
                    "有限曲线来源重新求值使取消运动持续");
        }
    }
    private void motionPolicies(boolean spring) throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,false,false);model.rejectMove=true;
        try(DesktopWindow w=open(model,"motion-policies-"+spring)) {
            DesktopTestInput input=new DesktopTestInput(w);Map<Integer,MountHandle> mounts=mounts(w,model);
            w.domain.execute(()->{
                model.transition.set(spring?null:new SceneSortable.Transition(800,.34,1.56,.64,1));
                if(spring){model.reorderMotion.set(MotionSpec.spring(.42,.68));model.dropMotion.set(MotionSpec.spring(.42,.68));}
            });w.frame();
            LayoutBox source=geometry(w,model.rows.get(1)).box,before=geometry(w,model.rows.get(2)).box;
            dragToAfter(w,input,model,1,3);advance(w,50);
            int middle=geometry(w,model.rows.get(2)).box.y;
            require(source.y<middle&&middle<before.y&&model.moves==0,"让位没有可见中间值 spring="+spring);
            w.domain.execute(()->{
                long now=w.page.runtime().inputNanos();
                int visible=w.page.pipeline().current().geometry.get(model.rows.get(2)).box.y;
                for(int i=0;i<3;i++){w.page.runtime().preparePresentation(now);w.page.pipeline().current();}
                require(w.page.pipeline().current().geometry.get(model.rows.get(2)).box.y==visible,"同帧重复准备推进排序运动");
            });
            move(w,input,source.x+source.width/2,source.y+4);
            int reversed=geometry(w,model.rows.get(2)).box.y;
            require(Math.abs((long)reversed-middle)<source.height/2,"途中反向从旧端点跳变");
            w.domain.execute(()->model.motionPolicy.set(MotionPolicy.REDUCED));w.frame();
            int start=geometry(w,model.rows.get(2)).box.y,previous=start;
            require(start<before.y,"减少模式切换未保留当前让位");
            for(int i=0;i<8;i++) {
                advance(w,20);int current=geometry(w,model.rows.get(2)).box.y;
                require(previous<=current&&current<=before.y,"减少模式让位往返或过冲");
                if(i==1)require(start<current&&current<before.y,"减少模式缺少平稳中间帧");
                previous=current;
            }
            require(previous==before.y,"减少模式未在短时内结束让位");continuous(w,model,mounts);
            w.domain.execute(()->model.motionPolicy.set(MotionPolicy.NORMAL));w.frame();
            LayoutBox third=geometry(w,model.rows.get(3)).box;
            move(w,input,third.x+third.width/2,third.y+third.height-3);advance(w,35);release(w,input);
            require(model.moves==1&&geometry(w).get(model.preview)!=null,"独立落下未保留反馈");
            advance(w,30);w.domain.execute(()->model.motionPolicy.set(MotionPolicy.REDUCED));w.frame();
            int dropStart=geometry(w,model.preview).box.y;long distance=Math.abs((long)dropStart-source.y);
            for(int i=0;i<3;i++){
                advance(w,20);LayoutBox current=geometry(w,model.preview).box;
                long next=Math.abs((long)current.y-source.y);
                require(next<=distance,"减少模式落下重新远离当前业务位置");distance=next;
            }
            require(distance>0&&distance<Math.abs((long)dropStart-source.y),"减少模式落下没有可见收敛");
            w.domain.execute(()->model.motionPolicy.set(MotionPolicy.OFF));w.frame();
            require(geometry(w).get(model.preview)==null&&geometry(w,model.rows.get(2)).box.y==before.y,
                    "关闭模式仍保留让位或落下");
            require(model.moves==1&&order(w,model,"A").equals(Arrays.asList(1,2,3,4,5,6)),"策略切换改变业务提交");
            continuous(w,model,mounts);settle(w);Object plan=w.lastPlan;w.frame();w.frame();
            require(w.lastPlan==plan,"策略结束后未恢复静态绘制复用");
        }
    }
    private static void advance(DesktopWindow window,long millis) throws Exception {
        long until=System.nanoTime()+millis*1_000_000L;
        do {window.frame();Thread.sleep(10);} while(System.nanoTime()<until);
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
            key(w,input,GLFW_KEY_ENTER);key(w,input,GLFW_KEY_DOWN);
            LayoutBox slot=geometry(w,model.rows.get(1)).box,preview=geometry(w,model.preview).box;
            require(slot.y==preview.y,"键盘预览没有跟随当前插槽");
            key(w,input,GLFW_KEY_UP);key(w,input,GLFW_KEY_ENTER);
            require(order(w,model,"A").equals(Arrays.asList(1,2,3,4,5,6))&&model.moves==0,"键盘DOWN后UP没有返回相邻原槽");
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
    private void contentEligibility() throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,false,false);model.noHandles=true;
        try(DesktopWindow w=open(model,"content-eligibility")) {
            DesktopTestInput input=new DesktopTestInput(w);
            w.domain.execute(()->model.contentEnabled.set(false));w.frame();
            LayoutBox source=geometry(w,model.rows.get(1)).box,target=geometry(w,model.rows.get(3)).box;
            down(w,input,source.x+source.width-5,source.y+source.height-5);
            move(w,input,target.x+target.width/2,target.y+target.height-3);release(w,input);
            require(model.moves==0,"原内容已禁用仍从背景拾取并提交");
            w.domain.execute(()->model.contentEnabled.set(true));w.frame();
            pickup(w,input,model.grips.get(1));
            w.domain.execute(()->model.contentEnabled.set(false));w.frame();
            advance(w,350);require(geometry(w).get(model.preview)==null,"原内容禁用后活动预览未结束");
            release(w,input);require(model.moves==0,"活动途中禁用原内容仍提交");
        }
    }
    private void contentGeometry() throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,false,false);model.narrowContent=true;
        try(DesktopWindow w=open(model,"content-geometry")) {
            DesktopTestInput input=new DesktopTestInput(w);
            w.domain.execute(()->model.transition.set(new SceneSortable.Transition(800,p->p)));w.frame();
            LayoutBox content=geometry(w,model.rows.get(1)).box;pickup(w,input,model.grips.get(1));
            require(geometry(w,model.preview).box.width==content.width,"预览使用了满列包装宽度");
            LayoutBox third=geometry(w,model.rows.get(3)).box;
            move(w,input,third.x+third.width/2,third.y+third.height-3);advance(w,60);
            key(w,input,GLFW_KEY_ESCAPE);release(w,input);advance(w,70);
            require(geometry(w,model.preview).box.width==content.width,"落下或取消将窄预览拉成满列包装宽度");
            advance(w,230);
            LayoutBox grip=geometry(w,model.grips.get(2)).box,visible=geometry(w,model.rows.get(2)).box;
            down(w,input,grip.x+3,grip.y+3);LayoutBox feedback=geometry(w,model.preview).box;
            require(Math.abs((long)feedback.x-visible.x)<=2&&Math.abs((long)feedback.y-visible.y)<=2,
                    "让位途中再次拾取跳回包装槽");
            key(w,input,GLFW_KEY_ESCAPE);release(w,input);
        }
    }
    private void scrolling() throws Exception {
        Model model=new Model(SceneSortable.Layout.VERTICAL,false,true);
        try(DesktopWindow w=open(model,"scrolling")) {
            DesktopTestInput input=new DesktopTestInput(w);pickup(w,input,model.grips.get(1));LayoutBox viewport=hit(geometry(w,model.lists.get("A")));
            move(w,input,viewport.x+viewport.width/2,viewport.y+viewport.height-2);
            for(int i=0;i<22;i++){Thread.sleep(20);w.frame();}
            require(geometry(w,model.lists.get("A")).scrollOffset>120,"静止指针未持续滚动");
            require(hit(geometry(w,model.rows.get(2))).height==0,"顶部条目尚未离屏，反例未命中");
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
