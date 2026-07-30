package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;

/**
 * SceneNavList —— scene 新栈纵向受控单选导航列表。
 *
 * <p>用于多分类导航场景（如配置页 &gt;5 section 时的左侧 navPane）。
 * 与 {@link SceneSegmented} 同构（N 选 1 受控，契约 R8），区别仅是纵向排列，
 * 导航项填满导航栏宽度，适合设置页分类。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (COLUMN, gap)
 *   └─ item[i] (ROW, mainAxisAlign=START, crossAxisAlign=CENTER, padding, cornerRadius, widthSizing=SHRINK)
 *         └─ label[i] (text, hitTestable=false 穿透到 item)
 * </pre>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 常量 + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 段内文字命中穿透到段 /
 * R8 多选项单选受控零状态。</p>
 */
public final class SceneNavList {

    /** 各项之间的纵向间距（像素） */
    private static final int ITEM_GAP = SceneChromeTokens.GAP_SM;
    /** 项内边距（像素） */
    private static final int ITEM_PADDING = SceneChromeTokens.PAD_MD;
    /** 项圆角（像素） */
    private static final int ITEM_RADIUS = SceneChromeTokens.RADIUS_MD;

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
    private SceneNavList() {
    }

    /**
     * NavList 输入契约 —— 纵向 N 选 1 受控，与 {@link SceneSegmented.Props} 同构（契约 R2/R8）。
     *
     * @param selectedIndex    当前选中项下标（响应式只读，受控源），控件绝不自己修改此值
     * @param options          项文本列表（构建期固定常量，R2 允许常量）
     * @param enabled          是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onSelect         选择回调，激活某项时以该项下标调用，由外部 set 回 selectedIndex signal
     * @param preferredHeight  可选根高度（像素）：非 null 时透传 {@code root.setPreferredHeight}；
     *                         null = 不设，由布局链（fill/grow/约束）决定。NavList 不内置自动推算
     *                         —— 纵向 N 项高度随项数变化，强行推算与 fill 语义冲突
     */
    @Desugar
    public record Props(
        ReadableSignal<Integer> selectedIndex,
        List<String> options,
        ReadableSignal<Boolean> enabled,
        Consumer<Integer> onSelect,
        Integer preferredHeight
    ) {
    }

    /**
     * 工厂：构建 NavList 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：
     * 体内 for 循环建 N 个 item 节点（options 固定，循环建树无副作用、只跑一次，守 I3）。
     * 动态外观全落 {@code bind(computed(...))}，交互只经 {@code on} 调 {@code onSelect}（R4/R5/R8）。</p>
     *
     * @param rt    场景运行时
     * @param props NavList 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneSingleSelectPrimitive.Props primitiveProps = new SceneSingleSelectPrimitive.Props(
                props.selectedIndex(),
                props.options(),
                props.enabled(),
                props.onSelect(),
                SceneSingleSelectPrimitive.Orientation.VERTICAL);
            SceneSingleSelectPrimitive.Result result = SceneSingleSelectPrimitive.create(rt, primitiveProps);
            result.root().setGap(ITEM_GAP);
            // 可选根高：非 null 透传，null 由布局链决定（NavList 不内置自动推算，纵向 N 项高随项数变化）
            if (props.preferredHeight() != null) {
                result.root().setPreferredHeight(props.preferredHeight());
            }

            for (SceneSingleSelectPrimitive.ItemHandle handle : result.items()) {
                SceneNode item = handle.item();
                item.setFlexDirection(FlexDirection.ROW);
                // 纵向导航项：文本左对齐（START），交叉轴居中
                item.setMainAxisAlign(MainAxisAlign.START);
                item.setCrossAxisAlign(CrossAxisAlign.CENTER);
                item.setPadding(ITEM_PADDING);
                item.setCornerRadius(ITEM_RADIUS);
                item.setBorderWidth(1);
                item.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
                item.setFillParentWidth(true);
                item.appendChild(handle.label());

                SceneInteractionState interaction = handle.interaction();

                // 背景：选中走 ACCENT 通道，未选中走标准灰通道
                SceneControlChrome.bindSelectableBackground(rt, item, props.enabled(), handle.selected(), interaction);
                SceneControlChrome.bindStandardBorder(rt, item, props.enabled(), interaction);
                // 文本色：选中白，未选中次要文本
                rt.bindComputed(() -> Boolean.TRUE.equals(handle.selected().get())
                        ? SceneStateColors.standardText(Boolean.TRUE.equals(props.enabled().get()), true)
                        : SceneStateColors.secondaryText(Boolean.TRUE.equals(props.enabled().get())),
                    handle.label()::setTextColor);
                SceneControlChrome.bindCursor(rt, item, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);
            }

            return result.root();
        };
    }
}
