package club.heiqi.uilib.ui.scene.host.lwjgl;

/**
 * scene 宿主文本桥生命周期协调器。
 *
 * <p>把注册成功判定、重复 init 幂等和 close finally 语义从 GuiScreen/GL 生命周期中分离，
 * 便于纯 JVM 测试覆盖；真正的反射仍只在 {@link SceneLwjgl3ifyTextBridge} 内。</p>
 */
public final class SceneTextBridgeLifecycle {

    /** 文本桥注册/注销窄接口。 */
    public interface Registration {
        /** @return 是否注册成功 */
        boolean register();

        /** 注销监听器。 */
        void unregister();
    }

    /** 宿主 external text mode 写入口。 */
    public interface Mode {
        /** @param external true=完整文本由外部桥接接管 */
        void setExternalTextMode(boolean external);
    }

    private boolean active;

    /** 创建未注册的生命周期协调器。 */
    public SceneTextBridgeLifecycle() {
        this.active = false;
    }

    /**
     * 注册文本桥；重复调用不重复注册，并保持 external mode 为 true。
     *
     * @param registration 注册/注销实现
     * @param mode 宿主模式写入口
     * @return true=当前已启用完整文本模式；false=保持降级模式
     */
    public boolean init(Registration registration, Mode mode) {
        if (active) {
            mode.setExternalTextMode(true);
            return true;
        }
        try {
            if (!registration.register()) {
                mode.setExternalTextMode(false);
                return false;
            }
            mode.setExternalTextMode(true);
            active = true;
            return true;
        } catch (RuntimeException exception) {
            active = false;
            // register 可能在已添加监听器后、返回结果前抛异常；unregister 本身必须幂等。
            try {
                registration.unregister();
            } catch (RuntimeException ignored) {
                // 注册失败已经确定，清理失败也不能阻止宿主回到降级模式。
            } finally {
                mode.setExternalTextMode(false);
            }
            return false;
        }
    }

    /**
     * 注销文本桥并无条件复位 external mode。
     *
     * @param registration 注册/注销实现
     * @param mode 宿主模式写入口
     */
    public void close(Registration registration, Mode mode) {
        try {
            // unregister 设计为幂等；即使 init 失败也尝试清理可能已部分注册的监听器。
            registration.unregister();
        } finally {
            active = false;
            mode.setExternalTextMode(false);
        }
    }

    /** @return 是否已成功注册并启用 external mode */
    public boolean isActive() {
        return active;
    }
}
