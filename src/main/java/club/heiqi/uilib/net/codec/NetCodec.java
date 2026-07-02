package club.heiqi.uilib.net.codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 紧凑反射 codec 入口。
 *
 * @param <T> 编解码类型
 */
public final class NetCodec<T> {

    private static final Map<Class<?>, NetCodec<?>> CUSTOM_CODECS = new ConcurrentHashMap<Class<?>, NetCodec<?>>();
    private static final int MAX_OBJECT_DEPTH = 64;

    private final Class<T> type;
    private final EncoderFn<T> customEncoder;
    private final DecoderFn<T> customDecoder;

    private NetCodec(Class<T> type, EncoderFn<T> customEncoder, DecoderFn<T> customDecoder) {
        this.type = type;
        this.customEncoder = customEncoder;
        this.customDecoder = customDecoder;
    }

    /**
     * 获取指定类型的 codec。
     *
     * @param type Java 类型
     * @param <T> 类型参数
     * @return codec
     */
    @SuppressWarnings("unchecked")
    public static <T> NetCodec<T> of(Class<T> type) {
        NetCodec<?> custom = CUSTOM_CODECS.get(type);
        if (custom != null) {
            return (NetCodec<T>) custom;
        }
        return new NetCodec<T>(type, null, null);
    }

    /**
     * 注册完全手写的 codec。
     *
     * @param type Java 类型
     * @param encoder 编码函数
     * @param decoder 解码函数
     * @param <T> 类型参数
     * @return 已注册 codec
     */
    public static <T> NetCodec<T> custom(Class<T> type, EncoderFn<T> encoder, DecoderFn<T> decoder) {
        NetCodec<T> codec = new NetCodec<T>(type, encoder, decoder);
        CUSTOM_CODECS.put(type, codec);
        return codec;
    }

    /**
     * 编码对象。
     *
     * @param value 对象
     * @return 二进制数据
     */
    public byte[] encode(T value) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            if (customEncoder != null) {
                customEncoder.encode(value, output);
            } else {
                writeValue(value, type, output, 0);
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new NetCodecException("编码 " + type.getName() + " 失败", exception);
        }
    }

    /**
     * 解码对象。
     *
     * @param payload 二进制数据
     * @return 对象
     */
    public T decode(byte[] payload) {
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
            if (customDecoder != null) {
                return customDecoder.decode(input);
            }
            return type.cast(readValue(type, input, 0));
        } catch (IOException exception) {
            throw new NetCodecException("解码 " + type.getName() + " 失败", exception);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void writeValue(Object value, Type declaredType, DataOutput output, int depth) throws IOException {
        if (depth > MAX_OBJECT_DEPTH) {
            throw new NetCodecException("对象嵌套过深，疑似循环引用");
        }
        output.writeBoolean(value != null);
        if (value == null) {
            return;
        }

        Class<?> rawType = resolveRawClass(declaredType);
        if (rawType == boolean.class || rawType == Boolean.class) {
            output.writeBoolean(((Boolean) value).booleanValue());
        } else if (rawType == byte.class || rawType == Byte.class) {
            output.writeByte(((Byte) value).byteValue());
        } else if (rawType == short.class || rawType == Short.class) {
            Varint.writeSignedInt(output, ((Short) value).shortValue());
        } else if (rawType == int.class || rawType == Integer.class) {
            Varint.writeSignedInt(output, ((Integer) value).intValue());
        } else if (rawType == long.class || rawType == Long.class) {
            Varint.writeSignedLong(output, ((Long) value).longValue());
        } else if (rawType == float.class || rawType == Float.class) {
            output.writeFloat(((Float) value).floatValue());
        } else if (rawType == double.class || rawType == Double.class) {
            output.writeDouble(((Double) value).doubleValue());
        } else if (rawType == char.class || rawType == Character.class) {
            Varint.writeUnsignedInt(output, ((Character) value).charValue());
        } else if (rawType == String.class) {
            writeBytes(output, ((String) value).getBytes(StandardCharsets.UTF_8));
        } else if (rawType == byte[].class) {
            writeBytes(output, (byte[]) value);
        } else if (rawType.isEnum()) {
            Varint.writeUnsignedInt(output, ((Enum) value).ordinal());
        } else if (Collection.class.isAssignableFrom(rawType)) {
            Type elementType = resolveTypeArgument(declaredType, 0);
            Collection<?> collection = (Collection<?>) value;
            Varint.writeUnsignedInt(output, collection.size());
            for (Object element : collection) {
                writeValue(element, elementType, output, depth + 1);
            }
        } else if (Map.class.isAssignableFrom(rawType)) {
            Type keyType = resolveTypeArgument(declaredType, 0);
            Type valueType = resolveTypeArgument(declaredType, 1);
            Map<?, ?> map = (Map<?, ?>) value;
            Varint.writeUnsignedInt(output, map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                writeValue(entry.getKey(), keyType, output, depth + 1);
                writeValue(entry.getValue(), valueType, output, depth + 1);
            }
        } else {
            NetCodec custom = CUSTOM_CODECS.get(rawType);
            if (custom != null) {
                byte[] nestedPayload = custom.encode(value);
                writeBytes(output, nestedPayload);
                return;
            }
            FieldLayout layout = FieldLayout.forClass(rawType);
            for (FieldLayout.FieldDescriptor descriptor : layout.getFields()) {
                Field field = descriptor.getField();
                try {
                    writeValue(field.get(value), descriptor.getGenericType(), output, depth + 1);
                } catch (IllegalAccessException exception) {
                    throw new NetCodecException("无法读取字段 " + field.getName(), exception);
                }
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Object readValue(Type declaredType, DataInput input, int depth) throws IOException {
        if (depth > MAX_OBJECT_DEPTH) {
            throw new NetCodecException("对象嵌套过深，疑似循环引用");
        }
        boolean present = input.readBoolean();
        Class<?> rawType = resolveRawClass(declaredType);
        if (!present) {
            return defaultValue(rawType);
        }

        if (rawType == boolean.class || rawType == Boolean.class) {
            return Boolean.valueOf(input.readBoolean());
        } else if (rawType == byte.class || rawType == Byte.class) {
            return Byte.valueOf(input.readByte());
        } else if (rawType == short.class || rawType == Short.class) {
            return Short.valueOf((short) Varint.readSignedInt(input));
        } else if (rawType == int.class || rawType == Integer.class) {
            return Integer.valueOf(Varint.readSignedInt(input));
        } else if (rawType == long.class || rawType == Long.class) {
            return Long.valueOf(Varint.readSignedLong(input));
        } else if (rawType == float.class || rawType == Float.class) {
            return Float.valueOf(input.readFloat());
        } else if (rawType == double.class || rawType == Double.class) {
            return Double.valueOf(input.readDouble());
        } else if (rawType == char.class || rawType == Character.class) {
            return Character.valueOf((char) Varint.readUnsignedInt(input));
        } else if (rawType == String.class) {
            return new String(readBytes(input), StandardCharsets.UTF_8);
        } else if (rawType == byte[].class) {
            return readBytes(input);
        } else if (rawType.isEnum()) {
            int ordinal = Varint.readUnsignedInt(input);
            Object[] constants = rawType.getEnumConstants();
            if (ordinal < 0 || ordinal >= constants.length) {
                throw new NetCodecException("枚举 " + rawType.getName() + " ordinal 越界：" + ordinal);
            }
            return constants[ordinal];
        } else if (Collection.class.isAssignableFrom(rawType)) {
            Type elementType = resolveTypeArgument(declaredType, 0);
            int size = Varint.readUnsignedInt(input);
            Collection collection = Set.class.isAssignableFrom(rawType)
                    ? new LinkedHashSet()
                    : new ArrayList(size);
            for (int index = 0; index < size; index++) {
                collection.add(readValue(elementType, input, depth + 1));
            }
            return collection;
        } else if (Map.class.isAssignableFrom(rawType)) {
            Type keyType = resolveTypeArgument(declaredType, 0);
            Type valueType = resolveTypeArgument(declaredType, 1);
            int size = Varint.readUnsignedInt(input);
            Map map = new LinkedHashMap(size);
            for (int index = 0; index < size; index++) {
                Object key = readValue(keyType, input, depth + 1);
                Object value = readValue(valueType, input, depth + 1);
                map.put(key, value);
            }
            return map;
        } else {
            NetCodec custom = CUSTOM_CODECS.get(rawType);
            if (custom != null) {
                return custom.decode(readBytes(input));
            }
            Object instance = instantiate(rawType);
            FieldLayout layout = FieldLayout.forClass(rawType);
            for (FieldLayout.FieldDescriptor descriptor : layout.getFields()) {
                Field field = descriptor.getField();
                Object fieldValue = readValue(descriptor.getGenericType(), input, depth + 1);
                try {
                    field.set(instance, fieldValue);
                } catch (IllegalAccessException exception) {
                    throw new NetCodecException("无法写入字段 " + field.getName(), exception);
                }
            }
            return instance;
        }
    }

    private static void writeBytes(DataOutput output, byte[] bytes) throws IOException {
        Varint.writeUnsignedInt(output, bytes.length);
        output.write(bytes);
    }

    private static byte[] readBytes(DataInput input) throws IOException {
        int length = Varint.readUnsignedInt(input);
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return bytes;
    }

    private static Class<?> resolveRawClass(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            Type raw = ((ParameterizedType) type).getRawType();
            if (raw instanceof Class<?>) {
                return (Class<?>) raw;
            }
        }
        throw new NetCodecException("暂不支持的字段类型：" + type);
    }

    private static Type resolveTypeArgument(Type type, int index) {
        if (!(type instanceof ParameterizedType)) {
            throw new NetCodecException("集合或 Map 字段必须声明具体泛型：" + type);
        }
        Type[] arguments = ((ParameterizedType) type).getActualTypeArguments();
        if (index < 0 || index >= arguments.length) {
            throw new NetCodecException("泛型参数索引越界：" + type);
        }
        return arguments[index];
    }

    private static Object instantiate(Class<?> rawType) {
        try {
            Constructor<?> constructor = rawType.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new NetCodecException("类型需要无参构造器：" + rawType.getName(), exception);
        }
    }

    private static Object defaultValue(Class<?> rawType) {
        if (!rawType.isPrimitive()) {
            return null;
        }
        if (rawType == boolean.class) {
            return Boolean.FALSE;
        }
        if (rawType == byte.class) {
            return Byte.valueOf((byte) 0);
        }
        if (rawType == short.class) {
            return Short.valueOf((short) 0);
        }
        if (rawType == int.class) {
            return Integer.valueOf(0);
        }
        if (rawType == long.class) {
            return Long.valueOf(0L);
        }
        if (rawType == float.class) {
            return Float.valueOf(0.0F);
        }
        if (rawType == double.class) {
            return Double.valueOf(0.0D);
        }
        if (rawType == char.class) {
            return Character.valueOf((char) 0);
        }
        return null;
    }

    /**
     * 自定义编码函数。
     *
     * @param <T> 类型参数
     */
    public interface EncoderFn<T> {

        /**
         * 编码对象。
         *
         * @param value 对象
         * @param output 输出目标
         * @throws IOException 写入失败
         */
        void encode(T value, DataOutput output) throws IOException;
    }

    /**
     * 自定义解码函数。
     *
     * @param <T> 类型参数
     */
    public interface DecoderFn<T> {

        /**
         * 解码对象。
         *
         * @param input 输入来源
         * @return 对象
         * @throws IOException 读取失败
         */
        T decode(DataInput input) throws IOException;
    }
}
