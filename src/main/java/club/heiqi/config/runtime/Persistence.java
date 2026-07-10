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
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文件读写 + 同 classloader 参与式 writer 串行的写前检测。
 *
 * <p>复用现有 {@link Config#parse} / 序列化；写入拆为锁外构树/序列化与 monitor 下 temp+replace。
 * 默认 YAML 格式。写失败抛 {@link ConfigException}；写前检测失败抛 {@link ConfigConflictException}。</p>
 *
 * <p>{@link #writeAll(Map, ConfigSchema)} 是<strong>低级无 expected 比较写</strong>：进入参与式静态
 * writer monitor 串行，但<strong>不做</strong> expected 字节 compare，也<strong>不能</strong>称为 CAS。
 * 生产 {@link ConfigManager} 的 save/flushRaw/reload 路径<strong>不</strong>调用本方法旁路；
 * 写前检测与 expected 推进仅走 {@link #casWritePrepared} / {@link #withWriteDomain} 参与式路径。
 * 明确<strong>非参与式写前检测</strong>旁路；见 {@code @deprecated}。</p>
 *
 * <h3>raw overlay 序列化</h3>
 * <p>{@link #prepareWrite} 以 typedValues 中的 raw overlay（非 schema 全路径的 {@link ConfigNode}，
 * 含顶层 unknown 与 schema section 内未知子树）为底，再覆盖 schema typed 字段。
 * 路径冲突时 <strong>schema 优先</strong>。已有文件未知字段不得静默 drop。</p>
 *
 * <h3>写前检测口径（beta，参与式 writer）</h3>
 * <ul>
 *   <li>bootstrap 时 {@link ConfigFileSnapshot#capture} 一次读取 canonical 原始字节作为 expected</li>
 *   <li>save/flushRaw 写前在静态写域 monitor（{@link #withWriteDomain}）内：再 capture 当前盘与 expected 精确字节比</li>
 *   <li>不等 → {@link SaveOutcome.ConflictType#CONFIG_FILE_CHANGED_SINCE_LOAD}，不写盘</li>
 *   <li>相等 → atomic replace（temp+move；<b>不</b>承诺 fsync / 掉电持久化顺序）；成功后返回新 REGULAR 快照</li>
 *   <li><b>范围</b>：仅保证<strong>同 JVM classloader 内、走本 Persistence 写路径</strong>的参与式 writer
 *       串行 + 写前检测已完成外部变更；<b>不</b>承诺阻止外部 writer（其它进程/直接 Files.write）
 *       在 compare→replace 窗口的竞态</li>
 *   <li>若口语仍称「CAS」，仅指上述参与式写前精确字节比较 + 串行 replace，<b>不是</b> OS 级跨进程 CAS</li>
 *   <li>相同字节重建视为等价（不看 mtime）；A→B→A 同字节 ABA 明确允许</li>
 *   <li>写域身份：仅 canonical path 语法别名（相对/绝对解析后相同）；<b>不</b>实现 inode/硬链接身份，
 *       硬链接共享写域不保证</li>
 * </ul>
 *
 * <h3>测试缝（包级，不暴露锁对象）</h3>
 * <p>{@link #installFaultInjector}/{@link #clearFaultInjector} 可在 temp 写成功后、move 前注入失败；
 * {@link #withWriteDomain}/{@link #verifyWriteDomainCurrent} 供同包测试与 reload commit 共用写域。
 * 生产路径 injector 为 null；测试 finally 必须 clear。</p>
 *
 * <p>本类零依赖 uilib。</p>
 */
public final class Persistence {

    /**
     * 同 JVM classloader 内串行「写前比较 + atomic replace」的静态 monitor。
     * 覆盖所有 ConfigManager/Persistence 实例对任意文件的参与式写路径。
     * <p><b>不对外暴露</b>：外部用 {@link #withWriteDomain}，勿直接同步本对象。</p>
     */
    private static final Object DISK_WRITE_MONITOR = new Object();

    /** 兼容旧测试名；同写域 monitor（包级只读别名，勿对外暴露锁对象）。 */
    @Deprecated
    static final Object DISK_CAS_MONITOR = DISK_WRITE_MONITOR;

    /**
     * 包级测试故障注入：temp 完整写入后、move 前调用；抛 IOException 则目标不变。
     */
    public interface FaultInjector {
        /**
         * @param target 目标文件
         * @param temp   已写满的临时文件
         * @throws IOException 模拟 move 失败等
         */
        void beforeMove(File target, Path temp) throws IOException;
    }

    private static final AtomicReference<FaultInjector> FAULT_INJECTOR =
            new AtomicReference<FaultInjector>(null);

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
     * 安装故障注入器（仅 runtime 同包测试；用完必须 {@link #clearFaultInjector}）。
     *
     * @param injector 注入器，null 清除
     */
    static void installFaultInjector(FaultInjector injector) {
        FAULT_INJECTOR.set(injector);
    }

    /** 清除故障注入器（测试 finally 必调）。 */
    static void clearFaultInjector() {
        FAULT_INJECTOR.set(null);
    }

    /**
     * 在参与式静态 writer monitor 内执行回调（不暴露锁对象）。
     *
     * @param action 回调，非 null
     */
    static void withWriteDomain(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        synchronized (DISK_WRITE_MONITOR) {
            action.run();
        }
    }

    /**
     * 在写域 monitor 内校验当前盘是否仍等于 expected（精确字节）。
     *
     * @param file     目标文件
     * @param expected 期望快照
     * @return 相等 true
     * @throws ConfigException 读盘失败
     */
    static boolean verifyWriteDomainCurrent(File file, ConfigFileSnapshot expected)
            throws ConfigException {
        if (file == null || expected == null) {
            throw new IllegalArgumentException("file/expected must not be null");
        }
        ConfigFileSnapshot current = ConfigFileSnapshot.capture(file);
        return current.exactBytesEqual(expected);
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
     * 低级整文件覆写：进入参与式静态 writer monitor 串行，但<strong>无 expected 字节比较</strong>。
     *
     * <p><b>不是 CAS</b>，也<strong>不是</strong>参与式写前检测路径。生产 {@link ConfigManager}
     * 不调用本方法；save/flush 须走 {@link #casWritePrepared}。仅兼容旧测试/诊断旁路。</p>
     *
     * @param typedValues typed 值映射（可含 raw overlay ConfigNode）
     * @param schema      配置 schema
     * @throws ConfigException 写盘失败
     * @deprecated 低级无 expected 比较写；非参与式写前检测；生产路径用 {@link #casWritePrepared}
     */
    @Deprecated
    public void writeAll(Map<String, Object> typedValues, ConfigSchema schema) throws ConfigException {
        if (typedValues == null) {
            throw new IllegalArgumentException("typedValues must not be null");
        }
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        final PreparedWrite prepared = prepareWrite(typedValues, schema);
        try {
            withWriteDomain(new Runnable() {
                @Override
                public void run() {
                    try {
                        writePreparedUnlocked(prepared);
                    } catch (ConfigException e) {
                        throw new WriteDomainRuntime(e);
                    }
                }
            });
        } catch (WriteDomainRuntime e) {
            throw e.unwrap();
        }
    }

    /**
     * 构造并序列化完整配置内容；调用方可在事务锁外执行。
     *
     * <p>顺序：先铺 raw overlay（非 schema 全路径的 ConfigNode 子树，含 section 内未知），
     * 再写 schema typed 字段——路径冲突时 schema 优先。</p>
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

            // 1) raw 为底：非 schema 全路径条目（顶层 unknown + section raw overlay）
            for (Map.Entry<String, Object> entry : typedValues.entrySet()) {
                String key = entry.getKey();
                if (schema.containsPath(key)) {
                    continue;
                }
                Object value = entry.getValue();
                if (value instanceof ConfigNode) {
                    ConfigNode node = (ConfigNode) value;
                    if (node.isNull()) {
                        continue;
                    }
                    if (schema.containsTopLevel(key)
                            && node.getType() == ConfigNode.NodeType.MAP) {
                        // section raw overlay：铺到 section.child
                        Map<String, ConfigNode> children = node.asMap();
                        if (children != null) {
                            for (Map.Entry<String, ConfigNode> child : children.entrySet()) {
                                String full = key + "." + child.getKey();
                                // 仅非 schema 子路径；schema 字段稍后覆盖
                                if (!schema.containsPath(full)) {
                                    builder.set(full, child.getValue());
                                }
                            }
                        }
                    } else {
                        builder.set(key, node);
                    }
                } else if (value != null) {
                    builder.set(key, value);
                }
            }

            // 2) schema typed 覆盖（路径冲突 schema 优先）
            for (FieldSpec field : schema.allFields()) {
                Object value = typedValues.get(field.path());
                if (value != null) {
                    builder.set(field.path(), value);
                }
            }

            return new PreparedWrite(ConfigSerializer.toString(builder.asImmutable(), format));
        } catch (RuntimeException e) {
            throw ioFailure("prepare config write failed", e);
        }
    }

    /**
     * 写入已序列化的完整内容（无写前检测；进入写域 monitor）。
     */
    void writePrepared(final PreparedWrite prepared) throws ConfigException {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared must not be null");
        }
        try {
            withWriteDomain(new Runnable() {
                @Override
                public void run() {
                    try {
                        writePreparedUnlocked(prepared);
                    } catch (ConfigException e) {
                        throw new WriteDomainRuntime(e);
                    }
                }
            });
        } catch (WriteDomainRuntime e) {
            throw e.unwrap();
        }
    }

    private void writePreparedUnlocked(PreparedWrite prepared) throws ConfigException {
        try {
            final FaultInjector injector = FAULT_INJECTOR.get();
            AtomicFileWrites.writeUtf8Atomically(
                    canonicalFile,
                    prepared.content,
                    injector == null ? null : new AtomicFileWrites.BeforeMoveHook() {
                        @Override
                        public void beforeMove(File target, Path temp) throws IOException {
                            injector.beforeMove(target, temp);
                        }
                    });
        } catch (IOException e) {
            throw ioFailure("write config failed", e);
        } catch (RuntimeException e) {
            throw ioFailure("write config failed", e);
        }
    }

    /**
     * 写前检测：在静态 monitor 内比较 expected 与当前盘精确字节，相等才 atomic replace。
     *
     * <p>仅覆盖参与式 writer；外部 writer 不在本方法承诺范围内。不是 OS 级 CAS。
     * atomic write <b>不</b>承诺 fsync。</p>
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
        final ConfigException[] err = new ConfigException[1];
        final ConfigFileSnapshot[] result = new ConfigFileSnapshot[1];
        withWriteDomain(new Runnable() {
            @Override
            public void run() {
                try {
                    ConfigFileSnapshot current = ConfigFileSnapshot.capture(canonicalFile);
                    if (!current.exactBytesEqual(expected)) {
                        err[0] = new ConfigConflictException(
                                SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD,
                                "config file changed since load (write-domain precheck)");
                        return;
                    }
                    writePreparedUnlocked(prepared);
                    result[0] = ConfigFileSnapshot.ofPreparedUtf8(canonicalFile, prepared.content);
                } catch (ConfigException e) {
                    err[0] = e;
                }
            }
        });
        if (err[0] != null) {
            throw err[0];
        }
        return result[0];
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

    /** 写域回调把 checked 异常带出 monitor。 */
    private static final class WriteDomainRuntime extends RuntimeException {
        private final ConfigException checked;

        private WriteDomainRuntime(ConfigException checked) {
            super(checked);
            this.checked = checked;
        }

        private ConfigException unwrap() {
            return checked;
        }
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
