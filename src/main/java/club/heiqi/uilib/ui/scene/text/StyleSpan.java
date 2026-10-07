package club.heiqi.uilib.ui.scene.text;
import java.util.Objects;
/** logical UTF16半开范围，完整样式而非可变delta。 */
public final class StyleSpan {
 public final int logicalStart,logicalEnd;public final TextStyle style;
 public StyleSpan(int start,int end,TextStyle style){if(start<0||end<=start)throw new IllegalArgumentException("无效属性范围");logicalStart=start;logicalEnd=end;this.style=Objects.requireNonNull(style,"style");}
}
