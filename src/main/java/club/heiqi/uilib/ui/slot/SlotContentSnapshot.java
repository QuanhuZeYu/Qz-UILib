package club.heiqi.uilib.ui.slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.image.HostImageSource;

/**
 * 通用槽位内容快照。
 *
 * <p>该类型只描述“槽位里现在有什么”，不绑定背包、配方、流体槽等具体业务语义。</p>
 */
public final class SlotContentSnapshot {

    private static final SlotContentSnapshot EMPTY = new Builder()
            .setOccupied(false)
            .setContentKind("empty")
            .buildInternal();

    private final boolean occupied;
    private final String contentKind;
    private final HostImageSource visualSource;
    private final String displayName;
    private final int primaryCount;
    private final String overlayText;
    private final List<String> tooltipLines;
    private final String accessibilityLabel;

    private SlotContentSnapshot(boolean occupied, String contentKind, HostImageSource visualSource,
            String displayName, int primaryCount, String overlayText, List<String> tooltipLines,
            String accessibilityLabel) {
        this.occupied = occupied;
        this.contentKind = contentKind;
        this.visualSource = visualSource;
        this.displayName = displayName;
        this.primaryCount = primaryCount;
        this.overlayText = overlayText;
        this.tooltipLines = tooltipLines;
        this.accessibilityLabel = accessibilityLabel;
    }

    /**
     * 返回空槽位快照。
     *
     * @return 空槽位快照
     */
    public static SlotContentSnapshot empty() {
        return EMPTY;
    }

    /**
     * 创建构建器。
     *
     * @return 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 便捷创建一个已占用槽位快照。
     *
     * @param contentKind 内容类型标识
     * @param visualSource 视觉内容源
     * @param displayName 显示名
     * @return 已占用槽位快照
     */
    public static SlotContentSnapshot occupied(String contentKind, HostImageSource visualSource,
            String displayName) {
        return builder()
                .setOccupied(true)
                .setContentKind(contentKind)
                .setVisualSource(visualSource)
                .setDisplayName(displayName)
                .build();
    }

    /**
     * 判断槽位是否占用。
     *
     * @return 是否占用
     */
    public boolean isOccupied() {
        return occupied;
    }

    /**
     * 返回内容类型标识。
     *
     * @return 内容类型标识
     */
    public String getContentKind() {
        return contentKind;
    }

    /**
     * 返回槽位视觉内容源。
     *
     * @return 视觉内容源；没有可绘制视觉时返回 null
     */
    public HostImageSource getVisualSource() {
        return visualSource;
    }

    /**
     * 返回内容显示名。
     *
     * @return 显示名
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 返回主数量值。
     *
     * @return 主数量值
     */
    public int getPrimaryCount() {
        return primaryCount;
    }

    /**
     * 返回叠加文本。
     *
     * @return 叠加文本
     */
    public String getOverlayText() {
        return overlayText;
    }

    /**
     * 返回 tooltip 文本行。
     *
     * @return 只读 tooltip 文本行
     */
    public List<String> getTooltipLines() {
        return tooltipLines;
    }

    /**
     * 返回显式无障碍标签正文。
     *
     * @return 标签正文；为空时由调用方按显示名和数量生成
     */
    public String getAccessibilityLabel() {
        return accessibilityLabel;
    }

    /**
     * 生成当前槽位的无障碍描述。
     *
     * @param slotLabel 槽位名称，例如“槽位 1”
     * @return 无障碍描述
     */
    public String describeForAria(String slotLabel) {
        String resolvedSlotLabel = normalizeText(slotLabel);
        if (!occupied) {
            return resolvedSlotLabel + "，空";
        }
        if (!accessibilityLabel.isEmpty()) {
            return resolvedSlotLabel + "，" + accessibilityLabel;
        }
        if (displayName.isEmpty()) {
            return resolvedSlotLabel + "，已占用";
        }
        if (primaryCount > 1) {
            return resolvedSlotLabel + "，" + displayName + "，数量 " + primaryCount;
        }
        return resolvedSlotLabel + "，" + displayName;
    }

    /**
     * 槽位内容快照构建器。
     */
    public static final class Builder {

        private boolean occupied;
        private String contentKind = "generic";
        private HostImageSource visualSource;
        private String displayName = "";
        private int primaryCount;
        private String overlayText = "";
        private List<String> tooltipLines = Collections.emptyList();
        private String accessibilityLabel = "";

        /**
         * 设置占用状态。
         *
         * @param occupied 是否占用
         * @return 当前构建器
         */
        public Builder setOccupied(boolean occupied) {
            this.occupied = occupied;
            return this;
        }

        /**
         * 设置内容类型标识。
         *
         * @param contentKind 内容类型标识
         * @return 当前构建器
         */
        public Builder setContentKind(String contentKind) {
            this.contentKind = normalizeKind(contentKind, occupied ? "generic" : "empty");
            return this;
        }

        /**
         * 设置视觉内容源。
         *
         * @param visualSource 视觉内容源
         * @return 当前构建器
         */
        public Builder setVisualSource(HostImageSource visualSource) {
            this.visualSource = visualSource;
            return this;
        }

        /**
         * 设置显示名。
         *
         * @param displayName 显示名
         * @return 当前构建器
         */
        public Builder setDisplayName(String displayName) {
            this.displayName = normalizeText(displayName);
            return this;
        }

        /**
         * 设置主数量值。
         *
         * @param primaryCount 主数量值
         * @return 当前构建器
         */
        public Builder setPrimaryCount(int primaryCount) {
            this.primaryCount = Math.max(0, primaryCount);
            return this;
        }

        /**
         * 设置叠加文本。
         *
         * @param overlayText 叠加文本
         * @return 当前构建器
         */
        public Builder setOverlayText(String overlayText) {
            this.overlayText = normalizeText(overlayText);
            return this;
        }

        /**
         * 设置 tooltip 文本行。
         *
         * @param tooltipLines tooltip 文本行
         * @return 当前构建器
         */
        public Builder setTooltipLines(List<String> tooltipLines) {
            this.tooltipLines = copyLines(tooltipLines);
            return this;
        }

        /**
         * 设置显式无障碍标签正文。
         *
         * @param accessibilityLabel 无障碍标签正文
         * @return 当前构建器
         */
        public Builder setAccessibilityLabel(String accessibilityLabel) {
            this.accessibilityLabel = normalizeText(accessibilityLabel);
            return this;
        }

        /**
         * 构建快照。
         *
         * @return 槽位内容快照
         */
        public SlotContentSnapshot build() {
            if (!occupied && visualSource == null && displayName.isEmpty() && primaryCount <= 0
                    && overlayText.isEmpty() && tooltipLines.isEmpty() && accessibilityLabel.isEmpty()) {
                return EMPTY;
            }
            return buildInternal();
        }

        private SlotContentSnapshot buildInternal() {
            String normalizedKind = normalizeKind(contentKind, occupied ? "generic" : "empty");
            return new SlotContentSnapshot(occupied, normalizedKind, visualSource, normalizeText(displayName),
                    Math.max(0, primaryCount), normalizeText(overlayText), copyLines(tooltipLines),
                    normalizeText(accessibilityLabel));
        }

        private static List<String> copyLines(List<String> lines) {
            if (lines == null || lines.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> copied = new ArrayList<String>(lines.size());
            for (String line : lines) {
                copied.add(normalizeText(line));
            }
            return Collections.unmodifiableList(copied);
        }
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text;
    }

    private static String normalizeKind(String kind, String fallback) {
        String resolved = kind == null ? "" : kind.trim().toLowerCase(java.util.Locale.ROOT);
        return resolved.isEmpty() ? fallback : resolved;
    }
}
