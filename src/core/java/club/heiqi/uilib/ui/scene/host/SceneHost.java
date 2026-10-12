package club.heiqi.uilib.ui.scene.host;

import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;

/** 宿主只提供环境与回放，不协调布局或修改声明树。 */
public interface SceneHost {
    /** 借用应用级配置；未提供时页面采用浅色默认，不创建全局单例。 */
    default ThemeConfiguration themeConfiguration() { return null; }

    /** 帧开始在宿主调用线程推进有界资源入口，再由服务投受控domain；不逐节点轮询。 */
    default void prepareResources() {}

    int width();

    int height();

    /** 字体、主题或度量环境改变时递增；尺寸变化会单独被框架感知。 */
    long metricsRevision();

    void replay(PaintPlan plan);
}
