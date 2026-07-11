package club.heiqi.config.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema 根，不可变。承载 mod 标识与保序分类列表，并提供按全路径 O(1) 查询字段的索引。
 * 通过 {@link #builder(String)} 入口使用 Builder DSL 声明式构建。
 */
public final class ConfigSchema {
    private final String modId;
    /** 人类可读标题，缺省回退 modId（供 UI titleBar 显示） */
    private final String title;
    private final List<SectionSpec> sections;
    private final Map<String, FieldSpec> byPath;

    /**
     * 构造不可变 Schema，title 缺省回退 modId，内部构建 byPath 索引。
     *
     * @param modId    mod 标识
     * @param sections 分类列表（保序）
     */
    public ConfigSchema(String modId, List<SectionSpec> sections) {
        this(modId, modId, sections);
    }

    /**
     * 构造不可变 Schema，内部构建 byPath 索引。
     *
     * @param modId    mod 标识
     * @param title    人类可读标题，null 时回退 modId
     * @param sections 分类列表（保序）
     */
    public ConfigSchema(String modId, String title, List<SectionSpec> sections) {
        if (modId == null) {
            throw new IllegalArgumentException("ConfigSchema.modId 不能为 null");
        }
        this.modId = modId;
        this.title = title == null ? modId : title;
        this.sections = Collections.unmodifiableList(new ArrayList<SectionSpec>(sections));
        Map<String, FieldSpec> map = new LinkedHashMap<String, FieldSpec>();
        for (SectionSpec s : this.sections) {
            for (FieldSpec f : s.fields()) {
                map.put(f.path(), f);
            }
        }
        this.byPath = Collections.unmodifiableMap(map);
    }

    /**
     * 获取 mod 标识。
     *
     * @return modId
     */
    public String modId() {
        return modId;
    }

    /**
     * 获取人类可读标题，缺省回退 modId。
     *
     * @return title
     */
    public String title() {
        return title;
    }

    /**
     * 获取保序分类列表，不可修改。
     *
     * @return 分类列表
     */
    public List<SectionSpec> sections() {
        return sections;
    }

    /**
     * 按全路径查字段，O(1)。
     *
     * @param path 全路径，例如 "general.scale"
     * @return 字段元数据，不存在返回 null
     */
    public FieldSpec field(String path) {
        return byPath.get(path);
    }

    /**
     * 扁平遍历所有字段，顺序与声明顺序一致。
     *
     * @return 所有字段的集合视图，不可修改
     */
    public Collection<FieldSpec> allFields() {
        return byPath.values();
    }

    /**
     * 是否包含某全路径字段。
     *
     * @param path 全路径
     * @return 包含返回 true
     */
    public boolean containsPath(String path) {
        return byPath.containsKey(path);
    }

    /**
     * 判断 validator 错误路径是否落在已声明字段或其结构化值后代内。
     * 列表下标只要求是非负数字，不按当前列表长度限制，避免校验错误因用户编辑而失效。
     */
    public boolean acceptsValidationPath(String path) {
        if (path == null) {
            return false;
        }
        for (FieldSpec field : byPath.values()) {
            if (path.equals(field.path())) {
                return true;
            }
            String prefix = field.path();
            boolean descendant = path.startsWith(prefix + ".") || path.startsWith(prefix + "[");
            if (descendant) {
                String suffix = path.substring(prefix.length());
                if (field.type() == FieldType.STRUCTURED_LIST) {
                    return field.valueSpec().acceptsPath(suffix);
                }
                if (field.type() == FieldType.SIMPLE_LIST) {
                    return acceptsSimpleListSuffix(suffix);
                }
            }
        }
        return false;
    }

    private static boolean acceptsSimpleListSuffix(String suffix) {
        if (suffix == null || suffix.length() < 3 || suffix.charAt(0) != '['
                || suffix.charAt(suffix.length() - 1) != ']') {
            return false;
        }
        for (int i = 1; i < suffix.length() - 1; i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 是否包含某顶层 key（即分类名）。
     * 用于 Authority 区分 Schema 字段和非 Schema 字段。
     *
     * @param key 顶层 key
     * @return 包含返回 true
     */
    public boolean containsTopLevel(String key) {
        for (SectionSpec s : sections) {
            if (s.name().equals(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建 Builder DSL 入口。
     *
     * @param modId mod 标识
     * @return schema 构建器
     */
    public static Builder builder(String modId) {
        return new Builder(modId);
    }

    /**
     * Schema 构建器，提供 {@link #section(String)} 进入分类作用域的 DSL。
     */
    public static final class Builder {
        private final String modId;
        private String title;
        private final List<SectionSpec> sections = new ArrayList<SectionSpec>();

        Builder(String modId) {
            this.modId = modId;
        }

        /**
         * 设置人类可读标题（schema 级，供 UI titleBar 显示），缺省回退 modId。
         *
         * @param title 标题
         * @return 当前构建器
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * 进入分类作用域，返回分类构建器。
         *
         * @param name 分类标识名
         * @return 分类构建器
         */
        public SectionSpec.Builder section(String name) {
            return new SectionSpec.Builder(this, name);
        }

        /**
         * 构建不可变 ConfigSchema。
         *
         * @return ConfigSchema
         */
        public ConfigSchema build() {
            return new ConfigSchema(modId, title, sections);
        }

        /**
         * 内部方法：将已构建的分类加入 schema。
         *
         * @param section 分类元数据
         */
        void addSection(SectionSpec section) {
            sections.add(section);
        }
    }
}
