package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;

import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** 宿主证据消费正式几何与真实GPU回放；连续raw采样先于PNG编码。 */
final class DesktopGalleryEvidence {
    private DesktopGalleryEvidence() {}
    static void scrollPage(
            DesktopWindow window,
            DesktopTestInput input,
            int delta) {
        int[] width = {0}, height = {0};
        glfwGetWindowSize(window.handle(), width, height);
        // 页面右侧留白仍属正式滚动根，避开文档中的局部横滚视口。
        input.cursor.invoke(window.handle(), width[0] - 32, height[0] / 2.0);
        input.scroll.invoke(window.handle(), 0, delta);
        window.frame();
    }

    static PaintCommand image(DesktopWindow window) {
        return window.lastPlan.commands().stream()
                .filter(
                        c ->
                                c.kind == PaintCommand.Kind.IMAGE
                                        && c.image.content.width == 16
                                        && c.image.content.height == 16)
                .findFirst()
                .orElse(null);
    }

    static void verifyImage(DesktopWindow window, PaintCommand image) {
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        PaintPlanBuilder builder = new PaintPlanBuilder();
        builder.add(image);
        window.fonts.beginPresentation(1000000);
        try {
            window.replay(builder.build());
        } finally {
            window.fonts.endPresentation();
        }
        glFinish();
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        int x = (int) (image.image.x + image.image.width / 2),
                y = (int) (image.image.y + image.image.height / 2);
        glReadPixels(x, window.height() - 1 - y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        if (glGetError() != GL_NO_ERROR || (pixel.get(2) & 255) < 150)
            throw new AssertionError("PNG纹理没有真实蓝色像素");
    }

    /** 实际GLFW hover序列与GPU像素取证，端点通道范围可捕获透明插值暗闪。 */
    static void navigationMotion(DesktopWindow window,DesktopTestInput input,Path output) throws Exception {
        SceneGeometryView.Entry[] item={null};
        window.domain.execute(() -> item[0]=DesktopTestInput.geometry(window).ordered().stream()
                .filter(e -> e.node.focusable()&&e.node.text().equals("星标邮件")).findFirst().get());
        input.point(1100,40);window.frame();settle(window);
        int x=item[0].box.x+item[0].box.width-20,y=item[0].box.y+item[0].box.height/2;
        int[] pixels=new int[6];long[] elapsed=new long[6];pixels[0]=pixel(window,x,y);
        int[] times={0,20,45,75,180};
        ByteBuffer[] images=new ByteBuffer[times.length];
        for(int i=0;i<images.length;i++)images[i]=ByteBuffer.allocateDirect(window.width()*window.height()*4);
        long start=System.nanoTime();input.point(x,y);
        for(int i=0;i<times.length;i++) {
            long target=start+times[i]*1000000L;
            do {window.frame();}while(System.nanoTime()<target);
            elapsed[i+1]=System.nanoTime()-start;pixels[i+1]=pixel(window,x,y);
            glReadPixels(0,0,window.width(),window.height(),GL_RGBA,GL_UNSIGNED_BYTE,images[i]);
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("过渡帧读取失败");
        }
        java.util.Set<Integer> colors=new java.util.HashSet<>();for(int value:pixels)colors.add(value);
        if(colors.size()<3)throw new AssertionError("没有采到真实过渡中间颜色");
        // PNG编码与文件写入在连续GPU采样之后，避免编码耗时挤掉短过渡的中间帧。
        for(int i=0;i<images.length;i++) {
            BufferedImage image=new BufferedImage(window.width(),window.height(),BufferedImage.TYPE_INT_ARGB);
            for(int py=0;py<window.height();py++)for(int px=0;px<window.width();px++) {
                int at=(py*window.width()+px)*4;ByteBuffer bytes=images[i];
                image.setRGB(px,window.height()-1-py,((bytes.get(at+3)&255)<<24)|((bytes.get(at)&255)<<16)
                        |((bytes.get(at+1)&255)<<8)|(bytes.get(at+2)&255));
            }
            ImageIO.write(image,"png",output.resolve("mailbox-motion-"+times[i]+".png").toFile());
        }
        for(int value:pixels)for(int shift=0;shift<24;shift+=8) {
            int channel=(value>>>shift)&255,a=(pixels[0]>>>shift)&255,b=(pixels[pixels.length-1]>>>shift)&255;
            if(channel<Math.min(a,b)-1||channel>Math.max(a,b)+1)throw new AssertionError("导航过渡中途颜色超出端点范围");
        }
        Files.write(output.resolve("mailbox-motion.json"),("{\"elapsedNanos\":"+java.util.Arrays.toString(elapsed)+",\"argb\":"+java.util.Arrays.toString(pixels)+"}").getBytes("UTF-8"));
        input.point(1100,40);window.frame();
        System.out.println("MAILBOX_MOTION actualGpuPixels=true monotonicTimes=true noDarkFlash=true");
    }
    static int pixel(DesktopWindow window,int x,int y) {
        glFinish();ByteBuffer value=ByteBuffer.allocateDirect(4);glReadPixels(x,window.height()-1-y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,value);
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
                                                && (c.text.originalText==null?c.text.logicalText:c.text.originalText)
                                                        .startsWith(target.getFileName().toString().startsWith("mailbox-")?"QZ Mail":"QZ / 场景工作台"))
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
