package club.heiqi.config.runtime;

import club.heiqi.config.schema.ConfigSchema;

import java.util.Collection;
import java.util.Map;

/**
 * 草稿只读视图，供 {@link DraftValidator} 在提交前读取值。
 *
 * <p>不暴露任何写接口（无 setDraft / setDraftAndCurrent / commit 等）。
 * 实现须对 List / Map / Collection / 数组做<strong>深度</strong> defensive copy 并 unmodifiable，
 * 防止校验器原地修改 SIMPLE_LIST 等容器污染原 {@link DraftBuffer}。</p>
 *
 * <p>{@link #schema()} 返回的 {@link ConfigSchema} 本身不可变（构造时 sections/fields 已冻结）。</p>
 *
 * <p>本接口零依赖 uilib，仅依赖 schema 与 JDK。</p>
 */
public interface DraftView {

    /**
     * 取草稿值（深度冻结后的只读结构；标量可复用引用）。
     *
     * @param path 字段路径
     * @return 草稿值，不存在时可能为 null
     */
    Object getDraft(String path);

    /**
     * 草稿快照的深度只读不可修改视图。
     *
     * @return path → 值映射，顶层与嵌套容器均不可修改
     */
    Map<String, Object> draftSnapshot();

    /**
     * 关联 schema（不可变对象，可安全只读）。
     *
     * @return schema
     */
    ConfigSchema schema();

    /**
     * @return Schema 字段路径集合（不可修改）
     */
    Collection<String> fieldPaths();
}
