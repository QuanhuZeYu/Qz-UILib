package club.heiqi.uilib.net.api;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.core.NetEnvelope;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.transport.NetSide;

/**
 * Stream 请求下载、分片接收与远端取消状态注册器。
 */
final class NetStreamDownloadRegistry {

    private static final String STREAM_TOTAL_BYTES_HEADER = "x-qz-stream-total-bytes";
    private static final String STREAM_CHUNK_COUNT_HEADER = "x-qz-stream-chunk-count";
    private static final String STREAM_SEQUENCE_HEADER = "x-qz-stream-sequence";
    private static final int STREAM_FRAME_MARGIN_BYTES = 2048;
    private static final int PREFERRED_STREAM_CHUNK_BYTES = 256 * 1024;

    private final NetService service;
    private final Map<String, NetStreamEndpoint> streamEndpoints;
    private final AtomicLong nextStreamRequestId = new AtomicLong(1L);
    private final Map<Long, StreamDownload> pendingStreams = new ConcurrentHashMap<Long, StreamDownload>();
    private final Set<Long> remoteCancelledStreams =
            Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());

    NetStreamDownloadRegistry(NetService service, Map<String, NetStreamEndpoint> streamEndpoints) {
        this.service = Objects.requireNonNull(service, "service");
        this.streamEndpoints = Objects.requireNonNull(streamEndpoints, "streamEndpoints");
    }

    /**
     * 发起 Stream endpoint 调用并登记下载状态。
     *
     * @param endpoint endpoint
     * @param request 请求
     * @return Stream 调用句柄
     */
    NetStreamCall callEndpoint(NetStreamEndpoint endpoint, NetRequest request) {
        long requestId = nextStreamRequestId.getAndIncrement();
        NetStreamCall call = new NetStreamCall(service, endpoint.getId().asKey(), requestId);
        StreamDownload download = new StreamDownload(call, System.currentTimeMillis() + endpoint.getTimeoutMillis(),
                endpoint.getMaxBytes());
        pendingStreams.put(Long.valueOf(requestId), download);
        try {
            service.sendEnvelope(NetTarget.server(), NetEnvelope.of(NetEnvelope.Kind.STREAM_REQUEST, NetSide.SERVER,
                    endpoint.getId().asKey(), requestId, 0, request.getHeaders(), request.getBody()));
        } catch (RuntimeException exception) {
            pendingStreams.remove(Long.valueOf(requestId), download);
            call.fail(exception);
            throw exception;
        }
        return call;
    }

    /**
     * 向客户端回复 Stream 内容。
     *
     * @param player 玩家对象
     * @param endpointKey endpoint key
     * @param requestId 请求 id
     * @param response 响应
     */
    void reply(Object player, String endpointKey, long requestId, NetResponse response) {
        Objects.requireNonNull(response, "response");
        if (isCancelled(requestId)) {
            remoteCancelledStreams.remove(Long.valueOf(requestId));
            MyMod.LOG.info("Qz Stream 在开始前已被远端取消，跳过发送：endpoint={} requestId={}", endpointKey,
                    Long.valueOf(requestId));
            return;
        }
        NetStreamEndpoint endpoint = streamEndpoints.get(endpointKey);
        long maxBytes = endpoint == null ? NetPayloadLimits.DEFAULT_STREAM_CONTENT_LIMIT : endpoint.getMaxBytes();
        if (player == null) {
            MyMod.LOG.warn("Qz Stream 回复缺少发送者，无法发送：endpoint={} requestId={}", endpointKey,
                    Long.valueOf(requestId));
            return;
        }
        NetBody body = response.getBody();
        NetPayloadLimits.requireStreamContentSize(body.size(), maxBytes);
        NetTarget target = NetTarget.player(player);
        byte[] bytes = body.getBytes();
        int physicalLimit = service.requireTransport().getPhysicalFrameLimit(NetSide.CLIENT);
        int chunkSize = streamChunkSize(physicalLimit);
        int chunkCount = bytes.length == 0 ? 0 : (bytes.length + chunkSize - 1) / chunkSize;

        Map<String, String> startHeaders = new LinkedHashMap<String, String>(response.getHeaders());
        startHeaders.put(STREAM_TOTAL_BYTES_HEADER, Long.toString(bytes.length));
        startHeaders.put(STREAM_CHUNK_COUNT_HEADER, Integer.toString(chunkCount));
        service.sendStreamEnvelope(target, NetEnvelope.of(NetEnvelope.Kind.STREAM_START, NetSide.CLIENT, endpointKey,
                requestId, response.getStatusCode(), startHeaders, NetBody.of(body.getContentType(), new byte[0])));

        for (int sequence = 0; sequence < chunkCount; sequence++) {
            if (isCancelled(requestId)) {
                MyMod.LOG.info("Qz Stream 已被远端取消，停止发送：endpoint={} requestId={}", endpointKey,
                        Long.valueOf(requestId));
                break;
            }
            int offset = sequence * chunkSize;
            int length = Math.min(chunkSize, bytes.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(bytes, offset, chunk, 0, length);
            Map<String, String> chunkHeaders = new LinkedHashMap<String, String>();
            chunkHeaders.put(STREAM_SEQUENCE_HEADER, Integer.toString(sequence));
            service.sendStreamEnvelope(target, NetEnvelope.of(NetEnvelope.Kind.STREAM_CHUNK, NetSide.CLIENT,
                    endpointKey, requestId, 0, chunkHeaders, NetBody.binary(chunk)));
        }
        remoteCancelledStreams.remove(Long.valueOf(requestId));
    }

    /**
     * 回复 Stream 错误帧。
     *
     * @param player 玩家对象
     * @param endpointKey endpoint key
     * @param requestId 请求 id
     * @param throwable 异常
     */
    void fail(Object player, String endpointKey, long requestId, Throwable throwable) {
        String message = throwable == null ? "远端 Stream 处理失败" : throwable.getClass().getSimpleName() + ": "
                + (throwable.getMessage() == null ? "" : throwable.getMessage());
        if (player == null) {
            MyMod.LOG.warn("Qz Stream 错误帧缺少发送者，无法回复：endpoint={} requestId={}", endpointKey,
                    Long.valueOf(requestId));
            return;
        }
        service.sendStreamEnvelope(NetTarget.player(player), NetEnvelope.of(NetEnvelope.Kind.STREAM_ERROR,
                NetSide.CLIENT, endpointKey, requestId, 500, Collections.<String, String>emptyMap(),
                NetBody.text(message)));
    }

    /**
     * 判断 Stream 请求是否已被远端取消。
     *
     * @param requestId 请求 id
     * @return 是否取消
     */
    boolean isCancelled(long requestId) {
        return remoteCancelledStreams.contains(Long.valueOf(requestId));
    }

    /**
     * 发送取消帧并移除本地 pending 状态。
     *
     * @param endpointKey endpoint key
     * @param requestId 请求 id
     */
    void cancelCall(String endpointKey, long requestId) {
        pendingStreams.remove(Long.valueOf(requestId));
        try {
            service.sendEnvelope(NetTarget.server(), NetEnvelope.binary(NetEnvelope.Kind.STREAM_CANCEL, NetSide.SERVER,
                    endpointKey, requestId, new byte[0]));
        } catch (RuntimeException exception) {
            MyMod.LOG.warn("发送 Qz Stream 取消帧失败：endpoint={} requestId={}", endpointKey,
                    Long.valueOf(requestId), exception);
        }
    }

    /**
     * 记录收到的远端取消帧。
     *
     * @param envelope 取消信封
     * @param sender 发送者
     */
    void markRemoteCancelled(NetEnvelope envelope, Object sender) {
        remoteCancelledStreams.add(Long.valueOf(envelope.getRequestId()));
        MyMod.LOG.info("收到 Qz Stream 取消帧：endpoint={} requestId={} sender={}", envelope.getKey(),
                Long.valueOf(envelope.getRequestId()), String.valueOf(sender));
    }

    /**
     * 处理 Stream start 帧。
     *
     * @param envelope 信封
     */
    void handleStart(NetEnvelope envelope) {
        StreamDownload download = pendingStreams.get(Long.valueOf(envelope.getRequestId()));
        if (download == null) {
            MyMod.LOG.warn("收到未知 Stream start：endpoint={} requestId={}", envelope.getKey(),
                    Long.valueOf(envelope.getRequestId()));
            return;
        }
        boolean started;
        try {
            started = download.markStarted(envelope);
        } catch (RuntimeException exception) {
            pendingStreams.remove(Long.valueOf(envelope.getRequestId()), download);
            download.getCall().fail(exception);
            return;
        }
        if (!started) {
            MyMod.LOG.warn("收到重复 Stream start：endpoint={} requestId={}", envelope.getKey(),
                    Long.valueOf(envelope.getRequestId()));
            return;
        }
        download.getCall().emitProgress(new NetStreamProgress(envelope.getRequestId(), 0L,
                download.getTotalBytes()));
        if (download.getTotalBytes() == 0L) {
            complete(envelope.getRequestId(), download);
        }
    }

    /**
     * 处理 Stream chunk 帧。
     *
     * @param envelope 信封
     */
    void handleChunk(NetEnvelope envelope) {
        StreamDownload download = pendingStreams.get(Long.valueOf(envelope.getRequestId()));
        if (download == null) {
            MyMod.LOG.warn("收到未知 Stream chunk：endpoint={} requestId={}", envelope.getKey(),
                    Long.valueOf(envelope.getRequestId()));
            return;
        }
        boolean accepted;
        try {
            accepted = download.acceptChunk(envelope);
        } catch (RuntimeException exception) {
            pendingStreams.remove(Long.valueOf(envelope.getRequestId()), download);
            download.getCall().fail(exception);
            return;
        }
        if (!accepted) {
            pendingStreams.remove(Long.valueOf(envelope.getRequestId()), download);
            download.getCall().fail(new IllegalStateException("Stream 分片序号或长度不一致: " + envelope.getKey()));
            return;
        }
        download.getCall().emitProgress(new NetStreamProgress(envelope.getRequestId(), download.getReceivedBytes(),
                download.getTotalBytes()));
        if (download.isComplete()) {
            complete(envelope.getRequestId(), download);
        }
    }

    /**
     * 处理 Stream 错误帧。
     *
     * @param envelope 信封
     */
    void handleError(NetEnvelope envelope) {
        StreamDownload download = pendingStreams.remove(Long.valueOf(envelope.getRequestId()));
        if (download == null) {
            MyMod.LOG.warn("收到未知 Stream error：endpoint={} requestId={}", envelope.getKey(),
                    Long.valueOf(envelope.getRequestId()));
            return;
        }
        download.getCall().fail(new NetRemoteException(new String(envelope.getPayload(), StandardCharsets.UTF_8)));
    }

    /**
     * 清理超时的 Stream 下载。
     */
    void expireTimedOut() {
        long now = System.currentTimeMillis();
        for (StreamDownload download : pendingStreams.values()) {
            if (download.isExpired(now) && pendingStreams.remove(Long.valueOf(download.getRequestId()), download)) {
                download.getCall().fail(new NetTimeoutException("Stream 请求超时: " + download.getRequestId()));
            }
        }
    }

    /**
     * 让所有 pending Stream 失败。
     *
     * @param throwable 失败原因
     */
    void failAll(Throwable throwable) {
        for (StreamDownload download : pendingStreams.values()) {
            if (pendingStreams.remove(Long.valueOf(download.getRequestId()), download)) {
                download.getCall().fail(throwable);
            }
        }
    }

    /**
     * 清理取消状态。
     */
    void clearRemoteCancelled() {
        remoteCancelledStreams.clear();
    }

    private void complete(long requestId, StreamDownload download) {
        pendingStreams.remove(Long.valueOf(requestId), download);
        try {
            download.getCall().complete(NetResponse.fromWire(download.getStatusCode(), download.getHeaders(),
                    NetBody.of(download.getContentType(), download.getBytes())));
        } catch (RuntimeException exception) {
            download.getCall().fail(exception);
        }
    }

    private static int streamChunkSize(int physicalLimit) {
        int normalizedLimit = NetPayloadLimits.clampPhysicalLimit(physicalLimit);
        int preferred = Math.min(PREFERRED_STREAM_CHUNK_BYTES, Math.max(1, normalizedLimit - STREAM_FRAME_MARGIN_BYTES));
        return Math.max(1, preferred);
    }

    private static long parseLongHeader(Map<String, String> headers, String name, long defaultValue) {
        String value = headers.get(name);
        if (value == null || value.length() == 0) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    private static final class StreamDownload {

        private final NetStreamCall call;
        private final long deadlineMillis;
        private final long maxBytes;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Map<String, String> headers = Collections.emptyMap();
        private NetContentType contentType = NetContentType.BINARY;
        private int statusCode;
        private long totalBytes = -1L;
        private int expectedSequence;
        private boolean started;

        private StreamDownload(NetStreamCall call, long deadlineMillis, long maxBytes) {
            this.call = call;
            this.deadlineMillis = deadlineMillis;
            this.maxBytes = maxBytes;
        }

        private long getRequestId() {
            return call.getRequestId();
        }

        private NetStreamCall getCall() {
            return call;
        }

        private long getTotalBytes() {
            return totalBytes;
        }

        private long getReceivedBytes() {
            return bytes.size();
        }

        private boolean isExpired(long nowMillis) {
            return nowMillis >= deadlineMillis;
        }

        private boolean markStarted(NetEnvelope envelope) {
            if (started) {
                return false;
            }
            started = true;
            statusCode = envelope.getStatusCode();
            contentType = envelope.getContentType();
            headers = filterStreamHeaders(envelope.getHeaders());
            totalBytes = parseLongHeader(envelope.getHeaders(), STREAM_TOTAL_BYTES_HEADER, 0L);
            NetPayloadLimits.requireStreamContentSize(totalBytes, maxBytes);
            return true;
        }

        private boolean acceptChunk(NetEnvelope envelope) {
            if (!started) {
                return false;
            }
            int sequence = (int) parseLongHeader(envelope.getHeaders(), STREAM_SEQUENCE_HEADER, -1L);
            if (sequence != expectedSequence) {
                return false;
            }
            byte[] payload = envelope.getPayload();
            if (((long) bytes.size()) + payload.length > totalBytes) {
                return false;
            }
            bytes.write(payload, 0, payload.length);
            expectedSequence++;
            return true;
        }

        private boolean isComplete() {
            return started && totalBytes >= 0L && ((long) bytes.size()) >= totalBytes;
        }

        private Map<String, String> getHeaders() {
            return headers;
        }

        private NetContentType getContentType() {
            return contentType;
        }

        private int getStatusCode() {
            return statusCode;
        }

        private byte[] getBytes() {
            return bytes.toByteArray();
        }

        private Map<String, String> filterStreamHeaders(Map<String, String> source) {
            Map<String, String> filtered = new LinkedHashMap<String, String>();
            for (Map.Entry<String, String> entry : source.entrySet()) {
                String key = entry.getKey();
                if (STREAM_TOTAL_BYTES_HEADER.equals(key) || STREAM_CHUNK_COUNT_HEADER.equals(key)
                        || STREAM_SEQUENCE_HEADER.equals(key)) {
                    continue;
                }
                filtered.put(key, entry.getValue());
            }
            return filtered;
        }
    }
}
