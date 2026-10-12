package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 有序字号/上下标意图；真实字号与基线由当前字体度量解析，绝对字号不丢失外层基线。 */
public final class TextScript {
    public static final TextScript NONE = new TextScript(Collections.<Step>emptyList());
    public static final class Step {
        public final Float em;
        public final Boolean superscript;
        private Step(Float em, Boolean superscript) { this.em = em; this.superscript = superscript; }
        @Override public boolean equals(Object value) {
            if (!(value instanceof Step)) return false;
            Step s = (Step) value;
            return Objects.equals(em, s.em) && Objects.equals(superscript, s.superscript);
        }
        @Override public int hashCode() { return Objects.hash(em, superscript); }
    }
    private final List<Step> steps;
    private TextScript(List<Step> steps) { this.steps = Collections.unmodifiableList(steps); }
    public List<Step> steps() { return steps; }
    public TextScript appendEm(float em) {
        if (!Float.isFinite(em) || em <= 0 || em > 32767) throw new IllegalArgumentException("无效字号");
        return append(new Step(em, null));
    }
    public TextScript appendScript(boolean superscript) { return append(new Step(null, superscript)); }
    private TextScript append(Step step) {
        List<Step> copy = new ArrayList<>(steps);
        copy.add(step);
        return new TextScript(copy);
    }
    public TextScript then(TextScript other) {
        if (other.steps.isEmpty()) return this;
        List<Step> copy = new ArrayList<>(steps);
        copy.addAll(other.steps);
        return new TextScript(copy);
    }
    public boolean hasScript() {
        for (Step step : steps) if (step.superscript != null) return true;
        return false;
    }
    @Override public boolean equals(Object value) { return value instanceof TextScript && steps.equals(((TextScript) value).steps); }
    @Override public int hashCode() { return steps.hashCode(); }
}
