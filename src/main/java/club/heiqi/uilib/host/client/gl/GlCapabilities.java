package club.heiqi.uilib.host.client.gl;

/** 实际driver能力快照，contextIdentity为不透明宿主身份，不从bindings版本猜GPU版本。 */
public final class GlCapabilities {
    public final Object contextIdentity;
    public final boolean openGL32, samplerObjects;

    public GlCapabilities(Object identity, boolean openGL32, boolean samplers) {
        if (identity == null) throw new NullPointerException("identity");
        contextIdentity = identity;
        this.openGL32 = openGL32;
        samplerObjects = samplers;
    }
}
