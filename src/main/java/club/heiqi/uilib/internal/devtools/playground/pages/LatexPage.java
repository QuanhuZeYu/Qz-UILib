package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.function.Supplier;

import club.heiqi.uilib.font.latex.LatexShowcaseFormulas;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextHorizontalAlign;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.paint.TextStyle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

/**
 * LaTeX 数学公式演示页 —— {@code <latex>...</latex>} 行内公式的解析、二维布局与渲染。
 *
 * <p>场地 = 自含滚动容器（viewport + 可视滚动条，与 FormPageShell attachScroll 同口径），
 * 内容超出可视高度即滚动。共 12 张测试卡：8 张主题卡（与 LatexShowcaseFormulas 分组同源）
 * + 嵌套边界压力卡 + 全量公式目检 A/B（22 条）+ 混排行内基准卡，供真机逐卡验收。</p>
 */
public final class LatexPage implements PlaygroundPage {

    /** 场地可视高度上限（px）：内容超限即滚动。 */
    private static final int VIEWPORT_MAX_HEIGHT = 620;

    /** 混排行内文本自动换行宽（px），避免长公式行横向溢出卡片。 */
    private static final int WRAP_WIDTH = 600;

    /** 卡片9：嵌套与边界（压力公式，盒抽象递归换元目检）。 */
    private static final String[] STRESS = {
            "\\frac{\\frac{a}{b}}{\\frac{c}{d}}",
            "\\sqrt{\\sqrt{x} + 1}",
            "x^{y^z} + a_{b_c} + x_{i}^{2}",
            "\\int_0^1 \\int_0^1 x^2\\,dx\\,dy",
            "\\begin{pmatrix} a & b & c \\\\ d & e & f \\\\ g & h & i \\end{pmatrix}",
            "\\overline{x + \\overline{y}} + \\underline{a + b}",
            "\\left\\{ \\frac{a}{b} \\right\\} \\left| \\frac{c}{d} \\right|",
            "\\sum_{i=1}^{n} \\frac{1}{i^2}",
    };

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
        return "行内 LaTeX 数学子集：分数/根号/上下标/求和积分/矩阵/分段函数/伸缩括号/重音/公式内中文（滚动场地，12 张测试卡）";
    }

    @Override
    public Supplier<SceneNode> build(final SceneRuntime rt) {
        return () -> {
            SceneNode shell = SceneNode.row(SceneChromeTokens.GAP_SM);
            shell.setFillParentWidth(true);

            SceneNode viewport = SceneNode.column();
            viewport.setFillParentWidth(true);
            viewport.setMaxHeight(VIEWPORT_MAX_HEIGHT);
            viewport.setScrollable(true);
            viewport.setClipChildren(true);
            viewport.setGap(10);
            viewport.setPadding(SceneChromeTokens.PAD_LG);
            shell.appendChild(viewport);

            // ===== 卡片1：公式速览（32px 大字号目检） =====
            viewport.appendChild(formulaCard(rt, "公式速览（32px）", LatexShowcaseFormulas.HERO, 32,
                    "分数线中心钉数学轴；\\sum/\\int 符号按数学轴居中、text 口径上下标侧挂；"
                            + "横线厚度与位置量化整像素行。", true));
            // ===== 卡片2：分数与根号 =====
            viewport.appendChild(formulaCard(rt, "分数与根号", new String[] {
                    "分数：<latex>\\frac{1}{2} + \\frac{a}{b}</latex>",
                    "平方根：<latex>\\sqrt{x} + \\sqrt{x^2+y^2}</latex>",
                    "开方：<latex>\\sqrt[3]{x}</latex>",
            }, 14, "根号横线中心 = 内容顶 + clr + θ/2（clr 钉 1.25θ）；根指数在根号内侧左上（0.7×）。", false));
            // ===== 卡片3：上下标 =====
            viewport.appendChild(formulaCard(rt, "上下标与函数名", new String[] {
                    "上标/下标：<latex>x^2 + y_i + x_i^2</latex>",
                    "欧拉公式：<latex>e^{i\\pi} + 1 = 0</latex>",
                    "极限：<latex>\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1</latex>",
            }, 14, "单字符基底上标抬升 = sup2（0.363em）；\\lim 为正体 limits 算子（恒上下堆叠）。", false));
            // ===== 卡片4：希腊字母与符号 =====
            viewport.appendChild(formulaCard(rt, "希腊字母与运算符", new String[] {
                    "希腊字母：<latex>\\alpha + \\beta = \\gamma, \\Delta \\pi \\sigma</latex>",
                    "变体对：<latex>\\epsilon \\varepsilon \\phi \\varphi \\theta \\vartheta</latex>",
                    "关系符：<latex>a \\leq b \\geq c, x \\neq y, \\infty \\pm \\times</latex>",
            }, 14, "符号命令映射 Unicode 码点后走现有字体 fallback 链；缺字形显示 U+FFFD 替换符。", false));
            // ===== 卡片5：矩阵与分段函数 =====
            viewport.appendChild(formulaCard(rt, "矩阵与分段函数", new String[] {
                    "矩阵：<latex>\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}</latex>",
                    "分段：<latex>f(x) = \\begin{cases} x & x > 0 \\\\ -x & x \\leq 0 \\end{cases}</latex>",
            }, 14, "& 分列、\\\\ 换行；列间 1em、行间 1ex、外沿 0.4ex，整体按数学轴居中。", false));
            // ===== 卡片6：伸缩括号与组合数 =====
            viewport.appendChild(formulaCard(rt, "伸缩括号与组合数", new String[] {
                    "伸缩括号：<latex>\\left( \\frac{a}{b} \\right) \\left[ x \\right]</latex>",
                    "组合数：<latex>\\binom{n}{k} = \\frac{n!}{k!(n-k)!}</latex>",
            }, 14, "\\left/\\right 定界符按 FencedAtom minHeight 整字缩放、ink 中心对齐数学轴。", false));
            // ===== 卡片7：重音 =====
            viewport.appendChild(formulaCard(rt, "重音与上下划线", new String[] {
                    "重音：<latex>\\hat{x} + \\bar{y} + \\vec{v} + \\dot{z} + \\tilde{w}</latex>",
                    "上划线：<latex>\\overline{AB} + \\underline{x}</latex>",
            }, 14, "重音按 script 字号（0.7×）、基线 = min(h, xHeight) − h；上下划线间隙 3θ、盒余量 5θ。", false));
            // ===== 卡片8：公式内中文与样式继承 =====
            viewport.appendChild(formulaCard(rt, "公式内文本与样式继承", new String[] {
                    "公式内中文：<latex>\\text{速度} = \\frac{\\Delta s}{\\Delta t}</latex>",
                    "颜色继承：<color=#FF5555><latex>x^2</latex></color> 与 <color=#55FF55><latex>\\sqrt{y}</latex></color>",
                    "字号继承：<size=20><latex>\\frac{1}{2}</latex></size>",
            }, 14, "公式继承外层 color/size；\\text{} 内容（含中文）走现有字体 fallback 链。", false));
            // ===== 卡片9：嵌套与边界（压力公式，24px） =====
            viewport.appendChild(formulaCard(rt, "嵌套与边界（压力公式 24px）", STRESS, 24,
                    "嵌套分数/根号/深脚本/3×3 矩阵/嵌套横线/双积分/花括号竖线——盒抽象（w/h/d + ink 越量）递归换元。", true));
            // ===== 卡片10/11：全量公式目检 A/B（20px） =====
            String[] all = LatexShowcaseFormulas.all();
            String[] allA = new String[all.length / 2];
            String[] allB = new String[all.length - all.length / 2];
            System.arraycopy(all, 0, allA, 0, allA.length);
            System.arraycopy(all, allA.length, allB, 0, allB.length);
            viewport.appendChild(formulaCard(rt, "全量公式目检 A（20px，1-11 条）", allA, 20,
                    "LatexShowcaseFormulas 全量清单前半；与 headless 验收场地（build/reports/latex-compare）同集。", true));
            viewport.appendChild(formulaCard(rt, "全量公式目检 B（20px，12-22 条）", allB, 20,
                    "LatexShowcaseFormulas 全量清单后半；与 headless 验收场地同集。", true));
            // ===== 卡片12：混排行内基准（14px） =====
            viewport.appendChild(formulaCard(rt, "混排行内基准（文本与公式基线）", new String[] {
                    "段落中间插入公式 <latex>\\frac{1}{2}</latex> 与 <latex>\\sqrt{x}</latex> 继续行文",
                    "中文混排 <latex>x^2 + y_i</latex> 之后接 <latex>\\sum_{i=1}^{n} i</latex> 收尾",
                    "多段：<latex>\\binom{n}{k}</latex> 与 <latex>\\left( \\frac{a}{b} \\right)</latex> 同基线",
            }, 14, "行内公式与文本共享同一条基线（渲染侧整行统一 baselineScale）；公式高出行盒时行高随内容撑开。", false));

            // 滚动受控源 + 可视滚动条（与 TestPlaygroundHost/FormPageShell 同口径）
            Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);
            SceneScrollbar.Result scrollbar = SceneScrollbar.createDefault(rt, viewport, scrollSignal);
            shell.appendChild(scrollbar.column());
            return shell;
        };
    }

    /**
     * 公式卡：标题 + 逐行公式（big=true 纯公式大字目检；false 为混排文本行，超宽自动换行）。
     *
     * @param lines 行文本：big=true 时为纯 LaTeX 源码，否则为可含 {@code <latex>} 的富文本
     */
    private static SceneNode formulaCard(SceneRuntime rt, String titleText, String[] lines, int size,
            String hintText, boolean big) {
        SceneNode node = PlaygroundKit.card();
        node.appendChild(PlaygroundKit.title(titleText));
        for (String line : lines) {
            String text = big ? "<latex>" + line + "</latex>" : line;
            int wrap = big ? 0 : WRAP_WIDTH;
            SceneNode label = SceneLabel.create(rt, new SceneLabel.Props(
                    Signal.create(text), PlaygroundKit.TEXT, size, TextStyle.TEXT_MODE_RICH_TAGS,
                    TextHorizontalAlign.LEFT, TextVerticalAlign.TOP, wrap, 0.0D, 0, 0, false, null)).get();
            node.appendChild(label);
        }
        node.appendChild(PlaygroundKit.hint(hintText));
        return node;
    }
}
