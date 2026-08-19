package club.heiqi.uilib.ui.scene.control;

import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
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
     * @param contentMode     内容模式（0=原始文本 / 2=富文本标签，见 {@link TextStyle} 常量）
     * @param horizontalAlign 水平对齐
     * @param verticalAlign   垂直对齐
     * @param wrapWidth       最大换行宽度（UI 像素），{@code <=0} 不换行
     */
    @Desugar
    public record Props(
        ReadableSignal<String> text,
        int color,
        int fontSizePx,
        int contentMode,
        TextHorizontalAlign horizontalAlign,
        TextVerticalAlign verticalAlign,
        int wrapWidth
    ) {
        /** 默认样式：主文本色 + 默认字号 + 原始文本模式 + 左上对齐 + 不换行。 */
        public Props(ReadableSignal<String> text) {
            this(text, SceneChromeTokens.TEXT_PRIMARY, DEFAULT_FONT_SIZE_PX, TextStyle.TEXT_MODE_UILIB_RAW,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 0);
        }

        /** 指定颜色与字号的原始文本标签。 */
        public Props(ReadableSignal<String> text, int color, int fontSizePx) {
            this(text, color, fontSizePx, TextStyle.TEXT_MODE_UILIB_RAW,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 0);
        }

        /** 指定颜色、字号与内容模式的标签。 */
        public Props(ReadableSignal<String> text, int color, int fontSizePx, int contentMode) {
            this(text, color, fontSizePx, contentMode,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, 0);
        }

        /** 指定颜色、字号、内容模式与换行宽度的标签。 */
        public Props(ReadableSignal<String> text, int color, int fontSizePx, int contentMode, int wrapWidth) {
            this(text, color, fontSizePx, contentMode,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, wrapWidth);
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
            rt.bindText(root, props.text());
            return root;
        };
    }
}
