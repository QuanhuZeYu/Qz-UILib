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
 * scriptscript 0.49；嵌套时缩放沿 addBox 复合（sizeScale 相对公式正文字号）。</p>
 */
public final class MathLayoutService {

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
        return layoutList(nodes, baseSizePx, metrics);
    }

    // ==================== 节点分派 ====================

    private MathBox layoutNode(LatexNode node, float size, MathMetrics m) {
        switch (node.getKind()) {
            case ATOM:
                return layoutAtom((LatexAtom) node, size, m);
            case GROUP:
                return layoutList(((LatexGroup) node).getChildren(), size, m);
            case SPACE:
                return spaceBox((float) ((LatexSpace) node).getEmWidth() * size);
            case SUP_SUB:
                return layoutSupSub((LatexSupSub) node, size, m);
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
        glyphs.add(new GlyphElem(text, 0.0F, 0.0F, 1.0F));
        return new MathBox(width, m.ascent(size), m.descent(size), glyphs, null);
    }

    private static MathBox spaceBox(float width) {
        return new MathBox(width, 0.0F, 0.0F, null, null);
    }

    /** 水平拼接（含数学原子间距）。 */
    private MathBox layoutList(List<LatexNode> nodes, float size, MathMetrics m) {
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
            MathBox child = layoutNode(node, size, m);
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

    private MathBox layoutSupSub(LatexSupSub node, float size, MathMetrics m) {
        MathBox base = layoutNode(node.getBase(), size, m);
        float scriptSize = size * MathConstants.SCRIPT_SCALE;
        MathBox sup = node.getSup() == null ? null : layoutNode(node.getSup(), scriptSize, m);
        MathBox sub = node.getSub() == null ? null : layoutNode(node.getSub(), scriptSize, m);

        boolean limits = node.getBase().getKind() == LatexNode.Kind.ATOM
                && ((LatexAtom) node.getBase()).isLimitsOperator()
                && (sup != null || sub != null);
        if (limits) {
            return layoutLimits(base, sup, sub, size);
        }
        if (sup == null && sub == null) {
            return base;
        }

        // TeX ScriptsAtom 约束链（行内=text 样式口径）：
        // shiftUp = base.height − supdrop；shiftDown = base.depth + subdrop；
        // 上标下限 max(sup2, sup.depth + xHeight/4)；下标下限 max(sub1, sub.height − 4·xHeight/5)；
        // 双脚本再经 sub2 与 4×规则线粗最小间距、4/5 xHeight 二次分配（psi）。
        float xHeight = m.xHeight(size);
        float drt = MathConstants.RULE_THICKNESS_EM * size;
        float shiftUp = base.getHeight() - MathConstants.SUP_DROP_EM * size;
        float shiftDown = base.getDepth() + MathConstants.SUB_DROP_EM * size;
        float supY = 0.0F;
        float subY = 0.0F;
        if (sup != null && sub == null) {
            shiftUp = Math.max(Math.max(shiftUp, MathConstants.SUP2_EM * size),
                    sup.getDepth() + xHeight / 4.0F);
            supY = -shiftUp;
        } else if (sub != null && sup == null) {
            shiftDown = Math.max(Math.max(shiftDown, MathConstants.SUB1_EM * size),
                    sub.getHeight() - 4.0F * xHeight / 5.0F);
            subY = shiftDown;
        } else {
            shiftUp = Math.max(Math.max(shiftUp, MathConstants.SUP2_EM * size),
                    sup.getDepth() + xHeight / 4.0F);
            shiftDown = Math.max(shiftDown, MathConstants.SUB2_EM * size);
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

        float scriptSpace = MathConstants.SCRIPT_SPACE_EM * size;
        float supWidth = sup == null ? 0.0F : sup.getWidth();
        float subWidth = sub == null ? 0.0F : sub.getWidth();
        float scriptWidth = Math.max(supWidth, subWidth);

        Builder builder = new Builder();
        builder.addBox(base, 0.0F, 0.0F, 1.0F);
        if (sup != null) {
            builder.addBox(sup, base.getWidth() + scriptSpace, supY, MathConstants.SCRIPT_SCALE);
        }
        if (sub != null) {
            builder.addBox(sub, base.getWidth() + scriptSpace, subY, MathConstants.SCRIPT_SCALE);
        }
        builder.width = base.getWidth() + (scriptWidth > 0.0F ? scriptSpace + scriptWidth : 0.0F);
        builder.height = Math.max(base.getHeight(), sup != null ? -supY + sup.getHeight() : 0.0F);
        builder.depth = Math.max(base.getDepth(), sub != null ? subY + sub.getDepth() : 0.0F);
        return builder.toBox();
    }

    /** 大运算符上下限：上下堆叠居中（TeX ScriptsAtom limits 路径：over 间隙 3pt、under 间隙 0.3pt）。 */
    private MathBox layoutLimits(MathBox base, MathBox sup, MathBox sub, float size) {
        Builder builder = new Builder();
        float contentWidth = base.getWidth();
        if (sup != null) {
            contentWidth = Math.max(contentWidth, sup.getWidth());
        }
        if (sub != null) {
            contentWidth = Math.max(contentWidth, sub.getWidth());
        }
        builder.addBox(base, (contentWidth - base.getWidth()) / 2.0F, 0.0F, 1.0F);
        float height = base.getHeight();
        float depth = base.getDepth();
        if (sup != null) {
            float overGap = MathConstants.LIMITS_OVER_GAP_EM * size;
            // 上标按底对齐：底 = base 顶上方 overGap（用 depth 参与，与 TeX UnderOverAtom 一致）
            float supY = -(base.getHeight() + overGap + sup.getDepth());
            builder.addBox(sup, (contentWidth - sup.getWidth()) / 2.0F, supY, MathConstants.SCRIPT_SCALE);
            height = base.getHeight() + overGap + sup.getTotalHeight();
        }
        if (sub != null) {
            float underGap = MathConstants.LIMITS_UNDER_GAP_EM * size;
            // 下标按顶对齐：顶 = base 底下方 underGap（用 height 参与）
            float subY = base.getDepth() + underGap + sub.getHeight();
            builder.addBox(sub, (contentWidth - sub.getWidth()) / 2.0F, subY, MathConstants.SCRIPT_SCALE);
            depth = base.getDepth() + underGap + sub.getTotalHeight();
        }
        builder.height = height;
        builder.depth = depth;
        return builder.toBox();
    }

    // ==================== 分数 ====================

    private MathBox layoutFrac(LatexFrac node, float size, MathMetrics m) {
        float scriptSize = size * MathConstants.SCRIPT_SCALE;
        MathBox num = layoutNode(node.getNumerator(), scriptSize, m);
        MathBox den = layoutNode(node.getDenominator(), scriptSize, m);
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
        // 分数线中心落在数学轴上
        builder.addRule(sideSpace, -(axis + delta), contentWidth, drt);
        builder.width = width;
        builder.height = axis + delta + kern1 + num.getDepth() + num.getHeight();
        builder.depth = -axis + delta + kern2 + den.getHeight() + den.getDepth();
        return builder.toBox();
    }

    // ==================== 根号 ====================

    private MathBox layoutSqrt(LatexSqrt node, float size, MathMetrics m) {
        MathBox radicand = layoutNode(node.getRadicand(), size, m);
        float drt = MathConstants.RULE_THICKNESS_EM * size;
        float xHeight = m.xHeight(size);
        // TeX NthRoot（行内=text 口径）：clr = drt + |xHeight|/4
        float clr = drt + Math.abs(xHeight) / 4.0F;
        float totalH = radicand.getHeight() + radicand.getDepth() + clr + drt;

        // 根号字形无阶梯变体：整字缩放覆盖目标高（TeX 取足够大变体的近似）
        float baseAscent = m.ascent(size);
        float radicalScale = baseAscent <= 0.0F ? 1.0F : Math.max(1.0F, totalH / baseAscent);
        float radicalSize = size * radicalScale;
        float radicalWidth = m.advance(RADICAL, radicalSize);
        float radicalAscent = m.ascent(radicalSize);
        float radicalDescent = m.descent(radicalSize);
        float radicalTotal = radicalAscent + radicalDescent;
        // 根号实际总高与目标的差的一半回补给 clr（TeX：clr += delta/2）
        float delta = radicalTotal - totalH;
        if (delta > 0.0F) {
            clr += delta / 2.0F;
        }

        Builder builder = new Builder();
        // 根号字形：底对齐内容底 + 线粗
        float radicalY = radicand.getDepth() + drt;
        builder.addGlyph(RADICAL, 0.0F, radicalY, radicalScale);
        // 横线：顶边 = 内容顶上方 clr + drt
        float ruleTopY = -(radicand.getHeight() + clr + drt);
        builder.addRule(radicalWidth, ruleTopY, radicand.getWidth(), drt);
        builder.addBox(radicand, radicalWidth, 0.0F, 1.0F);
        float height = radicand.getHeight() + clr + drt;
        if (node.getIndex() != null) {
            // 根指数：TeX NthRoot 精确语义——r.shift = sqrtBox.depth − r.depth − 0.55×(sqrtBox 总高)。
            // 指数基线落在根号盒深度侧内移，视觉位于根号内侧中上部（基线≈横线顶高度、x 在根号左上），
            // 而不是根号顶之外（顶之外是此前分离瑕疵的镜像错误）。
            MathBox index = layoutNode(node.getIndex(), size * MathConstants.SCRIPT_SCALE, m);
            float sqrtHeight = radicand.getHeight() + clr + radicalAscent;
            float sqrtDepth = radicand.getDepth();
            float indexY = sqrtDepth - index.getDepth()
                    - MathConstants.SQRT_INDEX_FACTOR * (sqrtHeight + sqrtDepth);
            float indexTop = indexY - index.getHeight();
            builder.addBox(index, radicalWidth * 0.6F, indexY, MathConstants.SCRIPT_SCALE);
            height = Math.max(height, -indexTop);
        }
        builder.width = radicalWidth + radicand.getWidth();
        builder.height = height;
        builder.depth = Math.max(0.0F, radicand.getDepth());
        return builder.toBox();
    }

    // ==================== 伸缩括号 ====================

    private MathBox layoutLeftRight(LatexLeftRight node, float size, MathMetrics m) {
        MathBox content = layoutNode(node.getContent(), size, m);
        float gap = MathConstants.DELIM_GAP_EM * size;
        float targetHeight = content.getTotalHeight() + 2.0F * gap;

        Builder builder = new Builder();
        builder.addBox(content, 0.0F, 0.0F, 1.0F);
        float leftShift = 0.0F;
        if (node.getLeftDelimiter() != null) {
            DelimBox left = layoutDelimiter(node.getLeftDelimiter(), targetHeight, content, size, m);
            builder.addBox(left.box, -(left.box.getWidth() + gap), left.baselineY, 1.0F);
            leftShift = left.box.getWidth() + gap;
        }
        if (node.getRightDelimiter() != null) {
            DelimBox right = layoutDelimiter(node.getRightDelimiter(), targetHeight, content, size, m);
            builder.addBox(right.box, content.getWidth() + gap, right.baselineY, 1.0F);
        }
        return shiftBox(builder.toBox(), leftShift, 0.0F);
    }

    /** 定界符盒：按目标高度整字缩放，垂直居中于内容。 */
    private DelimBox layoutDelimiter(String delimiter, float targetHeight, MathBox content, float size,
            MathMetrics m) {
        float nativeHeight = m.ascent(size) + m.descent(size);
        float scale = nativeHeight <= 0.0F ? 1.0F : Math.max(1.0F, targetHeight / nativeHeight);
        float delimSize = size * scale;
        float width = m.advance(delimiter, delimSize);
        float contentCenterUp = content.getTotalHeight() / 2.0F - content.getDepth();
        float delimCenterUp = (m.ascent(delimSize) - m.descent(delimSize)) / 2.0F;
        float baselineY = delimCenterUp - contentCenterUp;
        List<GlyphElem> glyphs = new ArrayList<GlyphElem>(1);
        glyphs.add(new GlyphElem(delimiter, 0.0F, 0.0F, scale));
        float height = Math.max(0.0F, -baselineY + m.ascent(delimSize));
        float depth = Math.max(0.0F, baselineY + m.descent(delimSize));
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
        float[] rowHeight = new float[rowCount];
        float[] rowMaxHeight = new float[rowCount];
        for (int i = 0; i < rowCount; i++) {
            for (int j = 0; j < rows.get(i).size(); j++) {
                MathBox cell = layoutList(rows.get(i).get(j), size, m);
                cells[i][j] = cell;
                colWidth[j] = Math.max(colWidth[j], cell.getWidth());
                rowHeight[i] = Math.max(rowHeight[i], cell.getTotalHeight());
                rowMaxHeight[i] = Math.max(rowMaxHeight[i], cell.getHeight());
            }
        }
        float colGap = MathConstants.MATRIX_COL_GAP_EM * size;
        float rowGap = MathConstants.MATRIX_ROW_GAP_EM * size;
        float[] colX = new float[colCount];
        float contentWidth = 0.0F;
        for (int j = 0; j < colCount; j++) {
            colX[j] = contentWidth + colGap / 2.0F;
            contentWidth += colWidth[j] + colGap;
        }
        float[] rowTop = new float[rowCount];
        float cursor = 0.0F;
        for (int i = 0; i < rowCount; i++) {
            rowTop[i] = cursor;
            cursor += rowHeight[i] + rowGap;
        }
        float totalHeight = cursor - rowGap;
        float axis = MathConstants.AXIS_HEIGHT_EM * size;
        float height = totalHeight / 2.0F + axis;
        float depth = totalHeight / 2.0F - axis;

        Builder builder = new Builder();
        for (int i = 0; i < rowCount; i++) {
            for (int j = 0; j < rows.get(i).size(); j++) {
                MathBox cell = cells[i][j];
                float x = colX[j] + (colWidth[j] - cell.getWidth()) / 2.0F;
                float y = -height + rowTop[i] + rowMaxHeight[i];
                builder.addBox(cell, x, y, 1.0F);
            }
        }
        float fenceOverhang = MathConstants.MATRIX_FENCE_OVERHANG_EM * size;
        switch (node.getFence()) {
            case PAREN:
                addFencePair(builder, "(", ")", contentWidth, totalHeight, fenceOverhang, size, m);
                break;
            case BRACKET:
                addFencePair(builder, "[", "]", contentWidth, totalHeight, fenceOverhang, size, m);
                break;
            case BAR:
                addFencePair(builder, "|", "|", contentWidth, totalHeight, fenceOverhang, size, m);
                break;
            case CASES:
                addLeftFence(builder, "{", contentWidth, totalHeight, fenceOverhang, size, m);
                break;
            case NONE:
            default:
                break;
        }
        builder.width = Math.max(builder.width, contentWidth);
        builder.height = Math.max(builder.height, height);
        builder.depth = Math.max(builder.depth, depth);
        return builder.toBox();
    }

    private void addFencePair(Builder builder, String left, String right, float contentWidth, float totalHeight,
            float overhang, float size, MathMetrics m) {
        float targetHeight = totalHeight + 2.0F * overhang;
        float nativeHeight = m.ascent(size) + m.descent(size);
        float scale = nativeHeight <= 0.0F ? 1.0F : Math.max(1.0F, targetHeight / nativeHeight);
        float delimSize = size * scale;
        float gap = MathConstants.DELIM_GAP_EM * size;
        float leftWidth = m.advance(left, delimSize);
        float rightWidth = m.advance(right, delimSize);
        float centerY = -MathConstants.AXIS_HEIGHT_EM * size;
        float baseline = centerY + (m.ascent(delimSize) - m.descent(delimSize)) / 2.0F;
        List<GlyphElem> lg = new ArrayList<GlyphElem>(1);
        lg.add(new GlyphElem(left, 0.0F, 0.0F, scale));
        builder.addBox(new MathBox(leftWidth, m.ascent(delimSize), m.descent(delimSize), lg, null),
                -(leftWidth + gap), baseline, 1.0F);
        List<GlyphElem> rg = new ArrayList<GlyphElem>(1);
        rg.add(new GlyphElem(right, 0.0F, 0.0F, scale));
        builder.addBox(new MathBox(rightWidth, m.ascent(delimSize), m.descent(delimSize), rg, null),
                contentWidth + gap, baseline, 1.0F);
    }

    private void addLeftFence(Builder builder, String fence, float contentWidth, float totalHeight,
            float overhang, float size, MathMetrics m) {
        float targetHeight = totalHeight + 2.0F * overhang;
        float nativeHeight = m.ascent(size) + m.descent(size);
        float scale = nativeHeight <= 0.0F ? 1.0F : Math.max(1.0F, targetHeight / nativeHeight);
        float delimSize = size * scale;
        float gap = MathConstants.DELIM_GAP_EM * size;
        float fenceWidth = m.advance(fence, delimSize);
        float centerY = -MathConstants.AXIS_HEIGHT_EM * size;
        float baseline = centerY + (m.ascent(delimSize) - m.descent(delimSize)) / 2.0F;
        List<GlyphElem> glyphs = new ArrayList<GlyphElem>(1);
        glyphs.add(new GlyphElem(fence, 0.0F, 0.0F, scale));
        builder.addBox(new MathBox(fenceWidth, m.ascent(delimSize), m.descent(delimSize), glyphs, null),
                -(fenceWidth + gap), baseline, 1.0F);
    }

    // ==================== 组合数与重音 ====================

    private MathBox layoutBinom(LatexBinom node, float size, MathMetrics m) {
        float scriptSize = size * MathConstants.SCRIPT_SCALE;
        MathBox upper = layoutNode(node.getUpper(), scriptSize, m);
        MathBox lower = layoutNode(node.getLower(), scriptSize, m);
        float gap = MathConstants.BINOM_GAP_EM * size;
        float totalHeight = upper.getTotalHeight() + gap + lower.getTotalHeight();
        float contentWidth = Math.max(upper.getWidth(), lower.getWidth());
        float axis = MathConstants.AXIS_HEIGHT_EM * size;
        float height = totalHeight / 2.0F + axis;

        Builder builder = new Builder();
        builder.addBox(upper, (contentWidth - upper.getWidth()) / 2.0F,
                -height + upper.getHeight(), MathConstants.SCRIPT_SCALE);
        builder.addBox(lower, (contentWidth - lower.getWidth()) / 2.0F,
                -height + upper.getTotalHeight() + gap + lower.getHeight(), MathConstants.SCRIPT_SCALE);
        float overhang = MathConstants.MATRIX_FENCE_OVERHANG_EM * size;
        addFencePair(builder, "(", ")", contentWidth, totalHeight, overhang, size, m);
        builder.width = Math.max(builder.width, contentWidth);
        builder.height = Math.max(builder.height, height);
        return builder.toBox();
    }

    private MathBox layoutAccent(LatexAccent node, float size, MathMetrics m) {
        MathBox base = layoutNode(node.getBase(), size, m);
        Builder builder = new Builder();
        builder.addBox(base, 0.0F, 0.0F, 1.0F);
        float gap = MathConstants.ACCENT_GAP_EM * size;
        if (node.isStretchable()) {
            if (node.isBelow()) {
                builder.addRule(0.0F, base.getDepth() + gap, base.getWidth(),
                        MathConstants.RULE_THICKNESS_EM * size);
            } else {
                builder.addRule(0.0F, -(base.getHeight() + gap + MathConstants.RULE_THICKNESS_EM * size),
                        base.getWidth(), MathConstants.RULE_THICKNESS_EM * size);
            }
            return builder.toBox();
        }
        String accentText = node.getAccentText();
        float accentWidth = m.advance(accentText, size);
        float accentY = -(base.getHeight() + gap + m.descent(size));
        builder.addGlyph(accentText, (base.getWidth() - accentWidth) / 2.0F, accentY, 1.0F);
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
            builder.addGlyph(glyph.getText(), glyph.getX() + dx, glyph.getY() + dy, glyph.getSizeScale());
        }
        for (RuleElem rule : box.getRules()) {
            builder.addRule(rule.getX() + dx, rule.getY() + dy, rule.getWidth(), rule.getThickness());
        }
        builder.width = box.getWidth() + dx;
        builder.height = box.getHeight() - dy;
        builder.depth = box.getDepth() + dy;
        return builder.toBox();
    }

    /** 布局累加器：拼接子盒并维护包围盒。 */
    private static final class Builder {
        float width;
        float height;
        float depth;
        final List<GlyphElem> glyphs = new ArrayList<GlyphElem>();
        final List<RuleElem> rules = new ArrayList<RuleElem>();

        void advance(float delta) {
            width += delta;
        }

        void addGlyph(String text, float x, float y, float sizeScale) {
            glyphs.add(new GlyphElem(text, x, y, sizeScale));
        }

        void addRule(float x, float y, float ruleWidth, float thickness) {
            rules.add(new RuleElem(x, y, ruleWidth, thickness));
        }

        void addBox(MathBox child, float dx, float dy, float glyphScale) {
            for (GlyphElem glyph : child.getGlyphs()) {
                glyphs.add(new GlyphElem(glyph.getText(), glyph.getX() + dx, glyph.getY() + dy,
                        glyph.getSizeScale() * glyphScale));
            }
            for (RuleElem rule : child.getRules()) {
                rules.add(new RuleElem(rule.getX() + dx, rule.getY() + dy, rule.getWidth(), rule.getThickness()));
            }
            height = Math.max(height, child.getHeight() - dy);
            depth = Math.max(depth, child.getDepth() + dy);
            width = Math.max(width, dx + child.getWidth());
        }

        MathBox toBox() {
            return new MathBox(width, height, depth, glyphs, rules);
        }
    }
}
