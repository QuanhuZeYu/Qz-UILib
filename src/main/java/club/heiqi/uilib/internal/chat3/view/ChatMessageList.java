package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatCardComposer;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatCodeSpanSplitter;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatUrlLinkifier;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageGroupModel;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTooltip;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.runtime.SceneListHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 消息列表组件(L3 渲染层,唯一消息渲染器):组头(名字+时间)+ 消息气泡(背景/圆角/行段)。
 *
 * <p>HUD 气泡流与容器列表共享本组件,形态差异由 {@link Style} 表达(组间距 + 是否 TTL 淡出),
 * 不再靠布尔分叉。段解析缓存按实例隔离(每个 controller 一份,测试注入 parser 互不串味)。</p>
 *
 * <p>T6a 链接 hover(设计稿 §3.5/§5.2):注入 {@link SegmentMeasurer} 后才启用——段流经
 * {@link ChatUrlLinkifier} 自动链接化,链接行附带行内命中区域(文本包围盒上下 +2 / 左右 +1),
 * 指针命中 → 仅该行段流重建为 hover 变体(提亮色 + 下划线)+ 手型;气泡 hover → 底色 + 3% 白;
 * 链接 hover 持续 400ms 出 URL tooltip(SceneTooltip)。</p>
 */
public final class ChatMessageList {

    /** 段解析缓存上限(历史 100 行 × 每行数行 + 组头)。 */
    private static final int SEGMENT_CACHE_MAX = 400;

    /** HUD 组出生 enter 起始位移(px,设计稿 §4.1:translateY +8→0)。 */
    private static final float ENTER_TRANSLATE_PX = 8.0F;

    /** 链接 tooltip 悬停延时(ms,设计稿 §5.2:悬停 400ms 出 URL tooltip)。 */
    private static final int LINK_TOOLTIP_DELAY_MILLIS = 400;
    /** 链接 tooltip 最大宽度(px,URL 换行不撑屏)。 */
    private static final int LINK_TOOLTIP_MAX_WIDTH_PX = 320;
    /** 链接 tooltip 最大行数。 */
    private static final int LINK_TOOLTIP_MAX_LINES = 4;

    /** 引用行竖条宽(px,设计稿 §3.5:行首 2px 竖条)。 */
    private static final int QUOTE_BAR_WIDTH_PX = 2;
    /** 引用行竖条圆角(px,设计稿 §3.5:圆角 1)。 */
    private static final int QUOTE_BAR_RADIUS_PX = 1;
    /** 引用行竖条与文本间距(px,设计稿 §3.5:竖条右 6px)。 */
    private static final int QUOTE_GAP_PX = 6;

    /** 段解析器(文本 → 样式段流;生产 = TextLayoutService.parseSegments,测试注入)。 */
    public interface SegmentParser {
        /** @return 文本 → 样式段流 */
        List<TextSegment> parse(String text, int baseColor);
    }

    /**
     * 段宽度度量(链接命中区域计算;生产 = TextLayoutService.getSegmentWidth,与渲染同源)。
     */
    public interface SegmentMeasurer {
        /** @return 段在指定字号下的宽度(UI px) */
        float widthOf(TextSegment segment, int fontSizePx);
    }

    /**
     * 段流后处理(T8 设计稿 §3.5:行内 LaTeX 行高约束;生产 =
     * TextLayoutService.applyLatexLineHeightConstraint,headless 测试注入替身)。
     *
     * <p>在段解析返回后、code 切分/链接化之前执行;处理结果进段流缓存
     * (segmentCache key 不含后处理产物细节,同一 text@baseColor 恒定触发同款处理)。</p>
     */
    public interface SegmentPostProcessor {
        /**
         * @param segments    段解析器产物(可被替换/修改;null 安全)
         * @param baseFontSizePx 段落基准字号(chat3 = chatFontSizePx,阈值基)
         * @return 处理后的段流
         */
        List<TextSegment> postProcess(List<TextSegment> segments, int baseFontSizePx);
    }

    /** 行内链接跨度(行内相对坐标,命中区域扩展在命中判定时统一应用)。 */
    static final class LinkSpan {
        final float startX;
        final float width;
        final String url;

        LinkSpan(float startX, float width, String url) {
            this.startX = startX;
            this.width = width;
            this.url = url;
        }
    }

    /** 消息列表形态:HUD(紧凑 + TTL 淡出)与容器(宽松 + 恒显)的唯一差异。 */
    public static final class Style {

        private final int groupGapPx;
        private final boolean ttlFade;

        private Style(int groupGapPx, boolean ttlFade) {
            this.groupGapPx = groupGapPx;
            this.ttlFade = ttlFade;
        }

        /** HUD 形态:组间紧密堆叠 + 12s 存活淡出(默认 TTL 12000/easeInQuad 800ms)。 */
        public static Style hud() {
            return new Style(Math.max(0, ChatMarkdownSettings.getGroupGapHudPx()), true);
        }

        /** 容器形态:组间宽松 + 恒显不淡出。 */
        public static Style container() {
            return new Style(Math.max(0, ChatMarkdownSettings.getGroupGapContainerPx()), false);
        }

        /** @return 组间距(px) */
        public int getGroupGapPx() {
            return groupGapPx;
        }

        /** @return 是否启用 TTL 淡出 */
        public boolean isTtlFade() {
            return ttlFade;
        }
    }

    /** 组级烘焙状态:行段流(正常/hover 两态)+ 气泡底色(正常/hover 两态)+ 组头 + accent 条,一次重写。 */
    private static final class MessageBake {

        private final List<SceneNode> messageNodes;
        private final List<SceneNode> lineNodes;
        private final List<List<TextSegment>> lineBases;
        private final List<List<TextSegment>> hoverBases;
        private final SceneNode headerNameNode;
        private final List<TextSegment> headerNameSegments;
        private final SceneNode headerTimeNode;
        private final List<TextSegment> headerTimeSegments;
        private final List<SceneNode> accentBars;
        private final int accentBarColor;
        /** 引用行竖条（T6b 设计稿 §3.5：行首 2px 竖条，随 alpha 同步淡出）。 */
        private final List<SceneNode> quoteBars;
        private final int quoteBarColor;
        private final boolean[] lineHovered;
        private final boolean[] bubbleHovered;
        private final int bubbleColor;
        private final int hoverBubbleColor;
        private final boolean system;

        MessageBake(List<SceneNode> messageNodes, List<SceneNode> lineNodes,
                List<List<TextSegment>> lineBases, List<List<TextSegment>> hoverBases,
                SceneNode headerNameNode, List<TextSegment> headerNameSegments,
                SceneNode headerTimeNode, List<TextSegment> headerTimeSegments,
                List<SceneNode> accentBars, int accentBarColor, List<SceneNode> quoteBars,
                int quoteBarColor, boolean[] lineHovered, boolean[] bubbleHovered,
                int bubbleColor, int hoverBubbleColor, boolean system) {
            this.messageNodes = messageNodes;
            this.lineNodes = lineNodes;
            this.lineBases = lineBases;
            this.hoverBases = hoverBases;
            this.headerNameNode = headerNameNode;
            this.headerNameSegments = headerNameSegments;
            this.headerTimeNode = headerTimeNode;
            this.headerTimeSegments = headerTimeSegments;
            this.accentBars = accentBars;
            this.accentBarColor = accentBarColor;
            this.quoteBars = quoteBars;
            this.quoteBarColor = quoteBarColor;
            this.lineHovered = lineHovered;
            this.bubbleHovered = bubbleHovered;
            this.bubbleColor = bubbleColor;
            this.hoverBubbleColor = hoverBubbleColor;
            this.system = system;
        }

        /**
         * PAINT 级重烘焙:气泡底色与行段流按 hover 态选择基础数据后乘淡出 alpha;
         * alpha ≥ 255 时零分配复用基础列表/颜色(淡出期间才发生复制)。
         */
        void bake(int alpha) {
            int a = Math.max(0, Math.min(255, alpha));
            for (int i = 0; i < messageNodes.size(); i++) {
                if (system) {
                    break; // 系统消息无气泡背景(现状语义)
                }
                int base = bubbleHovered[i] ? hoverBubbleColor : bubbleColor;
                messageNodes.get(i).setBackgroundColor(
                        a >= 255 ? base : ChatCardComposer.fadeColor(base, a));
            }
            for (SceneNode accentBar : accentBars) {
                accentBar.setBackgroundColor(
                        a >= 255 ? accentBarColor : ChatCardComposer.fadeColor(accentBarColor, a));
            }
            for (SceneNode quoteBar : quoteBars) {
                quoteBar.setBackgroundColor(
                        a >= 255 ? quoteBarColor : ChatCardComposer.fadeColor(quoteBarColor, a));
            }
            for (int i = 0; i < lineNodes.size(); i++) {
                List<TextSegment> selected = lineHovered[i] && hoverBases.get(i) != null
                        ? hoverBases.get(i) : lineBases.get(i);
                lineNodes.get(i).setSegments(
                        a >= 255 ? selected : ChatCardComposer.fadeSegments(selected, a));
            }
            if (headerNameNode != null && headerNameSegments != null) {
                headerNameNode.setSegments(a >= 255 ? headerNameSegments
                        : ChatCardComposer.fadeSegments(headerNameSegments, a));
            }
            if (headerTimeNode != null && headerTimeSegments != null) {
                headerTimeNode.setSegments(a >= 255 ? headerTimeSegments
                        : ChatCardComposer.fadeSegments(headerTimeSegments, a));
            }
        }
    }

    /** 单消息链接 hover 驱动器:命中判定 + 状态应用(包级,headless 测试可直驱)。 */
    static final class LinkHoverDriver {

        private final ChatMessageList owner;
        private final SceneNode messageNode;
        private final List<SceneNode> lineNodes;
        private final List<List<LinkSpan>> lineSpans;
        private final int lineStartOffset;
        private final int lineHeight;
        private final boolean[] lineHovered;
        private final MessageBake bake;
        private final int[] currentAlpha;
        /** 本驱动器上次应用的 URL(去重基准;不读共享 Signal 未提交值——set 是帧末批量提交)。 */
        private String lastUrl = "";

        LinkHoverDriver(ChatMessageList owner, SceneNode messageNode, List<SceneNode> lineNodes,
                List<List<LinkSpan>> lineSpans, int lineStartOffset, int lineHeight,
                boolean[] lineHovered, MessageBake bake, int[] currentAlpha) {
            this.owner = owner;
            this.messageNode = messageNode;
            this.lineNodes = lineNodes;
            this.lineSpans = lineSpans;
            this.lineStartOffset = lineStartOffset;
            this.lineHeight = lineHeight;
            this.lineHovered = lineHovered;
            this.bake = bake;
            this.currentAlpha = currentAlpha;
        }

        /** 指针移动(相对 messageNode 局部):命中链接 → 该行 hover;未命中 → 全清。 */
        void onPointerMove(int localX, int localY) {
            apply(resolveUrl(localX, localY));
        }

        /** 指针离开气泡:清空链接 hover 并复位光标。 */
        void onPointerLeave() {
            clearLineHover();
            apply(null);
        }

        /** 命中判定 + 行 hover 态写入,返回命中 URL(行内区域 = 文本包围盒上下 +2 / 左右 +1)。 */
        String resolveUrl(int localX, int localY) {
            AnchorRect messageBox = SceneGeometry.absoluteBox(messageNode, 0, 0);
            clearLineHover();
            for (int i = 0; i < lineSpans.size(); i++) {
                List<LinkSpan> spans = lineSpans.get(i);
                if (spans == null || spans.isEmpty()) {
                    continue;
                }
                AnchorRect lineBox = SceneGeometry.absoluteBox(lineNodes.get(i), 0, 0);
                int relX = localX - (lineBox.getX() - messageBox.getX());
                int relY = localY - (lineBox.getY() - messageBox.getY());
                if (relY < -ChatUrlLinkifier.HIT_PAD_Y
                        || relY >= lineHeight + ChatUrlLinkifier.HIT_PAD_Y) {
                    continue;
                }
                for (LinkSpan span : spans) {
                    if (relX >= span.startX - ChatUrlLinkifier.HIT_PAD_X
                            && relX < span.startX + span.width + ChatUrlLinkifier.HIT_PAD_X) {
                        lineHovered[lineStartOffset + i] = true;
                        return span.url;
                    }
                }
            }
            return null;
        }

        private void clearLineHover() {
            Arrays.fill(lineHovered, false);
        }

        /**
         * 应用 hover 状态:URL 变化才写信号/光标/重烘焙(同 URL 移动零开销)。
         *
         * <p>去重基准 = 本驱动器上次应用的 URL,而非共享 {@code hoverLink} 的
         * {@link Signal#get()}——{@code set} 是帧末批量提交,同一输入帧内多次驱动
         * (命中 → 移出)时 get() 仍返回上一帧提交值,会把净变化误判为无变化而漏恢复。</p>
         */
        private void apply(String url) {
            String next = url == null ? "" : url;
            if (lastUrl.equals(next)) {
                return;
            }
            lastUrl = next;
            owner.hoverLink.set(next);
            messageNode.setCursor(url != null ? SceneCursor.POINTER : SceneCursor.DEFAULT);
            bake.bake(currentAlpha[0]);
        }

        /** @return 当前行 hover 态(测试/恢复用)。 */
        boolean[] lineHoveredForTest() {
            return lineHovered;
        }
    }

    private final SegmentParser segmentParser;
    /** 段宽度度量;null = 链接化特性关闭(旧行为,测试/纯文本注入)。 */
    private final SegmentMeasurer segmentMeasurer;
    /** 段流后处理;null = 关闭(T8 latex 行高约束接入点,生产注入)。 */
    private final SegmentPostProcessor segmentPostProcessor;

    /** 段解析缓存(text@baseColor → segments;LRU,epoch 不参与——段流宽度渲染时才算)。 */
    private final Map<String, List<TextSegment>> segmentCache =
            new LinkedHashMap<String, List<TextSegment>>(64, 0.75F, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<TextSegment>> eldest) {
            return size() > SEGMENT_CACHE_MAX;
        }
    };

    /** hover 段流缓存(text@baseColor → hover 变体;key 与 {@link #segmentCache} 同构)。 */
    private final Map<String, List<TextSegment>> hoverSegmentCache =
            new LinkedHashMap<String, List<TextSegment>>(64, 0.75F, true) {
        private static final long serialVersionUID = 2L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<TextSegment>> eldest) {
            return size() > SEGMENT_CACHE_MAX;
        }
    };

    /** 当前 hover 链接 URL(空串 = 无;设计稿 §6.3 hoverLinkSignal)。 */
    private final Signal<String> hoverLink = Signal.create("");

    /** 消息节点 → 链接 hover 驱动器(测试探针;树重建时随组节点一起弃用)。 */
    private final Map<SceneNode, LinkHoverDriver> linkDrivers =
            new java.util.IdentityHashMap<SceneNode, LinkHoverDriver>();

    /**
     * 纯文本形态(无链接度量):不启用 URL 链接化(旧行为)。
     *
     * @param segmentParser 段解析器(生产/测试注入)
     */
    public ChatMessageList(SegmentParser segmentParser) {
        this(segmentParser, null);
    }

    /**
     * 完整形态(链接化启用):段解析 + 段宽度度量。
     *
     * @param segmentParser  段解析器(生产/测试注入)
     * @param segmentMeasurer 段宽度度量(null = 关闭链接化)
     */
    public ChatMessageList(SegmentParser segmentParser, SegmentMeasurer segmentMeasurer) {
        this(segmentParser, segmentMeasurer, null);
    }

    /**
     * 完整形态 + 段流后处理(T8 设计稿 §3.5 行内 LaTeX 行高约束接入点)。
     *
     * @param segmentParser   段解析器(生产/测试注入)
     * @param segmentMeasurer 段宽度度量(null = 关闭链接化)
     * @param segmentPostProcessor 段流后处理(null = 关闭;生产注入 latex 行高约束)
     */
    public ChatMessageList(SegmentParser segmentParser, SegmentMeasurer segmentMeasurer,
            SegmentPostProcessor segmentPostProcessor) {
        if (segmentParser == null) {
            throw new IllegalArgumentException("segmentParser 不能为空");
        }
        this.segmentParser = segmentParser;
        this.segmentMeasurer = segmentMeasurer;
        this.segmentPostProcessor = segmentPostProcessor;
    }

    /** @return 链接化是否启用(度量注入后才计算命中区域)。 */
    boolean isLinkifyEnabled() {
        return segmentMeasurer != null;
    }

    /**
     * 组 key = 首条消息序列号(进程内唯一,稳定)+ 组内行数(内容版本)。
     * 加行/切断/换发送者 → key 变化 → 重建组节点;真机 messageId 恒 0,不可用作身份。
     */
    public static Long groupKey(ChatCardComposer.ComposedGroup group) {
        long firstSequence = group.getMessages().isEmpty() ? 0L
                : group.getMessages().get(0).getRecord().getSequenceId();
        long lineCount = 0L;
        for (ChatCardComposer.MessageLines message : group.getMessages()) {
            lineCount += message.getDisplayLines().size();
        }
        return Long.valueOf(firstSequence * 10000L + lineCount);
    }

    /**
     * 把组列表挂到 {@code listParent}(独占容器)上。
     *
     * @param rt           宿主场景运行时
     * @param listParent   列表挂载节点(独占,子节点全由本列表管理)
     * @param groups       组列表数据源
     * @param style        形态(HUD/容器)
     * @param registry     消息节点 → 记录登记表(命中检测用,调用方持有)
     * @param frameMillis  帧时钟(HUD 淡出驱动)
     * @return 列表句柄(dispose 卸载整列表)
     */
    public SceneListHandle mount(SceneRuntime rt, SceneNode listParent,
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups, Style style,
            Map<SceneNode, ChatLineRecord> registry, ReadableSignal<Long> frameMillis) {
        listParent.setGap(style.getGroupGapPx());
        return rt.forEach(listParent, groups, ChatMessageList::groupKey,
                group -> buildGroupNode(rt, group, style, registry, frameMillis));
    }

    /** 构建单组子树:组头 row(名字+时间)+ 消息气泡(背景/四角圆角/行段);HUD 形态挂淡出绑定。 */
    private SceneNode buildGroupNode(SceneRuntime rt, ChatCardComposer.ComposedGroup group, Style style,
            Map<SceneNode, ChatLineRecord> registry, ReadableSignal<Long> frameMillis) {
        int fontSize = ChatMarkdownSettings.getChatFontSizePx();
        int lineHeight = ChatMarkdownSettings.getChatLineHeightPx();
        int paddingX = ChatMarkdownSettings.getBubblePaddingX();
        int paddingY = ChatMarkdownSettings.getBubblePaddingY();
        boolean system = group.getAlignment() == MessageGroupModel.Alignment.SYSTEM_CENTER;
        boolean selfRight = group.getAlignment() == MessageGroupModel.Alignment.SELF_RIGHT;
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
        // 组头 row 双节点(设计稿 §3.3/§6.1):名字 12px 加粗(名字色)+ 时间 10px(时间戳色),gap 4;
        // 他人组左对齐 + padding 左 2,自己组右对齐 + padding 右 2;名字为空(自己组默认)只建时间节点。
        SceneNode headerRow = null;
        SceneNode nameNode = null;
        SceneNode timeNode = null;
        List<TextSegment> headerNameBase = null;
        List<TextSegment> headerTimeBase = null;
        if (!group.getHeaderName().isEmpty() || !group.getHeaderTime().isEmpty()) {
            headerRow = SceneNode.row(4)
                    .setHitTestable(false)
                    .setAlignSelf(align);
            if (selfRight) {
                headerRow.setPadding(0, 2, 0, 0);
            } else {
                headerRow.setPadding(0, 0, 0, 2);
            }
            if (!group.getHeaderName().isEmpty()) {
                headerNameBase = segmentParser.parse("§l" + group.getHeaderName(), group.getNameColor());
                nameNode = new SceneNode()
                        .setHitTestable(false)
                        .setFontSize(ChatMarkdownSettings.getNameFontSizePx())
                        .setSegments(headerNameBase)
                        .setTextVerticalAlign(TextVerticalAlign.TOP);
                headerRow.appendChild(nameNode);
            }
            if (!group.getHeaderTime().isEmpty()) {
                headerTimeBase = segmentParser.parse(group.getHeaderTime(),
                        ChatMarkdownSettings.getTimeTextArgb());
                timeNode = new SceneNode()
                        .setHitTestable(false)
                        .setFontSize(ChatMarkdownSettings.getTimestampFontSizePx())
                        .setSegments(headerTimeBase)
                        .setTextVerticalAlign(TextVerticalAlign.TOP);
                headerRow.appendChild(timeNode);
            }
            groupNode.appendChild(headerRow);
        }
        int baseTextColor = system ? ChatMarkdownSettings.getSystemTextArgb() : 0xFFFFFFFF;
        int bubbleColor = selfRight ? ChatMarkdownSettings.getBubbleSelfArgb()
                : ChatMarkdownSettings.getBubbleOtherArgb();
        int hoverBubbleColor = ChatCardComposer.hoveredBubbleColor(bubbleColor);
        // 方案A accent(§10 已拍板):仅自己气泡 = row[内容列 + 2px 强调条];他人/classic/系统 = 现状 column
        boolean accent = selfRight && ChatMarkdownSettings.getSelfBubbleStyle()
                == ChatMarkdownSettings.SelfBubbleStyle.ACCENT;
        int rLg = ChatMarkdownSettings.getBubbleCornerRadius();
        int rInner = ChatMarkdownSettings.getBubbleInnerCornerRadiusPx();
        List<SceneNode> messageNodes = new ArrayList<SceneNode>();
        List<SceneNode> accentBars = new ArrayList<SceneNode>();
        List<SceneNode> quoteBars = new ArrayList<SceneNode>();
        List<SceneNode> lineNodes = new ArrayList<SceneNode>();
        List<List<TextSegment>> lineBases = new ArrayList<List<TextSegment>>();
        List<List<TextSegment>> hoverBases = new ArrayList<List<TextSegment>>();
        // 行级链接跨度(与 lineNodes 扁平对齐;hover 驱动器按消息行区间切片)
        List<List<LinkSpan>> messageLineSpans = new ArrayList<List<LinkSpan>>();
        List<ChatCardComposer.MessageLines> messages = group.getMessages();
        int messageCount = messages.size();
        // 每条消息在 lineNodes 中的起始行索引(hover 驱动器按消息切片)
        int[] messageLineStart = new int[messageCount];
        int globalLineIndex = 0;
        for (int i = 0; i < messageCount; i++) {
            ChatCardComposer.MessageLines message = messages.get(i);
            messageLineStart[i] = globalLineIndex;
            SceneNode messageNode;
            SceneNode contentNode;
            if (accent) {
                // 行本体只挂背景与四角圆角,不承载 padding;padding 由内容列承载(设计稿 §6.1 AccentBar)
                messageNode = SceneNode.row()
                        .setHitTestable(true)
                        .setWidthSizing(SceneNode.WidthSizing.SHRINK)
                        .setBackgroundColor(bubbleColor);
                setGradedCorners(messageNode, cornersFor(messageCount, i, selfRight, rLg, rInner));
                contentNode = SceneNode.column().setPadding(paddingY, paddingX, paddingY, paddingX);
                messageNode.appendChild(contentNode);
                SceneNode accentBar = new SceneNode()
                        .setHitTestable(false)
                        .setPreferredWidth(2)
                        .setFillParentHeight(true)
                        .setMargin(4, 0, 4, 0)
                        .setBackgroundColor(ChatMarkdownSettings.getAccentBarSelfArgb())
                        .setCornerRadius(2);
                messageNode.appendChild(accentBar);
                accentBars.add(accentBar);
            } else {
                messageNode = SceneNode.column()
                        .setHitTestable(true)
                        .setWidthSizing(SceneNode.WidthSizing.SHRINK);
                if (!system) {
                    messageNode.setBackgroundColor(bubbleColor)
                            .setPadding(paddingY, paddingX, paddingY, paddingX);
                    setGradedCorners(messageNode, cornersFor(messageCount, i, selfRight, rLg, rInner));
                }
                contentNode = messageNode;
            }
            for (String line : message.getDisplayLines()) {
                // 引用行(T6b 设计稿 §3.5):行文本以 "> " 或 ">" 开头 → 剥前缀 + 文字降
                // text-secondary + 行首 2px 竖条(0x40FFFFFF);剥前缀后的文本照常参与
                // linkify/code 切分(系统消息保持"不链接化"语义,引用也只作用于气泡行)。
                boolean quoteLine = line.startsWith("> ");
                String renderLine = quoteLine ? line.substring(2) : line;
                if (!quoteLine && line.startsWith(">")) {
                    quoteLine = true;
                    renderLine = line.substring(1);
                }
                int lineBaseColor = quoteLine ? ChatMarkdownSettings.getTextSecondaryArgb()
                        : baseTextColor;
                // 系统消息不链接化/不 code 切分(设计稿 §5.2:系统消息不可点不 hover;
                // §3.5 排版规则仅作用于气泡内)
                List<TextSegment> segments = parseCached(renderLine, lineBaseColor, !system);
                List<TextSegment> hover = null;
                List<LinkSpan> spans = Collections.<LinkSpan>emptyList();
                if (segmentMeasurer != null) {
                    spans = linkSpansOf(segments, segmentMeasurer, fontSize);
                    if (!spans.isEmpty()) {
                        hover = hoverCached(renderLine, lineBaseColor);
                    }
                }
                messageLineSpans.add(spans);
                SceneNode lineNode = new SceneNode()
                        .setHitTestable(false)
                        .setFontSize(fontSize)
                        .setSegments(segments)
                        .setTextVerticalAlign(TextVerticalAlign.TOP)
                        .setPreferredHeight(Math.max(1, lineHeight));
                // T8 设计稿 §5.4(验收 22):HUD 形态行节点携带 maxLines=8 + 省略号语义;
                // 实际行数截断在 L2 ChatCardComposer(displayLines 上限),此处为节点级
                // 语义一致 + 防御(行文本含换行符时 SceneLineClamp 生效);容器形态不设。
                if (style.isTtlFade()) {
                    lineNode.setMaxLines(ChatCardComposer.HUD_MAX_LINES)
                            .setEllipsis(true);
                }
                if (quoteLine) {
                    // 引用行结构 = row[竖条(宽2、bar-quote 色、fillParentHeight、圆角1、不可命中)
                    // + gap 6 + 文本节点];相邻行各自 18px 竖条行高无缝衔接即视觉连续
                    // (同一消息内行间无 gap;跨消息 2px 组内间距处竖条留 2px 缺口,属可接受)。
                    SceneNode quoteRow = SceneNode.row(QUOTE_GAP_PX)
                            .setHitTestable(false);
                    SceneNode quoteBar = new SceneNode()
                            .setHitTestable(false)
                            .setPreferredWidth(QUOTE_BAR_WIDTH_PX)
                            .setFillParentHeight(true)
                            .setBackgroundColor(ChatMarkdownSettings.getQuoteBarArgb())
                            .setCornerRadius(QUOTE_BAR_RADIUS_PX);
                    quoteRow.appendChild(quoteBar);
                    quoteRow.appendChild(lineNode);
                    contentNode.appendChild(quoteRow);
                    quoteBars.add(quoteBar);
                } else {
                    contentNode.appendChild(lineNode);
                }
                lineNodes.add(lineNode);
                lineBases.add(segments);
                hoverBases.add(hover);
                globalLineIndex++;
            }
            groupNode.appendChild(messageNode);
            messageNodes.add(messageNode);
            registry.put(messageNode, message.getRecord());
        }
        // ==================== T6a:气泡 hover 叠加 + 链接 hover/tooltip(两形态共用) ====================
        final boolean[] lineHovered = new boolean[lineNodes.size()];
        final boolean[] bubbleHovered = new boolean[messageCount];
        final int[] currentAlpha = new int[] { 255 };
        final MessageBake bake = new MessageBake(messageNodes, lineNodes, lineBases, hoverBases,
                nameNode, headerNameBase, timeNode, headerTimeBase, accentBars,
                ChatMarkdownSettings.getAccentBarSelfArgb(), quoteBars,
                ChatMarkdownSettings.getQuoteBarArgb(), lineHovered, bubbleHovered,
                bubbleColor, hoverBubbleColor, system);
        if (style.isTtlFade()) {
            // HUD 形态:组出生 enter 动画(设计稿 §4.1 行1)——translateY +8→0 + opacity 0→1,
            // 180ms easeOutCubic,基准 = 组内最新消息到达时刻(wall-clock;组树重建后老组按进度
            // 立即稳态,不重播;完成态 opacity=1 / transform 恒等,渲染引擎走快速路径)。
            final long bornMillis = group.getLatestMillis();
            rt.bind(Computed.create(() -> Float.valueOf(
                            enterOpacity(bornMillis, frameMillis.get().longValue()))),
                    opacity -> groupNode.setOpacity(opacity.floatValue()));
            rt.bind(Computed.create(() -> enterTransform(bornMillis, frameMillis.get().longValue())),
                    transform -> groupNode.setTransform(transform));
            // 淡出烘焙:fadeAlpha → currentAlpha → bake(正常/hover 两态同源)
            rt.bind(Computed.create(() -> Integer.valueOf(ChatCardComposer.fadeAlpha(
                            group.getLatestMillis(), frameMillis.get().longValue(),
                            ChatMarkdownSettings.getHudTtlMillis(),
                            ChatMarkdownSettings.getHudFadeMillis(), 255))),
                    alpha -> {
                        int a = alpha.intValue();
                        if (a != currentAlpha[0]) {
                            currentAlpha[0] = a;
                            bake.bake(a);
                        }
                    });
        }
        // 气泡 hover 底色(仅非系统消息;3% 白叠加,PAINT 级与淡出共同烘焙)
        if (!system) {
            for (int i = 0; i < messageCount; i++) {
                final SceneNode messageNode = messageNodes.get(i);
                final int idx = i;
                final boolean[] ownBubble = new boolean[] { bubbleHovered[idx] };
                rt.bind(rt.interactionState(messageNode).hovered(), hovered -> {
                    boolean now = Boolean.TRUE.equals(hovered);
                    boolean changed = now != ownBubble[0];
                    ownBubble[0] = now;
                    bubbleHovered[idx] = now;
                    if (!now) {
                        LinkHoverDriver driver = linkDrivers.get(messageNode);
                        if (driver != null) {
                            driver.onPointerLeave();
                        }
                    }
                    if (changed) {
                        bake.bake(currentAlpha[0]);
                    }
                });
            }
        }
        // 链接 hover + tooltip(仅非系统消息,设计稿 §5.2:系统消息不可点不 hover;含链接行才装配)
        if (segmentMeasurer != null && !system) {
            for (int i = 0; i < messageCount; i++) {
                final SceneNode messageNode = messageNodes.get(i);
                int lineStart = messageLineStart[i];
                int lineEnd = i + 1 < messageCount ? messageLineStart[i + 1] : lineNodes.size();
                final List<SceneNode> messageLineNodes = new ArrayList<SceneNode>(
                        lineNodes.subList(lineStart, lineEnd));
                final List<List<LinkSpan>> messageSpans = new ArrayList<List<LinkSpan>>(
                        messageLineSpans.subList(lineStart, lineEnd));
                boolean hasLinks = false;
                for (List<LinkSpan> spans : messageSpans) {
                    if (!spans.isEmpty()) {
                        hasLinks = true;
                        break;
                    }
                }
                if (!hasLinks) {
                    continue;
                }
                // 惰性清理离树节点的旧驱动器(树重建后旧组节点不再有输入)
                linkDrivers.entrySet().removeIf(entry -> entry.getKey().__getParent() == null);
                final LinkHoverDriver driver = new LinkHoverDriver(this, messageNode,
                        messageLineNodes, messageSpans, lineStart, lineHeight, lineHovered,
                        bake, currentAlpha);
                linkDrivers.put(messageNode, driver);
                final LinkHoverDriver boundDriver = driver;
                rt.on(messageNode, SceneEventType.POINTER_MOVE, (ev, ctx) -> {
                    boundDriver.onPointerMove(ctx.getLocalPointerX(), ctx.getLocalPointerY());
                });
                // 400ms 悬停出 URL tooltip(SceneTooltip;无输入宿主自然不显示)
                SceneTooltip.attach(rt, new SceneTooltip.Props(messageNode,
                        Computed.create(() -> hoverLink.get()),
                        Computed.create(() -> Boolean.valueOf(!hoverLink.get().isEmpty())),
                        LINK_TOOLTIP_DELAY_MILLIS, LINK_TOOLTIP_MAX_WIDTH_PX, LINK_TOOLTIP_MAX_LINES));
            }
        }
        return groupNode;
    }

    /** 把分级四角写入节点(T4a 四角 API)。 */
    private static void setGradedCorners(SceneNode node, int[] corners) {
        node.setCornerRadius(corners[0], corners[1], corners[2], corners[3]);
    }

    /** 组内节点圆角分级(tl/tr/br/bl,设计稿 §3.3):单消息全 r-lg;首 = 上 12 下 4;
     * 中 = 全 4;尾 = 上 4 下 12,尾巴角(他人左下/自己右下)保持 4——LAST 公式
     * (r-inner, r-inner, 他人?r-lg:r-inner, 他人?r-inner:r-lg)。 */
    private static int[] cornersFor(int messageCount, int index, boolean selfRight, int rLg, int rInner) {
        if (messageCount == 1) {
            return new int[] { rLg, rLg, rLg, rLg };
        }
        if (index == 0) {
            return new int[] { rLg, rLg, rInner, rInner };
        }
        if (index == messageCount - 1) {
            return new int[] { rInner, rInner, selfRight ? rInner : rLg, selfRight ? rLg : rInner };
        }
        return new int[] { rInner, rInner, rInner, rInner };
    }

    /** HUD 组出生 enter 动画进度(纯函数,wall-clock):距组内最新消息时刻 / enterAnimMillis,夹取 [0,1]。 */
    private static float enterProgress(long bornMillis, long nowMillis) {
        long duration = ChatMarkdownSettings.getEnterAnimMillis();
        if (duration <= 0) {
            return 1.0F; // 动画时长 ≤ 0 配置:直接稳态,不播放
        }
        return Animator.clamp01((float) (nowMillis - bornMillis) / (float) duration);
    }

    /** HUD 组出生 enter opacity(纯函数):easeOutCubic 曲线 0→1,动画结束恒 1(设计稿 §4.1/§4.3)。 */
    static float enterOpacity(long bornMillis, long nowMillis) {
        return Animator.easeOutCubic(enterProgress(bornMillis, nowMillis));
    }

    /** HUD 组出生 enter transform(纯函数):translateY +8→0;完成态恒等(渲染快速路径零边界命令)。 */
    static Transform enterTransform(long bornMillis, long nowMillis) {
        float eased = Animator.easeOutCubic(enterProgress(bornMillis, nowMillis));
        return Transform.translate(0.0F, ENTER_TRANSLATE_PX * (1.0F - eased));
    }

    /**
     * 段解析缓存(text@baseColor;启用链接化且非系统消息时缓存产物 = code 切分 + 链接化后的段流;
     * 系统消息与未注入度量的纯文本形态走原样 § 解析)。
     *
     * <p>T6b 解析顺序：先 {@link ChatCodeSpanSplitter#split code 切分} 再
     * {@link ChatUrlLinkifier#linkify linkify}——code 段是文本语义边界（不嵌套解析），
     * linkify 已对 code 段跳过；若反向（先 linkify），URL 扫描会把反引号吞进 URL 文本
     * （反引号不在 URL 分隔符/尾随标点集），code 配对被破坏（headless 实测
     * "``http://a.co``" 链接化后闭引号进入 link 段）。</p>
     */
    private List<TextSegment> parseCached(String text, int baseColor, boolean linkify) {
        String key = text + '@' + baseColor + (linkify ? '@' : '!');
        List<TextSegment> hit = segmentCache.get(key);
        if (hit != null) {
            return hit;
        }
        List<TextSegment> segments = segmentParser.parse(text, baseColor);
        // T8 设计稿 §3.5:行内 LaTeX 行高约束(超 1.6× 行高按 0.85 缩放重排),在其他
        // 段变换(code 切分/链接化)之前执行——latex 段是原子段,变换均透传,顺序无实质差异;
        // 生产注入 TextLayoutService.applyLatexLineHeightConstraint,测试注入替身/关闭。
        if (segmentPostProcessor != null) {
            segments = segmentPostProcessor.postProcess(segments, ChatMarkdownSettings.getChatFontSizePx());
        }
        if (linkify) {
            segments = ChatCodeSpanSplitter.split(segments, ChatMarkdownSettings.getCodeBackgroundArgb());
        }
        if (segmentMeasurer != null && linkify) {
            segments = ChatUrlLinkifier.linkify(segments, ChatMarkdownSettings.getLinkArgb());
        }
        segmentCache.put(key, segments);
        return segments;
    }

    /** hover 段流缓存(text@baseColor → 链接段换 hover 色 + 下划线)。 */
    private List<TextSegment> hoverCached(String text, int baseColor) {
        String key = text + '@' + baseColor;
        List<TextSegment> hit = hoverSegmentCache.get(key);
        if (hit != null) {
            return hit;
        }
        List<TextSegment> hover = ChatUrlLinkifier.hoverLinkify(
                parseCached(text, baseColor, true), ChatMarkdownSettings.getLinkHoverArgb());
        hoverSegmentCache.put(key, hover);
        return hover;
    }

    /** 行内链接跨度:逐段累计 x,link 段登记(段宽 = 注入度量,与渲染同源)。 */
    private static List<LinkSpan> linkSpansOf(List<TextSegment> segments, SegmentMeasurer measurer,
            int fontSizePx) {
        List<LinkSpan> spans = null;
        float x = 0.0F;
        for (TextSegment segment : segments) {
            float width = Math.max(0.0F, measurer.widthOf(segment, fontSizePx));
            if (segment.getStyle().getLink() != null) {
                if (spans == null) {
                    spans = new ArrayList<LinkSpan>(2);
                }
                spans.add(new LinkSpan(x, width, segment.getStyle().getLink()));
            }
            x += width;
        }
        return spans == null ? Collections.<LinkSpan>emptyList() : spans;
    }

    /** 测试探针:消息节点 → 链接 hover 驱动器。 */
    LinkHoverDriver __linkHoverDriverOf(SceneNode messageNode) {
        return linkDrivers.get(messageNode);
    }
}
