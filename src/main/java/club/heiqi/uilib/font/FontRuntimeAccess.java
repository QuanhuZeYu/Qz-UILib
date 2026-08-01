package club.heiqi.uilib.font;

import java.util.concurrent.Callable;

/**
 * 字体 singleton 内部 mutation scope。
 *
 * <p>owner token 只由 {@link FontService} 持有；公开暴露的内部协作对象只能在对应 scope 内执行写入。</p>
 */
public final class FontRuntimeAccess {

    private static final ThreadLocal<Object> ACTIVE_OWNER = new ThreadLocal<Object>();

    private FontRuntimeAccess() {}

    /**
     * 判断当前线程是否处于指定 owner 的内部 scope。
     *
     * @param ownerToken owner token；null 表示未绑定的独立测试对象
     * @return 是否允许内部操作
     */
    public static boolean isActive(Object ownerToken) {
        return ownerToken == null || ACTIVE_OWNER.get() == ownerToken;
    }

    /**
     * 在内部 scope 执行操作。
     *
     * @param ownerToken owner token
     * @param operation 操作
     */
    public static void run(Object ownerToken, Runnable operation) {
        if (operation == null) {
            throw new IllegalArgumentException("operation 不得为 null");
        }
        if (ownerToken == null) {
            operation.run();
            return;
        }

        Object previousOwner = ACTIVE_OWNER.get();
        ACTIVE_OWNER.set(ownerToken);
        try {
            operation.run();
        } finally {
            restore(previousOwner);
        }
    }

    /**
     * 在内部 scope 执行并返回结果。
     *
     * @param ownerToken owner token
     * @param operation 操作
     * @param <T> 返回类型
     * @return 操作结果
     */
    public static <T> T call(Object ownerToken, Callable<T> operation) {
        if (operation == null) {
            throw new IllegalArgumentException("operation 不得为 null");
        }
        if (ownerToken == null) {
            try {
                return operation.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Error error) {
                throw error;
            } catch (Exception exception) {
                throw new IllegalStateException("字体内部操作失败", exception);
            }
        }

        Object previousOwner = ACTIVE_OWNER.get();
        ACTIVE_OWNER.set(ownerToken);
        try {
            return operation.call();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Error error) {
            throw error;
        } catch (Exception exception) {
            throw new IllegalStateException("字体内部操作失败", exception);
        } finally {
            restore(previousOwner);
        }
    }

    private static void restore(Object previousOwner) {
        if (previousOwner == null) {
            ACTIVE_OWNER.remove();
        } else {
            ACTIVE_OWNER.set(previousOwner);
        }
    }
}
