package club.heiqi.uilib.ui.scene.text;
import java.util.Objects;
/** 合成效果的唯一状态。装饰是否开启也仅由此处声明。 */
public final class TextEffects {
 public static final TextEffects NORMAL = new TextEffects(false,false,false,false,false,0,0);
 public final boolean bold,italic,underline,strike,obfuscated;
 public final float boldAdvance,italicShear;
 public TextEffects(boolean bold,boolean italic,boolean underline,boolean strike,boolean obfuscated,float boldAdvance,float italicShear) {
  if(!Float.isFinite(boldAdvance)||boldAdvance<0||!Float.isFinite(italicShear))throw new IllegalArgumentException("无效合成效果");
  this.bold=bold;this.italic=italic;this.underline=underline;this.strike=strike;this.obfuscated=obfuscated;
  this.boldAdvance=bold?boldAdvance:0;this.italicShear=italic?italicShear:0;
 }
 @Override public boolean equals(Object o){if(!(o instanceof TextEffects))return false;TextEffects e=(TextEffects)o;return bold==e.bold&&italic==e.italic&&underline==e.underline&&strike==e.strike&&obfuscated==e.obfuscated&&boldAdvance==e.boldAdvance&&italicShear==e.italicShear;}
 @Override public int hashCode(){return Objects.hash(bold,italic,underline,strike,obfuscated,boldAdvance,italicShear);}
}
