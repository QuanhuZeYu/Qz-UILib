package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.ui.hud.api.HudEditService;
import club.heiqi.uilib.ui.hud.api.HudEditTarget;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudLayoutResolver;
import club.heiqi.uilib.ui.hud.api.HudLayoutService;
import club.heiqi.uilib.ui.hud.api.HudPlacement;
import club.heiqi.uilib.ui.hud.api.HudScaleState;
import club.heiqi.uilib.ui.hud.api.HudToolbarLayer;
import club.heiqi.uilib.ui.hud.api.HudToolbarService;
import club.heiqi.uilib.ui.hud.api.HudToolbarSpec;
import club.heiqi.uilib.ui.hud.api.HudWindowFactory;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.overlay.OverlayHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 聊天输入屏编辑态的 HUD 预览浮层（公开编辑契约的消费端；规划《聊天工具栏与HUD布局编辑》增量）。
 *
 * <p>{@link club.heiqi.uilib.ui.hud.api.HudEditService} 里的每个可编辑目标在编辑子模式下
 * 装配一个预览：内容由 {@link HudEditTarget#getPreviewFactory()} 构建（与 HUD 窗口工厂同契约），
 * 位置取 {@link HudLayoutService#placement(String)}，无用户覆盖时退回
 * {@link HudEditTarget#getDefaultPlacement()}。放置与拖动 clamp 共用同一份外框尺寸
 * （编辑态统一缩放行的 gap + thickness 计入外框，并按该 HUD 的统一倍率换算），口径与
 * {@link ChatInputSurface#applyOuterPlacement} 相同——否则拖到边界时工具栏会被推出视口。</p>
 *
 * <p><b>缩放入口属于编辑态</b>：每个预览统一装配 - / 1:1 / + 三个按钮，读写
 * {@link HudToolbarService#scale(String)} 的统一缩放状态——下游不声明
 * {@link HudEditTarget#getToolbarSpec()}、不注册 {@link HudToolbarService} 也能调节缩放，
 * 不再需要为了拿到倍率而塞一个空槽工具栏。非编辑态（关闭态 HUD）不挂任何缩放控件，
 * 缩放只按统一倍率呈现；{@code getToolbarSpec()} 保留为可选的额外自定义工具。</p>
 *
 * <h3>为什么每个目标一个浮层根</h3>
 * <p>scene 布局是 flex 流式布局，没有绝对定位原语：同层兄弟会按主轴顺序累加位置。浮层根走
 * {@link club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost}（{@code anchorProvider = null}
 * 时宿主按全屏约束布局），根内只有唯一子节点（预览外框），因此 margin 就是精确的视口坐标。
 * 浮层根自身 {@code hitTestable = false}，未命中预览内容的指针继续穿透到下一条浮层与主树
 * （{@code SceneInputRouter.hitTestWithOverlays} 的既有语义），预览不会吞掉聊天输入。</p>
 *
 * <h3>零开销边界</h3>
 * <p>非编辑态不构建任何浮层根、不注册 overlay、不挂事件；退出编辑时摘除浮层并回收预览作用域
 * （工厂在构建期建立的 bind/mount/on 一并清理）。装配失败只跳过该目标，不影响其它预览与聊天屏。</p>
 */
final class ChatHudEditPreviews {

    private static final Logger LOG = LogManager.getLogger("QzUILib Chat3HudEdit");

    private final SceneRuntime rt;
    private final HudLayoutService layoutService = HudLayoutService.getInstance();
    private final HudEditService editService = HudEditService.getInstance();
    /** 已挂载预览（注册顺序 = {@link HudEditService#targets()} 顺序）。 */
    private final List<Preview> previews = new ArrayList<Preview>();
    /** 预览挂载期的子作用域：卸载时一并回收构建期建立的 bind/mount/on。null = 非编辑态。 */
    private Owner scope;
    /** 当前拖动中的预览（单一 gesture 到 UP/CANCEL）；null = 无手势。 */
    private Preview dragging;
    /** 最近一帧宿主视口（屏幕像素；放置解析与拖动 clamp 共用）。 */
    private int viewportWidth = 1;
    private int viewportHeight = 1;
    /**
     * 最近一帧宿主绘制倍率（hostScale）：输入 × 倍率 = 屏幕像素，节点坐标 / 倍率 = logical。
     *
     * <p>预览自身的实绘倍率不在此处：每个预览按 {@code target / hostScale} 写 overlay 相对倍率，
     * 由帧管线与输入路由在同一坐标空间内施加（见 {@link #applyRelativeScale}），因此预览只随
     * 自身统一倍率变化，聊天屏倍率不再串扰其它 HUD。</p>
     */
    private float scale = 1F;
    /** 最近一帧宿主安全区。 */
    private HudInsets insets = HudInsets.NONE;

    ChatHudEditPreviews(SceneRuntime rt) {
        this.rt = rt;
    }

    /** @return 预览是否已挂载（= 编辑子模式进行中） */
    boolean isActive() {
        return scope != null;
    }

    /** @return 当前是否有预览拖动 gesture 进行中 */
    boolean isDragging() {
        return dragging != null;
    }

    /**
     * 编辑子模式开关：进入时按注册表装配全部预览，退出时整体卸载（幂等）。
     *
     * @param active 是否进入编辑子模式
     */
    void setSessionActive(boolean active) {
        if (active == isActive()) {
            return;
        }
        if (active) {
            rebuild();
        } else {
            unloadAll();
        }
    }

    /** 注册表版本变化（编辑态中增删目标）：重建预览集合；非编辑态零动作。 */
    void refreshTargets() {
        if (isActive()) {
            rebuild();
        }
    }

    /**
     * 每帧同步视口、倍率与安全区，并把每个预览的权威放置写进 margin。
     *
     * <p>必须在本帧帧管线（{@code super.render}）之前调用：margin 写入打 LAYOUT 脏，浮层布局
     * 在帧管线内物化。首帧内容尚未布局时退回内容自身声明尺寸，下一帧按实测收敛
     * （与 {@link HudToolbarLayer} 的工具栏实测口径同思路）。</p>
     *
     * <p>同一帧内先写每个预览的 overlay 相对倍率（{@link #applyRelativeScale}），再写 margin：
     * 帧管线与输入路由在本帧读到同一份 s，倍率变化当帧生效。</p>
     *
     * @param viewportWidth  宿主视口宽（屏幕像素）
     * @param viewportHeight 宿主视口高（屏幕像素）
     * @param scale          本帧绘制倍率
     * @param insets         宿主安全区
     */
    void frame(int viewportWidth, int viewportHeight, float scale, HudInsets insets) {
        this.viewportWidth = Math.max(1, viewportWidth);
        this.viewportHeight = Math.max(1, viewportHeight);
        this.scale = scale > 0F ? scale : 1F;
        this.insets = insets == null ? HudInsets.NONE : insets;
        for (Preview preview : previews) {
            applyRelativeScale(preview);
            applyPlacement(preview);
        }
    }

    /**
     * Esc 优先级：拖动中先回滚当前手势（不退出编辑会话）。
     *
     * @return true = 已消费本次 Esc
     */
    boolean cancelDrag() {
        if (dragging == null) {
            return false;
        }
        endDrag(true);
        return true;
    }

    /** 整体卸载（屏幕关闭 / 运行时释放）：摘除全部浮层并回收预览作用域。 */
    void dispose() {
        unloadAll();
    }

    // ==================== 装配 ====================

    private void rebuild() {
        unloadAll();
        final List<Preview> built = new ArrayList<Preview>();
        final Owner owner = new Owner();
        owner.run(() -> {
            for (HudEditTarget target : editService.targets()) {
                try {
                    Preview preview = buildPreview(target);
                    if (preview != null) {
                        built.add(preview);
                    }
                } catch (RuntimeException | Error failure) {
                    // 单个目标工厂失败只跳过该预览（对齐 SceneHudHost 的窗口工厂隔离语义）
                    LOG.warn("HUD 编辑预览装配失败: id={}", target.getHudId(), failure);
                }
            }
        });
        scope = owner;
        previews.addAll(built);
    }

    private Preview buildPreview(HudEditTarget target) {
        final String hudId = target.getHudId();
        SceneNode content = target.getPreviewFactory().build(rt);
        if (content == null) {
            throw new IllegalStateException("HUD preview factory must return a node: " + hudId);
        }
        List<SceneNode> toolbars = new ArrayList<SceneNode>(2);
        HudToolbarLayer.Result layer = mountLayer(target, content, toolbars);
        SceneNode overlayRoot = SceneNode.column().setHitTestable(false).setFillParentHeight(true);
        overlayRoot.appendChild(layer.root());
        OverlayHandle handle = rt.getOverlayHost().register(overlayRoot, OverlayDismissPolicy.NONE, null);
        Preview preview = new Preview(target, overlayRoot, layer, handle);
        // 拖动只挂在预览内容根上：工具栏（统一缩放入口与可选自定义工具）按钮/空白按下在此停止冒泡，
        // 缩放按钮不被拖动夺走 gesture（与 ChatInputSurface 对聊天外框工具栏的既有处理同口径）。
        rt.on(layer.content(), SceneEventType.POINTER_DOWN, (event, ctx) -> onDown(preview, event, ctx));
        rt.on(layer.content(), SceneEventType.POINTER_MOVE, (event, ctx) -> onMove(preview, event, ctx));
        rt.on(layer.content(), SceneEventType.POINTER_UP, (event, ctx) -> onUp(preview, event, ctx));
        rt.on(layer.content(), SceneEventType.POINTER_CANCEL, (event, ctx) -> onCancel(preview, event, ctx));
        for (SceneNode toolbar : toolbars) {
            rt.on(toolbar, SceneEventType.POINTER_DOWN, (event, ctx) -> ctx.stopPropagation());
        }
        return preview;
    }

    /**
     * 预览外框装配：<b>编辑态统一提供缩放入口</b>（- / 1:1 / +，读统一缩放状态），因此下游不声明
     * {@link HudEditTarget#getToolbarSpec()}、不注册 {@link HudToolbarService} 也照样能调缩放。
     *
     * <p>{@link HudEditTarget#getToolbarSpec()} 保留为「可选的额外自定义工具」：声明且该 hudId 已
     * 注册工具栏工厂时，下游工具栏作为内层挂上（其 {@code scaleControls} 一律关闭，缩放入口唯一）；
     * 未注册工厂时只装配统一缩放入口。</p>
     *
     * @param target   编辑目标
     * @param content  预览内容根
     * @param toolbars 输出参数：本次装配出的全部工具栏节点（调用方据此拦截按下事件）
     * @return 最外层预览外框（放置 / clamp 口径的权威尺寸来源）
     */
    private HudToolbarLayer.Result mountLayer(HudEditTarget target, SceneNode content,
            List<SceneNode> toolbars) {
        final String hudId = target.getHudId();
        HudToolbarService service = HudToolbarService.getInstance();
        HudScaleState unified = service.scale(hudId);
        HudToolbarSpec customSpec = target.getToolbarSpec();
        HudWindowFactory customFactory = customSpec == null ? null : service.factory(hudId);

        HudToolbarLayer.Result base;
        if (customSpec != null && customFactory != null) {
            base = HudToolbarLayer.mount(rt, withoutScaleControls(customSpec), content, customFactory, unified);
            toolbars.add(base.toolbar());
        } else {
            if (customSpec != null) {
                LOG.warn("HUD 编辑预览声明了自定义工具栏规格但未注册外接工具栏工厂，仅装配统一缩放入口: id={}",
                        hudId);
            }
            base = HudToolbarLayer.passthrough(content);
        }
        // 统一缩放入口恒为最外层：即便下游没给任何工具栏规格，预览也一定带 - / 1:1 / +。
        HudToolbarLayer.Result layer = HudToolbarLayer.mount(rt, scaleRowSpec(customSpec),
                base.root(), rt -> SceneNode.row(), unified);
        toolbars.add(layer.toolbar());
        return layer;
    }

    /** 复制下游规格并关闭 scaleControls：缩放入口统一由外层提供，避免出现两个缩放行。 */
    private static HudToolbarSpec withoutScaleControls(HudToolbarSpec spec) {
        return HudToolbarSpec.builder(spec.getSide())
                .scaleControls(false)
                .gap(spec.getGap())
                .thickness(spec.getThickness())
                .visible(spec.getVisible())
                .publicButtonStyle(spec.getPublicButtonStyle())
                .build();
    }

    /** 统一缩放行规格：始终追加 - / 1:1 / +；按钮样式沿用下游配方（若有）以保持外观一致。 */
    private static HudToolbarSpec scaleRowSpec(HudToolbarSpec customSpec) {
        HudToolbarSpec.Builder builder = HudToolbarSpec.builder().scaleControls(true);
        if (customSpec != null) {
            builder.publicButtonStyle(customSpec.getPublicButtonStyle());
        }
        return builder.build();
    }

    /** @return 该 HUD 的统一缩放倍率（与宿主 / 打开态聊天屏读同一份状态；无状态时 1.0） */
    private static float unifiedFactor(String hudId) {
        HudScaleState state = HudToolbarService.getInstance().scale(hudId);
        return state == null ? 1.0F : state.factor();
    }

    private void unloadAll() {
        dragging = null;
        for (Preview preview : previews) {
            preview.dragging = false;
            preview.handle.dispose();
        }
        previews.clear();
        if (scope != null) {
            // 预览作用域回收：工厂在构建期建立的 bind/mount/on 与拖动 handler 一并退订。
            scope.dispose();
            scope = null;
        }
    }

    // ==================== 放置与拖动 ====================

    /**
     * 每帧把该预览的 overlay 相对倍率写进句柄：{@code s = target / hostScale}。
     *
     * <p>overlay 层面只认相对倍率：物理实绘 = overlay 逻辑尺寸 × hostScale × s = 逻辑尺寸 × target，
     * 于是「预览缩放自身」与「聊天屏倍率不串扰」同时成立。非有限或 ≤ 0 的比值不写，保持 entry
     * 默认 1.0F（{@link #scale} 已收敛为有限正数，此处只作防御）。</p>
     */
    private void applyRelativeScale(Preview preview) {
        float relative = unifiedFactor(preview.hudId) / scale;
        if (Float.isNaN(relative) || Float.isInfinite(relative) || relative <= 0F) {
            return;
        }
        preview.handle.setRelativeScale(relative);
    }

    /** 每帧把权威放置解析为预览浮层的 margin（物理盒 → overlay 逻辑 px，与 clamp 同口径）。 */
    private void applyPlacement(Preview preview) {
        AnchorRect rect = HudLayoutResolver.resolve(effectivePlacement(preview),
                viewportWidth, viewportHeight, outerWidth(preview), outerHeight(preview), insets);
        // 节点与输入是 overlay 逻辑 px（= 该 HUD 物理 px / target）：margin 除以自身倍率，
        // 不是宿主倍率——overlay 的实绘倍率已由相对倍率承担。
        float target = unifiedFactor(preview.hudId);
        preview.layer.root().setMargin((int) Math.floor(rect.getY() / target), 0, 0,
                (int) Math.floor(rect.getX() / target));
    }

    /** 生效放置：用户布局（编辑中 = 草稿）优先，否则目标的默认放置。 */
    private HudPlacement effectivePlacement(Preview preview) {
        HudPlacement placement = layoutService.placement(preview.hudId);
        return placement != null ? placement : preview.target.getDefaultPlacement();
    }

    /** 外框宽（物理像素）= 外框 logical 宽 × 该 HUD 统一倍率（与 clamp 同口径）。 */
    private int outerWidth(Preview preview) {
        return (int) Math.ceil(preview.layer.logicalOuterWidth(contentExtent(preview.layer.content(), true))
                * unifiedFactor(preview.hudId));
    }

    /** 外框高（物理像素）= 外框 logical 高 × 该 HUD 统一倍率（与 clamp 同口径）。 */
    private int outerHeight(Preview preview) {
        return (int) Math.ceil(preview.layer.logicalOuterHeight(contentExtent(preview.layer.content(), false))
                * unifiedFactor(preview.hudId));
    }

    /** 内容盒尺寸（logical px）：优先上一帧实测布局，首帧退回内容自身声明，最后退回最小值。 */
    private static int contentExtent(SceneNode content, boolean horizontal) {
        Object cached = content.getCachedLayout();
        if (cached instanceof LayoutBox) {
            int extent = horizontal ? ((LayoutBox) cached).getWidth() : ((LayoutBox) cached).getHeight();
            if (extent > 0) {
                return extent;
            }
        }
        int preferred = horizontal ? content.getPreferredWidth() : content.getPreferredHeight();
        return preferred > 0 ? preferred : 1;
    }

    private void onDown(Preview preview, SceneEvent event, SceneEventContext ctx) {
        if (event.getButton() != SceneMouseButton.LEFT || dragging != null) {
            return;
        }
        preview.dragging = true;
        dragging = preview;
        // 与聊天外框拖动同源：增量必须用 raw 指针（容器本身随动，局部坐标不可作增量基准）。
        // raw 指针已被路由按 overlay 相对倍率换算到 overlay 逻辑空间：× target（不是 hostScale）
        // 得到物理 px，与放置 / clamp 的物理口径一致。
        float target = unifiedFactor(preview.hudId);
        preview.originX = Math.round(ctx.getRawPointerX() * target);
        preview.originY = Math.round(ctx.getRawPointerY() * target);
        preview.originHadOverride = layoutService.placement(preview.hudId) != null;
        preview.originPlacement = effectivePlacement(preview);
        ctx.requestPointerCapture();
        ctx.stopPropagation();
    }

    private void onMove(Preview preview, SceneEvent event, SceneEventContext ctx) {
        if (dragging != preview) {
            return;
        }
        float target = unifiedFactor(preview.hudId);
        int dx = Math.round(ctx.getRawPointerX() * target) - preview.originX;
        int dy = Math.round(ctx.getRawPointerY() * target) - preview.originY;
        HudPlacement desired = preview.originPlacement.translate(dx, dy);
        // clamp 与 applyPlacement 同口径：用外框（内容 + 工具栏 gap/thickness），不是裸内容尺寸，
        // 否则拖到边界时工具栏仍会被推到视口外。
        HudPlacement clamped = HudLayoutResolver.clamp(desired, viewportWidth, viewportHeight,
                outerWidth(preview), outerHeight(preview), insets);
        layoutService.setDraft(preview.hudId, clamped);
        ctx.stopPropagation();
    }

    private void onUp(Preview preview, SceneEvent event, SceneEventContext ctx) {
        if (dragging != preview) {
            return;
        }
        endDrag(false);
        ctx.stopPropagation();
    }

    private void onCancel(Preview preview, SceneEvent event, SceneEventContext ctx) {
        if (dragging != preview) {
            return;
        }
        endDrag(true);
    }

    /** 结束拖动；rollback = true 时把草稿回滚到按下前状态（取消手势语义）。 */
    private void endDrag(boolean rollback) {
        Preview preview = dragging;
        if (preview == null) {
            return;
        }
        dragging = null;
        preview.dragging = false;
        if (!rollback) {
            return;
        }
        if (preview.originHadOverride) {
            layoutService.setDraft(preview.hudId, preview.originPlacement);
        } else {
            layoutService.clearDraft(preview.hudId);
        }
    }

    /** 单个目标的预览装配结果与手势状态。 */
    private static final class Preview {
        final HudEditTarget target;
        final String hudId;
        final SceneNode overlayRoot;
        final HudToolbarLayer.Result layer;
        final OverlayHandle handle;
        boolean dragging;
        int originX;
        int originY;
        HudPlacement originPlacement;
        boolean originHadOverride;

        Preview(HudEditTarget target, SceneNode overlayRoot, HudToolbarLayer.Result layer,
                OverlayHandle handle) {
            this.target = target;
            this.hudId = target.getHudId();
            this.overlayRoot = overlayRoot;
            this.layer = layer;
            this.handle = handle;
        }
    }
}
