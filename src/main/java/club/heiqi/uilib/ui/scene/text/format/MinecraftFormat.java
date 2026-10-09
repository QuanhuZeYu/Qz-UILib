package club.heiqi.uilib.ui.scene.text.format;
import java.util.ArrayList;
import java.util.Objects;
import club.heiqi.uilib.ui.scene.text.AttributedText;
import club.heiqi.uilib.ui.scene.text.StyleSpan;
import club.heiqi.uilib.ui.scene.text.TextDecoration;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextStyle;
/** 显式格式入口，无MC依赖。普通文本不猜格式。 */
public final class MinecraftFormat {
 private MinecraftFormat(){}
 public interface Context { int paletteColor(int index); TextEffects effects(boolean bold,boolean italic,boolean underline,boolean strike,boolean obfuscated); TextDecoration decoration(); }
 public static AttributedText parse(String source,TextStyle base,Context context){return parse(TextInput.string(source),source,base,context);}
 public static AttributedText parse(TextInput original,String raw,TextStyle base,Context context){
  Objects.requireNonNull(context,"context");Objects.requireNonNull(base,"base");StringBuilder text=new StringBuilder();
  ArrayList<Integer> starts=new ArrayList<Integer>(),ends=new ArrayList<Integer>();ArrayList<StyleSpan> spans=new ArrayList<StyleSpan>();
  ArrayList<TextInput.SourceSegment> gaps=new ArrayList<TextInput.SourceSegment>();ArrayList<TextLayout.Diagnostic> diagnostics=new ArrayList<TextLayout.Diagnostic>();
  TextStyle reset=new TextStyle(base.font,base.logicalEm,base.color,base.candidates(),TextEffects.NORMAL,context.decoration()),state=reset;int spanStart=0;
  for(int i=0;i<original.text.length();) {
   int cp=original.text.codePointAt(i),next=i+Character.charCount(cp);
   if(cp==167&&next<original.text.length()){
    int end=next+Character.charCount(original.text.codePointAt(next));int code="0123456789abcdefklmnor".indexOf(Character.toLowerCase(original.text.charAt(next)));
    if(text.length()>spanStart){spans.add(new StyleSpan(spanStart,text.length(),state));spanStart=text.length();}
    gaps.add(new TextInput.SourceSegment(text.length(),text.length(),i,end,true));TextEffects e=state.effects;
    if(code<16){int index=code<0?15:code;state=reset.withColor((base.color&0xff000000)|(context.paletteColor(index)&0xffffff));
     if(code<0)diagnostics.add(new TextLayout.Diagnostic(TextLayout.Diagnostic.Kind.UNSUPPORTED,original.sourceStart(i),original.sourceEnd(end-1),"未知§格式消费后恢复白色并清效果"));
    }else if(code==21)state=reset;
    else state=state.withEffects(context.effects(e.bold||code==17,e.italic||code==20,e.underline||code==19,e.strike||code==18,e.obfuscated||code==16)).withFontStyle(club.heiqi.uilib.ui.scene.text.FontStyle.minecraft(e.bold||code==17,e.italic||code==20));
    i=end;continue;
   }
   text.appendCodePoint(cp);for(int j=i;j<next;j++){starts.add(i);ends.add(next);}i=next;
  }
  if(text.length()>spanStart)spans.add(new StyleSpan(spanStart,text.length(),state));
  int[] a=new int[starts.size()],b=new int[ends.size()];for(int i=0;i<a.length;i++){a[i]=starts.get(i);b[i]=ends.get(i);}
  return new AttributedText(TextInput.fromMapped(original,text.toString(),a,b,gaps),raw,reset,spans,state,diagnostics);
 }
}
