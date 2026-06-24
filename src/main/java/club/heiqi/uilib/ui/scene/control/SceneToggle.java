package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneToggle —— scene 新栈控件层 Phase 4 批 1 首批真实迁移控件（开关/拨动控件）。
 *
 * <h3>定位：受控双向控件（契约 R7）</h3>
 * <p>受控双向契约同 {@link SceneCheckbox}：控件<b>零内部状态</b>，当前开关态由外部 {@code on}
 * 只读 signal 驱动；点击时<b>绝不自己翻转</b>，而是经 {@code onChange.accept(!on.get())}
 * 把「期望新值」交还外部，由外部 set 回 on signal（守 R1/R5/I11/R7）。</p>
 *
 * <h3>结构</h3>
 * <p>root（交互单元，hitTestable 默认 true，ROW + 交叉轴 CENTER + gap）
 * + track 子节点（48×24 固定圆角胶囊，装饰穿透，内含 thumb）
 * + label 子节点（文本，装饰穿透）。
 * thumb（18 圆点）作为 track 的子节点，靠 track 的 {@code MainAxisAlign} START/END
 * 切换左右两种<b>静态位置</b>表达 on/off（本批不做 transform 平滑动画，动画排后续批）。</p>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 装饰子节点命中穿透 /
 * R7 受控双向零内部状态。</p>
 */
public final class SceneToggle {

    // ==================== track 四态背景配色（grounded 深灰系，复用 SceneButton 风格） ====================

    /** off + 默认态 track 背景（深灰） */
    private static final int TRACK_OFF_ENABLED = 0xFF3A3A3A;
    /** off + hover 态 track 背景（稍亮） */
    private static final int TRACK_OFF_HOVER = 0xFF505050;
    /** off + pressed 态 track 背景（更暗） */
    private static final int TRACK_OFF_PRESSED = 0xFF2A2A2A;
    /** on + 默认态 track 背景（亮蓝） */
    private static final int TRACK_ON_ENABLED = 0xFF4A90D9;
    /** on + hover 态 track 背景（更亮蓝） */
    private static final int TRACK_ON_HOVER = 0xFF5BA0E9;
    /** on + pressed 态 track 背景（暗蓝） */
    private static final int TRACK_ON_PRESSED = 0xFF3A7BC8;
    /** disabled 态 track 背景（灰，on 与否同色） */
    private static final int TRACK_DISABLED = 0xFF2F2F2F;

    /** track 边框色（中灰） */
    private static final int BORDER_COLOR = 0xFF808080;

    /** enabled thumb 颜色（亮灰白） */
    private static final int THUMB_ENABLED = 0xFFE0E0E0;
    /** disabled thumb 颜色（暗灰） */
    private static final int THUMB_DISABLED = 0xFF888888;

    /** enabled label 文本色（白） */
    private static final int TEXT_ENABLED = 0xFFFFFFFF;
    /** disabled label 文本色（暗灰） */
    private static final int TEXT_DISABLED = 0xFF888888;

    /** track 固定宽度（像素） */
    private static final int TRACK_WIDTH = 48;
    /** track 固定高度（像素） */
    private static final int TRACK_HEIGHT = 24;
    /** track 内边距（让 thumb 不贴边，像素） */
    private static final int TRACK_PADDING = 3;
    /** thumb 固定直径（像素） */
    private static final int THUMB_SIZE = 18;
    /** track/thumb 边框宽度（像素） */
    private static final int BORDER_WIDTH = 1;
    /** 圆角半径（像素，足够大使 track 呈胶囊、thumb 呈圆） */
    private static final int CAPSULE_RADIUS = 999;
    /** root 行内间距（track 与 label 之间，像素） */
    private static final int GAP = 8;

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
    private SceneToggle() {
    }

    /**
     * Toggle 输入契约 —— 受控双向：当前态由外部只读 signal 驱动，交互经 onChange 交还期望新值（契约 R2/R7）。
     *
     * @param on       开关态（响应式只读，受控源），控件绝不自己翻转或缓存此值
     * @param label    标签文本（响应式只读）
     * @param enabled  是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onChange 切换回调，激活时以 {@code !on.get()}（期望新值）调用，由外部 set 回 on signal
     */
    @Desugar
    public record Props(
            ReadableSignal<Boolean> on,
            ReadableSignal<String> label,
            ReadableSignal<Boolean> enabled,
            Consumer<Boolean> onChange
    ) {
    }

    /**
     * 工厂：构建 Toggle 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：建树 + 设静态属性 +
     * {@code rt.bind/bindText/on/focusable}，动态外观全落 {@code bind(computed(...))}，
     * thumb 位置随 on 态经 {@code bind(LAYOUT, on, ...)} 切 MainAxisAlign，
     * 交互只经 {@code on} 调 {@code onChange}（R4/R5/R7）。</p>
     *
     * @param rt    场景运行时
     * @param props Toggle 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            // ① 建树一次（无副作用，I3）—— 纯结构 + 静态样式
            SceneNode root = new SceneNode();
            root.setFlexDirection(FlexDirection.ROW);
            root.setCrossAxisAlign(CrossAxisAlign.CENTER);
            root.setGap(GAP);

            // track：48×24 固定圆角胶囊，装饰穿透；ROW + 交叉轴 CENTER 使 thumb 垂直居中
            SceneNode track = new SceneNode();
            track.setFlexDirection(FlexDirection.ROW);
            track.setCrossAxisAlign(CrossAxisAlign.CENTER);
            track.setPreferredWidth(TRACK_WIDTH);
            track.setPreferredHeight(TRACK_HEIGHT);
            track.setPadding(TRACK_PADDING);
            track.setBorderWidth(BORDER_WIDTH);
            track.setBorderColor(BORDER_COLOR);
            track.setCornerRadius(CAPSULE_RADIUS);
            track.setHitTestable(false);
            root.appendChild(track);

            // thumb：18 圆点，track 的子节点，装饰穿透；靠 track.mainAxisAlign 切左右静态位置
            SceneNode thumb = new SceneNode();
            thumb.setPreferredWidth(THUMB_SIZE);
            thumb.setPreferredHeight(THUMB_SIZE);
            thumb.setCornerRadius(CAPSULE_RADIUS);
            thumb.setHitTestable(false);
            track.appendChild(thumb);

            // label：纯文本装饰子节点，装饰穿透（契约 R6）
            SceneNode labelNode = new SceneNode();
            labelNode.setHitTestable(false);
            root.appendChild(labelNode);

            // ② 交互态：读 Router 权威 signal，绝不自维护 boolean（契约 R5）
            SceneInteractionState is = rt.interactionState(root);

            // ③ 动态外观全走 bind（契约 R4）
            //    track 背景：on × 四态优先级 disabled > pressed > hover > default（PAINT 级）
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveTrackBackground(
                            props.enabled().get(),
                            props.on().get(),
                            is.pressed().get(),
                            is.hovered().get())),
                    track::setBackgroundColor);

            // thumb 位置：on→靠右(END)、off→靠左(START)，静态非动画（LAYOUT 级，随 on 值切换会重排——合理）
            rt.bind(Invalidation.LAYOUT, props.on(),
                    o -> track.setMainAxisAlign(Boolean.TRUE.equals(o) ? MainAxisAlign.END : MainAxisAlign.START));

            // thumb 颜色：enabled 亮灰白、disabled 暗灰（PAINT 级）
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> thumb.setBackgroundColor(Boolean.TRUE.equals(e) ? THUMB_ENABLED : THUMB_DISABLED));

            // label 文本内容（响应式）
            rt.bindText(labelNode, props.label());

            // label 文本色：enabled 白、disabled 暗灰
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> labelNode.setTextColor(Boolean.TRUE.equals(e) ? TEXT_ENABLED : TEXT_DISABLED));

            // cursor 声明式附着：enabled 指针手型、disabled 禁止符号
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));

            // ④ 交互经 on → 只调 onChange 交还期望新值（受控双向 R7，绝不自己翻转/缓存）
            rt.on(root, SceneEventType.CLICK, (ev, ctx) -> {
                if (Boolean.TRUE.equals(props.enabled().get())) {
                    props.onChange().accept(!Boolean.TRUE.equals(props.on().get()));
                }
            });

            // 键盘可达：登记进 Tab 焦点环 + Enter/Space 激活
            rt.focusable(root);
            rt.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                SceneKey key = ev.getKey();
                if ((key == SceneKey.ENTER || key == SceneKey.SPACE)
                        && Boolean.TRUE.equals(props.enabled().get())) {
                    props.onChange().accept(!Boolean.TRUE.equals(props.on().get()));
                }
            });

            return root;
        };
    }

    /**
     * 解析 track 四态背景色（纯函数，无副作用）。
     *
     * <p>优先级：disabled &gt; pressed &gt; hover &gt; default；
     * 同一态下 on 与 off 用不同色系区分（on 亮蓝、off 深灰）。</p>
     *
     * @param enabled 是否启用
     * @param on      是否开启
     * @param pressed 是否按压中
     * @param hovered 是否悬停中
     * @return 当前态对应的 ARGB 背景色
     */
    private static int resolveTrackBackground(Boolean enabled, Boolean on, Boolean pressed, Boolean hovered) {
        if (!Boolean.TRUE.equals(enabled)) {
            return TRACK_DISABLED;
        }
        boolean isOn = Boolean.TRUE.equals(on);
        if (Boolean.TRUE.equals(pressed)) {
            return isOn ? TRACK_ON_PRESSED : TRACK_OFF_PRESSED;
        }
        if (Boolean.TRUE.equals(hovered)) {
            return isOn ? TRACK_ON_HOVER : TRACK_OFF_HOVER;
        }
        return isOn ? TRACK_ON_ENABLED : TRACK_OFF_ENABLED;
    }
}
