package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.glfwSetWindowSize;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;
import static club.heiqi.uilib.host.desktop.DesktopGalleryEvidence.*;

import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 人工入口装配同一广场；自动入口启动已有统一测试应用。 */
public final class DesktopGallery {
    public static void main(String[] args) throws Exception {
        Path output=args.length>1?Paths.get(args[1]):Paths.get("gallery-report");
        if(args.length>0&&"--verify".equals(args[0])) {
            DesktopGalleryVerification.verify(output,args);
            return;
        }
        boolean smoke=args.length>0&&"--mailbox-smoke".equals(args[0]);
        boolean mailbox=smoke||args.length>0&&"--mailbox".equals(args[0]);
        Files.createDirectories(output);
        try(DesktopWindow window=DesktopWindow.openWithServices(output.resolve("gallery.yaml"),
                services -> createGallery(services,mailbox?TestGallery.Page.MAILBOX:TestGallery.Page.HOME))) {
            if(smoke) {
                glfwSetWindowSize(window.handle(),1400,900);
                settle(window);
                if(window.lastPlan.commands().stream().noneMatch(c -> c.kind==PaintCommand.Kind.TEXT&&"QZ Mail".equals(c.text.logicalText)))
                    throw new AssertionError("独立测试启动器没有进入收件箱");
                saveGeometry(window,output.resolve("mailbox-launcher.geometry.json"));
                screenshot(window,output.resolve("mailbox-launcher.png"),0xfff8fafd);
                System.out.println("MAILBOX_LAUNCHER directEntry=true actualPixels=true sharedGallery=true");
                return;
            }
            while(!window.shouldClose())window.frame();
        }
    }
    static TestGallery createGallery(DesktopPageServices services,TestGallery.Page initial){
        TestGallery gallery=new TestGallery(services::settings,initial);
        services.onCloseRequested(gallery::requestClose);services.onReloadRequested(gallery::requestReload);
        return gallery;
    }
}
