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
 *   <li>HUD 形态过期组(10s+淡出结束)在 tick 中移除(结构级),新消息始终堆在底部。</li>
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

    private final ChatHistory history = new ChatHistory();
    private final MessageGrouper grouper = new MessageGrouper();
    private final ChatLineLayouter.Measure measure;
    private final SelfNameProvider selfNameProvider;
    private final ChatMessageList.SegmentParser segmentParser;
    private ChatLineLayouter layouter;
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

    /** 网络线程数据脏标记(主线程 tick 冲刷为版本号)。 */
    private volatile boolean dataDirty;

    /** HUD 形态过期移除阈值(latestMillis 低于此值的组不再进树;主线程 tick 推进)。 */
    private long expiredThreshold = 0L;

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
        this(uiLibMeasure(), mcSelfName(), uiLibSegmentParser());
    }

    /**
     * 以指定依赖创建(headless 测试注入确定性度量/玩家名/段解析)。
     */
    public ChatSceneController(ChatLineLayouter.Measure measure, SelfNameProvider selfNameProvider,
            ChatMessageList.SegmentParser segmentParser) {
        if (measure == null || selfNameProvider == null || segmentParser == null) {
            throw new IllegalArgumentException("依赖不能为空");
        }
        this.measure = measure;
        this.selfNameProvider = selfNameProvider;
        this.segmentParser = segmentParser;
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
     * 冲刷脏标记、推进状态机、检查过期组移除、必要时重建形态树。
     *
     * @param nowMillis 当前 wall millis
     */
    public void tick(long nowMillis) {
        if (dataDirty) {
            dataDirty = false;
            contentVersion.set(Integer.valueOf(contentVersion.get().intValue() + 1));
        }
        DisplayStateMachine.Phase phase = machine.tick(nowMillis,
                ChatMarkdownSettings.getCollapseAnimMillis(), ChatMarkdownSettings.getPopAnimMillis());
        if (phase != phaseSignal.get()) {
            phaseSignal.set(phase);
        }
        frameMillis.set(Long.valueOf(nowMillis));
        boolean hudNow = isHudPhase();
        if (runtime != null && root != null && hudNow != hudTreeBuilt) {
            rebuildTree();
        }
        removeExpiredHudGroups(nowMillis);
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
        for (MessageGroupModel group : groups) {
            if (applyTtl && group.getLatestMillis() < expiredThreshold) {
                continue; // HUD 形态:已完全过期的组移除(不占位)
            }
            composed.add(composer().compose(group, frameMillis.get().longValue(), maxLine, applyTtl));
        }
        return composed;
    }

    /** 滚动偏移(px)= 历史行偏移 × 行高(结构版本驱动重算;供 ChatContainer 滚动绑定)。 */
    Integer scrollOffsetPx() {
        contentVersion.get().intValue(); // 依赖:滚动变化经 notifyDataChanged 驱动
        return Integer.valueOf(history.getScroll() * ChatMarkdownSettings.getChatLineHeightPx());
    }

    /** 根节点动画 transform(收起滑出/弹出滑入/收回滑出;复合级,不触发重排)。 */
    private Transform animTransform() {
        DisplayStateMachine.Phase phase = phaseSignal.get();
        long now = frameMillis.get().longValue();
        float width = (float) ChatMarkdownSettings.chatWidthFor(hostViewportWidth);
        switch (phase) {
            case COLLAPSING:
                return Transform.translate(
                        -width * Animator.easeOut(machine.progress(now,
                                ChatMarkdownSettings.getCollapseAnimMillis())), 0.0F);
            case POPPING:
                return Transform.translate(
                        -width * (1.0F - Animator.easeOut(machine.progress(now,
                                ChatMarkdownSettings.getPopAnimMillis()))), 0.0F);
            case CLOSING:
                return Transform.translate(
                        -width * Animator.easeOut(machine.progress(now,
                                ChatMarkdownSettings.getPopAnimMillis())), 0.0F);
            default:
                return Transform.translate(0.0F, 0.0F);
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

    /** 帧时钟信号(渲染组件淡出/动画驱动)。 */
    ReadableSignal<Long> frameMillisSignal() {
        return frameMillis;
    }

    /** 懒取消息列表渲染器(依赖段解析器;供 ChatContainer 复用)。 */
    ChatMessageList messageList() {
        ChatMessageList current = messageList;
        if (current == null) {
            synchronized (this) {
                current = messageList;
                if (current == null) {
                    current = new ChatMessageList(segmentParser);
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
                    }
                    current = new ChatCardComposer(layouter);
                    composer = current;
                }
            }
        }
        return current;
    }
}
