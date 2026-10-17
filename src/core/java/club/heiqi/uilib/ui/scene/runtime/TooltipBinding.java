package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneTooltip;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.Objects;

/** 显式提示声明与当前挂载一起注册，来源变化只唤醒呈现。 */
final class TooltipBinding implements NodeBinding {
    private final ReadableSignal<SceneTooltip.Content> content;
    private final ReadableSignal<SceneTooltip.Options> options;
    private final ReadableSignal<SceneTheme> theme;
    TooltipBinding(ReadableSignal<SceneTooltip.Content> content,ReadableSignal<SceneTooltip.Options> options,ReadableSignal<SceneTheme> theme){
        this.content=Objects.requireNonNull(content);this.options=Objects.requireNonNull(options);this.theme=Objects.requireNonNull(theme);
    }
    @Override public BindingActivation activate(SceneRuntime runtime,SceneNode node,Owner owner){
        SceneTooltipCoordinator.Entry entry=runtime.tooltips().register(node);
        ScenePointerRouter.HoverInterest interest=runtime.pointerRouter().observeHover(node,()->runtime.invalidateBinding(node));
        Observation observation=runtime.domain().observe(owner,()->{
            entry.hint=new SceneTooltip.Hint(content.get(),options.get(),theme.get());runtime.invalidatePaint();
        },()->runtime.invalidateBinding(node));
        return new BindingActivation(){
            public void refresh(){observation.refresh();}
            public void close(){observation.close();interest.close();runtime.tooltips().remove(entry);}
        };
    }
}
