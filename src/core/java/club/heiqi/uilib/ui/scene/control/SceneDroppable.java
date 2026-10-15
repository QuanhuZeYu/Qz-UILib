package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import java.util.Objects;
import java.util.function.Predicate;

/** 普通流中的接收区；仅登记目标，不接管内容中的按钮、编辑器或指针所有者。 */
public final class SceneDroppable {
    public static final class Options<K> {
        final Predicate<K> accepts;
        final int priority;
        public Options() { this(key -> true, 0); }
        private Options(Predicate<K> accepts, int priority) { this.accepts = accepts; this.priority = priority; }
        public Options<K> accepts(Predicate<K> predicate) {
            return new Options<>(Objects.requireNonNull(predicate, "accepts"), priority);
        }
        public Options<K> priority(int value) { return new Options<>(accepts, value); }
    }
    private SceneDroppable() {}
    public static <K> SceneNode create(ScenePageContext context, SceneDragDrop<K> scope, K key, SceneNode content) {
        return create(context, scope, key, content, new Options<>());
    }
    public static <K> SceneNode create(ScenePageContext context, SceneDragDrop<K> scope, K key, SceneNode content, Options<K> options) {
        Objects.requireNonNull(options, "options");
        return create(context, scope, key, content, () -> options);
    }
    public static <K> SceneNode create(ScenePageContext context, SceneDragDrop<K> scope, K key, SceneNode content,
            ReadableSignal<Options<K>> options) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Objects.requireNonNull(scope, "scope"); Objects.requireNonNull(key, "key");
        Objects.requireNonNull(content, "content"); Objects.requireNonNull(options, "options");
        SceneNode node = new SceneNode().add(content);
        node.bind((runtime, self, owner) -> {
            owner.own(scope.target(runtime, key, self, options));
            return new BindingActivation() { public void refresh() {} public void close() {} };
        });
        return node;
    }
}
