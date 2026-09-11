package club.heiqi.uilib.ui.scene.host;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.control.SceneTooltip;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.AnchorProvider;
import club.heiqi.uilib.ui.scene.overlay.AnchoredPortalLayout;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.overlay.OverlayHandle;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 锚定浮层触发盒的「树坐标 → 宿主逻辑坐标」换算测试（tooltip 缩放后位置错回归）。
 *
 * <p>缺陷：{@link AnchorProvider#forNode} 返回的是触发节点<b>所在树</b>的绝对盒；当触发节点位于
 * 相对倍率 s != 1 的非锚定 overlay（HUD 编辑预览）内时，该盒属于 overlay 逻辑空间（= 宿主逻辑 ÷ s），
 * 而锚定浮层自身 s 恒为 1.0F、在宿主逻辑空间回放 —— 缺一次 ×s 换算就会让浮层物理位置随 s 错位。</p>
 *
 * <p>断言口径：物理 = 宿主逻辑 × hostScale（锚定浮层 s == 1），宿主逻辑 = 触发所在 overlay 逻辑 × s。
 * 主树触发 / s == 1 overlay 触发 / 关闭态 HUD（宿主 backend.scaled）三条路径必须保持原样（零回归）。</p>
 */
public class SceneAnchoredOverlayTriggerSpacePipelineTest {

    private static final int W = 200;
    private static final int H = 120;
    /** 预览 overlay 背景色。 */
    private static final int PREVIEW_COLOR = 0xFF00AAFF;
    /** 测试自建 tooltip 内容根背景色。 */
    private static final int TOOLTIP_COLOR = 0xFF00FF00;
    /** tooltip 内容高（决定 resolveAuto 的 contentHeight 与展开方向）。 */
    private static final int TOOLTIP_CONTENT_HEIGHT = 12;
    /** tooltip 锚定策略：首选宽 106（= maxWidthPx 96 + padding 8 + 边框 2），安全边距 8。 */
    private static final int TOOLTIP_PREFERRED_WIDTH = 106;
    private static final int TOOLTIP_SAFE_INSET = 8;

    private SceneTestHost host;
    private SceneRuntime runtime;
    private RecordingBackend backend;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new SceneTestHost();
        runtime = host.__getRuntime();
        backend = new RecordingBackend();
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== (a) 触发节点在 s != 1 的 overlay 内、tooltip 自身 s == 1 ====================

    /**
     * s = 2 的预览 overlay 内触发：锚点与物理位置都必须按「overlay 逻辑 × s」落到宿主逻辑空间。
     *
     * <p>触发盒 overlay 逻辑 (30,0,20,10) → 宿主逻辑 (60,0,40,20) → anchorX=60、触发底边=20；
     * 修复前分别取到 30 / 10（浮层整体偏左上 30×10 物理 px）。</p>
     */
    @Test
    public void triggerInsideScaledOverlayShouldAnchorTooltipAtScaledBox() {
        Preview preview = scaledPreview(2.0F, 30, 0, 20, 10);
        SceneNode tooltip = anchoredTooltip(preview.trigger);
        host.render(W, H, backend, 0, 0);

        SceneOverlayHost.Entry entry = entryOf(tooltip);
        Assert.assertNotNull("锚定浮层应已注册", entry);
        Assert.assertEquals("tooltip 自身不得带相对倍率（锚定浮层恒 1.0F）", 1.0F,
                entry.getRelativeScale(), 0.0F);
        Assert.assertEquals("宿主逻辑 anchorX = 触发 overlay 逻辑 x × s", 60, entry.getAnchorX());
        Assert.assertEquals("宿主逻辑 anchorY = 触发 overlay 逻辑底边 × s", 20, entry.getAnchorY());

        int[] physical = backend.lastRect(TOOLTIP_COLOR);
        Assert.assertNotNull("tooltip 背景必须已回放", physical);
        Assert.assertEquals("物理 X = (absX + anchorX) × hostScale", 60, physical[0]);
        Assert.assertEquals("物理 Y = (absY + anchorY) × hostScale", 20, physical[1]);
    }

    /**
     * s = 1.5 + 宿主倍率 2 + 非零 abs：物理 = (abs + 宿主逻辑锚点) × hostScale，两段取整口径一致。
     *
     * <p>触发盒 overlay 逻辑 (33,4,20,10) → 宿主逻辑 (round(33×1.5)=50, round(4×1.5)=6, 30, 15)
     * → anchor=(50, 21) → 物理 = ((10+50)×2, (6+21)×2) = (120, 54)。</p>
     */
    @Test
    public void triggerInsideScaledOverlayShouldFollowHostScaleAndOrigin() {
        Preview preview = scaledPreview(1.5F, 33, 4, 20, 10);
        SceneNode tooltip = anchoredTooltip(preview.trigger);
        host.render(W, H, backend.scaled(2.0F), 10, 6);

        SceneOverlayHost.Entry entry = entryOf(tooltip);
        Assert.assertNotNull(entry);
        Assert.assertEquals("宿主逻辑 anchorX = round(33 × 1.5)", 50, entry.getAnchorX());
        Assert.assertEquals("宿主逻辑 anchorY = round((4+10) × 1.5)", 21, entry.getAnchorY());

        int[] physical = backend.lastRect(TOOLTIP_COLOR);
        Assert.assertNotNull(physical);
        Assert.assertEquals("物理 X = (absX + anchorX) × hostScale", 120, physical[0]);
        Assert.assertEquals("物理 Y = (absY + anchorY) × hostScale", 54, physical[1]);
    }

    /** 换算后的锚点仍在宿主逻辑空间参与横向 clamp（右边界安全边距不得在 overlay 空间被提前吃掉）。 */
    @Test
    public void scaledTriggerShouldClampAnchorInsideHostLogicalSpace() {
        Preview preview = scaledPreview(2.0F, 80, 2, 20, 10);
        SceneNode tooltip = anchoredTooltip(preview.trigger);
        host.render(W, H, backend, 0, 0);

        SceneOverlayHost.Entry entry = entryOf(tooltip);
        Assert.assertNotNull(entry);
        SceneAnchorResolver.ResolvedAnchor resolved = SceneAnchorResolver.resolveAuto(
                new AnchorRect(160, 4, 40, 20), W, H, TOOLTIP_CONTENT_HEIGHT,
                new AnchoredPortalLayout(TOOLTIP_PREFERRED_WIDTH, 0, TOOLTIP_SAFE_INSET));
        Assert.assertEquals("换算后的宿主逻辑 X 应与宿主空间解析结果一致", resolved.getX(),
                entry.getAnchorX());
        Assert.assertEquals("右边缘应保留宿主空间安全边距", W - TOOLTIP_SAFE_INSET - TOOLTIP_PREFERRED_WIDTH,
                entry.getAnchorX());
    }

    // ==================== (b) 触发节点在主树：不受任何 overlay 的 s 影响 ====================

    /**
     * 主树触发 + 同时存在 s = 2 的预览 overlay：锚点必须仍取主树逻辑盒（零串扰）。
     */
    @Test
    public void mainTreeTriggerShouldIgnoreOtherOverlaysRelativeScale() {
        scaledPreview(2.0F, 0, 0, 1, 1);
        SceneNode trigger = mainTreeTrigger(30, 0, 20, 10);
        host.__getRoot().appendChild(trigger.__getParent());
        SceneNode tooltip = anchoredTooltip(trigger);
        host.render(W, H, backend, 0, 0);

        SceneOverlayHost.Entry entry = entryOf(tooltip);
        Assert.assertNotNull(entry);
        Assert.assertEquals("主树触发 anchorX 保持主树逻辑 x", 30, entry.getAnchorX());
        Assert.assertEquals("主树触发 anchorY 保持主树逻辑底边", 10, entry.getAnchorY());

        int[] physical = backend.lastRect(TOOLTIP_COLOR);
        Assert.assertNotNull(physical);
        Assert.assertEquals(30, physical[0]);
        Assert.assertEquals(10, physical[1]);
    }

    // ==================== (c) s == 1 零回归 ====================

    /**
     * s == 1 的 overlay 内触发：锚点解析必须与「未换算的原始盒」逐位一致（零行为噪音钉子）。
     */
    @Test
    public void identityScaleOverlayTriggerShouldKeepLegacyResolution() {
        Preview preview = scaledPreview(1.0F, 30, 0, 20, 10);
        SceneNode tooltip = anchoredTooltip(preview.trigger);
        host.render(W, H, backend, 0, 0);

        SceneOverlayHost.Entry entry = entryOf(tooltip);
        Assert.assertNotNull(entry);
        AnchoredPortalLayout layout =
                new AnchoredPortalLayout(TOOLTIP_PREFERRED_WIDTH, 0, TOOLTIP_SAFE_INSET);
        SceneAnchorResolver.ResolvedAnchor legacy = SceneAnchorResolver.resolveAuto(
                new AnchorRect(30, 0, 20, 10), W, H, TOOLTIP_CONTENT_HEIGHT, layout);
        Assert.assertEquals("s == 1 时 anchorX 必须等于旧路径解析值", legacy.getX(), entry.getAnchorX());
        Assert.assertEquals("s == 1 时 anchorY 必须等于旧路径解析值", legacy.getY(), entry.getAnchorY());

        int[] physical = backend.lastRect(TOOLTIP_COLOR);
        Assert.assertNotNull(physical);
        Assert.assertEquals(legacy.getX(), physical[0]);
        Assert.assertEquals(legacy.getY(), physical[1]);
    }

    /** s == 1 的预览 overlay 存在时，主树触发的物理位置也必须保持原样（同一帧两棵树互不污染）。 */
    @Test
    public void identityScaleOverlayShouldNotDisturbMainTreeTrigger() {
        scaledPreview(1.0F, 0, 0, 1, 1);
        SceneNode trigger = mainTreeTrigger(30, 0, 20, 10);
        host.__getRoot().appendChild(trigger.__getParent());
        SceneNode tooltip = anchoredTooltip(trigger);
        host.render(W, H, backend, 0, 0);

        int[] physical = backend.lastRect(TOOLTIP_COLOR);
        Assert.assertNotNull(physical);
        Assert.assertEquals(30, physical[0]);
        Assert.assertEquals(10, physical[1]);
    }

    // ==================== (d) 关闭态 HUD 形态：宿主 backend.scaled(scale) ====================

    /**
     * 关闭态 HUD 窗口（SceneHudHost 路径：宿主 {@code backend.scaled(scale)} + 逻辑原点）内 tooltip：
     * 触发节点在窗口主树（s == 1），物理 = (abs + 锚点) × scale，不得被本次换算改动。
     */
    @Test
    public void hudWindowScaledBackendTooltipShouldFollowPhysicalPosition() {
        SceneNode trigger = mainTreeTrigger(30, 4, 20, 10);
        host.__getRoot().appendChild(trigger.__getParent());
        SceneNode tooltip = anchoredTooltip(trigger);
        host.render(W, H, backend.scaled(2.0F), 10, 6);

        SceneOverlayHost.Entry entry = entryOf(tooltip);
        Assert.assertNotNull(entry);
        Assert.assertEquals("窗口主树触发 anchorX = 窗口逻辑 x", 30, entry.getAnchorX());
        Assert.assertEquals("窗口主树触发 anchorY = 窗口逻辑底边", 14, entry.getAnchorY());

        int[] physical = backend.lastRect(TOOLTIP_COLOR);
        Assert.assertNotNull(physical);
        Assert.assertEquals("物理 X = (窗口逻辑原点 + anchorX) × scale", 80, physical[0]);
        Assert.assertEquals("物理 Y = (窗口逻辑原点 + anchorY) × scale", 40, physical[1]);
    }

    // ==================== (e) 真实 SceneTooltip 端到端（hover 路径） ====================

    /**
     * 真实 {@link SceneTooltip#attach} 在 s = 2 预览 overlay 内的缩放按钮上：hover 后浮层物理位置
     * 必须落在该按钮的物理盒下方（修复前落在 overlay 逻辑坐标处，偏左上）。
     */
    @Test
    public void sceneTooltipOnScaledPreviewButtonShouldAppearAtPhysicalTrigger() {
        Preview preview = scaledPreview(2.0F, 30, 0, 20, 10);
        final int tooltipMaxWidth = 40;
        runtime.mount(host.__getRoot(), () -> {
            SceneTooltip.attach(runtime, new SceneTooltip.Props(preview.trigger,
                    Signal.create("Miner"), null, 0, tooltipMaxWidth, 3, true));
            return new SceneNode();
        });
        runtime.flush();

        host.render(W, H, backend, 0, 0);
        moveTo(80, 10);
        host.render(W, H, backend, 0, 0);

        Assert.assertEquals("hover 后 tooltip 浮层应已挂载", 2, runtime.getOverlayHost().size());
        SceneOverlayHost.Entry tooltipEntry = runtime.getOverlayHost().bottomFirst().get(1);
        Assert.assertEquals("真实 tooltip 的 anchorX = 按钮 overlay 逻辑 x × s", 60,
                tooltipEntry.getAnchorX());
        Assert.assertEquals("真实 tooltip 的 anchorY = 按钮物理底边", 20, tooltipEntry.getAnchorY());

        // 表面绘制盒允许 1px 边框内缩（OVERLAY 配方 borderWidth=1）：位置真值由上面的锚点断言钉死，
        // 这里只钉「物理落点在触发盒物理位置处」。
        int[] physical = backend.lastRect(tooltipSurfaceTint());
        Assert.assertNotNull("tooltip 外观必须已回放", physical);
        Assert.assertTrue("真实 tooltip 物理 X 应贴住按钮物理 X，实际 " + physical[0],
                Math.abs(physical[0] - 60) <= 1);
        Assert.assertTrue("真实 tooltip 物理 Y 应贴住按钮物理底边，实际 " + physical[1],
                Math.abs(physical[1] - 20) <= 1);
    }

    // ==================== (f) 缩小方向 s < 1 ====================

    /** s = 0.5（HUD 缩小）：换算后浮层落到物理盒下方，而不是被 overlay 逻辑坐标右推。 */
    @Test
    public void shrunkOverlayTriggerShouldAnchorTooltipAtPhysicalBox() {
        Preview preview = scaledPreview(0.5F, 140, 20, 40, 20);
        SceneNode tooltip = anchoredTooltip(preview.trigger);
        host.render(W, H, backend, 0, 0);

        SceneOverlayHost.Entry entry = entryOf(tooltip);
        Assert.assertNotNull(entry);
        Assert.assertEquals("宿主逻辑 anchorX = round(140 × 0.5)", 70, entry.getAnchorX());
        Assert.assertEquals("宿主逻辑 anchorY = round((20+20) × 0.5)", 20, entry.getAnchorY());

        int[] physical = backend.lastRect(TOOLTIP_COLOR);
        Assert.assertNotNull(physical);
        Assert.assertEquals(70, physical[0]);
        Assert.assertEquals(20, physical[1]);
    }

    // ==================== (g) 跨 overlay 锚点偏移叠加（锚定浮层内触发） ====================

    /**
     * 触发节点位于<b>锚定</b> overlay（listbox / 菜单这类）内时，其 absoluteBox 仍是该 overlay 树的
     * 局部盒，必须叠加宿主 entry 的 anchorX/anchorY 才是宿主逻辑坐标。
     *
     * <p>锚定浮层 s 被 {@code SceneOverlayHost.Entry.setRelativeScale} 硬拒绝（恒 1.0F），
     * 所以这一组合只差「锚点偏移」一项。外层探针 (30,40,80,20) 在 200x120 宿主下解析为
     * anchor=(30,60)；内层按钮在外层树内 (10,0,20,10) → 宿主逻辑 (40,60,20,10)
     * → 内层 tooltip anchor=(40,70)。</p>
     */
    @Test
    public void triggerInsideAnchoredOverlayShouldAddOwnerAnchorShift() {
        SceneNode outerContent = SceneNode.row();
        outerContent.setCrossAxisAlign(CrossAxisAlign.START);
        outerContent.setBackgroundColor(PREVIEW_COLOR);
        SceneNode trigger = positionedNode(10, 0, 20, 10);
        outerContent.appendChild(trigger);
        final AnchorRect outerAnchor = new AnchorRect(30, 40, 80, 20);
        runtime.portalAnchored(Signal.create(Boolean.TRUE), () -> outerContent,
                OverlayDismissPolicy.NONE, null, () -> outerAnchor);
        runtime.flush();

        SceneNode tooltip = anchoredTooltip(trigger);
        host.render(W, H, backend, 0, 0);

        SceneOverlayHost.Entry outerEntry = entryOf(outerContent);
        Assert.assertNotNull("外层锚定浮层应已注册", outerEntry);
        Assert.assertEquals("外层锚点 X = 探针 X", 30, outerEntry.getAnchorX());
        Assert.assertEquals("外层锚点 Y = 探针底边", 60, outerEntry.getAnchorY());

        SceneOverlayHost.Entry tooltipEntry = entryOf(tooltip);
        Assert.assertNotNull(tooltipEntry);
        Assert.assertEquals("内层 tooltip X = 外层 anchorX + 按钮树内 x", 40, tooltipEntry.getAnchorX());
        Assert.assertEquals("内层 tooltip Y = 外层 anchorY + 按钮树内底边", 70, tooltipEntry.getAnchorY());

        int[] physical = backend.lastRect(TOOLTIP_COLOR);
        Assert.assertNotNull(physical);
        Assert.assertEquals(40, physical[0]);
        Assert.assertEquals(70, physical[1]);
    }

    /**
     * 锚定浮层 s 的 API 硬约束：{@code OverlayHandle.setRelativeScale} 对锚定浮层抛异常，
     * 因此「锚定 overlay 自身 s != 1」在 API 层不可达 —— 跨树换算的 s 因子只来自
     * 「触发节点所在的非锚定 overlay」（HUD 编辑预览），锚定浮层只需叠加 owner 偏移。
     */
    @Test
    public void anchoredOverlayRelativeScaleShouldStayRejected() {
        SceneNode overlay = SceneNode.row();
        OverlayHandle handle = runtime.getOverlayHost().register(overlay, OverlayDismissPolicy.NONE, null,
                () -> new AnchorRect(30, 40, 80, 20));
        try {
            handle.setRelativeScale(2.0F);
            Assert.fail("锚定浮层必须拒绝 relativeScale（s 恒 1.0F）");
        } catch (IllegalStateException expected) {
            // 预期：锚定浮层的几何由锚点解析决定，不参与相对倍率换算
        }
        Assert.assertEquals("拒绝后 s 必须保持 1.0F", 1.0F, handle.getEntry().getRelativeScale(), 0.0F);
    }

    // ==================== 夹具 ====================

    /** 预览 overlay 装配结果：浮层根 + 其内触发节点。 */
    private static final class Preview {
        final SceneNode root;
        final SceneNode trigger;

        Preview(SceneNode root, SceneNode trigger) {
            this.root = root;
            this.trigger = trigger;
        }
    }

    /**
     * 装配一个非锚定预览 overlay（模拟 HUD 编辑预览），其内唯一触发节点按 margin 定位。
     * overlay 逻辑视口 = 宿主逻辑 ÷ s（帧管线施加），因此触发盒是 overlay 逻辑坐标。
     */
    private Preview scaledPreview(float scale, int left, int top, int triggerW, int triggerH) {
        SceneNode overlay = SceneNode.row();
        overlay.setCrossAxisAlign(CrossAxisAlign.START);
        overlay.setBackgroundColor(PREVIEW_COLOR);
        SceneNode trigger = positionedNode(left, top, triggerW, triggerH);
        overlay.appendChild(trigger);
        OverlayHandle handle = runtime.getOverlayHost().register(overlay);
        if (Float.compare(scale, 1F) != 0) {
            handle.setRelativeScale(scale);
        }
        return new Preview(overlay, trigger);
    }

    /** 主树内的横向行容器（唯一子项按 margin 定位），返回触发节点。 */
    private SceneNode mainTreeTrigger(int left, int top, int triggerW, int triggerH) {
        SceneNode row = SceneNode.row();
        row.setCrossAxisAlign(CrossAxisAlign.START);
        row.appendChild(positionedNode(left, top, triggerW, triggerH));
        return row.__getChildren().get(0);
    }

    private static SceneNode positionedNode(int left, int top, int width, int height) {
        SceneNode node = new SceneNode();
        node.setPreferredWidth(width);
        node.setPreferredHeight(height);
        node.setMargin(top, 0, 0, left);
        return node;
    }

    /** 装配测试用锚定浮层（等价 SceneTooltip 的底层 portal 路径），返回内容根。 */
    private SceneNode anchoredTooltip(SceneNode trigger) {
        SceneNode content = new SceneNode();
        content.setWidthSizing(SceneNode.WidthSizing.FILL);
        content.setPreferredHeight(TOOLTIP_CONTENT_HEIGHT);
        content.setBackgroundColor(TOOLTIP_COLOR);
        Signal<Boolean> visible = Signal.create(Boolean.TRUE);
        runtime.portalAnchored(visible, () -> content, OverlayDismissPolicy.NONE, null,
                AnchorProvider.forNode(trigger), Collections.<SceneNode>emptySet(),
                new AnchoredPortalLayout(TOOLTIP_PREFERRED_WIDTH, 0, TOOLTIP_SAFE_INSET));
        runtime.flush();
        return content;
    }

    private SceneOverlayHost.Entry entryOf(SceneNode overlayRoot) {
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().bottomFirst()) {
            if (entry.getRoot() == overlayRoot) {
                return entry;
            }
        }
        return null;
    }

    /** 指针移动（画布逻辑坐标；输入路由按 overlay 自身 s 换算命中）。 */
    private void moveTo(int x, int y) {
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.MOVE, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        runtime.route(host.__getRoot(), builder.drainFrame(), 0, 0);
        runtime.flush();
    }

    private static int tooltipSurfaceTint() {
        return SceneThemes.DEFAULT.surface(SceneTheme.Role.OVERLAY).getIdle().getTint();
    }

    /** 记录型后端：保留 fillRect / drawSurface 的物理坐标，其余 no-op。 */
    private static final class RecordingBackend implements UiRenderBackend {

        private final List<int[]> rects = new ArrayList<int[]>();
        private final List<Integer> colors = new ArrayList<Integer>();

        private void record(int left, int top, int right, int bottom, int color) {
            rects.add(new int[] {left, top, right, bottom});
            colors.add(Integer.valueOf(color));
        }

        /** 最近一次指定填充色的物理矩形；不存在时返回 null。 */
        int[] lastRect(int color) {
            for (int i = colors.size() - 1; i >= 0; i--) {
                if (colors.get(i).intValue() == color) {
                    return rects.get(i);
                }
            }
            return null;
        }

        @Override
        public void fillRect(int left, int top, int right, int bottom, int color) {
            record(left, top, right, bottom, color);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, int fillColor, int borderColor,
                int cornerRadius) {
            record(left, top, right, bottom, fillColor);
        }

        @Override
        public void drawSurface(int left, int top, int right, int bottom, int fillColor, int borderColor,
                int cornerRadiusTopLeft, int cornerRadiusTopRight, int cornerRadiusBottomRight,
                int cornerRadiusBottomLeft) {
            record(left, top, right, bottom, fillColor);
        }

        @Override
        public void drawBorder(int left, int top, int right, int bottom, int color) {
            // no-op
        }

        @Override
        public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
            // no-op
        }

        @Override
        public void popClip() {
            // no-op
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow) {
            // no-op
        }

        @Override
        public void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx) {
            // no-op
        }

        @Override
        public void pushGroupOpacity(int left, int top, int right, int bottom, float opacity) {
            // no-op
        }

        @Override
        public void popGroupOpacity() {
            // no-op
        }

        @Override
        public void pushTransform(float translateX, float translateY, float rotateDegrees, float scaleX,
                float scaleY, float originXRatio, float originYRatio, int left, int top, int right, int bottom) {
            // no-op
        }

        @Override
        public void popTransform() {
            // no-op
        }

        @Override
        public void pushTransformLayer(float translateX, float translateY, float rotateDegrees, float scaleX,
                float scaleY, float originXRatio, float originYRatio, int left, int top, int right, int bottom) {
            // no-op
        }

        @Override
        public void popTransformLayer() {
            // no-op
        }
    }
}
