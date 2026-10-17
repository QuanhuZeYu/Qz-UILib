package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.media.MediaState;
import club.heiqi.uilib.ui.media.MediaRange;
import club.heiqi.uilib.ui.media.MediaSnapshot;
import club.heiqi.uilib.ui.media.MediaSession;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
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
        SceneNode root=SceneAppearance.column(16).own(style.scope);
        root.bind((runtime,node,owner)->pump.attach(view,runtime.domain()));
        SceneNode buttons=SceneAppearance.row(10);
        buttons.add(style.action(()->state.get().playIntent?"暂停":"播放",null,
                ()->{if(session.snapshot().playIntent)session.pause();else session.play();},SceneAppearance.Role.PRIMARY));
        buttons.add(style.action(()->state.get().muted?"取消静音":"静音",null,
                ()->session.muted(!session.snapshot().muted),SceneAppearance.Role.OUTLINE));
        buttons.bind(NodeBinding.value(()->state.get().state!=MediaState.CLOSED&&state.get().state!=MediaState.FAILED,SceneNode::enabled));
        buttons.add(style.label(()->clock(state.get().positionNanos)+" / "+(state.get().info.durationKnown()?clock(state.get().info.durationNanos):"未知时长"),1,false)
                .size(Size.fill(1),Size.natural()));
        root.add(buttons);
        root.add(style.label(()->status(state.get()),.9f,true));
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
        return root.add(fields);
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
        if(snapshot.failure!=null)return "播放失败 · "+snapshot.failure.stage+"："+snapshot.failure.cause.getMessage();
        switch(snapshot.state){
            case LOADING:return "正在加载媒体…";
            case PLAYING:return "正在播放";
            case PAUSED:return "已暂停";
            case SEEKING:return "正在定位…";
            case BUFFERING:return "正在缓冲…";
            case ENDED:return "播放已结束";
            case CLOSED:return "播放已关闭";
            default:return "准备就绪";
        }
    }
}
