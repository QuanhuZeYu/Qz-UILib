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
 * 在运行期按 public 方法契约选择恢复档位：2.2.10 起走配对方法 {@code setCurrentEntityAndItem(int, int)}，
 * 2.1.x 走 {@code setCurrentEntity(int)} + {@code setCurrentRenderedItem(int)} 两段式，1.0.0-betaXX
 * （GTNH 2.8.x）只有 entity 面、走 {@link RecoveryTier#ENTITY_ONLY} 只恢复 entity。</p>
 *
 * <p>沿革：2.1.50 的 {@code setCurrentEntity(int)} 会隐式把 item ID 清零，旧实现只能"先 entity、后 item"
 * 两次调用绕开该副作用；2.2.10 把 {@code setCurrentEntity(int)} 收为 private 并新增配对 API，
 * 配对恢复即该语义的正解。1.0.0-betaXX 尚无 item 概念，item 维度整体缺席，恢复只写 entity。</p>
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
                MyMod.LOG.warn("玩家标签回放缺少可用的 Angelica 实体恢复入口（档位 {}），本帧标签保持即时绘制",
                        ProductionStateAccess.recoveryTier());
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
     * 宿主不存在 item 维度时的 item 哨兵。
     *
     * <p>捕获值与该维度一起缺席，恢复入参也用它，语义是「本宿主没有 item 概念」，不是「item 为 -1」。
     * Angelica 1.0.0-betaXX 的 {@code CapturedRenderingState} 只有 entity 面。</p>
     */
    static final int NO_ITEM = -1;

    /**
     * 回放围栏可用的恢复档位。
     *
     * <p>档位<b>只由 public 方法契约</b>解析（{@code Class#getMethod}），版本号不参与判定：宿主换版本后
     * 只要契约仍在，围栏就继续可用；未复核版本只影响日志文案。四档语义：</p>
     * <ul>
     *   <li>{@link #PAIRED}：配对入口 {@code setCurrentEntityAndItem(int,int)}——一次写入 entity 与 item；</li>
     *   <li>{@link #TWO_STEP}：{@code setCurrentEntity(int)} + {@code setCurrentRenderedItem(int)}——先 entity
     *       后 item，顺序不可颠倒（单参 entity 入口会把 item 清零）；</li>
     *   <li>{@link #ENTITY_ONLY}：只有 {@code setCurrentEntity(int)}，宿主没有 item 维度——只恢复 entity，
     *       绝不读写 item；</li>
     *   <li>{@link #UNAVAILABLE}：连 entity 读写面都不完整——围栏停用并 fail-open。</li>
     * </ul>
     */
    enum RecoveryTier {
        PAIRED,
        TWO_STEP,
        ENTITY_ONLY,
        UNAVAILABLE
    }

    /**
     * entity/item 捕获与恢复入口的运行期分派（纯反射，不依赖上游类型，可直接单测）。
     *
     * <p>只认 public 方法契约（{@code Class#getMethod}），不做 {@code setAccessible}：各档 Angelica 的
     * {@code CapturedRenderingState} public 方法集互不相同，且该类直接 {@code extends Object}，
     * 探针不会被父类继承干扰。读取面同样走句柄（{@code getCurrentRenderedEntity()} /
     * {@code getCurrentRenderedItem()}），因此本类字节码不再静态链接 item 读取方法——上游移除该成员时
     * 只会在解析期判定为低档位，不会在调用点抛 {@code NoSuchMethodError}。dev jar 复核结论：</p>
     * <ul>
     *   <li>1.0.0-beta57 / 1.0.0-beta66b：只有 {@code setCurrentEntity(int)}，无任何 item 成员 → ENTITY_ONLY；</li>
     *   <li>2.1.50：{@code setCurrentEntityAndItem(int,int)} 不存在（第一步必抛 NoSuchMethodException），
     *       {@code setCurrentEntity(int)} 与 {@code setCurrentRenderedItem(int)} 均为 public → TWO_STEP；</li>
     *   <li>2.2.10：配对方法 public（第一步必命中）→ PAIRED；即使未命中，第二步也取不到转 private 的
     *       {@code setCurrentEntity(int)} → 判定不可用并安全降级。</li>
     * </ul>
     */
    static final class RecoveryDispatch {

        private final RecoveryTier tier;
        private final Method pairedMethod;
        private final Method entityMethod;
        private final Method itemMethod;
        private final Method entityGetter;
        private final Method itemGetter;

        private RecoveryDispatch(RecoveryTier tier, Method pairedMethod, Method entityMethod, Method itemMethod,
                Method entityGetter, Method itemGetter) {
            this.tier = tier;
            this.pairedMethod = pairedMethod;
            this.entityMethod = entityMethod;
            this.itemMethod = itemMethod;
            this.entityGetter = entityGetter;
            this.itemGetter = itemGetter;
        }

        /** 当前档位。 */
        RecoveryTier tier() {
            return tier;
        }

        /**
         * 按 public 方法契约解析恢复档位：优先配对入口，其次两段式，再次 entity-only，都不具备则不可用。
         *
         * <p>可用性要求「能读当前 entity」与「能写回 entity」同时成立：只有写入口、无法捕获的宿主无法正确
         * 还原围栏前状态，按不可用处理。</p>
         *
         * @param owner 恢复目标类；{@code null} 或契约不符时返回不可用分派
         * @return 解析结果，永不为 {@code null}
         */
        static RecoveryDispatch resolve(Class<?> owner) {
            Method entityGetter = publicIntMethod(owner, "getCurrentRenderedEntity");
            if (entityGetter == null) {
                return new RecoveryDispatch(RecoveryTier.UNAVAILABLE, null, null, null, null, null);
            }
            Method itemGetter = publicIntMethod(owner, "getCurrentRenderedItem");
            Method paired = publicVoidMethod(owner, "setCurrentEntityAndItem", int.class, int.class);
            if (paired != null) {
                return new RecoveryDispatch(RecoveryTier.PAIRED, paired, null, null, entityGetter, itemGetter);
            }
            Method entity = publicVoidMethod(owner, "setCurrentEntity", int.class);
            if (entity == null) {
                return new RecoveryDispatch(RecoveryTier.UNAVAILABLE, null, null, null, null, null);
            }
            Method item = publicVoidMethod(owner, "setCurrentRenderedItem", int.class);
            if (item != null) {
                return new RecoveryDispatch(RecoveryTier.TWO_STEP, null, entity, item, entityGetter, itemGetter);
            }
            return new RecoveryDispatch(RecoveryTier.ENTITY_ONLY, null, entity, null, entityGetter, itemGetter);
        }

        /** 围栏是否具备可用的捕获 + 恢复面。 */
        boolean isUsable() {
            return tier != RecoveryTier.UNAVAILABLE;
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
                entityMethod.invoke(instance, Integer.valueOf(entityId));
                if (itemMethod != null) {
                    itemMethod.invoke(instance, Integer.valueOf(itemId));
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Angelica entity/item restore method is not accessible", exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                throw new IllegalStateException("Angelica entity/item restore method failed", cause);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Angelica entity/item restore invocation rejected", exception);
            }
        }

        /**
         * 读取宿主当前 entity ID。
         *
         * <p>反射失败属真缺陷，包装为 {@link IllegalStateException} 抛出并保留原因，不静默。</p>
         *
         * @param instance 宿主状态实例
         * @return 当前 entity ID
         */
        int readEntity(Object instance) {
            return invokeInt(entityGetter, instance, "current entity");
        }

        /**
         * 读取宿主当前 item ID；宿主无 item 维度时返回 {@link #NO_ITEM}。
         *
         * @param instance 宿主状态实例
         * @return 当前 item ID 或 {@link #NO_ITEM} 哨兵
         */
        int readItem(Object instance) {
            return itemGetter == null ? NO_ITEM : invokeInt(itemGetter, instance, "current item");
        }

        private static int invokeInt(Method method, Object instance, String what) {
            try {
                Object value = method.invoke(instance);
                if (!(value instanceof Number)) {
                    throw new IllegalStateException("Angelica " + what + " getter returned non-numeric value");
                }
                return ((Number) value).intValue();
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Angelica " + what + " getter is not accessible", exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                throw new IllegalStateException("Angelica " + what + " getter failed", cause);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("Angelica " + what + " getter invocation rejected", exception);
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

        private static Method publicIntMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
            if (owner == null) {
                return null;
            }
            try {
                Method method = owner.getMethod(name, parameterTypes);
                return method.getReturnType() == int.class ? method : null;
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
                RecoveryDispatch dispatch = RecoveryDispatch.resolve(CapturedRenderingState.class);
                // 档位是宿主能力档案里唯一影响回放行为的一项，启动期留一条可检索记录。
                MyMod.LOG.info("玩家标签回放 Angelica 恢复档位：{}", dispatch.tier());
                return dispatch;
            } catch (Throwable throwable) {
                MyMod.LOG.warn("玩家标签回放解析 Angelica 实体恢复入口失败，本帧标签保持即时绘制",
                        throwable);
                return RecoveryDispatch.resolve(null);
            }
        }

        static boolean isRestoreDispatchUsable() {
            return RESTORE_DISPATCH.isUsable();
        }

        static RecoveryTier recoveryTier() {
            return RESTORE_DISPATCH.tier();
        }

        @Override
        public boolean isPhaseNone() {
            return GbufferPrograms.getCurrentPhase() == WorldRenderingPhase.NONE;
        }

        @Override
        public int getCurrentEntity() {
            return RESTORE_DISPATCH.readEntity(CapturedRenderingState.INSTANCE);
        }

        @Override
        public int getCurrentItem() {
            return RESTORE_DISPATCH.readItem(CapturedRenderingState.INSTANCE);
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
