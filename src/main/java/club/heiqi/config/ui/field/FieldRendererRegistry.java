package club.heiqi.config.ui.field;

import java.util.EnumMap;
import java.util.Map;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;

/**
 * 字段渲染器注册表：按 {@link FieldType} 注册与解析 {@link FieldRenderer}。
 *
 * <p>{@link #defaultRegistry()} 预注册 4 种默认 renderer（STRING / NUMBER / BOOLEAN / CHOICE）。
 * 可通过 {@link #register} 替换默认实现或扩展新类型。</p>
 */
public final class FieldRendererRegistry {

    /** 按 FieldType 索引的渲染器表 */
    private final Map<FieldType, FieldRenderer> renderers;

    /** 创建空注册表 */
    public FieldRendererRegistry() {
        this.renderers = new EnumMap<FieldType, FieldRenderer>(FieldType.class);
    }

    /**
     * 注册某类型的渲染器，覆盖已有注册。
     *
     * @param type     字段类型
     * @param renderer 渲染器
     */
    public void register(FieldType type, FieldRenderer renderer) {
        if (type == null || renderer == null) {
            throw new IllegalArgumentException("type 与 renderer 均不可为 null");
        }
        renderers.put(type, renderer);
    }

    /**
     * 按 spec 类型解析渲染器。
     *
     * @param spec 字段元数据
     * @return 渲染器，未注册返回 null
     */
    public FieldRenderer resolve(FieldSpec spec) {
        if (spec == null) {
            return null;
        }
        return renderers.get(spec.type());
    }

    /**
     * 创建默认注册表，预注册 4 种默认 renderer。
     *
     * @return 预填充的注册表
     */
    public static FieldRendererRegistry defaultRegistry() {
        FieldRendererRegistry registry = new FieldRendererRegistry();
        registry.register(FieldType.STRING, new StringFieldRenderer());
        registry.register(FieldType.NUMBER, new NumberFieldRenderer());
        registry.register(FieldType.BOOLEAN, new BooleanFieldRenderer());
        registry.register(FieldType.CHOICE, new ChoiceFieldRenderer());
        return registry;
    }
}
