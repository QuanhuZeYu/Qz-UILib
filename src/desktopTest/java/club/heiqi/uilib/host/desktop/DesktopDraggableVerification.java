package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.image.BufferedImage;
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
            BufferedImage idle=save(w,output.resolve("draggable-idle.png"));
            SceneNode surface=DesktopTestInput.geometry(w).ordered().stream().filter(e -> e.text!=null&&"拖动我".equals(e.text.logicalText)).findFirst().get().node.parent().parent();
            LayoutBox idleFrame=box(w,surface);roundedPixels(idle,idleFrame,idle,idleFrame,"idle");
            LayoutBox label=DesktopTestInput.geometry(w).ordered().stream().filter(e -> e.text!=null&&"拖动我".equals(e.text.logicalText)).findFirst().get().box;
            down(w,input,label.x+8,label.y+8);move(w,input,label.x+128,label.y+88);
            BufferedImage active=save(w,output.resolve("draggable-active.png"));LayoutBox activeFrame=box(w,surface);
            roundedPixels(active,activeFrame,idle,idleFrame,"drag");
            require(colorDistance(active.getRGB(activeFrame.x+12,activeFrame.y+12),idle.getRGB(idleFrame.x+12,idleFrame.y+12))>2,"拖动填色未更新");
            up(w,input,label.x+128,label.y+88);
            BufferedImage focused=save(w,output.resolve("draggable-released.png"));LayoutBox focusedFrame=box(w,surface);
            roundedPixels(focused,focusedFrame,idle,idleFrame,"focus");
            require(colorDistance(focused.getRGB(focusedFrame.x+12,focusedFrame.y+12),idle.getRGB(idleFrame.x+12,idleFrame.y+12))>2
                    &&colorDistance(focused.getRGB(focusedFrame.x+12,focusedFrame.y+12),active.getRGB(activeFrame.x+12,activeFrame.y+12))>2,"聚焦填色未更新");
            for(String title:new String[]{"独立把手","双启动把手","拖动预览","只沿横轴","只沿纵轴","网格吸附","容器边界","窗口边界","距离激活","延时激活"})
                require(DesktopTestInput.geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&title.equals(e.text.logicalText)),"参考扩展示例缺失："+title);
            for(int step=0;step<12&&!visibleText(w,"拖动预览");step++){input.point(1000,700);input.scroll.invoke(w.handle(),0,-4);w.frame();}
            require(visibleText(w,"拖动预览")&&visibleText(w,"独立把手"),"更多参考交互无法在真实示例页显露");
            BufferedImage variants=save(w,output.resolve("draggable-variants.png"));
            SceneNode variantSurface=DesktopTestInput.geometry(w).ordered().stream().filter(e -> e.text!=null&&"拾起我".equals(e.text.logicalText))
                    .map(e -> e.node.parent().parent()).filter(n -> same(DesktopTestInput.hit(DesktopTestInput.geometry(w,n)),box(w,n)))
                    .findFirst().orElseThrow(() -> new AssertionError("圆角像素目标未完整显露"));
            roundedPixels(variants,box(w,variantSurface),idle,idleFrame,"variant-idle");
            centeredLabels(w,variants);
            verifyGraphicGrips(w,input);
            System.out.println("DRAGGABLE roundedPixels=true idle=true focus=true drag=true variant=true outsideBackground=true");
            input.point(1000,600);input.scroll.invoke(w.handle(),0,100);w.frame();
            glfwSetWindowSize(w.handle(),600,900);settle(w);
            for(String name:new String[]{"元素 A","元素 B","元素 C"})
                require(DesktopTestInput.geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&name.equals(e.text.logicalText)
                        &&e.box.intersect(e.clip).width==e.box.width),"窄窗示例被裁掉："+name);
            BufferedImage narrow=save(w,output.resolve("draggable-narrow.png"));
            centeredLabels(w,narrow);
            input.click("返回场景概览");input.click("打开拖拽");settle(w);
            require(DesktopTestInput.geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&"Draggable".equals(e.text.logicalText)),"真实目录重入失效");
            System.out.println("DRAGGABLE gallery=true actualGpu=true normalEntry=true");
        }
    }
    // 正式几何与真实墨迹共同验收，不能用字形Y补偿冒充行内居中。
    private static void centeredLabels(DesktopWindow w,BufferedImage image) {
        int measured=0,visible=0,grips=0;
        for(club.heiqi.uilib.ui.scene.layout.SceneGeometryView.Entry entry:DesktopTestInput.geometry(w).ordered()) {
            if(entry.text==null||!"拾起我".equals(entry.text.logicalText))continue;
            LayoutBox outer=box(w,entry.node.parent().parent()),label=entry.box;
            double center=outer.y+outer.height/2.0;
            require(Math.abs(label.y+label.height/2.0-center)<=0.5,"混合字号的标签行盒没有垂直居中");measured++;
            if(!same(DesktopTestInput.hit(entry),label))continue;
            int background=image.getRGB(label.x-2,label.y+1),top=image.getHeight(),bottom=-1;
            for(int y=label.y;y<label.y+label.height;y++)for(int x=label.x;x<label.x+label.width;x++)
                if(colorDistance(image.getRGB(x,y),background)>48){top=Math.min(top,y);bottom=Math.max(bottom,y);}
            require(bottom>=top,"文字居中像素探针未命中实际墨迹");
            require(Math.abs((top+bottom+1)/2.0-center)<=1,"拾起我可见文字偏离组件中心");visible++;
            for(SceneNode sibling:entry.node.parent().children()) if(sibling!=entry.node) {
                club.heiqi.uilib.ui.scene.layout.SceneGeometryView.Entry grip=DesktopTestInput.geometry(w,sibling);
                if(!same(DesktopTestInput.hit(grip),grip.box))continue;
                centeredGrip(image,grip.box,outer);grips++;
            }
        }
        require(measured>0,"未找到混合字号行");
        System.out.println("DRAGGABLE verticalCenter=true labels="+measured+" visibleInk="+visible+" centeredGripInk="+grips);
    }
    private static void centeredGrip(BufferedImage image,LayoutBox grip,LayoutBox surface) {
        int background=image.getRGB(grip.x,grip.y),left=image.getWidth(),right=-1,top=image.getHeight(),bottom=-1;
        for(int y=grip.y;y<grip.y+grip.height;y++)for(int x=grip.x;x<grip.x+grip.width;x++)
            if(colorDistance(image.getRGB(x,y),background)>48) {
                left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
            }
        require(right>=left&&bottom>=top,"把手像素探针未命中点阵");
        require(Math.abs((left+right+1)/2.0-(grip.x+grip.width/2.0))<=0.5,"点阵没有水平居中");
        require(Math.abs((top+bottom+1)/2.0-(surface.y+surface.height/2.0))<=0.5,"把手可见点阵偏离组件垂直中心");
    }
    private static void verifyGraphicGrips(DesktopWindow w,DesktopTestInput input) {
        SceneNode dual=DesktopTestInput.geometry(w).ordered().stream()
                .filter(e -> e.text!=null&&"拾起我".equals(e.text.logicalText)&&e.node.parent().children().size()==3)
                .map(e -> e.node.parent().parent())
                .filter(n -> same(DesktopTestInput.hit(DesktopTestInput.geometry(w,n)),box(w,n)))
                .findFirst().orElseThrow(() -> new AssertionError("双把手验证目标未完整显露"));
        SceneNode body=dual.children().get(0);
        for(SceneNode grip:new SceneNode[]{body.children().get(0),body.children().get(2)}) {
            LayoutBox start=box(w,dual),b=box(w,grip);int x=b.x+b.width/2,y=b.y+b.height/2;
            down(w,input,x,y);move(w,input,x+25,y+16);
            require(box(w,dual).x==start.x+25&&box(w,dual).y==start.y+16,
                    "图形把手未跟随指针 actual="+box(w,dual).x+","+box(w,dual).y+" start="+start.x+","+start.y);
            key(w,input,GLFW_KEY_ESCAPE);up(w,input,x+25,y+16);
            // 焦点显露可滚动祖先；取消要回到当前正式占位，不能拿旧屏幕坐标当槽位。
            require(same(box(w,dual),box(w,dual.parent())),"图形把手取消未回当前槽");
        }
        LayoutBox label=box(w,body.children().get(1));int x=label.x+label.width/2,y=label.y+label.height/2;
        down(w,input,x,y);move(w,input,x+25,y+16);
        require(same(box(w,dual),box(w,dual.parent())),"改用图形把手后正文误起拖");
        up(w,input,x+25,y+16);
        System.out.println("DRAGGABLE graphicGrips=true dualHit=true escapeCancel=true bodyNotActivator=true");
    }
    private static boolean visibleText(DesktopWindow w,String text){return DesktopTestInput.geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&text.equals(e.text.logicalText)&&DesktopTestInput.hit(e).height>0);}
    // LayoutBox是不可变区域但没有值相等实现；这里比较实际共同几何，不能比较对象身份。
    private static boolean same(LayoutBox a,LayoutBox b){return a.x==b.x&&a.y==b.y&&a.width==b.width&&a.height==b.height;}
    private static LayoutBox box(DesktopWindow w,SceneNode n){return DesktopTestInput.geometry(w,n).box;}
    private static boolean focused(DesktopWindow w,SceneNode node){boolean[] a={false};w.domain.execute(() -> a[0]=w.page.runtime().focusRouter().focused().get()==node);return a[0];}
    private static boolean active(DesktopWindow w,SceneDraggable.State state){boolean[] a={false};w.domain.execute(() -> a[0]=state.snapshot().get().active);return a[0];}
    private static void down(DesktopWindow w,DesktopTestInput i,int x,int y){i.point(x,y);i.mouse.invoke(w.handle(),0,GLFW_PRESS,0);w.frame();}
    private static void move(DesktopWindow w,DesktopTestInput i,int x,int y){i.point(x,y);w.frame();}
    private static void up(DesktopWindow w,DesktopTestInput i,int x,int y){i.point(x,y);i.mouse.invoke(w.handle(),0,GLFW_RELEASE,0);w.frame();}
    private static void key(DesktopWindow w,DesktopTestInput i,int key){i.key.invoke(w.handle(),key,0,GLFW_PRESS,0);i.key.invoke(w.handle(),key,0,GLFW_RELEASE,0);w.frame();}
    // 未聚焦卡片是同一视觉样式的健康参照；动态填色不能擦掉外圆弧，也不能填进角外。
    private static void roundedPixels(BufferedImage actual,LayoutBox frame,BufferedImage reference,LayoutBox original,String state) {
        for(int corner=0;corner<4;corner++) {
            boolean right=(corner&1)!=0,bottom=(corner&2)!=0;
            int outside=actual.getRGB(frame.x+(right?frame.width:-1),frame.y+(bottom?frame.height:-1));
            require(colorDistance(cornerPixel(actual,frame,right,bottom,1,1),outside)<=2,state+"圆角外被矩形填色覆盖");
            // 采样共同卡片半径的外圆弧，避免把旧8px轮廓位置误当新12px边框；仍用健康GPU画面互证。
            int diagonal=(int)Math.floor(club.heiqi.uilib.ui.scene.control.SceneAppearance.CARD_RADIUS*(1-Math.sqrt(.5))-.5);
            for(int[] point:new int[][]{{diagonal-1,diagonal+1},{diagonal,diagonal},{diagonal+1,diagonal-1}}) {
                int expected=cornerPixel(reference,original,right,bottom,point[0],point[1]);
                int referenceOutside=reference.getRGB(original.x+(right?original.width:-1),original.y+(bottom?original.height:-1));
                require(colorDistance(expected,referenceOutside)>2,"健康参照缺少可辨认圆弧");
                require(colorDistance(cornerPixel(actual,frame,right,bottom,point[0],point[1]),expected)<=2,state+"圆弧边框断开");
            }
        }
    }
    private static int cornerPixel(BufferedImage image,LayoutBox frame,boolean right,boolean bottom,int x,int y) {
        return image.getRGB(frame.x+(right?frame.width-1-x:x),frame.y+(bottom?frame.height-1-y:y));
    }
    private static int colorDistance(int left,int right) {
        int difference=0;for(int shift:new int[]{0,8,16})difference=Math.max(difference,Math.abs((left>>>shift&255)-(right>>>shift&255)));return difference;
    }
    private static BufferedImage save(DesktopWindow w,Path path) throws Exception {settle(w);w.fonts.beginPresentation(1000000);try{w.replay(w.lastPlan);}finally{w.fonts.endPresentation();}BufferedImage image=DesktopTestReadback.readBack(w);ImageIO.write(image,"png",path.toFile());return image;}
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
