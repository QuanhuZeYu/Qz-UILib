package club.heiqi.uilib.testing;

import java.util.ArrayDeque;
import java.util.Deque;

/** 可继承的测试应用：子类只组织具名生产场景，公共生命周期遵循规格9.1。
 * 这里只依赖Java8；真实字体/窗口等能力由测试选择组件并交付资源所有权。 */
public abstract class TestApplication {
    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }

    private final Deque<AutoCloseable> resources = new ArrayDeque<>();
    private String[] arguments;
    private int completed;

    protected void setup() throws Exception {}

    protected void teardown() throws Exception {}

    protected abstract void runTests() throws Exception;

    /** 初始化中途失败也清理已交付资源；失败保持原Throwable身份，不被close覆盖。 */
    public final void run(String... args) throws Exception {
        if (arguments != null) throw new IllegalStateException("测试应用正在运行");
        arguments = args.clone();
        completed = 0;
        Throwable failure = null;
        try {
            setup();
            runTests();
            require(completed > 0, "测试应用没有实际执行场景");
        } catch (Throwable error) {
            failure = error;
        }
        try {
            teardown();
        } catch (Throwable error) {
            failure = combine(failure, error);
        }
        failure = release(0, failure);
        arguments = null;
        if (failure != null) {
            System.err.println("FAIL 应用 " + applicationName() + ": " + failure);
            rethrow(failure);
        }
        System.out.println("PASS 应用 " + applicationName() + " 场景=" + completed);
    }

    protected final String[] arguments() {
        ensureRunning();
        return arguments.clone();
    }

    /** 场景资源在场景结束释放；setup注册的应用资源则保留到所有场景完成。 */
    protected final void scenario(String name, CheckedRunnable action) throws Exception {
        ensureRunning();
        int depth = resources.size();
        long begin = System.nanoTime();
        Throwable failure = null;
        try {
            action.run();
        } catch (Throwable error) {
            failure = error;
        }
        failure = release(depth, failure);
        if (failure != null) {
            System.err.println("FAIL 场景 " + applicationName() + "/" + name + ": " + failure);
            rethrow(failure);
        }
        completed++;
        System.out.println(
                "PASS 场景 "
                        + applicationName()
                        + "/"
                        + name
                        + " ms="
                        + (System.nanoTime() - begin) / 1000000d);
    }

    protected final <T extends AutoCloseable> T own(T resource) {
        ensureRunning();
        resources.push(resource);
        return resource;
    }

    protected final void onClose(CheckedRunnable action) {
        own((AutoCloseable) action::run);
    }

    protected static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private String applicationName() {
        String simple = getClass().getSimpleName();
        return simple.isEmpty() ? getClass().getName() : simple;
    }

    private void ensureRunning() {
        if (arguments == null) throw new IllegalStateException("测试能力只在应用运行期间可用");
    }

    private Throwable release(int depth, Throwable failure) {
        while (resources.size() > depth) {
            try {
                resources.pop().close();
            } catch (Throwable error) {
                failure = combine(failure, error);
            }
        }
        return failure;
    }

    private static Throwable combine(Throwable first, Throwable next) {
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    private static void rethrow(Throwable failure) throws Exception {
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof Exception) throw (Exception) failure;
        throw new RuntimeException(failure);
    }
}
