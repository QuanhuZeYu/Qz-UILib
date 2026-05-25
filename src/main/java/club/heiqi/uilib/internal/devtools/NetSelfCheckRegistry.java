package club.heiqi.uilib.internal.devtools;

import java.time.Duration;
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
import club.heiqi.uilib.net.api.NetStreamEndpoint;
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
final class NetSelfCheckRegistry {

    static final String NAMESPACE = "qz";
    static final String CHECK_ID_HEADER = "x-qz-check-id";
    static final String CHECK_KIND_HEADER = "x-qz-check-kind";
    static final NetChannelId CHANNEL_ID = NetChannelId.of(NAMESPACE, "runtimeChannelCheck");
    static final NetEndpointId FETCH_ID = NetEndpointId.of(NAMESPACE, "runtimeFetchCheck");
    static final NetEndpointId FETCH_ERROR_ID = NetEndpointId.of(NAMESPACE, "runtimeFetchErrorCheck");
    static final NetEndpointId FETCH_TIMEOUT_ID = NetEndpointId.of(NAMESPACE, "runtimeFetchTimeoutCheck");
    static final NetEndpointId FETCH_CANCEL_ID = NetEndpointId.of(NAMESPACE, "runtimeFetchCancelCheck");
    static final NetEndpointId FETCH_RATE_LIMIT_ID = NetEndpointId.of(NAMESPACE, "runtimeFetchLimitCheck");
    static final NetEndpointId STREAM_ID = NetEndpointId.of(NAMESPACE, "runtimeStreamCheck");
    static final NetEndpointId STORE_TRIGGER_ID = NetEndpointId.of(NAMESPACE, "runtimeStoreTrigger");
    static final NetEndpointId STORE_DELTA_TRIGGER_ID = NetEndpointId.of(NAMESPACE,
            "runtimeStoreDeltaTrigger");
    static final NetEndpointId PLAYER_STORE_TRIGGER_ID = NetEndpointId.of(NAMESPACE,
            "runtimePlayerStoreTrigger");
    static final NetEndpointId REMOTE_PAGE_TRIGGER_ID = NetEndpointId.of(NAMESPACE,
            "runtimeRemotePageTrigger");
    static final NetEndpointId REMOTE_HUD_TRIGGER_ID = NetEndpointId.of(NAMESPACE,
            "runtimeRemoteHudTrigger");
    static final NetStoreId STORE_ID = NetStoreId.of(NAMESPACE, "runtimeStoreCheck");
    static final NetStoreId STORE_DELTA_ID = NetStoreId.of(NAMESPACE, "runtimeStoreDeltaCheck");
    static final NetStoreId PLAYER_STORE_ID = NetStoreId.of(NAMESPACE, "runtimePlayerStoreCheck");
    static final int CHUNKED_CHANNEL_BYTES = 100 * 1024;
    static final int STREAM_DOWNLOAD_BYTES = NetPayloadLimits.DEFAULT_LOGICAL_MESSAGE_LIMIT + 32 * 1024;
    static final long FETCH_TIMEOUT_MILLIS = 120L;
    static final long FETCH_TIMEOUT_WAKEUP_MILLIS = 240L;
    static final long FETCH_CANCEL_REPLY_MILLIS = 240L;
    static final long FETCH_CANCEL_VERIFY_MILLIS = 520L;
    static final long TIMEOUT_MILLIS = 5_000L;

    static final AtomicLong NEXT_CHECK_ID = new AtomicLong(1L);
    static final Map<String, CompletableFuture<NetMessage>> CHANNEL_PENDING =
            new ConcurrentHashMap<String, CompletableFuture<NetMessage>>();
    static final Map<String, CompletableFuture<NetBody>> STORE_PENDING =
            new ConcurrentHashMap<String, CompletableFuture<NetBody>>();
    static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "QzNetSelfCheckTimeout");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    static volatile boolean registered;
    static NetChannel channel;
    static NetFetchEndpoint fetchEndpoint;
    static NetFetchEndpoint fetchErrorEndpoint;
    static NetFetchEndpoint fetchTimeoutEndpoint;
    static NetFetchEndpoint fetchCancelEndpoint;
    static NetFetchEndpoint fetchRateLimitEndpoint;
    static NetStreamEndpoint streamEndpoint;
    static NetFetchEndpoint storeTriggerEndpoint;
    static NetFetchEndpoint storeDeltaTriggerEndpoint;
    static NetStore store;
    static NetStore storeDelta;
    static NetFetchEndpoint playerStoreTriggerEndpoint;
    static NetStore playerStore;
    static NetFetchEndpoint remotePageTriggerEndpoint;
    static NetFetchEndpoint remoteHudTriggerEndpoint;

    private NetSelfCheckRegistry() {}

    /**
     * 注册运行时自检端点。
     */
    static synchronized void register() {
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
                            String sessionId = RemoteSelfCheckPages.openRemotePageSmoke(player, checkId);
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
        remoteHudTriggerEndpoint = service.fetch(REMOTE_HUD_TRIGGER_ID)
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
                            String sessionId = RemoteSelfCheckPages.openRemoteHudSmoke(player, checkId);
                            context.reply(NetResponse.json(jsonFor(checkId, "remoteHudOpen"))
                                    .withHeader(CHECK_ID_HEADER, checkId)
                                    .withHeader(CHECK_KIND_HEADER, "remoteHud")
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
     * 返回运行时 Store 视图，供客户端 DOM bridge 自检使用。
     *
     * @return Store 视图
     */
    static NetStoreView getRuntimeStoreView() {
        ensureRegistered();
        return store.view();
    }
    static void handleChannelMessage(NetMessage message, NetReceiveContext context) {
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

    static void scheduleFetchTimeoutWakeup(final String checkId, final Object player) {
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

    static void scheduleFetchCancelReply(final String checkId,
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

    static void completeStoreSnapshot(NetBody snapshot) {
        String checkId = extractId(snapshot.asUtf8String());
        CompletableFuture<NetBody> pending = STORE_PENDING.get(checkId);
        if (pending != null) {
            pending.complete(snapshot);
        }
    }

    static void ensureRegistered() {
        if (!registered) {
            throw new IllegalStateException("网络运行时自检端点尚未注册");
        }
    }

    static String nextCheckId(String kind) {
        return kind + "-" + NEXT_CHECK_ID.getAndIncrement();
    }

    static String jsonFor(String checkId, String kind) {
        return "{\"id\":\"" + safeJson(checkId) + "\",\"kind\":\"" + safeJson(kind) + "\",\"ok\":true}";
    }

    static String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static String extractId(String json) {
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

    static <T> CompletableFuture<T> withTimeout(final CompletableFuture<T> future,
            final String label, final String checkId) {
        return withTimeout(future, label, checkId, TIMEOUT_MILLIS);
    }

    static <T> CompletableFuture<T> withTimeout(final CompletableFuture<T> future,
            final String label, final String checkId, long timeoutMillis) {
        TIMEOUT_EXECUTOR.schedule(new Runnable() {
            @Override
            public void run() {
                future.completeExceptionally(new NetTimeoutException(label + " 自检超时: " + checkId));
            }
        }, timeoutMillis, TimeUnit.MILLISECONDS);
        return future;
    }

    static byte[] chunkedPayload() {
        byte[] payload = new byte[CHUNKED_CHANNEL_BYTES];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) (index & 0xFF);
        }
        return payload;
    }

    static byte[] streamPayload() {
        byte[] payload = new byte[STREAM_DOWNLOAD_BYTES];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) ((index * 31) & 0xFF);
        }
        return payload;
    }

    static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    static void requireEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(label + " 不一致: " + expected + " vs " + actual);
        }
    }

    static void requireContains(String text, String expected, String label) {
        if (text == null || !text.contains(expected)) {
            throw new IllegalStateException(label + " 缺少片段: " + expected);
        }
    }
}
