package club.heiqi.uilib.internal.font.angelica;

import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.MyMod;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.uniforms.CapturedRenderingState;

/**
 * Angelica 2.2.10 玩家标签批次回放围栏。
 *
 * <p>围栏只从 {@code NONE} phase 建立临时 {@code ENTITIES} phase；回放结束后经 2.2.10 的
 * {@code CapturedRenderingState#setCurrentEntityAndItem(int, int)} 一次性恢复 entity/item 配对状态。</p>
 *
 * <p>沿革：2.1.50 只提供 {@code setCurrentEntity(int)} / {@code setCurrentRenderedItem(int)}，且前者会
 * 隐式把 item ID 清零，旧实现只能"先 entity 后 item"两次调用绕开该副作用；2.2.10 把
 * {@code setCurrentEntity(int)} 收为 private 并新增配对 API，配对恢复即该语义的正解，也不再依赖两次
 * 调用之间的顺序假设。</p>
 */
public final class AngelicaNameTagReplayGuard {

    private static final AtomicBoolean INVALID_PHASE_WARNED = new AtomicBoolean();

    private AngelicaNameTagReplayGuard() {}

    /**
     * 在 Angelica entities phase 内执行回放；非法 phase 安全丢弃本批次。
     *
     * @param batch 当前 host scope 的标签回放批次
     */
    public static void runGuarded(Runnable batch) {
        runGuarded(
                batch,
                ProductionStateAccess.INSTANCE,
                INVALID_PHASE_WARNED,
                new WarningSink() {
                    @Override
                    public void warn() {
                        MyMod.LOG.warn("玩家标签回放遇到非 NONE Angelica phase，已丢弃本帧标签");
                    }
                });
    }

    /** 执行可替换状态访问的围栏核心，供纯 JVM 测试验证生命周期。 */
    static boolean runGuarded(
            Runnable batch,
            StateAccess state,
            AtomicBoolean invalidPhaseWarned,
            WarningSink warningSink) {
        if (batch == null || state == null || invalidPhaseWarned == null || warningSink == null) {
            throw new IllegalArgumentException("guard arguments must not be null");
        }
        if (!state.isPhaseNone()) {
            if (invalidPhaseWarned.compareAndSet(false, true)) {
                warningSink.warn();
            }
            return false;
        }

        int previousEntity = state.getCurrentEntity();
        int previousItem = state.getCurrentItem();
        boolean entitiesBegun = false;
        try {
            state.beginEntities();
            entitiesBegun = true;
            batch.run();
            return true;
        } finally {
            try {
                if (entitiesBegun) {
                    state.endEntities();
                }
            } finally {
                state.setCurrentEntityAndItem(previousEntity, previousItem);
            }
        }
    }

    /** Angelica phase 与 captured entity/item 状态访问边界。 */
    interface StateAccess {

        boolean isPhaseNone();

        int getCurrentEntity();

        int getCurrentItem();

        void beginEntities();

        void endEntities();

        void setCurrentEntityAndItem(int entityId, int itemId);
    }

    /** 非法 phase 的单次告警出口。 */
    interface WarningSink {

        void warn();
    }

    /** 直接绑定 Angelica 2.2.10 ABI 的生产访问器。 */
    private static final class ProductionStateAccess implements StateAccess {

        private static final ProductionStateAccess INSTANCE = new ProductionStateAccess();

        @Override
        public boolean isPhaseNone() {
            return GbufferPrograms.getCurrentPhase() == WorldRenderingPhase.NONE;
        }

        @Override
        public int getCurrentEntity() {
            return CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
        }

        @Override
        public int getCurrentItem() {
            return CapturedRenderingState.INSTANCE.getCurrentRenderedItem();
        }

        @Override
        public void beginEntities() {
            GbufferPrograms.beginEntities();
        }

        @Override
        public void endEntities() {
            GbufferPrograms.endEntities();
        }

        @Override
        public void setCurrentEntityAndItem(int entityId, int itemId) {
            // 2.2.10 起 setCurrentEntity(int) 为 private，配对 API 是唯一 public 的 entity/item 恢复入口。
            CapturedRenderingState.INSTANCE.setCurrentEntityAndItem(entityId, itemId);
        }
    }
}
