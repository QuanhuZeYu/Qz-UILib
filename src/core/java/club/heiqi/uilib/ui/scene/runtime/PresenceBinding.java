package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.motion.PresenceOptions;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.Objects;

final class PresenceBinding implements NodeBinding {
    private final ReadableSignal<Boolean> present;
    private final ReadableSignal<PresenceOptions> options;
    PresenceBinding(ReadableSignal<Boolean> present, ReadableSignal<PresenceOptions> options) {
        this.present = Objects.requireNonNull(present); this.options = Objects.requireNonNull(options);
    }
    public BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner) {
        AutoCloseable claim = runtime.claimMotion(node, "presence", this);
        owner.own(claim);
        BindingActivation value = NodeBinding.value(() -> {
            runtime.motionPolicy().get(); options.get();
            return Objects.requireNonNull(present.get(), "目标存在性");
        }, (self, exists) -> runtime.declarePresence(self, exists, options.get())).activate(runtime, node, owner);
        return new BindingActivation() {
            public void refresh() { value.refresh(); }
            public void close() {
                value.close(); runtime.removePresence(node);
                try { claim.close(); } catch (Exception failure) { throw new IllegalStateException("存在性通道释放失败", failure); }
            }
        };
    }
}
