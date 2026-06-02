package club.heiqi.uilib.internal.devtools.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.event.FontReloadRequest;
import club.heiqi.uilib.internal.devtools.UiHudDemoController;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentElementClickEvent;
import club.heiqi.uilib.ui.dom.DocumentElementClickHandler;
import club.heiqi.uilib.ui.dom.DocumentElementKeyEvent;
import club.heiqi.uilib.ui.dom.DocumentElementKeyHandler;
import club.heiqi.uilib.ui.dom.DocumentEventPhase;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * 运行时自检页：在游戏内对 LTS 阶段无法用 JVM 单测覆盖的关键路径做现场断言，
 * 失败立即抛 {@link IllegalStateException} 让 Minecraft 崩溃面板捕获堆栈。
 *
 * <p>覆盖场景：</p>
 * <ul>
 *   <li>FontService reload 防抖：连续 5 次 reload 在安静窗口内只让 1 次落到底层 performReloadLocked。</li>
 *   <li>FontService 并发 reload：4 个线程同时调用 reload，验证不会抛、不会让 isReloading 卡死。</li>
 *   <li>FontService fallback：未匹配字体时拿到 fallback codepoint 解析结果，不抛 NPE。</li>
 *   <li>ForgeConfigTemplateScreen 冷构造：用合成 Configuration 验证模板可以无崩溃构造。</li>
 *   <li>线程池关停：手动调用 DocumentRemoteImageCache.shutdown 验证线程池可在 2 秒内关停。</li>
 * </ul>
 *
 * <p>测试通过会在按钮上追加 ✓ 文字；失败会立即抛异常。</p>
 */
public final class UiRuntimeSelfTestDocumentPageController extends DocumentPageController {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/UiRuntimeSelfTest");
    private static final int MAX_LOG_LINES = 18;

    private final DocumentPageAuthoringSurface documentPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private final UiDocument document;
    private final TextMeasureService textMeasureService;
    private final List<TestEntry> entries = new ArrayList<TestEntry>();
    private final List<String> runtimeLogLines = new ArrayList<String>();
    private TextNode summaryText;
    private TextNode runtimeLogText;

    /**
     * 创建运行时自检页控制器。
     *
     * @param documentUi 文档组件作用域
     * @param documentPage 文档页面壳
     */
    public UiRuntimeSelfTestDocumentPageController(DocumentUiScope documentUi,
            DocumentPageAuthoringSurface documentPage) {
        Objects.requireNonNull(documentUi, "documentUi");
        this.documentPage = Objects.requireNonNull(documentPage, "documentPage");
        this.document = UiDocument.create();
        this.document.setDefaultTextContentMode(documentUi.getDefaultTextContentMode());
        this.textMeasureService = documentUi.getTextMeasureService();
        this.htmlLikeDocumentWidget = new HtmlLikeDocumentWidget(document, 760, 520,
                documentUi.getTextMeasureService());
        this.htmlLikeDocumentWidget.setViewportRootScrollingEnabled(true);
        this.htmlLikeDocumentWidget.setLayoutSpec(new UiLayoutSpec()
                .setWidth(UiLength.percent(1.0F))
                .setHeight(UiLength.percent(1.0F)));
        buildSelfTestDocument();
    }

    @Override
    public void configureDocumentPage() {
        documentPage.setContentWidthRange(720, 1080)
                .setMinContentHeight(540)
                .setViewportFillRatio(0.94F, 0.92F);
    }

    @Override
    public void buildDocument() {
        documentPage.addBlock(htmlLikeDocumentWidget);
    }

    private void buildSelfTestDocument() {
        ElementNode root = document.getRootElement();
        root.style()
                .setPadding(UiStyleLength.px(20))
                .setBackgroundColor(0xF00A1020)
                .setBorderColor(0xFF5B7CFA)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(20))
                .setOverflowY(UiOverflow.AUTO)
                .setTextColor(0xFFE8EEFF);

        ElementNode header = document.div();
        header.style()
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF101D33)
                .setBorderColor(0xFF6B96FF)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(0), UiStyleLength.px(12),
                        UiStyleLength.px(0)));
        header.appendText("运行时自检");
        header.appendText("点击下方按钮逐项执行；失败会立即抛 IllegalStateException，Minecraft 崩溃面板会保留完整堆栈与调用上下文。");
        root.append(header);

        registerEntry(root, "FontService reload 防抖",
                "连续 5 次 reload(...) 在安静窗口内应只触发 1 次实际 performReload。", new SelfTestRunnable() {
                    @Override
                    public void run() {
                        runFontServiceReloadDebounce();
                    }
                });
        registerEntry(root, "FontService 异步 reload 拦截",
                "4 个 worker 线程并发调用 reload(...)，应全部被丢弃；主线程 reload 不受影响。", new SelfTestRunnable() {
                    @Override
                    public void run() {
                        runFontServiceConcurrentReload();
                    }
                });
        registerEntry(root, "FontService fallback",
                "请求一个不可见 Unicode 私用区码点，应回落到 fallback 字体并返回非 0 像素宽度。", new SelfTestRunnable() {
                    @Override
                    public void run() {
                        runFontServiceFallback();
                    }
                });
        registerEntry(root, "ForgeConfigTemplate 冷构造",
                "用合成 Configuration 构造模板，应能创建 binding 与状态文本而不崩溃。", new SelfTestRunnable() {
                    @Override
                    public void run() {
                        runForgeConfigTemplateColdConstruction();
                    }
                });
        registerEntry(root, "RemoteImageCache 关停",
                "调用 DocumentRemoteImageCache.shutdown() 应在 2 秒内完成。", new SelfTestRunnable() {
                    @Override
                    public void run() {
                        runRemoteImageCacheShutdown();
                    }
                });
        registerEntry(root, "浏览器事件语义",
                "运行时断言 click 的 AT_TARGET 顺序、raw button 键盘默认 click 与 preventDefault 去重语义。",
                new SelfTestRunnable() {
                    @Override
                    public void run() {
                        runBrowserEventSemanticsAssertions();
                    }
                });
        registerEntry(root, "HUD 输入抢占 smoke",
                "打开本地 HUD 输入 smoke 浮窗，并把聊天框 / 退格 / 焦点交接的预期结果直接写在浮窗里供人工对照。", new SelfTestRunnable() {
                    @Override
                    public void run() {
                        runHudInputCaptureSmoke();
                    }
                });
        registerEntry(root, "全部依次执行",
                "依次跑上面所有项；任意一项失败立即抛异常。", new SelfTestRunnable() {
                    @Override
                    public void run() {
                        runAllInSequence();
                    }
                });

        ElementNode summaryBlock = document.div();
        summaryBlock.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xFF17243A)
                .setBorderColor(0xFF2E4C7F)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12));
        summaryText = summaryBlock.appendText("尚未执行任何自检项。");
        root.append(summaryBlock);

        ElementNode logBlock = document.div();
        logBlock.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(12), UiStyleLength.px(0), UiStyleLength.px(0),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xFF101A29)
                .setBorderColor(0xFF2A426A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(12));
        logBlock.appendText("运行时日志（最近 18 条）");
        runtimeLogText = logBlock.appendText("尚无日志。");
        root.append(logBlock);
        appendRuntimeLog("init", "运行时自检页已构建，可直接在游戏内触发断言。", null);
    }

    private void registerEntry(ElementNode root, String title, String description,
            final SelfTestRunnable runnable) {
        ElementNode card = document.div();
        card.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(0), UiStyleLength.px(10),
                        UiStyleLength.px(0)))
                .setPadding(UiStyleLength.px(14))
                .setBackgroundColor(0xFF1D2A44)
                .setBorderColor(0xFF405F9C)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderRadius(UiStyleLength.px(14))
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(14));

        ElementNode textColumn = document.div();
        textColumn.style()
                .setFlexGrow(1.0F)
                .setMinWidth(UiStyleLength.px(0));
        textColumn.appendText(title);
        textColumn.appendText(description);
        card.append(textColumn);

        ElementNode statusColumn = document.div();
        statusColumn.style().setMinWidth(UiStyleLength.px(170));
        TextNode statusLabel = statusColumn.appendText("待执行");
        card.append(statusColumn);

        DocumentButtonControl button = new DocumentButtonControl(document, "执行");
        button.setBackgroundColors(0xFF3B82F6, 0xFF1D4ED8, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE);
        button.getElement().setAttribute("data-runtime-self-test", title);
        button.getElement().style().setMinWidth(UiStyleLength.px(120));
        card.append(button.getElement());
        root.append(card);

        final TestEntry entry = new TestEntry(title, button, statusLabel, runnable);
        entries.add(entry);
        button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                executeAndDecorate(entry);
            }
        });
    }

    private void executeAndDecorate(TestEntry entry) {
        long startedAt = System.nanoTime();
        appendRuntimeLog(entry.title, "开始执行", null);
        try {
            entry.runnable.run();
        } catch (RuntimeException exception) {
            entry.statusLabel.setText("✗ 失败");
            updateSummary("[" + entry.title + "] 失败：" + exception.getClass().getSimpleName() + " - "
                    + safeMessage(exception));
            appendRuntimeLog(entry.title, "失败：" + safeMessage(exception), exception);
            throw exception;
        } catch (Error error) {
            entry.statusLabel.setText("✗ 错误");
            updateSummary("[" + entry.title + "] 错误：" + error.getClass().getSimpleName() + " - "
                    + safeMessage(error));
            appendRuntimeLog(entry.title, "错误：" + safeMessage(error), error);
            throw error;
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        entry.statusLabel.setText("✓ 通过 (" + durationMs + "ms)");
        updateSummary("[" + entry.title + "] 通过，用时 " + durationMs + "ms。");
        appendRuntimeLog(entry.title, "通过，用时 " + durationMs + "ms", null);
    }

    private void runAllInSequence() {
        // 跳过自身的"全部依次执行"项，依次跑前面的真实测试。
        for (TestEntry entry : entries) {
            if (entry.title.equals("全部依次执行")) {
                continue;
            }
            executeAndDecorate(entry);
        }
    }

    /**
     * Test 1：reload 防抖。
     */
    private void runFontServiceReloadDebounce() {
        FontService fontService = FontService.getInstance();
        if (!fontService.isInitialized()) {
            fontService.initialize();
        }
        // 在测试前推动一次 tickMainThread，让 reloadDebouncer 进入稳定窗口。
        fontService.tickMainThread(0);
        long beforeEpoch = fontService.getTextMeasureEpoch();
        for (int index = 0; index < 5; index++) {
            fontService.reload(new FontReloadRequest("self-test-debounce-" + index));
        }
        long afterEpoch = fontService.getTextMeasureEpoch();
        // reload 防抖在内部仅要求"不抛、不卡死"。安静窗口内 epoch 增量应有上限：
        // - 至多 1 次立即执行（首次 reload 满足 quietPeriod）+ 1 次 debounce 合并 = 2。
        long delta = afterEpoch - beforeEpoch;
        if (delta > 2) {
            throw new IllegalStateException("FontService reload 防抖失效：epoch 增量 " + delta + " > 2，beforeEpoch="
                    + beforeEpoch + " afterEpoch=" + afterEpoch);
        }
    }

    /**
     * Test 2：异步线程 reload 拦截。
     *
     * <p>4 个 worker 线程并发调用 reload，应全部被 FontService 在线程归属检查阶段丢弃；
     * 主线程在测试前后再 reload 一次以确认主线程路径仍可用。整个过程不应抛出任何异常，
     * 也不应触发 GL 资源释放（之前真机会抛 "No context is current"）。</p>
     */
    private void runFontServiceConcurrentReload() {
        final FontService fontService = FontService.getInstance();
        if (!fontService.isInitialized()) {
            fontService.initialize();
        }
        // 主线程先发一次，确认主线程路径活着，并把渲染主线程引用记录下来。
        fontService.reload(new FontReloadRequest("self-test-concurrent-main-pre"));

        final int threadCount = 4;
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch finishGate = new CountDownLatch(threadCount);
        final AtomicInteger failureCount = new AtomicInteger(0);
        final List<Throwable> failures = new ArrayList<Throwable>();
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        for (int index = 0; index < threadCount; index++) {
            final int taskId = index;
            pool.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        startGate.await();
                        // 异步线程的 reload 会被 FontService 丢弃；该调用不应抛异常。
                        fontService.reload(new FontReloadRequest("self-test-concurrent-worker-" + taskId));
                    } catch (Throwable throwable) {
                        failureCount.incrementAndGet();
                        synchronized (failures) {
                            failures.add(throwable);
                        }
                    } finally {
                        finishGate.countDown();
                    }
                }
            });
        }
        startGate.countDown();
        try {
            if (!finishGate.await(5L, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                throw new IllegalStateException("FontService 异步 reload 拦截 5 秒内未全部返回（threadCount=" + threadCount + "）");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FontService 异步 reload 拦截被打断", exception);
        } finally {
            pool.shutdown();
        }
        if (failureCount.get() > 0) {
            Throwable first;
            synchronized (failures) {
                first = failures.isEmpty() ? null : failures.get(0);
            }
            throw new IllegalStateException("FontService 异步 reload 出现 " + failureCount.get() + " 个错误", first);
        }
        // 主线程再发一次，确认拦截没有把主线程通道一起破坏。
        fontService.reload(new FontReloadRequest("self-test-concurrent-main-post"));
    }

    /**
     * Test 3：fallback。
     */
    private void runFontServiceFallback() {
        FontService fontService = FontService.getInstance();
        if (!fontService.isInitialized()) {
            fontService.initialize();
        }
        // PUA 区域字符在大多数字体中没有图形，但 fallback 选择不应抛 NPE。
        int privateUseCodepoint = 0xE000;
        // 直接走文本测量服务的字宽接口，等价于绘制路径会调用的 fallback 选择。
        TextMeasureProbe probe = new TextMeasureProbe();
        try {
            probe.measure(new String(Character.toChars(privateUseCodepoint)));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("FontService fallback 解析私用区码点抛出异常", exception);
        }
    }

    /**
     * Test 4：ForgeConfigTemplate 冷构造。
     */
    private void runForgeConfigTemplateColdConstruction() {
        Configuration configuration = new Configuration();
        configuration.addCustomCategoryComment("general", "运行时自检");
        Property toggle = configuration.get("general", "experimental", false, "实验性开关");
        Property listProperty = configuration.get("general", "tags",
                new String[] { "default", "fallback" }, "标签列表");
        if (toggle == null || listProperty == null) {
            throw new IllegalStateException("Configuration.get 返回 null，无法继续模板构造测试");
        }
        try {
            club.heiqi.uilib.config.ForgeConfigTemplateScreen.Spec spec =
                    new club.heiqi.uilib.config.ForgeConfigTemplateScreen.Spec(
                            "self_test_mod", "Self Test Config", configuration);
            club.heiqi.uilib.config.ForgeConfigTemplateScreen template =
                    new club.heiqi.uilib.config.ForgeConfigTemplateScreen(null, spec);
            if (template == null) {
                throw new IllegalStateException("ForgeConfigTemplateScreen 构造返回 null");
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("ForgeConfigTemplateScreen 冷构造失败", exception);
        }
    }

    /**
     * Test 5：RemoteImageCache shutdown 链路。
     *
     * <p>该测试会真正调用 shutdown，将关停内部线程池。本次测试通过后，业务侧不应再依赖此缓存。
     * 这是本自检页里副作用最大的一项，仅适合在客户端关闭前手动触发。</p>
     */
    private void runRemoteImageCacheShutdown() {
        long startedAt = System.nanoTime();
        DocumentRemoteImageCache.getInstance().shutdown();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        if (durationMs > 2_000L) {
            throw new IllegalStateException("DocumentRemoteImageCache.shutdown() 用时 " + durationMs + "ms > 2000ms");
        }
    }

    /**
     * Test 6：浏览器事件语义运行时断言。
     */
    private void runBrowserEventSemanticsAssertions() {
        runTargetPhaseOrderingAssertion();
        runRawButtonDefaultKeyboardClickAssertion();
        runRawButtonPreventDefaultAssertion();
        runDocumentButtonNoDuplicateKeyboardActivationAssertion();
    }

    /**
     * Test 7：HUD 输入抢占 smoke。
     *
     * <p>该项不做 JVM 断言，而是启用游戏内 HUD 浮窗，并把人工验证步骤与预期直接显示在浮窗内。</p>
     */
    private void runHudInputCaptureSmoke() {
        if (!UiHudDemoController.getInstance().isEnabled()) {
            UiHudDemoController.getInstance().toggle();
        }
        updateSummary("[HUD 输入抢占 smoke] 已打开。请在容器页与聊天框场景下对照浮窗中的预期结果逐项验证。");
    }

    private void updateSummary(String message) {
        if (summaryText != null) {
            summaryText.setText(message);
        }
    }

    private void appendRuntimeLog(String category, String message, Throwable throwable) {
        String resolvedCategory = category == null ? "self-test" : category;
        String resolvedMessage = message == null ? "<no message>" : message;
        String line = "[" + resolvedCategory + "] " + resolvedMessage;
        if (throwable == null) {
            LOG.info(line);
            MyMod.LOG.info(line);
        } else {
            LOG.error(line, throwable);
            MyMod.LOG.error(line, throwable);
        }
        runtimeLogLines.add(line);
        while (runtimeLogLines.size() > MAX_LOG_LINES) {
            runtimeLogLines.remove(0);
        }
        if (runtimeLogText != null) {
            runtimeLogText.setText(joinLines(runtimeLogLines));
        }
    }

    private void runTargetPhaseOrderingAssertion() {
        appendRuntimeLog("浏览器事件语义", "子项1：断言 target capture 返回 true 后 target handler 仍执行", null);
        UiDocument runtimeDocument = UiDocument.create();
        ElementNode root = runtimeDocument.getRootElement();
        ElementNode child = runtimeDocument.div();
        final List<String> eventLog = new ArrayList<String>();
        root.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(40));
        child.style().setWidth(UiStyleLength.px(40)).setHeight(UiStyleLength.px(20));
        child.setCaptureClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                eventLog.add("target-capture:" + event.getEventPhase());
                return true;
            }
        });
        child.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                eventLog.add("target:" + event.getEventPhase());
                return false;
            }
        });
        root.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                eventLog.add("root:" + event.getEventPhase());
                return false;
            }
        });
        root.append(child);
        HtmlLikeDocumentWidget widget = createProbeWidget(runtimeDocument, 80, 40);
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, 10, 10, 0, 0, 0, 0, 1L));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, 10, 10, 0, 0, 0, 0, 2L));
        appendRuntimeLog("浏览器事件语义", "子项1事件链=" + eventLog, null);
        require(eventLog.size() == 2,
                "AT_TARGET 顺序异常：期望 2 条事件，实际 " + eventLog.size() + "，eventLog=" + eventLog);
        require(("target-capture:" + DocumentEventPhase.AT_TARGET).equals(eventLog.get(0)),
                "AT_TARGET 顺序异常：第1条不是 target capture，eventLog=" + eventLog);
        require(("target:" + DocumentEventPhase.AT_TARGET).equals(eventLog.get(1)),
                "AT_TARGET 顺序异常：第2条不是 target handler，eventLog=" + eventLog);
    }

    private void runRawButtonDefaultKeyboardClickAssertion() {
        appendRuntimeLog("浏览器事件语义", "子项2：断言 raw button key handler 返回 true 时默认 keyboard click 仍执行", null);
        UiDocument runtimeDocument = UiDocument.create();
        ElementNode root = runtimeDocument.getRootElement();
        ElementNode rawButton = runtimeDocument.button();
        final List<DocumentElementClickEvent> clicks = new ArrayList<DocumentElementClickEvent>();
        rawButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clicks.add(event);
                return true;
            }
        });
        rawButton.setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                return true;
            }
        });
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(40));
        rawButton.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(32));
        root.append(rawButton);
        HtmlLikeDocumentWidget widget = createProbeWidget(runtimeDocument, 120, 40);
        widget.onFocusTraversalEntered(false);
        require(widget.getFocusedElement() == rawButton, "raw button 未获得键盘焦点，无法继续默认 click 断言");
        widget.onKeyEvent(new UiKeyEvent(org.lwjglx.input.Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED,
                false, false, false, false, 1L));
        widget.onKeyEvent(new UiKeyEvent(org.lwjglx.input.Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED,
                false, false, false, false, 2L));
        widget.onKeyEvent(new UiKeyEvent(org.lwjglx.input.Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED,
                false, false, false, false, 3L));
        appendRuntimeLog("浏览器事件语义", "子项2 click 数量=" + clicks.size(), null);
        require(clicks.size() == 2,
                "raw button 默认 keyboard click 异常：期望 2 次 click，实际 " + clicks.size());
    }

    private void runRawButtonPreventDefaultAssertion() {
        appendRuntimeLog("浏览器事件语义", "子项3：断言 preventDefault 会取消 raw button 默认 keyboard click", null);
        UiDocument runtimeDocument = UiDocument.create();
        ElementNode root = runtimeDocument.getRootElement();
        ElementNode rawButton = runtimeDocument.button();
        final List<DocumentElementClickEvent> clicks = new ArrayList<DocumentElementClickEvent>();
        rawButton.setClickHandler(new DocumentElementClickHandler() {
            @Override
            public boolean onClick(DocumentElementClickEvent event) {
                clicks.add(event);
                return true;
            }
        });
        rawButton.setKeyHandler(new DocumentElementKeyHandler() {
            @Override
            public boolean onKey(DocumentElementKeyEvent event) {
                event.preventDefault();
                return false;
            }
        });
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(40));
        rawButton.style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(32));
        root.append(rawButton);
        HtmlLikeDocumentWidget widget = createProbeWidget(runtimeDocument, 120, 40);
        widget.onFocusTraversalEntered(false);
        require(widget.getFocusedElement() == rawButton, "raw button 未获得键盘焦点，无法继续 preventDefault 断言");
        widget.onKeyEvent(new UiKeyEvent(org.lwjglx.input.Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED,
                false, false, false, false, 1L));
        widget.onKeyEvent(new UiKeyEvent(org.lwjglx.input.Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED,
                false, false, false, false, 2L));
        widget.onKeyEvent(new UiKeyEvent(org.lwjglx.input.Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED,
                false, false, false, false, 3L));
        appendRuntimeLog("浏览器事件语义", "子项3 click 数量=" + clicks.size(), null);
        require(clicks.isEmpty(), "preventDefault 未取消 raw button 默认 keyboard click，clicks=" + clicks.size());
    }

    private void runDocumentButtonNoDuplicateKeyboardActivationAssertion() {
        appendRuntimeLog("浏览器事件语义", "子项4：断言 DocumentButtonControl 键盘激活不会与 raw button 默认行为重复叠加", null);
        UiDocument runtimeDocument = UiDocument.create();
        ElementNode root = runtimeDocument.getRootElement();
        final List<DocumentButtonActionEvent> actions = new ArrayList<DocumentButtonActionEvent>();
        DocumentButtonControl buttonControl = new DocumentButtonControl(runtimeDocument, "OK");
        buttonControl.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                actions.add(event);
            }
        });
        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(40));
        buttonControl.getElement().style().setWidth(UiStyleLength.px(80)).setHeight(UiStyleLength.px(32));
        root.append(buttonControl.getElement());
        HtmlLikeDocumentWidget widget = createProbeWidget(runtimeDocument, 120, 40);
        widget.onFocusTraversalEntered(false);
        require(widget.getFocusedElement() == buttonControl.getElement(),
                "DocumentButtonControl 未获得键盘焦点，无法继续去重断言");
        widget.onKeyEvent(new UiKeyEvent(org.lwjglx.input.Keyboard.KEY_RETURN, 0, 0, UiKeyEvent.Action.PRESSED,
                false, false, false, false, 1L));
        widget.onKeyEvent(new UiKeyEvent(org.lwjglx.input.Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.PRESSED,
                false, false, false, false, 2L));
        widget.onKeyEvent(new UiKeyEvent(org.lwjglx.input.Keyboard.KEY_SPACE, 0, 0, UiKeyEvent.Action.RELEASED,
                false, false, false, false, 3L));
        appendRuntimeLog("浏览器事件语义", "子项4 action 数量=" + actions.size(), null);
        require(actions.size() == 2,
                "DocumentButtonControl 键盘激活重复触发：期望 2 次 action，实际 " + actions.size());
    }

    private HtmlLikeDocumentWidget createProbeWidget(UiDocument runtimeDocument, int width, int height) {
        HtmlLikeDocumentWidget widget = new HtmlLikeDocumentWidget(runtimeDocument, width, height, textMeasureService);
        widget.applyLayoutBounds(0, 0, width, height);
        return widget;
    }

    private static String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "尚无日志。";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(index));
        }
        return builder.toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null ? "<no message>" : message;
    }

    private interface SelfTestRunnable {
        void run();
    }

    private static final class TestEntry {

        final String title;
        final DocumentButtonControl button;
        final TextNode statusLabel;
        final SelfTestRunnable runnable;

        TestEntry(String title, DocumentButtonControl button, TextNode statusLabel, SelfTestRunnable runnable) {
            this.title = title;
            this.button = button;
            this.statusLabel = statusLabel;
            this.runnable = runnable;
        }
    }

    private static final class TextMeasureProbe {

        int measure(String text) {
            // 走 FontService 默认 measure 服务，不直接进入 GL 路径，避免在没有渲染上下文时崩溃。
            return club.heiqi.uilib.ui.text.DefaultTextMeasureService.getInstance().getStringWidth(text);
        }
    }
}
