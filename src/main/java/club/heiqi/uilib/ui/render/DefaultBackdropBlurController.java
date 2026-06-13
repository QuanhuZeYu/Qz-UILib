package club.heiqi.uilib.ui.render;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * 默认页面级背景模糊控制器实现。
 */
final class DefaultBackdropBlurController implements BackdropBlurController {

    private final BackdropBlurPolicy basePolicy;
    private final Runnable changeCallback;
    private BackdropBlurPolicy runtimeOverride;
    private int version;

    DefaultBackdropBlurController(BackdropBlurPolicy basePolicy, Runnable changeCallback) {
        this.basePolicy = basePolicy == null ? BackdropBlurPolicy.inheritGlobal() : basePolicy;
        this.changeCallback = changeCallback;
    }

    @Override
    public BackdropBlurPolicy getPolicy() {
        return basePolicy.merge(runtimeOverride);
    }

    @Override
    public void setPolicy(BackdropBlurPolicy policy) {
        BackdropBlurPolicy resolvedPolicy = Objects.requireNonNull(policy, "policy");
        if (resolvedPolicy.equals(runtimeOverride)) {
            return;
        }
        runtimeOverride = resolvedPolicy;
        notifyChanged();
    }

    @Override
    public void updatePolicy(UnaryOperator<BackdropBlurPolicy> updater) {
        UnaryOperator<BackdropBlurPolicy> resolvedUpdater = Objects.requireNonNull(updater, "updater");
        setPolicy(Objects.requireNonNull(resolvedUpdater.apply(getPolicy()), "updatedPolicy"));
    }

    @Override
    public void resetPolicyOverride() {
        if (runtimeOverride == null) {
            return;
        }
        runtimeOverride = null;
        notifyChanged();
    }

    int getVersion() {
        return version;
    }

    private void notifyChanged() {
        if (version == Integer.MAX_VALUE) {
            version = 1;
        } else {
            version++;
        }
        if (changeCallback != null) {
            changeCallback.run();
        }
    }
}
