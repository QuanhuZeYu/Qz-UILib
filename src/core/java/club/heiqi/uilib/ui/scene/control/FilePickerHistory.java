package club.heiqi.uilib.ui.scene.control;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Locale;

/** 有限浏览位置及转移；不读文件系统，不发布选择或编辑状态。 */
final class FilePickerHistory {
    static final class Location {
        final Path directory, selected;
        final boolean roots;
        final FilePickerQuery query;
        final int scroll;
        Location(Path directory, boolean roots, FilePickerQuery query, Path selected, int scroll) {
            this.directory = directory; this.roots = roots; this.query = query; this.selected = selected; this.scroll = scroll;
        }
    }
    private final ArrayDeque<Location> back = new ArrayDeque<>(), forward = new ArrayDeque<>();
    private Location current, leaving, restoring, refreshing;
    private int direction;

    void capture(Path selected, int scroll) { leaving = snapshot(selected, scroll); }
    Location snapshot(Path selected, int scroll) {
        return current == null ? null : new Location(current.directory, current.roots, current.query, selected, scroll);
    }
    FilePickerQuery query() { return current.query; }
    boolean sameQuery(String text, String extension, FilePickerOrder order) {
        return current != null && current.query.text.equals(text.trim().toLowerCase(Locale.ROOT))
                && current.query.extensions.equals(extension) && current.query.order == order;
    }
    void beginNavigation() { cancelRestore(); refreshing = null; }
    void cancelRestore() { restoring = null; direction = 0; }
    void refresh(Location keep) { refreshing = keep; }

    Location request(int direction) {
        ArrayDeque<Location> source = direction < 0 ? back : forward;
        if (source.isEmpty()) return null;
        restoring = source.peekLast(); this.direction = direction; return restoring;
    }

    Location acceptListing(FilePickerDirectory.Result result) {
        Location restore = refreshing; refreshing = null;
        if (restoring != null) {
            restore = restoreHistory();
        } else if (leftDirectory(result)) {
            back.addLast(leaving); forward.clear();
        }
        trim();
        return restore;
    }

    private Location restoreHistory() {
        ArrayDeque<Location> source = direction < 0 ? back : forward, target = direction < 0 ? forward : back;
        source.pollLast();
        if (leaving != null) target.addLast(leaving);
        Location result = restoring; cancelRestore(); return result;
    }

    private boolean leftDirectory(FilePickerDirectory.Result result) {
        return leaving != null && (leaving.roots != result.roots
                || result.directory != null && !leaving.directory.equals(result.directory));
    }
    private void trim() {
        while (back.size() + forward.size() > 64) {
            if (!back.isEmpty()) back.removeFirst(); else forward.removeFirst();
        }
    }
    int backCount() { return back.size(); }
    int forwardCount() { return forward.size(); }
    void current(Path directory, boolean roots, FilePickerQuery query, Path selected, int scroll) {
        current = new Location(directory, roots, query, selected, scroll);
    }
}
