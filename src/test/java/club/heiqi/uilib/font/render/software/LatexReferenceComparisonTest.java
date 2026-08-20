package club.heiqi.uilib.font.render.software;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assume;
import org.junit.Test;

import club.heiqi.uilib.font.latex.LatexShowcaseFormulas;
import club.heiqi.uilib.font.latex.layout.GlyphElem;
import club.heiqi.uilib.font.latex.layout.MathBox;
import club.heiqi.uilib.font.latex.layout.RuleElem;

/**
 * LaTeX 参考对比工具：同一公式分别经 JLaTeXMath（真 TeX 排版内核 + Computer Modern，
 * 仅作开发期对比基准，GPLv2+Classpath Exception，动态加载、不进入 mod 构建）与本仓
 * headless 软件渲染，产出并排 PNG 与几何差异报告，供布局常量与字体策略校准。
 *
 * <p>输出：{@code build/reports/latex-compare/}（ref/ours/side-by-side PNG + comparison.txt）。
 * 参考 jar 不存在时整类跳过（CI 安全）。</p>
 */
public class LatexReferenceComparisonTest {

    private static final String JAR = "D:\\Code\\MC\\Qz工作站\\temp\\latex-compare\\jlatexmath-1.0.7.jar";
    private static final File OUT_DIR = new File("build/reports/latex-compare");

    private static final String[][] FORMULAS = {
            {"frac-quadratic", "\\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}"},
            {"sum", "\\sum_{i=1}^{n} i = \\frac{n(n+1)}{2}"},
            {"int", "\\int_0^\\infty e^{-x}\\,dx = 1"},
            {"frac-basic", "\\frac{1}{2} + \\frac{a}{b}"},
            {"sqrt-basic", "\\sqrt{x} + \\sqrt{x^2+y^2}"},
            {"sqrt-index", "\\sqrt[3]{x}"},
            {"sup-sub", "x^2 + y_i + x_i^2"},
            {"euler", "e^{i\\pi} + 1 = 0"},
            {"limit", "\\lim_{x \\to 0} \\frac{\\sin x}{x} = 1"},
            {"greek", "\\alpha + \\beta = \\gamma, \\Delta \\pi \\sigma"},
            {"pmatrix", "\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}"},
            {"cases", "f(x) = \\begin{cases} x & x > 0 \\\\ -x & x \\leq 0 \\end{cases}"},
            {"delim", "\\left( \\frac{a}{b} \\right) \\left[ x \\right]"},
            {"binom", "\\binom{n}{k} = \\frac{n!}{k!(n-k)!}"},
            {"accent", "\\hat{x} + \\bar{y} + \\vec{v} + \\dot{z} + \\tilde{w}"},
            {"overline", "\\overline{AB} + \\underline{x}"},
    };

    /** 生成参考图 + 自己渲染图 + 并排图 + 几何对比报告。 */
    @Test
    public void generateReferenceComparison() throws Exception {
        Assume.assumeTrue("参考 jar 不存在，跳过对比（开发期工具）", new File(JAR).isFile());
        if (!OUT_DIR.exists() && !OUT_DIR.mkdirs()) {
            throw new IllegalStateException("无法创建输出目录: " + OUT_DIR);
        }
        StringBuilder report = new StringBuilder();
        report.append("LaTeX 参考对比报告（JLaTeXMath=真 TeX 排版基准 / ours=headless 软件渲染）")
                .append(System.lineSeparator());
        for (int index = 0; index < FORMULAS.length; index++) {
            String name = FORMULAS[index][0];
            String latex = FORMULAS[index][1];
            BufferedImage ref = renderReference(latex, 22.0F);
            LatexSoftwareRenderKit.RenderResult ours = LatexSoftwareRenderKit.render(
                    "<latex>" + latex + "</latex>", 16);
            MathBox box = LatexSoftwareRenderKit.layout(latex, 16);
            if (ref == null) {
                report.append(String.format("[%s] 参考渲染失败，跳过%n", name));
                continue;
            }
            int scale = 2;
            BufferedImage oursImage = FontSoftwareRasterizer.toImage(ours.pixels, ours.width, ours.height);
            BufferedImage scaled = scaleNearest(oursImage, scale);
            File refFile = new File(OUT_DIR, String.format("%02d-%s-ref.png", Integer.valueOf(index), name));
            File oursFile = new File(OUT_DIR, String.format("%02d-%s-ours.png", Integer.valueOf(index), name));
            File sideFile = new File(OUT_DIR, String.format("%02d-%s-side.png", Integer.valueOf(index), name));
            writePng(ref, refFile);
            writePng(scaleNearest(oursImage, scale), oursFile);
            writePng(sideBySide(ref, scaled), sideFile);

            report.append(String.format("[%02d] %s : %s%n", Integer.valueOf(index), name, latex));
            report.append(String.format("  ref : %dx%d  advance=%.1f%n", Integer.valueOf(ref.getWidth()),
                    Integer.valueOf(ref.getHeight()), Float.valueOf(referenceAdvance(latex, 22.0F))));
            report.append(String.format("  ours: %dx%d  advance=%d  box(w=%.1f h=%.1f d=%.1f)%n",
                    Integer.valueOf(ours.width), Integer.valueOf(ours.height),
                    Integer.valueOf(ours.advanceWidth), Float.valueOf(box.getWidth()),
                    Float.valueOf(box.getHeight()), Float.valueOf(box.getDepth())));
            dumpOursBox(report, box);
            report.append(System.lineSeparator());
        }
        Files.write(new File(OUT_DIR, "comparison.txt").toPath(),
                report.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 全部演示公式的参考渲染存在性（Smoke：JLaTeXMath 可渲染同集）。 */
    @Test
    public void referenceRendersAllShowcaseFormulas() throws Exception {
        Assume.assumeTrue("参考 jar 不存在，跳过对比（开发期工具）", new File(JAR).isFile());
        String[] formulas = LatexShowcaseFormulas.all();
        int rendered = 0;
        for (String formula : formulas) {
            BufferedImage ref = renderReference(formula, 20.0F);
            if (ref != null && ref.getWidth() > 0 && ref.getHeight() > 0) {
                rendered++;
            }
        }
        // Computer Modern 无 CJK，\text{中文} 参考渲染允许失败；其余应全部成功
        org.junit.Assert.assertTrue("参考渲染应覆盖演示公式（允许中文 1 条失败）: " + rendered + "/"
                + formulas.length, rendered >= formulas.length - 1);
    }

    // ==================== JLaTeXMath 反射桥 ====================

    private static URLClassLoader referenceLoader() throws Exception {
        return new URLClassLoader(new URL[] {new File(JAR).toURI().toURL()},
                LatexReferenceComparisonTest.class.getClassLoader());
    }

    private static BufferedImage renderReference(String latex, float size) {
        try {
            URLClassLoader loader = referenceLoader();
            Class<?> formulaClass = Class.forName("org.scilab.forge.jlatexmath.TeXFormula", true, loader);
            Object formula = formulaClass.getConstructor(String.class).newInstance(latex);
            // 优先 createBufferedImage(style, size, Color fg, Color bg)
            try {
                Method create = formulaClass.getMethod("createBufferedImage", int.class, float.class,
                        Color.class, Color.class);
                return (BufferedImage) create.invoke(formula, Integer.valueOf(0), Float.valueOf(size),
                        Color.BLACK, Color.WHITE);
            } catch (NoSuchMethodException noBuffered) {
                Method createPng = formulaClass.getMethod("createPNG", int.class, float.class, int.class,
                        int.class);
                Object icon = createPng.invoke(formula, Integer.valueOf(0), Float.valueOf(size),
                        Integer.valueOf(0x000000), Integer.valueOf(0xFFFFFF));
                Class<?> imageIconClass = Class.forName("javax.swing.ImageIcon", true, loader);
                Method getImage = imageIconClass.getMethod("getImage");
                java.awt.Image image = (java.awt.Image) getImage.invoke(icon);
                BufferedImage buffered = new BufferedImage(image.getWidth(null), image.getHeight(null),
                        BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = buffered.createGraphics();
                graphics.drawImage(image, 0, 0, null);
                graphics.dispose();
                return buffered;
            }
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static float referenceAdvance(String latex, float size) {
        try {
            URLClassLoader loader = referenceLoader();
            Class<?> formulaClass = Class.forName("org.scilab.forge.jlatexmath.TeXFormula", true, loader);
            Object formula = formulaClass.getConstructor(String.class).newInstance(latex);
            Object root = formulaClass.getMethod("getRoot").invoke(formula);
            Class<?> boxClass = Class.forName("org.scilab.forge.jlatexmath.Box", true, loader);
            float width = ((Number) boxClass.getMethod("getWidth").invoke(root)).floatValue();
            float height = ((Number) boxClass.getMethod("getHeight").invoke(root)).floatValue();
            float depth = ((Number) boxClass.getMethod("getDepth").invoke(root)).floatValue();
            return width + height + depth;
        } catch (Throwable throwable) {
            return Float.NaN;
        }
    }

    // ==================== 图合成 ====================

    private static BufferedImage sideBySide(BufferedImage left, BufferedImage right) {
        int gap = 10;
        int width = left.getWidth() + gap + right.getWidth();
        int height = Math.max(left.getHeight(), right.getHeight()) + 8;
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = combined.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(left, 2, 4, null);
        graphics.setColor(new Color(200, 60, 60));
        graphics.fillRect(left.getWidth() + 4, 4, 2, height - 8);
        graphics.drawImage(right, left.getWidth() + gap + 2, 4, null);
        graphics.dispose();
        return combined;
    }

    private static BufferedImage scaleNearest(BufferedImage source, int scale) {
        if (scale <= 1) {
            return source;
        }
        BufferedImage scaled = new BufferedImage(source.getWidth() * scale, source.getHeight() * scale,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.drawImage(source, 0, 0, source.getWidth() * scale, source.getHeight() * scale, null);
        graphics.dispose();
        return scaled;
    }

    private static void writePng(BufferedImage image, File out) throws Exception {
        if (!javax.imageio.ImageIO.write(image, "png", out)) {
            throw new IllegalStateException("PNG 编码失败: " + out);
        }
    }

    private static void dumpOursBox(StringBuilder report, MathBox box) {
        report.append("  ours glyphs:");
        for (GlyphElem glyph : box.getGlyphs()) {
            report.append(String.format(" [%s x=%.1f y=%.1f s=%.2f]", glyph.getText(),
                    Float.valueOf(glyph.getX()), Float.valueOf(glyph.getY()),
                    Float.valueOf(glyph.getSizeScale())));
        }
        for (RuleElem rule : box.getRules()) {
            report.append(String.format(" [rule x=%.1f y=%.1f w=%.1f t=%.1f]", Float.valueOf(rule.getX()),
                    Float.valueOf(rule.getY()), Float.valueOf(rule.getWidth()),
                    Float.valueOf(rule.getThickness())));
        }
        report.append(System.lineSeparator());
    }

    /** Box 树反射 dump（保留给后续深度对比；当前仅报告 ours 侧几何）。 */
    @SuppressWarnings("unused")
    private static void dumpReferenceBox(StringBuilder report, Object box, int depth, int maxDepth) {
        if (box == null || depth > maxDepth) {
            return;
        }
        try {
            Class<?> boxClass = box.getClass();
            Method getWidth = boxClass.getMethod("getWidth");
            Method getHeight = boxClass.getMethod("getHeight");
            Method getDepth = boxClass.getMethod("getDepth");
            StringBuilder indent = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                indent.append("  ");
            }
            report.append(indent).append(boxClass.getSimpleName()).append(String.format(" w=%.1f h=%.1f d=%.1f%n",
                    ((Number) getWidth.invoke(box)).floatValue(),
                    ((Number) getHeight.invoke(box)).floatValue(),
                    ((Number) getDepth.invoke(box)).floatValue()));
            Field childrenField = findField(boxClass, "children");
            if (childrenField != null) {
                childrenField.setAccessible(true);
                Object children = childrenField.get(box);
                if (children instanceof List<?>) {
                    List<?> list = new ArrayList<Object>((List<?>) children);
                    for (Object child : list) {
                        dumpReferenceBox(report, child, depth + 1, maxDepth);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 反射失败静默跳过（对比工具宽容失败）
        }
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException missing) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
