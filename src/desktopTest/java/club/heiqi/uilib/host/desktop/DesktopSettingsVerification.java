package club.heiqi.uilib.host.desktop;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static club.heiqi.uilib.host.desktop.DesktopTestFrames.settle;
import static club.heiqi.uilib.host.desktop.DesktopTestInput.geometry;
import static club.heiqi.uilib.host.desktop.DesktopTestInput.hit;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;

/** 正式发行组合的设置页验收：只通过已安装GLFW回调输入，文件与GL像素均取真实结果。 */
public final class DesktopSettingsVerification {
    private DesktopSettingsVerification() {}
    public static void main(String[] args) throws Exception {
        Path output=args.length==0?Paths.get("settings-report"):Paths.get(args[0]);Files.createDirectories(output);
        Path path=output.resolve("settings.yaml");Files.deleteIfExists(path);
        try(DesktopWindow w=DesktopWindow.openSettings(path)) {
            glfwSetWindowSize(w.handle(),1440,1000);settle(w);DesktopTestInput input=new DesktopTestInput(w);
            List<String> original=current(w);byte[] before=Files.readAllBytes(path);
            require(!original.isEmpty(),"默认字体候选为空");
            screenshot(w,output.resolve("settings-light.png"));
            require(currentTheme(w)==ThemeMode.LIGHT,"新配置全局主题不是默认浅色");
            int lightInk=titleInk(w);
            input.click("外观");settle(w);
            require(hasText(w,"界面主题")&&hasText(w,"浅色外观")&&hasText(w,"深色外观"),"外观分类缺少实际主题选项");
            screenshot(w,output.resolve("settings-appearance-light.png"));
            input.click("深色外观");settle(w);
            screenshot(w,output.resolve("settings-appearance-dark.png"));
            require(currentTheme(w)==ThemeMode.LIGHT&&hasText(w,"有待应用修改"),"主题切换没有保留草稿阶段");
            require(titleInk(w)!=lightInk,"主题草稿没有可见预览");
            require(java.util.Arrays.equals(before,Files.readAllBytes(path)),"主题草稿误写文件");
            input.click("放弃编辑");settle(w);
            screenshot(w,output.resolve("settings-after-discard.png"));
            require(currentTheme(w)==ThemeMode.LIGHT&&hasText(w,"已保存")&&hasText(w,"深色模式")&&titleInk(w)==lightInk,
                    "丢弃未恢复当前浅色主题 theme="+currentTheme(w)+" saved="+hasText(w,"已保存")+" action="+hasText(w,"深色模式")+" ink="+titleInk(w)+" initial="+lightInk);
            input.click("深色模式");
            input.click("字体与排版");
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
            clickField(w,input);"C:/nonexistent/uilib-settings-test.ttf".codePoints().forEach(cp -> input.character.invoke(w.handle(),cp));w.frame();input.click("添加到末尾");require(hasText(w,"添加字体来源"),"读取来源失败丢失模态");
            input.key.invoke(w.handle(),GLFW_KEY_ESCAPE,0,GLFW_PRESS,0);input.key.invoke(w.handle(),GLFW_KEY_ESCAPE,0,GLFW_RELEASE,0);w.frame();require(!hasText(w,"添加字体来源"),"ESC未关闭添加模态");
            glfwSetWindowSize(w.handle(),1024,768);settle(w);screenshot(w,output.resolve("settings-medium.png"));require(visible(w,"应用全部更改"),"中窗口footer不可达");
            glfwSetWindowSize(w.handle(),480,900);settle(w);screenshot(w,output.resolve("settings-narrow.png"));
            require(visible(w,"应用全部更改")&&visible(w,"字体与排版 · 切换分类"),"窄窗口操作或分类入口不可达");
            input.click("字体与排版 · 切换分类");settle(w);screenshot(w,output.resolve("settings-narrow-navigation.png"));
            input.key.invoke(w.handle(),GLFW_KEY_ESCAPE,0,GLFW_PRESS,0);input.key.invoke(w.handle(),GLFW_KEY_ESCAPE,0,GLFW_RELEASE,0);w.frame();
            require(!visible(w,"关闭分类"),"ESC没有关闭窄窗分类层");
            w.domain.execute(() -> require(w.page.runtime().focusRouter().focused().get()!=null
                    && w.page.runtime().focusRouter().focused().get().text().equals("字体与排版 · 切换分类"),"分类关闭没有恢复触发点焦点"));
            input.click("字体与排版 · 切换分类");input.click("配置文件");settle(w);
            require(visible(w,"配置文件 · 切换分类")&&hasText(w,"文件路径"),"窄窗分类选择未切换正文");
            input.click("配置文件 · 切换分类");input.click("字体与排版");settle(w);
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
        // 显式宿主回归fixture使用全局主题，旧windowProfile.dark不决定配色。
        DesktopVerificationPage[] fixture = new DesktopVerificationPage[1];
        try(DesktopWindow sample=DesktopWindow.openWithServices(path, services -> fixture[0] =
                new DesktopVerificationPage(services.configuration(), services::reloadConfiguration))){
            DesktopVerificationPage view=fixture[0];
            require(view.configuration==sample.configuration&&view.domain==sample.domain,"fixture未借用同域真实配置会话");
            settle(sample);require(currentTheme(sample)==ThemeMode.DARK,"另一桌面fixture未读取已保存主题");
            sample.domain.execute(() -> require(view.root.background()==SceneTheme.dark().panel,"fixture未显示已保存深色主题"));
            byte[] saved=Files.readAllBytes(path);
            sample.domain.execute(() -> sample.configuration.set(ThemeConfiguration.KEY,ThemeMode.LIGHT));settle(sample);
            sample.domain.execute(() -> {
                require(sample.configuration.get(DesktopWindowConfiguration.PROFILE).dark,"兼容旧示例dark字段丢失");
                require(view.root.background()==SceneTheme.light().panel,"fixture未动态跟随全局浅色，或旧dark字段覆盖主题");
                require(view.builds==1,"全局主题更新重建fixture页面");
            });
            require(java.util.Arrays.equals(saved,Files.readAllBytes(path)),"fixture内存主题发布隐式写盘");
            sample.requestClose();
        }
        System.out.println("SETTINGS themeDraftDiscard=true themeApplyWithoutSave=true themePersistedReopen=true sampleThemeFollow=true");
    }
    private static List<String> current(DesktopWindow w){final List<String>[] result=new List[1];w.domain.execute(() -> result[0]=new ArrayList<>(w.configuration.get(w.fontCandidates)));return result[0];}
    private static ThemeMode currentTheme(DesktopWindow w){final ThemeMode[] result={null};w.domain.execute(() -> result[0]=w.configuration.get(ThemeConfiguration.KEY));return result[0];}
    private static int titleInk(DesktopWindow w){return w.lastPlan.commands().stream().filter(c -> c.kind==PaintCommand.Kind.TEXT&&"QZ  /  UILib 设置".equals(c.text.logicalText)).findFirst().orElseThrow(() -> new AssertionError("正式设置标题缺失")).color;}
    // 断言呈现的逻辑内容；TextInput 入口允许 originalText 为空，不能把可选原串当显示文字。
    private static boolean hasText(DesktopWindow w,String text){return geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&text.equals(e.text.logicalText));}
    private static boolean hasTextContaining(DesktopWindow w,String text){return geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&e.text.logicalText.contains(text)&&hit(e).width>0&&hit(e).height>0);}
    private static boolean visible(DesktopWindow w,String text){return geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&text.equals(e.text.logicalText)&&hit(e).width>0&&hit(e).height>0);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void screenshot(DesktopWindow w,Path path) throws Exception {
        w.fonts.beginPresentation(1000000);try{w.replay(w.lastPlan);}finally{w.fonts.endPresentation();}
        BufferedImage image=DesktopTestReadback.readBack(w);
        PaintCommand title=w.lastPlan.commands().stream().filter(c -> c.kind==PaintCommand.Kind.TEXT&&"QZ  /  UILib 设置".equals(c.text.logicalText)).findFirst().orElseThrow(() -> new AssertionError("正式设置标题缺失"));
        int ink=0,background=image.getRGB(Math.min(w.width()-1,title.box.x),Math.min(w.height()-1,title.box.y));
        for(int y=Math.max(0,title.box.y);y<Math.min(w.height(),title.box.y+title.box.height);y++)for(int x=Math.max(0,title.box.x);x<Math.min(w.width(),title.box.x+title.box.width);x++)if(image.getRGB(x,y)!=background)ink++;
        require(ink>0&&glGetError()==GL_NO_ERROR,"设置标题无真实GL字形像素");ImageIO.write(image,"png",path.toFile());
    }
    private static void clickField(DesktopWindow w, DesktopTestInput input) {
        SceneGeometryView.Entry entry = geometry(w).ordered().stream()
                .filter(g -> g.node.control() != null && g.node.control().capturesPointer()
                        && hit(g).width > 0 && hit(g).height > 0)
                .reduce((previous, next) -> next)
                .orElseThrow(() -> new AssertionError("找不到模态文件路径编辑器"));
        input.click(entry);
    }

}
