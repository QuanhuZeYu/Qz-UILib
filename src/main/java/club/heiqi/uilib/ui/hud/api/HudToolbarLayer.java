package club.heiqi.uilib.ui.hud.api;

import java.util.List;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButtonPrimitive;
import club.heiqi.uilib.ui.scene.control.SceneButtonVariant;
import club.heiqi.uilib.ui.scene.control.SceneLiquidGlassStyle;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.control.SceneTooltip;

import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * HUD 外接工具栏挂载层（可复用；规划《聊天工具栏与HUD布局编辑》增量）。
 *
 * <p>把"工具栏挂在 HUD 内容盒外侧某一条边"这件事收口成一次装配：调用方给出内容根、
 * {@link HudToolbarSpec} 与工具栏工厂，本类返回一个外框节点（内容 + 工具栏的 flex 容器）
 * 与确定性外框尺寸查询。宿主（{@code SceneHudHost}）与打开态页面（聊天输入屏）用同一份
 * 装配，不再各自在容器内部插一行按钮。</p>
 *
 * <h3>装配语义</h3>
 * <ul>
 *   <li>外框是 SHRINK 容器：TOP/BOTTOM 用 COLUMN，LEFT/RIGHT 用 ROW；gap 即内容与工具栏
 *       之间的间隙；</li>
 *   <li>沿挂载边方向的尺寸由 {@link HudToolbarSpec#getThickness()} 钉死（TOP/BOTTOM 设
 *       preferredHeight，LEFT/RIGHT 设 preferredWidth）；垂直方向两者各取自身尺寸，外框
 *       取较大者，因此工具栏永远不与内容重叠；</li>
 *   <li>TOP/BOTTOM 下工具栏宽度用 SHRINK 而非 FILL：FILL 子节点会把 SHRINK 外框的宽度
 *       反馈成父约束宽（SizingCalculator.computeShrinkContainerWidth 读子 cachedLayout），
 *       工具栏就会被拉到整个视口宽；SHRINK 让外框宽度 = max(内容宽, 工具栏自身宽)；</li>
 *   <li><b>只给工具栏根设 SHRINK 不够</b>：SceneNode 默认 widthSizing=FILL，工具栏行里的
 *       按钮若也是 FILL，会被拉成整行宽，行宽再反馈成视口宽。工具栏内容的按钮/子项必须
 *       自己收缩（如 {@code ChatToolbar} 对水平边按钮设 SHRINK），本层只负责外框方向与厚度；</li>
 *   <li>外框尺寸（{@link Result#outerWidth(int)} / {@link Result#outerHeight(int)}）在未被
 *       厚度钉死的那一轴取「内容尺寸」与「工具栏实测外尺寸」的较大者：工具栏比内容宽/高时
 *       外框随之变宽/高，放置与拖动 clamp 才不会把工具栏推出视口。首帧工具栏尚未布局时退回
 *       内容尺寸，下一帧按实测收敛；</li>
 *   <li>{@link HudToolbarSpec#getVisible()} 为 false 时工具栏节点移出树，外框退化为内容
 *       自身尺寸（{@link Result#outerWidth(int)} / {@link Result#outerHeight(int)} 同步
 *       反映），恢复为 true 时按挂载边重新插回原位置。</li>
 * </ul>
 *
 * <p>可见性只改本层节点结构，经 {@code rt.bind} 走 Signal 驱动；工具栏节点本身仍是普通
 * scene 子树（控件 + 信号），输入作用域与命中仲裁仍由宿主/页面统一管理。</p>
 */
public final class HudToolbarLayer {

    private HudToolbarLayer() {
    }

    /** 挂载结果：外框、内容、工具栏与外框尺寸查询。 */
    public static final class Result {

        private final SceneNode root;
        private HudScaleState scale;
        private final SceneNode content;
        private final SceneNode toolbar;
        private final HudToolbarSpec spec;

        private Result(SceneNode root, SceneNode content, SceneNode toolbar, HudToolbarSpec spec) {
            this.root = root;
            this.content = content;
            this.toolbar = toolbar;
            this.spec = spec;
        }

        /** @return 外框节点（挂到宿主根上的那个；无工具栏时就是内容根） */
        public SceneNode root() {
            return root;
        }

        /** @return 内容根节点（HUD 主体） */
        public SceneNode content() {
            return content;
        }

        /** @return 工具栏根节点；无工具栏（未注册）时为 null */
        public SceneNode toolbar() {
            return toolbar;
        }

        /** @return 规格；无工具栏时为 null */
        public HudToolbarSpec spec() {
            return spec;
        }

        /**
         * @return 工具栏当前是否真的挂在树中（无工具栏恒 false）。
         *
         * <p>以树状态为准而非规格信号：{@link Signal#set(Object)} 帧末才生效，若用信号值算外框，
         * 同一帧内"信号已请求可见、树还没挂上"会给出与实际布局不一致的外框尺寸。
         * 信号只负责驱动挂/摘，外框尺寸必须与真实子树一致。</p>
         */
        public boolean isVisible() {
            return toolbar != null && toolbar.__getParent() == root;
        }

        /**
         * 给定缩放前内容盒宽度求视觉外框宽度（含每 HUD 倍率，向上取整，用于 placement）。
         *
         * <p>水平边（TOP/BOTTOM）：宽度由「内容宽」与「工具栏实测宽」的较大者决定——工具栏
         * 比内容宽时外框必须跟着变宽，否则打开态页面按内容宽 placement，右锚点下整条工具栏
         * 会溢出视口右侧（已知缺陷）。工具栏尚未布局时（首帧）退回内容宽，下一帧按实测收敛。</p>
         *
         * @param contentWidth 内容盒宽（logical px）
         * @return 外框宽；LEFT/RIGHT 且可见时 = 内容宽 + gap + thickness
         */
        public int outerWidth(int contentWidth) {
            return scaled(logicalOuterWidth(contentWidth));
        }

        /** 缩放前外框宽，供宿主布局；内容尺寸参数同为缩放前 logical px。 */
        public int logicalOuterWidth(int contentWidth) {
            int width = Math.max(1, contentWidth);
            if (spec == null || !isVisible()) {
                return width;
            }
            if (spec.getSide().isHorizontalEdge()) {
                return Math.max(width, measuredExtent(true));
            }
            return width + spec.getGap() + spec.getThickness();
        }

        /**
         * 给定缩放前内容盒高度求视觉外框高度（含每 HUD 倍率，向上取整，用于 placement）。
         *
         * <p>竖直边（LEFT/RIGHT）：高度由「内容高」与「工具栏实测高」的较大者决定（竖列
         * 按钮可能比内容高）；工具栏尚未布局时退回内容高，下一帧按实测收敛。</p>
         *
         * @param contentHeight 内容盒高（logical px）
         * @return 外框高；TOP/BOTTOM 且可见时 = 内容高 + gap + thickness
         */
        public int outerHeight(int contentHeight) {
            return scaled(logicalOuterHeight(contentHeight));
        }

        /** 缩放前外框高，供宿主布局；内容尺寸参数同为缩放前 logical px。 */
        public int logicalOuterHeight(int contentHeight) {
            int height = Math.max(1, contentHeight);
            if (spec == null || !isVisible()) {
                return height;
            }
            if (!spec.getSide().isHorizontalEdge()) {
                return Math.max(height, measuredExtent(false));
            }
            return height + spec.getGap() + spec.getThickness();
        }

        /** 每 HUD 倍率；由宿主成对转换绘制、布局约束和输入，scene 内仍使用原始 logical px。 */
        public float scaleFactor() { return scale == null ? 1.0f : scale.factor(); }

        /** 独立挂载拥有自己的状态；service 挂载共享注册项状态。未注册直通返回 null。 */
        public HudScaleState scale() { return scale; }

        private int scaled(int extent) {
            int percent = scale == null ? HudScaleState.DEFAULT_PERCENT : scale.percent().get().intValue();
            return (int) Math.min(Integer.MAX_VALUE, ((long) extent * percent + 99) / 100);
        }

        /**
         * 工具栏内在主轴尺寸。新增公共工具让 factory 根成为中间组合层，不能只累加它被
         * 右锚点父约束夹窄的 cachedLayout：那会低估真实动作行并使 placement 永久漂移。
         * 递归聚合容器、保留显式 preferred 外尺寸和裁剪视口，不把按钮内部标签计成外宽。
         */
        private int measuredExtent(boolean horizontal) {
            return intrinsicExtent(toolbar, horizontal, false);
        }

    }

    /**
     * 无工具栏直通结果（未注册 / 调用方不接外接层）：外框即内容，不额外包一层。
     *
     * @param content 内容根
     * @return 直通结果
     */
    public static Result passthrough(SceneNode content) {
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        return new Result(content, content, null, null);
    }

    /**
     * 装配外接工具栏层。
     *
     * @param rt      宿主场景运行时（可见性绑定归属它，随 runtime.dispose 回收）
     * @param spec    规格（不可为 null）
     * @param content 内容根（不可为 null）
     * @param factory 工具栏工厂（不可为 null；返回 null 立即失败）
     * @return 挂载结果
     */
    public static Result mount(SceneRuntime rt, HudToolbarSpec spec, SceneNode content,
            HudWindowFactory factory) {
        return mount(rt, spec, content, factory, new HudScaleState());
    }

    /** 使用调用方共享的每 HUD 倍率装配；同一 state 可供多个独立 runtime 投放。 */
    public static Result mount(SceneRuntime rt, HudToolbarSpec spec, SceneNode content,
            HudWindowFactory factory, HudScaleState scale) {
        if (scale == null) throw new IllegalArgumentException("scale must not be null");
        if (rt == null) {
            throw new IllegalArgumentException("rt must not be null");
        }
        if (spec == null) {
            throw new IllegalArgumentException("spec must not be null");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        if (factory == null) {
            throw new IllegalArgumentException("factory must not be null");
        }
        SceneNode toolbar = factory.build(rt);
        if (toolbar == null) {
            throw new IllegalStateException("HUD toolbar factory must return a node");
        }
        boolean horizontal = spec.getSide().isHorizontalEdge();
        if (spec.isScaleControls()) toolbar = withScaleControls(rt, spec, toolbar, scale);
        SceneNode wrapper = horizontal ? SceneNode.column() : SceneNode.row();
        wrapper.setHitTestable(false)
                .setWidthSizing(SceneNode.WidthSizing.SHRINK)
                .setGap(spec.getGap());
        if (horizontal) {
            toolbar.setPreferredHeight(spec.getThickness());
            toolbar.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        } else {
            toolbar.setPreferredWidth(spec.getThickness());
        }
        if (spec.getSide().isTrailing()) {
            wrapper.appendChild(content);
            wrapper.appendChild(toolbar);
        } else {
            wrapper.appendChild(toolbar);
            wrapper.appendChild(content);
        }
        final Result result = new Result(wrapper, content, toolbar, spec);
        result.scale = scale;
        // effect 首次执行在 flush 期；先同步一次树状态，避免首帧多挂一层
        applyVisibility(result, Boolean.TRUE.equals(spec.getVisible().get()));
        rt.bind(spec.getVisible(), value -> applyVisibility(result, Boolean.TRUE.equals(value)));
        return result;
    }

    /** 聚合任意深度的组合容器，不能把约束夹窄的中间容器盒误当成内容内在尺寸。 */
    private static int intrinsicExtent(SceneNode node, boolean horizontal, boolean honorPreferred) {
        int preferred = horizontal ? node.getPreferredWidth() : node.getPreferredHeight();
        if (honorPreferred && preferred > 0) return preferred;
        List<SceneNode> children = node.__getChildren();
        if (children.isEmpty() || (honorPreferred && node.isClipChildren())) {
            Object cached = node.getCachedLayout();
            return cached instanceof LayoutBox
                    ? (horizontal ? ((LayoutBox) cached).getWidth() : ((LayoutBox) cached).getHeight()) : 0;
        }
        boolean alongMain = (node.getFlexDirection() == FlexDirection.ROW) == horizontal;
        int extent = 0;
        for (SceneNode child : children) {
            int childExtent = intrinsicExtent(child, horizontal, true)
                    + (horizontal ? child.marginH() : child.marginV());
            extent = alongMain ? extent + childExtent : Math.max(extent, childExtent);
        }
        if (alongMain) extent += Math.max(0, children.size() - 1) * node.getGap();
        return extent + (horizontal ? node.getPaddingLeft() + node.getPaddingRight()
                : node.getPaddingTop() + node.getPaddingBottom());
    }

    private static SceneNode withScaleControls(SceneRuntime rt, HudToolbarSpec spec, SceneNode custom,
            HudScaleState scale) {
        boolean horizontal = spec.getSide().isHorizontalEdge();
        SceneNode toolbar = (horizontal ? SceneNode.row() : SceneNode.column())
                .setWidthSizing(SceneNode.WidthSizing.SHRINK).setHitTestable(false).setGap(6)
                .setCrossAxisAlign(CrossAxisAlign.CENTER);
        // 空容器也是合法工厂输出；显式空文本令空叶收缩为零宽，避免默认 FILL 挤走公共工具。
        // 后续 forEach 挂入子节点后仍按容器布局，空文本不参与子树尺寸。
        if (custom.getText() == null && custom.__getChildren().isEmpty()) custom.setText("");
        if (horizontal) {
            custom.setWidthSizing(SceneNode.WidthSizing.SHRINK).setPreferredHeight(spec.getThickness());

        } else {
            custom.setPreferredWidth(spec.getThickness());

        }
        // 工厂可能使用 rt.forEach，不能把公共按钮追加到其受协调器管理的子列表中。
        toolbar.appendChild(custom);
        rt.mount(toolbar, () -> scaleButton(rt, "-", () -> scale.percent().get() > HudScaleState.MIN_PERCENT,
                scale::zoomOut, spec.getThickness(), () -> "缩小 HUD"));
        rt.mount(toolbar, () -> scaleButton(rt, "1:1", () -> true, scale::reset,
                spec.getThickness(), () -> "当前 " + scale.percent().get() + "% · 点击恢复 100%"));
        rt.mount(toolbar, () -> scaleButton(rt, "+", () -> scale.percent().get() < HudScaleState.MAX_PERCENT,
                scale::zoomIn, spec.getThickness(), () -> "放大 HUD"));
        // fixed 子项在约束变化时可以继续复用 layout；组合根必须有确定的内在主轴尺寸，
        // 否则 SHRINK 会永久保留右锚点首帧被夹窄的盒。布局完成信号负责动态动作列表的后续变化。
        rt.bindComputed(() -> {
            rt.layoutDoneSignal().get();
            return intrinsicExtent(toolbar, horizontal, false);
        }, value -> {
            if (horizontal) toolbar.setPreferredWidth(value);
            else toolbar.setPreferredHeight(value);
        });
        return toolbar;
    }

    private static SceneNode scaleButton(SceneRuntime rt, String label, ReadableSignal<Boolean> enabled,
            Runnable action, int size, ReadableSignal<String> tooltip) {
        SceneButtonPrimitive.Result primitive = SceneButtonPrimitive.create(rt,
                new SceneButtonPrimitive.Props(() -> label, enabled, action));
        SceneNode button = primitive.root();
        int buttonSize = Math.min(24, size);
        button.setPreferredWidth(buttonSize).setPreferredHeight(buttonSize).setPadding(1)
                .setWidthSizing(SceneNode.WidthSizing.SHRINK);
        SceneLiquidGlassStyle.bindButton(rt, button, primitive.label(), enabled, SceneButtonVariant.STANDARD,
                UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN, 6, 1.0f));
        SceneTooltip.attach(rt, SceneTooltip.Props.of(button, tooltip));
        return button;
    }

    /** 可见性 → 工具栏在树中的挂/摘（只动本层，不触碰内容子树）。 */
    private static void applyVisibility(Result result, boolean visible) {
        SceneNode toolbar = result.toolbar;
        SceneNode wrapper = result.root;
        if (visible) {
            if (toolbar.__getParent() != null) {
                return;
            }
            if (result.spec.getSide().isTrailing()) {
                wrapper.appendChild(toolbar);
            } else {
                wrapper.insertBefore(toolbar, result.content);
            }
        } else if (toolbar.__getParent() == wrapper) {
            wrapper.removeChild(toolbar);
        }
    }
}
