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
        window=own(DesktopWindow.openWithServices(configuration, services -> DesktopGallery.createGallery(services,TestGallery.Page.HOME)));
        glfwSetWindowSize(window.handle(),1400,1100);
        window.frame();
        input=new DesktopTestInput(window);
    }

    @Override protected void runTests() throws Exception {
        scenario("共享广场旅程与邮箱真实像素和过渡帧",this::sharedJourney);
        scenario("广场正式配置加载资源失败与显式恢复",this::settingsResources);
        scenario("主题发布不重建页面也不隐式写盘",this::themePublication);
        scenario("新版配置宽窄空间与精简目录取证",this::pageEvidence);
        scenario("广场配置原生关闭与快捷重载确认",this::nativeSettingsIntents);
        System.out.println("GALLERY host=desktop sharedDeclaration=true sharedJourney=true realReferencesOnly=true settingsShared=true renderer="
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

    private void settingsResources() throws Exception {
        input.click("打开配置");input.click("配置文件");settle(window);
        byte[] saved=Files.readAllBytes(configuration);
        String invalid=new String(saved,java.nio.charset.StandardCharsets.UTF_8).replace("builtin:unifont","file:C:/nonexistent/uilib-gallery-settings.ttf");
        if(invalid.equals(new String(saved,java.nio.charset.StandardCharsets.UTF_8)))throw new AssertionError("未找到真实字体配置声明");
        Files.write(configuration,invalid.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        input.click("从文件重新加载");settle(window);
        if(window.shouldClose()||!window.lastPlan.commands().stream().anyMatch(c -> c.kind==PaintCommand.Kind.TEXT
                &&c.text.logicalText.contains("字体资源更新失败：")))throw new AssertionError("广场资源失败未保留正式配置页与错误事实");
        screenshot(window,output.resolve("gallery-settings-resource-error.png"),0xffffffff);
        Files.write(configuration,saved);input.click("从文件重新加载");settle(window);
        if(window.lastPlan.commands().stream().anyMatch(c -> c.kind==PaintCommand.Kind.TEXT
                &&c.text.logicalText.contains("字体资源更新失败：")))throw new AssertionError("恢复文件并显式加载未清除资源错误");
        input.click("返回");settle(window);
        System.out.println("GALLERY_SETTINGS actualConfig=true resourceFailureVisible=true publishedValuesRetained=true explicitReloadRecovery=true");
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

    private void nativeSettingsIntents() throws Exception {
        byte[] saved=Files.readAllBytes(configuration);
        input.click("打开配置");input.click("外观");input.click("深色外观");settle(window);
        input.key.invoke(window.handle(),GLFW_KEY_R,0,GLFW_PRESS,GLFW_MOD_CONTROL);
        input.key.invoke(window.handle(),GLFW_KEY_R,0,GLFW_RELEASE,GLFW_MOD_CONTROL);settle(window);
        if(window.shouldClose()||!hasText("从文件重新加载？"))throw new AssertionError("广场Ctrl+R绕过配置确认");
        input.click("继续编辑");settle(window);
        glfwSetWindowShouldClose(window.handle(),true);input.close.invoke(window.handle());settle(window);
        if(window.shouldClose()||!hasText("离开配置工作区？"))throw new AssertionError("广场原生关闭绕过草稿确认");
        input.click("继续编辑");input.click("应用全部更改");settle(window);
        if(!hasText("已应用，未保存"))throw new AssertionError("原生关闭取消后丢失主题草稿");
        glfwSetWindowShouldClose(window.handle(),true);input.close.invoke(window.handle());settle(window);
        if(window.shouldClose()||!hasText("离开配置工作区？"))throw new AssertionError("广场原生关闭绕过已应用未保存确认");
        input.click("不保存并离开");
        if(!window.shouldClose()||!java.util.Arrays.equals(saved,Files.readAllBytes(configuration)))
            throw new AssertionError("确认退出未关闭窗口或隐式保存文件");
        System.out.println("GALLERY_SETTINGS nativeCloseConfirmation=true shortcutReloadConfirmation=true appliedUnsavedGuard=true noImplicitSave=true");
    }
    private boolean hasText(String text){return window.lastPlan.commands().stream().anyMatch(c -> c.kind==PaintCommand.Kind.TEXT&&text.equals(c.text.logicalText));}

    private void pageEvidence() throws Exception {
        input.click("场景概览");input.click("预览浅色");
        input.click("打开配置");settle(window);
        screenshot(window,output.resolve("gallery-settings-wide.png"),0xffffffff);
        glfwSetWindowSize(window.handle(),480,900);settle(window);
        screenshot(window,output.resolve("gallery-settings-narrow.png"),0xffffffff);
        input.click("返回");settle(window);
        screenshot(window,output.resolve("gallery-reference-directory.png"),0xfffafafa);
        glfwSetWindowSize(window.handle(),1400,900);settle(window);
    }
}
