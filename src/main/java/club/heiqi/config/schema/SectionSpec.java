package club.heiqi.config.schema;

import com.github.bsideup.jabel.Desugar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 分类声明，不可变。
 * 一个分类包含标识名、显示名以及该分类下的字段列表（保序）。
 * 内嵌 {@link Builder} 用于在 {@link ConfigSchema.Builder#section(String)} 作用域内添加字段。
 */
@Desugar
public record SectionSpec(
    /** 分类标识名 */
    String name,
    /** 显示名，未设置时默认等于 {@link #name()} */
    String title,
    /** 该分类下的字段列表，保序，不可修改 */
    List<FieldSpec> fields
) {
    /**
     * 紧凑构造器，对 fields 做不可变防御性拷贝，title 缺省回退到 name。
     */
    public SectionSpec {
        if (name == null) {
            throw new IllegalArgumentException("SectionSpec.name 不能为 null");
        }
        if (title == null) {
            title = name;
        }
        fields = Collections.unmodifiableList(new ArrayList<>(fields));
    }

    /**
     * 分类构建器，提供按类型添加字段的 DSL 方法。
     * 通过 {@link #endSection()} 返回父 {@link ConfigSchema.Builder}，回到 schema 作用域。
     */
    public static final class Builder {
        private final ConfigSchema.Builder parent;
        private final String name;
        private String title;
        private final List<FieldSpec> fields = new ArrayList<>();

        /**
         * 构造分类构建器。
         *
         * @param parent 父 schema 构建器
         * @param name   分类标识名
         */
        Builder(ConfigSchema.Builder parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        /**
         * 设置显示名。
         *
         * @param title 显示名
         * @return 当前构建器
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * 添加 STRING 类型字段。
         *
         * @param key 字段 key（不含分类前缀）
         * @return 字段构建器，默认值类型编译期绑定为 {@link String}
         */
        public FieldSpec.Builder<String> string(String key) {
            return new FieldSpec.Builder<>(this, name + "." + key, FieldType.STRING);
        }

        /**
         * 添加 NUMBER 类型字段。
         *
         * @param key 字段 key
         * @return 字段构建器，默认值类型编译期绑定为 {@link Double}
         */
        public FieldSpec.Builder<Double> number(String key) {
            return new FieldSpec.Builder<>(this, name + "." + key, FieldType.NUMBER);
        }

        /**
         * 添加 BOOLEAN 类型字段。
         *
         * @param key 字段 key
         * @return 字段构建器，默认值类型编译期绑定为 {@link Boolean}
         */
        public FieldSpec.Builder<Boolean> bool(String key) {
            return new FieldSpec.Builder<>(this, name + "." + key, FieldType.BOOLEAN);
        }

        /**
         * 添加 CHOICE 类型字段。
         *
         * @param key 字段 key
         * @return 字段构建器，默认值类型编译期绑定为 {@link String}
         */
        public FieldSpec.Builder<String> choice(String key) {
            return new FieldSpec.Builder<>(this, name + "." + key, FieldType.CHOICE);
        }

        /**
         * 添加 SIMPLE_LIST 类型字段（字符串列表）。
         *
         * @param key 字段 key
         * @return 字段构建器，默认值类型编译期绑定为 {@code List<String>}
         */
        public FieldSpec.Builder<List<String>> simpleList(String key) {
            return new FieldSpec.Builder<>(this, name + "." + key, FieldType.SIMPLE_LIST);
        }

        /** 添加由 ValueSpec 描述的结构化对象列表字段。 */
        public FieldSpec.Builder<List<java.util.Map<String, Object>>> structuredList(
                String key, ValueSpec elementSpec) {
            if (elementSpec == null || elementSpec.kind() != ValueKind.OBJECT) {
                throw new IllegalArgumentException("structuredList elementSpec must be OBJECT");
            }
            return new FieldSpec.Builder<>(this, name + "." + key, ValueSpec.list(elementSpec));
        }

        /**
         * 结束当前分类，返回父 schema 构建器。
         *
         * @return 父 schema 构建器
         */
        public ConfigSchema.Builder endSection() {
            parent.addSection(new SectionSpec(name, title, fields));
            return parent;
        }

        /**
         * 内部方法：将已构建的字段加入当前分类。
         *
         * @param field 字段元数据
         */
        void addField(FieldSpec field) {
            fields.add(field);
        }
    }
}
