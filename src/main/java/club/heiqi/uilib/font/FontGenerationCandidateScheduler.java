package club.heiqi.uilib.font;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 独立于 glyph worker 的 generation candidate 单任务调度边界。 */
interface FontGenerationCandidateScheduler {

    PendingCandidate submit(Callable<FontGenerationCandidate> preparation);

    boolean isQuiescent();

    void shutdown();

    interface PendingCandidate {

        CandidateResult poll();

        void cancel();
    }
}

/** 生产 singleton 使用的单线程 candidate scheduler。 */
final class AsyncFontGenerationCandidateScheduler implements FontGenerationCandidateScheduler {

    private final AtomicInteger threadSequence = new AtomicInteger();
    private ThreadPoolExecutor executor;
    private ExecutorService retiringExecutor;

    @Override
    public synchronized PendingCandidate submit(Callable<FontGenerationCandidate> preparation) {
        if (preparation == null) {
            throw new IllegalArgumentException("candidate preparation 不得为 null");
        }
        reapRetiringExecutor();
        if (retiringExecutor != null) {
            throw new IllegalStateException("上一 generation candidate executor 尚未终止");
        }
        if (executor == null) {
            ThreadFactory threadFactory = new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable,
                            "QzFontGenerationBuilder-" + threadSequence.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            };
            executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<Runnable>(1), threadFactory, new ThreadPoolExecutor.AbortPolicy());
        }
        return new FuturePendingCandidate(executor, executor.submit(preparation));
    }

    @Override
    public synchronized boolean isQuiescent() {
        reapRetiringExecutor();
        return executor == null && retiringExecutor == null;
    }

    @Override
    public synchronized void shutdown() {
        reapRetiringExecutor();
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        retiringExecutor = executor;
        executor = null;
        reapRetiringExecutor();
    }

    private void reapRetiringExecutor() {
        if (retiringExecutor != null && retiringExecutor.isTerminated()) {
            retiringExecutor = null;
        }
    }

    private static final class FuturePendingCandidate implements PendingCandidate {

        private final Future<FontGenerationCandidate> future;
        private final ThreadPoolExecutor executor;

        private FuturePendingCandidate(ThreadPoolExecutor executor, Future<FontGenerationCandidate> future) {
            this.executor = executor;
            this.future = future;
        }

        @Override
        public CandidateResult poll() {
            if (!future.isDone()) {
                return null;
            }
            try {
                return CandidateResult.success(future.get());
            } catch (CancellationException exception) {
                return CandidateResult.failure(exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                return CandidateResult.failure(cause == null ? exception : cause);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return CandidateResult.failure(exception);
            }
        }

        @Override
        public void cancel() {
            future.cancel(true);
            if (future instanceof Runnable) {
                executor.remove((Runnable) future);
            }
            executor.purge();
        }
    }
}

/** package-private 测试构造器使用的确定性 inline scheduler。 */
enum DirectFontGenerationCandidateScheduler implements FontGenerationCandidateScheduler {
    INSTANCE;

    @Override
    public PendingCandidate submit(Callable<FontGenerationCandidate> preparation) {
        final CandidateResult result;
        try {
            result = CandidateResult.success(preparation.call());
        } catch (Throwable throwable) {
            return new CompletedPendingCandidate(CandidateResult.failure(throwable));
        }
        return new CompletedPendingCandidate(result);
    }

    @Override
    public boolean isQuiescent() {
        return true;
    }

    @Override
    public void shutdown() {}

    private static final class CompletedPendingCandidate implements PendingCandidate {

        private final CandidateResult result;

        private CompletedPendingCandidate(CandidateResult result) {
            this.result = result;
        }

        @Override
        public CandidateResult poll() {
            return result;
        }

        @Override
        public void cancel() {}
    }
}

/** Candidate 线程向 render owner 发布的不可变完成态。 */
final class CandidateResult {

    private final FontGenerationCandidate candidate;
    private final Throwable failure;

    private CandidateResult(FontGenerationCandidate candidate, Throwable failure) {
        this.candidate = candidate;
        this.failure = failure;
    }

    static CandidateResult success(FontGenerationCandidate candidate) {
        if (candidate == null) {
            return failure(new IllegalStateException("generation candidate builder 返回 null"));
        }
        return new CandidateResult(candidate, null);
    }

    static CandidateResult failure(Throwable failure) {
        return new CandidateResult(null, failure == null
                ? new IllegalStateException("generation candidate builder 未提供失败原因") : failure);
    }

    FontGenerationCandidate getCandidate() {
        return candidate;
    }

    Throwable getFailure() {
        return failure;
    }
}
