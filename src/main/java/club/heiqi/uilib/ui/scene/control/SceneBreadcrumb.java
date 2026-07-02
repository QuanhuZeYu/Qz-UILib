package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;

/**
 * SceneBreadcrumb —— scene 新栈控件层 Phase 4 批 2 面包屑控件（纯展示 + 点击回调）。
 *
 * <h3>定位：纯展示 + 回调，不走受控双向，不用 forEach</h3>
 * <p>本批 Breadcrumb 按「路径构建期固定」处理，<b>不用 forEach、不做动态路径</b>（绕开 I5 风险面，
 * 动态列表排后续批）。Breadcrumb <b>无选中态</b>，纯展示 + 点击回调，<b>控件自身零状态</b>：
 * 点击某段只经 {@code onSelect.accept(path)} 上抛该段 path（纯回调，非受控双向）。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (ROW, crossAxisAlign=CENTER, gap)
 *   └─ 对每段 seg[i]:
 *         ├─ separator[i] (text "&gt;")  ← 非首段才有；装饰 hitTestable=false
 *         └─ segBtn[i] (ROW, padding, cornerRadius)   ← 交互单元 hitTestable=true
 *               └─ label[i] (text)    ← 装饰 hitTestable=false
 * </pre>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>separator 和 segBtn 直接作 root 兄弟（root 是 ROW 自然横排），省 wrapper 层。</li>
 *   <li>所有段可点（含末段，本批简单一致），不做 flex-wrap 换行。</li>
 *   <li>label 静态色（建树时 setTextColor），段背景 bind hover×pressed。</li>
 * </ul>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 不可变常量 + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 装饰子节点命中穿透。</p>
 */
public final class SceneBreadcrumb {

    // ==================== segBtn chrome（全走 SceneChromeTokens / SceneStateColors，无硬编码色值） ====================

    /** 分隔符文本 */
    private static final String SEPARATOR_TEXT = ">";
    /** 段按钮内边距（像素） */
    private static final int SEGBTN_PADDING = SceneChromeTokens.PAD_MD;
    /** 段按钮圆角（像素） */
    private static final int SEGBTN_RADIUS = SceneChromeTokens.RADIUS_MD;
    /** 各元素之间的横向间距（像素） */
    private static final int ROOT_GAP = SceneChromeTokens.GAP_SM;

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
    private SceneBreadcrumb() {
    }

    /**
     * 面包屑的单段路径节点（不可变常量，构建期固定）。
     *
     * @param path  该段路径标识（点击时经 onSelect 上抛）
     * @param label 该段显示文本
     */
    @Desugar
    public record Segment(
            String path,
            String label
    ) {
    }

    /**
     * Breadcrumb 输入契约 —— 纯展示 + 点击回调（契约 R2）。
     *
     * @param segments 段列表（构建期固定常量，每段 path + label）
     * @param enabled  是否启用（响应式只读，可选），false 时禁用点击/键盘并切灰态
     * @param onSelect 选择回调，点击某段时以该段 path 调用（纯回调，非受控双向）
     */
    @Desugar
    public record Props(
            List<Segment> segments,
            ReadableSignal<Boolean> enabled,
            Consumer<String> onSelect
    ) {
    }

    /**
     * 工厂：构建 Breadcrumb 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：
     * 体内 for 循环建各段节点（segments 固定，循环建树无副作用、只跑一次，守 I3）。
     * 段背景随交互态经 {@code bind(computed(...))} 派生，交互只经 {@code on} 调 {@code onSelect}（R4/R5）。</p>
     *
     * @param rt    场景运行时
     * @param props Breadcrumb 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            // ① 建树一次（无副作用，I3）—— 横向容器
            SceneNode root = SceneNode.row();
            root.setCrossAxisAlign(CrossAxisAlign.CENTER);
            root.setGap(ROOT_GAP);

            final List<Segment> segments = props.segments();
            final int count = segments.size();

            for (int idx = 0; idx < count; idx++) {
                final int i = idx; // final 局部副本供 lambda 捕获
                final Segment seg = segments.get(i);

                // separator[i]：非首段才有；分隔符文本，装饰穿透（直接作 root 兄弟）
                if (i > 0) {
                    SceneNode separator = new SceneNode();
                    separator.setHitTestable(false);
                    separator.setText(SEPARATOR_TEXT);
                    separator.setTextColor(SceneChromeTokens.TEXT_SECONDARY);
                    root.appendChild(separator);
                }

                // segBtn[i]：交互单元（hitTestable 默认 true），ROW + padding + 圆角（直接作 root 兄弟）
                SceneNode segBtn = SceneNode.row();
                segBtn.setCrossAxisAlign(CrossAxisAlign.CENTER);
                segBtn.setWidthSizing(SceneNode.WidthSizing.SHRINK);
                segBtn.setPadding(SEGBTN_PADDING);
                segBtn.setCornerRadius(SEGBTN_RADIUS);
                root.appendChild(segBtn);

                // label[i]：段内纯文本装饰子节点，命中穿透到 segBtn（契约 R6）
                SceneNode labelNode = new SceneNode();
                labelNode.setHitTestable(false);
                labelNode.setText(seg.label());
                segBtn.appendChild(labelNode);

                // ② 段各取自己的 interactionState（契约 R5）
                SceneInteractionState is = rt.interactionState(segBtn);

                // ③ 动态外观全走 bind（契约 R4）
                //    segBtn 背景：link 变体（默认透明，hover/pressed 灰档；focused 不加背景，
                //    focus 指示靠下方 label 文本色提亮到 ACCENT_HOVER，避免背景与文本同色）
                rt.bind(Computed.create(() -> SceneStateColors.linkBackground(
                                Boolean.TRUE.equals(props.enabled().get()),
                                Boolean.TRUE.equals(is.hovered().get()),
                                Boolean.TRUE.equals(is.pressed().get()),
                                Boolean.TRUE.equals(is.focused().get()))),
                        segBtn::setBackgroundColor);

                // label 文本色：link 变体（enabled ACCENT 蓝、focused 提亮、disabled 灰）
                rt.bind(Computed.create(() -> SceneStateColors.linkText(
                                Boolean.TRUE.equals(props.enabled().get()),
                                Boolean.TRUE.equals(is.focused().get()))),
                        labelNode::setTextColor);

                // cursor 声明式附着：enabled 指针手型、disabled 禁止符号（挂在交互单元 segBtn 上）
                SceneControlChrome.bindCursor(rt, segBtn, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);

                // ④ 交互经 on → 只调 onSelect 上抛该段 path（纯回调，控件零状态）
                rt.on(segBtn, SceneEventType.CLICK, (ev, ctx) -> {
                    if (Boolean.TRUE.equals(props.enabled().get())) {
                        props.onSelect().accept(seg.path());
                    }
                });

                // 键盘可达：登记进 Tab 焦点环 + Enter/Space 激活
                rt.focusable(segBtn, props.enabled());
                rt.on(segBtn, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                    SceneKey key = ev.getKey();
                    if ((key == SceneKey.ENTER || key == SceneKey.SPACE)
                            && Boolean.TRUE.equals(props.enabled().get())) {
                        props.onSelect().accept(seg.path());
                    }
                });
            }

            return root;
        };
    }
}
