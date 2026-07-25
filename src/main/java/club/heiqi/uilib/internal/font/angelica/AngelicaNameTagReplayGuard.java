package club.heiqi.uilib.internal.font.angelica;

import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.MyMod;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.uniforms.CapturedRenderingState;

/**
 * Angelica 2.1.50 玩家标签批次回放围栏。
 *
 * <p>围栏只从 {@code NONE} phase 建立临时 {@code ENTITIES} phase，并按 entity、item
 * 的顺序恢复捕获状态，避免 {@code setCurrentEntity} 对 item ID 的隐式清零污染调用方。</p>
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
                try {
                    state.setCurrentEntity(previousEntity);
                } finally {
                    state.setCurrentItem(previousItem);
                }
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

        void setCurrentEntity(int entityId);

        void setCurrentItem(int itemId);
    }

    /** 非法 phase 的单次告警出口。 */
    interface WarningSink {

        void warn();
    }

    /** 直接绑定 Angelica 2.1.50 ABI 的生产访问器。 */
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
        public void setCurrentEntity(int entityId) {
            CapturedRenderingState.INSTANCE.setCurrentEntity(entityId);
        }

        @Override
        public void setCurrentItem(int itemId) {
            CapturedRenderingState.INSTANCE.setCurrentRenderedItem(itemId);
        }
    }
}
