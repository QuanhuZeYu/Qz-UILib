package club.heiqi.uilib.ui.container.experimental.scene;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.container.experimental.model.EntryKey;
import club.heiqi.uilib.ui.container.experimental.model.LongContainerSnapshot;
import club.heiqi.uilib.ui.container.experimental.model.LongEntrySnapshot;
import club.heiqi.uilib.ui.container.experimental.operation.LongContainerIntent;
import club.heiqi.uilib.ui.container.experimental.presentation.ItemPresentation;
import club.heiqi.uilib.ui.container.experimental.presentation.ItemPresentationResolver;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** Experimental confirmed long Entry snapshot 到 scene 固定列 Grid 的声明式投影。 */
public final class SceneLongEntryGrid {

    private static final int ICON_SIZE = 16;

    private SceneLongEntryGrid() {
    }

    /**
     * 创建 long Entry Grid 组件函数。
     *
     * @param runtime scene 运行时
     * @param props 只读输入与 intent 输出合同
     * @return 交给 {@link SceneRuntime#mount} 的一次性组件函数
     */
    public static Supplier<SceneNode> create(SceneRuntime runtime, Props props) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(props, "props");
        return () -> {
            SceneNode grid = SceneNode.grid(props.columns).setGap(props.gap);
            ReadableSignal<List<LongEntrySnapshot>> entries = Computed.create(
                    Collections.emptyList(), () -> requireSnapshot(props.snapshot.get()).entries());
            runtime.forEach(grid, entries, LongEntrySnapshot::key,
                    entry -> createEntry(runtime, props, entry));
            return grid;
        };
    }

    private static SceneNode createEntry(SceneRuntime runtime, Props props, LongEntrySnapshot initial) {
        EntryKey key = initial.key();
        ReadableSignal<LongEntrySnapshot> current = Computed.create(
                initial, () -> findCurrent(props.snapshot.get(), key, initial));
        ItemPresentation<SceneImageSource> initialPresentation = resolve(props, initial);
        ReadableSignal<ItemPresentation<SceneImageSource>> presentation = Computed.create(
                initialPresentation, () -> resolve(props, current.get()));

        SceneNode card = SceneNode.column(props.gap)
                .setWidthSizing(SceneNode.WidthSizing.FILL)
                .setPreferredHeight(props.entryHeight)
                .setPadding(SceneChromeTokens.PAD_SM)
                .setBorderWidth(1)
                .setBorderColor(SceneChromeTokens.BORDER_DEFAULT)
                .setCornerRadius(SceneChromeTokens.RADIUS_MD)
                .setClipChildren(true)
                .setCursor(SceneCursor.POINTER);
        SceneNode header = SceneNode.row(props.gap).setCrossAxisAlign(CrossAxisAlign.CENTER);
        SceneNode icon = new SceneNode().setPreferredWidth(ICON_SIZE).setPreferredHeight(ICON_SIZE);
        SceneNode name = new SceneNode().setTextColor(SceneChromeTokens.TEXT_PRIMARY);
        SceneNode amount = new SceneNode().setTextColor(SceneChromeTokens.TEXT_SECONDARY);
        header.setHitTestable(false);
        icon.setHitTestable(false);
        name.setHitTestable(false);
        amount.setHitTestable(false);
        header.appendChild(icon);
        header.appendChild(name);
        card.appendChild(header);
        card.appendChild(amount);

        runtime.bind(presentation, value -> {
            icon.setImageSource(value.icon());
            icon.setBackgroundColor(value.icon() == null ? SceneChromeTokens.BG_DISABLED : 0);
            name.setText(value.displayName());
        });
        runtime.bindComputed(
                () -> Objects.requireNonNull(props.amountFormatter.apply(current.get().amount()),
                        "amountFormatter result"),
                amount::setText);

        SceneInteractionState interaction = runtime.interactionState(card);
        runtime.bindComputed(
                () -> SceneStateColors.standardBackground(
                        true,
                        Boolean.TRUE.equals(interaction.hovered().get()),
                        Boolean.TRUE.equals(interaction.pressed().get())),
                card::setBackgroundColor);

        runtime.on(card, SceneEventType.CLICK, (event, context) -> {
            SceneMouseButton button = event.getButton();
            if (button != SceneMouseButton.LEFT && button != SceneMouseButton.RIGHT
                    && button != SceneMouseButton.MIDDLE) {
                return;
            }
            context.stopPropagation();
            if (button == SceneMouseButton.MIDDLE || Boolean.TRUE.equals(props.pending.get())) {
                return;
            }
            if (event.isShiftDown()) {
                props.onIntent.accept(LongContainerIntent.quickExtract(key));
                return;
            }
            boolean carriedEmpty = Boolean.TRUE.equals(props.carriedEmpty.get());
            if (button == SceneMouseButton.LEFT) {
                props.onIntent.accept(carriedEmpty
                        ? LongContainerIntent.takeStack(key)
                        : LongContainerIntent.depositAll());
            } else {
                props.onIntent.accept(carriedEmpty
                        ? LongContainerIntent.takeHalfStack(key)
                        : LongContainerIntent.depositOne());
            }
        });
        return card;
    }

    private static LongEntrySnapshot findCurrent(LongContainerSnapshot snapshot, EntryKey key,
                                                  LongEntrySnapshot removedFallback) {
        for (LongEntrySnapshot entry : requireSnapshot(snapshot).entries()) {
            if (entry.key().equals(key)) {
                return entry;
            }
        }
        // 删除项的 Owner 会在同一 flush 内销毁；回退值只避免销毁前的调度顺序产生 null。
        return removedFallback;
    }

    private static ItemPresentation<SceneImageSource> resolve(Props props, LongEntrySnapshot entry) {
        return Objects.requireNonNull(props.presentation.resolve(entry.item()), "presentation result");
    }

    private static LongContainerSnapshot requireSnapshot(LongContainerSnapshot snapshot) {
        return Objects.requireNonNull(snapshot, "snapshot value");
    }

    /** Scene long Entry Grid 的不可变输入合同。 */
    public static final class Props {
        private final ReadableSignal<LongContainerSnapshot> snapshot;
        private final ReadableSignal<Boolean> pending;
        private final ReadableSignal<Boolean> carriedEmpty;
        private final Consumer<LongContainerIntent> onIntent;
        private final ItemPresentationResolver<SceneImageSource> presentation;
        private final LongFunction<String> amountFormatter;
        private final int columns;
        private final int entryHeight;
        private final int gap;

        private Props(Builder builder) {
            this.snapshot = Objects.requireNonNull(builder.snapshot, "snapshot");
            this.pending = Objects.requireNonNull(builder.pending, "pending");
            this.carriedEmpty = Objects.requireNonNull(builder.carriedEmpty, "carriedEmpty");
            this.onIntent = Objects.requireNonNull(builder.onIntent, "onIntent");
            this.presentation = Objects.requireNonNull(builder.presentation, "presentation");
            this.amountFormatter = Objects.requireNonNull(builder.amountFormatter, "amountFormatter");
            if (builder.columns < 1) throw new IllegalArgumentException("columns 必须至少为 1");
            if (builder.entryHeight < 1) throw new IllegalArgumentException("entryHeight 必须至少为 1");
            if (builder.gap < 0) throw new IllegalArgumentException("gap 不可为负数");
            this.columns = builder.columns;
            this.entryHeight = builder.entryHeight;
            this.gap = builder.gap;
        }

        /** 创建带首版安全默认尺寸的 builder。 */
        public static Builder builder() {
            return new Builder();
        }

        /** Props builder；required ports 在 build 时 fail-fast。 */
        public static final class Builder {
            private ReadableSignal<LongContainerSnapshot> snapshot;
            private ReadableSignal<Boolean> pending;
            private ReadableSignal<Boolean> carriedEmpty;
            private Consumer<LongContainerIntent> onIntent;
            private ItemPresentationResolver<SceneImageSource> presentation;
            private LongFunction<String> amountFormatter = value -> Long.toString(value);
            private int columns = 9;
            private int entryHeight = 44;
            private int gap = SceneChromeTokens.GAP_SM;

            private Builder() {
            }

            /** 设置 confirmed snapshot。 */
            public Builder snapshot(ReadableSignal<LongContainerSnapshot> value) { snapshot = value; return this; }
            /** 设置 pending gate。 */
            public Builder pending(ReadableSignal<Boolean> value) { pending = value; return this; }
            /** 设置 confirmed carried empty 投影。 */
            public Builder carriedEmpty(ReadableSignal<Boolean> value) { carriedEmpty = value; return this; }
            /** 设置 semantic intent 输出。 */
            public Builder onIntent(Consumer<LongContainerIntent> value) { onIntent = value; return this; }
            /** 设置 descriptor 到 scene presentation 的纯解析器。 */
            public Builder presentation(ItemPresentationResolver<SceneImageSource> value) { presentation = value; return this; }
            /** 设置完整 long 数量格式化器。 */
            public Builder amountFormatter(LongFunction<String> value) { amountFormatter = value; return this; }
            /** 设置固定列数。 */
            public Builder columns(int value) { columns = value; return this; }
            /** 设置 Entry 卡片最小高度。 */
            public Builder entryHeight(int value) { entryHeight = value; return this; }
            /** 设置 Grid 与卡片内部复用的间距。 */
            public Builder gap(int value) { gap = value; return this; }
            /** 校验并创建不可变 Props。 */
            public Props build() { return new Props(this); }
        }
    }
}
