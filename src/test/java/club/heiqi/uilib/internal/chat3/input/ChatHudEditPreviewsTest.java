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
 * 放置走 {@link club.heiqi.uilib.ui.hud.api.HudLayoutResolver}（外框含编辑态统一缩放行的 gap + 厚度）、
 * 左键命中拖动写草稿（clamp 到视口）、Esc 手势回滚、提交/取消语义。
 *
 * <p><b>缩放入口</b>：每个预览都带 - / 1:1 / + 三个按钮（读写统一缩放状态），与下游是否声明
 * {@code HudEditTarget.getToolbarSpec()}、是否注册 {@code HudToolbarService} 无关；
 * 声明了自定义工具时它作为内层挂上，且不再重复追加缩放按钮。
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
    /** 无自定义工具时的预览外框逻辑高 = 内容 + 统一缩放行 (gap + thickness)。 */
    private static final int PREVIEW_OUTER_H = CONTENT_H + TOOLBAR_GAP + TOOLBAR_THICKNESS;

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

    /** 复刻生产帧管线对无锚点浮层的全屏约束布局（s == 1.0F 短路，与帧管线逐位等价）。 */
    private void layoutOverlays(SceneRuntime rt) {
        layoutOverlays(rt, VIEW_W, VIEW_H);
    }

    /**
     * 复刻生产帧管线对无锚点浮层的全屏约束布局：s != 1.0F 时约束按 {@code round(画布逻辑 / s)}
     * 收敛（= 宿主物理视口 / 该 HUD 自身倍率），与 {@code SceneFramePipeline#layoutOverlays} 同口径。
     *
     * @param rt      运行时
     * @param canvasW 画布逻辑视口宽（= 宿主物理宽 / hostScale）
     * @param canvasH 画布逻辑视口高
     */
    private void layoutOverlays(SceneRuntime rt, int canvasW, int canvasH) {
        for (SceneOverlayHost.Entry entry : rt.getOverlayHost().bottomFirst()) {
            float s = entry.getRelativeScale();
            Constraints constraints = Float.compare(s, 1F) == 0
                    ? new Constraints(canvasW, canvasH)
                    : new Constraints(Math.max(1, Math.round(canvasW / s)), Math.max(1, Math.round(canvasH / s)));
            layoutEngine.layout(entry.getRoot(), constraints);
        }
    }

    /** 预览外框 = 浮层根的唯一子节点（统一缩放行恒为其尾部孩子）。 */
    private static SceneNode previewOuter(SceneOverlayHost.Entry entry) {
        List<SceneNode> children = entry.getRoot().__getChildren();
        Assert.assertEquals("浮层根只承载预览外框一个子节点", 1, children.size());
        return children.get(0);
    }

    /** 预览内容根 = 预览外框的首个孩子（自定义工具在内层外框里，不占内容位）。 */
    private static SceneNode previewContent(SceneOverlayHost.Entry entry) {
        List<SceneNode> children = previewOuter(entry).__getChildren();
        Assert.assertEquals("预览外框 = 内容 + 统一缩放行", 2, children.size());
        return children.get(0);
    }

    /** 预览统一缩放行（- / 1:1 / +）。 */
    private static SceneNode previewScaleRow(SceneOverlayHost.Entry entry) {
        return previewOuter(entry).__getChildren().get(1);
    }

    private static AnchorRect box(SceneNode node) {
        return SceneGeometry.absoluteBox(node, 0, 0);
    }

    /** 画布逻辑尺寸 = 宿主物理尺寸 / hostScale（与 {@code ChatInputSurface.render} 的 floor(w / frameScale) 同口径）。 */
    private static int canvasLogical(int physicalExtent, float hostScale) {
        return Math.max(1, (int) Math.floor(physicalExtent / hostScale));
    }

    /**
     * overlay 逻辑坐标 → 画布逻辑坐标（× s）：指针注入必须用画布逻辑空间，
     * 路由按该 entry 的 s 换算回 overlay 逻辑空间后再命中。
     */
    private static int canvasCoord(int overlayLogical, SceneOverlayHost.Entry entry) {
        return Math.round(overlayLogical * entry.getRelativeScale());
    }

    /** 实绘物理宽 = overlay 逻辑盒宽 × hostScale × s（s 取 entry 当帧值）。 */
    private static float paintedWidth(SceneOverlayHost.Entry entry, float hostScale) {
        return box(previewOuter(entry)).getWidth() * hostScale * entry.getRelativeScale();
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
                    VIEW_W, VIEW_H, CONTENT_W, PREVIEW_OUTER_H, HudInsets.NONE);
            AnchorRect actual = box(previewOuter(rt.getOverlayHost().bottomFirst().get(0)));
            // AnchorRect 无 equals（identity 语义），逐字段断言
            Assert.assertEquals("预览位置 X = 解析器给出的盒", expected.getX(), actual.getX());
            Assert.assertEquals("预览位置 Y = 解析器给出的盒", expected.getY(), actual.getY());
            Assert.assertEquals("预览宽 = 外框宽", expected.getWidth(), actual.getWidth());
            Assert.assertEquals("预览高 = 外框高（含统一缩放行）", expected.getHeight(), actual.getHeight());
            Assert.assertEquals(VIEW_H - HudEditTarget.DEFAULT_MARGIN_PX - PREVIEW_OUTER_H, actual.getY());

            // 提交后的用户覆盖参与下一帧放置
            HudLayoutService.getInstance().commit(HUD_ID, HudPlacement.of(HudAnchor.TOP_LEFT, 30, 20));
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);
            AnchorRect moved = box(previewOuter(rt.getOverlayHost().bottomFirst().get(0)));
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
            // 下锚点量的是「底边距视口底部」：向左上拖 = 偏移增大，故上极限 = 视口高 - 外框高
            // （外框含统一缩放行的 gap + thickness）。
            harness.moveAt(VIEW_W - 10, 5);
            HudPlacement drafted = layout.placement(HUD_ID);
            Assert.assertNotNull("拖动必须写草稿", drafted);
            Assert.assertEquals(VIEW_W - CONTENT_W, drafted.getOffsetX());
            Assert.assertEquals(VIEW_H - PREVIEW_OUTER_H, drafted.getOffsetY());

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

    /**
     * 无 toolbarSpec / 无工具栏注册也必须有统一缩放入口：- / 1:1 / + 三个按钮计入外框，
     * 且与放置、拖动 clamp 同口径。
     */
    @Test
    public void unifiedScaleRowParticipatesInOuterBoxAndClamp() {
        SceneRuntime rt = harness.getRuntime();
        HudRegistration registration = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, null));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            previews.setSessionActive(true);
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);

            SceneOverlayHost.Entry entry = rt.getOverlayHost().bottomFirst().get(0);
            List<SceneNode> outerChildren = previewOuter(entry).__getChildren();
            Assert.assertEquals("预览外框 = 内容 + 统一缩放行", 2, outerChildren.size());
            SceneNode scaleRow = outerChildren.get(1);
            Assert.assertEquals("统一缩放行 = 空自定义占位 + - / 1:1 / +",
                    4, scaleRow.__getChildren().size());

            AnchorRect contentBox = box(outerChildren.get(0));
            AnchorRect scaleRowBox = box(scaleRow);
            AnchorRect expected = club.heiqi.uilib.ui.hud.api.HudLayoutResolver.resolve(
                    HudPlacement.defaultOf(HudAnchor.BOTTOM_LEFT, HudEditTarget.DEFAULT_MARGIN_PX),
                    VIEW_W, VIEW_H, CONTENT_W, PREVIEW_OUTER_H, HudInsets.NONE);
            Assert.assertEquals("缩放行厚度计入放置：内容顶部 = 外框顶部",
                    expected.getY(), contentBox.getY());
            Assert.assertEquals("缩放行紧贴内容下方留 gap",
                    contentBox.getY() + contentBox.getHeight() + TOOLBAR_GAP, scaleRowBox.getY());
            Assert.assertEquals(expected.getY() + PREVIEW_OUTER_H,
                    scaleRowBox.getY() + scaleRowBox.getHeight());

            // clamp 口径与放置同源：下锚点向上拖到极限 = 视口高 - 外框高（缩放行厚度计入外框）
            HudLayoutService layout = HudLayoutService.getInstance();
            layout.beginEdit();
            harness.pressAt(contentBox.getX() + 5, contentBox.getY() + 5);
            harness.moveAt(VIEW_W - 1, 0);
            harness.releaseAt(VIEW_W - 1, 0);
            HudPlacement drafted = layout.placement(HUD_ID);
            Assert.assertNotNull(drafted);
            Assert.assertEquals(VIEW_W - CONTENT_W, drafted.getOffsetX());
            Assert.assertEquals(VIEW_H - PREVIEW_OUTER_H, drafted.getOffsetY());
        } finally {
            previews.dispose();
            registration.close();
            layoutCancel();
        }
    }

    /** 编辑态缩放入口写统一缩放状态；外框尺寸与 clamp 随倍率同口径变化。 */
    @Test
    public void scaleButtonsDriveUnifiedStateAndClampUsesScaledOuterBox() {
        SceneRuntime rt = harness.getRuntime();
        HudRegistration registration = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, null));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            previews.setSessionActive(true);
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);

            SceneOverlayHost.Entry entry = rt.getOverlayHost().bottomFirst().get(0);
            SceneNode plus = previewScaleRow(entry).__getChildren().get(3);
            Assert.assertEquals(100,
                    HudToolbarService.getInstance().scale(HUD_ID).percent().get().intValue());
            harness.click(plus);
            Assert.assertEquals("编辑态缩放入口写统一缩放状态（宿主/打开态读同一份）",
                    110, HudToolbarService.getInstance().scale(HUD_ID).percent().get().intValue());

            // 放置与 clamp 同口径：倍率变化后上极限 = 视口高 - ceil(外框高 × 倍率)
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);
            HudLayoutService layout = HudLayoutService.getInstance();
            layout.beginEdit();
            // s = 1.1：指针注入必须换算到画布逻辑空间（路由按 s 换算回 overlay 逻辑空间再命中）
            AnchorRect contentBox = box(previewContent(entry));
            harness.pressAt(canvasCoord(contentBox.getX() + 5, entry), canvasCoord(contentBox.getY() + 5, entry));
            harness.moveAt(VIEW_W - 1, 0);
            harness.releaseAt(VIEW_W - 1, 0);
            HudPlacement drafted = layout.placement(HUD_ID);
            Assert.assertNotNull(drafted);
            // 右/上极限同口径：外框尺寸按统一倍率换算（物理 px）
            Assert.assertEquals(VIEW_W - (int) Math.ceil(CONTENT_W * 1.1F), drafted.getOffsetX());
            Assert.assertEquals(VIEW_H - (int) Math.ceil(PREVIEW_OUTER_H * 1.1F), drafted.getOffsetY());
        } finally {
            previews.dispose();
            registration.close();
            layoutCancel();
        }
    }

    /** 下游 toolbarSpec 保留为「可选额外自定义工具」：作为内层挂上，且不重复追加缩放入口。 */
    @Test
    public void optionalCustomToolbarStacksInsideUnifiedScaleRow() {
        SceneRuntime rt = harness.getRuntime();
        HudToolbarSpec spec = HudToolbarSpec.builder()
                .scaleControls(true).gap(TOOLBAR_GAP).thickness(TOOLBAR_THICKNESS).build();
        HudToolbarService.getInstance().register(HUD_ID, spec,
                r -> SceneNode.row().setPreferredWidth(60));
        HudRegistration registration = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, spec));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            previews.setSessionActive(true);
            // 两轮 frame+layout：内层外框首帧尚未实测，外框尺寸要等实测收敛后再定位（生产每帧都在跑）
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);

            SceneOverlayHost.Entry entry = rt.getOverlayHost().bottomFirst().get(0);
            List<SceneNode> outerChildren = previewOuter(entry).__getChildren();
            Assert.assertEquals("外层 = 内层外框 + 统一缩放行", 2, outerChildren.size());
            Assert.assertEquals("统一缩放行 = 空自定义占位 + - / 1:1 / +",
                    4, outerChildren.get(1).__getChildren().size());

            List<SceneNode> innerChildren = outerChildren.get(0).__getChildren();
            Assert.assertEquals("内层 = 内容 + 下游自定义工具", 2, innerChildren.size());
            Assert.assertEquals("下游工具栏不再追加缩放按钮（缩放入口唯一）",
                    0, innerChildren.get(1).__getChildren().size());

            // 两层厚度都计入放置与 clamp：外框高 = 内容 + 2 × (gap + thickness)
            int outerHeight = CONTENT_H + 2 * (TOOLBAR_GAP + TOOLBAR_THICKNESS);
            AnchorRect expected = club.heiqi.uilib.ui.hud.api.HudLayoutResolver.resolve(
                    HudPlacement.defaultOf(HudAnchor.BOTTOM_LEFT, HudEditTarget.DEFAULT_MARGIN_PX),
                    VIEW_W, VIEW_H, CONTENT_W, outerHeight, HudInsets.NONE);
            SceneNode content = innerChildren.get(0);
            Assert.assertEquals(expected.getY(), box(content).getY());

            HudLayoutService layout = HudLayoutService.getInstance();
            layout.beginEdit();
            AnchorRect contentBox = box(content);
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

    /**
     * C1/C4/C8/C9：每帧写入的相对倍率 = 该 HUD 自身统一倍率 / 宿主绘制倍率；
     * 任一 HUD 调倍率或聊天屏倍率变化都只影响自己的 s（同帧生效）。
     */
    @Test
    public void relativeScaleTracksOwnTargetOverHostScale() {
        SceneRuntime rt = harness.getRuntime();
        HudRegistration first = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, null));
        HudRegistration second = HudEditService.getInstance().register(target(OTHER_ID, 40, null));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            previews.setSessionActive(true);
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            List<SceneOverlayHost.Entry> entries = rt.getOverlayHost().bottomFirst();
            Assert.assertEquals("默认 target = hostScale 时 s = 1.0（既有 overlay 口径不变）",
                    1.0F, entries.get(0).getRelativeScale(), 1E-6F);
            Assert.assertEquals(1.0F, entries.get(1).getRelativeScale(), 1E-6F);

            // 只调 HUD_ID：自己的 s = 1.1，邻居仍 1.0（零串扰）
            HudToolbarService.getInstance().scale(HUD_ID).setPercent(110);
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            Assert.assertEquals("s = 自身倍率 / 宿主倍率", 1.1F, entries.get(0).getRelativeScale(), 1E-6F);
            Assert.assertEquals("其它 HUD 预览零串扰", 1.0F, entries.get(1).getRelativeScale(), 1E-6F);

            // 聊天屏倍率 1.25：两个预览各自按 target / hostScale 重算（当帧生效）
            previews.frame(250, 188, 1.25F, HudInsets.NONE);
            Assert.assertEquals("聊天屏倍率只改变自身换算口径", 1.1F / 1.25F,
                    entries.get(0).getRelativeScale(), 1E-6F);
            Assert.assertEquals(1.0F / 1.25F, entries.get(1).getRelativeScale(), 1E-6F);
        } finally {
            previews.dispose();
            first.close();
            second.close();
        }
    }

    /**
     * C1/C4：预览实绘物理尺寸 = 预览逻辑外框 × 自身统一倍率，与聊天屏倍率无关。
     *
     * <p>实绘 = overlay 逻辑盒 × hostScale × s，而 s = target / hostScale ⇒ 恒等于逻辑盒 × target。
     * 用例在两种宿主倍率下按帧管线口径复刻 overlay 约束，再对「逻辑盒 × hostScale × s」断言。</p>
     */
    @Test
    public void paintedSizeFollowsOwnTargetAcrossHostScales() {
        SceneRuntime rt = harness.getRuntime();
        HudRegistration first = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, null));
        HudRegistration second = HudEditService.getInstance().register(target(OTHER_ID, 40, null));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            HudToolbarService.getInstance().scale(HUD_ID).setPercent(110);
            previews.setSessionActive(true);

            // hostScale = 1.0（物理视口 200×150）
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);
            List<SceneOverlayHost.Entry> entries = rt.getOverlayHost().bottomFirst();
            float ownLogicalAt100 = box(previewOuter(entries.get(0))).getWidth();
            float otherLogicalAt100 = box(previewOuter(entries.get(1))).getWidth();
            float ownAt100 = paintedWidth(entries.get(0), 1F);
            float otherAt100 = paintedWidth(entries.get(1), 1F);
            Assert.assertEquals("实绘宽 = 逻辑外框 × 自身 1.1",
                    Math.ceil(ownLogicalAt100 * 1.1F), ownAt100, 1.0);
            // 邻居逻辑外框 = max(内容 40, 统一缩放行实测宽)，故按逻辑外框断言而非内容宽
            Assert.assertEquals("邻居 target 100%：实绘宽 = 逻辑外框宽",
                    otherLogicalAt100, otherAt100, 1.0);

            // 聊天屏倍率 1.25（物理 250×188，画布逻辑仍 200×150）：各自实绘尺寸都不变（C4）
            previews.frame(250, 188, 1.25F, HudInsets.NONE);
            layoutOverlays(rt, canvasLogical(250, 1.25F), canvasLogical(188, 1.25F));
            Assert.assertEquals("画布逻辑视口不随聊天屏倍率变化", VIEW_W, canvasLogical(250, 1.25F));
            Assert.assertEquals("自身实绘尺寸不随聊天屏倍率变化", ownAt100, paintedWidth(entries.get(0), 1.25F), 0.5);
            Assert.assertEquals("其它预览实绘尺寸同样不变", otherAt100, paintedWidth(entries.get(1), 1.25F), 0.5);
        } finally {
            previews.dispose();
            first.close();
            second.close();
        }
    }

    /**
     * C2/C8：聊天屏倍率变化后命中仍与视觉 1:1 对齐——指针按画布逻辑坐标注入，
     * 缩放按钮的命中区域 = 其物理位置 / hostScale（路由按 s 换算回 overlay 逻辑空间）。
     */
    @Test
    public void hitStaysAlignedWithPaintedPreviewAcrossHostScale() {
        SceneRuntime rt = harness.getRuntime();
        HudRegistration registration = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, null));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            HudToolbarService.getInstance().scale(HUD_ID).setPercent(110);
            previews.setSessionActive(true);
            previews.frame(250, 188, 1.25F, HudInsets.NONE);
            layoutOverlays(rt, canvasLogical(250, 1.25F), canvasLogical(188, 1.25F));

            SceneOverlayHost.Entry entry = rt.getOverlayHost().bottomFirst().get(0);
            Assert.assertEquals("hostScale = 1.25 时 s = 1.1 / 1.25",
                    1.1F / 1.25F, entry.getRelativeScale(), 1E-6F);
            SceneNode plus = previewScaleRow(entry).__getChildren().get(3);
            AnchorRect plusBox = box(plus);
            int cx = canvasCoord(plusBox.getX() + plusBox.getWidth() / 2, entry);
            int cy = canvasCoord(plusBox.getY() + plusBox.getHeight() / 2, entry);
            harness.clickAt(cx, cy);
            Assert.assertEquals("按视觉位置注入的点击必须命中 + 按钮（110% → 120%）",
                    120, HudToolbarService.getInstance().scale(HUD_ID).percent().get().intValue());
            // 命中回算落在按钮盒内（容差 ≤ target px；按钮盒本身远大于 1px，容差按规格 §5 放宽）
            Assert.assertTrue("命中点回算必须落在按钮内",
                    Math.abs(Math.round(cx / entry.getRelativeScale()) - (plusBox.getX() + plusBox.getWidth() / 2)) <= 1);

            // 预览物理盒之外必须穿透到主树，且不得改动缩放状态
            final int[] mainHits = new int[1];
            rt.on(mainRoot, SceneEventType.POINTER_DOWN, (event, ctx) -> mainHits[0]++);
            harness.clickAt(VIEW_W - 1, 4);
            Assert.assertEquals("预览物理盒之外应穿透到主树", 1, mainHits[0]);
            Assert.assertEquals("穿透点击不得改动缩放状态", 120,
                    HudToolbarService.getInstance().scale(HUD_ID).percent().get().intValue());
        } finally {
            previews.dispose();
            registration.close();
        }
    }

    /**
     * C2/C3：拖动增量按 target 乘数换算（事件坐标已被路由换算到 overlay 逻辑空间），
     * 放置盒与 clamp 同用物理口径。
     *
     * <p>用 target 125% / hostScale 1.0（s = 1.25）让「物理位移 = 画布位移 × hostScale」的
     * 断言不受注入取整干扰；若乘数仍是 hostScale（旧口径），增量会退化为画布位移。</p>
     */
    @Test
    public void dragDeltaUsesTargetMultiplierAndClampUsesPhysicalBox() {
        SceneRuntime rt = harness.getRuntime();
        HudRegistration registration = HudEditService.getInstance().register(target(HUD_ID, CONTENT_W, null));
        ChatHudEditPreviews previews = new ChatHudEditPreviews(rt);
        try {
            HudLayoutService layout = HudLayoutService.getInstance();
            HudToolbarService.getInstance().scale(HUD_ID).setPercent(125);
            layout.beginEdit();
            previews.setSessionActive(true);
            previews.frame(VIEW_W, VIEW_H, 1F, HudInsets.NONE);
            layoutOverlays(rt);

            SceneOverlayHost.Entry entry = rt.getOverlayHost().bottomFirst().get(0);
            AnchorRect contentBox = box(previewContent(entry));
            int downX = canvasCoord(contentBox.getX() + 5, entry);
            int downY = canvasCoord(contentBox.getY() + 5, entry);
            harness.pressAt(downX, downY);
            Assert.assertTrue("按下预览内容即开始拖动", previews.isDragging());

            // 小幅平移：物理位移 = 画布位移（hostScale = 1），BOTTOM_LEFT 下 Y 偏移反号
            int moveX = downX + 10;
            int moveY = downY - 10;
            harness.moveAt(moveX, moveY);
            HudPlacement drafted = layout.placement(HUD_ID);
            Assert.assertNotNull("拖动必须写草稿", drafted);
            Assert.assertEquals("拖动增量 = 物理位移（target 乘数）",
                    HudEditTarget.DEFAULT_MARGIN_PX + (moveX - downX), drafted.getOffsetX(), 1.25);
            Assert.assertEquals(HudEditTarget.DEFAULT_MARGIN_PX - (moveY - downY), drafted.getOffsetY(), 1.25);

            // clamp 用物理口径：右/上极限 = 物理视口 - ceil(外框 × target)
            harness.moveAt(VIEW_W - 1, 0);
            HudPlacement clamped = layout.placement(HUD_ID);
            Assert.assertEquals(VIEW_W - (int) Math.ceil(CONTENT_W * 1.25F), clamped.getOffsetX());
            Assert.assertEquals(VIEW_H - (int) Math.ceil(PREVIEW_OUTER_H * 1.25F), clamped.getOffsetY());
            harness.releaseAt(VIEW_W - 1, 0);
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
