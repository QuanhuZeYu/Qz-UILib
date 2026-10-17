package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.media.MediaRange;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.edit.SceneEditState;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.video.MediaViewPump;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** 正式媒体控制只发布会话意图；共同视觉、字段草稿与呈现视图各沿原owner。 */
public final class SceneMediaControls {
    private SceneMediaControls(){}
    public static SceneNode create(ScenePageContext page,MediaSession session){return create(page,session,page.theme());}
    public static SceneNode create(ScenePageContext page,MediaSession session,ReadableSignal<SceneTheme> theme){
        SceneAppearance style=new SceneAppearance(page,theme);
        MediaViewPump pump=page.mediaViews();MediaViewPump.View view=pump.view(page.domain(),session);
        ReadableSignal<MediaSnapshot> state=view.snapshot();
        SceneNode root=SceneAppearance.column(8).own(style.scope);
        Signal<Boolean> precise=page.domain().signal(false);
        Signal<String> interaction=page.domain().signal("");
        SceneNode timeline=SceneSlider.create(page,()->timeline(state.get()),value->{},value->{
            MediaSnapshot current=session.snapshot();MediaRange range=current.info.seekable;
            long target=Math.round(value);
            if(range==null||!range.contains(target)){interaction.set("时间范围已变化，请重新选择位置");return;}
            try{session.seek(target);interaction.set("");}catch(IllegalArgumentException|IllegalStateException failure){interaction.set(failure.getMessage());}
        },theme);
        ReadableSignal<SceneSlider.Preview> preview=SceneSlider.preview(timeline);
        SceneTooltip.attach(page,timeline,()->SceneTooltip.Content.text(preview.get().active?"跳转到 "+clock(Math.round(preview.get().value)):seekRange(state.get()),"← / → · Home / End"),()->new SceneTooltip.Options(),theme);
        root.bind((runtime,node,owner)->pump.attach(view,runtime.domain()));
        SceneNode buttons=SceneAppearance.row(10);
        buttons.add(style.action(()->playLabel(state.get()),null,
                ()->toggle(session),SceneAppearance.Role.PRIMARY));
        buttons.add(style.action(()->state.get().muted?"取消静音":"静音",null,
                ()->session.muted(!session.snapshot().muted),SceneAppearance.Role.OUTLINE));
        buttons.bind(NodeBinding.value(()->state.get().state!=MediaState.CLOSED&&(state.get().state!=MediaState.FAILED||liveExpired(state.get())),SceneNode::enabled));
        buttons.add(style.label(()->timeLabel(state.get()),1,false)
                .size(Size.fill(1),Size.natural()));
        SceneNode volumeSlider=SceneSlider.create(page,()->new SceneSlider.Model(0,1,.01,state.get().volume,state.get().state!=MediaState.CLOSED),session::volume,value->{},theme).size(Size.fixed(110),Size.natural());
        SceneTooltip.attach(page,volumeSlider,()->SceneTooltip.Content.text("音量 "+String.format(Locale.ROOT,"%.0f%%",state.get().volume*100),"← / →"),()->new SceneTooltip.Options(),theme);
        buttons.add(volumeSlider);
        buttons.add(style.action(()->"回到直播",null,()->liveEdge(session),SceneAppearance.Role.OUTLINE).bind(NodeBinding.value(()->!state.get().info.durationKnown()&&state.get().info.seekable!=null&&!liveExpired(state.get()),SceneNode::visible)));
        root.add(timeline).add(buttons);
        root.add(style.label(()->preview.get().active?"定位预览 "+clock(Math.round(preview.get().value)):"",.85f,true).bind(NodeBinding.value(()->preview.get().active,SceneNode::visible)));
        root.add(style.error(interaction));
        root.add(SceneAppearance.row(8).add(style.label(()->status(state.get()),.9f,true).size(Size.fill(1),Size.natural()))
                .add(style.action(()->precise.get()?"收起精确控制":"精确控制",null,()->precise.set(!precise.get()),SceneAppearance.Role.GHOST)));
        SceneNode fields=SceneGrid.create(page,()->new SceneGrid.Policy(Math.round(style.colors.get().textStyle.logicalEm*18),16,16));
        SceneEditState volume=new SceneEditState(page.domain(),Double.toString(state.get().volume));
        SceneNode volumeField=SceneTextField.create(page,volume,()->style.colors.get().editorTheme(),()->state.get().state==MediaState.CLOSED,
                submission->{
                    try{
                        double value=Double.parseDouble(submission.text.trim());
                        if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException("音量须为0到1");
                        session.volume(value);volume.accept(submission,Double.toString(value));
                    }catch(IllegalArgumentException|IllegalStateException failure){volume.reject(submission,failure.getMessage());}
                },SceneTextField.SubmitPolicy.CONFIRM,()->"音量（0到1）",false);
        page.domain().effect(style.scope,()->volume.synchronizeExternal(Double.toString(state.get().volume)));
        fields.add(SceneAppearance.column(8).add(style.label(()->"音量 "+String.format(Locale.ROOT,"%.0f%%",state.get().volume*100),.9f,false))
                .add(style.field(volumeField)).add(style.error(()->volume.snapshot().get().error)));
        SceneEditState seek=new SceneEditState(page.domain(),"");
        SceneNode seekField=SceneTextField.create(page,seek,()->style.colors.get().editorTheme(),()->state.get().info.seekable==null||state.get().state==MediaState.CLOSED,
                submission->{
                    try{
                        long nanos=new BigDecimal(submission.text.trim()).movePointRight(9).setScale(0,RoundingMode.HALF_UP).longValueExact();
                        MediaRange range=session.snapshot().info.seekable;
                        if(range==null||!range.contains(nanos))throw new IllegalArgumentException("定位目标不在当前有效范围");
                        session.seek(nanos);seek.accept(submission,submission.text.trim());
                    }catch(ArithmeticException|IllegalArgumentException|IllegalStateException failure){seek.reject(submission,failure.getMessage());}
                },SceneTextField.SubmitPolicy.CONFIRM,()->"输入秒数，Enter 确认",false);
        fields.add(SceneAppearance.column(8).add(style.label(()->"跳转到",.9f,false)).add(style.field(seekField))
                .add(style.label(()->seekRange(state.get()),.85f,true)).add(style.error(()->seek.snapshot().get().error)));
        return root.add(fields.bind(NodeBinding.value(precise,SceneNode::visible)));
    }
    /** 观看区域自身取得焦点后处理快捷键；工具栏、文字编辑与模态仍沿各自当前目标。 */
    public static SceneNode viewing(ScenePageContext page,MediaSession session,SceneNode content,ReadableSignal<SceneTheme> theme){
        SceneNode area=new SceneNode().control(new Viewing(session)).size(Size.fill(1),Size.fill(1)).focusable(true).clickFocus(true).onClick(()->{}).add(content);
        return SceneTooltip.attach(page,area,()->SceneTooltip.Content.text("播放区域","Space 播放/暂停 · ←/→ 5秒 · ↑/↓ 音量 · M 静音"),()->new SceneTooltip.Options().width(30),theme);
    }
    private static final class Viewing extends SceneAppearance.Flow {
        final MediaSession session;Viewing(MediaSession session){this.session=session;}
        @Override public boolean edit(SceneNode node,club.heiqi.uilib.ui.scene.input.SceneEditEvent event,club.heiqi.uilib.ui.scene.layout.SceneGeometryView geometry){
            MediaSnapshot current=session.snapshot();if(current.state==MediaState.CLOSED||event.compositionEvent||event.compositionSource!=null)return false;
            if(event.command==null){if("m".equalsIgnoreCase(event.text)){session.muted(!current.muted);return true;}return false;}
            switch(event.command){
                case ACTIVATE_ONLY:case SPACE:toggle(session);return true;
                case LEFT:case RIGHT:
                    MediaRange range=current.info.seekable;if(range==null||current.state==MediaState.FAILED)return true;
                    long step=event.command==club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.LEFT?-5_000_000_000L:5_000_000_000L;
                    long target=step>0?Math.min(range.endNanos,current.positionNanos>Long.MAX_VALUE-step?Long.MAX_VALUE:current.positionNanos+step):Math.max(range.startNanos,current.positionNanos+step);
                    try{session.seek(target);}catch(IllegalArgumentException shifted){/* 同批直播范围已变化，保留会话当前事实。 */}return true;
                case UP:case DOWN:session.volume(Math.max(0,Math.min(1,current.volume+(event.command==club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.UP?.05:-.05))));return true;
                default:return false;
            }
        }
    }
    public static void toggle(MediaSession session){
        MediaSnapshot current=session.snapshot();
        if(liveExpired(current)){liveEdge(session);return;}
        if(current.state==MediaState.ENDED){if(current.info.seekable!=null)session.seek(current.info.seekable.startNanos);else session.source(current.source);session.play();return;}
        if(current.playIntent)session.pause();else session.play();
    }
    private static boolean liveExpired(MediaSnapshot current){return current.failure!=null&&"live-window".equals(current.failure.stage)&&current.info.seekable!=null;}
    public static void liveEdge(MediaSession session){MediaSnapshot current=session.snapshot();MediaRange range=current.info.seekable;if(range==null||current.info.durationKnown())return;session.seek(Math.max(range.startNanos,range.endNanos-2_000_000_000L));session.play();}
    private static String playLabel(MediaSnapshot current){return liveExpired(current)?"回到直播":current.state==MediaState.ENDED?(current.info.seekable==null?"重新打开":"重新播放"):current.playIntent?"暂停":"播放";}
    private static String timeLabel(MediaSnapshot current){
        if(!current.info.durationKnown()&&current.info.seekable!=null)return "直播 · 落后 "+String.format(Locale.ROOT,"%.1f 秒",Math.max(0,current.info.seekable.endNanos-current.positionNanos)/1_000_000_000d);
        return clock(current.positionNanos)+" / "+(current.info.durationKnown()?clock(current.info.durationNanos):"未知时长");
    }
    private static SceneSlider.Model timeline(MediaSnapshot state){
        MediaRange range=state.info.seekable;boolean valid=range!=null&&range.endNanos>range.startNanos;
        SceneSlider.Model model=new SceneSlider.Model(valid?range.startNanos:0,valid?range.endNanos:1,1_000_000L,state.positionNanos,valid&&state.state!=MediaState.CLOSED&&state.state!=MediaState.FAILED).token(state.generation).keyboardStep(5_000_000_000d);
        MediaRange buffered=state.info.buffered;return buffered==null?model:model.buffered(buffered.startNanos,buffered.endNanos);
    }
    private static String seekRange(MediaSnapshot snapshot){
        MediaRange range=snapshot.info.seekable;
        return range==null?"当前来源不可定位":String.format(Locale.ROOT,"可定位 %.1f–%.1f 秒",range.startNanos/1_000_000_000d,range.endNanos/1_000_000_000d);
    }
    private static String clock(long nanos){
        long seconds=Math.max(0,nanos)/1_000_000_000L;
        return String.format(Locale.ROOT,"%02d:%02d",seconds/60,seconds%60);
    }
    private static String status(MediaSnapshot snapshot){
        if(liveExpired(snapshot))return "暂停位置已离开直播窗口，请选择有效位置或回到直播。";
        if(snapshot.failure!=null)return "播放失败 · "+snapshot.failure.stage+"："+snapshot.failure.cause.getMessage();
        String window=!snapshot.info.durationKnown()&&snapshot.info.seekable!=null?"直播窗口 "+clock(snapshot.info.seekable.startNanos)+"–"+clock(snapshot.info.seekable.endNanos)+" · ":"";
        switch(snapshot.state){
            case LOADING:return "正在加载媒体…";
            case PLAYING:return window+"正在播放";
            case PAUSED:return window+"已暂停";
            case SEEKING:return window+"正在定位…";
            case BUFFERING:return window+"正在缓冲…";
            case ENDED:return "播放已结束";
            case CLOSED:return "播放已关闭";
            default:return "准备就绪";
        }
    }
}
