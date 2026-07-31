package club.heiqi.uilib.ui.scene.runtime;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 单个 {@link SceneRuntime} 私有的最小 Motion 采样器。
 *
 * <p>只保存当前值、目标值和帧时间，不创建 ticker，也不写 signal 或事务历史。
 * host 每帧传入一次单调时间，track 直接写 SceneNode 的 paint/composite 属性。</p>
 */
final class SceneMotionDriver {

    private static final long UNSET_TIME = Long.MIN_VALUE;

    /** track key 按对象身份隔离，避免不同 occurrence 因 equals 合并。 */
    private final IdentityHashMap<Object, MotionTrack> tracks = new IdentityHashMap<Object, MotionTrack>();

    /** Config 显式启用；其它 scene runtime 保持既有立即应用语义。 */
    private boolean enabled;

    /** host 最近一次采样时间，仅用于拒绝倒退时钟。 */
    private long lastSampleNanos = UNSET_TIME;

    /** 当前 sample/完成回调所属的帧时间；帧外 retarget 必须等下一次 sample 才起计时。 */
    private long currentFrameNanos = UNSET_TIME;

    /** 当前是否处于 runtime 建立的单帧采样边界内。 */
    private boolean frameOpen;

    void enable() {
        enabled = true;
    }

    boolean isEnabled() {
        return enabled;
    }

    void setColorTarget(Object key, int target, int durationMillis, Consumer<Integer> applier) {
        MotionTrack existing = tracks.get(key);
        ColorTrack track;
        if (existing instanceof ColorTrack) {
            track = (ColorTrack) existing;
        } else {
            track = new ColorTrack(key, target, applier);
            tracks.put(key, track);
            applier.accept(Integer.valueOf(target));
            return;
        }
        if (!enabled || durationMillis <= 0) {
            track.applyImmediately(target);
            return;
        }
        track.retarget(target, durationNanos(durationMillis), motionStartNanos());
    }

    void setFloatTarget(Object key, float target, int durationMillis, Consumer<Float> applier) {
        MotionTrack existing = tracks.get(key);
        FloatTrack track;
        if (existing instanceof FloatTrack) {
            track = (FloatTrack) existing;
        } else {
            track = new FloatTrack(key, target, applier);
            tracks.put(key, track);
            applier.accept(Float.valueOf(target));
            return;
        }
        if (!enabled || durationMillis <= 0) {
            track.applyImmediately(target);
            return;
        }
        track.retarget(target, durationNanos(durationMillis), motionStartNanos());
    }

    void start(Object key, int durationMillis, Consumer<Float> applier, Runnable completion) {
        start(key, 0, durationMillis, applier, completion);
    }

    /** 启动带延迟的显式轨道；delay 期间持续应用 progress=0。 */
    void start(Object key, int delayMillis, int durationMillis,
               Consumer<Float> applier, Runnable completion) {
        if (!enabled || durationMillis <= 0) {
            tracks.remove(key);
            applier.accept(Float.valueOf(1.0f));
            if (completion != null) {
                completion.run();
            }
            return;
        }
        TimedTrack track = new TimedTrack(key, durationNanos(Math.max(0, delayMillis)),
                durationNanos(durationMillis), applier, completion, motionStartNanos());
        tracks.put(key, track);
        applier.accept(Float.valueOf(0.0f));
    }

    void remove(Object key) {
        tracks.remove(key);
    }

    /** 建立本帧边界；完成回调内新建的下一阶段复用同一 timestamp。 */
    void beginFrame(long nowNanos) {
        if (lastSampleNanos != UNSET_TIME && nowNanos < lastSampleNanos) {
            nowNanos = lastSampleNanos;
        }
        lastSampleNanos = nowNanos;
        currentFrameNanos = nowNanos;
        frameOpen = true;
    }

    /** 关闭本帧边界，防止帧外 retarget 错把上一帧时间当起点。 */
    void endFrame() {
        frameOpen = false;
        currentFrameNanos = UNSET_TIME;
    }

    /**
     * 采样当前 runtime 的全部 active track。
     *
     * @return 是否执行过 completion；runtime 据此同帧 flush 新挂载组件的初始 effect
     */
    boolean sample() {
        boolean ranCompletion = false;
        List<MotionTrack> snapshot = new ArrayList<MotionTrack>(tracks.values());
        for (MotionTrack track : snapshot) {
            if (tracks.get(track.key) != track || !track.isActive()) {
                continue;
            }
            boolean finished = track.sample(currentFrameNanos);
            if (finished && track instanceof TimedTrack && tracks.get(track.key) == track) {
                tracks.remove(track.key);
                Runnable completion = ((TimedTrack) track).completion;
                if (completion != null) {
                    completion.run();
                    ranCompletion = true;
                }
            }
        }
        return ranCompletion;
    }

    /** 完成当前所有 active track；供确定性测试收敛多阶段 motion。 */
    boolean finishActive() {
        boolean ranCompletion = false;
        List<MotionTrack> snapshot = new ArrayList<MotionTrack>(tracks.values());
        for (MotionTrack track : snapshot) {
            if (tracks.get(track.key) != track || !track.isActive()) {
                continue;
            }
            track.finish();
            if (track instanceof TimedTrack && tracks.get(track.key) == track) {
                tracks.remove(track.key);
                Runnable completion = ((TimedTrack) track).completion;
                if (completion != null) {
                    completion.run();
                    ranCompletion = true;
                }
            }
        }
        return ranCompletion;
    }

    boolean hasActiveTracks() {
        for (Map.Entry<Object, MotionTrack> entry : tracks.entrySet()) {
            if (entry.getValue().isActive()) {
                return true;
            }
        }
        return false;
    }

    int activeTrackCount() {
        int count = 0;
        for (MotionTrack track : tracks.values()) {
            if (track.isActive()) {
                count++;
            }
        }
        return count;
    }

    void clear() {
        tracks.clear();
        endFrame();
        lastSampleNanos = UNSET_TIME;
    }

    private long motionStartNanos() {
        return frameOpen ? currentFrameNanos : UNSET_TIME;
    }

    private static long durationNanos(int durationMillis) {
        return (long) durationMillis * 1_000_000L;
    }

    private static float progress(long nowNanos, long startNanos, long durationNanos) {
        if (durationNanos <= 0L || nowNanos >= startNanos + durationNanos) {
            return 1.0f;
        }
        if (nowNanos <= startNanos) {
            return 0.0f;
        }
        float linear = (float) ((double) (nowNanos - startNanos) / (double) durationNanos);
        return linear * linear * (3.0f - 2.0f * linear);
    }

    private abstract static class MotionTrack {

        private final Object key;

        private MotionTrack(Object key) {
            this.key = key;
        }

        abstract boolean isActive();

        abstract boolean sample(long nowNanos);

        abstract void finish();
    }

    private static final class ColorTrack extends MotionTrack {

        private final Consumer<Integer> applier;
        private int current;
        private int start;
        private int target;
        private long startNanos = UNSET_TIME;
        private long durationNanos;
        private boolean active;

        private ColorTrack(Object key, int initial, Consumer<Integer> applier) {
            super(key);
            this.applier = applier;
            this.current = initial;
            this.start = initial;
            this.target = initial;
        }

        private void retarget(int next, long duration, long frameStartNanos) {
            if (next == target && active) {
                return;
            }
            if (next == current) {
                applyImmediately(next);
                return;
            }
            start = current;
            target = next;
            durationNanos = duration;
            startNanos = frameStartNanos;
            active = true;
        }

        private void applyImmediately(int value) {
            current = value;
            start = value;
            target = value;
            active = false;
            startNanos = UNSET_TIME;
            applier.accept(Integer.valueOf(value));
        }

        @Override
        boolean isActive() {
            return active;
        }

        @Override
        boolean sample(long nowNanos) {
            if (startNanos == UNSET_TIME) {
                startNanos = nowNanos;
            }
            float p = progress(nowNanos, startNanos, durationNanos);
            current = interpolateColor(start, target, p);
            applier.accept(Integer.valueOf(current));
            if (p >= 1.0f) {
                applyImmediately(target);
                return true;
            }
            return false;
        }

        @Override
        void finish() {
            applyImmediately(target);
        }
    }

    private static final class FloatTrack extends MotionTrack {

        private final Consumer<Float> applier;
        private float current;
        private float start;
        private float target;
        private long startNanos = UNSET_TIME;
        private long durationNanos;
        private boolean active;

        private FloatTrack(Object key, float initial, Consumer<Float> applier) {
            super(key);
            this.applier = applier;
            this.current = initial;
            this.start = initial;
            this.target = initial;
        }

        private void retarget(float next, long duration, long frameStartNanos) {
            if (Float.compare(next, target) == 0 && active) {
                return;
            }
            if (Float.compare(next, current) == 0) {
                applyImmediately(next);
                return;
            }
            start = current;
            target = next;
            durationNanos = duration;
            startNanos = frameStartNanos;
            active = true;
        }

        private void applyImmediately(float value) {
            current = value;
            start = value;
            target = value;
            active = false;
            startNanos = UNSET_TIME;
            applier.accept(Float.valueOf(value));
        }

        @Override
        boolean isActive() {
            return active;
        }

        @Override
        boolean sample(long nowNanos) {
            if (startNanos == UNSET_TIME) {
                startNanos = nowNanos;
            }
            float p = progress(nowNanos, startNanos, durationNanos);
            current = start + (target - start) * p;
            applier.accept(Float.valueOf(current));
            if (p >= 1.0f) {
                applyImmediately(target);
                return true;
            }
            return false;
        }

        @Override
        void finish() {
            applyImmediately(target);
        }
    }

    private static final class TimedTrack extends MotionTrack {

        private final long delayNanos;
        private final long durationNanos;
        private final Consumer<Float> applier;
        private final Runnable completion;
        private long startNanos;
        private boolean active = true;

        private TimedTrack(Object key, long delayNanos, long durationNanos, Consumer<Float> applier,
                           Runnable completion, long frameStartNanos) {
            super(key);
            this.delayNanos = delayNanos;
            this.durationNanos = durationNanos;
            this.applier = applier;
            this.completion = completion;
            this.startNanos = frameStartNanos;
        }

        @Override
        boolean isActive() {
            return active;
        }

        @Override
        boolean sample(long nowNanos) {
            if (startNanos == UNSET_TIME) {
                startNanos = nowNanos;
            }
            float p = progress(nowNanos, startNanos + delayNanos, durationNanos);
            applier.accept(Float.valueOf(p));
            if (p >= 1.0f) {
                active = false;
                return true;
            }
            return false;
        }

        @Override
        void finish() {
            applier.accept(Float.valueOf(1.0f));
            active = false;
        }
    }

    private static int interpolateColor(int from, int to, float progress) {
        int a = interpolateChannel(from >>> 24, to >>> 24, progress);
        int r = interpolateChannel((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, progress);
        int g = interpolateChannel((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, progress);
        int b = interpolateChannel(from & 0xFF, to & 0xFF, progress);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int interpolateChannel(int from, int to, float progress) {
        return Math.round(from + (to - from) * progress);
    }
}
