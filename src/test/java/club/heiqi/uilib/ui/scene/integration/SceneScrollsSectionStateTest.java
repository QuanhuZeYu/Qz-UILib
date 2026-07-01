package club.heiqi.uilib.ui.scene.integration;

import java.util.function.Consumer;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * SceneScrolls 第二 attach 形态（调用方自管 per-section scroll state）集成测试 ——
 * 验证切换 section 后各自 scrollOffset 独立保持。
 *
 * <p>wiring 模式（调用方负责）：持有每个 section 独立的 {@code Signal<Integer>} offset，
 * 用 {@link Computed} 派生「当前 active section 的 offset」作为显示源接入 attach，
 * 写入回调按 active section 路由到对应 signal。切换 section 即切换 active 标识，
 * 显示源重算、写入回调重路由，两个 section 的 scroll state 互不污染。</p>
 *
 * <p>归类 L3 集成层：依赖 reactive（Signal/Computed）+ runtime（bind/flush）+ input（滚轮 route）
 * + layout（maxScrollY 依赖 viewport LayoutBox）多子系统协作。</p>
 *
 * <p>守 I1 signal-first（scroll state 全程经 signal 驱动，不命令式写 viewport）、
 * I7 GEOMETRY 级滚动（viewport scrollable，滚动不重排）。</p>
 */
public class SceneScrollsSectionStateTest {

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    /** 语义化交互注入 harness（route 根 + scroll 入口）；其 runtime 即上方 runtime 字段 */
    private SceneInteractionHarness harness;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        sceneRoot = new SceneNode();
        // 挂载路由根并对齐 layout，供 harness.scroll 取中心 + route
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @After
    public void tearDown() {
        harness.dispose();
        ReactiveScheduler.get().reset();
    }

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /**
     * 构建可滚动视口（preferredHeight=200）+ 高内容（600），maxScroll=400。
     *
     * @return 滚动视口节点
     */
    private SceneNode buildScrollableViewport() {
        SceneNode viewport = new SceneNode();
        viewport.setScrollable(true);
        viewport.setPreferredHeight(200);
        sceneRoot.appendChild(viewport);
        SceneNode content = new SceneNode();
        content.setPreferredHeight(600);
        viewport.appendChild(content);
        doLayout();
        return viewport;
    }

    /**
     * 完整流程：section A 滚到 100 → 切 B（B 仍 0）→ B 滚到 50 → 切回 A（A 恢复 100），
     * 断言两个 section 的 scrollOffset 互不污染、独立保持。
     */
    @Test
    public void perSectionOffsetsShouldSurviveSectionSwitch() {
        SceneNode viewport = buildScrollableViewport();

        // 两个 section 独立的 scroll state
        Signal<Integer> sectionAOffset = Signal.create(Integer.valueOf(0));
        Signal<Integer> sectionBOffset = Signal.create(Integer.valueOf(0));
        Signal<String> activeSection = Signal.create("A");

        // 显示源：派生当前 active section 的 offset
        ReadableSignal<Integer> display = Computed.create(() ->
                "A".equals(activeSection.get()) ? sectionAOffset.get() : sectionBOffset.get());
        // 写入回调：按 active section 路由
        Consumer<Integer> writer = v -> {
            if ("A".equals(activeSection.get())) {
                sectionAOffset.set(v);
            } else {
                sectionBOffset.set(v);
            }
        };

        SceneScrolls.attach(runtime, viewport, display, writer);
        runtime.flush(); // bind 首次物化 setScrollOffsetY=0

        // 1. A active，向下滚 wheelDelta=-100 → offset 增到 100
        harness.scroll(viewport, -100);
        runtime.flush();
        Assert.assertEquals("section A 滚动后 offset=100", 100, sectionAOffset.get().intValue());
        Assert.assertEquals("section B 不受 A 滚动影响 offset=0", 0, sectionBOffset.get().intValue());

        // 2. 切换 active=B：display 重算为 BOffset=0，viewport 物化为 0
        activeSection.set("B");
        runtime.flush();
        Assert.assertEquals("切换后 section B offset 仍为 0（B 未滚过）", 0, sectionBOffset.get().intValue());
        Assert.assertEquals("切换后 viewport 物化 B 的 offset=0", 0, viewport.getScrollOffsetY());

        // 3. B active，向下滚 wheelDelta=-50 → BOffset=50
        harness.scroll(viewport, -50);
        runtime.flush();
        Assert.assertEquals("section B 滚动后 offset=50", 50, sectionBOffset.get().intValue());
        Assert.assertEquals("section A 保持原值 100（per-section 独立保持）", 100, sectionAOffset.get().intValue());

        // 4. 切回 active=A：display 重算为 AOffset=100，viewport 物化为 100（A 状态恢复）
        activeSection.set("A");
        runtime.flush();
        Assert.assertEquals("切回 A 后 section A offset 恢复 100", 100, sectionAOffset.get().intValue());
        Assert.assertEquals("切回 A 后 viewport 物化 A 的 offset=100", 100, viewport.getScrollOffsetY());
        Assert.assertEquals("section B 保持 50（切回 A 不影响 B）", 50, sectionBOffset.get().intValue());
    }

    /**
     * 切换 active section 时 viewport.setScrollOffsetY 跟随新 active section 的 offset，
     * 验证 display Computed → bind → setScrollOffsetY 链路在 section 切换时正确重算物化。
     *
     * <p>预置：A 滚到 200、B 滚到 80（通过直接 set signal 模拟已滚动态），
     * 切换 active 后 viewport 物化值应等于目标 section 的 offset。</p>
     */
    @Test
    public void viewportShouldMaterializeActiveSectionOffsetOnSwitch() {
        SceneNode viewport = buildScrollableViewport();

        Signal<Integer> sectionAOffset = Signal.create(Integer.valueOf(200));
        Signal<Integer> sectionBOffset = Signal.create(Integer.valueOf(80));
        Signal<String> activeSection = Signal.create("A");

        ReadableSignal<Integer> display = Computed.create(() ->
                "A".equals(activeSection.get()) ? sectionAOffset.get() : sectionBOffset.get());
        Consumer<Integer> writer = v -> { /* 测试不触发滚轮，writer 不被调用 */ };

        SceneScrolls.attach(runtime, viewport, display, writer);
        runtime.flush();
        Assert.assertEquals("初始 active=A 时 viewport 物化 A 的 offset=200",
                200, viewport.getScrollOffsetY());

        activeSection.set("B");
        runtime.flush();
        Assert.assertEquals("切到 B 后 viewport 物化 B 的 offset=80",
                80, viewport.getScrollOffsetY());

        activeSection.set("A");
        runtime.flush();
        Assert.assertEquals("切回 A 后 viewport 物化 A 的 offset=200",
                200, viewport.getScrollOffsetY());
    }
}
