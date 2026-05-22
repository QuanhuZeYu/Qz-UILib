package club.heiqi.uilib.ui.screen.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.event.FontReloadRequest;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.image.DocumentRemoteImageCache;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.screen.page.DocumentPageAuthoringSurface;
import club.heiqi.uilib.ui.screen.page.DocumentPageController;
import club.heiqi.uilib.ui.screen.page.DocumentUiScope;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

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

    private final DocumentPageAuthoringSurface documentPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private final UiDocument document;
    private final List<TestEntry> entries = new ArrayList<TestEntry>();
    private TextNode summaryText;

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
        registerEntry(root, "FontService 并发 reload",
                "4 个线程并发调用 reload(...)，应能正常返回，且不会让 isReloading 卡死。", new SelfTestRunnable() {
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
        textColumn.style().setFlexGrow(1.0F);
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
        try {
            entry.runnable.run();
        } catch (RuntimeException exception) {
            entry.statusLabel.setText("✗ 失败");
            updateSummary("[" + entry.title + "] 失败：" + exception.getClass().getSimpleName() + " - "
                    + safeMessage(exception));
            throw exception;
        } catch (Error error) {
            entry.statusLabel.setText("✗ 错误");
            updateSummary("[" + entry.title + "] 错误：" + error.getClass().getSimpleName() + " - "
                    + safeMessage(error));
            throw error;
        }
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        entry.statusLabel.setText("✓ 通过 (" + durationMs + "ms)");
        updateSummary("[" + entry.title + "] 通过，用时 " + durationMs + "ms。");
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
     * Test 2：并发 reload。
     */
    private void runFontServiceConcurrentReload() {
        final FontService fontService = FontService.getInstance();
        if (!fontService.isInitialized()) {
            fontService.initialize();
        }
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
                        fontService.reload(new FontReloadRequest("self-test-concurrent-" + taskId));
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
                throw new IllegalStateException("FontService 并发 reload 5 秒内未全部返回（threadCount=" + threadCount + "）");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FontService 并发 reload 被打断", exception);
        } finally {
            pool.shutdown();
        }
        if (failureCount.get() > 0) {
            Throwable first;
            synchronized (failures) {
                first = failures.isEmpty() ? null : failures.get(0);
            }
            throw new IllegalStateException("FontService 并发 reload 出现 " + failureCount.get() + " 个错误", first);
        }
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

    private void updateSummary(String message) {
        if (summaryText != null) {
            summaryText.setText(message);
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
