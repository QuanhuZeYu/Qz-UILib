package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

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
 *   <li>label 文本色与段背景全走主题派生（构建期捕获信号，bind 应用），静态几何保留。</li>
 * </ul>
 *
 * <h3>外观归属：路径文字消费主题语义色，段只做极淡状态覆盖（不装表面/滤镜）</h3>
 * <p>Breadcrumb 是<b>文字路径导航</b>，不是玻璃卡片，故本控件<b>不安装表面绑定器</b>：不写
 * backdrop / border / borderWidth / surfaceElevation，root 与各段默认背景透明；既有静态几何
 * （{@code padding} / {@code cornerRadius} / {@code gap}）保留——几何不属主题。</p>
 *
 * <p><b>文字</b>：路径段与当前段（末段）统一取 {@link SceneThemes#foreground}，分隔符取
 * {@link SceneThemes#mutedForeground}，禁用取 {@link SceneThemes#disabledForeground}。当前段<b>不</b>取
 * {@link SceneThemes#accent}：accent 是强调底/标记用色，深色档在宿主底代理色（0xFF2B2930）上只有
 * 1.54:1（实算见 {@code SceneBreadcrumbTest}），远低于正文可读阈值，而主题正文色对同一底为 11.12:1
 * ——与 G09/Segmented「选中区分由染色承担、不靠文字变色」同口径，本控件优先保证「路径文字保持可读」。
 * 旧的 {@code SceneStateColors.linkText} 取色已删除。</p>
 *
 * <p><b>hover / pressed / focus</b>：只取 {@link SceneTheme.Role#INDICATOR} 配方对应档的 tint 作
 * <b>极淡覆盖</b>（pressed / hovered 取配方同名档，focus 取 idle 档），默认与禁用态保持透明。
 * 这里与导航族「选项保留配方自带轻滤镜」的 G09 裁决相比是本实例的取舍：面包屑没有选中态、路径是纯
 * 文字，且常嵌在已有面板内——给每段装滤镜会既把文字导航做成玻璃条、又形成第二层玻璃。状态覆盖色
 * 仍从配方派生，主题切换自动重算，不写死色值；旧的 {@code SceneStateColors.linkBackground} 取色已删除。</p>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 不可变常量 + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 装饰子节点命中穿透。</p>
 */
public final class SceneBreadcrumb {

    // ==================== segBtn chrome（色值全走主题派生；几何走 SceneChromeTokens） ====================

    /** 分隔符文本 */
    private static final String SEPARATOR_TEXT = ">";
    /** 段按钮内边距（像素） */
    private static final int SEGBTN_PADDING = SceneChromeTokens.PAD_MD;
    /** 段按钮圆角（像素，静态几何，不属主题） */
    private static final int SEGBTN_RADIUS = SceneChromeTokens.RADIUS_MD;
    /** 各元素之间的横向间距（像素） */
    private static final int ROOT_GAP = SceneChromeTokens.GAP_SM;
    /** 无状态覆盖时的透明背景（默认态与禁用态共用）。 */
    private static final int TRANSPARENT = 0;

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
     * 文字色与段背景随主题/交互态经 {@code bind} 派生，交互只经 {@code on} 调 {@code onSelect}（R4/R5）。</p>
     *
     * <p>主题信号在构造期捕获一次（此时 {@link club.heiqi.uilib.ui.reactive.Owner#current()} 是来源
     * 作用域），循环内所有段/分隔符共享同一组派生信号：主题切换只重算外观，不重建节点、不新增订阅。</p>
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
            // 控件内文字跟随 root 字号：分隔符与段标签都是 root 的后代，沿父链继承层 2 声明
            // （编写者用 rt.mount(...).fontSize(n) / root.setFontScope(n)），无需逐点接线。
            final List<Segment> segments = props.segments();
            final int count = segments.size();

            // 主题语义色与状态覆盖来源：构造期捕获来源主题信号，派生期只读它们。
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);
            ReadableSignal<Integer> separatorColor = SceneThemes.mutedForeground(rt);
            // 只消费 INDICATOR 配方的 tint 档作极淡覆盖，不装配方表面（不写 backdrop/border/elevation）。
            ReadableSignal<SceneSurfaceStyle> indicator = SceneThemes.surface(rt, SceneTheme.Role.INDICATOR);

            for (int idx = 0; idx < count; idx++) {
                final int i = idx; // final 局部副本供 lambda 捕获
                final Segment seg = segments.get(i);

                // separator[i]：非首段才有；分隔符文本，装饰穿透（直接作 root 兄弟）
                if (i > 0) {
                    SceneNode separator = new SceneNode();
                    separator.setHitTestable(false);
                    separator.setText(SEPARATOR_TEXT);
                    root.appendChild(separator);
                    // 分隔符取主题次要前景；绑定而非静态设色，主题切换自动更新。
                    rt.bind(separatorColor, separator::setTextColor);
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
                // 时序契约：Router 的 writeHovered/writePressed/writeFocused 对未创建的 signal 直接
                // 短路，故在构建期声明关心，保证后续 hover/pressed/focus 能驱动段的状态覆盖。
                is.hovered();
                is.pressed();
                is.focused();

                // ③ 动态外观全走 bind（契约 R4）
                //    段背景：默认/禁用透明；pressed/hovered 取 INDICATOR 配方对应档 tint、focus 取 idle 档，
                //    都是极淡覆盖（不装表面、不叠滤镜）。优先级 disabled > pressed > hovered > focus > 透明。
                rt.bindComputed(() -> {
                    if (!Boolean.TRUE.equals(props.enabled().get())) {
                        return TRANSPARENT;
                    }
                    SceneSurfaceStyle recipe = indicator.get();
                    if (Boolean.TRUE.equals(is.pressed().get())) {
                        return recipe.getPressed().getTint();
                    }
                    if (Boolean.TRUE.equals(is.hovered().get())) {
                        return recipe.getHovered().getTint();
                    }
                    if (Boolean.TRUE.equals(is.focused().get())) {
                        return recipe.getIdle().getTint();
                    }
                    return TRANSPARENT;
                }, segBtn::setBackgroundColor);

                // label 文本色：启用取主题正文色（当前段/路径段同色，见类注释可读性裁决），
                // 禁用取主题禁用前景色。
                rt.bindComputed(() -> Boolean.TRUE.equals(props.enabled().get())
                        ? foreground.get() : disabledForeground.get(),
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
