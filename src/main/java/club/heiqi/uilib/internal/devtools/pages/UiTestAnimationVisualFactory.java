package club.heiqi.uilib.internal.devtools.pages;

import java.util.Locale;

import club.heiqi.uilib.ui.animation.DocumentAnimationFillMode;
import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;
import club.heiqi.uilib.ui.animation.DocumentKeyframes;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `/qzuilib test` Animation 分组视觉样例工厂。
 */
final class UiTestAnimationVisualFactory {

    static final String ROLE_ATTRIBUTE = "data-ui-anim-role";

    /**
     * 判断是否支持指定 Animation 样例。
     *
     * @param caseId 样例编号
     * @return 是否支持
     */
    boolean supports(String caseId) {
        return "VIS-ANIM-001".equals(caseId)
                || "VIS-ANIM-002".equals(caseId)
                || "VIS-ANIM-003".equals(caseId)
                || "VIS-ANIM-004".equals(caseId)
                || "VIS-ANIM-005".equals(caseId);
    }

    /**
     * 追加 Animation 样例视觉舞台。
     *
     * @param document 文档实例
     * @param stage 样例舞台
     * @param testCase 样例规格
     */
    void appendCaseDemo(UiDocument document, ElementNode stage, UiTestCaseSpec testCase) {
        ensureKeyframes(document);
        String id = testCase.getId();
        if ("VIS-ANIM-001".equals(id)) {
            appendTransitionDemo(document, stage);
        } else if ("VIS-ANIM-002".equals(id)) {
            appendKeyframesDemo(document, stage);
        } else if ("VIS-ANIM-003".equals(id)) {
            appendTimingDemo(document, stage);
        } else if ("VIS-ANIM-004".equals(id)) {
            appendFillModeDemo(document, stage);
        } else if ("VIS-ANIM-005".equals(id)) {
            appendLayoutVsPaintDemo(document, stage);
        }
    }

    private void ensureKeyframes(UiDocument document) {
        document.registerKeyframes(DocumentKeyframes.named("qzAnimPulse")
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 0.0F, 0xFF1D4ED8)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 0.5F, 0xFFF59E0B)
                .setColorStop(DocumentAnimationProperty.BACKGROUND_COLOR, 1.0F, 0xFF059669)
                .build());
        document.registerKeyframes(DocumentKeyframes.named("qzAnimFill")
                .setFloat(DocumentAnimationProperty.WIDTH, 52.0F, 124.0F)
                .build());
        document.registerKeyframes(DocumentKeyframes.named("qzAnimLayoutGrow")
                .setFloat(DocumentAnimationProperty.WIDTH, 44.0F, 102.0F)
                .build());
        document.registerKeyframes(DocumentKeyframes.named("qzAnimPaintMove")
                .setFloat(DocumentAnimationProperty.TRANSLATE_X, 0.0F, 58.0F)
                .build());
    }

    private void appendTransitionDemo(UiDocument document, ElementNode stage) {
        ElementNode panel = createStack(document);
        ElementNode box = createAnimationBox(document, "transition target", 0xFF1D4ED8);
        box.setAttribute(ROLE_ATTRIBUTE, "transition-target");
        box.style()
                .setWidth(UiStyleLength.px(96))
                .setTransition(DocumentAnimationProperty.BACKGROUND_COLOR, 900L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_OUT);
        panel.append(box).append(createTimeline(document, "0ms", "450ms", "900ms"));
        stage.append(panel);
        appendMutedText(document, stage, "transition：background-color 从蓝到绿，时间轴诊断 start/end。 ");
    }

    private void appendKeyframesDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        ElementNode pulse = createAnimationBox(document, "keyframes pulse", 0xFF1D4ED8);
        pulse.setAttribute(ROLE_ATTRIBUTE, "keyframe-target");
        pulse.style()
                .setWidth(UiStyleLength.px(116))
                .setAnimation("qzAnimPulse", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.LINEAR);
        row.append(pulse);
        row.append(createLegend(document, "0% blue", "50% amber", "100% green"));
        stage.append(row);
        appendMutedText(document, stage, "keyframes：三段 stop 颜色轨道展示 declared keyframes 注册和 animation lifecycle。 ");
    }

    private void appendTimingDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        ElementNode linear = createTimingTrack(document, "timing-linear", "linear", 0xFF2563EB,
                DocumentAnimationTimingFunction.LINEAR);
        ElementNode steps = createTimingTrack(document, "timing-steps", "steps(4,end)", 0xFF7C3AED,
                DocumentAnimationTimingFunction.steps(4));
        row.append(linear).append(steps);
        stage.append(row);
        appendMutedText(document, stage, "timing：linear 连续移动，steps 离散跳变；自动断言读取 timing function。 ");
    }

    private ElementNode createTimingTrack(UiDocument document, String role, String label, int color,
            DocumentAnimationTimingFunction timingFunction) {
        ElementNode track = createTrack(document);
        ElementNode dot = createAnimationBox(document, label, color);
        dot.setAttribute(ROLE_ATTRIBUTE, role);
        dot.style()
                .setWidth(UiStyleLength.px(86))
                .setAnimation("qzAnimPaintMove", 1000L)
                .setAnimationTimingFunction(timingFunction)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS);
        track.append(dot);
        return track;
    }

    private void appendFillModeDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        row.append(createFillModePanel(document, "fill-forwards", "forwards",
                DocumentAnimationFillMode.FORWARDS, 0xFF059669));
        row.append(createFillModePanel(document, "fill-none", "none",
                DocumentAnimationFillMode.NONE, 0xFF475569));
        stage.append(row);
        appendMutedText(document, stage, "fill-mode：forwards 保留末帧运行态，none 回到作者侧基准值。 ");
    }

    private ElementNode createFillModePanel(UiDocument document, String role, String label,
            DocumentAnimationFillMode fillMode, int color) {
        ElementNode panel = createStack(document);
        ElementNode box = createAnimationBox(document, label, color);
        box.setAttribute(ROLE_ATTRIBUTE, role);
        box.style()
                .setWidth(UiStyleLength.px(52))
                .setAnimation("qzAnimFill", 1000L)
                .setAnimationFillMode(fillMode)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.LINEAR);
        panel.append(box).append(createTimeline(document, "base", fillMode.name().toLowerCase(Locale.ROOT), "final"));
        return panel;
    }

    private void appendLayoutVsPaintDemo(UiDocument document, ElementNode stage) {
        ElementNode row = createRow(document);
        ElementNode layoutTrack = createTrack(document);
        ElementNode layout = createAnimationBox(document, "layout width", 0xFFDC2626);
        layout.setAttribute(ROLE_ATTRIBUTE, "layout-target");
        layout.style()
                .setWidth(UiStyleLength.px(44))
                .setAnimation("qzAnimLayoutGrow", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.LINEAR);
        ElementNode layoutSibling = createAnimationBox(document, "sibling moves", 0xFF334155);
        layoutSibling.setAttribute(ROLE_ATTRIBUTE, "layout-sibling");
        layoutSibling.style().setWidth(UiStyleLength.px(86));
        layoutTrack.append(layout).append(layoutSibling);

        ElementNode paintTrack = createTrack(document);
        ElementNode paint = createAnimationBox(document, "paint translate", 0xFF7C3AED);
        paint.setAttribute(ROLE_ATTRIBUTE, "paint-target");
        paint.style()
                .setWidth(UiStyleLength.px(98))
                .setAnimation("qzAnimPaintMove", 1000L)
                .setAnimationFillMode(DocumentAnimationFillMode.FORWARDS)
                .setAnimationTimingFunction(DocumentAnimationTimingFunction.LINEAR);
        ElementNode paintSlot = createAnimationBox(document, "layout slot", 0xFF1E293B);
        paintSlot.setAttribute(ROLE_ATTRIBUTE, "paint-slot");
        paintSlot.style().setWidth(UiStyleLength.px(80));
        paintTrack.append(paint).append(paintSlot);

        row.append(layoutTrack).append(paintTrack);
        stage.append(row);
        appendMutedText(document, stage, "layout-vs-paint：width 推动 sibling 重排，translate 只移动视觉不改变布局槽位。 ");
    }

    private ElementNode createRow(UiDocument document) {
        ElementNode row = document.div();
        row.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(10))
                .setRowGap(UiStyleLength.px(10));
        return row;
    }

    private ElementNode createStack(UiDocument document) {
        ElementNode stack = document.div();
        stack.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(7));
        return stack;
    }

    private ElementNode createTrack(UiDocument document) {
        ElementNode track = document.div();
        track.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setWidth(UiStyleLength.px(232))
                .setMinHeight(UiStyleLength.px(58))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF020617)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF475569)
                .setBorderRadius(UiStyleLength.px(9));
        return track;
    }

    private ElementNode createAnimationBox(UiDocument document, String label, int color) {
        ElementNode box = document.div();
        box.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setHeight(UiStyleLength.px(34))
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF93C5FD)
                .setBorderRadius(UiStyleLength.px(8))
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFFFFFFFF);
        box.appendText(label);
        return box;
    }

    private ElementNode createTimeline(UiDocument document, String left, String middle, String right) {
        ElementNode timeline = createTrack(document);
        timeline.style()
                .setJustifyContent(UiJustifyContent.SPACE_BETWEEN)
                .setMinHeight(UiStyleLength.px(24));
        timeline.append(createTick(document, left));
        timeline.append(createTick(document, middle));
        timeline.append(createTick(document, right));
        return timeline;
    }

    private ElementNode createLegend(UiDocument document, String first, String second, String third) {
        ElementNode legend = createStack(document);
        legend.append(createTick(document, first));
        legend.append(createTick(document, second));
        legend.append(createTick(document, third));
        return legend;
    }

    private ElementNode createTick(UiDocument document, String text) {
        ElementNode tick = document.div();
        tick.style()
                .setPadding(UiStyleLength.px(5))
                .setBackgroundColor(0xFF1E293B)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF334155)
                .setBorderRadius(UiStyleLength.px(6))
                .setTextColor(0xFFEAF1FF);
        tick.appendText(text);
        return tick;
    }

    private void appendMutedText(UiDocument document, ElementNode parent, String text) {
        ElementNode line = document.div();
        line.style().setTextColor(0xFFC9D8F8);
        line.appendText(text);
        parent.append(line);
    }
}
