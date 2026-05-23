package club.heiqi.uilib.net.codec;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32;

/**
 * POJO 字段布局描述。
 *
 * <p>字段按父类优先、同层协议字段名排序，避免 JVM 反射返回顺序差异影响线协议。</p>
 */
public final class FieldLayout {

    private static final ClassValue<FieldLayout> CACHE = new ClassValue<FieldLayout>() {
        @Override
        protected FieldLayout computeValue(Class<?> type) {
            return build(type);
        }
    };

    private final Class<?> type;
    private final List<FieldDescriptor> fields;
    private final int schemaHash;

    private FieldLayout(Class<?> type, List<FieldDescriptor> fields, int schemaHash) {
        this.type = type;
        this.fields = fields;
        this.schemaHash = schemaHash;
    }

    /**
     * 获取类型的字段布局。
     *
     * @param type POJO 类型
     * @return 字段布局
     */
    public static FieldLayout forClass(Class<?> type) {
        return CACHE.get(type);
    }

    /**
     * 返回 POJO 类型。
     *
     * @return Java 类型
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * 返回稳定排序后的字段。
     *
     * @return 字段列表
     */
    public List<FieldDescriptor> getFields() {
        return fields;
    }

    /**
     * 返回 schema hash。
     *
     * @return schema hash
     */
    public int getSchemaHash() {
        return schemaHash;
    }

    private static FieldLayout build(Class<?> type) {
        List<FieldDescriptor> descriptors = new ArrayList<FieldDescriptor>();
        collectFields(type, descriptors);
        CRC32 crc32 = new CRC32();
        updateCrc(crc32, type.getName());
        for (FieldDescriptor descriptor : descriptors) {
            updateCrc(crc32, descriptor.getProtocolName());
            updateCrc(crc32, descriptor.getGenericType().toString());
            updateCrc(crc32, Integer.toString(descriptor.getSince()));
        }
        return new FieldLayout(type, Collections.unmodifiableList(descriptors), (int) crc32.getValue());
    }

    private static void collectFields(Class<?> type, List<FieldDescriptor> output) {
        Class<?> parent = type.getSuperclass();
        if (parent != null && parent != Object.class) {
            collectFields(parent, output);
        }

        List<Field> localFields = new ArrayList<Field>();
        for (Field field : type.getDeclaredFields()) {
            if (shouldSkip(field)) {
                continue;
            }
            field.setAccessible(true);
            localFields.add(field);
        }
        Collections.sort(localFields, new Comparator<Field>() {
            @Override
            public int compare(Field left, Field right) {
                return protocolNameOf(left).compareTo(protocolNameOf(right));
            }
        });

        for (Field field : localFields) {
            NetField netField = field.getAnnotation(NetField.class);
            int since = netField == null ? 1 : netField.since();
            output.add(new FieldDescriptor(field, protocolNameOf(field), since));
        }
    }

    private static boolean shouldSkip(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers)
                || Modifier.isTransient(modifiers)
                || field.isSynthetic()
                || field.getAnnotation(NetTransient.class) != null;
    }

    private static String protocolNameOf(Field field) {
        NetField netField = field.getAnnotation(NetField.class);
        if (netField != null && netField.name().trim().length() > 0) {
            return netField.name().trim();
        }
        return field.getName();
    }

    private static void updateCrc(CRC32 crc32, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        crc32.update(bytes, 0, bytes.length);
    }

    /**
     * 单个字段的协议描述。
     */
    public static final class FieldDescriptor {

        private final Field field;
        private final String protocolName;
        private final int since;

        private FieldDescriptor(Field field, String protocolName, int since) {
            this.field = field;
            this.protocolName = protocolName;
            this.since = since;
        }

        /**
         * 返回 Java 字段。
         *
         * @return 字段
         */
        public Field getField() {
            return field;
        }

        /**
         * 返回线协议字段名。
         *
         * @return 协议字段名
         */
        public String getProtocolName() {
            return protocolName;
        }

        /**
         * 返回字段引入版本。
         *
         * @return schema 版本
         */
        public int getSince() {
            return since;
        }

        /**
         * 返回字段泛型类型。
         *
         * @return 泛型类型
         */
        public Type getGenericType() {
            return field.getGenericType();
        }
    }
}
