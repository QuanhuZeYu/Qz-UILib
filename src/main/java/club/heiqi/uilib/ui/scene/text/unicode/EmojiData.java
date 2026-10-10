package club.heiqi.uilib.ui.scene.text.unicode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import com.ibm.icu.text.UnicodeSet;

/**
 * 版本化不可变 Emoji property/RGI 数据；ICU76 的 Unicode16 属性不能代表 Emoji17。
 * 字体升级一并重载此数据。普通 Unicode/Bidi/语言断行仍执行 ICU76.1，
 * 新 RGI trie 补充整体边界，不宣称整个 UAX29 已升级到 Unicode17。
 */
public final class EmojiData {
    public final String version;
    private final UnicodeSet emoji, presentation;
    private final Trie rgi;
    private EmojiData(String version, UnicodeSet emoji, UnicodeSet presentation, Trie rgi) {
        this.version = version; this.emoji = emoji.freeze(); this.presentation = presentation.freeze(); this.rgi = rgi;
    }
    public boolean isEmoji(int cp) { return emoji.contains(cp); }
    public boolean isDefaultEmoji(int cp) { return presentation.contains(cp); }
    public boolean isRgiSequence(String text, int start, int end) { return start < end && sequenceEnd(text, start, end) == end; }
    /** 读取官方 emoji-data 与 sequences/zwj-sequences，或已校验的 fully-qualified/component 集合。 */
    public static EmojiData read(String version, Reader properties, Reader... sequences) throws IOException {
        UnicodeSet emoji = new UnicodeSet(), presentation = new UnicodeSet(); Trie rgi = new Trie();
        BufferedReader props = new BufferedReader(properties);
        for (String line; (line = props.readLine()) != null;) {
            String body = line.split("#", 2)[0].trim(); if (body.isEmpty()) continue;
            String[] columns = body.split(";"); if (columns.length < 2) throw new IOException("无效Emoji property记录: " + line);
            UnicodeSet target = "Emoji".equals(columns[1].trim()) ? emoji : "Emoji_Presentation".equals(columns[1].trim()) ? presentation : null;
            if (target == null) continue;
            try {
                String[] range = columns[0].trim().split("\\.\\.");
                int start = Integer.parseInt(range[0], 16), end = range.length == 1 ? start : Integer.parseInt(range[1], 16); target.add(start, end);
            } catch (IllegalArgumentException error) { throw new IOException("无效Emoji property范围", error); }
        }
        for (Reader sequence : sequences) parseSequences(sequence, rgi);
        if (emoji.isEmpty() || presentation.isEmpty() || rgi.next.isEmpty()) throw new IOException("Emoji属性/序列集为空");
        return new EmojiData(version, emoji, presentation, rgi);
    }
    private static void parseSequences(Reader reader, Trie rgi) throws IOException {
        BufferedReader seq = new BufferedReader(reader);
        for (String line; (line = seq.readLine()) != null;) {
            String body = line.split("#", 2)[0].trim(); if (body.isEmpty()) continue;
            String[] columns = body.split(";"); if (columns.length < 2) throw new IOException("无效RGI记录: " + line);
            String kind = columns[1].trim();
            if (!kind.equals("fully-qualified") && !kind.equals("component") && !kind.equals("Basic_Emoji")
                    && !kind.equals("Emoji_Keycap_Sequence") && !kind.startsWith("RGI_Emoji_")) continue;
            try {
                String field = columns[0].trim();
                if (field.contains("..")) {
                    String[] range = field.split("\\.\\."); int a = Integer.parseInt(range[0], 16), b = Integer.parseInt(range[1], 16);
                    if (a > b || !Character.isValidCodePoint(a) || !Character.isValidCodePoint(b)) throw new IllegalArgumentException("无效range");
                    for (int cp = a; cp <= b; cp++) insert(rgi, new int[] {cp});
                } else {
                    String[] tokens = field.split("\\s+"); int[] cps = new int[tokens.length];
                    for (int i = 0; i < cps.length; i++) {
                        cps[i] = Integer.parseInt(tokens[i], 16);
                        if (!Character.isValidCodePoint(cps[i])) throw new IllegalArgumentException("无效码点");
                    }
                    insert(rgi, cps);
                }
            } catch (IllegalArgumentException error) { throw new IOException("无效RGI序列: " + line, error); }
        }
    }
    private static void insert(Trie rgi, int[] cps) {
        Trie current = rgi;
        for (int cp : cps) {
            Trie next = current.next.get(cp); if (next == null) { next = new Trie(); current.next.put(cp, next); } current = next;
        }
        current.terminal = true;
    }
    /** 正式数据缺失/损坏就是资源初始化错误；不猜测新版本或静默使用 ICU 旧数据。 */
    public static EmojiData bundled(ClassLoader loader) throws IOException {
        String base = "assets/qz_uilib/unicode/emoji17/";
        try (InputStream p = required(loader, base + "emoji-data.txt");
             InputStream s = required(loader, base + "emoji-sequences.txt");
             InputStream z = required(loader, base + "emoji-zwj-sequences.txt");
             InputStream v = required(loader, base + "emoji-variation-sequences.txt")) {
            // variation 数据列入同版本资源校验。VS15/VS16请求语义不依赖字体cmap或原生颜色声明。
            BufferedReader variation = new BufferedReader(new InputStreamReader(v, StandardCharsets.UTF_8));
            int count = 0;
            for (String line; (line = variation.readLine()) != null;) {
                String body = line.split("#", 2)[0].trim(); if (body.isEmpty()) continue;
                String[] columns = body.split(";");
                if (columns.length < 2 || (!columns[1].trim().equals("text style") && !columns[1].trim().equals("emoji style")))
                    throw new IOException("无效Emoji variation记录");
                count++;
            }
            if (count == 0) throw new IOException("Emoji variation集合为空");
            return read("17.0", new InputStreamReader(p, StandardCharsets.UTF_8),
                new InputStreamReader(s, StandardCharsets.UTF_8), new InputStreamReader(z, StandardCharsets.UTF_8));
        }
    }
    private static InputStream required(ClassLoader loader, String path) throws IOException {
        InputStream in = loader.getResourceAsStream(path); if (in == null) throw new IOException("缺少Emoji资源: " + path); return in;
    }
    public int sequenceEnd(String text, int start, int end) {
        Trie current = rgi; int found = start;
        for (int i = start; i < end;) {
            int cp = text.codePointAt(i); current = current.next.get(cp); if (current == null) break;
            i += Character.charCount(cp); if (current.terminal) found = i;
        }
        return found;
    }
    private static final class Trie { final Map<Integer, Trie> next = new HashMap<Integer, Trie>(); boolean terminal; }
}
