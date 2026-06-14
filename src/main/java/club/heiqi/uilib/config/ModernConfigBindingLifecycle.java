package club.heiqi.uilib.config;

/**
 * 现代配置字段绑定生命周期协议，支持实例池复用。
 *
 * <p>当嵌套分类绑定执行 {@code rebuildModel()} 时，可尝试复用已有绑定实例，
 * 避免重复创建绑定对象和关联的 DOM 结构。</p>
 */
interface ModernConfigBindingLifecycle {

    /**
     * 判断当前绑定是否可复用于指定路径和类型。
     *
     * @param path 目标配置路径
     * @param templateType 目标模板类型
     * @return true 表示可复用
     */
    boolean canReuse(String path, ModernConfigTypeInference.TemplateType templateType);

    /**
     * 重置绑定状态，准备复用于新的配置项。
     *
     * <p>子类应覆盖此方法以重置特定状态（如草稿值、验证错误等）。</p>
     */
    void reset();

    /**
     * 释放绑定持有的资源（如 DOM 引用、监听器等）。
     *
     * <p>当绑定不再需要时调用，子类应覆盖此方法以清理特定资源。</p>
     */
    void dispose();
}
