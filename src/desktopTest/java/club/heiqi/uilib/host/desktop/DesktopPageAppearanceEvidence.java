package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.glfwSetWindowSize;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.testing.gallery.PerformanceGallery;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/** 沿实际窗口逐页取证；只消费正式几何/配置与交换后画面，不为产品插入探针。 */
final class DesktopPageAppearanceEvidence {
    private DesktopPageAppearanceEvidence(){}
    static void captureAll(Path output)throws Exception{
        Files.createDirectories(output);List<String> evidence=new ArrayList<>();
        for(TestGallery.Page page:TestGallery.Page.values()){
            try(DesktopWindow window=DesktopWindow.openWithServices(output.resolve(page.name()+".yaml"),false,
                    services->DesktopGallery.createGallery(services,page))){capture(window,page.name().toLowerCase(java.util.Locale.ROOT),output,evidence);}
        }
        PerformanceGallery performance=new PerformanceGallery("Desktop","actual-frame","visual",report->{});
        try(DesktopWindow window=DesktopWindow.openWithServices(output.resolve("performance.yaml"),false,services->performance)){
            capture(window,"performance",output,evidence);
        }
        Files.write(output.resolve("pages.json"),("["+String.join(",",evidence)+"]\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        System.out.println("PAGE_APPEARANCE screenshots="+evidence.size()+" allExistingPages=true sharedDeclarations=true lightDark=true wideNarrow=true readBuffer=GL_FRONT");
    }
    private static void capture(DesktopWindow window,String page,Path output,List<String> evidence)throws Exception{
        for(ThemeMode mode:ThemeMode.values()){
            window.domain.execute(()->window.configuration.set(ThemeConfiguration.KEY,mode));
            for(int width:new int[]{1400,640}){
                glfwSetWindowSize(window.handle(),width,900);DesktopTestFrames.settle(window);
                DesktopTestInput input=new DesktopTestInput(window);input.point(window.width()-8,window.height()-8);
                // 主题改变沿已有有限过渡生效，实际推进到稳定画面后再取证，避免把中间色当深色终态。
                long settled=System.nanoTime()+350_000_000L;while(System.nanoTime()<settled)window.frame();
                SceneGeometryView geometry=DesktopTestInput.geometry(window);
                if(window.lastPlan.commands().isEmpty())throw new AssertionError("页面没有真实绘制内容："+page);
                for(SceneGeometryView.Entry e:geometry.ordered())if(e.box.width<0||e.box.height<0)
                    throw new AssertionError("页面布局尺寸非法："+page+" "+e.node.text());
                String name=page+"-"+mode.name().toLowerCase(java.util.Locale.ROOT)+"-"+width;
                ImageIO.write(DesktopTestReadback.readFront(window),"png",output.resolve(name+".png").toFile());
                DesktopGalleryEvidence.saveGeometry(window,output.resolve(name+".geometry.json"));
                evidence.add("{\"page\":\""+page+"\",\"theme\":\""+mode.name()+"\",\"width\":"+window.width()+",\"height\":"+window.height()+",\"image\":\""+name+".png\"}");
            }
        }
    }
}
