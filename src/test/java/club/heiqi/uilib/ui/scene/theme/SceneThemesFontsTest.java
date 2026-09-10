package club.heiqi.uilib.ui.scene.theme;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 排版度量派生入口（{@link SceneThemes#fontSize} / {@link SceneThemes#lineHeightMultiplier}）单测。
 *
 * <p>覆盖：构造期即可读初值（消费方在 builder 内取值播种，不依赖首次 flush）；主题切换同时驱动
 * 字号与行距；局部主题覆盖 runtime 默认且退出后不残留；两个 runtime 各自 install 互不可见；
 * 同一主题内各槽位互不干扰、未覆盖槽保持默认。</p>
 *
 * <p>本入口是契约 §4「排版度量」行的唯一通道：主题可经它接管字号与行距，但 padding 与尺寸等
 * 几何布局属性仍不进主题通道。</p>
 */
public class SceneThemesFontsTest {

    private static final SceneTheme.FontSlot SLOT = SceneTheme.FontSlot.TITLE;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    /** 深色档派生：只改标题字号与行距，其余字段随默认档。 */
    private static SceneTheme scaled(int titlePx, double lineHeight) {
        return SceneTheme.liquidGlassDark().toBuilder()
                .fontSize(SLOT, titlePx)
                .lineHeightMultiplier(lineHeight)
                .build();
    }

    /** 一次构造捕获的两个派生信号。 */
    private static final class Probe {
        private ReadableSignal<Integer> title;
        private ReadableSignal<Double> lineHeight;
    }

    /** 在 mount builder（构造期）内捕获；{@code scoped} 为 null 时不加局部主题。 */
    private static Probe capture(SceneRuntime rt, ReadableSignal<SceneTheme> scoped) {
        final Probe[] holder = new Probe[1];
        rt.mount(new SceneNode(), () -> {
            Runnable build = () -> {
                Probe probe = new Probe();
                probe.title = SceneThemes.fontSize(rt, SLOT);
                probe.lineHeight = SceneThemes.lineHeightMultiplier(rt);
                holder[0] = probe;
            };
            if (scoped == null) {
                build.run();
            } else {
                SceneThemes.withTheme(scoped, build);
            }
            return new SceneNode();
        });
        return holder[0];
    }

    /** ① 构造期初值来自来源主题，无需 flush——消费方在 builder 内 {@code get()} 播种即可。 */
    @Test
    public void constructionInitialValueComesFromThemeWithoutFlush() {
        SceneRuntime rt = new SceneRuntime();
        Probe probe = capture(rt, Signal.create(scaled(32, 1.4D)));

        Assert.assertEquals("构造期即可读字号初值", 32, probe.title.get().intValue());
        Assert.assertEquals("构造期即可读行距初值", 1.4D, probe.lineHeight.get().doubleValue(), 0.0D);
    }

    /** ② 未安装主题时回落库默认档：字号取默认七档值，行距为「不接管」。 */
    @Test
    public void fallsBackToLibraryDefaultWhenNothingInstalled() {
        SceneRuntime rt = new SceneRuntime();
        Probe probe = capture(rt, null);

        Assert.assertEquals(24, probe.title.get().intValue());
        Assert.assertEquals(0.0D, probe.lineHeight.get().doubleValue(), 0.0D);
    }

    /** ③ 主题切换同时驱动字号与行距；两档值互异是前提断言，避免假通过。 */
    @Test
    public void themeSwitchRecomputesFontSizeAndLineHeight() {
        SceneRuntime rt = new SceneRuntime();
        Signal<SceneTheme> page = Signal.create(scaled(24, 0.0D));
        Probe probe = capture(rt, page);

        Assert.assertEquals("切换前：字号", 24, probe.title.get().intValue());
        Assert.assertEquals("切换前：行距", 0.0D, probe.lineHeight.get().doubleValue(), 0.0D);

        page.set(scaled(40, 1.25D));
        rt.flush();

        Assert.assertEquals("切换后：字号", 40, probe.title.get().intValue());
        Assert.assertEquals("切换后：行距", 1.25D, probe.lineHeight.get().doubleValue(), 0.0D);
    }

    /** ④ 局部主题覆盖 runtime 默认；作用域退出后 runtime 默认档不受影响。 */
    @Test
    public void localThemeOverridesRuntimeDefaultWithoutLeakingBack() {
        SceneRuntime rt = new SceneRuntime();
        SceneThemes.install(rt, Signal.create(scaled(20, 0.0D)));
        Assert.assertEquals("runtime 默认档生效", 20, capture(rt, null).title.get().intValue());

        final int[] scoped = new int[1];
        final double[] scopedLine = new double[1];
        rt.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(scaled(44, 2.0D)), () -> {
                scoped[0] = SceneThemes.fontSize(rt, SLOT).get().intValue();
                scopedLine[0] = SceneThemes.lineHeightMultiplier(rt).get().doubleValue();
            });
            return new SceneNode();
        });

        Assert.assertEquals("局部主题覆盖字号", 44, scoped[0]);
        Assert.assertEquals("局部主题覆盖行距", 2.0D, scopedLine[0], 0.0D);
        Assert.assertEquals("作用域退出后 runtime 默认未被改写", 20, capture(rt, null).title.get().intValue());
    }

    /** ⑤ 两个 runtime 各自 install 互不可见（作用域槽挂在各自 rootOwner 上）。 */
    @Test
    public void runtimesAreIsolated() {
        SceneRuntime first = new SceneRuntime();
        SceneRuntime second = new SceneRuntime();
        SceneThemes.install(first, Signal.create(scaled(20, 0.0D)));
        SceneThemes.install(second, Signal.create(scaled(28, 1.5D)));

        Assert.assertEquals("runtime A 取自己的默认档", 20, capture(first, null).title.get().intValue());
        Assert.assertEquals("runtime B 取自己的默认档", 28, capture(second, null).title.get().intValue());
        Assert.assertEquals("runtime B 的行距也不串档",
                1.5D, capture(second, null).lineHeight.get().doubleValue(), 0.0D);
    }

    /** ⑥ 同主题内各槽位互相独立：改动只落在被覆盖的槽，未覆盖槽保持默认。 */
    @Test
    public void slotsAreResolvedIndependently() {
        SceneTheme theme = SceneTheme.liquidGlassDark().toBuilder()
                .fontSize(SceneTheme.FontSlot.TITLE, 30)
                .fontSize(SceneTheme.FontSlot.BASE, 11)
                .build();
        SceneRuntime rt = new SceneRuntime();
        final int[][] holder = new int[1][];

        rt.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(theme), () -> holder[0] = new int[] {
                SceneThemes.fontSize(rt, SceneTheme.FontSlot.TITLE).get().intValue(),
                SceneThemes.fontSize(rt, SceneTheme.FontSlot.BASE).get().intValue(),
                SceneThemes.fontSize(rt, SceneTheme.FontSlot.CAPTION).get().intValue(),
                SceneThemes.fontSize(rt, SceneTheme.FontSlot.HELPER).get().intValue(),
            });
            return new SceneNode();
        });

        Assert.assertEquals("被覆盖槽：TITLE", 30, holder[0][0]);
        Assert.assertEquals("被覆盖槽：BASE", 11, holder[0][1]);
        Assert.assertEquals("未覆盖槽保持默认：CAPTION", 12, holder[0][2]);
        Assert.assertEquals("未覆盖槽保持默认：HELPER", 13, holder[0][3]);
    }
}
