package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;
import club.heiqi.uilib.testing.TestResources;

import java.nio.file.Paths;
import java.util.Collections;
import club.heiqi.uilib.host.client.font.context.FontPlanSampling;
import club.heiqi.uilib.host.client.font.context.FontProjection;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.host.client.font.context.RasterPolicy;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphRasterStyle;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;
import club.heiqi.uilib.host.font.IsolatedFontEngine;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;

/** 原版替代near-plane欠采样真实native像素复现；其余验收只用已定位计划，完全不查询MC/GL。 */
public final class FontPlanSamplingScenario extends TestApplication {
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    private static FontRenderContext context(float[] p, float[] mv) {
        return new FontRenderContext(
                new FontProjection(p, mv, new int[] {0, 0, 800, 600}),
                0,
                600,
                false,
                0x0201,
                false,
                false,
                new int[] {0, 0, 800, 600},
                FontRenderContext.MatrixState.ui(p, mv),
                new Object(),
                71,
                0);
    }

    private static PaintCommand command(
            FontSource source, int id, InkBounds ink, float ratio, float logicalEm) {
        TextStyle style = new TextStyle("probe", 12, 0xffffffff);
        TextLayout.Glyph glyph =
                new TextLayout.Glyph(
                        source,
                        12,
                        id,
                        0,
                        1,
                        0,
                        1,
                        0,
                        9,
                        6,
                        0,
                        false,
                        false,
                        ink,
                        logicalEm,
                        ratio,
                        GlyphRasterStyle.NORMAL,
                        style,
                        0);
        TextLayout.Line line =
                new TextLayout.Line(
                        "A", 0, 0, 0, 1, 0, 1, 6, 9, 9, 3, 12, Collections.singletonList(glyph));
        return PaintCommand.text(
                new LayoutBox(0, 0, 6, 12),
                null,
                new TextLayout(6, 12, style, Collections.singletonList(line)));
    }

    private static double actual(RasterSampling s) {
        return s.pixelsPerLayoutUnit * 12.0;
    }

    public static void main(String[] args) throws Exception {
        new FontPlanSamplingScenario().run(args);
    }

    private PaintCommand known;
    private int id;

    @Override
    protected void runTests() throws Exception {
        String[] args = arguments();
        String font =
                args.length == 0
                        ? "src/main/resources/assets/qz_uilib/fonts/unifont/unifont-16.0.04.otf"
                        : args[0];
        FontSource source =
                new FontSource(
                        "sampling-probe",
                        1,
                        "native",
                        TestResources.bytes(Paths.get(font)).array(),
                        0,
                        false,
                        Collections.<String>emptySet());
        float[] p = new float[16];
        p[0] = p[5] = 1;
        p[10] = -1.002002f;
        p[11] = -1;
        p[14] = -.2002002f;
        float[] mv = IDENTITY.clone();
        mv[0] = .01f;
        mv[5] = .01f;
        mv[6] = .2f;
        mv[13] = -.03f;
        mv[14] = -1;
        FontRenderContext world = context(p, mv);
        scenario(
                "近裁面实际native可见像素采样",
                () -> {
                    try (FontSession nativeService = IsolatedFontEngine.shared().openSession();
                            FontFace face = source.openFace(nativeService, 12)) {
                        id = face.glyphIndex('A');
                        known = command(source, id, face.glyphInkBounds(id), 1, 12);
                        GlyphBitmap bitmap = face.rasterize(id);
                        byte[] pixels = bitmap.pixels();
                        int visible = 0;
                        double maximum = 0;
                        for (int y = 0; y < bitmap.height; y++)
                            for (int x = 0; x < bitmap.width; x++)
                                if ((pixels[(y * bitmap.width + x) * 4 + 3] & 255) != 0) {
                                    double px = bitmap.bearingX + (x + .5) * bitmap.pixelScale,
                                            py = 9 - bitmap.bearingY + (y + .5) * bitmap.pixelScale;
                                    double[] q = world.projection.clipAt(px, py, 0);
                                    if (q[3] > 0
                                            && Math.abs(q[0]) < q[3]
                                            && Math.abs(q[1]) < q[3]
                                            && Math.abs(q[2]) < q[3]) {
                                        visible++;
                                        maximum =
                                                Math.max(
                                                        maximum,
                                                        world.projection.pixelsPerUnitAt(
                                                                px, py, 0));
                                    }
                                }
                        double originW = world.projection.clipAt(0, 9, 0)[3];
                        RasterSampling sampled =
                                FontPlanSampling.sampling(known, world, RasterPolicy.DEFAULT);
                        require(
                                originW < 0 && visible > 0,
                                "original near-plane hypothesis not reached");
                        require(
                                RasterPolicy.DEFAULT.rasterEm(maximum * 12) == 512
                                        && Math.abs(actual(sampled) - 512) < 0.001,
                                "visible ink under sampled");
                        require(sampled.revision == 71, "lost context revision");
                        System.out.println(
                                "nearPlane originW="
                                        + originW
                                        + " visibleNativeAlpha="
                                        + visible
                                        + " visibleMaxDensity="
                                        + maximum
                                        + " selectedRasterEm="
                                        + actual(sampled));
                    }
                });
        scenario(
                "已关闭face计划墨迹与四角投影",
                () -> {
                    // face已关闭：后续计划采样只能消费logical ink，不可能依赖实时native ink查询。
                    float[] distantMv = mv.clone();
                    distantMv[14] = -10;
                    FontRenderContext distant = context(p, distantMv);
                    InkBounds stored = known.text.lines().get(0).glyphs().get(0).inkBounds;
                    double cornerDensity = 0;
                    for (int corner = 0; corner < 4; corner++)
                        cornerDensity =
                                Math.max(
                                        cornerDensity,
                                        distant.projection.pixelsPerUnitAt(
                                                (corner & 1) == 0 ? stored.left : stored.right,
                                                9
                                                        + ((corner & 2) == 0
                                                                ? stored.top
                                                                : stored.bottom),
                                                0));
                    int expectedDistant = RasterPolicy.DEFAULT.rasterEm(cornerDensity * 12);
                    require(
                            Math.abs(
                                            actual(
                                                            FontPlanSampling.sampling(
                                                                    known,
                                                                    distant,
                                                                    RasterPolicy.DEFAULT))
                                                    - expectedDistant)
                                    < 0.001,
                            "positive-w four corners ignored");
                    float[] behindMv = mv.clone();
                    behindMv[14] = 1;
                    FontRenderContext behind = context(p, behindMv);
                    require(
                            Math.abs(
                                            actual(
                                                            FontPlanSampling.sampling(
                                                                    known,
                                                                    behind,
                                                                    RasterPolicy.DEFAULT))
                                                    - 12)
                                    < 0.001,
                            "fully behind quality fallback");
                    PaintCommand moved =
                            PaintCommand.text(new LayoutBox(101, 203, 6, 12), null, known.text);
                    require(
                            Math.abs(
                                            actual(
                                                            FontPlanSampling.sampling(
                                                                    moved,
                                                                    world,
                                                                    RasterPolicy.DEFAULT))
                                                    - 12)
                                    < 0.001,
                            "command box missing from logical ink coordinates");
                });
        RasterPolicy custom = new RasterPolicy(4, 12, 37);
        PaintCommand unknown = command(source, id, InkBounds.UNKNOWN, 1, 12);
        scenario(
                "自定义上限及未知无效墨迹",
                () -> {
                    require(
                            Math.abs(actual(FontPlanSampling.sampling(known, world, custom)) - 37)
                                    < 0.001,
                            "hardcoded maximum");
                    require(
                            Math.abs(
                                            actual(
                                                            FontPlanSampling.sampling(
                                                                    command(
                                                                            source,
                                                                            id,
                                                                            known.text
                                                                                    .lines()
                                                                                    .get(0)
                                                                                    .glyphs()
                                                                                    .get(0)
                                                                                    .inkBounds,
                                                                            0.001f,
                                                                            12),
                                                                    world,
                                                                    custom))
                                                    - 37)
                                    < 0.001,
                            "logical ink multiplied by rasterToLayout twice");
                    require(
                            Math.abs(actual(FontPlanSampling.sampling(unknown, world, custom)) - 37)
                                    < 0.001,
                            "unknown perspective needs bounded conservative quality");
                    InkBounds nearOnly = new InkBounds(true, 0, -4.75f, 1, -4.25f);
                    require(
                            Math.abs(
                                            actual(
                                                            FontPlanSampling.sampling(
                                                                    command(
                                                                            source, id, nearOnly, 1,
                                                                            12),
                                                                    world,
                                                                    custom))
                                                    - 37)
                                    < 0.001,
                            "positive-w near plane crossing");
                    InkBounds invalid = new InkBounds(true, Float.NaN, -8, 1, 0);
                    require(
                            Math.abs(
                                            actual(
                                                            FontPlanSampling.sampling(
                                                                    command(
                                                                            source, id, invalid, 1,
                                                                            12),
                                                                    world,
                                                                    custom))
                                                    - 37)
                                    < 0.001,
                            "nonfinite ink discarded quality");
                });
        float[] gui = IDENTITY.clone();
        gui[0] = 2f / 800;
        gui[5] = -2f / 600;
        FontRenderContext screen = context(gui, IDENTITY);
        // float投影系数的精度可能把密度略推过1；按实际Jacobian向上选档，不硬塞em12。
        int guiEm = RasterPolicy.DEFAULT.rasterEm(screen.projection.pixelsPerUnitAt(0, 0, 0) * 12);
        scenario(
                "GUI并行投影稳定采样",
                () -> {
                    long begin = System.nanoTime();
                    for (int i = 0; i < 20000; i++)
                        require(
                                Math.abs(
                                                actual(
                                                                FontPlanSampling.sampling(
                                                                        (i & 1) == 0
                                                                                ? known
                                                                                : unknown,
                                                                        screen,
                                                                        RasterPolicy.DEFAULT))
                                                        - guiEm)
                                        < 0.001,
                                "parallel GUI unstable sampling");
                    System.out.println(
                            "gui iterations=20000 stableRasterEm="
                                    + guiEm
                                    + " elapsedNanos="
                                    + (System.nanoTime() - begin)
                                    + " nativeFaceClosed=true");
                });
        scenario(
                "空计划与混合logicalEm拒绝",
                () -> {
                    PaintCommand empty =
                            PaintCommand.text(
                                    new LayoutBox(0, 0, 0, 0),
                                    null,
                                    new TextLayout(
                                            0,
                                            0,
                                            new TextStyle("probe", 12, -1),
                                            Collections.<TextLayout.Line>emptyList()));
                    RasterSampling emptySampling =
                            FontPlanSampling.sampling(empty, world, new RasterPolicy(37));
                    require(
                            emptySampling.pixelsPerLayoutUnit == 1 && emptySampling.revision == 71,
                            "empty plan changed logical quality/revision");
                    boolean rejected = false;
                    try {
                        FontPlanSampling.sampling(
                                command(source, id, InkBounds.UNKNOWN, 1, 24), screen, custom);
                    } catch (IllegalArgumentException expected) {
                        rejected = true;
                    }
                    require(rejected, "mixed logicalEm silently accepted");
                });
        System.out.println(
                "FONT_PLAN_SAMPLING_SCENARIO_PASS nearEye knownInkCorners unknownBounded policyMaximum logicalInk guiStable mixedEmExplicit");
    }
}
