package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.FontSource;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.AnchoredPortalLayout;
import club.heiqi.uilib.ui.scene.overlay.AnchorProvider;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.ScenePortalHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;
import club.heiqi.uilib.ui.text.TextEllipsizer;

/**
 * SceneTooltip —— hover 延时浮层控件。
 *
 * <h3>行为</h3>
 * <ul>
 *   <li>目标节点 hover 保持约 {@link Props#delayMillis()} 后经 {@code rt.portalAnchored} 出现，
 *       锚定在目标节点几何上；</li>
 *   <li>hover 结束、enabled 置 false 或目标节点被卸载时<b>即时</b>关闭；</li>
 *   <li>文本按 {@code maxWidthPx} 换行（单词超宽走 {@link TextEllipsizer} ellipsis），
 *       {@code maxLines} 截断并省略末行；</li>
 *   <li>不抢焦点、不拦截点击：浮层整树 {@code hitTestable=false}（命中测试穿透到下层），
 *       关闭策略 {@link OverlayDismissPolicy#NONE}（ESC/外部点击/选中均不产生关闭请求）；</li>
 *   <li>外观走主题：浮层根取 {@link SceneTheme.Role#OVERLAY} 配方（{@link SceneThemes#surface}），
 *       文字取主题正文前景色，主题切换只重派生属性、不重建节点。</li>
 * </ul>
 *
 * <h3>延时机制</h3>
 * <p>延时用 {@link SceneRuntime#__startMotion} 的 TimedTrack 作平台无关计时器（完成回调触发显示），
 * 无任何平台/线程依赖；hover 取消时 {@code __cancelMotion} 即时撤销。Motion 未启用（默认）的
 * runtime 中延时退化为立即显示——启用 Motion 并逐帧采样的宿主获得完整 500ms 延时。</p>
 *
 * <h3>生命周期</h3>
 * <p>必须在组件构建作用域（mount/portal builder）内调用：hover 状态机 effect、portal 与清理回调
 * 全部归属当前 Owner，组件卸载时自动撤销计时器并关闭浮层，无泄漏。</p>
 */
public final class SceneTooltip {

    /** 默认 hover 保持延时。 */
    public static final int DEFAULT_DELAY_MILLIS = 500;
    /** 默认最大宽度。 */
    public static final int DEFAULT_MAX_WIDTH_PX = 260;
    /** 默认最大行数。 */
    public static final int DEFAULT_MAX_LINES = 8;

    private static final int FONT_SIZE = 12;
    /** 浮层 enabled 恒真：tooltip 无禁用态（显隐由 hover 延时状态机与 {@link Props#enabled()} 门控）。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;
    /**
     * 浮层外壳边框预算：盒宽 = 换行宽 + 左右 padding + 左右本值。
     *
     * <p>实际边框宽来自 OVERLAY 配方（{@code borderWidth}），本常量只参与 portal 首选宽度计算，
     * 不写回节点属性——布局口径不随主题改变。</p>
     */
    private static final int BORDER_WIDTH = 1;

    private SceneTooltip() {
    }

    /**
     * Tooltip 输入契约（不可变）。
     *
     * @param target      目标节点（hover 信号来源，非 null，须参与命中测试）
     * @param text        文本内容信号（非 null）
     * @param enabled     启用信号（null = 恒启用）
     * @param delayMillis hover 保持延时（&gt;=0）
     * @param maxWidthPx  换行宽度（&lt;=0 不限宽）
     * @param maxLines    最大行数（&lt;=0 不限）
     * @param breakLongWords 无折行机会的超宽词（URL/路径/哈希）是否按码点逐行切开；
     *                        false = 对该词加省略号（旧行为）。tooltip 是用来揭示被截断
     *                        内容的，链接提示这类文本应传 true
     */
    @Desugar
    public record Props(SceneNode target, ReadableSignal<String> text, ReadableSignal<Boolean> enabled,
                        int delayMillis, int maxWidthPx, int maxLines, boolean breakLongWords) {

        /** 六参便捷构造：超宽词按旧行为 ellipsize（既有调用点零改动）。 */
        public Props(SceneNode target, ReadableSignal<String> text, ReadableSignal<Boolean> enabled,
                     int delayMillis, int maxWidthPx, int maxLines) {
            this(target, text, enabled, delayMillis, maxWidthPx, maxLines, false);
        }

        /**
         * 创建 Tooltip 属性（规范构造：校验 + 逐字段赋值）。
         */
        public Props(SceneNode target, ReadableSignal<String> text, ReadableSignal<Boolean> enabled,
                     int delayMillis, int maxWidthPx, int maxLines, boolean breakLongWords) {
            this.target = Objects.requireNonNull(target, "target");
            this.text = Objects.requireNonNull(text, "text");
            if (delayMillis < 0) {
                throw new IllegalArgumentException("delayMillis 不可为负数");
            }
            this.enabled = enabled;
            this.delayMillis = delayMillis;
            this.maxWidthPx = maxWidthPx;
            this.maxLines = maxLines;
            this.breakLongWords = breakLongWords;
        }

        /** 便捷工厂：默认延时/宽度/行数、恒启用。 */
        public static Props of(SceneNode target, ReadableSignal<String> text) {
            return new Props(target, text, null, DEFAULT_DELAY_MILLIS, DEFAULT_MAX_WIDTH_PX,
                    DEFAULT_MAX_LINES);
        }
    }

    /** 换行行记录（index 作 keyed 复用键，文本经 Computed 实时重派生）。 */
    @Desugar
    private record Line(int index, String text) {
    }

    /**
     * 把 hover 延时浮层挂到目标节点。
     *
     * <p>必须在组件构建作用域（Owner.current() != null）内调用；所有 effect / portal /
     * cleanup 归属当前 Owner，随组件卸载一并回收。</p>
     *
     * @param rt    场景运行时（须注入文本度量）
     * @param props 输入契约（非 null）
     */
    public static void attach(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");
        Owner owner = Owner.current();
        if (owner == null) {
            throw new IllegalStateException(
                    "SceneTooltip.attach 必须在组件构建作用域（mount/portal builder）内调用");
        }
        ReadableSignal<Boolean> hovered = rt.interactionState(props.target()).hovered();
        Signal<Boolean> visible = Signal.create(Boolean.FALSE);
        Object timerKey = new Object();
        boolean[] armed = {false};
        // 受控关闭闩锁：host dismiss（锚点滚出裁剪/离树）后，若 hover 仍为 true（无滚动重算的
        // 数据变更路径），旧状态机会在延时届满后立刻重开，与 dismiss 形成周期性闪烁。
        // 闩锁要求 hover 真正退出并重新进入后才允许再次展示。
        boolean[] dismissed = {false};

        // hover 延时状态机：hover+enabled 起动计时，任一退出条件即时撤销
        Effect.create(() -> {
            boolean hover = Boolean.TRUE.equals(hovered.get());
            boolean enabled = props.enabled() == null
                    || Boolean.TRUE.equals(props.enabled().get());
            if (hover && enabled) {
                if (!armed[0] && !dismissed[0] && !Boolean.TRUE.equals(visible.get())) {
                    armed[0] = true;
                    rt.__startMotion(timerKey, props.delayMillis(), ignored -> { }, () -> {
                        armed[0] = false;
                        boolean stillHover = Boolean.TRUE.equals(hovered.get());
                        boolean stillEnabled = props.enabled() == null
                                || Boolean.TRUE.equals(props.enabled().get());
                        boolean attached = props.target().__getParent() != null;
                        if (stillHover && stillEnabled && attached && !dismissed[0]) {
                            visible.set(Boolean.TRUE);
                        }
                    });
                }
            } else {
                armed[0] = false;
                dismissed[0] = false;
                rt.__cancelMotion(timerKey);
                visible.set(Boolean.FALSE);
            }
        });

        // 目标卸载兜底：布局纪元推进时若目标已离树则关闭（hover 信号在卸载时不保证回写 false）
        rt.bind(rt.layoutDoneSignal(), epoch -> Effect.untrack(() -> {
            if (Boolean.TRUE.equals(visible.get()) && props.target().__getParent() == null) {
                visible.set(Boolean.FALSE);
            }
        }));

        // dismissRequest 写 visible=false：锚点滚出可视裁剪（host 每帧 dismissOverlaysWithInvisibleAnchor）
        // 与策略驱动关闭共用同一受控收口，避免浮层残留。
        // AnchoredPortalLayout(preferredWidth=maxWidth)：tooltip 按内容宽布局（至多 maxWidth），
        // 而非默认的触发器等宽（会把多行文本压成单元格宽度）。
        // 浮层内容建在 portal 树里，与目标节点不同树 → 用 portal 入口把「目标节点的字号声明」
        // 落到内容根（内容根持声明，行节点沿父链继承）。换行测量（content 内的 wrapLines）与
        // 绘制字号因此同源：都取 target.declaredFontSize()，盒宽 = 换行宽 + padding + 边框才成立。
        // 正文字号 = 目标节点链上的**字号声明**（只读投影；null = 链上没有任何声明）。
        // 声明值变化才产出新值，下游（portal 入口与换行测量的 Computed）只在真正变化时重算。
        // 无声明时不写 scope：行节点层 4a 回落 FONT_SIZE(12) —— tooltip 的既有默认观感保持不变。
        ReadableSignal<Integer> bodyFontSize = Computed.create(declaredFontOrNull(props.target()), () -> {
            rt.layoutDoneSignal().get();
            return declaredFontOrNull(props.target());
        });
        ScenePortalHandle tooltipPortal = rt.portalAnchored(visible,
                () -> content(rt, props, bodyFontSize),
                OverlayDismissPolicy.NONE,
                () -> {
                    dismissed[0] = true;
                    visible.set(Boolean.FALSE);
                }, AnchorProvider.forNode(props.target()),
                null, new AnchoredPortalLayout(portalPreferredWidthPx(props), 0, 8));
        tooltipPortal.fontSize(bodyFontSize);

        owner.onCleanup(() -> {
            armed[0] = false;
            rt.__cancelMotion(timerKey);
            visible.set(Boolean.FALSE);
        });
    }

    /**
     * 浮层盒宽：必须容得下「按 {@code props.maxWidthPx()} 排好的最长行 + 内边距 + 边框」。
     *
     * <p>历史缺陷：这里硬编码 {@link #DEFAULT_MAX_WIDTH_PX}(260)，而调用方可以要求更宽的
     * 换行宽（聊天链接 tooltip 用 320）。换行按 320 算、盒子只有 260，两者各说各话 ——
     * 真机表现为「tooltip 包不住内容」：文字紧贴甚至压过边框，且被盒子二次折行。
     * 盒宽 = 换行宽 + 左右 padding(PAD_SM) + 左右 1px 边框。</p>
     *
     * @param props tooltip 输入契约
     * @return portal 首选宽度（{@code maxWidthPx <= 0} 时回退默认宽）
     */
    static int portalPreferredWidthPx(Props props) {
        if (props.maxWidthPx() <= 0) {
            return DEFAULT_MAX_WIDTH_PX;
        }
        return props.maxWidthPx() + 2 * SceneChromeTokens.PAD_SM + 2 * BORDER_WIDTH;
    }

    /**
     * 浮层内容：SHRINK 整列，hitTestable=false 全树穿透，行文本经 wrapLines 换行。
     *
     * <p>外观唯一写入者：浮层根走 {@link SceneTheme.Role#OVERLAY} 配方（底色/缘色/圆角/边框/
     * 浮雕/滤镜全部来自主题），行文本前景取主题正文色。本方法不再静态写底色/边框/圆角，也不设置
     * 任何可命中的交互单元——tooltip 不得拦截点击。</p>
     */
    private static SceneNode content(SceneRuntime rt, Props props, ReadableSignal<Integer> bodyFontSize) {
        SceneNode root = SceneNode.column();
        root.setHitTestable(false);
        root.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        root.setPadding(SceneChromeTokens.PAD_SM);
        root.setGap(2);
        // 浮层根表面：OVERLAY 配方画在浮层根（唯一外观写入者），配方在 portal 构建调用栈内取，
        // 延迟显示时继承来源主题（withTheme 作用域）。enabled 恒真：tooltip 的显隐由 hover+延时
        // 状态机与 Props.enabled 门控，浮层自身不承接禁用态。
        SceneSurfaceBinder.bind(rt, root, SceneThemes.surface(rt, SceneTheme.Role.OVERLAY),
                ALWAYS_ENABLED, rt.interactionState(root));

        ReadableSignal<List<Line>> lines = Computed.create(() -> {
            // 测量字号必须与渲染字号同源（内容根 scope = 目标节点声明；无声明则同为层 4a 的 12），
            // 否则换行边界与绘制字号各说各话：盒宽按小字号算、文字按大字号画 → 内容压过边框。
            // bodyFontSize 已按值去重，只有字号真的变化（或文本变化）才重新换行。
            Integer declaredFontSizePx = bodyFontSize.get();
            int bodyFontSizePx = declaredFontSizePx == null
                    ? FONT_SIZE : declaredFontSizePx.intValue();
            List<String> wrapped = TextEllipsizer.wrapLines(
                    s -> rt.measureTextWidth(s, bodyFontSizePx),
                    props.text().get(), props.maxWidthPx(), props.maxLines(),
                    props.breakLongWords());
            List<Line> result = new ArrayList<>(wrapped.size());
            for (int i = 0; i < wrapped.size(); i++) {
                result.add(new Line(i, wrapped.get(i)));
            }
            return result;
        });
        ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
        rt.forEach(root, lines, Line::index, line -> {
            SceneNode node = new SceneNode();
            // 内容根已由 portal 入口写入目标节点的字号声明，行节点沿父链继承；
            // FONT_SIZE 降级为层 4a 回落值：仅当目标节点链上完全没有声明时兜底 12。
            node.setFallbackFontSize(FONT_SIZE);
            node.setHitTestable(false);
            rt.bindComputed(foreground::get, node::setTextColor);
            rt.bindComputed(() -> lineTextAt(lines.get(), line.index()), node::setText);
            return node;
        });
        return root;
    }

    /**
     * 目标节点链上的字号<b>声明</b>；{@code null} = 链上没有任何声明（只落到回落值/框架常量）。
     *
     * <p>区分「声明」与「解析结果」是本方法存在的唯一理由：{@link SceneNode#declaredFontSize()}
     * 在无声明时返回框架常量 16，无法表达「没声明」，会让 tooltip 的层 4a 回落值（12）永远失效。
     * 依据 {@link SceneNode#fontSizeSource()}：EXPLICIT / SCOPE / ENVIRONMENT 视为有声明
     * （层 1/2/3），NODE_DEFAULT / UNRESOLVED 视为无声明（只有回落值或常量）。</p>
     *
     * @param target 目标节点
     * @return 声明字号；null = 无声明
     */
    private static Integer declaredFontOrNull(SceneNode target) {
        FontSource source = target.fontSizeSource();
        if (source == FontSource.EXPLICIT || source == FontSource.SCOPE
                || source == FontSource.ENVIRONMENT) {
            return Integer.valueOf(target.declaredFontSize());
        }
        return null;
    }

    /** 行节点复用后按下标重新派生文本（keyed forEach 复用行时保持新鲜）。 */
    private static String lineTextAt(List<Line> lines, int index) {
        if (lines == null) {
            return "";
        }
        for (Line line : lines) {
            if (line.index() == index) {
                return line.text() == null ? "" : line.text();
            }
        }
        return "";
    }
}
