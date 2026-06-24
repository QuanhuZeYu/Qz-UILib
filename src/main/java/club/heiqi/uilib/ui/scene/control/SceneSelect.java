package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneSelect —— scene 新栈锚定浮层选择控件。
 *
 * <p>trigger 常驻主树，listbox 通过 {@link SceneRuntime#portalAnchored} 提升为 overlay root。
 * 选中值由外部 {@code selectedIndex} 唯一驱动；展开、收起、键盘高亮和滚动均以本地 signal 表达，
 * handler 只写 signal 或调用 {@code onSelect} 上抛期望下标，浮层显隐由 signal→portal 派生。</p>
 */
public final class SceneSelect {

    /**
     * trigger 默认背景色
     */
    private static final int TRIGGER_BG = 0xFF3A3A3A;
    /**
     * trigger hover 背景色
     */
    private static final int TRIGGER_BG_HOVER = 0xFF505050;
    /**
     * trigger pressed 背景色
     */
    private static final int TRIGGER_BG_PRESSED = 0xFF2A2A2A;
    /**
     * trigger disabled 背景色
     */
    private static final int TRIGGER_BG_DISABLED = 0xFF2F2F2F;
    /**
     * flat 变体透明背景色
     */
    private static final int TRIGGER_BG_TRANSPARENT = 0x00000000;
    /**
     * listbox 背景色
     */
    private static final int LISTBOX_BG = 0xFF1E293B;
    /**
     * item 默认背景色
     */
    private static final int ITEM_BG = 0x00000000;
    /**
     * item hover 背景色
     */
    private static final int ITEM_BG_HOVER = 0xFF334155;
    /**
     * item 高亮背景色
     */
    private static final int ITEM_BG_HIGHLIGHTED = 0xFF3B4E68;
    /**
     * item 选中背景色
     */
    private static final int ITEM_BG_SELECTED = 0xFF4A90D9;
    /**
     * 文本颜色
     */
    private static final int TEXT_ENABLED = 0xFFFFFFFF;
    /**
     * disabled 文本颜色
     */
    private static final int TEXT_DISABLED = 0xFF888888;
    /**
     * trigger 内边距
     */
    private static final int TRIGGER_PADDING = 6;
    /**
     * item 内边距
     */
    private static final int ITEM_PADDING = 6;
    /**
     * trigger label 与箭头间距
     */
    private static final int TRIGGER_GAP = 8;
    /**
     * 圆角
     */
    private static final int RADIUS = 4;

    /**
     * 纯静态工厂，禁止实例化。
     */
    private SceneSelect() {
    }

    /**
     * Select 输入契约 —— 多选项单选受控，浮层显隐由控件内部 signal 派生。
     *
     * @param selectedIndex 当前选中项下标，控件绝不修改
     * @param options       构建期固定选项文本，构造期防御性复制为不可变列表
     * @param enabled       是否启用
     * @param onSelect      选择回调，激活选项时上抛期望下标
     * @param flat          是否使用无背景、无圆角、无内边距的扁平 trigger
     */
    @Desugar
    public record Props(
            ReadableSignal<Integer> selectedIndex,
            List<String> options,
            ReadableSignal<Boolean> enabled,
            Consumer<Integer> onSelect,
            boolean flat
    ) {
        /**
         * 兼容构造：默认使用原始非 flat 外观。
         *
         * @param selectedIndex 当前选中项下标
         * @param options       选项文本列表
         * @param enabled       是否启用
         * @param onSelect      选择回调
         */
        public Props(ReadableSignal<Integer> selectedIndex,
                     List<String> options,
                     ReadableSignal<Boolean> enabled,
                     Consumer<Integer> onSelect) {
            this(selectedIndex, options, enabled, onSelect, false);
        }

        public Props(ReadableSignal<Integer> selectedIndex,
                     List<String> options,
                     ReadableSignal<Boolean> enabled,
                     Consumer<Integer> onSelect,
                     boolean flat) {
            this.selectedIndex = Objects.requireNonNull(selectedIndex, "selectedIndex");
            this.options = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(options, "options")));
            this.enabled = Objects.requireNonNull(enabled, "enabled");
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
            this.flat = flat;
        }
    }

    /**
     * 工厂：构建 Select 组件函数。
     *
     * @param rt    场景运行时
     * @param props Select 输入契约
     * @return 组件函数，交 {@link SceneRuntime#mount} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneSelectPrimitive.ListboxChrome chrome = new SceneSelectChrome(rt);
            SceneSelectPrimitive.Props primitiveProps = new SceneSelectPrimitive.Props(
                    props.selectedIndex(), props.options(), props.enabled(), props.onSelect(), chrome);
            SceneSelectPrimitive.Result result = SceneSelectPrimitive.create(rt, primitiveProps);

            SceneNode trigger = result.trigger();
            trigger.setPadding(props.flat() ? 0 : TRIGGER_PADDING);
            trigger.setCornerRadius(props.flat() ? 0 : RADIUS);
            trigger.setCursor(SceneCursor.POINTER);

            SceneNode label = result.label();
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> label.setTextColor(Boolean.TRUE.equals(e) ? TEXT_ENABLED : TEXT_DISABLED));

            SceneNode arrow = result.arrow();
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> arrow.setTextColor(Boolean.TRUE.equals(e) ? TEXT_ENABLED : TEXT_DISABLED));

            SceneInteractionState is = rt.interactionState(trigger);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveTriggerBackground(
                            props.enabled().get(), is.pressed().get(), is.hovered().get(), props.flat())),
                    trigger::setBackgroundColor);
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> trigger.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.DEFAULT));

            return trigger;
        };
    }

    /**
     * 解析 trigger 背景色。
     *
     * @param enabled 是否启用
     * @param pressed 是否按压
     * @param hovered 是否悬停
     * @param flat    是否 flat 变体
     * @return ARGB 背景色
     */
    private static int resolveTriggerBackground(Boolean enabled, Boolean pressed, Boolean hovered, boolean flat) {
        if (flat) {
            return TRIGGER_BG_TRANSPARENT;
        }
        if (!Boolean.TRUE.equals(enabled)) {
            return TRIGGER_BG_DISABLED;
        }
        if (Boolean.TRUE.equals(pressed)) {
            return TRIGGER_BG_PRESSED;
        }
        if (Boolean.TRUE.equals(hovered)) {
            return TRIGGER_BG_HOVER;
        }
        return TRIGGER_BG;
    }

    /**
     * 解析 item 背景色。
     *
     * @param selected    是否选中
     * @param highlighted 是否键盘高亮
     * @param hovered     是否悬停
     * @return ARGB 背景色
     */
    private static int resolveItemBackground(boolean selected, boolean highlighted, Boolean hovered) {
        if (selected) {
            return ITEM_BG_SELECTED;
        }
        if (highlighted) {
            return ITEM_BG_HIGHLIGHTED;
        }
        if (Boolean.TRUE.equals(hovered)) {
            return ITEM_BG_HOVER;
        }
        return ITEM_BG;
    }

    /** SceneSelect 默认 listbox chrome 装配器。 */
    private static final class SceneSelectChrome implements SceneSelectPrimitive.ListboxChrome {
        /** 场景运行时，用于注册 PAINT 绑定。 */
        private final SceneRuntime rt;

        /**
         * 创建默认 listbox chrome 装配器。
         *
         * @param rt 场景运行时
         */
        private SceneSelectChrome(SceneRuntime rt) {
            this.rt = rt;
        }

        @Override
        public void decorateListbox(SceneNode listbox) {
            listbox.setBackgroundColor(LISTBOX_BG);
            listbox.setCornerRadius(RADIUS);
        }

        @Override
        public void decorateItem(SceneSelectPrimitive.ItemHandle handle) {
            handle.item().setPadding(ITEM_PADDING);
            handle.item().setCursor(SceneCursor.POINTER);
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveItemBackground(
                            handle.selected().get(),
                            handle.highlighted().get(),
                            handle.interaction().hovered().get())),
                    handle.item()::setBackgroundColor);
            handle.label().setTextColor(TEXT_ENABLED);
        }
    }
}
