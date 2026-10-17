package club.heiqi.uilib.host.desktop;

import static org.lwjgl.opengl.GL11.*;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** 使用现有生产GLFW回调、共同几何与真实GPU帧，采样后才编码PNG。 */
final class DesktopMailboxMotion {
    private DesktopMailboxMotion() {}
    private static SceneGeometryView.Entry action(DesktopWindow window,String name) {
        return DesktopTestInput.geometry(window).ordered().stream()
            .filter(e -> name.equals(e.node.text())&&e.node.clickAction()!=null).findFirst().get();
    }
    private static void runFor(DesktopWindow window,long duration) {
        long end=System.nanoTime()+duration;
        do {window.frame();} while(System.nanoTime()<end);
    }

    static void verify(DesktopWindow window,DesktopTestInput input,Path output) throws Exception {
        input.point(1100,40);runFor(window,220000000L);
        capture(window,input,output,"collapse","折叠导航",false);
        capture(window,input,output,"expand","展开导航",false);
        capture(window,input,output,"reverse","折叠导航",true);
        search(window,input,output);
        receiveShadow(window,input,output);
        navigationHover(window,input,output);
        // 动画尚未结束时按正式当前区域点击，不能只让画面滑走、命中留在原位。
        input.click("折叠导航");runFor(window,35000000L);
        SceneGeometryView.Entry star=action(window,"星标邮件");
        if(star.box.width<=32||star.box.width>=240)throw new AssertionError("未命中导航过渡中间态");
        input.click(star);
        window.domain.execute(() -> {
            if(window.page.runtime().focusRouter().focused().get()!=star.node)
                throw new AssertionError("中间态点击未进入同一个正式动作");
        });
        input.click("展开导航");runFor(window,220000000L);input.click("收件箱");
        input.point(1100,40);runFor(window,220000000L);
        input.click("折叠导航");runFor(window,24000000L);
        input.click("放大文字");runFor(window,200000000L);
        SceneGeometryView.Entry enlarged=action(window,"星标邮件");
        if(enlarged.box.width!=Math.round(enlarged.node.style().logicalEm*32/14f))
            throw new AssertionError("过渡中字号变化使用了过期目标");
        input.click("标准文字");input.click("展开导航");runFor(window,200000000L);
        input.click("折叠导航");runFor(window,24000000L);
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.handle(),720,900);runFor(window,220000000L);
        if(DesktopTestInput.hit(action(window,"收件箱")).width<=0)throw new AssertionError("过渡中窄窗丢失导航动作");
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.handle(),1400,900);runFor(window,220000000L);
        input.click("展开导航");runFor(window,220000000L);
        // 切出结束挂载，返回直接读取当前目标，不能复活旧过渡。
        input.click("折叠导航");input.click("场景概览");input.click("打开收件箱");runFor(window,220000000L);
        if(action(window,"星标邮件").box.width!=32)throw new AssertionError("重新进入邮箱没有直接消费保留的折叠目标");
        input.click("展开导航");runFor(window,220000000L);
        input.point(1100,40);runFor(window,220000000L);
        PaintPlan plan=window.lastPlan;SceneGeometryView geometry=DesktopTestInput.geometry(window);
        window.frame();window.frame();
        if(window.lastPlan!=plan||DesktopTestInput.geometry(window)!=geometry)
            throw new AssertionError("导航动效结束后没有恢复静态呈现复用");
        System.out.println("MAILBOX_GEOMETRY_MOTION actualGpuFrames=true reverse=true currentHit=true settledReuse=true");
    }

    private static void focusAction(DesktopWindow window,DesktopTestInput input,String name) {
        SceneNode target=action(window,name).node;
        int maximum=DesktopTestInput.geometry(window).ordered().size()+1;
        for(int i=0;i<maximum;i++) {
            SceneNode[] focused={null};
            window.domain.execute(() -> focused[0]=window.page.runtime().focusRouter().focused().get());
            if(focused[0]==target)return;
            input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_TAB,0,org.lwjgl.glfw.GLFW.GLFW_PRESS,org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT);
            input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_TAB,0,org.lwjgl.glfw.GLFW.GLFW_RELEASE,org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT);
            window.frame();
        }
        throw new AssertionError("Tab未到达正式返回动作");
    }
    private static void activateKey(DesktopWindow window,DesktopTestInput input) {
        input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,org.lwjgl.glfw.GLFW.GLFW_PRESS,0);
        input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,org.lwjgl.glfw.GLFW.GLFW_RELEASE,0);
        window.frame();
    }
    private static void navigationHover(DesktopWindow window,DesktopTestInput input,Path output) throws Exception {
        input.click("收件箱");input.point(1100,40);runFor(window,200000000L);
        SceneGeometryView.Entry inbox=action(window,"收件箱");
        int background=presentedPixel(window,0,inbox.box.y);
        int center=presentedPixel(window,0,inbox.box.y+inbox.box.height/2);
        if(background!=center)throw new AssertionError("展开导航左角没有保持平边：top="+Integer.toHexString(background)+",center="+Integer.toHexString(center)+",box="+inbox.box.x+","+inbox.box.y+","+inbox.box.width);
        input.click("折叠导航");input.point(1100,40);runFor(window,200000000L);
        SceneGeometryView.Entry collapsed=action(window,"收件箱");
        SceneNode nav=collapsed.node.parent().parent();
        int rowY=collapsed.box.y+collapsed.box.height/2;
        input.cursor.invoke(window.handle(),36,rowY);window.frame();runFor(window,180000000L);
        if(action(window,"星标邮件").box.width!=32)throw new AssertionError("短暂停留未等到期限就展开");
        input.point(1100,40);runFor(window,260000000L);
        if(action(window,"星标邮件").box.width!=32)throw new AssertionError("离开后仍执行过期悬停展开");
        // 指针仍在导航内时改变视口，旧等待须失效，不能从resize前已累计的停留提前展开。
        input.cursor.invoke(window.handle(),36,rowY);window.frame();runFor(window,200000000L);
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.handle(),1380,900);window.frame();runFor(window,220000000L);
        if(action(window,"星标邮件").box.width!=32)throw new AssertionError("窗口变化没有结束旧悬停等待");
        input.point(1100,40);org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.handle(),1400,900);runFor(window,220000000L);
        int captureWidth=280,captureHeight=272,captureY=64;
        List<ByteBuffer> images=new ArrayList<>();
        for(int i=0;i<256;i++)images.add(org.lwjgl.system.MemoryUtil.memAlloc(captureWidth*captureHeight*4));
        input.cursor.invoke(window.handle(),36,rowY);window.frame();
        long start=System.nanoTime();int frames=0;boolean intermediate=false;
        int workspaceX=-1;
        StringBuilder facts=new StringBuilder("{\"readBuffer\":\"GL_FRONT\",\"region\":[0,64,280,272],\"frames\":[");
        try {
        do {
            if(frames==images.size())throw new AssertionError("悬停逐帧采样容量不足");
            long frameStart=System.nanoTime();window.frame();long presented=System.nanoTime();long elapsed=presented-start;
            SceneGeometryView geometry=DesktopTestInput.geometry(window);
            SceneGeometryView.Entry entry=action(window,"星标邮件");
            SceneGeometryView.Entry currentNav=geometry.get(nav);
            SceneNode body=nav.parent();SceneNode workspace=body.children().get(1);
            int x=geometry.get(workspace).box.x;
            if(workspaceX<0)workspaceX=x;
            if(x!=workspaceX)throw new AssertionError("悬停展开推动了工作区");
            if(currentNav.box.width>72&&currentNav.box.width<256)intermediate=true;
            int previous=glGetInteger(GL_READ_BUFFER);
            try {
                glReadBuffer(GL_FRONT);
                glReadPixels(0,window.height()-captureY-captureHeight,captureWidth,captureHeight,GL_RGBA,GL_UNSIGNED_BYTE,images.get(frames));
            } finally {glReadBuffer(previous);}
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("悬停逐呈现帧GPU读取失败");
            if(frames>0)facts.append(',');facts.append("{\"frame\":").append(frames)
                .append(",\"frameStartNanos\":").append(frameStart-start).append(",\"presentedNanos\":").append(elapsed)
                .append(",\"sampledNanos\":").append(System.nanoTime()-start).append(",\"elapsedNanos\":").append(elapsed)
                .append(",\"navigationWidth\":").append(currentNav.box.width)
                .append(",\"entryWidth\":").append(entry.box.width).append(",\"workspaceX\":").append(x).append('}');
            frames++;
        }while(System.nanoTime()-start<650000000L);
        Path frameOutput=output.resolve("mailbox-hover-frames");Files.createDirectories(frameOutput);
        for(int i=0;i<frames;i++) {
            ByteBuffer bytes=images.get(i);BufferedImage image=new BufferedImage(captureWidth,captureHeight,BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<captureHeight;y++)for(int x=0;x<captureWidth;x++) {
                int at=(y*captureWidth+x)*4;
                image.setRGB(x,captureHeight-1-y,((bytes.get(at+3)&255)<<24)|((bytes.get(at)&255)<<16)
                        |((bytes.get(at+1)&255)<<8)|(bytes.get(at+2)&255));
            }
            ImageIO.write(image,"png",frameOutput.resolve(String.format(java.util.Locale.ROOT,"frame-%04d.png",i)).toFile());
        }
        } finally {for(ByteBuffer image:images)org.lwjgl.system.MemoryUtil.memFree(image);}
        if(!intermediate||action(window,"星标邮件").box.width!=240)
            throw new AssertionError("持续悬停未沿真实中间态展开");
        DesktopGalleryEvidence.screenshot(window,output.resolve("mailbox-hover-open.png"),new club.heiqi.uilib.ui.scene.control.SceneAppearance.Palette(false).canvas);
        SceneGeometryView beforeScroll=DesktopTestInput.geometry(window);
        java.util.Map<SceneNode,Integer> scrolls=new java.util.IdentityHashMap<>();
        for(SceneGeometryView.Entry e:beforeScroll.ordered())if(e.node.scrollable())scrolls.put(e.node,e.scrollOffset);
        input.cursor.invoke(window.handle(),150,action(window,"星标邮件").box.y+16);
        input.scroll.invoke(window.handle(),0,-3);window.frame();
        for(java.util.Map.Entry<SceneNode,Integer> e:scrolls.entrySet())
            if(DesktopTestInput.geometry(window).get(e.getKey()).scrollOffset!=e.getValue())
                throw new AssertionError("导航覆盖区滚轮穿透并滚动了后台列表");
        // 已展开的覆盖区内文字位于原工作区上方，合法完整点击只能激活导航。
        SceneGeometryView.Entry stars=action(window,"星标邮件");
        input.enqueueAt(stars.box.x+150,stars.box.y+stars.box.height/2);window.frame();
        window.domain.execute(() -> {
            if(window.page.runtime().focusRouter().focused().get()!=stars.node)
                throw new AssertionError("覆盖区点击穿透了导航");
        });
        input.point(1100,40);runFor(window,220000000L);
        if(action(window,"星标邮件").box.width!=32)throw new AssertionError("离开没有收回临时展开");
        facts.append("]}");Files.write(output.resolve("mailbox-hover-motion.json"),facts.toString().getBytes("UTF-8"));
        // 等待中切出，再进入须重新等待；展开中切出不能保留临时覆盖状态。
        focusAction(window,input,"场景概览");
        input.cursor.invoke(window.handle(),36,rowY);window.frame();runFor(window,120000000L);
        activateKey(window,input);input.click("打开收件箱");input.point(1100,40);runFor(window,420000000L);
        if(action(window,"星标邮件").box.width!=32)throw new AssertionError("重挂复活了离开前的悬停等待");
        focusAction(window,input,"场景概览");
        input.cursor.invoke(window.handle(),36,rowY);window.frame();runFor(window,540000000L);
        if(action(window,"星标邮件").box.width!=240)throw new AssertionError("重挂后悬停声明没有重新激活");
        activateKey(window,input);input.click("打开收件箱");input.point(1100,40);runFor(window,420000000L);
        if(action(window,"星标邮件").box.width!=32)throw new AssertionError("重挂保留了旧临时展开呈现");
        input.click("展开导航");runFor(window,200000000L);input.click("收件箱");input.point(1100,40);runFor(window,200000000L);
        System.out.println("MAILBOX_HOVER delay=true cancellation=true overlayWithoutReflow=true currentHit=true scrollBlocked=true reverse=true remount=true actualGpuFrames=true frames="+frames);
    }

    private static int presentedPixel(DesktopWindow window,int x,int y) {
        return DesktopGalleryEvidence.pixel(window,x,y);
    }

    private static void receiveShadow(DesktopWindow window,DesktopTestInput input,Path output) throws Exception {
        input.point(1100,40);runFor(window,180000000L);
        SceneGeometryView.Entry receive=action(window,"接收示例邮件");
        int x=receive.box.x+receive.box.width/2,y=receive.box.y+receive.box.height;
        int before=presentedPixel(window,x,y);
        input.moveTo(receive.node);window.frame();
        List<Integer> pixels=new ArrayList<>();
        long start=System.nanoTime();StringBuilder facts=new StringBuilder("{\"frames\":[");
        do {
            window.frame();
            int pixel=presentedPixel(window,x,y);pixels.add(pixel);
            if(pixels.size()>1)facts.append(',');facts.append("{\"elapsedNanos\":").append(System.nanoTime()-start)
                .append(",\"outsideButtonArgb\":").append(pixel).append('}');
        }while(System.nanoTime()-start<200000000L);
        int end=pixels.get(pixels.size()-1);
        facts.append("]}");Files.write(output.resolve("mailbox-receive-shadow.json"),facts.toString().getBytes("UTF-8"));
        if(before==end)throw new AssertionError("主动作hover阴影被裁掉，按钮外GPU像素没有变化");
        if(pixels.stream().noneMatch(p -> p!=before&&p!=end))throw new AssertionError("主动作阴影没有真实中间像素");
        input.point(1100,40);runFor(window,180000000L);
        if(presentedPixel(window,x,y)!=before)throw new AssertionError("主动作离开后阴影没有恢复");
        System.out.println("MAILBOX_RECEIVE_SHADOW outsideButtonPixels=true intermediate=true leaveRestored=true");
    }

    private static void search(DesktopWindow window,DesktopTestInput input,Path output) throws Exception {
        input.enqueueAt(1100,40);window.frame();runFor(window,180000000L);
        SceneGeometryView.Entry field=DesktopTestInput.geometry(window).ordered().stream()
            .filter(e -> e.node.control()!=null&&e.node.control().acceptsTextInput(e.node)).findFirst().get();
        SceneNode surface=field.node.parent();
        LayoutBox box=DesktopTestInput.geometry(window,surface).box;
        int x=box.x+box.width-28,y=box.y+box.height/2;
        int before=presentedPixel(window,x,y);
        input.enqueueAt(field.box.x+10,field.box.y+field.box.height/2);window.frame();
        SceneGeometryView geometry=DesktopTestInput.geometry(window);
        List<Integer> pixels=new ArrayList<>();
        StringBuilder facts=new StringBuilder("{\"frames\":[");long start=System.nanoTime();
        do {
            window.frame();
            if(DesktopTestInput.geometry(window)!=geometry)throw new AssertionError("搜索背景过渡重新布局");
            int pixel=presentedPixel(window,x,y);pixels.add(pixel);
            if(pixels.size()>1)facts.append(',');facts.append("{\"elapsedNanos\":").append(System.nanoTime()-start)
                .append(",\"argb\":").append(pixel).append('}');
        }while(System.nanoTime()-start<200000000L);
        int end=pixels.get(pixels.size()-1);
        if(pixels.stream().noneMatch(p -> p!=before&&p!=end))throw new AssertionError("搜索真实GPU缺少中间颜色");
        facts.append("]}");Files.write(output.resolve("mailbox-search-motion.json"),facts.toString().getBytes("UTF-8"));
        input.enqueueAt(1100,40);window.frame();runFor(window,180000000L);
        System.out.println("MAILBOX_SEARCH_MOTION actualGpuPixels=true intermediateColor=true geometryUnchanged=true");
    }

    private static void capture(DesktopWindow window,DesktopTestInput input,Path output,
                                String name,String trigger,boolean reverse) throws Exception {
        // 以实际呈现帧为采样单位；ROI保留原生像素，减少读回对帧频的扰动。
        int captureWidth=280,captureHeight=272,captureY=64,capacity=128;
        List<ByteBuffer> buffers=new ArrayList<>();List<Integer> widths=new ArrayList<>();
        for(int i=0;i<capacity;i++)buffers.add(org.lwjgl.system.MemoryUtil.memAlloc(captureWidth*captureHeight*4));
        StringBuilder facts=new StringBuilder("{\"readBuffer\":\"GL_FRONT\",\"framebufferWidth\":")
            .append(window.width()).append(",\"framebufferHeight\":").append(window.height())
            .append(",\"region\":[0,").append(captureY).append(',').append(captureWidth).append(',').append(captureHeight)
            .append("],\"frames\":[");
        int count=0;boolean reversed=false;long start=0;
        try {
            input.enqueueClick(trigger);start=System.nanoTime();
            do {
                if(count==capacity)throw new AssertionError("逐帧采样容量耗尽");
                long frameStart=System.nanoTime();window.frame();long presented=System.nanoTime();
                SceneGeometryView geometry=DesktopTestInput.geometry(window);
                SceneGeometryView.Entry entry=action(window,"星标邮件"),receive=action(window,"接收示例邮件");
                SceneGeometryView.Entry caption=geometry.ordered().stream()
                    .filter(e -> e.node!=entry.node&&e.node.text().equals("星标邮件")).findFirst().orElse(null);
                SceneGeometryView.Entry icon=geometry.get(entry.node.children().get(1));
                widths.add(entry.box.width);LayoutBox visible=DesktopTestInput.hit(entry);
                if(visible.width<=0||visible.height<=0)throw new AssertionError("过渡丢失导航命中区域");
                int previous=glGetInteger(GL_READ_BUFFER);
                try {
                    // window.frame已swap，BACK可能是上一帧；FRONT才对应刚实际呈现的计划。
                    glReadBuffer(GL_FRONT);
                    glReadPixels(0,window.height()-captureY-captureHeight,captureWidth,captureHeight,
                        GL_RGBA,GL_UNSIGNED_BYTE,buffers.get(count));
                } finally {glReadBuffer(previous);}
                if(glGetError()!=GL_NO_ERROR)throw new AssertionError("逐呈现帧GPU读取失败");
                long sampled=System.nanoTime();
                if(count>0)facts.append(',');
                facts.append("{\"frame\":").append(count).append(",\"frameStartNanos\":").append(frameStart-start)
                    .append(",\"presentedNanos\":").append(presented-start).append(",\"sampledNanos\":").append(sampled-start)
                    .append(",\"entryX\":").append(entry.box.x).append(",\"entryWidth\":").append(entry.box.width)
                    .append(",\"receiveWidth\":").append(receive.box.width).append(",\"clipWidth\":").append(visible.width)
                    .append(",\"captionAlpha\":").append(caption==null?0:caption.node.style().color>>>24)
                    .append(",\"iconCenterX\":").append(icon.box.x+icon.box.width/2.0).append('}');
                count++;
                if(reverse&&!reversed&&presented-start>=48000000L) {input.enqueueClick("展开导航");reversed=true;}
            }while(System.nanoTime()-start<280000000L);
            int minimum=java.util.Collections.min(widths),maximum=java.util.Collections.max(widths);
            if(widths.stream().noneMatch(w -> w>minimum&&w<maximum))throw new AssertionError("没有导航真实中间帧："+name+widths);
            int expected=name.equals("collapse")?32:240;
            if(widths.get(widths.size()-1)!=expected)throw new AssertionError("导航端点错误："+name+widths);
            facts.append("]}");Files.write(output.resolve("mailbox-"+name+"-dense.json"),facts.toString().getBytes("UTF-8"));
            Path frames=output.resolve("mailbox-"+name+"-frames");Files.createDirectories(frames);
            for(int i=0;i<count;i++) {
                ByteBuffer bytes=buffers.get(i);BufferedImage image=new BufferedImage(captureWidth,captureHeight,BufferedImage.TYPE_INT_ARGB);
                for(int y=0;y<captureHeight;y++)for(int x=0;x<captureWidth;x++) {
                    int at=(y*captureWidth+x)*4;
                    image.setRGB(x,captureHeight-1-y,((bytes.get(at+3)&255)<<24)|((bytes.get(at)&255)<<16)
                        |((bytes.get(at+1)&255)<<8)|(bytes.get(at+2)&255));
                }
                ImageIO.write(image,"png",frames.resolve(String.format(java.util.Locale.ROOT,"frame-%04d.png",i)).toFile());
            }
            System.out.println("MAILBOX_DENSE_CAPTURE direction="+name+" frames="+count+" everyPresentedFrame=true readBuffer=FRONT");
        } finally {for(ByteBuffer buffer:buffers)org.lwjgl.system.MemoryUtil.memFree(buffer);}
        input.point(1100,40);runFor(window,220000000L);
    }
}
