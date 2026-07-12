package club.heiqi.uilib.ui.hud.api;

import java.util.Objects;

/** 不可变 HUD 文本行；稳定 id 用于 keyed 协调。 */
public final class HudLine {
    private final String id;
    private final String text;
    private final HudTone tone;
    private final Float progress;

    private HudLine(String id, String text, HudTone tone, Float progress) {
        this.id = requireText(id, "id");
        this.text = Objects.requireNonNull(text, "text");
        this.tone = Objects.requireNonNull(tone, "tone");
        this.progress = progress;
        if (progress != null && (progress < 0F || progress > 1F || Float.isNaN(progress))) {
            throw new IllegalArgumentException("progress must be between 0 and 1");
        }
    }

    /** 创建普通文本行。 */
    public static HudLine text(String id, String text) { return new HudLine(id, text, HudTone.NORMAL, null); }
    /** 创建带语义色调的文本行。 */
    public static HudLine text(String id, String text, HudTone tone) { return new HudLine(id, text, tone, null); }
    /** 创建带归一化进度的文本行。 */
    public static HudLine progress(String id, String text, HudTone tone, float progress) {
        return new HudLine(id, text, tone, progress);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public HudTone getTone() { return tone; }
    public boolean hasProgress() { return progress != null; }
    public float getProgress() { return progress == null ? 0F : progress; }
}
