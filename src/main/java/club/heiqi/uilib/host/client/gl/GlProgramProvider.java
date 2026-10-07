package club.heiqi.uilib.host.client.gl;

import club.heiqi.uilib.host.client.font.context.FontRenderContext;

/** preview只读，不改变pass；open负责匹配捕获的program/pipeline代际。 */
public interface GlProgramProvider {
    boolean preview(FontRenderContext context);GlProgramScope open(FontRenderContext context);
    GlProgramProvider NONE=new GlProgramProvider(){public boolean preview(FontRenderContext context){return false;}public GlProgramScope open(FontRenderContext context){return null;}};
}
