package club.heiqi.uilib.ui.scene.theme;

import club.heiqi.uilib.ui.scene.testkit.SceneTestEnvironments;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.BackdropQualityService;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 库默认主题按背景滤镜档位（{@code general.backdropQuality}）动态解析的回归锁。
 *
 * <p>覆盖 A2 的 headless 判据：{@code solid} 档（关闭滤镜）下<b>已构建页面</b>在不重建节点的
 * 前提下，同一配方信号自动重派生为 {@link SceneTheme#withoutBackdrop()} 的实色无滤镜替代；
 * 切回 {@code full} 恢复液态玻璃；runtime 显式安装的主题优先级不受档位影响；
 * 显式 {@code SceneThemes.DEFAULT} 常量语义不变（已知边界，见设计文档）。</p>
 *
 * <p>进程级档位信号是全局状态：{@code setUp/tearDown} 双向复位为 {@code full}（= 现状档），
 * 避免漂到后续 UI 测试。</p>
 */
public class SceneThemesBackdropQualityTest {

    private static final SceneTheme.Role ROLE = SceneTheme.Role.PANEL;

    private final List<SceneRuntime> runtimes = new ArrayList<SceneRuntime>();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        applyQuality("full");
    }

    @After
    public void tearDown() {
        for (SceneRuntime rt : runtimes) {
            rt.dispose();
        }
        runtimes.clear();
        applyQuality("full");
        ReactiveScheduler.get().reset();
    }

    /** 写进程级档位信号并等帧末 flush（档位生效同一口径）。 */
    private static void applyQuality(String raw) {
        BackdropQualityService.getInstance().applyConfigured(raw);
        ReactiveScheduler.get().flush();
    }

    /** 一个已构建页面：挂载表面节点 + 捕获库默认主题下的角色配方信号。 */
    private static final class Page {
        final SceneRuntime rt = SceneTestEnvironments.runtime();
        final SceneNode root = SceneNode.column();
        SceneNode surface;
        ReadableSignal<SceneSurfaceStyle> style;
        MountHandle handle;
    }

    @SuppressWarnings("unchecked")
    private Page mountPage() {
        final Page page = new Page();
        runtimes.add(page.rt);
        final SceneNode[] surface = new SceneNode[1];
        final ReadableSignal<SceneSurfaceStyle>[] style = new ReadableSignal[1];
        page.handle = page.rt.mount(page.root, () -> {
            SceneNode node = new SceneNode();
            node.setPreferredWidth(80).setPreferredHeight(32);
            style[0] = SceneThemes.surface(page.rt, ROLE);
            surface[0] = node;
            return node;
        });
        page.surface = surface[0];
        page.style = style[0];
        page.rt.flush();
        return page;
    }

    /** ① full 档：库默认配方 = 显式库默认主题的角色配方（带液态玻璃滤镜）。 */
    @Test
    public void fullQualityKeepsLibraryDefaultLiquidGlass() {
        Page page = mountPage();

        Assert.assertNotNull("full 档：库默认配方带液态玻璃滤镜", page.style.get().getBackdrop());
        Assert.assertEquals("full 档：等于显式库默认主题的同角色配方",
                SceneThemes.DEFAULT.surface(ROLE), page.style.get());
    }

    /** ② 切 solid：同一节点、同一信号、不新增订阅地重派生为无滤镜实色配方。 */
    @Test
    public void solidSwitchReDerivesToSolidRecipeOnSameNodeWithoutRebuild() {
        Page page = mountPage();
        SceneNode identity = page.surface;
        int childrenBefore = page.root.__getChildren().size();
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        applyQuality("solid");
        page.rt.flush();

        Assert.assertSame("切档不重建节点：同一实例仍挂在原父节点下",
                identity, page.root.__getChildren().get(0));
        Assert.assertEquals("切档不重建节点：子节点数不变", childrenBefore, page.root.__getChildren().size());
        Assert.assertNull("solid 档：同一配方信号重派生为无滤镜配方", page.style.get().getBackdrop());
        Assert.assertEquals("solid 档：等于 withoutBackdrop 的同角色配方",
                SceneThemes.DEFAULT.withoutBackdrop().surface(ROLE), page.style.get());
        Assert.assertEquals("切档只重派生，不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    /** ③ 切回 full：同一信号恢复液态玻璃（档位是双向热更，不是一次性降级）。 */
    @Test
    public void backToFullRestoresLiquidGlassOnSameSignal() {
        Page page = mountPage();
        applyQuality("solid");
        page.rt.flush();
        Assert.assertNull("前置：solid 档已生效", page.style.get().getBackdrop());

        applyQuality("full");
        page.rt.flush();

        Assert.assertNotNull("切回 full：同一信号恢复液态玻璃", page.style.get().getBackdrop());
        Assert.assertEquals("切回 full：逐值回到显式库默认主题配方",
                SceneThemes.DEFAULT.surface(ROLE), page.style.get());
    }

    /** ④ 显式 SceneThemes.DEFAULT 常量不随档位变化；resolve 才按档位回落。 */
    @Test
    public void explicitDefaultConstantStaysLiquidGlassWhileQualitySolid() {
        applyQuality("solid");

        Assert.assertNotNull("显式 SceneThemes.DEFAULT 是语义锚，不随档位变化",
                SceneThemes.DEFAULT.surface(ROLE).getBackdrop());

        SceneRuntime rt = SceneTestEnvironments.runtime();
        runtimes.add(rt);
        Assert.assertEquals("solid 档 + 未 install：resolve 回落档位感知默认（实色无滤镜）",
                SceneThemes.DEFAULT.withoutBackdrop(), SceneThemes.resolve(rt).get());

        applyQuality("full");
        Assert.assertSame("full 档 + 未 install：resolve 回落显式库默认实例",
                SceneThemes.DEFAULT, SceneThemes.resolve(rt).get());
    }

    /** ⑤ 主题优先级不变：runtime 显式安装的主题压过库默认的档位回落。 */
    @Test
    public void explicitlyInstalledRuntimeThemeWinsOverQualityFallback() {
        SceneRuntime rt = SceneTestEnvironments.runtime();
        runtimes.add(rt);
        SceneTheme explicit = SceneTheme.liquidGlassLight();
        SceneThemes.install(rt, Signal.create(explicit));

        applyQuality("solid");

        Assert.assertSame("显式运行时主题优先于库默认的档位回落",
                explicit, SceneThemes.resolve(rt).get());
    }
}
