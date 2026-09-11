package club.heiqi.uilib.ui.hud.api;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 公开 HUD 编辑契约：目标值对象校验、注册表语义（重复拒绝 / 幂等注销 / 版本 / 注册顺序）、
 * requestEdit 无活动宿主时静默、宿主端口（isEditing / focus）委托与身份判定、clear 边界。
 */
public class HudEditServiceTest {

    private static final String HUD_ID = "qzuilib:test_edit";
    private static final String OTHER_ID = "qzuilib:test_edit_other";

    /** 宿主端口测试桩：记录意图，暴露编辑态与聚焦。 */
    private static final class StubHost implements HudEditService.Host {
        final Signal<String> focus = Signal.create(null);
        boolean editing;
        int enterCount;
        String lastHudId;

        @Override
        public void requestEnterEdit(String hudId) {
            enterCount++;
            lastHudId = hudId;
            editing = true;
            focus.set(hudId);
        }

        @Override
        public boolean isEditing() {
            return editing;
        }

        @Override
        public ReadableSignal<String> focus() {
            return focus;
        }
    }

    @Before
    public void setUp() {
        HudEditService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        HudEditService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }

    private static HudEditTarget target(String hudId) {
        return HudEditTarget.builder(hudId).previewFactory(rt -> SceneNode.row()).build();
    }

    @Test
    public void builderRejectsBlankIdAndMissingPreviewFactory() {
        try {
            HudEditTarget.builder("  ");
            Assert.fail("空白 hudId 必须拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
        try {
            HudEditTarget.builder(HUD_ID).build();
            Assert.fail("缺失预览工厂必须拒绝（预览不能静默降级为空）");
        } catch (NullPointerException expected) {
            // 预期
        }
    }

    @Test
    public void builderDefaultsToBottomLeftMarginAndNullableToolbar() {
        HudEditTarget target = target(HUD_ID);
        Assert.assertEquals("未声明默认放置 = 左下角 + 兜底 margin",
                HudPlacement.defaultOf(HudAnchor.BOTTOM_LEFT, HudEditTarget.DEFAULT_MARGIN_PX),
                target.getDefaultPlacement());
        Assert.assertNull("未声明工具栏规格 = 预览不挂外接工具栏", target.getToolbarSpec());
        Assert.assertEquals(HUD_ID, target.getHudId());
        Assert.assertNotNull(target.getPreviewFactory());

        HudPlacement declared = HudPlacement.of(HudAnchor.TOP_RIGHT, 12, 34);
        HudEditTarget explicit = HudEditTarget.builder(HUD_ID)
                .previewFactory(rt -> SceneNode.row())
                .defaultPlacement(declared)
                .toolbarSpec(HudToolbarSpec.builder().build())
                .build();
        Assert.assertEquals(declared, explicit.getDefaultPlacement());
        Assert.assertNotNull(explicit.getToolbarSpec());
    }

    @Test
    public void duplicateRegistrationIsRejectedAndCloseIsIdempotent() {
        HudEditService service = HudEditService.getInstance();
        HudRegistration registration = service.register(target(HUD_ID));
        Assert.assertTrue(service.hasTarget(HUD_ID));
        Assert.assertEquals(HUD_ID, service.target(HUD_ID).getHudId());
        Assert.assertNull(service.target("qzuilib:unknown"));
        try {
            service.register(target(HUD_ID));
            Assert.fail("重复 id 必须明确拒绝（不静默覆盖）");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
        registration.close();
        registration.close();
        Assert.assertTrue(registration.isClosed());
        Assert.assertFalse("注销后注册表移除", service.hasTarget(HUD_ID));
        Assert.assertNull(service.target(HUD_ID));
    }

    @Test
    public void revisionBumpsOnStructuralChangeAndTargetsKeepRegistrationOrder() {
        HudEditService service = HudEditService.getInstance();
        int before = service.revision().get().intValue();
        HudRegistration first = service.register(target(HUD_ID));
        HudRegistration second = service.register(target(OTHER_ID));
        ReactiveScheduler.get().flush();
        int afterRegister = service.revision().get().intValue();
        Assert.assertTrue("注册必须推进版本（编辑宿主据此重建预览）", afterRegister > before);
        Assert.assertEquals("targets() 是注册顺序快照", HUD_ID, service.targets().get(0).getHudId());
        Assert.assertEquals(OTHER_ID, service.targets().get(1).getHudId());
        try {
            service.targets().clear();
            Assert.fail("targets() 必须是不可变快照");
        } catch (UnsupportedOperationException expected) {
            // 预期
        }
        first.close();
        second.close();
        ReactiveScheduler.get().flush();
        Assert.assertTrue("注销必须推进版本", service.revision().get().intValue() > afterRegister);
        Assert.assertTrue(service.targets().isEmpty());
    }

    @Test
    public void requestEditWithoutHostIsSilentlyDropped() {
        HudEditService service = HudEditService.getInstance();
        service.register(target(HUD_ID));
        service.requestEdit(HUD_ID);
        Assert.assertFalse("无活动编辑宿主时不得改变编辑态", service.isEditing());
        Assert.assertNull("无宿主时聚焦为 null", service.focus().get());
        service.requestEdit(null);
        service.requestEdit("   ");
        Assert.assertFalse(service.isEditing());
    }

    @Test
    public void hostPortCarriesIntentEditingAndFocus() {
        HudEditService service = HudEditService.getInstance();
        StubHost host = new StubHost();
        service.attachHost(host);
        Assert.assertFalse("宿主未进入编辑时 isEditing 为假", service.isEditing());
        Assert.assertSame(host.focus, service.focus());

        service.requestEdit(HUD_ID);
        Assert.assertEquals(1, host.enterCount);
        Assert.assertEquals(HUD_ID, host.lastHudId);
        Assert.assertTrue("编辑态以宿主为真值", service.isEditing());
        ReactiveScheduler.get().flush();
        Assert.assertEquals(HUD_ID, service.focus().get());

        // attachHost 重复注入同一宿主幂等
        service.attachHost(host);
        Assert.assertTrue(service.isEditing());

        // detachHost 只摘同身份宿主：旧屏关闭不得顶掉新屏
        int hostEnters = host.enterCount;
        StubHost other = new StubHost();
        service.attachHost(other);
        service.detachHost(host);
        Assert.assertFalse(service.isEditing());
        service.requestEdit(OTHER_ID);
        Assert.assertEquals("当前宿主是 newer host", 1, other.enterCount);
        Assert.assertEquals("旧宿主被摘除后不再收到意图", hostEnters, host.enterCount);

        service.detachHost(other);
        service.requestEdit(HUD_ID);
        Assert.assertEquals("摘除后意图静默丢弃", 1, other.enterCount);
        Assert.assertFalse(service.isEditing());
        Assert.assertNull(service.focus().get());
    }

    @Test
    public void clearOnlyClearsRegistryAndKeepsHostBinding() {
        HudEditService service = HudEditService.getInstance();
        StubHost host = new StubHost();
        service.attachHost(host);
        service.register(target(HUD_ID));
        service.clear();
        Assert.assertFalse(service.hasTarget(HUD_ID));
        service.requestEdit(HUD_ID);
        Assert.assertEquals("clear 只清注册表，不动宿主绑定", 1, host.enterCount);
        service.detachHost(host);
        service.requestEdit(HUD_ID);
        Assert.assertEquals(1, host.enterCount);
    }
}
