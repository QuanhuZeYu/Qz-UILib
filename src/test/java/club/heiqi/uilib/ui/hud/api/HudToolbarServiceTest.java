package club.heiqi.uilib.ui.hud.api;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * HUD 外接工具栏注册表契约：规格/工厂查询、重复 id 拒绝、注销幂等、版本驱动重建、
 * 未注册直通；规格默认值与校验。
 */
public class HudToolbarServiceTest {

    private static final String HUD_ID = "qzuilib:test_toolbar";

    @Before
    public void setUp() {
        HudToolbarService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        HudToolbarService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }

    private static SceneNode content() {
        return SceneNode.column().setPreferredWidth(120).setPreferredHeight(80);
    }

    @Test
    public void specDefaultsAreBottomSideWithThicknessAndGap() {
        HudToolbarSpec spec = HudToolbarSpec.builder().build();
        Assert.assertEquals("默认挂载边 = 下边", HudToolbarSide.BOTTOM, HudToolbarSide.DEFAULT);
        Assert.assertEquals(HudToolbarSide.DEFAULT, spec.getSide());
        Assert.assertEquals(HudToolbarSpec.DEFAULT_GAP_PX, spec.getGap());
        Assert.assertEquals(HudToolbarSpec.DEFAULT_THICKNESS_PX, spec.getThickness());
        Assert.assertTrue("默认可见", Boolean.TRUE.equals(spec.getVisible().get()));
        Assert.assertTrue(HudToolbarSide.BOTTOM.isHorizontalEdge());
        Assert.assertTrue(HudToolbarSide.BOTTOM.isTrailing());
        Assert.assertFalse(HudToolbarSide.TOP.isTrailing());
        Assert.assertFalse(HudToolbarSide.LEFT.isHorizontalEdge());
        Assert.assertTrue(HudToolbarSide.RIGHT.isTrailing());
    }

    @Test
    public void specRejectsInvalidGapAndThickness() {
        try {
            HudToolbarSpec.builder().gap(-1).build();
            Assert.fail("负 gap 必须拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
        try {
            HudToolbarSpec.builder().thickness(0).build();
            Assert.fail("0 厚度必须拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }

    @Test
    public void duplicateRegistrationIsRejectedAndCloseIsIdempotent() {
        HudToolbarService service = HudToolbarService.getInstance();
        HudToolbarSpec spec = HudToolbarSpec.builder().build();
        HudWindowFactory factory = rt -> SceneNode.row();
        HudRegistration registration = service.register(HUD_ID, spec, factory);
        Assert.assertTrue(service.hasToolbar(HUD_ID));
        Assert.assertSame(spec, service.spec(HUD_ID));
        Assert.assertSame(factory, service.factory(HUD_ID));
        try {
            service.register(HUD_ID, spec, factory);
            Assert.fail("重复 id 必须明确拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
        registration.close();
        registration.close();
        Assert.assertTrue(registration.isClosed());
        Assert.assertFalse("注销后注册表移除", service.hasToolbar(HUD_ID));
        Assert.assertNull(service.spec(HUD_ID));
    }

    @Test
    public void revisionBumpsOnlyOnStructuralChange() {
        HudToolbarService service = HudToolbarService.getInstance();
        int before = service.revision().get().intValue();
        HudRegistration registration = service.register(HUD_ID,
                HudToolbarSpec.builder().build(), rt -> SceneNode.row());
        ReactiveScheduler.get().flush(); // 版本经帧末批处理生效（宿主在下一帧读到）
        int afterRegister = service.revision().get().intValue();
        Assert.assertTrue("注册必须推进版本（宿主据此重建保留窗口）", afterRegister > before);
        registration.close();
        ReactiveScheduler.get().flush();
        Assert.assertTrue("注销必须推进版本", service.revision().get().intValue() > afterRegister);
    }

    @Test
    public void unregisteredHudMountsPassthrough() {
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        SceneNode content = content();
        HudToolbarLayer.Result result = HudToolbarService.getInstance()
                .mountLayer(rt, HUD_ID, content);
        Assert.assertSame("未注册时外框就是内容根（不额外包一层）", content, result.root());
        Assert.assertNull(result.toolbar());
        Assert.assertNull(result.spec());
        Assert.assertFalse(result.isVisible());
        Assert.assertEquals(120, result.outerWidth(120));
        Assert.assertEquals(80, result.outerHeight(80));
    }

    @Test
    public void registeredHudMountsLayerWithSpecAndFactory() {
        HudToolbarService.getInstance().register(HUD_ID,
                HudToolbarSpec.builder(HudToolbarSide.TOP).thickness(24).gap(3)
                        .visible(Signal.create(Boolean.TRUE)).build(),
                rt -> SceneNode.row().setPreferredWidth(40).setPreferredHeight(24));
        SceneRuntime rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        HudToolbarLayer.Result result = HudToolbarService.getInstance()
                .mountLayer(rt, HUD_ID, content());
        rt.flush();
        Assert.assertNotNull(result.toolbar());
        Assert.assertEquals(HudToolbarSide.TOP, result.spec().getSide());
        Assert.assertNotSame(result.content(), result.root());
        Assert.assertEquals(80 + 3 + 24, result.outerHeight(80));
    }
}
