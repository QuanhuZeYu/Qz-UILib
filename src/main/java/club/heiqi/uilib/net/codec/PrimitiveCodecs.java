package club.heiqi.uilib.net.codec;

/**
 * 反射 codec 的基础类型判断工具。
 */
public final class PrimitiveCodecs {

    private PrimitiveCodecs() {}

    /**
     * 判断类型是否按基础值直接编码。
     *
     * @param type Java 类型
     * @return true 表示直接编码
     */
    public static boolean isPrimitiveLike(Class<?> type) {
        return type.isPrimitive()
                || type == Boolean.class
                || type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class
                || type == Float.class
                || type == Double.class
                || type == Character.class
                || type == String.class
                || type == byte[].class
                || type.isEnum();
    }
}
