package club.heiqi.uilib.ui.screen.page;

import club.heiqi.uilib.ui.render.BackdropBlurPolicy;

/**
 * 文档页面控制器基类。
 *
 * <p>该 seam 把文档页面的生命周期从具体 Screen 宿主中抽离出来，
 * 便于后续将页面实现收敛为薄包装器，同时保持当前文档页面的构建语义不变。
 * 控制器运行所需依赖统一通过构造器注入，生命周期方法不再重复传递 page、ui 或 runtime。
 * 生命周期方法用 public 暴露，供同包外的 hosted factory 在屏幕生命周期中显式调用，
 * 子类仍可按需 override 这些方法以接入业务逻辑。</p>
 */
public abstract class DocumentPageController {

    /**
     * 配置文档壳的静态语义约束。
     */
    public void configureDocumentPage() {
    }

    /**
     * 构建文档内容。
     */
    public abstract void buildDocument();

    /**
     * 在文档首次构建完成后补充初始化状态。
     */
    public void afterDocumentBuilt() {
    }

    /**
     * 在文档壳尺寸变化后执行页面刷新。
     */
    public void onDocumentResized() {
    }

    /**
     * 每帧在宿主绘制前刷新页面状态。
     */
    public void beforeDocumentFrame() {
    }

    /**
     * 返回当前页面有效背景模糊策略。
     *
     * @return 背景模糊策略
     */
    public BackdropBlurPolicy getBackdropBlurPolicy() {
        return BackdropBlurPolicy.inheritGlobal();
    }

    /**
     * 在文档宿主界面关闭时释放页面状态。
     */
    public void onDocumentClosed() {
    }
}
