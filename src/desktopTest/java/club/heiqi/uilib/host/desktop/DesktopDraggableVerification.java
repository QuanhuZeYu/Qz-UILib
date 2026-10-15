package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneDraggable;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.input.SceneHitTester;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/** 真实已安装回调与唯一几何/GPU反馈；覆盖用户参考拖动及捕获终止，不在生产中增加验收分支。 */
public final class DesktopDraggableVerification {
    private DesktopDraggableVerification() {}
    public static void main(String[] args) throws Exception {
        Path output=args.length==0?Paths.get("draggable-report"):Paths.get(args[0]);Files.createDirectories(output);
        SceneDraggable.State[] states=new SceneDraggable.State[2];SceneNode[] content=new SceneNode[2],drag=new SceneNode[2];
        @SuppressWarnings("unchecked") Signal<Boolean>[] enabled=new Signal[1],shown=new Signal[1],attached=new Signal[1];
        SceneNode[] viewport=new SceneNode[1];
        int[] clicks={0};
        try(DesktopWindow w=DesktopWindow.open(output.resolve("fixture.yaml"),ctx -> {
            enabled[0]=ctx.domain().signal(true);shown[0]=ctx.domain().signal(true);attached[0]=ctx.domain().signal(true);
            SceneNode root=viewport[0]=new SceneNode().size(Size.fill(1),Size.fill(1)).padding(40).gap(40).scrollable(true);
            for(int i=0;i<2;i++) {
                states[i]=SceneDraggable.state(ctx.domain());
                content[i]=new SceneNode().size(Size.fixed(220),Size.fixed(100)).padding(12).background(i==0?0xff4477dd:0xffde7649);
                if(i==1)content[i].add(SceneButton.create(ctx,() -> "独立按钮",() -> SceneTheme.light(),() -> clicks[0]++));
                drag[i]=SceneDraggable.create(ctx,content[i],states[i]);
                if(i==0){ctx.own(drag[0]::destroy);root.add(new SceneNode().bind(NodeBinding.children(() -> attached[0].get()
                        ?java.util.Collections.singletonList(drag[0]):java.util.Collections.emptyList())));}
                else root.add(drag[i]);
            }
            drag[0].bind(NodeBinding.value(enabled[0],SceneNode::enabled)).bind(NodeBinding.value(shown[0],SceneNode::visible));
            root.add(new SceneNode().size(Size.fixed(1),Size.fixed(1200)));ctx.mountOwned(root);
        })) {
            glfwSetWindowSize(w.handle(),1000,800);settle(w);DesktopTestInput input=new DesktopTestInput(w);
            LayoutBox first=box(w,content[0]),sibling=box(w,content[1]);int x=first.x+30,y=first.y+30;
            down(w,input,x,y);move(w,input,x+120,y+80);
            require(active(w,states[0])&&!active(w,states[1]),"多元素未隔离活动手势");
            LayoutBox moved=box(w,content[0]);require(moved.x==first.x+120&&moved.y==first.y+80,"拖动与共同几何位移不一致");
            require(same(box(w,content[1]),sibling),"拖动改变兄弟占位");
            w.domain.execute(() -> {SceneHitTester.Target old=new SceneHitTester().target(w.page.runtime(),DesktopTestInput.geometry(w),x,y);
                require(old==null||old.node!=drag[0],"原槽残留幽灵拖拽命中");});
            save(w,output.resolve("fixture-active.png"));up(w,input,x+120,y+80);
            require(!active(w,states[0])&&same(box(w,content[0]),first),"释放后未回原槽或活动未结束");
            down(w,input,x,y);input.mouse.invoke(w.handle(),1,GLFW_PRESS,0);w.frame();up(w,input,x,y);
            down(w,input,x,y);move(w,input,x+20,y+20);require(!active(w,states[0]),"第二键尾部重新获取手势");
            up(w,input,x+20,y+20);input.mouse.invoke(w.handle(),1,GLFW_RELEASE,0);w.frame();
            require(focused(w,drag[0]),"鼠标拖动未取得正式焦点");
            key(w,input,GLFW_KEY_SPACE);require(active(w,states[0]),"鼠标释放后不能继续键盘拖动");
            int bx=sibling.x+190,by=sibling.y+80;down(w,input,bx,by);move(w,input,bx+70,by+30);
            require(!active(w,states[0])&&focused(w,drag[1]),"新指针拖动未结束旧键盘拖动或转移焦点");
            require(active(w,states[1])&&!active(w,states[0]),"第二元素不能独立拖动");up(w,input,bx+70,by+30);
            input.click("独立按钮");require(clicks[0]==1&&!active(w,states[1]),"包装劫持了普通子按钮");
            down(w,input,x,y);move(w,input,x+20,y+20);key(w,input,GLFW_KEY_TAB);
            require(!active(w,states[0]),"切换键盘目标未结束旧指针拖动");up(w,input,x+20,y+20);
            down(w,input,x,y);move(w,input,x+60,y+40);w.domain.execute(() -> enabled[0].set(false));w.frame();
            require(!active(w,states[0]),"禁用未终止拖动");w.domain.execute(() -> enabled[0].set(true));
            move(w,input,x+90,y+60);require(!active(w,states[0]),"禁用后恢复被尾部MOVE复活");up(w,input,x+90,y+60);
            down(w,input,x,y);move(w,input,x+60,y+40);w.domain.execute(() -> shown[0].set(false));w.frame();
            require(!active(w,states[0]),"隐藏未终止拖动");w.domain.execute(() -> shown[0].set(true));w.frame();up(w,input,x,y);
            down(w,input,x,y);move(w,input,x+60,y+40);w.domain.execute(() -> attached[0].set(false));w.frame();
            require(!active(w,states[0]),"卸载未结束拖动");w.domain.execute(() -> attached[0].set(true));w.frame();
            move(w,input,x+90,y+70);require(!active(w,states[0]),"重挂载被迟到输入复活");up(w,input,x+90,y+70);
            down(w,input,x,y);move(w,input,x+60,y+40);input.focus.invoke(w.handle(),false);w.frame();
            require(!active(w,states[0]),"失焦未结束拖动");input.focus.invoke(w.handle(),true);w.frame();up(w,input,x,y);
            down(w,input,x,y);move(w,input,x+50,y+40);key(w,input,GLFW_KEY_ESCAPE);require(!active(w,states[0]),"ESC未取消");
            move(w,input,x+80,y+70);require(!active(w,states[0]),"ESC后MOVE重新开始拖动");up(w,input,x+80,y+70);
            for(int tab=0;tab<4&&!focused(w,drag[0]);tab++)key(w,input,GLFW_KEY_TAB);
            require(focused(w,drag[0]),"正式Tab未到达可拖动内容");
            key(w,input,GLFW_KEY_SPACE);require(active(w,states[0]),"键盘Space未拾取");
            key(w,input,GLFW_KEY_RIGHT);key(w,input,GLFW_KEY_DOWN);
            LayoutBox keyboard=box(w,content[0]);require(keyboard.x>first.x&&keyboard.y>first.y,"键盘方向未进入同一排列");
            save(w,output.resolve("fixture-keyboard.png"));key(w,input,GLFW_KEY_SPACE);
            require(!active(w,states[0])&&same(box(w,content[0]),first),"键盘释放未回槽");
            key(w,input,GLFW_KEY_SPACE);key(w,input,GLFW_KEY_ESCAPE);require(!active(w,states[0]),"键盘ESC未取消");
            key(w,input,GLFW_KEY_SPACE);key(w,input,GLFW_KEY_TAB);require(!active(w,states[0]),"键盘失焦未终止拖动");
            down(w,input,x,y);move(w,input,x+30,y+20);glfwSetWindowSize(w.handle(),800,650);settle(w);
            require(active(w,states[0]),"resize无故结束有效拖动");up(w,input,x+30,y+20);
            require(!active(w,states[0]),"resize后不能正常释放");
            down(w,input,x,y);move(w,input,x+35,y+25);LayoutBox beforeScroll=box(w,content[0]);
            input.scroll.invoke(w.handle(),0,-1);w.frame();
            require(DesktopTestInput.geometry(w,viewport[0]).scrollOffset>0,"滚动探针未实际滚动");
            require(same(box(w,content[0]),beforeScroll),"祖先滚动丢失指针抓取点");
            input.scroll.invoke(w.handle(),0,1);w.frame();up(w,input,x+35,y+25);
            require(glGetError()==GL_NO_ERROR,"实际GPU拖动错误");
            System.out.println("DRAGGABLE actualCallbacks=true sharedGeometry=true originalSlotNotHit=true siblingsStable=true multiIndependent=true childButton=true disabledTail=true hiddenCancel=true focusCancel=true escapeCancel=true keyboard=true resize=true secondButtonTail=true unmountCancel=true keyboardBlur=true ancestorScroll=true pointerFocus=true mixedInputExclusive=true");
        }
        try(DesktopWindow w=DesktopWindow.openWithServices(output.resolve("gallery.yaml"),s -> DesktopGallery.createGallery(s,TestGallery.Page.DRAGGABLE))) {
            glfwSetWindowSize(w.handle(),1440,1000);settle(w);DesktopTestInput input=new DesktopTestInput(w);
            for(String name:new String[]{"拖动我","元素 A","元素 B","元素 C"})
                require(DesktopTestInput.geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&name.equals(e.text.logicalText)
                        &&e.box.intersect(e.clip).width==e.box.width),"示例元素溢出视口："+name);
            save(w,output.resolve("draggable-idle.png"));
            LayoutBox label=DesktopTestInput.geometry(w).ordered().stream().filter(e -> e.text!=null&&"拖动我".equals(e.text.logicalText)).findFirst().get().box;
            down(w,input,label.x+8,label.y+8);move(w,input,label.x+128,label.y+88);save(w,output.resolve("draggable-active.png"));
            up(w,input,label.x+128,label.y+88);save(w,output.resolve("draggable-released.png"));
            glfwSetWindowSize(w.handle(),600,900);settle(w);
            for(String name:new String[]{"元素 A","元素 B","元素 C"})
                require(DesktopTestInput.geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&name.equals(e.text.logicalText)
                        &&e.box.intersect(e.clip).width==e.box.width),"窄窗示例被裁掉："+name);
            save(w,output.resolve("draggable-narrow.png"));
            input.click("返回场景概览");input.click("打开拖拽");settle(w);
            require(DesktopTestInput.geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&"Draggable".equals(e.text.logicalText)),"真实目录重入失效");
            System.out.println("DRAGGABLE gallery=true actualGpu=true normalEntry=true");
        }
    }
    // LayoutBox是不可变区域但没有值相等实现；这里比较实际共同几何，不能比较对象身份。
    private static boolean same(LayoutBox a,LayoutBox b){return a.x==b.x&&a.y==b.y&&a.width==b.width&&a.height==b.height;}
    private static LayoutBox box(DesktopWindow w,SceneNode n){return DesktopTestInput.geometry(w,n).box;}
    private static boolean focused(DesktopWindow w,SceneNode node){boolean[] a={false};w.domain.execute(() -> a[0]=w.page.runtime().focusRouter().focused().get()==node);return a[0];}
    private static boolean active(DesktopWindow w,SceneDraggable.State state){boolean[] a={false};w.domain.execute(() -> a[0]=state.snapshot().get().active);return a[0];}
    private static void down(DesktopWindow w,DesktopTestInput i,int x,int y){i.point(x,y);i.mouse.invoke(w.handle(),0,GLFW_PRESS,0);w.frame();}
    private static void move(DesktopWindow w,DesktopTestInput i,int x,int y){i.point(x,y);w.frame();}
    private static void up(DesktopWindow w,DesktopTestInput i,int x,int y){i.point(x,y);i.mouse.invoke(w.handle(),0,GLFW_RELEASE,0);w.frame();}
    private static void key(DesktopWindow w,DesktopTestInput i,int key){i.key.invoke(w.handle(),key,0,GLFW_PRESS,0);i.key.invoke(w.handle(),key,0,GLFW_RELEASE,0);w.frame();}
    private static void save(DesktopWindow w,Path path) throws Exception {settle(w);w.fonts.beginPresentation(1000000);try{w.replay(w.lastPlan);}finally{w.fonts.endPresentation();}ImageIO.write(DesktopTestReadback.readBack(w),"png",path.toFile());}
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
