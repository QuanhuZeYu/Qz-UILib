package club.heiqi.config.ui.field;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;

/**
 * 字段渲染器注册表：按 {@link FieldType} 注册与解析 {@link FieldRenderer}，
 * 并支持按字段完整 path 覆盖（path 优先于 type）。
 *
 * <p>解析顺序（{@link #resolve(FieldSpec)}）：</p>
 * <ol>
 *   <li>先查 {@link #pathOverrides}（按字段完整 path 注册的覆盖表），
 *       命中则返回——用于让特定字段走专用 renderer（如 fontSort / characterFontRules
 *       走带拖拽/语法校验的专用 SIMPLE_LIST renderer）。</li>
 *   <li>未命中回落 {@link #renderers}（按 {@link FieldType} 注册的默认表）。</li>
 * </ol>
 *
 * <p>{@link #defaultRegistry()} 预注册 5 种默认 renderer（STRING / NUMBER / BOOLEAN / CHOICE / SIMPLE_LIST）。
 * 可通过 {@link #register} 替换默认实现或扩展新类型，通过 {@link #registerPath} 为特定字段挂覆盖。</p>
 *
 * <h3>path 格式</h3>
 * <p>{@link #registerPath} 的 key 必须与 {@link FieldSpec#path()} 返回值格式一致：
 * 点号分隔的 "section.field"（不含 schema 名前缀），例如 {@code "fontSystem.fontSort"}。</p>
 */
public final class FieldRendererRegistry {

    /** 按 FieldType 索引的渲染器表 */
    private final Map<FieldType, FieldRenderer> renderers;

    /** 按字段完整 path 索引的覆盖渲染器表，resolve 时优先命中此表 */
    private final Map<String, FieldRenderer> pathOverrides;

    /** 创建空注册表 */
    public FieldRendererRegistry() {
        this.renderers = new EnumMap<FieldType, FieldRenderer>(FieldType.class);
        this.pathOverrides = new HashMap<String, FieldRenderer>();
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
     * 按字段完整 path 注册覆盖 renderer。
     *
     * <p>覆盖优先级高于 {@link #register(FieldType, FieldRenderer)} 的 type 注册：
     * {@link #resolve(FieldSpec)} 会先查 pathOverrides，命中即返回，
     * 不再回落到 type 注册表。</p>
     *
     * <p>path 格式必须与 {@link FieldSpec#path()} 一致：点号分隔的 "section.field"，
     * 不含 schema 名前缀，例如 {@code "fontSystem.fontSort"}。</p>
     *
     * @param path     字段完整 path，不可为 null
     * @param renderer 覆盖渲染器，不可为 null
     * @throws IllegalArgumentException path 或 renderer 为 null
     */
    public void registerPath(String path, FieldRenderer renderer) {
        if (path == null || renderer == null) {
            throw new IllegalArgumentException("path 与 renderer 均不可为 null");
        }
        pathOverrides.put(path, renderer);
    }

    /**
     * 解析渲染器，优先级：pathOverrides &gt; renderers。
     *
     * <p>分发顺序：</p>
     * <ol>
     *   <li>spec 非空时，先按 {@link FieldSpec#path()} 查 {@link #pathOverrides}，命中返回。</li>
     *   <li>未命中再按 {@link FieldSpec#type()} 查 {@link #renderers}。</li>
     * </ol>
     *
     * @param spec 字段元数据
     * @return 渲染器，未注册返回 null
     */
    public FieldRenderer resolve(FieldSpec spec) {
        if (spec == null) {
            return null;
        }
        FieldRenderer override = pathOverrides.get(spec.path());
        if (override != null) {
            return override;
        }
        return renderers.get(spec.type());
    }

    /**
     * 创建默认注册表，预注册 5 种默认 renderer。
     *
     * @return 预填充的注册表
     */
    public static FieldRendererRegistry defaultRegistry() {
        FieldRendererRegistry registry = new FieldRendererRegistry();
        registry.register(FieldType.STRING, new StringFieldRenderer());
        registry.register(FieldType.NUMBER, new NumberFieldRenderer());
        registry.register(FieldType.BOOLEAN, new BooleanFieldRenderer());
        registry.register(FieldType.CHOICE, new ChoiceFieldRenderer());
        registry.register(FieldType.SIMPLE_LIST, new SimpleListFieldRenderer());
        return registry;
    }
}
