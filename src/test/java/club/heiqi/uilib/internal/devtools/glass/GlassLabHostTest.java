package club.heiqi.uilib.internal.devtools.glass;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend.RenderCall;

/**
 * {@link GlassLabHost} 的 headless 结构契约测试。
 *
 * <p>无 GL 环境（backend 非 UiRenderContext，UiRenderBackends 门面静默降级）下驱动
 * {@code render} 全帧，断言：主树渲染完成后玻璃叠加走 drawSurface tint 语义、
 * 采样场色带实际入绘、「暂停玻璃」冻结行为正确、路径诊断 signal 每帧刷新。
 * backdrop 滤镜本体（快照/shader/降级）属真机验收项，不在 headless 断言范围。</p>
 */
public class GlassLabHostTest {

    private static final int CANVAS_WIDTH = 1280;
    private static final int CANVAS_HEIGHT = 800;
    /** 玻璃面 tint（与 GlassLabHost.GLASS_TINT 同步的测试镜像常量）。 */
    private static final int GLASS_TINT = 0x26FFFFFF;
    /** drawSurface/fillRect 的填充色参数下标（l,t,r,b,fill,...）。 */
    private static final int FILL_COLOR_ARG = 4;

    private GlassLabHost host;
    private RecordingRenderBackend backend;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new GlassLabHost(new FixedTextMeasurer(), null);
        backend = new RecordingRenderBackend();
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 渲染并 flush（Signal.set 为 pending 写队列，冻结开关需 flush 后对 render 可见）。 */
    private void renderAndFlush(RecordingRenderBackend target) {
        host.__getRuntime().flush();
        host.render(CANVAS_WIDTH, CANVAS_HEIGHT, target, 0, 0);
        host.__getRuntime().flush();
    }

    @Test
    public void renderPaintsGlassOverlayAfterMainTreeAndRefreshesDiagnostics() {
        // 序号 0 = 旧线性饱和度语义，此时质感由宿主补的 tint 面表达，才能观察叠加时序。
        host.__getMaterialIndexSignal().set(Double.valueOf(0.0D));
        renderAndFlush(backend);

        int tintCount = 0;
        int mainTreeIndex = -1;
        int glassTintIndex = -1;
        for (int i = 0; i < backend.getCallCount(); i++) {
            RenderCall call = backend.getCall(i);
            if (!call.methodName().equals("drawSurface")) {
                continue;
            }
            if (call.getInt(FILL_COLOR_ARG) == GLASS_TINT) {
                tintCount++;
                if (glassTintIndex < 0) {
                    glassTintIndex = i;
                }
            } else {
                mainTreeIndex = i; // 主树面板底（非 tint）最后一次写入
            }
        }
        Assert.assertTrue("主树应回放面板底色", mainTreeIndex >= 0);
        Assert.assertTrue("至少存在主面板 + 探针两条玻璃 tint", tintCount >= 2);
        Assert.assertTrue("玻璃叠加必须在主树回放之后（backdrop 语义：采已绘内容）",
                glassTintIndex > mainTreeIndex);
        Assert.assertTrue("渲染路径诊断必须在首帧刷新",
                host.__getPathSignal().get().startsWith("backdrop 路径: "));
    }

    /**
     * 材质档（序号非 0）下质感全部在 shader 内合成，宿主不得再叠 tint 面：
     * 两层白叠加会二次加白过曝。同时滤镜请求仍必须发生（几何锁的锚点）。
     */
    @Test
    public void materialModeSuppressesHostSideGlassTint() {
        host.__getMaterialIndexSignal().set(Double.valueOf(3.0D));
        RecordingRenderBackend mat = new RecordingRenderBackend();
        renderAndFlush(mat);

        for (RenderCall call : mat.getCalls()) {
            if (call.methodName().equals("drawSurface")) {
                Assert.assertNotEquals("材质档下宿主不得再叠玻璃 tint 面",
                        GLASS_TINT, call.getInt(FILL_COLOR_ARG));
            }
        }
        Assert.assertTrue("材质档仍必须请求 backdrop 滤镜，当前=" + host.__getBackdropRects().size(),
                host.__getBackdropRects().size() >= 2);
    }

    /**
     * 坐标基准回归（2026-09-01 真机首验根因锁）：玻璃面板矩形必须落在采样场的
     * <b>绝对</b>盒内（SceneGeometry.absoluteBox 权威口径），而非其父相对局部坐标。
     * 居中列布局下局部 x≈0、绝对 x≈222，两者差之千里——回归时本测试直接失败。
     */
    @Test
    public void glassPanelUsesAbsoluteBoxNotParentLocalCoordinates() {
        renderAndFlush(backend);
        club.heiqi.uilib.ui.scene.layout.AnchorRect stageBox =
                club.heiqi.uilib.ui.scene.layout.SceneGeometry.absoluteBox(host.__getStage(), 0, 0);
        Assert.assertTrue("采样场应有绝对宽度", stageBox.getWidth() > 0);
        Assert.assertTrue("居中布局下采样场绝对 x 必须大于其父相对局部 x（否则本测试无区分度）",
                stageBox.getX() > 0);

        java.util.List<int[]> rects = host.__getBackdropRects();
        Assert.assertTrue("应有玻璃面板滤镜请求作为几何锚点", !rects.isEmpty());
        int[] panel = rects.get(0);
        Assert.assertEquals("面板左缘必须基于绝对 x（局部坐标回归时=10，正确=stageBox.getX()+10）",
                stageBox.getX() + 10, panel[0]);
        Assert.assertEquals("面板右缘必须基于绝对 x",
                stageBox.getX() + stageBox.getWidth() - 10, panel[2]);
        Assert.assertEquals("面板上缘必须基于绝对 y",
                stageBox.getY() + 26, panel[1]);
    }

    @Test
    public void frozenSwitchSkipsGlassOverlayForAbComparison() {
        // 锚点用"滤镜请求 ledger"而非 tint 面：材质档下宿主不叠 tint，若只查 tint
        // 本测试会恒真——冻结根本没跑也不会被察觉。ledger 与渲染模式无关。
        host.__getMaterialIndexSignal().set(Double.valueOf(3.0D));
        RecordingRenderBackend live = new RecordingRenderBackend();
        renderAndFlush(live);
        Assert.assertTrue("未冻结时应请求滤镜", !host.__getBackdropRects().isEmpty());

        host.__getFrozenSignal().set(Boolean.TRUE);
        RecordingRenderBackend frozen = new RecordingRenderBackend();
        renderAndFlush(frozen);

        int surfaceCalls = 0;
        for (RenderCall call : frozen.getCalls()) {
            if (call.methodName().equals("drawSurface")) {
                surfaceCalls++;
                Assert.assertNotEquals("冻结帧不得再画玻璃 tint", GLASS_TINT, call.getInt(FILL_COLOR_ARG));
            }
        }
        Assert.assertTrue("冻结帧主树仍应正常回放", surfaceCalls > 0);
        Assert.assertTrue("冻结帧必须完全停止 backdrop 滤镜请求", host.__getBackdropRects().isEmpty());
        Assert.assertTrue("冻结帧诊断应显示暂停", host.__getPathSignal().get().contains("已暂停"));
    }

    @Test
    public void stageKeepsSamplingRowsAndTextProbe() {
        renderAndFlush(backend);
        int chipBackgrounds = 0;
        for (RenderCall call : backend.getCalls()) {
            String name = call.methodName();
            if (!name.equals("drawSurface") && !name.equals("fillRect")) {
                continue;
            }
            int color = call.getInt(FILL_COLOR_ARG);
            boolean opaque = (color >>> 24) == 0xFF;
            boolean saturated = (color & 0xFFFFFF) != 0;
            if (opaque && saturated && color != PlaygroundKit.PANEL_BG && color != PlaygroundKit.ROOT_BG) {
                chipBackgrounds++;
            }
        }
        Assert.assertTrue("采样场色带 chip 必须实际入绘（为玻璃提供高对比采样内容），当前=" + chipBackgrounds,
                chipBackgrounds >= 20);
    }
}