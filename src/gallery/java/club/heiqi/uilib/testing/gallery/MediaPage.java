package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneMediaControls;
import club.heiqi.uilib.ui.scene.control.SceneFilePicker;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneVideo;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 共享媒体案例只编排正式会话与控件，离页关闭自己的当前播放，不拥有宿主共享配置。 */
final class MediaPage {
    private final ScenePageContext page;
    private final GalleryEnvironment environment;
    private final ReadableSignal<SceneTheme> theme;
    private final Signal<List<SceneNode>> content;
    private final Signal<String> message;
    private final SceneEditState address;
    private final Signal<Boolean> picking;
    private Path pickerDirectory;
    private MediaSession current;
    MediaPage(ScenePageContext page,GalleryEnvironment environment,ReadableSignal<SceneTheme> theme) {
        this.page=page;this.environment=environment;this.theme=theme;
        content=page.domain().signal(Collections.emptyList());message=page.domain().signal("选择本地视频，输入媒体地址，或打开内置示例。");
        address=new SceneEditState(page.domain(),"");picking=page.domain().signal(false);
        pickerDirectory=environment.initialMediaDirectory();page.own(this::stop);
    }
    SceneNode create(Runnable back) {
        club.heiqi.uilib.ui.scene.control.SceneAppearance style=new club.heiqi.uilib.ui.scene.control.SceneAppearance(page,theme);
        SceneNode root=new SceneNode().size(Size.fill(1),Size.fill(1)).padding(28).gap(20).scrollable(true).own(style.scope);
        root.add(GalleryStyle.header(page,theme,"媒体播放","选择一个来源，让声音与画面在这里继续。",back));
        SceneNode sourceCard=GalleryStyle.card(theme).gap(12);
        sourceCard.add(GalleryStyle.label(page,()->"媒体来源",theme,18,false));
        SceneNode addressField=SceneTextField.create(page,address,()->style.colors.get().editorTheme(),()->false,submission->{
            if(openAddress(submission.text))address.accept(submission,submission.text.trim());
            else address.reject(submission,message.get());
        },SceneTextField.SubmitPolicy.CONFIRM,()->"本地路径、file URI 或 HTTP / HLS 地址",false);
        sourceCard.add(style.field(addressField));
        SceneNode actions=GalleryStyle.centeredRow().size(Size.fill(1),Size.natural()).gap(8).scrollable(true);
        actions.add(GalleryStyle.primary(page,()->"打开媒体",theme,()->openAddress(address.snapshot().get().text)));
        actions.add(GalleryStyle.button(page,()->"选择本地视频",theme,()->picking.set(true)));
        actions.add(GalleryStyle.button(page,()->"内置示例",theme,()->{
            try{open(environment.exampleSource());}catch(Exception failure){message.set("无法打开示例："+failure.getMessage());}
        }));
        actions.add(GalleryStyle.button(page,()->"停止",theme,()->{stop();message.set("已停止播放。");}));
        sourceCard.add(actions).add(GalleryStyle.label(page,message,theme,13,true));
        root.add(GalleryStyle.frame(sourceCard,theme));
        SceneNode empty=GalleryStyle.card(theme).padding(36).gap(12)
                .add(GalleryStyle.label(page,()->"准备好，按下播放",theme,24,false))
                .add(GalleryStyle.label(page,()->"打开本地文件或粘贴媒体地址，也可以先体验内置示例。",theme,15,true));
        SceneNode blank=GalleryStyle.frame(empty,theme).bind(NodeBinding.value(()->content.get().isEmpty(),SceneNode::visible));
        root.add(blank);
        SceneNode player=new SceneNode().size(Size.fill(1),Size.natural()).gap(16);
        player.bind(NodeBinding.ownedChildren(content));root.add(player);
        if(environment.media()==null)message.set("当前入口未提供媒体播放，仍可浏览其他场景。");
        // 选择器与正文由同一结构声明拥有；普通流只包含正文，所属层由正式引擎占用视口。
        SceneNode stage=new SceneNode().size(Size.fill(1),Size.fill(1));
        stage.bind(NodeBinding.repeat(()->picking.get()?Arrays.asList(false,true):Collections.singletonList(false),
                value->value,item->item.get()?SceneFilePicker.create(page,pickerDirectory,theme,selected->{
                    picking.set(false);pickerDirectory=selected.getParent();
                    try {
                        MediaSource source=MediaSource.of(selected.toUri());
                        open(source);address.reset(source.uri.toString());
                    }catch(Exception failure){message.set("无法打开本地视频："+failure.getMessage());}
                },()->picking.set(false)):root));
        return stage;
    }
    private boolean openAddress(String raw) {
        try{open(environment.resolveSource(raw));return true;}
        catch(Exception failure){message.set("无法打开媒体："+failure.getMessage());return false;}
    }
    private void open(MediaSource source) {
        MediaService service=environment.media();
        if(service==null)throw new IllegalStateException("当前入口未提供媒体播放");
        stop();MediaSession opened=service.open(source,false);
        try {
            SceneNode body=new SceneNode().size(Size.fill(1),Size.natural()).gap(12);
            SceneNode video=SceneVideo.create(page,opened).size(Size.fill(1),Size.fill(1)).background(0xFF09090B);
            body.add(new SceneNode().size(Size.fill(1),Size.natural()).control(new Stage(theme)).add(video));
            body.add(GalleryStyle.frame(SceneMediaControls.create(page,opened,theme).padding(20),theme));
            current=opened;content.set(Collections.singletonList(body));message.set("使用下方控制暂停播放、调整音量或定位。");opened.play();
        }catch(RuntimeException|Error failure){current=null;try{opened.close();}catch(Throwable next){failure.addSuppressed(next);}throw failure;}
    }
    /** 播放画布按当前宽度与字号分配有界16:9空间，视频仍自行fit，不修改实际帧比例。 */
    private static final class Stage implements club.heiqi.uilib.ui.scene.control.SceneControl {
        final ReadableSignal<SceneTheme> theme;Stage(ReadableSignal<SceneTheme> theme){this.theme=theme;}
        int height(int width){return Math.min(Math.max(0,(int)Math.min(Integer.MAX_VALUE,(long)width*9/16)),Math.round(theme.get().textStyle.logicalEm*22));}
        public java.util.List<ReadableSignal<?>> measureDependencies(SceneNode node){return Collections.singletonList(theme);}
        public club.heiqi.uilib.ui.scene.layout.MeasuredSize measure(SceneNode n,club.heiqi.uilib.ui.scene.layout.Constraints c,club.heiqi.uilib.ui.scene.text.SceneTextMeasurer text){
            return new club.heiqi.uilib.ui.scene.layout.MeasuredSize(0,height(c.availableWidth));
        }
        public club.heiqi.uilib.ui.scene.layout.ChildPlacements arrange(SceneNode n,club.heiqi.uilib.ui.scene.layout.LayoutBox box,club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer children){
            SceneNode child=children.flowChildren().get(0);children.measureChild(child,new club.heiqi.uilib.ui.scene.layout.Constraints(box.width,box.height));
            return new club.heiqi.uilib.ui.scene.layout.ChildPlacements(Collections.singletonList(new club.heiqi.uilib.ui.scene.layout.ChildPlacements.Entry(child,new club.heiqi.uilib.ui.scene.layout.LayoutBox(0,0,box.width,box.height))));
        }
        public void paint(SceneNode n,club.heiqi.uilib.ui.scene.layout.SceneGeometryView g,club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder p){}
    }
    void stop() {
        picking.set(false);
        MediaSession previous=current;current=null;content.set(Collections.emptyList());
        if(previous!=null)previous.close();
    }
}
