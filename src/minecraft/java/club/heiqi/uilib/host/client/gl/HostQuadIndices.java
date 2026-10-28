package club.heiqi.uilib.host.client.gl;

/** 向宿主扩展提供平台共用的四边形索引生成器。 */
public final class HostQuadIndices {
    private HostQuadIndices() {}
    public static int[] create(int capacity) { return GlQuadRenderer.fontIndices(capacity); }
}
