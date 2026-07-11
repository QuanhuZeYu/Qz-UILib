package club.heiqi.config.schema;

/** 递归配置值 DSL 的静态入口。 */
public final class Values {
    private Values() { }

    /** @return STRING spec */
    public static ValueSpec string() { return ValueSpec.string(); }
    /** @return NUMBER spec */
    public static ValueSpec number() { return ValueSpec.number(); }
    /** @return BOOLEAN spec */
    public static ValueSpec bool() { return ValueSpec.bool(); }
    /** @return CHOICE spec */
    public static ValueSpec choice(String... options) { return ValueSpec.choice(options); }
    /** @return LIST spec */
    public static ValueSpec list(ValueSpec element) { return ValueSpec.list(element); }
    /** @return OBJECT spec */
    public static ValueSpec object(ValueSpec.Member... members) { return ValueSpec.object(members); }
    /** @return OBJECT member */
    public static ValueSpec.Member member(String name, ValueSpec spec) {
        return new ValueSpec.Member(name, spec);
    }
    /** @return OBJECT member with a declared default */
    public static ValueSpec.Member member(String name, ValueSpec spec, Object defaultValue) {
        return new ValueSpec.Member(name, spec.withDefault(defaultValue));
    }
}
