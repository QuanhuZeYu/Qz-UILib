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


    @Desugar
    public record Props(
            List<Segment> segments,
            ReadableSignal<Boolean> enabled,
            Consumer<String> onSelect
    ) {
    }


    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            // ① 建树一次（无副作用）—— 横向容器
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

                SceneNode labelNode = new SceneNode();
                labelNode.setHitTestable(false);
                labelNode.setText(seg.label());
                segBtn.appendChild(labelNode);

                SceneInteractionState is = rt.interactionState(segBtn);
                // 时序契约：Router 的 writeHovered/writePressed/writeFocused 对未创建的 signal 直接
                // 短路，故在构建期声明关心，保证后续 hover/pressed/focus 能驱动段的状态覆盖。
                is.hovered();
                is.pressed();
                is.focused();

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
