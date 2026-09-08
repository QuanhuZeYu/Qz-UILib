package club.heiqi.uilib.ui.hud.api;

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
         * 给定内容盒宽度求外框宽度（确定性，可在 layout 之前用于 placement）。
         *
         * @param contentWidth 内容盒宽（logical px）
         * @return 外框宽；LEFT/RIGHT 且可见时 = 内容宽 + gap + thickness
         */
        public int outerWidth(int contentWidth) {
            int width = Math.max(1, contentWidth);
            if (spec == null || !isVisible() || spec.getSide().isHorizontalEdge()) {
                return width;
            }
            return width + spec.getGap() + spec.getThickness();
        }

        /**
         * 给定内容盒高度求外框高度（确定性，可在 layout 之前用于 placement）。
         *
         * @param contentHeight 内容盒高（logical px）
         * @return 外框高；TOP/BOTTOM 且可见时 = 内容高 + gap + thickness
         */
        public int outerHeight(int contentHeight) {
            int height = Math.max(1, contentHeight);
            if (spec == null || !isVisible() || !spec.getSide().isHorizontalEdge()) {
                return height;
            }
            return height + spec.getGap() + spec.getThickness();
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
        // effect 首次执行在 flush 期；先同步一次树状态，避免首帧多挂一层
        applyVisibility(result, Boolean.TRUE.equals(spec.getVisible().get()));
        rt.bind(spec.getVisible(), value -> applyVisibility(result, Boolean.TRUE.equals(value)));
        return result;
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
