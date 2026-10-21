package club.heiqi.uilib.host.client.font.context;

/** 中性扩展复用平台同一光栅状态捕获，避免为可选宿主复制状态列表。 */
public final class HostRasterCapture {
    private HostRasterCapture() {}
    public static FontRenderContext.RasterState capture(LwjglFontContextCapture.RasterQueries queries) {
        return LwjglFontContextCapture.captureRaster(queries);
    }
}
