package club.heiqi.uilib.ui.render;

import java.util.List;

/**
 * 主层快照的采样区域、tile 覆盖和滤镜尺寸计算工具。
 */
final class UiMainLayerSnapshotGeometry {

    static final int SNAPSHOT_BLOCK_SIZE = 128;
    private static final int MAX_SNAPSHOT_EDGE = 4096;
    private static final int MAX_SNAPSHOT_PIXELS = 4096 * 4096;
    static final int MAX_DOWNSAMPLE_FACTOR = 4;
    private static final int MEDIUM_BLUR_DOWNSAMPLE_THRESHOLD = 18;
    private static final int LARGE_BLUR_DOWNSAMPLE_THRESHOLD = 34;

    private UiMainLayerSnapshotGeometry() {}

    static SampleRegion resolveSampleRegion(int screenWidth, int screenHeight, int left, int top, int right,
            int bottom, int blurRadius) {
        int sampleInset = Math.max(1, Math.min(64, blurRadius + resolveSampleStep(blurRadius)));
        int sampleLeft = clampInt(left - sampleInset, 0, screenWidth);
        int sampleTop = clampInt(top - sampleInset, 0, screenHeight);
        int sampleRight = clampInt(right + sampleInset, 0, screenWidth);
        int sampleBottom = clampInt(bottom + sampleInset, 0, screenHeight);
        if (sampleRight <= sampleLeft || sampleBottom <= sampleTop) {
            return null;
        }
        return new SampleRegion(sampleLeft, sampleTop, sampleRight, sampleBottom);
    }

    static SampleRegion resolveBlockAlignedSampleRegion(int screenWidth, int screenHeight, SampleRegion sampleRegion) {
        if (sampleRegion == null) {
            return null;
        }
        int sampleLeft = alignDown(sampleRegion.getLeft(), SNAPSHOT_BLOCK_SIZE);
        int sampleTop = alignDown(sampleRegion.getTop(), SNAPSHOT_BLOCK_SIZE);
        int sampleRight = alignUp(sampleRegion.getRight(), SNAPSHOT_BLOCK_SIZE);
        int sampleBottom = alignUp(sampleRegion.getBottom(), SNAPSHOT_BLOCK_SIZE);
        int alignedLeft = clampInt(sampleLeft, 0, screenWidth);
        int alignedTop = clampInt(sampleTop, 0, screenHeight);
        int alignedRight = clampInt(sampleRight, 0, screenWidth);
        int alignedBottom = clampInt(sampleBottom, 0, screenHeight);
        if (alignedRight <= alignedLeft || alignedBottom <= alignedTop) {
            return sampleRegion;
        }
        return new SampleRegion(alignedLeft, alignedTop, alignedRight, alignedBottom);
    }

    static boolean isSnapshotSizeAllowed(int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (width > MAX_SNAPSHOT_EDGE || height > MAX_SNAPSHOT_EDGE) {
            return false;
        }
        return (long) width * (long) height <= MAX_SNAPSHOT_PIXELS;
    }

    static boolean isSnapshotSizeAllowed(SampleRegion sampleRegion) {
        return sampleRegion != null && isSnapshotSizeAllowed(sampleRegion.getWidth(), sampleRegion.getHeight());
    }

    static int resolveCopySourceY(int screenHeight, SampleRegion sampleRegion) {
        if (sampleRegion == null) {
            return 0;
        }
        return screenHeight - sampleRegion.getBottom();
    }

    static int resolveDownsampleFactor(int blurRadius) {
        if (blurRadius < MEDIUM_BLUR_DOWNSAMPLE_THRESHOLD) {
            return 1;
        }
        if (blurRadius < LARGE_BLUR_DOWNSAMPLE_THRESHOLD) {
            return 2;
        }
        return MAX_DOWNSAMPLE_FACTOR;
    }

    static int resolveDownsampledSize(int sourceSize, int downsampleFactor) {
        int safeFactor = Math.max(1, Math.min(MAX_DOWNSAMPLE_FACTOR, downsampleFactor));
        return Math.max(1, (Math.max(1, sourceSize) + safeFactor - 1) / safeFactor);
    }

    static int resolveFilterPassRadius(int blurRadius, int downsampleFactor) {
        if (blurRadius <= 0 || downsampleFactor <= 1) {
            return 0;
        }
        return Math.max(1, Math.min(8, Math.round((float) blurRadius / (float) (downsampleFactor * 5))));
    }

    static TileRegion resolveTileRegion(SampleRegion sampleRegion) {
        if (sampleRegion == null || sampleRegion.getRight() <= sampleRegion.getLeft()
                || sampleRegion.getBottom() <= sampleRegion.getTop()) {
            return new TileRegion(0, 0, 0, 0);
        }
        return new TileRegion(sampleRegion.getLeft() / SNAPSHOT_BLOCK_SIZE,
                sampleRegion.getTop() / SNAPSHOT_BLOCK_SIZE,
                alignUp(sampleRegion.getRight(), SNAPSHOT_BLOCK_SIZE) / SNAPSHOT_BLOCK_SIZE,
                alignUp(sampleRegion.getBottom(), SNAPSHOT_BLOCK_SIZE) / SNAPSHOT_BLOCK_SIZE);
    }

    static int resolveTileCount(SampleRegion sampleRegion) {
        return resolveTileRegion(sampleRegion).getTileCount();
    }

    static SampleRegion resolveTileSampleRegion(SampleRegion sampleRegion, int tileLeft, int tileTop,
            int tileRight, int tileBottom) {
        if (sampleRegion == null || tileRight <= tileLeft || tileBottom <= tileTop) {
            return null;
        }
        int left = Math.max(sampleRegion.getLeft(), tileLeft * SNAPSHOT_BLOCK_SIZE);
        int top = Math.max(sampleRegion.getTop(), tileTop * SNAPSHOT_BLOCK_SIZE);
        int right = Math.min(sampleRegion.getRight(), tileRight * SNAPSHOT_BLOCK_SIZE);
        int bottom = Math.min(sampleRegion.getBottom(), tileBottom * SNAPSHOT_BLOCK_SIZE);
        if (right <= left || bottom <= top) {
            return null;
        }
        return new SampleRegion(left, top, right, bottom);
    }

    static int resolveTextureCopyTargetY(SampleRegion atlasRegion, SampleRegion copiedRegion) {
        if (atlasRegion == null || copiedRegion == null) {
            return 0;
        }
        return Math.max(0, atlasRegion.getBottom() - copiedRegion.getBottom());
    }

    static TileCoveragePlan resolveTileCoverage(TileRegion requestedTileRegion, List<TileRegion> coveredTileRegions) {
        if (requestedTileRegion == null || requestedTileRegion.getTileCount() <= 0) {
            return new TileCoveragePlan(new TileRegion(0, 0, 0, 0), 0);
        }
        boolean[] coveredTiles = new boolean[requestedTileRegion.getTileCount()];
        int coveredTileCount = 0;
        if (coveredTileRegions != null) {
            for (TileRegion coveredTileRegion : coveredTileRegions) {
                if (coveredTileRegion == null || coveredTileRegion.getTileCount() <= 0) {
                    continue;
                }
                int overlapLeft = Math.max(requestedTileRegion.getTileLeft(), coveredTileRegion.getTileLeft());
                int overlapTop = Math.max(requestedTileRegion.getTileTop(), coveredTileRegion.getTileTop());
                int overlapRight = Math.min(requestedTileRegion.getTileRight(), coveredTileRegion.getTileRight());
                int overlapBottom = Math.min(requestedTileRegion.getTileBottom(), coveredTileRegion.getTileBottom());
                if (overlapRight <= overlapLeft || overlapBottom <= overlapTop) {
                    continue;
                }
                for (int tileY = overlapTop; tileY < overlapBottom; tileY++) {
                    for (int tileX = overlapLeft; tileX < overlapRight; tileX++) {
                        int localX = tileX - requestedTileRegion.getTileLeft();
                        int localY = tileY - requestedTileRegion.getTileTop();
                        int tileIndex = localY * requestedTileRegion.getTileWidth() + localX;
                        if (!coveredTiles[tileIndex]) {
                            coveredTiles[tileIndex] = true;
                            coveredTileCount++;
                        }
                    }
                }
            }
        }
        return new TileCoveragePlan(requestedTileRegion, coveredTileCount, coveredTiles);
    }

    static boolean containsSampleRegion(SampleRegion outerRegion, SampleRegion innerRegion) {
        return outerRegion != null && innerRegion != null
                && outerRegion.getLeft() <= innerRegion.getLeft()
                && outerRegion.getTop() <= innerRegion.getTop()
                && outerRegion.getRight() >= innerRegion.getRight()
                && outerRegion.getBottom() >= innerRegion.getBottom();
    }

    static boolean isSameSampleRegion(SampleRegion firstRegion, SampleRegion secondRegion) {
        return firstRegion != null && secondRegion != null
                && firstRegion.getLeft() == secondRegion.getLeft()
                && firstRegion.getTop() == secondRegion.getTop()
                && firstRegion.getRight() == secondRegion.getRight()
                && firstRegion.getBottom() == secondRegion.getBottom();
    }

    static SampleRegion resolveFullScreenSampleRegion(int screenWidth, int screenHeight) {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return null;
        }
        return new SampleRegion(0, 0, screenWidth, screenHeight);
    }

    static boolean isSnapshotRegionWithinScreen(int screenWidth, int screenHeight, SampleRegion sampleRegion) {
        return screenWidth > 0 && screenHeight > 0
                && sampleRegion.getLeft() >= 0 && sampleRegion.getTop() >= 0
                && sampleRegion.getRight() <= screenWidth && sampleRegion.getBottom() <= screenHeight
                && sampleRegion.getRight() > sampleRegion.getLeft()
                && sampleRegion.getBottom() > sampleRegion.getTop();
    }

    static SampleRegion resolveReusableSampleRegion(int screenWidth, int screenHeight, SampleRegion sampleRegion) {
        SampleRegion blockRegion = resolveBlockAlignedSampleRegion(screenWidth, screenHeight, sampleRegion);
        if (isSnapshotSizeAllowed(blockRegion)) {
            return blockRegion;
        }
        return sampleRegion;
    }

    static String formatRegionDetail(SampleRegion requestedRegion, SampleRegion reusableRegion) {
        if (requestedRegion == null || reusableRegion == null) {
            return "exact";
        }
        if (requestedRegion.getLeft() == reusableRegion.getLeft() && requestedRegion.getTop() == reusableRegion.getTop()
                && requestedRegion.getRight() == reusableRegion.getRight()
                && requestedRegion.getBottom() == reusableRegion.getBottom()) {
            return "exact";
        }
        return "block" + SNAPSHOT_BLOCK_SIZE;
    }

    static String formatAtlasRegionDetail(String regionDetail) {
        if (regionDetail == null || regionDetail.isEmpty()) {
            return "atlas";
        }
        if (regionDetail.startsWith("atlas-")) {
            return regionDetail;
        }
        return "atlas-" + regionDetail;
    }

    static String formatTileAtlasRegionDetail(String regionDetail) {
        String atlasRegionDetail = formatAtlasRegionDetail(regionDetail);
        if (atlasRegionDetail.startsWith("tile-")) {
            return atlasRegionDetail;
        }
        return "tile-" + atlasRegionDetail;
    }

    static String formatTileDetail(TileCoveragePlan tileCoveragePlan, int reusedTileCount, int copiedTileCount) {
        TileCoveragePlan safePlan = tileCoveragePlan == null ? new TileCoveragePlan(new TileRegion(0, 0, 0, 0), 0)
                : tileCoveragePlan;
        return "tiles=" + safePlan.getTileCount() + " covered=" + safePlan.getCoveredTileCount()
                + " missing=" + safePlan.getMissingTileCount() + " reused=" + Math.max(0, reusedTileCount)
                + " copied=" + Math.max(0, copiedTileCount);
    }

    private static int resolveSampleStep(int blurRadius) {
        return Math.max(1, Math.min(12, Math.round(Math.max(1, blurRadius) / 2.5F)));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static int alignDown(int value, int blockSize) {
        if (blockSize <= 0) {
            return value;
        }
        return value / blockSize * blockSize;
    }

    private static int alignUp(int value, int blockSize) {
        if (blockSize <= 0) {
            return value;
        }
        return (value + blockSize - 1) / blockSize * blockSize;
    }
}
