package club.heiqi.uilib.ui.remote;

/**
 * 远程文档资源访问策略。
 *
 * <p>远程 HTML 页面不执行脚本，资源策略只控制图片与外部链接这类客户端侧访问边界。</p>
 */
public enum RemoteDocumentResourcePolicy {

    /** 仅允许 Minecraft ResourceLocation 与页内锚点。 */
    LOCAL_RESOURCES_ONLY(false, false),

    /** 允许 HTTP/HTTPS 图片，但外部链接仍不打开。 */
    HTTP_IMAGES(true, false),

    /** 允许 HTTP/HTTPS 图片，并在客户端确认后打开 HTTP/HTTPS 链接。 */
    FULL_EXTERNAL_LINKS(true, true);

    private final boolean httpImagesAllowed;
    private final boolean externalLinksAllowed;

    RemoteDocumentResourcePolicy(boolean httpImagesAllowed, boolean externalLinksAllowed) {
        this.httpImagesAllowed = httpImagesAllowed;
        this.externalLinksAllowed = externalLinksAllowed;
    }

    /**
     * 判断是否允许页面图片访问 HTTP/HTTPS URL。
     *
     * @return 是否允许远程图片
     */
    public boolean allowsHttpImages() {
        return httpImagesAllowed;
    }

    /**
     * 判断是否允许页面链接在确认后打开 HTTP/HTTPS URL。
     *
     * @return 是否允许外部链接
     */
    public boolean allowsExternalLinks() {
        return externalLinksAllowed;
    }
}
