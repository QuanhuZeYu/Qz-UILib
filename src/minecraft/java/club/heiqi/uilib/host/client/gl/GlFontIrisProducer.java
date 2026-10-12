package club.heiqi.uilib.host.client.gl;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;

import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Draw;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Quad;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** 有界自有vertex/index producer；program与uniform仍由active Iris Pass.use/宿主preDraw拥有。 */
public final class GlFontIrisProducer implements GlVertexProducer {
    private final GlApi gl;
    private int vao, vbo, ebo;
    private final int capacity;
    private ByteBuffer staging;
    private boolean initialized;

    public GlFontIrisProducer(GlApi gl, int capacity) {
        this.gl = gl;
        if (capacity <= 0 || capacity > Integer.MAX_VALUE / (4 * GlFontProgramLayout.STRIDE))
            throw new IllegalArgumentException("Iris staging容量超界");
        this.capacity = capacity;
        try {
            staging =
                    ByteBuffer.allocateDirect(capacity * 4 * GlFontProgramLayout.STRIDE)
                            .order(ByteOrder.nativeOrder());
            vao = gl.glGenVertexArrays();
            vbo = gl.glGenBuffers();
            ebo = gl.glGenBuffers();
        } catch (RuntimeException | Error error) {
            close();
            throw error;
        }
    }

    @Override
    public void configure(GlProgramScope scope) {
        GlFontProgramLayout layout = new GlFontProgramLayout(scope.attributes(), scope.programId());
        for (GlProgramAttribute a : scope.attributes())
            if ("mc_Entity".equals(a.name) || "at_midBlock".equals(a.name))
                scope.extended(a.name, a.type);
        scope.configure(vao, vbo, ebo, layout.uv, layout.color, layout.lightmap, layout.normal);
        for (int i = 0; i < gl.glGetInteger(GL_MAX_VERTEX_ATTRIBS); i++) scope.disable(i);
        for (GlFontProgramLayout.Binding b : layout.bindings) {
            scope.pointer(
                    b.location, b.size, GL_FLOAT, false, GlFontProgramLayout.STRIDE, b.offset);
            gl.glEnableVertexAttribArray(b.location);
            gl.glVertexAttribPointer(
                    b.location, b.size, GL_FLOAT, false, GlFontProgramLayout.STRIDE, b.offset);
        }
        if (!initialized) {
            gl.glBufferData(
                    GL_ARRAY_BUFFER,
                    (long) capacity * 4 * GlFontProgramLayout.STRIDE,
                    GL_STREAM_DRAW);
            gl.glBufferData(
                    GL_ELEMENT_ARRAY_BUFFER, GlQuadRenderer.fontIndices(capacity), GL_STATIC_DRAW);
            initialized = true;
        }
    }

    @Override
    public long consume(
            OrderedQuadBatch batch,
            GlProgramScope scope,
            FontRenderContext context,
            GlQuadRenderer.ResourceProvider resources) {
        if (batch.quads().size() > capacity)
            throw new IllegalArgumentException("Iris producer容量超限");
        staging.clear();
        float[] normal = scope.normal(), entity = null, midBlock = null;
        float alpha = context.state.color()[3];
        for (GlProgramAttribute a : scope.attributes())
            if ("mc_Entity".equals(a.name)) entity = scope.extended(a.name, a.type);
            else if ("at_midBlock".equals(a.name)) midBlock = scope.extended(a.name, a.type);
        for (Draw draw : batch.draws())
            for (int i = draw.firstQuad; i < draw.firstQuad + draw.quadCount; i++) {
                Quad q = batch.quads().get(i);
                boolean emoji = draw.state.material == GlQuadRenderer.COLOR_PREMULT;
                vertex(q, q.x0, q.y0, q.u0, q.v0, emoji, alpha, normal, entity, midBlock, scope);
                vertex(q, q.x1, q.y1, q.u1, q.v0, emoji, alpha, normal, entity, midBlock, scope);
                vertex(q, q.x2, q.y2, q.u1, q.v1, emoji, alpha, normal, entity, midBlock, scope);
                vertex(q, q.x3, q.y3, q.u0, q.v1, emoji, alpha, normal, entity, midBlock, scope);
            }
        staging.flip();
        long bytes = staging.remaining();
        gl.glBufferSubData(GL_ARRAY_BUFFER, 0, staging);
        for (Draw draw : batch.draws()) {
            if (draw.state.material == GlQuadRenderer.SOLID)
                throw new IllegalStateException("Iris SOLID必须由同预算atlas whiteview表示");
            GlQuadRenderer.Texture texture =
                    resources.resolve(draw.state.resource, draw.state.generation);
            if (texture == null || texture.target != GL_TEXTURE_2D)
                throw new IllegalStateException("Iris producer要求STRAIGHT_RGBA TEXTURE_2D");
            scope.bindTexture2D(texture.id);
            scope.beforeDraw();
            gl.glActiveTexture(GL_TEXTURE0);
            gl.glBindTexture(GL_TEXTURE_2D, texture.id);
            gl.glDrawElements(
                    GL_TRIANGLES,
                    draw.quadCount * 6,
                    GL_UNSIGNED_INT,
                    (long) draw.firstQuad * 6 * 4);
        }
        return bytes;
    }

    private void vertex(
            Quad q,
            float x,
            float y,
            float u,
            float v,
            boolean emoji,
            float alpha,
            float[] normal,
            float[] entity,
            float[] midBlock,
            GlProgramScope scope) {
        staging.putFloat(x).putFloat(y).putFloat(0).putFloat(1);
        staging.putFloat(emoji ? 1 : (q.color >>> 16 & 255) / 255f)
                .putFloat(emoji ? 1 : (q.color >>> 8 & 255) / 255f)
                .putFloat(emoji ? 1 : (q.color & 255) / 255f)
                .putFloat((q.color >>> 24) / 255f * alpha);
        staging.putFloat(u).putFloat(v).putFloat(0).putFloat(1);
        // UV1原始packedbrightness在shader中只通过捕获的unit1 matrix转换一次。
        staging.putFloat(scope.packedLight() & 65535)
                .putFloat(scope.packedLight() >>> 16 & 65535)
                .putFloat(0)
                .putFloat(1);
        for (int i = 0; i < 3; i++) staging.putFloat(normal[i]);
        staging.putFloat((q.u0 + q.u1) * 0.5f).putFloat((q.v0 + q.v1) * 0.5f);
        // quad caller平面的切向是+X，handedness为+1；宿主normal matrix处理到目标空间。
        staging.putFloat(1).putFloat(0).putFloat(0).putFloat(1);
        // 仅shader实际声明的known扩展字段读来源generic4；未声明的padding不充当unknown fallback。
        for (int i = 0; i < 4; i++) staging.putFloat(entity == null ? 0 : entity[i]);
        for (int i = 0; i < 4; i++) staging.putFloat(midBlock == null ? 0 : midBlock[i]);
    }

    @Override
    public void close() {
        if (staging != null) {
            staging = null;
        }
        gl.glDeleteBuffers(ebo);
        gl.glDeleteBuffers(vbo);
        gl.glDeleteVertexArrays(vao);
    }
}
