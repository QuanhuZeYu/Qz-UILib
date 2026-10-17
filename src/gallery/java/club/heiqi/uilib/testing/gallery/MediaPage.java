package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
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

/** 共享媒体案例只编排正式会话与控件，观看空间沿普通流分配，离页关闭自己的播放。 */
final class MediaPage {
    private final ScenePageContext page;
    private final GalleryEnvironment environment;
    private final ReadableSignal<SceneTheme> theme;
    private final Signal<List<SceneNode>> content;
    private final Signal<String> message;
    private final SceneEditState address;
    private final Signal<Boolean> picking,sourceExpanded,large;
    private Path pickerDirectory;
    private MediaSession current;
    private SceneAppearance style;
    private Runnable back;
    MediaPage(ScenePageContext page,GalleryEnvironment environment,ReadableSignal<SceneTheme> theme) {
        this.page=page;this.environment=environment;this.theme=theme;
        content=page.domain().signal(Collections.emptyList());message=page.domain().signal("选择本地视频，输入媒体地址，或打开内置示例。");
        address=new SceneEditState(page.domain(),"");picking=page.domain().signal(false);
        sourceExpanded=page.domain().signal(true);large=page.domain().signal(false);
        pickerDirectory=environment.initialMediaDirectory();page.own(this::stop);
    }
    SceneNode create(Runnable back) {
        this.back=back;style=new SceneAppearance(page,theme);
        SceneNode root=new SceneNode().size(Size.fill(1),Size.fill(1)).gap(10).own(style.scope)
                .bind(NodeBinding.value(large,(n,value)->n.padding(value?8:20)));
        root.add(GalleryStyle.header(page,theme,"媒体播放","选择一个来源，让声音与画面在这里继续。",back)
                .bind(NodeBinding.value(()->content.get().isEmpty(),SceneNode::visible)));
        SceneNode sourceCard=GalleryStyle.card(theme).padding(12).gap(8);
        sourceCard.add(GalleryStyle.label(page,()->"媒体来源",theme,16,false));
        SceneNode addressField=SceneTextField.create(page,address,()->style.colors.get().editorTheme(),()->false,submission->{
            if(openAddress(submission.text))address.accept(submission,submission.text.trim());
            else address.reject(submission,message.get());
        },SceneTextField.SubmitPolicy.CONFIRM,()->"本地路径、file URI 或 HTTP / HLS 地址",false);
        SceneNode pathRow=GalleryStyle.centeredRow().size(Size.fill(1),Size.natural()).gap(8)
                .add(style.field(addressField).size(Size.fill(1),Size.natural()))
                .add(GalleryStyle.primary(page,()->"打开媒体",theme,()->openAddress(address.snapshot().get().text)));
        sourceCard.add(pathRow).add(GalleryStyle.label(page,message,theme,13,true));
        root.add(GalleryStyle.frame(sourceCard,theme).bind(NodeBinding.value(
                ()->!large.get()&&(sourceExpanded.get()||content.get().isEmpty()),SceneNode::visible)));
        SceneNode actions=GalleryStyle.centeredRow().size(Size.fill(1),Size.natural()).gap(8).scrollable(true);
        actions.add(GalleryStyle.button(page,()->sourceExpanded.get()?"收起来源":"更换来源",theme,()->sourceExpanded.set(!sourceExpanded.get()))
                .bind(NodeBinding.value(()->!content.get().isEmpty(),SceneNode::visible)));
        actions.add(GalleryStyle.button(page,()->"选择本地视频",theme,()->picking.set(true)));
        actions.add(GalleryStyle.button(page,()->"内置示例",theme,()->{
            try{open(environment.exampleSource());}catch(Exception failure){sourceExpanded.set(true);message.set("无法打开示例："+failure.getMessage());}
        }));
        actions.add(GalleryStyle.button(page,()->"停止",theme,()->{stop();message.set("已停止播放。");}));
        root.add(actions.bind(NodeBinding.value(()->!large.get(),SceneNode::visible)));
        SceneNode empty=GalleryStyle.card(theme).padding(24).gap(12)
                .add(GalleryStyle.label(page,()->"准备好，按下播放",theme,24,false))
                .add(GalleryStyle.label(page,()->"打开本地文件或粘贴媒体地址，也可以先体验内置示例。",theme,15,true));
        root.add(GalleryStyle.frame(empty,theme).size(Size.fill(1),Size.fill(1))
                .bind(NodeBinding.value(()->content.get().isEmpty(),SceneNode::visible)));
        SceneNode player=new SceneNode().size(Size.fill(1),Size.fill(1));
        player.bind(NodeBinding.ownedChildren(content));root.add(player.bind(NodeBinding.value(()->!content.get().isEmpty(),SceneNode::visible)));
        if(environment.media()==null)message.set("当前入口未提供媒体播放，仍可浏览其他场景。");
        SceneNode stage=new SceneNode().size(Size.fill(1),Size.fill(1));
        stage.bind(NodeBinding.repeat(()->picking.get()?Arrays.asList(false,true):Collections.singletonList(false),
                value->value,item->item.get()?SceneFilePicker.create(page,pickerDirectory,theme,new SceneFilePicker.Options().filter("媒体文件","mp4,mkv,mov,webm,avi,ts,m4v,mp3,wav,flac,ogg,m4a,aac"),selected->{
                    picking.set(false);pickerDirectory=selected.getParent();
                    try {MediaSource source=MediaSource.of(selected.toUri());open(source);address.reset(source.uri.toString());}
                    catch(Exception failure){sourceExpanded.set(true);message.set("无法打开本地视频："+failure.getMessage());}
                },()->picking.set(false)):root));
        return stage;
    }
    private boolean openAddress(String raw) {
        try{open(environment.resolveSource(raw));return true;}
        catch(Exception failure){sourceExpanded.set(true);message.set("无法打开媒体："+failure.getMessage());return false;}
    }
    private void open(MediaSource source) {
        MediaService service=environment.media();
        if(service==null)throw new IllegalStateException("当前入口未提供媒体播放");
        stop();MediaSession opened=service.open(source,false);
        try {
            SceneNode body=new SceneNode().size(Size.fill(1),Size.fill(1)).gap(8);
            SceneNode video=SceneVideo.create(page,opened).size(Size.fill(1),Size.fill(1)).background(0xFF09090B);
            ReadableSignal<club.heiqi.uilib.ui.media.MediaSnapshot> state=SceneVideo.snapshot(video);
            ReadableSignal<Boolean> audio=()->state.get().info.ready&&!state.get().info.hasVideo&&state.get().info.hasAudio;
            video.bind(NodeBinding.value(audio,(node,value)->node.visible(!value)));
            SceneNode watching=SceneMediaControls.viewing(page,opened,video,theme).bind(NodeBinding.value(audio,(node,value)->node.visible(!value)));
            SceneNode audioCard=GalleryStyle.card(theme).padding(24).gap(8).add(style.title("正在播放音频")).add(style.ellipsis(()->sourceName(state.get().source),1,true)).bind(NodeBinding.value(audio,SceneNode::visible));
            SceneNode toolbar=GalleryStyle.centeredRow().size(Size.fill(1),Size.natural()).gap(8)
                    .add(GalleryStyle.button(page,()->"返回场景概览",theme,back))
                    .add(style.label(()->"媒体播放",1,false))
                    .add(style.ellipsis(()->sourceName(SceneVideo.snapshot(video).get().source),1,true).size(Size.fill(1),Size.natural()))
                    .add(GalleryStyle.button(page,()->large.get()?"退出放大":"放大观看",theme,()->large.set(!large.get())));
            // 普通流把工具和控制以外的当前空间交给视频；SceneVideo仍按实际内容比例CONTAIN。
            body.add(toolbar).add(watching).add(audioCard).add(GalleryStyle.frame(SceneMediaControls.create(page,opened,theme).padding(12),theme));
            current=opened;sourceExpanded.set(false);content.set(Collections.singletonList(body));
            message.set("输入新来源后打开，当前播放不受草稿编辑影响。");opened.play();
        }catch(RuntimeException|Error failure){current=null;try{opened.close();}catch(Throwable next){failure.addSuppressed(next);}throw failure;}
    }
    private static String sourceName(MediaSource source){
        String path=source.uri.getPath();
        return path==null||path.isEmpty()?source.uri.toString():path.substring(path.lastIndexOf('/')+1);
    }
    void stop() {
        picking.set(false);sourceExpanded.set(true);large.set(false);
        MediaSession previous=current;current=null;content.set(Collections.emptyList());
        if(previous!=null)previous.close();
    }
}
