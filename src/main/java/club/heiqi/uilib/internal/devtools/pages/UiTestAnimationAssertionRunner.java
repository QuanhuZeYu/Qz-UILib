package club.heiqi.uilib.internal.devtools.pages;

import java.util.List;

import club.heiqi.uilib.ui.animation.DocumentAnimationClock;
import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
import club.heiqi.uilib.ui.animation.DocumentAnimationImpact;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.animation.DocumentKeyframes;
import club.heiqi.uilib.ui.animation.SystemDocumentAnimationClock;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationEndEvent;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationStartEvent;
import club.heiqi.uilib.ui.dom.DocumentElementAnimationStartHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionEndEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionEndHandler;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionStartEvent;
import club.heiqi.uilib.ui.dom.DocumentElementTransitionStartHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `/qzuilib test` Animation 分组自动断言与人工诊断。
 */
final class UiTestAnimationAssertionRunner {

    /**
     * 判断指定样例是否具备自动断言。
     *
     * @param caseId 样例编号
     * @return 是否自动断言样例
     */
    boolean isAutomatic(String caseId) {
        return "VIS-ANIM-001".equals(caseId)
                || "VIS-ANIM-002".equals(caseId)
                || "VIS-ANIM-003".equals(caseId)
                || "VIS-ANIM-004".equals(caseId);
    }

    /**
     * 判断指定样例是否为 Animation 人工诊断样例。
     *
     * @param caseId 样例编号
     * @return 是否人工诊断样例
     */
    boolean isManual(String caseId) {
        return "VIS-ANIM-005".equals(caseId);
    }

    /**
     * 执行 Animation 自动断言。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param testCase 样例规格
     * @param diagnostics 诊断摘要
     * @return 是否通过
     */
    boolean runAutomatic(HtmlLikeDocumentWidget widget, ElementNode scope, UiTestCaseSpec testCase,
            List<String> diagnostics) {
        String id = testCase.getId();
        try {
            if ("VIS-ANIM-001".equals(id)) {
                return assertTransition(widget, scope, diagnostics);
            }
            if ("VIS-ANIM-002".equals(id)) {
                return assertKeyframes(widget, scope, diagnostics);
            }
            if ("VIS-ANIM-003".equals(id)) {
                return assertTiming(widget, scope, diagnostics);
            }
            if ("VIS-ANIM-004".equals(id)) {
                return assertFillMode(widget, scope, diagnostics);
            }
        } finally {
            widget.setAnimationClock(SystemDocumentAnimationClock.getInstance());
        }
        diagnostics.add("未知 Animation 自动样例：" + id);
        return false;
    }

    /**
     * 输出 Animation 人工诊断信息。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param testCase 样例规格
     * @param diagnostics 诊断摘要
     */
    void diagnoseManual(HtmlLikeDocumentWidget widget, ElementNode scope, UiTestCaseSpec testCase,
            List<String> diagnostics) {
        if ("VIS-ANIM-005".equals(testCase.getId())) {
            try {
                diagnoseLayoutVsPaint(widget, scope, diagnostics);
            } finally {
                widget.setAnimationClock(SystemDocumentAnimationClock.getInstance());
            }
            return;
        }
        diagnostics.add("未知 Animation 人工样例：" + testCase.getId());
    }

    private boolean assertTransition(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode target = findByRole(scope, "transition-target");
        if (target == null) {
            diagnostics.add("transition 节点缺失");
            return false;
        }
        final int[] startEvents = new int[] { 0 };
        final int[] endEvents = new int[] { 0 };
        target.setTransitionStartHandler(new DocumentElementTransitionStartHandler() {
            @Override
            public boolean onTransitionStart(DocumentElementTransitionStartEvent event) {
                if (event.getProperty() == DocumentAnimationProperty.BACKGROUND_COLOR) {
                    startEvents[0]++;
                }
                return false;
            }
        });
        target.setTransitionEndHandler(new DocumentElementTransitionEndHandler() {
            @Override
            public boolean onTransitionEnd(DocumentElementTransitionEndEvent event) {
                if (event.getProperty() == DocumentAnimationProperty.BACKGROUND_COLOR) {
                    endEvents[0]++;
                }
                return false;
            }
        });
        ManualAnimationClock clock = new ManualAnimationClock();
        widget.setAnimationClock(clock);
        target.style()
                .clearTransitionProperties()
                .setBackgroundColor(0xFF1D4ED8);
        DocumentLayoutBox initialBox = resolveBox(widget, target);
        int initialBg = initialBox.getComputedStyle().getBackgroundColor();
        target.style()
                .setTransition(DocumentAnimationProperty.BACKGROUND_COLOR, 900L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_OUT)
                .setBackgroundColor(0xFF059669);
        DocumentLayoutBox startBox = resolveBox(widget, target);
        ComputedStyle style = startBox.getComputedStyle();
        DocumentAnimationTimeline.DiagnosticsSnapshot startSnapshot = widget.getAnimationDiagnosticsSnapshot();
        clock.setCurrentTimeNanos(450_000_000L);
        widget.resolveLayoutBoxForTest();
        DocumentAnimationTimeline.DiagnosticsSnapshot running = widget.getAnimationDiagnosticsSnapshot();
        clock.setCurrentTimeNanos(950_000_000L);
        DocumentLayoutBox finishedBox = resolveBox(widget, target);
        DocumentAnimationTimeline.DiagnosticsSnapshot finished = widget.getAnimationDiagnosticsSnapshot();
        diagnostics.add("transitionProperty=BACKGROUND_COLOR");
        diagnostics.add("transitionDurationNanos=" + style.getTransitionDurationNanos(DocumentAnimationProperty.BACKGROUND_COLOR));
        diagnostics.add("transitionTiming=" + style.getTransitionTimingFunction(DocumentAnimationProperty.BACKGROUND_COLOR));
        diagnostics.add("transitionInitialBg=" + toHex(initialBg));
        diagnostics.add("transitionFinalBg=" + toHex(finishedBox.getComputedStyle().getBackgroundColor()));
        diagnostics.add("transitionStartEvents=" + startEvents[0] + ", transitionEndEvents=" + endEvents[0]);
        diagnostics.add("transitionActive=start:" + startSnapshot.getTransitionCount(DocumentAnimationImpact.PAINT)
                + ",mid:" + running.getTransitionCount(DocumentAnimationImpact.PAINT) + ",end:"
                + finished.getTransitionCount(DocumentAnimationImpact.PAINT));
        diagnostics.add("transitionDiff=expected transition start/end lifecycle, active paint transition and final green style");
        return style.canTransition(DocumentAnimationProperty.BACKGROUND_COLOR)
                && style.getTransitionDurationNanos(DocumentAnimationProperty.BACKGROUND_COLOR) == 900_000_000L
                && initialBg == 0xFF1D4ED8
                && startEvents[0] > 0
                && endEvents[0] > 0
                && startSnapshot.getTransitionCount(DocumentAnimationImpact.PAINT) > 0
                && running.getTransitionCount(DocumentAnimationImpact.PAINT) > 0
                && finished.getTransitionCount(DocumentAnimationImpact.PAINT) == 0
                && finishedBox.getComputedStyle().getBackgroundColor() == 0xFF059669;
    }

    private boolean assertKeyframes(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode target = findByRole(scope, "keyframe-target");
        DocumentKeyframes keyframes = widget.getDocument().getKeyframes("qzAnimPulse");
        if (target == null || keyframes == null) {
            diagnostics.add("keyframes 节点或定义缺失");
            return false;
        }
        final int[] startEvents = new int[] { 0 };
        final int[] endEvents = new int[] { 0 };
        target.setAnimationStartHandler(new DocumentElementAnimationStartHandler() {
            @Override
            public boolean onAnimationStart(DocumentElementAnimationStartEvent event) {
                if ("qzAnimPulse".equals(event.getAnimationName())) {
                    startEvents[0]++;
                }
                return false;
            }
        });
        target.setAnimationEndHandler(new DocumentElementAnimationEndHandler() {
            @Override
            public boolean onAnimationEnd(DocumentElementAnimationEndEvent event) {
                if ("qzAnimPulse".equals(event.getAnimationName())) {
                    endEvents[0]++;
                }
                return false;
            }
        });
        ManualAnimationClock clock = new ManualAnimationClock();
        widget.setAnimationClock(clock);
        ComputedStyle declaredStyle = resolveBox(widget, target).getComputedStyle();
        restartDeclaredAnimation(widget, target, declaredStyle.getAnimationName());
        clock.setCurrentTimeNanos(500_000_000L);
        widget.resolveLayoutBoxForTest();
        DocumentAnimationTimeline.DiagnosticsSnapshot running = widget.getAnimationDiagnosticsSnapshot();
        clock.setCurrentTimeNanos(1_050_000_000L);
        widget.resolveLayoutBoxForTest();
        DocumentAnimationTimeline.DiagnosticsSnapshot finished = widget.getAnimationDiagnosticsSnapshot();
        int stopCount = keyframes.getColorTracks().get(DocumentAnimationProperty.BACKGROUND_COLOR).getStops().size();
        ComputedStyle style = resolveBox(widget, target).getComputedStyle();
        diagnostics.add("keyframeName=" + style.getAnimationName());
        diagnostics.add("keyframeStopCount=" + stopCount);
        diagnostics.add("keyframeStartEvents=" + startEvents[0] + ", keyframeEndEvents=" + endEvents[0]);
        diagnostics.add("keyframeActiveHalf=" + running.getKeyframeCount(DocumentAnimationImpact.PAINT));
        diagnostics.add("keyframeFillEnd=" + finished.getForwardsFillCount(DocumentAnimationImpact.PAINT));
        diagnostics.add("keyframesDiff=expected qzAnimPulse start/end events and forwards fill after three stops");
        return "qzAnimPulse".equals(declaredStyle.getAnimationName())
                && "qzAnimPulse".equals(style.getAnimationName())
                && stopCount == 3
                && startEvents[0] > 0
                && endEvents[0] > 0
                && running.getKeyframeCount(DocumentAnimationImpact.PAINT) > 0
                && finished.getForwardsFillCount(DocumentAnimationImpact.PAINT) > 0
                && style.getAnimationFillMode() == DocumentAnimationFillMode.FORWARDS;
    }

    private boolean assertTiming(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode linear = findByRole(scope, "timing-linear");
        ElementNode steps = findByRole(scope, "timing-steps");
        if (linear == null || steps == null) {
            diagnostics.add("timing 节点缺失");
            return false;
        }
        ManualAnimationClock clock = new ManualAnimationClock();
        widget.setAnimationClock(clock);
        ComputedStyle declaredLinearStyle = resolveBox(widget, linear).getComputedStyle();
        ComputedStyle declaredStepsStyle = resolveBox(widget, steps).getComputedStyle();
        restartDeclaredAnimation(widget, linear, declaredLinearStyle.getAnimationName());
        restartDeclaredAnimation(widget, steps, declaredStepsStyle.getAnimationName());
        clock.setCurrentTimeNanos(375_000_000L);
        widget.resolveLayoutBoxForTest();
        DocumentLayoutBox linearBox = resolveBox(widget, linear);
        DocumentLayoutBox stepsBox = resolveBox(widget, steps);
        ComputedStyle linearStyle = linearBox.getComputedStyle();
        ComputedStyle stepsStyle = stepsBox.getComputedStyle();
        float sampleProgress = 0.375F;
        float linearSample = linearStyle.getAnimationTimingFunction().apply(sampleProgress);
        float stepsSample = stepsStyle.getAnimationTimingFunction().apply(sampleProgress);
        DocumentAnimationTimeline.DiagnosticsSnapshot running = widget.getAnimationDiagnosticsSnapshot();
        diagnostics.add("timingLinear=" + linearStyle.getAnimationTimingFunction());
        diagnostics.add("timingSteps=" + stepsStyle.getAnimationTimingFunction());
        diagnostics.add("timingAnimationNames=linear:" + linearStyle.getAnimationName() + ",steps:"
                + stepsStyle.getAnimationName());
        diagnostics.add("timingProgressSample=linear:" + formatFloat(linearSample) + ",steps:"
                + formatFloat(stepsSample));
        diagnostics.add("timingActivePaintKeyframes=" + running.getKeyframeCount(DocumentAnimationImpact.PAINT));
        diagnostics.add("timingDiff=expected linear and steps(4,end) timing functions both drive paint keyframes");
        return "linear".equals(linearStyle.getAnimationTimingFunction().toString())
                && stepsStyle.getAnimationTimingFunction().toString().contains("steps(4")
                && "qzAnimPaintMove".equals(linearStyle.getAnimationName())
                && "qzAnimPaintMove".equals(stepsStyle.getAnimationName())
                && linearSample > stepsSample
                && running.getKeyframeCount(DocumentAnimationImpact.PAINT) >= 2;
    }

    private boolean assertFillMode(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode forwards = findByRole(scope, "fill-forwards");
        ElementNode none = findByRole(scope, "fill-none");
        if (forwards == null || none == null) {
            diagnostics.add("fill-mode 节点缺失");
            return false;
        }
        DocumentKeyframes keyframes = widget.getDocument().getKeyframes("qzAnimFill");
        if (keyframes == null) {
            diagnostics.add("fill keyframes 定义缺失");
            return false;
        }
        ManualAnimationClock clock = new ManualAnimationClock();
        long baseTimeNanos = System.nanoTime();
        clock.setCurrentTimeNanos(baseTimeNanos);
        widget.setAnimationClock(clock);
        ComputedStyle declaredForwardsStyle = resolveBox(widget, forwards).getComputedStyle();
        ComputedStyle declaredNoneStyle = resolveBox(widget, none).getComputedStyle();
        restartDeclaredAnimation(widget, forwards, declaredForwardsStyle.getAnimationName());
        restartDeclaredAnimation(widget, none, declaredNoneStyle.getAnimationName());
        clock.setCurrentTimeNanos(baseTimeNanos + 500_000_000L);
        widget.resolveLayoutBoxForTest();
        DocumentAnimationTimeline.DiagnosticsSnapshot running = widget.getAnimationDiagnosticsSnapshot();
        long endTimeNanos = Math.max(declaredForwardsStyle.getAnimationDurationNanos(),
                declaredNoneStyle.getAnimationDurationNanos()) + baseTimeNanos + 50_000_000L;
        clock.setCurrentTimeNanos(endTimeNanos);
        DocumentLayoutBox finishedRoot = widget.resolveLayoutBoxForTest();
        DocumentAnimationTimeline.DiagnosticsSnapshot finished = widget.getAnimationDiagnosticsSnapshot();
        DocumentLayoutBox forwardsBox = findLayoutBox(finishedRoot, forwards);
        DocumentLayoutBox noneBox = findLayoutBox(finishedRoot, none);
        if (forwardsBox == null || noneBox == null) {
            diagnostics.add("fill-mode 结束帧布局盒缺失");
            return false;
        }
        DocumentKeyframes.FloatTrack track = keyframes.getFloatTracks().get(DocumentAnimationProperty.WIDTH);
        diagnostics.add("fillForwardsMode=" + forwardsBox.getComputedStyle().getAnimationFillMode());
        diagnostics.add("fillNoneMode=" + noneBox.getComputedStyle().getAnimationFillMode());
        diagnostics.add("fillTrack=from:" + formatFloat(track.getFirstValue()) + ",to:" + formatFloat(track.getLastValue()));
        diagnostics.add("fillWidths=forwards:" + forwardsBox.getWidth() + ",none:" + noneBox.getWidth());
        diagnostics.add("fillActiveHalf=" + running.getKeyframeCount(DocumentAnimationImpact.LAYOUT));
        diagnostics.add("fillActiveEnd=" + finished.getKeyframeCount(DocumentAnimationImpact.LAYOUT));
        diagnostics.add("fillForwardsEnd=" + finished.getForwardsFillCount(DocumentAnimationImpact.LAYOUT));
        diagnostics.add("fillRuntimeEnd=" + widget.hasLayoutRuntimeValueForDiagnostics() + ",activeTotal="
                + widget.getActiveAnimationCount() + ",endNanos=" + endTimeNanos);
        diagnostics.add("fillModeDiff=expected forwards keeps wider runtime layout and none returns base width");
        return forwardsBox.getComputedStyle().getAnimationFillMode() == DocumentAnimationFillMode.FORWARDS
                && noneBox.getComputedStyle().getAnimationFillMode() == DocumentAnimationFillMode.NONE
                && "qzAnimFill".equals(declaredForwardsStyle.getAnimationName())
                && "qzAnimFill".equals(declaredNoneStyle.getAnimationName())
                && track.getLastValue() > track.getFirstValue()
                && running.getKeyframeCount(DocumentAnimationImpact.LAYOUT) >= 2
                && finished.getForwardsFillCount(DocumentAnimationImpact.LAYOUT) > 0
                && forwardsBox.getWidth() > noneBox.getWidth();
    }

    private void restartDeclaredAnimation(HtmlLikeDocumentWidget widget, ElementNode target, String animationName) {
        if (target == null || animationName == null) {
            return;
        }
        target.style().clearAnimationName();
        widget.resolveLayoutBoxForTest();
        target.style().setAnimationName(animationName);
        widget.resolveLayoutBoxForTest();
    }

    private void diagnoseLayoutVsPaint(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode layout = findByRole(scope, "layout-target");
        ElementNode layoutSibling = findByRole(scope, "layout-sibling");
        ElementNode paint = findByRole(scope, "paint-target");
        ElementNode paintSlot = findByRole(scope, "paint-slot");
        if (layout == null || layoutSibling == null || paint == null || paintSlot == null) {
            diagnostics.add("layout-vs-paint 节点缺失，保持人工待确认。 ");
            return;
        }
        ManualAnimationClock clock = new ManualAnimationClock();
        widget.setAnimationClock(clock);
        widget.resolveLayoutBoxForTest();
        clock.setCurrentTimeNanos(500_000_000L);
        widget.resolveLayoutBoxForTest();
        DocumentLayoutBox layoutBox = resolveBox(widget, layout);
        DocumentLayoutBox layoutSiblingBox = resolveBox(widget, layoutSibling);
        DocumentLayoutBox paintBox = resolveBox(widget, paint);
        DocumentLayoutBox paintSlotBox = resolveBox(widget, paintSlot);
        DocumentAnimationTimeline.DiagnosticsSnapshot snapshot = widget.getAnimationDiagnosticsSnapshot();
        diagnostics.add("layoutPaintActive=layoutKeyframes:" + snapshot.getKeyframeCount(DocumentAnimationImpact.LAYOUT)
                + ",paintKeyframes:" + snapshot.getKeyframeCount(DocumentAnimationImpact.PAINT));
        diagnostics.add("layoutTrack=targetW:" + layoutBox.getWidth() + ",siblingLeft:" + layoutSiblingBox.getLeft());
        diagnostics.add("paintTrack=targetLeft:" + paintBox.getLeft() + ",slotLeft:" + paintSlotBox.getLeft()
                + ",transform=" + paintBox.getComputedStyle().getTransform());
        diagnostics.add("layoutVsPaintDiff=运行态几何可机器诊断；width 应推动 sibling 重排，translate 只移动视觉 quad。 ");
        diagnostics.add("当前样例需游戏内截图确认 layout 重排轨道与 paint-only translate 视觉轨道节奏差异。 ");
    }

    private ElementNode findByRole(ElementNode current, String role) {
        if (current == null || role == null) {
            return null;
        }
        if (role.equals(current.getAttribute(UiTestAnimationVisualFactory.ROLE_ATTRIBUTE))) {
            return current;
        }
        for (DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findByRole((ElementNode) child, role);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private DocumentLayoutBox resolveBox(HtmlLikeDocumentWidget widget, ElementNode element) {
        DocumentLayoutBox box = findLayoutBox(widget.resolveLayoutBoxForTest(), element);
        if (box == null) {
            throw new IllegalStateException("未找到 Animation 样例布局盒: " + element.getTagName());
        }
        return box;
    }

    private DocumentLayoutBox findLayoutBox(DocumentLayoutBox current, ElementNode element) {
        if (current == null || element == null) {
            return null;
        }
        if (current.getElement() == element) {
            return current;
        }
        for (DocumentLayoutBox child : current.getChildren()) {
            DocumentLayoutBox found = findLayoutBox(child, element);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String formatFloat(float value) {
        return String.format(java.util.Locale.ROOT, "%.2f", Float.valueOf(value));
    }

    private String toHex(int color) {
        return String.format(java.util.Locale.ROOT, "0x%08X", Integer.valueOf(color));
    }

    private static final class ManualAnimationClock implements DocumentAnimationClock {

        private long currentTimeNanos;

        void setCurrentTimeNanos(long currentTimeNanos) {
            this.currentTimeNanos = currentTimeNanos;
        }

        @Override
        public long getCurrentTimeNanos() {
            return currentTimeNanos;
        }
    }
}
