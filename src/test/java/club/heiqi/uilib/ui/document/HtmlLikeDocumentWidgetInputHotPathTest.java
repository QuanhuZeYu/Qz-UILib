package club.heiqi.uilib.ui.document;

import static club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.assertElementUid;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.DeterministicTextMeasureService;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.ManualAnimationClock;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidgetTestSupport.RecordingUiRenderContext;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionEndEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionEndHandler;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * {@code resolveLayoutBoxForBoundsQuery()} 快路径优化回归测试。
 *
 * <p>生产改动将 {@code findElementAt()} / {@code findElementAtWithin()} 的布局盒来源从
 * {@code resolveInteractiveLayoutBox()} 改为 {@code resolveLayoutBoxForBoundsQuery()}，
 * 稳态下不再重复推进动画时间线与派发完成事件。本测试类守护以下正确性契约：</p>
 * <ul>
 *   <li>A 类——命中语义等价：稳态/运行态下 hit-test 结果与改造前一致</li>
 *   <li>B 类——回调内同步改 DOM 后命中正确：click handler 内改 DOM/样式后立即生效</li>
 *   <li>C 类——滚动后命中正确：滚动改变 offset 后 hit-test 使用最新 scrollState</li>
 *   <li>E 类——动画完成事件不漏派：事件派发唯一化到绘制管线后不丢</li>
 * </ul>
 */
public class HtmlLikeDocumentWidgetInputHotPathTest {

    // ========================== A 类：命中语义等价 ==========================

    /**
     * A1: 验证稳态下同一坐标连续两次 {@code findElementAt} 返回同一元素。
     *
     * <p>快路径复用 {@code resolvePaintLayoutBox(false)} 的结果（与完整路径稳态时是同一个 rootBox），
     * 命中语义不应因跳过动画时间线推进而改变。</p>
     */
    @Test
    public void shouldReturnSameElementOnRepeatedFindElementAtInSteadyState() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        ElementNode firstHit = widget.findElementAt(20, 10);
        ElementNode secondHit = widget.findElementAt(20, 10);

        Assert.assertNotNull(firstHit);
        assertElementUid(firstHit, secondHit);
    }

    /**
     * A2: 验证多元素嵌套布局下 {@code findElementAt} 命中正确的最深元素。
     *
     * <p>三层嵌套（root &gt; child &gt; grandChild），坐标落在最深层元素内，
     * 应返回 grandChild 而非 child 或 root。</p>
     */
    @Test
    public void shouldFindDeepestElementInNestedLayoutAfterOptimization() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        ElementNode grandChild = document.div();
        root.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(80));
        child.style()
                .setWidth(UiStyleLength.px(60))
                .setHeight(UiStyleLength.px(40));
        grandChild.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10));
        child.append(grandChild);
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 120, 80,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 120, 80);

        // 坐标命中 grandChild
        ElementNode hit = widget.findElementAt(10, 5);
        assertElementUid(grandChild, hit);

        // 坐标命中 child 但不在 grandChild 内
        ElementNode hitChild = widget.findElementAt(50, 20);
        assertElementUid(child, hitChild);

        // 坐标超出 root
        Assert.assertNull(widget.findElementAt(200, 200));
    }

    /**
     * A3: 验证 layout 运行态动画期间 {@code findElementAt} 回退完整路径并命中正确。
     *
     * <p>当存在 LAYOUT 级运行态动画时，{@code resolveLayoutBoxForBoundsQuery()} 回退
     * {@code resolveInteractiveLayoutBox()}，行为与改造前完全一致。通过 width transition
     * 构造 layout runtime value，验证动画中帧命中运行态几何。</p>
     */
    @Test
    public void shouldFindCorrectElementDuringLayoutRuntimeAnimation() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode row = document.div();
        ElementNode animated = document.div();
        ElementNode sibling = document.div();
        root.style().setWidth(UiStyleLength.px(160));
        row.style().setDisplay(UiDisplay.FLEX);
        animated.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF112233)
                .setTransition(DocumentAnimationProperty.WIDTH, 1000L);
        sibling.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF445566);
        row.append(animated).append(sibling);
        root.append(row);
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 160, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 160, 40);

        // 初始稳态：animated=40px, sibling 从 x=40 开始
        widget.render(new RecordingUiRenderContext());
        // 触发 width transition：animated 40→80
        animated.style().setWidth(UiStyleLength.px(80));
        widget.render(new RecordingUiRenderContext());
        // 动画运行到一半：animated≈60px, sibling 从 x≈60 开始
        animationClock.setCurrentTimeNanos(500_000_000L);
        widget.render(new RecordingUiRenderContext());

        // 动画运行态下 hit-test：sibling 应在运行态位置 (~x=60)
        assertElementUid(sibling, widget.findElementAt(70, 10));
        // animated 仍占据运行态区域
        assertElementUid(animated, widget.findElementAt(30, 10));
    }

    // ========================== B 类：回调内同步改 DOM 后命中正确 ==========================

    /**
     * B1: 验证 click handler 内 {@code removeChild} 删除命中元素后，
     * 同坐标 {@code findElementAt} 不再命中已删元素。
     *
     * <p>回调内同步改 DOM 会立即 bump layoutVersion，快路径内
     * {@code resolvePaintLayoutBox} 应拿到新 rootBox，hitTest 不再返回已删元素。</p>
     */
    @Test
    public void shouldNotHitRemovedElementAfterSyncDomMutationInClickHandler() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        child.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                // 同步删除自身
                root.removeChild(child);
                return true;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        // 验证点击前可命中 child
        assertElementUid(child, widget.findElementAt(20, 10));

        // 点击 child 触发 removeChild
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 20, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 20, 10, 0, 0, 0, 0, 2L));

        // 点击后 child 已被移除，同坐标不应命中 child（因 root 占据该区域，命中 root）
        ElementNode hit = widget.findElementAt(20, 10);
        Assert.assertNotNull(hit);
        Assert.assertNotSame("不应命中已移除的 child", child, hit);
    }

    /**
     * B2: 验证 click handler 内将命中元素设为 {@code display:none}（LAYOUT 级）后，
     * 同序列后续 {@code findElementAt} 不再命中该元素。
     *
     * <p>{@code display:none} 导致元素退出布局树，layoutVersion bump 后快路径
     * 重建 layoutBox，hitTest 应跳过该元素。</p>
     */
    @Test
    public void shouldNotHitDisplayNoneElementAfterSyncStyleChangeInClickHandler() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode child = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        child.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20));
        child.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                child.style().setDisplay(UiDisplay.NONE);
                return true;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        // 验证点击前可命中 child
        assertElementUid(child, widget.findElementAt(20, 10));

        // 点击触发 display:none
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 20, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 20, 10, 0, 0, 0, 0, 2L));

        // display:none 后 child 退出布局树，同坐标不应命中 child（命中 root）
        ElementNode hit = widget.findElementAt(20, 10);
        Assert.assertNotNull(hit);
        Assert.assertNotSame("不应命中 display:none 的 child", child, hit);
    }

    /**
     * B3: 验证 click handler 内删除兄弟元素后，同坐标 {@code findElementAt} 命中正确剩余元素。
     *
     * <p>在 handler 内 {@code removeChild} 删除兄弟，布局重建后命中的应是
     * 该坐标下布局更新后的正确元素（而非残留的旧布局盒）。</p>
     */
    @Test
    public void shouldHitCorrectElementAfterSiblingRemovalInClickHandler() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final ElementNode first = document.div();
        final ElementNode second = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        first.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10));
        second.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(10));
        first.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                // 删除兄弟 second
                root.removeChild(second);
                return true;
            }
        });
        root.append(first).append(second);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        // 点击前 second 可命中（y=15 在 second 区域内）
        assertElementUid(second, widget.findElementAt(20, 15));

        // 点击 first 触发删除 second
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 20, 5, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 20, 5, 0, 0, 0, 0, 2L));

        // second 已被删除，同坐标不应命中 second
        ElementNode hit = widget.findElementAt(20, 15);
        Assert.assertNotNull(hit);
        Assert.assertNotSame(second, hit);
    }

    // ========================== C 类：滚动后命中正确 ==========================

    /**
     * C1: 验证滚轮滚动改变 offset 后，{@code findElementAt} 同一屏幕坐标命中
     * 滚动后该位置的正确元素。
     *
     * <p>快路径调用 {@code updateScrollStateFromCachedLayoutIfNeeded()} 同步 scrollState，
     * hitTest 使用最新 scrollState 计算视觉坐标，不应因复用 rootBox 而返回滚动前的旧元素。</p>
     */
    @Test
    public void shouldHitCorrectElementAtSameScreenPointAfterScroll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        first.style().setHeight(UiStyleLength.px(40));
        second.style().setHeight(UiStyleLength.px(40));
        root.append(first).append(second);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);

        // 滚动前：(10,10) 命中 first
        assertElementUid(first, widget.findElementAt(10, 10));

        // 滚轮滚动
        Assert.assertTrue(widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0,
                0, 1L)));

        // 滚动后：(10,10) 命中 second（first 已滚出视口）
        assertElementUid(second, widget.findElementAt(10, 10));
    }

    /**
     * C2: 验证滚动后在视口不同坐标命中正确的可见元素，多次查询结果一致。
     *
     * <p>滚动后连续两次 {@code findElementAt} 调用应返回一致结果，
     * 证明快路径的 rootBox 复用 + scrollState 同步不引入不一致性。</p>
     */
    @Test
    public void shouldReturnConsistentHitResultsAfterScrollWithMultipleQueries() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode first = document.div();
        ElementNode second = document.div();
        ElementNode third = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO);
        first.style().setHeight(UiStyleLength.px(40));
        second.style().setHeight(UiStyleLength.px(40));
        third.style().setHeight(UiStyleLength.px(40));
        root.append(first).append(second).append(third);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 20,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 20);

        // 滚轮滚动两次
        widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0, 0, 1L));
        widget.onMouseScroll(new UiMouseEvent(UiMouseEvent.Action.SCROLL, 10, 10, -1, -120, 0, 0, 2L));

        // 连续查询应一致
        ElementNode hit1 = widget.findElementAt(10, 5);
        ElementNode hit2 = widget.findElementAt(10, 5);
        Assert.assertNotNull(hit1);
        assertElementUid(hit1, hit2);
    }

    /**
     * C3: 验证可滚容器中 {@code scrollIntoView} 后 {@code findElementAt} 命中正确。
     *
     * <p>{@code scrollIntoView} 通过 API 路径修改 scrollState（不经过滚轮事件路径），
     * 后续 {@code findElementAt} 应读到最新 scrollState 并返回正确命中。</p>
     */
    @Test
    public void shouldHitCorrectElementAfterScrollIntoViewApi() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode spacer = document.div();
        ElementNode target = document.div();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setOverflowY(UiOverflow.AUTO);
        spacer.style().setHeight(UiStyleLength.px(80));
        target.style().setHeight(UiStyleLength.px(20));
        root.append(spacer).append(target);
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.applyLayoutBounds(0, 0, 80, 40);

        // 初始 scrollTop=0，视口内 (10,10) 命中 spacer 而非 target
        assertElementUid(spacer, widget.findElementAt(10, 10));

        // scrollIntoView 将 target 滚入视口
        Assert.assertTrue(target.scrollIntoView());
        Assert.assertTrue(widget.getScrollTop(root) > 0);

        // 滚动后 target 在屏幕 y∈[20,40)，选取 y=30 命中 target
        ElementNode hit = widget.findElementAt(10, 30);
        Assert.assertNotNull(hit);
        assertElementUid(target, hit);
    }

    // ========================== E 类：动画完成事件不漏派 ==========================

    /**
     * E1: 验证动画完成事件通过正常绘制路径被派发，不因稳态
     * {@code findElementAt} 不再 {@code flushCompletedAnimationEvents} 而丢失。
     *
     * <p>核心守护：动画完成事件派发已唯一化到绘制管线
     * ({@code resolveInteractiveLayoutBox()} → {@code flushCompletedAnimationEvents()})，
     * 稳态下 {@code findElementAt} 走快路径跳过此步骤。本测试证明只要每帧调用
     * {@code render()}（正常绘制路径），transitionend 事件就不会丢失。</p>
     *
     * <p>测试流程：
     * <ol>
     *   <li>启动 paint-only transition（background-color），注册 transitionend handler</li>
     *   <li>推进时钟到完成时刻</li>
     *   <li>调用 {@code findElementAt}（稳态快路径不派发事件）</li>
     *   <li>断言 transitionend 尚未派发</li>
     *   <li>调用 {@code render()}（正常绘制路径派发事件）</li>
     *   <li>断言 transitionend 被派发恰好一次</li>
     * </ol></p>
     */
    @Test
    public void shouldDispatchTransitionEndEventThroughRenderAfterBoundsQuerySkipsFlush() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentElementTransitionEndEvent> transitionEndEvents =
                new ArrayList<DocumentElementTransitionEndEvent>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setBackgroundColor(0xFF000000)
                .setTransition(DocumentAnimationProperty.BACKGROUND_COLOR, 1000L);
        root.setTransitionEndHandler(new DocumentElementTransitionEndHandler() {
            @Override
            public boolean onTransitionEnd(DocumentElementTransitionEndEvent event) {
                transitionEndEvents.add(event);
                return true;
            }
        });
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 40);

        // 初始渲染
        widget.render(new RecordingUiRenderContext());
        // 触发 transition（paint-only：不涉及 layout runtime value）
        root.style().setBackgroundColor(0xFFFFFFFF);
        widget.render(new RecordingUiRenderContext());
        Assert.assertEquals(1, widget.getActiveAnimationCount());

        // 推进时钟到完成
        animationClock.setCurrentTimeNanos(1_000_000_000L);

        // 调用 findElementAt（稳态快路径：没有 layout runtime，跳过 flushCompletedAnimationEvents）
        widget.findElementAt(40, 20);
        Assert.assertTrue("transitionend 不应由 findElementAt 派发",
                transitionEndEvents.isEmpty());

        // 调用 render()（正常绘制路径：resolveInteractiveLayoutBox → flushCompletedAnimationEvents）
        widget.render(new RecordingUiRenderContext());

        Assert.assertEquals("transitionend 应由 render 派发恰好一次",
                1, transitionEndEvents.size());
        Assert.assertEquals(DocumentAnimationProperty.BACKGROUND_COLOR,
                transitionEndEvents.get(0).getProperty());
        Assert.assertEquals(0, widget.getActiveAnimationCount());
    }

    /**
     * E2: 验证多次 {@code findElementAt} 调用不重复派发动画完成事件，
     * 最终由单次 {@code render()} 派发恰好一次。
     *
     * <p>确保快路径既不会漏派事件（E1），也不会因为多次命中查询而触发
     * 重复派发（本测试）。旧实现每次 {@code findElementAt} 都走
     * {@code resolveInteractiveLayoutBox()}，如果动画恰好在某次查询时完成，
     * 会导致非确定性的事件派发时机。新实现统一到绘制管线，派发次数和时机确定。</p>
     */
    @Test
    public void shouldNotDispatchDuplicateTransitionEndEventsAfterMultipleHitQueries() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        final List<DocumentElementTransitionEndEvent> transitionEndEvents =
                new ArrayList<DocumentElementTransitionEndEvent>();
        root.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40))
                .setBackgroundColor(0xFF000000)
                .setTransition(DocumentAnimationProperty.BACKGROUND_COLOR, 1000L);
        root.setTransitionEndHandler(new DocumentElementTransitionEndHandler() {
            @Override
            public boolean onTransitionEnd(DocumentElementTransitionEndEvent event) {
                transitionEndEvents.add(event);
                return true;
            }
        });
        ManualAnimationClock animationClock = new ManualAnimationClock();
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(document, 80, 40,
                new DeterministicTextMeasureService());
        widget.setAnimationClock(animationClock);
        widget.applyLayoutBounds(0, 0, 80, 40);

        widget.render(new RecordingUiRenderContext());
        root.style().setBackgroundColor(0xFFFFFFFF);
        widget.render(new RecordingUiRenderContext());

        // 推进时钟到完成，然后多次调用 findElementAt
        animationClock.setCurrentTimeNanos(1_000_000_000L);
        for (int i = 0; i < 5; i++) {
            widget.findElementAt(40, 20);
        }
        Assert.assertTrue("多次 findElementAt 不应派发事件",
                transitionEndEvents.isEmpty());

        // 单次 render 派发事件
        widget.render(new RecordingUiRenderContext());

        Assert.assertEquals("render 应派发恰好一次 transitionend",
                1, transitionEndEvents.size());
    }
}
