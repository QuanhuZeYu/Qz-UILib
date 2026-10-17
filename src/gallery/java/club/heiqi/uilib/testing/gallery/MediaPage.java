package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.media.MediaSource;
import club.heiqi.uilib.ui.media.MediaResolver;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.scene.control.SceneControl;
import club.heiqi.uilib.ui.scene.control.SceneLayer;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
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
    private final Signal<Boolean> picking,sourceExpanded,large,busy,feedback;
    private final Signal<MediaResolver.Result> catalog;
    private final Signal<String> choosing;
    private final GalleryMediaResolver resolver;
    private SceneEditState.Submission pendingSubmission;
    private boolean preservePosition;
    private Path pickerDirectory;
    private MediaSession current;
    private SceneAppearance style;
    private Runnable back;
    MediaPage(ScenePageContext page,GalleryEnvironment environment,ReadableSignal<SceneTheme> theme) {
        this.page=page;this.environment=environment;this.theme=theme;
        content=page.domain().signal(Collections.emptyList());message=page.domain().signal("选择本地视频，输入媒体地址，或打开内置示例。");
        address=new SceneEditState(page.domain(),"");picking=page.domain().signal(false);
        sourceExpanded=page.domain().signal(true);large=page.domain().signal(false);
        busy=page.domain().signal(false);feedback=page.domain().signal(false);catalog=page.domain().signal(null);choosing=page.domain().signal("");
        resolver=environment.mediaResolver()==null?null:new GalleryMediaResolver(environment.mediaResolver());
        if(resolver!=null)page.own(resolver);
        pickerDirectory=environment.initialMediaDirectory();page.own(this::stop);
    }
    SceneNode create(Runnable back) {
        this.back=back;style=new SceneAppearance(page,theme);
        SceneNode root=new SceneNode().control(new InputPump()).size(Size.fill(1),Size.fill(1)).gap(10).own(style.scope)
                .bind(NodeBinding.value(large,(n,value)->n.padding(value?8:20)));
        root.add(GalleryStyle.header(page,theme,"媒体播放","选择一个来源，让声音与画面在这里继续。",back)
                .bind(NodeBinding.value(()->content.get().isEmpty(),SceneNode::visible)));
        SceneNode sourceCard=GalleryStyle.card(theme).padding(12).gap(8);
        sourceCard.add(GalleryStyle.label(page,()->"媒体来源",theme,16,false));
        SceneNode addressField=SceneTextField.create(page,address,()->style.colors.get().editorTheme(),()->false,submission->{
            openAddress(submission);
        },SceneTextField.SubmitPolicy.CONFIRM,()->"BV号、Bilibili链接、直播间、本地路径或媒体地址",false);
        SceneNode pathRow=GalleryStyle.centeredRow().size(Size.fill(1),Size.natural()).gap(8)
                .add(style.field(addressField).size(Size.fill(1),Size.natural()))
                .add(GalleryStyle.primary(page,()->"打开媒体",theme,()->openAddress(address.request(true))));
        sourceCard.add(pathRow).add(GalleryStyle.label(page,()->"支持Bilibili点播与直播；匿名可观看的内容可直接打开。",theme,13,true));
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
        root.add(GalleryStyle.label(page,message,theme,13,true).bind(NodeBinding.value(feedback,SceneNode::visible)));
        root.add(networkDetails());
        root.add(choices());
        SceneNode empty=GalleryStyle.card(theme).padding(24).gap(12)
                .add(GalleryStyle.label(page,()->"准备好，按下播放",theme,24,false))
                .add(GalleryStyle.label(page,()->"打开本地文件或粘贴媒体地址，也可以先体验内置示例。",theme,15,true));
        root.add(GalleryStyle.frame(empty,theme).size(Size.fill(1),Size.fill(1))
                .bind(NodeBinding.value(()->content.get().isEmpty(),SceneNode::visible)));
        SceneNode player=new SceneNode().size(Size.fill(1),Size.fill(1));
        player.bind(NodeBinding.ownedChildren(content));root.add(player.bind(NodeBinding.value(()->!content.get().isEmpty(),SceneNode::visible)));
        if(environment.media()==null){message.set("当前入口未提供媒体播放，仍可浏览其他场景。");feedback.set(true);}
        SceneNode stage=new SceneNode().size(Size.fill(1),Size.fill(1));
        stage.bind(NodeBinding.repeat(()->picking.get()?Arrays.asList(false,true):Collections.singletonList(false),
                value->value,item->item.get()?SceneFilePicker.create(page,pickerDirectory,theme,new SceneFilePicker.Options().filter("媒体文件","mp4,mkv,mov,webm,avi,ts,m4v,mp3,wav,flac,ogg,m4a,aac"),selected->{
                    picking.set(false);pickerDirectory=selected.getParent();
                    try {MediaSource source=MediaSource.of(selected.toUri());open(source);address.reset(source.uri.toString());}
                    catch(Exception failure){sourceExpanded.set(true);message.set("无法打开本地视频："+failure.getMessage());}
                },()->picking.set(false)):root));
        return stage;
    }
    private void openAddress(SceneEditState.Submission submission) {
        String raw=submission.text.trim();
        if(resolver!=null&&environment.mediaResolver().supports(raw)){
            pendingSubmission=submission;resolve(new MediaResolver.Request(raw,"",""),false);return;
        }
        try{open(environment.resolveSource(raw));address.accept(submission,raw);}
        catch(Exception failure){sourceExpanded.set(true);feedback.set(true);message.set("无法打开媒体："+failure.getMessage());address.reject(submission,message.get());}
    }
    private void open(MediaSource source) {
        cancelResolution();catalog.set(null);openResolved(source);
    }
    private void openResolved(MediaSource source) {
        MediaService service=environment.media();
        if(service==null)throw new IllegalStateException("当前入口未提供媒体播放");
        closePlayback();MediaSession opened=service.open(source,false);
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
                    .add(style.ellipsis(()->catalog.get()==null?sourceName(SceneVideo.snapshot(video).get().source):catalog.get().title,1,true).size(Size.fill(1),Size.natural()))
                    .add(GalleryStyle.button(page,()->large.get()?"退出放大":"放大观看",theme,()->large.set(!large.get())));
            // 普通流把工具和控制以外的当前空间交给视频；SceneVideo仍按实际内容比例CONTAIN。
            body.add(toolbar).add(watching).add(audioCard).add(GalleryStyle.frame(SceneMediaControls.create(page,opened,theme).padding(12),theme));
            current=opened;sourceExpanded.set(false);content.set(Collections.singletonList(body));
            message.set("输入新来源后打开，当前播放不受草稿编辑影响。");feedback.set(false);opened.play();
        }catch(RuntimeException|Error failure){current=null;try{opened.close();}catch(Throwable next){failure.addSuppressed(next);}throw failure;}
    }
    private void resolve(MediaResolver.Request request,boolean preserve){
        if(resolver==null)return;preservePosition=preserve;busy.set(true);feedback.set(true);message.set("正在解析网络媒体…");
        choosing.set("");resolver.request(request);
    }
    private void receiveCompletion(){
        if(resolver==null||!busy.get())return;GalleryMediaResolver.Completion completion=resolver.take();if(completion==null)return;
        busy.set(false);SceneEditState.Submission submission=pendingSubmission;pendingSubmission=null;
        if(completion.failure!=null){
            String reason="无法解析媒体："+completion.failure.getMessage();
            if(submission!=null&&!address.reject(submission,reason)){feedback.set(false);return;}
            feedback.set(true);message.set(reason);sourceExpanded.set(true);return;
        }
        if(submission!=null&&!address.accept(submission,submission.text.trim())){feedback.set(true);message.set("地址已修改，请打开当前输入的新地址。");return;}
        MediaResolver.Result result=completion.result;catalog.set(result);
        if(result.source==null){closePlayback();feedback.set(true);message.set(result.message);return;}
        if(current!=null){
            MediaSnapshot previous=current.snapshot();long position=preservePosition&&!result.live?previous.positionNanos:0;
            current.source(result.source,position);feedback.set(false);
        }else openResolved(result.source);
    }
    private SceneNode networkDetails(){
        SceneNode details=new SceneNode().size(Size.fill(1),Size.natural()).gap(7)
                .bind(NodeBinding.value(()->catalog.get()!=null&&!large.get(),SceneNode::visible));
        SceneNode summary=GalleryStyle.centeredRow().size(Size.fill(1),Size.natural()).gap(10);
        if(environment.images()!=null){
            SceneNode cover=new SceneNode().size(Size.fixed(96),Size.fixed(54))
                    .bind(NodeBinding.value(()->page.viewportWidth().get()>=720,SceneNode::visible));
            cover.bind(NodeBinding.repeat(()->catalog.get()==null||catalog.get().poster==null?Collections.<java.net.URI>emptyList():Collections.singletonList(catalog.get().poster),uri->uri,
                    poster->SceneImage.create(environment.images(),()->ImageSource.http(poster.get().toString()),
                        ()->new ImageOptions(Size.fixed(96),Size.fixed(54),ImageOptions.Fit.COVER,.5f,.5f,96,54,"媒体封面",false))));
            summary.add(cover);
        }
        summary.add(new SceneNode().size(Size.fill(1),Size.natural()).gap(4)
                .add(style.ellipsis(()->catalog.get()==null?"":catalog.get().title,1,false))
                .add(style.ellipsis(()->catalog.get()==null?"":catalog.get().author+" · "+(catalog.get().live?"直播":option(catalog.get().parts,catalog.get().part)),.86f,true)));
        SceneNode options=GalleryStyle.centeredRow().size(Size.fill(1),Size.natural()).gap(8).scrollable(true);
        options.add(GalleryStyle.button(page,()->"选择分P",theme,()->choosing.set("part"))
                .bind(NodeBinding.value(()->catalog.get()!=null&&catalog.get().parts.size()>1,SceneNode::visible))
                .bind(NodeBinding.value(()->!busy.get(),SceneNode::enabled)));
        options.add(GalleryStyle.button(page,()->catalog.get()==null?"清晰度":"清晰度 · "+option(catalog.get().qualities,catalog.get().quality),theme,()->choosing.set("quality"))
                .bind(NodeBinding.value(()->catalog.get()!=null&&!catalog.get().qualities.isEmpty(),SceneNode::visible))
                .bind(NodeBinding.value(()->!busy.get(),SceneNode::enabled)));
        options.add(GalleryStyle.button(page,()->"刷新地址",theme,()->{
            MediaResolver.Result result=catalog.get();pendingSubmission=null;resolve(new MediaResolver.Request(result.canonical.toString(),result.part,result.quality),true);
        }).bind(NodeBinding.value(()->!busy.get(),SceneNode::enabled)));
        details.add(summary).add(options);return details;
    }
    private SceneNode choices(){
        SceneNode list=new SceneNode().size(Size.fill(1),Size.fill(1)).gap(6).scrollable(true);
        list.bind(NodeBinding.repeat(()->catalog.get()==null?Collections.<MediaResolver.Option>emptyList():"part".equals(choosing.get())?catalog.get().parts:catalog.get().qualities,
                item->item.id,item->GalleryStyle.button(page,()->item.get().label,theme,()->{
                    MediaResolver.Result result=catalog.get();boolean part="part".equals(choosing.get());choosing.set("");pendingSubmission=null;
                    resolve(new MediaResolver.Request(result.canonical.toString(),part?item.get().id:result.part,part?"":item.get().id),!part);
                }).size(Size.fill(1),Size.natural())));
        SceneNode cancel=GalleryStyle.button(page,()->"取消选择",theme,()->choosing.set(""));
        SceneNode panel=GalleryStyle.card(theme).size(Size.fill(1),Size.fill(1)).padding(16).gap(12)
                .add(style.label(()->"part".equals(choosing.get())?"选择分P":"选择清晰度",1.2f,false))
                .add(list).add(cancel);
        return SceneLayer.create(page,()->new SceneLayer.Policy(0,SceneLayer.Participation.INTERACTIVE))
                .size(Size.fill(1),Size.fill(1)).padding(24).background(SceneAppearance.MODAL_SCRIM).modal(true).initialFocus(cancel)
                .onEscape(()->{choosing.set("");return true;}).add(panel)
                .bind(NodeBinding.value(()->!choosing.get().isEmpty(),SceneNode::visible));
    }
    private static String option(List<MediaResolver.Option> options,String selected){for(MediaResolver.Option item:options)if(item.id.equals(selected))return item.label;return selected;}
    private final class InputPump implements SceneControl {
        public long nextInputNanos(SceneNode node){return busy.get()?0:Long.MAX_VALUE;}
        public void inputFrame(SceneNode node,SceneGeometryView geometry,long now){receiveCompletion();}
        public MeasuredSize measure(SceneNode node,Constraints constraints,SceneTextMeasurer text){return new MeasuredSize(0,0);}
        public MeasuredSize measure(SceneNode node,Constraints constraints,SceneTextMeasurer text,SceneChildMeasurer children){return SceneFlowLayout.measure(node,constraints,children);}
        public ChildPlacements arrange(SceneNode node,LayoutBox box,SceneChildMeasurer children){return SceneFlowLayout.arrange(node,box,children);}
        public void paint(SceneNode node,SceneGeometryView geometry,PaintPlanBuilder target){}
    }
    private static String sourceName(MediaSource source){
        String path=source.uri.getPath();
        return path==null||path.isEmpty()?source.uri.toString():path.substring(path.lastIndexOf('/')+1);
    }
    void stop() {
        cancelResolution();catalog.set(null);choosing.set("");closePlayback();
    }
    private void cancelResolution(){if(resolver!=null)resolver.cancel();busy.set(false);pendingSubmission=null;feedback.set(false);}
    private void closePlayback() {
        picking.set(false);sourceExpanded.set(true);large.set(false);
        MediaSession previous=current;current=null;content.set(Collections.emptyList());
        if(previous!=null)previous.close();
    }
}
