package club.heiqi.uilib.internal.devtools;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
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
import club.heiqi.uilib.ui.remote.RemoteDocumentPage;
import club.heiqi.uilib.ui.remote.RemoteDocumentPages;
import club.heiqi.uilib.ui.remote.RemoteDocumentResourcePolicy;
import club.heiqi.uilib.ui.remote.RemoteDocumentSubmitEvent;
import club.heiqi.uilib.ui.remote.RemoteDocumentSubmitHandler;

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
    private static final NetEndpointId REMOTE_PAGE_TRIGGER_ID = NetEndpointId.of(NAMESPACE,
            "runtimeRemotePageTrigger");
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
    private static NetFetchEndpoint remotePageTriggerEndpoint;

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
        remotePageTriggerEndpoint = service.fetch(REMOTE_PAGE_TRIGGER_ID)
                .onRequest(new NetFetchEndpoint.NetFetchHandler() {
                    @Override
                    public void onRequest(NetRequest request, NetFetchEndpoint.NetFetchRequestContext context) {
                        final String checkId = request.getHeader(CHECK_ID_HEADER);
                        Object player = context.getReceiveContext().getSenderPlayer();
                        if (player == null) {
                            context.reply(NetResponse.error(400, "缺少发送玩家"));
                            return;
                        }
                        try {
                            String sessionId = RemoteDocumentPages.open(player, buildRemotePageSmokePage(checkId),
                                    new RemoteDocumentSubmitHandler() {
                                        @Override
                                        public void onSubmit(RemoteDocumentSubmitEvent event) {
                                            handleRemotePageSmokeSubmit(event, checkId);
                                        }
                                    });
                            context.reply(NetResponse.json(jsonFor(checkId, "remotePageOpen"))
                                    .withHeader(CHECK_ID_HEADER, checkId)
                                    .withHeader(CHECK_KIND_HEADER, "remotePage")
                                    .withHeader("x-qz-session-id", sessionId));
                        } catch (IllegalArgumentException exception) {
                            context.reply(NetResponse.error(400, exception.getMessage()));
                        } catch (IllegalStateException exception) {
                            context.fail(exception);
                        }
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

    /**
     * 运行远程页面下发 smoke 自检。
     *
     * <p>该检查会让服务端通过正式 `RemoteDocumentPages.open(...)` 打开页面。
     * 页面内提交按钮负责完成 HTML Stream 拉取、解析、表单收集和 C2S 提交回调的最终人工确认。</p>
     *
     * @return 自检 future
     */
    public static CompletableFuture<String> runRemoteDocumentPageSmoke() {
        ensureRegistered();
        final String checkId = nextCheckId("remotePage");
        CompletableFuture<NetResponse> response = remotePageTriggerEndpoint.call(NetRequest.json(jsonFor(checkId,
                "remotePageSmoke"))
                .withHeader(CHECK_ID_HEADER, checkId)
                .withHeader(CHECK_KIND_HEADER, "remotePage"));
        return withTimeout(response, "Remote document page", checkId)
                .thenApply(new java.util.function.Function<NetResponse, String>() {
                    @Override
                    public String apply(NetResponse value) {
                        require(value.isOk(), "Remote page status 应为 2xx");
                        requireEquals(checkId, value.getHeader(CHECK_ID_HEADER), "Remote page check id");
                        requireEquals("remotePage", value.getHeader(CHECK_KIND_HEADER), "Remote page kind");
                        requireContains(value.getBody().asUtf8String(), "\"kind\":\"remotePageOpen\"",
                                "Remote page body");
                        return "远程页面打开请求已送达，最终以页面内提交后的回复页为准，id=" + checkId;
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

    /**
     * 创建远程页面运行时 smoke 页面。
     *
     * @param checkId 自检标识
     * @return 远程页面
     */
    private static RemoteDocumentPage buildRemotePageSmokePage(String checkId) {
        return RemoteDocumentPage.builder("qz-runtime-remote-page")
                .title("远程页面运行时自检")
                .resourcePolicy(RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY)
                .metadata("checkId", checkId)
                .html(buildRemotePageSmokeHtml(checkId))
                .build();
    }

    /**
     * 生成远程页面 smoke HTML。
     *
     * @param checkId 自检标识
     * @return HTML 文本
     */
    private static String buildRemotePageSmokeHtml(String checkId) {
        String escapedCheckId = escapeHtml(checkId);
        return "<html><head><title>远程页面运行时自检</title><style>"
                + ".smoke{box-sizing:border-box;width:100%;padding:14px;background-color:#0f172a;color:#e5e7eb;}"
                + ".hint{color:#bfdbfe;margin:6px 0;}"
                + ".field{margin:8px 0 4px 0;color:#cbd5e1;}"
                + "input,textarea,select{width:calc(100% - 8px);margin:4px 0;padding:6px;}"
                + "button{margin-top:10px;padding:8px 12px;}"
                + "</style></head><body><div class=\"smoke\">"
                + "<h1>远程页面运行时自检</h1>"
                + "<p class=\"hint\">这页由服务端通过 RemoteDocumentPages.open 下发，客户端会用 Stream 拉取 HTML。</p>"
                + "<p class=\"hint\">保持默认值，点击提交即可验证表单收集与 C2S 回传。</p>"
                + "<form id=\"remote-smoke-form\" action=\"runtime-remote-submit\">"
                + "<input type=\"hidden\" name=\"checkId\" value=\"" + escapedCheckId + "\">"
                + "<input type=\"hidden\" name=\"disabledField\" value=\"blocked\" disabled>"
                + "<p class=\"field\">只读文本字段</p>"
                + "<input type=\"text\" name=\"textEcho\" value=\"text-ok\" readonly maxlength=\"32\">"
                + "<input type=\"checkbox\" name=\"flag\" value=\"checked-ok\" checked data-label=\"复选框字段\">"
                + "<input type=\"radio\" name=\"mode\" value=\"ignored\" data-label=\"未选模式\">"
                + "<input type=\"radio\" name=\"mode\" value=\"selected-ok\" checked data-label=\"已选模式\">"
                + "<p class=\"field\">只读多行文本</p>"
                + "<textarea name=\"note\" readonly maxlength=\"64\">textarea-ok</textarea>"
                + "<p class=\"field\">选择字段</p>"
                + "<select name=\"phase\"><option value=\"wrong\">错误项</option>"
                + "<option value=\"stream-ok\" selected>Stream 已拉取</option></select>"
                + "<p><a href=\"#submit-area\">跳到提交按钮</a></p>"
                + "<button id=\"submit-area\" type=\"submit\" name=\"submitter\" value=\"提交运行时自检\"></button>"
                + "</form></div></body></html>";
    }

    /**
     * 校验远程页面 smoke 表单提交。
     *
     * @param event 提交事件
     * @param checkId 自检标识
     */
    private static void handleRemotePageSmokeSubmit(RemoteDocumentSubmitEvent event, String checkId) {
        StringBuilder problems = new StringBuilder();
        requireSubmitted(problems, "pageId", "qz-runtime-remote-page", event.getPageId());
        requireSubmitted(problems, "action", "runtime-remote-submit", event.getAction());
        requireSubmitted(problems, "formId", "remote-smoke-form", event.getFormId());
        requireSubmitted(problems, "checkId", checkId, event.getFirstValue("checkId"));
        requireSubmitted(problems, "textEcho", "text-ok", event.getFirstValue("textEcho"));
        requireSubmitted(problems, "note", "textarea-ok", event.getFirstValue("note"));
        requireSubmitted(problems, "phase", "stream-ok", event.getFirstValue("phase"));
        requireSubmitted(problems, "submitter", "提交运行时自检", event.getFirstValue("submitter"));
        if (!hasValue(event.getValues(), "flag", "checked-ok")) {
            appendSubmitProblem(problems, "flag 未提交 checked-ok");
        }
        if (!hasValue(event.getValues(), "mode", "selected-ok")) {
            appendSubmitProblem(problems, "mode 未提交 selected-ok");
        }
        if (hasValue(event.getValues(), "mode", "ignored")) {
            appendSubmitProblem(problems, "未选中的 radio 被提交");
        }
        if (event.getValues().containsKey("disabledField")) {
            appendSubmitProblem(problems, "disabled 字段不应被提交");
        }
        event.reply(buildRemotePageSmokeResultPage(checkId, problems.length() == 0, problems.toString()), null);
    }

    /**
     * 创建远程页面 smoke 结果页。
     *
     * @param checkId 自检标识
     * @param success 是否通过
     * @param detail 失败详情
     * @return 结果页
     */
    private static RemoteDocumentPage buildRemotePageSmokeResultPage(String checkId, boolean success, String detail) {
        String title = success ? "远程页面自检通过" : "远程页面自检失败";
        String body = success
                ? "<h1>远程页面运行时自检通过</h1>"
                        + "<p>HTML Stream 拉取、解析、表单收集和 C2S 提交回调均已完成。</p>"
                : "<h1>远程页面运行时自检失败</h1><p>" + escapeHtmlLines(detail) + "</p>";
        return RemoteDocumentPage.builder("qz-runtime-remote-page-result")
                .title(title)
                .resourcePolicy(RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY)
                .metadata("checkId", checkId)
                .html("<html><body><div style=\"padding:16px;background-color:#0f172a;color:#e5e7eb;\">"
                        + body + "<p>checkId: " + escapeHtml(checkId) + "</p></div></body></html>")
                .build();
    }

    /**
     * 校验单个字段的首值。
     */
    private static void requireSubmitted(StringBuilder problems, String name, String expected, String actual) {
        if (!expected.equals(actual)) {
            appendSubmitProblem(problems, name + " 不一致: expected=" + expected + ", actual=" + actual);
        }
    }

    /**
     * 判断字段是否包含指定值。
     */
    private static boolean hasValue(Map<String, List<String>> values, String name, String expected) {
        List<String> list = values.get(name);
        return list != null && list.contains(expected);
    }

    /**
     * 追加提交校验问题。
     */
    private static void appendSubmitProblem(StringBuilder problems, String message) {
        if (problems.length() > 0) {
            problems.append('\n');
        }
        problems.append("- ").append(message);
    }

    /**
     * HTML 转义普通文本。
     */
    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * HTML 转义多行文本，并保留换行展示。
     */
    private static String escapeHtmlLines(String value) {
        return escapeHtml(value).replace("\n", "<br>");
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
