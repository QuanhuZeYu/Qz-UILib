package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
import club.heiqi.uilib.internal.chat3.viewmodel.ChatClock;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageGroupModel;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageGrouper;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.runtime.Binding;
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

    /** 段解析器(生产 = TextLayoutService.parseSegments 解析 § 样式码;测试注入)。 */
    public interface SegmentParser {
        /** @return 文本 → 样式段流 */
        List<TextSegment> parse(String text, int baseColor);
    }

    /** 段解析缓存上限(历史 100 行 × 每行数行 + 组头)。 */
    private static final int SEGMENT_CACHE_MAX = 400;

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
    public static SegmentParser uiLibSegmentParser() {
        final TextLayoutService service = FontService.getInstance().getTextLayoutService();
        return new SegmentParser() {
            @Override
            public List<TextSegment> parse(String text, int baseColor) {
                return service.parseSegments(text, baseColor);
            }
        };
    }

    /** 树形态(结构级)。 */
    enum ContentShape {
        /** HUD 堆叠气泡树(聊天关闭)。 */
        HUD,
        /** 容器树(聊天打开)。 */
        CONTAINER
    }

    private final ChatHistory history = new ChatHistory();
    private final MessageGrouper grouper = new MessageGrouper();
    private final ChatLineLayouter.Measure measure;
    private final SelfNameProvider selfNameProvider;
    private final SegmentParser segmentParser;
    private ChatLineLayouter layouter;
    private ChatCardComposer composer;

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
    private ContentShape builtShape;

    /** HUD 形态树的组列表句柄(树重建时释放,防句柄累积)。 */
    private SceneListHandle listHandle;

    /** 容器形态树的组列表句柄与滚动绑定(HUD 树重建时释放)。 */
    private SceneListHandle containerHandle;
    private Binding scrollBinding;

    /** 消息节点 → 记录(命中检测查询;树重建时清空,离树节点惰性清理)。 */
    private final Map<SceneNode, ChatLineRecord> messageNodes =
            new IdentityHashMap<SceneNode, ChatLineRecord>();

    /** 宿主视口(逻辑 px,渲染帧由接线层写入;命中检测窗口原点推导用)。 */
    private int hostViewportWidth;
    private int hostViewportHeight;

    /** 段解析缓存(text@baseColor → segments;epoch 失效由 controller 重建时清空)。 */
    private final Map<String, List<TextSegment>> segmentCache =
            new LinkedHashMap<String, List<TextSegment>>(64, 0.75F, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<TextSegment>> eldest) {
            return size() > SEGMENT_CACHE_MAX;
        }
    };

    /** 以生产 UILib 度量创建(真机路径)。 */
    public ChatSceneController() {
        this(uiLibMeasure(), mcSelfName(), uiLibSegmentParser());
    }

    /**
     * 以指定依赖创建(headless 测试注入确定性度量/玩家名/段解析)。
     */
    public ChatSceneController(ChatLineLayouter.Measure measure, SelfNameProvider selfNameProvider,
            SegmentParser segmentParser) {
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
        AnchorRect rootBox = SceneGeometry.absoluteBox(root, 0, 0);
        int margin = ChatMarkdownSettings.getChatMarginPx();
        int rootAbsX = margin;
        int rootAbsY = hostViewportHeight - margin - rootBox.getHeight();
        List<SceneNode> stale = null;
        for (Map.Entry<SceneNode, ChatLineRecord> entry : messageNodes.entrySet()) {
            SceneNode node = entry.getKey();
            if (node.__getParent() == null) {
                if (stale == null) {
                    stale = new ArrayList<SceneNode>();
                }
                stale.add(node);
                continue;
            }
            AnchorRect box = SceneGeometry.absoluteBox(node, rootAbsX, rootAbsY);
            if (x >= box.getX() && x < box.getX() + box.getWidth()
                    && y >= box.getY() && y < box.getY() + box.getHeight()) {
                return entry.getValue().getComponent();
            }
        }
        if (stale != null) {
            for (SceneNode node : stale) {
                messageNodes.remove(node);
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
        if (runtime != null && root != null && currentShape() != builtShape) {
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
            segmentCache.clear();
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
        this.builtShape = null;
        rt.bind(Computed.create(this::animTransform), transform -> root.setTransform(transform));
        rebuildTree();
        return newRoot;
    }

    /** 测试探针:当前状态机阶段。 */
    DisplayStateMachine.Phase __phaseForTest() {
        return machine.getPhase();
    }

    /**
     * 当前形态(结构级):HUD/收起中 → HUD 树;弹出/稳定/收回 → 容器树。
     *
     * <p>读状态机即时态(信号经帧末批处理提交,tick 内判断不能用信号值)。</p>
     */
    private ContentShape currentShape() {
        DisplayStateMachine.Phase phase = machine.getPhase();
        return phase == DisplayStateMachine.Phase.HUD || phase == DisplayStateMachine.Phase.COLLAPSING
                ? ContentShape.HUD : ContentShape.CONTAINER;
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
        if (containerHandle != null) {
            containerHandle.dispose();
            containerHandle = null;
        }
        if (scrollBinding != null) {
            scrollBinding.dispose();
            scrollBinding = null;
        }
        messageNodes.clear();
        if (mount != null) {
            root.removeChild(mount);
        }
        mount = SceneNode.column().setHitTestable(false);
        root.appendChild(mount);
        ContentShape shape = currentShape();
        if (shape == ContentShape.HUD) {
            SceneNode list = SceneNode.column()
                    .setHitTestable(false)
                    .setGap(Math.max(0, ChatMarkdownSettings.getGroupGapHudPx()));
            mount.appendChild(list);
            listHandle = runtime.forEach(list, groupsSignal(), this::groupKey,
                    group -> buildGroupNode(runtime, group, true, messageNodes));
        } else {
            SceneNode container = SceneNode.column()
                    .setHitTestable(false)
                    .setBackgroundColor(ChatMarkdownSettings.getContainerBgArgb())
                    .setBorderColor(ChatMarkdownSettings.getContainerBorderArgb())
                    .setBorderWidth(1)
                    .setCornerRadius(ChatMarkdownSettings.getContainerCornerRadius())
                    .setPadding(ChatMarkdownSettings.getBubblePaddingY(),
                            ChatMarkdownSettings.getBubblePaddingX(),
                            ChatMarkdownSettings.getBubblePaddingY(),
                            ChatMarkdownSettings.getBubblePaddingX())
                    .setPreferredWidth(Math.max(1, ChatMarkdownSettings.chatWidthFor(hostViewportWidth)))
                    .setPreferredHeight(Math.max(1, ChatMarkdownSettings.containerHeightFor(hostViewportHeight)))
                    .setClipChildren(true);
            SceneNode list = SceneNode.column()
                    .setHitTestable(false)
                    .setGap(Math.max(0, ChatMarkdownSettings.getGroupGapContainerPx()));
            container.appendChild(list);
            mount.appendChild(container);
            containerHandle = runtime.forEach(list, groupsSignal(), this::groupKey,
                    group -> buildGroupNode(runtime, group, false, messageNodes));
            // 容器滚动:历史滚动偏移 → 容器滚动属性(结构版本驱动重算)
            scrollBinding = runtime.bind(Computed.create(this::scrollOffsetPx),
                    offset -> container.setScrollOffsetY(offset.intValue()));
        }
        builtShape = shape;
    }

    /** 组列表(结构级 Computed:数据版本 → 合成组)。 */
    private ReadableSignal<List<ChatCardComposer.ComposedGroup>> groupsSignal() {
        return Computed.create(this::composeAll);
    }

    private List<ChatCardComposer.ComposedGroup> composeAll() {
        contentVersion.get().intValue(); // 结构依赖
        boolean applyTtl = currentShape() == ContentShape.HUD;
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

    /**
     * 组 key = 首条消息序列号(进程内唯一,稳定)+ 组内行数(内容版本)。
     * 加行/切断/换发送者 → key 变化 → 重建组节点;真机 messageId 恒 0,不可用作身份。
     */
    private Long groupKey(ChatCardComposer.ComposedGroup group) {
        long firstSequence = group.getMessages().isEmpty() ? 0L
                : group.getMessages().get(0).getRecord().getSequenceId();
        long lineCount = 0L;
        for (ChatCardComposer.MessageLines message : group.getMessages()) {
            lineCount += message.getDisplayLines().size();
        }
        return Long.valueOf(firstSequence * 10000L + lineCount);
    }

    /**
     * 外部容器树构建(输入屏复用):把容器形态的组列表 forEach 与滚动绑定挂到调用方节点。
     *
     * @param rt            宿主运行时
     * @param containerNode 滚动容器节点(滚动绑定目标)
     * @param list          组列表挂载节点
     * @param registry      消息节点 → 记录登记表(命中检测用,调用方持有)
     * @return 生命周期句柄(关闭时 dispose)
     */
    public SceneListHandle buildContainerContent(SceneRuntime rt, SceneNode containerNode, SceneNode list,
            Map<SceneNode, ChatLineRecord> registry) {
        SceneListHandle handle = rt.forEach(list, groupsSignal(), this::groupKey,
                group -> buildGroupNode(rt, group, false, registry));
        // 滚动绑定(目标 = 调用方容器节点)
        rt.bind(Computed.create(this::scrollOffsetPx),
                offset -> containerNode.setScrollOffsetY(offset.intValue()));
        return handle;
    }

    /** 滚动偏移(px)= 历史行偏移 × 行高(结构版本驱动重算)。 */
    private Integer scrollOffsetPx() {
        contentVersion.get().intValue(); // 依赖:滚动变化经 notifyDataChanged 驱动
        return Integer.valueOf(history.getScroll() * ChatMarkdownSettings.getChatLineHeightPx());
    }

    /** 构建单组子树:组头(名字+时间) + 消息气泡(背景/圆角/行段);HUD 形态挂淡出绑定。 */
    private SceneNode buildGroupNode(SceneRuntime rt, ChatCardComposer.ComposedGroup group, boolean hud,
            Map<SceneNode, ChatLineRecord> registry) {
        int fontSize = ChatMarkdownSettings.getChatFontSizePx();
        int lineHeight = ChatMarkdownSettings.getChatLineHeightPx();
        int headerFont = ChatMarkdownSettings.getChatHeaderFontSizePx();
        int paddingX = ChatMarkdownSettings.getBubblePaddingX();
        int paddingY = ChatMarkdownSettings.getBubblePaddingY();
        boolean system = group.getAlignment() == MessageGroupModel.Alignment.SYSTEM_CENTER;
        AlignSelf align;
        switch (group.getAlignment()) {
            case SELF_RIGHT:
                align = AlignSelf.END;
                break;
            case SYSTEM_CENTER:
                align = AlignSelf.CENTER;
                break;
            default:
                align = AlignSelf.START;
                break;
        }
        SceneNode groupNode = SceneNode.column()
                .setHitTestable(false)
                .setWidthSizing(SceneNode.WidthSizing.SHRINK)
                .setAlignSelf(align)
                .setGap(Math.max(0, ChatMarkdownSettings.getGroupInnerGapPx()));
        SceneNode headerNode = null;
        List<TextSegment> headerBase = null;
        if (!group.getHeaderText().isEmpty()) {
            headerBase = new ArrayList<TextSegment>();
            headerBase.addAll(segmentParser.parse(group.getSender(), group.getNameColor()));
            headerBase.addAll(segmentParser.parse(" " + ChatClock.formatTime(group.getLatestMillis()),
                    ChatMarkdownSettings.getTimeTextArgb()));
            headerNode = new SceneNode()
                    .setHitTestable(false)
                    .setFontSize(headerFont)
                    .setSegments(headerBase)
                    .setTextVerticalAlign(TextVerticalAlign.TOP);
            groupNode.appendChild(headerNode);
        }
        int baseTextColor = system ? ChatMarkdownSettings.getSystemTextArgb() : 0xFFFFFFFF;
        int bubbleColor = group.getAlignment() == MessageGroupModel.Alignment.SELF_RIGHT
                ? ChatMarkdownSettings.getBubbleSelfArgb() : ChatMarkdownSettings.getBubbleOtherArgb();
        List<SceneNode> messageNodes = new ArrayList<SceneNode>();
        List<SceneNode> lineNodes = new ArrayList<SceneNode>();
        List<List<TextSegment>> lineBases = new ArrayList<List<TextSegment>>();
        for (ChatCardComposer.MessageLines message : group.getMessages()) {
            SceneNode messageNode = SceneNode.column()
                    .setHitTestable(true)
                    .setWidthSizing(SceneNode.WidthSizing.SHRINK);
            if (!system) {
                messageNode.setBackgroundColor(bubbleColor)
                        .setCornerRadius(ChatMarkdownSettings.getBubbleCornerRadius())
                        .setPadding(paddingY, paddingX, paddingY, paddingX);
            }
            for (String line : message.getDisplayLines()) {
                List<TextSegment> segments = parseCached(line, baseTextColor);
                SceneNode lineNode = new SceneNode()
                        .setHitTestable(false)
                        .setFontSize(fontSize)
                        .setSegments(segments)
                        .setTextVerticalAlign(TextVerticalAlign.TOP)
                        .setPreferredHeight(Math.max(1, lineHeight));
                messageNode.appendChild(lineNode);
                lineNodes.add(lineNode);
                lineBases.add(segments);
            }
            groupNode.appendChild(messageNode);
            messageNodes.add(messageNode);
            registry.put(messageNode, message.getRecord());
        }
        if (hud) {
            final SceneNode header = headerNode;
            final List<TextSegment> headerSegments = headerBase;
            final int bubble = bubbleColor;
            final int[] lastAlpha = new int[] { 255 };
            rt.bind(Computed.create(() -> Integer.valueOf(ChatCardComposer.fadeAlpha(
                            group.getLatestMillis(), frameMillis.get().longValue(),
                            ChatMarkdownSettings.getHudTtlMillis(),
                            ChatMarkdownSettings.getHudFadeMillis(), 255))),
                    alpha -> {
                        int a = alpha.intValue();
                        if (a != lastAlpha[0]) {
                            lastAlpha[0] = a;
                            applyAlpha(messageNodes, lineNodes, lineBases, header, headerSegments,
                                    bubble, system, a);
                        }
                    });
        }
        return groupNode;
    }

    /** alpha 烘焙到气泡背景与段流(PAINT 级;alpha = 255 复用基础数据零分配)。 */
    private void applyAlpha(List<SceneNode> messageNodes, List<SceneNode> lineNodes,
            List<List<TextSegment>> lineBases, SceneNode headerNode, List<TextSegment> headerSegments,
            int bubbleColor, boolean system, int alpha) {
        for (int i = 0; i < messageNodes.size(); i++) {
            if (!system) {
                messageNodes.get(i).setBackgroundColor(ChatCardComposer.fadeColor(bubbleColor, alpha));
            }
        }
        for (int i = 0; i < lineNodes.size(); i++) {
            lineNodes.get(i).setSegments(ChatCardComposer.fadeSegments(lineBases.get(i), alpha));
        }
        if (headerNode != null && headerSegments != null) {
            headerNode.setSegments(ChatCardComposer.fadeSegments(headerSegments, alpha));
        }
    }

    /** 段解析缓存(text@baseColor)。 */
    private List<TextSegment> parseCached(String text, int baseColor) {
        String key = text + '@' + baseColor;
        List<TextSegment> hit = segmentCache.get(key);
        if (hit != null) {
            return hit;
        }
        List<TextSegment> segments = segmentParser.parse(text, baseColor);
        segmentCache.put(key, segments);
        return segments;
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
        if (currentShape() != ContentShape.HUD) {
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
