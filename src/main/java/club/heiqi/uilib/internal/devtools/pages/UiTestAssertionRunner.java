package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutEdges;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiBackgroundImage;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * `/qzuilib test` 首批运行时断言执行器。
 */
final class UiTestAssertionRunner {

    private final UiTestDomAssertionRunner domAssertionRunner = new UiTestDomAssertionRunner();
    private final UiTestInputAssertionRunner inputAssertionRunner = new UiTestInputAssertionRunner();
    private final UiTestControlsAssertionRunner controlsAssertionRunner = new UiTestControlsAssertionRunner();
    private final UiTestTextAssertionRunner textAssertionRunner = new UiTestTextAssertionRunner();
    private final UiTestAnimationAssertionRunner animationAssertionRunner = new UiTestAnimationAssertionRunner();

    /**
     * 运行指定样例的首批断言。
     *
     * @param documentWidget 文档组件
     * @param testCase 样例规格
     * @param logger 断言日志记录器
     * @param assertionContext 断言运行上下文
     * @return 断言结果
     */
    UiTestCaseResult run(HtmlLikeDocumentWidget documentWidget, UiTestCaseSpec testCase, UiTestAssertionLogger logger,
            String assertionContext) {
        long now = System.currentTimeMillis();
        logger.log(new UiTestAssertionLogEntry(testCase.getId(), testCase.getGroupCode(), "start",
                "开始运行样例断言", testCase.getSemanticAssertion(), "准备解析舞台", "", now,
                assertionContext));
        try {
            if (documentWidget == null || documentWidget.getDocument() == null) {
                return buildFailure(testCase, logger, "document", "文档未初始化", "文档已构建", "document=null");
            }
            ElementNode root = documentWidget.getDocument().getRootElement();
            ElementNode scope = findElementByAttribute(root, "data-ui-test-case", testCase.getId());
            if (scope == null) {
                return buildFailure(testCase, logger, "scope", "未找到样例舞台", testCase.getId(), "null");
            }
            DocumentLayoutBox rootBox = documentWidget.resolveLayoutBoxForTest();
            if (rootBox == null) {
                return buildFailure(testCase, logger, "layout", "布局盒不可用", "非空布局盒", "null");
            }
            DocumentLayoutBox scopeBox = findLayoutBox(rootBox, scope);
            if (scopeBox == null) {
                return buildFailure(testCase, logger, "layout", "未找到样例布局盒", testCase.getId(), "null");
            }

            List<String> diagnostics = new ArrayList<String>();
            diagnostics.add("caseContext=" + testCase.getGroupCode() + "/" + testCase.getId());
            diagnostics.add("stageBox=" + summarizeLayoutBox(scopeBox));
            diagnostics.add("stageStyle=" + summarizeComputedStyle(scopeBox.getComputedStyle()));
            boolean passed;
            if (domAssertionRunner.isAutomatic(testCase.getId())) {
                passed = domAssertionRunner.runAutomatic(documentWidget, scope, testCase, diagnostics);
            } else if ("VIS-CSS-001".equals(testCase.getId())) {
                passed = assertCssSpecificity(documentWidget, scope, diagnostics);
            } else if ("VIS-CSS-002".equals(testCase.getId())) {
                passed = assertCssBoxSizing(documentWidget, scope, diagnostics);
            } else if ("VIS-CSS-003".equals(testCase.getId())) {
                passed = assertCssVisibility(documentWidget, scope, diagnostics);
            } else if ("VIS-CSS-004".equals(testCase.getId())) {
                passed = assertCssInheritance(documentWidget, scope, diagnostics);
            } else if ("VIS-CSS-005".equals(testCase.getId())) {
                passed = assertCssBackground(documentWidget, scope, diagnostics);
            } else if ("VIS-CSS-006".equals(testCase.getId())) {
                passed = assertCssOverflow(documentWidget, scope, diagnostics);
            } else if ("VIS-LAYOUT-001".equals(testCase.getId())) {
                passed = assertLayoutBlockFlow(documentWidget, scope, diagnostics);
            } else if ("VIS-LAYOUT-002".equals(testCase.getId())) {
                passed = assertLayoutFlex(documentWidget, scope, diagnostics);
            } else if ("VIS-LAYOUT-003".equals(testCase.getId())) {
                passed = assertLayoutTable(documentWidget, scope, diagnostics);
            } else if ("VIS-LAYOUT-004".equals(testCase.getId())) {
                passed = assertLayoutInline(documentWidget, scope, diagnostics);
            } else if ("VIS-LAYOUT-006".equals(testCase.getId())) {
                passed = assertLayoutFixedSticky(documentWidget, scope, diagnostics);
            } else if ("VIS-PAINT-001".equals(testCase.getId())) {
                passed = assertPaintStacking(documentWidget, scope, diagnostics);
            } else if ("VIS-PAINT-002".equals(testCase.getId())) {
                passed = assertPaintClip(documentWidget, scope, diagnostics);
            } else if ("VIS-PAINT-005".equals(testCase.getId())) {
                passed = assertPaintTopLayer(documentWidget, scope, diagnostics);
            } else if (inputAssertionRunner.isAutomatic(testCase.getId())) {
                passed = inputAssertionRunner.runAutomatic(documentWidget, scope, testCase, diagnostics);
            } else if (controlsAssertionRunner.isAutomatic(testCase.getId())) {
                passed = controlsAssertionRunner.runAutomatic(documentWidget, scope, testCase, diagnostics);
            } else if (textAssertionRunner.isAutomatic(testCase.getId())) {
                passed = textAssertionRunner.runAutomatic(documentWidget, scope, testCase, diagnostics);
            } else if (animationAssertionRunner.isAutomatic(testCase.getId())) {
                passed = animationAssertionRunner.runAutomatic(documentWidget, scope, testCase, diagnostics);
            } else {
                if ("VIS-PAINT-003".equals(testCase.getId())) {
                    diagnosePaintTransform(documentWidget, scope, diagnostics);
                } else if ("VIS-LAYOUT-005".equals(testCase.getId())) {
                    diagnoseLayoutInlineBlockBaseline(documentWidget, scope, diagnostics);
                } else if ("VIS-PAINT-004".equals(testCase.getId())) {
                    diagnosePaintTransformHit(documentWidget, scope, diagnostics);
                } else if ("VIS-PAINT-006".equals(testCase.getId())) {
                    diagnosePaintScrollbar(documentWidget, scope, diagnostics);
                } else if ("VIS-PAINT-007".equals(testCase.getId())) {
                    diagnosePaintHostImage(documentWidget, scope, diagnostics);
                } else if (inputAssertionRunner.isManual(testCase.getId())) {
                    inputAssertionRunner.diagnoseManual(documentWidget, scope, testCase, diagnostics);
                } else if (controlsAssertionRunner.isManual(testCase.getId())) {
                    controlsAssertionRunner.diagnoseManual(documentWidget, scope, testCase, diagnostics);
                } else if (textAssertionRunner.isManual(testCase.getId())) {
                    textAssertionRunner.diagnoseManual(documentWidget, scope, testCase, diagnostics);
                } else if (animationAssertionRunner.isManual(testCase.getId())) {
                    animationAssertionRunner.diagnoseManual(documentWidget, scope, testCase, diagnostics);
                } else {
                    diagnostics.add("当前样例未接入自动断言，保持人工待确认。");
                }
                logger.log(new UiTestAssertionLogEntry(testCase.getId(), testCase.getGroupCode(), "skip",
                        "当前样例未接入自动断言", testCase.getSemanticAssertion(), join(diagnostics), "", now));
                return new UiTestCaseResult(UiTestVisualStatus.DISPLAYING, UiTestSemanticStatus.MANUAL_PENDING,
                        UiTestSummaryStatus.PENDING, join(diagnostics), "");
            }

            String actual = join(diagnostics);
            if (passed) {
                logger.log(new UiTestAssertionLogEntry(testCase.getId(), testCase.getGroupCode(), "pass",
                        "自动断言通过", testCase.getSemanticAssertion(), actual, "", System.currentTimeMillis()));
                return new UiTestCaseResult(UiTestVisualStatus.DISPLAYING, UiTestSemanticStatus.AUTO_PASSED,
                        UiTestSummaryStatus.PARTIAL_PASSED, actual, "");
            }
            logger.log(new UiTestAssertionLogEntry(testCase.getId(), testCase.getGroupCode(), "fail",
                    "自动断言失败", testCase.getSemanticAssertion(), actual, actual, System.currentTimeMillis()));
            return new UiTestCaseResult(UiTestVisualStatus.DISPLAYING, UiTestSemanticStatus.AUTO_FAILED,
                    UiTestSummaryStatus.FAILED, actual, actual);
        } catch (RuntimeException exception) {
            logger.log(new UiTestAssertionLogEntry(testCase.getId(), testCase.getGroupCode(), "exception",
                    "自动断言异常", testCase.getSemanticAssertion(), exception.getClass().getSimpleName() + ":"
                            + exception.getMessage(), "异常中断", System.currentTimeMillis()));
            return new UiTestCaseResult(UiTestVisualStatus.DISPLAYING, UiTestSemanticStatus.AUTO_FAILED,
                    UiTestSummaryStatus.FAILED, exception.getClass().getSimpleName() + ": " + exception.getMessage(),
                    "断言执行抛出异常");
        }
    }

    private UiTestCaseResult buildFailure(UiTestCaseSpec testCase, UiTestAssertionLogger logger, String phase,
            String message, String expected, String actual) {
        logger.log(new UiTestAssertionLogEntry(testCase.getId(), testCase.getGroupCode(), phase, message, expected,
                actual, expected + " != " + actual, System.currentTimeMillis()));
        return new UiTestCaseResult(UiTestVisualStatus.DISPLAYING, UiTestSemanticStatus.AUTO_FAILED,
                UiTestSummaryStatus.FAILED, message + "；actual=" + actual, expected + " != " + actual);
    }

    private boolean assertCssSpecificity(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode sample = findByTag(scope, "sample");
        ElementNode classNode = findByClass(scope, "specificity-class");
        ElementNode idNode = findById(scope, "specificity-id");
        if (sample == null || classNode == null || idNode == null) {
            diagnostics.add("specificity 节点缺失");
            return false;
        }
        DocumentLayoutBox sampleBox = resolveBox(widget, sample);
        DocumentLayoutBox classBox = resolveBox(widget, classNode);
        DocumentLayoutBox idBox = resolveBox(widget, idNode);
        ComputedStyle sampleStyle = sampleBox.getComputedStyle();
        ComputedStyle classStyle = classBox.getComputedStyle();
        ComputedStyle idStyle = idBox.getComputedStyle();
        diagnostics.add("sampleBg=" + toHex(sampleStyle.getBackgroundColor()));
        diagnostics.add("classBg=" + toHex(classStyle.getBackgroundColor()));
        diagnostics.add("idBg=" + toHex(idStyle.getBackgroundColor()));
        appendElementDiagnostics(diagnostics, "sample", sampleBox);
        appendElementDiagnostics(diagnostics, "class", classBox);
        appendElementDiagnostics(diagnostics, "id", idBox);
        diagnostics.add("specificityDiff=expected sample=0xFF334155,class=0xFF2563EB,id=0xFF059669; actual sample="
                + toHex(sampleStyle.getBackgroundColor()) + ",class=" + toHex(classStyle.getBackgroundColor())
                + ",id=" + toHex(idStyle.getBackgroundColor()));
        return sampleStyle.getBackgroundColor() == 0xFF334155
                && classStyle.getBackgroundColor() == 0xFF2563EB
                && idStyle.getBackgroundColor() == 0xFF059669;
    }

    private boolean assertCssBoxSizing(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode contentBox = findByText(scope, "content-box");
        ElementNode borderBox = findByText(scope, "border-box");
        if (contentBox == null || borderBox == null) {
            diagnostics.add("box-sizing 节点缺失");
            return false;
        }
        DocumentLayoutBox contentBoxLayout = resolveBox(widget, contentBox);
        DocumentLayoutBox borderBoxLayout = resolveBox(widget, borderBox);
        diagnostics.add("contentBoxWidth=" + contentBoxLayout.getWidth());
        diagnostics.add("borderBoxWidth=" + borderBoxLayout.getWidth());
        appendElementDiagnostics(diagnostics, "contentBox", contentBoxLayout);
        appendElementDiagnostics(diagnostics, "borderBox", borderBoxLayout);
        diagnostics.add("boxSizingDiff=contentBoxWidth-borderBoxWidth="
                + (contentBoxLayout.getWidth() - borderBoxLayout.getWidth())
                + "; expected content-box visual width > border-box visual width");
        return contentBoxLayout.getWidth() > borderBoxLayout.getWidth();
    }

    private boolean assertCssVisibility(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode hidden = findByText(scope, "hidden 占位");
        ElementNode pointerNone = findByText(scope, "pointer-events:none");
        if (hidden == null || pointerNone == null) {
            diagnostics.add("visibility 节点缺失");
            return false;
        }
        DocumentLayoutBox hiddenBox = resolveBox(widget, hidden);
        DocumentLayoutBox pointerBox = resolveBox(widget, pointerNone);
        ComputedStyle hiddenStyle = hiddenBox.getComputedStyle();
        ComputedStyle pointerStyle = pointerBox.getComputedStyle();
        diagnostics.add("hiddenVisibility=" + hiddenStyle.getVisibility());
        diagnostics.add("pointerEvents=" + pointerStyle.getPointerEvents());
        appendElementDiagnostics(diagnostics, "hidden", hiddenBox);
        appendElementDiagnostics(diagnostics, "pointerNone", pointerBox);
        diagnostics.add("visibilityDiff=expected hidden=HIDDEN,pointerEvents=NONE; actual hidden="
                + hiddenStyle.getVisibility() + ",pointerEvents=" + pointerStyle.getPointerEvents());
        return hiddenStyle.getVisibility().name().equals("HIDDEN")
                && pointerStyle.getPointerEvents().name().equals("NONE");
    }

    private boolean assertLayoutBlockFlow(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode first = findByText(scope, "Block A margin-bottom=18");
        ElementNode second = findByText(scope, "Block B margin-top=24");
        if (first == null || second == null) {
            diagnostics.add("block flow 节点缺失");
            return false;
        }
        DocumentLayoutBox firstBox = resolveBox(widget, first);
        DocumentLayoutBox secondBox = resolveBox(widget, second);
        int collapsedGap = secondBox.getTop() - firstBox.getBottom();
        int rawGap = firstBox.getMargin().getBottom() + secondBox.getMargin().getTop();
        diagnostics.add("firstTop=" + firstBox.getTop());
        diagnostics.add("secondTop=" + secondBox.getTop());
        diagnostics.add("collapsedGap=" + collapsedGap + ", rawMarginSum=" + rawGap);
        appendElementDiagnostics(diagnostics, "firstBlock", firstBox);
        appendElementDiagnostics(diagnostics, "secondBlock", secondBox);
        diagnostics.add("blockFlowDiff=secondTop-firstBottom=" + collapsedGap
                + "; expected adjacent vertical margins collapse to max(18,24)=24, not raw sum 42");
        return secondBox.getTop() > firstBox.getTop()
                && collapsedGap >= 20 && collapsedGap <= 28 && collapsedGap < rawGap;
    }

    private boolean assertLayoutFlex(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode minContent = findByText(scope, "min-content item keeps long label");
        ElementNode minWidthZero = findByText(scope, "min-width:0 item");
        if (minContent == null || minWidthZero == null) {
            diagnostics.add("flex 节点缺失");
            return false;
        }
        DocumentLayoutBox minContentBox = resolveBox(widget, minContent);
        DocumentLayoutBox minWidthZeroBox = resolveBox(widget, minWidthZero);
        diagnostics.add("minContentWidth=" + minContentBox.getWidth());
        diagnostics.add("minWidthZeroWidth=" + minWidthZeroBox.getWidth());
        appendElementDiagnostics(diagnostics, "minContent", minContentBox);
        appendElementDiagnostics(diagnostics, "minWidthZero", minWidthZeroBox);
        diagnostics.add("flexDiff=minContentWidth-minWidthZeroWidth="
                + (minContentBox.getWidth() - minWidthZeroBox.getWidth())
                + "; expected min-content item >= min-width:0 item");
        return minContentBox.getWidth() >= minWidthZeroBox.getWidth();
    }

    private boolean assertLayoutTable(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode shortCell = findByText(scope, "A");
        ElementNode longCell = findByText(scope, "content driven width");
        if (shortCell == null || longCell == null) {
            diagnostics.add("table 节点缺失");
            return false;
        }
        DocumentLayoutBox shortBox = resolveBox(widget, shortCell);
        DocumentLayoutBox longBox = resolveBox(widget, longCell);
        diagnostics.add("shortCellWidth=" + shortBox.getWidth());
        diagnostics.add("longCellWidth=" + longBox.getWidth());
        appendElementDiagnostics(diagnostics, "shortCell", shortBox);
        appendElementDiagnostics(diagnostics, "longCell", longBox);
        diagnostics.add("tableDiff=longCellWidth-shortCellWidth=" + (longBox.getWidth() - shortBox.getWidth())
                + "; expected content-driven column >= short column");
        return longBox.getWidth() >= shortBox.getWidth();
    }

    private boolean assertPaintStacking(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode red = findByText(scope, "red z=1");
        ElementNode blue = findByText(scope, "blue opacity=.72 z=2");
        ElementNode green = findByText(scope, "green z=3");
        if (red == null || blue == null || green == null) {
            diagnostics.add("stacking 节点缺失");
            return false;
        }
        DocumentLayoutBox redBox = resolveBox(widget, red);
        DocumentLayoutBox blueBox = resolveBox(widget, blue);
        DocumentLayoutBox greenBox = resolveBox(widget, green);
        diagnostics.add("redZ=" + redBox.getComputedStyle().getZIndex());
        diagnostics.add("blueOpacity=" + blueBox.getComputedStyle().getOpacity());
        diagnostics.add("greenZ=" + greenBox.getComputedStyle().getZIndex());
        appendElementDiagnostics(diagnostics, "redLayer", redBox);
        appendElementDiagnostics(diagnostics, "blueLayer", blueBox);
        appendElementDiagnostics(diagnostics, "greenLayer", greenBox);
        diagnostics.add("stackingDiff=red phase=" + redBox.getStackingPhase() + "/zSort="
                + redBox.getStackingZIndex() + ", blue phase=" + blueBox.getStackingPhase() + "/zSort="
                + blueBox.getStackingZIndex() + ", green phase=" + greenBox.getStackingPhase() + "/zSort="
                + greenBox.getStackingZIndex() + "; expected green z-index above red");
        Integer redZ = redBox.getComputedStyle().getZIndex();
        Integer greenZ = greenBox.getComputedStyle().getZIndex();
        return redZ != null && greenZ != null && greenZ.intValue() > redZ.intValue();
    }

    private boolean assertPaintClip(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode clipBoundary = findByText(scope, "clip boundary");
        ElementNode bar = findByText(scope, "wide child clipped by parent");
        if (clipBoundary == null || bar == null) {
            diagnostics.add("clip 节点缺失");
            return false;
        }
        DocumentLayoutBox clipBox = resolveBox(widget, clipBoundary.getParent() instanceof ElementNode
                ? (ElementNode) clipBoundary.getParent() : clipBoundary);
        DocumentLayoutBox barBox = resolveBox(widget, bar);
        diagnostics.add("clipWidth=" + clipBox.getWidth());
        diagnostics.add("barWidth=" + barBox.getWidth());
        appendElementDiagnostics(diagnostics, "clipBoundary", clipBox);
        appendElementDiagnostics(diagnostics, "clipChild", barBox);
        diagnostics.add("clipDiff=childRight-parentRight=" + (barBox.getRight() - clipBox.getRight())
                + "; parentOverflow=" + clipBox.getComputedStyle().getOverflowX() + "/"
                + clipBox.getComputedStyle().getOverflowY()
                + "; expected child overflows but is clipped by parent padding box");
        return barBox.getWidth() > clipBox.getWidth();
    }

    private boolean assertCssInheritance(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode parent = findByText(scope, "继承 color (应为蓝色)");
        if (parent == null) {
            parent = scope; // fallback search parent container
        }
        ElementNode childColor = findByText(scope, "继承 color (应为蓝色)");
        ElementNode childWidth = findByText(scope, "width=80px (非继承)");
        if (childColor == null || childWidth == null) {
            diagnostics.add("inheritance 节点缺失");
            return false;
        }
        DocumentLayoutBox colorBox = resolveBox(widget, childColor);
        DocumentLayoutBox widthBox = resolveBox(widget, childWidth);
        ComputedStyle colorStyle = colorBox.getComputedStyle();
        diagnostics.add("inheritedColor=" + toHex(colorStyle.getTextColor()));
        diagnostics.add("childWidth=" + widthBox.getWidth() + ", childContentWidth=" + widthBox.getContentWidth());
        appendElementDiagnostics(diagnostics, "colorChild", colorBox);
        appendElementDiagnostics(diagnostics, "widthChild", widthBox);
        diagnostics.add("inheritanceDiff=expected color inherit 0xFF38BDF8, width independent ~80; actual color="
                + toHex(colorStyle.getTextColor()) + ", contentWidth=" + widthBox.getContentWidth());
        return colorStyle.getTextColor() == 0xFF38BDF8 && widthBox.getContentWidth() <= 90;
    }

    private boolean assertCssBackground(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode urlPanel = findByText(scope, "background + url");
        ElementNode nonePanel = findByText(scope, "background:none 效果");
        if (urlPanel == null || nonePanel == null) {
            diagnostics.add("background 节点缺失");
            return false;
        }
        DocumentLayoutBox urlBox = resolveBox(widget, urlPanel);
        DocumentLayoutBox noneBox = resolveBox(widget, nonePanel);
        ComputedStyle urlStyle = urlBox.getComputedStyle();
        ComputedStyle noneStyle = noneBox.getComputedStyle();
        UiBackgroundImage urlImg = urlStyle.getBackgroundImage();
        UiBackgroundImage noneImg = noneStyle.getBackgroundImage();
        diagnostics.add("urlBg=" + toHex(urlStyle.getBackgroundColor()) + ", hasImage=" + (urlImg != null));
        diagnostics.add("noneBg=" + toHex(noneStyle.getBackgroundColor()) + ", hasImage=" + (noneImg != null));
        diagnostics.add("urlSource=" + formatBackgroundSource(urlImg));
        appendElementDiagnostics(diagnostics, "urlPanel", urlBox);
        appendElementDiagnostics(diagnostics, "nonePanel", noneBox);
        diagnostics.add("backgroundDiff=expected url hasImage, none has no image; actual urlImg=" + (urlImg != null)
                + ", noneImg=" + (noneImg != null));
        return urlImg != null && urlImg.getSource().getTexture() != null
                && "minecraft:textures/gui/options_background.png".equals(urlImg.getSource().getTexture().toString())
                && noneImg == null;
    }

    private boolean assertCssOverflow(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode hidden = findByText(scope, "hidden 裁剪");
        ElementNode autoBox = findByText(scope, "宽内容触发滚动");
        ElementNode visible = findByText(scope, "visible 越界可见");
        if (hidden == null || autoBox == null || visible == null) {
            diagnostics.add("overflow 节点缺失");
            return false;
        }
        ElementNode hiddenContainer = hidden.getParent() instanceof ElementNode ? (ElementNode) hidden.getParent()
                : hidden;
        ElementNode autoContainer = autoBox.getParent() instanceof ElementNode ? (ElementNode) autoBox.getParent()
                : autoBox;
        ElementNode visibleContainer = visible.getParent() instanceof ElementNode ? (ElementNode) visible.getParent()
                : visible;
        DocumentLayoutBox hiddenBox = resolveBox(widget, hiddenContainer);
        DocumentLayoutBox hiddenChildBox = resolveBox(widget, hidden);
        DocumentLayoutBox autoContainerBox = resolveBox(widget, autoContainer);
        DocumentLayoutBox autoChildBox = resolveBox(widget, autoBox);
        DocumentLayoutBox visibleBox = resolveBox(widget, visibleContainer);
        DocumentLayoutBox visibleChildBox = resolveBox(widget, visible);
        ComputedStyle hiddenStyle = hiddenBox.getComputedStyle();
        ComputedStyle autoStyle = autoContainerBox.getComputedStyle();
        ComputedStyle visibleStyle = visibleBox.getComputedStyle();
        diagnostics.add("hiddenOverflow=" + hiddenStyle.getOverflowX() + "/" + hiddenStyle.getOverflowY());
        diagnostics.add("autoOverflow=" + autoStyle.getOverflowX() + "/" + autoStyle.getOverflowY());
        diagnostics.add("visibleOverflow=" + visibleStyle.getOverflowX() + "/" + visibleStyle.getOverflowY());
        diagnostics.add("hiddenChildWidth=" + hiddenChildBox.getWidth() + ", hiddenWidth=" + hiddenBox.getWidth());
        diagnostics.add("autoChildWidth=" + autoChildBox.getWidth() + ", autoWidth=" + autoContainerBox.getWidth());
        diagnostics.add("visibleChildWidth=" + visibleChildBox.getWidth() + ", visibleWidth=" + visibleBox.getWidth());
        appendElementDiagnostics(diagnostics, "hiddenContainer", hiddenBox);
        appendElementDiagnostics(diagnostics, "hiddenChild", hiddenChildBox);
        appendElementDiagnostics(diagnostics, "autoContainer", autoContainerBox);
        appendElementDiagnostics(diagnostics, "autoChild", autoChildBox);
        appendElementDiagnostics(diagnostics, "visibleContainer", visibleBox);
        appendElementDiagnostics(diagnostics, "visibleChild", visibleChildBox);
        diagnostics.add("overflowDiff=expected hidden clip, auto scrollable, visible overflow; actual hiddenChild>hidden="
                + (hiddenChildBox.getWidth() > hiddenBox.getWidth()) + ", autoChild>auto="
                + (autoChildBox.getWidth() > autoContainerBox.getWidth()) + ", visibleChild>visible="
                + (visibleChildBox.getWidth() > visibleBox.getWidth()));
        return hiddenStyle.getOverflowX() == UiOverflow.HIDDEN
                && autoStyle.getOverflowX() == UiOverflow.AUTO
                && visibleStyle.getOverflowX() == UiOverflow.VISIBLE
                && hiddenChildBox.getWidth() > hiddenBox.getWidth()
                && autoChildBox.getWidth() > autoContainerBox.getWidth()
                && visibleChildBox.getWidth() > visibleBox.getWidth();
    }

    private boolean assertLayoutInline(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode ib = findByText(scope, "inline-block");
        if (ib == null) {
            diagnostics.add("inline 节点缺失");
            return false;
        }
        DocumentLayoutBox ibBox = resolveBox(widget, ib);
        ComputedStyle ibStyle = ibBox.getComputedStyle();
        diagnostics.add("inlineBlockDisplay=" + ibStyle.getDisplay());
        diagnostics.add("inlineBlockWidth=" + ibBox.getWidth());
        appendElementDiagnostics(diagnostics, "inlineBlock", ibBox);
        diagnostics.add("inlineDiff=expected display=INLINE_BLOCK, width~72; actual=" + ibStyle.getDisplay());
        return ibStyle.getDisplay() == UiDisplay.INLINE_BLOCK && ibBox.getWidth() >= 60;
    }

    private boolean assertLayoutFixedSticky(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode sticky = findByText(scope, "sticky 头");
        ElementNode fixed = findByText(scope, "fixed 按钮");
        if (sticky == null || fixed == null) {
            diagnostics.add("fixed/sticky 节点缺失");
            return false;
        }
        DocumentLayoutBox stickyBox = resolveBox(widget, sticky);
        DocumentLayoutBox fixedBox = resolveBox(widget, fixed);
        ComputedStyle stickyStyle = stickyBox.getComputedStyle();
        ComputedStyle fixedStyle = fixedBox.getComputedStyle();
        diagnostics.add("stickyPos=" + stickyStyle.getPosition() + ", top=" + formatLength(stickyStyle.getTop()));
        diagnostics.add("fixedPos=" + fixedStyle.getPosition());
        appendElementDiagnostics(diagnostics, "sticky", stickyBox);
        appendElementDiagnostics(diagnostics, "fixed", fixedBox);
        diagnostics.add("fixedStickyDiff=expected sticky=STICKY, fixed=FIXED; actual sticky=" + stickyStyle.getPosition()
                + ", fixed=" + fixedStyle.getPosition());
        return stickyStyle.getPosition() == UiPosition.STICKY && fixedStyle.getPosition() == UiPosition.FIXED;
    }

    private boolean assertPaintTopLayer(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode top = findByText(scope, "top-layer 弹层");
        if (top == null) {
            diagnostics.add("top-layer 节点缺失");
            return false;
        }
        boolean registered = widget.getDocument().__isTopLayerElement(top);
        diagnostics.add("topLayerElement=" + describeElement(top));
        diagnostics.add("topLayerRegistered=" + registered);
        diagnostics.add("topLayerDiff=expected document top-layer registration true; actual=" + registered);
        return registered;
    }

    private void diagnosePaintScrollbar(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode scroller = findByText(scope, "scroll item 0");
        if (scroller == null) {
            diagnostics.add("scrollbar 节点缺失，保持人工待确认。");
            return;
        }
        ElementNode scrollContainer = scroller.getParent() instanceof ElementNode ? (ElementNode) scroller.getParent()
                : scroller;
        DocumentLayoutBox scrollBox = resolveBox(widget, scrollContainer);
        ComputedStyle s = scrollBox.getComputedStyle();
        diagnostics.add("scrollOverflow=" + s.getOverflowY());
        diagnostics.add("scrollTop=" + widget.getScrollTop(scrollContainer)
                + ", maxScrollTop=" + widget.getMaxScrollTop(scrollContainer));
        appendElementDiagnostics(diagnostics, "scrollContainer", scrollBox);
        diagnostics.add("scrollbarDiff=overflow 与 scroll range 可机器诊断；track/thumb 几何、拖拽和命中需截图人工确认。");
    }

    private void diagnosePaintHostImage(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode ok = findByText(scope, "有效 host image");
        ElementNode fb = findByText(scope, "缺失 fallback");
        if (ok == null || fb == null) {
            diagnostics.add("host image 节点缺失");
            return;
        }
        DocumentLayoutBox okBox = resolveBox(widget, ok);
        DocumentLayoutBox fbBox = resolveBox(widget, fb);
        UiBackgroundImage okImg = okBox.getComputedStyle().getBackgroundImage();
        UiBackgroundImage fbImg = fbBox.getComputedStyle().getBackgroundImage();
        diagnostics.add("okHasImage=" + (okImg != null) + ", fbHasImage=" + (fbImg != null));
        diagnostics.add("okSource=" + formatBackgroundSource(okImg) + ", fbSource=" + formatBackgroundSource(fbImg));
        appendElementDiagnostics(diagnostics, "okImage", okBox);
        appendElementDiagnostics(diagnostics, "fbImage", fbBox);
        diagnostics.add("hostImageDiff=背景图声明可机器诊断；缺失资源应保留底色，不使用 Minecraft 默认紫黑 missing texture。");
    }

    /**
     * 为人工确认的 transform 样例补充布局盒与 transform 摘要。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param diagnostics 诊断摘要列表
     */
    private void diagnosePaintTransform(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode placeholder = findByText(scope, "layout box");
        ElementNode transformed = findByText(scope, "rotate(12deg) translate(28,8)");
        if (placeholder == null || transformed == null) {
            diagnostics.add("transform 节点缺失，保持人工待确认。");
            return;
        }
        DocumentLayoutBox placeholderBox = resolveBox(widget, placeholder);
        DocumentLayoutBox transformedBox = resolveBox(widget, transformed);
        UiTransform transform = transformedBox.getComputedStyle().getTransform();
        appendElementDiagnostics(diagnostics, "layoutPlaceholder", placeholderBox);
        appendElementDiagnostics(diagnostics, "transformedLayer", transformedBox);
        diagnostics.add("transformDiff=layoutLeftDelta=" + (transformedBox.getLeft() - placeholderBox.getLeft())
                + ", layoutTopDelta=" + (transformedBox.getTop() - placeholderBox.getTop())
                + "; visualTransform=" + formatTransform(transform)
                + "; expected layout box unchanged, paint/hit uses transformed visual quad");
        diagnostics.add("当前样例仍需人工确认旋转卡片与原始占位框的视觉相对位置。");
    }

    /**
     * 为 inline-block baseline 人工样例补充布局摘要。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param diagnostics 诊断摘要列表
     */
    private void diagnoseLayoutInlineBlockBaseline(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode ib = findByText(scope, "ib baseline tall");
        if (ib == null) {
            ib = findByText(scope, "inline-block");
        }
        if (ib == null) {
            diagnostics.add("inline-block baseline 节点缺失，保持人工待确认。");
            return;
        }
        DocumentLayoutBox ibBox = resolveBox(widget, ib);
        appendElementDiagnostics(diagnostics, "inlineBlockBaseline", ibBox);
        diagnostics.add("baselineDiff=ibHeight=" + ibBox.getHeight()
                + "; expected baseline alignment with sibling text (manual visual confirm)");
        diagnostics.add("当前样例需人工确认 inline-block 基线与相邻文本对齐。");
    }

    /**
     * 为 transform 命中人工样例补充诊断。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param diagnostics 诊断摘要列表
     */
    private void diagnosePaintTransformHit(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode placeholder = findByText(scope, "layout占位");
        ElementNode hit = findByText(scope, "transform 命中区");
        if (placeholder == null || hit == null) {
            diagnostics.add("transform hit 节点缺失，保持人工待确认。");
            return;
        }
        DocumentLayoutBox phBox = resolveBox(widget, placeholder);
        DocumentLayoutBox hitBox = resolveBox(widget, hit);
        UiTransform tr = hitBox.getComputedStyle().getTransform();
        appendElementDiagnostics(diagnostics, "transformHitPlaceholder", phBox);
        appendElementDiagnostics(diagnostics, "transformHitLayer", hitBox);
        diagnostics.add("transformHitDiff=layoutLeftDelta=" + (hitBox.getLeft() - phBox.getLeft())
                + ", layoutTopDelta=" + (hitBox.getTop() - phBox.getTop())
                + "; visualTransform=" + formatTransform(tr)
                + "; expected layout unchanged, hit uses transformed area");
        diagnostics.add("当前样例仍需人工确认点击变换后区域是否命中。");
    }

    /**
     * 追加单个元素的稳定诊断摘要。
     *
     * @param diagnostics 诊断摘要列表
     * @param label 摘要标签
     * @param box 布局盒
     */
    private void appendElementDiagnostics(List<String> diagnostics, String label, DocumentLayoutBox box) {
        diagnostics.add(label + "Element=" + describeElement(box.getElement()));
        diagnostics.add(label + "Box=" + summarizeLayoutBox(box));
        diagnostics.add(label + "Style=" + summarizeComputedStyle(box.getComputedStyle()));
    }

    /**
     * 构建布局盒位置、尺寸和 stacking 摘要。
     *
     * @param box 布局盒
     * @return 布局盒摘要
     */
    private String summarizeLayoutBox(DocumentLayoutBox box) {
        return "border(" + formatRect(box.getLeft(), box.getTop(), box.getWidth(), box.getHeight()) + ")"
                + ", content(" + formatRect(box.getContentLeft(), box.getContentTop(), box.getContentWidth(),
                        box.getContentHeight()) + ")"
                + ", margin(" + formatEdges(box.getMargin()) + ")"
                + ", borderEdges(" + formatEdges(box.getBorder()) + ")"
                + ", padding(" + formatEdges(box.getPadding()) + ")"
                + ", phase=" + box.getStackingPhase()
                + ", zSort=" + box.getStackingZIndex()
                + ", stackingContext=" + box.createsStackingContext();
    }

    /**
     * 构建 computed style 的排障摘要。
     *
     * @param style computed style
     * @return 样式摘要
     */
    private String summarizeComputedStyle(ComputedStyle style) {
        return "display=" + style.getDisplay()
                + ", position=" + style.getPosition()
                + ", boxSizing=" + style.getBoxSizing()
                + ", width=" + formatLength(style.getWidth())
                + ", height=" + formatLength(style.getHeight())
                + ", minWidth=" + formatLength(style.getMinWidth())
                + ", flexShrink=" + formatFloat(style.getFlexShrink())
                + ", overflow=" + style.getOverflowX() + "/" + style.getOverflowY()
                + ", visibility=" + style.getVisibility()
                + ", pointer=" + style.getPointerEvents()
                + ", bg=" + toHex(style.getBackgroundColor())
                + ", border=" + toHex(style.getBorderColor())
                + ", text=" + toHex(style.getTextColor())
                + ", opacity=" + formatFloat(style.getOpacity())
                + ", zIndex=" + (style.getZIndex() == null ? "auto" : style.getZIndex().toString())
                + ", transform=" + formatTransform(style.getTransform());
    }

    /**
     * 构建元素身份摘要。
     *
     * @param element 元素节点
     * @return 元素身份摘要
     */
    private String describeElement(ElementNode element) {
        StringBuilder builder = new StringBuilder(element.getTagName());
        if (element.getId() != null && element.getId().length() > 0) {
            builder.append('#').append(element.getId());
        }
        if (element.getClassName() != null && element.getClassName().length() > 0) {
            builder.append('.').append(element.getClassName().replace(' ', '.'));
        }
        String text = element.getTextContent();
        if (text != null && text.trim().length() > 0) {
            builder.append(" text=\"").append(truncate(text.trim(), 48)).append('"');
        }
        return builder.toString();
    }

    /**
     * 格式化矩形摘要。
     *
     * @param left 左坐标
     * @param top 上坐标
     * @param width 宽度
     * @param height 高度
     * @return 矩形摘要
     */
    private String formatRect(int left, int top, int width, int height) {
        return "x=" + left + ",y=" + top + ",w=" + width + ",h=" + height;
    }

    /**
     * 格式化盒模型四边摘要。
     *
     * @param edges 四边值
     * @return 四边摘要
     */
    private String formatEdges(DocumentLayoutEdges edges) {
        return "t=" + edges.getTop() + ",r=" + edges.getRight() + ",b=" + edges.getBottom()
                + ",l=" + edges.getLeft();
    }

    /**
     * 格式化样式长度。
     *
     * @param length 样式长度
     * @return 长度摘要
     */
    private String formatLength(UiStyleLength length) {
        if (length == null) {
            return "null";
        }
        switch (length.getType()) {
            case AUTO:
                return "auto";
            case PIXEL:
                return formatFloat(length.getValue()) + "px";
            case PERCENT:
                return formatFloat(length.getValue() * 100.0F) + "%";
            case CALC:
                return "calc(" + formatFloat(length.getValue() * 100.0F) + "%"
                        + (length.getPixelOffset() >= 0.0F ? "+" : "")
                        + formatFloat(length.getPixelOffset()) + "px)";
            default:
                return length.getType().name();
        }
    }

    /**
     * 格式化 transform 摘要。
     *
     * @param transform transform 值
     * @return transform 摘要
     */
    private String formatTransform(UiTransform transform) {
        if (transform == null || transform.isIdentity()) {
            return "none";
        }
        return "translate(" + formatFloat(transform.getTranslateX()) + "," + formatFloat(transform.getTranslateY())
                + ") scale(" + formatFloat(transform.getScaleX()) + "," + formatFloat(transform.getScaleY())
                + ") rotate(" + formatFloat(transform.getRotateDegrees()) + "deg) origin("
                + formatLength(transform.getOriginX()) + "," + formatLength(transform.getOriginY()) + ")";
    }

    /**
     * 格式化背景图宿主资源摘要。
     *
     * @param image 背景图值
     * @return 背景图资源摘要
     */
    private String formatBackgroundSource(UiBackgroundImage image) {
        if (image == null) {
            return "none";
        }
        if (image.getSource().getTexture() != null) {
            return image.getSource().getKind() + ":" + image.getSource().getTexture();
        }
        if (image.getSource().getImageKey() != null) {
            return image.getSource().getKind() + ":" + image.getSource().getImageKey();
        }
        return image.getSource().getKind().name();
    }

    /**
     * 格式化浮点数，避免平台本地化影响日志。
     *
     * @param value 浮点值
     * @return 浮点摘要
     */
    private String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.2f", Float.valueOf(value));
    }

    /**
     * 截断过长文本，保持日志单行可读。
     *
     * @param text 原文本
     * @param maxLength 最大长度
     * @return 截断后文本
     */
    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private DocumentLayoutBox resolveBox(HtmlLikeDocumentWidget widget, ElementNode element) {
        DocumentLayoutBox box = findLayoutBox(widget.resolveLayoutBoxForTest(), element);
        if (box == null) {
            throw new IllegalStateException("未找到布局盒: " + element.getTagName());
        }
        return box;
    }

    private ElementNode findElementByAttribute(ElementNode current, String name, String value) {
        if (current == null) {
            return null;
        }
        if (value.equals(current.getAttribute(name))) {
            return current;
        }
        for (club.heiqi.uilib.ui.dom.DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findElementByAttribute((ElementNode) child, name, value);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private ElementNode findByText(ElementNode current, String text) {
        if (current == null || text == null) {
            return null;
        }
        for (club.heiqi.uilib.ui.dom.DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findByText((ElementNode) child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        if (text.equals(current.getTextContent())) {
            return current;
        }
        return null;
    }

    private ElementNode findById(ElementNode current, String id) {
        if (current == null || id == null) {
            return null;
        }
        if (id.equals(current.getId())) {
            return current;
        }
        for (club.heiqi.uilib.ui.dom.DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findById((ElementNode) child, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private ElementNode findByClass(ElementNode current, String className) {
        if (current == null || className == null) {
            return null;
        }
        if (current.getClassList().contains(className)) {
            return current;
        }
        for (club.heiqi.uilib.ui.dom.DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findByClass((ElementNode) child, className);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private ElementNode findByTag(ElementNode current, String tagName) {
        if (current == null || tagName == null) {
            return null;
        }
        if (tagName.equals(current.getTagName())) {
            return current;
        }
        for (club.heiqi.uilib.ui.dom.DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findByTag((ElementNode) child, tagName);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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

    private String toHex(int color) {
        return String.format(java.util.Locale.ROOT, "0x%08X", Integer.valueOf(color));
    }

    private String join(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "无诊断输出。";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append(" | ");
            }
            builder.append(lines.get(index));
        }
        return builder.toString();
    }
}
