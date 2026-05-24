package club.heiqi.uilib.ui.remote;

import java.awt.Desktop;
import java.net.URI;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationEvent;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationHandler;
import club.heiqi.uilib.ui.dom.UiDocument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNoCallback;

/**
 * 远程 HTML 链接激活支持。
 *
 * <p>远程页面与远程 HUD 共用同一套安全链接语义：页内锚点由运行时处理，
 * HTTP/HTTPS 外链必须经过客户端确认，其他协议一律阻止。</p>
 */
final class RemoteDocumentLinkSupport {

    private RemoteDocumentLinkSupport() {}

    /**
     * 安装远程链接处理器。
     *
     * @param document 目标文档
     * @param policy 资源策略
     * @param noticeSink 页面内提示出口；可为 null
     */
    static void install(UiDocument document, final RemoteDocumentResourcePolicy policy,
            final NoticeSink noticeSink) {
        if (document == null) {
            return;
        }
        final RemoteDocumentResourcePolicy resolvedPolicy = policy == null
                ? RemoteDocumentResourcePolicy.FULL_EXTERNAL_LINKS : policy;
        final NoticeSink resolvedNoticeSink = noticeSink == null ? NoticeSink.NOOP : noticeSink;
        document.setLinkActivationHandler(new DocumentLinkActivationHandler() {
            @Override
            public void onLinkActivated(DocumentLinkActivationEvent event) {
                String href = event.getHref() == null ? "" : event.getHref().trim();
                if (href.startsWith("#")) {
                    return;
                }
                event.preventDefault();
                if (!isHttpUrl(href)) {
                    resolvedNoticeSink.showNotice("已阻止不安全链接：" + href);
                    return;
                }
                if (!resolvedPolicy.allowsExternalLinks()) {
                    resolvedNoticeSink.showNotice("当前页面策略不允许打开外部链接");
                    return;
                }
                confirmExternalLink(href, resolvedNoticeSink);
            }
        });
    }

    private static void confirmExternalLink(final String href, final NoticeSink noticeSink) {
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
                    noticeSink.showNotice("已取消打开外部链接");
                    return;
                }
                try {
                    openSystemBrowser(href);
                    noticeSink.showNotice("已请求系统浏览器打开链接");
                } catch (RuntimeException exception) {
                    MyMod.LOG.warn("远程页面外部链接打开失败：{}", href, exception);
                    noticeSink.showNotice("外部链接打开失败：" + exception.getMessage());
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

    private static boolean isHttpUrl(String href) {
        return href != null && (href.regionMatches(true, 0, "http://", 0, 7)
                || href.regionMatches(true, 0, "https://", 0, 8));
    }

    /**
     * 页面内可见提示出口。
     */
    interface NoticeSink {

        NoticeSink NOOP = new NoticeSink() {
            @Override
            public void showNotice(String message) {
            }
        };

        /**
         * 显示提示文本。
         *
         * @param message 提示文本
         */
        void showNotice(String message);
    }
}
