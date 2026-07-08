package club.heiqi.uilib.internal.devtools;

import static club.heiqi.uilib.internal.devtools.NetSelfCheckRegistry.*;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import club.heiqi.uilib.net.api.NetBody;
import club.heiqi.uilib.net.api.NetMessage;
import club.heiqi.uilib.net.api.NetRequest;
import club.heiqi.uilib.net.api.NetResponse;
import club.heiqi.uilib.net.api.NetStreamCall;
import club.heiqi.uilib.net.api.NetStreamProgress;
import club.heiqi.uilib.net.api.NetStreamProgressListener;
import club.heiqi.uilib.net.api.NetTimeoutException;

/**
 * 网络层运行时自检执行器。
 */
final class NetSelfCheckRunner {

    private NetSelfCheckRunner() {}

    /**
     * 运行 Channel C2S/S2C 往返自检。
     *
     * @return 自检 future
     */
    static CompletableFuture<String> runChannelRoundTrip() {
        ensureRegistered();
        final String checkId = nextCheckId("channel");
        final CompletableFuture<NetMessage> pending = new CompletableFuture<NetMessage>();
        CHANNEL_PENDING.put(checkId, pending);
        pending.whenComplete(new java.util.function.BiConsumer<NetMessage, Throwable>() {
            @Override
            public void accept(NetMessage message, Throwable throwable) {
                CHANNEL_PENDING.remove(checkId);
            }
        });
        try {
            channel.toServer().send(NetMessage.json(jsonFor(checkId, "channel"))
                    .withHeader(CHECK_ID_HEADER, checkId)
                    .withHeader(CHECK_KIND_HEADER, "channel"));
        } catch (RuntimeException exception) {
            CHANNEL_PENDING.remove(checkId);
            pending.completeExceptionally(exception);
        }
        return withTimeout(pending, "Channel ping", checkId)
                .thenApply(new java.util.function.Function<NetMessage, String>() {
                    @Override
                    public String apply(NetMessage message) {
                        requireEquals(checkId, message.getHeader(CHECK_ID_HEADER), "Channel check id");
                        requireEquals("channel", message.getHeader(CHECK_KIND_HEADER), "Channel kind");
                        requireContains(message.getBody().asUtf8String(), "\"kind\":\"channel\"", "Channel body");
                        return "Channel ping 已完成，id=" + checkId;
                    }
                });
    }

    /**
     * 运行超过 32KB 的 Channel 分片往返自检。
     *
     * @return 自检 future
     */
    static CompletableFuture<String> runChunkedChannelRoundTrip() {
        ensureRegistered();
        final String checkId = nextCheckId("chunkedChannel");
        final byte[] payload = chunkedPayload();
        final CompletableFuture<NetMessage> pending = new CompletableFuture<NetMessage>();
        CHANNEL_PENDING.put(checkId, pending);
        pending.whenComplete(new java.util.function.BiConsumer<NetMessage, Throwable>() {
            @Override
            public void accept(NetMessage message, Throwable throwable) {
                CHANNEL_PENDING.remove(checkId);
            }
        });
        try {
            channel.toServer().send(NetMessage.binary(payload)
                    .withHeader(CHECK_ID_HEADER, checkId)
                    .withHeader(CHECK_KIND_HEADER, "chunkedChannel"));
        } catch (RuntimeException exception) {
            CHANNEL_PENDING.remove(checkId);
            pending.completeExceptionally(exception);
        }
        return withTimeout(pending, "Chunked Channel", checkId)
                .thenApply(new java.util.function.Function<NetMessage, String>() {
                    @Override
                    public String apply(NetMessage message) {
                        requireEquals(checkId, message.getHeader(CHECK_ID_HEADER), "Chunked Channel check id");
                        requireEquals("chunkedChannel", message.getHeader(CHECK_KIND_HEADER),
                                "Chunked Channel kind");
                        require(message.getContentType().isBinary(), "Chunked Channel contentType 应为二进制");
                        require(Arrays.equals(payload, message.getBody().getBytes()), "Chunked Channel body 不一致");
                        return "Chunked Channel 已完成，bytes=" + payload.length + "，id=" + checkId;
                    }
                });
    }

    /**
     * 运行 Fetch 请求响应自检。
     *
     * @return 自检 future
     */
    static CompletableFuture<String> runFetchRoundTrip() {
        ensureRegistered();
        final String checkId = nextCheckId("fetch");
        CompletableFuture<NetResponse> response = fetchEndpoint.call(NetRequest.json(jsonFor(checkId, "fetch"))
                .withHeader(CHECK_ID_HEADER, checkId)
                .withHeader(CHECK_KIND_HEADER, "fetch"));
        return withTimeout(response, "Fetch echo", checkId)
                .thenApply(new java.util.function.Function<NetResponse, String>() {
                    @Override
                    public String apply(NetResponse value) {
                        require(value.isOk(), "Fetch status 应为 2xx");
                        requireEquals(checkId, value.getHeader(CHECK_ID_HEADER), "Fetch check id");
                        requireEquals("fetch", value.getHeader(CHECK_KIND_HEADER), "Fetch kind");
                        requireContains(value.getBody().asUtf8String(), "\"kind\":\"fetch\"", "Fetch body");
                        return "Fetch echo 已完成，id=" + checkId;
                    }
                });
    }

    /**
     * 运行 Fetch 远端错误响应自检。
     *
     * @return 自检 future
     */
    static CompletableFuture<String> runFetchErrorRoundTrip() {
        ensureRegistered();
        final String checkId = nextCheckId("fetchError");
        CompletableFuture<NetResponse> response = fetchErrorEndpoint.call(NetRequest.json(jsonFor(checkId, "fetchError"))
                .withHeader(CHECK_ID_HEADER, checkId)
                .withHeader(CHECK_KIND_HEADER, "fetchError"));
        return withTimeout(response, "Fetch error", checkId)
                .thenApply(new java.util.function.Function<NetResponse, String>() {
                    @Override
                    public String apply(NetResponse value) {
                        require(!value.isOk(), "Fetch error status 不应为 2xx");
                        require(value.getStatusCode() == 500, "Fetch error status 应为 500");
                        requireContains(value.getBody().asUtf8String(), "IllegalStateException",
                                "Fetch error body");
                        requireContains(value.getBody().asUtf8String(), checkId, "Fetch error body");
                        return "Fetch error 已完成，status=" + value.getStatusCode() + "，id=" + checkId;
                    }
                });
    }

    /**
     * 运行 Fetch 超时自检。
     *
     * @return 自检 future
     */
    static CompletableFuture<String> runFetchTimeout() {
        ensureRegistered();
        final String checkId = nextCheckId("fetchTimeout");
        CompletableFuture<NetResponse> response = fetchTimeoutEndpoint.call(NetRequest.json(jsonFor(checkId,
                "fetchTimeout"))
                .withHeader(CHECK_ID_HEADER, checkId)
                .withHeader(CHECK_KIND_HEADER, "fetchTimeout"));
        return withTimeout(response, "Fetch timeout", checkId)
                .handle(new java.util.function.BiFunction<NetResponse, Throwable, String>() {
                    @Override
                    public String apply(NetResponse value, Throwable throwable) {
                        Throwable cause = unwrap(throwable);
                        if (cause == null) {
                            throw new IllegalStateException("Fetch timeout 应失败但收到响应: "
                                    + value.getStatusCode());
                        }
                        if (!(cause instanceof NetTimeoutException)) {
                            throw new CompletionException(cause);
                        }
                        requireContains(cause.getMessage(), "Fetch 请求超时", "Fetch timeout");
                        return "Fetch timeout 已按预期触发，id=" + checkId;
                    }
                });
    }

    /**
     * 运行 Fetch 本地取消自检。
     *
     * @return 自检 future
     */
    static CompletableFuture<String> runFetchCancellation() {
        ensureRegistered();
        final String checkId = nextCheckId("fetchCancel");
        final CompletableFuture<NetResponse> response = fetchCancelEndpoint.call(NetRequest.json(jsonFor(checkId,
                "fetchCancel"))
                .withHeader(CHECK_ID_HEADER, checkId)
                .withHeader(CHECK_KIND_HEADER, "fetchCancel"));
        final CompletableFuture<String> result = new CompletableFuture<String>();
        boolean cancelled = response.cancel(false);
        if (!cancelled) {
            result.completeExceptionally(new IllegalStateException("Fetch cancel 返回 false: " + checkId));
            return result;
        }
        TIMEOUT_EXECUTOR.schedule(new Runnable() {
            @Override
            public void run() {
                if (!response.isCancelled()) {
                    result.completeExceptionally(new IllegalStateException("Fetch future 未保持取消状态: "
                            + checkId));
                    return;
                }
                result.complete("Fetch cancel 已保持本地取消，迟到响应被忽略，id=" + checkId);
            }
        }, FETCH_CANCEL_VERIFY_MILLIS, TimeUnit.MILLISECONDS);
        return withTimeout(result, "Fetch cancel", checkId);
    }

    /**
     * 运行 Fetch 限流自检。
     *
     * @return 自检 future
     */
    static CompletableFuture<String> runFetchRateLimit() {
        ensureRegistered();
        final String checkId = nextCheckId("fetchRateLimit");
        CompletableFuture<NetResponse> first = fetchRateLimitEndpoint.call(NetRequest.json(jsonFor(checkId,
                "fetchRateLimitFirst"))
                .withHeader(CHECK_ID_HEADER, checkId)
                .withHeader(CHECK_KIND_HEADER, "fetchRateLimit"));
        CompletableFuture<NetResponse> second = fetchRateLimitEndpoint.call(NetRequest.json(jsonFor(checkId,
                "fetchRateLimitSecond"))
                .withHeader(CHECK_ID_HEADER, checkId)
                .withHeader(CHECK_KIND_HEADER, "fetchRateLimit"));
        return withTimeout(first, "Fetch rate limit first", checkId)
                .thenCombine(withTimeout(second, "Fetch rate limit second", checkId),
                        new java.util.function.BiFunction<NetResponse, NetResponse, String>() {
                            @Override
                            public String apply(NetResponse firstResponse, NetResponse secondResponse) {
                                require(firstResponse.isOk(), "Fetch rate limit 首包应通过");
                                require(secondResponse.getStatusCode() == 429, "Fetch rate limit 第二包应为 429");
                                require(secondResponse.getHeader("retry-after-ms") != null,
                                        "Fetch rate limit 缺少 retry-after-ms");
                                return "Fetch rate limit 已返回 429，id=" + checkId;
                            }
                        });
    }

    /**
     * 运行 Stream 大内容下载自检。
     *
     * @return 自检 future
     */
    static CompletableFuture<String> runStreamDownload() {
        ensureRegistered();
        final String checkId = nextCheckId("stream");
        final boolean[] sawProgress = new boolean[1];
        final long[] progressBytes = new long[2];
        NetStreamCall call = streamEndpoint.call(NetRequest.json(jsonFor(checkId, "stream"))
                .withHeader(CHECK_ID_HEADER, checkId)
                .withHeader(CHECK_KIND_HEADER, "stream"));
        call.onProgress(new NetStreamProgressListener() {
            @Override
            public void onProgress(NetStreamProgress progress) {
                sawProgress[0] = true;
                progressBytes[0] = progress.getReceivedBytes();
                progressBytes[1] = progress.getTotalBytes();
            }
        });
        return withTimeout(call.future(), "Stream download", checkId, 30_000L)
                .thenApply(new java.util.function.Function<NetResponse, String>() {
                    @Override
                    public String apply(NetResponse response) {
                        require(response.isOk(), "Stream status 应为 2xx");
                        requireEquals(checkId, response.getHeader(CHECK_ID_HEADER), "Stream check id");
                        requireEquals("stream", response.getHeader(CHECK_KIND_HEADER), "Stream kind");
                        require(response.getBody().size() == STREAM_DOWNLOAD_BYTES, "Stream body 长度不一致");
                        require(Arrays.equals(streamPayload(), response.getBody().getBytes()),
                                "Stream body 内容不一致");
                        require(sawProgress[0], "Stream progress 未回调");
                        require(progressBytes[0] == STREAM_DOWNLOAD_BYTES, "Stream progress received 不一致");
                        require(progressBytes[1] == STREAM_DOWNLOAD_BYTES, "Stream progress total 不一致");
                        return "Stream download 已完成，bytes=" + STREAM_DOWNLOAD_BYTES + "，id=" + checkId;
                    }
                });
    }

    /**
     * 运行 Store snapshot 自检。
     *
     * @return 自检 future
     */
    static CompletableFuture<String> runStoreSnapshot() {
        ensureRegistered();
        final String checkId = nextCheckId("store");
        final CompletableFuture<NetBody> pending = new CompletableFuture<NetBody>();
        STORE_PENDING.put(checkId, pending);
        pending.whenComplete(new java.util.function.BiConsumer<NetBody, Throwable>() {
            @Override
            public void accept(NetBody body, Throwable throwable) {
                STORE_PENDING.remove(checkId);
            }
        });
        CompletableFuture<NetResponse> trigger = storeTriggerEndpoint.call(NetRequest.json(jsonFor(checkId, "store"))
                .withHeader(CHECK_ID_HEADER, checkId)
                .withHeader(CHECK_KIND_HEADER, "store"));
        trigger.whenComplete(new java.util.function.BiConsumer<NetResponse, Throwable>() {
            @Override
            public void accept(NetResponse response, Throwable throwable) {
                if (throwable != null) {
                    pending.completeExceptionally(throwable);
                }
            }
        });
        return withTimeout(pending, "Store snapshot", checkId)
                .thenApply(new java.util.function.Function<NetBody, String>() {
                    @Override
                    public String apply(NetBody body) {
                        requireContains(body.asUtf8String(), "\"kind\":\"store\"", "Store body");
                        return "Store snapshot 已完成，id=" + checkId;
                    }
                });
    }

    /**
     * 运行 Store delta 自检。
     *
     * @return 自检 future
     */
    static CompletableFuture<String> runStoreDelta() {
        ensureRegistered();
        final String checkId = nextCheckId("storeDelta");
        final CompletableFuture<NetBody> pending = new CompletableFuture<NetBody>();
        STORE_PENDING.put(checkId, pending);
        pending.whenComplete(new java.util.function.BiConsumer<NetBody, Throwable>() {
            @Override
            public void accept(NetBody body, Throwable throwable) {
                STORE_PENDING.remove(checkId);
            }
        });
        CompletableFuture<NetResponse> trigger = storeDeltaTriggerEndpoint.call(NetRequest.json(jsonFor(checkId,
                "storeDelta"))
                .withHeader(CHECK_ID_HEADER, checkId)
                .withHeader(CHECK_KIND_HEADER, "storeDelta"));
        trigger.whenComplete(new java.util.function.BiConsumer<NetResponse, Throwable>() {
            @Override
            public void accept(NetResponse response, Throwable throwable) {
                if (throwable != null) {
                    pending.completeExceptionally(throwable);
                    return;
                }
                if (!response.isOk()) {
                    pending.completeExceptionally(new IllegalStateException(response.getBody().asUtf8String()));
                }
            }
        });
        return withTimeout(pending, "Store delta", checkId)
                .thenApply(new java.util.function.Function<NetBody, String>() {
                    @Override
                    public String apply(NetBody body) {
                        requireContains(body.asUtf8String(), "\"kind\":\"storeDelta\"", "Store delta body");
                        return "Store delta 已完成，id=" + checkId;
                    }
                });
    }

    /**
     * 运行 per-player Store snapshot 自检。
     *
     * @return 自检 future
     */
    static CompletableFuture<String> runPlayerStoreSnapshot() {
        ensureRegistered();
        final String checkId = nextCheckId("playerStore");
        final CompletableFuture<NetBody> pending = new CompletableFuture<NetBody>();
        STORE_PENDING.put(checkId, pending);
        pending.whenComplete(new java.util.function.BiConsumer<NetBody, Throwable>() {
            @Override
            public void accept(NetBody body, Throwable throwable) {
                STORE_PENDING.remove(checkId);
            }
        });
        CompletableFuture<NetResponse> trigger = playerStoreTriggerEndpoint.call(NetRequest.json(jsonFor(checkId,
                "playerStore"))
                .withHeader(CHECK_ID_HEADER, checkId)
                .withHeader(CHECK_KIND_HEADER, "playerStore"));
        trigger.whenComplete(new java.util.function.BiConsumer<NetResponse, Throwable>() {
            @Override
            public void accept(NetResponse response, Throwable throwable) {
                if (throwable != null) {
                    pending.completeExceptionally(throwable);
                    return;
                }
                if (!response.isOk()) {
                    pending.completeExceptionally(new IllegalStateException(response.getBody().asUtf8String()));
                }
            }
        });
        return withTimeout(pending, "Player Store snapshot", checkId)
                .thenApply(new java.util.function.Function<NetBody, String>() {
                    @Override
                    public String apply(NetBody body) {
                        requireContains(body.asUtf8String(), "\"kind\":\"playerStore\"", "Player Store body");
                        return "Player Store snapshot 已完成，id=" + checkId;
                    }
                });
    }

}
