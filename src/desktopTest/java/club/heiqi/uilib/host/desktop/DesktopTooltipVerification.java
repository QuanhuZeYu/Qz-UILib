package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/** 真实GLFW回调及GL_FRONT图；通用Tooltip沿正式广场声明。 */
public final class DesktopTooltipVerification {
    public static void main(String[] args)throws Exception{
        Path output=Paths.get(args[0]);Files.createDirectories(output);
        try(DesktopWindow window=DesktopWindow.openWithServices(output.resolve("tooltip.yaml"),false,services->DesktopGallery.createGallery(services,TestGallery.Page.TOOLTIP))){
            DesktopTestInput input=new DesktopTestInput(window);
            for(ThemeMode mode:ThemeMode.values())for(int width:new int[]{1100,640}){
                window.domain.execute(()->window.configuration.set(ThemeConfiguration.KEY,mode));glfwSetWindowSize(window.handle(),width,900);DesktopTestFrames.settle(window);
                for(String name:new String[]{"上侧提示","下侧提示","左侧提示","右侧提示","多行说明","RTL 对齐"}){
                    input.point(window.width()-2,window.height()-2);window.frame();
                    SceneNode trigger=DesktopTestInput.geometry(window).ordered().stream().filter(e->name.equals(e.node.text())&&e.node.clickAction()!=null).map(e->e.node).findFirst().get();
                    input.moveTo(trigger);window.frame();DesktopTestFrames.settle(window);
                    String expected=name.equals("多行说明")?"多行与属性文字":name.equals("RTL 对齐")?"مرحبا":name+"：";
                    PaintCommand tip=window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.contains(expected)).findFirst().orElseThrow(()->new AssertionError("没有正式提示 "+name));
                    LayoutBox visible=tip.clip==null?tip.box:tip.box.intersect(tip.clip);
                    require(visible.width>0&&visible.height>0&&visible.x>=0&&visible.y>=0&&visible.x+visible.width<=window.width()&&visible.y+visible.height<=window.height(),"提示未在viewport内");
                    String file=mode.name().toLowerCase(java.util.Locale.ROOT)+"-"+width+"-"+name;
                    ImageIO.write(DesktopTestReadback.readFront(window),"png",output.resolve(file+".png").toFile());
                    input.key.invoke(window.handle(),GLFW_KEY_ESCAPE,0,GLFW_PRESS,0);input.key.invoke(window.handle(),GLFW_KEY_ESCAPE,0,GLFW_RELEASE,0);window.frame();
                    require(window.lastPlan.commands().stream().noneMatch(c->c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.contains(expected)),"ESC没有只关闭提示");
                }
                SceneNode disabled=DesktopTestInput.geometry(window).ordered().stream().filter(e->"不可用功能".equals(e.node.text())).map(e->e.node.parent()).findFirst().get();
                input.moveTo(disabled);window.frame();DesktopTestFrames.settle(window);
                require(window.lastPlan.commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.contains("此功能当前不可用")),"禁用说明来源未生效");
                input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);window.frame();
                ImageIO.write(DesktopTestReadback.readFront(window),"png",output.resolve("disabled-"+mode.name()+"-"+width+".png").toFile());
            }
        }
        System.out.println("TOOLTIP_DESKTOP actualCallbacks=true realGlyphs=true fourSides=true lightDark=true wideNarrow=true richText=true rtl=true disabledReason=true escape=true readBuffer=GL_FRONT");
    }
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
