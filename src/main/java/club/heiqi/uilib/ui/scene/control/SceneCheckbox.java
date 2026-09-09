package club.heiqi.uilib.ui.scene.control;

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
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneCheckbox —— scene 新栈控件层 Phase 4 批 1 首个真实迁移控件。
 *
 * <h3>定位：受控双向控件范本（契约 R7 确立者）</h3>
 * <p>本控件是 strangler 模式下控件层重建的首批真实控件之一，确立「受控双向控件」契约 R7：
 * 控件<b>零内部状态</b>，当前勾选态完全由外部 {@code checked} 只读 signal 驱动；点击时
 * <b>绝不自己翻转</b>，而是经 {@code onChange.accept(!checked.get())} 把「期望的新值」交还外部，
 * 由外部 set 回 checked signal（守 R1/R5/I11，避免双向状态源不一致）。</p>
 *
 * <h3>结构</h3>
 * <p>root（交互单元，hitTestable 默认 true，ROW + SHRINK 内容宽 + 交叉轴 CENTER + gap）
 * + box 子节点（16×16 固定方块，装饰穿透 hitTestable=false）
 * + label 子节点（文本，装饰穿透 hitTestable=false）。
 * root 默认 SHRINK，命中外轮廓收至 box+gap+label 内容宽，避免 FILL 透明根吞掉父行整宽。</p>
 *
 * <h3>外观归属：配方是唯一写入者</h3>
 * <p>box 表面由 {@link SceneSurfaceBinder} 从 {@link SceneThemes#selectableSurface} 的角色配方
 * （{@link SceneTheme.Role#INDICATOR}）派生：未选中取角色配方、选中态换主题强调色 tint，四态优先级
 * disabled &gt; pressed &gt; hovered &gt; idle 由绑定器统一决定。background/border/borderWidth/
 * cornerRadius/backdrop/surfaceElevation 均归表面绑定；勾选标记取
 * {@link SceneThemes#onAccentForeground}、label 前景取 {@link SceneThemes#foreground}/
 * {@link SceneThemes#disabledForeground}。本控件不再静态设色、不叠加第二套实色动画
 * （守「一个属性只有一个外观写入者」）。{@code checked} 仍是唯一权威值，控件不自行翻转。</p>
 *
 * <h3>契约</h3>
 * <p>纯静态工厂 + 私有构造，无实例字段（R1）。Props 全只读 signal + 回调（R2）。
 * {@link #create} 返回 {@code Supplier<SceneNode>} 只执行一次（R3）。动态外观全走
 * {@code rt.bind(Computed, setter)}（R4）。交互态只读 interactionState
 * signal（R5）。装饰子节点 hitTestable=false 命中穿透到根（R6）。受控双向零内部状态（R7）。</p>
 */
public final class SceneCheckbox {

    /** 勾号文本，节点常驻，靠颜色透明/强调底前景切换显隐。 */
    private static final String CHECK_MARK_TEXT = "✓";
    /** 透明色，用于未勾选时隐藏勾号。 */
    private static final int CHECK_MARK_TRANSPARENT = 0x00000000;

    /** box 固定边长（像素） */
    private static final int BOX_SIZE = 16;
    /** root 行内间距（box 与 label 之间，像素） */
    private static final int GAP = SceneChromeTokens.GAP_MD;

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
    private SceneCheckbox() {
    }

    /**
     * Checkbox 输入契约 —— 受控双向：当前值由外部只读 signal 驱动，交互经 onChange 交还期望新值（契约 R2/R7）。
     *
     * @param checked  勾选态（响应式只读，受控源），控件绝不自己翻转或缓存此值
     * @param label    标签文本（响应式只读）
     * @param enabled  是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onChange 切换回调，激活时以 {@code !checked.get()}（期望新值）调用，由外部 set 回 checked signal
     */
    @Desugar
    public record Props(
            ReadableSignal<Boolean> checked,
            ReadableSignal<String> label,
            ReadableSignal<Boolean> enabled,
            Consumer<Boolean> onChange
    ) {
    }

    /**
     * 工厂：构建 Checkbox 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：建树 + 设静态属性 +
     * {@code rt.bind/bindText/on/focusable}，动态外观全落主题配方派生，交互只经 {@code on}
     * 调 {@code onChange}（R4/R5/R7）。</p>
     *
     * @param rt    场景运行时
     * @param props Checkbox 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneToggleablePrimitive.Props primitiveProps = new SceneToggleablePrimitive.Props(
                    props.checked(), props.label(), props.enabled(), props.onChange());
            SceneToggleablePrimitive.Result result = SceneToggleablePrimitive.create(rt, primitiveProps);
            SceneInteractionState interaction = result.interaction();

            SceneNode root = result.root();
            root.setGap(GAP);

            SceneNode box = result.indicator();
            box.setPreferredWidth(BOX_SIZE);
            box.setPreferredHeight(BOX_SIZE);
            box.setFlexDirection(FlexDirection.ROW);
            box.setCrossAxisAlign(CrossAxisAlign.CENTER);
            box.setMainAxisAlign(MainAxisAlign.CENTER);

            SceneNode checkMark = new SceneNode();
            checkMark.setText(CHECK_MARK_TEXT);
            checkMark.setTextColor(CHECK_MARK_TRANSPARENT);
            checkMark.setHitTestable(false);
            box.appendChild(checkMark);

            // box 表面：选中/未选中共享 INDICATOR 角色配方（选中态只换强调色 tint，不靠透明度区分），
            // 圆角/边框宽/边框色/滤镜/浮雕高度全归表面绑定器；本控件不再静态设值、不再叠加实色动画。
            ReadableSignal<SceneSurfaceStyle> surface =
                    SceneThemes.selectableSurface(rt, SceneTheme.Role.INDICATOR, props.checked());
            SceneSurfaceBinder.bind(rt, box, surface, props.enabled(), interaction);

            // 勾选标记：选中取主题强调底前景，未选中保持透明（节点常驻，结构不变）。
            ReadableSignal<Integer> onAccentForeground = SceneThemes.onAccentForeground(rt);
            rt.bindComputed(() -> Boolean.TRUE.equals(props.checked().get())
                            ? onAccentForeground.get() : CHECK_MARK_TRANSPARENT,
                    checkMark::setTextColor);

            // label 前景：启用取主题正文色，禁用取主题禁用色（不再走 SceneStateColors 静态取色）。
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
            rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                            ? foreground.get() : disabledForeground.get(),
                    result.labelNode()::setTextColor);

            // cursor 声明式附着：enabled 指针手型、disabled 禁止符号
            SceneControlChrome.bindCursor(rt, root, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);

            return root;
        };
    }
}
