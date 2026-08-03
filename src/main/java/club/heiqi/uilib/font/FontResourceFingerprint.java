package club.heiqi.uilib.font;

import java.awt.Font;
import java.awt.geom.AffineTransform;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;

/** Candidate 实际消费的 immutable resource snapshot 内容身份。 */
final class FontResourceFingerprint {

    private final byte[] digest;

    private FontResourceFingerprint(byte[] digest) {
        this.digest = digest.clone();
    }

    static FontResourceFingerprint create(FontResourceSnapshot resources) {
        if (resources == null) {
            throw new IllegalArgumentException("resource snapshot 不得为 null");
        }
        MessageDigest digest = newDigest();
        updateString(digest, "qz-font-resource-v1");
        updateInt(digest, resources.getAssetFonts().size());
        for (FontResourceSnapshot.AssetFontResource asset : resources.getAssetFonts()) {
            updateString(digest, asset.getName());
            updateInt(digest, asset.getContentLength());
            asset.updateDigest(digest);
        }
        String[] defaultHints = resources.getDefaultOrderHints();
        updateInt(digest, defaultHints.length);
        for (String hint : defaultHints) {
            updateString(digest, hint);
        }
        Font[] installedFonts = resources.getInstalledFonts();
        updateInt(digest, installedFonts.length);
        for (Font font : installedFonts) {
            updateFontDescriptor(digest, font);
        }
        return new FontResourceFingerprint(digest.digest());
    }

    static FontResourceFingerprint unspecified() {
        MessageDigest digest = newDigest();
        updateString(digest, "qz-font-resource-unspecified");
        return new FontResourceFingerprint(digest.digest());
    }

    static FontResourceFingerprint synthetic(String identity) {
        MessageDigest digest = newDigest();
        updateString(digest, "qz-font-resource-synthetic");
        updateString(digest, identity);
        return new FontResourceFingerprint(digest.digest());
    }

    private static void updateFontDescriptor(MessageDigest digest, Font font) {
        updateString(digest, font.getName());
        updateString(digest, font.getFamily(Locale.ENGLISH));
        updateString(digest, font.getFontName(Locale.ENGLISH));
        updateString(digest, font.getPSName());
        updateInt(digest, font.getStyle());
        updateInt(digest, Float.floatToIntBits(font.getSize2D()));
        updateInt(digest, font.getNumGlyphs());
        updateInt(digest, font.getMissingGlyphCode());
        AffineTransform transform = font.getTransform();
        double[] matrix = new double[6];
        transform.getMatrix(matrix);
        for (double value : matrix) {
            updateLong(digest, Double.doubleToLongBits(value));
        }
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JRE 缺少 SHA-256", exception);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateLong(MessageDigest digest, long value) {
        updateInt(digest, (int) (value >>> 32));
        updateInt(digest, (int) value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FontResourceFingerprint
                && Arrays.equals(digest, ((FontResourceFingerprint) other).digest);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(digest);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            builder.append(String.format(Locale.ENGLISH, "%02x", Integer.valueOf(value & 0xFF)));
        }
        return builder.toString();
    }
}
