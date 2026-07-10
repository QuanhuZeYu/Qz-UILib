package club.heiqi.config.runtime;

import club.heiqi.config.schema.ConfigSchema;

import java.util.Collection;
import java.util.Map;

/**
 * 草稿只读视图，供 {@link DraftValidator} 在提交前读取值。
 *
 * <p>不暴露任何写接口（无 setDraft / setDraftAndCurrent / commit 等），
 * 防止自定义校验在 fail-closed 路径上污染原 {@link DraftBuffer}。</p>
 *
 * <p>本接口零依赖 uilib，仅依赖 schema 与 JDK。</p>
 */
public interface DraftView {

    /**
     * 取草稿值。
     *
     * @param path 字段路径
     * @return 草稿值，不存在时可能为 null
     */
    Object getDraft(String path);

    /**
     * 草稿快照的不可修改视图（构造时已拷贝，调用方无法改原 buffer）。
     *
     * @return path → 值映射，不可修改
     */
    Map<String, Object> draftSnapshot();

    /**
     * @return 关联 schema
     */
    ConfigSchema schema();

    /**
     * @return Schema 字段路径集合
     */
    Collection<String> fieldPaths();
}
