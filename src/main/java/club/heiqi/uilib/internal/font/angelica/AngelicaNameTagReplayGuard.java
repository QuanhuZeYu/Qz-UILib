package club.heiqi.uilib.internal.font.angelica;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.MyMod;
import net.coderbot.iris.layer.GbufferPrograms;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.uniforms.CapturedRenderingState;

/**
 * Angelica 玩家标签批次回放围栏（同一份字节码支持 2.1.50 与 2.2.10 两档 ABI）。
 *
 * <p>围栏只从 {@code NONE} phase 建立临时 {@code ENTITIES} phase；回放结束后由 {@link RecoveryDispatch}
 * 在运行期选择可用的 entity/item 恢复入口：2.2.10 起走配对方法 {@code setCurrentEntityAndItem(int, int)}，
 * 2.1.x 走 {@code setCurrentEntity(int)} + {@code setCurrentRenderedItem(int)} 两段式。</p>
 *
 * <p>沿革：2.1.50 的 {@code setCurrentEntity(int)} 会隐式把 item ID 清零，旧实现只能"先 entity、后 item"
 * 两次调用绕开该副作用；2.2.10 把 {@code setCurrentEntity(int)} 收为 private 并新增配对 API，
 * 配对恢复即该语义的正解。</p>
 */
public final class AngelicaNameTagReplayGuard {

    private static final AtomicBoolean INVALID_PHASE_WARNED = new AtomicBoolean();
    /** 恢复入口不可用时的降级告警只发一次：本路径位于每帧回放入口。 */
    private static final AtomicBoolean RESTORE_UNAVAILABLE_WARNED = new AtomicBoolean();

    private AngelicaNameTagReplayGuard() {}

    /**
     * 在 Angelica entities phase 内执行回放；非法 phase 安全丢弃本批次。
     *
     * <p>若当前 Angelica 版本没有可用的 entity/item 恢复入口（未复核版本），本方法退化为"只执行批次、
     * 不进 phase 围栏、不触碰捕获状态"的即时绘制语义，且不向调用方抛错。</p>
     *
     * @param batch 当前 host scope 的标签回放批次
     */
    public static void runGuarded(Runnable batch) {
        if (batch == null) {
            throw new IllegalArgumentException("batch must not be null");
        }
        if (!ProductionStateAccess.isRestoreDispatchUsable()) {
            if (RESTORE_UNAVAILABLE_WARNED.compareAndSet(false, true)) {
                MyMod.LOG.warn("玩家标签回放缺少可用的 Angelica entity/item 恢复入口，本帧标签保持即时绘制");
            }
            batch.run();
            return;
        }
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

    /**
     * entity/item 恢复入口的运行期分派（纯反射，不依赖上游类型，可直接单测）。
     *
     * <p>只认 public 方法契约（{@code Class#getMethod}），不做 {@code setAccessible}：两档 Angelica 的
     * {@code CapturedRenderingState} public 方法集恰好互斥，且该类直接 {@code extends Object}，
     * 探针不会被父类继承干扰。dev jar javap 复核结论：</p>
     * <ul>
     *   <li>2.1.50：{@code setCurrentEntityAndItem(int,int)} 不存在（第一步必抛 NoSuchMethodException），
     *       {@code setCurrentEntity(int)} 与 {@code setCurrentRenderedItem(int)} 均为 public → 两段式；</li>
     *   <li>2.2.10：配对方法 public（第一步必命中）；即使未命中，第二步也取不到转 private 的
     *       {@code setCurrentEntity(int)} → 判定不可用并安全降级。</li>
     * </ul>
     */
    static final class RecoveryDispatch {

        private final Method pairedMethod;
        private final Method legacyEntityMethod;
        private final Method legacyItemMethod;

        private RecoveryDispatch(Method pairedMethod, Method legacyEntityMethod, Method legacyItemMethod) {
            this.pairedMethod = pairedMethod;
            this.legacyEntityMethod = legacyEntityMethod;
            this.legacyItemMethod = legacyItemMethod;
        }

        /**
         * 按 public 方法契约解析恢复入口：优先配对方法，其次两段式，都不具备则不可用。
         *
         * @param owner 恢复目标类；{@code null} 或契约不符时返回不可用分派
         * @return 解析结果，永不为 {@code null}
         */
        static RecoveryDispatch resolve(Class<?> owner) {
            Method paired = publicVoidMethod(owner, "setCurrentEntityAndItem", int.class, int.class);
            if (paired != null) {
                return new RecoveryDispatch(paired, null, null);
            }
            Method legacyEntity = publicVoidMethod(owner, "setCurrentEntity", int.class);
            Method legacyItem = publicVoidMethod(owner, "setCurrentRenderedItem", int.class);
            if (legacyEntity != null && legacyItem != null) {
                return new RecoveryDispatch(null, legacyEntity, legacyItem);
            }
            return new RecoveryDispatch(null, null, null);
        }

        /** 是否存在可用的恢复入口。 */
        boolean isUsable() {
            return pairedMethod != null || (legacyEntityMethod != null && legacyItemMethod != null);
        }

        /**
         * 恢复捕获前的 entity/item。
         *
         * <p>配对路径一次写入两者；两段式先 entity 后 item——2.1.x 的 {@code setCurrentEntity(int)} 会把
         * item ID 隐式清零，顺序不可颠倒。反射失败属真缺陷，包装为 {@link IllegalStateException}
         * 抛出并保留原因，不静默。</p>
         *
         * @param instance 恢复目标实例
         * @param entityId 捕获前的 entity ID
         * @param itemId 捕获前的 item ID
         */
        void restore(Object instance, int entityId, int itemId) {
            if (!isUsable()) {
                throw new IllegalStateException("Angelica entity/item restore dispatch is not usable");
            }
            try {
                if (pairedMethod != null) {
                    pairedMethod.invoke(instance, Integer.valueOf(entityId), Integer.valueOf(itemId));
                    return;
                }
                legacyEntityMethod.invoke(instance, Integer.valueOf(entityId));
                legacyItemMethod.invoke(instance, Integer.valueOf(itemId));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Angelica entity/item restore method is not accessible", exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                throw new IllegalStateException("Angelica entity/item restore method failed", cause);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Angelica entity/item restore invocation rejected", exception);
            }
        }

        private static Method publicVoidMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
            if (owner == null) {
                return null;
            }
            try {
                Method method = owner.getMethod(name, parameterTypes);
                return method.getReturnType() == void.class ? method : null;
            } catch (NoSuchMethodException exception) {
                return null;
            } catch (SecurityException exception) {
                return null;
            }
        }
    }

    /** 直接绑定 Angelica ABI 的生产访问器；入口解析失败只降级、不抛异常。 */
    private static final class ProductionStateAccess implements StateAccess {

        /**
         * 生产恢复入口。解析失败只标记不可用并 WARN，绝不让异常冒到静态初始化
         * （否则 Mixin 的 clinit 握手会看到 ExceptionInInitializerError）。
         */
        private static final RecoveryDispatch RESTORE_DISPATCH = resolveRestoreDispatchSafely();

        private static final ProductionStateAccess INSTANCE = new ProductionStateAccess();

        private static RecoveryDispatch resolveRestoreDispatchSafely() {
            try {
                return RecoveryDispatch.resolve(CapturedRenderingState.class);
            } catch (Throwable throwable) {
                MyMod.LOG.warn("玩家标签回放解析 Angelica entity/item 恢复入口失败，本帧标签保持即时绘制",
                        throwable);
                return RecoveryDispatch.resolve(null);
            }
        }

        static boolean isRestoreDispatchUsable() {
            return RESTORE_DISPATCH.isUsable();
        }

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
            RESTORE_DISPATCH.restore(CapturedRenderingState.INSTANCE, entityId, itemId);
        }
    }
}
