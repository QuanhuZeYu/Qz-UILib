package club.heiqi.uilib.ui.reactive;

import java.util.ArrayList;
import java.util.List;

/**
 * 生命周期作用域：统一管理一批 effect，{@link #dispose()} 时全部释放（信条三）。
 * <p>对应宪章③「组件挂载/卸载」——宿主卸载组件时调用 dispose 一次性清理所有订阅。</p>
 */
public final class Owner {

    private final List<Effect> effects = new ArrayList<>();

    /**
     * 在本作用域内创建一个 effect，生命周期绑定到此 Owner。
     *
     * @param body effect 体（I3：动态行为的唯一落点）
     * @return 创建的 effect（通常不需要直接持有）
     */
    public Effect createEffect(Runnable body) {
        Effect e = new Effect(body);
        effects.add(e);
        return e;
    }

    /** 销毁全部子 effect，释放订阅关系。 */
    public void dispose() {
        for (Effect e : effects) e.dispose();
        effects.clear();
    }
}
