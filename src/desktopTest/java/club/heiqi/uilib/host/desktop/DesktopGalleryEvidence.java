package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;

import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** 宿主证据消费正式几何与真实GPU回放；连续raw采样先于PNG编码。 */
final class DesktopGalleryEvidence {
    private DesktopGalleryEvidence() {}
    /** 实际GLFW hover序列与GPU像素取证，端点通道范围可捕获透明插值暗闪。 */
    static void navigationMotion(DesktopWindow window,DesktopTestInput input,Path output) throws Exception {
        SceneGeometryView.Entry[] item={null};
        window.domain.execute(() -> item[0]=DesktopTestInput.geometry(window).ordered().stream()
                .filter(e -> e.node.focusable()&&e.node.text().equals("星标邮件")).findFirst().get());
        input.point(1100,40);window.frame();settle(window);
        int x=item[0].box.x+item[0].box.width-20,y=item[0].box.y+item[0].box.height/2;
        java.util.List<Integer> pixels=new java.util.ArrayList<>();java.util.List<Long> elapsed=new java.util.ArrayList<>();
        pixels.add(pixel(window,x,y));elapsed.add(0L);input.point(x,y);long start=System.nanoTime();
        do {window.frame();pixels.add(pixel(window,x,y));elapsed.add(System.nanoTime()-start);}
        while(System.nanoTime()-start<220000000L);
        java.util.Set<Integer> colors=new java.util.HashSet<>(pixels);
        if(colors.size()<3)throw new AssertionError("没有采到真实过渡中间颜色");
        for(int value:pixels)for(int shift=0;shift<24;shift+=8) {
            int channel=(value>>>shift)&255,a=(pixels.get(0)>>>shift)&255,b=(pixels.get(pixels.size()-1)>>>shift)&255;
            if(channel<Math.min(a,b)-1||channel>Math.max(a,b)+1)throw new AssertionError("导航过渡中途颜色超出端点范围");
        }
        Files.write(output.resolve("mailbox-motion.json"),("{\"elapsedNanos\":"+elapsed+",\"argb\":"+pixels+",\"readBuffer\":\"GL_FRONT\",\"everyPresentedFrame\":true}").getBytes("UTF-8"));
        input.point(1100,40);window.frame();
        System.out.println("MAILBOX_MOTION actualGpuPixels=true monotonicTimes=true noDarkFlash=true everyPresentedFrame=true frames="+pixels.size());
    }
    static int pixel(DesktopWindow window,int x,int y) {
        glFinish();ByteBuffer value=ByteBuffer.allocateDirect(4);int previous=glGetInteger(GL_READ_BUFFER);
        try {glReadBuffer(GL_FRONT);glReadPixels(x,window.height()-1-y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,value);}
        finally {glReadBuffer(previous);}
        if(glGetError()!=GL_NO_ERROR)throw new AssertionError("过渡像素读取失败");
        return 0xff000000|((value.get(0)&255)<<16)|((value.get(1)&255)<<8)|(value.get(2)&255);
    }

    /** 证据只读正式几何与实际绘制，不为页面增加定位标记或布局接缝。 */
    static void saveGeometry(DesktopWindow window, Path target) throws Exception {
        StringBuilder out=new StringBuilder();
        window.domain.execute(() -> {
            out.append("{\"width\":").append(window.width()).append(",\"height\":").append(window.height())
                    .append(",\"paintCommands\":").append(window.lastPlan.commands().size()).append(",\"entries\":[");
            boolean first=true;
            for(SceneGeometryView.Entry e:DesktopTestInput.geometry(window).ordered()) {
                if(!first)out.append(',');first=false;
                out.append("{\"id\":").append(quote(e.node.id().toString())).append(",\"name\":").append(quote(e.node.text()))
                        .append(",\"text\":").append(quote(e.text==null?"":e.text.logicalText))
                        .append(",\"focusable\":").append(e.node.focusable())
                        .append(",\"control\":").append(quote(e.node.control()==null?"":e.node.control().getClass().getSimpleName()))
                        .append(",\"parent\":").append(quote(e.node.parent()==null?"":e.node.parent().id().toString()))
                        .append(",\"x\":").append(e.box.x).append(",\"y\":").append(e.box.y)
                        .append(",\"width\":").append(e.box.width).append(",\"height\":").append(e.box.height)
                        .append(",\"em\":").append(e.node.style().logicalEm);
                if(e.clip!=null)out.append(",\"clip\":[").append(e.clip.x).append(',').append(e.clip.y)
                        .append(',').append(e.clip.width).append(',').append(e.clip.height).append(']');
                out.append('}');
            }
            out.append("]}");
        });
        Files.write(target,out.toString().getBytes("UTF-8"));
    }
    static String quote(String value) {
        StringBuilder out=new StringBuilder("\"");
        for(int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='\\'||c=='\"')out.append('\\').append(c);
            else if(c<' ')out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }

    static void screenshot(DesktopWindow window, Path target, int background)
            throws Exception {
        window.fonts.beginPresentation(1000000);
        try {
            window.replay(window.lastPlan);
        } finally {
            window.fonts.endPresentation();
        }
        BufferedImage output = DesktopTestReadback.readBack(window);
        int error=glGetError(),actualBackground=output.getRGB(window.width()-4,window.height()-4);
        if(error!=GL_NO_ERROR||actualBackground!=background) {
            ImageIO.write(output,"png",target.resolveSibling(target.getFileName()+"-failed.png").toFile());
            throw new AssertionError("页面背景与预期不符: "+target.getFileName()+" expected="+Integer.toHexString(background)
                    +" actual="+Integer.toHexString(actualBackground)+" glError="+error);
        }
        PaintCommand title =
                window.lastPlan.commands().stream()
                        .filter(
                                c ->
                                        c.kind == PaintCommand.Kind.TEXT
                                                && (c.text.logicalText.equals("QZ Mail")
                                                        ||c.text.logicalText.equals("QZ / 场景工作台")
                                                        ||c.text.logicalText.equals("QZ  /  UILib 设置")))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("广场标题缺失"));
        int ink = 0;
        for (int y = Math.max(0, title.box.y);
                y < Math.min(window.height(), title.box.y + title.box.height);
                y++)
            for (int x = Math.max(0, title.box.x);
                    x < Math.min(window.width(), title.box.x + title.box.width);
                    x++) if (output.getRGB(x, y) != background) ink++;
        if (ink == 0) throw new AssertionError("广场标题没有真实GL字形像素");
        ImageIO.write(output, "png", target.toFile());
    }
}
