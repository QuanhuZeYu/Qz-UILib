package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatCardComposer;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatClock;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageGroupModel;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.runtime.SceneListHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 消息列表组件(L3 渲染层,唯一消息渲染器):组头(名字+时间)+ 消息气泡(背景/圆角/行段)。
 *
 * <p>HUD 气泡流与容器列表共享本组件,形态差异由 {@link Style} 表达(组间距 + 是否 TTL 淡出),
 * 不再靠布尔分叉。段解析缓存按实例隔离(每个 controller 一份,测试注入 parser 互不串味)。</p>
 */
public final class ChatMessageList {

    /** 段解析缓存上限(历史 100 行 × 每行数行 + 组头)。 */
    private static final int SEGMENT_CACHE_MAX = 400;

    /** 段解析器(文本 → 样式段流;生产 = TextLayoutService.parseSegments,测试注入)。 */
    public interface SegmentParser {
        /** @return 文本 → 样式段流 */
        List<TextSegment> parse(String text, int baseColor);
    }

    /** 消息列表形态:HUD(紧凑 + TTL 淡出)与容器(宽松 + 恒显)的唯一差异。 */
    public static final class Style {

        private final int groupGapPx;
        private final boolean ttlFade;

        private Style(int groupGapPx, boolean ttlFade) {
            this.groupGapPx = groupGapPx;
            this.ttlFade = ttlFade;
        }

        /** HUD 形态:组间紧密堆叠 + 10s 存活淡出。 */
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

    private final SegmentParser segmentParser;

    /** 段解析缓存(text@baseColor → segments;LRU,epoch 不参与——段流宽度渲染时才算)。 */
    private final Map<String, List<TextSegment>> segmentCache =
            new LinkedHashMap<String, List<TextSegment>>(64, 0.75F, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<TextSegment>> eldest) {
            return size() > SEGMENT_CACHE_MAX;
        }
    };

    /**
     * @param segmentParser 段解析器(生产/测试注入)
     */
    public ChatMessageList(SegmentParser segmentParser) {
        if (segmentParser == null) {
            throw new IllegalArgumentException("segmentParser 不能为空");
        }
        this.segmentParser = segmentParser;
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

    /** 构建单组子树:组头(名字+时间)+ 消息气泡(背景/圆角/行段);HUD 形态挂淡出绑定。 */
    private SceneNode buildGroupNode(SceneRuntime rt, ChatCardComposer.ComposedGroup group, Style style,
            Map<SceneNode, ChatLineRecord> registry, ReadableSignal<Long> frameMillis) {
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
            if (group.getAlignment() == MessageGroupModel.Alignment.SELF_RIGHT) {
                // 自己的消息:只显示时间(名字与气泡同为主题蓝,不显示名字避免撞色)
                headerBase.addAll(segmentParser.parse(ChatClock.formatTime(group.getLatestMillis()),
                        ChatMarkdownSettings.getTimeTextArgb()));
            } else {
                headerBase.addAll(segmentParser.parse(group.getSender(), group.getNameColor()));
                headerBase.addAll(segmentParser.parse(" " + ChatClock.formatTime(group.getLatestMillis()),
                        ChatMarkdownSettings.getTimeTextArgb()));
            }
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
        if (style.isTtlFade()) {
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
    private static void applyAlpha(List<SceneNode> messageNodes, List<SceneNode> lineNodes,
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
}
