package club.heiqi.uilib.ui.hud.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HUD 布局持久化快照（包内实现，不是公共 API）：{@code schemaVersion + hudId -> 相对记录}。
 *
 * <p>文本格式由本类唯一冻结（宿主只搬运文本）：</p>
 * <pre>
 * qz_uilib_hud_layout
 * schemaVersion=1
 * qzuilib:chat3&lt;TAB&gt;BOTTOM_LEFT&lt;TAB&gt;0.123456789&lt;TAB&gt;0.5&lt;TAB&gt;110
 * qz_miner:chain-status&lt;TAB&gt;-&lt;TAB&gt;0&lt;TAB&gt;0&lt;TAB&gt;90
 * </pre>
 *
 * <p>容错：字段数不对、锚点名未知、数字不可解析、百分比非有限 → 只跳过该行；百分比越界钳到 [0,1]、
 * 缩放越界钳到 50–200；重复 hudId 首条生效。魔数缺失、schemaVersion 缺失或不等于
 * {@link #SCHEMA_VERSION} → {@link #parse(String)} 返回 null（整体不可用，调用方降级为默认布局）。</p>
 */
final class HudLayoutData {

    /** 当前 schemaVersion（宿主只需原样搬运文本，不需要理解该数字）。 */
    static final int SCHEMA_VERSION = 1;

    private static final String MAGIC = "qz_uilib_hud_layout";
    private static final String VERSION_PREFIX = "schemaVersion=";
    private static final String NO_ANCHOR = "-";
    private static final String SEPARATOR = "\t";
    private static final int FIELD_COUNT = 5;

    private final int schemaVersion;
    private final Map<String, HudLayoutPreference> entries;

    private HudLayoutData(int schemaVersion, Map<String, HudLayoutPreference> entries) {
        this.schemaVersion = schemaVersion;
        this.entries = Collections.unmodifiableMap(
                new LinkedHashMap<String, HudLayoutPreference>(entries));
    }

    /** @return 空快照（当前 schemaVersion、无记录） */
    static HudLayoutData empty() {
        return new Builder().build();
    }

    static Builder builder() {
        return new Builder();
    }

    int getSchemaVersion() { return schemaVersion; }

    /** @return hudId -> 相对记录（插入序、不可变） */
    Map<String, HudLayoutPreference> getEntries() { return entries; }

    /** @return 记录数 */
    int size() { return entries.size(); }

    /** 序列化为持久化文本（确定性：魔数 + 版本行 + 逐记录行）。 */
    String toText() {
        StringBuilder text = new StringBuilder();
        text.append(MAGIC).append('\n');
        text.append(VERSION_PREFIX).append(schemaVersion).append('\n');
        for (HudLayoutPreference preference : entries.values()) {
            text.append(preference.getHudId()).append(SEPARATOR);
            text.append(preference.hasPlacement() ? preference.getAnchor().name() : NO_ANCHOR)
                    .append(SEPARATOR);
            text.append(Double.toString(preference.getFractionX())).append(SEPARATOR);
            text.append(Double.toString(preference.getFractionY())).append(SEPARATOR);
            text.append(Integer.toString(preference.getScalePercent())).append('\n');
        }
        return text.toString();
    }

    /**
     * 解析持久化文本。
     *
     * @param text 持久化原文（可为 null）
     * @return 快照；魔数缺失、schemaVersion 缺失或不等于 {@link #SCHEMA_VERSION} 时返回 null
     */
    static HudLayoutData parse(String text) {
        if (text == null) {
            return null;
        }
        String[] lines = text.split("\n", -1);
        int index = 0;
        while (index < lines.length && lines[index].trim().isEmpty()) {
            index++;
        }
        if (index >= lines.length || !MAGIC.equals(lines[index].trim())) {
            return null;
        }
        index++;
        while (index < lines.length && lines[index].trim().isEmpty()) {
            index++;
        }
        if (index >= lines.length) {
            return null;
        }
        String header = lines[index].trim();
        if (!header.startsWith(VERSION_PREFIX)) {
            return null;
        }
        int version;
        try {
            version = Integer.parseInt(header.substring(VERSION_PREFIX.length()).trim());
        } catch (NumberFormatException malformed) {
            return null;
        }
        if (version != SCHEMA_VERSION) {
            return null;
        }
        index++;
        Builder builder = builder().schemaVersion(version);
        for (; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] fields = line.split(SEPARATOR, -1);
            if (fields.length != FIELD_COUNT) {
                continue;
            }
            try {
                String hudId = fields[0].trim();
                String anchorName = fields[1].trim();
                double fractionX = Double.parseDouble(fields[2].trim());
                double fractionY = Double.parseDouble(fields[3].trim());
                int scalePercent = Integer.parseInt(fields[4].trim());
                if (NO_ANCHOR.equals(anchorName)) {
                    builder.put(HudLayoutPreference.scaleOnly(hudId, scalePercent));
                } else {
                    builder.put(HudLayoutPreference.of(hudId, HudAnchor.valueOf(anchorName),
                            fractionX, fractionY, scalePercent));
                }
            } catch (RuntimeException malformedRecord) {
                // 单条记录损坏：跳过该行，保留同一快照里其它有效记录（安全降级）
            }
        }
        return builder.build();
    }

    /** 快照 builder（首条同 hudId 生效）。 */
    static final class Builder {

        private int schemaVersion = SCHEMA_VERSION;
        private final Map<String, HudLayoutPreference> entries = new LinkedHashMap<String, HudLayoutPreference>();

        Builder schemaVersion(int value) {
            this.schemaVersion = value;
            return this;
        }

        Builder put(HudLayoutPreference preference) {
            if (preference != null && !entries.containsKey(preference.getHudId())) {
                entries.put(preference.getHudId(), preference);
            }
            return this;
        }

        HudLayoutData build() {
            return new HudLayoutData(schemaVersion, entries);
        }
    }
}
