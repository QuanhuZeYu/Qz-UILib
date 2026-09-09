package club.heiqi.uilib.ui.scene.theme;

import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 主题作用域契约测试（规划第 4.3 节：延迟构建与弹层）。
 *
 * <p>覆盖：库默认回落、runtime 默认安装、两个 runtime 隔离、局部/嵌套覆盖、
 * mount/show/forEach/portal 四条构建路径的继承、主题更新驱动配方重算、
 * 卸载后作用域不可见。</p>
 */
public class SceneThemeScopeTest {

    /** 测试用可变持有器：避免泛型数组的 unchecked 警告。 */
    private static final class Holder<T> {
        private T value;
    }

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    private static SceneTheme theme(int accent) {
        return theme(accent, 12);
    }

    /**
     * 两个主题必须让 PANEL 配方本身不同：{@link club.heiqi.uilib.ui.reactive.Computed} 按值记忆化，
     * 只改 accent 时角色配方相等、输出不传播（这是期望行为，见
     * {@link #equalRoleRecipesDoNotPropagate}）。
     */
    private static SceneTheme theme(int accent, int panelRadius) {
        return SceneTheme.builder()
                .accent(accent)
                .surface(SceneTheme.Role.PANEL, SceneSurfaceStyle.builder().cornerRadius(panelRadius).build())
                .build();
    }

    @Test
    public void resolveFallsBackToLibraryDefaultWithoutInstall() {
        SceneRuntime rt = new SceneRuntime();
        Assert.assertSame("未安装时回落库默认主题",
                SceneThemes.DEFAULT, SceneThemes.resolve(rt).get());
    }

    @Test
    public void installProvidesRuntimeDefaultTheme() {
        SceneRuntime rt = new SceneRuntime();
        SceneTheme installed = theme(0xFF112233);
        SceneThemes.install(rt, Signal.create(installed));

        Assert.assertSame(installed, SceneThemes.resolve(rt).get());
    }

    @Test
    public void twoRuntimesKeepIndependentDefaults() {
        SceneRuntime left = new SceneRuntime();
        SceneRuntime right = new SceneRuntime();
        SceneTheme leftTheme = theme(0xFF112233);
        SceneTheme rightTheme = theme(0xFF445566);
        SceneThemes.install(left, Signal.create(leftTheme));
        SceneThemes.install(right, Signal.create(rightTheme));

        Assert.assertSame("左 runtime 用自己的默认主题", leftTheme, SceneThemes.resolve(left).get());
        Assert.assertSame("右 runtime 用自己的默认主题", rightTheme, SceneThemes.resolve(right).get());
    }

    @Test
    public void withThemeOverridesWithinScopeAndRestoresAfter() {
        SceneRuntime rt = new SceneRuntime();
        SceneTheme page = theme(0xFF112233);
        SceneNode root = new SceneNode();
        Holder<ReadableSignal<SceneTheme>> inside = new Holder<ReadableSignal<SceneTheme>>();
        Holder<ReadableSignal<SceneTheme>> outside = new Holder<ReadableSignal<SceneTheme>>();

        rt.mount(root, () -> {
            SceneThemes.withTheme(Signal.create(page), () -> inside.value = SceneThemes.resolve(rt));
            outside.value = SceneThemes.resolve(rt);
            return new SceneNode();
        });

        Assert.assertSame("作用域内解析到页面主题", page, inside.value.get());
        Assert.assertSame("作用域外回落库默认", SceneThemes.DEFAULT, outside.value.get());
    }

    @Test
    public void nestedWithThemeNarrowsThenRestores() {
        SceneRuntime rt = new SceneRuntime();
        SceneTheme outer = theme(0xFF112233);
        SceneTheme inner = theme(0xFF445566);
        SceneNode root = new SceneNode();
        Holder<ReadableSignal<SceneTheme>> innerHolder = new Holder<ReadableSignal<SceneTheme>>();
        Holder<ReadableSignal<SceneTheme>> outerHolder = new Holder<ReadableSignal<SceneTheme>>();

        rt.mount(root, () -> {
            SceneThemes.withTheme(Signal.create(outer), () -> {
                SceneThemes.withTheme(Signal.create(inner), () -> innerHolder.value = SceneThemes.resolve(rt));
                outerHolder.value = SceneThemes.resolve(rt);
            });
            return new SceneNode();
        });

        Assert.assertSame("内层覆盖外层", inner, innerHolder.value.get());
        Assert.assertSame("退出内层恢复外层", outer, outerHolder.value.get());
    }

    @Test
    public void themeIsInheritedByShowContentBuilder() {
        SceneRuntime rt = new SceneRuntime();
        SceneTheme page = theme(0xFF112233);
        SceneNode root = new SceneNode();
        Holder<ReadableSignal<SceneTheme>> captured = new Holder<ReadableSignal<SceneTheme>>();

        rt.mount(root, () -> {
            SceneNode host = new SceneNode();
            SceneThemes.withTheme(Signal.create(page), () -> {
                Signal<Boolean> visible = Signal.create(Boolean.FALSE);
                rt.show(host, visible, () -> {
                    captured.value = SceneThemes.resolve(rt);
                    return new SceneNode();
                });
                visible.set(Boolean.TRUE);
            });
            return host;
        });
        rt.flush();

        Assert.assertNotNull("show 内容应在 flush 时构建", captured.value);
        Assert.assertSame("show 延迟内容继承来源主题", page, captured.value.get());
    }

    @Test
    public void themeIsInheritedByForEachItemBuilder() {
        SceneRuntime rt = new SceneRuntime();
        SceneTheme page = theme(0xFF112233);
        SceneNode root = new SceneNode();
        Holder<ReadableSignal<SceneTheme>> captured = new Holder<ReadableSignal<SceneTheme>>();

        rt.mount(root, () -> {
            SceneNode host = new SceneNode();
            SceneThemes.withTheme(Signal.create(page), () -> {
                Signal<List<String>> items = Signal.create(Collections.singletonList("only"));
                rt.forEach(host, items, item -> {
                    captured.value = SceneThemes.resolve(rt);
                    return new SceneNode();
                });
            });
            return host;
        });
        rt.flush();

        Assert.assertNotNull("forEach 项应在 flush 时构建", captured.value);
        Assert.assertSame("forEach 列表项继承来源主题", page, captured.value.get());
    }

    @Test
    public void themeIsInheritedByPortalContentBuilder() {
        SceneRuntime rt = new SceneRuntime();
        SceneTheme page = theme(0xFF112233);
        SceneNode root = new SceneNode();
        Holder<ReadableSignal<SceneTheme>> captured = new Holder<ReadableSignal<SceneTheme>>();

        rt.mount(root, () -> {
            SceneThemes.withTheme(Signal.create(page), () -> {
                Signal<Boolean> open = Signal.create(Boolean.TRUE);
                rt.portal(open, () -> {
                    captured.value = SceneThemes.resolve(rt);
                    return new SceneNode();
                });
            });
            return new SceneNode();
        });
        rt.flush();

        Assert.assertNotNull("portal 内容应在 flush 时构建", captured.value);
        Assert.assertSame("浮层内容继承来源主题", page, captured.value.get());
    }

    @Test
    public void surfaceFollowsThemeUpdatesWithoutRebuild() {
        SceneRuntime rt = new SceneRuntime();
        SceneTheme first = theme(0xFF112233, 12);
        SceneTheme second = theme(0xFF445566, 24);
        Signal<SceneTheme> page = Signal.create(first);
        SceneNode root = new SceneNode();
        Holder<ReadableSignal<SceneSurfaceStyle>> captured = new Holder<ReadableSignal<SceneSurfaceStyle>>();

        rt.mount(root, () -> {
            SceneThemes.withTheme(page, () ->
                    captured.value = SceneThemes.surface(rt, SceneTheme.Role.PANEL));
            return new SceneNode();
        });

        Assert.assertSame("构造期初值来自来源主题",
                first.surface(SceneTheme.Role.PANEL), captured.value.get());

        page.set(second);
        rt.flush();

        Assert.assertSame("主题更新后配方重算", second.surface(SceneTheme.Role.PANEL), captured.value.get());
        Assert.assertEquals("重算后的配方取新值", 24, captured.value.get().getCornerRadius());
    }

    @Test
    public void equalRoleRecipesDoNotPropagate() {
        SceneRuntime rt = new SceneRuntime();
        SceneTheme first = theme(0xFF112233, 12);
        SceneTheme second = theme(0xFF445566, 12);
        Signal<SceneTheme> page = Signal.create(first);
        SceneNode root = new SceneNode();
        Holder<ReadableSignal<SceneSurfaceStyle>> captured = new Holder<ReadableSignal<SceneSurfaceStyle>>();

        rt.mount(root, () -> {
            SceneThemes.withTheme(page, () ->
                    captured.value = SceneThemes.surface(rt, SceneTheme.Role.PANEL));
            return new SceneNode();
        });

        page.set(second);
        rt.flush();

        Assert.assertSame("角色配方值相等时下游不重算（记忆化去重，避免无谓外观写入）",
                first.surface(SceneTheme.Role.PANEL), captured.value.get());
    }

    @Test
    public void disposedScopeStopsResolvingLocalTheme() {
        SceneRuntime rt = new SceneRuntime();
        SceneTheme page = theme(0xFF112233);
        SceneNode root = new SceneNode();
        Holder<ReadableSignal<SceneTheme>> afterDispose = new Holder<ReadableSignal<SceneTheme>>();

        MountHandle handle = rt.mount(root, () -> {
            SceneThemes.withTheme(Signal.create(page), () -> {
                // 记录作用域内解析到的来源，卸载后再从 runtime 根视角解析。
                SceneThemes.resolve(rt);
            });
            return new SceneNode();
        });
        handle.dispose();
        afterDispose.value = SceneThemes.resolve(rt);

        Assert.assertSame("页面卸载后回落库默认", SceneThemes.DEFAULT, afterDispose.value.get());
    }

    @Test
    public void installAfterMountDoesNotRetargetAlreadyCapturedSurface() {
        SceneRuntime rt = new SceneRuntime();
        SceneNode root = new SceneNode();
        Holder<ReadableSignal<SceneSurfaceStyle>> captured = new Holder<ReadableSignal<SceneSurfaceStyle>>();

        rt.mount(root, () -> {
            captured.value = SceneThemes.surface(rt, SceneTheme.Role.PANEL);
            return new SceneNode();
        });
        SceneThemes.install(rt, Signal.create(theme(0xFF112233)));
        rt.flush();

        Assert.assertSame("构造期已捕获库默认的派生不因事后安装而改指向",
                SceneThemes.DEFAULT.surface(SceneTheme.Role.PANEL), captured.value.get());
    }
}
