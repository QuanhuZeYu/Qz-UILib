package club.heiqi.uilib.host.desktop;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.gallery.GalleryMediaResources;
import club.heiqi.uilib.testing.gallery.TestGallery;
import club.heiqi.uilib.testing.media.MediaFixture;
import club.heiqi.uilib.host.media.IsolatedMediaAdapters;
import club.heiqi.uilib.ui.media.MediaBudget;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.scene.control.SceneVideo;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.glfwSetWindowSize;

/** 用户从目录进入媒体页，经正式GLFW输入操作与actualswap验证，共用广场、正式媒体服务及短样本。 */
public final class DesktopGalleryMediaVerification extends TestApplication {
    private DesktopWindow window;
    private DesktopTestInput input;
    private SceneNode sourceEditor;
    private long swaps;
    private club.heiqi.uilib.ui.reactive.Owner pickerObservation;
    private final java.util.Set<Thread> pickerUiThreads=java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<Thread,Boolean>());
    private void frames(int count) { for(int i=0;i<count;i++){window.frame();swaps+=window.mediaPresentations().size();} }
    private void await(BooleanSupplier condition,String reason) throws Exception {
        long deadline=System.nanoTime()+5_000_000_000L;
        while(!condition.getAsBoolean()&&System.nanoTime()<deadline){frames(1);Thread.sleep(5);}
        if(!condition.getAsBoolean()) {
            DesktopGalleryEvidence.saveGeometry(window,Paths.get(arguments()[0]).resolve("failure.geometry.json"));
            javax.imageio.ImageIO.write(capture(),"png",Paths.get(arguments()[0]).resolve("failure.png").toFile());
            MediaSnapshot s=snapshot();System.out.println("WAIT reason="+reason+" snapshot="+(s==null?"null":s.state+" "+s.positionNanos+" fail="+(s.failure==null?"none":s.failure.cause))+" native="+IsolatedMediaAdapters.resources());
            System.out.println("TEXT "+window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.TEXT).map(c->c.text.logicalText).collect(Collectors.joining(" | ")));
        }
        require(condition.getAsBoolean(),reason);
    }
    private MediaSnapshot snapshot() {
        final MediaSnapshot[] value={null};
        window.domain.execute(()->DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.control() instanceof SceneVideo)
                .findFirst().ifPresent(e->value[0]=SceneVideo.snapshot(e.node).get()));return value[0];
    }
    private long sessionId() {return window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.VIDEO).findFirst().map(c->c.video.frame.key.sessionId).orElse(-1L);}
    private boolean text(String value) {return window.lastPlan.commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.contains(value));}
    private boolean exactText(String value) {return window.lastPlan.commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.equals(value));}
    private List<SceneNode> fields() {
        List<SceneNode> visible=DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.control() instanceof club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget).map(e->e.node).collect(Collectors.toList());
        if(sourceEditor!=null&&sourceEditor.runtime()==null)sourceEditor=null;
        if(sourceEditor==null&&!pickerOpen()&&!visible.isEmpty())sourceEditor=visible.get(0);
        if(sourceEditor!=null&&!visible.contains(sourceEditor))visible.add(0,sourceEditor);
        return visible;
    }
    private String editText(SceneNode field) {
        final String[] text={null};window.domain.execute(()->text[0]=((club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget)field.control()).compositionState().snapshot().get().text);return text[0];
    }
    private void type(SceneNode field,String value,boolean enter) {
        if(field==sourceEditor&&DesktopTestInput.geometry(window).get(field)==null){input.click("更换来源");frames(1);}
        input.moveTo(field);input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);
        input.key.invoke(window.handle(),GLFW_KEY_A,0,GLFW_PRESS,GLFW_MOD_CONTROL);
        if(value.isEmpty())input.key.invoke(window.handle(),GLFW_KEY_BACKSPACE,0,GLFW_PRESS,0);
        else value.codePoints().forEach(c->input.character.invoke(window.handle(),c));
        if(enter)input.key.invoke(window.handle(),GLFW_KEY_ENTER,0,GLFW_PRESS,0);frames(1);
        if(inPicker(field))require(value.equals(editText(field)),"路径输入未生效：期望="+value+" 实际="+editText(field));
    }
    private static boolean inPicker(SceneNode node) {
        for(SceneNode at=node;at!=null;at=at.parent())if(at.modal())return true;
        return false;
    }
    private void observePickerThread() {
        SceneNode field=pickerField();
        window.domain.execute(()->{
            pickerObservation=window.domain.owner();
            club.heiqi.uilib.ui.scene.edit.SceneEditState state=((club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget)field.control()).compositionState();
            window.domain.effect(pickerObservation,()->{state.snapshot().get();pickerUiThreads.add(Thread.currentThread());});
        });
    }
    private void closePickerObservation() {
        if(pickerObservation!=null)window.domain.execute(()->{pickerObservation.close();pickerObservation=null;});
    }
    private boolean pickerWorkersGone() {return Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&t.getName().startsWith("uilib-file-picker-"));}
    private boolean pickerOpen() {return DesktopTestInput.geometry(window).ordered().stream().anyMatch(e->e.node.modal());}
    private boolean pickerButtonEnabled(String label) {return DesktopTestInput.geometry(window).ordered().stream().anyMatch(e->inPicker(e.node)&&e.node.clickAction()!=null&&e.node.text().equals(label)&&e.node.enabled());}
    private SceneNode pickerField() {return fields().stream().filter(DesktopGalleryMediaVerification::inPicker).findFirst().orElseThrow(()->new AssertionError("选择器没有正式路径字段"));}
    private void pickerEntry(String name) {
        String label=DesktopTestInput.geometry(window).ordered().stream().filter(e->inPicker(e.node)&&e.node.clickAction()!=null&&e.node.text().endsWith(name))
                .map(e->e.node.text()).findFirst().orElseThrow(()->new AssertionError("选择器没有目录条目："+name));
        input.click(label);
    }
    private java.util.Set<String> listedPage() {
        return DesktopTestInput.geometry(window).ordered().stream().filter(e->inPicker(e.node)&&e.node.clickAction()!=null&&e.node.text().contains("分页文件 "))
                .map(e->e.node.text()).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
    private void requireNameTooltip(String name) {
        List<PaintCommand> commands=window.lastPlan.commands();
        PaintCommand last=commands.get(commands.size()-1);
        require(last.kind==PaintCommand.Kind.TEXT&&name.equals(last.text.logicalText)
                &&last.text.lines().stream().map(line->line.text).collect(Collectors.joining()).equals(name),"长名称没有完整提示："+name);
    }
    private void directControls()throws Exception{
        if(snapshot().state==MediaState.PLAYING)input.click("暂停");await(()->snapshot().state==MediaState.PAUSED,"直接控制前暂停失败");
        List<SceneNode> sliders=DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.control()!=null&&e.node.control().getClass().getName().contains("SceneSlider")).map(e->e.node).collect(Collectors.toList());require(sliders.size()==2,"缺少正式时间轴/音量滑杆");
        SceneNode timeline=sliders.get(0),volume=sliders.get(1);club.heiqi.uilib.ui.scene.layout.LayoutBox box=DesktopTestInput.geometry(window,timeline).box;long before=snapshot().generation;
        input.point(box.x+box.width*.25,box.y+box.height/2.0);input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);frames(1);
        input.point(box.x+box.width*.65,box.y+box.height/2.0);frames(2);require(snapshot().generation==before,"拖动期间实际seek");
        input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);frames(1);await(()->snapshot().generation>before&&snapshot().state==MediaState.PAUSED,"释放未完成定位预览");
        double oldVolume=snapshot().volume;box=DesktopTestInput.geometry(window,volume).box;input.enqueueAt(box.x+box.width*.7,box.y+box.height/2.0);frames(1);await(()->snapshot().volume>oldVolume&&snapshot().volume>.5,"音量投影没有在后续帧更新");require(snapshot().volume>oldVolume&&snapshot().volume>.5,"音量滑杆没有沿会话调节：before="+oldVolume+" after="+snapshot().volume+" box="+box.x+","+box.y+","+box.width+","+box.height);
        SceneNode video=DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.control() instanceof SceneVideo).map(e->e.node).findFirst().get();
        input.moveTo(video);input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);frames(1);
        input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE,0,GLFW_PRESS,0);frames(1);await(()->snapshot().state==MediaState.PLAYING,"观看范围Space没有播放");
        input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE,0,GLFW_PRESS,0);frames(1);await(()->snapshot().state==MediaState.PAUSED,"观看范围Space没有暂停");
        input.click("更换来源");frames(1);input.moveTo(sourceEditor);input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);frames(1);
        long inputGeneration=snapshot().generation;input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT,0,GLFW_PRESS,0);input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE,0,GLFW_PRESS,0);frames(1);require(snapshot().generation==inputGeneration&&snapshot().state==MediaState.PAUSED,"编辑字段快捷键串到播放器");
        input.click("收起来源");frames(1);input.click("播放");await(()->snapshot().state==MediaState.ENDED,"短片没有结束");
        long endGeneration=snapshot().generation;input.click("重新播放");await(()->snapshot().generation>endGeneration&&snapshot().state==MediaState.PLAYING&&snapshot().positionNanos<snapshot().info.durationNanos,"结束态重播没有新输出");
        System.out.println("MEDIA_DIRECT actualCallbacks=true sliderPreviewOnly=true releaseSeekOnce=true volume=true focusScopedKeys=true editorPriority=true endedReplay=true");
    }
    private boolean focused(SceneNode target) {
        final boolean[] result={false};
        window.domain.execute(()->result[0]=window.page.runtime().focusRouter().focused().get()==target
                &&window.page.runtime().focusRouter().focusVisible(target));
        return result[0];
    }
    private club.heiqi.uilib.ui.scene.control.SceneScrollViewport.Snapshot pathViewport(SceneNode field) {
        return field.control().scrollViewport(field,SceneNode.Axis.ROW,DesktopTestInput.geometry(window));
    }
    private void dragPathScrollbar() {
        SceneNode field=pickerField();String before=editText(field);
        input.moveTo(field);input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);frames(1);
        SceneNode bar=field.parent().children().stream().filter(n->n!=field).findFirst().orElseThrow(()->new AssertionError("路径框缺少横向条"));
        club.heiqi.uilib.ui.scene.layout.LayoutBox box=DesktopTestInput.hit(DesktopTestInput.geometry(window,bar));
        require(box.width>0&&box.height>0,"路径条没有可交互区域");
        int centerY=box.y+box.height/2;
        input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_HOME,0,GLFW_PRESS,0);
        input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_HOME,0,GLFW_RELEASE,0);frames(1);
        require(pathViewport(field).offset==0,"路径编辑器HOME没有显露开头");
        input.point(box.x+3,centerY);input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);frames(1);
        input.point(box.x+box.width+50,centerY);frames(1);input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);frames(1);
        require(pathViewport(field).offset==pathViewport(field).maximum,"路径条未拖到末端 offset="+pathViewport(field).offset+" maximum="+pathViewport(field).maximum+" box="+box);
        input.point(box.x+box.width-3,centerY);input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);frames(1);
        input.point(box.x-50,centerY);frames(1);input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);frames(1);
        require(pathViewport(field).offset==0&&before.equals(editText(field)),"路径条未回到起点或改变了草稿");
        window.domain.execute(()->require(window.page.runtime().focusRouter().focused().get()==field,"拖动路径条丢失编辑焦点"));
        System.out.println("FILE_PICKER_PATH_BAR width="+box.width+" height="+box.height+" bothEnds=true draftAndFocusPreserved=true");
    }
    private static void deleteTree(Path root) throws java.io.IOException {
        if(root==null)return;
        try(java.util.stream.Stream<Path> paths=Files.walk(root)) {
            for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).collect(Collectors.toList()))Files.deleteIfExists(path);
        }
    }
    private void baseline(MediaService service,String stage) {
        require(service.activeSessions()==0,stage+"会话未关闭");
        require(IsolatedMediaAdapters.resources().activeHandles()==0,stage+"native未归零: "+IsolatedMediaAdapters.resources());
        require(window.mediaViews().activeConsumers()==0,stage+"consumer未解除");
        club.heiqi.uilib.host.client.gl.GlVideoResources.Snapshot gpu=window.mediaResources();
        System.out.println("BASELINE stage="+stage+" sessions="+service.activeSessions()+" native="+IsolatedMediaAdapters.resources()+" textures="+gpu.textureCount+" bytes="+gpu.retainedBytes+" scratch="+gpu.scratchBytes);
        require(gpu.textureCount==0&&gpu.retainedBytes==0&&gpu.scratchBytes==0,stage+"视频GPU资源仍被持有");
        require(Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive()&&(t.getName().startsWith("uilib-media-decoder-")||t.getName().startsWith("uilib-media-output-"))),stage+"worker未关闭");
    }
    private void viewingEvidence(Path output)throws Exception {
        long session=sessionId(),generation=snapshot().generation;
        type(sourceEditor,"尚未打开的新来源草稿",false);input.click("收起来源");frames(1);
        java.util.List<String> geometry=new java.util.ArrayList<>();
        for(int[] viewport:new int[][]{{1200,1000},{1400,900},{640,900},{800,480}}){
            glfwSetWindowSize(window.handle(),viewport[0],viewport[1]);frames(3);DesktopTestFrames.settle(window);
            club.heiqi.uilib.ui.scene.layout.LayoutBox normal=videoBox();
            require(normal.height>0&&normal.y>=0&&normal.y+normal.height<=window.height(),"画面没有留在实际视口内");
            if(window.height()>=900)require(normal.height>=window.height()/2,"正常观看没有优先分配画面空间");
            require(DesktopTestInput.geometry(window).ordered().stream().anyMatch(e->e.node.clickAction()!=null&&(e.node.text().equals("播放")||e.node.text().equals("暂停"))&&e.box.y+e.box.height<=window.height()),"主要播放控制被挤出视口");
            input.point(window.width()-2,window.height()-2);frames(1);DesktopTestFrames.settle(window);
            String size=window.width()+"x"+window.height();
            javax.imageio.ImageIO.write(capture(),"png",output.resolve("media-view-"+size+".png").toFile());
            DesktopGalleryEvidence.saveGeometry(window,output.resolve("media-view-"+size+".geometry.json"));
            input.click("放大观看");frames(2);DesktopTestFrames.settle(window);
            club.heiqi.uilib.ui.scene.layout.LayoutBox enlarged=videoBox();
            require(enlarged.height>normal.height,"放大观看没有增加视频可用高度");
            require(sessionId()==session&&snapshot().generation==generation,"空间变化重开或定位了会话");
            require(editText(sourceEditor).equals("尚未打开的新来源草稿"),"观看切换覆盖来源草稿");
            input.point(window.width()-2,window.height()-2);frames(1);DesktopTestFrames.settle(window);
            javax.imageio.ImageIO.write(capture(),"png",output.resolve("media-enlarged-"+size+".png").toFile());
            geometry.add("{\"width\":"+window.width()+",\"height\":"+window.height()+",\"normalVideoHeight\":"+normal.height+",\"enlargedVideoHeight\":"+enlarged.height+"}");
            input.click("退出放大");frames(1);
        }
        Files.write(output.resolve("media-viewports.json"),("["+String.join(",",geometry)+"]").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        glfwSetWindowSize(window.handle(),1200,1000);frames(3);DesktopTestFrames.settle(window);
        System.out.println("MEDIA_VIEWPORT remainingSpace=true enlarged=true controlsVisible=true fourViewports=true sourceDraftPreserved=true sameSessionAndGeneration=true");
    }
    private club.heiqi.uilib.ui.scene.layout.LayoutBox videoBox(){
        return DesktopTestInput.geometry(window).ordered().stream().filter(e->e.node.control() instanceof SceneVideo).findFirst().orElseThrow(()->new AssertionError("没有实际视频控件")).box;
    }
    @Override protected void runTests() throws Exception {
        Path output=Paths.get(arguments()[0]);Files.createDirectories(output);
        scenario("真实共享广场媒体入口、示例、控件和离页关闭",()->{
            Path sample;
            final Path[] localFile={null},localDirectory={null};
            int pickerOpens=0;
            try(GalleryMediaResources media=new GalleryMediaResources(new MediaService(IsolatedMediaAdapters.decoderFactory(),IsolatedMediaAdapters.audioSinkFactory(),MediaBudget.DEFAULT));
                    MediaFixture fixture=new MediaFixture();
                    AutoCloseable localCleanup=()->deleteTree(localDirectory[0])) {
                MediaService service=media.service();
                try(DesktopWindow opened=DesktopWindow.openWithServices(output.resolve("gallery.yaml"),service,true,
                        services->DesktopGallery.createGallery(services,TestGallery.Page.HOME,media));
                        AutoCloseable observationCleanup=this::closePickerObservation) {
                    window=opened;glfwSetWindowSize(window.handle(),1200,1000);frames(12);input=new DesktopTestInput(window);
                    require(service.activeSessions()==0&&IsolatedMediaAdapters.resources().runtimeLoaders==0&&!pickerOpen(),"目录预热了媒体native或自动弹文件选择");
                    input.click("打开媒体播放");frames(2);
                    require(service.activeSessions()==0&&text("媒体播放"),"进入媒体页自动打开或未呈现");
                    sourceEditor=fields().get(0);
                    input.click("内置示例");await(()->snapshot()!=null&&snapshot().statistics.presentedFrames>0&&snapshot().positionNanos>30_000_000L,"示例未actualswap/device播放");
                    require(!snapshot().muted&&snapshot().volume>0,"显式打开媒体没有默认有声意图");input.click("静音");await(()->snapshot().muted,"主动静音未生效");sample=Paths.get(media.exampleSource().uri);require(Files.exists(sample),"示例未提取");
                    System.out.println("FILE source="+media.exampleSource()+" exists="+Files.exists(sample)+" nativeInput="+sample);
                    long firstSwaps=swaps;input.click("暂停");await(()->snapshot().state==MediaState.PAUSED,"暂停未生效");
                    input.click("精确控制");frames(1);
                    double beforeInvalidVolume=snapshot().volume;long beforeInvalidSeek=snapshot().generation;
                    type(fields().get(1),"2",true);await(()->text("音量须为0到1"),"非法音量没有在当前页面显示拒绝原因");
                    type(fields().get(2),"999999",true);await(()->text("定位目标不在当前有效范围"),"越界定位没有在当前页面显示拒绝原因");
                    require(snapshot().volume==beforeInvalidVolume&&snapshot().generation==beforeInvalidSeek,"非法输入改变了播放权威值");
                    require(text("可定位"),"当前来源可定位范围没有呈现");
                    long pendingGlyphs=window.fonts.rasterizer().stats().pending;
                    javax.imageio.ImageIO.write(capture(),"png",output.resolve("media-validation-initial.png").toFile());
                    DesktopTestFrames.settle(window);
                    DesktopGalleryEvidence.saveGeometry(window,output.resolve("media-validation.geometry.json"));
                    java.util.List<String> validationText=new java.util.ArrayList<>();
                    for(PaintCommand command:window.lastPlan.commands())if(command.kind==PaintCommand.Kind.TEXT&&command.text.logicalText.contains("定位目标"))
                        validationText.add("box="+command.box+" clip="+command.clip+" lines="+command.text.lines().stream().map(line->line.text+"@"+line.x+","+line.y).collect(Collectors.joining(" | ")));
                    Files.write(output.resolve("media-validation-text.txt"),validationText,java.nio.charset.StandardCharsets.UTF_8);
                    java.awt.image.BufferedImage validationImage=capture();
                    PaintCommand rejection=window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.TEXT&&c.text.logicalText.equals("定位目标不在当前有效范围")).findFirst().orElseThrow(()->new AssertionError("缺少定位拒绝文字"));
                    require(rejection.text.lines().stream().map(line->line.text).collect(Collectors.joining()).equals(rejection.text.logicalText),"定位拒绝文字投影不完整");
                    int firstInk=validationImage.getWidth(),lastInk=-1;
                    club.heiqi.uilib.ui.scene.layout.LayoutBox errorBox=rejection.clip==null?rejection.box:rejection.box.intersect(rejection.clip);
                    for(int y=Math.max(0,errorBox.y);y<Math.min(validationImage.getHeight(),errorBox.y+errorBox.height);y++)
                        for(int x=Math.max(0,errorBox.x);x<Math.min(validationImage.getWidth(),errorBox.x+errorBox.width);x++){
                            int color=validationImage.getRGB(x,y),red=color>>>16&255,green=color>>>8&255,blue=color&255;
                            if(red>green+40&&red>blue+40){firstInk=Math.min(firstInk,x);lastInk=Math.max(lastInk,x);}
                        }
                    require(lastInk-firstInk>errorBox.width/2,"定位拒绝只有部分字形实际可见");
                    System.out.println("MEDIA_VALIDATION illegalInputsRejected=true authorityUnchanged=true completeProjection=true redInkWidth="+(lastInk-firstInk+1)+" boxWidth="+errorBox.width+" pendingBeforeSettle="+pendingGlyphs+" pendingAfter="+window.fonts.rasterizer().stats().pending);
                    javax.imageio.ImageIO.write(validationImage,"png",output.resolve("media-validation.png").toFile());
                    type(fields().get(1),"0.01",true);input.click("取消静音");await(()->!snapshot().muted,"静音控制未生效");input.click("静音");
                    long generation=snapshot().generation;type(fields().get(2),"0",true);await(()->snapshot().generation>generation&&snapshot().state==MediaState.PAUSED,"合法零秒定位未预览");
                    input.click("播放");await(()->swaps>firstSwaps+2&&snapshot().positionNanos>100_000_000L,"恢复播放无新swap");
                    input.click("收起精确控制");frames(1);directControls();viewingEvidence(output);
                    DesktopGalleryEvidence.saveGeometry(window,output.resolve("media.geometry.json"));
                    javax.imageio.ImageIO.write(capture(),"png",output.resolve("media.png").toFile());
                    long sameSession=sessionId();SceneNode sameSourceField=fields().get(0);
                    window.domain.execute(()->window.configuration.set(club.heiqi.uilib.ui.scene.control.ThemeConfiguration.KEY,club.heiqi.uilib.ui.scene.control.ThemeMode.DARK));frames(3);
                    javax.imageio.ImageIO.write(capture(),"png",output.resolve("media-dark.png").toFile());
                    glfwSetWindowSize(window.handle(),640,900);frames(3);
                    javax.imageio.ImageIO.write(capture(),"png",output.resolve("media-narrow.png").toFile());
                    require(sessionId()==sameSession&&fields().get(0)==sameSourceField,"主题/窗口变化重建了播放器或来源草稿");
                    window.domain.execute(()->window.configuration.set(club.heiqi.uilib.ui.scene.control.ThemeConfiguration.KEY,club.heiqi.uilib.ui.scene.control.ThemeMode.LIGHT));
                    glfwSetWindowSize(window.handle(),1200,1000);frames(3);
                    localDirectory[0]=Files.createTempDirectory("qz 文件选择 中文 +");
                    Path browse=Files.createDirectory(localDirectory[0].resolve("浏览目录 中文 +"));
                    Files.createDirectory(browse.resolve("空目录"));
                    Path many=Files.createDirectory(localDirectory[0].resolve("分页目录"));
                    int directoryEntries=150;
                    for(int i=0;i<directoryEntries;i++)Files.createFile(many.resolve(String.format(java.util.Locale.ROOT,"分页文件 %03d.ViDeO",i)));
                    localFile[0]=browse.resolve(arguments().length>1?"任意视频 +中文.MP4":"任意视频 +中文.ViDeO");
                    Files.copy(arguments().length>1?Paths.get(arguments()[1]):sample,localFile[0]);
                    club.heiqi.uilib.ui.media.MediaSource chosen=club.heiqi.uilib.testing.host.LocalMediaFiles.resolve(localFile[0].toString());
                    type(fields().get(0),sample.toString(),false);input.click("打开媒体");await(()->snapshot()!=null&&snapshot().state==MediaState.PLAYING&&snapshot().positionNanos>30_000_000L,"打开选择器前媒体未播放");
                    long duringPicker=sessionId(),positionBeforePicker=snapshot().positionNanos;
                    input.click("选择本地视频");pickerOpens++;await(this::pickerOpen,"未出现UILib模态文件选择器");
                    observePickerThread();
                    input.click("根目录");await(()->text("根目录 / 盘符"),"根目录入口没有读取实际盘符");
                    pickerEntry(localDirectory[0].getRoot().toString());
                    type(pickerField(),localDirectory[0].toString(),true);await(()->text("浏览目录 中文 +"),"实际目录没有进入自有选择器");
                    input.click("停止");frames(2);
                    require(pickerOpen()&&sessionId()==duringPicker&&service.activeSessions()==1,"模态指针穿透后台停止了媒体");
                    await(()->snapshot().positionNanos>positionBeforePicker,"选择器打开期间媒体没有继续推进");
                    // 背景按钮的屏幕坐标可能落在模态导航上；隔离只要求后台不激活，随后显式恢复目标目录。
                    type(pickerField(),localDirectory[0].toString(),true);await(()->text("浏览目录 中文 +"),"隔离验证后不能导航回目标目录");
                    pickerEntry("浏览目录 中文 +");await(()->text(localFile[0].getFileName().toString()),"目录条目不能导航");
                    pickerEntry("空目录");await(()->exactText("空目录"),"空目录没有反馈");
                    input.click("上一级");await(()->text(localFile[0].getFileName().toString()),"上级目录没有恢复列表");
                    input.click("后退");await(()->exactText("空目录"),"后退没有恢复目录");input.click("前进");await(()->text(localFile[0].getFileName().toString()),"前进没有恢复浏览位置");
                    Path refreshed=browse.resolve("刷新后出现.ViDeO");Files.createFile(refreshed);
                    input.click("刷新");await(()->text(refreshed.getFileName().toString()),"目录刷新没有显示新增文件");
                    type(pickerField(),browse.resolve("不存在的目录").toString(),true);await(()->text("无法"),"不存在路径没有可见反馈");
                    type(pickerField(),many.toString(),true);await(()->!listedPage().isEmpty(),"大目录没有首批条目");
                    java.util.Set<String> firstPage=listedPage();
                    require(firstPage.size()<directoryEntries,"大目录一次挂载了整个目录");
                    input.click("下一页");await(()->!listedPage().isEmpty()&&java.util.Collections.disjoint(firstPage,listedPage()),"下一页没有读取独立批次");
                    int nextPageSize=listedPage().size();
                    input.click("上一页");await(()->listedPage().equals(firstPage),"上一页没有返回原批次");
                    List<SceneNode> pickerFields=DesktopTestInput.geometry(window).ordered().stream().filter(e->inPicker(e.node)&&e.node.control() instanceof club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget).map(e->e.node).collect(Collectors.toList());
                    require(pickerFields.size()==2,"路径与当前目录查询字段未分开");type(pickerFields.get(1),"149",true);await(()->listedPage().equals(java.util.Collections.singleton("分页文件 149.ViDeO")),"搜索没有覆盖当前目录后续页");
                    input.click("清除");await(()->listedPage().equals(firstPage),"清除没有恢复首个结果页");
                    pickerEntry("分页文件 000.ViDeO");input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_END,0,GLFW_PRESS,0);frames(1);
                    SceneNode[] listFocus={null};window.domain.execute(()->listFocus[0]=window.page.runtime().focusRouter().focused().get());require(listFocus[0].text().equals("分页文件 063.ViDeO")&&DesktopTestInput.hit(DesktopTestInput.geometry(window,listFocus[0])).height>0,"列表End没有移动并显露末项");
                    input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_HOME,0,GLFW_PRESS,0);frames(1);window.domain.execute(()->listFocus[0]=window.page.runtime().focusRouter().focused().get());require(listFocus[0].text().equals("分页文件 000.ViDeO"),"列表Home没有返回首项");
                    "分页文件 05".codePoints().forEach(cp->input.character.invoke(window.handle(),cp));frames(1);window.domain.execute(()->listFocus[0]=window.page.runtime().focusRouter().focused().get());require(listFocus[0].text().startsWith("分页文件 05"),"列表前缀未沿普通文本跳转");
                    input.key.invoke(window.handle(),GLFW_KEY_ENTER,0,GLFW_PRESS,0);frames(1);require(editText(pickerField()).endsWith(listFocus[0].text()),"Enter没有选择明确的当前焦点文件");
                    System.out.println("FILE_PICKER_QUERY actualCallbacks=true wholeDirectorySearch=true clearRestores=true history=true homeEndReveal=true prefix=true enterSelects=true");
                    type(pickerField(),browse.toString(),true);await(()->text(localFile[0].getFileName().toString()),"不能返回中文目录");
                    Path longName=browse.resolve("项目资料与设计规范_这是一个用于确认窄窗省略与选择身份的长文件名称_中文+空格.ViDeO");
                    Files.createFile(longName);input.click("刷新");await(()->text(longName.getFileName().toString()),"长文件名没有进入实际列表");
                    pickerEntry(localFile[0].getFileName().toString());frames(1);
                    input.click(DesktopTestInput.geometry(window).ordered().stream().filter(e->inPicker(e.node)&&e.node.text().equals("选择本地文件")).findFirst().orElseThrow(()->new AssertionError("缺少选择器标题")));
                    input.point(0,0);frames(2);
                    javax.imageio.ImageIO.write(capture(),"png",output.resolve("file-picker-light.png").toFile());
                    SceneNode retainedField=pickerField();
                    window.domain.execute(()->window.configuration.set(club.heiqi.uilib.ui.scene.control.ThemeConfiguration.KEY,club.heiqi.uilib.ui.scene.control.ThemeMode.DARK));
                    frames(3);require(pickerField()==retainedField,"主题变化重建路径编辑器");
                    javax.imageio.ImageIO.write(capture(),"png",output.resolve("file-picker-dark.png").toFile());
                    window.domain.execute(()->window.configuration.set(club.heiqi.uilib.ui.scene.control.ThemeConfiguration.KEY,club.heiqi.uilib.ui.scene.control.ThemeMode.LIGHT));frames(3);
                    glfwSetWindowSize(window.handle(),560,620);frames(3);
                    require(pickerField()==retainedField,"窄窗变化重建路径编辑器");
                    pickerEntry(longName.getFileName().toString());frames(1);
                    SceneNode longEntry=DesktopTestInput.geometry(window).ordered().stream()
                            .filter(e->inPicker(e.node)&&e.node.clickAction()!=null&&e.node.text().equals(longName.getFileName().toString()))
                            .map(e->e.node).findFirst().orElseThrow(()->new AssertionError("长名称条目缺失"));
                    input.point(0,0);frames(1);input.moveTo(longEntry);frames(2);requireNameTooltip(longName.getFileName().toString());
                    input.point(0,0);frames(1);
                    for(int attempt=0;attempt<DesktopTestInput.geometry(window).ordered().size()&&!focused(longEntry);attempt++) {
                        input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_TAB,0,GLFW_PRESS,0);
                        input.key.invoke(window.handle(),org.lwjgl.glfw.GLFW.GLFW_KEY_TAB,0,GLFW_RELEASE,0);frames(1);
                    }
                    require(focused(longEntry),"键盘导航未到达长名称条目");
                    requireNameTooltip(longName.getFileName().toString());
                    System.out.println("FILE_PICKER_STYLE themeWithoutRebuild=true narrow=true hoverFullName=true keyboardFullName=true");
                    DesktopGalleryEvidence.saveGeometry(window,output.resolve("file-picker-narrow.geometry.json"));
                    javax.imageio.ImageIO.write(capture(),"png",output.resolve("file-picker-narrow.png").toFile());
                    dragPathScrollbar();
                    glfwSetWindowSize(window.handle(),760,620);frames(3);
                    require(pickerOpen(),"窗口变化关闭了选择器");
                    DesktopGalleryEvidence.saveGeometry(window,output.resolve("file-picker.geometry.json"));
                    javax.imageio.ImageIO.write(capture(),"png",output.resolve("file-picker.png").toFile());
                    input.click("取消");await(()->!pickerOpen()&&pickerWorkersGone(),"取消没有结束选择器或目录工作");
                    require(sessionId()==duringPicker,"取消选择更换了当前会话");
                    require(pickerUiThreads.size()==1&&pickerUiThreads.contains(Thread.currentThread()),"目录结果在宿主线程外推动了UI："+pickerUiThreads);
                    closePickerObservation();
                    glfwSetWindowSize(window.handle(),1200,1000);frames(3);
                    input.click("选择本地视频");pickerOpens++;await(this::pickerOpen,"第二次打开选择器失败");
                    input.key.invoke(window.handle(),GLFW_KEY_ESCAPE,0,GLFW_PRESS,0);input.key.invoke(window.handle(),GLFW_KEY_ESCAPE,0,GLFW_RELEASE,0);frames(2);
                    await(()->!pickerOpen()&&pickerWorkersGone(),"ESC后目录工作未结束");
                    require(sessionId()==duringPicker&&text("媒体播放"),"ESC取消选择器误关页面或更换会话");
                    input.click("选择本地视频");pickerOpens++;await(this::pickerOpen,"确认文件前无法打开选择器");
                    type(pickerField(),browse.toString(),true);await(()->text(localFile[0].getFileName().toString()),"重新打开未读取中文目录");
                    type(pickerField(),"",false);input.click("选择文件");await(()->text("无法"),"空路径没有错误反馈");
                    require(pickerButtonEnabled("选择文件"),"空路径错误后确认被永久禁用");
                    input.click("刷新");await(()->text(localFile[0].getFileName().toString()),"空路径错误后不能继续浏览");
                    pickerEntry(refreshed.getFileName().toString());Files.delete(refreshed);input.click("选择文件");await(()->text("无法"),"确认时没有重新校验已删除文件");
                    require(pickerOpen()&&sessionId()==duringPicker,"失效文件结束选择器或改变了播放");
                    input.click("刷新");await(()->text(localFile[0].getFileName().toString()),"核验失败后不能继续浏览");
                    pickerEntry(localFile[0].getFileName().toString());frames(1);
                    require(pickerOpen()&&sessionId()==duringPicker,"文件条目在确认前换源");
                    SceneNode pendingField=pickerField();String laterDraft=browse.resolve("尚未确认的新路径.ViDeO").toString();
                    // 同一真实宿主输入批次：先确认A再编辑B，完成帧只能在这些输入之后领取worker结果。
                    input.enqueueClick("选择文件");input.moveTo(pendingField);
                    input.mouse.invoke(window.handle(),0,GLFW_PRESS,0);input.mouse.invoke(window.handle(),0,GLFW_RELEASE,0);
                    input.key.invoke(window.handle(),GLFW_KEY_A,0,GLFW_PRESS,GLFW_MOD_CONTROL);
                    laterDraft.codePoints().forEach(c->input.character.invoke(window.handle(),c));frames(1);
                    await(()->text("路径已更改")&&pickerButtonEnabled("选择文件"),"旧确认没有撤销当前草稿的完成权");
                    require(pickerOpen()&&sessionId()==duringPicker&&editText(pendingField).equals(laterDraft),"旧A核验关闭或覆盖了当前B草稿");
                    System.out.println("FILE_PICKER_STALE_CONFIRM actualCallbacks=true sameInputBatch=true laterDraftPreserved=true noSourceChange=true");
                    pickerEntry(localFile[0].getFileName().toString());frames(1);
                    input.click("选择文件");await(()->!pickerOpen()&&snapshot()!=null&&snapshot().statistics.presentedFrames>0&&snapshot().positionNanos>50_000_000L&&snapshot().source.uri.equals(chosen.uri),"自有选择器确认任意本地视频未实际播放");
                    long beforeCancel=sessionId();
                    type(fields().get(0),localFile[0].toString(),false);input.click("打开媒体");
                    await(()->snapshot()!=null&&snapshot().statistics.presentedFrames>0&&sessionId()!=beforeCancel&&snapshot().positionNanos>50_000_000L,"本地绝对路径不能播放");
                    System.out.println("LOCAL_VIDEO source="+chosen+" absolute="+localFile[0]+" pickerOpens="+pickerOpens+" pageRows="+firstPage.size()+" nextPageRows="+nextPageSize+" totalFiles="+directoryEntries+" directoryUiThreads="+pickerUiThreads.stream().map(Thread::getName).collect(Collectors.joining(","))+" cancelKeptSession=true escapeKeptPage=true modalIsolated=true refreshed=true audioPlayed="+snapshot().positionNanos);
                    DesktopGalleryEvidence.saveGeometry(window,output.resolve("local-media.geometry.json"));
                    javax.imageio.ImageIO.write(capture(),"png",output.resolve("local-media.png").toFile());
                    input.click("停止");frames(3);baseline(service,"stop");
                    type(fields().get(0),"bad address",false);input.click("打开媒体");frames(2);require(text("无法打开媒体"),"非法地址未显示反馈");
                    type(fields().get(0),sample.resolveSibling("missing-media.ts").toString(),false);input.click("打开媒体");await(()->snapshot()!=null&&snapshot().state==MediaState.FAILED,"缺失文件未显示失败");
                    require(IsolatedMediaAdapters.resources().activeHandles()==0,"缺失文件失败未清理native");
                    type(fields().get(0),fixture.uri().toString(),false);input.click("打开媒体");await(()->snapshot()!=null&&snapshot().statistics.presentedFrames>0&&snapshot().positionNanos>50_000_000L,"HTTP HLS没有正式播放");
                    long oldSession=window.lastPlan.commands().stream().filter(c->c.kind==PaintCommand.Kind.VIDEO).findFirst().get().video.frame.key.sessionId;
                    input.click("更换来源");frames(1);input.click("打开媒体");await(()->window.lastPlan.commands().stream().anyMatch(c->c.kind==PaintCommand.Kind.VIDEO&&c.video.frame.key.sessionId!=oldSession),"页内地址重开未换会话");
                    input.click("返回场景概览");frames(3);baseline(service,"home");
                    input.click("打开媒体播放");frames(2);require(service.activeSessions()==0&&text(fixture.uri().toString()),"返回媒体页未保留地址或自动播放");input.click("内置示例");await(()->snapshot()!=null&&snapshot().statistics.presentedFrames>0,"重复进入不能再次播放");
                    await(()->snapshot().state==MediaState.ENDED,"短示例未结束");input.click("内置示例");await(()->snapshot()!=null&&snapshot().state==MediaState.PLAYING&&snapshot().statistics.presentedFrames>0,"结束后不能重开示例");
                }
                require(service.activeSessions()==0&&IsolatedMediaAdapters.resources().activeHandles()==0&&pickerWorkersGone(),"关窗后资源残留");
                require(window.mediaResources().closed&&window.mediaResources().textureCount==0,"关窗GPU未关闭");
            }
            require(!Files.exists(sample)&&!Files.exists(localFile[0]),"示例或任意本地临时文件未删除");
            Files.write(output.resolve("media-summary.json"),("{\"actualSwap\":"+swaps+",\"pickerOpens\":"+pickerOpens+",\"ownScenePicker\":true,\"cancelKeptSession\":true,\"escapeKeptPage\":true,\"modalIsolated\":true,\"directoryPagingAndRefresh\":true,\"absolutePathPlayed\":true,\"httpHlsPlayed\":true,\"stopHomeReleased\":true,\"tempDeleted\":true}\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("GALLERY_MEDIA actualSwap="+swaps+" sampleAndHls=true stopped=true homeClosed=true reopened=true tempDeleted=true");
        });
    }
    private java.awt.image.BufferedImage capture() {
        int width=window.width(),height=window.height();java.nio.ByteBuffer bytes=java.nio.ByteBuffer.allocateDirect(width*height*4);
        int previous=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_READ_BUFFER);
        try{org.lwjgl.opengl.GL11.glReadBuffer(org.lwjgl.opengl.GL11.GL_FRONT);org.lwjgl.opengl.GL11.glReadPixels(0,0,width,height,org.lwjgl.opengl.GL11.GL_RGBA,org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE,bytes);}
        finally{org.lwjgl.opengl.GL11.glReadBuffer(previous);}
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=(y*width+x)*4;image.setRGB(x,height-1-y,0xFF000000|(bytes.get(i)&255)<<16|(bytes.get(i+1)&255)<<8|bytes.get(i+2)&255);}return image;
    }
    public static void main(String[] args) throws Exception {new DesktopGalleryMediaVerification().run(args);}
}
