package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.Arrays;
import java.util.List;

/** 主动作按真实子内容测自然宽度，内部宽度过渡仍由正式测量和裁剪生效。 */
final class MailReceiveSurface extends MailRaisedSurface {
    private final ReadableSignal<SceneTheme> theme;
    private final ReadableSignal<Float> expansion;

    MailReceiveSurface(ReadableSignal<SceneTheme> theme, ReadableSignal<Integer> color,
                       ReadableSignal<Float> expansion) {
        super(theme,color,1.1f,null,80000000L);
        this.theme=theme;
        this.expansion=expansion;
    }

    @Override public List<ReadableSignal<?>> measureDependencies(SceneNode node) {
        return Arrays.asList(theme,expansion);
    }

    @Override public MeasuredSize measure(SceneNode node,Constraints c,SceneTextMeasurer text,
                                         SceneChildMeasurer children) {
        MeasuredSize natural=children.measureChild(children.flowChildren().get(0),
                new Constraints(Integer.MAX_VALUE,c.availableHeight));
        int minimum=Math.max(0,MailStyle.px(theme.get(),56)-node.padding()*2);
        // Gmail只过渡min-width；文字即时显隐，展开时自然内容可直接撑开。
        int width=Math.max(natural.width,minimum+Math.round(MailStyle.px(theme.get(),40)*expansion.get()));
        return new MeasuredSize(width,natural.height);
    }
}
