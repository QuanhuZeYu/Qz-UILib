package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.overlay.AnchorProvider;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;

/**
 * SceneSelectPrimitive —— 无样式 scene 单选下拉行为核心。
 *
 * <p>该 primitive 只负责 trigger/listbox 结构、展开状态、键盘导航、overlay/anchor 和选择上抛，
 * 不设置背景、圆角、padding、文本色或 trigger cursor 等 chrome。listbox 与 item 的视觉装饰
 * 通过 {@link ListboxChrome} 在 overlay 构建调用栈内同步注入。</p>
 */
public final class SceneSelectPrimitive {

    /** trigger label 与箭头间距。 */
    private static final int TRIGGER_GAP = 8;
    /** 完全无样式的 listbox chrome。 */
    private static final ListboxChrome NOOP_CHROME = new ListboxChrome() {
        @Override
        public void decorateListbox(SceneNode listbox) {
        }

        @Override
        public void decorateItem(ItemHandle item) {
        }
    };

    /** 纯静态工厂，禁止实例化。 */
    private SceneSelectPrimitive() {
    }

    /**
     * Select primitive 输入契约 —— 只包含行为所需数据与 listbox chrome 回调。
     *
     * @param selectedIndex 当前选中项下标，控件绝不修改
     * @param options       构建期固定选项文本，构造期防御性复制为不可变列表
     * @param enabled       是否启用
     * @param onSelect      选择回调，激活选项时上抛期望下标
     * @param chrome        listbox 与 item 装饰回调，必须同步挂载 overlay 内绑定
     */
    @Desugar
    public record Props(
            ReadableSignal<Integer> selectedIndex,
            List<String> options,
            ReadableSignal<Boolean> enabled,
            Consumer<Integer> onSelect,
            ListboxChrome chrome
    ) {
        /**
         * 兼容构造：默认使用完全无样式 listbox chrome。
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
            this(selectedIndex, options, enabled, onSelect, NOOP_CHROME);
        }

        public Props(ReadableSignal<Integer> selectedIndex,
                     List<String> options,
                     ReadableSignal<Boolean> enabled,
                     Consumer<Integer> onSelect,
                     ListboxChrome chrome) {
            this.selectedIndex = Objects.requireNonNull(selectedIndex, "selectedIndex");
            this.options = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(options, "options")));
            this.enabled = Objects.requireNonNull(enabled, "enabled");
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
            this.chrome = Objects.requireNonNull(chrome, "chrome");
        }
    }

    /** listbox chrome 装配回调，必须在 primitive 调用栈内同步执行。 */
    public interface ListboxChrome {
        /**
         * 装饰 listbox 容器。
         *
         * @param listbox listbox overlay 根节点
         */
        void decorateListbox(SceneNode listbox);

        /**
         * 装饰单个 item。
         *
         * @param item item 句柄，包含结构节点与派生状态
         */
        void decorateItem(ItemHandle item);
    }

    /**
     * item 装饰句柄，暴露 item 结构节点与样式所需响应式状态。
     *
     * @param item        listbox 直接子节点
     * @param label       item 直接子文本节点
     * @param index       选项下标
     * @param selected    当前 item 是否为选中项
     * @param highlighted 当前 item 是否为键盘高亮项
     * @param interaction item 交互状态，供 wrapper 复用 hover 等 signal
     */
    @Desugar
    public record ItemHandle(
            SceneNode item,
            SceneNode label,
            int index,
            ReadableSignal<Boolean> selected,
            ReadableSignal<Boolean> highlighted,
            SceneInteractionState interaction
    ) {
    }

    /**
     * Select primitive 创建结果，暴露无样式结构节点与派生行为状态。
     *
     * @param trigger          trigger 根节点
     * @param label            当前选中文本节点
     * @param arrow            展开指示箭头节点
     * @param expanded         当前是否展开
     * @param highlightedIndex 当前键盘高亮下标
     */
    @Desugar
    public record Result(
            SceneNode trigger,
            SceneNode label,
            SceneNode arrow,
            ReadableSignal<Boolean> expanded,
            ReadableSignal<Integer> highlightedIndex
    ) {
    }

    /**
     * 创建无样式 Select primitive。
     *
     * @param rt    场景运行时
     * @param props primitive 输入契约
     * @return 创建结果，供 wrapper 或高级控件挂载样式
     */
    public static Result create(SceneRuntime rt, Props props) {
        Signal<Boolean> expanded = Signal.create(Boolean.FALSE);
        Signal<Integer> highlightedIndex = Signal.create(normalizeIndex(props.selectedIndex().get(), props.options().size()));

        SceneNode trigger = SceneNode.row();
        trigger.setCrossAxisAlign(CrossAxisAlign.CENTER);
        trigger.setGap(TRIGGER_GAP);
        trigger.setWidthSizing(WidthSizing.SHRINK);

        SceneNode label = new SceneNode();
        label.setHitTestable(false);
        rt.bindText(label, Computed.create(() -> selectedText(props)));

        SceneNode arrow = new SceneNode();
        arrow.setHitTestable(false);
        rt.bindText(arrow, Computed.create(() -> Boolean.TRUE.equals(expanded.get()) ? "▲" : "▼"));

        trigger.appendChild(label);
        trigger.appendChild(arrow);

        rt.on(trigger, SceneEventType.CLICK, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            boolean next = !Boolean.TRUE.equals(expanded.get());
            expanded.set(Boolean.valueOf(next));
            if (next) {
                highlightedIndex.set(Integer.valueOf(normalizeIndex(props.selectedIndex().get(), props.options().size())));
            }
            ctx.stopPropagation();
        });

        rt.focusable(trigger, props.enabled());
        rt.on(trigger, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            if (!Boolean.TRUE.equals(props.enabled().get())) {
                return;
            }
            handleKeyDown(ev.getKey(), props, expanded, highlightedIndex, ctx::stopPropagation);
        });

        AnchorProvider anchorProvider = AnchorProvider.forNode(trigger);
        rt.portalAnchored(
                expanded,
                () -> buildListbox(rt, props, expanded, highlightedIndex),
                OverlayDismissPolicy.DEFAULT,
                () -> expanded.set(Boolean.FALSE),
                anchorProvider);

        return new Result(trigger, label, arrow, expanded, highlightedIndex);
    }

    /**
     * 构建 listbox overlay root。
     *
     * @param rt               场景运行时
     * @param props            Select primitive 输入契约
     * @param expanded         展开态 signal
     * @param highlightedIndex 键盘高亮下标 signal
     * @return listbox 根节点
     */
    private static SceneNode buildListbox(SceneRuntime rt, Props props,
                                          Signal<Boolean> expanded,
                                          Signal<Integer> highlightedIndex) {
        SceneNode listbox = SceneNode.column();
        listbox.setWidthSizing(WidthSizing.SHRINK);
        listbox.setScrollable(true);
        listbox.setClipChildren(true);

        SceneScrolls.attach(rt, listbox);
        props.chrome().decorateListbox(listbox);

        for (int idx = 0; idx < props.options().size(); idx++) {
            final int i = idx;
            SceneNode item = SceneNode.row();

            SceneNode itemLabel = new SceneNode();
            itemLabel.setHitTestable(false);
            itemLabel.setText(props.options().get(i));
            item.appendChild(itemLabel);

            SceneInteractionState itemState = rt.interactionState(item);
            ItemHandle handle = new ItemHandle(
                    item,
                    itemLabel,
                    i,
                    Computed.create(() -> Boolean.valueOf(i == normalizeIndex(props.selectedIndex().get(), props.options().size()))),
                    Computed.create(() -> Boolean.valueOf(i == normalizeIndex(highlightedIndex.get(), props.options().size()))),
                    itemState);
            props.chrome().decorateItem(handle);

            rt.on(item, SceneEventType.CLICK, (ev, ctx) -> {
                props.onSelect().accept(Integer.valueOf(i));
                expanded.set(Boolean.FALSE);
                ctx.stopPropagation();
            });
            listbox.appendChild(item);
        }
        return listbox;
    }

    /**
     * 处理 trigger 键盘事件。
     *
     * @param key              当前按键
     * @param props            Select primitive 输入契约
     * @param expanded         展开态 signal
     * @param highlightedIndex 高亮下标 signal
     * @param stopPropagation  停止传播命令
     */
    private static void handleKeyDown(SceneKey key, Props props,
                                      Signal<Boolean> expanded,
                                      Signal<Integer> highlightedIndex,
                                      Runnable stopPropagation) {
        boolean open = Boolean.TRUE.equals(expanded.get());
        int size = props.options().size();
        if (key == SceneKey.ARROW_DOWN) {
            if (!open) {
                expanded.set(Boolean.TRUE);
                highlightedIndex.set(Integer.valueOf(normalizeIndex(props.selectedIndex().get(), size)));
            } else {
                highlightedIndex.set(Integer.valueOf(clamp(highlightedIndex.get().intValue() + 1, 0, size - 1)));
            }
            stopPropagation.run();
        } else if (key == SceneKey.ARROW_UP) {
            if (!open) {
                expanded.set(Boolean.TRUE);
                highlightedIndex.set(Integer.valueOf(normalizeIndex(props.selectedIndex().get(), size)));
            } else {
                highlightedIndex.set(Integer.valueOf(clamp(highlightedIndex.get().intValue() - 1, 0, size - 1)));
            }
            stopPropagation.run();
        } else if (key == SceneKey.ENTER || key == SceneKey.SPACE) {
            if (open) {
                props.onSelect().accept(Integer.valueOf(normalizeIndex(highlightedIndex.get(), size)));
                expanded.set(Boolean.FALSE);
            } else {
                expanded.set(Boolean.TRUE);
                highlightedIndex.set(Integer.valueOf(normalizeIndex(props.selectedIndex().get(), size)));
            }
            stopPropagation.run();
        } else if (key == SceneKey.ESCAPE && open) {
            expanded.set(Boolean.FALSE);
            stopPropagation.run();
        }
    }

    /**
     * 读取当前选中文本。
     *
     * @param props Select primitive 输入契约
     * @return 当前选中文本，越界时为空串
     */
    private static String selectedText(Props props) {
        Integer idxObj = props.selectedIndex().get();
        if (idxObj == null) {
            return "";
        }
        int idx = idxObj.intValue();
        if (idx < 0 || idx >= props.options().size()) {
            return "";
        }
        return props.options().get(idx);
    }

    /**
     * 归一化下标到合法选项范围。
     *
     * @param value 输入下标
     * @param size  选项数量
     * @return 合法下标；无选项时返回 0
     */
    private static int normalizeIndex(Integer value, int size) {
        if (size <= 0) {
            return 0;
        }
        int raw = value == null ? 0 : value.intValue();
        return clamp(raw, 0, size - 1);
    }

    /**
     * 将值裁剪到闭区间。
     *
     * @param value 输入值
     * @param min   最小值
     * @param max   最大值
     * @return 裁剪后的值
     */
    private static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }
}
