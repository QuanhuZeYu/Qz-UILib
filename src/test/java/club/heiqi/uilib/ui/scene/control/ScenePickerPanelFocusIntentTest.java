package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.testkit.SceneTestEnvironments;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel.FocusIntent;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 面板焦点意图消费的双依赖回归锁。
 *
 * <p>契约：消费条件 = 「意图 ≠ NONE」<b>且</b>「目标已就绪」。历史形态把目标放在单元素
 * {@code SceneNode[]} 里且只绑意图信号，于是"意图早于目标就绪"时本次打开会永久丢焦点
 * （意图既没消费也没清 NONE，而此后目标写入不触发任何 effect）。</p>
 *
 * <p>本类通过包内接缝直接驱动 {@link ScenePickerPanel#bindFocusIntent} 与三个目标就绪信号，
 * 覆盖四条语义：</p>
 * <ol>
 *   <li>目标晚到 → 自动补一次消费（本回归的唯一目标，历史实现必红）；</li>
 *   <li>意图为 NONE 时目标到达/变化不得抢焦点（防加固引入"目标一到就聚焦"的新副作用）；</li>
 *   <li>常规顺序（目标先就绪、意图后到）保持既有行为；</li>
 *   <li>消费后意图回 NONE，重复 flush 幂等（焦点不被反复改写）。</li>
 * </ol>
 */
public class ScenePickerPanelFocusIntentTest {

    private SceneRuntime rt;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        rt = SceneTestEnvironments.runtime(new FixedTextMeasurer());
    }

    @After
    public void tearDown() {
        if (rt != null) {
            rt.dispose();
        }
        ReactiveScheduler.get().reset();
    }

    /** 夹具：意图 + 三个目标就绪信号（全部一开始未就绪）。 */
    private static final class Targets {
        final Signal<FocusIntent> intent = Signal.create(FocusIntent.NONE);
        final Signal<SceneNode> search = Signal.create(null);
        final Signal<SceneNode> grid = Signal.create(null);
        final Signal<SceneNode> variants = Signal.create(null);
    }

    @Test
    public void lateReadyTargetStillConsumesPendingFocusIntent() {
        Targets t = new Targets();
        ScenePickerPanel.bindFocusIntent(rt, t.intent, t.search, t.grid, t.variants);
        SceneNode input = new SceneNode();

        // ① 意图先到，目标仍未就绪：不消费、也不清意图（等目标到达再估）
        t.intent.set(FocusIntent.SEARCH_INPUT);
        rt.flush();
        Assert.assertNull("目标未就绪时不得产生焦点", rt.getFocusedNode());
        Assert.assertEquals("目标未就绪时意图必须保留待消费", FocusIntent.SEARCH_INPUT, t.intent.get());

        // ② 目标晚到：双依赖派生重估，自动补一次消费（历史数组形态在此永久丢焦点）
        t.search.set(input);
        rt.flush();
        Assert.assertSame("目标晚到必须自动补消费", input, rt.getFocusedNode());
        Assert.assertEquals("消费后意图回到 NONE", FocusIntent.NONE, t.intent.get());
    }

    @Test
    public void readyTargetWithoutIntentNeverStealsFocus() {
        Targets t = new Targets();
        ScenePickerPanel.bindFocusIntent(rt, t.intent, t.search, t.grid, t.variants);
        SceneNode input = new SceneNode();
        SceneNode other = new SceneNode();

        // 意图恒为 NONE：目标就绪与后续替换都不得引发任何聚焦
        t.search.set(input);
        rt.flush();
        Assert.assertNull("意图为 NONE 时目标就绪不得抢焦点", rt.getFocusedNode());

        t.search.set(other);
        t.grid.set(new SceneNode());
        rt.flush();
        Assert.assertNull("意图为 NONE 时目标替换同样不得抢焦点", rt.getFocusedNode());
        Assert.assertEquals(FocusIntent.NONE, t.intent.get());
    }

    @Test
    public void intentArrivingAfterReadyTargetFocusesAsBefore() {
        Targets t = new Targets();
        ScenePickerPanel.bindFocusIntent(rt, t.intent, t.search, t.grid, t.variants);
        SceneNode input = new SceneNode();
        t.search.set(input);
        rt.flush();

        // 常规顺序（面板打开边沿的现行时序）：目标已就绪，意图后到 ⇒ 立即消费
        t.intent.set(FocusIntent.SEARCH_INPUT);
        rt.flush();
        Assert.assertSame(input, rt.getFocusedNode());
        Assert.assertEquals(FocusIntent.NONE, t.intent.get());
    }

    @Test
    public void consumptionIsIdempotentAcrossRepeatedFlushes() {
        Targets t = new Targets();
        ScenePickerPanel.bindFocusIntent(rt, t.intent, t.search, t.grid, t.variants);
        SceneNode input = new SceneNode();
        SceneNode gridViewport = new SceneNode();
        t.search.set(input);
        t.grid.set(gridViewport);
        t.intent.set(FocusIntent.SEARCH_INPUT);
        rt.flush();
        Assert.assertSame(input, rt.getFocusedNode());

        // 消费后：无关目标变化 / 空 flush 都不得改写焦点
        t.grid.set(new SceneNode());
        rt.flush();
        rt.flush();
        Assert.assertSame("消费后焦点不得被无关信号变化改写", input, rt.getFocusedNode());

        // 意图再次置位（↓ 进网格）：按新意图重新消费
        t.intent.set(FocusIntent.GRID);
        rt.flush();
        Assert.assertSame("新意图必须消费到对应目标", t.grid.get(), rt.getFocusedNode());
        Assert.assertEquals(FocusIntent.NONE, t.intent.get());
    }
}
