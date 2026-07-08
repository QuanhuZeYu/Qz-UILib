package club.heiqi.uilib.ui.scene.control;

import static club.heiqi.uilib.ui.scene.control.SceneTextUtils.nullSafe;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 单行键值对数据。
 *
 * <p>公开字段模型保持 key/value/type 三元组语义，同时携带稳定 rowId 供 forEach keyed 使用。
 * 复制更新时保留 rowId；业务也可用三参构造器创建新行。</p>
 */
public class KeyValueRow {
    /**
     * 行 id 分配器，用于 keyed 列表稳定身份。
     */
    private static final AtomicLong NEXT_ROW_ID = new AtomicLong(1L);
    /**
     * 稳定行 id。
     */
    private final long rowId;
    /**
     * key 文本。
     */
    private String key;
    /**
     * value 文本。
     */
    private String value;
    /**
     * value 类型。
     */
    private ValueType type;

    /**
     * 创建空字符串类型行。
     */
    public KeyValueRow() {
        this("", "", ValueType.STRING);
    }

    /**
     * 创建一行键值对。
     *
     * @param key   key 文本
     * @param value value 文本
     * @param type  value 类型
     */
    public KeyValueRow(String key, String value, ValueType type) {
        this(NEXT_ROW_ID.getAndIncrement(), key, value, type);
    }

    /**
     * 创建带稳定 id 的一行键值对。
     *
     * @param rowId 稳定行 id
     * @param key   key 文本
     * @param value value 文本
     * @param type  value 类型
     */
    private KeyValueRow(long rowId, String key, String value, ValueType type) {
        this.rowId = rowId;
        this.key = nullSafe(key);
        this.value = nullSafe(value);
        this.type = type == null ? ValueType.STRING : type;
    }

    /**
     * 获取稳定行 id。
     *
     * @return 稳定行 id
     */
    public long getRowId() {
        return rowId;
    }

    /**
     * 获取 key 文本。
     *
     * @return key 文本
     */
    public String getKey() {
        return key;
    }

    /**
     * 设置 key 文本。
     *
     * @param key key 文本
     */
    public void setKey(String key) {
        this.key = nullSafe(key);
    }

    /**
     * 获取 value 文本。
     *
     * @return value 文本
     */
    public String getValue() {
        return value;
    }

    /**
     * 设置 value 文本。
     *
     * @param value value 文本
     */
    public void setValue(String value) {
        this.value = nullSafe(value);
    }

    /**
     * 获取 value 类型。
     *
     * @return value 类型
     */
    public ValueType getType() {
        return type;
    }

    /**
     * 设置 value 类型。
     *
     * @param type value 类型
     */
    public void setType(ValueType type) {
        this.type = type == null ? ValueType.STRING : type;
    }

    /**
     * 复制为同 id 的新行。
     *
     * @param key   新 key
     * @param value 新 value
     * @param type  新类型
     * @return 新行对象
     */
    public KeyValueRow copyWith(String key, String value, ValueType type) {
        return new KeyValueRow(rowId, key, value, type);
    }
}
