package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static club.heiqi.uilib.host.desktop.DesktopTestInput.*;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneDraggable;
import club.heiqi.uilib.ui.scene.control.SceneSortableList;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** 隐藏真实GLFW回调验收：不移动系统鼠标、不抢前台；覆盖示例与字体排序的实际反例。 */
public final class DesktopDraggableDeepVerification {
    private static Path output;
    public static void main(String[] args) throws Exception {
        output=Paths.get(args.length==0?"draggable-deep-report":args[0]);Files.createDirectories(output);
        for(String kind:Arrays.asList("handles","handle-distance","handle-delay","axisX","axisY","grid","parent","viewport","overlay","distance","delay","or")) verifyDrag(kind);
        verifySorting();
        verifyKeyboardFrameFailure();
        System.out.println("DRAG_DEEP handles=true dual=true overlay=true axis=true grid=true bounds=true distance=true delay=true tolerance=true or=true enter=true dropCancel=true sorting=true stableIdentity=true variableHeight=true stoppedPointerAutoScroll=true deleteKey=true hidden=true actualCallbacks=true realGl=true");
    }
    private static void verifyDrag(String kind) throws Exception {
        SceneDraggable.State[] state={null};SceneNode[] body={null},h1={null},h2={null},preview={null},stage={null},wrapper={null};
        int[] drops={0},dx={0},dy={0},clicks={0};Signal<SceneDraggable.Options>[] policy=new Signal[1];
        try(DesktopWindow w=DesktopWindow.openWithServices(output.resolve(kind+".yaml"),false,services -> ctx -> {
            state[0]=SceneDraggable.state(ctx.domain());
            h1[0]=new SceneNode().size(Size.fixed(24),Size.fixed(64)).text("::").background(0xffe0e7ff);
            h2[0]=new SceneNode().size(Size.fixed(24),Size.fixed(64)).text("::").background(0xffe0e7ff).focusable(true).clickFocus(true).onClick(() -> clicks[0]+=100);
            body[0]=new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fixed(240),Size.fixed(90)).padding(8).gap(6).background(0xfff0f4ff);
            body[0].add(h1[0]).add(new SceneNode().size(Size.fixed(65),Size.fixed(64)).text("正文"));
            body[0].add(h2[0]).add(SceneButton.create(ctx,() -> "按钮",() -> SceneTheme.light(),() -> clicks[0]++).size(Size.fixed(70),Size.fixed(50)));
            preview[0]=new SceneNode().size(Size.fixed(240),Size.fixed(90)).padding(12).text("独立拖动预览").background(0xffb8d5ff);
            SceneDraggable.Options options=new SceneDraggable.Options().onDrop(drop -> {drops[0]++;dx[0]=drop.deltaX;dy[0]=drop.deltaY;});
            switch(kind){
                case "handles":options=options.handles(h1[0],h2[0]);break;
                case "handle-distance":options=options.handles(h1[0]).activation(8,0,0);break;
                case "handle-delay":options=options.activation(0,80,3).handles(h1[0]);break;
                case "axisX":options=options.axis(SceneDraggable.Axis.X);break;
                case "axisY":options=options.axis(SceneDraggable.Axis.Y);break;
                case "grid":options=options.grid(24,24);break;
                case "parent":options=options.bounds(SceneDraggable.Bounds.PARENT);break;
                case "viewport":options=options.bounds(SceneDraggable.Bounds.VIEWPORT);break;
                case "overlay":options=options.overlay(preview[0]);break;
                case "distance":options=options.activation(8,0,0);break;
                case "delay":options=options.activation(0,80,3);break;
                case "or":options=options.activation(8,80,3);break;
                default:throw new AssertionError(kind);
            }
            policy[0]=ctx.domain().signal(options);wrapper[0]=SceneDraggable.create(ctx,body[0],state[0],policy[0]);
            stage[0]=new SceneNode().size(Size.fixed(500),Size.fixed(250)).padding(10).background(0xfffafafa).add(wrapper[0]);
            ctx.mountOwned(new SceneNode().size(Size.fill(1),Size.fill(1)).padding(40).add(stage[0]));
        })) {
            require(glfwGetWindowAttrib(w.handle(),GLFW_VISIBLE)==GLFW_FALSE,"深验收窗口不应显示");glfwSwapInterval(0);settle(w);DesktopTestInput input=new DesktopTestInput(w);
            LayoutBox start=geometry(w,body[0]).box,grip=hit(geometry(w,h1[0]));int x=grip.x+8,y=grip.y+8;
            if(kind.equals("handles")){
                down(w,input,start.x+70,start.y+20);move(w,input,start.x+90,start.y+45);require(!active(w,state[0]),"正文被指定把手劫持");up(w,input);
                input.click("按钮");require(clicks[0]==1&&drops[0]==0,"普通子按钮被拖拽劫持");
                for(SceneNode handle:Arrays.asList(h1[0],h2[0])){LayoutBox hb=hit(geometry(w,handle));down(w,input,hb.x+8,hb.y+8);move(w,input,hb.x+38,hb.y+28);require(active(w,state[0]),"双把手不能启动同一拖动");up(w,input);}
                require(drops[0]==2&&clicks[0]==1,"显式可聚焦把手未委派拖动或误触自己的click");
                key(w,input,GLFW_KEY_ENTER);require(active(w,state[0]),"Enter未沿正式焦点拾取");key(w,input,GLFW_KEY_RIGHT);key(w,input,GLFW_KEY_ENTER);require(drops[0]==3&&!active(w,state[0]),"Enter未正常结束");
            } else if(kind.endsWith("distance")){
                down(w,input,x,y);require(!active(w,state[0]),"距离条件在DOWN即触发");move(w,input,x+3,y);up(w,input);require(drops[0]==0,"等待中UP被计为drop");
                down(w,input,x,y);move(w,input,x+12,y);require(active(w,state[0]),"距离达到后未激活");up(w,input);require(drops[0]==1,"距离拖动未提交一次");
            } else if(kind.endsWith("delay")||kind.equals("or")){
                down(w,input,x,y);require(!active(w,state[0]),"延迟条件在DOWN即触发");Thread.sleep(100);w.frame();require(active(w,state[0]),"指针静止延迟到期未激活");up(w,input);require(drops[0]==1,"延迟正常释放未提交");
                down(w,input,x,y);move(w,input,x+4,y);Thread.sleep(100);w.frame();require(!active(w,state[0]),"超容差的延迟分支复活");
                if(kind.equals("or")){move(w,input,x+14,y);require(active(w,state[0]),"延迟分支失效破坏独立距离OR分支");}
                key(w,input,GLFW_KEY_ESCAPE);up(w,input);require(drops[0]==1,"取消误提交drop");
            } else {
                down(w,input,x,y);move(w,input,x+30,y+40);require(active(w,state[0]),"未进入活动拖动");LayoutBox moved=geometry(w,kind.equals("overlay")?preview[0]:body[0]).box;
                if(kind.equals("axisX"))require(moved.y==start.y&&moved.x==start.x+30,"横轴约束与几何不一致");
                if(kind.equals("axisY"))require(moved.x==start.x&&moved.y==start.y+40,"纵轴约束与几何不一致");
                if(kind.equals("grid"))require(moved.x-start.x==Math.round(30f/24)*24&&moved.y-start.y==Math.round(40f/24)*24,"网格与正式几何不一致");
                if(kind.equals("overlay")){require(same(start,geometry(w,body[0]).box),"预览拖动移走原槽");require(moved.x==start.x+30&&moved.y==start.y+40,"独立预览未跟随指针");save(w,kind+"-active.png");}
                if(kind.equals("parent")||kind.equals("viewport")){
                    move(w,input,x+900,y+900);moved=geometry(w,body[0]).box;LayoutBox bound=kind.equals("parent")?geometry(w,stage[0]).box:new LayoutBox(0,0,w.width(),w.height());
                    require(moved.x>=bound.x&&moved.y>=bound.y&&moved.x+moved.width<=bound.x+bound.width&&moved.y+moved.height<=bound.y+bound.height,"硬边界未约束整体内容");
                }
                int finalX=moved.x-start.x,finalY=moved.y-start.y;up(w,input);require(drops[0]==1&&dx[0]==finalX&&dy[0]==finalY,"drop发布的位移与当前受约束几何不同");require(same(start,geometry(w,body[0]).box),"释放未回位");
                down(w,input,x,y);move(w,input,x+20,y+20);key(w,input,GLFW_KEY_ESCAPE);up(w,input);require(drops[0]==1,"ESC误发布正常放下");
            }
            require(glGetError()==GL_NO_ERROR,"真实GL回放异常");System.out.println("DRAG_DEEP_CASE "+kind+" drops="+drops[0]+" complete=true");
        }
    }
    /** 业务公开snapshot订阅在resize重限位时抛错；没有pointer owner也必须取消键盘手势。 */
    private static void verifyKeyboardFrameFailure() throws Exception {
        SceneDraggable.State[] state={null};SceneNode[] drag={null};boolean[] armed={false};int[] drops={0};RuntimeException original=new RuntimeException("键盘拖动重新限位的Effect失败A");
        try(DesktopWindow w=DesktopWindow.openWithServices(output.resolve("keyboard-frame-failure.yaml"),false,services -> ctx -> {
            state[0]=SceneDraggable.state(ctx.domain());
            SceneNode body=new SceneNode().size(Size.fixed(240),Size.fixed(90)).text("键盘输入帧异常").background(0xffccdeff);
            drag[0]=SceneDraggable.create(ctx,body,state[0],new SceneDraggable.Options().bounds(SceneDraggable.Bounds.VIEWPORT).onDrop(value -> drops[0]++));
            ctx.domain().effect(ctx.scope(),() -> {SceneDraggable.Snapshot value=state[0].snapshot().get();if(armed[0]&&value.active&&value.deltaX<350){armed[0]=false;throw original;}});
            ctx.mountOwned(new SceneNode().size(Size.fill(1),Size.fill(1)).padding(40).add(drag[0]));
        })){
            glfwSwapInterval(0);settle(w);DesktopTestInput input=new DesktopTestInput(w);
            w.domain.execute(() -> require(w.page.runtime().focusRouter().request(w.page.runtime().captureMount(drag[0])),"不能建立正式键盘焦点"));
            key(w,input,GLFW_KEY_ENTER);for(int i=0;i<16;i++)key(w,input,GLFW_KEY_RIGHT);require(active(w,state[0]),"键盘异常反例未先进入拖动");
            armed[0]=true;glfwSetWindowSize(w.handle(),500,600);boolean failed=false;
            for(int i=0;i<6&&!failed;i++)try{w.frame();}catch(RuntimeException actual){require(actual==original,"键盘输入帧须保原Effect异常身份");failed=true;}
            require(failed,"未命中resize后重新clamp公开snapshot的Effect");require(!active(w,state[0]),"无pointerowner的失败键盘手势仍active");
            key(w,input,GLFW_KEY_ENTER);require(drops[0]==0&&active(w,state[0]),"Enter提交了失败旧手势而非新拾取");key(w,input,GLFW_KEY_ESCAPE);require(!active(w,state[0])&&drops[0]==0,"异常后新手势取消失败");
            System.out.println("DRAG_DEEP_KEYBOARD_FRAME_FAILURE originalIdentity=true inactiveAfterFailure=true lateEnterNoDrop=true");
        }
    }
    private static final class Item {final int key;Item(int key){this.key=key;}}
    private static void verifySorting() throws Exception {
        Signal<List<Item>>[] items=new Signal[1];SceneNode[] list={null};Map<Integer,SceneNode> handles=new HashMap<>(),contents=new HashMap<>();int[] moves={0};
        try(DesktopWindow w=DesktopWindow.openWithServices(output.resolve("sorting.yaml"),false,services -> ctx -> {
            List<Item> initial=new ArrayList<>();for(int i=0;i<24;i++)initial.add(new Item(i));items[0]=ctx.domain().signal(Collections.unmodifiableList(initial));
            list[0]=SceneSortableList.create(ctx,items[0],item -> item.key,item -> {
                int key=item.get().key;SceneNode handle=new SceneNode().size(Size.fixed(30),Size.fixed(40)).text("::");
                SceneNode content=new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).padding(8).gap(8).background(0xfff3f5ff).add(handle)
                        .add(new SceneNode().size(Size.fill(1),Size.fixed(key%3==0?80:40)).text("重复的字体路径 · 行 "+key));
                handles.put(key,handle);contents.put(key,content);return new SceneSortableList.Row(content,handle);
            },item -> new SceneNode().size(Size.fixed(280),Size.fixed(70)).text("来源预览 "+item.get().key).background(0xffbbd4ff),move -> {
                List<Item> next=new ArrayList<>(items[0].get());Item source=null,before=null;for(Item item:next){if(item.key==move.key)source=item;if(move.beforeKey!=null&&item.key==move.beforeKey)before=item;}
                require(source!=null,"排序提交失去来源");next.remove(source);next.add(before==null?next.size():next.indexOf(before),source);moves[0]++;items[0].set(Collections.unmodifiableList(next));
            }).size(Size.fill(1),Size.fixed(340)).gap(8).scrollable(true).clip(true);
            ctx.mountOwned(new SceneNode().size(Size.fill(1),Size.fill(1)).padding(30).add(list[0]));
        })){
            require(glfwGetWindowAttrib(w.handle(),GLFW_VISIBLE)==GLFW_FALSE,"排序验收窗口不应显示");glfwSwapInterval(0);settle(w);DesktopTestInput input=new DesktopTestInput(w);
            Map<Integer,java.util.UUID> ids=new HashMap<>();for(Map.Entry<Integer,SceneNode> entry:contents.entrySet())ids.put(entry.getKey(),entry.getValue().id());
            List<Integer> before=keys(w,items[0]);LayoutBox grip=hit(geometry(w,handles.get(0))),target=geometry(w,contents.get(2)).box;
            down(w,input,grip.x+8,grip.y+8);move(w,input,grip.x+8,target.y+target.height-2);require(keys(w,items[0]).equals(before)&&moves[0]==0,"拖动中修改了权威顺序");save(w,"sort-active.png");up(w,input);
            List<Integer> after=keys(w,items[0]);require(!after.equals(before)&&after.indexOf(0)==2&&moves[0]==1,"正常drop没有一次按变高行重排："+after);
            for(Map.Entry<Integer,SceneNode> entry:contents.entrySet())require(ids.get(entry.getKey()).equals(entry.getValue().id()),"排序重建了稳定行");
            int first=after.get(0);grip=hit(geometry(w,handles.get(first)));target=geometry(w,contents.get(after.get(2))).box;
            down(w,input,grip.x+8,grip.y+8);move(w,input,grip.x+8,target.y+target.height-2);key(w,input,GLFW_KEY_ESCAPE);up(w,input);require(keys(w,items[0]).equals(after)&&moves[0]==1,"取消排序改变列表");
            input.moveTo(handles.get(first));input.mouse.invoke(w.handle(),0,GLFW_PRESS,0);input.mouse.invoke(w.handle(),0,GLFW_RELEASE,0);w.frame();key(w,input,GLFW_KEY_ENTER);key(w,input,GLFW_KEY_DOWN);key(w,input,GLFW_KEY_ENTER);
            require(!keys(w,items[0]).equals(after)&&moves[0]==2,"键盘排序未沿条目提交");
            int source=keys(w,items[0]).get(0);grip=hit(geometry(w,handles.get(source)));LayoutBox viewport=hit(geometry(w,list[0]));
            down(w,input,grip.x+8,grip.y+8);move(w,input,grip.x+8,viewport.y+viewport.height-2);int offset=geometry(w,list[0]).scrollOffset;
            for(int i=0;i<18;i++){Thread.sleep(20);w.frame();}
            require(geometry(w,list[0]).scrollOffset>offset,"指针停在边缘时没有持续自动滚动");save(w,"sort-autoscroll.png");key(w,input,GLFW_KEY_ESCAPE);up(w,input);
            int stopped=geometry(w,list[0]).scrollOffset;Thread.sleep(40);w.frame();require(geometry(w,list[0]).scrollOffset==stopped&&moves[0]==2,"取消后滚动仍活动或误提交");
            // 删除当前拖动key时，旧物理UP不得提交或转给后台。
            w.domain.execute(() -> {List<Item> next=new ArrayList<>(items[0].get());Collections.rotate(next,1);items[0].set(Collections.unmodifiableList(next));});settle(w);
            SceneGeometryView.Entry handle=geometry(w).ordered().stream().filter(e -> "::".equals(e.node.text())&&hit(e).height>0).findFirst().orElseThrow(() -> new AssertionError("无可见把手"));
            Integer selected=handles.entrySet().stream().filter(e -> e.getValue()==handle.node).map(Map.Entry::getKey).findFirst().get();grip=hit(handle);down(w,input,grip.x+8,grip.y+8);move(w,input,grip.x+8,grip.y+30);
            w.domain.execute(() -> {List<Item> next=new ArrayList<>(items[0].get());next.removeIf(item -> item.key==selected);items[0].set(Collections.unmodifiableList(next));});w.frame();up(w,input);require(moves[0]==2,"删除来源后旧UP仍提交排序");
            require(glGetError()==GL_NO_ERROR,"排序真实GL回放异常");save(w,"sort-finished.png");
        }
    }
    private static List<Integer> keys(DesktopWindow w,Signal<List<Item>> source){List<Integer> result=new ArrayList<>();w.domain.execute(() -> {for(Item item:source.get())result.add(item.key);});return result;}
    private static boolean active(DesktopWindow w,SceneDraggable.State state){boolean[] a={false};w.domain.execute(() -> a[0]=state.snapshot().get().active);return a[0];}
    private static void down(DesktopWindow w,DesktopTestInput input,int x,int y){input.point(x,y);input.mouse.invoke(w.handle(),0,GLFW_PRESS,0);w.frame();}
    private static void move(DesktopWindow w,DesktopTestInput input,int x,int y){input.point(x,y);w.frame();}
    private static void up(DesktopWindow w,DesktopTestInput input){input.mouse.invoke(w.handle(),0,GLFW_RELEASE,0);w.frame();}
    private static void key(DesktopWindow w,DesktopTestInput input,int key){input.key.invoke(w.handle(),key,0,GLFW_PRESS,0);input.key.invoke(w.handle(),key,0,GLFW_RELEASE,0);w.frame();}
    private static boolean same(LayoutBox a,LayoutBox b){return a.x==b.x&&a.y==b.y&&a.width==b.width&&a.height==b.height;}
    private static void save(DesktopWindow w,String name) throws Exception {settle(w);glClearColor(1,1,1,1);glClear(GL_COLOR_BUFFER_BIT);w.fonts.beginPresentation(1000000);try{w.replay(w.lastPlan);}finally{w.fonts.endPresentation();}glFinish();ImageIO.write(DesktopTestReadback.readBack(w),"png",output.resolve(name).toFile());}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
