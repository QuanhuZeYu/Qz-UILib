package club.heiqi.uilib.host.client.font.context;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;

/** 标准字体方法的绘制语义适配；不查询或修改宿主有效缓存。 */
public final class StandardFontContext {
    private StandardFontContext() {}

    public static FontRenderContext from(FontRenderContext context) {
        FontRenderContext.MatrixState state = context.state;
        // 显式文字颜色替换current color，格式颜色已由布局保存。
        FontRenderContext.MatrixState fontState =
                new FontRenderContext.MatrixState(
                        state.projection(),
                        state.modelView(),
                        new float[] {1, 1, 1, 1},
                        state.alphaTest,
                        state.alphaFunction,
                        state.alphaReference,
                        state.lightmapActive,
                        state.lightmapTexture,
                        state.lightmapU,
                        state.lightmapV,
                        state.packedLight,
                        state.normal(),
                        state.lighting(),
                        state.blockEntityId);
        FontRenderContext.RasterState raster = context.rasterState;
        // 单色atlas存coverage（字体spec 5.2）；标准文字拥有coverage混合，不能继承caller的关闭blend。
        // typed replay输出直色，因此RGB乘一次源alpha，目标alpha采用覆盖率合成。其余历史限制仍来自caller。
        // 这里只创建值快照；backend恢复actual入口，Iris Pass.use仍拥有其blend override。
        FontRenderContext.RasterState fontRaster =
                raster == null
                        ? null
                        : new FontRenderContext.RasterState(
                                true,
                                GL_SRC_ALPHA,
                                GL_ONE_MINUS_SRC_ALPHA,
                                GL_ONE,
                                GL_ONE_MINUS_SRC_ALPHA,
                                GL_FUNC_ADD,
                                GL_FUNC_ADD,
                                raster.blendColor(),
                                raster.stencilEnabled,
                                raster.frontStencil,
                                raster.backStencil,
                                raster.colorMask(),
                                raster.depthRange(),
                                raster.cullEnabled,
                                raster.cullFace,
                                raster.frontFace,
                                raster.polygonOffsetFill,
                                raster.polygonOffsetFactor,
                                raster.polygonOffsetUnits);
        return new FontRenderContext(
                context.projection,
                context.drawFramebuffer,
                context.sourceFramebufferHeight,
                context.depthTest,
                context.depthFunction,
                context.depthWrite,
                context.scissorEnabled,
                context.scissor(),
                fontState,
                context.contextIdentity,
                context.revision,
                context.passKey,
                fontRaster);
    }
}
