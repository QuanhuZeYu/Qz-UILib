package club.heiqi.uilib.ui.scene.text;
import java.util.Objects;
/** baseline相对几何；flags统一在TextEffects，不在此处建立第二套状态。 */
public final class TextDecoration {
 public static final TextDecoration NONE=new TextDecoration(0,0,0,0,0);
 public final float underlineTop,underlineThickness,underlineStartOffset,strikeTop,strikeThickness;
 public TextDecoration(float underlineTop,float underlineThickness,float underlineStartOffset,float strikeTop,float strikeThickness){
  if(!Float.isFinite(underlineTop)||!Float.isFinite(underlineThickness)||!Float.isFinite(underlineStartOffset)||!Float.isFinite(strikeTop)||!Float.isFinite(strikeThickness)||underlineThickness<0||strikeThickness<0)throw new IllegalArgumentException("无效装饰几何");
  this.underlineTop=underlineTop;this.underlineThickness=underlineThickness;this.underlineStartOffset=underlineStartOffset;this.strikeTop=strikeTop;this.strikeThickness=strikeThickness;
 }
 @Override public boolean equals(Object o){if(!(o instanceof TextDecoration))return false;TextDecoration d=(TextDecoration)o;return underlineTop==d.underlineTop&&underlineThickness==d.underlineThickness&&underlineStartOffset==d.underlineStartOffset&&strikeTop==d.strikeTop&&strikeThickness==d.strikeThickness;}
 @Override public int hashCode(){return Objects.hash(underlineTop,underlineThickness,underlineStartOffset,strikeTop,strikeThickness);}
}
