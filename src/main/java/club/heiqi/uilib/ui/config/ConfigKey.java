package club.heiqi.uilib.ui.config;

import java.util.Objects;
import java.util.function.Predicate;

/** 类型化声明；实例身份用于 session 访问，存储名称用于 schema 唯一性诊断。 */
public final class ConfigKey<T> {
    private final String name;
    private final ConfigCodec<T> codec;
    private final T defaultValue;
    private final Predicate<? super T> validator;

    public ConfigKey(String name, ConfigCodec<T> codec, T defaultValue, Predicate<? super T> validator) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isEmpty() || !name.equals(name.trim())) throw new IllegalArgumentException("非法配置键: " + name);
        this.codec = Objects.requireNonNull(codec, "codec");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.defaultValue = checked(defaultValue);
    }

    public ConfigKey(String name, ConfigCodec<T> codec, T defaultValue) {
        this(name, codec, defaultValue, value -> true);
    }

    public String name() { return name; }
    public ConfigCodec<T> codec() { return codec; }
    public T defaultValue() { return defaultValue; }

    T checked(T value) {
        try {
            T frozen = Objects.requireNonNull(codec.snapshot(Objects.requireNonNull(value, "value")), "snapshot");
            if (!validator.test(frozen)) throw new IllegalArgumentException("校验不通过");
            return frozen;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("配置键 " + name + ": " + failure.getMessage(), failure);
        }
    }

    T decode(java.util.Properties properties) {
        T value;
        try {
            value = codec.decode(properties, name);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("配置键 " + name + ": " + failure.getMessage(), failure);
        }
        return checked(value == null ? defaultValue : value);
    }
}
