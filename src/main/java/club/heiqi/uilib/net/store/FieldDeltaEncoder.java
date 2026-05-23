package club.heiqi.uilib.net.store;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.net.codec.FieldLayout;

/**
 * Store 字段增量计算器。
 */
public final class FieldDeltaEncoder {

    /**
     * 计算两个快照之间变化的字段索引。
     *
     * @param before 旧快照
     * @param after 新快照
     * @return 字段增量列表
     */
    public List<FieldDelta> diff(Object before, Object after) {
        if (before == null || after == null) {
            return new ArrayList<FieldDelta>();
        }
        if (before.getClass() != after.getClass()) {
            throw new IllegalArgumentException("Store diff 类型不一致：" + before.getClass() + " vs "
                    + after.getClass());
        }
        FieldLayout layout = FieldLayout.forClass(after.getClass());
        List<FieldDelta> deltas = new ArrayList<FieldDelta>();
        List<FieldLayout.FieldDescriptor> fields = layout.getFields();
        for (int index = 0; index < fields.size(); index++) {
            Field field = fields.get(index).getField();
            try {
                Object left = field.get(before);
                Object right = field.get(after);
                if (!Objects.equals(left, right)) {
                    deltas.add(new FieldDelta(index, right));
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("无法读取 Store 字段：" + field.getName(), exception);
            }
        }
        return deltas;
    }

    /**
     * 字段增量。
     */
    public static final class FieldDelta {

        private final int fieldIndex;
        private final Object value;

        FieldDelta(int fieldIndex, Object value) {
            this.fieldIndex = fieldIndex;
            this.value = value;
        }

        public int getFieldIndex() {
            return fieldIndex;
        }

        public Object getValue() {
            return value;
        }
    }
}
