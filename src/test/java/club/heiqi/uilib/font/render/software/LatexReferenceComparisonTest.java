package club.heiqi.uilib.font.render.software;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.swing.Icon;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Test;

import club.heiqi.uilib.font.latex.LatexShowcaseFormulas;
import club.heiqi.uilib.font.latex.layout.GlyphElem;
import club.heiqi.uilib.font.latex.layout.MathBox;
import club.heiqi.uilib.font.latex.layout.MathLayoutService;
import club.heiqi.uilib.font.latex.layout.RuleElem;

/**
 * 开发期 LaTeX 对照工具，JLaTeXMath 仅通过独立 classloader 加载，不进入生产依赖。
 * 它是另一套 Java 数学排版器，并非 TeX 可执行程序；不同字体不做像素相等断言。
 *
 * <p>所有设置优先 system property，其次 env。必需设置（未设置时 JUnit skip）：
 * {@code qz.latex.reference.jar / QZ_LATEX_REFERENCE_JAR}。
 * 显式空路径、非法 jar、API 不兼容与渲染失败均失败，不伪装成缺资源跳过。</p>
 * <p>可选设置：同前缀 {@code size / SIZE}（整数逻辑 px，默认 16），
 * {@code scale / SCALE}（真实 renderScale，默认 1），{@code style / STYLE}
 * （display/text/script/scriptscript，默认 text），{@code source / SOURCE}
 * （下载 URL 或本地来源说明），{@code version / VERSION}（预期版本，设置后验证），
 * {@code output / OUTPUT}（报告根目录，默认 build/reports/latex-compare）。
 * {@code classpath / CLASSPATH} 可显式指定附加参考字体 jar（按平台 pathSeparator 分隔），
 * 不自动扫描目录。每次使用唯一 run 目录保留旧报告。Gradle 调用优先用环境变量，避免 -D 未转发到 test JVM。</p>
 * <p>建议从 Maven Central 取得 org.scilab.forge:jlatexmath:1.0.7 及 sources.jar，记录实际
 * 下载地址；镜像取得的文件明确标注镜像，不能仅凭文件名宣称已验证官方制品。
 * 参考 bridge 基于该版本 TeXIcon.java 的公开 API：清零 insets 后，绘制基线为
 * box.height * size；getBaseLine() 是比例，不能当作像素。</p>
 */
public class LatexReferenceComparisonTest {

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

    @AfterClass
    public static void release() {
        LatexSoftwareRenderKit.resetShared();
    }

    @Test
    public void generateReferenceComparison() throws Exception {
        Config config = Config.load();
        try (Reference reference = new Reference(config)) {
            File output = outputDirectory(config, "comparison-");
            StringBuilder report = metadata(config, reference);
            for (int index = 0; index < FORMULAS.length; index++) {
                String name = FORMULAS[index][0];
                String latex = FORMULAS[index][1];
                String styled = "\\" + config.style + "style " + latex;
                ReferenceSample ref = reference.prepare(latex);
                LatexComparisonRenderHelper.Sample ours = LatexComparisonRenderHelper.render(
                        styled, config.size, config.scale, null);
                float baseline = (float) Math.ceil(Math.max(ours.baseline,
                        ref.height * config.scale + LatexComparisonRenderHelper.PAD));
                ours = LatexComparisonRenderHelper.render(styled, config.size, config.scale,
                        Float.valueOf(baseline));
                BufferedImage refImage = ref.render(config.scale, baseline);
                String prefix = String.format(Locale.ROOT, "%02d-%s", Integer.valueOf(index), name);
                writePng(refImage, new File(output, prefix + "-ref.png"));
                writePng(ours.image, new File(output, prefix + "-ours.png"));
                writePng(sideBySide(refImage, ours.image), new File(output, prefix + "-side.png"));
                report.append(String.format(Locale.ROOT, "[%s] %s%n", prefix, latex));
                report.append(String.format(Locale.ROOT,
                        "  ref logical box: advance=%.4f height=%.4f depth=%.4f%n",
                        Float.valueOf(ref.width), Float.valueOf(ref.height), Float.valueOf(ref.depth)));
                report.append(String.format(Locale.ROOT,
                        "  ours logical box: advance=%.4f height=%.4f depth=%.4f%n",
                        Float.valueOf(ours.box.getWidth()), Float.valueOf(ours.box.getHeight()),
                        Float.valueOf(ours.box.getDepth())));
                report.append(String.format(Locale.ROOT,
                        "  actual renderScale=%.4f shared baselinePx=%.4f; ref canvas=%dx%d ours canvas=%dx%d%n",
                        Float.valueOf(config.scale), Float.valueOf(baseline), Integer.valueOf(refImage.getWidth()),
                        Integer.valueOf(refImage.getHeight()), Integer.valueOf(ours.image.getWidth()),
                        Integer.valueOf(ours.image.getHeight())));
                report.append("  ours originPx=").append(ours.originX).append(',').append(ours.originY)
                        .append(" observedBaselinePx=").append(ours.baseline)
                        .append(" collectorEndXCeilPx=").append(ours.endX)
                        .append(" frameBoundsPx=").append(Arrays.toString(ours.bounds)).append('\n');
                dumpOursBox(report, ours.box);
                // Keep useful diagnostics even if a later formula fails.
                writeReport(output, report);
            }
            System.out.println("LaTeX comparison: " + output.getCanonicalPath());
        }
    }

    @Test
    public void referenceRendersAllShowcaseFormulas() throws Exception {
        Config config = Config.load();
        try (Reference reference = new Reference(config)) {
            File output = outputDirectory(config, "showcase-");
            StringBuilder report = metadata(config, reference);
            for (String formula : LatexShowcaseFormulas.all()) {
                try {
                    ReferenceSample sample = reference.prepare(formula);
                    sample.render(config.scale, (float) Math.ceil(
                            sample.height * config.scale + LatexComparisonRenderHelper.PAD));
                    report.append("OK: ").append(formula).append('\n');
                } catch (InvocationTargetException failure) {
                    // Explicitly report CJK font limitations; do not swallow API/linkage or unrelated failures.
                    if (!containsHan(formula) || failure.getCause() == null
                            || !"org.scilab.forge.jlatexmath.ParseException".equals(
                                    failure.getCause().getClass().getName())) throw failure;
                    report.append("CJK reference limitation: ").append(formula).append(" -> ")
                            .append(failure.getCause()).append('\n');
                }
                writeReport(output, report);
            }
            System.out.println("LaTeX showcase reference: " + output.getCanonicalPath());
        }
    }

    private static boolean containsHan(String text) {
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) return true;
            offset += Character.charCount(cp);
        }
        return false;
    }

    private static final class Config {
        File jar;
        final List<File> classpath = new ArrayList<File>();
        int size;
        float scale;
        String style;
        String source;
        String version;
        File output;

        static Config load() throws Exception {
            String jar = setting("jar", null);
            Assume.assumeTrue("Configure qz.latex.reference.jar or QZ_LATEX_REFERENCE_JAR to enable comparison",
                    jar != null);
            Config config = new Config();
            config.jar = new File(jar).getCanonicalFile();
            if (jar.trim().isEmpty() || !config.jar.isFile() || !config.jar.canRead()) {
                throw new IllegalArgumentException("Explicit reference jar is not a readable file: " + jar);
            }
            config.classpath.add(config.jar);
            String extraClasspath = setting("classpath", null);
            if (extraClasspath != null) {
                for (String entry : extraClasspath.split(java.util.regex.Pattern.quote(File.pathSeparator), -1)) {
                    File extra = new File(entry).getCanonicalFile();
                    if (entry.trim().isEmpty() || !extra.isFile() || !extra.canRead()) {
                        throw new IllegalArgumentException("Invalid explicit reference classpath entry: " + entry);
                    }
                    try (JarFile archive = new JarFile(extra)) {
                        if (archive.getJarEntry("org/scilab/forge/jlatexmath/TeXFormula.class") != null) {
                            throw new IllegalArgumentException("Additional classpath must not replace reference engine: " + extra);
                        }
                    }
                    config.classpath.add(extra);
                }
            }
            config.size = Integer.parseInt(setting("size", "16"));
            config.scale = Float.parseFloat(setting("scale", "1"));
            if (config.size < 1 || config.size > 256 || !Float.isFinite(config.scale)
                    || config.scale <= 0 || config.scale > 16) {
                throw new IllegalArgumentException("Require size in [1,256], finite scale in (0,16]");
            }
            config.style = setting("style", "text").toLowerCase(Locale.ROOT);
            if (!Arrays.asList("display", "text", "script", "scriptscript").contains(config.style)) {
                throw new IllegalArgumentException("Unknown math style: " + config.style);
            }
            config.source = setting("source", "unspecified (local jar; download origin not attested)");
            config.version = setting("version", null);
            config.output = new File(setting("output", "build/reports/latex-compare"));
            return config;
        }
    }

    private static String setting(String name, String fallback) {
        String value = System.getProperty("qz.latex.reference." + name);
        if (value == null) value = System.getenv("QZ_LATEX_REFERENCE_" + name.toUpperCase(Locale.ROOT));
        return value == null ? fallback : value.trim();
    }

    private static final class Reference implements AutoCloseable {
        final Config config;
        final URLClassLoader loader;
        final Class<?> formulaClass;
        final Class<?> iconClass;
        final int style;
        final String version;
        final String versionSource;

        Reference(Config config) throws Exception {
            this.config = config;
            String discoveredVersion = null;
            String discoveredSource = "unknown (jar has no Maven/manifest version)";
            // Validate the configured archive even when no version expectation was provided.
            try (JarFile jar = new JarFile(config.jar)) {
                JarEntry entry = jar.getJarEntry("META-INF/maven/org.scilab.forge/jlatexmath/pom.properties");
                if (entry != null) {
                    Properties properties = new Properties();
                    try (InputStream stream = jar.getInputStream(entry)) { properties.load(stream); }
                    discoveredVersion = properties.getProperty("version");
                    discoveredSource = entry.getName();
                }
                if (discoveredVersion == null && jar.getManifest() != null) {
                    discoveredVersion = jar.getManifest().getMainAttributes().getValue("Implementation-Version");
                    discoveredSource = "META-INF/MANIFEST.MF Implementation-Version";
                }
            }
            version = discoveredVersion == null ? "unknown" : discoveredVersion;
            versionSource = discoveredSource;
            if (config.version != null && !config.version.equals(version)) {
                throw new IllegalArgumentException("Reference version expected=" + config.version + " actual=" + version);
            }
            // No parent test classpath: the loaded implementation must come from this exact configured jar.
            URL[] urls = new URL[config.classpath.size()];
            for (int i = 0; i < urls.length; i++) urls[i] = config.classpath.get(i).toURI().toURL();
            loader = new URLClassLoader(urls, null);
            try {
                formulaClass = Class.forName("org.scilab.forge.jlatexmath.TeXFormula", true, loader);
                iconClass = Class.forName("org.scilab.forge.jlatexmath.TeXIcon", true, loader);
                Class<?> constants = Class.forName("org.scilab.forge.jlatexmath.TeXConstants", true, loader);
                style = constants.getField("STYLE_" + config.style.toUpperCase(Locale.ROOT)).getInt(null);
                // Static magnification defaults are part of the effective size contract, even in isolated loaders.
                if (iconClass.getField("defaultSize").getFloat(null) != -1
                        || iconClass.getField("magFactor").getFloat(null) != 0) {
                    throw new IllegalStateException("Reference applies unexpected global size/magnification");
                }
            } catch (Exception | LinkageError failure) {
                loader.close();
                throw failure;
            }
        }

        ReferenceSample prepare(String latex) throws Exception {
            Object formula = formulaClass.getConstructor(String.class).newInstance(latex);
            Object icon = formulaClass.getMethod("createTeXIcon", int.class, float.class)
                    .invoke(formula, Integer.valueOf(style), Float.valueOf(config.size));
            iconClass.getMethod("setInsets", Insets.class, boolean.class)
                    .invoke(icon, new Insets(0, 0, 0, 0), Boolean.TRUE);
            iconClass.getMethod("setForeground", Color.class).invoke(icon, Color.BLACK);
            float width = ((Number) iconClass.getMethod("getTrueIconWidth").invoke(icon)).floatValue();
            Object box = iconClass.getMethod("getBox").invoke(icon);
            Class<?> boxClass = Class.forName("org.scilab.forge.jlatexmath.Box", true, loader);
            float height = ((Number) boxClass.getMethod("getHeight").invoke(box)).floatValue() * config.size;
            float depth = ((Number) iconClass.getMethod("getTrueIconDepth").invoke(icon)).floatValue();
            return new ReferenceSample((Icon) icon, width, height, depth);
        }

        @Override
        public void close() throws Exception {
            loader.close();
        }
    }

    private static final class ReferenceSample {
        final Icon icon;
        final float width;
        final float height;
        final float depth;

        ReferenceSample(Icon icon, float width, float height, float depth) {
            this.icon = icon;
            this.width = width;
            this.height = height;
            this.depth = depth;
        }

        BufferedImage render(float scale, float baseline) {
            int pad = LatexComparisonRenderHelper.PAD;
            int canvasWidth = LatexComparisonRenderHelper.dimension(icon.getIconWidth() * scale + pad * 2);
            int canvasHeight = LatexComparisonRenderHelper.dimension(baseline
                    + Math.max(depth * scale, (icon.getIconHeight() - height) * scale) + pad);
            BufferedImage image = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, canvasWidth, canvasHeight);
                graphics.translate(pad, baseline - height * scale);
                // Scale the vector/font paint before rasterization; never resample an existing image.
                graphics.scale(scale, scale);
                icon.paintIcon(null, graphics, 0, 0);
            } finally {
                graphics.dispose();
            }
            LatexComparisonRenderHelper.requireUnclippedInk(image, "JLaTeXMath reference");
            return image;
        }
    }

    private static StringBuilder metadata(Config config, Reference reference) throws Exception {
        StringBuilder report = new StringBuilder("LaTeX geometry/visual comparison; no cross-font pixel equality\n");
        report.append("reference.jar=").append(config.jar).append('\n')
                .append("reference.sha256=").append(sha256(config.jar)).append('\n')
                .append("reference.version=").append(reference.version).append('\n')
                .append("reference.versionSource=").append(reference.versionSource).append('\n')
                .append("reference.declaredDownloadSource=").append(config.source).append('\n')
                .append("reference.codeSource=").append(reference.formulaClass.getProtectionDomain()
                        .getCodeSource().getLocation()).append('\n')
                .append("logicalSizePx=").append(config.size).append(" mathStyle=").append(config.style)
                .append(" actualRenderScale=").append(config.scale).append('\n')
                .append("foreground=FF000000 background=FFFFFFFF; shadow=false; imageResampling=none\n")
                .append("baseline=first production glyph baseline minus MathBox glyph offset; reference=Box.height * logicalSizePx * renderScale\n")
                .append("reference.fonts=jar-bundled TeX fonts (typically Computer Modern); ours.font=")
                .append(LatexSoftwareRenderKit.baseCatalogFont()).append('\n')
                .append("ours.platform=").append(LatexSoftwareRenderKit.platformFontReport()).append('\n')
                .append("ours.layoutVersion=").append(MathLayoutService.LAYOUT_VERSION).append('\n')
                .append("ours.awtCharSize=").append(LatexSoftwareRenderKit.currentAwtCharSize()).append('\n')
                .append("Limits: font metrics, glyph designs, atlas filtering and antialiasing differ. Width/height/depth\n")
                .append("are observations, not equality assertions. Reference is not the TeX executable.\n\n");
        for (File entry : config.classpath) {
            report.append("reference.classpath=").append(entry).append(" sha256=").append(sha256(entry)).append('\n');
        }
        return report;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static File outputDirectory(Config config, String prefix) throws Exception {
        Files.createDirectories(config.output.toPath());
        return Files.createTempDirectory(config.output.toPath(), prefix).toFile();
    }

    private static void writeReport(File output, StringBuilder report) throws Exception {
        Files.write(new File(output, "comparison.txt").toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static BufferedImage sideBySide(BufferedImage left, BufferedImage right) {
        int gap = 12;
        int width = left.getWidth() + gap + right.getWidth();
        int height = Math.max(left.getHeight(), right.getHeight());
        BufferedImage combined = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = combined.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.drawImage(left, 0, 0, null);
            graphics.drawImage(right, left.getWidth() + gap, 0, null);
            graphics.setColor(new Color(200, 60, 60));
            graphics.fillRect(left.getWidth() + gap / 2, 0, 1, height);
        } finally {
            graphics.dispose();
        }
        return combined;
    }

    private static void writePng(BufferedImage image, File out) throws Exception {
        if (!javax.imageio.ImageIO.write(image, "png", out)) throw new IllegalStateException("PNG writer unavailable");
    }

    private static void dumpOursBox(StringBuilder report, MathBox box) {
        report.append("  ours glyphs (logical px):");
        for (GlyphElem glyph : box.getGlyphs()) {
            report.append(String.format(Locale.ROOT, " [%s x=%.4f y=%.4f scale=%.4f]", glyph.getText(),
                    Float.valueOf(glyph.getX()), Float.valueOf(glyph.getY()), Float.valueOf(glyph.getSizeScale())));
        }
        for (RuleElem rule : box.getRules()) {
            report.append(String.format(Locale.ROOT, " [rule x=%.4f y=%.4f w=%.4f t=%.4f]",
                    Float.valueOf(rule.getX()), Float.valueOf(rule.getY()), Float.valueOf(rule.getWidth()),
                    Float.valueOf(rule.getThickness())));
        }
        report.append('\n');
    }
}
