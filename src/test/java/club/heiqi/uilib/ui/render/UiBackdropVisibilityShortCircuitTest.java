package club.heiqi.uilib.ui.render;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver.ResolvedCornerRadii;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;

/**
 * W2 可见性短路的端到端行为：表面与当前 clip 盒无交集时整链早退
 * （不取快照、不设 uniform、不 draw），记为 {@code NONE + "clipped"}。
 *
 * <p>证据口径：测试在无 GL 环境下运行，只要 render 走进快照/GL 路径就会抛错，因此断言成立
 * 即"没有发生任何 GL 调用"。反过来说，本测试不证明"有交集时逐像素行为不变"——那需要真机 GL。</p>
 */
public class UiBackdropVisibilityShortCircuitTest {

    @After
    public void resetState() {
        BackdropBlurConfig.getInstance().resetToDefaults();
        BackdropQualityService.getInstance().resetForTest();
        ReactiveScheduler.get().flush();
    }

    /** 用裁剪栈直接构造事实：glOperationsEnabled=false 下 push 是纯内存操作（测试钩子，见 ClipStack 注释）。 */
    private static UiRenderContext contextClippedTo(int left, int top, int right, int bottom) {
        UiRenderContext context = new UiRenderContext(320, 240, 0, 0, 0.0F, new PaintContextCompositor(),
                new UiMainLayerSnapshotService(), UiRuntimeAdapters.empty());
        context.clipStack.glOperationsEnabled = false;
        context.clipStack.push(left, top, right, bottom, 320, 240, ResolvedCornerRadii.uniform(0));
        return context;
    }

    private static void renderGlass(UiRenderContext context, int left, int top, int right, int bottom) {
        UiBackdropFilterRenderer.render(context, left, top, right, bottom, 12, 1.0F,
                ResolvedCornerRadii.uniform(0), UiBackdropEffect.classic(UiGlassMaterial.DARK_THIN));
    }

    @Test
    public void clippedSurfaceTakesTheEarlyExitPath() {
        UiRenderContext context = contextClippedTo(0, 0, 100, 100);

        renderGlass(context, 150, 150, 250, 250);

        Assert.assertEquals("被裁掉的表面必须记为 NONE", BackdropFilterRenderPath.NONE,
                UiRenderContext.getLastBackdropFilterRenderPath());
        Assert.assertEquals("clipped", UiRenderContext.getLastBackdropFilterDetail());
    }

    @Test
    public void clippedSurfaceIsStillRecordedWhenDiagnosticsAreDisabled() {
        BackdropBlurConfig.getInstance().setDiagnosticsEnabled(false);
        UiRenderContext context = contextClippedTo(0, 0, 100, 100);

        renderGlass(context, 300, 300, 400, 400);

        Assert.assertEquals("lastRenderPath 恒写，与诊断开关无关", BackdropFilterRenderPath.NONE,
                UiRenderContext.getLastBackdropFilterRenderPath());
        Assert.assertEquals("clipped", UiRenderContext.getLastBackdropFilterDetail());
    }

    /** 既有短路语义优先：策略禁用页面的 detail 不得被新判定改写。 */
    @Test
    public void pagePolicyStillWinsOverTheVisibilityShortCircuit() {
        UiRenderContext context = new UiRenderContext(320, 240, 0, 0, 0.0F, new PaintContextCompositor(),
                new UiMainLayerSnapshotService(), UiRuntimeAdapters.empty(), BackdropBlurPolicy.disabled());
        context.clipStack.glOperationsEnabled = false;
        context.clipStack.push(0, 0, 100, 100, 320, 240, ResolvedCornerRadii.uniform(0));

        renderGlass(context, 150, 150, 250, 250);

        Assert.assertEquals("NONE", BackdropFilterRenderPath.NONE, UiRenderContext.getLastBackdropFilterRenderPath());
        Assert.assertEquals("disabled by page policy", UiRenderContext.getLastBackdropFilterDetail());
    }

    /** 档位 solid：渲染层整链早退（与页面策略禁用同一出口）。 */
    @Test
    public void solidQualityDisablesTheWholeChain() {
        BackdropQualityService.getInstance().applyConfigured("solid");
        UiRenderContext context = new UiRenderContext(320, 240, 0, 0, 0.0F, new PaintContextCompositor(),
                new UiMainLayerSnapshotService(), UiRuntimeAdapters.empty());
        context.clipStack.glOperationsEnabled = false;

        renderGlass(context, 10, 10, 110, 110);

        Assert.assertEquals(BackdropFilterRenderPath.NONE, UiRenderContext.getLastBackdropFilterRenderPath());
        Assert.assertEquals("disabled by page policy", UiRenderContext.getLastBackdropFilterDetail());
    }
}
