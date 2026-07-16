package club.heiqi.uilib.ui.hud.api;

import java.util.Objects;

/** 不可变 HUD 富文本片段；稳定 id 用于行内 keyed 协调。 */
public final class HudSpan {
    private final String id;
    private final String text;
    private final HudTone tone;

    /** 创建语义色调文本片段，text 可空并按空文本呈现。 */
    public HudSpan(String id, String text, HudTone tone) {
        this.id = requireId(id);
        this.text = text;
        this.tone = Objects.requireNonNull(tone, "tone");
    }

    private static String requireId(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("id must not be blank");
        return value;
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public HudTone getTone() { return tone; }
}
