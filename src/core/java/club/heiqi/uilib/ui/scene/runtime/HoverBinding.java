package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.Objects;
import java.util.function.BiConsumer;

/** 延迟进入、即时离开的挂载期呈现订阅；使用路由投影和现有有限时间队列。 */
final class HoverBinding implements NodeBinding {
    private final ReadableSignal<Boolean> enabled;
    private final long delay;
    private final BiConsumer<SceneNode,Boolean> apply;
    HoverBinding(ReadableSignal<Boolean> enabled,long delay,BiConsumer<SceneNode,Boolean> apply) {
        this.enabled=Objects.requireNonNull(enabled,"悬停可用来源");
        if(delay<0)throw new IllegalArgumentException("悬停延迟不能为负");
        this.delay=delay;this.apply=Objects.requireNonNull(apply,"悬停呈现");
    }
    @Override public BindingActivation activate(SceneRuntime runtime,SceneNode node,Owner owner) {return new Active(runtime,node,owner);}
    private final class Active implements BindingActivation,SceneTransitions.Step {
        private final SceneRuntime runtime;
        private final SceneNode node;
        private final ScenePointerRouter.HoverInterest interest;
        private final Observation eligibility;
        private boolean eligible,reset,initialized,shown,waiting,closed;
        private long started;
        Active(SceneRuntime runtime,SceneNode node,Owner owner) {
            this.runtime=runtime;this.node=node;
            interest=runtime.pointerRouter().observeHover(node,() -> runtime.invalidateBinding(node));
            eligibility=runtime.domain().observe(owner,() -> eligible=Boolean.TRUE.equals(enabled.get()),
                    () -> {reset=true;runtime.invalidateBinding(node);});
        }
        @Override public void refresh() {
            if(closed)return;
            eligibility.refresh();
            if(!initialized) {initialized=true;apply.accept(node,false);}
            if(reset) {reset=false;waiting=false;runtime.transitions().stop(this);publish(false);}
            if(!eligible||!interest.inside) {waiting=false;runtime.transitions().stop(this);publish(false);}
            else if(!shown&&!waiting) {
                started=runtime.transitions().now();waiting=true;
                if(delay==0) {waiting=false;publish(true);}
                else runtime.transitions().start(this);
            }
        }
        @Override public boolean advance(long now) {
            if(closed||reset||!eligible||!interest.inside) {waiting=false;return false;}
            if(now-started<delay)return true;
            waiting=false;publish(true);return false;
        }
        private void publish(boolean value) {if(shown!=value) {shown=value;apply.accept(node,value);}}
        @Override public void close() {
            closed=true;runtime.transitions().stop(this);interest.close();eligibility.close();
            if(shown) {shown=false;apply.accept(node,false);}
        }
    }
}
