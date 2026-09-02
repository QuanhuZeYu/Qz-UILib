package club.heiqi.uilib.internal.chat3.view;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.util.IChatComponent;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.data.ChatLineRecord;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatCardComposer;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatCodeSpanSplitter;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatLineLayouter;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatMarkdownLineRule;
import club.heiqi.uilib.internal.chat3.viewmodel.ChatUrlLinkifier;
import club.heiqi.uilib.internal.chat3.viewmodel.MessageGroupModel;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTooltip;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
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
 *
 * <p>HUD 淡出 = 每条消息的可见显示预算(仅 HUD 实际渲染时按可见时钟消耗,聊天框打开期间
 * 冻结;注入 {@code hudVisible} 后生效);入场动画仅新组(isEnterOnMount)播放,组增长
 * 重建/重挂载不重播。</p>
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

    /** 组头行高(px,设计稿 §3.3/§2.2:font-name 12/16 与 font-meta 10/16 同行,组头高 16;
     * 单一事实源 = ChatMarkdownSettings.getChatHeaderRowHeightPx(),HUD 高度估算同口径)。 */
    private static final int HEADER_ROW_HEIGHT_PX = ChatMarkdownSettings.getChatHeaderRowHeightPx();

    /** 方案A 强调条宽(px,设计稿 §3.3/§2.1:自己气泡右内缘 2px 竖条)。 */
    private static final int ACCENT_BAR_WIDTH_PX = 2;

    /** 组头与首个气泡间距(px,设计稿 §2.3 sp-2=3;组内相邻消息仍为 sp-1=2 两级 gap)。 */
    private static final int HEADER_TO_BUBBLE_GAP_PX = 3;
    /** 块级公式独占行上下间距(px,设计稿 §3.5/§10.1:上下各 4px,左对齐不居中)。 */
    private static final int BLOCK_MATH_GAP_PX = 4;
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

    /**
     * 行内链接跨度(行内相对坐标,命中区域扩展在命中判定时统一应用)。
     *
     * <p>{@code url} 可写:长 URL 被字符硬断成多个显示行时,每行只能看到片段,完整 URL
     * 要等链闭合才知道,届时由 {@link UrlChain#close()} 回填到链上所有跨度。</p>
     */
    static final class LinkSpan {
        final float startX;
        final float width;
        String url;

        LinkSpan(float startX, float width, String url) {
            this.startX = startX;
            this.width = width;
            this.url = url;
        }
    }

    /**
     * 跨显示行的 URL 续链累加器(每条消息一个实例)。
     *
     * <p>不变量:处理完一行后,{@code urlChain} 开放 ⟺ 该行最后一段是 link 段;
     * {@link #url()} = 该链已拼出的 URL 全文,{@link #spans} = 链上所有待回填跨度。
     * 下一行只有 {@code LineFragment.continuesWord()} 为真(词内字符硬断,断点两侧原文
     * 无空白)才接链——词边界回退会丢弃断点空白,两种断行在行文本上同形,只有切分器能
     * 区分,故该标记必须由 {@code ChatLineLayouter} 上报而非在此反推。</p>
     */
    private static final class UrlChain {

        private String url = "";
        private final List<LinkSpan> spans = new ArrayList<LinkSpan>(2);
        private LinkSpan lastSpan;

        /** @return true = 已有链头(某行末尾是一段未闭合 URL) */
        boolean open() {
            return !spans.isEmpty();
        }

        /** @return 已累积的 URL 全文(链未闭合时是「到目前为止」的前缀) */
        String url() {
            return url;
        }

        /** 链头:本行末尾是一段 URL(scheme 在本行内)。 */
        void start(String headUrl, LinkSpan span) {
            url = headUrl == null ? "" : headUrl;
            register(span);
        }

        /** 链中:本行行首是上一行 URL 的延续片段。 */
        void extend(String accumulated, LinkSpan span) {
            url = accumulated;
            register(span);
        }

        private void register(LinkSpan span) {
            if (span != null && span != lastSpan) {
                spans.add(span);
                lastSpan = span;
            }
        }

        /** 闭合:把完整 URL 回填到链上每个跨度;无链时零操作(幂等)。 */
        void close() {
            for (int i = 0; i < spans.size(); i++) {
                spans.get(i).url = url;
            }
            spans.clear();
            lastSpan = null;
            url = "";
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

        /** HUD 形态:组间紧密堆叠 + 12s 存活淡出(默认 TTL 12000/easeInQuad 800ms);
         *  TB1 常驻模式(hudPersistMessages=true,默认):淡出在烘焙处关闭,enter 动画保留。 */
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

        /** 气泡 hover 叠加插值时长(ms,设计稿 §4.1:100 easeOutQuad)。 */
        private static final long BUBBLE_HOVER_MS = 100L;
        /** 链接 hover 提亮插值时长(ms,设计稿 §4.1:80 easeOutQuad)。 */
        private static final long LINK_HOVER_MS = 80L;

        /** 气泡/行 hover 插值进度(0..1,与目标态布尔数组同构;每帧由 advanceHover 推进)。 */
        private final float[] bubbleProgress;
        private final float[] lineProgress;
        private final int[] lastBubbleTarget;
        private final int[] lastLineTarget;
        private final long[] bubbleAnchorMillis;
        private final long[] lineAnchorMillis;
        private final float[] bubbleAnchorProgress;
        private final float[] lineAnchorProgress;

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
            this.bubbleProgress = new float[messageNodes.size()];
            this.lineProgress = new float[lineNodes.size()];
            this.lastBubbleTarget = new int[messageNodes.size()];
            this.lastLineTarget = new int[lineNodes.size()];
            this.bubbleAnchorMillis = new long[messageNodes.size()];
            this.lineAnchorMillis = new long[lineNodes.size()];
            this.bubbleAnchorProgress = new float[messageNodes.size()];
            this.lineAnchorProgress = new float[lineNodes.size()];
        }

        /**
         * PAINT 级重烘焙:气泡底色与行段流按 hover 插值进度选择/混合后乘淡出 alpha
         * (P2-4:进度 0..1 中间态逐通道 lerp,替代原布尔硬切;alpha ≥ 255 且端态时
         * 零分配复用基础列表/颜色)。
         */
        void bake(int alpha) {
            int a = Math.max(0, Math.min(255, alpha));
            for (int i = 0; i < messageNodes.size(); i++) {
                if (system) {
                    break; // 系统消息无气泡背景(现状语义)
                }
                float t = bubbleProgress[i];
                int base = t <= 0.0F ? bubbleColor : t >= 1.0F ? hoverBubbleColor
                        : ChatCardComposer.interpolateArgb(bubbleColor, hoverBubbleColor, t);
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
                float t = lineProgress[i];
                List<TextSegment> selected;
                if (hoverBases.get(i) == null || t <= 0.0F) {
                    selected = lineBases.get(i);
                } else if (t >= 1.0F) {
                    selected = hoverBases.get(i);
                } else {
                    selected = ChatCardComposer.interpolateSegments(
                            lineBases.get(i), hoverBases.get(i), t);
                }
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

        /**
         * 推进 hover 插值(每帧由 frameMillis 绑定驱动,P2-4):目标态变化时从当前进度锚定,
         * 按 easeOutQuad 向目标(1=hover/0=常态)推进;反向同样从当前进度续播(双向可逆)。
         *
         * @param nowMillis 当前 wall millis
         * @return 任一进度变化 → true(调用方重烘)
         */
        boolean advanceHover(long nowMillis) {
            boolean changed = false;
            for (int i = 0; i < bubbleHovered.length; i++) {
                int target = bubbleHovered[i] ? 1 : 0;
                if (target != lastBubbleTarget[i]) {
                    lastBubbleTarget[i] = target;
                    bubbleAnchorMillis[i] = nowMillis;
                    bubbleAnchorProgress[i] = bubbleProgress[i];
                }
                if (bubbleProgress[i] != target) {
                    float next = step(bubbleAnchorProgress[i], target,
                            nowMillis - bubbleAnchorMillis[i], BUBBLE_HOVER_MS);
                    if (next != bubbleProgress[i]) {
                        bubbleProgress[i] = next;
                        changed = true;
                    }
                }
            }
            for (int i = 0; i < lineHovered.length; i++) {
                int target = lineHovered[i] ? 1 : 0;
                if (target != lastLineTarget[i]) {
                    lastLineTarget[i] = target;
                    lineAnchorMillis[i] = nowMillis;
                    lineAnchorProgress[i] = lineProgress[i];
                }
                if (lineProgress[i] != target) {
                    float next = step(lineAnchorProgress[i], target,
                            nowMillis - lineAnchorMillis[i], LINK_HOVER_MS);
                    if (next != lineProgress[i]) {
                        lineProgress[i] = next;
                        changed = true;
                    }
                }
            }
            return changed;
        }

        /** 锚点步进:easeOutQuad 曲线,t=0 → anchor、t≥1 → target。 */
        private static float step(float anchor, int target, long elapsedMillis, long durationMillis) {
            if (durationMillis <= 0L || elapsedMillis <= 0L) {
                return anchor;
            }
            if (elapsedMillis >= durationMillis) {
                return target;
            }
            float eased = Animator.easeOut((float) elapsedMillis / (float) durationMillis);
            return anchor + (target - anchor) * eased;
        }
    }

    /** 单消息链接 hover 驱动器:命中判定 + 状态应用(包级,headless 测试可直驱)。 */
    static final class LinkHoverDriver {

        private final ChatMessageList owner;
        private final SceneNode messageNode;
        private final IChatComponent component;
        private final List<SceneNode> lineNodes;
        private final List<List<LinkSpan>> lineSpans;
        private final int lineStartOffset;
        private final int lineHeight;
        private final boolean[] lineHovered;
        private final MessageBake bake;
        private final int[] currentAlpha;
        /** 本驱动器上次应用的 URL(去重基准;不读共享 Signal 未提交值——set 是帧末批量提交)。 */
        private String lastUrl = "";

        LinkHoverDriver(ChatMessageList owner, SceneNode messageNode, IChatComponent component,
                List<SceneNode> lineNodes, List<List<LinkSpan>> lineSpans, int lineStartOffset,
                int lineHeight, boolean[] lineHovered, MessageBake bake, int[] currentAlpha) {
            this.owner = owner;
            this.messageNode = messageNode;
            this.component = component;
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

        /**
         * 链接点击(scene CLICK,指针坐标由框架换算成 messageNode 局部)。
         *
         * <p>刻意与 hover 共用同一个命中函数 {@link #resolveUrl} —— 「亮着的区域」与
         * 「可点的区域」必须同源。坐标也同源:两者都吃框架给的节点局部值,不在这里做任何
         * 屏幕坐标换算。</p>
         */
        /**
         * 链接点击。按钮在驱动内判级:宿主只在左键时取用记录,若右键也记账,会留下一笔
         * 无人消费的残留,被下一次「落在所有消息之外」的左键当成刚点的链接(幽灵打开)。
         */
        void onLinkClick(SceneMouseButton button, int localX, int localY) {
            if (button != SceneMouseButton.LEFT) {
                return;
            }
            String url = resolveUrl(localX, localY);
            owner.deliverLinkClick(new ChatLinkClick(component,
                    url == null || url.isEmpty() ? null : url));
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
                        // 整条链接一起亮:长 URL 被字符硬断成多行时,只亮命中那一行会「半截
                        // 提亮半截不亮」。链闭合时各行 LinkSpan.url 已回填为同一完整 URL,
                        // 所以「同一链接」的判据就是 url 相等,不需要额外链状态。
                        markLinesOfUrl(span.url);
                        return span.url;
                    }
                }
            }
            return null;
        }

        /**
         * 把本消息内所有承载同一 URL 的行置 hover。
         *
         * <p>长 URL 被字符硬断成多个显示行后，每行各有一个跨度；跨行续链已把它们的 url
         * 回填成同一个完整地址，故 url 相等即同一链接。同一条 URL 在一行内出现两次
         * （或跨行各出现一次）也一并点亮。</p>
         */
        private void markLinesOfUrl(String url) {
            if (url == null) {
                return;
            }
            for (int i = 0; i < lineSpans.size(); i++) {
                for (LinkSpan span : lineSpans.get(i)) {
                    if (url.equals(span.url)) {
                        lineHovered[lineStartOffset + i] = true;
                        break;
                    }
                }
            }
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

    /** 链接化模式(parseCached/缓存 key 用):关闭 / 统一 link 色(气泡) / 保留 § 原色(系统消息)。 */
    private enum LinkifyMode {
        /** 不链接化(旧行为;系统消息无链接度量注入时)。 */
        NONE,
        /** 链接化 + 强制统一 link 色(气泡消息,设计稿 §3.5 链接恒 text-link)。 */
        COLORED,
        /** 链接化但保留 URL 原 § 格式色(系统消息,K3/用户拍板 F5:命中 + 点击回投、不强制 0xFF7AB8F5)。 */
        PRESERVE
    }

    /** 当前 hover 链接 URL(空串 = 无;设计稿 §6.3 hoverLinkSignal)。 */
    private final Signal<String> hoverLink = Signal.create("");

    /** 消息节点 → 链接 hover 驱动器(测试探针;树重建时随组节点一起弃用)。 */
    private final Map<SceneNode, LinkHoverDriver> linkDrivers =
            new java.util.IdentityHashMap<SceneNode, LinkHoverDriver>();

    /**
     * 链接点击出口(宿主注册)。CLICK 事件发生时**立即**投递,不做任何延后消费。
     *
     * <p>旧实现把点击"记账"成 pending,等宿主在 {@code mouseClicked} 回调里取走 —— 那是
     * 错的:scene 的 CLICK 由 {@code SceneInputRouter} 在 <b>POINTER_UP</b> 合成,而
     * {@code mouseClicked} 对应 <b>POINTER_DOWN</b>。同一次点击里 DOWN 早于 UP,取账时账上
     * 永远是空的,真机表现即「链接要点第二下才有效」,且第二下开的是上一次点的那条。</p>
     */
    private Consumer<ChatLinkClick> linkClickHandler;

    /** 气泡最大宽上限(px,设计稿 §3.x:气泡 ≤ 0.85 组内容宽;0 = 不限制,headless 默认)。 */
    private volatile int maxBubbleWidthPx;

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
     * 设置气泡最大宽上限(px;0 = 不限制)。只在非系统消息气泡上生效
     * (系统消息无气泡底,设计稿 §6.2);视口变化由容器/控制器同步后由组树重建生效。
     */
    public void setBubbleMaxWidthPx(int px) {
        this.maxBubbleWidthPx = Math.max(0, px);
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
     * @param frameMillis  帧时钟(旧 wall-clock 淡出路径驱动)
     * @param hudVisible   HUD 可见时钟信号(新显示预算路径驱动;null = 旧 wall-clock 语义)
     * @return 列表句柄(dispose 卸载整列表)
     */
    public SceneListHandle mount(SceneRuntime rt, SceneNode listParent,
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups, Style style,
            Map<SceneNode, ChatLineRecord> registry, ReadableSignal<Long> frameMillis,
            ReadableSignal<Long> hudVisible) {
        listParent.setGap(style.getGroupGapPx());
        return rt.forEach(listParent, groups, ChatMessageList::groupKey,
                group -> buildGroupNode(rt, group, style, registry, frameMillis, hudVisible));
    }

    /**
     * 旧 6 参重载(测试兼容):不注入可见时钟,HUD 淡出走旧 wall-clock 路径。
     *
     * @return 列表句柄(dispose 卸载整列表)
     */
    public SceneListHandle mount(SceneRuntime rt, SceneNode listParent,
            ReadableSignal<List<ChatCardComposer.ComposedGroup>> groups, Style style,
            Map<SceneNode, ChatLineRecord> registry, ReadableSignal<Long> frameMillis) {
        return mount(rt, listParent, groups, style, registry, frameMillis, null);
    }

    /** 构建单组子树:组头 row(名字+时间)+ 消息气泡(背景/四角圆角/行段);HUD 形态挂入场与淡出绑定。 */
    private SceneNode buildGroupNode(SceneRuntime rt, ChatCardComposer.ComposedGroup group, Style style,
            Map<SceneNode, ChatLineRecord> registry, ReadableSignal<Long> frameMillis,
            ReadableSignal<Long> hudVisible) {
        boolean system = group.getAlignment() == MessageGroupModel.Alignment.SYSTEM_CENTER;
        boolean selfRight = group.getAlignment() == MessageGroupModel.Alignment.SELF_RIGHT;
        // K3 三轮:系统消息独立字号/行高(font-system 12/16,设计稿 §2.2/§3.4),
        // 不再沿用 body 13/18;行段宽与链接命中区度量随之同源(12px 口径)
        int fontSize = system ? ChatMarkdownSettings.getSystemFontSizePx()
                : ChatMarkdownSettings.getChatFontSizePx();
        int lineHeight = system ? ChatMarkdownSettings.getSystemLineHeightPx()
                : ChatMarkdownSettings.getChatLineHeightPx();
        int paddingX = ChatMarkdownSettings.getBubblePaddingX();
        int paddingY = ChatMarkdownSettings.getBubblePaddingY();
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
        // P3-3 两级 gap(设计稿 §2.3):组头与气泡列 3px(sp-2)、组内相邻消息 2px(sp-1)。
        // 不再对 groupNode 统一 setGap(原统一 2 把组头→首气泡也算 2),改为显式 margin:
        // headerRow 下 margin 3 + 非首条消息上 margin 2。
        SceneNode groupNode = SceneNode.column()
                .setHitTestable(false)
                .setWidthSizing(SceneNode.WidthSizing.SHRINK)
                .setAlignSelf(align);
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
                    // K3 缺陷 2:row 默认 FILL 撑满父宽,把 SHRINK 的 groupNode 顶成全宽 →
                    // AlignSelf.END 交叉轴偏移恒 0,自己组永远左对齐;收缩到组头内容宽
                    .setWidthSizing(SceneNode.WidthSizing.SHRINK)
                    .setAlignSelf(align)
                    // P3-3:组头与首个气泡间距 sp-2 = 3(两级 gap)
                    .setMargin(0, 0, HEADER_TO_BUBBLE_GAP_PX, 0);
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
                        .setTextVerticalAlign(TextVerticalAlign.CENTER)
                        // K3 真机修复:组头文本节点缺 preferredHeight → 行高塌为 0(文本被气泡
                        // 背景覆盖的"幽影");段流节点不走文本度量,布局几何必须显式钉高
                        // (设计稿 §3.3:组头一行高 16)
                        .setPreferredHeight(HEADER_ROW_HEIGHT_PX);
                // K3 缺陷 2:段流节点无文本 → 布局宽 = fill 全宽,把 SHRINK 组头/组顶回全宽;
                // 注入度量时钉段流实宽(度量未注入的纯文本形态保持旧行为)
                float nameWidth = segmentsWidth(headerNameBase, segmentMeasurer,
                        ChatMarkdownSettings.getNameFontSizePx());
                if (nameWidth >= 0.0F) {
                    nameNode.setPreferredWidth(Math.max(1, (int) Math.ceil(nameWidth)));
                }
                headerRow.appendChild(nameNode);
            }
            if (!group.getHeaderTime().isEmpty()) {
                headerTimeBase = segmentParser.parse(group.getHeaderTime(),
                        ChatMarkdownSettings.getTimeTextArgb());
                timeNode = new SceneNode()
                        .setHitTestable(false)
                        .setFontSize(ChatMarkdownSettings.getTimestampFontSizePx())
                        .setSegments(headerTimeBase)
                        .setTextVerticalAlign(TextVerticalAlign.CENTER)
                        // 与名字节点同因(段流节点无文本度量):钉 16px 保组头行不塌(K3 缺陷 1)
                        .setPreferredHeight(HEADER_ROW_HEIGHT_PX);
                float timeWidth = segmentsWidth(headerTimeBase, segmentMeasurer,
                        ChatMarkdownSettings.getTimestampFontSizePx());
                if (timeWidth >= 0.0F) {
                    timeNode.setPreferredWidth(Math.max(1, (int) Math.ceil(timeWidth)));
                }
                headerRow.appendChild(timeNode);
            }
            groupNode.appendChild(headerRow);
        }
        int baseTextColor = system ? ChatMarkdownSettings.getSystemTextArgb() : 0xFFFFFFFF;
        int bubbleColor = selfRight ? ChatMarkdownSettings.getBubbleSelfArgb()
                : ChatMarkdownSettings.getBubbleOtherArgb();
        // 液态玻璃（用户裁决 2026-09-02）：气泡本身变半透明磨砂玻璃。
        // alpha 改在这里是唯一正确落点——bubbleColor 同时喂"创建时 setBackgroundColor"
        // 与"每帧 bake() 淡入/hover 重烘焙"两条路，改别处会被动画回路下一帧覆盖回实心。
        // 打底用 DARK 系材质：聊天正文是浅色，白 tint 会把浅色文字一起洗白，黑 tint
        // 压暗背景才保得住对比度（也是真机"DARK 更有苹果味"的成因）。
        UiBackdrop bubbleBackdrop = null;
        if (ChatMarkdownSettings.isGlassEnabled()) {
            bubbleColor = (bubbleColor & 0x00FFFFFF)
                    | (ChatMarkdownSettings.getGlassBubbleAlpha() << 24);
            bubbleBackdrop = UiBackdrop.liquidGlass(UiGlassMaterial.DARK_REGULAR,
                    ChatMarkdownSettings.getGlassBlurRadiusPx(), ChatMarkdownSettings.getGlassLensStrength());
        }
        int hoverBubbleColor = ChatCardComposer.hoveredBubbleColor(bubbleColor);
        // 方案A accent(§10 已拍板):仅自己气泡 = row[内容列 + 2px 强调条];他人/classic/系统 = 现状 column
        boolean accent = selfRight && ChatMarkdownSettings.getSelfBubbleStyle()
                == ChatMarkdownSettings.SelfBubbleStyle.ACCENT;
        int rLg = ChatMarkdownSettings.getBubbleCornerRadius();
        int rInner = ChatMarkdownSettings.getBubbleInnerCornerRadiusPx();
        List<SceneNode> messageNodes = new ArrayList<SceneNode>();
        // 与 messageNodes 同序:点击时交出服务端组件(原版语义优先于我们的链接跨度)
        List<IChatComponent> messageComponents = new ArrayList<IChatComponent>();
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
                        // 右对齐根因(2026-08-29 真机取证):组节点的 crossAxisAlign 默认
                        // STRETCH, SHRINK 子被豁免拉伸后 crossPos=0 左贴 → 组内气泡
                        // 右缘参差(accent 条 x=315/338/344)。显式 AlignSelf 让每条气泡
                        // 贴组右缘:自己组 END 右对齐/他人组 START/系统组 CENTER。
                        .setAlignSelf(align)
                        .setBackgroundColor(bubbleColor)
                        .setBackdrop(bubbleBackdrop)
                        .setMaxWidth(maxBubbleWidthPx);
                setGradedCorners(messageNode, cornersFor(messageCount, i, selfRight, rLg, rInner));
                // K3 缺陷 2:内容列收缩到文本宽,强调条才能贴气泡右内缘——否则列默认 FILL
                // 占满行内宽,强调条被挤出气泡右缘 2px(真机"accent 在外侧")
                contentNode = SceneNode.column()
                        .setWidthSizing(SceneNode.WidthSizing.SHRINK)
                        .setPadding(paddingY, paddingX, paddingY, paddingX);
                messageNode.appendChild(contentNode);
                SceneNode accentBar = new SceneNode()
                        .setHitTestable(false)
                        .setPreferredWidth(ACCENT_BAR_WIDTH_PX)
                        .setFillParentHeight(true)
                        .setMargin(4, 0, 4, 0)
                        .setBackgroundColor(ChatMarkdownSettings.getAccentBarSelfArgb())
                        .setCornerRadius(2);
                messageNode.appendChild(accentBar);
                accentBars.add(accentBar);
            } else {
                messageNode = SceneNode.column()
                        .setHitTestable(true)
                        .setWidthSizing(SceneNode.WidthSizing.SHRINK)
                        // 同右对齐修复(见 accent 分支注释):非 accent 形态气泡同样贴组右缘
                        .setAlignSelf(align);
                if (!system) {
                    messageNode.setBackgroundColor(bubbleColor)
                            .setBackdrop(bubbleBackdrop)
                            .setPadding(paddingY, paddingX, paddingY, paddingX)
                            .setMaxWidth(maxBubbleWidthPx);
                    setGradedCorners(messageNode, cornersFor(messageCount, i, selfRight, rLg, rInner));
                }
                contentNode = messageNode;
            }
            if (i > 0) {
                // P3-3:组内相邻消息间距 sp-1 = 2(组头→首气泡 3px 由 headerRow margin 承载)
                messageNode.setMargin(Math.max(0, ChatMarkdownSettings.getGroupInnerGapPx()), 0, 0, 0);
            }
            // 跨显示行 URL 续链(每条消息独立;长 URL 被字符硬断时才真正开放)
            UrlChain urlChain = new UrlChain();
            List<String> displayLines = message.getDisplayLines();
            List<ChatLineLayouter.LineFragment> displayFragments = message.getDisplayFragments();
            for (int lineIndex = 0; lineIndex < displayLines.size(); lineIndex++) {
                String line = displayLines.get(lineIndex);
                // 本行是否为「词内字符硬断」的续行(片段数与行数不等时按保守 false 处理)
                boolean continuesWord = lineIndex < displayFragments.size()
                        && displayFragments.get(lineIndex).continuesWord();
                // 引用行(T6b 设计稿 §3.5):行文本以 "> " 或 ">" 开头 → 剥前缀 + 文字降
                // text-secondary + 行首 2px 竖条(0x40FFFFFF);剥前缀后的文本照常参与
                // linkify(系统消息按 F5 保留原色链接化)/code 切分;引用只作用于气泡行。
                boolean quoteLine = line.startsWith("> ");
                String renderLine = quoteLine ? line.substring(2) : line;
                if (!quoteLine && line.startsWith(">")) {
                    quoteLine = true;
                    renderLine = line.substring(1);
                }
                int lineBaseColor = quoteLine ? ChatMarkdownSettings.getTextSecondaryArgb()
                        : baseTextColor;
                // F5 用户拍板:系统消息中的裸 URL 也链接化(命中区 + 点击回投原版事件链),
                // 但保留 URL 原 § 格式色(LinkifyMode.PRESERVE)、不强制 0xFF7AB8F5;
                // 气泡消息维持统一链接色(COLORED)。系统消息仍不 code 切分(§3.5 排版规则
                // 仅作用于气泡内;链接度量为 null 时 PRESERVE 内部不生效 = 旧行为)。
                LinkifyMode linkifyMode = system ? LinkifyMode.PRESERVE : LinkifyMode.COLORED;
                // C 拍板(§10.1):行级 markdown 轻量规则——列表「• 」前缀行与块级公式独占行。
                // 与 code 切分同边界(仅气泡行,系统消息不套用;引用行保持既有语义)。
                ChatMarkdownLineRule.Match markdown = (!system && !quoteLine)
                        ? ChatMarkdownLineRule.classify(renderLine) : ChatMarkdownLineRule.NONE;
                if (markdown.getKind() == ChatMarkdownLineRule.Kind.BLOCK_MATH) {
                    // 块级公式独占行:TeX 源走既有 LaTeX 渲染链,上下各 4px 间距,左对齐(不居中)
                    TextStyle mathStyle = new TextStyle();
                    mathStyle.setColor(lineBaseColor);
                    List<TextSegment> mathSegments = Collections.singletonList(
                            TextSegment.forLatex(markdown.getLatexSource(), mathStyle));
                    SceneNode mathNode = new SceneNode()
                            .setHitTestable(false)
                            .setFontSize(fontSize)
                            .setSegments(mathSegments)
                            .setTextVerticalAlign(TextVerticalAlign.CENTER)
                            .setPreferredHeight(Math.max(1, lineHeight))
                            .setMargin(BLOCK_MATH_GAP_PX, 0, BLOCK_MATH_GAP_PX, 0);
                    if (style.isTtlFade()) {
                        mathNode.setMaxLines(ChatCardComposer.HUD_MAX_LINES).setEllipsis(true);
                    }
                    if (segmentMeasurer != null) {
                        int mathWidth = Math.max(1, (int) Math.ceil(
                                segmentsWidth(mathSegments, segmentMeasurer, fontSize)));
                        if (maxBubbleWidthPx > 0) {
                            mathWidth = Math.min(mathWidth, Math.max(1, maxBubbleWidthPx
                                    - 2 * paddingX - (accent ? ACCENT_BAR_WIDTH_PX : 0)));
                        }
                        mathNode.setPreferredWidth(mathWidth);
                    }
                    contentNode.appendChild(mathNode);
                    lineNodes.add(mathNode);
                    lineBases.add(mathSegments);
                    hoverBases.add(null);
                    messageLineSpans.add(Collections.<LinkSpan>emptyList());
                    urlChain.close(); // 块级公式独占行不可能是 URL 续行,链到此终止
                    globalLineIndex++;
                    continue;
                }
                List<TextSegment> segments;
                List<TextSegment> hover = null;
                List<LinkSpan> spans = Collections.<LinkSpan>emptyList();
                // 本行链接化作用域文本 = 段流可见字符的同口径原文(列表行剥「• 」前缀);
                // 续链要按它数「行首属于 URL 体的字符」,必须与实际链接化的文本严格同源
                String scopeText;
                TextSegment bulletSegment = null;
                if (markdown.getKind() == ChatMarkdownLineRule.Kind.UNORDERED_LIST) {
                    // 「• 」前缀段(正文色)+ 内容段;层级缩进 = 前导空格数/2,每级 2 个空格
                    // (2 空格=1 级的简单映射,缩进随文本宽度度量,不依赖布局语义)
                    StringBuilder bulletBuilder = new StringBuilder();
                    for (int l = 0; l < markdown.getLevel(); l++) {
                        bulletBuilder.append("  ");
                    }
                    bulletBuilder.append("• ");
                    TextStyle bulletStyle = new TextStyle();
                    bulletStyle.setColor(lineBaseColor);
                    bulletSegment = new TextSegment(bulletBuilder.toString(), bulletStyle);
                    String listContent = markdown.getContent() == null ? "" : markdown.getContent();
                    List<TextSegment> contentSegments = parseCached(listContent, lineBaseColor, linkifyMode);
                    List<TextSegment> combined = new ArrayList<TextSegment>(contentSegments.size() + 1);
                    combined.add(bulletSegment);
                    combined.addAll(contentSegments);
                    segments = combined;
                    scopeText = listContent;
                } else {
                    segments = parseCached(renderLine, lineBaseColor, linkifyMode);
                    scopeText = renderLine;
                }
                // —— 跨显示行 URL 续链:上一行末尾是未闭合 URL 且本行是词内硬断续行 ——
                String chainRun = null;
                // bulletSegment != null 时本行段流首段是「• 」前缀,不是正文——按可见字符数
                // 从头吞并会把项目符号划进 URL,故列表行不参与续链(保守不接,不污染)
                if (continuesWord && urlChain.open() && bulletSegment == null) {
                    String run = ChatUrlLinkifier.leadingUrlRun(scopeText);
                    if (run.isEmpty()) {
                        urlChain.close(); // 行首即终止:URL 其实在上一行就完整了
                    } else {
                        chainRun = run;
                        segments = ChatUrlLinkifier.linkifyLeadingRun(segments,
                                linkColorArg(linkifyMode), run.length(), urlChain.url() + run);
                    }
                }
                if (segmentMeasurer != null) {
                    spans = linkSpansOf(segments, segmentMeasurer, fontSize);
                    if (chainRun != null && !spans.isEmpty()) {
                        urlChain.extend(urlChain.url() + chainRun, spans.get(0));
                    }
                    if (!spans.isEmpty()) {
                        // 续链行的段流是链上叠加产物,不在 hoverCached 的 key 空间里,
                        // 必须由最终段流现推(hoverLinkify 只改色与下划线,零副作用)
                        hover = chainRun == null
                                ? hoverCached(scopeText, lineBaseColor, linkifyMode)
                                : ChatUrlLinkifier.hoverLinkify(segments,
                                        ChatMarkdownSettings.getLinkHoverArgb());
                        if (bulletSegment != null) {
                            List<TextSegment> combinedHover =
                                    new ArrayList<TextSegment>(hover.size() + 1);
                            combinedHover.add(bulletSegment);
                            combinedHover.addAll(hover);
                            hover = combinedHover;
                        }
                    }
                }
                // 链尾判定:本行末尾仍是一段 URL → 链保持开放,等下一行的 continuesWord
                if (chainRun == null
                        || chainRun.length() < ChatUrlLinkifier.plainLength(scopeText)) {
                    TextSegment lastSegment = segments.isEmpty() ? null
                            : segments.get(segments.size() - 1);
                    String tailLink = lastSegment == null ? null : lastSegment.getStyle().getLink();
                    urlChain.close();
                    if (tailLink != null) {
                        urlChain.start(tailLink, spans.isEmpty() ? null
                                : spans.get(spans.size() - 1));
                    }
                }
                messageLineSpans.add(spans);
                // 垂直口径：段流节点钉的行框高(lineHeight=字号+行距)大于 em-box(=字号)，
                // 必须 CENTER 才能把行距按 half-leading 上下均分；TOP 会把整段行距堆到文字
                // 下方，单行气泡看起来贴底。四处段流节点(组头名/时间/块公式/正文行)同因。
                SceneNode lineNode = new SceneNode()
                        .setHitTestable(false)
                        .setFontSize(fontSize)
                        .setSegments(segments)
                        .setTextVerticalAlign(TextVerticalAlign.CENTER)
                        .setPreferredHeight(Math.max(1, lineHeight));
                // K3 缺陷 2 根因:段流节点不参与文本度量(SceneNode.setSegments 契约),布局宽
                // = fill 全宽 → messageNode SHRINK 被全宽行顶满 → clamp 到 maxWidth 恒占
                // 289px 且组节点被 headerRow 顶成全宽后 AlignSelf.END 偏移恒 0(左对齐)。
                // 注入度量时钉行段实宽(上限 = 气泡内可用宽),气泡按内容收缩;
                // 度量未注入的纯文本形态保持旧行为。
                if (segmentMeasurer != null) {
                    int lineWidth = Math.max(1,
                            (int) Math.ceil(segmentsWidth(segments, segmentMeasurer, fontSize)));
                    // K3 三轮:钳宽仅作用于气泡行(气泡 ≤ 0.85 组内容宽);系统消息无气泡,
                    // 行宽 = 实宽(钳到 269 会把居中的系统行节点收缩到 269,行文本 340 溢出
                    // 节点且居中几何错位——K3 摘要第 4 条)
                    if (maxBubbleWidthPx > 0 && !system) {
                        int reserve = (accent ? ACCENT_BAR_WIDTH_PX : 0)
                                + (quoteLine ? QUOTE_BAR_WIDTH_PX + QUOTE_GAP_PX : 0);
                        lineWidth = Math.min(lineWidth,
                                Math.max(1, maxBubbleWidthPx - 2 * paddingX - reserve));
                    }
                    lineNode.setPreferredWidth(lineWidth);
                }
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
                            .setHitTestable(false)
                            // K3 缺陷 2:引用行同样收缩(竖条 2 + gap 6 + 文本),否则引用行
                            // FILL 全宽会把 messageNode 顶回全宽、气泡无法按内容收缩
                            .setWidthSizing(SceneNode.WidthSizing.SHRINK);
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
            // 消息末行仍开放 → 链到此为止,回填完整 URL(下一行不存在,不可能再接)
            urlChain.close();
            groupNode.appendChild(messageNode);
            messageNodes.add(messageNode);
            messageComponents.add(message.getRecord().getComponent());
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
        // P2-4:hover 颜色插值每帧推进(气泡 100ms / 链接 80ms,easeOutQuad;目标态由
        // hovered 绑定与 LinkHoverDriver 写入,本绑定只推进进度并按需重烘)。
        // 与 HUD 淡出烘焙共享 currentAlpha,两路重烘幂等。
        rt.bind(frameMillis, now -> {
            if (bake.advanceHover(now.longValue())) {
                bake.bake(currentAlpha[0]);
            }
        });
        if (style.isTtlFade()) {
            // HUD 形态:组出生 enter 动画(设计稿 §4.1 行1)——translateY +8→0 + opacity 0→1,
            // 180ms easeOutCubic,基准 = 组内最新消息到达时刻(wall-clock;组树重建后老组按进度
            // 立即稳态,不重播;完成态 opacity=1 / transform 恒等,渲染引擎走快速路径)。
            // 新机制门控:仅组首次以 HUD 形态合成(isEnterOnMount)时挂入场绑定——组增长重建
            // (同组连发消息,isEnterOnMount=false)后不重播,组保持稳态渲染,消除整组闪烁;
            // 重挂载(形态切换树重建)同样跳过,入场动画与消息是否首次进 HUD 一一对应。
            if (group.isEnterOnMount()) {
                final long bornMillis = group.getLatestMillis();
                rt.bind(Computed.create(() -> Float.valueOf(
                                enterOpacity(bornMillis, frameMillis.get().longValue()))),
                        opacity -> groupNode.setOpacity(opacity.floatValue()));
                rt.bind(Computed.create(() -> enterTransform(bornMillis, frameMillis.get().longValue())),
                        transform -> groupNode.setTransform(transform));
            }
            // 淡出烘焙 → currentAlpha → bake(正常/hover 两态同源):
            // 新显示时长机制(hudVisible 注入):alpha = 每条消息可见预算(hudAlpha 纯函数,
            // 起点/预算取自合成组,仅 HUD 实际渲染时按可见时钟消耗,聊天框打开期间冻结);
            // 起点 = -1(未进入 HUD 渲染)时 hudAlpha 恒 255 = 天然稳态,预算不消耗。
            // 未注入(hudVisible == null):旧 wall-clock 路径(测试兼容)。
            // TB1 常驻模式:TTL 淡出关闭,alpha 恒满 255(设置内读,运行时切换即时生效)
            rt.bind(Computed.create(() -> Integer.valueOf(
                            ChatMarkdownSettings.isHudPersistMessages() ? 255
                                    : hudVisible == null
                                            ? ChatCardComposer.fadeAlpha(
                                                    group.getLatestMillis(),
                                                    frameMillis.get().longValue(),
                                                    ChatMarkdownSettings.getHudTtlMillis(),
                                                    ChatMarkdownSettings.getHudFadeMillis(), 255)
                                            : ChatCardComposer.hudAlpha(
                                                    group.getHudVisibleStartMillis(),
                                                    group.getBudgetMillis(),
                                                    ChatMarkdownSettings.getHudFadeMillis(),
                                                    hudVisible.get().longValue()))),
                    alpha -> {
                        int a = alpha.intValue();
                        if (a != currentAlpha[0]) {
                            currentAlpha[0] = a;
                            bake.bake(a);
                        }
                    });
        }
        // 气泡 hover 底色(仅非系统消息;3% 白叠加,PAINT 级与淡出共同烘焙)+
        // 链接 hover 离开清理(K3 三轮:系统消息同样装配——此前 hovered 绑定仅限非系统消息,
        // 指针离开系统消息链接行后 lineHovered 残留 → URL 行 stuck hover,
        // 真机 URL L1 恒 hover 色 + 下划线实锤)
        for (int i = 0; i < messageCount; i++) {
            final SceneNode messageNode = messageNodes.get(i);
            final int idx = i;
            final boolean[] ownBubble = new boolean[] { bubbleHovered[idx] };
            rt.bind(rt.interactionState(messageNode).hovered(), hovered -> {
                boolean now = Boolean.TRUE.equals(hovered);
                boolean changed = now != ownBubble[0];
                ownBubble[0] = now;
                if (!system) {
                    bubbleHovered[idx] = now;
                }
                if (!now) {
                    LinkHoverDriver driver = linkDrivers.get(messageNode);
                    if (driver != null) {
                        driver.onPointerLeave();
                    }
                }
                if (!system && changed) {
                    bake.bake(currentAlpha[0]);
                }
            });
        }
        // 链接 hover + tooltip(含链接行才装配;F5 用户拍板:系统消息裸 URL 同样装配命中区
        // 与 hover/tooltip/cursor,点击经 registry 回投原版事件链——仅系统消息无气泡 hover)
        if (segmentMeasurer != null) {
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
                        messageComponents.get(i), messageLineNodes, messageSpans, lineStart,
                        lineHeight, lineHovered, bake, currentAlpha);
                linkDrivers.put(messageNode, driver);
                final LinkHoverDriver boundDriver = driver;
                rt.on(messageNode, SceneEventType.POINTER_MOVE, (ev, ctx) -> {
                    boundDriver.onPointerMove(ctx.getLocalPointerX(), ctx.getLocalPointerY());
                });
                // 点击与悬停共用框架算好的节点局部坐标(SceneLabel 同款)。**不要**在这里
                // 自己拿屏幕坐标去减绝对盒:GuiScreen 回调给的是 guiScale 缩放后的坐标,
                // 而 McScreenBridge 喂进 scene 的是输入 reader 读的物理坐标,两套空间
                // 相减永远命不中(本仓「1 权威 + N 处重实现」教训的第三次复发)。
                rt.on(messageNode, SceneEventType.CLICK, (ev, ctx) -> {
                    boundDriver.onLinkClick(ev.getButton(),
                            ctx.getLocalPointerX(), ctx.getLocalPointerY());
                });
                // 400ms 悬停出 URL tooltip(SceneTooltip;无输入宿主自然不显示)
                // breakLongWords=true:URL 是无折行机会的超长单词,旧行为会对它再加一次
                // 省略号 —— tooltip 的全部意义就是揭示被气泡截断的地址,再截一次等于白做
                SceneTooltip.attach(rt, new SceneTooltip.Props(messageNode,
                        Computed.create(() -> hoverLink.get()),
                        Computed.create(() -> Boolean.valueOf(!hoverLink.get().isEmpty())),
                        LINK_TOOLTIP_DELAY_MILLIS, LINK_TOOLTIP_MAX_WIDTH_PX,
                        LINK_TOOLTIP_MAX_LINES, true));
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
     * 链接色实参:COLORED = 强制设计稿链接色;PRESERVE = {@code null}(保留各段原 § 色,
     * F5 用户拍板)。与 {@code ChatUrlLinkifier.linkifyInternal} 的 nullable-Integer 约定
     * 同一开关语义,续链叠加({@code linkifyLeadingRun})与整行链接化因此共用一套配色规则。
     */
    private static Integer linkColorArg(LinkifyMode mode) {
        return mode == LinkifyMode.PRESERVE ? null
                : Integer.valueOf(ChatMarkdownSettings.getLinkArgb());
    }

    /**
     * 段解析缓存(text@baseColor@mode):NONE = 原样 § 解析(旧行为);COLORED = code 切分 +
     * 统一 link 色链接化(气泡);PRESERVE = 保留 § 原色的链接化(系统消息,F5 用户拍板),
     * 不 code 切分(§3.5 排版规则仅作用于气泡内)。链接度量为 null 时 COLORED/PRESERVE
     * 的链接化步骤内部跳过(旧行为)。
     *
     * <p>T6b 解析顺序：先 {@link ChatCodeSpanSplitter#split code 切分} 再
     * {@link ChatUrlLinkifier#linkify linkify}——code 段是文本语义边界（不嵌套解析），
     * linkify 已对 code 段跳过；若反向（先 linkify），URL 扫描会把反引号吞进 URL 文本
     * （反引号不在 URL 分隔符/尾随标点集），code 配对被破坏（headless 实测
     * "``http://a.co``" 链接化后闭引号进入 link 段）。</p>
     */
    private List<TextSegment> parseCached(String text, int baseColor, LinkifyMode mode) {
        String key = text + '@' + baseColor + (mode == LinkifyMode.COLORED ? '@'
                : mode == LinkifyMode.PRESERVE ? '~' : '!');
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
        if (mode == LinkifyMode.COLORED) {
            segments = ChatCodeSpanSplitter.split(segments, ChatMarkdownSettings.getCodeBackgroundArgb());
        }
        if (segmentMeasurer != null && mode != LinkifyMode.NONE) {
            segments = mode == LinkifyMode.PRESERVE
                    ? ChatUrlLinkifier.linkifyPreserveColor(segments)
                    : ChatUrlLinkifier.linkify(segments, ChatMarkdownSettings.getLinkArgb());
        }
        segmentCache.put(key, segments);
        return segments;
    }

    /** hover 段流缓存(text@baseColor@mode → 链接段换 hover 色 + 下划线;PRESERVE 模式下
     *  常态保留 § 原色,hover 提亮 + 下划线是命中反馈,与气泡一致)。 */
    private List<TextSegment> hoverCached(String text, int baseColor, LinkifyMode mode) {
        String key = text + '@' + baseColor + (mode == LinkifyMode.PRESERVE ? '~' : '@');
        List<TextSegment> hit = hoverSegmentCache.get(key);
        if (hit != null) {
            return hit;
        }
        List<TextSegment> hover = ChatUrlLinkifier.hoverLinkify(
                parseCached(text, baseColor, mode), ChatMarkdownSettings.getLinkHoverArgb());
        hoverSegmentCache.put(key, hover);
        return hover;
    }

    /**
     * 段流总宽(注入度量逐段求和,与渲染推进同源);度量为 null → 返回 -1(不钉宽,
     * 保持引擎"无文本叶 fill 全宽"的旧行为,K3 缺陷 2 修复的纯文本降级路径)。
     */
    private static float segmentsWidth(List<TextSegment> segments, SegmentMeasurer measurer,
            int fontSizePx) {
        if (measurer == null || segments == null) {
            return -1.0F;
        }
        float total = 0.0F;
        for (TextSegment segment : segments) {
            total += Math.max(0.0F, measurer.widthOf(segment, fontSizePx));
        }
        return total;
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

    /** scene CLICK handler 回调入口:立即投递给宿主(无暂存、无延后消费)。 */
    void deliverLinkClick(ChatLinkClick click) {
        Consumer<ChatLinkClick> handler = linkClickHandler;
        if (handler != null) {
            handler.accept(click);
        }
    }

    /**
     * 注册链接点击出口(宿主)。
     *
     * <p>玩家手打的裸 URL 原版 {@code IChatComponent} 上不带 clickEvent,服务端也没下发可点
     * 区域 —— 本出口让我们自己的链接化跨度补上这个能力。事件驱动:一次 CLICK 恰好一次投递,
     * 不存在"上一次点击的残留"。</p>
     */
    public void setLinkClickHandler(Consumer<ChatLinkClick> handler) {
        linkClickHandler = handler;
    }

    /** 测试探针:消息节点 → 链接 hover 驱动器。 */
    LinkHoverDriver __linkHoverDriverOf(SceneNode messageNode) {
        return linkDrivers.get(messageNode);
    }
}
