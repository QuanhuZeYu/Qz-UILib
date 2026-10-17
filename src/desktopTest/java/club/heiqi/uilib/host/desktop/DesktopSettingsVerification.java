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

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.MathTextFontSources;

/** 正式发行组合的设置页验收：真实GLFW、文件与GL断言归属具名场景。 */
public final class DesktopSettingsVerification extends TestApplication {
    private Path output, path;
    private DesktopWindow w;
    private DesktopTestInput input;
    private List<String> original, applied;
    private byte[] before;
    private int lightInk;

    public static void main(String[] args) throws Exception { new DesktopSettingsVerification().run(args); }

    @Override protected void setup() throws Exception {
        String visible=System.getProperty("uilib.desktop.visible");
        System.setProperty("uilib.desktop.visible","false");
        onClose(() -> { if(visible==null)System.clearProperty("uilib.desktop.visible");else System.setProperty("uilib.desktop.visible",visible); });
        String[] args=arguments();
        output=args.length==0?Paths.get("settings-report"):Paths.get(args[0]);Files.createDirectories(output);
        path=output.resolve("settings.yaml");Files.deleteIfExists(path);
    }
    @Override protected void runTests() throws Exception {
        scenario("字体浏览回填、嵌套ESC与显式添加应用保存",this::fontPicker);
        w=own(DesktopWindow.openSettings(path));
        glfwSetWindowSize(w.handle(),1440,1000);settle(w);input=new DesktopTestInput(w);
        original=current(w);before=Files.readAllBytes(path);

        scenario("统一外观首屏与主题草稿预览",this::appearanceDraft);
        scenario("字体排序、应用与真实文件保存",this::applyAndSave);
        scenario("显式重载、资源错误与保存事实",this::reloadAndErrors);
        scenario("字段真实输入、模态返回与合法空候选",this::fieldsAndEmpty);
        scenario("窗口布局与窄窗分类焦点返回",this::viewportAndNavigation);
        scenario("原生关闭确认与未保存值保留",this::closeConfirmation);
        scenario("重开读取持久化主题",this::reopenSavedTheme);
        scenario("共享配置与全局主题动态跟随",this::sharedTheme);
        scenario("短暂反馈重复身份、焦点与真实路径字形",this::presentationContract);
    }

    private void fontPicker()throws Exception{
        Path directory=Files.createTempDirectory("uilib 字体 +浏览"),font=directory.resolve("字体 +sample.ttf"),config=output.resolve("font-picker.yaml");Files.deleteIfExists(config);
        try{
            try(java.io.InputStream source=getClass().getResourceAsStream("/assets/qz_uilib/fonts/math-text/sans/NotoSans-Regular.ttf")){require(source!=null,"缺少可移植的实际字体");Files.copy(source,font);}
            try(DesktopWindow window=DesktopWindow.openSettings(config)){
                DesktopTestInput keys=new DesktopTestInput(window);settle(window);List<String> initial=current(window);byte[] saved=Files.readAllBytes(config);
                keys.click("管理字体来源");keys.click("+ 添加来源");keys.click("本地字体文件");settle(window);
                SceneNode field=activeFields(window).get(0);typeInto(window,keys,field,font.toString(),false);
                keys.click("浏览字体文件");settle(window);require(hasText(window,"选择文件"),"字体没有使用正式选择器");
                keys.key.invoke(window.handle(),GLFW_KEY_ESCAPE,0,GLFW_PRESS,0);keys.key.invoke(window.handle(),GLFW_KEY_ESCAPE,0,GLFW_RELEASE,0);window.frame();
                require(hasText(window,"添加字体来源")&&!hasText(window,"选择文件")&&activeFields(window).get(0)==field,"内层ESC关闭了添加框或重建草稿");
                window.domain.execute(()->require(((club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget)field.control()).compositionState().snapshot().get().text.equals(font.toString()),"取消选择覆盖了原路径草稿"));
                keys.click("浏览字体文件");settle(window);SceneNode pickerField=activeFields(window).get(0);typeInto(window,keys,pickerField,directory.toString(),true);
                long until=System.nanoTime()+5_000_000_000L;while(!hasText(window,font.getFileName().toString())&&System.nanoTime()<until){window.frame();Thread.sleep(5);}
                keys.click(font.getFileName().toString());keys.click("选择文件");until=System.nanoTime()+5_000_000_000L;while(hasText(window,"选择文件")&&System.nanoTime()<until){window.frame();Thread.sleep(5);}
                require(!hasText(window,"选择文件")&&hasText(window,"添加字体来源")&&current(window).equals(initial)&&java.util.Arrays.equals(saved,Files.readAllBytes(config)),"回填自动追加、应用或保存");
                window.domain.execute(()->require(((club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget)field.control()).compositionState().snapshot().get().text.equals(font.toString()),"选择结果未回填路径草稿"));
                keys.click("添加到末尾");require(current(window).equals(initial)&&hasText(window,"有待应用修改"),"显式追加提前应用");keys.click("完成字体编辑");keys.click("应用全部更改");settle(window);
                require(current(window).contains("file:"+font)&&java.util.Arrays.equals(saved,Files.readAllBytes(config)),"应用未发布字体或自动保存");keys.click("保存到文件");settle(window);require(!java.util.Arrays.equals(saved,Files.readAllBytes(config)),"显式保存未写真实文件");
                screenshot(window,output.resolve("font-picker-applied.png"));
            }
            try(DesktopWindow window=DesktopWindow.openSettings(config)){settle(window);require(current(window).contains("file:"+font),"字体来源重开未恢复");}
            System.out.println("FONT_PICKER sharedScenePicker=true cancelPreservesDraft=true nestedEscape=true fillOnly=true explicitAppendApplySave=true reopened=true actualCallbacks=true");
        }finally{Files.deleteIfExists(font);Files.deleteIfExists(directory);}
    }
    private static List<SceneNode> activeFields(DesktopWindow window){List<SceneNode> nodes=new ArrayList<>();window.domain.execute(()->{for(SceneGeometryView.Entry entry:geometry(window).ordered())if(entry.node.control() instanceof club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget&&window.page.runtime().interactive(entry.node))nodes.add(entry.node);});return nodes;}
    private static void typeInto(DesktopWindow window,DesktopTestInput input,SceneNode field,String value,boolean enter){input.moveTo(field);input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);input.key.invoke(window.handle(),GLFW_KEY_A,0,GLFW_PRESS,GLFW_MOD_CONTROL);value.codePoints().forEach(cp->input.character.invoke(window.handle(),cp));if(enter)input.key.invoke(window.handle(),GLFW_KEY_ENTER,0,GLFW_PRESS,0);window.frame();}
    private void appearanceDraft() throws Exception {
        require(!original.isEmpty(),"默认字体候选为空");
        require(visible(w,"管理字体来源")&&visible(w,"浅色外观")&&visible(w,"深色外观")&&visible(w,"文字样本"),"默认首屏未完整呈现统一外观表单");
        require(!visible(w,"字体与排版")&&!visible(w,"下移"),"默认首屏仍保留旧字体页面或长表操作");
        w.domain.execute(() -> {
            SceneGeometryView.Entry preview=geometry(w).ordered().stream()
                    .filter(e -> e.node.control()!=null&&e.node.control().capturesPointer()
                            &&e.node.text().startsWith("每一种语言")).findFirst()
                    .orElseThrow(() -> new AssertionError("实际字体预览编辑器缺失"));
            require(preview.clip!=null&&preview.box.intersect(preview.clip).height==preview.box.height,
                    "默认首屏字体预览被裁剪或被操作栏遮挡");
        });
        screenshot(w,output.resolve("settings-light.png"));
        require(currentTheme(w)==ThemeMode.LIGHT,"新配置全局主题不是默认浅色");
        lightInk=titleInk(w);
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
    }
    private void applyAndSave() throws Exception {
        input.click("深色模式");
        input.click("管理字体来源");screenshot(w,output.resolve("settings-font-manager.png"));
        fontDrag(w,input,true);require(current(w).equals(original),"取消拖动提前发布字体");
        fontDrag(w,input,false);require(current(w).equals(original),"排序草稿提前发布");require(hasText(w,"有待应用修改"),"排序未标记草稿");
        screenshot(w,output.resolve("settings-font-dragged.png"));
        input.click("完成字体编辑");input.click("配置文件");require(hasText(w,"有待应用修改"),"切分类丢草稿");input.click("外观");
        input.click("应用全部更改");settle(w);applied=current(w);
        require(applied.get(0).equals(original.get(1))&&applied.get(1).equals(original.get(0)),"正式应用没有发布顺序");
        require(currentTheme(w)==ThemeMode.DARK&&titleInk(w)!=lightInk,"应用没有发布深色主题");
        screenshot(w,output.resolve("settings-dark.png"));
        require(java.util.Arrays.equals(before,Files.readAllBytes(path)),"应用误写文件");require(hasText(w,"已应用，未保存"),"已应用未保存状态不实");
        Path backup=output.resolve("save-failure-backup.yaml");Files.move(path,backup,java.nio.file.StandardCopyOption.REPLACE_EXISTING);Files.createDirectory(path);
        input.click("保存到文件");require(hasTextContaining(w,"保存失败：")&&hasText(w,"已应用，未保存"),"保存失败缺全局可见反馈或伪保存");
        Files.delete(path);Files.move(backup,path,java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        input.click("保存到文件");require(!java.util.Arrays.equals(before,Files.readAllBytes(path)),"保存没有写真实文件");
        String yaml=new String(Files.readAllBytes(path),StandardCharsets.UTF_8);require(yaml.contains(applied.get(0))&&yaml.contains("windowProfile"),"保存丢字体或其他字段");
    }
    private void reloadAndErrors() throws Exception {
        input.click("管理字体来源");input.click("下移");input.click("完成字体编辑");input.click("配置文件");input.click("从文件重新加载");require(hasText(w,"从文件重新加载？"),"脏加载没有确认");
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
        input.click("外观");input.click("管理字体来源");input.click("移除");input.click("+ 添加来源");input.click("添加到末尾");
        require(!hasText(w,"添加字体来源")&&hasText(w,"有待应用修改"),"旧资源错误阻断合法来源添加或未关闭模态");
        input.click("完成字体编辑");input.click("应用全部更改");settle(w);require(!hasTextContaining(w,"字体资源更新失败："),"修正并应用未清资源错误");
        input.click("配置文件");input.click("从文件重新加载");input.click("放弃更改并加载");settle(w);
        input.click("从文件重新加载");settle(w);require(hasTextContaining(w,"字体资源更新失败：")&&!hasText(w,"已从 "+path+" 重新加载"),"重复同文案资源失败伪成功");
        Files.write(path,validSaved);input.click("从文件重新加载");settle(w);require(!hasTextContaining(w,"字体资源更新失败：")&&current(w).equals(applied),"成功资源加载未清错误");
    }
    private void fieldsAndEmpty() throws Exception {
        input.click("外观");input.click("管理字体来源");
        input.click("+ 添加来源");input.click("本地字体文件");input.click("添加到末尾");require(hasText(w,"请输入字体文件路径。"),"空路径误关闭或未定位错误");
        clickField(w,input);"C:/nonexistent/uilib-settings-test.ttf".codePoints().forEach(cp -> input.character.invoke(w.handle(),cp));w.frame();input.click("添加到末尾");require(hasText(w,"添加字体来源"),"读取来源失败丢失模态");
        input.key.invoke(w.handle(),GLFW_KEY_ESCAPE,0,GLFW_PRESS,0);input.key.invoke(w.handle(),GLFW_KEY_ESCAPE,0,GLFW_RELEASE,0);w.frame();require(!hasText(w,"添加字体来源")&&visible(w,"完成字体编辑"),"ESC未返回字体管理");
        input.click("完成字体编辑");
        input.click("管理字体来源");int sources=current(w).size();
        for(int i=0;i<sources;i++)input.click("移除");
        require(visible(w,"未配置字体候选。不会自动补入默认字体。"),"移除最后一项未呈现合法空列表");
        screenshot(w,output.resolve("settings-font-empty.png"));
        input.click("完成字体编辑");
        w.domain.execute(() -> require(w.page.runtime().focusRouter().focused().get()!=null
                &&w.page.runtime().focusRouter().focused().get().text().equals("管理字体来源"),"完成字体编辑后焦点未回到原字段"));
        input.click("应用全部更改");settle(w);require(current(w).isEmpty(),"空列表应用后隐式补回了来源");
        require(visible(w,"未配置生效字体来源，当前没有可用的文字样本。"),"空值生效后没有准确说明样本不可用");
        input.click("保存到文件");input.click("配置文件");input.click("从文件重新加载");settle(w);
        require(current(w).isEmpty(),"已保存空列表重新加载后被补回");
        input.click("外观");input.click("管理字体来源");
        require(visible(w,"未配置字体候选。不会自动补入默认字体。")&&!visible(w,"移除"),"空值重开管理未保持实际空态");
        input.click("恢复默认");input.click("完成字体编辑");input.click("应用全部更改");settle(w);input.click("保存到文件");
        System.out.println("SETTINGS emptySources=true removeLast=true applySaveReloadEmpty=true reopenEmptyManager=true returnFocus=true restoreDefaults=true");
    }
    private void viewportAndNavigation() throws Exception {
        glfwSetWindowSize(w.handle(),1024,768);settle(w);screenshot(w,output.resolve("settings-medium.png"));require(visible(w,"应用全部更改"),"中窗口footer不可达");
        glfwSetWindowSize(w.handle(),480,900);settle(w);screenshot(w,output.resolve("settings-narrow.png"));
        require(visible(w,"应用全部更改")&&visible(w,"外观 · 切换分类"),"窄窗口操作或分类入口不可达");
        input.click("外观 · 切换分类");settle(w);screenshot(w,output.resolve("settings-narrow-navigation.png"));
        input.key.invoke(w.handle(),GLFW_KEY_ESCAPE,0,GLFW_PRESS,0);input.key.invoke(w.handle(),GLFW_KEY_ESCAPE,0,GLFW_RELEASE,0);w.frame();
        require(!visible(w,"关闭分类"),"ESC没有关闭窄窗分类层");
        w.domain.execute(() -> require(w.page.runtime().focusRouter().focused().get()!=null
                && w.page.runtime().focusRouter().focused().get().text().equals("外观 · 切换分类"),"分类关闭没有恢复触发点焦点"));
        input.click("外观 · 切换分类");input.click("配置文件");settle(w);
        require(visible(w,"配置文件 · 切换分类")&&hasText(w,"文件路径"),"窄窗分类选择未切换正文");
        input.click("配置文件 · 切换分类");input.click("外观");settle(w);
    }
    private void closeConfirmation() throws Exception {
        glfwSetWindowSize(w.handle(),1440,1000);settle(w);input.enqueueClick("浅色模式");input.close.invoke(w.handle());w.frame();
        require(!w.shouldClose()&&hasText(w,"离开配置工作区？"),"原生关闭绕过草稿确认");input.click("继续编辑");require(hasText(w,"有待应用修改"),"关闭取消丢草稿");
        input.click("浅色外观");input.click("应用全部更改");settle(w);
        require(currentTheme(w)==ThemeMode.LIGHT,"未保存主题应用没有生效");
        byte[] saved=Files.readAllBytes(path);input.close.invoke(w.handle());w.frame();require(!w.shouldClose(),"未保存原生关闭未确认");
        screenshot(w,output.resolve("settings-close.png"));input.click("不保存并离开");require(w.shouldClose(),"确认离开未关闭");require(java.util.Arrays.equals(saved,Files.readAllBytes(path)),"离开隐式保存");
        require(glGetError()==GL_NO_ERROR,"设置页GL错误");
        System.out.println("SETTINGS host=desktop sharedScene=true actualFileIO=true applyWithoutSave=true explicitReload=true callbackInput=true viewport=wide,medium,narrow theme=light,dark glyphPixels=true renderer="+glGetString(GL_RENDERER));
        w.close();
    }
    private void reopenSavedTheme() throws Exception {
        DesktopWindow reopened=own(DesktopWindow.openSettings(path));
        settle(reopened);require(hasText(reopened,"已保存"),"重开不是文件保存基线");
        require(currentTheme(reopened)==ThemeMode.DARK&&hasText(reopened,"浅色模式"),"保存后关闭重开未恢复深色，或离开隐式保存了浅色");
        screenshot(reopened,output.resolve("settings-reopened-dark.png"));
        reopened.requestClose();reopened.frame();require(reopened.shouldClose(),"无修改不能正常关闭");
    }
    private void sharedTheme() throws Exception {
        // 显式宿主回归fixture使用全局主题，旧windowProfile.dark不决定配色。
        DesktopVerificationPage[] fixture = new DesktopVerificationPage[1];
        DesktopWindow sample=own(DesktopWindow.openWithServices(path, services -> fixture[0] =
                new DesktopVerificationPage(services.configuration(), services::reloadConfiguration)));
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

    private void presentationContract() throws Exception {
        Path presentationPath=output.resolve("settings-presentation.yaml");Files.deleteIfExists(presentationPath);
        DesktopWindow window=own(DesktopWindow.openSettings(presentationPath));
        glfwSetWindowSize(window.handle(),1440,1000);settle(window);
        DesktopTestInput events=new DesktopTestInput(window);
        events.click("配置文件");settle(window);
        SceneGeometryView.Entry pathEntry=geometry(window).ordered().stream()
                .filter(e -> e.text!=null&&presentationPath.toString().equals(e.text.logicalText)).findFirst()
                .orElseThrow(() -> new AssertionError("实际配置路径未进入正式文本布局"));
        TextStyle pathStyle=pathEntry.text.style;
        require(pathStyle.candidates()!=null&&pathStyle.candidates().get(0).id.equals(MathTextFontSources.MONO),
                "实际路径未显式选择随包等宽族");
        float advance=-1;
        for(String character:new String[]{"i","W","0",":","/"}) {
            TextLayout layout=window.fonts.layout(character,pathStyle,4096);
            TextLayout.Glyph glyph=layout.lines().get(0).glyphs().get(0);
            require(!glyph.replacement&&glyph.source.id.startsWith(MathTextFontSources.MONO),"路径字符未取真实PlexMono字形："+character);
            if(advance>=0)require(Math.abs(glyph.xAdvance-advance)<0.001f,"实际路径Latin字符advance不等宽");
            advance=glyph.xAdvance;
        }
        TextLayout normal=window.fonts.layout("iW0:/iW0:/",pathStyle,4096);
        TextLayout larger=window.fonts.layout("iW0:/iW0:/",pathStyle.withEm(pathStyle.logicalEm*2),4096);
        require(larger.advanceWidth>normal.advanceWidth&&larger.height>normal.height,"路径真实字号变化未进入度量");
        TextLayout narrow=window.fonts.layout("iW0:/iW0:/",pathStyle,Math.max(1,normal.width/3),
                new club.heiqi.uilib.ui.scene.text.TextPolicy(club.heiqi.uilib.ui.scene.text.TextPolicy.Direction.LTR,
                        club.heiqi.uilib.ui.scene.text.TextPolicy.Wrap.EMERGENCY,"und",0,0));
        require(narrow.lines().size()>normal.lines().size(),"路径换行没有响应实际可用宽度");
        TextStyle uiOnly=new TextStyle("settings-ui",pathStyle.size,pathStyle.color,
                pathStyle.candidates().subList(1,pathStyle.candidates().size()));
        require(Math.abs(window.fonts.layout("i",uiOnly,4096).advanceWidth-window.fonts.layout("W",uiOnly,4096).advanceWidth)>0.001f,
                "更换正式候选后仍被固定advance伪装等宽");
        buttonFocus(window,events,"从文件重新加载",false);
        events.click("外观");buttonFocus(window,events,"外观",true);
        events.click("管理字体来源");events.click("+ 添加来源");events.click("本地字体文件");clickField(window,events);
        SceneNode editor=focused(window);
        window.domain.execute(() -> require(editor!=null&&editor.control().acceptsTextInput(editor)
                &&window.page.runtime().focusRouter().focusVisible(editor),"鼠标聚焦字段丢失编辑焦点线索"));
        events.character.invoke(window.handle(),'i');window.frame();
        club.heiqi.uilib.ui.scene.layout.LayoutBox editorBox=geometry(window,editor).box;
        require(window.lastPlan.commands().stream().anyMatch(c -> c.kind==PaintCommand.Kind.RECT&&c.box.width==1
                &&c.box.height>=pathStyle.size/2&&c.box.x>editorBox.x+2&&c.box.x<editorBox.x+editorBox.width-2
                &&c.box.y>editorBox.y&&c.box.y+c.box.height<editorBox.y+editorBox.height),"鼠标字段输入后实际caret未绘制");
        require(editor.text().equals("i"),"真实字符回调没有进入路径字段");
        press(window,events,GLFW_KEY_ESCAPE,0);events.click("完成字体编辑");events.click("配置文件");
        events.click("从文件重新加载");String success="已从 "+presentationPath+" 重新加载";
        require(visible(window,success),"实际重载没有成功提示："+textGeometry(window,success));
        SceneNode focus=focused(window);
        waitFrames(window,1_500_000_000L);
        events.click("从文件重新加载");
        require(focused(window)==focus&&visible(window,success),"重复成功抢焦点或未呈现同文案结果");
        waitFrames(window,1_700_000_000L);
        require(visible(window,success),"旧期限清除了同文案新成功提示");
        long deadline=System.nanoTime()+4_000_000_000L;
        while(visible(window,success)&&System.nanoTime()<deadline){window.frame();Thread.sleep(10);}
        require(!visible(window,success)&&hasText(window,"已保存")&&focused(window)==focus,
                "成功未到期或到期误改配置阶段/焦点");
        byte[] saved=Files.readAllBytes(presentationPath);String first=current(window).get(0);
        Files.write(presentationPath,new String(saved,StandardCharsets.UTF_8)
                .replace(first,"file:C:/nonexistent/uilib-transient-error.ttf").getBytes(StandardCharsets.UTF_8));
        events.click("从文件重新加载");settle(window);
        require(hasTextContaining(window,"字体资源更新失败："),"资源错误反例未命中正式加载路径");
        waitFrames(window,3_100_000_000L);
        require(hasTextContaining(window,"字体资源更新失败：")&&hasText(window,"已保存")&&!visible(window,success),
                "期限误清错误、保存事实或伪报成功");
        screenshot(window,output.resolve("settings-presentation-contract.png"));
        club.heiqi.uilib.ui.scene.host.ScenePageSession session=window.page;
        window.close();require(session.ended(),"实际窗口关闭没有结束呈现会话");
        System.out.println("SETTINGS transientSuccess=true repeatedIdentity=true focusPixels=true editorCaret=true actualMonoAdvance="+advance+" errorPersists=true closedSession=true");
    }

    private static String textGeometry(DesktopWindow window,String text) {
        return geometry(window).ordered().stream().filter(e -> e.text!=null&&text.equals(e.text.logicalText))
                .map(e -> "box="+e.box+",clip="+e.clip+",effective="+hit(e)).collect(java.util.stream.Collectors.joining(";"));
    }
    private static SceneNode focused(DesktopWindow window) {
        SceneNode[] result={null};window.domain.execute(() -> result[0]=window.page.runtime().focusRouter().focused().get());return result[0];
    }
    private static void press(DesktopWindow window,DesktopTestInput events,int key,int modifiers) {
        events.key.invoke(window.handle(),key,0,GLFW_PRESS,modifiers);
        events.key.invoke(window.handle(),key,0,GLFW_RELEASE,modifiers);window.frame();
    }
    private static void waitFrames(DesktopWindow window,long nanos) throws InterruptedException {
        long started=System.nanoTime();
        do {window.frame();Thread.sleep(10);} while(System.nanoTime()-started<nanos);
    }
    private static void buttonFocus(DesktopWindow window,DesktopTestInput events,String name,boolean ownOutline) throws Exception {
        events.click(name);SceneNode target=focused(window);
        require(target!=null&&name.equals(target.text()),"点击普通按钮未取得逻辑焦点："+name);
        window.domain.execute(() -> require(!window.page.runtime().focusRouter().focusVisible(target),"鼠标按钮错误显示键盘焦点："+name));
        SceneNode surface=ownOutline?target:target.parent();int pointerEdge=edgePixel(window,surface);
        press(window,events,GLFW_KEY_TAB,0);
        for(int attempt=0;attempt<80&&focused(window)!=target;attempt++)press(window,events,GLFW_KEY_TAB,0);
        require(focused(window)==target,"真实Tab未回到目标按钮："+name);
        window.domain.execute(() -> require(window.page.runtime().focusRouter().focusVisible(target),"Tab按钮缺键盘焦点线索"));
        int keyboardEdge=edgePixel(window,surface);
        require(pointerEdge!=keyboardEdge,"正式装饰未区分鼠标与Tab焦点像素："+name);
        press(window,events,GLFW_KEY_ENTER,0);
        require(focused(window)==target&&edgePixel(window,surface)==keyboardEdge,"键盘激活未保持正式焦点框："+name);
    }
    private static int edgePixel(DesktopWindow window,SceneNode surface) throws Exception {
        settle(window);
        window.fonts.beginPresentation(1_000_001);try{window.replay(window.lastPlan);}finally{window.fonts.endPresentation();}
        BufferedImage image=DesktopTestReadback.readBack(window);
        club.heiqi.uilib.ui.scene.layout.LayoutBox box=geometry(window,surface).box;
        return image.getRGB(box.x+box.width/2,box.y);
    }

    /** 真实把手输入替代旧相邻按钮，后续原应用/保存/重载检查验证拖动确实影响草稿顺序。 */
    private static void fontDrag(DesktopWindow w,DesktopTestInput input,boolean cancel) throws Exception {
        List<SceneGeometryView.Entry> handles=geometry(w).ordered().stream().filter(e -> "::".equals(e.node.text())&&hit(e).height>0)
                .collect(java.util.stream.Collectors.toList());
        require(handles.size()>=2,"字体来源没有两个可见拖动把手");
        club.heiqi.uilib.ui.scene.layout.LayoutBox first=hit(handles.get(0)),second=geometry(w,handles.get(1).node.parent()).box;
        int x=first.x+first.width/2,y=first.y+first.height/2,target=second.y+second.height-3;
        input.point(x,y);input.mouse.invoke(w.handle(),0,GLFW_PRESS,0);w.frame();
        input.point(x+1,target);w.frame();
        screenshot(w,w.configPath.getParent().resolve(cancel?"settings-font-drag-cancel.png":"settings-font-drag-active.png"));
        if(cancel){input.key.invoke(w.handle(),GLFW_KEY_ESCAPE,0,GLFW_PRESS,0);input.key.invoke(w.handle(),GLFW_KEY_ESCAPE,0,GLFW_RELEASE,0);w.frame();}
        input.mouse.invoke(w.handle(),0,GLFW_RELEASE,0);w.frame();
        require(visible(w,"完成字体编辑"),"字体拖动错误关闭模态");
    }
    private static List<String> current(DesktopWindow w){final List<String>[] result=new List[1];w.domain.execute(() -> result[0]=new ArrayList<>(w.configuration.get(w.fontCandidates)));return result[0];}
    private static ThemeMode currentTheme(DesktopWindow w){final ThemeMode[] result={null};w.domain.execute(() -> result[0]=w.configuration.get(ThemeConfiguration.KEY));return result[0];}
    private static int titleInk(DesktopWindow w){return w.lastPlan.commands().stream().filter(c -> c.kind==PaintCommand.Kind.TEXT&&"QZ  /  UILib 设置".equals(c.text.logicalText)).findFirst().orElseThrow(() -> new AssertionError("正式设置标题缺失")).color;}
    // 断言呈现的逻辑内容；TextInput 入口允许 originalText 为空，不能把可选原串当显示文字。
    private static boolean hasText(DesktopWindow w,String text){return geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&text.equals(e.text.logicalText));}
    private static boolean hasTextContaining(DesktopWindow w,String text){return geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&e.text.logicalText.contains(text)&&hit(e).width>0&&hit(e).height>0);}
    private static boolean visible(DesktopWindow w,String text){return geometry(w).ordered().stream().anyMatch(e -> e.text!=null&&text.equals(e.text.logicalText)&&hit(e).width>0&&hit(e).height>0);}
    private static void screenshot(DesktopWindow w,Path path) throws Exception {
        // 新内容挂载后字形按正式异步管线到达，取证等待连续稳定，不能把首帧缺字当最终画面。
        settle(w);
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