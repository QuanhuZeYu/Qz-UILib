package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.glfwSetWindowSize;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;
import static club.heiqi.uilib.host.desktop.DesktopGalleryEvidence.*;

import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.testing.gallery.GalleryMediaResources;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaService;
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
        boolean smoke=args.length>0&&"--smoke".equals(args[0]);
        if(args.length>0&&!smoke)throw new IllegalArgumentException("使用无参数打开统一广场；有限验收使用 --smoke 或 --verify。");
        Files.createDirectories(output);
        try(GalleryMediaResources media=new GalleryMediaResources(new MediaService(IsolatedMediaAdapters.decoderFactory(),
                        IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT),new club.heiqi.uilib.host.media.bilibili.BilibiliResolver());
                DesktopWindow window=DesktopWindow.openWithServices(output.resolve("gallery.yaml"),media.service(),true,
                services -> createGallery(services,TestGallery.Page.HOME,media))) {
            if(smoke) {
                glfwSetWindowSize(window.handle(),1400,900);
                settle(window);
                if(window.lastPlan.commands().stream().noneMatch(c -> c.kind==PaintCommand.Kind.TEXT&&"QZ / 场景工作台".equals(c.text.logicalText)))
                    throw new AssertionError("统一启动器没有进入广场目录");
                saveGeometry(window,output.resolve("gallery-launcher.geometry.json"));
                screenshot(window,output.resolve("gallery-launcher.png"),0xfffafafa);
                System.out.println("GALLERY_LAUNCHER directory=true actualPixels=true sharedGallery=true");
                return;
            }
            while(!window.shouldClose())window.frame();
        }
    }
    static TestGallery createGallery(DesktopPageServices services,TestGallery.Page initial){
        return createGallery(services,initial,null);
    }
    static TestGallery createGallery(DesktopPageServices services,TestGallery.Page initial,GalleryMediaResources media){
        TestGallery gallery=new TestGallery(media==null?services::settings:media.environment(services.settings(),
                club.heiqi.uilib.testing.host.LocalMediaFiles::resolve,
                club.heiqi.uilib.testing.host.LocalMediaFiles.initialDirectory(),services.images()),initial);
        services.onCloseRequested(gallery::requestClose);services.onReloadRequested(gallery::requestReload);
        return gallery;
    }
}
