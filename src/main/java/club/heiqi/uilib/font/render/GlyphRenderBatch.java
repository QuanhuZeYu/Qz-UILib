package club.heiqi.uilib.font.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.page.GlyphPage;

/**
 * 同一字符页的批渲染数据。
 */
public class GlyphRenderBatch {

    private final GlyphPage glyphPage;
    private final List<GlyphQuad> glyphQuads = new ArrayList<GlyphQuad>();

    /**
     * 创建字符页批次。
     *
     * @param glyphPage 字符页
     */
    public GlyphRenderBatch(GlyphPage glyphPage) {
        this.glyphPage = glyphPage;
    }

    /**
     * 添加字符四边形。
     *
     * @param quad 字符四边形
     */
    public void addQuad(GlyphQuad quad) {
        glyphQuads.add(quad);
    }

    public GlyphPage getGlyphPage() {
        return glyphPage;
    }

    public List<GlyphQuad> getGlyphQuads() {
        return Collections.unmodifiableList(glyphQuads);
    }

    public boolean isEmpty() {
        return glyphQuads.isEmpty();
    }
}
