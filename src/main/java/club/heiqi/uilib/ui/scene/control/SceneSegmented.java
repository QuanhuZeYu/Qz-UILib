package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneSegmented —— scene 新栈控件层 Phase 4 批 2 分段单选控件（水平段式）。
 *
 * <h3>定位：多选项单选受控控件（契约 R8，与 {@link SceneRadioGroup} 同构）</h3>
 * <p>Props 与 RadioGroup 完全同构（selectedIndex + options 固定 + enabled + onSelect），复用 R8：
 * 当前选中段由外部 {@code selectedIndex} 只读 signal 唯一驱动；激活某段时<b>只经
 * {@code onSelect.accept(i)} 上抛期望选中下标</b>，控件<b>绝不自己维护或修改 selectedIndex</b>。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (ROW, crossAxisAlign=STRETCH, gap)
 *   └─ segment[i] (ROW, mainAxisAlign=CENTER, crossAxisAlign=CENTER, padding, cornerRadius, preferredWidth=固定段宽)  ← 交互单元 hitTestable=true
 *         └─ label[i] (text)   ← 装饰 hitTestable=false
 * </pre>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>不复用 SceneButton</b>：它是试金石不是积木，嵌套会让交互态归属混乱，直接建段节点。</li>
 *   <li><b>等宽用固定段宽</b>：scene 无 flex-grow，用 {@code setPreferredWidth(固定段宽)} 替代（YAGNI 退让）。</li>
 *   <li><b>R6 段穿透权威落地</b>：段本身 hitTestable=true，段内 label 文字 hitTestable=false 穿透到所属段。</li>
 * </ul>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 常量 + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 段内文字命中穿透到段 /
 * R8 多选项单选受控零状态。</p>
 */
public final class SceneSegmented {

    // ==================== segment 背景配色（enabled × selected × pressed 三态，无 hover） ====================

    /**
     * 未选中 + 默认态段背景（深灰）
     */
    private static final int SEG_UNSEL_ENABLED = 0xFF3A3A3A;
    /**
     * 未选中 + pressed 态段背景（更暗）
     */
    private static final int SEG_UNSEL_PRESSED = 0xFF2A2A2A;
    /**
     * 选中 + 默认态段背景（亮蓝实心）
     */
    private static final int SEG_SEL_ENABLED = 0xFF4A90D9;
    /**
     * 选中 + pressed 态段背景（暗蓝）
     */
    private static final int SEG_SEL_PRESSED = 0xFF3A7BC8;
    /**
     * disabled 态段背景（灰，选中与否同色）
     */
    private static final int SEG_DISABLED = 0xFF2F2F2F;

    /**
     * 选中段文本色（白）
     */
    private static final int TEXT_SELECTED = 0xFFFFFFFF;
    /**
     * 未选中段文本色（暗灰）
     */
    private static final int TEXT_UNSELECTED = 0xFFB0B0B0;

    /**
     * 固定段宽（像素，scene 无 flex-grow 的等宽退让，本批契约外决定）
     */
    private static final int SEGMENT_WIDTH = 72;
    /**
     * 段内边距（像素）
     */
    private static final int SEGMENT_PADDING = 6;
    /**
     * 段圆角（像素）
     */
    private static final int SEGMENT_RADIUS = 4;
    /**
     * 各段之间的横向间距（像素）
     */
    private static final int SEG_GAP = 4;

    /**
     * 纯静态工厂，禁止实例化（强制无状态，契约 R1）
     */
    private SceneSegmented() {
    }

    /**
     * Segmented 输入契约 —— 多选项单选受控，与 {@link SceneRadioGroup.Props} 同构（契约 R2/R8）。
     *
     * @param selectedIndex 当前选中段下标（响应式只读，受控源），控件绝不自己修改此值
     * @param options       段文本列表（构建期固定常量，R2 允许常量）
     * @param enabled       是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onSelect      选择回调，激活某段时以该段下标调用，由外部 set 回 selectedIndex signal
     */
    @Desugar
    public record Props(
        ReadableSignal<Integer> selectedIndex,
        List<String> options,
        ReadableSignal<Boolean> enabled,
        Consumer<Integer> onSelect
    ) {
    }

    /**
     * 工厂：构建 Segmented 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：
     * 体内 for 循环建 N 个 segment 节点（options 固定，循环建树无副作用、只跑一次，守 I3）。
     * 动态外观全落 {@code bind(computed(...))}，交互只经 {@code on} 调 {@code onSelect}（R4/R5/R8）。</p>
     *
     * @param rt    场景运行时
     * @param props Segmented 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneSingleSelectPrimitive.Props primitiveProps = new SceneSingleSelectPrimitive.Props(
                props.selectedIndex(),
                props.options(),
                props.enabled(),
                props.onSelect(),
                SceneSingleSelectPrimitive.Orientation.HORIZONTAL);
            SceneSingleSelectPrimitive.Result result = SceneSingleSelectPrimitive.create(rt, primitiveProps);
            result.root().setCrossAxisAlign(CrossAxisAlign.STRETCH);
            result.root().setGap(SEG_GAP);

            for (SceneSingleSelectPrimitive.ItemHandle handle : result.items()) {
                SceneNode segment = handle.item();
                segment.setFlexDirection(FlexDirection.ROW);
                segment.setMainAxisAlign(MainAxisAlign.CENTER);
                segment.setCrossAxisAlign(CrossAxisAlign.CENTER);
                segment.setPadding(SEGMENT_PADDING);
                segment.setCornerRadius(SEGMENT_RADIUS);
                segment.setPreferredWidth(SEGMENT_WIDTH);
                segment.appendChild(handle.label());

                rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveSegmentBackground(
                        props.enabled().get(),
                        Boolean.TRUE.equals(handle.selected().get()),
                        handle.interaction().pressed().get())),
                    segment::setBackgroundColor);
                rt.bind(Invalidation.PAINT, handle.selected(),
                    selected -> handle.label().setTextColor(Boolean.TRUE.equals(selected)
                        ? TEXT_SELECTED : TEXT_UNSELECTED));
                rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> segment.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));
            }

            return result.root();
        };
    }

    /**
     * 解析 segment 背景色（纯函数，无副作用）。
     *
     * <p>优先级：disabled &gt; pressed &gt; default（照契约无 hover 态）；
     * 同一态下选中与未选中用不同色系区分（选中亮蓝、未选中深灰）。</p>
     *
     * @param enabled  是否启用
     * @param selected 是否为当前选中段
     * @param pressed  是否按压中
     * @return 当前态对应的 ARGB 背景色
     */
    private static int resolveSegmentBackground(Boolean enabled, boolean selected, Boolean pressed) {
        if (!Boolean.TRUE.equals(enabled)) {
            return SEG_DISABLED;
        }
        if (Boolean.TRUE.equals(pressed)) {
            return selected ? SEG_SEL_PRESSED : SEG_UNSEL_PRESSED;
        }
        return selected ? SEG_SEL_ENABLED : SEG_UNSEL_ENABLED;
    }
}
