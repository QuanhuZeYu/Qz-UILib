package club.heiqi.uilib.internal.chat3.view;

import java.util.Map;
import java.util.Objects;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.input.ChatInputBar;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.InputBinding;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.Binding;
import club.heiqi.uilib.ui.scene.runtime.SceneListHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 聊天容器组件(L3 组件层):外框(背景/描边/圆角/clip)+ 滚动消息列表 + 底部输入条。
 *
 * <p>容器动态尺寸 = 视口宽 × 1/4 × 视口高 × 1/2,由 {@link Result#setViewport(int,int)} 每帧
 * 同步(窗口缩放即时跟随)。消息列表复用 {@link ChatMessageList}(容器形态),输入条复用
 * {@link ChatInputBar}(SceneTextInput)。</p>
 *
 * <p><b>外框表面配方（G17/Container 液态玻璃口径）</b>：外框的 滤镜/底色/描边/描边宽/圆角 五项
 * 不再构造期静态设值，唯一写入者 = {@link SceneSurfaceBinder}（G20 起统一走通用表面绑定器）。
 * 配方以通用主题 {@link SceneTheme.Role#PANEL} 档为默认兜底（聊天设置未覆盖的字段随主题），
 * 既有聊天玻璃设置（开关/模糊/强度/玻璃 alpha 档与容器色板令牌）作为局部覆盖按第一优先级逐项
 * 覆盖材质字段——「设置开=保持既有玻璃观感、设置关=实色令牌逃生舱」语义不变，未新增另一份配置
 * 存储。设置变更经帧观察 Signal 按值去重（与 chat3.input 的动作外观桥接件同机制）、主题变更经
 * {@link SceneThemes} 作用域解析，两者都只重派生：节点身份不变、面板不重建、effect 数不增长。</p>
 *
 * <p><b>静态表面 + 浮雕豁免（P-05 落点）</b>：大面板不进浮雕通道的既有观感由配方上的
 * {@code reliefDisabled} 显式声明（{@code surfaceElevation} 恒 -1），不再是「绕开绑定器自己写」
 * 的隐式例外；配方四态同值 + 过渡时长 0 表达「静态表面」语义（容器不可命中，交互态本就不变），
 * 与迁移前的普通绘制通道逐值等价。绑定挂本组件自持的 {@link Owner} 作用域，随 {@link Result#dispose()}
 * 连同动画轨道一次性回收。</p>
 */
public final class ChatContainer {

    // 输入条区四周内边距取 ChatMarkdownSettings.INPUT_AREA_INSET_PX(唯一数值来源)：
    // 它同时是同心规则的减数(输入框圆角 = 容器圆角 − 内缩)，此处不再抄第二份 8。
    /** 容器内容区上内边距(px,设计稿 §2.3/§6.2:上 10)。 */
    private static final int CONTENT_PADDING_TOP_PX = 10;
    /** 容器内容区左右内边距(px,设计稿 §2.3/§6.2:左右 10)。 */
    private static final int CONTENT_PADDING_SIDE_PX = 10;
    /** 容器内容区下内边距(px,设计稿 §2.3/§6.2:下 4,留给滚动条视觉余量)。 */
    private static final int CONTENT_PADDING_BOTTOM_PX = 4;
    /** 容器外框描边宽度(px,既有静态值；属聊天局部配方覆盖项，主题不改)。 */
    private static final int CONTAINER_BORDER_WIDTH_PX = 1;

    /**
     * 聊天玻璃设置的容器面快照（值对象）。
     *
     * <p>只覆盖容器面<b>运行时可调</b>的 4 个设置源：开关 / 模糊半径 / 透镜强度 / 玻璃 alpha 档。
     * 容器底色 RGB、描边色、圆角是 {@link ChatMarkdownSettings} 内无 setter 的进程级令牌，
     * 不会在运行期变化，不入快照。本类不新增任何配置存储——快照每次现读设置，仅作为
     * 「设置已变」的按值去重触发器（与 chat3.input 的动作外观桥接件同机制——帧观察 + 按值去重）。</p>
     */
    private static final class GlassSnapshot {

        private final boolean enabled;
        private final int blurRadiusPx;
        private final float lensStrength;
        private final int containerAlpha;

        private GlassSnapshot(boolean enabled, int blurRadiusPx, float lensStrength, int containerAlpha) {
            this.enabled = enabled;
            this.blurRadiusPx = blurRadiusPx;
            this.lensStrength = lensStrength;
            this.containerAlpha = containerAlpha;
        }

        /** @return 当前聊天玻璃设置的快照（现读 {@link ChatMarkdownSettings}） */
        private static GlassSnapshot read() {
            return new GlassSnapshot(ChatMarkdownSettings.isGlassEnabled(),
                    ChatMarkdownSettings.getGlassBlurRadiusPx(),
                    ChatMarkdownSettings.getGlassLensStrength(),
                    ChatMarkdownSettings.getGlassContainerAlpha());
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof GlassSnapshot)) {
                return false;
            }
            GlassSnapshot that = (GlassSnapshot) other;
            return enabled == that.enabled
                    && blurRadiusPx == that.blurRadiusPx
                    && Float.compare(lensStrength, that.lensStrength) == 0
                    && containerAlpha == that.containerAlpha;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Boolean.valueOf(enabled), Integer.valueOf(blurRadiusPx),
                    Float.valueOf(lensStrength), Integer.valueOf(containerAlpha));
        }
    }

    /**
     * 帧观察快照信号：设置变化 → 配方重派生的唯一触发源。
     *
     * <p>Signal 按值去重，设置未变的帧不唤醒下游（无变化帧不新增重算，也不新增计时器）。
     * 静态共享跨 runtime 复用同一份通知——通知里只有设置值，没有节点与 runtime，不会串写。</p>
     */
    private static final Signal<GlassSnapshot> OBSERVED_GLASS = Signal.create(GlassSnapshot.read());

    /** 容器无禁用语义（不可命中也不可聚焦）：外框表面绑定恒启用，交互态取值恒定。 */
    private static final ReadableSignal<Boolean> SURFACE_ENABLED = Signal.create(Boolean.TRUE);

    /**
     * 外框表面配方信号：主题 PANEL 档基线（通用兜底）+ 聊天玻璃设置局部覆盖（第一优先级）。
     *
     * <p>构造期捕获主题来源信号（{@link SceneThemes#resolve} 的构建期约定：返回已安装主题信号
     * 本身或库默认常量，不自建派生单元），返回信号在派生期只读上游：主题切换与设置切换是两个
     * 独立失效源，任一变化只重算本配方，不重建节点。本方法只落一颗 {@link Computed}（其
     * recompute 单元由 {@link Result#dispose()} 显式回收，不留 orphan effect）；聊天设置覆盖
     * <b>不置空</b>主题字段：过渡时长/缘色档/悬停·按下·禁用状态档/前景策略等聊天设置未覆盖的
     * 字段全部随所属主题的 PANEL 角色配方——这就是「无显式聊天设置时容器用主题档」的落点
     * （聊天设置自身永远按既有语义表达，见 {@link #chatGlassOverride}）。</p>
     *
     * @param rt 宿主场景运行时
     * @return 配方派生信号（构造期不读值；随 Result.dispose 回收）
     */
    private static Computed<SceneSurfaceStyle> containerRecipeSignal(SceneRuntime rt) {
        final ReadableSignal<SceneTheme> theme = SceneThemes.resolve(rt);
        return Computed.create(() -> {
            OBSERVED_GLASS.get(); // 失效源①：聊天设置帧观察（按值去重）
            SceneTheme source = Objects.requireNonNull(theme.get(), "theme value");
            return chatGlassOverride(source.surface(SceneTheme.Role.PANEL)); // 失效源②：主题
        });
    }

    /**
     * 旧聊天玻璃设置 → {@link SceneSurfaceStyle} 配方参数的逐项映射（局部覆盖，第一优先级）。
     *
     * <p>映射表（旧写入点 → 配方字段）：</p>
     * <ul>
     *   <li>{@code isGlassEnabled() + getGlassBlurRadiusPx() + getGlassLensStrength()}
     *       → {@code backdrop}（开 = DARK_THIN 系液态玻璃按设置模糊/强度；关 = null 显式关闭滤镜）</li>
     *   <li>{@code getContainerBgArgb()} 的 RGB + {@code getGlassContainerAlpha()}
     *       → {@code idle.tint}（开 = 令牌 RGB 换玻璃 alpha 档；关 = 令牌实心档，逃生舱语义不变）</li>
     *   <li>{@code getContainerBorderArgb()} → {@code idle.edge}</li>
     *   <li>{@code getContainerCornerRadius()} → {@code cornerRadius}（覆盖主题档位）</li>
     *   <li>既有静态描边宽 1px → {@code borderWidth}</li>
     * </ul>
     *
     * <p>{@code idle.elevation} 保持主题基线值但桥接器<b>不消费</b>（外框 surfaceElevation 恒 -1，
     * 维持普通绘制 = 既有像素合同）；{@code idle.lensFactor} 置 1.0 表示设置透镜强度直通、
     * 无二次调制。</p>
     */
    private static SceneSurfaceStyle chatGlassOverride(SceneSurfaceStyle base) {
        boolean glass = ChatMarkdownSettings.isGlassEnabled();
        UiBackdrop backdrop = glass
                ? UiBackdrop.liquidGlass(UiGlassMaterial.DARK_THIN,
                        ChatMarkdownSettings.getGlassBlurRadiusPx(), ChatMarkdownSettings.getGlassLensStrength())
                : null;
        int containerBg = glass
                ? (ChatMarkdownSettings.getContainerBgArgb() & 0x00FFFFFF)
                        | (ChatMarkdownSettings.getGlassContainerAlpha() << 24)
                : ChatMarkdownSettings.getContainerBgArgb();
        // 静态表面：四态同值使交互态派生的结果恒定（容器 setHitTestable(false)，状态本就不变）；
        // 过渡时长 0 让绑定器的动画写退化为立即应用，与迁移前的普通绘制通道逐值等价。
        SceneSurfaceStyle.StateStyle surface = new SceneSurfaceStyle.StateStyle(containerBg,
                ChatMarkdownSettings.getContainerBorderArgb(),
                base.getIdle().getElevation(), 1.0F);
        return base.toBuilder()
                .backdrop(backdrop)
                .cornerRadius(ChatMarkdownSettings.getContainerCornerRadius())
                .borderWidth(CONTAINER_BORDER_WIDTH_PX)
                .idle(surface)
                .hovered(surface)
                .pressed(surface)
                .disabled(surface)
                .transitionMillis(0)
                // P-05：大面板暗边不回归——声明式关闭浮雕通道，不再靠「绕开绑定器」实现。
                .reliefDisabled(true)
                .build();
    }

    /** 容器装配结果:外框节点 + 生命周期句柄 + 输入条。 */
    public static final class Result {

        private final SceneNode root;
        private final SceneListHandle listHandle;
        private final Binding scrollBinding;
        private final Binding hintBinding;
        private final InputBinding hintInputBinding;
        private final Binding settingsBinding;
        private final Owner surfaceScope;
        private final Computed<SceneSurfaceStyle> surfaceRecipe;
        private final ChatScrollbar.Result scrollbar;
        private final ChatInputBar bar;
        private final ChatSceneController controller;
        /** 输入条行（编辑态用于拦截拖动起点，保证输入区优先命中）。 */
        private final SceneNode barRow;

        private Result(SceneNode root, SceneListHandle listHandle, Binding scrollBinding,
                Binding hintBinding, InputBinding hintInputBinding, Binding settingsBinding,
                Owner surfaceScope, Computed<SceneSurfaceStyle> surfaceRecipe,
                ChatScrollbar.Result scrollbar, ChatInputBar bar,
                ChatSceneController controller, SceneNode barRow) {
            this.root = root;
            this.listHandle = listHandle;
            this.scrollBinding = scrollBinding;
            this.hintBinding = hintBinding;
            this.hintInputBinding = hintInputBinding;
            this.settingsBinding = settingsBinding;
            this.surfaceScope = surfaceScope;
            this.surfaceRecipe = surfaceRecipe;
            this.scrollbar = scrollbar;
            this.bar = bar;
            this.controller = controller;
            this.barRow = barRow;
        }

        /** 释放列表与滚动绑定(屏幕关闭时)。 */
        public void dispose() {
            if (listHandle != null) {
                listHandle.dispose();
            }
            if (scrollBinding != null) {
                scrollBinding.dispose();
            }
            if (hintBinding != null) {
                hintBinding.dispose();
            }
            if (hintInputBinding != null) {
                hintInputBinding.dispose();
            }
            if (surfaceScope != null) {
                // 表面绑定及其动画轨道在本作用域内建立，一次性回收（含 motionDriver.remove）。
                surfaceScope.dispose();
            }
            if (settingsBinding != null) {
                settingsBinding.dispose();
            }
            if (surfaceRecipe != null) {
                // 配方 Computed 自带 recompute effect，不归 runtime 回收，必须显式注销。
                surfaceRecipe.dispose();
            }
            if (scrollbar != null) {
                scrollbar.dispose();
            }
            if (bar != null) {
                bar.dispose();
            }
        }

        /** @return 容器外框节点(动画 transform / 挂载目标) */
        public SceneNode root() {
            return root;
        }

        /**
         * 外框表面配方信号（包内测试接缝：断言「主题兜底字段 = 对应 Role 配方、聊天设置字段
         * = 局部覆盖值」；构造期不读值，首次 flush 后可安全解引用）。
         *
         * @return 配方只读信号
         */
        ReadableSignal<SceneSurfaceStyle> surfaceRecipe() {
            return surfaceRecipe;
        }

        /** @return 输入条组件(文本/历史/补全) */
        public ChatInputBar bar() {
            return bar;
        }

        /** @return 输入条行节点(编辑态拖动起点拦截用) */
        public SceneNode barRow() {
            return barRow;
        }

        /** 每帧同步动态尺寸(视口 1/4 × 1/2)与气泡最大宽。钳宽式唯一出处 =
         *  {@link ChatSceneController#bubbleMaxWidthPxFor(int)}(包内 static,A3 提取),
         *  容器路不再自算镜像式(A3 镜像残留收口);传入前 Math.max(1, width) 视口守卫
         *  原样保持,未知视口(0)时既有语义不变。 */
        public void setViewport(int width, int height) {
            setViewport(width, height, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        /** 宿主扣除外接工具栏后给出的内容预算；气泡排版同步使用真实内容宽。 */
        public void setViewport(int width, int height, int maxContentWidth, int maxContentHeight) {
            int contentWidth = Math.min(ChatMarkdownSettings.chatWidthFor(Math.max(1, width)), Math.max(1, maxContentWidth));
            root.setPreferredWidth(contentWidth);
            root.setPreferredHeight(Math.min(ChatMarkdownSettings.containerHeightFor(Math.max(1, height)),
                    Math.max(1, maxContentHeight)));
            controller.messageList().setBubbleMaxWidthPx(
                    ChatSceneController.bubbleMaxWidthPxForContent(contentWidth));
        }
    }

    private ChatContainer() {
    }

    /**
     * 装配容器并挂到调用方运行时。
     *
     * <p>工具栏自 P1/P2 增量起不再挂在容器内部：它经 HUD 级
     * {@link club.heiqi.uilib.ui.hud.api.HudToolbarService} 注册、由
     * {@link club.heiqi.uilib.ui.hud.api.HudToolbarLayer} 挂在内容盒外侧一条边，
     * 尺寸参与外框测量与放置（宿主/打开态页面共用同一份规格）。</p>
     *
     * @param rt          宿主场景运行时
     * @param controller  聊天场景控制器(数据源:组列表 / 滚动偏移 / 消息列表渲染器 / 帧时钟)
     * @param registry    消息节点 → 记录登记表(命中检测用,调用方持有)
     * @param initialText 输入框预填文本
     * @return 容器装配结果
     */
    public static Result mount(SceneRuntime rt, ChatSceneController controller,
            Map<SceneNode, ChatLineRecord> registry, String initialText) {
        // 外框表面（G17/Container）：滤镜/底色/描边/描边宽/圆角五项不再构造期静态设值——
        // 静态色板写入与配方绑定会竞争同一属性槽（施工手册 §1「同一个属性不能留两个绑定」），
        // 唯一写入者 = containerRecipeSignal 配方 + 通用表面绑定器（见下方 surfaceScope）。
        // 液态玻璃批次语义不变：容器与气泡同处一个 backdrop 批次，二者采样的是<strong>同一张
        // 世界画面</strong>（批次内主层 revision 冻结）——气泡的玻璃不会把容器已糊过的画面
        // 再糊一层。这正是 iOS 一个 visual effect 层级内共享背景采样的语义，层级差靠 alpha
        // 递进表达（容器 0x59 < 气泡 0x73）。
        SceneNode containerNode = SceneNode.column()
                .setHitTestable(false)
                // 设计稿 §2.3/§6.2:容器内容区上 10/左右 10/下 4(下留给滚动条视觉余量);
                // 不再复用 bubblePadding(5,10,5,10)——气泡区自身 padding 不受影响
                .setPadding(CONTENT_PADDING_TOP_PX,
                        CONTENT_PADDING_SIDE_PX,
                        CONTENT_PADDING_BOTTOM_PX,
                        CONTENT_PADDING_SIDE_PX)
                .setClipChildren(true);

        // 外框配方 = 主题 PANEL 档兜底（聊天设置未覆盖的字段随所属主题）+ 聊天玻璃设置局部
        // 覆盖（第一优先级：设置开=既有玻璃观感逐项保持，设置关=实色令牌逃生舱语义不变）。
        Computed<SceneSurfaceStyle> surfaceRecipe = containerRecipeSignal(rt);
        // 设置观察挂当前挂载作用域（关屏随 Owner/runtime 一并回收）；设置未变的帧快照按值
        // 去重，不唤醒配方，也不新增计时器。
        Binding settingsBinding = rt.bind(rt.__frameTimeNanos(),
                frame -> OBSERVED_GLASS.set(GlassSnapshot.read()));
        // 外框绑定统一走通用表面绑定器（G20）：配方已声明浮雕豁免 + 静态表面，
        // 不再是「绕开绑定器自持写入」的隐式例外。mount 发生在屏幕构造期（无外层 Owner），
        // 故用自持 Owner 作用域承接绑定与动画轨道，由 Result.dispose() 回收。
        Owner surfaceScope = new Owner();
        surfaceScope.run(() -> SceneSurfaceBinder.bind(rt, containerNode, surfaceRecipe,
                SURFACE_ENABLED, rt.interactionState(containerNode)));

        // ★ 滚动区行(与消息视口同级):[消息视口 flexGrow=1, 滚动条 column 右对齐]
        // 滚动条必须与视口并列(不进 scrollable 视口),否则随内容平移错位。
        SceneNode listRow = SceneNode.row().setHitTestable(false).setFillParentHeight(true);
        containerNode.appendChild(listRow);

        // 消息视口(scrollable,滚动偏移受体;内容列由控制器挂组)
        SceneNode listViewport = SceneNode.column()
                .setHitTestable(false)
                .setFlexGrow(1)
                .setScrollable(true)
                .setClipChildren(true);
        listRow.appendChild(listViewport);
        // 容器列表挂「容器全量信号」(controller.containerGroupsSignal()),不挂共享 HUD 信号:
        // 打开方向 COLLAPSING 阶段共享信号走 TTL 预算过滤,预算耗尽的历史消息在弹出动画期间
        // 不合成 → 文字在动画尾部瞬间刷出(2026-08-31 真机闪烁);容器信号恒全量即时呈现。
        SceneListHandle listHandle = controller.messageList().mount(rt, listViewport,
                controller.containerGroupsSignal(), ChatMessageList.Style.container(), registry,
                controller.frameMillisSignal());

        // 滚动唯一汇点:历史滚动偏移(px) → 视口滚动属性(结构版本驱动重算)。
        //
        // ★ 聊天↔scene 语义转换(真机「滚轮方向反」修复):chat3 滚动 = 自底部向上偏移
        // (0 = 贴底最新,越大越旧),scene scrollOffsetY = 自顶部向下偏移
        // (0 = 顶部,SceneGeometry.maxScrollY = 底部)。两者方向相反,故
        // 视口偏移 = maxScrollY - 聊天偏移(聊天偏移 clamp 到 [0, maxScrollY],向上最多滚到最旧)。
        // 由此滚轮向上(wheelDelta > 0 → history.scrollBy(+7))→ 聊天偏移↑ → 视口偏移↓ →
        // 内容下移 = 看上方旧消息,与原版 GuiNewChat.func_146229_b(scroll>0 查看更早消息)一致。
        // maxScrollY 是布局后几何:依赖 layoutDoneSignal 在内容变化帧布局完成后重算(与
        // SceneScrollbar 同模式,layout 未跑时兜底 0)。
        //
        // ★ 滚动权威统一在行域(V7 方案甲):滚动状态的唯一事实源 = ChatHistory.scrollOffset
        // (自底部向上的行数) + SmoothScroller 目标行。四类路径共享同一行域通道,不再互相
        // 覆盖权威:
        //   ① 滚轮(ChatInputSurface:releaseDrag + history.scrollBy(±行)+ notifyDataChanged);
        //   ② 回底(scrollToBottom:目标 0,即行域 0);
        //   ③ 贴底跟随(scrollOffsetPx 距底 ≤2 行 → 行域目标归 0,新消息自动贴底);
        //   ④ 拖动(setScrollOffset:scene px → round(chatPx/行高) 折算回行域再 scrollBy;
        //      onDragStart:snapTo 进入直通,display 恒等于目标行)。
        // 显示投影(行 × 18px,scrollOffsetPx)与真实几何 clamp(viewportScrollPx)只发生在
        // 本计算块,不写回权威 → 任何路径都不会把另一套「px 域」真值覆盖进行域。
        Computed<Integer> chatScrollPx = Computed.create(controller::scrollOffsetPx);
        // ★ V7 方案甲语义(行域权威 + 假想几何投影 + 真实几何 clamp):
        // chatPx = scrollOffsetPx() = round(显示行 × 18px) 是「行×18px」假想几何投影
        // (抽象单位,与真实行宽无关);maxScroll = SceneGeometry.maxScrollY(listViewport) 是
        // 真实内容几何(系统行 16 / 组头 16 / 正文行 18 / 气泡内边距与组距,可含多行换行)。
        // 双向 clamp(chatPx ∈ [0, maxScroll]) 保证:
        //   ① 底部恒等恒成立:chatPx=0(贴底)→ 视口偏移 = maxScroll(内容底),
        //      不依赖内容是否 18px 整倍(混合行高下同样成立);
        //   ② 顶部死区 ≤ 17px:chatPx 假想上限 = round(行数 × 18) 相对真实内容的残差
        //      ≤ 行高-1 = 17px,假想上限略超 maxScroll 时视觉已到顶,死区无感;
        //   ③ 无下溢/上溢:双向 clamp 使视口偏移恒 ∈ [0, maxScroll]。
        // 现状实现即方案甲语义,保持不动(契约测试见 ChatContainerTest)。
        Computed<Integer> viewportScrollPx = Computed.create(() -> {
            int chatPx = chatScrollPx.get().intValue();
            rt.layoutDoneSignal().get();
            Object cached = listViewport.getCachedLayout();
            if (!(cached instanceof LayoutBox)) {
                return Integer.valueOf(0); // flush 前 layout 未跑:兜底 0(下帧 layoutDone 校准)
            }
            int maxScroll = SceneGeometry.maxScrollY(listViewport);
            return Integer.valueOf(maxScroll - Math.max(0, Math.min(chatPx, maxScroll)));
        });
        Binding scrollBinding = rt.bind(viewportScrollPx,
                offset -> listViewport.setScrollOffsetY(offset.intValue()));

        // 滚动条:与视口同级 ROW 内右对齐,右内边距 2(贴容器右缘)。
        // 显示源 = viewportScrollPx(scene px,与视口绑定同源同值):thumb 底部 = 贴底最新、
        // 顶部 = 最旧(滚动条方向与聊天语义对齐)。
        // setScrollOffset 回调(scene px → 聊天行反向换算)与滚轮路径同源(history.scrollBy + notifyDataChanged)。
        // onDragStart:拖动接管时机 → 平滑器 snapTo 当前显示行(取消平滑、进入直通,拖动手感即时;
        // 拖动中每次 setScrollOffset 目标变化经平滑器直通直接到位,display 恒等于目标)。
        //
        // ★ V7 折算互逆(拖动路径):round(chatPx/行高) 与投影 round(display × 行高) 互为逆——
        // onDragStart snapTo 置位后进入直通(direct),scrollOffsetPx 投影的 display 恒为整数行,
        // round(round(x×18)/18) == x 严格成立(18px 整倍线域无损;拖动后视口偏移与拖动目标
        // 逐像素一致);非 18 整倍的残差由上方 clamp 吸收(≤17px,见 viewportScrollPx 注释)。
        // 滚轮/回底/贴底路径不经此折算:scrollBy 直接以「行」写权威,无 px 往返,天然无折损。
        // 注意:非整数 display 时互逆不成立,但只有滚轮平滑插值期 display 非整,且该期无人
        // 经 setScrollOffset 回写行域(权威 = SmoothScroller 行目标)→ 折算冲突不存在。
        // RC-06：px ↔ 行折算必须用「有效行高」（= 滚动投影行高），否则倍率下拖动位置漂移。
        final int lineHeight = Math.max(1, ChatFontMetrics.chatLineHeightPx(rt));
        ChatScrollbar.Result scrollbar = ChatScrollbar.create(rt, listViewport, viewportScrollPx,
                offset -> {
                    // scene px(已由 SceneScrollbar clamp 到 [0, maxScrollY])→ 聊天行(自底部向上);
                    // 这是唯一的「px → 行域」折算回写点,与滚轮/回底同写 history.scrollBy 通道
                    int maxScroll = SceneGeometry.maxScrollY(listViewport);
                    int chatPx = Math.max(0, maxScroll - offset.intValue());
                    int targetLines = (int) Math.round(chatPx / (double) lineHeight);
                    int current = controller.history().getScroll();
                    if (targetLines != current) {
                        // scrollBy 下限 0 clamp 与滚轮路径一致(上限由视口偏移 clamp 折算保证)
                        controller.history().scrollBy(targetLines - current);
                        controller.notifyDataChanged();
                    }
                },
                controller.frameMillisSignal(),
                offsetPx -> {
                    int maxScroll = SceneGeometry.maxScrollY(listViewport);
                    int chatPx = Math.max(0, maxScroll - offsetPx.intValue());
                    controller.smoothScroll().snapTo((int) Math.round(chatPx / (double) lineHeight));
                });
        scrollbar.column().setMargin(0, 2, 0, 0);
        listRow.appendChild(scrollbar.column());

        // 行域滚动上限回填(修「到顶后继续滚,后台仍在累加」):上限是几何事实,只有布局完成后的
        // 视口知道;但 clamp 必须写进权威 ChatHistory,而不是只靠上面 viewportScrollPx 的投影
        // clamp —— 投影 clamp 让渲染看起来正常,行域却无界增长,往回滚要先消费掉死值。
        // 终止性:仅当偏移被拉回时才 notifyDataChanged,重算后上限同值不再拉回 → 不形成回环。
        rt.bind(rt.layoutDoneSignal(), done -> {
            if (!(listViewport.getCachedLayout() instanceof LayoutBox)) {
                return; // 布局未就位:此刻 maxScrollY 读到 0,回填会把偏移误清零
            }
            // 必须**向上**取整:行域是整数行,round 会把不足半行的余量抹掉,导致
            // chatPx 永远够不到 maxScrollY —— 最上那几像素再也滚不出来(既有
            // clampedTopDoesNotUnderflow / viewportOffsetKeepsBottomIdentity 当场抓到)。
            // ceil 的代价是顶部最多多出不到一行(<18px)的死值,由投影 clamp 吸收,
            // 有界且不可感;而旧行为的累加是**无界**的。
            int ceilingLines = (int) Math.ceil(
                    SceneGeometry.maxScrollY(listViewport) / (double) lineHeight);
            if (controller.applyScrollCeilingLines(ceilingLines)) {
                controller.notifyDataChanged();
            }
        });

        // 输入条(容器内底部,设计稿 §6.2:输入条区高 40 贴容器底)。
        // ★ 固定高(40)是 COLUMN 容器的"先验固定兄弟":缺了它,ConstraintResolver 的
        //   grow 分配会因"固定兄弟高度无法先验"而对 listRow 回退 shrink-to-fit,
        //   消息区不撑满、输入条悬在内容高度之后(重心塌陷,B12 真机）。
        ChatInputBar bar = new ChatInputBar(rt, initialText);
        // K3 缺陷 F6②:输入条区四周 8px 内边距(设计稿 §2.3/§6.2)——修复前 divider 到输入框
        // 顶仅 5px、输入框贴边;8 + 输入框高 24 + 8 = 40 恰好占满输入条区高
        SceneNode barRow = SceneNode.row()
                .setHitTestable(false)
                .setCrossAxisAlign(CrossAxisAlign.CENTER)
                .setPadding(ChatMarkdownSettings.INPUT_AREA_INSET_PX)
                .setPreferredHeight(ChatMarkdownSettings.getInputBarHeightPx());
        barRow.appendChild(bar.root());
        containerNode.appendChild(barRow);

        // 输入条顶部分隔线(设计稿 §6.2:滚动消息区 → 1px 分隔线 → 输入条区;divider-input 8% 白)
        SceneNode divider = new SceneNode()
                .setHitTestable(false)
                .setPreferredHeight(1)
                .setBackgroundColor(ChatMarkdownSettings.getDividerInputArgb());
        containerNode.insertBefore(divider, barRow);

        // 输入条上方「↓ N 条新消息」提示(设计稿 §5.1 P1):unreadSignal > 0 时显示,点击回底。
        // 挂摘式显隐:文本节点空文本也占一行(拆分契约「至少一行」),故 unread=0 时移出树(零占位、
        // 不消费命中);显示时插到分隔线上方(设计稿 §6.2:提示位于 Divider 上方)。
        SceneNode hintNode = new SceneNode()
                .setHitTestable(false)
                .setFontSize(ChatMarkdownSettings.getNameFontSizePx())
                .setTextColor(ChatMarkdownSettings.getNewMessageHintArgb())
                .setAlignSelf(AlignSelf.CENTER);
        Binding hintBinding = rt.bind(controller.unreadSignal(), count -> {
            int n = count.intValue();
            if (n > 0) {
                if (hintNode.__getParent() == null) {
                    containerNode.insertBefore(hintNode, divider);
                }
                hintNode.setText("↓ " + n + " 条新消息");
                hintNode.setHitTestable(true);
            } else if (hintNode.__getParent() != null) {
                containerNode.removeChild(hintNode);
                hintNode.setHitTestable(false);
            }
        });
        InputBinding hintInputBinding = rt.on(hintNode, SceneEventType.POINTER_DOWN,
                (SceneEvent event, SceneEventContext ctx) -> controller.scrollToBottom());

        return new Result(containerNode, listHandle, scrollBinding, hintBinding, hintInputBinding,
                settingsBinding, surfaceScope, surfaceRecipe, scrollbar, bar, controller, barRow);
    }
}
