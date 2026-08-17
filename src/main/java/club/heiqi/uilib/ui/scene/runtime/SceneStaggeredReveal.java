package club.heiqi.uilib.ui.scene.runtime;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;

/**
 * Owner-bound 的 layout-ready 级联进入序列。
 *
 * <p>调用方只登记独占 internal presentation offset 的 shell。序列先同步写入满 opacity 初态，
 * 再观察 host 发布的 layout epoch；全部目标取得 {@link LayoutBox} 后才启动带 delay 的轨道。
 * 因此同步建树和未来短暂延迟的 presentation publication 使用同一条路径，不会先画终态再跳回初态。</p>
 *
 * <p>本类是 runtime 包内实现，不形成公共兼容承诺。业务页面通过
 * {@link SceneRuntime#__staggeredReveal(List, float, int, int, int)} 使用。</p>
 */
final class SceneStaggeredReveal {

    private SceneStaggeredReveal() {
    }

    static void install(SceneMotionDriver driver,
                        Signal<Integer> layoutDoneSignal,
                        Owner owner,
                        List<SceneNode> requestedTargets,
                        float startOffsetY,
                        int durationMillis,
                        int itemDelayMillis,
                        int maxDelayMillis,
                        Runnable requestHoverReconcile) {
        List<SceneNode> targets = validateAndCopy(requestedTargets);
        if (targets.isEmpty()) {
            return;
        }
        int startOffset = Math.round(startOffsetY);
        if (!driver.isEnabled() || durationMillis <= 0 || startOffset == 0) {
            for (SceneNode target : targets) {
                target.setOpacity(1.0f);
                target.__setPresentationOffsetY(0);
            }
            return;
        }

        Object[] keys = new Object[targets.size()];
        boolean[] previousInputGates = new boolean[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            keys[i] = new Object();
            SceneNode target = targets.get(i);
            previousInputGates[i] = target.__isHitTestSubtreeEnabled();
            target.__setHitTestSubtreeEnabled(false);
            target.setOpacity(1.0f);
            target.__setPresentationOffsetY(startOffset);
        }

        boolean[] started = {false};
        boolean[] disposed = {false};
        owner.onCleanup(() -> {
            disposed[0] = true;
            for (int i = 0; i < targets.size(); i++) {
                driver.remove(keys[i]);
                targets.get(i).__setPresentationOffsetY(0);
                targets.get(i).__setHitTestSubtreeEnabled(previousInputGates[i]);
            }
            if (requestHoverReconcile != null) {
                requestHoverReconcile.run();
            }
        });

        int[] observedLayoutEpoch = {Integer.MIN_VALUE};
        owner.createEffect(() -> {
            if (started[0] || disposed[0]) {
                return;
            }
            int currentLayoutEpoch = layoutDoneSignal.get().intValue();
            if (observedLayoutEpoch[0] == Integer.MIN_VALUE) {
                observedLayoutEpoch[0] = currentLayoutEpoch;
                return;
            }
            if (currentLayoutEpoch == observedLayoutEpoch[0]) {
                return;
            }
            for (SceneNode target : targets) {
                if (!(target.getCachedLayout() instanceof LayoutBox)) {
                    return;
                }
            }
            started[0] = true;
            for (int i = 0; i < targets.size(); i++) {
                final int targetIndex = i;
                final SceneNode target = targets.get(i);
                long requestedDelay = (long) i * Math.max(0, itemDelayMillis);
                int delay = (int) Math.min(Math.max(0, maxDelayMillis), requestedDelay);
                driver.start(keys[i], delay, durationMillis,
                        progress -> target.__setPresentationOffsetY(Math.round(
                                startOffset * (1.0f - progress.floatValue()))),
                        () -> {
                            target.__setHitTestSubtreeEnabled(previousInputGates[targetIndex]);
                            if (requestHoverReconcile != null) {
                                requestHoverReconcile.run();
                            }
                        });
            }
        });
    }

    private static List<SceneNode> validateAndCopy(List<SceneNode> requestedTargets) {
        if (requestedTargets == null) {
            throw new IllegalArgumentException("targets 不可为 null");
        }
        List<SceneNode> targets = new ArrayList<SceneNode>(requestedTargets.size());
        IdentityHashMap<SceneNode, Boolean> seen = new IdentityHashMap<SceneNode, Boolean>();
        for (SceneNode target : requestedTargets) {
            if (target == null) {
                throw new IllegalArgumentException("targets 不可包含 null");
            }
            if (seen.put(target, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("同一 presentation shell 不可重复登记");
            }
            Transform transform = target.getTransform();
            if (transform != null && !transform.isIdentity()) {
                throw new IllegalArgumentException("stagger reveal 要求 target 独占 identity transform");
            }
            if (target.__getPresentationOffsetY() != 0) {
                throw new IllegalArgumentException("stagger reveal 要求 target 初始 presentation offset 为 0");
            }
            targets.add(target);
        }
        return targets;
    }
}
