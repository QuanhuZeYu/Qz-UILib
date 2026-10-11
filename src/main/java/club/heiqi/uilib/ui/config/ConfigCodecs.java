package club.heiqi.uilib.ui.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/** 可组合的声明类型；缺失由字段声明处理，null 仅由 nullable 接受。 */
public final class ConfigCodecs {
    private ConfigCodecs() {}

    private static <T> ConfigCodec<T> scalar(Class<T> type) {
        return new ConfigCodec<T>() {
            public T read(Object value, String path) {
                if (!type.isInstance(value)) throw new ConfigException(path, "需要 " + type.getSimpleName());
                return type.cast(value);
            }
            public Object write(T value) { return snapshot(value); }
            public T snapshot(T value) { return type.cast(Objects.requireNonNull(value, "value")); }
        };
    }
    public static final ConfigCodec<Boolean> BOOLEAN = scalar(Boolean.class);
    public static final ConfigCodec<String> STRING = scalar(String.class);
    public static final ConfigCodec<Integer> INTEGER = new ConfigCodec<Integer>() {
        public Integer read(Object value, String path) {
            if (!(value instanceof Number) || value instanceof Float || value instanceof Double || value instanceof BigDecimal)
                throw new ConfigException(path, "需要整数");
            try { return new BigDecimal(value.toString()).intValueExact(); }
            catch (ArithmeticException failure) { throw new ConfigException(path, "整数超出 Integer 范围", failure); }
        }
        public Object write(Integer value) { return snapshot(value); }
        public Integer snapshot(Integer value) { return Objects.requireNonNull(value, "value"); }
    };
    public static final ConfigCodec<Double> DOUBLE = new ConfigCodec<Double>() {
        public Double read(Object value, String path) {
            if (!(value instanceof Number)) throw new ConfigException(path, "需要数值");
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number) || number == 0.0 && new BigDecimal(value.toString()).signum() != 0)
                throw new ConfigException(path, "浮点数必须有限且在 Double 范围内");
            return number;
        }
        public Object write(Double value) { return snapshot(value); }
        public Double snapshot(Double value) {
            Objects.requireNonNull(value, "value");
            if (!Double.isFinite(value)) throw new IllegalArgumentException("浮点数必须有限");
            return value;
        }
    };

    public static <T> ConfigCodec<T> nullable(ConfigCodec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        return new ConfigCodec<T>() {
            public Map<String, String> comments(T value) { return value == null ? Collections.emptyMap() : codec.comments(value); }
            public T read(Object value, String path) { return value == null ? null : codec.read(value, path); }
            public Object write(T value) { return value == null ? null : codec.write(value); }
            public T snapshot(T value) { return value == null ? null : codec.snapshot(value); }
        };
    }

    public static <T> ConfigCodec<List<T>> listOf(ConfigCodec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        return new ConfigCodec<List<T>>() {
            public Map<String, String> comments(List<T> value) {
                Map<String, String> result = new LinkedHashMap<>();
                for (int i = 0; i < value.size(); i++)
                    for (Map.Entry<String, String> entry : codec.comments(value.get(i)).entrySet())
                        result.put(child("[" + i + "]", entry.getKey()), entry.getValue());
                return Collections.unmodifiableMap(result);
            }
            public List<T> read(Object value, String path) {
                if (!(value instanceof List)) throw new ConfigException(path, "需要列表");
                List<?> input = (List<?>) value;
                List<T> result = new ArrayList<>();
                for (int i = 0; i < input.size(); i++) result.add(codec.read(input.get(i), path + "[" + i + "]"));
                return snapshot(result);
            }
            public Object write(List<T> value) {
                List<Object> result = new ArrayList<>();
                for (T item : value) result.add(codec.write(item));
                return Collections.unmodifiableList(result);
            }
            public List<T> snapshot(List<T> value) {
                List<T> result = new ArrayList<>();
                for (T item : Objects.requireNonNull(value, "value")) {
                    String path = "[" + result.size() + "]";
                    try { result.add(codec.snapshot(item)); }
                    catch (ConfigException failure) { throw new ConfigException(child(path, failure.path()), failure.getMessage(), failure); }
                    catch (RuntimeException failure) { throw new ConfigException(path, failure.getMessage(), failure); }
                }
                return Collections.unmodifiableList(result);
            }
        };
    }
    public static final ConfigCodec<List<String>> STRING_LIST = listOf(STRING);

    public static <T> ConfigCodec<Map<String, T>> mapOf(ConfigCodec<T> codec) {
        Objects.requireNonNull(codec, "codec");
        return new ConfigCodec<Map<String, T>>() {
            public Map<String, String> comments(Map<String, T> value) {
                Map<String, String> result = new LinkedHashMap<>();
                for (Map.Entry<String, T> item : value.entrySet())
                    for (Map.Entry<String, String> entry : codec.comments(item.getValue()).entrySet())
                        result.put(child(item.getKey(), entry.getKey()), entry.getValue());
                return Collections.unmodifiableMap(result);
            }
            public Map<String, T> read(Object value, String path) {
                if (!(value instanceof Map)) throw new ConfigException(path, "需要映射");
                Map<String, T> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!(entry.getKey() instanceof String)) throw new ConfigException(path, "映射键必须是 string");
                    String key = (String) entry.getKey();
                    result.put(key, codec.read(entry.getValue(), child(path, key)));
                }
                return snapshot(result);
            }
            public Object write(Map<String, T> value) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<String, T> entry : value.entrySet()) result.put(entry.getKey(), codec.write(entry.getValue()));
                return Collections.unmodifiableMap(result);
            }
            public Map<String, T> snapshot(Map<String, T> value) {
                Map<String, T> result = new LinkedHashMap<>();
                for (Map.Entry<String, T> entry : Objects.requireNonNull(value, "value").entrySet()) {
                    String path = Objects.requireNonNull(entry.getKey(), "map key");
                    try { result.put(path, codec.snapshot(entry.getValue())); }
                    catch (ConfigException failure) { throw new ConfigException(child(path, failure.path()), failure.getMessage(), failure); }
                    catch (RuntimeException failure) { throw new ConfigException(path, failure.getMessage(), failure); }
                }
                return Collections.unmodifiableMap(result);
            }
        };
    }

    static String child(String path, String name) {
        if (path.isEmpty()) return name;
        if (name.isEmpty()) return path;
        return path + (name.startsWith("[") ? "" : ".") + name;
    }
    public static <T> ObjectBuilder<T> object() { return new ObjectBuilder<>(); }

    /** 显式字段 schema。factory 得到冻结映射与冻结 children；业务类型自己保持不可变。 */
    public static final class ObjectBuilder<T> {
        private final Map<String, Field<T, ?>> fields = new LinkedHashMap<>();
        private final Map<String, String> comments = new LinkedHashMap<>();
        public <V> ObjectBuilder<T> field(String name, ConfigCodec<V> codec, V defaultValue, Function<? super T, ? extends V> getter, String comment) {
            field(name, codec, defaultValue, getter);
            comments.put(name, Objects.requireNonNull(comment, "comment"));
            return this;
        }
        public <V> ObjectBuilder<T> field(String name, ConfigCodec<V> codec, V defaultValue, Function<? super T, ? extends V> getter) {
            return field(name, codec, defaultValue, getter, value -> true);
        }
        public <V> ObjectBuilder<T> field(String name, ConfigCodec<V> codec, V defaultValue, Function<? super T, ? extends V> getter, Predicate<? super V> validator) {
            return add(name, new Field<>(codec, defaultValue, getter, validator, false));
        }
        public <V> ObjectBuilder<T> requiredField(String name, ConfigCodec<V> codec, Function<? super T, ? extends V> getter) {
            return add(name, new Field<>(codec, null, getter, value -> true, true));
        }
        private ObjectBuilder<T> add(String name, Field<T, ?> field) {
            if (name == null || name.isEmpty() || name.indexOf('.') >= 0 || fields.containsKey(name))
                throw new IllegalArgumentException("非法或重复对象字段: " + name);
            fields.put(name, field);
            return this;
        }
        public ConfigCodec<T> build(Function<Map<String, Object>, T> factory) {
            Objects.requireNonNull(factory, "factory");
            Map<String, Field<T, ?>> declaration = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            Map<String, String> descriptions = new LinkedHashMap<>(comments);
            for (Map.Entry<String, Field<T, ?>> field : declaration.entrySet())
                for (Map.Entry<String, String> child : field.getValue().codec.comments().entrySet())
                    descriptions.put(ConfigCodecs.child(field.getKey(), child.getKey()), child.getValue());
            Map<String, String> frozenComments = Collections.unmodifiableMap(descriptions);
            return new ConfigCodec<T>() {
                public Map<String, String> comments() { return frozenComments; }
                public Map<String, String> comments(T value) {
                    Map<String, String> result = new LinkedHashMap<>(frozenComments);
                    for (Map.Entry<String, Field<T, ?>> field : declaration.entrySet())
                        for (Map.Entry<String, String> entry : field.getValue().comments(value).entrySet())
                            result.put(child(field.getKey(), entry.getKey()), entry.getValue());
                    return Collections.unmodifiableMap(result);
                }
                public T read(Object value, String path) {
                    if (!(value instanceof Map)) throw new ConfigException(path, "需要对象映射");
                    Map<?, ?> input = (Map<?, ?>) value;
                    for (Object name : input.keySet()) if (!declaration.containsKey(name))
                        throw new ConfigException(child(path, String.valueOf(name)), "未知字段");
                    Map<String, Object> children = new LinkedHashMap<>();
                    for (Map.Entry<String, Field<T, ?>> field : declaration.entrySet()) {
                        String name = field.getKey();
                        children.put(name, field.getValue().read(input, name, child(path, name)));
                    }
                    try { return Objects.requireNonNull(factory.apply(Collections.unmodifiableMap(children)), "factory result"); }
                    catch (ConfigException failure) { throw failure; }
                    catch (RuntimeException failure) { throw new ConfigException(path, "对象构造校验失败: " + failure.getMessage(), failure); }
                }
                public Object write(T value) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    for (Map.Entry<String, Field<T, ?>> field : declaration.entrySet())
                        result.put(field.getKey(), field.getValue().write(value));
                    return Collections.unmodifiableMap(result);
                }
                public T snapshot(T value) {
                    Objects.requireNonNull(value, "value");
                    Map<String, Object> children = new LinkedHashMap<>();
                    for (Map.Entry<String, Field<T, ?>> field : declaration.entrySet())
                        children.put(field.getKey(), field.getValue().freeze(value, field.getKey()));
                    return Objects.requireNonNull(factory.apply(Collections.unmodifiableMap(children)), "factory result");
                }
            };
        }
    }
    private static final class Field<T, V> {
        final ConfigCodec<V> codec;
        final V defaultValue;
        final Function<? super T, ? extends V> getter;
        final Predicate<? super V> validator;
        final boolean required;
        Field(ConfigCodec<V> codec, V value, Function<? super T, ? extends V> getter, Predicate<? super V> validator, boolean required) {
            this.codec = Objects.requireNonNull(codec, "codec");
            this.getter = Objects.requireNonNull(getter, "getter");
            this.validator = Objects.requireNonNull(validator, "validator");
            this.required = required;
            defaultValue = required ? null : checked(value, "default");
        }
        V checked(V value, String path) {
            try {
                V frozen = codec.snapshot(value);
                if (!validator.test(frozen)) throw new ConfigException(path, "字段校验不通过");
                return frozen;
            } catch (ConfigException failure) {
                String childPath = failure.path();
                if (childPath.equals(path) || childPath.startsWith(path + ".") || childPath.startsWith(path + "[")) throw failure;
                throw new ConfigException(child(path, childPath), failure.getMessage(), failure);
            } catch (RuntimeException failure) { throw new ConfigException(path, failure.getMessage(), failure); }
        }
        V read(Map<?, ?> input, String name, String path) {
            if (!input.containsKey(name)) {
                if (required) throw new ConfigException(path, "缺少必填字段");
                return checked(defaultValue, path);
            }
            return checked(codec.read(input.get(name), path), path);
        }
        V freeze(T value, String path) { return checked(getter.apply(value), path); }
        Object write(T value) { return codec.write(getter.apply(value)); }
        Map<String, String> comments(T value) { return codec.comments(getter.apply(value)); }
    }
}
