package club.heiqi.uilib.ui.scene.text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 来源映射与属性意图的稳定声明；宿主只在转换边界生成它，呈现按当前主题派生属性文本。 */
public final class StyledText {
    public final TextInput input;
    public final String originalText;
    private final List<TextStyleOverrideSpan> spans;
    private final TextStyleOverride trailing;
    private final List<TextLayout.Diagnostic> diagnostics;
    public StyledText(TextInput input,String originalText,List<TextStyleOverrideSpan> spans,
            TextStyleOverride trailing,List<TextLayout.Diagnostic> diagnostics) {
        this.input=Objects.requireNonNull(input,"input");this.originalText=originalText;
        this.trailing=Objects.requireNonNull(trailing,"trailing");
        List<TextStyleOverrideSpan> copy=new ArrayList<>(spans);int end=0;
        for(TextStyleOverrideSpan s:copy) {
            if(s.start!=end||s.end>input.text.length()||s.end<input.text.length()&&Character.isLowSurrogate(input.text.charAt(s.end)))
                throw new IllegalArgumentException("属性意图必须完整覆盖且不切代理对");
            end=s.end;
        }
        if(end!=input.text.length())throw new IllegalArgumentException("属性意图未覆盖文本");
        this.spans=Collections.unmodifiableList(copy);this.diagnostics=Collections.unmodifiableList(new ArrayList<>(diagnostics));
    }
    public List<TextStyleOverrideSpan> spans() {return spans;}
    public List<TextLayout.Diagnostic> diagnostics() {return diagnostics;}
    public AttributedText resolve(TextStyle base) {
        List<StyleSpan> resolved=new ArrayList<>();
        for(TextStyleOverrideSpan s:spans)resolved.add(new StyleSpan(s.start,s.end,s.style.resolve(base)));
        return new AttributedText(input,originalText,base,resolved,trailing.resolve(base),diagnostics);
    }
}
