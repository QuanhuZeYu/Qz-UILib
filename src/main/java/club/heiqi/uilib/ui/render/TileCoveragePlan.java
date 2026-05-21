package club.heiqi.uilib.ui.render;

/**
 * 单次 backdrop 请求的 tile 覆盖计划。
 */
final class TileCoveragePlan {

    private final TileRegion requestedTileRegion;
    private final int coveredTileCount;
    private final boolean[] coveredTiles;

    TileCoveragePlan(TileRegion requestedTileRegion, int coveredTileCount) {
        this(requestedTileRegion, coveredTileCount, null);
    }

    TileCoveragePlan(TileRegion requestedTileRegion, int coveredTileCount, boolean[] coveredTiles) {
        this.requestedTileRegion = requestedTileRegion == null ? new TileRegion(0, 0, 0, 0) : requestedTileRegion;
        this.coveredTiles = new boolean[this.requestedTileRegion.getTileCount()];
        int actualCoveredTileCount = 0;
        if (coveredTiles != null) {
            int copyLength = Math.min(coveredTiles.length, this.coveredTiles.length);
            for (int index = 0; index < copyLength; index++) {
                this.coveredTiles[index] = coveredTiles[index];
                if (this.coveredTiles[index]) {
                    actualCoveredTileCount++;
                }
            }
        } else {
            actualCoveredTileCount = coveredTileCount;
        }
        this.coveredTileCount = clampInt(actualCoveredTileCount, 0, this.requestedTileRegion.getTileCount());
    }

    TileRegion getRequestedTileRegion() {
        return requestedTileRegion;
    }

    int getTileCount() {
        return requestedTileRegion.getTileCount();
    }

    int getCoveredTileCount() {
        return coveredTileCount;
    }

    int getMissingTileCount() {
        return getTileCount() - coveredTileCount;
    }

    boolean isTileCovered(int tileX, int tileY) {
        int localX = tileX - requestedTileRegion.getTileLeft();
        int localY = tileY - requestedTileRegion.getTileTop();
        if (localX < 0 || localY < 0 || localX >= requestedTileRegion.getTileWidth()
                || localY >= requestedTileRegion.getTileHeight()) {
            return false;
        }
        int tileIndex = localY * requestedTileRegion.getTileWidth() + localX;
        return tileIndex >= 0 && tileIndex < coveredTiles.length && coveredTiles[tileIndex];
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
