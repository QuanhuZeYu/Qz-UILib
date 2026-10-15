package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;
import static club.heiqi.uilib.host.desktop.DesktopGalleryEvidence.*;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.GalleryJourney;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import java.nio.file.Files;
import java.nio.file.Path;

/** 自动验收沿统一应用管理场景与窗口清理；宿主只递交已安装的GLFW回调。 */
final class DesktopGalleryVerification extends TestApplication {
    private final Path output;
    private final Path configuration;
    private DesktopWindow window;
    private DesktopTestInput input;

    DesktopGalleryVerification(Path output) {
        this.output=output;
        configuration=output.resolve("gallery.yaml");
    }

    static void verify(Path output, String[] args) throws Exception {
        DesktopGalleryResult result=DesktopGalleryResult.begin(output);
        new DesktopGalleryVerification(output).run(args);
        // run返回时所有场景及窗口清理已经成功；失败路径不执行结果写入。
        result.passed();
    }

    @Override protected void setup() throws Exception {
        Files.deleteIfExists(configuration);
        window=own(DesktopWindow.openWithImages(configuration, images -> new TestGallery(() -> images)));
        glfwSetWindowSize(window.handle(),1400,1100);
        window.frame();
        input=new DesktopTestInput(window);
    }

    @Override protected void runTests() throws Exception {
        scenario("共享广场旅程与邮箱真实像素和过渡帧",this::sharedJourney);
        scenario("本地图像真实像素与资源换代",this::imageReload);
        scenario("主题发布不重建页面也不隐式写盘",this::themePublication);
        scenario("宽窄空间的内容与数学页面取证",this::pageEvidence);
        System.out.println("GALLERY host=desktop sharedDeclaration=true sharedJourney=true imagePixels=true resourceReload=true renderer="
                +glGetString(GL_RENDERER));
    }

    private void sharedJourney() {
        GalleryJourney.run(
                new GalleryJourney.Driver() {
                    public boolean resize(int width,int height) { glfwSetWindowSize(window.handle(),width,height);return true; }
                    public void frame() {
                        window.frame();
                    }

                    public club.heiqi.uilib.ui.scene.node.SceneNode focused() {
                        final club.heiqi.uilib.ui.scene.node.SceneNode[] value = {null};
                        window.domain.execute(() -> value[0] = window.page.runtime().focusRouter().focused().get());
                        return value[0];
                    }

                    public PaintPlan paint() {
                        return window.lastPlan;
                    }

                    public SceneGeometryView geometry() {
                        return DesktopTestInput.geometry(window);
                    }

                    public void click(int x, int y) {
                        input.enqueueAt(x, y);
                    }

                    public void scroll(int x, int y, int delta) {
                        input.point(x, y);
                        input.scroll.invoke(window.handle(), 0, delta);
                    }

                    public void command(club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command command, boolean shift) {
                        int code, mods = shift ? GLFW_MOD_SHIFT : 0;
                        switch (command) {
                            case UNDO: code = GLFW_KEY_Z; mods |= GLFW_MOD_CONTROL; break;
                            case REDO: code = GLFW_KEY_Y; mods |= GLFW_MOD_CONTROL; break;
                            case SUBMIT: code = GLFW_KEY_ENTER; mods |= GLFW_MOD_CONTROL; break;
                            case CONFIRM: code = GLFW_KEY_ENTER; break;
                            case TAB: code = GLFW_KEY_TAB; break;
                            case DOCUMENT_HOME: code = GLFW_KEY_HOME; mods |= GLFW_MOD_CONTROL; break;
                            case DOCUMENT_END: code = GLFW_KEY_END; mods |= GLFW_MOD_CONTROL; break;
                            default: throw new IllegalArgumentException("旅程未声明宿主键：" + command);
                        }
                        input.key.invoke(window.handle(), code, 0, GLFW_PRESS, mods);
                        input.key.invoke(window.handle(), code, 0, GLFW_RELEASE, mods);
                    }
                    public boolean pointer(club.heiqi.uilib.ui.scene.input.ScenePointerEvent.Kind kind,int x,int y) {
                        input.point(x,y);
                        if(kind==club.heiqi.uilib.ui.scene.input.ScenePointerEvent.Kind.DOWN) input.mouse.invoke(window.handle(),GLFW_MOUSE_BUTTON_LEFT,GLFW_PRESS,0);
                        if(kind==club.heiqi.uilib.ui.scene.input.ScenePointerEvent.Kind.UP) input.mouse.invoke(window.handle(),GLFW_MOUSE_BUTTON_LEFT,GLFW_RELEASE,0);
                        return true;
                    }
                    public void escape() {
                        input.key.invoke(window.handle(), GLFW_KEY_ESCAPE, 0, GLFW_PRESS, 0);
                        input.key.invoke(window.handle(), GLFW_KEY_ESCAPE, 0, GLFW_RELEASE, 0);
                    }
                    public void typeText(String value) {
                        value.codePoints().forEach(c -> input.character.invoke(window.handle(), c));
                    }
                    public void evidence(String name) {
                        try {
                            if(name.equals("mailbox-navigation-motion")) {navigationMotion(window,input,output);DesktopMailboxMotion.verify(window,input,output);return;}
                            long motionEnd=System.nanoTime()+220000000L;
                            while(System.nanoTime()<motionEnd)window.frame();
                            settle(window);
                            int background=name.startsWith("mailbox-")
                                    ? name.equals("mailbox-dark")?0xff111315:0xfff8fafd : 0xfffafafa;
                            if(name.startsWith("mailbox-")) saveGeometry(window,output.resolve(name+".geometry.json"));
                            screenshot(window,output.resolve(name+".png"),background);
                        }
                        catch (Exception failure) { throw new IllegalStateException("广场实窗取证失败：" + name, failure); }
                    }
                    public void replaceText(String value) {
                        input.key.invoke(
                                window.handle(), GLFW_KEY_A, 0, GLFW_PRESS, GLFW_MOD_CONTROL);
                        input.key.invoke(
                                window.handle(), GLFW_KEY_A, 0, GLFW_RELEASE, GLFW_MOD_CONTROL);
                        value.codePoints().forEach(c -> input.character.invoke(window.handle(), c));
                    }
                });
    }

    private void imageReload() throws Exception {
        // 固定本地样本的等待上限只属于验收，不替生产增加重试；每次检查实际绘制载荷。
        PaintCommand image = image(window);
        long deadline = System.nanoTime() + 5000000000L;
        while (image == null && System.nanoTime() < deadline) {
            window.frame();
            image = image(window);
        }
        if (image == null) throw new AssertionError("资源图像未ready");
        settle(window);
        screenshot(window, output.resolve("gallery-desktop.png"), 0xfffafafa);
        verifyImage(window, image);
        Object generation = image.image.content;
        window.reloadConfiguration();
        image = image(window);
        deadline = System.nanoTime() + 5000000000L;
        while ((image == null || image.image.content == generation)
                && System.nanoTime() < deadline) {
            window.frame();
            image = image(window);
        }
        if (image == null || image.image.content == generation)
            throw new AssertionError("资源重载未换代");
        verifyImage(window, image);
    }

    private void themePublication() throws Exception {
        input.click("场景概览");
        settle(window);
        screenshot(window, output.resolve("gallery-home.png"), 0xfffafafa);
        input.click("预览深色");
        settle(window);
        byte[] beforeTheme = Files.readAllBytes(configuration);
        Object samePage = window.page;
        window.domain.execute(() -> window.configuration.set(ThemeConfiguration.KEY,ThemeMode.DARK));
        settle(window);
        window.domain.execute(() -> window.configuration.set(ThemeConfiguration.KEY,ThemeMode.LIGHT));
        settle(window);
        // 全局发布清除旧深色预览，按钮才能重新变为“预览深色”。
        input.click("预览深色");
        settle(window);
        if (window.page != samePage || !java.util.Arrays.equals(beforeTheme,Files.readAllBytes(configuration)))
            throw new AssertionError("图库主题发布重建页面或隐式写盘");
        System.out.println("PASS gallery theme publication clears local preview and follows global, same page, no implicit save");
        screenshot(window, output.resolve("gallery-dark.png"), 0xff09090b);
    }

    private void pageEvidence() throws Exception {
        glfwSetWindowSize(window.handle(), 640, 900);
        settle(window);
        screenshot(window, output.resolve("gallery-narrow.png"), 0xff09090b);
        input.click("打开知识库");
        settle(window);
        screenshot(window, output.resolve("gallery-markdown-narrow.png"), 0xff09090b);
        glfwSetWindowSize(window.handle(), 1100, 900);
        settle(window);
        screenshot(window, output.resolve("gallery-markdown-wide.png"), 0xff09090b);
        input.click("排产核对");
        settle(window);
        screenshot(window, output.resolve("gallery-checklist-wide.png"), 0xff09090b);
        glfwSetWindowSize(window.handle(), 640, 900);
        settle(window);
        screenshot(window, output.resolve("gallery-checklist-narrow.png"), 0xff09090b);
        glfwSetWindowSize(window.handle(), 1100, 900);
        settle(window);
        input.click("交接备忘");
        settle(window);
        screenshot(window, output.resolve("gallery-handover.png"), 0xff09090b);
        input.click("场景概览");
        input.click("打开生产计划");
        settle(window);
        screenshot(window, output.resolve("gallery-production.png"), 0xff09090b);
        scrollPage(window, input, -20);
        settle(window);
        screenshot(window, output.resolve("gallery-production-math.png"), 0xff09090b);
        input.click("场景概览");
        input.click("打开数据报告");
        settle(window);
        screenshot(window, output.resolve("gallery-report.png"), 0xff09090b);
        scrollPage(window, input, -20);
        settle(window);
        screenshot(window, output.resolve("gallery-report-math.png"), 0xff09090b);
        input.click("场景概览");
    }
}
