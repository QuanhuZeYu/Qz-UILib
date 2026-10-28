package club.heiqi.uilib.host.client.font.context;

/** 向中性宿主扩展提供平台共用的光栅状态捕获。 */
public final class HostRasterCapture {
    private HostRasterCapture() {}
    public static FontRenderContext.RasterState capture(LwjglFontContextCapture.RasterQueries queries) {
        return LwjglFontContextCapture.captureRaster(queries);
    }
}
