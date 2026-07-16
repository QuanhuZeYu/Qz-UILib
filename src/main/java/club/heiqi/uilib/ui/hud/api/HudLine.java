package club.heiqi.uilib.ui.hud.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 不可变 HUD 文本行；稳定 id 用于 keyed 协调。 */
public final class HudLine {
    private final String id;
    private final List<HudSpan> spans;
    private final Float progress;

    private HudLine(String id, List<HudSpan> spans, Float progress) {
        this.id = requireText(id, "id");
        Objects.requireNonNull(spans, "spans");
        ArrayList<HudSpan> copy = new ArrayList<HudSpan>(spans);
        Set<String> ids = new HashSet<String>();
        for (HudSpan span : copy) {
            Objects.requireNonNull(span, "span");
            if (!ids.add(span.getId())) throw new IllegalArgumentException("duplicate span id: " + span.getId());
        }
        this.spans = Collections.unmodifiableList(copy);
        this.progress = progress;
        if (progress != null && (progress < 0F || progress > 1F || Float.isNaN(progress))) {
            throw new IllegalArgumentException("progress must be between 0 and 1");
        }
    }

    /** 创建普通文本行。 */
    public static HudLine text(String id, String text) { return text(id, text, HudTone.NORMAL); }
    /** 创建带语义色调的文本行。 */
    public static HudLine text(String id, String text, HudTone tone) {
        return new HudLine(id, Collections.singletonList(new HudSpan(id, Objects.requireNonNull(text, "text"), tone)), null);
    }
    /** 创建带归一化进度的文本行。 */
    public static HudLine progress(String id, String text, HudTone tone, float progress) {
        return new HudLine(id, Collections.singletonList(new HudSpan(id, Objects.requireNonNull(text, "text"), tone)), progress);
    }

    /** 创建富文本行。 */
    public static HudLine rich(String id, List<HudSpan> spans) { return new HudLine(id, spans, null); }
    /** 创建富文本行。 */
    public static HudLine rich(String id, HudSpan... spans) { return rich(id, Arrays.asList(spans)); }
    /** 创建带归一化进度的富文本行。 */
    public static HudLine richProgress(String id, List<HudSpan> spans, float progress) {
        return new HudLine(id, spans, progress);
    }
    /** 创建带归一化进度的富文本行。 */
    public static HudLine richProgress(String id, float progress, HudSpan... spans) {
        return richProgress(id, Arrays.asList(spans), progress);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    public String getId() { return id; }
    public String getText() {
        StringBuilder value = new StringBuilder();
        for (HudSpan span : spans) if (span.getText() != null) value.append(span.getText());
        return value.toString();
    }
    public HudTone getTone() { return spans.isEmpty() ? HudTone.NORMAL : spans.get(0).getTone(); }
    public List<HudSpan> getSpans() { return spans; }
    public boolean hasProgress() { return progress != null; }
    public float getProgress() { return progress == null ? 0F : progress; }
}
