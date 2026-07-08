package club.heiqi.uilib.internal.devtools;

import java.util.concurrent.CompletableFuture;

import club.heiqi.uilib.net.api.NetStoreView;

/**
 * 网络层运行时自检的兼容门面。
 *
 * <p>端点注册、用例执行和远程页面构造分别委派给包内协作者，
 * 现有命令页与 CommonProxy 仍通过本类访问。</p>
 */
public final class NetRuntimeSelfChecks {

    private NetRuntimeSelfChecks() {}

    /**
     * 注册运行时自检端点。
     */
    public static void register() {
        NetSelfCheckRegistry.register();
    }

    /**
     * 运行 Channel C2S/S2C 往返自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runChannelRoundTrip() {
        return NetSelfCheckRunner.runChannelRoundTrip();
    }

    /**
     * 返回运行时 Store 视图，供客户端 DOM bridge 自检使用。
     *
     * @return Store 视图
     */
    public static NetStoreView getRuntimeStoreView() {
        return NetSelfCheckRegistry.getRuntimeStoreView();
    }

    /**
     * 运行超过 32KB 的 Channel 分片往返自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runChunkedChannelRoundTrip() {
        return NetSelfCheckRunner.runChunkedChannelRoundTrip();
    }

    /**
     * 运行 Fetch 请求响应自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runFetchRoundTrip() {
        return NetSelfCheckRunner.runFetchRoundTrip();
    }

    /**
     * 运行 Fetch 远端错误响应自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runFetchErrorRoundTrip() {
        return NetSelfCheckRunner.runFetchErrorRoundTrip();
    }

    /**
     * 运行 Fetch 超时自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runFetchTimeout() {
        return NetSelfCheckRunner.runFetchTimeout();
    }

    /**
     * 运行 Fetch 本地取消自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runFetchCancellation() {
        return NetSelfCheckRunner.runFetchCancellation();
    }

    /**
     * 运行 Fetch 限流自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runFetchRateLimit() {
        return NetSelfCheckRunner.runFetchRateLimit();
    }

    /**
     * 运行 Stream 大内容下载自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runStreamDownload() {
        return NetSelfCheckRunner.runStreamDownload();
    }

    /**
     * 运行 Store snapshot 自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runStoreSnapshot() {
        return NetSelfCheckRunner.runStoreSnapshot();
    }

    /**
     * 运行 Store delta 自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runStoreDelta() {
        return NetSelfCheckRunner.runStoreDelta();
    }

    /**
     * 运行 per-player Store snapshot 自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runPlayerStoreSnapshot() {
        return NetSelfCheckRunner.runPlayerStoreSnapshot();
    }

}
