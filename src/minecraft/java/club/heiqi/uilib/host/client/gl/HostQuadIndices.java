package club.heiqi.uilib.host.client.gl;

/** 正式宿主扩展共用平台的四边形索引生成器，不维护第二种几何布局。 */
public final class HostQuadIndices {
    private HostQuadIndices() {}
    public static int[] create(int capacity) { return GlQuadRenderer.fontIndices(capacity); }
}
