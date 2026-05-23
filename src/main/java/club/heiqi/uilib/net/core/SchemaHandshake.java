package club.heiqi.uilib.net.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.net.codec.SchemaRegistry;
import club.heiqi.uilib.net.codec.Varint;

/**
 * schema 握手快照。
 */
public final class SchemaHandshake {

    private final List<Entry> entries;

    private SchemaHandshake(List<Entry> entries) {
        this.entries = entries;
    }

    /**
     * 从注册表创建握手。
     *
     * @param registry schema 注册表
     * @return 握手快照
     */
    public static SchemaHandshake fromRegistry(SchemaRegistry registry) {
        List<Entry> entries = new ArrayList<Entry>();
        for (SchemaRegistry.TypeDescriptor descriptor : registry.snapshot()) {
            entries.add(new Entry(descriptor.getTypeId(), descriptor.getType().getName(),
                    descriptor.getSchemaHash()));
        }
        return new SchemaHandshake(Collections.unmodifiableList(entries));
    }

    /**
     * 解码握手。
     *
     * @param payload 二进制数据
     * @return 握手快照
     */
    public static SchemaHandshake decode(byte[] payload) {
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload));
            int size = Varint.readUnsignedInt(input);
            List<Entry> entries = new ArrayList<Entry>(size);
            for (int index = 0; index < size; index++) {
                int typeId = Varint.readUnsignedInt(input);
                String className = readString(input);
                int schemaHash = input.readInt();
                entries.add(new Entry(typeId, className, schemaHash));
            }
            return new SchemaHandshake(Collections.unmodifiableList(entries));
        } catch (IOException exception) {
            throw new IllegalArgumentException("schema 握手解码失败", exception);
        }
    }

    /**
     * 编码握手。
     *
     * @return 二进制数据
     */
    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            Varint.writeUnsignedInt(output, entries.size());
            for (Entry entry : entries) {
                Varint.writeUnsignedInt(output, entry.getTypeId());
                writeString(output, entry.getClassName());
                output.writeInt(entry.getSchemaHash());
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("schema 握手编码失败", exception);
        }
    }

    public List<Entry> getEntries() {
        return entries;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        Varint.writeUnsignedInt(output, bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = Varint.readUnsignedInt(input);
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 单个类型条目。
     */
    public static final class Entry {

        private final int typeId;
        private final String className;
        private final int schemaHash;

        private Entry(int typeId, String className, int schemaHash) {
            this.typeId = typeId;
            this.className = className;
            this.schemaHash = schemaHash;
        }

        public int getTypeId() {
            return typeId;
        }

        public String getClassName() {
            return className;
        }

        public int getSchemaHash() {
            return schemaHash;
        }
    }
}
