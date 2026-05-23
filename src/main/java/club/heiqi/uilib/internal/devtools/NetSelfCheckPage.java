package club.heiqi.uilib.internal.devtools;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import club.heiqi.uilib.net.api.NetBody;
import club.heiqi.uilib.net.api.NetContentType;
import club.heiqi.uilib.net.api.NetHeaders;
import club.heiqi.uilib.net.api.NetMessage;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.codec.NetCodec;
import club.heiqi.uilib.net.core.NetChunkAssembler;
import club.heiqi.uilib.net.core.NetEnvelope;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.transport.NetSide;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
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
 * 网络层自检页。
 *
 * <p>该页覆盖本地运行时断言与真实客户端-服务端网络往返。</p>
 */
public final class NetSelfCheckPage extends DocumentPageController {

    private final DocumentPageAuthoringSurface documentPage;
    private final HtmlLikeDocumentWidget htmlLikeDocumentWidget;
    private final UiDocument document;
    private final List<SelfCheckEntry> entries = new ArrayList<SelfCheckEntry>();
    private TextNode summaryText;

    /**
     * 创建网络层自检页。
     *
     * @param documentUi 文档 UI 作用域
     * @param documentPage 页面壳
     */
    public NetSelfCheckPage(DocumentUiScope documentUi, DocumentPageAuthoringSurface documentPage) {
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
        buildDocumentTree();
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

    private void buildDocumentTree() {
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
        header.appendText("网络层自检");
        header.appendText("覆盖大小策略、内容信封、Header、可选 codec、分片重组、主线程队列、真实联机往返、错误与超时。");
        root.append(header);

        register(root, "大小策略", "验证 32KB 兼容帧、8/16 MiB 普通消息边界、256 MiB 默认物理能力与 1 GiB 硬上限。",
                new SelfCheckRunnable() {
                    @Override
                    public void run() {
                        checkPayloadLimits();
                    }
                });
        register(root, "内容信封", "运行时编解码 route/key、contentType、headers、statusCode 与 body。",
                new SelfCheckRunnable() {
                    @Override
                    public void run() {
                        checkContentEnvelope();
                    }
                });
        register(root, "Header 规则", "运行时验证 header 大小写归一、token 校验、数量上限与 CR/LF 拒绝。",
                new SelfCheckRunnable() {
                    @Override
                    public void run() {
                        checkHeaders();
                    }
                });
        register(root, "可选 POJO codec", "作为业务二进制辅助，编码并解码带枚举、List、Map、嵌套对象和 @NetTransient 字段的 POJO。",
                new SelfCheckRunnable() {
                    @Override
                    public void run() {
                        checkReflectionCodec();
                    }
                });
        register(root, "分片重组", "模拟 100KB 逻辑信封在 32KB 物理帧下分片并完整重组。",
                new SelfCheckRunnable() {
                    @Override
                    public void run() {
                        checkChunkAssembler();
                    }
                });
        register(root, "主线程队列", "入队客户端与服务端任务并手动 drain，确认 MainThreadDispatcher 链路可用。",
                new SelfCheckRunnable() {
                    @Override
                    public void run() {
                        checkMainThreadQueue();
                    }
                });
        registerAsync(root, "运行时 Channel 往返", "通过预注册内部 Channel 执行 C2S ping 与 S2C pong。",
                new SelfCheckAsyncRunnable() {
                    @Override
                    public CompletableFuture<String> run() {
                        return NetRuntimeSelfChecks.runChannelRoundTrip();
                    }
                });
        registerAsync(root, "运行时分片 Channel", "发送超过 32KB 的二进制 body，验证 C2S 分片与服务端重组。",
                new SelfCheckAsyncRunnable() {
                    @Override
                    public CompletableFuture<String> run() {
                        return NetRuntimeSelfChecks.runChunkedChannelRoundTrip();
                    }
                });
        registerAsync(root, "运行时 Fetch 往返", "通过预注册内部 Fetch endpoint 执行 C2S 请求与响应。",
                new SelfCheckAsyncRunnable() {
                    @Override
                    public CompletableFuture<String> run() {
                        return NetRuntimeSelfChecks.runFetchRoundTrip();
                    }
                });
        registerAsync(root, "运行时 Fetch 错误", "通过 Fetch context.fail 验证服务端错误会返回 500 响应。",
                new SelfCheckAsyncRunnable() {
                    @Override
                    public CompletableFuture<String> run() {
                        return NetRuntimeSelfChecks.runFetchErrorRoundTrip();
                    }
                });
        registerAsync(root, "运行时 Fetch 超时", "调用短超时 endpoint，并由后续网络帧触发 pending timeout。",
                new SelfCheckAsyncRunnable() {
                    @Override
                    public CompletableFuture<String> run() {
                        return NetRuntimeSelfChecks.runFetchTimeout();
                    }
                });
        registerAsync(root, "运行时 Store 快照", "通过 Fetch 触发服务端 Store set，再等待客户端 Store snapshot。",
                new SelfCheckAsyncRunnable() {
                    @Override
                    public CompletableFuture<String> run() {
                        return NetRuntimeSelfChecks.runStoreSnapshot();
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
        summaryText = summaryBlock.appendText("尚未执行网络层自检。");
        root.append(summaryBlock);
    }

    private void register(ElementNode root, String title, String description, final SelfCheckRunnable runnable) {
        final SelfCheckEntry entry = appendEntry(root, title, description);
        entry.button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                execute(entry, runnable);
            }
        });
    }

    private void registerAsync(ElementNode root, String title, String description,
            final SelfCheckAsyncRunnable runnable) {
        final SelfCheckEntry entry = appendEntry(root, title, description);
        entry.button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                executeAsync(entry, runnable);
            }
        });
    }

    private SelfCheckEntry appendEntry(ElementNode root, String title, String description) {
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
        statusColumn.style().setMinWidth(UiStyleLength.px(150));
        TextNode statusLabel = statusColumn.appendText("待执行");
        card.append(statusColumn);

        DocumentButtonControl button = new DocumentButtonControl(document, "执行");
        button.setBackgroundColors(0xFF3B82F6, 0xFF1D4ED8, 0xFF334155)
                .setFocusBorderColor(0xFFBFDBFE);
        button.getElement().setAttribute("data-net-self-check", title);
        button.getElement().style().setMinWidth(UiStyleLength.px(110));
        card.append(button.getElement());
        root.append(card);

        final SelfCheckEntry entry = new SelfCheckEntry(title, statusLabel, button);
        entries.add(entry);
        return entry;
    }

    private void execute(SelfCheckEntry entry, SelfCheckRunnable runnable) {
        long startedAt = System.nanoTime();
        try {
            runnable.run();
            markPassed(entry, startedAt, null);
        } catch (RuntimeException exception) {
            markFailed(entry, exception);
        }
    }

    private void executeAsync(final SelfCheckEntry entry, SelfCheckAsyncRunnable runnable) {
        final long startedAt = System.nanoTime();
        entry.statusText.setText("执行中");
        summaryText.setText("[" + entry.title + "] 执行中，等待网络响应。");
        CompletableFuture<String> future;
        try {
            future = runnable.run();
        } catch (RuntimeException exception) {
            markFailed(entry, exception);
            return;
        }
        future.whenComplete(new java.util.function.BiConsumer<String, Throwable>() {
            @Override
            public void accept(final String result, final Throwable throwable) {
                NetService.getInstance().runOnMainThread(NetSide.CLIENT, new Runnable() {
                    @Override
                    public void run() {
                        if (throwable != null) {
                            markFailed(entry, unwrap(throwable));
                            return;
                        }
                        markPassed(entry, startedAt, result);
                    }
                });
            }
        });
    }

    private void markPassed(SelfCheckEntry entry, long startedAt, String detail) {
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        entry.statusText.setText("通过 (" + durationMs + "ms)");
        String suffix = detail == null ? "" : " " + detail;
        summaryText.setText("[" + entry.title + "] 通过，用时 " + durationMs + "ms。" + suffix);
    }

    private void markFailed(SelfCheckEntry entry, Throwable throwable) {
        entry.statusText.setText("失败");
        summaryText.setText("[" + entry.title + "] 失败：" + throwable.getMessage());
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private void checkPayloadLimits() {
        require(NetPayloadLimits.COMPAT_PHYSICAL_FRAME_LIMIT == 32766, "兼容物理帧下限应为 32766");
        require(NetPayloadLimits.LARGE_MESSAGE_WARN_THRESHOLD == 8 * 1024 * 1024, "大消息提示阈值应为 8 MiB");
        require(NetPayloadLimits.DEFAULT_LOGICAL_MESSAGE_LIMIT == 16 * 1024 * 1024, "默认逻辑消息上限应为 16 MiB");
        require(NetPayloadLimits.GTNH_DEFAULT_PHYSICAL_LIMIT == 256 * 1024 * 1024, "GTNH 默认物理能力应为 256 MiB");
        require(NetPayloadLimits.GTNH_HARD_PHYSICAL_LIMIT == 1024 * 1024 * 1024, "硬上限应为 1 GiB");
    }

    private void checkContentEnvelope() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("X-Qz-Event", "self-check");
        NetBody body = NetBody.of(NetContentType.of("application/vnd.qz.selfcheck+json; charset=utf-8"),
                "{\"value\":42}".getBytes(StandardCharsets.UTF_8));

        NetEnvelope decoded = NetEnvelope.decode(NetEnvelope.of(NetEnvelope.Kind.FETCH_RESPONSE, NetSide.CLIENT,
                "qz:selfCheck", 99L, 202, headers, body).encode());

        require(decoded.getKind() == NetEnvelope.Kind.FETCH_RESPONSE, "信封 kind 不一致");
        require(decoded.getTargetSide() == NetSide.CLIENT, "信封方向不一致");
        require("qz:selfCheck".equals(decoded.getKey()), "信封 key 不一致");
        require(decoded.getRequestId() == 99L, "requestId 不一致");
        require(decoded.getStatusCode() == 202, "statusCode 不一致");
        require(decoded.getContentType().isJson(), "contentType 应识别为 JSON");
        require("self-check".equals(decoded.getHeaders().get("x-qz-event")), "header 未归一或丢失");
        require("{\"value\":42}".equals(decoded.toBody().asUtf8String()), "body 不一致");
    }

    private void checkHeaders() {
        NetMessage message = NetMessage.text("ok").withHeader("X-Qz-Trace", "abc");
        require("abc".equals(message.getHeader("x-qz-trace")), "header 小写读取失败");
        require("abc".equals(message.getHeader("X-QZ-TRACE")), "header 大写读取失败");
        require(message.getHeaders().containsKey("x-qz-trace"), "header 未归一成小写");
        try {
            NetMessage.text("bad").withHeader("bad header", "value");
            throw new IllegalStateException("非法 header 名未被拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期路径。
        }
        try {
            NetMessage.text("bad").withHeader("x-qz", "line1\nline2");
            throw new IllegalStateException("带换行 header 值未被拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期路径。
        }
        NetMessage manyHeaders = NetMessage.text("many");
        try {
            for (int index = 0; index <= NetHeaders.MAX_HEADER_COUNT; index++) {
                manyHeaders = manyHeaders.withHeader("x-qz-" + index, "v");
            }
            throw new IllegalStateException("超过 header 数量上限未被拒绝");
        } catch (IllegalArgumentException expected) {
            // 预期路径。
        }
    }

    private void checkReflectionCodec() {
        CodecProbe probe = CodecProbe.sample();
        byte[] bytes = NetCodec.of(CodecProbe.class).encode(probe);
        CodecProbe decoded = NetCodec.of(CodecProbe.class).decode(bytes);
        require(decoded.count == probe.count, "count 不一致");
        require(decoded.mode == probe.mode, "mode 不一致");
        require(decoded.tags.size() == 2 && "alpha".equals(decoded.tags.get(0)), "List 字段不一致");
        require("v".equals(decoded.values.get("k")), "Map 字段不一致");
        require(decoded.child != null && decoded.child.value == 42, "嵌套对象不一致");
        require(decoded.transientValue == null, "@NetTransient 字段不应被传输");
    }

    private void checkChunkAssembler() {
        byte[] payload = new byte[100 * 1024];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (index & 0xFF);
        }
        byte[] envelope = NetEnvelope.of(NetEnvelope.Kind.CHANNEL, NetSide.SERVER, "qz:selfCheck", 0L, 0,
                java.util.Collections.<String, String>emptyMap(), NetBody.binary(payload)).encode();
        NetChunkAssembler assembler = new NetChunkAssembler();
        int chunkSize = NetPayloadLimits.COMPAT_PHYSICAL_FRAME_LIMIT - 160;
        int total = (envelope.length + chunkSize - 1) / chunkSize;
        byte[] completed = null;
        for (int sequence = 0; sequence < total; sequence++) {
            int offset = sequence * chunkSize;
            int length = Math.min(chunkSize, envelope.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(envelope, offset, chunk, 0, length);
            completed = assembler.accept(NetChunkAssembler.encodeChunk(7L, sequence, total, envelope.length, chunk));
        }
        require(completed != null && completed.length == envelope.length, "分片未完成重组");
        NetEnvelope decoded = NetEnvelope.decode(completed);
        require(decoded.getPayload().length == payload.length, "重组后 payload 长度不一致");
    }

    private void checkMainThreadQueue() {
        final int[] counters = new int[2];
        NetService.getInstance().runOnMainThread(NetSide.CLIENT, new Runnable() {
            @Override
            public void run() {
                counters[0]++;
            }
        });
        NetService.getInstance().runOnMainThread(NetSide.SERVER, new Runnable() {
            @Override
            public void run() {
                counters[1]++;
            }
        });
        NetService.getInstance().drainClientMainThreadTasks();
        NetService.getInstance().drainServerMainThreadTasks();
        require(counters[0] == 1 && counters[1] == 1, "主线程队列未正确执行");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private interface SelfCheckRunnable {
        void run();
    }

    private interface SelfCheckAsyncRunnable {
        CompletableFuture<String> run();
    }

    private static final class SelfCheckEntry {

        final String title;
        final TextNode statusText;
        final DocumentButtonControl button;

        SelfCheckEntry(String title, TextNode statusText, DocumentButtonControl button) {
            this.title = title;
            this.statusText = statusText;
            this.button = button;
        }
    }

    public static final class CodecProbe {

        public int count;
        public Mode mode;
        public List<String> tags = new ArrayList<String>();
        public java.util.Map<String, String> values = new java.util.LinkedHashMap<String, String>();
        public ChildProbe child;
        @club.heiqi.uilib.net.codec.NetTransient
        public String transientValue;

        static CodecProbe sample() {
            CodecProbe probe = new CodecProbe();
            probe.count = 12;
            probe.mode = Mode.ACTIVE;
            probe.tags.add("alpha");
            probe.tags.add("beta");
            probe.values.put("k", "v");
            probe.child = new ChildProbe();
            probe.child.value = 42;
            probe.transientValue = "local";
            return probe;
        }
    }

    public static final class ChildProbe {

        public int value;
    }

    public enum Mode {
        ACTIVE,
        IDLE
    }
}
