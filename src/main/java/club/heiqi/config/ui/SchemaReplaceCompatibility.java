package club.heiqi.config.ui;

import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;

/**
 * replaceDraft / 同 owner 下 schema 兼容性纯判定。
 *
 * <p>在 owner 已确认同一的前提下，路径集合与每字段类型必须一致。
 * constraints / defaultValue / widget 随 bootstrap 的 schema 冻结，ConfigManager
 * 不提供 schema reload——同 owner 即绑定该冻结 schema。</p>
 *
 * <p>本类无副作用，可供单元测试直接覆盖 missing path / type mismatch，
 * 不被 owner mismatch 提前截断。</p>
 */
public final class SchemaReplaceCompatibility {

    private SchemaReplaceCompatibility() {
    }

    /**
     * 校验 next 相对 current 是否可替换（路径集合 + 字段类型）。
     *
     * @param current 当前 adapter 绑定的 schema
     * @param next    新 draft 的 schema
     * @throws IllegalArgumentException 不兼容时；消息含 missing path / type mismatch
     */
    public static void checkCompatible(ConfigSchema current, ConfigSchema next) {
        if (current == null) {
            throw new IllegalArgumentException("current schema must not be null");
        }
        if (next == null) {
            throw new IllegalArgumentException("next schema must not be null");
        }
        for (FieldSpec field : current.allFields()) {
            FieldSpec other = next.field(field.path());
            if (other == null) {
                throw new IllegalArgumentException(
                        "replaceDraft rejected: missing path " + field.path());
            }
            if (other.type() != field.type()) {
                throw new IllegalArgumentException(
                        "replaceDraft rejected: type mismatch at " + field.path()
                                + " expected " + field.type() + " got " + other.type());
            }
        }
        for (FieldSpec field : next.allFields()) {
            if (current.field(field.path()) == null) {
                throw new IllegalArgumentException(
                        "replaceDraft rejected: unexpected path " + field.path());
            }
        }
    }
}
