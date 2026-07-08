package club.heiqi.uilib.ui.paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentEffectChain;
import club.heiqi.uilib.ui.layout.DocumentEffectType;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEdges;
import club.heiqi.uilib.ui.layout.DocumentLayoutInlineFragment;
import club.heiqi.uilib.ui.layout.DocumentLayoutTextRun;
import club.heiqi.uilib.ui.layout.DocumentScrollState;
import club.heiqi.uilib.ui.layout.DocumentScrollState.ScrollbarMetrics;
import club.heiqi.uilib.ui.layout.DocumentStackingPhase;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.BoxContext;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.ClipContext;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.RootEntry;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.StackingContextResolver;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.TraversalEntry;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.VisualScene;
import club.heiqi.uilib.ui.render.BackdropBlurConfig;
import club.heiqi.uilib.ui.render.BackdropBlurPolicy;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;
import club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.values.UiBoxShadow;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.base.props.UiFontStyle;
import club.heiqi.uilib.ui.base.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiListStyleType;
import club.heiqi.uilib.ui.style.values.UiOutline;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiScrollbarColor;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.cascade.UiStyleResolver;
import club.heiqi.uilib.ui.style.props.UiTextDecoration;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.base.values.UiSurfaceStyle;
import club.heiqi.uilib.ui.base.values.UiTransform;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureStyle;

/**
 * HTML-like 绘制命令生成器。
 */
public final class DocumentPaintEngine {

    private DocumentPaintEngine() {}

    /**
     * 从布局盒树生成绘制命令。
     *
     * <p>当前初版按背后滤镜、元素背景、元素边框、结构裁剪、滚动内容、子树与滚动条的顺序输出命令。
     * 同级子元素会按 CSS-like stacking phase 做稳定排序，更完整 stacking context 会在后续阶段继续扩展。</p>
     *
     * @param rootBox 根布局盒
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox) {
        return buildPaintCommands(rootBox, null);
    }

    /**
     * 从布局盒树和滚动状态生成绘制命令。
     *
     * @param rootBox 根布局盒
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox,
            DocumentScrollState scrollState) {
        return buildPaintCommands(rootBox, scrollState, System.nanoTime());
    }

    /**
     * 从布局盒树和滚动状态生成绘制命令。
     *
     * <p>根元素滚动条保持可见；嵌套滚动条只在最近滚动后的短暂窗口内绘制，避免空闲时遮挡内容。</p>
     *
     * @param rootBox 根布局盒
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @param currentTimeNanos 当前时间戳
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox,
            DocumentScrollState scrollState, long currentTimeNanos) {
        return buildPaintCommands(rootBox, scrollState, currentTimeNanos, null);
    }

    /**
     * 从布局盒树、滚动状态和动画时间线生成绘制命令。
     *
     * @param rootBox 根布局盒
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @param currentTimeNanos 当前时间戳
     * @param animationTimeline 动画时间线；为 null 时不应用动画覆盖
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox,
            DocumentScrollState scrollState, long currentTimeNanos, DocumentAnimationTimeline animationTimeline) {
        return buildPaintCommands(rootBox, Collections.<DocumentLayoutBox>emptyList(), scrollState, currentTimeNanos,
                animationTimeline);
    }

    /**
     * 从普通布局盒树、top-layer 根盒、滚动状态和动画时间线生成绘制命令。
     *
     * @param rootBox 普通文档根盒
     * @param topLayerBoxes top-layer 根盒；后面的盒位于更上层
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @param currentTimeNanos 当前时间戳
     * @param animationTimeline 动画时间线；为 null 时不应用动画覆盖
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox,
            List<DocumentLayoutBox> topLayerBoxes, DocumentScrollState scrollState, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline) {
        return buildPaintCommands(rootBox, topLayerBoxes, scrollState, currentTimeNanos, animationTimeline, null);
    }

    /**
     * 从普通布局盒树、top-layer 根盒、滚动状态、动画时间线和文本测量服务生成绘制命令。
     *
     * <p>传入文本测量服务后，绘制阶段可对被 overflow clip 横向裁掉的长单行文本生成可见片段，
     * 避免每帧把完整长字符串提交给字体后端。</p>
     *
     * @param rootBox 普通文档根盒
     * @param topLayerBoxes top-layer 根盒；后面的盒位于更上层
     * @param scrollState 滚动状态；为 null 时按无滚动处理
     * @param currentTimeNanos 当前时间戳
     * @param animationTimeline 动画时间线；为 null 时不应用动画覆盖
     * @param textMeasureService 文本测量服务；为 null 时只做不依赖测量的可见性裁剪
     * @return 绘制命令列表
     */
    public static List<DocumentPaintCommand> buildPaintCommands(DocumentLayoutBox rootBox,
            List<DocumentLayoutBox> topLayerBoxes, DocumentScrollState scrollState, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline, TextMeasureService textMeasureService) {
        Objects.requireNonNull(rootBox, "rootBox");
        List<DocumentPaintCommand> commands = new ArrayList<DocumentPaintCommand>();
        StackingContextResolver resolver = createPaintStackingContextResolver(currentTimeNanos, animationTimeline);
        VisualScene scene = DocumentVisualTraversal.resolveVisualScene(rootBox, topLayerBoxes, scrollState,
                currentTimeNanos, animationTimeline);
        for (RootEntry rootEntry : scene.getRootEntries()) {
            appendBoxCommands(rootEntry.getRootBox(), rootEntry.getRootContext(), commands, scrollState,
                    animationTimeline, currentTimeNanos, 1.0F, true, Collections.<ClipContext>emptyList(), resolver,
                    false, textMeasureService, false);
        }
        attachCustomRenderBounds(commands, scene, scrollState);
        return commands;
    }

    /**
     * 生成绘制计划：命令列表 + 滚动依赖快照。
     *
     * <p>方案2 下，{@link HtmlLikeDocumentWidget} 缓存命中改为「快照为空 → 滚动不重建」或「快照中各容器
     * 当前偏移 == 构建期偏移」，而非比对全局 scrollVersion。快照只登记被判为回退（ineligible，即不走回放期
     * 偏移栈）的可滚动容器及其构建期滚动偏移：这类容器内容坐标已烘焙构建期 scroll、又不发 {@code SCROLL_OFFSET}
     * 作用域，滚动后必须重建才正确。真正可免重建的页面（所有可滚动容器都 eligible）快照为空 → 滚动永不重建。</p>
     *
     * @param rootBox 普通文档根盒
     * @param topLayerBoxes top-layer 根盒；后面的盒位于更上层
     * @param scrollState 滚动状态；为 null 时按无滚动处理（快照恒为空）
     * @param currentTimeNanos 当前时间戳
     * @param animationTimeline 动画时间线；为 null 时不应用动画覆盖
     * @param textMeasureService 文本测量服务；为 null 时只做不依赖测量的可见性裁剪
     * @return 绘制计划
     */
    public static DocumentPaintPlan buildPaintPlan(DocumentLayoutBox rootBox,
            List<DocumentLayoutBox> topLayerBoxes, DocumentScrollState scrollState, long currentTimeNanos,
            DocumentAnimationTimeline animationTimeline, TextMeasureService textMeasureService) {
        List<DocumentPaintCommand> commands = buildPaintCommands(rootBox, topLayerBoxes, scrollState,
                currentTimeNanos, animationTimeline, textMeasureService);
        Map<ElementNode, int[]> scrollDependencies = collectScrollDependencySnapshot(rootBox, topLayerBoxes,
                scrollState);
        return new DocumentPaintPlan(commands, scrollDependencies);
    }

    /**
     * 尝试对已缓存命令执行 composite-only 就地回放：仅 transform/opacity 变化（compositeVersion 变、
     * paintVersion 未变）时，跳过整批命令重建，就地更新 {@code TRANSFORM_START}/{@code TRANSFORM_END} 的
     * 变换值与 {@code PAINT_CONTEXT_START} 的局部 opacity。
     *
     * <p><b>结构守卫</b>：transform/opacity 不仅影响数值，还可能改变命令<em>结构</em>——transform 在
     * identity↔非identity 之间翻转会增删 {@code TRANSFORM} 命令对并翻转 transform stacking context；opacity
     * 跨越 {@code 0.999} 阈值（见 {@link DocumentEffectChain#createsPaintContext}）会增删 {@code PAINT_CONTEXT}
     * 命令对并翻转 opacity stacking context，二者都会改变 stacking 排序与命令序列。此外 opacity ≥ 0.999 的盒
     * 不建立 paint context，其 opacity 会烘焙进子命令颜色，无命令可就地更新。因此本方法先按盒树逐元素比对
     * 「transform 是否 identity」「是否建立 paint context（opacity &lt; 0.999）」与旧固化命令的对应状态，
     * 任一结构性翻转或无法安全就地更新即返回 false，由调用方回退全量重建（保证 I8 缓存正确性）。</p>
     *
     * <p>仅当结构完全一致、且所有受影响盒都能就地更新时返回 true，原命令列表已被就地刷新、可直接复用。</p>
     *
     * @param rootBox 已 refreshComputedStyles 的根布局盒（携带最新 transform/opacity）
     * @param commands 已缓存的绘制命令列表（成功时被就地修改）
     * @return 是否成功执行 composite-only 就地回放；false 表示存在结构性变化，需全量重建
     */
    public static boolean tryApplyCompositeReplay(DocumentLayoutBox rootBox, List<DocumentPaintCommand> commands) {
        Objects.requireNonNull(rootBox, "rootBox");
        if (commands == null || commands.isEmpty()) {
            return false;
        }
        Map<ElementNode, DocumentLayoutBox> boxByElement = new java.util.IdentityHashMap<ElementNode, DocumentLayoutBox>();
        indexBoxesByElement(rootBox, boxByElement);
        for (DocumentPaintCommand command : commands) {
            DocumentPaintCommandType type = command.getType();
            if (type == DocumentPaintCommandType.TRANSFORM_START
                    || type == DocumentPaintCommandType.TRANSFORM_END) {
                DocumentLayoutBox box = boxByElement.get(command.getElement());
                if (box == null) {
                    return false;
                }
                UiTransform newTransform = box.getComputedStyle().getTransform();
                boolean newIdentity = newTransform == null || newTransform.isIdentity();
                UiTransform oldTransform = command.getTransform();
                boolean oldIdentity = oldTransform == null || oldTransform.isIdentity();
                // identity 状态翻转 => transform 命令对增删 + stacking 翻转 => 结构变化，回退重建。
                if (newIdentity != oldIdentity) {
                    return false;
                }
            } else if (type == DocumentPaintCommandType.PAINT_CONTEXT_START) {
                DocumentLayoutBox box = boxByElement.get(command.getElement());
                if (box == null) {
                    return false;
                }
                // PAINT_CONTEXT_START 仅在 opacity < 0.999（建立 paint context）时 emit。新 opacity 若回升到
                // >= 0.999，paint context 应消失 => 命令对增删 + stacking 翻转 => 结构变化，回退重建。
                float newOpacity = box.getComputedStyle().getOpacity();
                if (newOpacity >= PAINT_CONTEXT_OPACITY_THRESHOLD) {
                    return false;
                }
            }
        }
        // 结构守卫已确认所有 TRANSFORM/PAINT_CONTEXT 命令结构未变，可安全就地更新数值。
        for (DocumentPaintCommand command : commands) {
            DocumentPaintCommandType type = command.getType();
            if (type == DocumentPaintCommandType.TRANSFORM_START
                    || type == DocumentPaintCommandType.TRANSFORM_END) {
                DocumentLayoutBox box = boxByElement.get(command.getElement());
                UiTransform newTransform = box.getComputedStyle().getTransform();
                command.updateTransform(newTransform == null ? UiTransform.identity() : newTransform);
            } else if (type == DocumentPaintCommandType.PAINT_CONTEXT_START) {
                DocumentLayoutBox box = boxByElement.get(command.getElement());
                command.updatePaintContextOpacity(box.getComputedStyle().getOpacity());
            }
        }
        return true;
    }

    /**
     * opacity 建立 paint context 的阈值，与 {@link DocumentEffectChain#createsPaintContext} 保持一致。
     */
    private static final float PAINT_CONTEXT_OPACITY_THRESHOLD = 0.999F;

    private static void indexBoxesByElement(DocumentLayoutBox box, Map<ElementNode, DocumentLayoutBox> boxByElement) {
        if (box == null) {
            return;
        }
        ElementNode element = box.getElement();
        if (element != null && !boxByElement.containsKey(element)) {
            // 同一元素只登记第一个盒（与命令固化的 element 引用一致即可，匿名/伪元素不参与 transform/opacity）。
            boxByElement.put(element, box);
        }
        for (DocumentLayoutBox child : box.getChildren()) {
            indexBoxesByElement(child, boxByElement);
        }
    }

    /**
     * 收集滚动依赖快照：登记所有被判为回退（ineligible）的可滚动容器及其构建期滚动偏移。
     *
     * <p>遍历普通根盒与 top-layer 根盒子树，对每个 {@code maxScroll > 0} 的可滚动容器判定 eligibility：
     * eligible 容器走回放期偏移栈、滚动免重建，不登记；ineligible 容器（子树含 positioned 后代）内容坐标已
     * 烘焙构建期 scroll，滚动后须重建，登记 {@code 元素 -> [scrollLeft, scrollTop]} 构建期快照供缓存比对。</p>
     *
     * @param rootBox 普通文档根盒
     * @param topLayerBoxes top-layer 根盒
     * @param scrollState 构建期滚动态；为 null 时返回空快照
     * @return 元素到构建期滚动偏移的快照；无回退容器时为空 Map
     */
    private static Map<ElementNode, int[]> collectScrollDependencySnapshot(DocumentLayoutBox rootBox,
            List<DocumentLayoutBox> topLayerBoxes, DocumentScrollState scrollState) {
        if (scrollState == null) {
            return Collections.emptyMap();
        }
        Map<ElementNode, int[]> snapshot = new java.util.IdentityHashMap<ElementNode, int[]>();
        collectScrollDependencies(rootBox, scrollState, snapshot, true);
        if (topLayerBoxes != null) {
            for (DocumentLayoutBox topLayerBox : topLayerBoxes) {
                if (topLayerBox != null) {
                    collectScrollDependencies(topLayerBox, scrollState, snapshot, true);
                }
            }
        }
        return snapshot.isEmpty() ? Collections.<ElementNode, int[]>emptyMap() : snapshot;
    }

    /**
     * 深度遍历布局盒子树，把回退可滚动容器的构建期滚动偏移登记进快照。
     *
     * @param box 当前布局盒
     * @param scrollState 构建期滚动态
     * @param snapshot 累积快照
     * @param paintRoot 当前盒是否为绘制根（普通根或 top-layer 根，强制收集自身 positioned 后代）
     */
    private static void collectScrollDependencies(DocumentLayoutBox box, DocumentScrollState scrollState,
            Map<ElementNode, int[]> snapshot, boolean paintRoot) {
        ElementNode element = box.getElement();
        // 与 appendBoxCommands 的 resolvedPaintStackingContext 判据保持一致：绘制根或建立 stacking context 的盒
        // 在自身作用域内收集 positioned 后代。静置帧（快照比对生效的唯一场景）无动画运行值，静态 createsStackingContext
        // 与运行态判据等价，故此处用静态判据安全。
        boolean collectsPositionedDescendants = paintRoot
                || DocumentEffectChain.resolve(box).createsStackingContext();
        if (element != null
                && (scrollState.getMaxScrollTop(element) > 0 || scrollState.getMaxScrollLeft(element) > 0)
                && !isReplayScrollOffsetEligible(box, scrollState, collectsPositionedDescendants)) {
            snapshot.put(element, new int[] {scrollState.getScrollLeft(element), scrollState.getScrollTop(element)});
        }
        for (DocumentLayoutBox child : box.getChildren()) {
            collectScrollDependencies(child, scrollState, snapshot, false);
        }
    }

    /**
     * 把构建期视觉场景的边界/滚动态快照固化进各 CUSTOM 命令。
     *
     * <p>仅当存在至少一条 CUSTOM 命令时才一次性展开 {@code 元素 -> BoxLocation} 索引并包装为共享
     * {@link DocumentCustomRenderBounds}；无自定义渲染器的页面零额外开销。该快照供回放期
     * {@link DocumentCustomRenderSurface} 免实时查询读取视口/内容/图层文档坐标边界与滚动偏移。</p>
     *
     * @param commands 已生成的绘制命令
     * @param scene 构建期视觉场景
     * @param scrollState 构建期滚动态；可为 null
     */
    private static void attachCustomRenderBounds(List<DocumentPaintCommand> commands, VisualScene scene,
            DocumentScrollState scrollState) {
        boolean hasCustomCommand = false;
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == DocumentPaintCommandType.CUSTOM) {
                hasCustomCommand = true;
                break;
            }
        }
        if (!hasCustomCommand) {
            return;
        }
        DocumentCustomRenderBounds bounds = new DocumentCustomRenderBounds(
                DocumentVisualTraversal.indexBoxLocations(scene), scrollState);
        for (DocumentPaintCommand command : commands) {
            if (command.getType() == DocumentPaintCommandType.CUSTOM) {
                command.withCustomRenderBounds(bounds);
            }
        }
    }

    private static void appendBoxCommands(DocumentLayoutBox rootBox, BoxContext boxContext,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float inheritedOpacity,
            boolean paintStackingContext, List<ClipContext> activeClipChain, StackingContextResolver resolver,
            boolean transformActive, TextMeasureService textMeasureService, boolean textClipDeferred) {
        DocumentLayoutBox box = boxContext.getBox();
        // visibility:hidden 只隐藏当前元素自身，允许显式 visibility:visible 的后代恢复绘制。
        boolean visibilityHidden = isVisibilityHidden(box);
        int boxOffsetX = boxContext.getBoxOffsetX();
        int boxOffsetY = boxContext.getBoxOffsetY();
        UiTransform transform = resolveAnimatedTransform(animationTimeline, box, currentTimeNanos);
        boolean transformed = transform != null && !transform.isIdentity();
        boolean currentTransformActive = transformActive || transformed;
        if (transformed) {
            appendTransformStartCommand(box, commands, transform, boxOffsetX, boxOffsetY);
        }
        float localOpacity = resolveAnimatedOpacity(animationTimeline, box, currentTimeNanos);
        DocumentEffectChain effectChain = boxContext.getEffectChain();
        boolean paintContext = effectChain.createsPaintContext(box == rootBox, localOpacity);
        boolean resolvedPaintStackingContext = paintStackingContext || box == rootBox;
        float boxOpacity = paintContext ? inheritedOpacity : inheritedOpacity * localOpacity;
        List<ClipContext> currentClipChain = activeClipChain;
        if (paintContext) {
            appendPaintContextStartCommand(box, commands, localOpacity, boxOffsetX, boxOffsetY);
        }
        if (!visibilityHidden) {
            currentClipChain = transitionClipChain(commands, currentClipChain, boxContext.getClipChain(),
                    animationTimeline, currentTimeNanos);
            appendBackdropFilterCommand(box, commands, animationTimeline, currentTimeNanos, effectChain, boxOffsetX,
                    boxOffsetY);
            appendBoxShadowCommand(box, commands, animationTimeline, currentTimeNanos, boxOpacity, boxOffsetX,
                    boxOffsetY, false);
            appendBackgroundCommand(box, commands, animationTimeline, currentTimeNanos, boxOpacity, boxOffsetX,
                    boxOffsetY);
            appendBoxShadowCommand(box, commands, animationTimeline, currentTimeNanos, boxOpacity, boxOffsetX,
                    boxOffsetY, true);
            appendBorderCommand(box, commands, animationTimeline, currentTimeNanos, boxOpacity, boxOffsetX,
                    boxOffsetY);
        }
        int childOffsetX = boxContext.getChildOffsetX();
        int childOffsetY = boxContext.getChildOffsetY();
        // 免重建滚动容器：在随内容滚动的命令外层包 SCROLL_OFFSET 命令对（回放期实时叠加 -scroll）。容器自身 clip
        // 在作用域外（视口框不随自身滚动）。eligibility 第二参传 resolvedPaintStackingContext：仅当本盒在自身
        // 作用域内收集 positioned 后代时，才放行随内容线性滚动的 relative 后代（见 isReplayScrollOffsetEligible）。
        boolean scrollOffsetScope = isReplayScrollOffsetEligible(box, scrollState, resolvedPaintStackingContext);
        boolean deferTextClip = scrollOffsetScope || textClipDeferred;
        if (resolvedPaintStackingContext) {
            // 负 z-index 的 relative 后代同样随内容线性滚动，由 appendStackingPhaseItems 在该 phase 非空时
            // 自动补一对 SCROLL_OFFSET 作用域（wrapScrollOffset=scrollOffsetScope）。
            appendStackingPhaseItems(rootBox, boxContext, commands, scrollState, animationTimeline,
                    currentTimeNanos, boxOpacity, DocumentStackingPhase.NEGATIVE_POSITIONED, currentClipChain,
                    resolver, currentTransformActive, textMeasureService, deferTextClip, scrollOffsetScope);
            if (hasFlowContent(box, visibilityHidden)) {
                currentClipChain = transitionClipChain(commands, currentClipChain, boxContext.getChildClipChain(),
                        animationTimeline, currentTimeNanos);
            }
            if (scrollOffsetScope) {
                appendScrollOffsetStartCommand(box, commands, scrollState);
            }
            if (!visibilityHidden) {
                appendCustomCommand(box, commands, childOffsetX, childOffsetY);
            }
            appendInlineFragmentSurfaceCommands(box, commands, animationTimeline, currentTimeNanos, boxOpacity,
                    childOffsetX, childOffsetY);
            appendListMarkerCommand(box, commands, boxOpacity, childOffsetX, childOffsetY);
            appendTextCommands(box, commands, animationTimeline, currentTimeNanos, boxOpacity, childOffsetX,
                    childOffsetY, currentClipChain, currentTransformActive, textMeasureService, deferTextClip);
            appendNormalFlowChildren(rootBox, boxContext, commands, scrollState, animationTimeline,
                    currentTimeNanos, boxOpacity, currentClipChain, resolver, currentTransformActive,
                    textMeasureService, deferTextClip);
            if (scrollOffsetScope) {
                appendScrollOffsetEndCommand(box, commands);
            }
            if (hasFlowContent(box, visibilityHidden)) {
                currentClipChain = transitionClipChain(commands, currentClipChain, boxContext.getClipChain(),
                        animationTimeline, currentTimeNanos);
            }
            // 免重建滚动容器的 positioned 后代（已收窄为仅 relative，随内容线性滚动）落在 normal flow 段的
            // SCROLL_OFFSET 作用域之外、且处于容器自身 clip 链下，故由 appendStackingPhaseItems 在该 phase 非空时
            // 各自补一对 SCROLL_OFFSET，使回放期同样叠加 -scroll。其内文本同步走延迟裁剪（deferTextClip）。
            appendStackingPhaseItems(rootBox, boxContext, commands, scrollState, animationTimeline,
                    currentTimeNanos, boxOpacity, DocumentStackingPhase.POSITIONED_AUTO_OR_ZERO, currentClipChain,
                    resolver, currentTransformActive, textMeasureService, deferTextClip, scrollOffsetScope);
            appendStackingPhaseItems(rootBox, boxContext, commands, scrollState, animationTimeline,
                    currentTimeNanos, boxOpacity, DocumentStackingPhase.POSITIVE_POSITIONED, currentClipChain,
                    resolver, currentTransformActive, textMeasureService, deferTextClip, scrollOffsetScope);
        } else {
            if (hasFlowContent(box, visibilityHidden)) {
                currentClipChain = transitionClipChain(commands, currentClipChain, boxContext.getChildClipChain(),
                        animationTimeline, currentTimeNanos);
            }
            if (scrollOffsetScope) {
                appendScrollOffsetStartCommand(box, commands, scrollState);
            }
            if (!visibilityHidden) {
                appendCustomCommand(box, commands, childOffsetX, childOffsetY);
            }
            appendInlineFragmentSurfaceCommands(box, commands, animationTimeline, currentTimeNanos, boxOpacity,
                    childOffsetX, childOffsetY);
            appendListMarkerCommand(box, commands, boxOpacity, childOffsetX, childOffsetY);
            appendTextCommands(box, commands, animationTimeline, currentTimeNanos, boxOpacity, childOffsetX,
                    childOffsetY, currentClipChain, currentTransformActive, textMeasureService, deferTextClip);
            appendNormalFlowChildren(rootBox, boxContext, commands, scrollState, animationTimeline,
                    currentTimeNanos, boxOpacity, currentClipChain, resolver, currentTransformActive,
                    textMeasureService, deferTextClip);
            if (scrollOffsetScope) {
                appendScrollOffsetEndCommand(box, commands);
            }
            if (hasFlowContent(box, visibilityHidden)) {
                currentClipChain = transitionClipChain(commands, currentClipChain, boxContext.getClipChain(),
                        animationTimeline, currentTimeNanos);
            }
        }
        currentClipChain = transitionClipChain(commands, currentClipChain, boxContext.getClipChain(),
                animationTimeline, currentTimeNanos);
        if (!visibilityHidden) {
            appendScrollbarCommands(rootBox, box, commands, scrollState, boxOffsetX, boxOffsetY, currentTimeNanos,
                    boxOpacity);
            appendOutlineCommand(box, commands, animationTimeline, currentTimeNanos, boxOpacity, boxOffsetX,
                    boxOffsetY);
        }
        transitionClipChain(commands, currentClipChain, activeClipChain, animationTimeline, currentTimeNanos);
        if (paintContext) {
            appendPaintContextEndCommand(box, commands, boxOffsetX, boxOffsetY);
        }
        if (transformed) {
            appendTransformEndCommand(box, commands, transform, boxOffsetX, boxOffsetY);
        }
    }

    /**
     * 判断滚动容器是否可走回放期偏移栈（免重建）。
     *
     * <p>条件：1) 当前盒可滚动（任一方向 maxScroll &gt; 0）；2) 子树内所有随内容线性滚动的命令都落在该盒的
     * {@code SCROLL_OFFSET} 作用域内。第二条按 {@code collectsPositionedDescendants} 分两种判据：</p>
     *
     * <ul>
     *   <li><b>该盒在自身作用域内收集 positioned 后代</b>（建立 stacking context 或为绘制根）：
     *   {@code position:relative} 后代「保留普通流位置、仅按 inset 偏移」，坐标从本盒 childOffset（已含本容器
     *   scroll）出发随内容线性滚动，且 {@code appendBoxCommands} 已为 NEGATIVE/AUTO/POSITIVE 三个 positioned
     *   阶段补包 {@code SCROLL_OFFSET} 作用域，故 relative 后代可被正确覆盖、放行。仅 absolute/fixed/sticky
     *   （滚动语义与线性叠加 -scroll 不一致）触发回退。</li>
     *   <li><b>该盒不收集 positioned 后代</b>（非 stacking context，positioned 后代被祖先 stacking context
     *   收集、脱离本盒作用域）：任何 positioned 后代（含 relative）都会逃逸本盒的 {@code SCROLL_OFFSET}
     *   作用域，故只要存在任一 positioned 后代即回退，保守且正确。</li>
     * </ul>
     *
     * @param box 候选滚动容器
     * @param scrollState 构建期滚动态；为 null 时不可走偏移栈
     * @param collectsPositionedDescendants 该盒是否在自身作用域内收集 positioned 后代（建立 stacking context 或为绘制根）
     * @return 是否可走回放期偏移栈
     */
    private static boolean isReplayScrollOffsetEligible(DocumentLayoutBox box, DocumentScrollState scrollState,
            boolean collectsPositionedDescendants) {
        if (scrollState == null) {
            return false;
        }
        ElementNode element = box.getElement();
        if (element == null) {
            return false;
        }
        if (scrollState.getMaxScrollTop(element) <= 0 && scrollState.getMaxScrollLeft(element) <= 0) {
            return false;
        }
        if (collectsPositionedDescendants) {
            return !hasScrollEscapingDescendant(box);
        }
        return !hasPositionedDescendant(box);
    }

    /**
     * 判断布局盒子树是否含 positioned（非 static）后代。
     *
     * @param box 布局盒
     * @return 子树是否含 positioned 后代
     */
    private static boolean hasPositionedDescendant(DocumentLayoutBox box) {
        for (DocumentLayoutBox child : box.getChildren()) {
            if (child.getComputedStyle().getPosition() != UiPosition.STATIC) {
                return true;
            }
            if (hasPositionedDescendant(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断布局盒子树是否含「脱离滚动作用域」的 positioned 后代（absolute/fixed/sticky）。
     *
     * <p>{@code position:relative} 不脱流、随内容线性滚动，不计入；其余非 static 定位（absolute/fixed/sticky）
     * 滚动语义与线性叠加 -scroll 不一致，计入。relative 后代的子树继续递归检查，藏在其内的 absolute/fixed/sticky
     * 仍会被发现。</p>
     *
     * @param box 布局盒
     * @return 子树是否含 absolute/fixed/sticky 后代
     */
    private static boolean hasScrollEscapingDescendant(DocumentLayoutBox box) {
        for (DocumentLayoutBox child : box.getChildren()) {
            UiPosition position = child.getComputedStyle().getPosition();
            if (position != UiPosition.STATIC && position != UiPosition.RELATIVE) {
                return true;
            }
            if (hasScrollEscapingDescendant(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Emit {@code SCROLL_OFFSET_START} 命令，left/top 携带「构建期 scroll 快照」。
     *
     * <p>delta 模型：回放期 {@code delta = 构建期scroll - 当前scroll}，叠加进累计偏移，最终视觉位置 =
     * 命令坐标 + delta。因此 START 命令必须把构建期 scroll 快照写入 left/top，缺失则 delta 漏掉
     * {@code +构建期scroll} 项、滚动后内容错位。provider 为 NONE 时 delta 恒为 0、逐像素等价现状。</p>
     *
     * @param box 滚动容器盒
     * @param commands 命令列表
     * @param scrollState 构建期滚动态，提供 scroll 快照
     */
    private static void appendScrollOffsetStartCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentScrollState scrollState) {
        ElementNode element = box.getElement();
        int buildScrollLeft = scrollState == null ? 0 : scrollState.getScrollLeft(element);
        int buildScrollTop = scrollState == null ? 0 : scrollState.getScrollTop(element);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.SCROLL_OFFSET_START, element,
                buildScrollLeft, buildScrollTop, buildScrollLeft, buildScrollTop, 0, 0, 0));
    }

    private static void appendScrollOffsetEndCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.SCROLL_OFFSET_END, box.getElement(),
                0, 0, 0, 0, 0, 0, 0));
    }

    private static void appendTransformStartCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            UiTransform transform, int offsetX, int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TRANSFORM_START, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, transform));
    }

    private static void appendTransformEndCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            UiTransform transform, int offsetX, int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TRANSFORM_END, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, transform));
    }

    private static void appendPaintContextStartCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            float localOpacity, int offsetX, int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.PAINT_CONTEXT_START, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, 0, null, null, 0, 1.0F, localOpacity,
                DocumentEffectType.PAINT_CONTEXT));
    }

    private static void appendPaintContextEndCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            int offsetX, int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.PAINT_CONTEXT_END, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, 0, null, null, 0, 1.0F, 1.0F,
                DocumentEffectType.PAINT_CONTEXT));
    }

    private static void appendNormalFlowChildren(DocumentLayoutBox rootBox, BoxContext contextRootContext,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float inheritedOpacity,
            List<ClipContext> activeClipChain, StackingContextResolver resolver, boolean transformActive,
            TextMeasureService textMeasureService, boolean textClipDeferred) {
        List<TraversalEntry> children = DocumentVisualTraversal.getNormalFlowEntries(contextRootContext.getBox(),
                contextRootContext, scrollState, resolver, false);
        for (TraversalEntry child : children) {
            appendBoxCommands(rootBox, child.getBoxContext(), commands, scrollState, animationTimeline,
                    currentTimeNanos, inheritedOpacity, child.isStackingContext(), activeClipChain, resolver,
                    transformActive, textMeasureService, textClipDeferred);
        }
    }

    /**
     * Append 指定 stacking phase 的命令；可选地在该 phase 实际产出命令时为其包裹一对 {@code SCROLL_OFFSET}
     * 作用域，使回放期对这些随内容线性滚动的 positioned 后代（已收窄为仅 relative）叠加 -scroll。
     *
     * <p>仅当 {@code wrapScrollOffset} 为 true 且该 phase 至少有一个待绘制 entry 时才补 START/END，避免空作用域
     * 污染命令序列与增加回放开销。</p>
     *
     * @param wrapScrollOffset 是否为本 phase 命令包裹 SCROLL_OFFSET 作用域（仅 eligible 滚动容器为 true）
     */
    private static void appendStackingPhaseItems(DocumentLayoutBox rootBox, BoxContext contextRootContext,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float inheritedOpacity,
            DocumentStackingPhase phase, List<ClipContext> activeClipChain, StackingContextResolver resolver,
            boolean transformActive, TextMeasureService textMeasureService, boolean textClipDeferred,
            boolean wrapScrollOffset) {
        List<TraversalEntry> items = DocumentVisualTraversal.collectStackingPhaseEntries(contextRootContext.getBox(),
                contextRootContext, scrollState, resolver, phase);
        if (items.isEmpty()) {
            return;
        }
        if (wrapScrollOffset) {
            appendScrollOffsetStartCommand(contextRootContext.getBox(), commands, scrollState);
        }
        for (TraversalEntry item : items) {
            appendBoxCommands(rootBox, item.getBoxContext(), commands, scrollState, animationTimeline,
                    currentTimeNanos, inheritedOpacity, item.isStackingContext(), activeClipChain, resolver,
                    transformActive, textMeasureService, textClipDeferred);
        }
        if (wrapScrollOffset) {
            appendScrollOffsetEndCommand(contextRootContext.getBox(), commands);
        }
    }

    private static boolean hasFlowContent(DocumentLayoutBox box, boolean visibilityHidden) {
        if (hasNormalFlowChildren(box)) {
            return true;
        }
        if (visibilityHidden) {
            return false;
        }
        return box.getElement().getCustomRenderer() != null || !box.getInlineFragments().isEmpty()
                || !box.getTextRuns().isEmpty() || isListItemWithMarker(box);
    }

    private static boolean hasNormalFlowChildren(DocumentLayoutBox box) {
        for (DocumentLayoutBox child : box.getChildren()) {
            if (child.getStackingPhase() == DocumentStackingPhase.NORMAL_FLOW) {
                return true;
            }
        }
        return false;
    }

    private static boolean isListItemWithMarker(DocumentLayoutBox box) {
        ElementNode element = box.getElement();
        if (!"li".equals(element.getTagName())) {
            return false;
        }
        ElementNode parent = resolveParentElement(element);
        if (parent == null) {
            return false;
        }
        String parentTagName = parent.getTagName();
        if (!"ul".equals(parentTagName) && !"ol".equals(parentTagName)) {
            return false;
        }
        return box.getComputedStyle().getListStyleType() != UiListStyleType.NONE;
    }

    private static void appendBackgroundCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY) {
        ComputedStyle style = box.getComputedStyle();
        int color = resolveAnimatedColor(animationTimeline, box, DocumentAnimationProperty.BACKGROUND_COLOR,
                style.getBackgroundColor(), currentTimeNanos);
        color = applyOpacity(color, opacity);
        UiBackgroundImage backgroundImage = style.getBackgroundImage();
        if (box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveBorderRadii(box, animationTimeline,
                currentTimeNanos);
        if (!isTransparent(color)) {
            commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND, box.getElement(),
                    box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                    box.getBottom() + offsetY, color, 0, cornerRadii));
        }
        if (backgroundImage != null) {
            commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND_IMAGE, box.getElement(),
                    box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                    box.getBottom() + offsetY, cornerRadii, backgroundImage));
        }
    }

    private static void appendBackdropFilterCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, DocumentEffectChain effectChain,
            int offsetX, int offsetY) {
        BackdropBlurPolicy backdropBlurPolicy = resolveBackdropBlurPolicy(box);
        if (!backdropBlurPolicy.resolveEnabled(BackdropBlurConfig.getInstance())) {
            return;
        }
        int blurRadius = resolveAnimatedBackdropBlurRadius(animationTimeline, box, effectChain.getBackdropBlurRadius(),
                currentTimeNanos, backdropBlurPolicy);
        float saturation = effectChain.getBackdropSaturation();
        boolean runningBlurTransition = hasRunningTransition(animationTimeline, box,
                DocumentAnimationProperty.BACKDROP_BLUR_RADIUS);
        if ((!hasBackdropFilter(blurRadius, saturation) && !runningBlurTransition)
                || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveBorderRadii(box, animationTimeline,
                currentTimeNanos);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKDROP_FILTER, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, cornerRadii, UiSurfaceStyle.CORNER_ALL, null, null, blurRadius,
                saturation, 1.0F,
                DocumentEffectType.BACKDROP_FILTER));
    }

    private static void appendBorderCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY) {
        ComputedStyle style = box.getComputedStyle();
        if (style.getBorderStyle() == UiBorderStyle.NONE || style.getBorderStyle() == UiBorderStyle.HIDDEN
                || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        DocumentLayoutEdges borderWidths = box.getBorder();
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveBorderRadii(box, animationTimeline,
                currentTimeNanos);
        int color = resolveAnimatedColor(animationTimeline, box, DocumentAnimationProperty.BORDER_COLOR,
                style.getBorderColor(), currentTimeNanos);
        color = applyOpacity(color, opacity);
        if (isTransparent(color) || borderWidths.getTop() <= 0 && borderWidths.getRight() <= 0
                && borderWidths.getBottom() <= 0 && borderWidths.getLeft() <= 0) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BORDER, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, color, borderWidths.getTop(), cornerRadii).withElementStyle(style));
    }

    private static void appendBoxShadowCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY, boolean inset) {
        UiBoxShadow boxShadow = resolveAnimatedBoxShadow(box, animationTimeline, currentTimeNanos);
        if (boxShadow == null || boxShadow.getColor() == 0 || boxShadow.isInset() != inset) {
            return;
        }
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveBorderRadii(box, animationTimeline,
                currentTimeNanos);
        commands.add(new DocumentPaintCommand(
                inset ? DocumentPaintCommandType.BOX_SHADOW_INSET : DocumentPaintCommandType.BOX_SHADOW,
                box.getElement(), box.getLeft() + offsetX, box.getTop() + offsetY,
                box.getRight() + offsetX, box.getBottom() + offsetY, applyOpacity(boxShadow.getColor(), opacity), 0,
                cornerRadii, boxShadow).withElementStyle(box.getComputedStyle()));
    }

    private static void appendOutlineCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY) {
        ComputedStyle style = box.getComputedStyle();
        UiOutline outline = style.getOutline();
        if (outline == null || outline.isNone() || outline.getStyle() == UiBorderStyle.HIDDEN) {
            return;
        }
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveBorderRadii(box, animationTimeline,
                currentTimeNanos);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.OUTLINE, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, applyOpacity(outline.getColor(), opacity), outline.getWidth(),
                cornerRadii).withElementStyle(style));
    }

    private static void appendCustomCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands, int offsetX,
            int offsetY) {
        DocumentCustomRenderer customRenderer = box.getElement().getCustomRenderer();
        if (customRenderer == null || box.getWidth() <= 0 || box.getHeight() <= 0) {
            return;
        }
        int contentLeft = box.getContentLeft() + offsetX;
        int contentTop = box.getContentTop() + offsetY;
        int contentRight = contentLeft + box.getContentWidth();
        int contentBottom = contentTop + box.getContentHeight();
        if (contentRight <= contentLeft || contentBottom <= contentTop) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CUSTOM, box.getElement(),
                contentLeft, contentTop, contentRight, contentBottom,
                0, 0, 0, null, customRenderer));
    }

    private static void appendTextCommands(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY, List<ClipContext> activeClipChain, boolean transformActive,
            TextMeasureService textMeasureService, boolean deferTextClip) {
        // 免重建滚动容器子树内的文本命令坐标是与滚动无关的内容坐标，构建期按当前滚动位置剔除/横向裁切会让
        // 滚动后进入视口的文本缺失，故在 deferTextClip 时全量生成并打标记，由回放期按实时反算窗口再裁。
        DocumentTextPaintClipper.ClipBounds clipBounds = transformActive || deferTextClip ? null
                : DocumentTextPaintClipper.resolveClipBounds(activeClipChain);
        for (DocumentLayoutTextRun textRun : box.getTextRuns()) {
            if (textRun.getText().isEmpty() || textRun.getWidth() <= 0 || textRun.getHeight() <= 0) {
                continue;
            }
            if (isVisibilityHidden(textRun.getOwnerElement())) {
                continue;
            }
            int color = resolveTextRunColor(textRun, animationTimeline, currentTimeNanos, opacity);
            if (isTransparent(color)) {
                continue;
            }
            ComputedStyle ownerStyle = UiStyleResolver.compute(textRun.getOwnerElement());
            if (deferTextClip) {
                // 全量整 run 生成：left/width 用布局原始宽度，回放期再按反算窗口剔除并对超长单行横向裁切。
                int runLeft = textRun.getLeft() + offsetX;
                int runRight = runLeft + textRun.getWidth();
                appendTextDecorationCommand(textRun, commands, color, runLeft, runRight, offsetY, ownerStyle);
                commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, textRun.getOwnerElement(),
                        runLeft, textRun.getTop() + offsetY, runRight,
                        textRun.getBottom() + offsetY, color, 0, 0, textRun.getText(), textRun.getTextContentMode(),
                        ownerStyle == null ? UiFontWeight.NORMAL : ownerStyle.getFontWeight(),
                        ownerStyle == null ? UiFontStyle.NORMAL : ownerStyle.getFontStyle(),
                        textRun.getTextMeasureStyle(), null, 0, 1.0F, 1.0F).withElementStyle(ownerStyle)
                        .withClipDeferred());
                continue;
            }
            DocumentTextPaintClipper.PaintBounds paintBounds = DocumentTextPaintClipper.resolvePaintBounds(textRun,
                    ownerStyle, textMeasureService, offsetX, offsetY, clipBounds != null, false);
            int expansion = DocumentTextPaintClipper.resolveVisualExpansion(ownerStyle);
            if (clipBounds != null && !DocumentTextPaintClipper.intersectsExpanded(paintBounds, clipBounds,
                    expansion)) {
                continue;
            }
            if (clipBounds != null) {
                paintBounds = DocumentTextPaintClipper.resolvePaintBounds(textRun, ownerStyle, textMeasureService,
                        offsetX, offsetY, true, true);
                if (!DocumentTextPaintClipper.intersectsExpanded(paintBounds, clipBounds, expansion)) {
                    continue;
                }
            }
            DocumentTextPaintClipper.PaintSlice paintSlice = DocumentTextPaintClipper.resolveVisibleSlice(textRun,
                    paintBounds, ownerStyle, textMeasureService, clipBounds, expansion);
            if (paintSlice.getText().isEmpty() || paintSlice.getRight() <= paintSlice.getLeft()) {
                continue;
            }
            appendTextDecorationCommand(textRun, commands, color, paintSlice.getLeft(), paintSlice.getRight(),
                    offsetY, ownerStyle);
            commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, textRun.getOwnerElement(),
                    paintSlice.getLeft(), textRun.getTop() + offsetY, paintSlice.getRight(),
                    textRun.getBottom() + offsetY, color, 0, 0, paintSlice.getText(), textRun.getTextContentMode(),
                    ownerStyle == null ? UiFontWeight.NORMAL : ownerStyle.getFontWeight(),
                    ownerStyle == null ? UiFontStyle.NORMAL : ownerStyle.getFontStyle(),
                    textRun.getTextMeasureStyle(), null, 0, 1.0F, 1.0F).withElementStyle(ownerStyle));
        }
    }

    private static void appendListMarkerCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            float opacity, int offsetX, int offsetY) {
        ElementNode element = box.getElement();
        if (!"li".equals(element.getTagName())) {
            return;
        }
        ElementNode parent = resolveParentElement(element);
        if (parent == null) {
            return;
        }
        String parentTagName = parent.getTagName();
        if (!"ul".equals(parentTagName) && !"ol".equals(parentTagName)) {
            return;
        }
        ComputedStyle style = box.getComputedStyle();
        if (style.getVisibility() == UiVisibility.HIDDEN) {
            return;
        }
        UiListStyleType listStyleType = style.getListStyleType();
        String markerText;
        if (listStyleType == UiListStyleType.NONE) {
            return;
        }
        if (listStyleType == UiListStyleType.DECIMAL) {
            markerText = String.valueOf(resolveListItemIndex(element)) + ".";
        } else if (listStyleType == UiListStyleType.CIRCLE) {
            markerText = "◦";
        } else if (listStyleType == UiListStyleType.SQUARE) {
            markerText = "▪";
        } else if (listStyleType == UiListStyleType.DISC) {
            markerText = "•";
        } else {
            return;
        }
        if (markerText.isEmpty() || box.getContentHeight() <= 0) {
            return;
        }
        int textColor = applyOpacity(style.getTextColor(), opacity);
        if (isTransparent(textColor)) {
            return;
        }
        int markerWidth = Math.max(4, markerText.length() * 8);
        int markerRight = box.getContentLeft() + offsetX - 6;
        int markerLeft = Math.max(box.getLeft() + offsetX, markerRight - markerWidth);
        int markerTop = box.getContentTop() + offsetY;
        int markerBottom = Math.min(box.getBottom() + offsetY, markerTop + Math.max(1, box.getContentHeight()));
        if (markerRight <= markerLeft || markerBottom <= markerTop) {
            return;
        }
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT, element, markerLeft, markerTop,
                markerRight, markerBottom, textColor, 0, 0, markerText, TextContentMode.UILIB_RAW,
                style.getFontWeight(), style.getFontStyle(),
                resolveTextMeasureStyle(style, TextContentMode.UILIB_RAW), null, 0, 1.0F, 1.0F));
    }

    private static TextMeasureStyle resolveTextMeasureStyle(ComputedStyle style, TextContentMode textContentMode) {
        int fontSizePx = style == null || style.getFontSize() == null
                ? TextMeasureStyle.DEFAULT_FONT_SIZE_PX
                : Math.max(1, style.getFontSize().resolve(TextMeasureStyle.DEFAULT_FONT_SIZE_PX,
                        TextMeasureStyle.DEFAULT_FONT_SIZE_PX));
        UiFontWeight fontWeight = style == null ? UiFontWeight.NORMAL : style.getFontWeight();
        UiFontStyle fontStyle = style == null ? UiFontStyle.NORMAL : style.getFontStyle();
        return new TextMeasureStyle(fontSizePx, textContentMode, fontWeight, fontStyle);
    }

    private static ElementNode resolveParentElement(ElementNode element) {
        if (element == null || !(element.getParent() instanceof ElementNode)) {
            return null;
        }
        return (ElementNode) element.getParent();
    }

    private static int resolveListItemIndex(ElementNode element) {
        ElementNode parent = resolveParentElement(element);
        if (parent == null) {
            return 1;
        }
        int index = 0;
        for (club.heiqi.uilib.ui.dom.DocumentNode child : parent.getChildren()) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            if (!"li".equals(childElement.getTagName())) {
                continue;
            }
            index++;
            if (childElement == element) {
                return index;
            }
        }
        return Math.max(1, index);
    }

    private static void appendTextDecorationCommand(DocumentLayoutTextRun textRun, List<DocumentPaintCommand> commands,
            int color, int commandLeft, int commandRight, int offsetY, ComputedStyle ownerStyle) {
        UiTextDecoration textDecoration = ownerStyle == null
                ? UiStyleResolver.compute(textRun.getOwnerElement()).getTextDecoration()
                : ownerStyle.getTextDecoration();
        if (textDecoration == UiTextDecoration.NONE || textRun.getWidth() <= 0 || textRun.getHeight() <= 0) {
            return;
        }
        int lineTop = resolveTextDecorationTop(textRun, textDecoration);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.TEXT_DECORATION, textRun.getOwnerElement(),
                commandLeft, lineTop + offsetY, commandRight,
                lineTop + 1 + offsetY, color, 0, 0));
    }

    private static int resolveTextDecorationTop(DocumentLayoutTextRun textRun, UiTextDecoration textDecoration) {
        if (textDecoration == UiTextDecoration.OVERLINE) {
            return textRun.getTop();
        }
        if (textDecoration == UiTextDecoration.LINE_THROUGH) {
            return textRun.getTop() + Math.max(0, textRun.getHeight() / 2);
        }
        return textRun.getBottom() - Math.max(1, textRun.getHeight() / 6);
    }

    private static void appendInlineFragmentSurfaceCommands(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, float opacity, int offsetX,
            int offsetY) {
        for (DocumentLayoutInlineFragment inlineFragment : box.getInlineFragments()) {
            ElementNode ownerElement = inlineFragment.getOwnerElement();
            if (inlineFragment.getWidth() <= 0 || inlineFragment.getHeight() <= 0) {
                continue;
            }
            ComputedStyle ownerStyle = UiStyleResolver.compute(ownerElement);
            if (ownerStyle.getVisibility() == UiVisibility.HIDDEN) {
                continue;
            }
            int cornerMask = resolveInlineFragmentCornerMask(inlineFragment);
            UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveInlineFragmentBorderRadii(ownerStyle,
                    inlineFragment.getWidth(), inlineFragment.getHeight());
            int backgroundColor = resolveAnimatedColor(animationTimeline, ownerElement,
                    DocumentAnimationProperty.BACKGROUND_COLOR, ownerStyle.getBackgroundColor(), currentTimeNanos);
            backgroundColor = applyOpacity(backgroundColor, opacity);
            if (!isTransparent(backgroundColor)) {
                commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BACKGROUND, ownerElement,
                        inlineFragment.getLeft() + offsetX, inlineFragment.getTop() + offsetY,
                        inlineFragment.getRight() + offsetX, inlineFragment.getBottom() + offsetY, backgroundColor, 0,
                        cornerRadii, cornerMask, null, null, 0, 1.0F, 1.0F, null));
            }
            int borderWidth = Math.max(0, ownerStyle.getBorderWidth().resolve(inlineFragment.getWidth(), 0));
            int borderColor = resolveAnimatedColor(animationTimeline, ownerElement, DocumentAnimationProperty.BORDER_COLOR,
                    ownerStyle.getBorderColor(), currentTimeNanos);
            borderColor = applyOpacity(borderColor, opacity);
            if (!isTransparent(borderColor) && borderWidth > 0
                    && ownerStyle.getBorderStyle() != UiBorderStyle.NONE
                    && ownerStyle.getBorderStyle() != UiBorderStyle.HIDDEN) {
                commands.add(new DocumentPaintCommand(DocumentPaintCommandType.BORDER, ownerElement,
                        inlineFragment.getLeft() + offsetX, inlineFragment.getTop() + offsetY,
                        inlineFragment.getRight() + offsetX, inlineFragment.getBottom() + offsetY, borderColor,
                        borderWidth, cornerRadii, cornerMask, null, null, 0, 1.0F, 1.0F, null)
                        .withElementStyle(ownerStyle));
            }
        }
    }

    private static int resolveInlineFragmentCornerMask(DocumentLayoutInlineFragment inlineFragment) {
        boolean first = inlineFragment.isFirstForElement();
        boolean last = inlineFragment.isLastForElement();
        if (first && last) {
            return UiSurfaceStyle.CORNER_ALL;
        }
        int cornerMask = 0;
        if (first) {
            cornerMask |= UiSurfaceStyle.CORNER_TOP_LEFT | UiSurfaceStyle.CORNER_BOTTOM_LEFT;
        }
        if (last) {
            cornerMask |= UiSurfaceStyle.CORNER_TOP_RIGHT | UiSurfaceStyle.CORNER_BOTTOM_RIGHT;
        }
        return cornerMask;
    }

    private static UiBorderRadiusResolver.ResolvedCornerRadii resolveInlineFragmentBorderRadii(ComputedStyle style,
            int width, int height) {
        return UiBorderRadiusResolver.resolve(style, width, height);
    }

    private static int resolveTextRunColor(DocumentLayoutTextRun textRun, DocumentAnimationTimeline animationTimeline,
            long currentTimeNanos, float opacity) {
        ElementNode ownerElement = textRun.getOwnerElement();
        int baseColor = UiStyleResolver.compute(ownerElement).getTextColor();
        int color = resolveAnimatedColor(animationTimeline, ownerElement, DocumentAnimationProperty.TEXT_COLOR,
                baseColor, currentTimeNanos);
        return applyOpacity(color, opacity);
    }

    private static int resolveAnimatedColor(DocumentAnimationTimeline animationTimeline, DocumentLayoutBox box,
            DocumentAnimationProperty property, int baseColor, long currentTimeNanos) {
        return resolveAnimatedColor(animationTimeline, box.getElement(), property, baseColor, currentTimeNanos);
    }

    private static int resolveAnimatedColor(DocumentAnimationTimeline animationTimeline, ElementNode element,
            DocumentAnimationProperty property, int baseColor, long currentTimeNanos) {
        if (animationTimeline == null) {
            return baseColor;
        }
        return animationTimeline.resolveColor(element, property, baseColor, currentTimeNanos);
    }

    private static float resolveAnimatedOpacity(DocumentAnimationTimeline animationTimeline, DocumentLayoutBox box,
            long currentTimeNanos) {
        float baseOpacity = box.getComputedStyle().getOpacity();
        if (animationTimeline == null) {
            return baseOpacity;
        }
        return animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.OPACITY, baseOpacity,
                currentTimeNanos);
    }

    private static UiTransform resolveAnimatedTransform(DocumentAnimationTimeline animationTimeline,
            DocumentLayoutBox box, long currentTimeNanos) {
        UiTransform baseTransform = box.getComputedStyle().getTransform();
        if (baseTransform == null) {
            baseTransform = UiTransform.identity();
        }
        if (animationTimeline == null) {
            return baseTransform;
        }
        ElementNode element = box.getElement();
        float translateX = animationTimeline.resolveFloat(element, DocumentAnimationProperty.TRANSLATE_X,
                baseTransform.getTranslateX(), currentTimeNanos);
        float translateY = animationTimeline.resolveFloat(element, DocumentAnimationProperty.TRANSLATE_Y,
                baseTransform.getTranslateY(), currentTimeNanos);
        float scaleX = animationTimeline.resolveFloat(element, DocumentAnimationProperty.SCALE_X,
                baseTransform.getScaleX(), currentTimeNanos);
        float scaleY = animationTimeline.resolveFloat(element, DocumentAnimationProperty.SCALE_Y,
                baseTransform.getScaleY(), currentTimeNanos);
        float rotate = animationTimeline.resolveFloat(element, DocumentAnimationProperty.ROTATE,
                baseTransform.getRotateDegrees(), currentTimeNanos);
        return UiTransform.of(translateX, translateY, scaleX, scaleY, rotate,
                baseTransform.getOriginX(), baseTransform.getOriginY());
    }

    private static UiBoxShadow resolveAnimatedBoxShadow(DocumentLayoutBox box,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos) {
        UiBoxShadow baseShadow = box.getComputedStyle().getBoxShadow();
        int baseOffsetX = baseShadow == null ? 0 : baseShadow.getOffsetX();
        int baseOffsetY = baseShadow == null ? 0 : baseShadow.getOffsetY();
        int baseBlurRadius = baseShadow == null ? 0 : baseShadow.getBlurRadius();
        int baseSpreadRadius = baseShadow == null ? 0 : baseShadow.getSpreadRadius();
        int baseColor = baseShadow == null ? 0 : baseShadow.getColor();
        boolean inset = baseShadow != null && baseShadow.isInset();
        if (animationTimeline == null) {
            return baseShadow;
        }
        int offsetX = Math.round(animationTimeline.resolveFloat(box.getElement(),
                DocumentAnimationProperty.BOX_SHADOW_OFFSET_X, baseOffsetX, currentTimeNanos));
        int offsetY = Math.round(animationTimeline.resolveFloat(box.getElement(),
                DocumentAnimationProperty.BOX_SHADOW_OFFSET_Y, baseOffsetY, currentTimeNanos));
        int blurRadius = Math.round(animationTimeline.resolveFloat(box.getElement(),
                DocumentAnimationProperty.BOX_SHADOW_BLUR_RADIUS, baseBlurRadius, currentTimeNanos));
        int spreadRadius = Math.round(animationTimeline.resolveFloat(box.getElement(),
                DocumentAnimationProperty.BOX_SHADOW_SPREAD_RADIUS, baseSpreadRadius, currentTimeNanos));
        int color = animationTimeline.resolveColor(box.getElement(), DocumentAnimationProperty.BOX_SHADOW_COLOR,
                baseColor, currentTimeNanos);
        if (baseShadow == null && offsetX == 0 && offsetY == 0 && blurRadius == 0 && spreadRadius == 0
                && color == 0) {
            return null;
        }
        return inset ? UiBoxShadow.inset(offsetX, offsetY, blurRadius, spreadRadius, color)
                : UiBoxShadow.of(offsetX, offsetY, blurRadius, spreadRadius, color);
    }

    private static int resolveAnimatedBackdropBlurRadius(DocumentAnimationTimeline animationTimeline,
            DocumentLayoutBox box, int baseRadius, long currentTimeNanos, BackdropBlurPolicy backdropBlurPolicy) {
        if (animationTimeline == null) {
            return baseRadius;
        }
        float animatedRadius = animationTimeline.resolveFloat(box.getElement(),
                DocumentAnimationProperty.BACKDROP_BLUR_RADIUS, baseRadius, currentTimeNanos);
        int maxRadius = backdropBlurPolicy.resolveMaxBlurRadius(BackdropBlurConfig.getInstance());
        return Math.max(0, Math.min(Math.round(animatedRadius), maxRadius));
    }

    private static BackdropBlurPolicy resolveBackdropBlurPolicy(DocumentLayoutBox box) {
        if (box == null || box.getElement() == null || box.getElement().getOwnerDocument() == null) {
            return BackdropBlurPolicy.inheritGlobal();
        }
        return box.getElement().getOwnerDocument().getBackdropBlurController().getPolicy();
    }

    private static boolean hasRunningTransition(DocumentAnimationTimeline animationTimeline, DocumentLayoutBox box,
            DocumentAnimationProperty property) {
        return animationTimeline != null && animationTimeline.hasRunningTransition(box.getElement(), property);
    }

    private static boolean hasBackdropFilter(int blurRadius, float saturation) {
        return blurRadius > 0 || Float.compare(saturation, 1.0F) != 0;
    }

    private static StackingContextResolver createPaintStackingContextResolver(final long currentTimeNanos,
            final DocumentAnimationTimeline animationTimeline) {
        return new StackingContextResolver() {
            @Override
            public boolean createsStackingContext(DocumentLayoutBox box) {
                return DocumentVisualTraversal.createsRuntimeStackingContext(box, currentTimeNanos,
                        animationTimeline);
            }
        };
    }

    private static int applyOpacity(int color, float opacity) {
        float clampedOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
        if (clampedOpacity >= 0.999F) {
            return color;
        }
        int alpha = Math.round(((color >>> 24) & 0xFF) * clampedOpacity);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static boolean isTransparent(int color) {
        return ((color >>> 24) & 0xFF) == 0;
    }

    private static void appendClipChainStart(List<DocumentPaintCommand> commands, List<ClipContext> clipChain,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos) {
        if (clipChain == null || clipChain.isEmpty()) {
            return;
        }
        for (ClipContext clipContext : clipChain) {
            appendClipStartCommand(clipContext.getBox(), commands, animationTimeline, currentTimeNanos,
                    clipContext.getEffectChain(), clipContext.getBoxOffsetX(), clipContext.getBoxOffsetY());
        }
    }

    private static void appendClipChainEnd(List<DocumentPaintCommand> commands, List<ClipContext> clipChain) {
        if (clipChain == null || clipChain.isEmpty()) {
            return;
        }
        for (int index = clipChain.size() - 1; index >= 0; index--) {
            ClipContext clipContext = clipChain.get(index);
            appendClipEndCommand(clipContext.getBox(), commands, clipContext.getBoxOffsetX(),
                    clipContext.getBoxOffsetY());
        }
    }

    private static List<ClipContext> transitionClipChain(List<DocumentPaintCommand> commands,
            List<ClipContext> currentClipChain, List<ClipContext> targetClipChain,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos) {
        List<ClipContext> current = currentClipChain == null ? Collections.<ClipContext>emptyList() : currentClipChain;
        List<ClipContext> target = targetClipChain == null ? Collections.<ClipContext>emptyList() : targetClipChain;
        int sharedPrefix = 0;
        int maxShared = Math.min(current.size(), target.size());
        while (sharedPrefix < maxShared && current.get(sharedPrefix) == target.get(sharedPrefix)) {
            sharedPrefix++;
        }
        for (int index = current.size() - 1; index >= sharedPrefix; index--) {
            ClipContext clipContext = current.get(index);
            appendClipEndCommand(clipContext.getBox(), commands, clipContext.getBoxOffsetX(), clipContext.getBoxOffsetY());
        }
        for (int index = sharedPrefix; index < target.size(); index++) {
            ClipContext clipContext = target.get(index);
            appendClipStartCommand(clipContext.getBox(), commands, animationTimeline, currentTimeNanos,
                    clipContext.getEffectChain(), clipContext.getBoxOffsetX(), clipContext.getBoxOffsetY());
        }
        return target;
    }

    private static void appendClipStartCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            DocumentAnimationTimeline animationTimeline, long currentTimeNanos, DocumentEffectChain effectChain,
            int offsetX, int offsetY) {
        DocumentEffectChain.ClipBounds clipBounds = effectChain.resolveChildClipBounds(offsetX, offsetY);
        UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii = resolveBorderRadii(box, animationTimeline,
                currentTimeNanos);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CLIP_START, box.getElement(),
                clipBounds.getLeft(), clipBounds.getTop(), clipBounds.getRight(), clipBounds.getBottom(), 0, 0,
                cornerRadii, UiSurfaceStyle.CORNER_ALL, null, null, 0, 1.0F, 1.0F,
                DocumentEffectType.OVERFLOW_CLIP));
    }

    private static void appendClipEndCommand(DocumentLayoutBox box, List<DocumentPaintCommand> commands, int offsetX,
            int offsetY) {
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.CLIP_END, box.getElement(),
                box.getLeft() + offsetX, box.getTop() + offsetY, box.getRight() + offsetX,
                box.getBottom() + offsetY, 0, 0, 0, null, null, 0, 1.0F, 1.0F,
                DocumentEffectType.OVERFLOW_CLIP));
    }

    private static void appendScrollbarCommands(DocumentLayoutBox rootBox, DocumentLayoutBox box,
            List<DocumentPaintCommand> commands, DocumentScrollState scrollState, int offsetX, int offsetY,
            long currentTimeNanos, float opacity) {
        if (scrollState == null || box.getContentWidth() <= 0 || box.getContentHeight() <= 0) {
            return;
        }
        int maxScrollTop = scrollState.getMaxScrollTop(box.getElement());
        int maxScrollLeft = scrollState.getMaxScrollLeft(box.getElement());
        boolean hasVerticalScrollbar = maxScrollTop > 0
                && (box.getComputedStyle().getOverflowY() == UiOverflow.AUTO
                        || box.getComputedStyle().getOverflowY() == UiOverflow.SCROLL);
        boolean hasHorizontalScrollbar = maxScrollLeft > 0
                && (box.getComputedStyle().getOverflowX() == UiOverflow.AUTO
                        || box.getComputedStyle().getOverflowX() == UiOverflow.SCROLL);
        if (!hasVerticalScrollbar && !hasHorizontalScrollbar) {
            return;
        }
        if (box != rootBox && !scrollState.shouldShowTransientScrollbar(box.getElement(), currentTimeNanos)) {
            return;
        }

        if (hasVerticalScrollbar) {
            appendScrollbarCommands(box, commands, scrollState.getVerticalScrollbarMetrics(box, offsetX, offsetY,
                    hasHorizontalScrollbar), opacity, true, maxScrollTop);
        }
        if (hasHorizontalScrollbar) {
            appendScrollbarCommands(box, commands, scrollState.getHorizontalScrollbarMetrics(box, offsetX, offsetY,
                    hasVerticalScrollbar), opacity, false, maxScrollLeft);
        }
    }

    private static void appendScrollbarCommands(DocumentLayoutBox box, List<DocumentPaintCommand> commands,
            ScrollbarMetrics metrics, float opacity, boolean vertical, int maxScrollOffset) {
        if (metrics == null) {
            return;
        }
        int radius = Math.max(0, Math.min(metrics.getTrackRight() - metrics.getTrackLeft(),
                metrics.getTrackBottom() - metrics.getTrackTop()) / 2);
        UiScrollbarColor scrollbarColor = box.getComputedStyle().getScrollbarColor();
        int trackColor = applyOpacity(scrollbarColor.getTrackColor(), opacity);
        int thumbColor = applyOpacity(scrollbarColor.getThumbColor(), opacity);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.SCROLLBAR_TRACK, box.getElement(),
                metrics.getTrackLeft(),
                metrics.getTrackTop(), metrics.getTrackRight(), metrics.getTrackBottom(), trackColor, 0,
                radius));
        // thumb 命令坐标用构建期滚动算出的位置（与现状一致），并附回放期重算描述：免重建滚动时 thumb 落在
        // SCROLL_OFFSET 作用域外、坐标不随滚动平移，回放期按实时滚动偏移在主轴上重算起点使其跟手。
        int trackStart = vertical ? metrics.getTrackTop() : metrics.getTrackLeft();
        int thumbSize = vertical ? metrics.getThumbBottom() - metrics.getThumbTop()
                : metrics.getThumbRight() - metrics.getThumbLeft();
        int trackLength = vertical ? metrics.getTrackBottom() - metrics.getTrackTop()
                : metrics.getTrackRight() - metrics.getTrackLeft();
        int travel = Math.max(0, trackLength - thumbSize);
        DocumentScrollbarThumbReplay thumbReplay = new DocumentScrollbarThumbReplay(box.getElement(), vertical,
                trackStart, travel, thumbSize, maxScrollOffset);
        commands.add(new DocumentPaintCommand(DocumentPaintCommandType.SCROLLBAR_THUMB, box.getElement(),
                metrics.getThumbLeft(), metrics.getThumbTop(), metrics.getThumbRight(), metrics.getThumbBottom(),
                thumbColor, 0, radius).withThumbReplay(thumbReplay));
    }

    private static UiBorderRadiusResolver.ResolvedCornerRadii resolveBorderRadii(DocumentLayoutBox box,
            DocumentAnimationTimeline animationTimeline,
            long currentTimeNanos) {
        UiBorderRadiusResolver.ResolvedCornerRadii radii = resolveStaticBorderRadii(box);
        if (hasRunningTransition(animationTimeline, box, DocumentAnimationProperty.BORDER_RADIUS)) {
            int radius = Math.round(animationTimeline.resolveFloat(box.getElement(), DocumentAnimationProperty.BORDER_RADIUS,
                    radii.getUniformRadius(), currentTimeNanos));
            radii = UiBorderRadiusResolver.resolve(box.getComputedStyle(), box.getWidth(), box.getHeight(),
                    Integer.valueOf(radius));
        }
        return radii;
    }

    private static UiBorderRadiusResolver.ResolvedCornerRadii resolveStaticBorderRadii(DocumentLayoutBox box) {
        return UiBorderRadiusResolver.resolve(box.getComputedStyle(), box.getWidth(), box.getHeight());
    }

    private static UiStyleInsets resolveBorderWidthSides(ComputedStyle style) {
        UiStyleInsets borderWidthSides = style.getBorderWidthSides();
        return borderWidthSides == null ? UiStyleInsets.all(style.getBorderWidth()) : borderWidthSides;
    }

    /**
     * 判断布局盒是否因 visibility:hidden 而不绘制。
     */
    private static boolean isVisibilityHidden(DocumentLayoutBox box) {
        return isVisibilityHidden(box.getElement());
    }

    private static boolean isVisibilityHidden(ElementNode element) {
        if (element == null) {
            return false;
        }
        return UiStyleResolver.compute(element).getVisibility() == UiVisibility.HIDDEN;
    }

}
