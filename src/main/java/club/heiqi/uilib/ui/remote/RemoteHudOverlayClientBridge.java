package club.heiqi.uilib.ui.remote;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.api.NetResponse;
import club.heiqi.uilib.net.api.NetStreamCall;
import club.heiqi.uilib.ui.control.DocumentButtonActionEvent;
import club.heiqi.uilib.ui.control.DocumentButtonActionHandler;
import club.heiqi.uilib.ui.control.DocumentButtonControl;
import club.heiqi.uilib.ui.control.DocumentDraggableSupport;
import club.heiqi.uilib.ui.dom.DocumentElementBounds;
import club.heiqi.uilib.ui.dom.DocumentElementDragEvent;
import club.heiqi.uilib.ui.dom.DocumentElementDragHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.hud.UiHudDocumentHost;
import club.heiqi.uilib.ui.hud.UiHudDocumentRegistration;
import club.heiqi.uilib.ui.hud.UiHudLayerType;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBoxSizing;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.text.DefaultTextMeasureService;
import club.heiqi.uilib.ui.text.TextMeasureService;
import net.minecraft.client.Minecraft;

/**
 * 远程 HUD 客户端桥。
 *
 * <p>该类只在客户端通过反射加载，避免服务端类加载路径触碰 Minecraft 客户端类。</p>
 */
public final class RemoteHudOverlayClientBridge {

    private static final RemoteHudOverlayClientBridge INSTANCE = new RemoteHudOverlayClientBridge();
    private static final int DANMAKU_FALLBACK_WIDTH = 320;
    private static final int DIALOG_HORIZONTAL_MARGIN = 16;
    private static final int DIALOG_VERTICAL_MARGIN = 24;

    private final Map<String, ActiveOverlay> activeOverlays =
            new java.util.concurrent.ConcurrentHashMap<String, ActiveOverlay>();
    private final Map<String, PendingOpen> pendingOpens =
            new java.util.concurrent.ConcurrentHashMap<String, PendingOpen>();

    private RemoteHudOverlayClientBridge() {}

    /**
     * 返回客户端桥单例。
     *
     * @return 单例
     */
    public static RemoteHudOverlayClientBridge getInstance() {
        return INSTANCE;
    }

    /**
     * 接收服务端下发的 HUD 打开 offer。
     *
     * @param json offer JSON
     */
    public static void receiveOpenOffer(String json) {
        INSTANCE.handleOpenOffer(json);
    }

    /**
     * 接收服务端下发的 HUD 关闭命令。
     *
     * @param json dismiss JSON
     */
    public static void receiveDismiss(String json) {
        INSTANCE.handleDismiss(json);
    }

    /**
     * 每帧刷新自动消失与弹幕位移。
     */
    public void tick() {
        long nowMillis = System.currentTimeMillis();
        List<ActiveOverlay> snapshot = new ArrayList<ActiveOverlay>(activeOverlays.values());
        for (ActiveOverlay overlay : snapshot) {
            if (overlay.isExpired(nowMillis)) {
                dismissOverlaySession(overlay.offer.overlayId, overlay.offer.sessionId, true, "client-expired");
                continue;
            }
            if (overlay.mode == RemoteHudOverlayMode.DANMAKU) {
                updateDanmakuPosition(overlay, nowMillis);
            } else if (overlay.mode == RemoteHudOverlayMode.DIALOG) {
                updateDialogPlacement(overlay);
            }
        }
    }

    /**
     * 清空客户端持有的远程 HUD 浮层。
     */
    public void clearAll() {
        pendingOpens.clear();
        List<ActiveOverlay> snapshot = new ArrayList<ActiveOverlay>(activeOverlays.values());
        activeOverlays.clear();
        for (ActiveOverlay overlay : snapshot) {
            unregisterQuietly(overlay.registration);
        }
    }

    private void handleOpenOffer(String json) {
        final RemoteHudOverlays.OpenOffer offer;
        try {
            offer = RemoteHudOverlays.decodeOpenOffer(json);
        } catch (IllegalArgumentException exception) {
            showErrorOverlay("remote-hud-error", "远程 HUD 协议无效", exception.getMessage());
            return;
        }
        dismissPendingOpensByOverlayId(offer.overlayId);
        PendingOpen pendingOpen = new PendingOpen(offer);
        pendingOpens.put(offer.sessionId, pendingOpen);
        NetStreamCall call;
        try {
            call = RemoteHudOverlays.callOverlayStream(offer.sessionId);
        } catch (RuntimeException exception) {
            pendingOpens.remove(offer.sessionId);
            showErrorOverlay(offer.overlayId, "远程 HUD 请求失败", exception.getMessage());
            return;
        }
        call.future().whenComplete(new BiConsumer<NetResponse, Throwable>() {
            @Override
            public void accept(final NetResponse response, final Throwable throwable) {
                UiScreenManager.getInstance().enqueue(new Runnable() {
                    @Override
                    public void run() {
                        PendingOpen currentPending = pendingOpens.remove(offer.sessionId);
                        if (currentPending == null || currentPending.isDismissed()) {
                            return;
                        }
                        if (throwable != null) {
                            showErrorOverlay(offer.overlayId, "远程 HUD 下载失败", readableError(throwable));
                            return;
                        }
                        try {
                            String html = validateAndDecode(offer, response);
                            openHudOverlay(offer, html);
                        } catch (RuntimeException exception) {
                            showErrorOverlay(offer.overlayId, "远程 HUD 校验失败", exception.getMessage());
                        }
                    }
                });
            }
        });
    }

    private void handleDismiss(String json) {
        RemoteHudOverlays.DismissPayload payload;
        try {
            payload = RemoteHudOverlays.decodeDismissPayload(json);
        } catch (IllegalArgumentException exception) {
            MyMod.LOG.warn("远程 HUD dismiss 协议无效", exception);
            return;
        }
        if (!payload.sessionId.isEmpty()) {
            PendingOpen pendingOpen = pendingOpens.remove(payload.sessionId);
            if (pendingOpen != null) {
                pendingOpen.dismiss();
            }
            dismissOverlaySession(payload.overlayId, payload.sessionId, false, payload.reason);
            return;
        }
        dismissPendingOpensByOverlayId(payload.overlayId);
        dismissOverlay(payload.overlayId, false, payload.reason);
    }

    private void dismissPendingOpensByOverlayId(String overlayId) {
        if (overlayId == null || overlayId.trim().isEmpty()) {
            return;
        }
        for (PendingOpen pendingOpen : pendingOpens.values()) {
            if (pendingOpen != null && pendingOpen.matchesOverlayId(overlayId)) {
                pendingOpen.dismiss();
            }
        }
    }

    private String validateAndDecode(RemoteHudOverlays.OpenOffer offer, NetResponse response) {
        if (response == null) {
            throw new IllegalStateException("服务端未返回响应");
        }
        if (!response.isOk()) {
            throw new IllegalStateException("服务端返回错误 " + response.getStatusCode() + "："
                    + response.getBody().asUtf8String());
        }
        if (!RemoteHudOverlays.REMOTE_HTML_CONTENT_TYPE.equals(response.getContentType())) {
            throw new IllegalStateException("远程 HUD 内容类型不匹配：" + response.getContentType());
        }
        if (!offer.sessionId.equals(response.getHeader("x-qz-session-id"))) {
            throw new IllegalStateException("远程 HUD session 响应不匹配");
        }
        if (!offer.overlayId.equals(response.getHeader("x-qz-overlay-id"))) {
            throw new IllegalStateException("远程 HUD overlayId 响应不匹配");
        }
        if (!offer.pageId.equals(response.getHeader("x-qz-page-id"))) {
            throw new IllegalStateException("远程 HUD pageId 响应不匹配");
        }
        byte[] bytes = response.getBody().getBytes();
        if (bytes.length != offer.htmlBytes) {
            throw new IllegalStateException("远程 HUD 大小不匹配，期望 " + offer.htmlBytes + "，实际 "
                    + bytes.length);
        }
        String actualSha256 = RemoteHudOverlays.sha256Hex(bytes);
        if (!offer.sha256.equalsIgnoreCase(actualSha256)
                || !offer.sha256.equalsIgnoreCase(response.getHeader("x-qz-sha256"))) {
            throw new IllegalStateException("远程 HUD SHA-256 校验失败");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void openHudOverlay(final RemoteHudOverlays.OpenOffer offer, final String html) {
        PendingOpen pendingOpen = pendingOpens.remove(offer.sessionId);
        if (pendingOpen != null && pendingOpen.isDismissed()) {
            return;
        }
        dismissOverlay(offer.overlayId, false, "client-replace");
        final OverlayDocumentParts[] partsRef = new OverlayDocumentParts[1];
        UiHudDocumentRegistration registration = UiHudDocumentHost.getInstance().register(UiHudLayerType.INTERACTIVE,
                new UiHudDocumentHost.UiHudDocumentContentBuilder() {
                    @Override
                    public void build(UiHudDocumentHost.UiHudMountContext context) {
                        partsRef[0] = buildOverlayDocument(context.getDocument(), context.getMountRoot(), offer, html);
                    }
                });
        ActiveOverlay activeOverlay = new ActiveOverlay(offer, registration, partsRef[0],
                System.currentTimeMillis(), resolveMode(offer.mode));
        activeOverlays.put(offer.overlayId, activeOverlay);
        if (activeOverlay.mode == RemoteHudOverlayMode.DANMAKU) {
            updateDanmakuPosition(activeOverlay, activeOverlay.openedAtMillis);
        } else if (activeOverlay.mode == RemoteHudOverlayMode.DIALOG) {
            updateDialogPlacement(activeOverlay);
        }
    }

    private void showErrorOverlay(String overlayId, String title, String detail) {
        RemoteHudOverlays.OpenOffer offer = new RemoteHudOverlays.OpenOffer();
        offer.sessionId = "local-error";
        offer.overlayId = overlayId == null || overlayId.trim().isEmpty() ? "remote-hud-error" : overlayId;
        offer.pageId = "remote-hud-error";
        offer.title = title == null ? "远程 HUD 错误" : title;
        offer.mode = RemoteHudOverlayMode.TOAST.name();
        offer.resourcePolicy = RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY.name();
        offer.durationMillis = RemoteHudOverlay.DEFAULT_TOAST_DURATION_MILLIS;
        offer.defaultCloseButtonVisible = false;
        offer.closeButtonLabel = "关闭";
        offer.metadata = java.util.Collections.emptyMap();
        offer.pageMetadata = java.util.Collections.emptyMap();
        String html = "<div style=\"color:#fee2e2\"><strong>" + escapeHtml(offer.title)
                + "</strong><p>" + escapeHtml(detail == null ? "未知错误" : detail) + "</p></div>";
        openHudOverlay(offer, html);
    }

    private void dismissOverlay(String overlayId, boolean notifyServer, String reason) {
        if (overlayId == null || overlayId.trim().isEmpty()) {
            return;
        }
        ActiveOverlay overlay = activeOverlays.remove(overlayId);
        if (overlay == null) {
            return;
        }
        unregisterQuietly(overlay.registration);
        if (!notifyServer || "local-error".equals(overlay.offer.sessionId)) {
            return;
        }
        RemoteHudOverlays.DismissPayload payload = new RemoteHudOverlays.DismissPayload();
        payload.sessionId = overlay.offer.sessionId;
        payload.overlayId = overlay.offer.overlayId;
        payload.reason = reason == null ? "client-dismiss" : reason;
        try {
            RemoteHudOverlays.dismissFromClient(payload);
        } catch (RuntimeException exception) {
            MyMod.LOG.debug("远程 HUD 客户端关闭回传失败：{}", overlay.offer.overlayId, exception);
        }
    }

    private void dismissOverlaySession(String overlayId, String sessionId, boolean notifyServer, String reason) {
        if (overlayId == null || overlayId.trim().isEmpty() || sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }
        ActiveOverlay overlay = activeOverlays.get(overlayId);
        if (overlay == null || overlay.offer == null || !sessionId.equals(overlay.offer.sessionId)) {
            return;
        }
        if (activeOverlays.remove(overlayId, overlay)) {
            unregisterQuietly(overlay.registration);
            if (notifyServer) {
                RemoteHudOverlays.DismissPayload payload = new RemoteHudOverlays.DismissPayload();
                payload.sessionId = overlay.offer.sessionId;
                payload.overlayId = overlay.offer.overlayId;
                payload.reason = reason == null ? "client-dismiss" : reason;
                try {
                    RemoteHudOverlays.dismissFromClient(payload);
                } catch (RuntimeException exception) {
                    MyMod.LOG.debug("远程 HUD 客户端关闭回传失败：{}", overlay.offer.overlayId, exception);
                }
            }
        }
    }

    void addActiveOverlayForTest(RemoteHudOverlays.OpenOffer offer, UiHudDocumentRegistration registration) {
        RemoteHudOverlays.OpenOffer resolvedOffer = offer == null ? new RemoteHudOverlays.OpenOffer() : offer;
        activeOverlays.put(resolvedOffer.overlayId, new ActiveOverlay(resolvedOffer, registration, null,
                System.currentTimeMillis(), resolveMode(resolvedOffer.mode)));
    }

    boolean hasActiveOverlayForTest(String overlayId, String sessionId) {
        ActiveOverlay overlay = activeOverlays.get(overlayId);
        return overlay != null && overlay.offer != null && sessionId != null && sessionId.equals(overlay.offer.sessionId);
    }

    private void updateDanmakuPosition(ActiveOverlay overlay, long nowMillis) {
        if (overlay == null || overlay.parts == null || overlay.parts.movingElement == null) {
            return;
        }
        long durationMillis = overlay.offer.durationMillis > 0L ? overlay.offer.durationMillis
                : RemoteHudOverlay.DEFAULT_DANMAKU_DURATION_MILLIS;
        float progress = Math.max(0.0F, Math.min(1.0F, (nowMillis - overlay.openedAtMillis)
                / (float) Math.max(1L, durationMillis)));
        int screenWidth = resolveScreenWidth();
        int contentWidth = DANMAKU_FALLBACK_WIDTH;
        DocumentElementBounds bounds = overlay.parts.movingElement.getDocumentBounds();
        if (bounds.isAvailable() && bounds.getWidth() > 0) {
            contentWidth = bounds.getWidth();
        }
        float travel = screenWidth + contentWidth + 48.0F;
        overlay.parts.movingElement.style().setLeft(UiStyleLength.px(screenWidth + 24.0F - progress * travel));
    }

    private void updateDialogPlacement(ActiveOverlay overlay) {
        if (overlay == null || overlay.parts == null || overlay.parts.dialogPlacement == null) {
            return;
        }
        overlay.parts.dialogPlacement.updateForViewport(resolveScreenWidth(), resolveScreenHeight());
    }

    static OverlayDocumentParts buildOverlayDocument(UiDocument document, ElementNode root,
            final RemoteHudOverlays.OpenOffer offer, String html) {
        RemoteHudOverlayMode mode = resolveMode(offer.mode);
        RemoteDocumentResourcePolicy policy = resolvePolicy(offer.resourcePolicy);
        applyRootContract(root, mode);
        ElementNode shell = document.div();
        root.append(shell);
        configureShell(shell, offer, mode);
        DialogPlacementSupport dialogPlacement = mode == RemoteHudOverlayMode.DIALOG
                ? new DialogPlacementSupport(root, shell) : null;
        ElementNode content = document.div();
        configureContent(content, mode);
        shell.append(content);
        RemoteFormSubmitSink submitSink = new RemoteFormSubmitSink() {
            @Override
            public void submit(String sessionId, String pageId, String action, String formId,
                    Map<String, List<String>> values) {
                RemoteHudOverlays.SubmitPayload payload = new RemoteHudOverlays.SubmitPayload();
                payload.sessionId = sessionId;
                payload.overlayId = offer.overlayId;
                payload.pageId = pageId;
                payload.action = action;
                payload.formId = formId;
                payload.values = values;
                RemoteHudOverlays.submitFromClient(payload);
            }
        };
        RemoteHtmlDocumentParser.parseInto(document, content, html,
                RemoteHtmlDocumentParser.Options.of(offer.sessionId, offer.pageId, policy, submitSink, false));
        if (mode == RemoteHudOverlayMode.DIALOG) {
            installDialogDrag(shell, content, dialogPlacement);
            if (offer.defaultCloseButtonVisible) {
                shell.append(createCloseButton(document, offer));
            }
        }
        ElementNode notice = document.div();
        TextNode noticeText = notice.appendRawText("");
        configureNotice(notice);
        shell.append(notice);
        RemoteDocumentLinkSupport.install(document, policy, new RemoteDocumentLinkSupport.NoticeSink() {
            @Override
            public void showNotice(String message) {
                RemoteHudOverlayClientBridge.showNotice(notice, noticeText, message);
            }
        });
        return new OverlayDocumentParts(shell, content, notice, noticeText, mode, dialogPlacement);
    }

    static OverlayDocumentParts buildOverlayDocument(UiDocument document, final RemoteHudOverlays.OpenOffer offer,
            String html) {
        return buildOverlayDocument(document, document == null ? null : document.getRootElement(), offer, html);
    }

    private static void applyRootContract(ElementNode root, RemoteHudOverlayMode mode) {
        root.style()
                .setDisplay(UiDisplay.BLOCK)
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(0))
                .setTop(UiStyleLength.px(0))
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.percent(1.0F))
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE)
                .setTextColor(0xFFE5E7EB);
        if (mode != RemoteHudOverlayMode.DIALOG) {
            root.setAttribute("data-hit-test-hidden", "true");
            root.style().setPointerEvents(UiPointerEvents.NONE);
        }
    }

    private static void configureShell(ElementNode shell, RemoteHudOverlays.OpenOffer offer,
            RemoteHudOverlayMode mode) {
        shell.style().setBoxSizing(UiBoxSizing.BORDER_BOX).setZIndex(9000);
        if (mode == RemoteHudOverlayMode.DIALOG) {
            shell.style()
                    .setPosition(UiPosition.FIXED)
                    .setLeft(UiStyleLength.px(0))
                    .setTop(UiStyleLength.px(0))
                    .setDisplay(UiDisplay.FLEX)
                    .setFlexDirection(UiFlexDirection.COLUMN)
                    .setWidth(UiStyleLength.px(480))
                    .setMaxWidth(UiStyleLength.calc(1.0F, -32.0F))
                    .setMaxHeight(UiStyleLength.calc(1.0F, -48.0F))
                    .setRowGap(UiStyleLength.px(8))
                    .setOverflowX(UiOverflow.VISIBLE)
                    .setOverflowY(UiOverflow.VISIBLE)
                    .setVisibility(UiVisibility.HIDDEN);
            return;
        }
        if (mode == RemoteHudOverlayMode.TOAST) {
            configureToastShell(shell, offer);
            return;
        }
        configureDanmakuShell(shell, offer);
    }

    private static void configureToastShell(ElementNode shell, RemoteHudOverlays.OpenOffer offer) {
        shell.style()
                .setPosition(UiPosition.FIXED)
                .setWidth(UiStyleLength.px(340))
                .setMaxWidth(UiStyleLength.calc(1.0F, -24.0F))
                .setPadding(UiStyleLength.px(10))
                .setBackgroundColor(0xE60F172A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF38BDF8)
                .setBorderRadius(UiStyleLength.px(8))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.VISIBLE);
        String placement = metadataValue(offer.metadata, "placement", "bottom-right").toLowerCase(java.util.Locale.ROOT);
        if (placement.startsWith("top")) {
            shell.style().setTop(UiStyleLength.px(16));
        } else {
            shell.style().setBottom(UiStyleLength.px(16));
        }
        if (placement.endsWith("left")) {
            shell.style().setLeft(UiStyleLength.px(16));
        } else {
            shell.style().setRight(UiStyleLength.px(16));
        }
    }

    private static void configureDanmakuShell(ElementNode shell, RemoteHudOverlays.OpenOffer offer) {
        int lane = Math.abs((offer.overlayId == null ? "" : offer.overlayId).hashCode()) % 5;
        try {
            lane = Math.max(0, Math.min(8, Integer.parseInt(metadataValue(offer.metadata, "lane",
                    Integer.toString(lane)))));
        } catch (NumberFormatException ignored) {
            // 非法 lane 按自动轨道处理。
        }
        shell.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(DANMAKU_FALLBACK_WIDTH + 24.0F))
                .setTop(UiStyleLength.px(18 + lane * 32))
                .setDisplay(UiDisplay.INLINE_BLOCK)
                .setWidth(UiStyleLength.auto())
                .setPadding(UiStyleLength.px(0))
                .setBackgroundColor(0)
                .setBorderWidth(UiStyleLength.px(0))
                .setBorderStyle(UiBorderStyle.NONE)
                .setBorderColor(0)
                .setBorderRadius(UiStyleLength.px(0))
                .setWhiteSpace(UiWhiteSpace.NOWRAP)
                .setOverflowX(UiOverflow.VISIBLE)
                .setOverflowY(UiOverflow.VISIBLE);
    }

    /**
     * 为 DIALOG 安装拖拽语义。
     *
     * <p>优先尊重作者 HTML 中声明的拖拽把手；没有声明时使用解析内容作为兜底拖拽区域，
     * 避免为了拖拽额外生成可见宿主标题栏。</p>
     *
     * @param shell 负责移动的宿主 shell
     * @param content 解析后的远程 HTML 内容容器
     * @param placementSupport DIALOG 初始 fixed 放置支持
     */
    private static void installDialogDrag(ElementNode shell, ElementNode content,
            final DialogPlacementSupport placementSupport) {
        ElementNode explicitHandle = findElementByAttribute(content, "data-qz-hud-drag-handle", "true");
        final ElementNode dragHandle = explicitHandle == null ? content : explicitHandle;
        if (explicitHandle != null) {
            explicitHandle.style().setCursor(UiCursor.MOVE);
        }
        DocumentDraggableSupport.attachFixed(shell, dragHandle, DocumentDraggableSupport.DragAxis.BOTH);
        if (placementSupport == null) {
            return;
        }
        final DocumentElementDragHandler delegate = dragHandle.getDragHandler();
        dragHandle.setDragHandler(new DocumentElementDragHandler() {
            @Override
            public boolean onDrag(DocumentElementDragEvent event) {
                if (event != null && event.getPhase() == DocumentElementDragEvent.DragPhase.START) {
                    placementSupport.ensurePlacedForCurrentViewport();
                }
                boolean handled = delegate != null && delegate.onDrag(event);
                if (handled && event != null && event.getPhase() == DocumentElementDragEvent.DragPhase.DRAG) {
                    placementSupport.markUserMoved();
                }
                return handled;
            }
        });
    }

    private static ElementNode createCloseButton(UiDocument document, final RemoteHudOverlays.OpenOffer offer) {
        DocumentButtonControl closeButton = new DocumentButtonControl(document,
                offer.closeButtonLabel == null || offer.closeButtonLabel.trim().isEmpty()
                        ? "关闭" : offer.closeButtonLabel);
        closeButton.getElement().style()
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(8))
                .setRight(UiStyleLength.px(8))
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setWidth(UiStyleLength.px(44))
                .setHeight(UiStyleLength.px(24))
                .setMinWidth(UiStyleLength.px(0))
                .setMaxWidth(UiStyleLength.px(44))
                .setPadding(UiStyleLength.px(0))
                .setBorderRadius(UiStyleLength.px(6))
                .setFlexShrink(0.0F)
                .setZIndex(2);
        closeButton.getElement().setAttribute("data-qz-hud-close-button", "true");
        closeButton.setActionHandler(new DocumentButtonActionHandler() {
            @Override
            public void onAction(DocumentButtonActionEvent event) {
                RemoteHudOverlayClientBridge.getInstance().dismissOverlaySession(offer.overlayId, offer.sessionId,
                        true, "client-close");
            }
        });
        return closeButton.getElement();
    }

    private static void configureContent(ElementNode content, RemoteHudOverlayMode mode) {
        content.style()
                .setDisplay(mode == RemoteHudOverlayMode.DANMAKU ? UiDisplay.INLINE_BLOCK : UiDisplay.BLOCK)
                .setBoxSizing(UiBoxSizing.BORDER_BOX)
                .setWidth(mode == RemoteHudOverlayMode.DANMAKU ? UiStyleLength.auto()
                        : UiStyleLength.percent(1.0F))
                .setMinWidth(UiStyleLength.px(0))
                .setTextColor(0xFFE5E7EB);
        if (mode == RemoteHudOverlayMode.DIALOG) {
            content.style()
                    .setFlexGrow(1.0F)
                    .setMinHeight(UiStyleLength.px(0))
                    .setOverflowX(UiOverflow.AUTO)
                    .setOverflowY(UiOverflow.AUTO);
        } else {
            content.style()
                    .setOverflowX(UiOverflow.VISIBLE)
                    .setOverflowY(UiOverflow.VISIBLE);
            if (mode == RemoteHudOverlayMode.DANMAKU) {
                content.style().setWhiteSpace(UiWhiteSpace.NOWRAP);
            }
        }
    }

    private static void configureNotice(ElementNode notice) {
        notice.style()
                .setDisplay(UiDisplay.NONE)
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xEE111827)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF334155)
                .setBorderRadius(UiStyleLength.px(6))
                .setTextColor(0xFFE5E7EB);
    }

    private static void showNotice(ElementNode notice, TextNode noticeText, String message) {
        if (notice == null || noticeText == null) {
            return;
        }
        noticeText.setText(message == null ? "" : message);
        notice.style().setDisplay(UiDisplay.BLOCK);
    }

    private static RemoteDocumentResourcePolicy resolvePolicy(String value) {
        if (value == null || value.trim().isEmpty()) {
            return RemoteDocumentResourcePolicy.FULL_EXTERNAL_LINKS;
        }
        try {
            return RemoteDocumentResourcePolicy.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            return RemoteDocumentResourcePolicy.FULL_EXTERNAL_LINKS;
        }
    }

    private static RemoteHudOverlayMode resolveMode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return RemoteHudOverlayMode.DIALOG;
        }
        try {
            return RemoteHudOverlayMode.valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            return RemoteHudOverlayMode.DIALOG;
        }
    }

    private static String metadataValue(Map<String, String> metadata, String name, String fallback) {
        if (metadata == null || name == null) {
            return fallback;
        }
        String value = metadata.get(name);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    /**
     * 在节点子树中查找首个带指定属性值的元素。
     *
     * @param node 起始节点
     * @param name 属性名
     * @param value 属性值
     * @return 匹配元素；不存在时返回 null
     */
    private static ElementNode findElementByAttribute(DocumentNode node, String name, String value) {
        if (node == null) {
            return null;
        }
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if (value.equals(element.getAttribute(name))) {
                return element;
            }
        }
        for (DocumentNode child : node.getChildren()) {
            ElementNode found = findElementByAttribute(child, name, value);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String readableError(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.trim().isEmpty() ? current.getClass().getName() : message;
    }

    private static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static int resolveScreenWidth() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            return minecraft == null ? DANMAKU_FALLBACK_WIDTH : Math.max(1, minecraft.displayWidth);
        } catch (RuntimeException exception) {
            return DANMAKU_FALLBACK_WIDTH;
        } catch (LinkageError error) {
            return DANMAKU_FALLBACK_WIDTH;
        }
    }

    private static int resolveScreenHeight() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            return minecraft == null ? 180 : Math.max(1, minecraft.displayHeight);
        } catch (RuntimeException exception) {
            return 180;
        } catch (LinkageError error) {
            return 180;
        }
    }

    private static void unregisterQuietly(UiHudDocumentRegistration registration) {
        if (registration == null) {
            return;
        }
        try {
            registration.unregister();
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("远程 HUD unregister 失败", exception);
        }
    }

    static final class OverlayDocumentParts {

        final ElementNode movingElement;
        final ElementNode contentElement;
        final ElementNode noticeElement;
        final TextNode noticeText;
        final RemoteHudOverlayMode mode;
        private final DialogPlacementSupport dialogPlacement;

        private OverlayDocumentParts(ElementNode movingElement, ElementNode contentElement, ElementNode noticeElement,
                TextNode noticeText, RemoteHudOverlayMode mode, DialogPlacementSupport dialogPlacement) {
            this.movingElement = movingElement;
            this.contentElement = contentElement;
            this.noticeElement = noticeElement;
            this.noticeText = noticeText;
            this.mode = mode;
            this.dialogPlacement = dialogPlacement;
        }

        void updateDialogPlacementForTest(int viewportWidth, int viewportHeight, TextMeasureService textMeasureService) {
            if (dialogPlacement != null) {
                dialogPlacement.updateForViewport(viewportWidth, viewportHeight, textMeasureService);
            }
        }
    }

    /**
     * DIALOG fixed 浮窗的初始放置状态。
     */
    private static final class DialogPlacementSupport {

        private final ElementNode root;
        private final ElementNode shell;
        private boolean placed;
        private boolean userMoved;
        private int lastViewportWidth = -1;
        private int lastViewportHeight = -1;

        private DialogPlacementSupport(ElementNode root, ElementNode shell) {
            this.root = root;
            this.shell = shell;
        }

        private void ensurePlacedForCurrentViewport() {
            if (!placed) {
                updateForViewport(resolveScreenWidth(), resolveScreenHeight());
            }
        }

        private void updateForViewport(int viewportWidth, int viewportHeight) {
            updateForViewport(viewportWidth, viewportHeight, DefaultTextMeasureService.getInstance());
        }

        private void updateForViewport(int viewportWidth, int viewportHeight, TextMeasureService textMeasureService) {
            int safeViewportWidth = Math.max(1, viewportWidth);
            int safeViewportHeight = Math.max(1, viewportHeight);
            if (userMoved) {
                if (placed && (safeViewportWidth != lastViewportWidth || safeViewportHeight != lastViewportHeight)) {
                    clampCurrentPosition(safeViewportWidth, safeViewportHeight, textMeasureService);
                }
                lastViewportWidth = safeViewportWidth;
                lastViewportHeight = safeViewportHeight;
                return;
            }
            if (!placed || safeViewportWidth != lastViewportWidth || safeViewportHeight != lastViewportHeight) {
                centerInViewport(safeViewportWidth, safeViewportHeight, textMeasureService);
            }
        }

        private void markUserMoved() {
            userMoved = true;
            placed = true;
        }

        private void centerInViewport(int viewportWidth, int viewportHeight, TextMeasureService textMeasureService) {
            Size shellSize = measureShell(viewportWidth, viewportHeight, textMeasureService);
            int left = clampPosition(Math.round((viewportWidth - shellSize.width) / 2.0F), viewportWidth,
                    shellSize.width, DIALOG_HORIZONTAL_MARGIN);
            int top = clampPosition(Math.round((viewportHeight - shellSize.height) / 2.0F), viewportHeight,
                    shellSize.height, DIALOG_VERTICAL_MARGIN);
            applyPosition(left, top);
            lastViewportWidth = viewportWidth;
            lastViewportHeight = viewportHeight;
            placed = true;
        }

        private void clampCurrentPosition(int viewportWidth, int viewportHeight, TextMeasureService textMeasureService) {
            Size shellSize = measureShell(viewportWidth, viewportHeight, textMeasureService);
            int left = clampPosition(resolvePixel(shell.style().getLeft(), 0), viewportWidth, shellSize.width,
                    DIALOG_HORIZONTAL_MARGIN);
            int top = clampPosition(resolvePixel(shell.style().getTop(), 0), viewportHeight, shellSize.height,
                    DIALOG_VERTICAL_MARGIN);
            applyPosition(left, top);
        }

        private Size measureShell(int viewportWidth, int viewportHeight, TextMeasureService textMeasureService) {
            TextMeasureService resolvedMeasureService = textMeasureService == null ? DefaultTextMeasureService.getInstance()
                    : textMeasureService;
            DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, viewportWidth, viewportHeight,
                    resolvedMeasureService);
            DocumentLayoutBox shellBox = findLayoutBox(rootBox, shell);
            if (shellBox == null) {
                return new Size(0, 0);
            }
            return new Size(Math.max(0, shellBox.getWidth()), Math.max(0, shellBox.getHeight()));
        }

        private void applyPosition(int left, int top) {
            shell.style()
                    .setPosition(UiPosition.FIXED)
                    .setLeft(UiStyleLength.px(left))
                    .setTop(UiStyleLength.px(top))
                    .clearRight()
                    .clearBottom()
                    .setVisibility(UiVisibility.VISIBLE);
        }

        private static DocumentLayoutBox findLayoutBox(DocumentLayoutBox box, ElementNode element) {
            if (box == null || element == null) {
                return null;
            }
            if (box.getElement() == element) {
                return box;
            }
            for (DocumentLayoutBox child : box.getChildren()) {
                DocumentLayoutBox found = findLayoutBox(child, element);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        private static int clampPosition(int value, int viewportSize, int elementSize, int margin) {
            int max = Math.max(margin, viewportSize - elementSize - margin);
            return Math.max(margin, Math.min(value, max));
        }

        private static int resolvePixel(UiStyleLength length, int fallback) {
            if (length == null || length.getType() != UiStyleLength.Type.PIXEL) {
                return fallback;
            }
            return Math.round(length.getValue());
        }
    }

    private static final class Size {

        private final int width;
        private final int height;

        private Size(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    private static final class ActiveOverlay {

        private final RemoteHudOverlays.OpenOffer offer;
        private final UiHudDocumentRegistration registration;
        private final OverlayDocumentParts parts;
        private final long openedAtMillis;
        private final long expiresAtMillis;
        private final RemoteHudOverlayMode mode;

        private ActiveOverlay(RemoteHudOverlays.OpenOffer offer, UiHudDocumentRegistration registration,
                OverlayDocumentParts parts, long openedAtMillis, RemoteHudOverlayMode mode) {
            this.offer = offer;
            this.registration = registration;
            this.parts = parts;
            this.openedAtMillis = openedAtMillis;
            this.expiresAtMillis = offer.durationMillis <= 0L ? Long.MAX_VALUE : openedAtMillis
                    + offer.durationMillis;
            this.mode = mode == null ? RemoteHudOverlayMode.DIALOG : mode;
        }

        private boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }

    private static final class PendingOpen {

        private final RemoteHudOverlays.OpenOffer offer;
        private volatile boolean dismissed;

        private PendingOpen(RemoteHudOverlays.OpenOffer offer) {
            this.offer = offer;
        }

        private void dismiss() {
            this.dismissed = true;
        }

        private boolean isDismissed() {
            return dismissed;
        }

        private boolean matchesOverlayId(String overlayId) {
            return offer != null && offer.overlayId != null && offer.overlayId.equals(overlayId);
        }
    }
}
