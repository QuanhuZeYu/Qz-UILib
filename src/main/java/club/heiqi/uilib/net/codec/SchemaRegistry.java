package club.heiqi.uilib.net.codec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

/**
 * 网络类型字典。
 */
public final class SchemaRegistry {

    private final AtomicInteger nextTypeId = new AtomicInteger(1);
    private final Map<Class<?>, TypeDescriptor> descriptorsByType = new ConcurrentHashMap<Class<?>, TypeDescriptor>();
    private final Map<Integer, TypeDescriptor> descriptorsById = new ConcurrentHashMap<Integer, TypeDescriptor>();

    /**
     * 注册类型并返回稳定在本进程内的 type id。
     *
     * @param type Java 类型
     * @return type id
     */
    public int register(Class<?> type) {
        TypeDescriptor existing = descriptorsByType.get(type);
        if (existing != null) {
            return existing.getTypeId();
        }
        int schemaHash = schemaHashOf(type);
        int id = nextTypeId.getAndIncrement();
        TypeDescriptor descriptor = new TypeDescriptor(id, type, schemaHash);
        TypeDescriptor raced = descriptorsByType.putIfAbsent(type, descriptor);
        if (raced != null) {
            return raced.getTypeId();
        }
        descriptorsById.put(Integer.valueOf(id), descriptor);
        return id;
    }

    /**
     * 根据 id 查找类型。
     *
     * @param typeId type id
     * @return 类型描述，未找到时返回 null
     */
    public TypeDescriptor findById(int typeId) {
        return descriptorsById.get(Integer.valueOf(typeId));
    }

    /**
     * 根据类型查找描述。
     *
     * @param type Java 类型
     * @return 类型描述，未注册时返回 null
     */
    public TypeDescriptor findByType(Class<?> type) {
        return descriptorsByType.get(type);
    }

    /**
     * 返回当前 schema 快照。
     *
     * @return 类型描述列表
     */
    public List<TypeDescriptor> snapshot() {
        List<TypeDescriptor> descriptors = new ArrayList<TypeDescriptor>(descriptorsById.values());
        Collections.sort(descriptors);
        return descriptors;
    }

    private static int schemaHashOf(Class<?> type) {
        if (!PrimitiveCodecs.isPrimitiveLike(type)) {
            return FieldLayout.forClass(type).getSchemaHash();
        }
        CRC32 crc32 = new CRC32();
        byte[] bytes = ("primitive:" + type.getName()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        crc32.update(bytes, 0, bytes.length);
        return (int) crc32.getValue();
    }

    /**
     * 类型字典条目。
     */
    public static final class TypeDescriptor implements Comparable<TypeDescriptor> {

        private final int typeId;
        private final Class<?> type;
        private final int schemaHash;

        private TypeDescriptor(int typeId, Class<?> type, int schemaHash) {
            this.typeId = typeId;
            this.type = type;
            this.schemaHash = schemaHash;
        }

        /**
         * 返回 type id。
         *
         * @return type id
         */
        public int getTypeId() {
            return typeId;
        }

        /**
         * 返回 Java 类型。
         *
         * @return Java 类型
         */
        public Class<?> getType() {
            return type;
        }

        /**
         * 返回 schema hash。
         *
         * @return schema hash
         */
        public int getSchemaHash() {
            return schemaHash;
        }

        @Override
        public int compareTo(TypeDescriptor other) {
            return Integer.compare(this.typeId, other.typeId);
        }
    }
}
