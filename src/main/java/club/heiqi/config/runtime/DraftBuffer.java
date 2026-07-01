package club.heiqi.config.runtime;

import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldConstraints;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 纯数据草稿容器，承载用户编辑中的三态（current / draft）。
 *
 * <p>关键约束：</p>
 * <ul>
 *   <li>零 signal、零 uilib 依赖，仅依赖 JDK 与 schema 包。</li>
 *   <li>每字段持 current + draft 两个值，分别存于两个独立 {@link Map}，物理隔离。</li>
 *   <li>{@link #from(Authority)} 从权威态深拷贝种子，current = draft = 权威值。</li>
 *   <li>{@link #isDirty(String)} 比较 draft 与 current；{@link #isDirtyAny()} 任一字段脏即 true。</li>
 *   <li>{@link #validateAll()} 按 {@link FieldConstraints} 校验每个 Schema 字段。</li>
 *   <li>{@link #commitDraftToCurrent()} 保存成功后同步 current = draft。</li>
 * </ul>
 *
 * <p>本类不持有 {@link Authority} 引用，仅持 schema 与值映射，避免循环依赖。</p>
 */
public final class DraftBuffer {

    private final ConfigSchema schema;
    private final Map<String, Object> currentValues;
    private final Map<String, Object> draftValues;

    private DraftBuffer(ConfigSchema schema, Map<String, Object> seed) {
        this.schema = schema;
        this.currentValues = new HashMap<String, Object>(seed);
        this.draftValues = new HashMap<String, Object>(seed);
    }

    /**
     * 从权威态深拷贝创建草稿。
     *
     * @param authority 权威快照
     * @return 草稿容器
     */
    public static DraftBuffer from(Authority authority) {
        if (authority == null) {
            throw new IllegalArgumentException("authority must not be null");
        }
        return new DraftBuffer(authority.schema(), authority.snapshotTyped());
    }

    /**
     * 取草稿值。
     *
     * @param path 字段路径
     * @return 草稿值
     */
    public Object getDraft(String path) {
        return draftValues.get(path);
    }

    /**
     * 取当前值（上次保存或加载的值）。
     *
     * @param path 字段路径
     * @return 当前值
     */
    public Object getCurrent(String path) {
        return currentValues.get(path);
    }

    /**
     * 设置草稿值。
     *
     * @param path  字段路径
     * @param value 草稿值
     */
    public void setDraft(String path, Object value) {
        draftValues.put(path, value);
    }

    /**
     * 单字段是否脏（draft != current）。
     *
     * @param path 字段路径
     * @return 脏返回 true
     */
    public boolean isDirty(String path) {
        return !Objects.equals(draftValues.get(path), currentValues.get(path));
    }

    /**
     * 任意 Schema 字段脏则 true。
     *
     * @return 有脏字段返回 true
     */
    public boolean isDirtyAny() {
        for (FieldSpec field : schema.allFields()) {
            if (isDirty(field.path())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 取指定字段的校验错误信息。
     *
     * @param path 字段路径
     * @return 错误信息，无错返回 null
     */
    public String error(String path) {
        return validateAll().errorFor(path);
    }

    /**
     * 是否存在任意校验错误。
     *
     * @return 有错返回 true
     */
    public boolean hasError() {
        return validateAll().hasErrors();
    }

    /**
     * 校验全部 Schema 字段。
     *
     * <p>校验规则：</p>
     * <ul>
     *   <li>{@link FieldConstraints#required()}：值为 null 或空串报错。</li>
     *   <li>{@link FieldType#NUMBER}：超出 [min, max] 报错。</li>
     *   <li>{@link FieldType#STRING}：长度超过 maxLength（maxLength > 0 时）报错。</li>
     *   <li>{@link FieldType#CHOICE}：值不在 choices 列表（choices 非空时）报错。</li>
     * </ul>
     *
     * @return 校验结果
     */
    public ValidationResult validateAll() {
        Map<String, String> errors = new HashMap<String, String>();
        for (FieldSpec field : schema.allFields()) {
            String msg = validateField(field);
            if (msg != null) {
                errors.put(field.path(), msg);
            }
        }
        return ValidationResult.of(errors);
    }

    /**
     * 重置全部草稿为当前值。
     */
    public void resetToCurrent() {
        draftValues.clear();
        draftValues.putAll(currentValues);
    }

    /**
     * 重置单字段草稿为默认值，current 不变。
     *
     * <p>默认值经 {@link Authority#normalizeDefault(Object, FieldType)} 规范化为 typed 类型，
     * 避免与 current 的 typed 值类型不一致导致 {@link #isDirty(String)} 误报。</p>
     *
     * @param path 字段路径
     */
    public void resetFieldToDefault(String path) {
        FieldSpec field = schema.field(path);
        if (field == null) {
            return;
        }
        draftValues.put(path, Authority.normalizeDefault(field.defaultValue(), field.type()));
    }

    /**
     * 草稿快照，保存事务用。返回新 Map，调用方修改不影响本对象。
     *
     * @return 草稿值映射的拷贝
     */
    public Map<String, Object> draftSnapshot() {
        return new HashMap<String, Object>(draftValues);
    }

    /**
     * 保存成功后同步 current = draft。
     */
    public void commitDraftToCurrent() {
        currentValues.clear();
        currentValues.putAll(draftValues);
    }

    /**
     * @return 关联的 schema
     */
    public ConfigSchema schema() {
        return schema;
    }

    /**
     * @return 全部 Schema 字段路径
     */
    public Collection<String> fieldPaths() {
        java.util.List<String> paths = new java.util.ArrayList<String>();
        for (FieldSpec field : schema.allFields()) {
            paths.add(field.path());
        }
        return paths;
    }

    /**
     * 校验单字段，返回错误信息或 null。
     */
    private String validateField(FieldSpec field) {
        Object value = draftValues.get(field.path());
        FieldConstraints c = field.constraints();

        // required 校验
        if (c != null && c.required()) {
            if (value == null) {
                return "字段必填";
            }
            if (value instanceof String && ((String) value).isEmpty()) {
                return "字段必填";
            }
        }

        if (value == null) {
            return null;
        }

        switch (field.type()) {
            case NUMBER: {
                double v;
                if (value instanceof Number) {
                    v = ((Number) value).doubleValue();
                } else {
                    try {
                        v = Double.parseDouble(String.valueOf(value));
                    } catch (NumberFormatException e) {
                        return "值不是有效数字";
                    }
                }
                if (c != null) {
                    if (v < c.min()) {
                        return "数值 " + v + " 小于下限 " + c.min();
                    }
                    if (v > c.max()) {
                        return "数值 " + v + " 大于上限 " + c.max();
                    }
                }
                break;
            }
            case STRING: {
                if (c != null && c.maxLength() >= 0) {
                    int len = String.valueOf(value).length();
                    if (len > c.maxLength()) {
                        return "长度 " + len + " 超过上限 " + c.maxLength();
                    }
                }
                break;
            }
            case CHOICE: {
                if (c != null && c.choices() != null && !c.choices().isEmpty()) {
                    String str = String.valueOf(value);
                    if (!c.choices().contains(str)) {
                        return "值 " + str + " 不在可选范围";
                    }
                }
                break;
            }
            case BOOLEAN:
            default:
                break;
        }
        return null;
    }
}
