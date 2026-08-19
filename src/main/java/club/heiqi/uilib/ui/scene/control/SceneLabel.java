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
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintCommandType;
import club.heiqi.uilib.ui.scene.paint.PaintFragment;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.TextStyle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * SceneLabel —— 通用文本显示组件，纯文本与现代富文本合一。
 *
 * <h3>定位</h3>
 * <p>scene 新栈的通用只读文本组件：文本经 {@code ReadableSignal} 响应式驱动，
 * 内容模式经 {@link Props#contentMode} 切换（原始文本 / 现代富文本标签），
 * 支持水平/垂直对齐与 {@code wrapWidth} 自动换行（富文本感知：标签不占宽、样式跨行续传）。</p>
 *
 * <h3>富文本标签语法（{@code contentMode = TextStyle.TEXT_MODE_RICH_TAGS}）</h3>
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
     * Label 输入契约 —— 全部只读 signal + 静态样式。
     *
     * @param text            文本内容（响应式只读；富文本模式可含标签）
     * @param color           ARGB 文字颜色
     * @param fontSizePx      UI 像素字号
     * @param contentMode     内容模式（0=原始文本 / 1=原版格式码 / 2=富文本标签，见 {@link TextStyle} 常量）
     * @param horizontalAlign 水平对齐
     * @param verticalAlign   垂直对齐
     * @param wrapWidth       最大换行宽度（UI 像素），{@code <=0} 不换行
     * @param lineHeightMultiplier 行距倍数（0=自动行高，&gt;0 时行高 = 自动行高 × 倍数，优先于绝对行高）
     * @param lineHeightPx    绝对行高（UI 像素，0=自动行高，倍数未设置时生效）
     * @param maxLines        最大显示行数（0=不限行；超出部分丢弃）
     * @param ellipsis        是否在截断末行追加省略号（仅换行宽度有效时生效）
     * @param onLinkClick     链接点击回调（URL 入参）；null 表示无交互（节点不可命中，零开销）
     */
    @Desugar
    public record Props(
        ReadableSignal<String> text,
        int color,
        int fontSizePx,
        int contentMode,
        TextHorizontalAlign horizontalAlign,
        TextVerticalAlign verticalAlign,
        int wrapWidth,
        double lineHeightMultiplier,
        int lineHeightPx,
        int maxLines,
        boolean ellipsis,
        Consumer<String> onLinkClick
    ) {
        /** 默认样式：主文本色 + 默认字号 + 原始文本模式 + 左上对齐 + 不换行。 */
        public Props(ReadableSignal<String> text) {
            this(text, SceneChromeTokens.TEXT_PRIMARY, DEFAULT_FONT_SIZE_PX, TextStyle.TEXT_MODE_UILIB_RAW,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 0, 0.0D, 0, 0, false, null);
        }

        /** 指定颜色与字号的原始文本标签。 */
        public Props(ReadableSignal<String> text, int color, int fontSizePx) {
            this(text, color, fontSizePx, TextStyle.TEXT_MODE_UILIB_RAW,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 0, 0.0D, 0, 0, false, null);
        }

        /** 指定颜色、字号与内容模式的标签。 */
        public Props(ReadableSignal<String> text, int color, int fontSizePx, int contentMode) {
            this(text, color, fontSizePx, contentMode,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 0, 0.0D, 0, 0, false, null);
        }

        /** 指定颜色、字号、内容模式与换行宽度的标签。 */
        public Props(ReadableSignal<String> text, int color, int fontSizePx, int contentMode, int wrapWidth) {
            this(text, color, fontSizePx, contentMode,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, wrapWidth, 0.0D, 0, 0, false, null);
        }
    }

    /**
     * 工厂：构建标签组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（I3）：
     * 只建 SceneNode + 设静态样式 + {@code rt.bindText} 绑定响应式文本。</p>
     *
     * @param rt    场景运行时（提供 bindText）
     * @param props 标签输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneNode root = new SceneNode();
            root.setHitTestable(false);
            root.setTextColor(props.color());
            root.setFontSize(props.fontSizePx());
            root.setTextContentMode(props.contentMode());
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
                // 链接命中（点击/悬停共用）：读当前 fragment 的 LINK_REGION 命令（相对节点局部坐标，
                // 与 ctx 的 localPointer 同坐标系），矩形包含判定。
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
     * 命中测试：返回包含 (localX, localY) 的链接区域 URL。
     *
     * @param root   标签根节点（其 fragment 含 LINK_REGION 命令）
     * @param localX 指针相对节点局部的 X
     * @param localY 指针相对节点局部的 Y
     * @return 命中链接 URL；未命中返回 null
     */
    private static String hitLinkUrl(SceneNode root, int localX, int localY) {
        Object cached = root.getCachedPaint();
        if (!(cached instanceof PaintFragment)) {
            return null;
        }
        for (PaintCommand cmd : ((PaintFragment) cached).getCommands()) {
            if (cmd.getType() == PaintCommandType.LINK_REGION
                    && localX >= cmd.getLeft() && localX < cmd.getRight()
                    && localY >= cmd.getTop() && localY < cmd.getBottom()) {
                return cmd.getLinkUrl();
            }
        }
        return null;
    }
}
