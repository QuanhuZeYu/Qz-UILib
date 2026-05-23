package club.heiqi.uilib.ui.remote;

import java.awt.Desktop;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.api.NetResponse;
import club.heiqi.uilib.net.api.NetStreamCall;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationEvent;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationHandler;
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
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNoCallback;

/**
 * 远程页面客户端桥。
 *
 * <p>该类只在客户端通过反射加载，避免服务端类加载路径触碰 Minecraft 客户端类。</p>
 */
public final class RemoteDocumentClientBridge {

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
        openLoadingScreen(offer);
        NetStreamCall call;
        try {
            call = RemoteDocumentPages.callPageStream(offer.sessionId);
        } catch (RuntimeException exception) {
            openErrorScreen("远程页面请求失败", exception.getMessage());
            return;
        }
        call.future().whenComplete(new BiConsumer<NetResponse, Throwable>() {
            @Override
            public void accept(final NetResponse response, final Throwable throwable) {
                UiScreenManager.getInstance().enqueue(new Runnable() {
                    @Override
                    public void run() {
                        if (throwable != null) {
                            openErrorScreen("远程页面下载失败", readableError(throwable));
                            return;
                        }
                        try {
                            String html = validateAndDecode(offer, response);
                            openRemoteDocumentScreen(offer, html);
                        } catch (RuntimeException exception) {
                            openErrorScreen("远程页面校验失败", exception.getMessage());
                        }
                    }
                });
            }
        });
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

    private static void openRemoteDocumentScreen(final RemoteDocumentPages.OpenOffer offer, final String html) {
        final RemoteDocumentResourcePolicy policy = resolvePolicy(offer.resourcePolicy);
        openDocumentScreen(new UiDocumentScreens.DocumentScreenContentBuilder() {
            @Override
            public void build(UiDocument document) {
                RemoteHtmlDocumentParser.parseInto(document, html,
                        RemoteHtmlDocumentParser.Options.of(offer.sessionId, offer.pageId, policy));
                installRemoteLinkHandler(document, policy);
            }
        });
    }

    private static void openLoadingScreen(final RemoteDocumentPages.OpenOffer offer) {
        openDocumentScreen(new UiDocumentScreens.DocumentScreenContentBuilder() {
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
        openDocumentScreen(new UiDocumentScreens.DocumentScreenContentBuilder() {
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

    private static void openDocumentScreen(UiDocumentScreens.DocumentScreenContentBuilder builder) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        GuiScreen screen = UiDocumentScreens.createDocumentScreen(builder);
        minecraft.displayGuiScreen(screen);
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
        document.setLinkActivationHandler(new DocumentLinkActivationHandler() {
            @Override
            public void onLinkActivated(DocumentLinkActivationEvent event) {
                String href = event.getHref() == null ? "" : event.getHref().trim();
                if (href.startsWith("#")) {
                    return;
                }
                event.preventDefault();
                if (!isHttpUrl(href)) {
                    showNotice(notice, noticeText, "已阻止不安全链接：" + href);
                    return;
                }
                if (!policy.allowsExternalLinks()) {
                    showNotice(notice, noticeText, "当前页面策略不允许打开外部链接");
                    return;
                }
                confirmExternalLink(href, notice, noticeText);
            }
        });
    }

    private static void confirmExternalLink(final String href, final ElementNode notice, final TextNode noticeText) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen previousScreen = minecraft.currentScreen;
        GuiConfirmOpenLink confirm = new GuiConfirmOpenLink(new GuiYesNoCallback() {
            @Override
            public void confirmClicked(boolean result, int id) {
                minecraft.displayGuiScreen(previousScreen);
                if (!result) {
                    showNotice(notice, noticeText, "已取消打开外部链接");
                    return;
                }
                try {
                    openSystemBrowser(href);
                    showNotice(notice, noticeText, "已请求系统浏览器打开链接");
                } catch (RuntimeException exception) {
                    MyMod.LOG.warn("远程页面外部链接打开失败：{}", href, exception);
                    showNotice(notice, noticeText, "外部链接打开失败：" + exception.getMessage());
                }
            }
        }, href, 0, false);
        confirm.func_146358_g();
        minecraft.displayGuiScreen(confirm);
    }

    private static void openSystemBrowser(String href) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IllegalStateException("当前环境不支持系统浏览器");
            }
            Desktop.getDesktop().browse(URI.create(href));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
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

    private static boolean isHttpUrl(String href) {
        return href != null && (href.regionMatches(true, 0, "http://", 0, 7)
                || href.regionMatches(true, 0, "https://", 0, 8));
    }
}
