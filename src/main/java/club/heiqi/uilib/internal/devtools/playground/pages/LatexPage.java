package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.function.Supplier;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.paint.TextStyle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * LaTeX 数学公式演示页 —— {@code <latex>...</latex>} 行内公式的解析、二维布局与渲染。
 *
 * <p>覆盖：分数/根号/上下标/求和积分上下限/希腊字母/矩阵/分段函数/伸缩括号/组合数/重音/
 * 公式内中文（\text）；字号缩放（script 0.7×）与规则线（分数线/根号横线）走 decoration 通道。</p>
 */
public final class LatexPage implements PlaygroundPage {

    @Override
    public String id() {
        return "latex";
    }

    @Override
    public String title() {
        return "LaTeX 公式";
    }

    @Override
    public String description() {
        return "行内 LaTeX 数学子集：分数/根号/上下标/求和积分/矩阵/分段函数/伸缩括号/重音/公式内中文";
    }

    @Override
    public Supplier<SceneNode> build(final SceneRuntime rt) {
        return () -> {
            SceneNode root = SceneNode.column();
            root.setFillParentWidth(true);
            root.setGap(10);

            // ===== 卡片1：公式速览（大字号） =====
            SceneNode heroCard = PlaygroundKit.card();
            heroCard.appendChild(PlaygroundKit.title("公式速览（32px）"));
            heroCard.appendChild(bigText(rt,
                    "<latex>\\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}</latex>", 0));
            heroCard.appendChild(bigText(rt,
                    "<latex>\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}</latex>", 0));
            heroCard.appendChild(bigText(rt,
                    "<latex>\\int_0^\\infty e^{-x}\\,dx = 1</latex>", 0));
            heroCard.appendChild(PlaygroundKit.hint(
                    "分数分子分母 0.7× 字号；根号横线/分数线为规则线（decoration 通道）；"
                    + "求和/积分上下限上下堆叠居中。"));
            root.appendChild(heroCard);

            // ===== 卡片2：分数与根号 =====
            SceneNode fracCard = PlaygroundKit.card();
            fracCard.appendChild(PlaygroundKit.title("分数与根号"));
            fracCard.appendChild(richText(rt, "分数：<latex>\\frac{1}{2} + \\frac{a}{b}</latex>", 0));
            fracCard.appendChild(richText(rt, "平方根：<latex>\\sqrt{x} + \\sqrt{x^2+y^2}</latex>", 0));
            fracCard.appendChild(richText(rt, "开方：<latex>\\sqrt[3]{x}</latex>", 0));
            fracCard.appendChild(PlaygroundKit.hint("根指数在根号左上方（0.7× 字号）。"));
            root.appendChild(fracCard);

            // ===== 卡片3：上下标 =====
            SceneNode supCard = PlaygroundKit.card();
            supCard.appendChild(PlaygroundKit.title("上下标与函数名"));
            supCard.appendChild(richText(rt, "上标/下标：<latex>x^2 + y_i + x_i^2</latex>", 0));
            supCard.appendChild(richText(rt, "欧拉公式：<latex>e^{i\\pi} + 1 = 0</latex>", 0));
            supCard.appendChild(richText(rt, "极限：<latex>\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1</latex>", 0));
            supCard.appendChild(PlaygroundKit.hint(
                    "上标 0.4em 抬升、下标 0.25em 下沉（与富文本 <sup>/<sub> 同口径）；"
                    + "\\lim 为正体文本算子且上下限堆叠。"));
            root.appendChild(supCard);

            // ===== 卡片4：希腊字母与符号 =====
            SceneNode symbolCard = PlaygroundKit.card();
            symbolCard.appendChild(PlaygroundKit.title("希腊字母与运算符"));
            symbolCard.appendChild(richText(rt,
                    "希腊字母：<latex>\\alpha + \\beta = \\gamma, \\Delta \\pi \\sigma</latex>", 0));
            symbolCard.appendChild(richText(rt,
                    "变体对：<latex>\\epsilon \\varepsilon \\phi \\varphi \\theta \\vartheta</latex>", 0));
            symbolCard.appendChild(richText(rt,
                    "关系符：<latex>a \\leq b \\geq c, x \\neq y, \\infty \\pm \\times</latex>", 0));
            symbolCard.appendChild(PlaygroundKit.hint(
                    "符号命令映射 Unicode 码点后走现有字体 fallback 链；缺字形显示 U+FFFD 替换符。"));
            root.appendChild(symbolCard);

            // ===== 卡片5：矩阵与分段函数 =====
            SceneNode matrixCard = PlaygroundKit.card();
            matrixCard.appendChild(PlaygroundKit.title("矩阵与分段函数"));
            matrixCard.appendChild(richText(rt,
                    "矩阵：<latex>\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}</latex>", 0));
            matrixCard.appendChild(richText(rt,
                    "分段：<latex>f(x) = \\begin{cases} x & x > 0 \\\\ -x & x \\leq 0 \\end{cases}</latex>", 0));
            matrixCard.appendChild(PlaygroundKit.hint(
                    "& 分列、\\\\ 换行；列宽取该列最宽格、行列按数学轴居中；pmatrix 圆括号/cases 左花括号随内容伸缩。"));
            root.appendChild(matrixCard);

            // ===== 卡片6：伸缩括号与组合数 =====
            SceneNode delimCard = PlaygroundKit.card();
            delimCard.appendChild(PlaygroundKit.title("伸缩括号与组合数"));
            delimCard.appendChild(richText(rt,
                    "伸缩括号：<latex>\\left( \\frac{a}{b} \\right) \\left[ x \\right]</latex>", 0));
            delimCard.appendChild(richText(rt, "组合数：<latex>\\binom{n}{k} = \\frac{n!}{k!(n-k)!}</latex>", 0));
            delimCard.appendChild(PlaygroundKit.hint(
                    "\\left/\\right 定界符按内容高度整字缩放（分段括号族拼接为后续增强）。"));
            root.appendChild(delimCard);

            // ===== 卡片7：重音 =====
            SceneNode accentCard = PlaygroundKit.card();
            accentCard.appendChild(PlaygroundKit.title("重音与上下划线"));
            accentCard.appendChild(richText(rt,
                    "重音：<latex>\\hat{x} + \\bar{y} + \\vec{v} + \\dot{z} + \\tilde{w}</latex>", 0));
            accentCard.appendChild(richText(rt, "上划线：<latex>\\overline{AB} + \\underline{x}</latex>", 0));
            accentCard.appendChild(PlaygroundKit.hint(
                    "重音为组合变音符叠加在基底顶部居中；\\overline/\\underline 为可变长规则线。"));
            root.appendChild(accentCard);

            // ===== 卡片8：公式内中文与颜色继承 =====
            SceneNode textCard = PlaygroundKit.card();
            textCard.appendChild(PlaygroundKit.title("公式内文本与样式继承"));
            textCard.appendChild(richText(rt,
                    "公式内中文：<latex>\\text{速度} = \\frac{\\Delta s}{\\Delta t}</latex>", 0));
            textCard.appendChild(richText(rt,
                    "颜色继承：<color=#FF5555><latex>x^2</latex></color> 与 <color=#55FF55><latex>\\sqrt{y}</latex></color>", 0));
            textCard.appendChild(richText(rt,
                    "字号继承：<size=20><latex>\\frac{1}{2}</latex></size>", 0));
            textCard.appendChild(PlaygroundKit.hint(
                    "公式继承外层 color/size；\\text{} 内容（含中文）走现有字体 fallback 链。"));
            root.appendChild(textCard);
            return root;
        };
    }

    /** 创建 RICH 模式演示文本节点（可含 <latex> 标签）。 */
    private static SceneNode richText(SceneRuntime rt, String text, int wrapWidth) {
        return SceneLabel.create(rt, new SceneLabel.Props(
                Signal.create(text), PlaygroundKit.TEXT, 14, TextStyle.TEXT_MODE_RICH_TAGS,
                TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, wrapWidth, 0.0D, 0, 0, false, null)).get();
    }

    /** 创建 32px 大字号 RICH 模式演示节点（公式目检用）。 */
    private static SceneNode bigText(SceneRuntime rt, String text, int wrapWidth) {
        return SceneLabel.create(rt, new SceneLabel.Props(
                Signal.create(text), PlaygroundKit.TEXT, 32, TextStyle.TEXT_MODE_RICH_TAGS,
                TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, wrapWidth, 0.0D, 0, 0, false, null)).get();
    }
}
