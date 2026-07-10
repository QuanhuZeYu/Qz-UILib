package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.AtomicFileWrites;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSerializer;
import club.heiqi.config.MutableConfig;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * 文件读写 + 同 classloader 参与式 writer 串行的写前检测。
 *
 * <p>复用现有 {@link Config#parse} / 序列化；写入拆为锁外构树/序列化与 monitor 下 temp+replace。
 * 默认 YAML 格式。写失败抛 {@link ConfigException}；写前检测失败抛 {@link ConfigConflictException}。</p>
 *
 * <p>{@link #writeAll(Map, ConfigSchema)} 把 typed 值映射重建为 {@link ConfigNode} 树：
 * Schema 字段按 path 拆点号重建嵌套，非 Schema 顶层 key 原样挂回子树。</p>
 *
 * <h3>写前检测口径（beta，参与式 writer）</h3>
 * <ul>
 *   <li>bootstrap 时 {@link ConfigFileSnapshot#capture} 一次读取 canonical 原始字节作为 expected</li>
 *   <li>save/flushRaw 写前在静态 {@link #DISK_WRITE_MONITOR} 内：再 capture 当前盘与 expected 精确字节比</li>
 *   <li>不等 → {@link SaveOutcome.ConflictType#CONFIG_FILE_CHANGED_SINCE_LOAD}，不写盘</li>
 *   <li>相等 → atomic replace；成功后返回新 REGULAR 快照（预制 UTF-8 字节）</li>
 *   <li><b>范围</b>：仅保证<strong>同 JVM classloader 内、走本 Persistence 写路径</strong>的参与式 writer
 *       串行 + 写前检测已完成外部变更；<b>不</b>承诺阻止外部 writer（其它进程/直接 Files.write）
 *       在 compare→replace 窗口的竞态</li>
 *   <li>若口语仍称「CAS」，仅指上述参与式写前精确字节比较 + 串行 replace，<b>不是</b> OS 级跨进程 CAS</li>
 *   <li>相同字节重建视为等价（不看 mtime）；A→B→A 同字节 ABA 明确允许</li>
 *   <li>写域身份：仅 canonical path 语法别名（相对/绝对解析后相同）；<b>不</b>实现 inode/硬链接身份，
 *       硬链接共享写域不保证</li>
 * </ul>
 *
 * <p>本类零依赖 uilib。</p>
 */
public final class Persistence {

    /**
     * 同 JVM classloader 内串行「写前比较 + atomic replace」的静态 monitor。
     * 覆盖所有 ConfigManager/Persistence 实例对任意文件的参与式写路径。
     */
    static final Object DISK_WRITE_MONITOR = new Object();

    /** @deprecated 兼容旧测试名；同 {@link #DISK_WRITE_MONITOR} */
    @Deprecated
    static final Object DISK_CAS_MONITOR = DISK_WRITE_MONITOR;

    private final File canonicalFile;
    private final ConfigFormat format;

    /**
     * 构造持久化器：先解析 canonical（失败抛 ConfigException）。
     *
     * @param file   目标文件
     * @param format 文件格式
     * @throws ConfigException canonical 解析失败
     */
    public Persistence(File file, ConfigFormat format) throws ConfigException {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }
        try {
            this.canonicalFile = file.getCanonicalFile();
        } catch (IOException e) {
            throw new ConfigException("cannot resolve canonical config path: " + file, e);
        }
        this.format = format;
    }

    /**
     * 读取文件为配置树。
     *
     * @return 配置根节点，文件不存在返回空 MAP 节点
     * @throws ConfigException 解析失败
     */
    public ConfigNode read() throws ConfigException {
        ConfigFileSnapshot snap = ConfigFileSnapshot.capture(canonicalFile);
        return parseSnapshot(snap);
    }

    /**
     * 从快照解析配置树（不二次读盘）。
     *
     * @param snap 文件快照
     * @return 配置根节点
     * @throws ConfigException 非普通文件或解析失败
     */
    public ConfigNode parseSnapshot(ConfigFileSnapshot snap) throws ConfigException {
        if (snap == null) {
            throw new IllegalArgumentException("snap must not be null");
        }
        if (snap.state() == ConfigFileSnapshot.State.NON_REGULAR) {
            throw new ConfigException("config path is not a regular file: " + snap.canonicalFile());
        }
        if (snap.state() == ConfigFileSnapshot.State.MISSING
                || snap.rawBytes().length == 0) {
            return Config.parse("", format);
        }
        return Config.parse(snap.utf8Text(), format);
    }

    /**
     * 把 typed 值映射整文件覆写（无写前检测：直接写；生产 save/flush 应走 {@link #casWritePrepared}）。
     *
     * @param typedValues typed 值映射
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
        writePrepared(prepareWrite(typedValues, schema));
    }

    /**
     * 构造并序列化完整配置内容；调用方可在事务锁外执行。
     */
    PreparedWrite prepareWrite(Map<String, Object> typedValues, ConfigSchema schema) throws ConfigException {
        if (typedValues == null) {
            throw new IllegalArgumentException("typedValues must not be null");
        }
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }

        try {
            MutableConfig builder = Config.createMutable(format);

            for (FieldSpec field : schema.allFields()) {
                Object value = typedValues.get(field.path());
                if (value != null) {
                    builder.set(field.path(), value);
                }
            }

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
            return new PreparedWrite(ConfigSerializer.toString(builder.asImmutable(), format));
        } catch (RuntimeException e) {
            throw ioFailure("prepare config write failed", e);
        }
    }

    /**
     * 写入已序列化的完整内容（无写前检测，兼容旧测试/直接调用）。
     */
    void writePrepared(PreparedWrite prepared) throws ConfigException {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared must not be null");
        }
        try {
            AtomicFileWrites.writeUtf8Atomically(canonicalFile, prepared.content);
        } catch (IOException e) {
            throw ioFailure("write config failed", e);
        } catch (RuntimeException e) {
            throw ioFailure("write config failed", e);
        }
    }

    /**
     * 写前检测：在静态 monitor 内比较 expected 与当前盘精确字节，相等才 atomic replace。
     *
     * <p>仅覆盖参与式 writer；外部 writer 不在本方法承诺范围内。</p>
     *
     * @param prepared 预制写入内容
     * @param expected 期望磁盘快照（bootstrap/上次成功写后的 expected）
     * @return 写成功后的新 expected（预制 UTF-8 字节的 REGULAR 快照）
     * @throws ConfigConflictException 当前盘与 expected 不等
     * @throws ConfigException         IO 失败（不推进 expected）
     */
    ConfigFileSnapshot casWritePrepared(PreparedWrite prepared, ConfigFileSnapshot expected)
            throws ConfigException {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared must not be null");
        }
        if (expected == null) {
            throw new IllegalArgumentException("expected must not be null");
        }
        synchronized (DISK_WRITE_MONITOR) {
            ConfigFileSnapshot current = ConfigFileSnapshot.capture(canonicalFile);
            if (!current.exactBytesEqual(expected)) {
                throw new ConfigConflictException(
                        SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD,
                        "config file changed since load (write-domain precheck)");
            }
            try {
                AtomicFileWrites.writeUtf8Atomically(canonicalFile, prepared.content);
            } catch (IOException e) {
                throw ioFailure("write config failed", e);
            } catch (RuntimeException e) {
                throw ioFailure("write config failed", e);
            }
            return ConfigFileSnapshot.ofPreparedUtf8(canonicalFile, prepared.content);
        }
    }

    /**
     * 在静态写 monitor 内执行回调（供测试/诊断扩展；生产写路径用 {@link #casWritePrepared}）。
     */
    static void withDiskCasMonitor(Runnable action) {
        synchronized (DISK_WRITE_MONITOR) {
            action.run();
        }
    }

    /**
     * @return canonical 目标文件
     */
    public File file() {
        return canonicalFile;
    }

    /**
     * @return 文件格式
     */
    public ConfigFormat format() {
        return format;
    }

    private static ConfigException ioFailure(String prefix, Throwable cause) {
        String message = cause.getMessage();
        if (message == null || message.isEmpty()) {
            message = cause.getClass().getSimpleName();
        }
        return new ConfigException(prefix + ": " + message, cause);
    }

    /** 锁外已完成构树与序列化的完整写入内容。 */
    static final class PreparedWrite {
        private final String content;

        PreparedWrite(String content) {
            this.content = content == null ? "" : content;
        }

        /** 包内测试 / 快照更新用 */
        String content() {
            return content;
        }
    }
}
