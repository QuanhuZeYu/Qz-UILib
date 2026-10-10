package club.heiqi.uilib.host.client.gl;

/** 当前宿主program实际introspection属性，不跨program代际缓存。 */
public final class GlProgramAttribute {
    public final String name;
    public final int location, type, size;

    public GlProgramAttribute(String name, int location, int type, int size) {
        this.name = name;
        this.location = location;
        this.type = type;
        this.size = size;
    }
}
