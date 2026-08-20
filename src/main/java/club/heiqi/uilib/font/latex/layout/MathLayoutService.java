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
        LatexNode previous = null;
        for (LatexNode node : nodes) {
            if (previous != null) {
                float gap = spacingMu(previous, node) / 18.0F * size;
                builder.advance(gap);
            }
            MathBox child = layoutNode(node, size, m);
            builder.addBox(child, builder.width, 0.0F, 1.0F);
            previous = node;
        }
        return builder.toBox();
    }

    /** 相邻原子间距（mu，简化 TeX 表）。 */
    private static float spacingMu(LatexNode left, LatexNode right) {
        AtomClass l = atomClassOf(left);
        AtomClass r = atomClassOf(right);
        if (l == AtomClass.BIN) {
            return isOrdish(r) ? MathConstants.MED_MU : 0.0F;
        }
        if (r == AtomClass.BIN) {
            return isOrdish(l) ? MathConstants.MED_MU : 0.0F;
        }
        if (l == AtomClass.REL) {
            return isOrdish(r) ? MathConstants.THICK_MU : 0.0F;
        }
        if (r == AtomClass.REL) {
            return isOrdish(l) ? MathConstants.THICK_MU : 0.0F;
        }
        if (l == AtomClass.OP) {
            return (r == AtomClass.ORD || r == AtomClass.OPEN) ? MathConstants.THIN_MU : 0.0F;
        }
        if (r == AtomClass.OP) {
            return (l == AtomClass.ORD || l == AtomClass.CLOSE) ? MathConstants.THIN_MU : 0.0F;
        }
        if (l == AtomClass.PUNCT && isOrdish(r)) {
            return MathConstants.THIN_MU;
        }
        return 0.0F;
    }

    private static boolean isOrdish(AtomClass cls) {
        return cls == AtomClass.ORD || cls == AtomClass.OP || cls == AtomClass.CLOSE
                || cls == AtomClass.PUNCT || cls == AtomClass.INNER;
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

        Builder builder = new Builder();
        builder.addBox(base, 0.0F, 0.0F, 1.0F);
        float supX = base.getWidth();
        float subX = base.getWidth();
        float supY = -(MathConstants.SUP_RAISE_EM * size);
        if (sup != null) {
            float maxY = -(base.getHeight() + sup.getDepth() - MathConstants.SUP_SUB_MIN_GAP_EM * size);
            supY = Math.min(supY, maxY);
        }
        if (sub != null) {
            float subY = MathConstants.SUB_DROP_EM * size;
            float minY = base.getDepth() + sub.getHeight() - MathConstants.SUP_SUB_MIN_GAP_EM * size;
            subY = Math.max(subY, minY);
            // 下标比主体宽时，上标与下标水平居中
            if (sup != null && sub.getWidth() > base.getWidth()) {
                float shift = (sub.getWidth() - sup.getWidth()) / 2.0F;
                builder.addBox(sup, subX + shift, supY, MathConstants.SCRIPT_SCALE);
            }
            builder.addBox(sub, subX, subY, MathConstants.SCRIPT_SCALE);
        } else if (sup != null) {
            builder.addBox(sup, supX, supY, MathConstants.SCRIPT_SCALE);
        }
        return builder.toBox();
    }

    /** 大运算符上下限：上下堆叠居中。 */
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
        float gap = MathConstants.LIMITS_GAP_EM * size;
        if (sup != null) {
            float supY = -(base.getHeight() + gap + sup.getHeight());
            builder.addBox(sup, (contentWidth - sup.getWidth()) / 2.0F, supY, MathConstants.SCRIPT_SCALE);
        }
        if (sub != null) {
            float subY = base.getDepth() + gap + sub.getHeight();
            builder.addBox(sub, (contentWidth - sub.getWidth()) / 2.0F, subY, MathConstants.SCRIPT_SCALE);
        }
        return builder.toBox();
    }

    // ==================== 分数 ====================

    private MathBox layoutFrac(LatexFrac node, float size, MathMetrics m) {
        float scriptSize = size * MathConstants.SCRIPT_SCALE;
        MathBox num = layoutNode(node.getNumerator(), scriptSize, m);
        MathBox den = layoutNode(node.getDenominator(), scriptSize, m);
        float thickness = MathConstants.RULE_THICKNESS_EM * size;
        float gap = MathConstants.FRAC_GAP_EM * size;
        float width = Math.max(num.getWidth(), den.getWidth()) + 2.0F * MathConstants.FRAC_OVERHANG_EM * size;

        Builder builder = new Builder();
        // 分子：基线使分子底 = -(gap + t/2)
        float numY = -(gap + thickness / 2.0F + num.getDepth());
        builder.addBox(num, (width - num.getWidth()) / 2.0F, numY, MathConstants.SCRIPT_SCALE);
        // 分母：基线使分母顶 = bar 底 + gap
        float denY = gap + thickness / 2.0F + den.getHeight();
        builder.addBox(den, (width - den.getWidth()) / 2.0F, denY, MathConstants.SCRIPT_SCALE);
        // 分数线：顶边 = 分子底 - gap
        float barTopY = -(2.0F * gap + thickness / 2.0F);
        builder.addRule(0.0F, barTopY, width, thickness);
        builder.width = width;
        return builder.toBox();
    }

    // ==================== 根号 ====================

    private MathBox layoutSqrt(LatexSqrt node, float size, MathMetrics m) {
        MathBox radicand = layoutNode(node.getRadicand(), size, m);
        float thickness = MathConstants.RULE_THICKNESS_EM * size;
        float clearance = MathConstants.SQRT_CLEARANCE_EM * size;
        float totalHeight = radicand.getHeight() + radicand.getDepth() + clearance + thickness;

        float baseAscent = m.ascent(size);
        float radicalScale = baseAscent <= 0.0F ? 1.0F : totalHeight / baseAscent;
        radicalScale = Math.max(1.0F, radicalScale);
        float radicalSize = size * radicalScale;
        float radicalWidth = m.advance(RADICAL, radicalSize);
        float radicalAscent = m.ascent(radicalSize);

        Builder builder = new Builder();
        // 根号字形：底部贴齐内容底，顶覆盖 -totalHeight
        float radicalY = radicand.getDepth() + thickness - (radicalAscent - totalHeight);
        builder.addGlyph(RADICAL, 0.0F, radicalY, radicalScale);
        // 横线：顶边 = 内容顶上方 clearance + thickness
        float ruleTopY = -(radicand.getHeight() + clearance + thickness);
        builder.addRule(radicalWidth, ruleTopY, radicand.getWidth(), thickness);
        builder.addBox(radicand, radicalWidth, 0.0F, 1.0F);
        if (node.getIndex() != null) {
            MathBox index = layoutNode(node.getIndex(), size * MathConstants.SCRIPT_SCALE, m);
            // 根指数基线 = 根号横线顶上方 ACCENT_GAP（指数底部距横线一个 gap，不叠加自身高度——
            // 叠加 index.getHeight() 会把指数推高一个字号，与横线分离，真机/headless 渲染均可见）。
            float indexY = -(radicand.getHeight() + clearance + thickness
                    + MathConstants.ACCENT_GAP_EM * size);
            builder.addBox(index, radicalWidth * 0.6F, indexY, MathConstants.SCRIPT_SCALE);
        }
        builder.width = radicalWidth + radicand.getWidth();
        builder.height = radicand.getHeight() + clearance + thickness;
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
        float axis = MathConstants.AXIS_HEIGHT_ASCENT_RATIO * m.ascent(size);
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
        float centerY = -MathConstants.AXIS_HEIGHT_ASCENT_RATIO * m.ascent(size);
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
        float centerY = -MathConstants.AXIS_HEIGHT_ASCENT_RATIO * m.ascent(size);
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
        float axis = MathConstants.AXIS_HEIGHT_ASCENT_RATIO * m.ascent(size);
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
