package club.heiqi.uilib.ui.remote;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

import club.heiqi.uilib.net.api.NetResponse;
import club.heiqi.uilib.net.api.NetStreamCall;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.screen.UiDocumentScreens;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 远程页面客户端桥。
 *
 * <p>该类只在客户端通过反射加载，避免服务端类加载路径触碰 Minecraft 客户端类。</p>
 */
public final class RemoteDocumentClientBridge {

    private static final Object STATE_LOCK = new Object();
    private static final DocumentScreenOpener MINECRAFT_SCREEN_OPENER = new DocumentScreenOpener() {
        @Override
        public void open(UiDocumentScreens.DocumentScreenProvision provision) {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null) {
                return;
            }
            GuiScreen screen = UiDocumentScreens.createDocumentScreen(UiDocumentScreens.DocumentScreenEnvironment
                    .minecraftDefaults(), provision);
            minecraft.displayGuiScreen(screen);
        }
    };

    private static String currentSessionId = "";
    private static long currentGeneration;
    private static String replacingSessionId = "";
    private static long replacingGeneration;
    private static DocumentScreenOpener screenOpener = MINECRAFT_SCREEN_OPENER;

    private RemoteDocumentClientBridge() {}

    /**
     * 接收服务端下发的远程页面打开 offer。
     *
     * @param json offer JSON
     */
    public static void receiveOpenOffer(String json) {
        final RemoteDocumentPages.OpenOffer offer;
        try {
            offer = RemoteDocumentPages.decodeOpenOffer(json);
        } catch (IllegalArgumentException exception) {
            openErrorScreen("远程页面协议无效", exception.getMessage());
            return;
        }
        final long generation = markCurrentOffer(offer.sessionId);
        openLoadingScreen(offer, generation);
        NetStreamCall call;
        try {
            call = RemoteDocumentPages.callPageStream(offer.sessionId);
        } catch (RuntimeException exception) {
            if (isCurrentOffer(offer.sessionId, generation)) {
                openErrorScreen("远程页面请求失败", exception.getMessage());
            }
            return;
        }
        call.future().whenComplete(new BiConsumer<NetResponse, Throwable>() {
            @Override
            public void accept(final NetResponse response, final Throwable throwable) {
                UiScreenManager.getInstance().enqueue(new Runnable() {
                    @Override
                    public void run() {
                        if (!isCurrentOffer(offer.sessionId, generation)) {
                            return;
                        }
                        if (throwable != null) {
                            openErrorScreen("远程页面下载失败", readableError(throwable));
                            return;
                        }
                        try {
                            String html = validateAndDecode(offer, response);
                            openRemoteDocumentScreen(offer, html, generation);
                        } catch (RuntimeException exception) {
                            openErrorScreen("远程页面校验失败", exception.getMessage());
                        }
                    }
                });
            }
        });
    }

    /**
     * 接收服务端下发的远程页面 session 失效通知。
     *
     * @param json 失效通知 JSON
     */
    public static void receiveSessionExpired(String json) {
        RemoteDocumentPages.ExpiredPayload payload;
        try {
            payload = RemoteDocumentPages.decodeExpiredPayload(json);
        } catch (IllegalArgumentException exception) {
            openErrorScreen("远程页面失效通知无效", exception.getMessage());
            return;
        }
        if (!expireCurrentOffer(payload.sessionId)) {
            return;
        }
        openErrorScreen("远程页面已失效", "当前远程页面 session 已过期，请重新打开页面后再操作。");
    }

    private static String validateAndDecode(RemoteDocumentPages.OpenOffer offer, NetResponse response) {
        if (response == null) {
            throw new IllegalStateException("服务端未返回响应");
        }
        if (!response.isOk()) {
            throw new IllegalStateException("服务端返回错误 " + response.getStatusCode() + "："
                    + response.getBody().asUtf8String());
        }
        if (!RemoteDocumentPages.REMOTE_HTML_CONTENT_TYPE.equals(response.getContentType())) {
            throw new IllegalStateException("远程页面内容类型不匹配：" + response.getContentType());
        }
        if (!offer.sessionId.equals(response.getHeader("x-qz-session-id"))) {
            throw new IllegalStateException("远程页面 session 响应不匹配");
        }
        if (!offer.pageId.equals(response.getHeader("x-qz-page-id"))) {
            throw new IllegalStateException("远程页面 pageId 响应不匹配");
        }
        byte[] bytes = response.getBody().getBytes();
        if (bytes.length != offer.htmlBytes) {
            throw new IllegalStateException("远程页面大小不匹配，期望 " + offer.htmlBytes + "，实际 " + bytes.length);
        }
        String actualSha256 = RemoteDocumentPages.sha256Hex(bytes);
        if (!offer.sha256.equalsIgnoreCase(actualSha256)
                || !offer.sha256.equalsIgnoreCase(response.getHeader("x-qz-sha256"))) {
            throw new IllegalStateException("远程页面 SHA-256 校验失败");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void openRemoteDocumentScreen(final RemoteDocumentPages.OpenOffer offer, final String html,
            long generation) {
        final RemoteDocumentResourcePolicy policy = resolvePolicy(offer.resourcePolicy);
        openDocumentScreen(offer.sessionId, generation, new UiDocumentScreens.DocumentScreenContentBuilder() {
            @Override
            public void build(UiDocument document) {
                RemoteHtmlDocumentParser.parseInto(document, html,
                        RemoteHtmlDocumentParser.Options.of(offer.sessionId, offer.pageId, policy));
                installRemoteLinkHandler(document, policy);
            }
        });
    }

    private static void openLoadingScreen(final RemoteDocumentPages.OpenOffer offer, long generation) {
        openDocumentScreen(offer.sessionId, generation, new UiDocumentScreens.DocumentScreenContentBuilder() {
            @Override
            public void build(UiDocument document) {
                ElementNode root = document.getRootElement();
                root.style()
                        .setPadding(UiStyleLength.px(16))
                        .setTextColor(0xFFE5E7EB)
                        .setBackgroundColor(0xCC0F172A);
                ElementNode title = document.h2();
                title.appendRawText(offer.title == null || offer.title.trim().isEmpty()
                        ? "正在加载远程页面" : offer.title);
                ElementNode detail = document.p();
                detail.appendRawText("正在从服务端拉取页面内容...");
                root.append(title).append(detail);
            }
        });
    }

    private static void openErrorScreen(final String title, final String detail) {
        openDocumentScreen(null, 0L, new UiDocumentScreens.DocumentScreenContentBuilder() {
            @Override
            public void build(UiDocument document) {
                ElementNode root = document.getRootElement();
                root.style()
                        .setPadding(UiStyleLength.px(16))
                        .setTextColor(0xFFFFE4E6)
                        .setBackgroundColor(0xCC1F2937);
                ElementNode heading = document.h2();
                heading.appendRawText(title == null ? "远程页面错误" : title);
                heading.style().setTextColor(0xFFFCA5A5);
                ElementNode body = document.p();
                body.appendRawText(detail == null || detail.trim().isEmpty() ? "未知错误" : detail);
                root.append(heading).append(body);
            }
        });
    }

    private static void openDocumentScreen(final String sessionId, final long generation,
            UiDocumentScreens.DocumentScreenContentBuilder builder) {
        UiDocumentScreens.DocumentScreenLifecycle lifecycle = RemoteHtmlSessionGateway.isBlank(sessionId) ? null
                : new UiDocumentScreens.DocumentScreenLifecycle() {
                    @Override
                    public void onClosed() {
                        clearCurrentOfferFromScreen(sessionId, generation);
                    }
                };
        beginScreenReplacement(sessionId, generation);
        try {
            screenOpener.open(UiDocumentScreens.DocumentScreenProvision.of(builder, lifecycle));
        } finally {
            endScreenReplacement(sessionId, generation);
        }
    }

    static void resetForTests() {
        synchronized (STATE_LOCK) {
            currentSessionId = "";
            currentGeneration = 0L;
            replacingSessionId = "";
            replacingGeneration = 0L;
        }
        screenOpener = MINECRAFT_SCREEN_OPENER;
    }

    static void setDocumentScreenOpenerForTests(DocumentScreenOpener opener) {
        screenOpener = opener == null ? MINECRAFT_SCREEN_OPENER : opener;
    }

    private static long markCurrentOffer(String sessionId) {
        synchronized (STATE_LOCK) {
            currentSessionId = sessionId == null ? "" : sessionId;
            currentGeneration++;
            return currentGeneration;
        }
    }

    private static boolean isCurrentOffer(String sessionId, long generation) {
        synchronized (STATE_LOCK) {
            return currentGeneration == generation && currentSessionId.equals(sessionId == null ? "" : sessionId);
        }
    }

    private static boolean expireCurrentOffer(String sessionId) {
        synchronized (STATE_LOCK) {
            if (!currentSessionId.equals(sessionId == null ? "" : sessionId)) {
                return false;
            }
            currentSessionId = "";
            currentGeneration++;
            return true;
        }
    }

    private static void beginScreenReplacement(String sessionId, long generation) {
        synchronized (STATE_LOCK) {
            replacingSessionId = sessionId == null ? "" : sessionId;
            replacingGeneration = generation;
        }
    }

    private static void endScreenReplacement(String sessionId, long generation) {
        synchronized (STATE_LOCK) {
            if (replacingGeneration == generation && replacingSessionId.equals(sessionId == null ? "" : sessionId)) {
                replacingSessionId = "";
                replacingGeneration = 0L;
            }
        }
    }

    private static void clearCurrentOfferFromScreen(String sessionId, long generation) {
        synchronized (STATE_LOCK) {
            if (replacingGeneration == generation && replacingSessionId.equals(sessionId == null ? "" : sessionId)) {
                return;
            }
            if (currentGeneration == generation && currentSessionId.equals(sessionId == null ? "" : sessionId)) {
                currentSessionId = "";
                currentGeneration++;
            }
        }
    }

    private static void installRemoteLinkHandler(UiDocument document, final RemoteDocumentResourcePolicy policy) {
        final ElementNode notice = document.div();
        final TextNode noticeText = notice.appendRawText("");
        notice.style()
                .setDisplay(UiDisplay.NONE)
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(12))
                .setRight(UiStyleLength.px(12))
                .setBottom(UiStyleLength.px(12))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xEE111827)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF334155)
                .setTextColor(0xFFE5E7EB)
                .setZIndex(1000);
        document.getRootElement().append(notice);
        RemoteDocumentLinkSupport.install(document, policy, new RemoteDocumentLinkSupport.NoticeSink() {
            @Override
            public void showNotice(String message) {
                RemoteDocumentClientBridge.showNotice(notice, noticeText, message);
            }
        });
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

    private static String readableError(Throwable throwable) {
        Throwable current = throwable;
        if (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.trim().isEmpty() ? current.getClass().getName() : message;
    }

    interface DocumentScreenOpener {
        void open(UiDocumentScreens.DocumentScreenProvision provision);
    }

}
