package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import club.heiqi.uilib.internal.chat3.viewmodel.HudVisibleClock;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageGroupModel;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageGrouper;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageLifecycle;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageLifecycleRegistry;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneAnchorResolver;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.runtime.SceneListHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 聊天 3.0 场景控制器(L3 渲染层状态中枢):数据 + 信号 + 双形态 scene 树。
 *
 * <p>结构/动画解耦:</p>
 * <ul>
 *   <li>组列表 = Computed(contentVersion) → forEach 构建组节点(声明式 diff);</li>
 *   <li>淡出 = 组节点内 Computed(frameMillis) 绑定,PAINT 级颜色烘焙(零结构协调);</li>
 *   <li>形态切换 = 状态机阶段驱动树根重建(HUD 气泡树 ↔ 容器树),动画 = root transform 平移;</li>
 *   <li>HUD 形态过期组按「显示预算」消费(每条消息附带需要显示的时间,预算只在 HUD
 *   真正可见时按可见时钟消耗;聊天框打开期间冻结,关闭后用尽剩余预算继续显示);
 *   过期移除 = 合成列表队首弹出(预算同源 → 过期序 = 到达序,最旧在最前),不再全量重扫;
 *   组首次以 HUD 形态合成才播 enter 入场动画(重挂载/组增长重建不重播);
 *   可选常驻模式(ChatMarkdownSettings.hudPersistMessages=true):预算过期移除不生效,
 *   仅 50% 视口高裁剪(trimHudGroupsByHeight)与历史容量 100 天然裁剪;</li>
 *   <li>窗体过渡抽象(2026-08-29 用户设计指示):关闭 = 窗体整体收回段 → 窗体整体浮现段
 *   (两段同属一个窗体过渡,总时长 = closing + fadeIn,默认 540ms,可配至秒级);
 *   过渡期(屏幕关闭动画 → HUD 渐入完成)消息列表冻结快照({@link #transitionFrozen} +
 *   {@link #steadySnapshot})——消息只入数据层,树/布局/enter/过期/裁剪一律不响应,
 *   窗体整体动画独占画面;渐入完成(稳态)后积压消息一次性应用(forEach diff 差量
 *   挂载,新组按稳态 enter 入场)。打开方向不冻结(容器全量即时呈现)。</li>
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
    /** 真机诊断日志器(临时,定位「关闭聊天框看不到消息」;每 300 帧一行快照)。 */
    private static final Logger DIAG_LOG = LogManager.getLogger("QzUILib Chat3Diag");
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

    /** HUD 可见时钟(仅 HUD 形态帧推进累计,帧间 delta 夹取 1s;聊天框打开/容器阶段
     *  冻结——消息预算只在 HUD 真正可见时消耗)。 */
    private final HudVisibleClock hudVisibleClock = new HudVisibleClock();
    /** 消息生命周期注册表(sequenceId → 生命周期;预算/done 脏标记,与历史容量裁剪联动)。 */
    private final MessageLifecycleRegistry lifecycles = new MessageLifecycleRegistry();
    /** 曾经以 HUD 形态合成过的消息序列号(enter 入场动画门控:组内全部 seq 均未登记
     *  → 首次合成播放;组内任一 seq 已登记 → 不重播)。每次 HUD 合成后无条件登记组内
     *  全部 seq——集合只增不减、add 幂等,任意次 compose 求值收敛同一判定;仅随历史
     *  容量裁剪保留在史 seq(seq 进程内单调递增不复用,离史不复活,不影响判定)。 */
    private final Set<Long> hudEverFirstSeqs = new HashSet<Long>();
    /** HUD 渐入衔接动画起点(wall millis;关闭衔接重建时设置,根级 opacity 0→1
     *  easeOutCubic 一次性播放,完成复位 -1 快速路径;非衔接恒 -1 不参与)。 */
    private long hudFadeInStartMillis = -1L;
    /** 关闭衔接抑制(2026-08-29 真机「关闭聊天框时闪烁」根因之一):上次树非 HUD → 本次
     *  HUD 重建时整批组稳态直接出现。实现 = rebuildTree 挂载前把整批组内全部 seq 预登记进
     *  {@link #hudEverFirstSeqs}(集合幂等,任意次 compose 求值结果一致——一次性标志在
     *  Computed 多次求值时会漏,后续求值把组算回 enter=true)。 */
    // (实现见 rebuildTree,无独立字段)
    /**
     * 窗体过渡窗口起点(wall millis;>=-1):屏幕 requestClose 时设置(关闭动画开始),
     * 窗口 = closing + fadeIn 总时长(用户高层语义:关闭动画开始→HUD 渐入结束整体
     * 可配 500ms~秒级)。窗口内 = 窗体过渡期 → 内容冻结(见 {@link #transitionFrozen})。
     * tick 推进,窗口耗尽自动复位 -1。
     */
    private long transitionStartMillis = -1L;
    /** 窗体过渡期标志(tick 每帧按即时机器态/窗口计算):过渡期内消息列表冻结快照
     *  (composeAll 直接返回 {@link #steadySnapshot}),树/布局/enter/过期/裁剪一律
     *  不响应——窗体整体动画独占画面;过渡完成(稳态)一次性应用。根治「过渡期消息
     *  到达打穿窗体动画」的闪烁(2026-08-29 窗体动画抽象,见类注释)。 */
    private boolean transitionFrozen;
    /** 窗体过渡冻结快照(HUD 信号):上次稳态 compose 结果(composeAll 冻结期返回此引用,列表引用
     *  稳定 → forEach diff 零变化;解冻后按 contentVersion 重算更新)。 */
    private List<ChatCardComposer.ComposedGroup> steadySnapshot;
    /** 窗体过渡冻结快照(容器信号):容器全量 compose 的稳态快照,冻结期返回此引用
     *  (关闭动画期间容器内容冻结;与 HUD 信号快照独立——HUD 过滤与容器全量语义不同)。 */
    private List<ChatCardComposer.ComposedGroup> steadyContainerSnapshot;
    /** 可见时钟信号(HUD 淡出渲染驱动;仅值变化时 set,防每帧唤醒下游)。 */
    private final Signal<Long> hudVisibleSignal = Signal.create(Long.valueOf(0L));

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
    /** 宿主权威放置端口;由装配层注入,未注入时命中检测走 SceneAnchorResolver 兜底。 */
    private volatile ChatHudWindow.HudPlacementSource placementSource;

    /** 装配层注入 HUD 宿主放置端口(client 包 → internal 为正向依赖)。 */
    public void attachPlacementSource(ChatHudWindow.HudPlacementSource source) {
        this.placementSource = source;
    }

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
                rebuildTree(frameMillis.get().longValue());
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
     * <p>窗口原点:优先取 HUD host 本帧权威放置盒(含堆叠偏移、安全区与 clamp，与像素严格
     * 对齐)；host 未放置本窗(未注册/空内容/测试)时回退 {@link SceneAnchorResolver} 同一
     * BOTTOM_LEFT 视口锚定数学。两条路径都不再手算，锚点公式不再有第二事实源。</p>
     *
     * @param x 命中点 x(逻辑 px,scaled resolution 口径)
     * @param y 命中点 y
     * @return 命中的消息组件;未命中返回 null
     */
    public IChatComponent hitTest(int x, int y) {
        if (root == null || hostViewportWidth <= 0 || hostViewportHeight <= 0) {
            return null;
        }
        // 注册表命中(节点相对窗口根)+ 窗口原点平移:宿主权威放置优先,resolver 数学兜底
        int rootAbsX;
        int rootAbsY;
        ChatHudWindow.HudPlacementSource source = placementSource;
        AnchorRect placed = source == null ? null : source.placement(ChatHudWindow.HUD_ID);
        if (placed != null) {
            rootAbsX = placed.getX();
            rootAbsY = placed.getY();
        } else {
            AnchorRect rootBox = SceneGeometry.absoluteBox(root, 0, 0);
            SceneAnchorResolver.ResolvedViewport resolved = SceneAnchorResolver.resolveViewport(
                    false, true, hostViewportWidth, hostViewportHeight,
                    rootBox.getWidth(), rootBox.getHeight(),
                    ChatMarkdownSettings.getChatMarginPx(), 0, 0, 0, 0, 0);
            rootAbsX = resolved.getX();
            rootAbsY = resolved.getY();
        }
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
     * 冲刷脏标记、推进状态机与 HUD 可见时钟、检查队首过期/高度超限组移除、必要时重建形态树。
     *
     * <p>可见时钟只在 HUD 形态(isHudPhase)帧推进时累计——聊天框打开(容器阶段)期间
     * 消息预算冻结,关闭后用尽剩余预算继续显示。</p>
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
        // CLOSING 挂起打开兑现进 COLLAPSING(打开方向)本帧作废窗体过渡窗口:窗口只属于
        // 关闭方向,兑现帧起解冻(打开方向容器全量即时呈现);挂起期标志为 false → 窗口
        // 保留(不回归 f4b1af36「过渡期消息打穿」)。peek 不清位,rebuildTree 是唯一消费点。
        if (machine.isPendingOpenRedeemed()) {
            transitionStartMillis = -1L;
        }
        if (phase != phaseSignal.get()) {
            phaseSignal.set(phase);
        }
        frameMillis.set(Long.valueOf(nowMillis));
        boolean hudNow = isHudPhase();
        long hudVisible = hudVisibleClock.tickFrame(nowMillis, hudNow);
        if (hudVisible != hudVisibleSignal.get().longValue()) {
            hudVisibleSignal.set(Long.valueOf(hudVisible)); // 仅值变化时 set,防每帧唤醒
        }
        // 窗体过渡期推进(在 rebuildTree 之前:衔接重建帧必须实时合成——冻结快照
        // 是容器全量未过滤语义,重建需 HUD 预算过滤;重建帧 frozen=false → 实时合成
        // → steadySnapshot 更新为 HUD 过滤快照,下一帧起冻结返回它)
        DisplayStateMachine.Phase currentPhase = machine.getPhase();
        long fadeInStart = hudFadeInStartMillis;
        // 渐入只属于关闭衔接 HUD 阶段:打开方向(COLLAPSING/POPPING/CONTAINER)不设渐入,
        // 防 hudFadeInStartMillis 被误设时反向冻结打开方向(护栏)。
        boolean fadeInActive = fadeInStart >= 0L
                && nowMillis - fadeInStart < ChatMarkdownSettings.getHudFadeInAnimMillis()
                && currentPhase == DisplayStateMachine.Phase.HUD;
        if (transitionStartMillis >= 0L) {
            boolean windowOpen = nowMillis - transitionStartMillis <
                    ChatMarkdownSettings.getClosingAnimMillis()
                    + ChatMarkdownSettings.getHudFadeInAnimMillis();
            if (!windowOpen) {
                transitionStartMillis = -1L; // 窗口耗尽:过渡结束,解除冻结
            }
        }
        // 冻结窗口只认关闭方向:窗口内机器处于 CLOSING(屏幕关闭动画段)∪ HUD 渐入活跃段。
        // 打开方向(COLLAPSING/POPPING/CONTAINER)即使窗口未耗尽也不冻结——容器全量即时呈现
        // (打开瞬间玩家就要看到消息列表,冻结反而延迟显示)。原判定用「窗口内 && phase != HUD」
        // 不区分方向,渐入窗内重开/CLOSING 兑现/旧屏回调顶替会把打开方向误冻结成陈旧快照。
        boolean frozenNow = (transitionStartMillis >= 0L
                && currentPhase == DisplayStateMachine.Phase.CLOSING)
                || fadeInActive;
        // 冻结状态翻转 → 内容版本 +1:解冻帧两个组信号立即失效重算(积压消息一次性应用)。
        // 否则解冻瞬间无任何依赖变化,冻结期消费过的 Computed 缓存卡在稳态快照引用,
        // 积压消息要等下一次 contentVersion 变化才应用(2026-08-31 容器信号拆分时发现;
        // f4b1af36 冻结机制遗留缺陷,渐入中段无新消息到达时同样卡快照)。
        if (frozenNow != transitionFrozen) {
            notifyDataChanged();
        }
        transitionFrozen = frozenNow;
        if (runtime != null && root != null && hudNow != hudTreeBuilt) {
            rebuildTree(nowMillis);
        }
        // 渐入通道复位:机器离开 HUD(进入打开方向)即清渐入起点——打开方向根 opacity
        // 恢复纯 animOpacity,消除渐入残留 × 形态淡出的双重曲线(如渐入窗内重开)。
        if (currentPhase != DisplayStateMachine.Phase.HUD && hudFadeInStartMillis >= 0L) {
            hudFadeInStartMillis = -1L;
        }
        expireHudGroupsByHead();
        trimHudGroupsByHeight();
        // A2 收口:原「每帧强制 flush」自锁补丁已删——宿主合同改为空窗 flush 照常、
        // paint 跳过(SceneHudHost RetainedWindow.settleWithoutPaint),本运行时物化不再
        // 依赖宿主栈外直调;tick 早于 HUD 帧时,宿主本帧 settle 仍会物化本帧新写入。
    }

    /** 数据结构变化(消息到达/删除/清空/滚动/设置)后调用,驱动重协调(主线程)。 */
    public void notifyDataChanged() {
        contentVersion.set(Integer.valueOf(contentVersion.get().intValue() + 1));
    }

    /** 任意线程标记数据脏(消息到达网络线程安全):由 {@link #tick} 在主线程冲刷为版本号。 */
    public void markDataDirty() {
        dataDirty = true;
    }

    /**
     * 开始窗体过渡窗口(输入屏关闭动画开始):窗口 = closing + fadeIn 总时长,窗口内
     * 消息列表冻结(composeAll 返回稳态快照),窗体整体动画独占画面——过渡期消息到达
     * 只入数据层,不触发树/布局/enter/过期变化(窗体动画抽象,2026-08-29)。
     * 幂等:已开窗口重复调用不重置起点(关闭动画期间重复 Esc/Enter 不延长冻结)。
     *
     * @param nowMillis 当前 wall millis(屏幕侧 requestClose 时刻)
     */
    public void beginCloseTransition(long nowMillis) {
        if (transitionStartMillis < 0L) {
            transitionStartMillis = nowMillis;
        }
    }

    /** 聊天打开状态变化(输入屏开关,接线层调用)。 */
    public void setChatOpen(boolean open) {
        chatOpen.set(Boolean.valueOf(open));
        machine.setTarget(open, frameMillis.get().longValue(),
                ChatMarkdownSettings.getCollapseAnimMillis(),
                ChatMarkdownSettings.getPopAnimMillis(),
                ChatMarkdownSettings.getClosingAnimMillis());
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
        // 动画期间离屏栅格化（B6 方案）：开合动画 transform 施加于整体栅格化结果而非字形顶点
        // （transform 恒等帧 needTransform=false，不产生图层边界命令，稳定态零开销）
        SceneNode newRoot = SceneNode.column()
                .setHitTestable(false)
                .setPreferTransformLayer(true)
                .setPreferredWidth(Math.max(1, ChatMarkdownSettings.chatWidthFor(hostViewportWidth)));
        this.root = newRoot;
        this.mount = null;
        rt.bind(Computed.create(this::animTransform), transform -> root.setTransform(transform));
        rt.bind(Computed.create(this::rootOpacity), opacity -> root.setOpacity(opacity.floatValue()));
        rebuildTree(frameMillis.get().longValue());
        return newRoot;
    }

    /** 测试探针:当前状态机阶段。 */
    DisplayStateMachine.Phase __phaseForTest() {
        return machine.getPhase();
    }

    /** 测试探针:窗体过渡窗口起点(-1 = 未开启)。 */
    long __transitionStartMillisForTest() {
        return transitionStartMillis;
    }

    /** 测试探针:形态相位信号(帧末提交时序验证)。 */
    Signal<DisplayStateMachine.Phase> __phaseSignalForTest() {
        return phaseSignal;
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

    /** 树根重建(形态切换;旧挂载点整体移除,新树上重新 forEach 组列表)。
     *  @param nowMillis 当前帧 wall millis(帧信号未提交也可用;tick 驱动传精确帧时刻) */
    private void rebuildTree(long nowMillis) {
        if (root == null) {
            return;
        }
        if (listHandle != null) {
            listHandle.dispose();
            listHandle = null;
        }
        messageNodes.clear();
        SceneNode previousMount = mount;
        if (previousMount != null) {
            root.removeChild(previousMount);
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
        // pendingOpen 兑现标志一次性消费(唯一消费点):兑现 = 打开方向衔接,
        // 与关闭衔接互斥——CLOSING 挂起打开兑现进 COLLAPSING 后本帧 hudNow=true
        // 且 hudTreeBuilt=false,若无标志会被误判为「关闭完成→HUD」而整批预登记
        // enterOnMount(抑制打开方向 enter)+ 误设渐入起点(打开方向双重淡出)。
        // 标志为权威信号,nowPhase 为不变式防御(机器兑现后必进 COLLAPSING)。
        boolean openRedemption = machine.consumePendingOpenRedeemed();
        DisplayStateMachine.Phase nowPhase = machine.getPhase();
        boolean hud = isHudPhase();
        // 关闭衔接(上次树非 HUD → 本次 HUD 重建):整批组预登记进 hudEverFirstSeqs,
        // enterOnMount 恒 false → 稳态直接出现,不播 180ms 入场动画
        // (真机「关闭聊天框时闪烁」根因之一——打开期间到达/自发送回显的消息组首合成为
        // HUD 形态时重播入场动画,opacity 0→1 + translateY 8→0 在关屏瞬间闪现);
        // 之后 HUD 形态实时新消息照常入场。
        // previousMount != null 排除首次构建(旧树从未挂过,不抑制);
        // !hudTreeBuilt 代表上次树形态为容器/空(视口变更的 HUD→HUD 重建不抑制);
        // && !openRedemption 排除 pendingOpen 兑现(打开方向播 enter、不设渐入);
        // && nowPhase == HUD 锁定严格关闭衔接(COLLAPSING 兑现帧不误判)。
        if (hud && !hudTreeBuilt && previousMount != null && !openRedemption
                && nowPhase == DisplayStateMachine.Phase.HUD) {
            List<ChatCardComposer.ComposedGroup> existing = groupsSignal().get();
            if (existing != null) {
                for (ChatCardComposer.ComposedGroup group0 : existing) {
                    // 组内全部 seq 预登记(D6:非仅首条)——衔接后若组首行被历史容量
                    // 裁剪删除,剩余行 seq 仍已登记 → 判定不重播;空组自然跳过
                    for (ChatCardComposer.MessageLines message : group0.getMessages()) {
                        hudEverFirstSeqs.add(Long.valueOf(
                                message.getRecord().getSequenceId()));
                    }
                }
            }
            // 关闭衔接 → HUD 渐入动画:根级 opacity 从本帧起 0→1 easeOutCubic,
            // 与容器关闭动画(淡出+下滑)视觉衔接,替代关屏瞬间气泡跳现(「仍闪烁」观感源);
            // 一次性,完成即复位(非衔接的 HUD 帧/后续新消息不受影响)。
            // 注意:frameMillis 是帧末批量提交 Signal,同帧 get() 仍是上一帧值——
            // 渐入起点必须用调用方传入的当前帧 nowMillis(见 rebuildTree 参数)。
            hudFadeInStartMillis = nowMillis;
        }
        if (hud) {
            SceneNode list = SceneNode.column().setHitTestable(false);
            mount.appendChild(list);
            // HUD 形态注入可见时钟信号:渲染层淡出按可见时钟驱动,预算只在 HUD 真正
            // 可见时消耗(聊天框打开期间冻结,关闭后用尽剩余预算继续显示)
            listHandle = messageList().mount(runtime, list, groupsSignal(),
                    ChatMessageList.Style.hud(), messageNodes, frameMillis, hudVisibleSignal);
        }
        // 非 HUD 阶段:HUD 树清空(整窗隐藏,容器由输入屏幕绘制)
        hudTreeBuilt = hud;
    }

    /** 组列表(HUD 信号,结构级 Computed 缓存单例:数据版本 + 形态 → 合成组;供 HUD 树/
     *  队首过期/高度裁剪共用——Computed 记忆化,常规帧(结构无变化)零重算)。 */
    ReadableSignal<List<ChatCardComposer.ComposedGroup>> groupsSignal() {
        return groupsSignalValue;
    }

    /** 组列表派生(缓存单例;见 {@link #groupsSignal()})。 */
    private final ReadableSignal<List<ChatCardComposer.ComposedGroup>> groupsSignalValue =
            Computed.create(this::composeAll);

    /**
     * 容器形态组列表(输入屏容器挂载;结构级 Computed 缓存单例):恒全量合成
     * (applyTtl=false,不做 HUD 生命周期过滤),只依赖内容版本。
     *
     * <p>2026-08-31 真机「打开动画文字瞬间刷出」根因:容器与 HUD 树曾共享 {@link #groupsSignal()},
     * 而打开方向 COLLAPSING 阶段 {@code isHudPhase()=true} 使共享信号走 TTL 预算过滤——预算
     * 耗尽的历史消息在容器弹出动画期间不合成(空白容器),POPPING 才切换全量,文字在动画
     * 尾部瞬间物化。容器列表独立信号源后,打开瞬间即全量呈现,文字随容器一同淡入。</p>
     */
    ReadableSignal<List<ChatCardComposer.ComposedGroup>> containerGroupsSignal() {
        return containerGroupsSignalValue;
    }

    /** 容器组列表派生(缓存单例;见 {@link #containerGroupsSignal()})。 */
    private final ReadableSignal<List<ChatCardComposer.ComposedGroup>> containerGroupsSignalValue =
            Computed.create(this::composeContainerAll);

    /**
     * 合成组列表(HUD 生命周期过滤路径):HUD 形态按每条消息的显示预算(可见时钟驱动)
     * 过滤——预算耗尽 + 淡出窗结束(done/淡出脏标记)的组不再合成(结构移除,节点卸载);
     * 容器形态全量合成(applyTtl=false,不碰生命周期)。
     *
     * <p>生命周期按组内最新消息(时间正序末条)记账:组增长重建后组预算以新消息为准重新
     * 起算(连发消息不丢);enterOnMount 按组首条序列号门控——首次以 HUD 形态合成播放
     * 入场动画,重挂载/组增长重建不重播。预算路径(applyTtl 且非常驻)compose 注入
     * HudBudget,alpha 恒 255(淡出由渲染层按可见时钟每帧驱动,不出现在合成期)。</p>
     */
    private List<ChatCardComposer.ComposedGroup> composeAll() {
        contentVersion.get().intValue(); // 结构依赖
        phaseSignal.get(); // 形态依赖:打开/关闭切换(HUD ↔ 容器)触发重算(容器全量、HUD 过滤);常规帧不变零重算
        // 窗体过渡期冻结:消息照常入数据层(purge/分组/合成不碰),树/布局/enter/过期
        // 一律不响应——窗体整体动画独占画面,过渡完成(稳态)后按 contentVersion 重算
        // 一次性应用(forEach diff 差量挂载,新组按稳态 enter 入场)。返回稳态快照引用
        // (引用稳定 → 冻结期零结构变化),根治「过渡期消息到达打穿窗体动画」闪烁(2026-08-29)。
        if (transitionFrozen) {
            List<ChatCardComposer.ComposedGroup> frozen = steadySnapshot;
            return frozen != null ? frozen
                    : java.util.Collections.<ChatCardComposer.ComposedGroup>emptyList();
        }
        List<ChatCardComposer.ComposedGroup> composed = composeCore(isHudPhase());
        steadySnapshot = composed; // 稳态快照(窗体过渡期冻结返回;解冻后按版本重算更新)
        return composed;
    }

    /**
     * 容器形态合成:恒全量(applyTtl=false,不碰生命周期/enter 门控),窗体过渡期冻结
     * 返回容器稳态快照(关闭动画期间容器内容冻结——与「打开方向不冻结、关闭方向冻结」
     * 的窗体动画语义一致;打开方向 transitionStartMillis 未开启,天然实时全量)。
     */
    private List<ChatCardComposer.ComposedGroup> composeContainerAll() {
        contentVersion.get().intValue(); // 结构依赖(容器信号不读形态:恒全量,无 phaseSignal 依赖)
        if (transitionFrozen) {
            List<ChatCardComposer.ComposedGroup> frozen = steadyContainerSnapshot;
            return frozen != null ? frozen
                    : java.util.Collections.<ChatCardComposer.ComposedGroup>emptyList();
        }
        List<ChatCardComposer.ComposedGroup> composed = composeCore(false);
        steadyContainerSnapshot = composed; // 容器稳态快照(冻结期返回;解冻后按版本重算更新)
        return composed;
    }

    /**
     * 合成核心(applyTtl 参数化):分组 → 布局 → HUD 预算过滤(applyTtl=true)/容器全量
     * (applyTtl=false) → enter 门控(仅 HUD 路径)。HUD 信号与容器信号共用本核心,
     * 冻结短路与稳态快照由调用方各自维护(两种形态快照语义不同,不复用同一引用)。
     *
     * @param applyTtl true = HUD 生命周期过滤路径;false = 容器全量路径
     */
    private List<ChatCardComposer.ComposedGroup> composeCore(boolean applyTtl) {
        List<ChatLineRecord> snapshot = history.snapshot();
        lifecycles.purge(snapshot); // 历史容量裁剪联动,防注册表泄漏
        // D6 收敛(数据路审计 P3[3] 泄漏):hudEverFirstSeqs 只保留当前历史存活 seq——
        // 序列号进程内单调递增、永不复用(ChatHistory.nextSequence,clear 不重置),
        // 离史 seq 不再参与任何合成 → retainAll 幂等、不影响判定正确性;
        // 同时把集合大小收敛为「在史行数」,消解随运行时长线性增长的占用。
        HashSet<Long> alive = new HashSet<Long>();
        for (ChatLineRecord record : snapshot) {
            if (record != null) {
                alive.add(Long.valueOf(record.getSequenceId()));
            }
        }
        hudEverFirstSeqs.retainAll(alive);
        List<MessageGroupModel> groups = grouper.group(snapshot, selfNameProvider.selfName());
        int maxLine = Math.max(1, ChatMarkdownSettings.chatWidthFor(hostViewportWidth)
                - 2 * ChatMarkdownSettings.getBubblePaddingX());
        boolean persist = ChatMarkdownSettings.isHudPersistMessages();
        // 预算路径下 compose 的 nowMillis 无实际用途(alpha 恒 255,淡出由渲染层按
        // 可见时钟驱动,组头时间戳按组内最新到达时刻);不读帧时钟 → 组列表 Computed
        // 只依赖 contentVersion,常规帧(结构无变化)零重算
        long hudVisible = hudVisibleClock.visibleMillis();
        long fadeMillis = ChatMarkdownSettings.getHudFadeMillis();
        List<ChatCardComposer.ComposedGroup> composed =
                new ArrayList<ChatCardComposer.ComposedGroup>();
        for (MessageGroupModel group : groups) {
            if (!applyTtl) {
                // 容器路径:旧重载(budget=null),不碰生命周期
                composed.add(composer().compose(group, 0L, maxLine, false));
                continue;
            }
            // HUD 形态:高度裁剪阈值(设计稿 §3.1,只进不退——被裁组不复活)先过滤
            if (group.getLatestMillis() < heightTrimThreshold) {
                continue; // 结构级移除(不占位)
            }
            if (!persist) {
                // 组内最新消息(时间正序末条)的生命周期;预算只在 HUD 可见时消耗
                List<MessageGroupModel.GroupLine> lines = group.getLines();
                MessageLifecycle lifecycle = lifecycles.ensure(
                        lines.get(lines.size() - 1).getRecord().getSequenceId(),
                        ChatMarkdownSettings.getHudTtlMillis());
                if (lifecycle.isFadeElapsed(hudVisible, fadeMillis) || lifecycle.isDone()) {
                    lifecycle.markDone(); // 脏标记:一次性标记,后续 compose/裁剪/扫描 O(1) 跳过
                    continue; // 不再合成(结构移除,节点卸载)
                }
                lifecycle.markEntered(hudVisible); // 幂等,重挂载不重置预算
                composed.add(composer().compose(group, 0L, maxLine, true,
                        new ChatCardComposer.HudBudget(lifecycle.getBudgetMillis(),
                                lifecycle.getHudVisibleStartMillis())));
            } else {
                // 常驻模式:完全跳过生命周期(alpha 255 由 compose 旧路径保证),
                // enterOnMount 仍按集合计算
                composed.add(composer().compose(group, 0L, maxLine, true));
            }
            // enterOnMount 门控(D6 组 key 漂移修复):判定 = 「组内是否存在任一已登记 seq」——
            // 组内全部 seq 均未登记才播放入场;每次 HUD 合成后无条件登记组内全部 seq。
            // 三约束验证:
            //   ① 裁剪不重播:历史容量裁剪删组首行后 groupKey(firstSeq*10000+lineCount)漂移
            //      → 组重建,但组内剩余行 seq 均曾登记 → seenBefore=true → enter=false;
            //   ② 增长不重播:同发送者续发仅 lineCount 变,组内老行 seq 均曾登记 → false;
            //   ③ 幂等:集合只增不减 + HashSet.add 幂等,任意次 compose 求值收敛同一判定。
            // 必须无条件登记全部(而非仅 enter=true 时登记):否则场景「{a,b,c} 首合登记 →
            // 增长 d(不登记)→ 裁剪删 a,b,c → 剩 {d}」会误播入场动画。
            // 关闭衔接抑制由 rebuildTree 预登记组内全部 seq 承担(集合幂等,任意次求值一致);
            // 窗体过渡期(渐入中)消息本就冻结不合成(见 composeAll 开头 transitionFrozen),
            // 渐入完成解冻后新组按稳态 enter 正常入场——过渡期不再需要额外抑制(2026-08-29)
            ChatCardComposer.ComposedGroup composedGroup =
                    composed.get(composed.size() - 1);
            boolean seenBefore = false;
            for (ChatCardComposer.MessageLines message : composedGroup.getMessages()) {
                if (hudEverFirstSeqs.contains(
                        Long.valueOf(message.getRecord().getSequenceId()))) {
                    seenBefore = true;
                    break;
                }
            }
            composedGroup.setEnterOnMount(!seenBefore);
            for (ChatCardComposer.MessageLines message : composedGroup.getMessages()) {
                hudEverFirstSeqs.add(Long.valueOf(message.getRecord().getSequenceId()));
            }
        }
        return composed;
    }

    /**
     * 滚动偏移(px)= 平滑显示行 × 行高(内容版本 + 帧时钟驱动重算;供 ChatContainer 滚动绑定)。
     *
     * <p>输出是「行域权威 → px」的<b>假想几何投影</b>(行 × 18px,与真实内容行高无关);
     * 真实内容几何的 clamp 在 ChatContainer.viewportScrollPx(V7 方案甲:行域唯一权威 +
     * 假想几何投影 + 真实几何 clamp,本方法只负责投影,不读真实几何)。</p>
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
     * POPPING = easeOutBack(c=1.04 默认) translateY(+24→0) + scale(0.96→1),origin 容器左下角(0,1);
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
                        ChatMarkdownSettings.getPopAnimMillis()));
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
     * 根 opacity = 形态动画 opacity 通道 × HUD 渐入衔接通道:形态动画(COLLAPSING/CLOSING
     * 淡出、POPPING 淡入、稳定恒 1)与关闭衔接渐入(仅关闭聊天框后短暂存在)相乘组合,
     * 两通道正交。
     */
    private float rootOpacity() {
        return animOpacity() * hudFadeInOpacity();
    }

    /**
     * HUD 渐入衔接通道(关闭完成→HUD 平滑出现):根级 opacity 0→1
     * ({@link ChatMarkdownSettings#getHudFadeInAnimMillis()} ms),一次性——
     * 播放完成即复位起点标记(-1),此后恒 1 快速路径。非衔接(HUD 稳定/首次进游戏)
     * 恒 1,零参与。
     *
     * <p>曲线 = {@link Animator#emergeIn(float)}(sqrt,先快后慢):真机取证(2026-08-29)
     * easeOutCubic 前段近乎不可见——长渐入(秒级)下气泡「消失数秒后才出现」,被用户
     * 感知为「关闭时消失-出现来回几下」;sqrt 曲线 10% 进度即达 ~32% 透明度,气泡快速
     * 浮现后缓慢稳定,任何配置时长下都持续可见地变亮。</p>
     */
    private float hudFadeInOpacity() {
        long start = hudFadeInStartMillis;
        if (start < 0L) {
            return 1.0F;
        }
        long now = frameMillis.get().longValue();
        long duration = ChatMarkdownSettings.getHudFadeInAnimMillis();
        float progress = duration <= 0L ? 1.0F
                : Math.min(1.0F, (float) (now - start) / (float) duration);
        if (progress >= 1.0F) {
            hudFadeInStartMillis = -1L; // 一次性:完成即复位(快速路径)
            return 1.0F;
        }
        return Animator.emergeIn(progress);
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
                        ChatMarkdownSettings.getPopAnimMillis())));
            case CLOSING:
                return 1.0F - Animator.easeOut(machine.progress(now,
                        ChatMarkdownSettings.getClosingAnimMillis()));
            default:
                return 1.0F;
        }
    }

    /**
     * HUD 形态过期组队首弹出(结构级移除):预算同源(默认预算一致)→ 过期序 = 到达序,
     * 合成列表最旧在前,队首即最早过期——只检查队首一组,不再全量重扫;队首过期
     * (预算耗尽 + 淡出窗结束)→ {@link #notifyDataChanged()}(一帧收敛:被下一帧
     * compose 排除 = 结构移除,节点卸载);极端差异预算兜底下每帧至多弹出队首一帧,
     * 下帧续弹(有界)。
     *
     * <p>TB1 常驻模式:预算过期移除不生效,直接返回(消息常驻;高度裁剪
     * trimHudGroupsByHeight 仍保留)。</p>
     */
    private void expireHudGroupsByHead() {
        if (!isHudPhase()) {
            return;
        }
        if (transitionFrozen) {
            return; // 窗体过渡期冻结:过期移除等待过渡完成(稳态一次性处理)
        }
        // TB1 常驻模式:消息常驻,预算过期移除不生效(仅高度裁剪参与)
        if (ChatMarkdownSettings.isHudPersistMessages()) {
            return;
        }
        List<ChatCardComposer.ComposedGroup> groups = groupsSignal().get();
        if (groups == null || groups.isEmpty()) {
            return;
        }
        // 队首 = grouper 输出的最旧组(合成列表保持时间正序);生命周期按组内最新消息记账
        ChatCardComposer.ComposedGroup oldest = groups.get(0);
        List<ChatCardComposer.MessageLines> messages = oldest.getMessages();
        if (messages.isEmpty()) {
            return;
        }
        MessageLifecycle lifecycle = lifecycles.get(
                messages.get(messages.size() - 1).getRecord().getSequenceId());
        if (lifecycle != null && lifecycle.isFadeElapsed(hudVisibleClock.visibleMillis(),
                ChatMarkdownSettings.getHudFadeMillis())) {
            DIAG_LOG.info("[ExpireDiag] t={} 队首组过期移除 seq={} treeWas={} hud={}",
                    Long.valueOf(frameMillis.get().longValue()),
                    Long.valueOf(messages.get(messages.size() - 1).getRecord().getSequenceId()),
                    Integer.valueOf(groups.size()),
                    Long.valueOf(hudVisibleClock.visibleMillis()));
            notifyDataChanged(); // 队首过期:触发结构重算,下一帧 compose 排除该组
        }
    }

    /**
     * HUD 形态堆叠高度上限(设计稿 §3.1):树中未过期组总高 &gt; 视口高 ×
     * {@code hudMaxHeightRatio} 时,从最旧组起立即剔除(结构级、不等 TTL 淡出),
     * 直到满足上限,刷屏不侵占半屏以上;最新单组自身超限时至少保留该组(不空屏);
     * 未超限仍走预算淡出语义。
     *
     * <p>高度估算对象 = 生命周期过滤后的合成列表(groupsSignal 缓存单例,Computed
     * 记忆化常规帧零重算),行数直接取合成后的切分行数(与渲染一致);cutoff 只由
     * 高度裁剪阈值决定(旧 expiredThreshold 半边已删除)。阈值只进不退(被裁组不复活);
     * 容器形态不参与(与生命周期同路,applyTtl=false 不过滤)。</p>
     */
    private void trimHudGroupsByHeight() {
        if (!isHudPhase() || hostViewportHeight <= 0 || hostViewportWidth <= 0) {
            return;
        }
        if (transitionFrozen) {
            return; // 窗体过渡期冻结:高度裁剪等待过渡完成
        }
        int version = contentVersion.get().intValue();
        if (version == lastTrimContentVersion && hostViewportHeight == lastTrimViewportHeight) {
            return; // 内容与视口无变化:评估幂等,跳过(常规帧零开销)
        }
        lastTrimContentVersion = version;
        lastTrimViewportHeight = hostViewportHeight;
        List<ChatCardComposer.ComposedGroup> groups = groupsSignal().get();
        if (groups == null || groups.isEmpty()) {
            return;
        }
        int maxHeight = (int) Math.round(hostViewportHeight
                * ChatMarkdownSettings.getHudMaxHeightRatio());
        if (maxHeight <= 0) {
            return;
        }
        long cutoff = heightTrimThreshold; // 只进不退的高度裁剪阈值(常驻模式同口径)
        int groupGap = Math.max(0, ChatMarkdownSettings.getGroupGapHudPx());
        // 树中(未过期/未裁剪)组,时间正序(最旧在前);并行记录组高与最新时刻
        int keptCount = 0;
        int[] heights = new int[groups.size()];
        long[] keptLatest = new long[groups.size()];
        int total = 0;
        for (int i = 0; i < groups.size(); i++) {
            ChatCardComposer.ComposedGroup group = groups.get(i);
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
            return; // 未超限:保持预算淡出语义
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
     * HUD 组高粗粒度估算(与渲染同式,合成列表中直接数切分行数):非系统组 =
     * 组头行高(16,渲染 HEADER_ROW_HEIGHT 同口径) + 2×纵向内边距 + 行数×行高 + 组内消息间距;
     * 系统组 = 行数×行高(无壳)。K3 四轮:此前按组头字号 12 计、实际渲染行高 16,每组低估 4px,
     * 堆叠上限估算偏松——改为 16 与渲染对齐。
     * 行数 = 合成组的切分行数(displayLines,与渲染共源,含 HUD 行数截断口径)。
     */
    private int estimateHudGroupHeight(ChatCardComposer.ComposedGroup group) {
        // K3 三轮:系统消息按 font-system 12/16 估算(与渲染/切分同源),非系统组沿用 body
        boolean system = group.getAlignment() == MessageGroupModel.Alignment.SYSTEM_CENTER;
        int lineHeight = system ? ChatMarkdownSettings.getSystemLineHeightPx()
                : ChatMarkdownSettings.getChatLineHeightPx();
        int paddingY = ChatMarkdownSettings.getBubblePaddingY();
        // K3 四轮:组头按实际渲染行高 16 计(原按字号 12 计每组低估 4px)
        int headerRowHeight = ChatMarkdownSettings.getChatHeaderRowHeightPx();
        int innerGap = ChatMarkdownSettings.getGroupInnerGapPx();
        int lines = 0;
        int messageCount = 0;
        for (ChatCardComposer.MessageLines message : group.getMessages()) {
            messageCount++;
            lines += Math.max(1, message.getDisplayLines().size());
        }
        if (system) {
            return lines * lineHeight;
        }
        return headerRowHeight + 2 * paddingY + lines * lineHeight
                + Math.max(0, messageCount - 1) * innerGap;
    }

    /** 帧时钟信号(渲染组件淡出/动画驱动)。 */
    ReadableSignal<Long> frameMillisSignal() {
        return frameMillis;
    }

    /** HUD 可见时钟信号(渲染层每帧淡出驱动;仅 HUD 形态帧推进,聊天框打开期间冻结)。 */
    ReadableSignal<Long> hudVisibleSignal() {
        return hudVisibleSignal;
    }

    /**
     * 直接切回 HUD 气泡形态(输入屏容器收回动画完成回调):状态机经
     * {@link DisplayStateMachine#forceHud(long)} 跳过 CLOSING 空窗直接挂 HUD
     * 气泡——收回动画已由输入屏播完,机器不再需要 140ms 空屏;幂等。
     */
    public void closeToHudImmediately() {
        long now = frameMillis.get().longValue();
        machine.forceHud(now);
        // forceHud 兑现挂起打开(进 COLLAPSING)时作废窗体过渡窗口并返回:窗口只属于
        // 关闭方向,兑现进打开方向不冻结;普通落 HUD 路径照旧补开窗口(兜底)。
        if (machine.isPendingOpenRedeemed()) {
            transitionStartMillis = -1L;
            return;
        }
        // 窗体过渡窗口兜底:真机已由屏幕 requestClose → beginCloseTransition 开启(幂等),
        // 测试/异常路径(未经历屏幕关闭动画)在此补开——窗口 = closing + fadeIn 总时长
        beginCloseTransition(now);
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
    /**
     * 屏幕绝对坐标 → 命中的链接 URL(无命中 null;宿主点击路径经此,不直接摸消息列表)。
     *
     * @param screenX 屏幕绝对 X
     * @param screenY 屏幕绝对 Y
     * @return 命中链接的完整 URL
     */
    public String resolveLinkUrlAt(int screenX, int screenY) {
        return messageList().resolveLinkUrlAt(screenX, screenY);
    }

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
