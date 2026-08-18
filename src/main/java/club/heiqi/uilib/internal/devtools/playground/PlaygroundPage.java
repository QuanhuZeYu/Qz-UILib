package club.heiqi.uilib.internal.devtools.playground;

import java.util.function.Supplier;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 测试场地演示页契约。
 *
 * <p>页面是「可挂载的 scene 组件函数」：{@link #build} 返回 {@link Supplier}，
 * 由宿主在页切换时经 {@code runtime.mount(content, ...)} 在当前 Owner 作用域内执行一次
 * （scene 信条 I3：组件函数只执行一次，随后外观随状态经 bind 派生、交互只经 on 回调）。</p>
 *
 * <p>推荐实现形态：页面实例持有自己的 {@code Signal} 状态（页切换保留——测试场地便于
 * 「切走再切回」观察状态粘性），{@code build} 引用这些信号构建树并注册
 * {@code bind/on/show/forEach} 等响应式接线。</p>
 */
public interface PlaygroundPage {

    /**
     * 稳定页面 id（小写字母 + 数字 + 连字符），注册表唯一性断言锚点。
     *
     * @return 页面唯一 id
     */
    String id();

    /**
     * 导航段与 Home 总览展示的标题。
     *
     * @return 页面标题
     */
    String title();

    /**
     * 一句话说明页面演示什么（Home 总览列表展示）。
     *
     * @return 页面说明
     */
    String description();

    /**
     * 构建页面组件函数；由宿主在页切换时 mount 一次。
     *
     * <p>返回的函数体在 mount 的 Owner 作用域内执行；页切换离开时 mount 句柄 dispose，
     * 该作用域内创建的 bind/effect/on 订阅随之回收。</p>
     *
     * @param rt 场景运行时（bind/on/mount/show/portal 等入口）
     * @return 页面根节点组件函数
     */
    Supplier<SceneNode> build(SceneRuntime rt);
}
