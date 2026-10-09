package club.heiqi.uilib.ui.scene.edit;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.Signal;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

/**
 * 业务持有的单行/多行编辑状态；合同见 UILib editing spec 1.1、1.3。
 * 构造和写命令必须在所属域内执行，外部异步完成经 domain.execute 递交。
 * 不持有控件、宿主或业务权威值；焦点切换由调用方调用 breakHistoryGroup。
 */
public final class SceneEditState {
    public enum Mode { SINGLE_LINE, MULTI_LINE }

    public static final class Snapshot {
        public final String text, composition, error;
        public final int anchor, caret;
        public final boolean active, composing;
        public final long revision;

        private Snapshot(String text, String composition, String error, int anchor, int caret,
                         boolean active, boolean composing, long revision) {
            this.text = text;
            this.composition = composition;
            this.error = error;
            this.anchor = anchor;
            this.caret = caret;
            this.active = active;
            this.composing = composing;
            this.revision = revision;
        }
    }

    public enum Status { PENDING, ACCEPTED, REJECTED }

    public static final class Result {
        public final Status status;
        /** 待定/拒绝时是请求文本，接受时是规范化文本。 */
        public final String text;
        /** 仅拒绝时有原因，其余为 null。 */
        public final String reason;

        private Result(Status status, String text, String reason) {
            this.status = status;
            this.text = text;
            this.reason = reason;
        }
    }

    public static final class Submission {
        public final String text;
        public final long revision;
        public final boolean complete;
        private final SceneEditState state;
        private final long generation;
        private final Signal<Result> result;
        private final ReadableSignal<Result> view;
        private boolean resolved;

        private Submission(SceneEditState state, Snapshot snapshot, boolean complete) {
            this.state = state;
            this.text = snapshot.text;
            this.revision = snapshot.revision;
            this.complete = complete;
            this.generation = state.generation;
            this.result = state.domain.signal(new Result(Status.PENDING, text, null));
            this.view = result::get;
        }

        public ReadableSignal<Result> status() { return view; }
    }

    // 128 是局部历史容量，影响可逆步数，不影响业务提交合同。
    private static final int HISTORY_LIMIT = 128;
    private enum Kind { NONE, INPUT, BACKWARD, FORWARD }
    private static final class Position {
        final String text;
        final int anchor, caret;
        Position(String text, int anchor, int caret) {
            this.text = text; this.anchor = anchor; this.caret = caret;
        }
        Position(Snapshot snapshot) { this(snapshot.text, snapshot.anchor, snapshot.caret); }
        boolean matches(Position other) {
            return text.equals(other.text) && anchor == other.anchor && caret == other.caret;
        }
    }
    private static final class Change {
        final Position before;
        Position after;
        Change(Position before, Position after) { this.before = before; this.after = after; }
    }

    private final ReactiveDomain domain;
    private final Mode mode;
    private final Signal<Snapshot> value;
    private final ReadableSignal<Snapshot> snapshotView;
    private final ReadableSignal<String> textView;
    private final ArrayList<Change> history = new ArrayList<>();
    private int historyPosition;
    private Kind groupKind = Kind.NONE;
    private String baseline;
    private long generation;
    private Submission latest;

    public SceneEditState(ReactiveDomain domain, String baseline) {
        this(domain, baseline, Mode.SINGLE_LINE);
    }

    public SceneEditState(ReactiveDomain domain, String baseline, Mode mode) {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.mode = Objects.requireNonNull(mode, "mode");
        domain.checkWrite();
        this.baseline = normalize(baseline);
        int end = this.baseline.length();
        value = domain.signal(new Snapshot(this.baseline, "", null, end, end, false, false, 0));
        // 缓存独立只读视图，不把可强转为 Signal 的写入口暴露给控件。
        snapshotView = value::get;
        textView = () -> value.get().text;
    }

    public ReactiveDomain domain() { return domain; }
    public Mode mode() { return mode; }
    public ReadableSignal<Snapshot> snapshot() { return snapshotView; }
    public ReadableSignal<String> text() { return textView; }

    /** 指针文字簇位置由控件排版提供；越界夹取，簇内位置向左对齐。 */
    public void select(int anchor, int caret) {
        domain.checkWrite();
        groupKind = Kind.NONE;
        Snapshot s = value.get();
        int[] boundaries = boundaries(s.text);
        int a = floor(boundaries, anchor), c = floor(boundaries, caret);
        if (a != s.anchor || c != s.caret)
            value.set(new Snapshot(s.text, s.composition, s.error, a, c, s.active, s.composing, s.revision));
    }

    public void move(int direction, boolean extend) {
        domain.checkWrite();
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("direction 必须是 -1 或 1");
        Snapshot s = value.get();
        int target;
        if (!extend && s.anchor != s.caret)
            target = direction < 0 ? Math.min(s.anchor, s.caret) : Math.max(s.anchor, s.caret);
        else target = adjacent(s.text, s.caret, direction);
        select(extend ? s.anchor : target, target);
    }

    public void home(boolean extend) {
        domain.checkWrite();
        Snapshot s = value.get();
        select(extend ? s.anchor : 0, 0);
    }

    public void end(boolean extend) {
        domain.checkWrite();
        Snapshot s = value.get();
        int end = s.text.length();
        select(extend ? s.anchor : end, end);
    }

    /** 普通连续输入；替换已有选区自成一步。 */
    public void replaceSelection(String text) { replaceSelection(text, true); }

    /** group=false 用于粘贴、剪切、模板插入及一次 IME 提交，各自自成一步。 */
    public void replaceSelection(String text, boolean group) {
        domain.checkWrite();
        String insertion = normalize(text);
        Snapshot s = value.get();
        if (!group) groupKind = Kind.NONE;
        int start = Math.min(s.anchor, s.caret), end = Math.max(s.anchor, s.caret);
        replace(s, start, end, insertion, group && start == end ? Kind.INPUT : Kind.NONE);
        if (!group) groupKind = Kind.NONE;
    }

    public void deleteBackward() { delete(-1); }
    public void deleteForward() { delete(1); }

    private void delete(int direction) {
        domain.checkWrite();
        Snapshot s = value.get();
        int start = Math.min(s.anchor, s.caret), end = Math.max(s.anchor, s.caret);
        Kind kind = Kind.NONE;
        if (start == end) {
            if (direction < 0) { start = adjacent(s.text, start, -1); kind = Kind.BACKWARD; }
            else { end = adjacent(s.text, end, 1); kind = Kind.FORWARD; }
        }
        replace(s, start, end, "", kind);
    }

    private void replace(Snapshot s, int start, int end, String insertion, Kind kind) {
        String next = s.text.substring(0, start) + insertion + s.text.substring(end);
        if (next.equals(s.text)) {
            if (kind == Kind.NONE || kind != groupKind) groupKind = Kind.NONE;
            return;
        }
        // 插入组合符或 ZWJ 后，插入尾部可能与后文合簇，光标向右对齐完整新簇。
        int caret = ceiling(boundaries(next), start + insertion.length());
        record(new Position(s), new Position(next, caret, caret), kind);
        if (!s.active) generation++;
        value.set(new Snapshot(next, "", null, caret, caret, true, false, s.revision + 1));
    }

    private void record(Position before, Position after, Kind kind) {
        while (history.size() > historyPosition) history.remove(history.size() - 1);
        if (kind != Kind.NONE && groupKind == kind && historyPosition > 0
                && history.get(historyPosition - 1).after.matches(before)) {
            history.get(historyPosition - 1).after = after;
        } else {
            history.add(new Change(before, after));
            historyPosition++;
            if (history.size() > HISTORY_LIMIT) { history.remove(0); historyPosition--; }
        }
        groupKind = kind;
    }

    public void undo() {
        domain.checkWrite();
        groupKind = Kind.NONE;
        if (historyPosition > 0) restore(history.get(--historyPosition).before);
    }

    public void redo() {
        domain.checkWrite();
        groupKind = Kind.NONE;
        if (historyPosition < history.size()) restore(history.get(historyPosition++).after);
    }

    private void restore(Position position) {
        Snapshot s = value.get();
        if (!s.active) generation++;
        value.set(new Snapshot(position.text, "", null, position.anchor, position.caret,
                true, false, s.revision + 1));
    }

    public void breakHistoryGroup() { domain.checkWrite(); groupKind = Kind.NONE; }

    /** 取消组字只清除 preedit，不取消草稿会话；提交文本使用 replaceSelection(text,false)。 */
    public void composition(String text, boolean composing) {
        domain.checkWrite();
        String preedit = composing ? normalize(text) : "";
        Snapshot s = value.get();
        groupKind = Kind.NONE;
        if (composing && !s.composing) generation++; // 新组字也不能被此前请求的完成结果清掉。
        if (!preedit.equals(s.composition) || composing != s.composing)
            value.set(new Snapshot(s.text, preedit, s.error, s.anchor, s.caret,
                    s.active || composing, composing, s.revision));
    }

    public void reset(String text) {
        domain.checkWrite();
        resetBaseline(normalize(text));
    }

    public void cancel() { domain.checkWrite(); resetBaseline(baseline); }

    private void resetBaseline(String text) {
        Snapshot s = value.get();
        baseline = text;
        generation++;
        latest = null;
        history.clear(); historyPosition = 0; groupKind = Kind.NONE;
        int end = text.length();
        value.set(new Snapshot(text, "", null, end, end, false, false, s.revision + 1));
    }

    public void synchronizeExternal(String text) {
        domain.checkWrite();
        String next = normalize(text);
        Snapshot s = value.get();
        if (!s.active && !next.equals(baseline)) resetBaseline(next);
    }

    public Submission request(boolean complete) {
        domain.checkWrite();
        if (complete) groupKind = Kind.NONE;
        Snapshot s = value.get();
        Submission submission = new Submission(this, s, complete);
        latest = submission;
        if (s.error != null)
            value.set(new Snapshot(s.text, s.composition, null, s.anchor, s.caret, s.active, s.composing, s.revision));
        return submission;
    }

    /** 返回是否影响当前草稿；旧请求仍记录结果，重复 resolve 返回 false 且不改任何结果。 */
    public boolean accept(Submission submission, String normalized) {
        domain.checkWrite();
        requireOwn(submission);
        if (submission.resolved) return false;
        String next = normalize(normalized);
        Snapshot s = value.get();
        boolean current = isCurrent(submission, s);
        // 先占用一次性完成权；Snapshot 失效通知可同步重入，不能重复改写业务结果。
        submission.resolved = true;
        if (current) {
            boolean changed = !next.equals(s.text);
            // 同文本确认保留当前选区；不同文本只夹取当前选区并向左对齐，不恢复请求时旧选区。
            int[] bounds = changed ? boundaries(next) : null;
            int anchor = changed ? floor(bounds, s.anchor) : s.anchor;
            int caret = changed ? floor(bounds, s.caret) : s.caret;
            if (changed) normalizeHistory(s, new Position(next, anchor, caret));
            baseline = next;
            value.set(new Snapshot(next, submission.complete ? "" : s.composition, null, anchor, caret,
                    !submission.complete, !submission.complete && s.composing, s.revision + (changed ? 1 : 0)));
        }
        submission.result.set(new Result(Status.ACCEPTED, next, null));
        return current;
    }

    private void normalizeHistory(Snapshot s, Position after) {
        groupKind = Kind.NONE;
        if (historyPosition == history.size() && historyPosition > 0
                && history.get(historyPosition - 1).after.text.equals(s.text)) {
            Change last = history.get(historyPosition - 1);
            last.after = after;
            // 例如输入多余空格又被规范化删掉，不留下只会再次触发同一修正的空步骤。
            if (last.before.text.equals(after.text)) { history.remove(--historyPosition); }
        } else record(new Position(s), after, Kind.NONE);
    }

    public boolean reject(Submission submission, String reason) {
        domain.checkWrite();
        requireOwn(submission);
        if (submission.resolved) return false;
        Objects.requireNonNull(reason, "reason");
        Snapshot s = value.get();
        boolean current = isCurrent(submission, s);
        // 先占用一次性完成权；Snapshot 失效通知可同步重入，不能重复改写业务结果。
        submission.resolved = true;
        if (current)
            value.set(new Snapshot(s.text, s.composition, reason, s.anchor, s.caret, s.active, s.composing, s.revision));
        submission.result.set(new Result(Status.REJECTED, submission.text, reason));
        return current;
    }

    private void requireOwn(Submission submission) {
        if (Objects.requireNonNull(submission, "submission").state != this)
            throw new IllegalArgumentException("提交请求不属于此编辑状态");
    }

    private boolean isCurrent(Submission submission, Snapshot s) {
        return latest == submission && submission.revision == s.revision
                && submission.generation == generation && submission.text.equals(s.text);
    }

    /** 所有文本入口共用固定模式的换行规则；多行保留末尾 LF。 */
    private String normalize(String text) {
        Objects.requireNonNull(text, "text");
        return mode == Mode.MULTI_LINE
                ? text.replace("\r\n", "\n").replace("\r", "\n")
                : text.replace("\r", "").replace("\n", "");
    }

    /** Java8 字符边界加扩展规则；不依赖字体、桌面工具箱或宿主字符事件。 */
    private static int[] boundaries(String text) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        boolean[] legal = new boolean[text.length() + 1];
        for (int p = iterator.first(); p != BreakIterator.DONE; p = iterator.next()) legal[p] = true;
        int regionalRun = 0;
        for (int p = 0; p < text.length();) {
            int cp = text.codePointAt(p);
            if (p > 0) {
                int previous = text.codePointBefore(p);
                // Java8 BreakIterator 会把普通 a ZWJ b 也连起来；GB11 不成立时恢复此边界。
                if (previous == 0x200D) legal[p] = !joinsPictographs(text, p, cp);
                if (extend(cp) || cp == 0x200D
                        || regional(cp) && regional(previous) && (regionalRun & 1) != 0)
                    legal[p] = false;
            }
            regionalRun = regional(cp) ? regionalRun + 1 : 0;
            int next = p + Character.charCount(cp);
            for (int middle = p + 1; middle < next; middle++) legal[middle] = false;
            p = next;
        }
        legal[0] = true; legal[text.length()] = true;
        int[] result = new int[legal.length];
        int count = 0;
        for (int p = 0; p < legal.length; p++) if (legal[p]) result[count++] = p;
        return Arrays.copyOf(result, count);
    }

    /** GB11 仅连接 Extended_Pictographic Extend* ZWJ Extended_Pictographic。 */
    private static boolean joinsPictographs(String text, int position, int cp) {
        if (!UCharacter.hasBinaryProperty(cp, UProperty.EXTENDED_PICTOGRAPHIC)) return false;
        int p = position - 1; // 调用处已确认前一个码点是单个 UTF-16 单元的 ZWJ。
        while (p > 0) {
            int before = text.codePointBefore(p);
            if (UCharacter.getIntPropertyValue(before, UProperty.GRAPHEME_CLUSTER_BREAK)
                    != UCharacter.GraphemeClusterBreak.EXTEND)
                return UCharacter.hasBinaryProperty(before, UProperty.EXTENDED_PICTOGRAPHIC);
            p -= Character.charCount(before);
        }
        return false;
    }

    private static boolean extend(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK || cp >= 0xFE00 && cp <= 0xFE0F
                || cp >= 0xE0100 && cp <= 0xE01EF || cp >= 0x1F3FB && cp <= 0x1F3FF
                || cp >= 0xE0020 && cp <= 0xE007F; // emoji 标签序列也附着于前一字符。
    }

    private static boolean regional(int cp) { return cp >= 0x1F1E6 && cp <= 0x1F1FF; }

    private static int floor(int[] boundaries, int position) {
        int p = Math.max(0, Math.min(position, boundaries[boundaries.length - 1]));
        int found = Arrays.binarySearch(boundaries, p);
        return found >= 0 ? p : boundaries[-found - 2];
    }

    private static int ceiling(int[] boundaries, int position) {
        int p = Math.max(0, Math.min(position, boundaries[boundaries.length - 1]));
        int found = Arrays.binarySearch(boundaries, p);
        return found >= 0 ? p : boundaries[-found - 1];
    }

    private static int adjacent(String text, int position, int direction) {
        int[] bounds = boundaries(text);
        int index = Arrays.binarySearch(bounds, position);
        return bounds[Math.max(0, Math.min(index + direction, bounds.length - 1))];
    }
}
