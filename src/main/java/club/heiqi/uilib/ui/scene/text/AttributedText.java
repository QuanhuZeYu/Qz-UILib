package club.heiqi.uilib.ui.scene.text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
/** 无宿主依赖的属性文本；普通文本不推断§。 */
public final class AttributedText {
 public final TextInput input;public final String originalText;public final TextStyle baseStyle,trailingStyle;
 private final List<StyleSpan> spans;private final List<TextLayout.Diagnostic> diagnostics;
 public AttributedText(TextInput input,String originalText,TextStyle base,List<StyleSpan> spans){this(input,originalText,base,spans,spans.isEmpty()?base:spans.get(spans.size()-1).style,Collections.<TextLayout.Diagnostic>emptyList());}
 public AttributedText(TextInput input,String originalText,TextStyle base,List<StyleSpan> spans,TextStyle trailing,List<TextLayout.Diagnostic> diagnostics){
  this.input=Objects.requireNonNull(input,"input");this.originalText=originalText;baseStyle=Objects.requireNonNull(base,"base");trailingStyle=Objects.requireNonNull(trailing,"trailing");
  ArrayList<StyleSpan> copy=new ArrayList<StyleSpan>(spans);int end=0;
  for(StyleSpan s:copy){if(s.logicalStart!=end||s.logicalEnd>input.text.length()||s.logicalEnd<input.text.length()&&Character.isLowSurrogate(input.text.charAt(s.logicalEnd)))throw new IllegalArgumentException("属性范围必须完整覆盖且不切代理对");end=s.logicalEnd;}
  if(end!=input.text.length())throw new IllegalArgumentException("属性范围没有覆盖文本");this.spans=Collections.unmodifiableList(copy);this.diagnostics=Collections.unmodifiableList(new ArrayList<TextLayout.Diagnostic>(diagnostics));
 }
 public static AttributedText plain(TextInput input,TextStyle style){return new AttributedText(input,null,style,input.text.isEmpty()?Collections.<StyleSpan>emptyList():Collections.singletonList(new StyleSpan(0,input.text.length(),style)));}
 public List<StyleSpan> spans(){return spans;}public List<TextLayout.Diagnostic> diagnostics(){return diagnostics;}
 public TextStyle styleAt(int offset){if(offset<0||offset>input.text.length())throw new IndexOutOfBoundsException();if(offset==input.text.length())return trailingStyle;int low=0,high=spans.size();while(low<high){int mid=(low+high)>>>1;if(spans.get(mid).logicalEnd<=offset)low=mid+1;else high=mid;}return spans.get(low).style;}
}
