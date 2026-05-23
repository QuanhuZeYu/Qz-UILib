package club.heiqi.uilib.internal.devtools;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.net.api.NetBody;
import club.heiqi.uilib.net.api.NetChannel;
import club.heiqi.uilib.net.api.NetChannelId;
import club.heiqi.uilib.net.api.NetEndpointId;
import club.heiqi.uilib.net.api.NetFetchEndpoint;
import club.heiqi.uilib.net.api.NetFetchRateLimit;
import club.heiqi.uilib.net.api.NetMessage;
import club.heiqi.uilib.net.api.NetReceiveContext;
import club.heiqi.uilib.net.api.NetRequest;
import club.heiqi.uilib.net.api.NetResponse;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.api.NetStreamCall;
import club.heiqi.uilib.net.api.NetStreamEndpoint;
import club.heiqi.uilib.net.api.NetStreamProgress;
import club.heiqi.uilib.net.api.NetStreamProgressListener;
import club.heiqi.uilib.net.api.NetStore;
import club.heiqi.uilib.net.api.NetStoreId;
import club.heiqi.uilib.net.api.NetStoreScope;
import club.heiqi.uilib.net.api.NetStoreView;
import club.heiqi.uilib.net.api.NetTimeoutException;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * 网络层运行时自检端点。
 */
public final class NetRuntimeSelfChecks {

    private static final String NAMESPACE = "qz";
    private static final String CHECK_ID_HEADER = "x-qz-check-id";
    private static final String CHECK_KIND_HEADER = "x-qz-check-kind";
    private static final NetChannelId CHANNEL_ID = NetChannelId.of(NAMESPACE, "runtimeChannelCheck");
    private static final NetEndpointId FETCH_ID = NetEndpointId.of(NAMESPACE, "runtimeFetchCheck");
    private static final NetEndpointId FETCH_ERROR_ID = NetEndpointId.of(NAMESPACE, "runtimeFetchErrorCheck");
    private static final NetEndpointId FETCH_TIMEOUT_ID = NetEndpointId.of(NAMESPACE, "runtimeFetchTimeoutCheck");
    private static final NetEndpointId FETCH_CANCEL_ID = NetEndpointId.of(NAMESPACE, "runtimeFetchCancelCheck");
    private static final NetEndpointId FETCH_RATE_LIMIT_ID = NetEndpointId.of(NAMESPACE, "runtimeFetchLimitCheck");
    private static final NetEndpointId STREAM_ID = NetEndpointId.of(NAMESPACE, "runtimeStreamCheck");
    private static final NetEndpointId STORE_TRIGGER_ID = NetEndpointId.of(NAMESPACE, "runtimeStoreTrigger");
    private static final NetEndpointId STORE_DELTA_TRIGGER_ID = NetEndpointId.of(NAMESPACE,
            "runtimeStoreDeltaTrigger");
    private static final NetEndpointId PLAYER_STORE_TRIGGER_ID = NetEndpointId.of(NAMESPACE,
            "runtimePlayerStoreTrigger");
    private static final NetStoreId STORE_ID = NetStoreId.of(NAMESPACE, "runtimeStoreCheck");
    private static final NetStoreId STORE_DELTA_ID = NetStoreId.of(NAMESPACE, "runtimeStoreDeltaCheck");
    private static final NetStoreId PLAYER_STORE_ID = NetStoreId.of(NAMESPACE, "runtimePlayerStoreCheck");
    private static final int CHUNKED_CHANNEL_BYTES = 100 * 1024;
    private static final int STREAM_DOWNLOAD_BYTES = NetPayloadLimits.DEFAULT_LOGICAL_MESSAGE_LIMIT + 32 * 1024;
    private static final long FETCH_TIMEOUT_MILLIS = 120L;
    private static final long FETCH_TIMEOUT_WAKEUP_MILLIS = 240L;
    private static final long FETCH_CANCEL_REPLY_MILLIS = 240L;
    private static final long FETCH_CANCEL_VERIFY_MILLIS = 520L;
    private static final long TIMEOUT_MILLIS = 5_000L;

    private static final AtomicLong NEXT_CHECK_ID = new AtomicLong(1L);
    private static final Map<String, CompletableFuture<NetMessage>> CHANNEL_PENDING =
            new ConcurrentHashMap<String, CompletableFuture<NetMessage>>();
    private static final Map<String, CompletableFuture<NetBody>> STORE_PENDING =
            new ConcurrentHashMap<String, CompletableFuture<NetBody>>();
    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "QzNetSelfCheckTimeout");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private static volatile boolean registered;
    private static NetChannel channel;
    private static NetFetchEndpoint fetchEndpoint;
    private static NetFetchEndpoint fetchErrorEndpoint;
    private static NetFetchEndpoint fetchTimeoutEndpoint;
    private static NetFetchEndpoint fetchCancelEndpoint;
    private static NetFetchEndpoint fetchRateLimitEndpoint;
    private static NetStreamEndpoint streamEndpoint;
    private static NetFetchEndpoint storeTriggerEndpoint;
    private static NetFetchEndpoint storeDeltaTriggerEndpoint;
    private static NetStore store;
    private static NetStore storeDelta;
    private static NetFetchEndpoint playerStoreTriggerEndpoint;
    private static NetStore playerStore;

    private NetRuntimeSelfChecks() {}

    /**
     * 注册运行时自检端点。
     */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        NetService service = NetService.getInstance();
        channel = service.channel(CHANNEL_ID)
                .onReceive(new NetChannel.NetChannelHandler() {
                    @Override
                    public void onReceive(NetMessage message, NetReceiveContext context) {
                        handleChannelMessage(message, context);
                    }
                })
                .register();
        fetchEndpoint = service.fetch(FETCH_ID)
                .onRequest(new NetFetchEndpoint.NetFetchHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
                        context.reply(NetResponse.json(jsonFor(request.getHeader(CHECK_ID_HEADER), "fetch"))
                                .withHeader(CHECK_ID_HEADER, request.getHeader(CHECK_ID_HEADER))
                                .withHeader(CHECK_KIND_HEADER, "fetch"));
                    }
                })
                .register();
        fetchErrorEndpoint = service.fetch(FETCH_ERROR_ID)
                .onRequest(new NetFetchEndpoint.NetFetchHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
                        context.fail(new IllegalStateException("runtime fetch failure "
                                + request.getHeader(CHECK_ID_HEADER)));
                    }
                })
                .register();
        fetchTimeoutEndpoint = service.fetch(FETCH_TIMEOUT_ID)
                .timeout(Duration.ofMillis(FETCH_TIMEOUT_MILLIS))
                .onRequest(new NetFetchEndpoint.NetFetchHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
                        scheduleFetchTimeoutWakeup(request.getHeader(CHECK_ID_HEADER),
                                context.getReceiveContext().getSenderPlayer());
                    }
                })
                .register();
        fetchCancelEndpoint = service.fetch(FETCH_CANCEL_ID)
                .onRequest(new NetFetchEndpoint.NetFetchHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
                        scheduleFetchCancelReply(request.getHeader(CHECK_ID_HEADER), context);
                    }
                })
                .register();
        fetchRateLimitEndpoint = service.fetch(FETCH_RATE_LIMIT_ID)
                .rateLimit(NetFetchRateLimit.of(1, Duration.ofMillis(500)))
                .onRequest(new NetFetchEndpoint.NetFetchHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
                        String checkId = request.getHeader(CHECK_ID_HEADER);
                        context.reply(NetResponse.json(jsonFor(checkId, "fetchRateLimit"))
                                .withHeader(CHECK_ID_HEADER, checkId)
                                .withHeader(CHECK_KIND_HEADER, "fetchRateLimit"));
                    }
                })
                .register();
        streamEndpoint = service.stream(STREAM_ID)
                .timeout(Duration.ofSeconds(60))
                .onRequest(new NetStreamEndpoint.NetStreamHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetStreamEndpoint.NetStreamRequestContext context) {
                        String checkId = request.getHeader(CHECK_ID_HEADER);
                        context.reply(NetResponse.ok(NetBody.binary(streamPayload()))
                                .withHeader(CHECK_ID_HEADER, checkId)
                                .withHeader(CHECK_KIND_HEADER, "stream"));
                    }
                })
                .register();
        store = service.store(STORE_ID)
                .scope(NetStoreScope.GLOBAL)
                .initialJson(jsonFor("initial", "store"))
                .register();
        store.view().subscribe(new NetStoreView.NetStoreSubscriber() {
            @Override
            public void onSnapshot(NetBody snapshot) {
                completeStoreSnapshot(snapshot);
            }
        });
        storeDelta = service.store(STORE_DELTA_ID)
                .scope(NetStoreScope.GLOBAL)
                .initialJson(jsonFor("initial", "storeDelta"))
                .deltaApplier(new NetStore.StoreDeltaApplier() {
                    @Override
                    public NetBody apply(NetBody current, NetBody delta) {
                        String checkId = extractId(delta.asUtf8String());
                        return NetBody.json(jsonFor(checkId, "storeDelta"));
                    }
                })
                .register();
        storeDelta.view().subscribe(new NetStoreView.NetStoreSubscriber() {
            @Override
            public void onSnapshot(NetBody snapshot) {
                completeStoreSnapshot(snapshot);
            }
        });
        playerStore = service.store(PLAYER_STORE_ID)
                .scope(NetStoreScope.PER_PLAYER)
                .initialJson(jsonFor("initial", "playerStore"))
                .accessControl(new NetStore.AccessControl() {
                    @Override
                    public boolean canAccess(Object player, NetStore store) {
                        return player != null;
                    }
                })
                .register();
        playerStore.view().subscribe(new NetStoreView.NetStoreSubscriber() {
            @Override
            public void onSnapshot(NetBody snapshot) {
                completeStoreSnapshot(snapshot);
            }
        });
        storeTriggerEndpoint = service.fetch(STORE_TRIGGER_ID)
                .onRequest(new NetFetchEndpoint.NetFetchHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
                        String checkId = request.getHeader(CHECK_ID_HEADER);
                        store.set(NetBody.json(jsonFor(checkId, "store")));
                        context.reply(NetResponse.json(jsonFor(checkId, "storeAck"))
                                .withHeader(CHECK_ID_HEADER, checkId)
                                .withHeader(CHECK_KIND_HEADER, "store"));
                    }
                })
                .register();
        storeDeltaTriggerEndpoint = service.fetch(STORE_DELTA_TRIGGER_ID)
                .onRequest(new NetFetchEndpoint.NetFetchHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
                        String checkId = request.getHeader(CHECK_ID_HEADER);
                        storeDelta.applyDelta(NetBody.json(jsonFor(checkId, "storeDelta")));
                        context.reply(NetResponse.json(jsonFor(checkId, "storeDeltaAck"))
                                .withHeader(CHECK_ID_HEADER, checkId)
                                .withHeader(CHECK_KIND_HEADER, "storeDelta"));
                    }
                })
                .register();
        playerStoreTriggerEndpoint = service.fetch(PLAYER_STORE_TRIGGER_ID)
                .onRequest(new NetFetchEndpoint.NetFetchHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
                        String checkId = request.getHeader(CHECK_ID_HEADER);
                        Object player = context.getReceiveContext().getSenderPlayer();
                        if (player == null) {
                            context.reply(NetResponse.error(400, "缺少发送玩家"));
                            return;
                        }
                        playerStore.setForPlayer(player, NetBody.json(jsonFor(checkId, "playerStore")));
                        context.reply(NetResponse.json(jsonFor(checkId, "playerStoreAck"))
                                .withHeader(CHECK_ID_HEADER, checkId)
                                .withHeader(CHECK_KIND_HEADER, "playerStore"));
                    }
                })
                .register();
        registered = true;
    }

    /**
     * 运行 Channel C2S/S2C 往返自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runChannelRoundTrip() {
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
     * 返回运行时 Store 视图，供客户端 DOM bridge 自检使用。
     *
     * @return Store 视图
     */
    public static NetStoreView getRuntimeStoreView() {
        ensureRegistered();
        return store.view();
    }

    /**
     * 运行超过 32KB 的 Channel 分片往返自检。
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runChunkedChannelRoundTrip() {
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
    public static CompletableFuture<String> runFetchRoundTrip() {
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
    public static CompletableFuture<String> runFetchErrorRoundTrip() {
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
    public static CompletableFuture<String> runFetchTimeout() {
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
    public static CompletableFuture<String> runFetchCancellation() {
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
    public static CompletableFuture<String> runFetchRateLimit() {
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
    public static CompletableFuture<String> runStreamDownload() {
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
    public static CompletableFuture<String> runStoreSnapshot() {
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
    public static CompletableFuture<String> runStoreDelta() {
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
    public static CompletableFuture<String> runPlayerStoreSnapshot() {
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

    private static void handleChannelMessage(NetMessage message, NetReceiveContext context) {
        String checkId = message.getHeader(CHECK_ID_HEADER);
        if (context.getSide() == NetSide.SERVER) {
            Object sender = context.getSenderPlayer();
            if (sender == null) {
                return;
            }
            channel.toPlayer(sender).send(NetMessage.of(message.getBody())
                    .withHeader(CHECK_ID_HEADER, checkId)
                    .withHeader(CHECK_KIND_HEADER, message.getHeader(CHECK_KIND_HEADER)));
            return;
        }
        CompletableFuture<NetMessage> pending = CHANNEL_PENDING.get(checkId);
        if (pending != null) {
            pending.complete(message);
        }
    }

    private static void scheduleFetchTimeoutWakeup(final String checkId, final Object player) {
        if (player == null) {
            return;
        }
        TIMEOUT_EXECUTOR.schedule(new Runnable() {
            @Override
            public void run() {
                NetService.getInstance().runOnMainThread(NetSide.SERVER, new Runnable() {
                    @Override
                    public void run() {
                        channel.toPlayer(player).send(NetMessage.json(jsonFor(checkId, "fetchTimeoutWakeup"))
                                .withHeader(CHECK_ID_HEADER, checkId)
                                .withHeader(CHECK_KIND_HEADER, "fetchTimeoutWakeup"));
                    }
                });
            }
        }, FETCH_TIMEOUT_WAKEUP_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static void scheduleFetchCancelReply(final String checkId,
            final NetFetchEndpoint.NetFetchRequestContext context) {
        TIMEOUT_EXECUTOR.schedule(new Runnable() {
            @Override
            public void run() {
                NetService.getInstance().runOnMainThread(NetSide.SERVER, new Runnable() {
                    @Override
                    public void run() {
                        context.reply(NetResponse.json(jsonFor(checkId, "fetchCancelLateReply"))
                                .withHeader(CHECK_ID_HEADER, checkId)
                                .withHeader(CHECK_KIND_HEADER, "fetchCancel"));
                    }
                });
            }
        }, FETCH_CANCEL_REPLY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static void completeStoreSnapshot(NetBody snapshot) {
        String checkId = extractId(snapshot.asUtf8String());
        CompletableFuture<NetBody> pending = STORE_PENDING.get(checkId);
        if (pending != null) {
            pending.complete(snapshot);
        }
    }

    private static void ensureRegistered() {
        if (!registered) {
            throw new IllegalStateException("网络运行时自检端点尚未注册");
        }
    }

    private static String nextCheckId(String kind) {
        return kind + "-" + NEXT_CHECK_ID.getAndIncrement();
    }

    private static String jsonFor(String checkId, String kind) {
        return "{\"id\":\"" + safeJson(checkId) + "\",\"kind\":\"" + safeJson(kind) + "\",\"ok\":true}";
    }

    private static String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractId(String json) {
        String marker = "\"id\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return "";
        }
        return json.substring(valueStart, valueEnd);
    }

    private static <T> CompletableFuture<T> withTimeout(final CompletableFuture<T> future,
            final String label, final String checkId) {
        return withTimeout(future, label, checkId, TIMEOUT_MILLIS);
    }

    private static <T> CompletableFuture<T> withTimeout(final CompletableFuture<T> future,
            final String label, final String checkId, long timeoutMillis) {
        TIMEOUT_EXECUTOR.schedule(new Runnable() {
            @Override
            public void run() {
                future.completeExceptionally(new NetTimeoutException(label + " 自检超时: " + checkId));
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);
        return future;
    }

    private static byte[] chunkedPayload() {
        byte[] payload = new byte[CHUNKED_CHANNEL_BYTES];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (index & 0xFF);
        }
        return payload;
    }

    private static byte[] streamPayload() {
        byte[] payload = new byte[STREAM_DOWNLOAD_BYTES];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) ((index * 31) & 0xFF);
        }
        return payload;
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(label + " 不一致: " + expected + " vs " + actual);
        }
    }

    private static void requireContains(String text, String expected, String label) {
        if (text == null || !text.contains(expected)) {
            throw new IllegalStateException(label + " 缺少片段: " + expected);
        }
    }
}
