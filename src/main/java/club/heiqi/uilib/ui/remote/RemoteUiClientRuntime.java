package club.heiqi.uilib.ui.remote;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 远程 UI 客户端 runtime 骨架，集中维护 pending/active 与异步落地 guard。
 */
final class RemoteUiClientRuntime {

    private final AtomicLong tokenCounter = new AtomicLong();
    private final Map<String, SurfaceMount> currentBySurface = new ConcurrentHashMap<String, SurfaceMount>();
    private final Map<String, SurfaceMount> pendingBySession = new ConcurrentHashMap<String, SurfaceMount>();

    /**
     * 标记新的 open offer，并生成本地挂载 token。
     */
    PendingMount beginOpen(RemoteUiProtocol.SurfaceType surfaceType, String surfaceId, String sessionId,
            long contentRevision) {
        SurfaceMount mount = new SurfaceMount(surfaceType, surfaceId, sessionId, contentRevision,
                tokenCounter.incrementAndGet(), RemoteUiProtocol.ClientSurfaceState.FETCHING);
        pendingBySession.put(mount.sessionId, mount);
        SurfaceMount previous = currentBySurface.put(mount.surfaceKey(), mount);
        if (previous != null && !previous.matches(surfaceType, surfaceId, sessionId, contentRevision,
                mount.localMountToken)) {
            previous.state = RemoteUiProtocol.ClientSurfaceState.STALE;
            pendingBySession.remove(previous.sessionId, previous);
        }
        return new PendingMount(mount);
    }

    /**
     * 校验异步回调是否仍属于当前 surface。
     */
    boolean isCurrent(RemoteUiProtocol.SurfaceType surfaceType, String surfaceId, String sessionId,
            long contentRevision, long localMountToken) {
        SurfaceMount current = currentBySurface.get(surfaceKey(surfaceType, surfaceId));
        return current != null && current.matches(surfaceType, surfaceId, sessionId, contentRevision,
                localMountToken);
    }

    /**
     * 将 pending mount 切换为 active。
     */
    boolean completePending(RemoteUiProtocol.SurfaceType surfaceType, String surfaceId, String sessionId,
            long contentRevision, long localMountToken) {
        SurfaceMount pending = pendingBySession.get(sessionId == null ? "" : sessionId);
        if (pending == null || !pending.matches(surfaceType, surfaceId, sessionId, contentRevision,
                localMountToken) || !isCurrent(surfaceType, surfaceId, sessionId, contentRevision, localMountToken)) {
            if (pending != null) {
                pending.state = RemoteUiProtocol.ClientSurfaceState.STALE;
            }
            return false;
        }
        pendingBySession.remove(pending.sessionId, pending);
        pending.state = RemoteUiProtocol.ClientSurfaceState.ACTIVE;
        currentBySurface.put(pending.surfaceKey(), pending);
        return true;
    }

    /**
     * 丢弃已经变旧的 pending mount。
     */
    boolean discard(RemoteUiProtocol.SurfaceType surfaceType, String surfaceId, String sessionId,
            long contentRevision, long localMountToken) {
        return terminalize(surfaceType, surfaceId, sessionId, contentRevision, localMountToken,
                RemoteUiProtocol.ClientSurfaceState.STALE);
    }

    /**
     * 将失败的 pending/current mount 置为终态错误并移除。
     */
    boolean terminalizeError(RemoteUiProtocol.SurfaceType surfaceType, String surfaceId, String sessionId,
            long contentRevision, long localMountToken) {
        return terminalize(surfaceType, surfaceId, sessionId, contentRevision, localMountToken,
                RemoteUiProtocol.ClientSurfaceState.ERROR);
    }

    /**
     * 按 session 精确关闭当前 surface。
     */
    boolean closeSession(RemoteUiProtocol.SurfaceType surfaceType, String surfaceId, String sessionId,
            long contentRevision, long localMountToken) {
        String key = surfaceKey(surfaceType, surfaceId);
        SurfaceMount current = currentBySurface.get(key);
        if (current == null || !current.matches(surfaceType, surfaceId, sessionId, contentRevision, localMountToken)) {
            return false;
        }
        current.state = RemoteUiProtocol.ClientSurfaceState.CLOSED;
        currentBySurface.remove(key, current);
        pendingBySession.remove(current.sessionId, current);
        return true;
    }

    /**
     * 按 session 与 revision 关闭当前 surface，不校验本地 token。
     */
    boolean closeSession(RemoteUiProtocol.SurfaceType surfaceType, String surfaceId, String sessionId,
            long contentRevision) {
        String key = surfaceKey(surfaceType, surfaceId);
        SurfaceMount current = currentBySurface.get(key);
        if (current == null || !current.matchesWithoutToken(surfaceType, surfaceId, sessionId, contentRevision)) {
            return false;
        }
        current.state = RemoteUiProtocol.ClientSurfaceState.CLOSED;
        currentBySurface.remove(key, current);
        pendingBySession.remove(current.sessionId, current);
        return true;
    }

    /**
     * 按 surface 关闭当前 pending/active。
     */
    boolean closeSurface(RemoteUiProtocol.SurfaceType surfaceType, String surfaceId) {
        SurfaceMount current = currentBySurface.remove(surfaceKey(surfaceType, surfaceId));
        if (current == null) {
            return false;
        }
        current.state = RemoteUiProtocol.ClientSurfaceState.CLOSED;
        pendingBySession.remove(current.sessionId, current);
        return true;
    }

    /**
     * 关闭同 surface 的 pending open。
     */
    void dismissPendingBySurface(RemoteUiProtocol.SurfaceType surfaceType, String surfaceId) {
        for (SurfaceMount pending : pendingBySession.values()) {
            if (pending != null && pending.surfaceType == surfaceType
                    && pending.surfaceId.equals(surfaceId == null ? "" : surfaceId)) {
                pending.state = RemoteUiProtocol.ClientSurfaceState.STALE;
                pendingBySession.remove(pending.sessionId, pending);
            }
        }
    }

    /**
     * 清空客户端 runtime。
     */
    void clear() {
        currentBySurface.clear();
        pendingBySession.clear();
        tokenCounter.set(0L);
    }

    /**
     * 返回测试可见的 pending 数量。
     */
    int pendingSizeForTests() {
        return pendingBySession.size();
    }

    /**
     * 返回测试可见的 current 数量。
     */
    int currentSizeForTests() {
        return currentBySurface.size();
    }

    private boolean terminalize(RemoteUiProtocol.SurfaceType surfaceType, String surfaceId, String sessionId,
            long contentRevision, long localMountToken, RemoteUiProtocol.ClientSurfaceState terminalState) {
        boolean changed = false;
        String sessionKey = sessionId == null ? "" : sessionId;
        SurfaceMount pending = pendingBySession.get(sessionKey);
        if (pending != null && pending.matches(surfaceType, surfaceId, sessionId, contentRevision, localMountToken)
                && pendingBySession.remove(sessionKey, pending)) {
            pending.state = terminalState;
            changed = true;
        }
        String surfaceKey = surfaceKey(surfaceType, surfaceId);
        SurfaceMount current = currentBySurface.get(surfaceKey);
        if (current != null && current.matches(surfaceType, surfaceId, sessionId, contentRevision, localMountToken)
                && currentBySurface.remove(surfaceKey, current)) {
            current.state = terminalState;
            pendingBySession.remove(current.sessionId, current);
            changed = true;
        }
        return changed;
    }

    private static String surfaceKey(RemoteUiProtocol.SurfaceType surfaceType, String surfaceId) {
        return (surfaceType == null ? "" : surfaceType.name()) + ":" + (surfaceId == null ? "" : surfaceId);
    }

    /**
     * beginOpen 返回的本地挂载身份。
     */
    static final class PendingMount {

        private final SurfaceMount mount;

        private PendingMount(SurfaceMount mount) {
            this.mount = mount;
        }

        long getLocalMountToken() {
            return mount.localMountToken;
        }
    }

    private static final class SurfaceMount {

        private final RemoteUiProtocol.SurfaceType surfaceType;
        private final String surfaceId;
        private final String sessionId;
        private final long contentRevision;
        private final long localMountToken;
        private volatile RemoteUiProtocol.ClientSurfaceState state;

        private SurfaceMount(RemoteUiProtocol.SurfaceType surfaceType, String surfaceId, String sessionId,
                long contentRevision, long localMountToken, RemoteUiProtocol.ClientSurfaceState state) {
            this.surfaceType = surfaceType;
            this.surfaceId = surfaceId == null ? "" : surfaceId;
            this.sessionId = sessionId == null ? "" : sessionId;
            this.contentRevision = contentRevision;
            this.localMountToken = localMountToken;
            this.state = state;
        }

        private String surfaceKey() {
            return RemoteUiClientRuntime.surfaceKey(surfaceType, surfaceId);
        }

        private boolean matches(RemoteUiProtocol.SurfaceType candidateSurfaceType, String candidateSurfaceId,
                String candidateSessionId, long candidateRevision, long candidateToken) {
            return matchesWithoutToken(candidateSurfaceType, candidateSurfaceId, candidateSessionId, candidateRevision)
                    && localMountToken == candidateToken;
        }

        private boolean matchesWithoutToken(RemoteUiProtocol.SurfaceType candidateSurfaceType,
                String candidateSurfaceId, String candidateSessionId, long candidateRevision) {
            return surfaceType == candidateSurfaceType
                    && surfaceId.equals(candidateSurfaceId == null ? "" : candidateSurfaceId)
                    && sessionId.equals(candidateSessionId == null ? "" : candidateSessionId)
                    && contentRevision == candidateRevision;
        }
    }
}
