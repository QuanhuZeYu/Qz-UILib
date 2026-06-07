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
        widget.resolveLayoutBoxForTest();
        DocumentLayoutBox box = resolveBox(widget, target);
        ComputedStyle style = box.getComputedStyle();
        diagnostics.add("transitionProperty=BACKGROUND_COLOR");
        diagnostics.add("transitionDurationNanos=" + style.getTransitionDurationNanos(DocumentAnimationProperty.BACKGROUND_COLOR));
        diagnostics.add("transitionTiming=" + style.getTransitionTimingFunction(DocumentAnimationProperty.BACKGROUND_COLOR));
        diagnostics.add("transitionFinalBg=" + toHex(box.getComputedStyle().getBackgroundColor()));
        diagnostics.add("transitionDiff=expected transition start/end declaration for BACKGROUND_COLOR and final green style");
        return style.canTransition(DocumentAnimationProperty.BACKGROUND_COLOR)
                && style.getTransitionDurationNanos(DocumentAnimationProperty.BACKGROUND_COLOR) == 900_000_000L
                && box.getComputedStyle().getBackgroundColor() == 0xFF059669;
    }

    private boolean assertKeyframes(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode target = findByRole(scope, "keyframe-target");
        DocumentKeyframes keyframes = DocumentKeyframes.named("qzAnimPulseAssert")
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 0.0F, 0xFF1D4ED8)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 0.5F, 0xFFF59E0B)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 1.0F, 0xFF059669)
                .build();
        widget.getDocument().registerKeyframes(keyframes);
        if (target == null || keyframes == null) {
            diagnostics.add("keyframes 节点或定义缺失");
            return false;
        }
        ManualAnimationClock clock = new ManualAnimationClock();
        widget.setAnimationClock(clock);
        target.style()
                .setAnimationName("qzAnimPulseAssert")
                .setAnimationDurationMillis(1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.LINEAR);
        widget.resolveLayoutBoxForTest();
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
        diagnostics.add("keyframeActiveHalf=" + running.getKeyframeCount(DocumentAnimationImpact.PAINT));
        diagnostics.add("keyframeFillEnd=" + finished.getForwardsFillCount(DocumentAnimationImpact.PAINT));
        diagnostics.add("keyframesDiff=expected qzAnimPulse start/end events and forwards fill after three stops");
        return style.getAnimationName() != null && style.getAnimationName().startsWith("qzAnimPulse")
                && stopCount == 3
                && running.getKeyframeCount(DocumentAnimationImpact.PAINT) > 0
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
        widget.getDocument().registerKeyframes(DocumentKeyframes.named("qzAnimTimingAssert")
                .setFloat(DocumentAnimationProperty.TRANSLATE_X, 0.0F, 58.0F)
                .build());
        linear.style().setAnimationName("qzAnimTimingAssert").setAnimationDurationMillis(1000L);
        steps.style().setAnimationName("qzAnimTimingAssert").setAnimationDurationMillis(1000L);
        widget.resolveLayoutBoxForTest();
        clock.setCurrentTimeNanos(500_000_000L);
        widget.resolveLayoutBoxForTest();
        DocumentLayoutBox linearBox = resolveBox(widget, linear);
        DocumentLayoutBox stepsBox = resolveBox(widget, steps);
        ComputedStyle linearStyle = linearBox.getComputedStyle();
        ComputedStyle stepsStyle = stepsBox.getComputedStyle();
        float linearHalf = linearStyle.getAnimationTimingFunction().apply(0.5F);
        float stepsHalf = stepsStyle.getAnimationTimingFunction().apply(0.5F);
        DocumentAnimationTimeline.DiagnosticsSnapshot running = widget.getAnimationDiagnosticsSnapshot();
        diagnostics.add("timingLinear=" + linearStyle.getAnimationTimingFunction());
        diagnostics.add("timingSteps=" + stepsStyle.getAnimationTimingFunction());
        diagnostics.add("timingProgressHalf=linear:" + formatFloat(linearHalf) + ",steps:" + formatFloat(stepsHalf));
        diagnostics.add("timingActivePaintKeyframes=" + running.getKeyframeCount(DocumentAnimationImpact.PAINT));
        diagnostics.add("timingDiff=expected linear and steps(4,end) timing functions both drive paint keyframes");
        return "linear".equals(linearStyle.getAnimationTimingFunction().toString())
                && stepsStyle.getAnimationTimingFunction().toString().contains("steps(4")
                && linearHalf == 0.5F
                && stepsHalf == 0.5F
                && running.getKeyframeCount(DocumentAnimationImpact.PAINT) >= 2;
    }

    private boolean assertFillMode(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode forwards = findByRole(scope, "fill-forwards");
        ElementNode none = findByRole(scope, "fill-none");
        if (forwards == null || none == null) {
            diagnostics.add("fill-mode 节点缺失");
            return false;
        }
        widget.getDocument().registerKeyframes(DocumentKeyframes.named("qzAnimFillAssert")
                .setFloat(DocumentAnimationProperty.WIDTH, 52.0F, 124.0F)
                .build());
        forwards.style().setAnimationName("qzAnimFillAssert").setAnimationDurationMillis(1000L);
        none.style().setAnimationName("qzAnimFillAssert").setAnimationDurationMillis(1000L);
        widget.resolveLayoutBoxForTest();
        DocumentLayoutBox forwardsBox = resolveBox(widget, forwards);
        DocumentLayoutBox noneBox = resolveBox(widget, none);
        DocumentKeyframes keyframes = widget.getDocument().getKeyframes("qzAnimFillAssert");
        DocumentKeyframes.FloatTrack track = keyframes.getFloatTracks().get(DocumentAnimationProperty.WIDTH);
        diagnostics.add("fillForwardsMode=" + forwardsBox.getComputedStyle().getAnimationFillMode());
        diagnostics.add("fillNoneMode=" + noneBox.getComputedStyle().getAnimationFillMode());
        diagnostics.add("fillTrack=from:" + formatFloat(track.getFirstValue()) + ",to:" + formatFloat(track.getLastValue()));
        diagnostics.add("fillModeDiff=expected forwards keeps wider runtime layout and none returns base width");
        return forwardsBox.getComputedStyle().getAnimationFillMode() == DocumentAnimationFillMode.FORWARDS
                && noneBox.getComputedStyle().getAnimationFillMode() == DocumentAnimationFillMode.NONE
                && track.getLastValue() > track.getFirstValue();
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
