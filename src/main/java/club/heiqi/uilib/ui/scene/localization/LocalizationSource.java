package club.heiqi.uilib.ui.scene.localization;

import java.util.function.Consumer;

/** 独立已加载资料入口。订阅建立时同步交付当前资料，随后递交的事实按发布顺序单调推进；
 * 重入发布可合并尚未递交的旧事实，不能在新事实之后回送旧事实。current始终返回最新资料；
 * 初次交付与订阅登记须为同一受控操作，不能在current和subscribe之间漏掉换代。
 * 通知可能来自资料提供者线程；调用方经LocalizationBinding递交自己的状态域。 */
public interface LocalizationSource {
    LocalizationSnapshot current();

    Subscription subscribe(Consumer<LocalizationSnapshot> listener);

    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
