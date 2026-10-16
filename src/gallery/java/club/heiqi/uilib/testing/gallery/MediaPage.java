package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneMediaControls;
import club.heiqi.uilib.ui.scene.control.SceneTextField;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneVideo;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
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
    private MediaSession current;
    MediaPage(ScenePageContext page,GalleryEnvironment environment,ReadableSignal<SceneTheme> theme) {
        this.page=page;this.environment=environment;this.theme=theme;
        content=page.domain().signal(Collections.emptyList());message=page.domain().signal("选择本地视频，输入媒体地址，或打开内置示例。");
        address=new SceneEditState(page.domain(),"");page.own(this::stop);
    }
    SceneNode create(Runnable back) {
        SceneNode root=new SceneNode().size(Size.fill(1),Size.fill(1)).padding(24).gap(12).scrollable(true);
        SceneNode heading=GalleryStyle.centeredRow().size(Size.fill(1),Size.natural()).gap(16);
        heading.add(GalleryStyle.button(page,()->"返回场景概览",theme,back));
        heading.add(GalleryStyle.label(page,()->"媒体播放",theme,24,false));root.add(heading);
        root.add(GalleryStyle.label(page,()->"播放视频或音频，暂停、调整音量并跳转到可播放的位置。",theme,15,true));
        root.add(SceneTextField.create(page,address,theme,()->false,submission->{
            if(openAddress(submission.text))address.accept(submission,submission.text.trim());
            else address.reject(submission,message.get());
        },SceneTextField.SubmitPolicy.CONFIRM,()->"本地绝对路径、file URI 或 HTTP / HLS 地址"));
        SceneNode actions=new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1),Size.natural()).gap(8);
        actions.add(GalleryStyle.button(page,()->"打开媒体",theme,()->openAddress(address.snapshot().get().text)));
        actions.add(GalleryStyle.button(page,()->"选择本地视频",theme,()->{
            try {
                MediaSource source=environment.chooseMediaSource();
                if(source!=null){open(source);address.reset(source.uri.toString());}
            }catch(Exception failure){message.set("无法打开本地视频："+failure.getMessage());}
        }));
        actions.add(GalleryStyle.button(page,()->"内置示例",theme,()->{
            try{open(environment.exampleSource());}catch(Exception failure){message.set("无法打开示例："+failure.getMessage());}
        }));
        actions.add(GalleryStyle.button(page,()->"停止",theme,()->{stop();message.set("已停止播放。");}));root.add(actions);
        root.add(GalleryStyle.label(page,message,theme,14,true));
        SceneNode player=new SceneNode().size(Size.fill(1),Size.natural()).gap(8);
        player.bind(NodeBinding.ownedChildren(content));root.add(player);
        if(environment.media()==null)message.set("当前入口未提供媒体播放，仍可浏览其他场景。");
        return root;
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
            body.add(SceneVideo.create(page,opened).size(Size.fill(1),Size.fixed(240)).background(0xFF09090B));
            body.add(SceneMediaControls.create(page,opened));
            current=opened;content.set(Collections.singletonList(body));message.set("使用下方控制暂停播放、调整音量或定位。");opened.play();
        }catch(RuntimeException|Error failure){current=null;try{opened.close();}catch(Throwable next){failure.addSuppressed(next);}throw failure;}
    }
    void stop() {
        MediaSession previous=current;current=null;content.set(Collections.emptyList());
        if(previous!=null)previous.close();
    }
}
