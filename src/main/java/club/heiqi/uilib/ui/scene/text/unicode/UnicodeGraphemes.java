package club.heiqi.uilib.ui.scene.text.unicode;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.util.ULocale;
import java.io.IOException;
import java.util.Objects;
import java.util.TreeSet;

/** 纯 Unicode 字素规则：ICU76/Unicode16 与 Emoji17 RGI 补充；不依赖字体或宿主。 */
public final class UnicodeGraphemes {
    private UnicodeGraphemes() {}
    /** 返回稳定且原子读取的不可变快照；调用方在所属域协调替换并显式通知字体服务刷新。 */
    public interface Source { Profile snapshot(); }
    public static final class Profile {
        public final EmojiData emoji;
        public Profile(EmojiData emoji) {this.emoji=Objects.requireNonNull(emoji,"emoji");}
        public int[] boundaries(String text) {
            BreakIterator iterator=BreakIterator.getCharacterInstance(ULocale.ROOT);iterator.setText(text);
            TreeSet<Integer> boundaries=new TreeSet<>();
            for(int b=iterator.first();b!=BreakIterator.DONE;b=iterator.next())boundaries.add(b);
            for(int a=0;a<text.length();) {
                int end=emoji.sequenceEnd(text,a,text.length());
                if(end>a)boundaries.subSet(a,false,end,false).clear();
                Integer next=boundaries.higher(a);if(next==null)break;a=next;
            }
            int[] result=new int[boundaries.size()];int i=0;for(int b:boundaries)result[i++]=b;return result;
        }
    }
    private static final class Bundled {
        static final Profile PROFILE=load();
        private static Profile load() {
            try{return new Profile(EmojiData.bundled(UnicodeGraphemes.class.getClassLoader()));}
            catch(IOException error){throw new IllegalStateException("Unicode资源无法载入",error);}
        }
    }
    public static Profile bundled() {return Bundled.PROFILE;}
    public static Source defaults() {return UnicodeGraphemes::bundled;}
    public static Source fixed(Profile value) {Objects.requireNonNull(value,"profile");return ()->value;}
}
