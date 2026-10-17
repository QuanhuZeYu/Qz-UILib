package club.heiqi.uilib.ui.scene.runtime;

import java.util.function.BiConsumer;
import java.util.List;
import java.util.function.Function;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 可重新激活的逻辑声明。绑定值失效只唤醒呈现，不运行业务 Effect。 */
public interface NodeBinding {
    BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner);

    /** 创建并拥有键条目；持续键延续节点，退出生效自动销毁，同键返回重新创建。 */
    static <T, K> NodeBinding repeat(
            ReadableSignal<List<T>> source,
            Function<T, K> key,
            Function<ReadableSignal<T>, SceneNode> declaration) {
        return new RepeatBinding<>(source, key, declaration);
    }

    static NodeBinding presence(ReadableSignal<Boolean> present,
            ReadableSignal<club.heiqi.uilib.ui.scene.motion.PresenceOptions> options) { return new PresenceBinding(present, options); }

    /** 同页明确移动；null 目的地表示页面根，调用方仍持有根生命周期。 */
    static NodeBinding move(ReadableSignal<SceneNode> destination) {
        return new MoveBinding(destination);
    }

    /** 创建并拥有的子序列，退出生效自动销毁；可用于条件分支。 */
    static NodeBinding ownedChildren(ReadableSignal<List<SceneNode>> source) {
        return new ChildrenBinding(source, true);
    }

    /** 显式拥有集合退场；普通 ownedChildren 仍即时销毁。 */
    static NodeBinding ownedChildren(ReadableSignal<List<SceneNode>> source,
            ReadableSignal<club.heiqi.uilib.ui.scene.motion.PresenceOptions> exits) { return new ChildrenBinding(source, true, exits); }

    static <T, K> NodeBinding repeat(ReadableSignal<List<T>> source, Function<T, K> key,
            Function<ReadableSignal<T>, SceneNode> declaration,
            ReadableSignal<club.heiqi.uilib.ui.scene.motion.PresenceOptions> exits) {
        return new RepeatBinding<>(source, key, declaration, exits);
    }

    /** 显式引用调用方保留的逻辑树，退出仅解除挂载。 */
    static NodeBinding children(ReadableSignal<List<SceneNode>> source) {
        return new ChildrenBinding(source, false);
    }

    /** 有限呈现值过渡。初挂/重挂直接消费目标，反向从当前值续接，卸载结束推进。
     * 曲线将0到1映射到0到1；apply只更新呈现声明或所属呈现Signal，不执行业务动作。
     * 时间由帧准备统一采样，apply不在布局、绘制或纯派生内执行。 */
    static NodeBinding transition(ReadableSignal<Float> source, long durationNanos,
            java.util.function.DoubleUnaryOperator easing, BiConsumer<SceneNode, Float> apply) {
        return new TransitionBinding(source, durationNanos, easing, apply);
    }

    /** 目标跟随；普通扩展映射自行声明呈现用途，空间策略服从用户减少动效限制。 */
    static NodeBinding motion(ReadableSignal<Float> source,
            ReadableSignal<club.heiqi.uilib.ui.scene.motion.MotionSpec> strategy,
            BiConsumer<SceneNode, Float> apply) {
        return motion(source, strategy, false, null, apply);
    }

    static NodeBinding scrollMotion(ReadableSignal<club.heiqi.uilib.ui.scene.motion.ScrollMotionOptions> options) {
        return (runtime, node, owner) -> {
            BindingActivation value = NodeBinding.value(() -> { runtime.motionPolicy().get(); return options.get(); },
                    (self, policy) -> runtime.configureScrollMotion(self, policy)).activate(runtime, node, owner);
            return new BindingActivation() {
                public void refresh() { value.refresh(); }
                public void close() { value.close(); runtime.removeScrollMotion(node); }
            };
        };
    }

    /** 作用于本容器直属普通流子项的重排。 */
    static NodeBinding layoutPositions(ReadableSignal<club.heiqi.uilib.ui.scene.motion.MotionSpec> source) { return layoutMotion(source, false); }
    /** 本节点当前动画尺寸真实参与父子分配。 */
    static NodeBinding layoutSize(ReadableSignal<club.heiqi.uilib.ui.scene.motion.MotionSpec> source) { return layoutMotion(source, true); }
    static NodeBinding layoutMotion(ReadableSignal<club.heiqi.uilib.ui.scene.motion.MotionSpec> source, boolean size) {
        return (runtime, node, owner) -> {
            AutoCloseable claim = runtime.claimMotion(node, size ? "layoutSize" : "layoutPositions", new Object());
            owner.own(claim);
            BindingActivation value = NodeBinding.value(() -> runtime.motionPolicy().get().resolve(source.get(), true),
                    (self, spec) -> runtime.configureLayoutMotion(self, size, spec)).activate(runtime, node, owner);
            return new BindingActivation() {
                public void refresh() { value.refresh(); }
                public void close() {
                    value.close(); runtime.removeLayoutMotion(node, size);
                    try { claim.close(); } catch (Exception failure) { throw new IllegalStateException("布局运动通道释放失败", failure); }
                }
            };
        };
    }

    /** channel 标识实际呈现属性；框架拒绝同挂载重复驱动。 */
    static NodeBinding motion(ReadableSignal<Float> source,
            ReadableSignal<club.heiqi.uilib.ui.scene.motion.MotionSpec> strategy,
            boolean spatial, Object channel, BiConsumer<SceneNode, Float> apply) {
        return new MotionBinding(source, strategy, spatial, channel, apply);
    }

    /** 短暂离散呈现：非null来源的新对象身份重新起期，到期发布null但不改来源。
     * 同一来源身份到期后保持结束；初挂/重挂消费当前来源，null即时撤销。
     * apply只更新呈现声明或所属呈现Signal；卸载结束观察与有限推进。 */
    static <T> NodeBinding transientValue(ReadableSignal<T> source, long durationNanos,
            BiConsumer<SceneNode, T> apply) {
        return new TransientBinding<>(source, durationNanos, apply);
    }

    /** 当前被动命中处于本节点逻辑子树时，停留后发布true；离开/卸载立即结束。
     * apply仅发布呈现值；命中、裁剪、模态及所属层资格沿唯一路由器。 */
    static NodeBinding hoverWithin(long enterDelayNanos, BiConsumer<SceneNode, Boolean> apply) {
        return hoverWithin(() -> true,enterDelayNanos,apply);
    }

    /** 可用来源变化会结束旧等待；恢复资格后仍在区域内时重新等待完整期限。 */
    static NodeBinding hoverWithin(ReadableSignal<Boolean> enabled,long enterDelayNanos, BiConsumer<SceneNode, Boolean> apply) {
        return new HoverBinding(enabled,enterDelayNanos,apply);
    }

    /** 补充说明依原触发器挂载，不建立焦点、点击或另一棵呈现树。 */
    static NodeBinding tooltip(ReadableSignal<club.heiqi.uilib.ui.scene.control.SceneTooltip.Content> content,
            ReadableSignal<club.heiqi.uilib.ui.scene.control.SceneTooltip.Options> options,
            ReadableSignal<club.heiqi.uilib.ui.scene.control.SceneTheme> theme){
        return new TooltipBinding(content,options,theme);
    }

    static <T> NodeBinding value(ReadableSignal<T> source, BiConsumer<SceneNode, T> apply) {
        return (runtime, node, owner) -> {
            Observation observation =
                    runtime.domain()
                            .observe(
                                    owner,
                                    () -> apply.accept(node, source.get()),
                                    () -> runtime.invalidateBinding(node));
            return new BindingActivation() {
                @Override
                public void refresh() {
                    observation.refresh();
                }

                @Override
                public void close() {
                    observation.close();
                }
            };
        };
    }
}
