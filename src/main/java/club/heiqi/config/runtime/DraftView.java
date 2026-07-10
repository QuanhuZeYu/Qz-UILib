package club.heiqi.config.runtime;

import java.util.Collection;
import java.util.Map;

/**
 * 草稿只读视图，供 {@link DraftValidator} 在提交前读取<strong>Schema 字段</strong>值。
 *
 * <p>不暴露写接口、不暴露 {@code ConfigSchema}/defaultValue 容器、不暴露非 Schema 子树。
 * 实现须对 List/Map/数组做深度 defensive copy 并 unmodifiable。</p>
 *
 * <p>本接口零依赖 uilib。</p>
 */
public interface DraftView {

    /**
     * 取 Schema 字段草稿值（深度冻结后的只读结构；标量可复用引用）。
     *
     * @param path 字段路径
     * @return 草稿值；非 schema path 或不存在时可能为 null
     */
    Object getDraft(String path);

    /**
     * Schema 字段草稿快照的深度只读视图（仅 schema path，无 raw 子树）。
     *
     * @return path → 值，顶层与嵌套容器均不可修改
     */
    Map<String, Object> draftSnapshot();

    /**
     * @return Schema 字段路径集合（不可修改）
     */
    Collection<String> fieldPaths();
}
