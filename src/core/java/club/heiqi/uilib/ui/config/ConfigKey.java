package club.heiqi.uilib.ui.config;

import java.util.Objects;
import java.util.function.Predicate;

/** 类型化声明；点分 name 对应嵌套 YAML 路径，实例身份用于 session 访问。 */
public final class ConfigKey<T> {
    private final String name;
    private final ConfigCodec<T> codec;
    private final T defaultValue;
    private final Predicate<? super T> validator;
    private String comment;

    public ConfigKey(String name, ConfigCodec<T> codec, T defaultValue, Predicate<? super T> validator) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isEmpty() || !name.equals(name.trim())) throw new IllegalArgumentException("非法配置键: " + name);
        for (String segment : name.split("\\.", -1))
            if (segment.isEmpty()) throw new IllegalArgumentException("非法配置路径: " + name);
        this.codec = Objects.requireNonNull(codec, "codec");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.defaultValue = checked(defaultValue);
    }
    public ConfigKey(String name, ConfigCodec<T> codec, T defaultValue) { this(name, codec, defaultValue, value -> true); }
    public String name() { return name; }
    public ConfigCodec<T> codec() { return codec; }
    public T defaultValue() { return defaultValue; }
    /** 声明阶段添加说明并返回同一 key；保存不会覆盖已加载节点的用户注释。 */
    public ConfigKey<T> withComment(String comment) { this.comment = Objects.requireNonNull(comment, "comment"); return this; }
    public String comment() { return comment; }

    T checked(T value) {
        try {
            T frozen = codec.snapshot(value);
            if (!validator.test(frozen)) throw new ConfigException(name, "校验不通过");
            return frozen;
        } catch (ConfigException failure) {
            String path = failure.path();
            if (!path.equals(name) && !path.startsWith(name + ".") && !path.startsWith(name + "["))
                throw new ConfigException(ConfigCodecs.child(name, path), failure.getMessage(), failure);
            throw failure;
        } catch (RuntimeException failure) {
            throw new ConfigException(name, failure.getMessage(), failure);
        }
    }
    T read(Object tree) { return checked(codec.read(tree, name)); }
}
