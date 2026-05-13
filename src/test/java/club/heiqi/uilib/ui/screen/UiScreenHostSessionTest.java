package club.heiqi.uilib.ui.screen;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.host.DocumentHostRenderSupport;
import club.heiqi.uilib.ui.image.HostImageRenderer;
import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;

/**
 * `UiScreenHostSession` 的运行时渲染上下文契约测试。
 */
public class UiScreenHostSessionTest {

    /**
     * 验证宿主会话创建页面渲染上下文时会透传屏幕声明的运行时适配器。
     */
    @Test
    public void shouldCreateRenderContextWithExplicitRuntimeAdapters() {
        HostImageRenderer hostImageRenderer = new HostImageRenderer() {
            @Override
            public void render(HostImageSource source, int left, int top, int right, int bottom) {}
        };
        UiRuntimeAdapters runtimeAdapters = UiRuntimeAdapters.empty().withHostImageRenderer(hostImageRenderer);

        UiRenderContext context = DocumentHostRenderSupport.createRenderContext(320, 240, 12, 34, 0.5F,
                new UiRenderContext.PaintContextCompositor(), new UiMainLayerSnapshotService(), runtimeAdapters);

        Assert.assertSame(runtimeAdapters, context.getRuntimeAdapters());
        Assert.assertSame(hostImageRenderer, context.getRuntimeAdapters().getHostImageRenderer());
    }

    /**
     * 验证内部屏幕身份识别优先返回稳定 pageId。
     */
    @Test
    public void shouldResolveRuntimeScreenNameFromInternalDescriptorOwner() {
        Object screen = new FakeDescriptorOwner(InternalDiagnosticScreenRegistry.UI_TEST);

        Assert.assertEquals(InternalDiagnosticScreenRegistry.uiTestPageId(),
                InternalScreenIdentity.runtimeScreenNameOf(screen));
    }

    /**
     * 供测试使用的最小 descriptor screen。
     */
    private static final class FakeDescriptorOwner implements InternalScreenIdentity.DescriptorOwner {

        private final InternalScreenIdentity.PageDescriptor pageDescriptor;

        private FakeDescriptorOwner(InternalScreenIdentity.PageDescriptor pageDescriptor) {
            this.pageDescriptor = pageDescriptor;
        }

        @Override
        public InternalScreenIdentity.PageDescriptor getPageDescriptor() {
            return pageDescriptor;
        }
    }
}
