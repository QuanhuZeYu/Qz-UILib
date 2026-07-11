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
    /** @return 带 UI widget 元数据的新 spec */
    public static ValueSpec widget(ValueSpec spec, WidgetSpec widget) {
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
        return spec.withWidget(widget);
    }
    /** @return 搜索选择器 widget 元数据 */
    public static SearchPickerSpec searchPicker(String editorId, int maxItems) {
        return new SearchPickerSpec(editorId, maxItems);
    }
    /** @return OBJECT spec */
    public static ValueSpec object(ValueSpec.Member... members) { return ValueSpec.object(members); }
    /**
     * 创建带可靠身份 member 声明的 OBJECT spec。
     *
     * @param identityMember 用于 keyed 列表复用的对象 member 名称
     * @param members 对象 member 定义
     * @return 带身份声明的 OBJECT spec
     * @throws IllegalArgumentException identity member 不是 STRING、NUMBER、BOOLEAN 或 CHOICE 标量
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
