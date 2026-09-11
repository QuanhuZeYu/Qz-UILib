package club.heiqi.uilib.internal.chat3.input;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudEditService;
import club.heiqi.uilib.ui.hud.api.HudEditTarget;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudLayoutService;
import club.heiqi.uilib.ui.hud.api.HudPlacement;
import club.heiqi.uilib.ui.hud.api.HudRegistration;
import club.heiqi.uilib.ui.hud.api.HudToolbarService;
import club.heiqi.uilib.ui.hud.api.HudToolbarSpec;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * 聊天屏编辑态 HUD 预览浮层契约：非编辑态零注册、每目标一个浮层且按注册顺序装配、
 * 放置走 {@link club.heiqi.uilib.ui.hud.api.HudLayoutResolver}（外框含工具栏 gap + 厚度）、
 * 左键命中拖动写草稿（clamp 到视口）、Esc 手势回滚、提交/取消语义。
 *
 * <p>浮层布局在生产里由帧管线按全屏约束完成（{@code anchorProvider = null}），测试用
 * {@link SceneLayoutEngine} 复刻同一约束，保证命中与几何断言走真实布局盒。</p>
 */
public class ChatHudEditPreviewsTest {

    private static final String HUD_ID = "qzuilib:test_preview";
    private static final String OTHER_ID = "qzuilib:test_preview_other";
    private static final int VIEW_W = 200;
    private static final int VIEW_H = 150;
    private static final int CONTENT_W = 120;
    private static final int CONTENT_H = 60;
    private static final int TOOLBAR_GAP = 4;
    private static final int TOOLBAR_THICKNESS = 28;

    private SceneInteractionHarness harness;
    private SceneLayoutEngine layoutEngine;
    /** 主树根（聊天屏在真实装配里就是它承接未命中预览的指针）。 */
    private SceneNode mainRoot;

    @Before
    public void setUp() {
        HudLayoutService.getInstance().clear();
        HudEditService.getInstance().clear();
        HudToolbarService.getInstance().clear();
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create(new FixedTextMeasurer(8, 16));
        mainRoot = new SceneNode();
        mainRoot.setFillParentHeight(true);
        harness.mountRoot(mainRoot, VIEW_W, VIEW_H);
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
    }

    @After
    public void tearDown() {
        if (harness != null) {
            harness.dispose();
        }
        HudLayoutService.getInstance().clear();
        HudEditService.getInstance().clear();
        HudToolbarService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }

    private static HudEditTarget target(String hudId, int width, HudToolbarSpec toolbarSpec) {
        HudEditTarget.Builder builder = HudEditTarget.builder(hudId)
                .previewFactory(rt -> SceneNode.column()
                        .setPreferredWidth(width).setPreferredHeight(CONTENT_H));
        if (toolbarSpec != null) {
            builder.toolbarSpec(toolbarSpec);
        }
        return builder.build();
    }

    /** 复刻生产帧管线对无锚点浮层的全屏约束布局。 */
    private void layoutOverlays(SceneRuntime rt) {
        for (SceneOverlayHost.Entry entry : rt.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(VIEW_W, VIEW_H));
        }
    }

    /** 无 toolbarSpec 时浮层根的唯一子节点就是预览内容根（passthrough）。 */
    private static SceneNode previewContent(SceneOverlayHost.Entry entry) {
        List<SceneNode> children = entry.getRoot().__getChildren();
        Assert.assertEquals("浮层根只承载预览外框一个子节点", 1, children.size());
        return children.get(0);
    }

    private static AnchorRect box(SceneNode node) {
        return SceneGeometry.absoluteBox(node, 0, 0);
    }

    @Test
    public void nonEditingModeRegistersNoOverlay() {
        SceneRuntime rt = harness.getRuntime();
        HudRegistration registration = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, null));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            Assert.assertTrue("非编辑态不得注册任何浮层", rt.getOverlayHost().isEmpty());
            previews.setSessionActive(false);
            Assert.assertTrue(rt.getOverlayHost().isEmpty());

            previews.setSessionActive(true);
            Assert.assertEquals(1, rt.getOverlayHost().size());
            previews.setSessionActive(false);
            Assert.assertTrue("退出编辑态必须摘除全部预览浮层", rt.getOverlayHost().isEmpty());
            previews.setSessionActive(false);
            Assert.assertTrue(rt.getOverlayHost().isEmpty());
        } finally {
            previews.dispose();
            registration.close();
        }
    }

    @Test
    public void onePreviewPerTargetInRegistrationOrder() {
        SceneRuntime rt = harness.getRuntime();
        HudRegistration first = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, null));
        HudRegistration second = HudEditService.getInstance().register(target(OTHER_ID, 40, null));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            previews.setSessionActive(true);
            Assert.assertEquals("每个已注册目标一个预览浮层", 2, rt.getOverlayHost().size());
            List<SceneOverlayHost.Entry> entries = rt.getOverlayHost().bottomFirst();
            Assert.assertEquals("浮层顺序 = 注册顺序（先注册先绘制）",
                    CONTENT_W, previewContent(entries.get(0)).getPreferredWidth());
            Assert.assertEquals(40, previewContent(entries.get(1)).getPreferredWidth());

            // 注册表变化（注销）后编辑态重建：预览集合随之收敛
            second.close();
            previews.refreshTargets();
            Assert.assertEquals(1, rt.getOverlayHost().size());
        } finally {
            previews.dispose();
            first.close();
            second.close();
        }
    }

    @Test
    public void pointerOutsidePreviewsFallsThroughToMainTree() {
        SceneRuntime rt = harness.getRuntime();
        HudRegistration registration = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, null));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        final int[] mainHits = new int[1];
        rt.on(mainRoot, SceneEventType.POINTER_DOWN, (event, ctx) -> mainHits[0]++);
        try {
            HudLayoutService layout = HudLayoutService.getInstance();
            layout.beginEdit();
            previews.setSessionActive(true);
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);

            // 预览内容盒之外的按下必须穿透到主树（浮层根 hitTestable=false），且不产生任何草稿
            harness.pressAt(1, 1);
            Assert.assertEquals("未命中预览的指针必须穿透到主树", 1, mainHits[0]);
            Assert.assertFalse(previews.isDragging());
            harness.moveAt(VIEW_W - 10, 5);
            Assert.assertNull("未命中预览不得写草稿", layout.placement(HUD_ID));
            harness.releaseAt(VIEW_W - 10, 5);
        } finally {
            previews.dispose();
            registration.close();
            layoutCancel();
        }
    }

    @Test
    public void previewPlacementFollowsResolverWithDefaultPlacement() {
        SceneRuntime rt = harness.getRuntime();
        HudRegistration registration = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, null));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            previews.setSessionActive(true);
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);

            AnchorRect expected = club.heiqi.uilib.ui.hud.api.HudLayoutResolver.resolve(
                    HudPlacement.defaultOf(HudAnchor.BOTTOM_LEFT, HudEditTarget.DEFAULT_MARGIN_PX),
                    VIEW_W, VIEW_H, CONTENT_W, CONTENT_H, HudInsets.NONE);
            AnchorRect actual = box(previewContent(rt.getOverlayHost().bottomFirst().get(0)));
            // AnchorRect 无 equals（identity 语义），逐字段断言
            Assert.assertEquals("预览位置 X = 解析器给出的盒", expected.getX(), actual.getX());
            Assert.assertEquals("预览位置 Y = 解析器给出的盒", expected.getY(), actual.getY());
            Assert.assertEquals("预览宽 = 外框宽", expected.getWidth(), actual.getWidth());
            Assert.assertEquals("预览高 = 外框高", expected.getHeight(), actual.getHeight());
            Assert.assertEquals(VIEW_H - HudEditTarget.DEFAULT_MARGIN_PX - CONTENT_H, actual.getY());

            // 提交后的用户覆盖参与下一帧放置
            HudLayoutService.getInstance().commit(HUD_ID, HudPlacement.of(HudAnchor.TOP_LEFT, 30, 20));
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);
            AnchorRect moved = box(previewContent(rt.getOverlayHost().bottomFirst().get(0)));
            Assert.assertEquals(30, moved.getX());
            Assert.assertEquals(20, moved.getY());
        } finally {
            previews.dispose();
            registration.close();
        }
    }

    @Test
    public void dragWritesClampedDraftAndCommitKeepsIt() {
        SceneRuntime rt = harness.getRuntime();
        HudRegistration registration = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, null));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            HudLayoutService layout = HudLayoutService.getInstance();
            layout.beginEdit();
            previews.setSessionActive(true);
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);

            SceneNode content = previewContent(rt.getOverlayHost().bottomFirst().get(0));
            AnchorRect start = box(content);
            harness.pressAt(start.getX() + start.getWidth() / 2, start.getY() + start.getHeight() / 2);
            Assert.assertTrue("按下预览内容即开始拖动", previews.isDragging());

            // 拖到视口右上极限之外：偏移收敛进 [0, 可用尺寸 - 外框尺寸]。
            // 下锚点量的是「底边距视口底部」：向左上拖 = 偏移增大，故上极限 = 视口高 - 内容高。
            harness.moveAt(VIEW_W - 10, 5);
            HudPlacement drafted = layout.placement(HUD_ID);
            Assert.assertNotNull("拖动必须写草稿", drafted);
            Assert.assertEquals(VIEW_W - CONTENT_W, drafted.getOffsetX());
            Assert.assertEquals(VIEW_H - CONTENT_H, drafted.getOffsetY());

            harness.releaseAt(VIEW_W - 10, 5);
            Assert.assertFalse("抬起结束手势", previews.isDragging());
            Assert.assertEquals("抬起保留草稿（提交前不落已提交布局）", drafted, layout.placement(HUD_ID));

            layout.commitEdit();
            Assert.assertEquals(drafted, layout.placement(HUD_ID));
        } finally {
            previews.dispose();
            registration.close();
            layoutCancel();
        }
    }

    @Test
    public void dragCancelRollsBackToPrePressPlacement() {
        SceneRuntime rt = harness.getRuntime();
        HudRegistration registration = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, null));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            HudLayoutService layout = HudLayoutService.getInstance();
            layout.beginEdit();
            previews.setSessionActive(true);
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);

            // 无先前覆盖：取消手势回滚到「无草稿」
            SceneNode content = previewContent(rt.getOverlayHost().bottomFirst().get(0));
            AnchorRect start = box(content);
            harness.pressAt(start.getX() + 5, start.getY() + 5);
            harness.moveAt(VIEW_W - 10, 5);
            Assert.assertNotNull(layout.placement(HUD_ID));
            Assert.assertTrue("Esc 拖动中消费本次手势", previews.cancelDrag());
            Assert.assertNull("回滚到按下前（无覆盖 = 无草稿）", layout.placement(HUD_ID));
            Assert.assertFalse(previews.cancelDrag());

            // 有先前覆盖：取消手势回到按下前位置
            layout.setDraft(HUD_ID, HudPlacement.of(HudAnchor.BOTTOM_LEFT, 22, 33));
            harness.pressAt(start.getX() + 5, start.getY() + 5);
            harness.moveAt(VIEW_W - 10, 5);
            Assert.assertNotEquals(HudPlacement.of(HudAnchor.BOTTOM_LEFT, 22, 33), layout.placement(HUD_ID));
            Assert.assertTrue(previews.cancelDrag());
            Assert.assertEquals(HudPlacement.of(HudAnchor.BOTTOM_LEFT, 22, 33), layout.placement(HUD_ID));
        } finally {
            previews.dispose();
            registration.close();
            layoutCancel();
        }
    }

    @Test
    public void previewOuterBoxIncludesToolbarGapAndThickness() {
        SceneRuntime rt = harness.getRuntime();
        HudToolbarSpec spec = HudToolbarSpec.builder()
                .scaleControls(false).gap(TOOLBAR_GAP).thickness(TOOLBAR_THICKNESS).build();
        HudToolbarService.getInstance().register(HUD_ID, spec,
                r -> SceneNode.row().setPreferredWidth(60));
        HudRegistration registration = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, spec));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            previews.setSessionActive(true);
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);

            SceneOverlayHost.Entry entry = rt.getOverlayHost().bottomFirst().get(0);
            SceneNode outer = entry.getRoot().__getChildren().get(0);
            List<SceneNode> outerChildren = outer.__getChildren();
            Assert.assertEquals("外框 = 内容 + 工具栏", 2, outerChildren.size());
            SceneNode content = outerChildren.get(0);
            SceneNode toolbar = outerChildren.get(1);

            int outerHeight = CONTENT_H + TOOLBAR_GAP + TOOLBAR_THICKNESS;
            AnchorRect contentBox = box(content);
            AnchorRect toolbarBox = box(toolbar);
            AnchorRect expected = club.heiqi.uilib.ui.hud.api.HudLayoutResolver.resolve(
                    HudPlacement.defaultOf(HudAnchor.BOTTOM_LEFT, HudEditTarget.DEFAULT_MARGIN_PX),
                    VIEW_W, VIEW_H, CONTENT_W, outerHeight, HudInsets.NONE);
            Assert.assertEquals("工具栏厚度计入放置：内容顶部 = 外框顶部",
                    expected.getY(), contentBox.getY());
            Assert.assertEquals("工具栏紧贴内容下方留 gap",
                    contentBox.getY() + contentBox.getHeight() + TOOLBAR_GAP, toolbarBox.getY());
            Assert.assertEquals(expected.getY() + outerHeight, toolbarBox.getY() + toolbarBox.getHeight());

            // clamp 口径与放置同源：下锚点向上拖到极限 = 视口高 - 外框高（工具栏厚度计入外框）
            HudLayoutService layout = HudLayoutService.getInstance();
            layout.beginEdit();
            harness.pressAt(contentBox.getX() + 5, contentBox.getY() + 5);
            harness.moveAt(VIEW_W - 1, 0);
            harness.releaseAt(VIEW_W - 1, 0);
            HudPlacement drafted = layout.placement(HUD_ID);
            Assert.assertNotNull(drafted);
            Assert.assertEquals(VIEW_W - CONTENT_W, drafted.getOffsetX());
            Assert.assertEquals(VIEW_H - outerHeight, drafted.getOffsetY());
        } finally {
            previews.dispose();
            registration.close();
            layoutCancel();
        }
    }

    private static void layoutCancel() {
        HudLayoutService.getInstance().cancelEdit();
    }
}
