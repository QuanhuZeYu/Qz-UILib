package club.heiqi.config;

/**
 * 配置变更监听器接口
 */
public interface ConfigChangeListener {

    /**
     * 配置发生变更时调用
     * 
     * @param event 变更事件
     */
    void onConfigChanged(ConfigChangeEvent event);
}
