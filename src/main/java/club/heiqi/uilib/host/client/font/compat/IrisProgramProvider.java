package club.heiqi.uilib.host.client.font.compat;

import java.util.Objects;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlProgramProvider;
import club.heiqi.uilib.host.client.gl.GlProgramScope;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;

/** MC outer的可选Iris接入；GPU binding由outer注入，缺席查询不创建scope或初始化GL。 */
public final class IrisProgramProvider implements GlProgramProvider {
    private final ClassLoader loader;
    private final GlApi gl;

    public IrisProgramProvider(ClassLoader loader, GlApi gl) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.gl = Objects.requireNonNull(gl, "gl");
    }

    @Override
    public boolean preview(FontRenderContext context) {
        return OptionalIrisProgramScope.preview(loader, context) != null;
    }

    @Override
    public GlProgramScope open(FontRenderContext context) {
        return OptionalIrisProgramScope.open(loader, gl, context);
    }
}
