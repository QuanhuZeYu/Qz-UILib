package club.heiqi.config.schema;

import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigNode;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 不可变的递归配置值描述。
 *
 * <p>该类只描述配置值，不依赖 scene。对象未知 member 采用 PRESERVE 语义：读取、草稿复制和
 * 写盘都会保留它们，但未知 member 不是可供 schema validator 使用的合法路径。</p>
 */
public final class ValueSpec {
    /** 防止配置 schema 形成不可控的递归值树。 */
    public static final int MAX_DEPTH = 8;

    private final ValueKind kind;
    private final ValueSpec element;
    private final Map<String, Member> members;
    private final List<String> choices;
    private final Object explicitDefault;
    private final boolean hasExplicitDefault;
    /** 对象作为列表元素时可用于证明行身份的 member；未声明时为 null。 */
    private final String identityMember;
    /** 仅供 UI 选择编辑器使用的元数据；不参与配置值语义。 */
    private final WidgetSpec widget;

    private ValueSpec(ValueKind kind, ValueSpec element, Map<String, Member> members,
                      List<String> choices, Object explicitDefault, boolean hasExplicitDefault,
                       String identityMember) {
        this(kind, element, members, choices, explicitDefault, hasExplicitDefault, identityMember, null);
    }

    private ValueSpec(ValueKind kind, ValueSpec element, Map<String, Member> members,
                      List<String> choices, Object explicitDefault, boolean hasExplicitDefault,
                      String identityMember, WidgetSpec widget) {
        this.kind = kind;
        this.element = element;
        this.members = members;
        this.choices = choices;
        this.explicitDefault = explicitDefault;
        this.hasExplicitDefault = hasExplicitDefault;
        this.identityMember = identityMember;
        this.widget = widget;
    }

    /** 创建 STRING 描述。 */
    public static ValueSpec string() {
        return scalar(ValueKind.STRING);
    }

    /** 创建 NUMBER 描述。 */
    public static ValueSpec number() {
        return scalar(ValueKind.NUMBER);
    }

    /** 创建 BOOLEAN 描述。 */
    public static ValueSpec bool() {
        return scalar(ValueKind.BOOLEAN);
    }

    /** 创建 CHOICE 描述。 */
    public static ValueSpec choice(String... options) {
        if (options == null || options.length == 0) {
            throw new IllegalArgumentException("choice options must not be empty");
        }
        List<String> copy = new ArrayList<String>(options.length);
        for (String option : options) {
            if (option == null || option.isEmpty() || copy.contains(option)) {
                throw new IllegalArgumentException("choice options must be unique and non-empty");
            }
            copy.add(option);
        }
        return new ValueSpec(ValueKind.CHOICE, null,
                Collections.<String, Member>emptyMap(),
                Collections.unmodifiableList(copy), null, false, null);
    }

    /** 创建列表描述。 */
    public static ValueSpec list(ValueSpec element) {
        require(element, "element");
        ensureDepth(element, 1);
        return new ValueSpec(ValueKind.LIST, element,
                Collections.<String, Member>emptyMap(),
                Collections.<String>emptyList(), null, false, null);
    }

    /** 创建对象描述，member 顺序即 YAML/UI 顺序。 */
    public static ValueSpec object(Member... members) {
        if (members == null || members.length == 0) {
            throw new IllegalArgumentException("object must declare at least one member");
        }
        LinkedHashMap<String, Member> map = new LinkedHashMap<String, Member>();
        for (Member member : members) {
            if (member == null) {
                throw new IllegalArgumentException("object member must not be null");
            }
            if (map.put(member.name(), member) != null) {
                throw new IllegalArgumentException("duplicate object member: " + member.name());
            }
        }
        Map<String, Member> frozen = Collections.unmodifiableMap(map);
        for (Member member : frozen.values()) {
            ensureDepth(member.spec(), 1);
        }
        return new ValueSpec(ValueKind.OBJECT, null, frozen,
                Collections.<String>emptyList(), null, false, null);
    }

    private static ValueSpec scalar(ValueKind kind) {
        return new ValueSpec(kind, null, Collections.<String, Member>emptyMap(),
                Collections.<String>emptyList(), null, false, null);
    }

    /** 将顶层旧 FieldType 映射为递归描述，保持旧 API 行为。 */
    static ValueSpec forFieldType(FieldType type) {
        switch (type) {
            case STRING: return string();
            case NUMBER: return number();
            case BOOLEAN: return bool();
            // 旧 CHOICE 的 options 仍由 FieldConstraints 持有；这里仅提供兼容的值种类。
            case CHOICE: return scalar(ValueKind.CHOICE);
            case SIMPLE_LIST: return list(string());
            case STRUCTURED_LIST:
                throw new IllegalArgumentException("STRUCTURED_LIST requires an element ValueSpec");
            default: throw new IllegalArgumentException("unknown field type: " + type);
        }
    }

    /** 为任意 spec 声明不可变默认值。 */
    public ValueSpec withDefault(Object value) {
        Validation validation = validate(value, "_default");
        if (validation.hasErrors()) {
            throw new IllegalArgumentException(validation.firstMessage());
        }
        return new ValueSpec(kind, element, members, choices, copyAndFreeze(normalize(value)), true,
                identityMember, widget);
    }

    /** @return 节点种类 */
    public ValueKind kind() { return kind; }

    /** @return 列表元素 spec，非 LIST 时为 null */
    public ValueSpec element() { return element; }

    /** @return 对象 member 保序只读表 */
    public Map<String, Member> members() { return members; }

    /** @return 对象 member，找不到返回 null */
    public Member member(String name) { return members.get(name); }

    /** @return CHOICE 选项只读列表 */
    public List<String> choices() { return choices; }

    /**
     * 返回对象声明的身份 member 名称。
     *
     * <p>该元数据只供 keyed 列表模型证明行身份；它不是 scene key，也不会把业务值直接暴露给
     * scene reconciler。</p>
     *
     * @return 身份 member 名称；未声明或非 OBJECT 时为 null
     */
    public String identityMember() { return identityMember; }

    /** @return UI widget 元数据；未声明时为 null */
    public WidgetSpec widget() { return widget; }

    /**
     * 声明 UI widget 元数据。
     *
     * <p>该元数据不参与 YAML、默认值、值校验或 schema 兼容判定。</p>
     *
     * @param widget widget 元数据，不可为 null
     * @return 带元数据的新 spec
     */
    public ValueSpec withWidget(WidgetSpec widget) {
        require(widget, "widget");
        return new ValueSpec(kind, element, members, choices, explicitDefault, hasExplicitDefault,
                identityMember, widget);
    }

    /**
     * 声明对象的可靠身份 member。
     *
     * <p>identity member 必须是稳定可比较的 {@link ValueKind#STRING}、
     * {@link ValueKind#NUMBER}、{@link ValueKind#BOOLEAN} 或 {@link ValueKind#CHOICE} 标量；
     * {@link ValueKind#LIST}、{@link ValueKind#OBJECT} 及未来不支持的种类在 schema 构建阶段直接拒绝。
     * 只有非空、唯一的身份值才会被模型用于复用内部 key；空值或重复值按未知身份处理，不做猜测。</p>
     *
     * @param memberName 对象中已声明的 member 名称
     * @return 带身份声明的新 spec
     * @throws IllegalArgumentException member 不存在或不是受支持的稳定标量
     */
    public ValueSpec withIdentityMember(String memberName) {
        if (kind != ValueKind.OBJECT) {
            throw new IllegalStateException("identity member requires OBJECT spec");
        }
        if (memberName == null || !members.containsKey(memberName)) {
            throw new IllegalArgumentException("identity member must be a declared object member: " + memberName);
        }
        ValueKind identityKind = members.get(memberName).spec().kind();
        switch (identityKind) {
            case STRING:
            case NUMBER:
            case BOOLEAN:
            case CHOICE:
                break;
            default:
                throw new IllegalArgumentException("identity member '" + memberName
                        + "' must use a stable comparable scalar (STRING, NUMBER, BOOLEAN, or CHOICE), but was "
                        + identityKind);
        }
        return new ValueSpec(kind, element, members, choices, explicitDefault, hasExplicitDefault, memberName, widget);
    }

    /** @return 深冻结默认值 */
    public Object defaultValue() {
        if (hasExplicitDefault) {
            return copyAndFreeze(explicitDefault);
        }
        switch (kind) {
            case STRING: return "";
            case NUMBER: return Double.valueOf(0.0);
            case BOOLEAN: return Boolean.FALSE;
            case CHOICE: return choices.isEmpty() ? "" : choices.get(0);
            case LIST: return Collections.unmodifiableList(new ArrayList<Object>());
            case OBJECT:
                LinkedHashMap<String, Object> object = new LinkedHashMap<String, Object>();
                for (Member member : members.values()) {
                    object.put(member.name(), member.defaultValue());
                }
                return Collections.unmodifiableMap(object);
            default: throw new IllegalStateException("unknown value kind: " + kind);
        }
    }

    /** 按定义填充对象缺失 member，并深拷贝所有值；错误类型原样复制供校验拒绝。 */
    public Object normalize(Object value) {
        if (value == null) {
            return defaultValue();
        }
        switch (kind) {
            case LIST:
                if (!(value instanceof List)) return copyAndFreeze(value);
                List<Object> list = new ArrayList<Object>();
                for (Object item : (List<?>) value) {
                    list.add(item == null ? null : element.normalize(item));
                }
                return Collections.unmodifiableList(list);
            case OBJECT:
                if (!(value instanceof Map)) return copyAndFreeze(value);
                Map<?, ?> raw = (Map<?, ?>) value;
                LinkedHashMap<String, Object> object = new LinkedHashMap<String, Object>();
                for (Member member : members.values()) {
                    Object child = raw.containsKey(member.name())
                            ? raw.get(member.name()) : member.defaultValue();
                    object.put(member.name(), child == null ? null : member.spec().normalize(child));
                }
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    if (!members.containsKey(key)) object.put(key, copyAndFreeze(entry.getValue()));
                }
                return Collections.unmodifiableMap(object);
            default:
                return copyAndFreeze(value);
        }
    }

    /** 递归校验值，错误 key 使用传入 path 的嵌套路径。 */
    public Validation validate(Object value, String path) {
        if (isContainer(value)) {
            // 先做一次循环探测，避免恶意默认值/草稿让递归校验栈溢出。
            copyAndFreeze(value);
        }
        LinkedHashMap<String, String> errors = new LinkedHashMap<String, String>();
        validateInto(value, path, errors);
        return new Validation(errors);
    }

    private void validateInto(Object value, String path, Map<String, String> errors) {
        switch (kind) {
            case STRING:
                if (!(value instanceof String)) errors.put(path, "值必须是字符串");
                return;
            case NUMBER:
                if (!(value instanceof Number)) {
                    errors.put(path, "值必须是数字类型");
                } else {
                    double number = ((Number) value).doubleValue();
                    if (Double.isNaN(number) || Double.isInfinite(number)) {
                        errors.put(path, "值不是有限数字");
                    }
                }
                return;
            case BOOLEAN:
                if (!(value instanceof Boolean)) errors.put(path, "值必须是布尔类型");
                return;
            case CHOICE:
                if (!(value instanceof String) || (!choices.isEmpty() && !choices.contains(value))) {
                    errors.put(path, "值不在可选范围");
                }
                return;
            case LIST:
                if (!(value instanceof List)) {
                    errors.put(path, "值必须是列表");
                    return;
                }
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    element.validateInto(list.get(i), path + "[" + i + "]", errors);
                }
                return;
            case OBJECT:
                if (!(value instanceof Map)) {
                    errors.put(path, "值必须是对象");
                    return;
                }
                Map<?, ?> object = (Map<?, ?>) value;
                for (Member member : members.values()) {
                    if (object.containsKey(member.name())) {
                        member.spec().validateInto(object.get(member.name()), path + "." + member.name(), errors);
                    }
                }
                return;
            default:
                errors.put(path, "未知值类型");
        }
    }

    /** 判断一个自定义 validator path 是否是该值树的合法 schema 后代。 */
    public boolean acceptsPath(String suffix) {
        if (suffix == null || suffix.isEmpty()) return true;
        int cursor = 0;
        ValueSpec current = this;
        while (cursor < suffix.length()) {
            if (current.kind == ValueKind.LIST) {
                if (suffix.charAt(cursor) != '[') return false;
                int close = suffix.indexOf(']', cursor + 1);
                if (close < 0 || close == cursor + 1) return false;
                for (int i = cursor + 1; i < close; i++) {
                    if (!Character.isDigit(suffix.charAt(i))) return false;
                }
                current = current.element;
                cursor = close + 1;
            } else if (current.kind == ValueKind.OBJECT) {
                if (suffix.charAt(cursor) != '.') return false;
                int start = ++cursor;
                while (cursor < suffix.length() && suffix.charAt(cursor) != '.' && suffix.charAt(cursor) != '[') {
                    cursor++;
                }
                if (start == cursor) return false;
                Member member = current.members.get(suffix.substring(start, cursor));
                if (member == null) return false;
                current = member.spec();
            } else {
                return false;
            }
        }
        return true;
    }

    /** 从严格 YAML 节点读取值；未知 object member 递归转为普通值并保留。 */
    public Object readNode(ConfigNode node, String path, boolean strict) throws ConfigException {
        if (node == null || node.isNull()) return defaultValue();
        switch (kind) {
            case STRING:
            case CHOICE:
                if (node.getType() != ConfigNode.NodeType.STRING) return wrong(node, path, strict, "STRING");
                return node.asString();
            case NUMBER:
                if (node.getType() != ConfigNode.NodeType.NUMBER) return wrong(node, path, strict, "NUMBER");
                double number = node.asDouble();
                if (Double.isNaN(number) || Double.isInfinite(number)) {
                    if (strict) throw validationError(path + " must be finite");
                    return nodeToJava(node);
                }
                return Double.valueOf(number);
            case BOOLEAN:
                if (node.getType() != ConfigNode.NodeType.BOOLEAN) return wrong(node, path, strict, "BOOLEAN");
                return Boolean.valueOf(node.asBoolean());
            case LIST:
                if (node.getType() != ConfigNode.NodeType.LIST) return wrong(node, path, strict, "LIST");
                List<ConfigNode> nodes = node.asList();
                List<Object> result = new ArrayList<Object>(nodes == null ? 0 : nodes.size());
                if (nodes != null) {
                    for (int i = 0; i < nodes.size(); i++) {
                        result.add(element.readNode(nodes.get(i), path + "[" + i + "]", strict));
                    }
                }
                return Collections.unmodifiableList(result);
            case OBJECT:
                if (node.getType() != ConfigNode.NodeType.MAP) return wrong(node, path, strict, "MAP");
                Map<String, ConfigNode> map = node.asMap();
                LinkedHashMap<String, Object> object = new LinkedHashMap<String, Object>();
                for (Member member : members.values()) {
                    ConfigNode child = map == null ? null : map.get(member.name());
                    object.put(member.name(), member.spec().readNode(child, path + "." + member.name(), strict));
                }
                if (map != null) {
                    for (Map.Entry<String, ConfigNode> entry : map.entrySet()) {
                        if (!members.containsKey(entry.getKey())) object.put(entry.getKey(), nodeToJava(entry.getValue()));
                    }
                }
                return Collections.unmodifiableMap(object);
            default:
                throw new ConfigException("unknown value kind: " + kind, ConfigException.Category.VALIDATION);
        }
    }

    private Object wrong(ConfigNode node, String path, boolean strict, String expected) throws ConfigException {
        if (strict) throw validationError(path + " expected " + expected + " NodeType, got " + node.getType());
        return nodeToJava(node);
    }

    private static ConfigException validationError(String message) {
        return new ConfigException("structured value: " + message, ConfigException.Category.VALIDATION);
    }

    /** 对象 member 描述。 */
    public static final class Member {
        private final String name;
        private final ValueSpec spec;
        private final String displayLabel;
        private final String helper;

        public Member(String name, ValueSpec spec) {
            this(name, spec, null, null);
        }

        /**
         * 创建带可选展示元数据的对象 member。
         *
         * @param name 持久化 key 与校验路径名称
         * @param spec member 值描述
         * @param displayLabel 可选显示名；为空时回退到 name
         * @param helper 可选辅助说明
         */
        public Member(String name, ValueSpec spec, String displayLabel, String helper) {
            if (name == null || name.isEmpty() || name.indexOf('.') >= 0
                    || name.indexOf('[') >= 0 || name.indexOf(']') >= 0) {
                throw new IllegalArgumentException("member name is ambiguous: " + name);
            }
            require(spec, "spec");
            this.name = name;
            this.spec = spec;
            this.displayLabel = displayLabel;
            this.helper = helper;
        }

        /** @return member 名称 */
        public String name() { return name; }
        /** @return member 值描述 */
        public ValueSpec spec() { return spec; }
        /** @return UI 显示名；未声明时返回持久化名称 */
        public String displayLabel() {
            return displayLabel == null || displayLabel.isEmpty() ? name : displayLabel;
        }
        /** @return 可选 UI 辅助说明 */
        public String helper() { return helper; }
        /** @return member 默认值 */
        public Object defaultValue() { return spec.defaultValue(); }
    }

    /** 递归校验结果，不依赖 runtime。 */
    public static final class Validation {
        private final Map<String, String> errors;

        private Validation(Map<String, String> errors) {
            this.errors = Collections.unmodifiableMap(new LinkedHashMap<String, String>(errors));
        }

        /** @return 错误映射 */
        public Map<String, String> errors() { return errors; }
        /** @return 是否有错误 */
        public boolean hasErrors() { return !errors.isEmpty(); }
        private String firstMessage() { return errors.isEmpty() ? "" : errors.values().iterator().next(); }
    }

    private static void require(Object value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " must not be null");
    }

    private static boolean isContainer(Object value) {
        return value instanceof Map || value instanceof java.util.Collection
                || (value != null && value.getClass().isArray());
    }

    private static void ensureDepth(ValueSpec spec, int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("structured value depth exceeds " + MAX_DEPTH);
        if (spec.kind == ValueKind.LIST) ensureDepth(spec.element, depth + 1);
        if (spec.kind == ValueKind.OBJECT) {
            for (Member member : spec.members.values()) ensureDepth(member.spec(), depth + 1);
        }
    }

    /** schema 默认值冻结；只接受配置值树中的标量、List、Map、数组。 */
    public static Object copyAndFreeze(Object value) {
        return copy(value, Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>()));
    }

    private static Object copy(Object value, Set<Object> visiting) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Character
                || value instanceof Number || value instanceof Enum) return value;
        if (value instanceof Map) {
            if (!visiting.add(value)) throw new IllegalArgumentException("cyclic structured default");
            try {
                LinkedHashMap<Object, Object> out = new LinkedHashMap<Object, Object>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (entry.getKey() != null && !(entry.getKey() instanceof String)) {
                        throw new IllegalArgumentException("structured object keys must be String");
                    }
                    out.put(entry.getKey(), copy(entry.getValue(), visiting));
                }
                return Collections.unmodifiableMap(out);
            } finally { visiting.remove(value); }
        }
        if (value instanceof List || value instanceof java.util.Collection || value.getClass().isArray()) {
            if (!visiting.add(value)) throw new IllegalArgumentException("cyclic structured default");
            try {
                List<Object> out = new ArrayList<Object>();
                if (value instanceof List || value instanceof java.util.Collection) {
                    for (Object item : (java.util.Collection<?>) value) out.add(copy(item, visiting));
                } else {
                    int length = Array.getLength(value);
                    for (int i = 0; i < length; i++) out.add(copy(Array.get(value, i), visiting));
                }
                return Collections.unmodifiableList(out);
            } finally { visiting.remove(value); }
        }
        throw new IllegalArgumentException("unsupported structured value: " + value.getClass().getName());
    }

    private static Object nodeToJava(ConfigNode node) {
        if (node == null || node.isNull()) return null;
        switch (node.getType()) {
            case STRING: return node.asString();
            case NUMBER: return Double.valueOf(node.asDouble(0.0));
            case BOOLEAN: return Boolean.valueOf(node.asBoolean(false));
            case LIST:
                List<Object> list = new ArrayList<Object>();
                if (node.asList() != null) for (ConfigNode child : node.asList()) list.add(nodeToJava(child));
                return Collections.unmodifiableList(list);
            case MAP:
                LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
                if (node.asMap() != null) for (Map.Entry<String, ConfigNode> entry : node.asMap().entrySet()) {
                    map.put(entry.getKey(), nodeToJava(entry.getValue()));
                }
                return Collections.unmodifiableMap(map);
            default: return null;
        }
    }
}
