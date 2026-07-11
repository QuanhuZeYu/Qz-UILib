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
    /**
     * 创建带可靠身份 member 声明的 OBJECT spec。
     *
     * @param identityMember 用于 keyed 列表复用的对象 member 名称
     * @param members 对象 member 定义
     * @return 带身份声明的 OBJECT spec
     */
    public static ValueSpec objectWithIdentity(String identityMember, ValueSpec.Member... members) {
        return ValueSpec.object(members).withIdentityMember(identityMember);
    }
    /** @return OBJECT member */
    public static ValueSpec.Member member(String name, ValueSpec spec) {
        return new ValueSpec.Member(name, spec);
    }
    /** @return OBJECT member with a declared default */
    public static ValueSpec.Member member(String name, ValueSpec spec, Object defaultValue) {
        return new ValueSpec.Member(name, spec.withDefault(defaultValue));
    }
}
