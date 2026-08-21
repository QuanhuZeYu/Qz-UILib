package club.heiqi.uilib.font.latex.layout;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.font.latex.LatexNode;
import club.heiqi.uilib.font.latex.node.LatexAccent;
import club.heiqi.uilib.font.latex.node.LatexAtom;
import club.heiqi.uilib.font.latex.node.LatexAtom.AtomClass;
import club.heiqi.uilib.font.latex.node.LatexBinom;
import club.heiqi.uilib.font.latex.node.LatexFrac;
import club.heiqi.uilib.font.latex.node.LatexGroup;
import club.heiqi.uilib.font.latex.node.LatexLeftRight;
import club.heiqi.uilib.font.latex.node.LatexMatrix;
import club.heiqi.uilib.font.latex.node.LatexSpace;
import club.heiqi.uilib.font.latex.node.LatexSqrt;
import club.heiqi.uilib.font.latex.node.LatexSupSub;

/**
 * 数学布局引擎：AST → {@link MathBox}（TeX box 模型简化，规划 §6.3）。
 *
 * <p>纯 JVM 可测：字体度量经 {@link MathMetrics} 注入；布局参数取 {@link MathConstants}
 * 比例常量（决议 §7.1：不读字体 MATH 表）。</p>
 *
 * <p>坐标：盒内元素 x/y 相对盒基线，y 向下为正。字号缩放：正文 1.0、script 0.7、
 * scriptscript 0.5；嵌套时缩放沿 addBox 复合（sizeScale 相对公式正文字号）。</p>
 */
public final class MathLayoutService {

    /**
     * 布局算法版本：任何改变布局几何（坐标/间距/字号/横线位置）的算法迭代都必须 +1，
     * 与 {@link LatexCache} 键联动使旧缓存盒失效（字体 runtimeVersion 只管字形重载，
     * 不管布局算法）。
     */
    public static final int LAYOUT_VERSION = 13;

    /** 根号字符（U+221A）。 */
    private static final String RADICAL = "\u221A";

    /**
     * 布局顶层公式节点列表。
     *
     * @param nodes      公式 AST（{@link club.heiqi.uilib.font.latex.LatexParser#parse} 结果）
     * @param baseSizePx 正文字号（px）
     * @param metrics    度量注入（不可为 null）
     * @return 布局盒
     */
    public MathBox layout(List<LatexNode> nodes, float baseSizePx, MathMetrics metrics) {
        if (nodes == null || nodes.isEmpty()) {
            return MathBox.empty();
        }
        return layoutList(nodes, baseSizePx, metrics, false);
    }

    // ==================== 节点分派 ====================

    private MathBox layoutNode(LatexNode node, float size, MathMetrics m) {
        return layoutNode(node, size, m, false);
    }

    /**
     * 布局节点（TeX cramped style：根式/分数分子分母/重音基底内，上标抬升用 cramped 参数 sup3，
     * 其余不变）。
     */
    private MathBox layoutNode(LatexNode node, float size, MathMetrics m, boolean cramped) {
        switch (node.getKind()) {
            case ATOM: {
                LatexAtom atom = (LatexAtom) node;
                if (atom.getOperatorMode() == LatexAtom.OperatorMode.BIG_OPERATOR) {
                    // 裸大运算符（无脚本节点）：TeX ScriptsAtom big-op 分支仍轴居中 +
                    // 尾部 MEDMUSKIP；\nolimits 裸符号取自然尺寸轴居中（不缩放到 limits 目标高）
                    MathBox base = layoutAtom(atom, size, m);
                    if (atom.getLimitsFlag() == LatexAtom.LIMITS_NOLIMITS) {
                        return appendBigOperatorTail(axisCenteredBigOperator(base, size, m), size, m,
                                atom.getText());
                    }
                    return layoutLimits(base, null, null, size, m);
                }
                return layoutAtom(atom, size, m);
            }
            case GROUP:
                return layoutList(((LatexGroup) node).getChildren(), size, m, cramped);
            case SPACE:
                return spaceBox((float) ((LatexSpace) node).getEmWidth() * size);
            case SUP_SUB:
                return layoutSupSub((LatexSupSub) node, size, m, cramped);
            case FRAC:
                return layoutFrac((LatexFrac) node, size, m);
            case SQRT:
                return layoutSqrt((LatexSqrt) node, size, m);
            case LEFT_RIGHT:
                return layoutLeftRight((LatexLeftRight) node, size, m);
            case MATRIX:
                return layoutMatrix((LatexMatrix) node, size, m);
            case BINOM:
                return layoutBinom((LatexBinom) node, size, m);
            case ACCENT:
                return layoutAccent((LatexAccent) node, size, m);
            default:
                return MathBox.empty();
        }
    }

    // ==================== 原子/列表/间距 ====================

    private MathBox layoutAtom(LatexAtom atom, float size, MathMetrics m) {
        String text = atom.getText();
        float width = m.advance(text, size);
        List<GlyphElem> glyphs = new ArrayList<GlyphElem>(1);
        // TeX mathnormal：ORD 类 ASCII 字母为数学变量（斜体）；数字/符号/函数名（OP）/
        // \text 内容（TEXT）保持直体。
        boolean italic = atom.getAtomClass() == LatexAtom.AtomClass.ORD && isMathVariable(text);
        // 定界符 ink 中心钉数学轴（与 layoutFence 同口径）：普通小括号/竖线不再按基线裸放——
        // 字库括号 ink 在字格内的分布不对称（真机括号 ink 中心可高于轴 0.1em+），基线对齐会
        // 整体偏上；锚定后括号视觉居中于公式行，与伸缩 fence（矩阵等）行为一致。
        // 盒度量按 ink 边界（TeX 字符级 fontdimen 语义的近似）：统一字体盒 ascent/descent 会
        // 把每个字符盒虚高到 0.8em（真实 ink 顶如 a/x 仅 ~0.53em），分数分子间隙、根号横线、
        // 行高随之虚胖，嵌套公式累积放大——用户反馈「包围盒问题、嵌套偏差更明显」的根因。
        // 无 ink 数据回退与旧行为代数等价（inkHeight 回退盒总高、inkCenter 回退盒中心时
        // inkHalf − inkCenter ≡ ascent、inkCenter + inkHalf ≡ descent）。
        float glyphY = 0.0F;
        float height;
        float depth;
        if (isDelimiterAtom(atom)) {
            float axis = MathConstants.AXIS_HEIGHT_EM * size;
            float inkCenter = m.inkCenterOffsetY(text, size);
            float inkHalf = m.inkHeight(text, size) / 2.0F;
            glyphY = -axis - inkCenter;
            height = Math.max(0.0F, inkHalf - inkCenter - glyphY);
            depth = Math.max(0.0F, glyphY + inkCenter + inkHalf);
        } else {
            float inkTop = 0.0F;
            float inkBottom = 0.0F;
            for (int index = 0; index < text.length(); ) {
                int codepoint = text.codePointAt(index);
                index += Character.charCount(codepoint);
                String glyphText = new String(Character.toChars(codepoint));
                float inkCenter = m.inkCenterOffsetY(glyphText, size);
                float inkHalf = m.inkHeight(glyphText, size) / 2.0F;
                inkTop = Math.max(inkTop, inkHalf - inkCenter);
                inkBottom = Math.max(inkBottom, inkCenter + inkHalf);
            }
            height = Math.max(0.0F, inkTop);
            depth = Math.max(0.0F, inkBottom);
        }
        glyphs.add(new GlyphElem(text, 0.0F, glyphY, 1.0F, italic));
        // 斜体视觉右越量（ink 超出 advance 的量）：随盒向上嵌套传播（TeX box 的 ink 边界抽象）
        float rightOverhang = italic ? m.italicOverhang(text, size) : 0.0F;
        return new MathBox(width, height, depth, glyphs, null, 0.0F, rightOverhang);
    }

    /** 是否为定界符原子（OPEN/CLOSE 类，或 ORD 类定界符字符 |、‖）。 */
    private static boolean isDelimiterAtom(LatexAtom atom) {
        LatexAtom.AtomClass atomClass = atom.getAtomClass();
        if (atomClass == LatexAtom.AtomClass.OPEN || atomClass == LatexAtom.AtomClass.CLOSE) {
            return true;
        }
        String text = atom.getText();
        if (text == null || text.codePointCount(0, text.length()) != 1) {
            return false;
        }
        int codepoint = text.codePointAt(0);
        return codepoint == 0x007C || codepoint == 0x2016; // | ‖（TeX 定界符族，ORD 类）
    }

    /** 是否数学变量文本（全 ASCII 字母：TeX mathnormal 的 capitals/small 映射）。 */
    private static boolean isMathVariable(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int index = 0; index < text.length(); ) {
            int codepoint = text.codePointAt(index);
            index += Character.charCount(codepoint);
            if (codepoint < 'A' || codepoint > 'z' || (codepoint > 'Z' && codepoint < 'a')) {
                return false;
            }
        }
        return true;
    }

    private static MathBox spaceBox(float width) {
        return new MathBox(width, 0.0F, 0.0F, null, null);
    }

    /** 水平拼接（含数学原子间距）。 */
    private MathBox layoutList(List<LatexNode> nodes, float size, MathMetrics m, boolean cramped) {
        if (nodes.isEmpty()) {
            return MathBox.empty();
        }
        Builder builder = new Builder();
        AtomClass[] effective = effectiveAtomClasses(nodes);
        for (int index = 0; index < nodes.size(); index++) {
            LatexNode node = nodes.get(index);
            // 显式间距（\, \quad 等）是 kern：不参与 glue（TeX RowAtom「kerns do not interfere
            // with the normal glue-rules」），前后均不插数学间距。
            boolean leftIsKern = index > 0 && nodes.get(index - 1).getKind() == LatexNode.Kind.SPACE;
            boolean rightIsKern = node.getKind() == LatexNode.Kind.SPACE;
            if (index > 0 && !leftIsKern && !rightIsKern) {
                float gap = spacingMu(effective[index - 1], effective[index]) / 18.0F * size;
                builder.advance(gap);
            }
            MathBox child = layoutNode(node, size, m, cramped);
            builder.addBox(child, builder.width, 0.0F, 1.0F);
        }
        return builder.toBox();
    }

    /**
     * 有效原子类型序列（含 TeX bin→ord 上下文降级，TeXBook p170）：
     * BIN 在行首/行尾、前一原子为 bin/op/rel/open/punct、或后一原子为 rel/close/punct 时降级为 ORD
     * （两侧不再加 medium 间距）。
     */
    private static AtomClass[] effectiveAtomClasses(List<LatexNode> nodes) {
        AtomClass[] classes = new AtomClass[nodes.size()];
        for (int index = 0; index < nodes.size(); index++) {
            classes[index] = atomClassOf(nodes.get(index));
        }
        for (int index = 0; index < nodes.size(); index++) {
            if (classes[index] != AtomClass.BIN) {
                continue;
            }
            // 显式间距节点（kern）对上下文透明：取最近的非 SPACE 邻居
            int previousIndex = index - 1;
            while (previousIndex >= 0 && nodes.get(previousIndex).getKind() == LatexNode.Kind.SPACE) {
                previousIndex--;
            }
            int nextIndex = index + 1;
            while (nextIndex < nodes.size() && nodes.get(nextIndex).getKind() == LatexNode.Kind.SPACE) {
                nextIndex++;
            }
            AtomClass previous = previousIndex >= 0 ? classes[previousIndex] : null;
            AtomClass next = nextIndex < nodes.size() ? classes[nextIndex] : null;
            boolean degraded = previousIndex < 0 || nextIndex >= nodes.size()
                    || previous == AtomClass.BIN || previous == AtomClass.OP
                    || previous == AtomClass.REL || previous == AtomClass.OPEN
                    || previous == AtomClass.PUNCT
                    || next == AtomClass.REL || next == AtomClass.CLOSE || next == AtomClass.PUNCT;
            if (degraded) {
                classes[index] = AtomClass.ORD;
            }
        }
        return classes;
    }

    /**
     * 相邻原子间距（mu）：TeX 原子间距全表（TeXBook p181，GlueSettings.xml 取证）——
     * thin=3mu、med=4mu、thick=5mu；display/text 口径（行内公式统一按 text）。
     */
    private static float spacingMu(AtomClass left, AtomClass right) {
        switch (left) {
            case ORD:
                return right == AtomClass.OP || right == AtomClass.INNER ? MathConstants.THIN_MU
                        : right == AtomClass.BIN ? MathConstants.MED_MU
                        : right == AtomClass.REL ? MathConstants.THICK_MU : 0.0F;
            case OP:
                return right == AtomClass.ORD || right == AtomClass.OP || right == AtomClass.INNER
                        ? MathConstants.THIN_MU
                        : right == AtomClass.REL ? MathConstants.THICK_MU : 0.0F;
            case BIN:
                return right == AtomClass.ORD || right == AtomClass.OP || right == AtomClass.OPEN
                        || right == AtomClass.INNER ? MathConstants.MED_MU : 0.0F;
            case REL:
                return right == AtomClass.ORD || right == AtomClass.OP || right == AtomClass.OPEN
                        || right == AtomClass.INNER ? MathConstants.THICK_MU : 0.0F;
            case OPEN:
                return 0.0F;
            case CLOSE:
                return right == AtomClass.OP || right == AtomClass.INNER ? MathConstants.THIN_MU
                        : right == AtomClass.BIN ? MathConstants.MED_MU
                        : right == AtomClass.REL ? MathConstants.THICK_MU : 0.0F;
            case PUNCT:
                return right == AtomClass.ORD || right == AtomClass.OPEN || right == AtomClass.CLOSE
                        ? MathConstants.THIN_MU
                        : right == AtomClass.OP ? MathConstants.MED_MU
                        : right == AtomClass.REL || right == AtomClass.INNER ? MathConstants.THICK_MU
                        : right == AtomClass.PUNCT ? MathConstants.MED_MU : 0.0F;
            case INNER:
                return right == AtomClass.ORD || right == AtomClass.OP || right == AtomClass.OPEN
                        || right == AtomClass.PUNCT || right == AtomClass.INNER ? MathConstants.THIN_MU
                        : right == AtomClass.BIN ? MathConstants.MED_MU
                        : right == AtomClass.REL ? MathConstants.THICK_MU : 0.0F;
            default:
                return 0.0F;
        }
    }

    private static AtomClass atomClassOf(LatexNode node) {
        if (node.getKind() == LatexNode.Kind.ATOM) {
            return ((LatexAtom) node).getAtomClass();
        }
        return AtomClass.INNER; // 复合结构按 Inner（TeX 惯例）
    }

    // ==================== 上下标 ====================

    private MathBox layoutSupSub(LatexSupSub node, float size, MathMetrics m, boolean cramped) {
        LatexNode baseNode = node.getBase();
        MathBox base = baseNode.getKind() == LatexNode.Kind.ATOM ? layoutAtom((LatexAtom) baseNode, size, m)
                : layoutNode(baseNode, size, m);
        float scriptSize = size * MathConstants.SCRIPT_SCALE;
        // 上标内容沿用父级 cramped 位（JLaTeXMath supStyle 保留 cramped 位：非 cramped 上下文
        // 的上标为 script、cramped 上下文（\frac 分子/根式内）的上标为 script-cramped，
        // 内部嵌套上标的 sup2/sup3 判据随之确定，不额外强制 cramped）。
        MathBox sup = node.getSup() == null ? null : layoutNode(node.getSup(), scriptSize, m, cramped);
        MathBox sub = node.getSub() == null ? null : layoutNode(node.getSub(), scriptSize, m);

        // 大运算符（\sum \int \prod …）：TeX ScriptsAtom big-op 分支——符号按 ink 锚定数学轴，
        // 即使无上下标也轴居中并补 MEDMUSKIP 尾部；默认/\limits 上下堆叠（BigOperatorAtom
        // limits 路径），\nolimits 显式降级为侧挂脚本（符号仍轴居中）。
        // 注：JLaTeXMath 默认 SCRIPT_NORMAL（text 样式侧挂），本项目行内统一 limits 堆叠
        // 为产品决策（卡片观感口径），可经 \nolimits 取侧挂。
        boolean bigOperator = baseNode.getKind() == LatexNode.Kind.ATOM
                && ((LatexAtom) baseNode).getOperatorMode() == LatexAtom.OperatorMode.BIG_OPERATOR;
        boolean nolimitsBigOp = bigOperator
                && ((LatexAtom) baseNode).getLimitsFlag() == LatexAtom.LIMITS_NOLIMITS;
        String baseText = bigOperator ? ((LatexAtom) baseNode).getText() : null;
        if (bigOperator && !nolimitsBigOp) {
            return layoutLimits(base, sup, sub, size, m);
        }
        if (bigOperator && nolimitsBigOp) {
            // 符号轴居中（自然尺寸，不缩放到 limits 目标视觉高），脚本侧挂
            base = axisCenteredBigOperator(base, size, m);
        }
        if (sup == null && sub == null) {
            if (bigOperator) {
                // 裸大运算符（\nolimits）：轴居中 + 尾部 MEDMUSKIP + 符号自然 italic
                return appendBigOperatorTail(base, size, m, baseText);
            }
            return base;
        }

        float drt = MathConstants.RULE_THICKNESS_EM * size;
        float xHeight = m.xHeight(size);
        // TeX 口径：supdrop/subdrop 按 script 字号缩放（SCRIPT_SUP_DROP_EM 已预乘 SCRIPT_SCALE）
        float supDrop = MathConstants.SCRIPT_SUP_DROP_EM * size;
        float subDrop = MathConstants.SCRIPT_SUB_DROP_EM * size;

        // 脚本放置参照盒（nolimits 大运算符已轴居中：按其盒度量；其余按 TeX ScriptsAtom 口径）
        float refHeight = base.getHeight();
        float refDepth = base.getDepth();
        if (!nolimitsBigOp && baseNode.getKind() == LatexNode.Kind.ACCENT) {
            // TeX ScriptsAtom 特殊分支：重音基底按裸基底（cramped）度量
            MathBox bare = layoutNode(((LatexAccent) baseNode).getBase(), size, m, true);
            refHeight = bare.getHeight();
            refDepth = bare.getDepth();
        } else if (!nolimitsBigOp && isSingleCharAtom(baseNode)) {
            // 单字符基底（CharSymbol 路径）：shift 起点 0，由 sup2/sup3、sub1/sub2 下限决定
            refHeight = 0.0F;
            refDepth = 0.0F;
        }
        float shiftUp0 = refHeight - supDrop;
        float shiftDown0 = refDepth + subDrop;

        // TeX ScriptsAtom 约束链（行内=text 样式口径）：
        // 上标下限 max(sup2/sup3 按 cramped, sup.depth + xHeight/4)；
        // 下标下限 max(sub1, sub.height − 4·xHeight/5)；
        // 双脚本再经 sub2 与 4×规则线粗最小间距、4/5 xHeight 二次分配（psi）。
        float supMinimum = (cramped ? MathConstants.SUP3_EM : MathConstants.SUP2_EM) * size;
        float shiftUp = 0.0F;
        float shiftDown = 0.0F;
        float supY = 0.0F;
        float subY = 0.0F;
        if (sup != null && sub == null) {
            shiftUp = Math.max(Math.max(shiftUp0, supMinimum),
                    sup.getDepth() + xHeight / 4.0F);
            supY = -shiftUp;
        } else if (sub != null && sup == null) {
            shiftDown = Math.max(Math.max(shiftDown0, MathConstants.SUB1_EM * size),
                    sub.getHeight() - 4.0F * xHeight / 5.0F);
            subY = shiftDown;
        } else {
            shiftUp = Math.max(Math.max(shiftUp0, supMinimum),
                    sup.getDepth() + xHeight / 4.0F);
            shiftDown = Math.max(shiftDown0, MathConstants.SUB2_EM * size);
            float interSpace = shiftUp - sup.getDepth() + shiftDown - sub.getHeight();
            if (interSpace < 4.0F * drt) {
                shiftUp += 4.0F * drt - interSpace;
                float psi = 4.0F * xHeight / 5.0F - (shiftUp - sup.getDepth());
                if (psi > 0.0F) {
                    shiftUp += psi;
                    shiftDown -= psi;
                }
            }
            supY = -shiftUp;
            subY = shiftDown;
        }

        float supWidth = sup == null ? 0.0F : sup.getWidth();
        float subWidth = sub == null ? 0.0F : sub.getWidth();
        float scriptWidth = Math.max(supWidth, subWidth);
        // 斜体校正（TeX Char.italic）：
        // - 单字符数学变量基底（渲染侧会做 tan≈0.25 几何斜切）：上标右移 = 字形自然 ink
        //   右越量（italicCorrection）+ 渲染斜切避让（斜切角 × xHeight/2 折半，
        //   与 GlyphBatchRenderer 斜切角同源、与 accent skew 同一几何近似）；
        // - nolimits 大运算符（渲染不斜切）：只取自然 ink 右越量（cmex ∫ 0.194em、∑ 0）。
        float supShift = 0.0F;
        if (sup != null && baseNode.getKind() == LatexNode.Kind.ATOM) {
            String atomText = ((LatexAtom) baseNode).getText();
            if (nolimitsBigOp) {
                supShift = m.italicCorrection(atomText, size);
            } else if (isSingleCharAtom(baseNode) && isMathVariable(atomText)) {
                supShift = m.italicCorrection(atomText, size)
                        + MathConstants.ACCENT_SKEW_FACTOR * xHeight;
            }
        }

        // 脚本水平位置（TeX ScriptsAtom）：上/下标左缘紧贴基底右缘（+斜体校正），
        // scriptspace 加在脚本盒内部而非与基底之间，故此处不额外加
        Builder builder = new Builder();
        builder.addBox(base, 0.0F, 0.0F, 1.0F);
        if (sup != null) {
            builder.addBox(sup, base.getWidth() + supShift, supY, MathConstants.SCRIPT_SCALE);
        }
        if (sub != null) {
            builder.addBox(sub, base.getWidth(), subY, MathConstants.SCRIPT_SCALE);
        }
        builder.width = base.getWidth() + (scriptWidth > 0.0F ? scriptWidth : 0.0F) + supShift;
        builder.height = Math.max(builder.height, refHeight);
        builder.depth = Math.max(builder.depth, refDepth);
        if (sup != null) {
            builder.height = Math.max(builder.height, -supY + sup.getHeight());
        }
        if (sub != null) {
            builder.depth = Math.max(builder.depth, subY + sub.getDepth());
        }
        if (nolimitsBigOp) {
            // 侧挂尾部 = MEDMUSKIP + 符号自然 italic（TeX deltaSymbol + 无下标时 StrutBox(delta)）
            builder.width += MathConstants.BIG_OPERATOR_TAIL_SPACE_EM * size
                    + (sub == null ? m.italicCorrection(baseText, size) : 0.0F);
        }
        return builder.toBox();
    }

    /** 单字符 ATOM 基底判断（TeX CharSymbol 路径判据）。 */
    private static boolean isSingleCharAtom(LatexNode node) {
        if (node.getKind() != LatexNode.Kind.ATOM) {
            return false;
        }
        String text = ((LatexAtom) node).getText();
        return text.codePointCount(0, text.length()) == 1;
    }

    /**
     * 大运算符 limits 上下堆叠：TeX BigOperatorAtom limits 路径——
     * 符号视觉中心按 ink 锚定数学轴（ScriptsAtom big-op 分支 setShift 语义），
     * 上隙 max(bigop1, bigop3 − sup.depth)、下隙 max(bigop2, bigop4 − sub.height)，
     * 外沿 bigop5 留白、上下限水平居中、盒尾补 MEDMUSKIP（deltaSymbol）。
     */
    private MathBox layoutLimits(MathBox base, MathBox sup, MathBox sub, float size, MathMetrics m) {
        Builder builder = new Builder();
        float axis = MathConstants.AXIS_HEIGHT_EM * size;
        // ink 锚定（y 向下口径，与 layoutFence/layoutAtom 同源）：中心偏移 inkCenterY，总高 inkH；
        // 盒度量回退保持旧行为。
        String baseText = base.getGlyphs().isEmpty() ? null : base.getGlyphs().get(0).getText();
        float inkCenterY = baseText == null ? (base.getDepth() - base.getHeight()) / 2.0F
                : m.inkCenterOffsetY(baseText, size);
        float inkH = baseText == null ? base.getTotalHeight() : m.inkHeight(baseText, size);
        // 无阶梯变体（TeX 会选 cmex 行内小变体，本仓资源受限）：整字缩放到目标视觉高
        //（TeX 口径：∑∏ ≈ 1.0em、∫ 族 ≈ 1.11em），上下限随之贴合缩放后符号。
        float targetH = bigOperatorTargetHeightEm(baseText) * size;
        float opScale = inkH > 0.0F ? targetH / inkH : 1.0F;
        // 轴居中：渲染 ink 中心 = baseShift + inkCenterY×scale，须落在轴（y 向下 −axis）
        // → baseShift = −axis − inkCenterY×scale（与 layoutFence 同式；符号必须与
        // inkCenterOffsetY 的 y 向下口径一致，否则大运算符整体偏移 2×inkCenterY）
        float baseShift = -axis - inkCenterY * opScale;
        // 轴居中后相对新基线的有效度量：顶 = targetH/2 + axis、底 = targetH/2 − axis
        float refHeight = targetH / 2.0F + axis;
        float refDepth = targetH / 2.0F - axis;
        float baseWidth = base.getWidth() * opScale;
        float contentWidth = baseWidth;
        if (sup != null) {
            contentWidth = Math.max(contentWidth, sup.getWidth());
        }
        if (sub != null) {
            contentWidth = Math.max(contentWidth, sub.getWidth());
        }
        // 符号自然 italic（TeX BigOperatorAtom delta = Char.italic；cmex ∫ 0.194em、∑/∏ 0）：
        // 双脚本时上/下限相对符号 ±delta/2，单脚本/无脚本时补进尾部（StrutBox(delta)）
        float opItalic = baseText == null ? 0.0F : m.italicCorrection(baseText, size);
        builder.addBox(base, (contentWidth - baseWidth) / 2.0F, baseShift, opScale);
        float height = refHeight;
        float depth = refDepth;
        if (sup != null) {
            float overKern = Math.max(MathConstants.BIGOP1_EM * size,
                    MathConstants.BIGOP3_EM * size - sup.getDepth());
            float supY = -(refHeight + overKern + sup.getDepth());
            float supX = (contentWidth - sup.getWidth()) / 2.0F + (sub != null ? opItalic / 2.0F : 0.0F);
            builder.addBox(sup, supX, supY, MathConstants.SCRIPT_SCALE);
            height = MathConstants.BIGOP5_EM * size + sup.getTotalHeight() + overKern + refHeight;
        }
        if (sub != null) {
            float underKern = Math.max(MathConstants.BIGOP2_EM * size,
                    MathConstants.BIGOP4_EM * size - sub.getHeight());
            float subY = refDepth + underKern + sub.getHeight();
            float subX = (contentWidth - sub.getWidth()) / 2.0F - (sup != null ? opItalic / 2.0F : 0.0F);
            builder.addBox(sub, subX, subY, MathConstants.SCRIPT_SCALE);
            depth = sub.getTotalHeight() + underKern + refDepth + MathConstants.BIGOP5_EM * size;
        }
        builder.height = height;
        builder.depth = depth;
        builder.width = contentWidth + MathConstants.BIG_OPERATOR_TAIL_SPACE_EM * size
                + (sup != null && sub != null ? 0.0F : opItalic);
        return builder.toBox();
    }

    /**
     * 大运算符符号轴居中盒（自然尺寸，不缩放到 limits 目标视觉高）：\nolimits 侧挂路径。
     * glyph 直接入盒 y = baseShift，盒度量按 ink 边界（与 layoutFence 同口径）。
     */
    private MathBox axisCenteredBigOperator(MathBox base, float size, MathMetrics m) {
        String text = base.getGlyphs().isEmpty() ? null : base.getGlyphs().get(0).getText();
        if (text == null) {
            return base;
        }
        float axis = MathConstants.AXIS_HEIGHT_EM * size;
        float inkCenter = m.inkCenterOffsetY(text, size);
        float inkHalf = m.inkHeight(text, size) / 2.0F;
        Builder builder = new Builder();
        builder.addGlyph(text, 0.0F, -axis - inkCenter, 1.0F);
        builder.width = base.getWidth();
        builder.height = Math.max(0.0F, inkHalf - inkCenter);
        builder.depth = Math.max(0.0F, inkCenter + inkHalf);
        return builder.toBox();
    }

    /** 裸大运算符（\nolimits 无脚本）：轴居中盒 + 尾部 MEDMUSKIP + 符号自然 italic。 */
    private MathBox appendBigOperatorTail(MathBox base, float size, MathMetrics m, String baseText) {
        Builder builder = new Builder();
        builder.addBox(base, 0.0F, 0.0F, 1.0F);
        builder.width = base.getWidth() + MathConstants.BIG_OPERATOR_TAIL_SPACE_EM * size
                + (baseText == null ? 0.0F : m.italicCorrection(baseText, size));
        return builder.toBox();
    }

    /** 积分族（TeX cmex 行内变体高 ≈1.11em）与求和族（≈1.0em）的目标视觉高。 */
    private static float bigOperatorTargetHeightEm(String symbol) {
        if (symbol != null && symbol.codePointCount(0, symbol.length()) == 1) {
            switch (symbol.codePointAt(0)) {
                case 0x222B: // ∫
                case 0x222C: // ∬
                case 0x222D: // ∭
                case 0x222E: // ∮
                case 0x222F: // ∯
                case 0x2230: // ∰
                    return 1.11F;
                default:
                    return 1.0F;
            }
        }
        return 1.0F;
    }

    // ==================== 分数 ====================

    private MathBox layoutFrac(LatexFrac node, float size, MathMetrics m) {
        float scriptSize = size * MathConstants.SCRIPT_SCALE;
        // 分子分母用 cramped scriptstyle（TeX numStyle/denomStyle）
        MathBox num = layoutNode(node.getNumerator(), scriptSize, m, true);
        MathBox den = layoutNode(node.getDenominator(), scriptSize, m, true);
        // TeX FractionAtom（行内=text 样式口径）：轴高对齐 + num2/denom2 + clr 补足链
        float drt = MathConstants.RULE_THICKNESS_EM * size;
        float axis = MathConstants.AXIS_HEIGHT_EM * size;
        float delta = drt / 2.0F;
        float contentWidth = Math.max(num.getWidth(), den.getWidth());
        float sideSpace = MathConstants.NULL_DELIMITER_SPACE_EM * size;
        float width = contentWidth + 2.0F * sideSpace;

        float shiftUp = MathConstants.NUM2_EM * size;
        float shiftDown = MathConstants.DENOM2_EM * size;
        float clr = drt;
        // 轴高对齐：kern1 = shiftUp − num.depth − (axis + drt/2)，kern2 = axis − drt/2 − (den.height − shiftDown)
        float kern1 = shiftUp - num.getDepth() - (axis + delta);
        float kern2 = axis - delta - (den.getHeight() - shiftDown);
        if (clr > kern1) {
            kern1 = clr;
        }
        if (clr > kern2) {
            kern2 = clr;
        }

        Builder builder = new Builder();
        float numY = -(axis + delta + kern1 + num.getDepth());
        float denY = -axis + delta + kern2 + den.getHeight();
        builder.addBox(num, sideSpace + (contentWidth - num.getWidth()) / 2.0F, numY,
                MathConstants.SCRIPT_SCALE);
        builder.addBox(den, sideSpace + (contentWidth - den.getWidth()) / 2.0F, denY,
                MathConstants.SCRIPT_SCALE);
        // 分数线中心落在数学轴上；左右端点覆盖视觉 ink 边界（斜体剪切越量），
        // 而非仅排版盒宽——否则横线右端比斜体分子短、位置随内容字形"左右飘"
        float numLeft = (contentWidth - num.getWidth()) / 2.0F - num.getLeftInkOverhang();
        float numRight = (contentWidth + num.getWidth()) / 2.0F + num.getRightInkOverhang();
        float denLeft = (contentWidth - den.getWidth()) / 2.0F - den.getLeftInkOverhang();
        float denRight = (contentWidth + den.getWidth()) / 2.0F + den.getRightInkOverhang();
        float barLeft = Math.min(numLeft, denLeft);
        float barRight = Math.max(numRight, denRight);
        builder.addRule(sideSpace + barLeft, -(axis + delta), barRight - barLeft, drt);
        builder.width = width;
        builder.height = axis + delta + kern1 + num.getDepth() + num.getHeight();
        builder.depth = -axis + delta + kern2 + den.getHeight() + den.getDepth();
        return builder.toBox();
    }

    // ==================== 根号 ====================

    private MathBox layoutSqrt(LatexSqrt node, float size, MathMetrics m) {
        // 被开方内容用 cramped style（TeX NthRoot）
        MathBox radicand = layoutNode(node.getRadicand(), size, m, true);
        float drt = MathConstants.RULE_THICKNESS_EM * size;
        // TeX NthRoot（text 口径）：clr = θ + θ/4，再经根号变体阶梯余量对半补偿——
        // DelimiterFactory 选最小 ≥ totalH+clr 的 radical 变体，depth 超出部分的一半补入 clr
        // （JLaTeXMath NthRoot delta/2；小根号 clr 由 0.05em 提升到 ≈0.29em，横线不再贴内容）。
        float clr = drt + Math.abs(drt) / 4.0F;
        float totalH = radicand.getHeight() + radicand.getDepth();
        float target = totalH + clr;
        float variantDepth = MathConstants.SQRT_VARIANT_DEPTH_EM[MathConstants.SQRT_VARIANT_DEPTH_EM.length - 1]
                * size;
        for (float depthEm : MathConstants.SQRT_VARIANT_DEPTH_EM) {
            variantDepth = depthEm * size;
            if (variantDepth >= target) {
                break;
            }
        }
        clr += (variantDepth - target) / 2.0F;
        // 横线：顶 = 内容顶 + clr + θ，只依赖内容几何与 TeX 常数
        float barTopAbove = radicand.getHeight() + clr + drt;
        float mu = size / 18.0F;

        // 根号字形：整字缩放覆盖（内容 + clr + θ）总高（TeX 阶梯变体的连续近似），至少自然
        // 尺寸；缩放基准用 ink 高（与 layoutLimits 同口径——盒高含 pad，按盒高缩放会使勾偏小），
        // 字形 ink 顶对齐横线顶（√ 勾顶部接横线），缩放只影响字形本身，不影响横线位置。
        // mock/无 ink 数据回退字体盒总高（旧行为）。
        float nativeInkH = m.inkHeight(RADICAL, size);
        float nativeH = nativeInkH > 0.0F ? nativeInkH : m.ascent(size) + m.descent(size);
        float targetTotal = totalH + clr + drt;
        float radicalScale = nativeH <= 0.0F ? 1.0F : Math.max(1.0F, targetTotal / nativeH);
        float radicalSize = size * radicalScale;
        float radicalWidth = m.advance(RADICAL, radicalSize);
        // ink 顶相对字形基线（y 向下，负 = 基线上方）= ink 中心 − ink 半高
        float radicalInkCenter = m.inkCenterOffsetY(RADICAL, radicalSize);
        float radicalInkHalf = m.inkHeight(RADICAL, radicalSize) / 2.0F;

        // 根指数（可选）：scriptscript 字号（TeX env.rootStyle()），水平 −10mu 负 kern 与
        // 垂直 0.55×总高抬升（TeX NthRoot）
        MathBox index = node.getIndex() == null ? null
                : layoutNode(node.getIndex(), size * MathConstants.SCRIPT_SCRIPT_SCALE, m);
        float indexLeft = 0.0F;
        float radicalLeft = 0.0F;
        if (index != null) {
            float negKern = MathConstants.SQRT_INDEX_NEG_KERN_MU * mu;
            float pos = index.getWidth() - negKern;
            if (pos < 0.0F) {
                indexLeft = -pos;
            } else {
                radicalLeft = pos;
            }
        }

        Builder builder = new Builder();
        // 根号字形：ink 顶对齐横线顶（radicalY = bar 顶 − ink 顶偏移；盒顶对齐会把勾抬高 pad 量）
        float radicalY = -barTopAbove - (radicalInkCenter - radicalInkHalf);
        builder.addGlyph(RADICAL, radicalLeft, radicalY, radicalScale);
        // 横线：中心 = 内容顶 + clr + drt/2；左端对齐根号字形 ink 右缘（勾的视觉终点，
        // 而非 advance——ink 窄于 advance 时横线左端悬空），右端覆盖被开方内容视觉右缘 + 1mu
        float ruleCenterY = -(barTopAbove - drt / 2.0F);
        // 左端 = 勾的 ink 右缘（ink 左偏移 + ink 宽）：仅按 ink 宽会左移 bearingX 吃进勾内
        float barLeft = radicalLeft + m.inkLeftBearing(RADICAL, radicalSize) + m.inkWidth(RADICAL, radicalSize);
        float barRight = barLeft + radicand.getWidth() + radicand.getRightInkOverhang() + mu;
        builder.addRule(barLeft, ruleCenterY, barRight - barLeft, drt);
        builder.addBox(radicand, radicalLeft + radicalWidth, 0.0F, 1.0F);
        float height = barTopAbove;
        float depth = Math.max(0.0F, radicalY + radicalInkCenter + radicalInkHalf);
        if (index != null) {
            // TeX NthRoot 精确语义：r.shift = sqrtBox.depth − r.depth − 0.55×(sqrtBox 总高)
            float indexY = depth - index.getDepth()
                    - MathConstants.SQRT_INDEX_FACTOR * (height + depth);
            builder.addBox(index, indexLeft, indexY, MathConstants.SCRIPT_SCRIPT_SCALE);
            height = Math.max(height, -(indexY - index.getHeight()));
            depth = Math.max(depth, indexY + index.getDepth());
        }
        builder.width = Math.max(builder.width, radicalLeft + radicalWidth + radicand.getWidth());
        builder.height = height;
        builder.depth = depth;
        return builder.toBox();
    }

    // ==================== 伸缩括号 ====================

    private MathBox layoutLeftRight(LatexLeftRight node, float size, MathMetrics m) {
        // 内容可能被 \middle 分为多段：先布局全部段并拼总内容盒（minHeight 按整体 δ），
        // 再组装段与中间定界符（JLaTeXMath FencedAtom middle 语义：与两侧同 minHeight + 轴居中）。
        List<LatexNode> parts = node.getParts();
        MathBox[] partBoxes = new MathBox[parts.size()];
        Builder contentBuilder = new Builder();
        for (int i = 0; i < parts.size(); i++) {
            partBoxes[i] = layoutNode(parts.get(i), size, m, false);
            contentBuilder.addBox(partBoxes[i], contentBuilder.width, 0.0F, 1.0F);
        }
        MathBox content = contentBuilder.toBox();
        // TeX FencedAtom：δ = max(h − axis, d + axis)；minHeight = max(δ×901/500, 2δ − 5pt)
        float axis = MathConstants.AXIS_HEIGHT_EM * size;
        float delta = Math.max(content.getHeight() - axis, content.getDepth() + axis);
        float minHeight = Math.max(delta * MathConstants.DELIMITER_FACTOR,
                2.0F * delta - MathConstants.DELIMITER_SHORTFALL_EM * size);
        // 定界符与内容之间用标准原子间距（OPEN→leftType / rightType→CLOSE），非固定 gap
        AtomClass contentClass = atomClassOf(node.getContent());
        float leftGlue = spacingMu(AtomClass.OPEN, contentClass) / 18.0F * size;
        float rightGlue = spacingMu(contentClass, AtomClass.CLOSE) / 18.0F * size;
        List<String> middles = node.getMiddleDelimiters();

        Builder builder = new Builder();
        float cursorX = 0.0F;
        for (int i = 0; i < parts.size(); i++) {
            builder.addBox(partBoxes[i], cursorX, 0.0F, 1.0F);
            cursorX += partBoxes[i].getWidth();
            if (i < middles.size() && middles.get(i) != null) {
                // TeX MiddleAtom 默认 REL 类：中间定界符前后按 REL 原子间距
                cursorX += spacingMu(AtomClass.INNER, AtomClass.REL) / 18.0F * size;
                DelimBox middle = layoutFence(middles.get(i), minHeight, size, m);
                builder.addBox(middle.box, cursorX, middle.baselineY, 1.0F);
                cursorX += middle.box.getWidth()
                        + spacingMu(AtomClass.REL, AtomClass.INNER) / 18.0F * size;
            }
        }
        float leftShift = 0.0F;
        if (node.getLeftDelimiter() != null) {
            DelimBox left = layoutFence(node.getLeftDelimiter(), minHeight, size, m);
            builder.addBox(left.box, -(left.box.getWidth() + leftGlue), left.baselineY, 1.0F);
            leftShift = left.box.getWidth() + leftGlue;
        }
        if (node.getRightDelimiter() != null) {
            DelimBox right = layoutFence(node.getRightDelimiter(), minHeight, size, m);
            builder.addBox(right.box, cursorX + rightGlue, right.baselineY, 1.0F);
        }
        return shiftBox(builder.toBox(), leftShift, 0.0F);
    }

    /**
     * 定界符盒：整字缩放覆盖 minHeight（TeX 阶梯变体的连续近似，阶梯字形资源受限），
     * 垂直居中于数学轴（TeX FencedAtom center）。
     *
     * <p>缩放基准用 ink 高（与 layoutLimits 同口径：盒高含 pad，按盒高缩放会使真机括号
     * 视觉偏矮）；盒内 glyph 基线 y=0，盒度量按 ink 边界（ink 中心/半高相对字形基线），
     * 调用方按 {@code baselineY} 平移——盒度量不重复计入 baselineY（此前 height/depth 已含
     * baselineY 又经 addBox 平移一次，行高虚增 |baselineY|、嵌套几何错位）。</p>
     */
    private DelimBox layoutFence(String delimiter, float minHeight, float size, MathMetrics m) {
        float axis = MathConstants.AXIS_HEIGHT_EM * size;
        float nativeInkH = m.inkHeight(delimiter, size);
        float nativeH = nativeInkH > 0.0F ? nativeInkH : m.ascent(size) + m.descent(size);
        float scale = nativeH <= 0.0F ? 1.0F : Math.max(1.0F, minHeight / nativeH);
        float delimSize = size * scale;
        float width = m.advance(delimiter, delimSize);
        // 轴居中锚定 ink 中心（ink 在字格内不对称，按盒度量会偏轴 0.3em+）；mock 回退盒度量。
        // 数学轴在基线上方 −axis（y 向下坐标），ink 中心 = 盒基线 + inkCenterOffsetY → 基线 = −axis − offset
        float inkCenter = m.inkCenterOffsetY(delimiter, delimSize);
        float inkHalf = m.inkHeight(delimiter, delimSize) / 2.0F;
        float baselineY = -axis - inkCenter;
        List<GlyphElem> glyphs = new ArrayList<GlyphElem>(1);
        glyphs.add(new GlyphElem(delimiter, 0.0F, 0.0F, scale));
        // 盒内 glyph 基线 = 盒基线：ink 顶 = inkCenter − inkHalf（y 向下，负 = 基线上方）
        float height = Math.max(0.0F, inkHalf - inkCenter);
        float depth = Math.max(0.0F, inkCenter + inkHalf);
        return new DelimBox(new MathBox(width, height, depth, glyphs, null), baselineY);
    }

    private static final class DelimBox {
        final MathBox box;
        final float baselineY;

        DelimBox(MathBox box, float baselineY) {
            this.box = box;
            this.baselineY = baselineY;
        }
    }

    // ==================== 矩阵/分段函数 ====================

    private MathBox layoutMatrix(LatexMatrix node, float size, MathMetrics m) {
        List<List<List<LatexNode>>> rows = node.getRows();
        int rowCount = rows.size();
        int colCount = 0;
        for (List<List<LatexNode>> row : rows) {
            colCount = Math.max(colCount, row.size());
        }
        MathBox[][] cells = new MathBox[rowCount][colCount];
        float[] colWidth = new float[colCount];
        float[] rowMaxHeight = new float[rowCount];
        float[] rowMaxDepth = new float[rowCount];
        for (int i = 0; i < rowCount; i++) {
            for (int j = 0; j < rows.get(i).size(); j++) {
                MathBox cell = layoutList(rows.get(i).get(j), size, m, false);
                cells[i][j] = cell;
                colWidth[j] = Math.max(colWidth[j], cell.getWidth());
                rowMaxHeight[i] = Math.max(rowMaxHeight[i], cell.getHeight());
                rowMaxDepth[i] = Math.max(rowMaxDepth[i], cell.getDepth());
            }
        }
        // TeX MatrixAtom（MATRIX 口径）：列间 1em（无外沿半隙）、行间 1ex、外沿 0.4ex
        float colSep = MathConstants.MATRIX_COL_GAP_EM * size;
        float rowSep = MathConstants.MATRIX_ROW_SEP_EX * m.xHeight(size);
        float outerPad = MathConstants.MATRIX_OUTER_PAD_EX * m.xHeight(size);
        float[] colX = new float[colCount];
        float cursorX = 0.0F;
        for (int j = 0; j < colCount; j++) {
            colX[j] = cursorX;
            cursorX += colWidth[j] + colSep;
        }
        float contentWidth = cursorX - colSep;
        float[] rowBaseline = new float[rowCount];
        float cursorY = outerPad;
        for (int i = 0; i < rowCount; i++) {
            rowBaseline[i] = cursorY + rowMaxHeight[i];
            cursorY = rowBaseline[i] + rowMaxDepth[i] + rowSep;
        }
        float totalHeight = cursorY - rowSep + outerPad;
        float axis = MathConstants.AXIS_HEIGHT_EM * size;
        float height = totalHeight / 2.0F + axis;
        float depth = totalHeight / 2.0F - axis;

        // 定界符：TeX FencedAtom minHeight（内容轴居中 → δ = totalHeight/2）
        float fenceMin = Math.max(totalHeight / 2.0F * MathConstants.DELIMITER_FACTOR,
                totalHeight - MathConstants.DELIMITER_SHORTFALL_EM * size);
        DelimBox leftFence = null;
        DelimBox rightFence = null;
        switch (node.getFence()) {
            case PAREN:
                leftFence = layoutFence("(", fenceMin, size, m);
                rightFence = layoutFence(")", fenceMin, size, m);
                break;
            case BRACKET:
                leftFence = layoutFence("[", fenceMin, size, m);
                rightFence = layoutFence("]", fenceMin, size, m);
                break;
            case BAR:
                leftFence = layoutFence("|", fenceMin, size, m);
                rightFence = layoutFence("|", fenceMin, size, m);
                break;
            case CASES:
                leftFence = layoutFence("{", fenceMin, size, m);
                break;
            case NONE:
            default:
                break;
        }
        float leftW = leftFence == null ? 0.0F : leftFence.box.getWidth();
        float rightW = rightFence == null ? 0.0F : rightFence.box.getWidth();

        Builder builder = new Builder();
        for (int i = 0; i < rowCount; i++) {
            for (int j = 0; j < rows.get(i).size(); j++) {
                MathBox cell = cells[i][j];
                // 列对齐：cases（TeX array{ll}）与 array 列说明 l/r/c；其余环境默认居中（TeX array c）
                char align = node.columnAlignOf(j);
                float cellOffset;
                if (align == 'l') {
                    cellOffset = 0.0F;
                } else if (align == 'r') {
                    cellOffset = colWidth[j] - cell.getWidth();
                } else {
                    cellOffset = (colWidth[j] - cell.getWidth()) / 2.0F;
                }
                builder.addBox(cell, leftW + colX[j] + cellOffset, -height + rowBaseline[i], 1.0F);
            }
        }
        if (leftFence != null) {
            builder.addBox(leftFence.box, 0.0F, leftFence.baselineY, 1.0F);
        }
        if (rightFence != null) {
            builder.addBox(rightFence.box, leftW + contentWidth, rightFence.baselineY, 1.0F);
        }
        builder.width = Math.max(builder.width, leftW + contentWidth + rightW);
        builder.height = Math.max(builder.height, height);
        builder.depth = Math.max(builder.depth, depth);
        return builder.toBox();
    }

    // ==================== 组合数与重音 ====================

    private MathBox layoutBinom(LatexBinom node, float size, MathMetrics m) {
        float scriptSize = size * MathConstants.SCRIPT_SCALE;
        // \binom = 无分数线分数（TeX FractionAtom no-rule 路径）：cramped scriptstyle，
        // shiftUp = num3、shiftDown = denom2，间隙不足 3θ 时上下均分 delta
        MathBox upper = layoutNode(node.getUpper(), scriptSize, m, true);
        MathBox lower = layoutNode(node.getLower(), scriptSize, m, true);
        float drt = MathConstants.RULE_THICKNESS_EM * size;
        float clr = MathConstants.NO_RULE_CLR_FACTOR * drt;
        float shiftUp = MathConstants.NUM3_EM * size;
        float shiftDown = MathConstants.DENOM2_EM * size;
        float kern = shiftUp - upper.getDepth() - (lower.getHeight() - shiftDown);
        float delta = (clr - kern) / 2.0F;
        if (delta > 0.0F) {
            shiftUp += delta;
            shiftDown += delta;
        }
        float contentWidth = Math.max(upper.getWidth(), lower.getWidth());
        float sideSpace = MathConstants.NULL_DELIMITER_SPACE_EM * size;
        float axis = MathConstants.AXIS_HEIGHT_EM * size;
        float height = shiftUp + upper.getHeight();
        float depth = shiftDown + lower.getDepth();
        // 圆括号：TeX FencedAtom minHeight + 轴居中（\binom = \left( … \right) 语义）
        float fenceDelta = Math.max(height - axis, depth + axis);
        float fenceMin = Math.max(fenceDelta * MathConstants.DELIMITER_FACTOR,
                2.0F * fenceDelta - MathConstants.DELIMITER_SHORTFALL_EM * size);
        DelimBox leftFence = layoutFence("(", fenceMin, size, m);
        DelimBox rightFence = layoutFence(")", fenceMin, size, m);
        float leftW = leftFence.box.getWidth();
        float innerW = contentWidth + 2.0F * sideSpace;

        Builder builder = new Builder();
        builder.addBox(upper, leftW + sideSpace + (contentWidth - upper.getWidth()) / 2.0F, -shiftUp,
                MathConstants.SCRIPT_SCALE);
        builder.addBox(lower, leftW + sideSpace + (contentWidth - lower.getWidth()) / 2.0F, shiftDown,
                MathConstants.SCRIPT_SCALE);
        builder.addBox(leftFence.box, 0.0F, leftFence.baselineY, 1.0F);
        builder.addBox(rightFence.box, leftW + innerW, rightFence.baselineY, 1.0F);
        builder.width = leftW + innerW + rightFence.box.getWidth();
        builder.height = Math.max(builder.height, height);
        builder.depth = Math.max(builder.depth, depth);
        return builder.toBox();
    }

    private MathBox layoutAccent(LatexAccent node, float size, MathMetrics m) {
        // 重音基底用 cramped style（TeX AccentedAtom）；\\underline 基底不 cramp（TeX UnderlinedAtom）
        MathBox base = layoutNode(node.getBase(), size, m, !node.isBelow());
        Builder builder = new Builder();
        float drt = MathConstants.RULE_THICKNESS_EM * size;
        if (node.isStretchable()) {
            builder.addBox(base, 0.0F, 0.0F, 1.0F);
            // \overline：kern 3θ + 线 θ，盒高 h+5θ；\\underline：kern 3θ + 线 θ，盒深 d+5θ；
            // 横线覆盖基底视觉 ink 边界（斜体剪切越量）
            float ruleCenter = MathConstants.OVERBAR_KERN_FACTOR * drt + drt / 2.0F;
            float barLeft = -base.getLeftInkOverhang();
            float barWidth = base.getWidth() + base.getLeftInkOverhang() + base.getRightInkOverhang();
            if (node.isBelow()) {
                builder.addRule(barLeft, base.getDepth() + ruleCenter, barWidth, drt);
                builder.depth = base.getDepth() + MathConstants.OVERBAR_BOX_FACTOR * drt;
                builder.height = base.getHeight();
            } else {
                builder.addRule(barLeft, -(base.getHeight() + ruleCenter), barWidth, drt);
                builder.height = base.getHeight() + MathConstants.OVERBAR_BOX_FACTOR * drt;
                builder.depth = base.getDepth();
            }
            return builder.toBox();
        }
        String accentText = node.getAccentText();
        // TeX AccentedAtom（acc=false 路径，hat/bar/vec/dot/tilde）：重音按正文字号，
        // vBox [accent][strut(−base.h)][base] 堆叠 → 重音字形底与基底基线重合，
        // 字形自身带上方偏移（CM accent 字形设计，JLaTeXMath ref-boxes 取证）。
        // 盒高 = 重音字形盒高（base 顶低于重音顶时由重音决定），深 = 基底深。
        // 水平 = 盒宽居中 + 斜体 skew（TeX \accent skewchar：斜体单字符基底视觉重心
        // 随几何斜切右移，重音右移 skew 对齐；JLaTeXMath AccentedAtom.getSkew 同语义，
        // ref-abs 取证 \hat{x} 的 ^ 右移 0.064em）。
        float accentWidth = m.advance(accentText, size);
        float diff = (base.getWidth() - accentWidth) / 2.0F;
        float skew = 0.0F;
        if (!base.getGlyphs().isEmpty() && base.getGlyphs().size() == 1
                && base.getGlyphs().get(0).isItalic()) {
            skew = MathConstants.ACCENT_SKEW_FACTOR * m.xHeight(size);
        }
        float baseX = 0.0F;
        float accentX = skew;
        if (diff > 0.0F) {
            accentX = skew + diff; // 重音窄于基底：重音盒居中 + skew
        } else if (diff < 0.0F) {
            baseX = -diff; // 重音宽于基底：基底居中于重音（JLaTeXMath ALIGN_CENTER 语义）
        }
        builder.addBox(base, baseX, 0.0F, 1.0F);
        builder.addGlyph(accentText, accentX, 0.0F, 1.0F);
        builder.width = Math.max(base.getWidth(), accentX + accentWidth);
        // 盒高 = max(重音字形高（字体 ascent 近似）, 基底高)：复合基底（\hat{AB} 等）时
        // 不得低于基底，否则盒高低估、嵌套几何错位（JLaTeXMath 取 accent 高 + 1mu + base 高）
        float accentInkCenter = m.inkCenterOffsetY(accentText, size);
        float accentInkHalf = m.inkHeight(accentText, size) / 2.0F;
        // 盒高 = max(重音字形 ink 顶, 基底高)；无 ink 数据回退字体 ascent（代数等价）
        builder.height = Math.max(Math.max(0.0F, accentInkHalf - accentInkCenter), base.getHeight());
        builder.depth = base.getDepth();
        return builder.toBox();
    }

    // ==================== 工具 ====================

    /** 盒平移（用于 left/right 归一基线）。 */
    private static MathBox shiftBox(MathBox box, float dx, float dy) {
        if (dx == 0.0F && dy == 0.0F) {
            return box;
        }
        Builder builder = new Builder();
        for (GlyphElem glyph : box.getGlyphs()) {
            builder.addGlyph(glyph.getText(), glyph.getX() + dx, glyph.getY() + dy, glyph.getSizeScale(),
                    glyph.isItalic());
        }
        for (RuleElem rule : box.getRules()) {
            builder.addRule(rule.getX() + dx, rule.getY() + dy, rule.getWidth(), rule.getThickness());
        }
        builder.width = box.getWidth() + dx;
        builder.height = box.getHeight() - dy;
        builder.depth = box.getDepth() + dy;
        // addGlyph 重建只按 glyph 起点记占位边界，必须保留原盒越量（\frac{\left(x\right)}{2}
        // 类公式的分数横线依赖 \left\right 盒的斜体越量）；dx 平移后左越量按 dx 缩减
        // （\left\right 左括号从 −leftW 平移到 0，原左越量 leftW 同步消去）
        MathBox shifted = builder.toBox();
        return new MathBox(shifted.getWidth(), shifted.getHeight(), shifted.getDepth(),
                shifted.getGlyphs(), shifted.getRules(),
                Math.max(0.0F, box.getLeftInkOverhang() - dx), box.getRightInkOverhang());
    }

    /** 布局累加器：拼接子盒并维护包围盒与视觉 ink 边界（换元：上层只吃盒边界）。 */
    private static final class Builder {
        float width;
        float height;
        float depth;
        /** 视觉 ink 边界累计（相对盒原点，可为负/超宽）。 */
        float minVisualX = Float.MAX_VALUE;
        float maxVisualX = -Float.MAX_VALUE;
        final List<GlyphElem> glyphs = new ArrayList<GlyphElem>();
        final List<RuleElem> rules = new ArrayList<RuleElem>();

        void advance(float delta) {
            width += delta;
        }

        void addGlyph(String text, float x, float y, float sizeScale) {
            addGlyph(text, x, y, sizeScale, false);
        }

        void addGlyph(String text, float x, float y, float sizeScale, boolean italic) {
            glyphs.add(new GlyphElem(text, x, y, sizeScale, italic));
            // 非变量基元字形（根号/重音/定界符）ink 不超出 advance：只记占位边界
            minVisualX = Math.min(minVisualX, x);
            maxVisualX = Math.max(maxVisualX, x);
        }

        void addRule(float x, float y, float ruleWidth, float thickness) {
            rules.add(new RuleElem(x, y, ruleWidth, thickness));
            minVisualX = Math.min(minVisualX, x);
            maxVisualX = Math.max(maxVisualX, x + ruleWidth);
        }

        void addBox(MathBox child, float dx, float dy, float glyphScale) {
            for (GlyphElem glyph : child.getGlyphs()) {
                glyphs.add(new GlyphElem(glyph.getText(), glyph.getX() + dx, glyph.getY() + dy,
                        glyph.getSizeScale() * glyphScale, glyph.isItalic()));
            }
            for (RuleElem rule : child.getRules()) {
                rules.add(new RuleElem(rule.getX() + dx, rule.getY() + dy, rule.getWidth(), rule.getThickness()));
            }
            height = Math.max(height, child.getHeight() - dy);
            depth = Math.max(depth, child.getDepth() + dy);
            width = Math.max(width, dx + child.getWidth());
            // ink 越量随嵌套传播：子盒视觉左/右边界 = dx − leftOv .. dx + w + rightOv
            minVisualX = Math.min(minVisualX, dx - child.getLeftInkOverhang());
            maxVisualX = Math.max(maxVisualX, dx + child.getWidth() + child.getRightInkOverhang());
        }

        MathBox toBox() {
            float leftOverhang = minVisualX == Float.MAX_VALUE ? 0.0F : Math.max(0.0F, -minVisualX);
            float rightOverhang = maxVisualX == -Float.MAX_VALUE ? 0.0F : Math.max(0.0F, maxVisualX - width);
            return new MathBox(width, height, depth, glyphs, rules, leftOverhang, rightOverhang);
        }
    }
}
