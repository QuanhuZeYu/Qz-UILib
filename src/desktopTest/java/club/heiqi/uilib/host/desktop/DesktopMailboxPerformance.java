package club.heiqi.uilib.host.desktop;

import club.heiqi.uilib.testing.gallery.mail.MailboxWorkload;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwSwapInterval;
import static org.lwjgl.opengl.GL11.GL_RENDERER;
import static org.lwjgl.opengl.GL11.GL_VERSION;
import static org.lwjgl.opengl.GL11.glGetString;

/** 完整正式Desktop帧及已安装滚轮回调，不把自动事件冒充人工设备。 */
public final class DesktopMailboxPerformance {
    public static void main(String[] args)throws Exception{
        Path output=Paths.get(args[0]);Files.createDirectories(output);
        int scale=Integer.parseInt(args[1]),warmup=Integer.parseInt(args[2]),frames=Integer.parseInt(args[3]);
        MailboxWorkload page=new MailboxWorkload(scale);int[] sequence={0};
        try(DesktopWindow window=DesktopWindow.open(output.resolve("mailbox.yaml"),page)){
            glfwSwapInterval(0);glfwSetWindowSize(window.handle(),1100,800);
            DesktopTestInput input=new DesktopTestInput(window);
            Files.write(output.resolve("graphics.txt"),("renderer="+glGetString(GL_RENDERER)+"\nversion="+glGetString(GL_VERSION)+"\n").getBytes(StandardCharsets.UTF_8));
            page.run(new MailboxWorkload.Driver(){
                public void frame(){window.frame();}public void scroll(int x,int y,int delta){input.point(x,y);input.scroll.invoke(window.handle(),0,delta);}
                public void resize(int w,int h){glfwSetWindowSize(window.handle(),w,h);}
                public SceneGeometryView geometry(){return DesktopTestInput.geometry(window);}
                public club.heiqi.uilib.ui.scene.paint.PaintPlan paint(){return window.lastPlan;}
                public int width(){return window.width();}public int height(){return window.height();}
            },"desktop-actions+window.frame-poll-clear-swap",System.getProperty("uilib.version","unknown"),warmup,frames,json->{
                try{Files.write(output.resolve("desktop-"+scale+"-"+(sequence[0]++)+".json"),json.getBytes(StandardCharsets.UTF_8));}
                catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
            });
        }
    }
}
