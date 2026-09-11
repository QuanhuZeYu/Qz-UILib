package club.heiqi.uilib.internal.chat3.input;

import java.awt.Desktop;
import java.net.URI;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.view.ChatLinkClick;
import club.heiqi.uilib.internal.chat3.view.ChatContainer;
import club.heiqi.uilib.internal.chat3.view.ChatHudWindow;
import club.heiqi.uilib.internal.chat3.view.ChatSceneController;
import club.heiqi.uilib.internal.chat3.view.ChatSurfaceAnimator;
import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudEditService;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudLayoutResolver;
import club.heiqi.uilib.ui.hud.api.HudLayoutService;
import club.heiqi.uilib.ui.hud.api.HudPlacement;
import club.heiqi.uilib.ui.hud.api.HudScaleState;
import club.heiqi.uilib.ui.hud.api.HudToolbarLayer;
import club.heiqi.uilib.ui.hud.api.HudToolbarService;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiRenderBackend;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglInputSource;
import club.heiqi.uilib.ui.scene.host.lwjgl.LwjglStateReader;
import club.heiqi.uilib.ui.scene.control.SceneDialog;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.ScenePortalHandle;

/**
 * 聊天输入屏的 scene 渲染面(L4 宿主层,薄壳):装配 {@link ChatContainer}(消息列表 + 输入条),
 * 只保留事件路由(滚轮/行点击)与开合动画。输入条/列表/容器的组装全部下沉到组件层。
 *
 * <p>开合动画由 {@link ChatSurfaceAnimator} 状态机驱动(设计稿 §4.1:弹入 = easeOutBack pop,
 * 关闭 = 140ms easeOutQuad 淡出+下滑,与 ChatSceneController.CLOSING 同参数同曲线);
 * 关闭完成回调不在渲染栈内触发,由屏幕 updateScreen 每 tick 经 {@link #tickCloseState()} 取走
 * (关屏 displayGuiScreen 会销毁本 surface,不能在 render 栈内执行)。</p>
 */
public final class ChatInputSurface extends AbstractSceneHostWidget
        implements ChatToolbar.Host, ChatHudEditIntent.Sink, HudEditService.Host {

    private static final Logger LOG = LogManager.getLogger("QzUILib Chat3Input");

    private final ChatSceneController controller;
    private final SceneNode root;
    private final ChatContainer.Result container;
    /** 外接工具栏层：聊天内容盒外侧一条边（HUD 级注册表取规格与工厂）。 */
    private final HudToolbarLayer.Result toolbarLayer;
    /** 屏幕树消息节点 → 记录(命中检测)。 */
    private final Map<SceneNode, ChatLineRecord> screenMessageNodes =
            new IdentityHashMap<SceneNode, ChatLineRecord>();
    /** 容器开合动画状态机(纯逻辑,时间由渲染帧/updateScreen 注入)。 */
    private final ChatSurfaceAnimator animator;
    /** 周期诊断帧计数(每 120 帧打印一次渲染视口,真机定位坐标系问题)。 */
    private int renderLogCounter;
    /** 当前「打开链接？」确认框(连点顶掉旧框;随 runtime.dispose 一并回收)。 */
    private ScenePortalHandle linkConfirm;

    /** 用户布局服务(会话内唯一事实源;打开态容器与关闭态 HUD 共用同一份放置)。 */
    private final HudLayoutService layoutService = HudLayoutService.getInstance();
    /** 编辑子模式信号(工具栏行切换 + 拖动启停 + 输入暂停)。 */
    private final Signal<Boolean> editing = Signal.create(Boolean.FALSE);
    /**
     * 当前聚焦的编辑目标(进入编辑时 = 触发者;退出编辑清空)。
     *
     * <p>聚焦只决定「恢复当前默认」作用于哪个 hudId:chat3 内置入口聚焦
     * {@link ChatHudWindow#HUD_ID},第三方经 {@code HudEditService.requestEdit(hudId)} 聚焦自己。
     * 空 = 非编辑态,读取方回退既有 chat3 语义(零回归)。</p>
     */
    private final Signal<String> editFocus = Signal.create(null);
    /** 编辑目标预览浮层(每目标一个 overlay 浮层;非编辑态不注册、零开销)。 */
    private final ChatHudEditPreviews previews;
    /** 「恢复当前默认」可用性(聚焦目标存在草稿或已提交覆盖时为真;无聚焦回退 chat3)。 */
    private final ReadableSignal<Boolean> canResetCurrent = Computed.create(() -> {
        layoutService.revision().get();
        return Boolean.valueOf(layoutService.hasDraftOverride(focusedHudId())
                || layoutService.hasCommittedOverride(focusedHudId()));
    });
    /** 「恢复全部默认」可用性(存在任一已提交覆盖时为真)。 */
    private final ReadableSignal<Boolean> canResetAll = Computed.create(() -> {
        layoutService.revision().get();
        return Boolean.valueOf(layoutService.hasCommittedOverrides());
    });
    /** 拖动状态:true = 本次按下已取得指针捕获(单一 gesture 到 UP/CANCEL)。 */
    private final boolean[] dragging = new boolean[1];
    /** 按下时指针屏幕绝对坐标(raw 层;容器随动,增量必须用 raw 而非局部坐标)。 */
    private final int[] dragOrigin = new int[2];
    /** 按下前的生效放置(取消手势时回滚)。 */
    private HudPlacement dragOriginPlacement;
    /** 按下前是否已有用户覆盖(决定取消时 clearDraft 还是回写原放置)。 */
    private boolean dragOriginHadOverride;
    /** 最近一帧宿主视口(logical px;拖动换算与放置解析用)。 */
    private int hostWidth = 1;
    private int hostHeight = 1;
    /** render 开始采样，整帧的绘制、命中与拖动共用。 */
    private float frameScale = 1F;

    public ChatInputSurface(String initialText) {
        super(new ChatScaledInputSource(new LwjglStateReader()));
        // 宿主负责启用并逐帧采样动画：玻璃按钮过渡与 tooltip 延时共用标准帧管线。
        runtime.__enableMotion();
        this.controller = ChatHudWindow.ensureRegistered();

        // 开合动画状态机:生产参数取自设计稿 §4.1 同源配置(pop 240 / closing 140 可配);
        // 挂起兜底 = closeTimeoutFor(closing):超时永远 ≥ closing+500,任何配置时长下
        // 关闭动画都完整播放(用户高层语义:关闭动画开始→渐入结束整体可配 500ms~5s,
        // 超时只能兜底渲染挂起,不得截断动画);构造即开始弹出动画(与旧 openAtMillis 同语义)
        this.animator = new ChatSurfaceAnimator(
                ChatMarkdownSettings.getPopAnimMillis(),
                ChatMarkdownSettings.getClosingAnimMillis(),
                ChatSurfaceAnimator.closeTimeoutFor(ChatMarkdownSettings.getClosingAnimMillis()));
        this.animator.startOpen(System.currentTimeMillis());

        // 根节点只做视口盒(padding 0):容器位置由统一 HUD 布局服务解析后写入 margin,
        // 默认放置 = BOTTOM_LEFT + margin,与历史 padding + mainAxis END 完全同值。
        root = SceneNode.column()
                .setHitTestable(true)
                .setFillParentHeight(true)
                .setPadding(0);

        // 工具栏宿主绑定必须先于外接层装配：HUD 级注册表的工厂在装配时取当前宿主。
        ChatHudWindow.attachToolbarHost(this);
        container = ChatContainer.mount(runtime, controller, screenMessageNodes, initialText);
        // 外接工具栏层（P1/P2 增量）：工具栏不再进容器内部，由 HUD 级注册表提供规格/工厂，
        // 挂在聊天内容盒外侧（默认下边）；未注册时直通（root 子树与旧行为一致）。
        toolbarLayer = HudToolbarService.getInstance().mountLayer(runtime, ChatHudWindow.HUD_ID,
                container.root());
        root.appendChild(toolbarLayer.root());
        // 链接点击:事件当场投递(不在 mouseClicked 里取账 —— CLICK 要到 UP 才合成)
        controller.setMessageLinkClickHandler(this::onSceneLinkClick);

        // 编辑子模式:容器自身在编辑态才可命中(空白/消息区按下 = 开始拖动并捕获);
        // 工具栏与输入条在 DOWN 停止冒泡,保证按钮/输入框优先命中,不被拖动夺走 gesture。
        runtime.bind(editing, value -> container.root().setHitTestable(Boolean.TRUE.equals(value)));
        runtime.on(container.root(), SceneEventType.POINTER_DOWN, this::onDragDown);
        runtime.on(container.root(), SceneEventType.POINTER_MOVE, this::onDragMove);
        runtime.on(container.root(), SceneEventType.POINTER_UP, this::onDragUp);
        runtime.on(container.root(), SceneEventType.POINTER_CANCEL, this::onDragCancel);
        // 工具栏在内容盒之外：按钮/空白按下在此停止冒泡，拖动只挂在内容根上，
        // 两者天然不抢事件（编辑态按钮与拖动区域各走各的命中链）。
        SceneNode toolbarNode = toolbarLayer.toolbar();
        if (toolbarNode != null) {
            runtime.on(toolbarNode, SceneEventType.POINTER_DOWN,
                    (event, ctx) -> ctx.stopPropagation());
        }
        runtime.on(container.barRow(), SceneEventType.POINTER_DOWN,
                (event, ctx) -> ctx.stopPropagation());

        // 公开编辑契约接线(规划 P3 增量):注册表增删驱动预览重建,编辑开关驱动预览挂载/卸载;
        // 非编辑态零注册。宿主在构造期注入(同一时刻只有当前打开的聊天屏是宿主)。
        previews = new ChatHudEditPreviews(runtime);
        runtime.bind(editing, value -> previews.setSessionActive(Boolean.TRUE.equals(value)));
        runtime.bind(HudEditService.getInstance().revision(), value -> previews.refreshTargets());
        HudEditService.getInstance().attachHost(this);
        ChatHudEditIntent.attach(this);

        // 滚轮滚动聊天历史(vanilla ±7/Shift±1 语义)。
        // 方向语义:wheelDelta > 0(滚轮向上)→ 正行数 → history.scrollBy(+) = 向旧消息
        // (scrollOffset 自底部向上,与原版 GuiNewChat.func_146229_b 正号同语义);
        // wheelDelta < 0(滚轮向下)→ 负行数 → 回最新底部。
        runtime.on(root, SceneEventType.SCROLL,
                (SceneEvent event, club.heiqi.uilib.ui.scene.input.SceneEventContext ctx) -> {
                    if (Boolean.TRUE.equals(editing.get())) {
                        return; // 编辑子模式:暂停历史滚动交互,滚轮不穿透到聊天
                    }
                    int wheel = wheelScrollLines(event.getWheelDelta(), event.isShiftDown());
                    if (wheel == 0) {
                        return;
                    }
                    // 滚轮 = 非拖动来源:退出拖动接管直通,恢复 120ms 平滑(拖动结束后的首滚轮不平滑回归)
                    controller.smoothScroll().releaseDrag();
                    controller.history().scrollBy(wheel);
                    controller.notifyDataChanged();
                });
    }

    /**
     * 滚轮增量 → 聊天滚动行数(原版 ±7/Shift±1 语义,包级供 headless 单测锁定符号)。
     *
     * <p>wheelDelta 符号遵循 {@link LwjglInputSource}(正 = 滚轮向上);返回正行数 = 向旧消息
     * (自底部向上偏移,原版 GuiNewChat.func_146229_b 正号同语义),负行数 = 向新消息回底。
     * 幅度 clamp 到 ±1 后:非 Shift × {@code scrollWheelLines}(默认 7),Shift ×1。</p>
     */
    static int wheelScrollLines(int wheelDelta, boolean shiftDown) {
        if (wheelDelta == 0) {
            return 0;
        }
        int wheel = Math.max(-1, Math.min(1, wheelDelta));
        if (!shiftDown) {
            wheel *= ChatMarkdownSettings.getScrollWheelLines();
        }
        return wheel;
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** @return 聊天 HUD 的统一缩放倍率(与 SceneHudHost 读同一份状态;无状态时 1.0) */
    private static float unifiedScaleFactor() {
        HudScaleState state = HudToolbarService.getInstance().scale(ChatHudWindow.HUD_ID);
        return state == null ? 1.0F : state.factor();
    }

    /** 每帧同步动态尺寸(视口 1/4 × 1/2)并推进开合动画(设计稿 §4.1);随后走标准帧管线。 */
    @Override
    public void render(int w, int h, UiRenderBackend ctx, int absX, int absY) {
        // 倍率真值 = 统一缩放状态(宿主/打开态/编辑预览同源);工具栏层只负责装配,不承载倍率。
        frameScale = unifiedScaleFactor();
        ((ChatScaledInputSource) inputSource).setScale(frameScale);
        hostWidth = Math.max(1, w);
        hostHeight = Math.max(1, h);
        applyPlacement(hostWidth, hostHeight);
        // 编辑态预览浮层:与聊天外框同一帧口径(放置解析用屏幕像素,节点 margin 用 logical px)。
        previews.frame(hostWidth, hostHeight, frameScale, ChatHudWindow.currentSafeInsets());
        container.setViewport(w, h, container.root().getPreferredWidth(), container.root().getPreferredHeight());
        if ((renderLogCounter++ % 120) == 0) {
            LOG.info("聊天输入屏渲染视口: w={}, h={}, chatWidthFor={}, containerHeightFor={}",
                    Integer.valueOf(w), Integer.valueOf(h),
                    Integer.valueOf(ChatMarkdownSettings.chatWidthFor(Math.max(1, w))),
                    Integer.valueOf(ChatMarkdownSettings.containerHeightFor(Math.max(1, h))));
        }
        // 开合动画统一由状态机驱动(设计稿 §4.1,与 ChatSceneController 同参数同曲线):
        // 弹入 = easeOutBack pop,关闭 = easeOutQuad 淡出+下滑;此处只推进状态与取输出,
        // 完成回调由屏幕 updateScreen 经 tickCloseState 在渲染栈外取走(关屏会销毁本 surface)
        long nowMillis = System.currentTimeMillis();
        animator.tick(nowMillis);
        // 动画施加在外框上：工具栏在内容盒之外，也必须随聊天整体弹入/收起（未注册时外框就是内容根）
        toolbarLayer.root().setTransform(animator.transform(nowMillis));
        toolbarLayer.root().setOpacity(animator.opacity(nowMillis));
        super.render(Math.max(1, (int) Math.floor(w / frameScale)),
                Math.max(1, (int) Math.floor(h / frameScale)), ctx.scaled(frameScale),
                Math.round(absX / frameScale), Math.round(absY / frameScale));
    }

    /** 屏幕打开:聚焦输入框 + 同步发送历史(委托输入条)。 */
    public void onOpened() {
        container.bar().onOpened();
    }

    /** 屏幕关闭:取消未完成编辑会话并释放容器句柄(列表 + 滚动绑定)。 */
    public void onClosed() {
        ChatHudEditIntent.detach(this);
        // 公开编辑契约:摘除宿主(按身份)并整体卸载预览浮层;焦点清空,不复用到下一屏。
        HudEditService.getInstance().detachHost(this);
        previews.dispose();
        editFocus.set(null);
        // 解绑工具栏宿主并置不可见：关闭态 HUD 形态不显示工具栏（规划 P1 语义）。
        ChatHudWindow.detachToolbarHost(this);
        if (Boolean.TRUE.equals(editing.get())) {
            endDrag(true);
            layoutService.cancelEdit();
            editing.set(Boolean.FALSE);
        }
        container.dispose();
    }

    // ==================== HUD 编辑子模式(P1/P2 最小闭环) ====================

    /** @return 是否处于 HUD 编辑子模式 */
    public boolean isEditing() {
        return Boolean.TRUE.equals(editing.get());
    }

    /**
     * 屏幕级 Esc 决策(由 {@link ChatInputScreen} 在原生键路径最先调用)。
     *
     * <p>拖动中 = 先取消当前手势并恢复按下前位置(返回 true,屏幕不关闭);无拖动 = 取消整个
     * 会话并退出编辑(返回 true)。非编辑态返回 false,交由聊天原有关闭流程处理。</p>
     *
     * @return true = 已消费本次 Esc
     */
    public boolean handleEscape() {
        if (!Boolean.TRUE.equals(editing.get())) {
            return false;
        }
        if (previews.cancelDrag()) {
            return true; // 预览拖动中:先取消当前手势并回到按下前位置(不退出编辑会话)
        }
        if (dragging[0]) {
            endDrag(true);
            return true;
        }
        cancelEdit();
        return true;
    }

    /**
     * 每帧把权威放置解析为外框 margin（统一 HUD 布局服务 → 宿主放置 → 既有 layout/paint）。
     *
     * <p>外框尺寸由 {@link HudToolbarLayer.Result#outerWidth(int)} /
     * {@link HudToolbarLayer.Result#outerHeight(int)} 给出（内容盒 + 挂载边上的 gap + 厚度），
     * 因此四边工具栏参与 placement/clamp，且不遮挡聊天主体。</p>
     */
    private void applyPlacement(int width, int height) {
        applyOuterPlacement(toolbarLayer, width, height, effectivePlacement(),
                ChatHudWindow.currentSafeInsets(), frameScale);
    }

    /**
     * 外框放置（打开态聊天屏与 headless 装配测试共用同一份口径）：外框 = 内容盒 + 挂载边
     * 工具栏，placement 与拖动 clamp 必须用同一份外框尺寸，否则两者会互相打架。
     *
     * @param layer            外接工具栏层
     * @param viewportWidth    宿主视口宽
     * @param viewportHeight   宿主视口高
     * @param placement        生效放置
     * @param insets           宿主安全区
     */
    static void applyOuterPlacement(HudToolbarLayer.Result layer, int viewportWidth, int viewportHeight,
            HudPlacement placement, HudInsets insets) {
        applyOuterPlacement(layer, viewportWidth, viewportHeight, placement, insets, layer.scaleFactor());
    }

    static void applyOuterPlacement(HudToolbarLayer.Result layer, int viewportWidth, int viewportHeight,
            HudPlacement placement, HudInsets insets, float scale) {
        int availableWidth = Math.max(1, (int) Math.floor(
                Math.max(1, viewportWidth - insets.getLeft() - insets.getRight()) / scale));
        int availableHeight = Math.max(1, (int) Math.floor(
                Math.max(1, viewportHeight - insets.getTop() - insets.getBottom()) / scale));
        if (layer.isVisible()) {
            int reserved = layer.spec().getGap() + layer.spec().getThickness();
            if (layer.spec().getSide().isHorizontalEdge()) availableHeight -= reserved;
            else availableWidth -= reserved;
        }
        // resolve 的返回盒会缩小尺寸，只有改位置会让尾部工具栏仍排在原始内容之后、落到屏幕外。
        // 先给工具栏预留 logical px，再把可用尺寸交回 scene 布局；缩回时从用户尺寸重算。
        layer.content().setPreferredWidth(Math.min(ChatMarkdownSettings.chatWidthFor(Math.max(1, viewportWidth)),
                Math.max(1, availableWidth)));
        layer.content().setPreferredHeight(Math.min(ChatMarkdownSettings.containerHeightFor(Math.max(1, viewportHeight)),
                Math.max(1, availableHeight)));
        AnchorRect rect = HudLayoutResolver.resolve(placement, viewportWidth, viewportHeight,
                scaledOuterWidth(layer, viewportWidth, scale), scaledOuterHeight(layer, viewportHeight, scale), insets);
        // 节点和输入仍为 logical px，只有宿主边界放大到屏幕。
        layer.root().setMargin((int) Math.floor(rect.getY() / scale), 0, 0,
                (int) Math.floor(rect.getX() / scale));
    }

    /** @return 外框宽 = 内容盒 + 挂载边工具栏（放置与拖动 clamp 共用的唯一口径） */
    static int outerWidthFor(HudToolbarLayer.Result layer, int viewportWidth) {
        return scaledOuterWidth(layer, viewportWidth, layer.scaleFactor());
    }

    /** @return 外框高 = 内容盒 + 挂载边工具栏（放置与拖动 clamp 共用的唯一口径） */
    static int outerHeightFor(HudToolbarLayer.Result layer, int viewportHeight) {
        return scaledOuterHeight(layer, viewportHeight, layer.scaleFactor());
    }

    private static int scaledOuterWidth(HudToolbarLayer.Result layer, int width, float scale) {
        int contentWidth = layer.content().getPreferredWidth();
        if (contentWidth <= 0) contentWidth = ChatMarkdownSettings.chatWidthFor(Math.max(1, width));
        return (int) Math.ceil(layer.logicalOuterWidth(contentWidth) * scale);
    }

    private static int scaledOuterHeight(HudToolbarLayer.Result layer, int height, float scale) {
        int contentHeight = layer.content().getPreferredHeight();
        if (contentHeight <= 0) contentHeight = ChatMarkdownSettings.containerHeightFor(Math.max(1, height));
        return (int) Math.ceil(layer.logicalOuterHeight(contentHeight) * scale);
    }

    /** @return 生效放置(用户覆盖优先,否则按注册规格算默认放置 = BOTTOM_LEFT + margin) */
    private HudPlacement effectivePlacement() {
        HudPlacement placement = layoutService.placement(ChatHudWindow.HUD_ID);
        return placement != null ? placement
                : HudPlacement.defaultOf(HudAnchor.BOTTOM_LEFT, ChatMarkdownSettings.getChatMarginPx());
    }

    private void onDragDown(SceneEvent event, SceneEventContext ctx) {
        if (!Boolean.TRUE.equals(editing.get()) || event.getButton() != SceneMouseButton.LEFT) {
            return;
        }
        dragging[0] = true;
        dragOrigin[0] = Math.round(ctx.getRawPointerX() * frameScale);
        dragOrigin[1] = Math.round(ctx.getRawPointerY() * frameScale);
        dragOriginHadOverride = layoutService.placement(ChatHudWindow.HUD_ID) != null;
        dragOriginPlacement = effectivePlacement();
        ctx.requestPointerCapture();
        ctx.stopPropagation();
    }

    private void onDragMove(SceneEvent event, SceneEventContext ctx) {
        if (!dragging[0]) {
            return;
        }
        int dx = Math.round(ctx.getRawPointerX() * frameScale) - dragOrigin[0];
        int dy = Math.round(ctx.getRawPointerY() * frameScale) - dragOrigin[1];
        HudPlacement desired = dragOriginPlacement.translate(dx, dy);
        // clamp 与 applyPlacement 同口径：用外框（内容 + 工具栏），不是裸内容尺寸，
        // 否则拖动到边界时工具栏仍会被推到视口外。
        HudPlacement clamped = HudLayoutResolver.clamp(desired, hostWidth, hostHeight,
                scaledOuterWidth(toolbarLayer, hostWidth, frameScale),
                scaledOuterHeight(toolbarLayer, hostHeight, frameScale),
                ChatHudWindow.currentSafeInsets());
        layoutService.setDraft(ChatHudWindow.HUD_ID, clamped);
        ctx.stopPropagation();
    }

    private void onDragUp(SceneEvent event, SceneEventContext ctx) {
        if (!dragging[0]) {
            return;
        }
        endDrag(false);
        ctx.stopPropagation();
    }

    private void onDragCancel(SceneEvent event, SceneEventContext ctx) {
        endDrag(true);
    }

    /** 结束拖动;rollback = true 时把草稿回滚到按下前状态(取消手势语义)。 */
    private void endDrag(boolean rollback) {
        if (!dragging[0]) {
            return;
        }
        dragging[0] = false;
        if (!rollback) {
            return;
        }
        if (dragOriginHadOverride) {
            layoutService.setDraft(ChatHudWindow.HUD_ID, dragOriginPlacement);
        } else {
            layoutService.clearDraft(ChatHudWindow.HUD_ID);
        }
    }

    private void restoreInputFocus() {
        container.bar().refocus();
    }

    /** 编辑态暂停聊天输入:文本桥旁路直接调 pushText,必须在 surface 层拦截。 */
    @Override
    public void pushText(String text) {
        if (Boolean.TRUE.equals(editing.get())) {
            return;
        }
        super.pushText(text);
    }

    @Override
    public void onKeyTyped(char typedChar, int keyCode) {
        if (Boolean.TRUE.equals(editing.get())) {
            return;
        }
        super.onKeyTyped(typedChar, keyCode);
    }

    // ==================== ChatToolbar.Host ====================

    @Override
    public ReadableSignal<Boolean> editing() {
        return editing;
    }

    @Override
    public ReadableSignal<Boolean> canResetCurrent() {
        return canResetCurrent;
    }

    @Override
    public ReadableSignal<Boolean> canResetAll() {
        return canResetAll;
    }

    @Override
    public void finishEdit() {
        if (!Boolean.TRUE.equals(editing.get())) {
            return;
        }
        endDrag(false);
        layoutService.commitEdit();
        editing.set(Boolean.FALSE);
        editFocus.set(null);
        restoreInputFocus();
    }

    @Override
    public void cancelEdit() {
        if (!Boolean.TRUE.equals(editing.get())) {
            return;
        }
        endDrag(true);
        layoutService.cancelEdit();
        editing.set(Boolean.FALSE);
        editFocus.set(null);
        restoreInputFocus();
    }

    @Override
    public void resetCurrent() {
        // 公开契约:重置当前 = 聚焦目标;无聚焦回退 chat3 自身(内置入口的既有语义)。
        layoutService.resetDraft(focusedHudId());
    }

    @Override
    public void resetAll() {
        layoutService.resetAllDraft();
    }

    // ==================== ChatHudEditIntent.Sink ====================

    /** chat3 内置「编辑 HUD」入口:进入编辑并聚焦聊天 HUD 自身(既有语义零回归)。 */
    @Override
    public void requestEnterEdit() {
        editFocus.set(ChatHudWindow.HUD_ID);
        enterEditMode();
    }

    // ==================== HudEditService.Host ====================

    /**
     * 第三方编辑入口(公开契约):进入编辑并聚焦该目标。
     *
     * <p>首次进入调用 {@link HudLayoutService#beginEdit()}(一次会话覆盖所有目标);
     * 已编辑时只切换聚焦,不重置本次会话草稿。</p>
     */
    @Override
    public void requestEnterEdit(String hudId) {
        if (hudId == null || hudId.trim().isEmpty()) {
            return;
        }
        editFocus.set(hudId);
        enterEditMode();
    }

    @Override
    public ReadableSignal<String> focus() {
        return editFocus;
    }

    /** 进入编辑子模式(首次 = 开始草稿会话;已编辑 = 幂等)。 */
    private void enterEditMode() {
        if (Boolean.TRUE.equals(editing.get())) {
            return;
        }
        layoutService.beginEdit();
        editing.set(Boolean.TRUE);
    }

    /** @return 聚焦目标;非编辑态/未聚焦回退 chat3 自身(既有「恢复当前默认」语义)。 */
    private String focusedHudId() {
        String focused = editFocus.get();
        return focused == null ? ChatHudWindow.HUD_ID : focused;
    }

    /**
     * 容器收回动画完成、真正关屏后调用:委托控制器直接切 HUD(forceHud,跳过机器
     * CLOSING 空窗,气泡立即挂回);CLOSING 期间收到打开请求(pendingOpen)的折算由
     * 控制器状态机按设计稿 §4.2 处理。
     */
    public void notifyScreenClosed() {
        controller.closeToHudImmediately();
    }

    /**
     * 请求关闭(播放容器 CLOSING 动画):首次请求进入 CLOSING 并注册完成回调;
     * 动画期间重复请求幂等返回同一请求(不重置动画、不重复注册)。
     *
     * @param onCloseComplete 关闭动画完成回调(为 null 则只播动画不回调)
     * @return 本次关闭请求令牌(重入时 = 旧令牌)
     */
    public ChatSurfaceAnimator.CloseRequest requestClose(Runnable onCloseComplete) {
        long nowMillis = System.currentTimeMillis();
        // 窗体过渡窗口开启:关闭动画开始 → 内容冻结(消息只入数据层,树/布局/enter/过期
        // 不响应),窗体整体动画独占画面;结束后(稳态)一次性应用(窗体动画抽象 2026-08-29)
        controller.beginCloseTransition(nowMillis);
        return animator.requestClose(onCloseComplete, nowMillis);
    }

    /** @return 关闭动画是否已请求/已完成(提交路径防重入用:动画期间重复 Enter 不重发)。 */
    public boolean isClosePending() {
        return animator.isClosing() || animator.isClosed();
    }

    /**
     * 屏幕级推进(updateScreen 每 tick 调用,渲染栈外):推进关闭动画状态,并在完成后取走
     * 完成回调触发(关屏 displayGuiScreen → onGuiClosed → 容器销毁,不能在 render 栈内执行);
     * 渲染停滞时超时兜底(500ms)也在此强制完成,不放任屏幕卡死。
     */
    public void tickCloseState() {
        long nowMillis = System.currentTimeMillis();
        animator.tick(nowMillis);
        Runnable closeCallback = animator.takeCloseCallback();
        if (closeCallback != null) {
            closeCallback.run();
        }
    }

    /** 提交文本(trim 后);空串返回空。 */
    public String takeText() {
        return container.bar().takeText();
    }

    /** 提交文本(trim 后);空串返回 null 且不入发送历史,非空记录历史并返回消息文本。 */
    public String submitText() {
        return container.bar().submitText();
    }

    /** 记录已发送(发送路径增量同步)。 */
    public void recordSent(String message) {
        container.bar().recordSent(message);
    }

    /** 历史回显(委托输入条)。 */
    public void recallHistory(int direction) {
        container.bar().recallHistory(direction);
    }

    /** Tab 补全(委托输入条;direction +1 正向 Tab,-1 Shift+Tab 反向)。 */
    public void autocomplete(int direction) {
        container.bar().autocomplete(direction);
    }

    /** 非 Tab 键清补全循环态(原版 GuiChat:91;委托输入条)。 */
    public void clearCompletionCycle() {
        container.bar().clearCompletionCycle();
    }

    /** PageUp/PageDown 聊天区翻页(可见行数 - 1;+1 向旧消息,-1 向新消息)。 */
    public void pageScroll(int direction) {
        int page = Math.max(1, controller.visibleLineCount() - 1);
        // 与滚轮路径同源:退出拖动接管直通后按行滚动,再通知数据变更
        controller.smoothScroll().releaseDrag();
        controller.history().scrollBy(direction > 0 ? page : -page);
        controller.notifyDataChanged();
    }

    /** 服务端补全响应(委托输入条)。 */
    public void applyAutocompleteResponse(String[] options) {
        container.bar().applyAutocompleteResponse(options);
    }

    /**
     * 链接点击回投（scene CLICK 事件当场调用，见 {@link ChatSceneController#setMessageLinkClickHandler}）。
     *
     * <p>优先级：服务端显式下发的 click 事件 > 我们自己链接化出的跨度。前者保持原版语义
     * 不动（RUN_COMMAND / SUGGEST_COMMAND 是命令注入，加确认会改变既有行为，故**不弹窗**）；
     * 但凡是**打开浏览器**，两条来源一律先过确认框。</p>
     *
     * <p>后者是为了玩家手打的裸 URL：原版 {@code IChatComponent} 上不带 clickEvent，
     * 服务端也没下发可点区域，原来点了完全没反应。</p>
     *
     * <p><b>既不接收坐标，也不接收"稍后再取"的账。</b>坐标不可用（MC 回调是 guiScale 缩放值，
     * chat3 几何是物理像素，不可无损换算），账更不能用：CLICK 在 POINTER_UP 才合成，而
     * {@code mouseClicked} 在 POINTER_DOWN 就跑了 —— 在 DOWN 里取账等于每笔都慢一拍，
     * 真机就是「要点第二下才有效」。</p>
     */
    void onSceneLinkClick(ChatLinkClick hit) {
        if (Boolean.TRUE.equals(editing.get())) {
            return; // 编辑子模式:暂停消息链接交互(拖动/点击都不应打开外链)
        }
        IChatComponent component = hit.component();
        ClickEvent click = component == null ? null
                : component.getChatStyle().getChatClickEvent();
        if (click != null) {
            Minecraft mc = Minecraft.getMinecraft();
            switch (click.getAction()) {
                case RUN_COMMAND:
                    if (mc != null && mc.thePlayer != null) {
                        mc.thePlayer.sendChatMessage(click.getValue());
                    }
                    return;
                case SUGGEST_COMMAND:
                    container.bar().setText(click.getValue());
                    return;
                case OPEN_URL:
                    confirmAndOpenUrl(click.getValue());
                    return;
                default:
                    return;
            }
        }
        if (hit.url() != null) {
            confirmAndOpenUrl(hit.url());
        }
    }

    /**
     * 打开外链前先弹确认框（用户裁定：点链接要真能开浏览器，且必须带确认弹窗）。
     *
     * <p>弹窗用本仓自有 {@link SceneDialog#confirm}，不用原版 {@code GuiConfirmOpenLink}
     * ——「与原版对齐」指体感对齐，实现必须落在自有抽象内（工作站规范）。</p>
     *
     * <p>连点不叠窗：新框先 dispose 旧框（{@code ScenePortalHandle.dispose} 幂等，
     * 已随 runtime 释放的也安全）。原版 {@code gameSettings.chatLinks} 关闭时完全不响应，
     * 连弹窗都不出现 —— 与原版「链接不可用」口径一致。</p>
     */
    private void confirmAndOpenUrl(final String url) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || !mc.gameSettings.chatLinks
                || url == null || url.isEmpty()) {
            return;
        }
        if (linkConfirm != null) {
            linkConfirm.dispose();
        }
        linkConfirm = SceneDialog.confirm(runtime, "打开链接？", url, new Runnable() {
            @Override
            public void run() {
                openUrl(url);
            }
        });
    }

    /** 链接打开(vanilla chatLinks 设置门控)。 */
    private static void openUrl(String url) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || !mc.gameSettings.chatLinks) {
            return;
        }
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception | Error failure) {
            // 不能只挡 Exception：没有桌面会话时 AWT 桌面子系统集成失败抛的是 Error
            // （InternalError / UnsatisfiedLinkError）。issue #71 的同一课：AWT 的失败形态不限于 Exception。
            LOG.warn("聊天链接打开失败: {}", failure.toString());
        }
    }
}
