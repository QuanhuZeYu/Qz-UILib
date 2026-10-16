package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 同一轴映射服务绘制、命中、抓取与键盘，领域控件的内部坐标不泄漏到条。 */
final class SceneScrollbarGeometry {
    final LayoutBox track, thumb, clip;
    final SceneScrollViewport.Snapshot view;
    private final boolean horizontal, forced;
    private final int start, thumbStart, length, travel;
    SceneScrollbarGeometry(SceneGeometryView.Entry bar, SceneScrollViewport.Snapshot view,
            SceneNode.Axis axis, SceneScrollbar.Options options) {
        this.view = view; horizontal = axis == SceneNode.Axis.ROW; forced = options.forceVisible;
        track = bar == null ? new LayoutBox(0, 0, 0, 0) : bar.box; clip = bar == null ? null : bar.clip;
        start = (horizontal ? track.x : track.y) + options.inset;
        int available = Math.max(0, (horizontal ? track.width : track.height) - options.inset * 2);
        length = thumbLength(view, available, options.minimumThumb);
        travel = available - length;
        thumbStart = start + (view == null || view.maximum == 0 ? 0 : (int) Math.round((double) view.offset * travel / view.maximum));
        int cross = Math.max(0, (horizontal ? track.height : track.width) - options.inset * 2);
        thumb = horizontal ? new LayoutBox(thumbStart, track.y + options.inset, length, cross)
                : new LayoutBox(track.x + options.inset, thumbStart, cross, length);
    }
    private static int thumbLength(SceneScrollViewport.Snapshot view, int available, int minimum) {
        if (view == null || view.maximum == 0 || view.extent == 0) return 0;
        return Math.min(available, Math.max(minimum, (int) ((long) available * view.extent / ((long) view.extent + view.maximum))));
    }
    boolean scrollable() { return view != null && view.maximum > 0 && length > 0 && thumb.width > 0 && thumb.height > 0; }
    boolean visible() { return view != null && track.width > 0 && track.height > 0 && (view.maximum > 0 || forced); }
    boolean contains(int x, int y) { return visible() && track.contains(x, y) && (clip == null || clip.contains(x, y)); }
    boolean onThumb(int coordinate) { return coordinate >= thumbStart && coordinate < thumbStart + length; }
    int coordinate(int x, int y) { return horizontal ? x : y; }
    int grab(int coordinate) { return coordinate - thumbStart; }
    int direction(int coordinate) { return coordinate < thumbStart ? -1 : 1; }
    int offset(int coordinate, int grab) {
        return travel <= 0 ? view.offset : (int) Math.round(Math.max(0d, Math.min(travel, (double) coordinate - grab - start)) * view.maximum / travel);
    }
    Integer command(SceneEditEvent.Command command, int step) {
        if (view == null || command == null) return null;
        switch (command) {
            case LEFT: return horizontal ? -step : null;
            case RIGHT: return horizontal ? step : null;
            case UP: return horizontal ? null : -step;
            case DOWN: return horizontal ? null : step;
            case PAGE_UP: return -view.extent;
            case PAGE_DOWN: return view.extent;
            case HOME: case DOCUMENT_HOME: return -view.offset;
            case END: case DOCUMENT_END: return view.maximum - view.offset;
            default: return null;
        }
    }
}
