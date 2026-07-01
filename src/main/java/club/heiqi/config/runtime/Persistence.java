package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSource;
import club.heiqi.config.MutableConfig;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;

import java.io.File;
import java.util.Map;

/**
 * 文件读写 + 整文件覆写。
 *
 * <p>复用现有 {@link Config#load} 读文件、{@link MutableConfig#save()} 写文件。
 * 默认 YAML 格式。写失败抛 {@link ConfigException}，回滚由 {@link ConfigManager} 负责。</p>
 *
 * <p>{@link #writeAll(Map, ConfigSchema)} 把 typed 值映射重建为 {@link ConfigNode} 树：
 * Schema 字段按 path 拆点号重建嵌套，非 Schema 顶层 key 原样挂回子树。</p>
 *
 * <p>本类零依赖 uilib。</p>
 */
public final class Persistence {

    private final File file;
    private final ConfigFormat format;

    /**
     * 构造持久化器。
     *
     * @param file   目标文件
     * @param format 文件格式
     */
    public Persistence(File file, ConfigFormat format) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        this.file = file;
        this.format = format;
    }

    /**
     * 读取文件为配置树。
     *
     * @return 配置根节点，文件不存在返回空 MAP 节点
     * @throws ConfigException 解析失败
     */
    public ConfigNode read() throws ConfigException {
        if (!file.isFile() || file.length() == 0) {
            // 空文件或不存在：返回空 MAP 节点（不返回 NullConfigNode，便于上层按 map 路径取值）
            return Config.parse("", format);
        }
        return Config.load(ConfigSource.fromFile(file), format);
    }

    /**
     * 把 typed 值映射整文件覆写。
     *
     * <p>Schema 字段按 {@link FieldSpec#path()} 拆点号重建嵌套结构；
     * 非 Schema 顶层 key（{@link ConfigNode} 子树）原样挂回顶层。</p>
     *
     * @param typedValues typed 值映射（Schema path → typed value，非 Schema key → ConfigNode）
     * @param schema      配置 schema
     * @throws ConfigException 写盘失败
     */
    public void writeAll(Map<String, Object> typedValues, ConfigSchema schema) throws ConfigException {
        if (typedValues == null) {
            throw new IllegalArgumentException("typedValues must not be null");
        }
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }

        MutableConfig builder = Config.createMutable(file, format);

        // Schema 字段：按 path 重建嵌套
        for (FieldSpec field : schema.allFields()) {
            Object value = typedValues.get(field.path());
            if (value != null) {
                builder.set(field.path(), value);
            }
        }

        // 非 Schema 顶层 key：原样挂回
        for (Map.Entry<String, Object> entry : typedValues.entrySet()) {
            String key = entry.getKey();
            if (schema.containsPath(key) || schema.containsTopLevel(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value != null) {
                builder.set(key, value);
            }
        }

        builder.save();
    }

    /**
     * @return 目标文件
     */
    public File file() {
        return file;
    }

    /**
     * @return 文件格式
     */
    public ConfigFormat format() {
        return format;
    }
}
