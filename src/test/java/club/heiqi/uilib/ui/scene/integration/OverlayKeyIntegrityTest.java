package club.heiqi.uilib.ui.scene.integration;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.control.SceneToast;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * 浮层 keyed 列表完整性回归测试。
 *
 * <p>真机崩溃回归（crash-2026-08-18_14.02.57「forEach 重复 key」）：SceneToast.tick
 * 曾用「remove 后按原索引 set」的错位写法，退场标记副本覆盖相邻条目，列表出现同 id 双份
 * （E19 原条目 + E19 leaving 副本、E20 被覆盖）→ forEach key 唯一性打破 → 崩溃。
 * 本类锚定修复后的不变量：任何 show/tick/开关交错下列表 key 永不重复。</p>
 *
 * <p>覆盖：① toast 高频 show/tick 交错（复现原崩溃路径）；② 多 runtime 开关循环（模拟反复
 * 开关测试场地）；③ TextArea 边界文本（emoji/连续空行/长行/尾换行）视觉行 key 唯一。</p>
 */
public class OverlayKeyIntegrityTest {

    private SceneNode sceneRoot;
    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private SceneInteractionHarness harness;

    private static final int CANVAS_WIDTH = 400;
    private static final int CANVAS_HEIGHT = 300;
    private static final int STUB_CHAR_WIDTH = 8;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(STUB_CHAR_WIDTH, 16);
        harness = SceneInteractionHarness.create(measurer);
        runtime = harness.getRuntime();
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        harness.mountRoot(sceneRoot, CANVAS_WIDTH, CANVAS_HEIGHT);
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    private void doLayout() {
        layoutEngine.layout(sceneRoot, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        }
    }

    private void tickAndFlush(long nanos) {
        runtime.__tickFrame(nanos);
        runtime.flush();
    }

    @Test
    public void toastInterleavedShowTickNeverDuplicatesKey() {
        // 高频交错：show 与 tick 同帧/跨帧混合（覆盖 read-modify-write 竞争）
        for (int i = 1; i <= 300; i++) {
            SceneToast.show(runtime, "m" + i, 40_000_000L + (i % 5) * 10_000_000L);
            runtime.flush();
            tickAndFlush((long) i * 30_000_000L);
            doLayout();
        }
        // 收敛：所有 toast 到期（第一段标记退场、第二段完成移除）
        tickAndFlush(60_000_000_000L);
        tickAndFlush(60_000_000_000L + SceneToast.LEAVE_DURATION_NANOS);
        Assert.assertEquals("全部退场", 0, runtime.getOverlayHost().size());
    }

    @Test
    public void toastSurvivesRuntimeOpenCloseCycles() {
        // 模拟反复开关测试场地：新 runtime → show/tick → dispose，循环 20 次
        for (int cycle = 0; cycle < 20; cycle++) {
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
            SceneNode root = new SceneNode();
            // 直接以该 runtime 投递（无需完整 host widget）
            for (int i = 0; i < 8; i++) {
                SceneToast.show(rt, "c" + cycle + "-" + i, 100_000_000L + i * 10_000_000L);
                rt.flush();
                rt.__tickFrame(1_000_000L * (cycle * 8 + i + 1));
                rt.flush();
            }
            rt.dispose();
        }
    }

    @Test
    public void textAreaBoundaryTextsNeverDuplicateVisualKey() {
        String[] samples = {
                "012345678901234567890123456789",                                   // 28 字符
                "test, modernconfig",
                "a\nb\nc",                                                       // 普通多行
                "\n\n\n",                                                       // 连续空行
                "行\n\n行\n\n\n尾",                                           // 空行混排
                "😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀😀",       // 28 个 emoji（代理对）
                "😀a😀b😀c\n😀d😀e😀f\n尾\n",                                  // emoji + 换行 + 尾换行
                "trailing\n",                                                     // 尾换行
                longWrapLine(),                                                     // 长行 wrap
        };
        for (String sample : samples) {
            // 每个样本独立 runtime：避免缓存干扰
            SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
            SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
            SceneNode root = new SceneNode();
            rt.flush();
            Signal<String> value = Signal.create(sample);
            MountHandle handle = rt.mount(root, SceneTextArea.create(rt, SceneTextArea.Props.builder(value)
                    .onChange(t -> { })
                    .maxLength(64)
                    .viewportHeight(80)
                    .build()));
            rt.flush();
            // 多次布局桥接 + 修改文本（触发 visualKeys 重算路径）
            for (int pass = 0; pass < 4; pass++) {
                engine.layout(root, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
                for (SceneOverlayHost.Entry entry : rt.getOverlayHost().bottomFirst()) {
                    engine.layout(entry.getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
                }
                rt.__setLayoutDoneEpoch(engine.layoutEpoch());
                rt.flush();
                value.set(sample + pass);
                rt.flush();
            }
            rt.dispose();
        }
    }

    /** 长行样本（约 100 字符，强制软换行）。 */
    private static String longWrapLine() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("word ");
        }
        return sb.toString().trim();
    }

    @Test
    public void textAreaRepeatInsertionsAroundVisualLine28() {
        // 模拟连续输入推进视觉行：逐步加长文本跨过 28 字符边界 + 换行切分
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer(STUB_CHAR_WIDTH, 16));
        SceneNode root = new SceneNode();
        Signal<String> value = Signal.create("");
        rt.mount(root, SceneTextArea.create(rt, SceneTextArea.Props.builder(value)
                .onChange(t -> { })
                .maxLength(512)
                .viewportHeight(80)
                .build()));
        rt.flush();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 120; i++) {
            sb.append((char) ('a' + (i % 26)));
            if (i % 7 == 0) {
                sb.append('\n');
            }
            value.set(sb.toString());
            rt.flush();
            engine.layout(root, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
            rt.__setLayoutDoneEpoch(engine.layoutEpoch());
            rt.flush();
        }
        rt.dispose();
    }
}