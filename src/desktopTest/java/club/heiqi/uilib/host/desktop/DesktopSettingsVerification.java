package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWWindowCloseCallback;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

/** 正式发行组合的设置页验收：只通过已安装GLFW回调输入，文件与GL像素均取真实结果。 */
public final class DesktopSettingsVerification {
    private DesktopSettingsVerification() {}
    public static void main(String[] args) throws Exception {
        Path output=args.length==0?Paths.get("settings-report"):Paths.get(args[0]);Files.createDirectories(output);
        Path path=output.resolve("settings.yaml");Files.deleteIfExists(path);
        try(DesktopWindow w=DesktopWindow.openSettings(path)) {
            glfwSetWindowSize(w.handle(),1440,1000);settle(w);Input input=new Input(w);
            List<String> original=current(w);byte[] before=Files.readAllBytes(path);
            require(!original.isEmpty(),"默认字体候选为空");
            screenshot(w,output.resolve("settings-light.png"));
            require(currentTheme(w)==ThemeMode.LIGHT,"新配置全局主题不是默认浅色");
            int lightInk=titleInk(w);
            input.click("深色模式");settle(w);
            require(currentTheme(w)==ThemeMode.LIGHT&&hasText(w,"有待应用修改"),"主题切换没有保留草稿阶段");
            require(titleInk(w)!=lightInk,"主题草稿没有可见预览");
            require(java.util.Arrays.equals(before,Files.readAllBytes(path)),"主题草稿误写文件");
            input.click("放弃编辑");settle(w);
            require(currentTheme(w)==ThemeMode.LIGHT&&hasText(w,"已保存")&&hasText(w,"深色模式")&&titleInk(w)==lightInk,"丢弃未恢复当前浅色主题");
            input.click("深色模式");
            input.click("下移");require(current(w).equals(original),"排序草稿提前发布");require(hasText(w,"有待应用修改"),"排序未标记草稿");
            input.click("配置文件");require(hasText(w,"有待应用修改"),"切分类丢草稿");input.click("字体与排版");
            input.click("应用全部更改");settle(w);List<String> applied=current(w);
            require(applied.get(0).equals(original.get(1))&&applied.get(1).equals(original.get(0)),"正式应用没有发布顺序");
            require(currentTheme(w)==ThemeMode.DARK&&titleInk(w)!=lightInk,"应用没有发布深色主题");
            screenshot(w,output.resolve("settings-dark.png"));
            require(java.util.Arrays.equals(before,Files.readAllBytes(path)),"应用误写文件");require(hasText(w,"已应用，未保存"),"已应用未保存状态不实");
            Path backup=output.resolve("save-failure-backup.yaml");Files.move(path,backup,java.nio.file.StandardCopyOption.REPLACE_EXISTING);Files.createDirectory(path);
            input.click("保存到文件");require(hasTextContaining(w,"保存失败：")&&hasText(w,"已应用，未保存"),"保存失败缺全局可见反馈或伪保存");
            Files.delete(path);Files.move(backup,path,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            input.click("保存到文件");require(!java.util.Arrays.equals(before,Files.readAllBytes(path)),"保存没有写真实文件");
            String yaml=new String(Files.readAllBytes(path),StandardCharsets.UTF_8);require(yaml.contains(applied.get(0))&&yaml.contains("windowProfile"),"保存丢字体或其他字段");
            input.click("下移");input.click("配置文件");input.click("从文件重新加载");require(hasText(w,"从文件重新加载？"),"脏加载没有确认");
            input.click("继续编辑");require(hasText(w,"有待应用修改"),"加载取消丢草稿");
            input.click("从文件重新加载");input.click("放弃更改并加载");settle(w);require(current(w).equals(applied)&&hasText(w,"已保存"),"显式load未恢复保存值");
            long generation=w.fonts.fonts().generation();input.click("从文件重新加载");settle(w);require(w.fonts.fonts().generation()>generation,"同候选显式加载未刷新资源");
            screenshot(w,output.resolve("settings-file.png"));
            byte[] validSaved=Files.readAllBytes(path);
            String invalidYaml=new String(validSaved,StandardCharsets.UTF_8).replace(applied.get(0),"file:C:/nonexistent/uilib-resource-test.ttf");
            Files.write(path,invalidYaml.getBytes(StandardCharsets.UTF_8));input.click("从文件重新加载");settle(w);
            require(hasTextContaining(w,"字体资源更新失败：")&&hasText(w,"已保存")&&!w.shouldClose(),"已load资源失败未保留工作区与保存事实");
            require(current(w).get(0).equals("file:C:/nonexistent/uilib-resource-test.ttf"),"资源错误回滚已读值");
            screenshot(w,output.resolve("settings-resource-error.png"));
            input.click("字体与排版");input.click("移除");input.click("+ 添加来源");input.click("添加到末尾");
            require(!hasText(w,"添加字体来源")&&hasText(w,"有待应用修改"),"旧资源错误阻断合法来源添加或未关闭模态");
            input.click("应用全部更改");settle(w);require(!hasTextContaining(w,"字体资源更新失败："),"修正并应用未清资源错误");
            input.click("配置文件");input.click("从文件重新加载");input.click("放弃更改并加载");settle(w);
            input.click("从文件重新加载");settle(w);require(hasTextContaining(w,"字体资源更新失败：")&&!hasText(w,"已从 "+path+" 重新加载"),"重复同文案资源失败伪成功");
            Files.write(path,validSaved);input.click("从文件重新加载");settle(w);require(!hasTextContaining(w,"字体资源更新失败：")&&current(w).equals(applied),"成功资源加载未清错误");
            input.click("字体与排版");
            input.click("+ 添加来源");input.click("本地字体文件");input.click("添加到末尾");require(hasText(w,"请输入字体文件路径。"),"空路径误关闭或未定位错误");
            input.clickField();input.type("C:/nonexistent/uilib-settings-test.ttf");input.click("添加到末尾");require(hasText(w,"添加字体来源"),"读取来源失败丢失模态");
            input.escape();require(!hasText(w,"添加字体来源"),"ESC未关闭添加模态");
            glfwSetWindowSize(w.handle(),1024,768);settle(w);screenshot(w,output.resolve("settings-medium.png"));require(visible(w,"应用全部更改"),"中窗口footer不可达");
            glfwSetWindowSize(w.handle(),480,900);settle(w);screenshot(w,output.resolve("settings-narrow.png"));require(visible(w,"应用全部更改")&&visible(w,"配置文件"),"窄窗口操作或分类不可达");
            glfwSetWindowSize(w.handle(),1440,1000);settle(w);input.enqueueClick("下移");input.close.invoke(w.handle());w.frame();
            require(!w.shouldClose()&&hasText(w,"离开配置工作区？"),"原生关闭绕过草稿确认");input.click("继续编辑");require(hasText(w,"有待应用修改"),"关闭取消丢草稿");
            input.click("浅色模式");input.click("应用全部更改");settle(w);
            require(currentTheme(w)==ThemeMode.LIGHT,"未保存主题应用没有生效");
            byte[] saved=Files.readAllBytes(path);input.close.invoke(w.handle());w.frame();require(!w.shouldClose(),"未保存原生关闭未确认");
            screenshot(w,output.resolve("settings-close.png"));input.click("不保存并离开");require(w.shouldClose(),"确认离开未关闭");require(java.util.Arrays.equals(saved,Files.readAllBytes(path)),"离开隐式保存");
            require(glGetError()==GL_NO_ERROR,"设置页GL错误");
            System.out.println("SETTINGS host=desktop sharedScene=true actualFileIO=true applyWithoutSave=true explicitReload=true callbackInput=true viewport=wide,medium,narrow theme=light,dark glyphPixels=true renderer="+glGetString(GL_RENDERER));
        }
        try(DesktopWindow reopened=DesktopWindow.openSettings(path)){
            settle(reopened);require(hasText(reopened,"已保存"),"重开不是文件保存基线");
            require(currentTheme(reopened)==ThemeMode.DARK&&hasText(reopened,"浅色模式"),"保存后关闭重开未恢复深色，或离开隐式保存了浅色");
            screenshot(reopened,output.resolve("settings-reopened-dark.png"));
            reopened.requestClose();reopened.frame();require(reopened.shouldClose(),"无修改不能正常关闭");
        }
        // 同一配置的普通sample页面也使用宿主全局主题，旧windowProfile.dark不决定配色。
        try(DesktopWindow sample=DesktopWindow.open(path)){
            settle(sample);require(currentTheme(sample)==ThemeMode.DARK,"另一桌面sample未读取已保存主题");
            sample.domain.execute(() -> require(sample.samplePage.root.background()==SceneTheme.dark().panel,"sample未显示已保存深色主题"));
            byte[] saved=Files.readAllBytes(path);
            sample.domain.execute(() -> sample.configuration.set(ThemeConfiguration.KEY,ThemeMode.LIGHT));settle(sample);
            sample.domain.execute(() -> {
                require(sample.configuration.get(DesktopPage.PROFILE).dark,"兼容旧示例dark字段丢失");
                require(sample.samplePage.root.background()==SceneTheme.light().panel,"sample未动态跟随全局浅色，或旧dark字段覆盖主题");
                require(sample.samplePage.builds==1,"全局主题更新重建sample页面");
            });
            require(java.util.Arrays.equals(saved,Files.readAllBytes(path)),"sample内存主题发布隐式写盘");
            sample.requestClose();
        }
        System.out.println("SETTINGS themeDraftDiscard=true themeApplyWithoutSave=true themePersistedReopen=true sampleThemeFollow=true");
    }
    private static List<String> current(DesktopWindow w){final List<String>[] result=new List[1];w.domain.execute(() -> result[0]=new ArrayList<>(w.configuration.get(w.fontCandidates)));return result[0];}
    private static ThemeMode currentTheme(DesktopWindow w){final ThemeMode[] result={null};w.domain.execute(() -> result[0]=w.configuration.get(ThemeConfiguration.KEY));return result[0];}
    private static int titleInk(DesktopWindow w){return w.lastPlan.commands().stream().filter(c -> c.kind==PaintCommand.Kind.TEXT&&c.text.originalText.equals("QZ  /  UILib 设置")).findFirst().orElseThrow(() -> new AssertionError("正式设置标题缺失")).color;}
    private static SceneGeometryView geometry(DesktopWindow w){final SceneGeometryView[] result={null};w.domain.execute(() -> result[0]=w.page.pipeline().current().geometry);return result[0];}
    private static boolean hasText(DesktopWindow w,String text){return geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&e.text.originalText.equals(text));}
    private static boolean hasTextContaining(DesktopWindow w,String text){return geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&e.text.originalText.contains(text)&&hit(e).width>0&&hit(e).height>0);}
    private static LayoutBox hit(SceneGeometryView.Entry e){return e.clip==null?e.box:e.box.intersect(e.clip);}
    private static boolean visible(DesktopWindow w,String text){return geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&e.text.originalText.equals(text)&&hit(e).width>0&&hit(e).height>0);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void settle(DesktopWindow w){long deadline=System.nanoTime()+5000000000L;int stable=0;do{w.frame();if(w.fonts.rasterizer().stats().pending==0&&w.fonts.rasterizer().stats().ready==0&&w.fonts.presentationStats().uploadCalls==0)stable++;else stable=0;if(stable>=4)break;}while(System.nanoTime()<deadline);require(stable>=4&&w.fonts.rasterizer().stats().pending==0&&w.fonts.rasterizer().stats().errors==0,"设置字形未完成或有raster错误");}
    private static void screenshot(DesktopWindow w,Path path) throws Exception {
        w.fonts.beginPresentation(1000000);try{w.replay(w.lastPlan);}finally{w.fonts.endPresentation();}glFinish();
        ByteBuffer bytes=ByteBuffer.allocateDirect(w.width()*w.height()*4);glReadPixels(0,0,w.width(),w.height(),GL_RGBA,GL_UNSIGNED_BYTE,bytes);
        BufferedImage image=new BufferedImage(w.width(),w.height(),BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<w.height();y++)for(int x=0;x<w.width();x++){int i=((w.height()-1-y)*w.width()+x)*4;image.setRGB(x,y,0xff000000|((bytes.get(i)&255)<<16)|((bytes.get(i+1)&255)<<8)|(bytes.get(i+2)&255));}
        PaintCommand title=w.lastPlan.commands().stream().filter(c -> c.kind==PaintCommand.Kind.TEXT&&c.text.originalText.equals("QZ  /  UILib 设置")).findFirst().orElseThrow(() -> new AssertionError("正式设置标题缺失"));
        int ink=0,background=image.getRGB(Math.min(w.width()-1,title.box.x),Math.min(w.height()-1,title.box.y));
        for(int y=Math.max(0,title.box.y);y<Math.min(w.height(),title.box.y+title.box.height);y++)for(int x=Math.max(0,title.box.x);x<Math.min(w.width(),title.box.x+title.box.width);x++)if(image.getRGB(x,y)!=background)ink++;
        require(ink>0&&glGetError()==GL_NO_ERROR,"设置标题无真实GL字形像素");ImageIO.write(image,"png",path.toFile());
    }
    private static final class Input {
        final DesktopWindow w;final GLFWCursorPosCallback cursor;final GLFWMouseButtonCallback mouse;final GLFWScrollCallback scroll;final GLFWKeyCallback key;final GLFWCharCallback character;final GLFWWindowCloseCallback close;
        Input(DesktopWindow w){this.w=w;cursor=glfwSetCursorPosCallback(w.handle(),null);glfwSetCursorPosCallback(w.handle(),cursor);mouse=glfwSetMouseButtonCallback(w.handle(),null);glfwSetMouseButtonCallback(w.handle(),mouse);scroll=glfwSetScrollCallback(w.handle(),null);glfwSetScrollCallback(w.handle(),scroll);key=glfwSetKeyCallback(w.handle(),null);glfwSetKeyCallback(w.handle(),key);character=glfwSetCharCallback(w.handle(),null);glfwSetCharCallback(w.handle(),character);close=glfwSetWindowCloseCallback(w.handle(),null);glfwSetWindowCloseCallback(w.handle(),close);}
        void click(String text){for(int attempt=0;attempt<40;attempt++){
            SceneGeometryView.Entry e=geometry(w).ordered().stream().filter(g -> g.text!=null&&g.text.originalText.equals(text)&&g.node.clickAction()!=null&&g.node.enabled()&&hit(g).width>0&&hit(g).height>0).findFirst().orElse(null);
            if(e!=null){click(e);return;}
            SceneGeometryView.Entry clipped=geometry(w).ordered().stream().filter(g -> g.text!=null&&g.text.originalText.equals(text)&&g.node.clickAction()!=null&&g.node.enabled()).findFirst().orElse(null);
            if(clipped==null)throw new AssertionError("找不到可操作按钮："+text+"；当前文本："+geometry(w).ordered().stream().filter(g -> g.text!=null).map(g -> g.text.originalText).collect(java.util.stream.Collectors.joining(" | ")));
            LayoutBox b=clipped.clip==null?new LayoutBox(0,0,w.width(),w.height()):clipped.clip;point(b.x+b.width/2.0,b.y+b.height/2.0);scroll.invoke(w.handle(),0,clipped.box.y<b.y?5:-5);w.frame();
        }throw new AssertionError("按钮不可显露："+text);}
        void click(SceneGeometryView.Entry e){enqueue(e);w.frame();}
        void enqueueClick(String text){SceneGeometryView.Entry e=geometry(w).ordered().stream().filter(g -> g.text!=null&&g.text.originalText.equals(text)&&g.node.clickAction()!=null&&g.node.enabled()&&hit(g).width>0&&hit(g).height>0).findFirst().orElseThrow(() -> new AssertionError("找不到同批按钮："+text));enqueue(e);}
        void enqueue(SceneGeometryView.Entry e){LayoutBox b=hit(e);point(b.x+b.width/2.0,b.y+b.height/2.0);mouse.invoke(w.handle(),GLFW_MOUSE_BUTTON_LEFT,GLFW_PRESS,0);mouse.invoke(w.handle(),GLFW_MOUSE_BUTTON_LEFT,GLFW_RELEASE,0);}
        void point(double x,double y){int[] width={0},height={0};glfwGetWindowSize(w.handle(),width,height);cursor.invoke(w.handle(),x*width[0]/w.width(),y*height[0]/w.height());}
        void clickField(){SceneGeometryView.Entry e=geometry(w).ordered().stream().filter(g -> g.node.control()!=null&&g.node.control().capturesPointer()&&hit(g).width>0&&hit(g).height>0).reduce((previous,next) -> next).orElseThrow(() -> new AssertionError("找不到模态文件路径编辑器"));click(e);}
        void type(String value){value.codePoints().forEach(cp -> character.invoke(w.handle(),cp));w.frame();}
        void escape(){key.invoke(w.handle(),GLFW_KEY_ESCAPE,0,GLFW_PRESS,0);key.invoke(w.handle(),GLFW_KEY_ESCAPE,0,GLFW_RELEASE,0);w.frame();}
    }
}
