package club.heiqi.uilib.internal.devtools;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.api.NetBody;
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
 * <p>该页覆盖不依赖真实联机环境的基础链路；真实 Channel / Fetch / Store 往返仍需要
 * runClient + runServer 人工场景补验。</p>
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
        header.appendText("覆盖大小策略、内容信封、可选 codec、分片重组与主线程队列。真实联机往返请配合 runClient/runServer 验证。");
        root.append(header);

        register(root, "大小策略", "验证 32KB 兼容帧、8/16 MiB 普通消息边界、256 MiB 默认物理能力与 1 GiB 硬上限。",
                new SelfCheckRunnable() {
                    @Override
                    public void run() {
                        checkPayloadLimits();
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

        final SelfCheckEntry entry = new SelfCheckEntry(title, statusLabel, runnable);
        entries.add(entry);
        button.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                execute(entry);
            }
        });
    }

    private void execute(SelfCheckEntry entry) {
        long startedAt = System.nanoTime();
        entry.runnable.run();
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000L;
        entry.statusText.setText("通过 (" + durationMs + "ms)");
        summaryText.setText("[" + entry.title + "] 通过，用时 " + durationMs + "ms。");
    }

    private void checkPayloadLimits() {
        require(NetPayloadLimits.COMPAT_PHYSICAL_FRAME_LIMIT == 32766, "兼容物理帧下限应为 32766");
        require(NetPayloadLimits.LARGE_MESSAGE_WARN_THRESHOLD == 8 * 1024 * 1024, "大消息提示阈值应为 8 MiB");
        require(NetPayloadLimits.DEFAULT_LOGICAL_MESSAGE_LIMIT == 16 * 1024 * 1024, "默认逻辑消息上限应为 16 MiB");
        require(NetPayloadLimits.GTNH_DEFAULT_PHYSICAL_LIMIT == 256 * 1024 * 1024, "GTNH 默认物理能力应为 256 MiB");
        require(NetPayloadLimits.GTNH_HARD_PHYSICAL_LIMIT == 1024 * 1024 * 1024, "硬上限应为 1 GiB");
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

    private static final class SelfCheckEntry {

        final String title;
        final TextNode statusText;
        final SelfCheckRunnable runnable;

        SelfCheckEntry(String title, TextNode statusText, SelfCheckRunnable runnable) {
            this.title = title;
            this.statusText = statusText;
            this.runnable = runnable;
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
