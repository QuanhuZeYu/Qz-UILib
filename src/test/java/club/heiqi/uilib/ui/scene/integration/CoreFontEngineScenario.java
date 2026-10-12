package club.heiqi.uilib.ui.scene.integration;

import club.heiqi.uilib.testing.TestApplication;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.ui.scene.text.RasterSampling;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontEngine;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontMetrics;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphBitmap;
import club.heiqi.uilib.ui.scene.text.font.nativefont.GlyphRasterStyle;
import club.heiqi.uilib.ui.scene.text.font.nativefont.InkBounds;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeDirection;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeRun;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapedGlyph;

/** 无MC/Forge/LWJGL classpath的注入与数据所有权验收；此engine是合同探针，不是发行字体实现。
 * 真实脚本/emoji/像素验收仍由独立真实FT/HB场景完成，不能用此探针代替。
 */
public final class CoreFontEngineScenario extends TestApplication {

    private static final class ContractEngine implements FontEngine {
        int opened, closed;

        @Override
        public FontSession openSession() {
            opened++;
            return new FontSession() {
                final Thread thread = Thread.currentThread();
                boolean done;

                @Override
                public FontFace openFace(ByteBuffer source, long index, float em) {
                    require(Thread.currentThread() == thread && !done, "session线程/关闭合同");
                    return new FontFace() {
                        @Override
                        public FontMetrics metrics() {
                            return new FontMetrics(em * .75f, em * .25f, em, em);
                        }

                        @Override
                        public int glyphIndex(int cp) {
                            return cp;
                        }

                        @Override
                        public ShapeRun shape(
                                String text,
                                int start,
                                int end,
                                ShapeDirection direction,
                                String language,
                                String script,
                                boolean beginning,
                                boolean ending) {
                            List<ShapedGlyph> glyphs = new ArrayList<ShapedGlyph>();
                            for (int i = start; i < end; ) {
                                int cp = text.codePointAt(i);
                                glyphs.add(new ShapedGlyph(cp, i, em * .5f, 0, 0, 0));
                                i += Character.charCount(cp);
                            }
                            if (direction == ShapeDirection.RTL) Collections.reverse(glyphs);
                            return new ShapeRun(glyphs);
                        }

                        @Override
                        public GlyphBitmap rasterize(int glyph, GlyphRasterStyle style) {
                            return new GlyphBitmap(
                                            1,
                                            1,
                                            0,
                                            em * .75f,
                                            em,
                                            false,
                                            new byte[] {-1, -1, -1, -1})
                                    .styled(style);
                        }

                        @Override
                        public InkBounds glyphInkBounds(int glyph, GlyphRasterStyle style) {
                            return new InkBounds(true, 0, -em * .75f, em * .5f, 0);
                        }

                        @Override
                        public void close() {
                            require(Thread.currentThread() == thread, "face关闭线程");
                        }
                    };
                }

                @Override
                public void close() {
                    require(Thread.currentThread() == thread, "session关闭线程");
                    if (!done) {
                        done = true;
                        closed++;
                    }
                }
            };
        }
    }

    public static void main(String[] args) throws Exception {
        new CoreFontEngineScenario().run(args);
    }

    @Override
    protected void runTests() throws Exception {
        scenario(
                "纯核心类路径隔离",
                () -> {
                    for (String forbidden :
                            new String[] {
                                "org.lwjgl.Sys",
                                "org.lwjgl.system.MemoryUtil",
                                "net.minecraft.client.Minecraft",
                                "cpw.mods.fml.common.Mod"
                            }) {
                        boolean absent = false;
                        try {
                            Class.forName(forbidden);
                        } catch (ClassNotFoundException expected) {
                            absent = true;
                        }
                        require(absent, "classpath不纯: " + forbidden);
                    }
                });
        scenario(
                "不可变位图所有权",
                () -> {
                    byte[] rgba = {1, 2, 3, 4};
                    GlyphBitmap bitmap = new GlyphBitmap(1, 1, 2, 3, .5f, true, rgba);
                    rgba[0] = 9;
                    require(
                            bitmap.pixels()[0] == 1 && bitmap.pixelsBuffer().isReadOnly(),
                            "pure bitmap所有权");
                    byte[] clone = bitmap.pixels();
                    clone[0] = 7;
                    require(bitmap.pixels()[0] == 1, "pure bitmap泄露可变像素");
                });
        scenario(
                "注入engine布局采样及session生命周期",
                () -> {
                    ContractEngine engine = new ContractEngine();
                    FontSource source =
                            FontSource.file(
                                    "contract-probe",
                                    1,
                                    java.nio.file.Paths.get(
                                            "src/core/resources/assets/qz_uilib/fonts/unifont/unifont-16.0.04.otf"),
                                    0,
                                    false,
                                    Collections.<String>emptySet());
                    require(engine.opened == 0, "engine工厂构造触发native/session");
                    try (FontTextService text =
                            new FontTextService(
                                    Collections.singletonList(source), engine, 2, 4, 128)) {
                        TextLayout logical =
                                text.layout(
                                        "AB中",
                                        new TextStyle("probe", 12, -1),
                                        1000,
                                        TextPolicy.SINGLE_LINE);
                        TextLayout sampled = text.sample(logical, new RasterSampling(2, 1));
                        require(
                                logical.advanceWidth == 18
                                        && sampled.advanceWidth == 18
                                        && sampled.lines().get(0).glyphs().get(0).size == 24,
                                "注入布局与采样逻辑流程");
                        require(
                                logical.lines().get(0).glyphs().get(2).source == source
                                        && logical.input.sourceStart(2) == 2,
                                "纯核心计划来源事实");
                    }
                    require(engine.opened == 1 && engine.closed == 1, "session资源生命周期");
                });
        System.out.println(
                "CoreFontEngineScenario PASS core+ICU only no MC/Forge/LWJGL; injected lifecycle/data/layout/sample contract (非真实字体能力证据)");
    }
}
