package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;
import club.heiqi.config.MutableConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 内存权威快照，保存事务的唯一"真相源"。
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li>直接持 {@code Map<String, Object>}，不复用 {@code DefaultMutableConfig}。</li>
 *   <li>Schema 字段存 typed 值（String / Double / Boolean），按全路径 {@code "section.key"} 为键。</li>
 *   <li>非 Schema 顶层 key 存 {@link ConfigNode} 子树，原样保留供 {@link LegacyAdapter} 透传。</li>
 *   <li><b>section raw overlay</b>：schema 分类名（顶层 section）下未知字段/子树存为
 *       {@link ConfigNode}（键为 section 名，仅含非 schema 子键）；序列化时以 raw 为底再覆盖
 *       schema typed 字段（路径冲突 schema 优先）。无静默丢失。</li>
 *   <li>{@link #applyAll(Map)} 保留兼容签名；保存事务使用 prepared state 引用交换。</li>
 *   <li>{@link #snapshotTyped()} 供 {@link DraftBuffer} 深拷贝种子（含 raw overlay）。</li>
 *   <li>{@link #getRaw(String)} / {@link #putRaw(String, Object)} 供 {@link LegacyAdapter} 受控访问。
 *       schema 字段 putRaw 按 FieldType 严格 NodeType 提取，错型抛 {@link ConfigException} 且
 *       Authority/typed/expected/disk 零变化（不写哨兵）。</li>
 *   <li>公开与包级读写统一持事务锁；容器和 {@link ConfigNode} 读出口均返回防御副本。</li>
 *   <li>BATCH_SAVE / RELOAD 通知期间 mutation 经 {@link AuthorityMutationGuard} fail-closed，
 *       内存零变化（见 {@link #putRaw}）。</li>
 *   <li><b>disk 严格类型</b>：从 {@link ConfigNode} 加载时按 {@link FieldType} 先检查
 *       {@link ConfigNode.NodeType}——STRING/CHOICE 仅 STRING；BOOLEAN 仅 BOOLEAN；
 *       NUMBER 仅 NUMBER（quoted {@code "80"} 拒绝）；SIMPLE_LIST 仅 LIST 且每项 STRING 非 null。
 *       与 UI {@link DraftBuffer} 的 NUMBER 字符串解析边界分离。</li>
 * </ul>
 *
 * <p>本类零依赖 uilib。</p>
 */
public final class Authority {

    private final ConfigSchema schema;
    /**
     * Schema 字段 path → typed value；
     * 非 Schema 顶层 key → ConfigNode 子树；
     * schema section 名 → 该 section 内未知字段 raw overlay（ConfigNode，仅未知子键）。
     */
    private Map<String, Object> typedValues;
    /** Authority、Legacy 与 ConfigManager 保存事务共享的锁域 */
    private final Object transactionLock;
    private final LegacyAdapter legacyAdapter;
    /**
     * mutation 守卫（默认 ALLOW）；由 ConfigManager 在通知期切换为封锁实现。
     * 须在持 {@link #transactionLock} 时读写。
     */
    private AuthorityMutationGuard mutationGuard = AuthorityMutationGuard.ALLOW;

    private Authority(ConfigSchema schema, Map<String, Object> typedValues) {
        this.schema = schema;
        this.typedValues = typedValues;
        this.transactionLock = new Object();
        this.legacyAdapter = new LegacyAdapter(this);
    }

    /**
     * 从文件启动加载权威快照。
     *
     * <p>文件不存在或为空时，Schema 字段全部补 {@link FieldSpec#defaultValue()}。
     * 文件存在时按 {@link ConfigFormat#YAML} 解析；Schema 字段从解析树中按 path 取值并转 typed，
     * 缺失补默认；非 Schema 顶层 key 与 schema section 内未知字段原样保留为 raw overlay。</p>
     *
     * @param file   配置文件，可为 null 或不存在
     * @param schema 配置 schema
     * @return 权威快照
     * @throws ConfigException 文件存在但解析失败；非普通文件失败；严格类型不匹配
     */
    public static Authority load(File file, ConfigSchema schema) throws ConfigException {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        if (file == null) {
            return fromRoot(null, schema);
        }
        ConfigFileSnapshot snap = ConfigFileSnapshot.capture(file);
        return load(snap, schema);
    }

    /**
     * 从已捕获的磁盘快照解析权威态（不二次读盘）。
     *
     * <p>disk 路径按 FieldType 严格检查 NodeType；NUMBER 非法不静默折叠为 0.0。
     * reload 校验路径另用 {@link #extractSchemaCandidateForValidation} 保留可拒绝形态。</p>
     *
     * @param snap   文件快照，非 null
     * @param schema 配置 schema
     * @return 权威快照
     * @throws ConfigException 非普通文件、解析失败或严格类型不匹配
     */
    public static Authority load(ConfigFileSnapshot snap, ConfigSchema schema) throws ConfigException {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        if (snap == null) {
            throw new IllegalArgumentException("snap must not be null");
        }
        if (snap.state() == ConfigFileSnapshot.State.NON_REGULAR) {
            throw new ConfigException("config path is not a regular file: " + snap.canonicalFile());
        }
        ConfigNode root = null;
        if (snap.state() == ConfigFileSnapshot.State.REGULAR && snap.rawBytes().length > 0) {
            root = Config.parse(snap.utf8Text(), ConfigFormat.YAML);
        }
        return fromRoot(root, schema);
    }

    /**
     * 从磁盘快照提取 schema 字段候选（供 reload 校验用）。
     *
     * <p>与 {@link #load} 不同：严格 NodeType 检查后，非法类型<strong>不</strong>用默认值折叠，
     * 而保留可被内置校验拒绝的原始解释（如 NUMBER 的 quoted 字符串、BOOLEAN 非布尔）。</p>
     *
     * @param snap   文件快照
     * @param schema 冻结 schema
     * @return schema path → 候选值（深拷贝）
     * @throws ConfigException 非普通文件或解析失败
     */
    static Map<String, Object> extractSchemaCandidateForValidation(
            ConfigFileSnapshot snap, ConfigSchema schema) throws ConfigException {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        if (snap == null) {
            throw new IllegalArgumentException("snap must not be null");
        }
        if (snap.state() == ConfigFileSnapshot.State.NON_REGULAR) {
            throw new ConfigException("config path is not a regular file: " + snap.canonicalFile());
        }
        ConfigNode root = null;
        if (snap.state() == ConfigFileSnapshot.State.REGULAR && snap.rawBytes().length > 0) {
            root = Config.parse(snap.utf8Text(), ConfigFormat.YAML);
        }
        Map<String, Object> schemaFields = new LinkedHashMap<String, Object>();
        for (FieldSpec field : schema.allFields()) {
            ConfigNode node = root != null ? root.get(field.path()) : null;
            Object value;
            if (node == null || node.isNull()) {
                value = normalizeDefault(field.defaultValue(), field.type());
            } else {
                value = extractTypedStrict(node, field.type());
            }
            schemaFields.put(field.path(), ValueCopy.copyOf(value));
        }
        return schemaFields;
    }

    /**
     * 严格提取 typed 值：先按 FieldType 检查 NodeType。
     * 错型返回可被内置校验拒绝的哨兵/原形态，不静默折叠。
     */
    private static Object extractTypedStrict(ConfigNode node, FieldType type) {
        if (node == null || node.isNull()) {
            return null;
        }
        ConfigNode.NodeType nt = node.getType();
        switch (type) {
            case STRING:
            case CHOICE: {
                if (nt != ConfigNode.NodeType.STRING) {
                    return Integer.valueOf(-1);
                }
                return node.asString();
            }
            case NUMBER: {
                if (nt != ConfigNode.NodeType.NUMBER) {
                    String raw = node.asString();
                    return raw != null ? raw : "not-a-number";
                }
                try {
                    double v = node.asDouble();
                    if (Double.isNaN(v) || Double.isInfinite(v)) {
                        return "nan";
                    }
                    return Double.valueOf(v);
                } catch (ConfigException e) {
                    String raw = node.asString();
                    return raw != null ? raw : "not-a-number";
                }
            }
            case BOOLEAN: {
                if (nt != ConfigNode.NodeType.BOOLEAN) {
                    Object raw = node.asString();
                    return raw != null ? raw : "not-a-boolean";
                }
                try {
                    return Boolean.valueOf(node.asBoolean());
                } catch (ConfigException e) {
                    return "not-a-boolean";
                }
            }
            case SIMPLE_LIST: {
                if (nt != ConfigNode.NodeType.LIST) {
                    return node.asString() != null ? node.asString() : "not-a-list";
                }
                List<ConfigNode> raw = node.asList();
                if (raw == null) {
                    return "not-a-list";
                }
                List<String> out = new ArrayList<String>(raw.size());
                for (ConfigNode n : raw) {
                    if (n == null || n.isNull()) {
                        out.add(null);
                        continue;
                    }
                    if (n.getType() != ConfigNode.NodeType.STRING) {
                        List<Object> bad = new ArrayList<Object>();
                        bad.add(Integer.valueOf(-1));
                        return bad;
                    }
                    out.add(n.asString());
                }
                return out;
            }
            default:
                return node.asString();
        }
    }

    void replaceAllFrom(Authority other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        if (!Thread.holdsLock(transactionLock)) {
            throw new IllegalStateException("authority transaction lock is required for replaceAllFrom");
        }
        this.typedValues = ValueCopy.copyMapValues(other.typedValues);
    }

    void commitReloadSchemaFields(Map<String, Object> schemaFieldValues, Authority nonSchemaFrom) {
        if (!Thread.holdsLock(transactionLock)) {
            throw new IllegalStateException("authority transaction lock is required for commitReloadSchemaFields");
        }
        Map<String, Object> next = new HashMap<String, Object>();
        if (schemaFieldValues != null) {
            next.putAll(ValueCopy.copyMapValues(schemaFieldValues));
        }
        if (nonSchemaFrom != null) {
            for (Map.Entry<String, Object> e : nonSchemaFrom.typedValues.entrySet()) {
                // 非 schema 全路径：顶层 unknown 与 section raw overlay 均保留
                if (!schema.containsPath(e.getKey())) {
                    next.put(e.getKey(), ValueCopy.copyOf(e.getValue()));
                }
            }
        }
        this.typedValues = next;
    }

    /**
     * 从解析根构建 Authority：schema typed + 顶层 unknown + section 内 unknown raw overlay。
     * 未知字段不得静默丢弃。
     */
    private static Authority fromRoot(ConfigNode root, ConfigSchema schema) throws ConfigException {
        Map<String, Object> typed = new HashMap<String, Object>();

        for (FieldSpec field : schema.allFields()) {
            Object typedValue;
            ConfigNode node = root != null ? root.get(field.path()) : null;
            if (node != null && !node.isNull()) {
                typedValue = extractTypedStrictForLoad(node, field.type(), field.path());
            } else {
                typedValue = normalizeDefault(field.defaultValue(), field.type());
            }
            typed.put(field.path(), typedValue);
        }

        if (root != null && root.getType() == ConfigNode.NodeType.MAP) {
            Map<String, ConfigNode> rootMap = root.asMap();
            if (rootMap != null) {
                for (Map.Entry<String, ConfigNode> entry : rootMap.entrySet()) {
                    String key = entry.getKey();
                    ConfigNode value = entry.getValue();
                    if (!schema.containsTopLevel(key)) {
                        // 完全未知顶层：整棵子树保留
                        typed.put(key, ValueCopy.copyOf(value));
                    } else {
                        // schema section：剥离未知子键为 raw overlay（键 = section 名）
                        ConfigNode overlay = extractSectionUnknownOverlay(key, value, schema);
                        if (overlay != null) {
                            typed.put(key, overlay);
                        }
                    }
                }
            }
        }

        return new Authority(schema, typed);
    }

    /**
     * 从 schema section 节点提取非 schema 子键/子树为 raw overlay。
     * 仅含未知键；无未知时返回 null。
     */
    private static ConfigNode extractSectionUnknownOverlay(
            String sectionName, ConfigNode sectionNode, ConfigSchema schema) {
        if (sectionNode == null || sectionNode.isNull()) {
            return null;
        }
        if (sectionNode.getType() != ConfigNode.NodeType.MAP) {
            // section 不是 MAP（异常形态）：整节点作为 overlay 保留，避免静默丢
            return (ConfigNode) ValueCopy.copyOf(sectionNode);
        }
        Map<String, ConfigNode> map = sectionNode.asMap();
        if (map == null || map.isEmpty()) {
            return null;
        }
        MutableConfig overlay = Config.createMutable(ConfigFormat.YAML);
        boolean any = false;
        for (Map.Entry<String, ConfigNode> e : map.entrySet()) {
            String childKey = e.getKey();
            String fullPath = sectionName + "." + childKey;
            if (!schema.containsPath(fullPath)) {
                overlay.set(childKey, e.getValue());
                any = true;
            }
        }
        if (!any) {
            return null;
        }
        return (ConfigNode) ValueCopy.copyOf(overlay.asImmutable());
    }

    private static Object extractTypedStrictForLoad(ConfigNode node, FieldType type, String path)
            throws ConfigException {
        if (node == null || node.isNull()) {
            return null;
        }
        ConfigNode.NodeType nt = node.getType();
        switch (type) {
            case STRING:
            case CHOICE: {
                if (nt != ConfigNode.NodeType.STRING) {
                    throw new ConfigException(
                            "strict type: field " + path + " expected STRING NodeType, got " + nt,
                            ConfigException.Category.VALIDATION);
                }
                return node.asString();
            }
            case NUMBER: {
                if (nt != ConfigNode.NodeType.NUMBER) {
                    throw new ConfigException("strict type: field " + path + " expected NUMBER NodeType, got " + nt + " (quoted numeric strings are rejected on disk path)", ConfigException.Category.VALIDATION);
                }
                double v = node.asDouble();
                if (Double.isNaN(v) || Double.isInfinite(v)) {
                    throw new ConfigException("strict type: field " + path + " NUMBER is not finite", ConfigException.Category.VALIDATION);
                }
                return Double.valueOf(v);
            }
            case BOOLEAN: {
                if (nt != ConfigNode.NodeType.BOOLEAN) {
                    throw new ConfigException("strict type: field " + path + " expected BOOLEAN NodeType, got " + nt, ConfigException.Category.VALIDATION);
                }
                return Boolean.valueOf(node.asBoolean());
            }
            case SIMPLE_LIST: {
                if (nt != ConfigNode.NodeType.LIST) {
                    throw new ConfigException("strict type: field " + path + " expected LIST NodeType, got " + nt, ConfigException.Category.VALIDATION);
                }
                List<ConfigNode> raw = node.asList();
                if (raw == null) {
                    throw new ConfigException("strict type: field " + path + " LIST is null", ConfigException.Category.VALIDATION);
                }
                List<String> out = new ArrayList<String>(raw.size());
                for (int i = 0; i < raw.size(); i++) {
                    ConfigNode n = raw.get(i);
                    if (n == null || n.isNull()) {
                        throw new ConfigException(
                                "strict type: field " + path + " list item[" + i + "] must be non-null STRING",
                                ConfigException.Category.VALIDATION);
                    }
                    if (n.getType() != ConfigNode.NodeType.STRING) {
                        throw new ConfigException(
                                "strict type: field " + path + " list item[" + i
                                        + "] expected STRING NodeType, got " + n.getType(),
                                ConfigException.Category.VALIDATION);
                    }
                    out.add(n.asString());
                }
                return out;
            }
            default:
                return node.asString();
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String path) {
        synchronized (transactionLock) {
            return (T) ValueCopy.copyOf(typedValues.get(path));
        }
    }

    public String getString(String path) {
        synchronized (transactionLock) {
            Object value = typedValues.get(path);
            return value == null ? null : String.valueOf(value);
        }
    }

    public double getNumber(String path) {
        synchronized (transactionLock) {
            Object value = typedValues.get(path);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return 0.0;
        }
    }

    public boolean getBool(String path) {
        synchronized (transactionLock) {
            Object value = typedValues.get(path);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            return false;
        }
    }

    public ConfigSchema schema() {
        return schema;
    }

    public LegacyAdapter legacy() {
        return legacyAdapter;
    }

    void applyAll(Map<String, Object> newValues) {
        PreparedState prepared = prepareState(newValues);
        synchronized (transactionLock) {
            commitPrepared(prepared);
        }
    }

    PreparedState prepareState(Map<String, Object> newValues) {
        Map<String, Object> values = newValues == null
                ? new HashMap<String, Object>()
                : ValueCopy.copyMapValues(newValues);
        return new PreparedState(values);
    }

    void commitPrepared(PreparedState prepared) {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared must not be null");
        }
        if (!Thread.holdsLock(transactionLock)) {
            throw new IllegalStateException("authority transaction lock is required for commit");
        }
        typedValues = prepared.values;
    }

    Map<String, Object> snapshotTyped() {
        synchronized (transactionLock) {
            return ValueCopy.copyMapValues(typedValues);
        }
    }

    Map<String, Object> deepSnapshotTyped() {
        return snapshotTyped();
    }

    boolean matchesDeepSnapshot(Map<String, Object> snapshot) {
        synchronized (transactionLock) {
            if (snapshot == null || typedValues.size() != snapshot.size()) {
                return false;
            }
            for (Map.Entry<String, Object> e : typedValues.entrySet()) {
                if (!valueDeepEquals(e.getValue(), snapshot.get(e.getKey()))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static boolean valueDeepEquals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof ConfigNode && b instanceof ConfigNode) {
            try {
                String sa = club.heiqi.config.ConfigSerializer.toString(
                        (ConfigNode) a, club.heiqi.config.ConfigFormat.YAML);
                String sb = club.heiqi.config.ConfigSerializer.toString(
                        (ConfigNode) b, club.heiqi.config.ConfigFormat.YAML);
                return Objects.equals(sa, sb);
            } catch (RuntimeException e) {
                return false;
            }
        }
        if (a instanceof List && b instanceof List) {
            List<?> la = (List<?>) a;
            List<?> lb = (List<?>) b;
            if (la.size() != lb.size()) {
                return false;
            }
            for (int i = 0; i < la.size(); i++) {
                if (!valueDeepEquals(la.get(i), lb.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof Map && b instanceof Map) {
            Map<?, ?> ma = (Map<?, ?>) a;
            Map<?, ?> mb = (Map<?, ?>) b;
            if (ma.size() != mb.size()) {
                return false;
            }
            for (Map.Entry<?, ?> e : ma.entrySet()) {
                if (!valueDeepEquals(e.getValue(), mb.get(e.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(a, b);
    }

    ConfigNode getRaw(String path) {
        synchronized (transactionLock) {
            ConfigNode node = getRawLocked(path);
            return node == null ? null : (ConfigNode) ValueCopy.copyOf(node);
        }
    }

    private ConfigNode getRawLocked(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (schema.containsPath(path)) {
            return scalarToNode(typedValues.get(path));
        }
        String[] parts = path.split("\\.");
        Object top = typedValues.get(parts[0]);
        if (!(top instanceof ConfigNode)) {
            return null;
        }
        ConfigNode node = (ConfigNode) top;
        if (parts.length == 1) {
            return node;
        }
        StringBuilder sub = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            if (i > 1) {
                sub.append(".");
            }
            sub.append(parts[i]);
        }
        ConfigNode child = node.get(sub.toString());
        return child == null || child.isNull() ? null : child;
    }

    void putRaw(String path, Object value) throws ConfigException {
        synchronized (transactionLock) {
            mutationGuard.assertWritable();
            if (path == null || path.isEmpty()) {
                return;
            }
            if (schema.containsPath(path)) {
                // schema 字段：按 FieldType + NodeType 严格提取；错型抛 ConfigException，零写入
                if (value instanceof ConfigNode) {
                    ConfigNode node = (ConfigNode) value;
                    if (!node.isNull()) {
                        Object typed = extractTypedStrictForLoad(
                                node, schema.field(path).type(), path);
                        typedValues.put(path, typed);
                    }
                } else if (value != null) {
                    throw new ConfigException(
                            "strict type: field " + path
                                    + " putRaw expects ConfigNode for schema path, got "
                                    + value.getClass().getName(),
                            ConfigException.Category.VALIDATION);
                }
                return;
            }
            String[] parts = path.split("\\.");
            String topKey = parts[0];
            if (parts.length == 1) {
                if (value == null) {
                    typedValues.remove(topKey);
                } else if (value instanceof ConfigNode) {
                    typedValues.put(topKey, ValueCopy.copyOf(value));
                }
                return;
            }
            // 删除 raw 子路径
            if (value == null) {
                Object existing = typedValues.get(topKey);
                if (!(existing instanceof ConfigNode) || ((ConfigNode) existing).isNull()) {
                    return;
                }
                MutableConfig mc = Config.createMutable(ConfigFormat.YAML);
                loadSubtreeInto(mc, (ConfigNode) existing);
                StringBuilder sub = new StringBuilder();
                for (int i = 1; i < parts.length; i++) {
                    if (i > 1) {
                        sub.append(".");
                    }
                    sub.append(parts[i]);
                }
                mc.remove(sub.toString());
                ConfigNode after = mc.asImmutable();
                if (after.getType() == ConfigNode.NodeType.MAP) {
                    Map<String, ConfigNode> m = after.asMap();
                    if (m == null || m.isEmpty()) {
                        typedValues.remove(topKey);
                    } else {
                        typedValues.put(topKey, ValueCopy.copyOf(after));
                    }
                } else {
                    typedValues.put(topKey, ValueCopy.copyOf(after));
                }
                return;
            }
            Object existing = typedValues.get(topKey);
            MutableConfig mc = Config.createMutable(ConfigFormat.YAML);
            if (existing instanceof ConfigNode && !((ConfigNode) existing).isNull()) {
                loadSubtreeInto(mc, (ConfigNode) existing);
            }
            StringBuilder sub = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) {
                    sub.append(".");
                }
                sub.append(parts[i]);
            }
            mc.set(sub.toString(), value);
            typedValues.put(topKey, ValueCopy.copyOf(mc.asImmutable()));
        }
    }

    void setMutationGuard(AuthorityMutationGuard guard) {
        if (!Thread.holdsLock(transactionLock)) {
            throw new IllegalStateException("authority transaction lock is required for setMutationGuard");
        }
        this.mutationGuard = guard == null ? AuthorityMutationGuard.ALLOW : guard;
    }

    Object transactionLock() {
        return transactionLock;
    }

    private static void loadSubtreeInto(MutableConfig mc, ConfigNode subtree) {
        if (subtree.getType() != ConfigNode.NodeType.MAP) {
            return;
        }
        Map<String, ConfigNode> map = subtree.asMap();
        if (map == null) {
            return;
        }
        for (Map.Entry<String, ConfigNode> entry : map.entrySet()) {
            mc.set(entry.getKey(), entry.getValue());
        }
    }

    private static ConfigNode scalarToNode(Object typedValue) {
        if (typedValue == null) {
            return null;
        }
        MutableConfig mc = Config.createMutable(ConfigFormat.YAML);
        mc.set("_", typedValue);
        return mc.get("_");
    }

    static Object normalizeDefault(Object defaultValue, FieldType type) {
        if (defaultValue == null) {
            switch (type) {
                case STRING:
                case CHOICE:
                    return "";
                case NUMBER:
                    return 0.0;
                case BOOLEAN:
                    return false;
                case SIMPLE_LIST:
                    return new ArrayList<String>();
                default:
                    return null;
            }
        }
        switch (type) {
            case STRING:
            case CHOICE:
                return String.valueOf(defaultValue);
            case NUMBER:
                if (defaultValue instanceof Number) {
                    return ((Number) defaultValue).doubleValue();
                }
                try {
                    return Double.parseDouble(String.valueOf(defaultValue));
                } catch (NumberFormatException e) {
                    return 0.0;
                }
            case BOOLEAN:
                if (defaultValue instanceof Boolean) {
                    return defaultValue;
                }
                return Boolean.parseBoolean(String.valueOf(defaultValue));
            case SIMPLE_LIST:
                if (defaultValue instanceof List) {
                    return ValueCopy.copyOf(defaultValue);
                }
                return new ArrayList<String>();
            default:
                return defaultValue;
        }
    }

    static final class PreparedState {
        private final Map<String, Object> values;

        PreparedState(Map<String, Object> values) {
            this.values = values;
        }
    }
}
