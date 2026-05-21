package club.heiqi.uilib.ui.render;

import java.util.List;

/**
 * 当前有效裁剪状态快照，供延迟回放阶段复用。
 */
public final class ClipSnapshot {

    private final int[] clipRect;
    private final List<RoundedClipRegion> roundedClipRegions;

    ClipSnapshot(int[] clipRect, List<RoundedClipRegion> roundedClipRegions) {
        this.clipRect = clipRect;
        this.roundedClipRegions = roundedClipRegions;
    }

    public int[] getClipRect() {
        if (clipRect == null) {
            return null;
        }
        return new int[] { clipRect[0], clipRect[1], clipRect[2], clipRect[3] };
    }

    public List<RoundedClipRegion> getRoundedClipRegions() {
        return roundedClipRegions;
    }
}
