package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.IChatComponent;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.layout.TextLayoutService;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatHistory;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatCardComposer;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageGroupModel;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageGrouper;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.runtime.SceneListHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 聊天 3.0 场景控制器(L3 渲染层状态中枢):数据 + 信号 + 双形态 scene 树。
 *
 * <p>结构/动画解耦:</p>
 * <ul>
 *   <li>组列表 = Computed(contentVersion) → forEach 构建组节点(声明式 diff);</li>
 *   <li>淡出 = 组节点内 Computed(frameMillis) 绑定,PAINT 级颜色烘焙(零结构协调);</li>
 *   <li>形态切换 = 状态机阶段驱动树根重建(HUD 气泡树 ↔ 容器树),动画 = root transform 平移;</li>
 *   <li>HUD 形态过期组(12s+淡出结束)在 tick 中移除(结构级),新消息始终堆在底部。</li>
 * </ul>
 *
 * <p>渲染驱动点 = 接线层每帧调 {@link #tick(long)}(S4 接 drawChat);不依赖 HUD 服务异步帧循环。</p>
 */
public final class ChatSceneController {

    /** 本地玩家名提供者(视图模型不依赖 Minecraft;生产 = mc.thePlayer,测试注入)。 */
    public interface SelfNameProvider {
        /** @return 本地玩家名;null = 无本地玩家(全部按他人处理) */
        String selfName();
    }

    /** 生产度量注入:UILib TextLayoutService 同源(段解析 + 段宽求和,与渲染推进同口径)。 */
    public static ChatLineLayouter.Measure uiLibMeasure() {
        final FontService fontService = FontService.getInstance();
        final TextLayoutService service = fontService.getTextLayoutService();
        return new ChatLineLayouter.Measure() {
            @Override
            public float advance(String text, int fontSizePx) {
                float total = 0.0F;
                for (TextSegment segment : service.parseSegments(text, 0xFFFFFFFF)) {
                    total += (float) service.getSegmentWidth(segment, fontSizePx);
                }
                return total;
            }

            @Override
            public int epoch() {
                return fontService.getRuntimeVersion();
            }
        };
    }

    /** 生产本地玩家名读取。 */
    public static SelfNameProvider mcSelfName() {
        return new SelfNameProvider() {
            @Override
            public String selfName() {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc == null || mc.thePlayer == null) {
                    return null;
                }
                return mc.thePlayer.getCommandSenderName();
            }
        };
    }

    /** 生产段解析:TextLayoutService 解析 § 样式码。 */
    public static ChatMessageList.SegmentParser uiLibSegmentParser() {
        final TextLayoutService service = FontService.getInstance().getTextLayoutService();
        return new ChatMessageList.SegmentParser() {
            @Override
            public List<TextSegment> parse(String text, int baseColor) {
                return service.parseSegments(text, baseColor);
            }
        };
    }

    /**
     * 生产段宽度度量:TextLayoutService.getSegmentWidth 与渲染推进同源
     * (链接命中区域行内定位用,T6a)。
     */
    public static ChatMessageList.SegmentMeasurer uiLibSegmentMeasurer() {
        final TextLayoutService service = FontService.getInstance().getTextLayoutService();
        return new ChatMessageList.SegmentMeasurer() {
            @Override
            public float widthOf(TextSegment segment, int fontSizePx) {
                return (float) service.getSegmentWidth(segment, fontSizePx);
            }
        };
    }

    private final ChatHistory history = new ChatHistory();
    private final MessageGrouper grouper = new MessageGrouper();
    private final ChatLineLayouter.Measure measure;
    private final SelfNameProvider selfNameProvider;
    private final ChatMessageList.SegmentParser segmentParser;
    /** 段宽度度量(null = 关闭 URL 链接化;生产恒注入,T6a)。 */
    private final ChatMessageList.SegmentMeasurer segmentMeasurer;
    private ChatLineLayouter layouter;
    /** 系统消息行切分器(font-system 12px 口径,K3 三轮)。 */
    private ChatLineLayouter systemLayouter;
    private ChatCardComposer composer;
    private ChatMessageList messageList;

    /** 结构版本(消息/滚动/设置变化 +1,驱动组列表与树重建)。 */
    private final Signal<Integer> contentVersion = Signal.create(Integer.valueOf(0));
    /** 帧时钟(wall millis,每渲染帧由接线层推进,驱动淡出/动画)。 */
    private final Signal<Long> frameMillis = Signal.create(Long.valueOf(0L));
    /** 聊天打开目标(接线层写入)。 */
    private final Signal<Boolean> chatOpen = Signal.create(Boolean.FALSE);
    /** 形态阶段(状态机输出,信号化供绑定追踪)。 */
    private final Signal<DisplayStateMachine.Phase> phaseSignal =
            Signal.create(DisplayStateMachine.Phase.HUD);
    private final DisplayStateMachine machine = new DisplayStateMachine();

    /** 显示行平滑器(T5b):history 目标行 → 120ms easeOutQuad 插值显示行(滚动唯一显示源)。 */
    private final SmoothScroller smooth = new SmoothScroller();
    /** 未读新消息计数(设计稿 §5.1「↓ N 条新消息」;HUD 形态不参与,容器滚动重算驱动)。 */
    private final Signal<Integer> unreadSignal = Signal.create(Integer.valueOf(0));
    /** 未读计数镜像(信号写入去重:仅变化时 set)。 */
    private int unreadCount = 0;
    /** 上次滚动重算所见历史行数(新消息增量判定 = history.size() 增量;删除/清空为负不计)。
     *  -1 = 首次物化前:历史基线未校准(首次重算只记录不计数,历史消息不误计为未读)。 */
    private int lastSeenSize = -1;

    /** 网络线程数据脏标记(主线程 tick 冲刷为版本号)。 */
    private volatile boolean dataDirty;

    /** HUD 形态过期移除阈值(latestMillis 低于此值的组不再进树;主线程 tick 推进)。 */
    private long expiredThreshold = 0L;

    /** HUD 高度裁剪阈值(设计稿 §3.1):堆叠超限时 latestMillis 低于此值的组立即剔除,
     *  不等 TTL(结构级、只进不退——被裁组不复活)。 */
    private long heightTrimThreshold = 0L;
    /** 高度裁剪评估去重(内容版本 + 视口高;无变化跳过,常规帧零开销)。 */
    private long lastTrimContentVersion = -1L;
    private int lastTrimViewportHeight = -1;

    /** buildContent 后持有:树根/挂载点/运行时与当前形态(树重建与动画绑定用)。 */
    private SceneRuntime runtime;
    private SceneNode root;
    private SceneNode mount;
    /** 当前树是否 HUD 气泡流(阶段切换时驱动树重建)。 */
    private boolean hudTreeBuilt;

    /** HUD 形态树的组列表句柄(树重建时释放,防句柄累积)。 */
    private SceneListHandle listHandle;

    /** 消息节点 → 记录(命中检测查询;树重建时清空,离树节点惰性清理)。 */
    private final Map<SceneNode, ChatLineRecord> messageNodes =
            new IdentityHashMap<SceneNode, ChatLineRecord>();

    /** 宿主视口(逻辑 px,渲染帧由接线层写入;命中检测窗口原点推导用)。 */
    private int hostViewportWidth;
    private int hostViewportHeight;

    /** 以生产 UILib 度量创建(真机路径)。 */
    public ChatSceneController() {
        this(uiLibMeasure(), mcSelfName(), uiLibSegmentParser(), uiLibSegmentMeasurer());
    }

    /**
     * 以指定依赖创建(headless 测试注入确定性度量/玩家名/段解析;链接化关闭)。
     */
    public ChatSceneController(ChatLineLayouter.Measure measure, SelfNameProvider selfNameProvider,
            ChatMessageList.SegmentParser segmentParser) {
        this(measure, selfNameProvider, segmentParser, null);
    }

    /**
     * 以指定依赖创建(headless 测试注入确定性度量/玩家名/段解析/段宽度度量;度量注入后启用 URL 链接化)。
     */
    public ChatSceneController(ChatLineLayouter.Measure measure, SelfNameProvider selfNameProvider,
            ChatMessageList.SegmentParser segmentParser, ChatMessageList.SegmentMeasurer segmentMeasurer) {
        if (measure == null || selfNameProvider == null || segmentParser == null) {
            throw new IllegalArgumentException("依赖不能为空");
        }
        this.measure = measure;
        this.selfNameProvider = selfNameProvider;
        this.segmentParser = segmentParser;
        this.segmentMeasurer = segmentMeasurer;
    }

    // ==================== 数据访问 ====================

    /** @return 聊天历史(接线层写入) */
    public ChatHistory history() {
        return history;
    }

    /** @return 聊天打开目标状态 */
    public boolean isChatOpen() {
        return chatOpen.get().booleanValue();
    }

    /** @return 容器可视行数(func_146232_i 用,近似 = 容器高/行高) */
    public int visibleLineCount() {
        return Math.max(1, ChatMarkdownSettings.containerHeightFor(hostViewportHeight)
                / ChatMarkdownSettings.getChatLineHeightPx());
    }

    /** @return 聊天高度(func_146246_g 用,容器高,随视口动态) */
    public int chatHeight() {
        return ChatMarkdownSettings.containerHeightFor(hostViewportHeight);
    }

    /**
     * 宿主视口尺寸(逻辑 px;渲染帧由接线层写入)。
     *
     * @param width  视口宽
     * @param height 视口高
     */
    public void setHostViewport(int width, int height) {
        int newWidth = Math.max(1, width);
        int newHeight = Math.max(1, height);
        if (newWidth != hostViewportWidth || newHeight != hostViewportHeight) {
            hostViewportWidth = newWidth;
            hostViewportHeight = newHeight;
            // 视口变化(窗口缩放/分辨率切换):动态尺寸变化,重建形态树
            if (runtime != null && root != null) {
                rebuildTree();
            }
        }
    }

    /**
     * 注册表命中(纯函数,输入屏幕复用):节点绝对盒(全屏原点 0,0)包含点 → 组件。
     *
     * @param registry 消息节点 → 记录(离树节点惰性清理)
     * @param x        命中点 x(逻辑 px)
     * @param y        命中点 y
     * @return 命中的消息组件;未命中返回 null
     */
    public static IChatComponent hitTestInRegistry(Map<SceneNode, ChatLineRecord> registry, int x, int y) {
        List<SceneNode> stale = null;
        for (Map.Entry<SceneNode, ChatLineRecord> entry : registry.entrySet()) {
            SceneNode node = entry.getKey();
            if (node.__getParent() == null) {
                if (stale == null) {
                    stale = new ArrayList<SceneNode>();
                }
                stale.add(node);
                continue;
            }
            AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
            if (x >= box.getX() && x < box.getX() + box.getWidth()
                    && y >= box.getY() && y < box.getY() + box.getHeight()) {
                return entry.getValue().getComponent();
            }
        }
        if (stale != null) {
            for (SceneNode node : stale) {
                registry.remove(node);
            }
        }
        return null;
    }

    /**
     * 命中检测(func_146236_a 转发):点 → 消息组件(事件链经原版组件回投)。
     *
     * <p>窗口原点推导:BOTTOM_LEFT 锚点 + 边距,底边 = 视口高 - 边距 - 根高(chrome=false
     * 无外壳;聊天窗口 stackOrder 最底无堆叠偏移——S6 真机校准)。</p>
     *
     * @param x 命中点 x(逻辑 px,scaled resolution 口径)
     * @param y 命中点 y
     * @return 命中的消息组件;未命中返回 null
     */
    public IChatComponent hitTest(int x, int y) {
        if (root == null || hostViewportWidth <= 0 || hostViewportHeight <= 0) {
            return null;
        }
        // 注册表命中(节点相对窗口根)+ 窗口原点平移:BOTTOM_LEFT 锚点 + 边距
        AnchorRect rootBox = SceneGeometry.absoluteBox(root, 0, 0);
        int margin = ChatMarkdownSettings.getChatMarginPx();
        int rootAbsX = margin;
        int rootAbsY = hostViewportHeight - margin - rootBox.getHeight();
        for (Map.Entry<SceneNode, ChatLineRecord> entry : messageNodes.entrySet()) {
            SceneNode node = entry.getKey();
            if (node.__getParent() == null) {
                continue;
            }
            AnchorRect box = SceneGeometry.absoluteBox(node, rootAbsX, rootAbsY);
            if (x >= box.getX() && x < box.getX() + box.getWidth()
                    && y >= box.getY() && y < box.getY() + box.getHeight()) {
                return entry.getValue().getComponent();
            }
        }
        return null;
    }

    // ==================== 信号驱动 ====================

    /**
     * 每渲染帧推进(接线层 drawChat 单行委托,主线程):
     * 冲刷脏标记、推进状态机、检查过期/高度超限组移除、必要时重建形态树。
     *
     * @param nowMillis 当前 wall millis
     */
    public void tick(long nowMillis) {
        if (dataDirty) {
            dataDirty = false;
            contentVersion.set(Integer.valueOf(contentVersion.get().intValue() + 1));
        }
        DisplayStateMachine.Phase phase = machine.tick(nowMillis,
                ChatMarkdownSettings.getCollapseAnimMillis(), ChatMarkdownSettings.getPopAnimMillis(),
                ChatMarkdownSettings.getClosingAnimMillis());
        if (phase != phaseSignal.get()) {
            phaseSignal.set(phase);
        }
        frameMillis.set(Long.valueOf(nowMillis));
        boolean hudNow = isHudPhase();
        if (runtime != null && root != null && hudNow != hudTreeBuilt) {
            rebuildTree();
        }
        removeExpiredHudGroups(nowMillis);
        trimHudGroupsByHeight();
    }

    /** 数据结构变化(消息到达/删除/清空/滚动/设置)后调用,驱动重协调(主线程)。 */
    public void notifyDataChanged() {
        contentVersion.set(Integer.valueOf(contentVersion.get().intValue() + 1));
    }

    /** 任意线程标记数据脏(消息到达网络线程安全):由 {@link #tick} 在主线程冲刷为版本号。 */
    public void markDataDirty() {
        dataDirty = true;
    }

    /** 聊天打开状态变化(输入屏开关,接线层调用)。 */
    public void setChatOpen(boolean open) {
        chatOpen.set(Boolean.valueOf(open));
        machine.setTarget(open, frameMillis.get().longValue());
    }

    /** 设置变化(字号/气泡参数):重建布局器与段缓存 + 全量重建。 */
    public void invalidateLayout() {
        synchronized (this) {
            layouter = null;
            systemLayouter = null;
            composer = null;
            messageList = null;
        }
        notifyDataChanged();
    }

    // ==================== 内容树 ====================

    /**
     * 构建聊天内容根节点(HUD 窗口内容;双形态由树根重建切换)。
     *
     * @param rt 宿主场景运行时(signal 绑定/挂载与 UI 页面同源)
     * @return 内容根节点(窗口内容,宽钉死 chatWidthPx)
     */
    public SceneNode buildContent(SceneRuntime rt) {
        this.runtime = rt;
        SceneNode newRoot = SceneNode.column()
                .setHitTestable(false)
                .setPreferredWidth(Math.max(1, ChatMarkdownSettings.chatWidthFor(hostViewportWidth)));
        this.root = newRoot;
        this.mount = null;
        rt.bind(Computed.create(this::animTransform), transform -> root.setTransform(transform));
        rt.bind(Computed.create(this::animOpacity), opacity -> root.setOpacity(opacity.floatValue()));
        rebuildTree();
        return newRoot;
    }

    /** 测试探针:当前状态机阶段。 */
    DisplayStateMachine.Phase __phaseForTest() {
        return machine.getPhase();
    }

    /**
     * 是否 HUD 气泡流阶段(聊天关闭稳定态 + 收起动画中)。弹出/稳定/收回阶段 HUD 树清空,
     * 容器由输入屏幕绘制(避免 HUD 窗口与屏幕双容器重复渲染)。
     *
     * <p>读状态机即时态(信号经帧末批处理提交,tick 内判断不能用信号值)。</p>
     */
    private boolean isHudPhase() {
        DisplayStateMachine.Phase phase = machine.getPhase();
        return phase == DisplayStateMachine.Phase.HUD || phase == DisplayStateMachine.Phase.COLLAPSING;
    }

    /** 树根重建(形态切换;旧挂载点整体移除,新树上重新 forEach 组列表)。 */
    private void rebuildTree() {
        if (root == null) {
            return;
        }
        if (listHandle != null) {
            listHandle.dispose();
            listHandle = null;
        }
        messageNodes.clear();
        if (mount != null) {
            root.removeChild(mount);
        }
        mount = SceneNode.column().setHitTestable(false);
        root.appendChild(mount);
        // 气泡最大宽同步(设计稿 §3.x:气泡 ≤ 0.85 组内容宽;视口变化重建树时生效,
        // 容器路径由 ChatContainer.setViewport 每帧同值幂等同步;未知视口(0)时
        // 保持不限制,避免把气泡错误 clamp 到 1px)
        if (hostViewportWidth > 0) {
            int bubbleContentWidth = Math.max(1, ChatMarkdownSettings.chatWidthFor(
                    hostViewportWidth) - 2 * ChatMarkdownSettings.getBubblePaddingX());
            messageList().setBubbleMaxWidthPx((int) Math.round(
                    bubbleContentWidth * ChatMarkdownSettings.getBubbleMaxWidthRatio()));
        }
        boolean hud = isHudPhase();
        if (hud) {
            SceneNode list = SceneNode.column().setHitTestable(false);
            mount.appendChild(list);
            listHandle = messageList().mount(runtime, list, groupsSignal(),
                    ChatMessageList.Style.hud(), messageNodes, frameMillis);
        }
        // 非 HUD 阶段:HUD 树清空(整窗隐藏,容器由输入屏幕绘制)
        hudTreeBuilt = hud;
    }

    /** 组列表(结构级 Computed:数据版本 → 合成组;供 ChatContainer 复用)。 */
    ReadableSignal<List<ChatCardComposer.ComposedGroup>> groupsSignal() {
        return Computed.create(this::composeAll);
    }

    private List<ChatCardComposer.ComposedGroup> composeAll() {
        contentVersion.get().intValue(); // 结构依赖
        boolean applyTtl = isHudPhase();
        List<MessageGroupModel> groups = grouper.group(history.snapshot(), selfNameProvider.selfName());
        int maxLine = Math.max(1, ChatMarkdownSettings.chatWidthFor(hostViewportWidth)
                - 2 * ChatMarkdownSettings.getBubblePaddingX());
        List<ChatCardComposer.ComposedGroup> composed =
                new ArrayList<ChatCardComposer.ComposedGroup>();
        // HUD 形态剔除语义:TTL 完全过期(expiredThreshold)与堆叠高度超限(heightTrimThreshold,
        // 设计稿 §3.1 不等 TTL 的结构级移除)双阈值合并,取较新者
        long cutoff = Math.max(expiredThreshold, heightTrimThreshold);
        for (MessageGroupModel group : groups) {
            if (applyTtl && group.getLatestMillis() < cutoff) {
                continue; // HUD 形态:过期/超限裁剪的组移除(不占位)
            }
            composed.add(composer().compose(group, frameMillis.get().longValue(), maxLine, applyTtl));
        }
        return composed;
    }

    /**
     * 滚动偏移(px)= 平滑显示行 × 行高(内容版本 + 帧时钟驱动重算;供 ChatContainer 滚动绑定)。
     *
     * <p>每次重算(幂等)顺序:① 贴底跟随/未读判定(内联)——距底 ≤ {@link #nearBottomLineThreshold()}
     * 行 → 未读清零、目标归底(新消息自动贴底);否则若本次有历史增量(新消息) → 未读累加;
     * ② 目标喂 {@link SmoothScroller#setTarget}:目标变化 → 以当前显示为起点重启 120ms 动画;
     * 目标未变 → 插值前进(平滑收敛);③ 输出插值显示 × 行高。</p>
     *
     * <p>动画期间读帧时钟 → 每帧重算推进插值;静止后输出同值,Computed 记忆化去重,下游零重跑。</p>
     */
    Integer scrollOffsetPx() {
        contentVersion.get().intValue(); // 依赖:滚动/内容变化经 notifyDataChanged 驱动
        long nowMillis = frameMillis.get().longValue(); // 依赖:平滑插值每帧推进
        int scroll = history.getScroll();
        int size = history.size();
        int appended = 0;
        if (lastSeenSize >= 0) {
            appended = size - lastSeenSize; // 新消息增量(controller 侧对比,不改 ChatHistory 公共面)
        }
        lastSeenSize = size; // 首次物化:仅校准基线(历史消息不误计为未读)
        boolean nearBottom = scroll <= nearBottomLineThreshold();
        int target = scroll;
        if (nearBottom) {
            // 贴底跟随(距底 ≤2 行):未读清零 + 目标归底(新消息自动贴底,120ms 平滑到位)
            if (unreadCount != 0) {
                unreadCount = 0;
                unreadSignal.set(Integer.valueOf(0));
            }
            target = 0;
        } else if (appended > 0) {
            // 离开底部的新消息:未读累加(批到达按增量计),不打断阅读
            unreadCount += appended;
            unreadSignal.set(Integer.valueOf(unreadCount));
        }
        smooth.setTarget(target, nowMillis);
        float display = smooth.displayLines(nowMillis);
        return Integer.valueOf(Math.round(display * ChatMarkdownSettings.getChatLineHeightPx()));
    }

    /**
     * @return 贴底跟随阈值(行):36px 阈值按行粒度换算 = ceil(36 / 行高);行高 18 → 2 行。
     */
    private static int nearBottomLineThreshold() {
        int lineHeight = Math.max(1, ChatMarkdownSettings.getChatLineHeightPx());
        return (int) Math.ceil(36.0D / lineHeight);
    }

    /** @return 显示行平滑器(拖动接管/回底接线用) */
    public SmoothScroller smoothScroll() {
        return smooth;
    }

    /** @return 未读新消息计数信号(容器提示节点 visible/文本驱动)。 */
    ReadableSignal<Integer> unreadSignal() {
        return unreadSignal;
    }

    /**
     * 滚动回底(提示节点「↓ N 条新消息」点击路径):退出拖动接管直通 → 历史复位到底 →
     * 内容版本重算(距底判定 → 未读清零,目标 = 0 → 120ms 平滑回底)。
     */
    public void scrollToBottom() {
        smooth.releaseDrag();
        history.resetScroll();
        notifyDataChanged();
    }

    /**
     * 根节点动画 transform(设计稿 §4.1 三段式;复合级,不触发重排):
     * POPPING = easeOutBack(c=1.4) translateY(+24→0) + scale(0.96→1),origin 容器左下角(0,1);
     * COLLAPSING = 与 POPPING 完全对称反向(easeOutQuad translateY 0→+24、scale 1→0.96);
     * CLOSING = easeOutQuad opacity 1→0 + translateY 0→+12(scale 不参与)。
     * transform 通道保留 easeOutBack overshoot(eased&gt;1 时轻微回弹过头再回正)。
     */
    private Transform animTransform() {
        DisplayStateMachine.Phase phase = phaseSignal.get();
        long now = frameMillis.get().longValue();
        switch (phase) {
            case COLLAPSING: {
                float eased = Animator.easeOut(machine.progress(now,
                        ChatMarkdownSettings.getCollapseAnimMillis()));
                // 反向:translateY 0→+24、scale 1→0.96(与 POPPING 对称)
                return new Transform(0.0F, 24.0F * eased, 0.0F,
                        1.0F - 0.04F * eased, 1.0F - 0.04F * eased, 0.0F, 1.0F);
            }
            case POPPING: {
                float eased = Animator.easeOutBack(machine.progress(now,
                        ChatMarkdownSettings.getPopAnimMillis()), 1.4F);
                // 弹出:translateY +24→0、scale 0.96→1,origin 左下角
                return new Transform(0.0F, 24.0F * (1.0F - eased), 0.0F,
                        0.96F + 0.04F * eased, 0.96F + 0.04F * eased, 0.0F, 1.0F);
            }
            case CLOSING: {
                float eased = Animator.easeOut(machine.progress(now,
                        ChatMarkdownSettings.getClosingAnimMillis()));
                // 关闭:translateY 0→+12(下滑消失),scale 不参与
                return new Transform(0.0F, 12.0F * eased, 0.0F,
                        1.0F, 1.0F, 0.0F, 1.0F);
            }
            default:
                return Transform.translate(0.0F, 0.0F);
        }
    }

    /**
     * 根节点动画 opacity(设计稿 §4.3 opacity 通道补齐):与 {@link #animTransform()} 同 phase /
     * 同 progress / 同曲线,同步输出双通道。COLLAPSING/CLOSING 淡出 1→0(easeOut),POPPING
     * 淡入 0→1(easeOutBack 输出 clamp01——opacity 不能 &gt;1,transform 通道才保留 overshoot);
     * 稳定态恒 1(渲染快速路径,零边界命令)。
     */
    private float animOpacity() {
        DisplayStateMachine.Phase phase = phaseSignal.get();
        long now = frameMillis.get().longValue();
        switch (phase) {
            case COLLAPSING:
                return 1.0F - Animator.easeOut(machine.progress(now,
                        ChatMarkdownSettings.getCollapseAnimMillis()));
            case POPPING:
                return Animator.clamp01(Animator.easeOutBack(machine.progress(now,
                        ChatMarkdownSettings.getPopAnimMillis()), 1.4F));
            case CLOSING:
                return 1.0F - Animator.easeOut(machine.progress(now,
                        ChatMarkdownSettings.getClosingAnimMillis()));
            default:
                return 1.0F;
        }
    }

    /** HUD 形态:存在完全过期(存活+淡出结束)的组时推进移除阈值(阈值只进不退),触发结构重算。 */
    private void removeExpiredHudGroups(long nowMillis) {
        if (!isHudPhase()) {
            return;
        }
        long window = ChatMarkdownSettings.getHudTtlMillis() + ChatMarkdownSettings.getHudFadeMillis();
        long newThreshold = nowMillis - window;
        if (newThreshold <= expiredThreshold) {
            return;
        }
        for (MessageGroupModel group : grouper.group(history.snapshot(), selfNameProvider.selfName())) {
            if (group.getLatestMillis() < newThreshold) {
                expiredThreshold = newThreshold;
                notifyDataChanged();
                return;
            }
        }
    }

    /**
     * HUD 形态堆叠高度上限(设计稿 §3.1):树中未过期组总高 &gt; 视口高 ×
     * {@code hudMaxHeightRatio} 时,从最旧组起立即剔除(结构级、不等 TTL 淡出),
     * 直到满足上限,刷屏不侵占半屏以上;最新单组自身超限时至少保留该组(不空屏);
     * 未超限仍走 TTL 淡出语义。
     *
     * <p>高度估算 = ChatGeometry 同式粗粒度(组头 + 行数×行高 + 内边距/组内间距,
     * 系统组 = 纯行高;行数按注入度量整段宽 ÷ 单行最大宽估算),不依赖布局后几何。
     * 阈值只进不退(被裁组不再复活);容器形态不参与(与 TTL 同路,applicTtl=false 不过滤)。</p>
     */
    private void trimHudGroupsByHeight() {
        if (!isHudPhase() || hostViewportHeight <= 0 || hostViewportWidth <= 0) {
            return;
        }
        int version = contentVersion.get().intValue();
        if (version == lastTrimContentVersion && hostViewportHeight == lastTrimViewportHeight) {
            return; // 内容与视口无变化:评估幂等,跳过(常规帧零开销)
        }
        lastTrimContentVersion = version;
        lastTrimViewportHeight = hostViewportHeight;
        List<MessageGroupModel> groups = grouper.group(history.snapshot(), selfNameProvider.selfName());
        if (groups.isEmpty()) {
            return;
        }
        int maxHeight = (int) Math.round(hostViewportHeight
                * ChatMarkdownSettings.getHudMaxHeightRatio());
        if (maxHeight <= 0) {
            return;
        }
        long cutoff = Math.max(expiredThreshold, heightTrimThreshold);
        int groupGap = Math.max(0, ChatMarkdownSettings.getGroupGapHudPx());
        // 树中(未过期/未裁剪)组,时间正序(最旧在前);并行记录组高与最新时刻
        int keptCount = 0;
        int[] heights = new int[groups.size()];
        long[] keptLatest = new long[groups.size()];
        int total = 0;
        for (int i = 0; i < groups.size(); i++) {
            MessageGroupModel group = groups.get(i);
            if (group.getLatestMillis() < cutoff) {
                continue;
            }
            int height = estimateHudGroupHeight(group);
            heights[keptCount] = height;
            keptLatest[keptCount] = group.getLatestMillis();
            total += keptCount == 0 ? height : groupGap + height;
            keptCount++;
        }
        if (total <= maxHeight) {
            return; // 未超限:保持 TTL 淡出语义
        }
        // 超限:从最新(尾部)反向累计,首个使总高超限的组及其全部更旧组一次剔除(一帧收敛)
        int sum = 0;
        for (int i = keptCount - 1; i >= 0; i--) {
            sum += heights[i];
            if (i < keptCount - 1) {
                sum += groupGap;
            }
            if (sum > maxHeight) {
                if (i == keptCount - 1) {
                    return; // 最新单组已超限:至少保留最新一组(不空屏),不再向前累计
                }
                // 剔除 kept[0..i](时间正序下全部更旧);阈值取被裁最新组时刻 +1,只进不退
                long newTrim = keptLatest[i] + 1;
                if (newTrim > heightTrimThreshold) {
                    heightTrimThreshold = newTrim;
                    notifyDataChanged();
                }
                return;
            }
        }
    }

    /**
     * HUD 组高粗粒度估算(与 ChatGeometry.measureGroups 同式):非系统组 =
     * 组头字号 + 2×纵向内边距 + 行数×行高 + 组内消息间距;系统组 = 行数×行高(无壳)。
     * 行数按整段文本宽 ÷ 单行最大宽估算(与 layouter 同源注入度量,粗粒度、零布局)。
     */
    private int estimateHudGroupHeight(MessageGroupModel group) {
        // K3 三轮:系统消息按 font-system 12/16 估算(与渲染/切分同源),非系统组沿用 body
        boolean system = group.getAlignment() == MessageGroupModel.Alignment.SYSTEM_CENTER;
        int fontSize = system ? ChatMarkdownSettings.getSystemFontSizePx()
                : ChatMarkdownSettings.getChatFontSizePx();
        int lineHeight = system ? ChatMarkdownSettings.getSystemLineHeightPx()
                : ChatMarkdownSettings.getChatLineHeightPx();
        int paddingY = ChatMarkdownSettings.getBubblePaddingY();
        int headerFontSize = ChatMarkdownSettings.getChatHeaderFontSizePx();
        int innerGap = ChatMarkdownSettings.getGroupInnerGapPx();
        int maxLineWidth = Math.max(1, ChatMarkdownSettings.chatWidthFor(hostViewportWidth)
                - 2 * ChatMarkdownSettings.getBubblePaddingX());
        int lines = 0;
        int messageCount = 0;
        for (MessageGroupModel.GroupLine line : group.getLines()) {
            messageCount++;
            lines += estimatedLines(line.getRest(), maxLineWidth, fontSize);
        }
        if (system) {
            return lines * lineHeight;
        }
        return headerFontSize + 2 * paddingY + lines * lineHeight
                + Math.max(0, messageCount - 1) * innerGap;
    }

    /** 行数估算(粗粒度):整段文本宽 ÷ 单行最大宽,向上取整,至少 1 行。 */
    private int estimatedLines(String text, int maxLineWidth, int fontSize) {
        if (text == null || text.isEmpty()) {
            return 1;
        }
        int lines = (int) Math.ceil(measure.advance(text, fontSize) / (double) maxLineWidth);
        return Math.max(1, lines);
    }

    /** 帧时钟信号(渲染组件淡出/动画驱动)。 */
    ReadableSignal<Long> frameMillisSignal() {
        return frameMillis;
    }

    /**
     * T8 生产 LaTeX 行高约束(设计稿 §3.5)：段流经
     * {@link TextLayoutService#applyLatexLineHeightConstraint} 约束——公式盒总高
     * &gt; 行高×1.6 时按 0.85 缩放重排(段字号落点,布局/测量/渲染全链路同源);
     * 缩放后仍超限保持缩放结果(截断+省略号按行高上限 clamp 降级,渲染层无公式盒裁剪通道)。
     */
    private static ChatMessageList.SegmentPostProcessor latexLineHeightConstraint() {
        final TextLayoutService service = FontService.getInstance().getTextLayoutService();
        return new ChatMessageList.SegmentPostProcessor() {
            @Override
            public List<TextSegment> postProcess(List<TextSegment> segments, int baseFontSizePx) {
                return service.applyLatexLineHeightConstraint(segments, baseFontSizePx,
                        ChatMarkdownSettings.getChatLineHeightPx(),
                        ChatMarkdownSettings.getLatexMaxLineHeightFactor(),
                        ChatMarkdownSettings.getLatexShrinkFactor());
            }
        };
    }

    /** 懒取消息列表渲染器(依赖段解析器;供 ChatContainer 复用)。 */
    ChatMessageList messageList() {
        ChatMessageList current = messageList;
        if (current == null) {
            synchronized (this) {
                current = messageList;
                if (current == null) {
                    current = new ChatMessageList(segmentParser, segmentMeasurer,
                            latexLineHeightConstraint());
                    messageList = current;
                }
            }
        }
        return current;
    }

    /** 懒取合成器(依赖布局器)。 */
    private ChatCardComposer composer() {
        ChatCardComposer current = composer;
        if (current == null) {
            synchronized (this) {
                current = composer;
                if (current == null) {
                    if (layouter == null) {
                        layouter = new ChatLineLayouter(measure, ChatMarkdownSettings.getChatFontSizePx());
                        systemLayouter = new ChatLineLayouter(measure,
                                ChatMarkdownSettings.getSystemFontSizePx());
                    }
                    current = new ChatCardComposer(layouter, systemLayouter);
                    composer = current;
                }
            }
        }
        return current;
    }
}
