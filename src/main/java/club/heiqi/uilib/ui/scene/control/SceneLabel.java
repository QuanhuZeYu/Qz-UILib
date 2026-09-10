package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.TextStyle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.LinkHitRegion;
import club.heiqi.uilib.ui.scene.text.SceneTextMode;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * SceneLabel —— 通用文本显示组件，纯文本与现代富文本合一。
 *
 * <h3>定位</h3>
 * <p>scene 新栈的通用只读文本组件：文本经 {@code ReadableSignal} 响应式驱动，
 * 内容模式经 {@link Props} 切换（原始文本 / 现代富文本标签），
 * 支持水平/垂直对齐与 {@code wrapWidth} 自动换行（富文本感知：标签不占宽、样式跨行续传）。</p>
 *
 * <h3>Props 分组（审查报告 §8 B2-1）</h3>
 * <p>输入契约按语义分组为 {@link TextSpec}（文本内容与字形）、{@link LayoutSpec}（换行/行距/限行）、
 * {@link AlignSpec}（对齐），外加交互回调 {@code onLinkClick}；历史 4 个级联构造器与 12 个
 * accessor（{@code text()/color()/...}）全部保留委托分组，公共 API 面零破坏。</p>
 *
 * <h3>富文本标签语法（{@code contentMode = SceneTextMode.RICH_TAGS}）</h3>
 * <ul>
 *   <li>{@code <color=#FF5533>} / {@code <color=red>} 颜色（6/8 位 hex 或 CSS 16 基础色名）</li>
 *   <li>{@code <b>} {@code <i>} {@code <u>} {@code <s>} 粗体/斜体/下划线/删除线</li>
 *   <li>{@code <size=N>} 绝对像素字号（1..256）；{@code <br>} 硬换行</li>
 *   <li>任意嵌套、样式继承父级；转义实体 {@code &lt;} {@code &gt;} {@code &amp;}</li>
 * </ul>
 *
 * <h3>契约</h3>
 * <p>纯静态工厂 + 无状态（契约 R1）；输入全只读 signal（R2）；组件函数交
 * {@link SceneRuntime#mount} 执行一次（I3）；节点不可命中，不拦截输入。</p>
 *
 * <h3>前景归属</h3>
 * <p>默认路径（未传颜色参数）跟随来源主题的正文前景：构建期捕获
 * {@link SceneThemes#resolve} 返回的主题信号，派生期只读该信号（见 {@link #bindTextColor}），
 * 主题切换只重算前景、不重建节点。显式 {@code color} 继续生效且不再订阅主题——包括色值
 * 恰好等于旧默认色的调用（禁止用 {@code color == TEXT_PRIMARY} 推断调用意图）。
 * Label 不追加玻璃、不参与表面绑定，{@code textColor} 只有本类一个写入者。</p>
 */
public final class SceneLabel {

    /** 默认字号（UI 像素），与 {@link SceneNode} 默认一致。 */
    public static final int DEFAULT_FONT_SIZE_PX = 16;

    /**
     * 纯静态工厂，禁止实例化。
     */
    private SceneLabel() {
    }

    /**
     * 文本内容与字形分组。
     *
     * @param text        文本内容（响应式只读；富文本模式可含标签）
     * @param color       ARGB 文字颜色（{@code followTheme=false} 时为权威显式值；
     *                    {@code followTheme=true} 时仅作占位，实际前景取当前主题正文色）
     * @param fontSizePx  UI 像素字号
     * @param contentMode 内容模式编码（0=UILIB_RAW / 1=MINECRAFT_FORMATTED / 2=RICH_TAGS，
     *                    锚定 {@link SceneTextMode}）
     * @param followTheme 是否跟随当前主题前景（true=默认路径；false=显式 color）
     * @param fontSize    运行时可调字号信号（UI 像素）；null = 用 {@code fontSizePx} 定值
     */
    @Desugar
    public record TextSpec(
            ReadableSignal<String> text,
            int color,
            int fontSizePx,
            int contentMode,
            boolean followTheme,
            ReadableSignal<Integer> fontSize
    ) {
        /**
         * 旧 5 参构造器（历史兼容入口）：字号只有构建期定值，无运行时信号。
         *
         * @param text        文本内容
         * @param color       ARGB 文字颜色
         * @param fontSizePx  UI 像素字号
         * @param contentMode 内容模式编码
         * @param followTheme 是否跟随当前主题前景
         */
        public TextSpec(ReadableSignal<String> text, int color, int fontSizePx, int contentMode,
                boolean followTheme) {
            this(text, color, fontSizePx, contentMode, followTheme, null);
        }

        /**
         * 旧 4 参构造器（历史兼容入口）：语义 = 显式 color（{@code followTheme=false}）。
         *
         * @param text        文本内容
         * @param color       显式 ARGB 文字颜色
         * @param fontSizePx  UI 像素字号
         * @param contentMode 内容模式编码
         */
        public TextSpec(ReadableSignal<String> text, int color, int fontSizePx, int contentMode) {
            this(text, color, fontSizePx, contentMode, false, null);
        }

        /** 默认分组：前景跟随当前主题 + 默认字号 + 原始文本模式（本目标的默认视觉改变）。 */
        public TextSpec(ReadableSignal<String> text) {
            this(text, SceneChromeTokens.TEXT_PRIMARY, DEFAULT_FONT_SIZE_PX,
                    TextStyle.TEXT_MODE_UILIB_RAW, true, null);
        }
    }

    /**
     * 换行/行距/限行分组。
     *
     * @param wrapWidth            最大换行宽度（UI 像素），{@code <=0} 不换行
     * @param lineHeightMultiplier 行距倍数（0=自动行高，&gt;0 时行高 = 自动行高 × 倍数，优先于绝对行高）
     * @param lineHeightPx         绝对行高（UI 像素，0=自动行高，倍数未设置时生效）
     * @param maxLines             最大显示行数（0=不限行；超出部分丢弃）
     * @param ellipsis             是否在截断末行追加省略号（仅换行宽度有效时生效）
     */
    @Desugar
    public record LayoutSpec(
            int wrapWidth,
            double lineHeightMultiplier,
            int lineHeightPx,
            int maxLines,
            boolean ellipsis
    ) {
        /** 默认分组：不换行、自动行高、不限行、无省略号。 */
        public static LayoutSpec defaults() {
            return new LayoutSpec(0, 0.0D, 0, 0, false);
        }
    }

    /**
     * 对齐分组。
     *
     * @param horizontalAlign 水平对齐
     * @param verticalAlign   垂直对齐
     */
    @Desugar
    public record AlignSpec(
            TextHorizontalAlign horizontalAlign,
            TextVerticalAlign verticalAlign
    ) {
        /** 默认分组：左上对齐。 */
        public static AlignSpec defaults() {
            return new AlignSpec(TextHorizontalAlign.LEFT, TextVerticalAlign.TOP);
        }
    }

    /**
     * Label 输入契约 —— 按语义分组 + 交互回调。
     *
     * <p>历史 12 字段平铺构造器与 accessor 全部保留（委托分组），见
     * {@link #Props(ReadableSignal, int, int, int, TextHorizontalAlign, TextVerticalAlign, int, double, int, int, boolean, Consumer)}
     * 与 {@link #text()} 等兼容方法；新代码建议用 {@link #builder(ReadableSignal)} 或分组构造。</p>
     *
     * @param textSpec    文本内容与字形分组
     * @param layoutSpec  换行/行距/限行分组
     * @param alignSpec   对齐分组
     * @param onLinkClick 链接点击回调（URL 入参）；null 表示无交互（节点不可命中，零开销）
     */
    @Desugar
    public record Props(
            TextSpec textSpec,
            LayoutSpec layoutSpec,
            AlignSpec alignSpec,
            Consumer<String> onLinkClick
    ) {
        /** 默认样式：前景跟随当前主题 + 默认字号 + 原始文本模式 + 左上对齐 + 不换行。 */
        public Props(ReadableSignal<String> text) {
            this(new TextSpec(text), LayoutSpec.defaults(), AlignSpec.defaults(), null);
        }

        /** 指定颜色与字号的原始文本标签（显式 color，不跟随主题）。 */
        public Props(ReadableSignal<String> text, int color, int fontSizePx) {
            this(text, color, fontSizePx, TextStyle.TEXT_MODE_UILIB_RAW,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 0, 0.0D, 0, 0, false, null);
        }

        /** 指定颜色、字号与内容模式的标签（显式 color，不跟随主题）。 */
        public Props(ReadableSignal<String> text, int color, int fontSizePx, int contentMode) {
            this(text, color, fontSizePx, contentMode,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 0, 0.0D, 0, 0, false, null);
        }

        /** 指定颜色、字号、内容模式与换行宽度的标签（显式 color，不跟随主题）。 */
        public Props(ReadableSignal<String> text, int color, int fontSizePx, int contentMode, int wrapWidth) {
            this(text, color, fontSizePx, contentMode,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, wrapWidth, 0.0D, 0, 0, false, null);
        }

        /** 全字段平铺构造器（历史兼容入口，委托分组；语义 = 显式 color）。 */
        public Props(ReadableSignal<String> text, int color, int fontSizePx, int contentMode,
                TextHorizontalAlign horizontalAlign, TextVerticalAlign verticalAlign,
                int wrapWidth, double lineHeightMultiplier, int lineHeightPx,
                int maxLines, boolean ellipsis, Consumer<String> onLinkClick) {
            this(new TextSpec(text, color, fontSizePx, contentMode),
                    new LayoutSpec(wrapWidth, lineHeightMultiplier, lineHeightPx, maxLines, ellipsis),
                    new AlignSpec(horizontalAlign, verticalAlign), onLinkClick);
        }

        // ==================== 历史 accessor 兼容层（委托分组，公共 API 面零破坏） ====================

        /** @return 文本内容（响应式只读） */
        public ReadableSignal<String> text() {
            return textSpec.text();
        }

        /** @return ARGB 文字颜色 */
        public int color() {
            return textSpec.color();
        }

        /** @return UI 像素字号 */
        public int fontSizePx() {
            return textSpec.fontSizePx();
        }

        /** @return 运行时可调字号信号；null = 只有构建期定值 */
        public ReadableSignal<Integer> fontSize() {
            return textSpec.fontSize();
        }

        /** @return 内容模式编码（锚定 {@link SceneTextMode}） */
        public int contentMode() {
            return textSpec.contentMode();
        }

        /** @return 水平对齐 */
        public TextHorizontalAlign horizontalAlign() {
            return alignSpec.horizontalAlign();
        }

        /** @return 垂直对齐 */
        public TextVerticalAlign verticalAlign() {
            return alignSpec.verticalAlign();
        }

        /** @return 最大换行宽度（UI 像素，{@code <=0} 不换行） */
        public int wrapWidth() {
            return layoutSpec.wrapWidth();
        }

        /** @return 行距倍数（0=自动行高） */
        public double lineHeightMultiplier() {
            return layoutSpec.lineHeightMultiplier();
        }

        /** @return 绝对行高（UI 像素，0=自动行高） */
        public int lineHeightPx() {
            return layoutSpec.lineHeightPx();
        }

        /** @return 最大显示行数（0=不限行） */
        public int maxLines() {
            return layoutSpec.maxLines();
        }

        /** @return 是否在截断末行追加省略号 */
        public boolean ellipsis() {
            return layoutSpec.ellipsis();
        }

        /**
         * 有界 builder 入口（审查报告 §8 B2-1）。
         *
         * @param text 文本内容（响应式只读）
         * @return 未定型的 builder（每 setter 返回自身，build 收敛为 Props）
         */
        public static Builder builder(ReadableSignal<String> text) {
            return new Builder(text);
        }
    }

    /**
     * Props 有界 builder：逐项覆盖默认值，{@link #build()} 一次性产出不可变 {@link Props}。
     *
     * <p>全部 setter 返回 this 链式调用；未调用的字段取组件默认值（与单参 Props 构造器一致）。
     * 未调用 {@link #color(int)} 时前景跟随当前主题；一旦显式指定颜色即切回显式语义。</p>
     */
    public static final class Builder {

        private final ReadableSignal<String> text;

        private int color = SceneChromeTokens.TEXT_PRIMARY;

        /** 是否仍跟随主题：未调 {@link #color(int)} 时为 true（与单参 Props 构造器同语义）。 */
        private boolean followTheme = true;

        private int fontSizePx = DEFAULT_FONT_SIZE_PX;

        /** 运行时可调字号信号；null = 用 {@link #fontSizePx} 定值。 */
        private ReadableSignal<Integer> fontSize = null;

        private int contentMode = TextStyle.TEXT_MODE_UILIB_RAW;

        private TextHorizontalAlign horizontalAlign = TextHorizontalAlign.LEFT;

        private TextVerticalAlign verticalAlign = TextVerticalAlign.TOP;

        private int wrapWidth = 0;

        private double lineHeightMultiplier = 0.0D;

        private int lineHeightPx = 0;

        private int maxLines = 0;

        private boolean ellipsis = false;

        private Consumer<String> onLinkClick = null;

        private Builder(ReadableSignal<String> text) {
            this.text = text;
        }

        /** @param color ARGB 文字颜色（显式指定；此后前景不再跟随主题） */
        public Builder color(int color) {
            this.color = color;
            this.followTheme = false;
            return this;
        }

        /** @param fontSizePx UI 像素字号 */
        public Builder fontSizePx(int fontSizePx) {
            this.fontSizePx = fontSizePx;
            return this;
        }

        /**
         * 运行时可调字号：信号变化时标签文字与按字号算出来的几何同帧跟随。
         *
         * <p>与 {@link #fontSizePx(int)} 写的是同一个 root 字号（标签根的 SceneNode 属性），
         * 两者同时给出时以本信号为准。</p>
         *
         * @param fontSize UI 像素字号信号；null = 不指定
         * @return 当前 builder
         */
        public Builder fontSize(ReadableSignal<Integer> fontSize) {
            this.fontSize = fontSize;
            return this;
        }

        /** @param contentMode 内容模式编码（锚定 {@link SceneTextMode}） */
        public Builder contentMode(int contentMode) {
            this.contentMode = contentMode;
            return this;
        }

        /** @param horizontalAlign 水平对齐 */
        public Builder horizontalAlign(TextHorizontalAlign horizontalAlign) {
            this.horizontalAlign = horizontalAlign;
            return this;
        }

        /** @param verticalAlign 垂直对齐 */
        public Builder verticalAlign(TextVerticalAlign verticalAlign) {
            this.verticalAlign = verticalAlign;
            return this;
        }

        /** @param wrapWidth 最大换行宽度（UI 像素），{@code <=0} 不换行 */
        public Builder wrapWidth(int wrapWidth) {
            this.wrapWidth = wrapWidth;
            return this;
        }

        /** @param lineHeightMultiplier 行距倍数（0=自动行高） */
        public Builder lineHeightMultiplier(double lineHeightMultiplier) {
            this.lineHeightMultiplier = lineHeightMultiplier;
            return this;
        }

        /** @param lineHeightPx 绝对行高（UI 像素，0=自动行高） */
        public Builder lineHeightPx(int lineHeightPx) {
            this.lineHeightPx = lineHeightPx;
            return this;
        }

        /** @param maxLines 最大显示行数（0=不限行） */
        public Builder maxLines(int maxLines) {
            this.maxLines = maxLines;
            return this;
        }

        /** @param ellipsis 是否在截断末行追加省略号 */
        public Builder ellipsis(boolean ellipsis) {
            this.ellipsis = ellipsis;
            return this;
        }

        /** @param onLinkClick 链接点击回调（URL 入参）；null 表示无交互 */
        public Builder onLinkClick(Consumer<String> onLinkClick) {
            this.onLinkClick = onLinkClick;
            return this;
        }

        /**
         * @return 按当前 builder 状态产出的不可变 Props（未指定 color 时前景跟随主题）
         */
        public Props build() {
            return new Props(new TextSpec(text, color, fontSizePx, contentMode, followTheme, fontSize),
                    new LayoutSpec(wrapWidth, lineHeightMultiplier, lineHeightPx, maxLines, ellipsis),
                    new AlignSpec(horizontalAlign, verticalAlign), onLinkClick);
        }
    }

    /**
     * 工厂：构建标签组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（I3）：
     * 只建 SceneNode + 设静态样式 + 前景归属 + {@code rt.bindText} 绑定响应式文本。
     * 不追加玻璃、不参与表面绑定。</p>
     *
     * @param rt    场景运行时（提供 bindText / bindComputed）
     * @param props 标签输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneNode root = new SceneNode();
            root.setHitTestable(false);
            bindTextColor(rt, root, props.textSpec());
            root.setFontSize(props.fontSizePx());
            // 运行时可调字号：写的是同一个 root（root 是标签字号的唯一真值）。
            SceneControlTypography.applyFontSize(rt, root, props.fontSize());
            root.setTextMode(SceneTextMode.fromCode(props.contentMode()));
            root.setTextHorizontalAlign(props.horizontalAlign());
            root.setTextVerticalAlign(props.verticalAlign());
            root.setMaxTextWidth(props.wrapWidth());
            root.setLineHeightMultiplier(props.lineHeightMultiplier());
            root.setLineHeightPx(props.lineHeightPx());
            root.setMaxLines(props.maxLines());
            root.setEllipsis(props.ellipsis());
            if (props.onLinkClick() != null) {
                root.setHitTestable(true);
                root.setCursor(SceneCursor.DEFAULT);
                // 链接命中（点击/悬停共用）：读绘制引擎投影的强类型 LinkHitRegion 缓存
                // （相对节点局部坐标，与 ctx 的 localPointer 同坐标系），矩形包含判定。
                rt.on(root, SceneEventType.CLICK, (ev, ctx) -> {
                    String url = hitLinkUrl(root, ctx.getLocalPointerX(), ctx.getLocalPointerY());
                    if (url != null) {
                        props.onLinkClick().accept(url);
                    }
                });
                // 悬停命中 → 写入 activeLinkUrl（绘制层据此给命中区域画高亮背景，标脏去重）；
                // 光标只在命中链接时切手型，非链接区域保持默认。
                rt.on(root, SceneEventType.POINTER_MOVE, (ev, ctx) -> {
                    String url = hitLinkUrl(root, ctx.getLocalPointerX(), ctx.getLocalPointerY());
                    root.setActiveLinkUrl(url);
                    root.setCursor(url != null ? SceneCursor.POINTER : SceneCursor.DEFAULT);
                });
                // 指针离开节点（hovered 翻转）清悬停命中并恢复默认光标
                rt.bind(rt.interactionState(root).hovered(), hovered -> {
                    if (!Boolean.TRUE.equals(hovered)) {
                        root.setActiveLinkUrl(null);
                        root.setCursor(SceneCursor.DEFAULT);
                    }
                });
            }
            rt.bindText(root, props.text());
            return root;
        };
    }

    /**
     * 前景归属：{@code followTheme=true} 时绑定 {@link SceneThemes#foreground} 的主题派生，
     * 否则静态写入显式 color。
     *
     * <p><b>构建期</b>只取共享入口返回的派生信号，不自行解引用未求值的 Computed
     * （规划 3.1：未提供初值的 Computed 首次 flush 前为 null，不得在构造绑定器时解引用；
     * {@code SceneThemes.foreground} 已带构造期初值）。前景在首次 flush 物化并写入，
     * 此后主题信号变化自动重算，不重建节点、不重新调用工厂。</p>
     *
     * <p>{@code followTheme=false} 恒返回显式 color，不订阅主题——因此显式传入与旧默认相同的
     * 色值（{@code SceneChromeTokens.TEXT_PRIMARY}）也保持显式语义，不存在按色值猜调用意图。</p>
     *
     * @param rt   场景运行时
     * @param root 标签根节点
     * @param spec 文本分组（携带 followTheme 与显式 color）
     */
    private static void bindTextColor(SceneRuntime rt, SceneNode root, TextSpec spec) {
        if (!spec.followTheme()) {
            root.setTextColor(spec.color());
            return;
        }
        // 主题前景派生统一由 SceneThemes 提供（构造期捕获来源主题、带初值的 Computed，主题切换自动重算），
        // 本类只负责绑定；followTheme=false 恒返回显式 color，不订阅主题。
        rt.bind(SceneThemes.foreground(rt), root::setTextColor);
    }

    /**
     * 命中测试：返回包含 (localX, localY) 的链接区域 URL（命中数据正式化，审查报告 §8 B2-5）。
     *
     * <p>数据源为绘制引擎投影缓存的强类型 {@link LinkHitRegion} 列表，
     * 不再强转 PaintFragment 遍历绘制命令流。</p>
     *
     * @param root   标签根节点（绘制后持有 link 命中区域缓存）
     * @param localX 指针相对节点局部的 X
     * @param localY 指针相对节点局部的 Y
     * @return 命中链接 URL；未命中返回 null
     */
    private static String hitLinkUrl(SceneNode root, int localX, int localY) {
        java.util.List<LinkHitRegion> regions = root.getCachedLinkHitRegions();
        if (regions == null) {
            return null;
        }
        for (LinkHitRegion region : regions) {
            if (region.contains(localX, localY)) {
                return region.getUrl();
            }
        }
        return null;
    }
}
