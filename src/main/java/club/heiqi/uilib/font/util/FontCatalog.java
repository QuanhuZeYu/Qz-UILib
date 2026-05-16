package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 字体目录快照。
 */
public class FontCatalog {

    private volatile Snapshot snapshot = new Snapshot(Collections.<Font>emptyList(), 0);

    /**
     * 使用新字体列表替换当前目录。
     *
     * @param updatedFonts 新字体列表
     */
    public synchronized void replaceAll(List<Font> updatedFonts) {
        int nextVersion = snapshot.getVersion() + 1;
        if (updatedFonts == null || updatedFonts.isEmpty()) {
            snapshot = new Snapshot(Collections.<Font>emptyList(), nextVersion);
            return;
        }
        snapshot = new Snapshot(Collections.unmodifiableList(new ArrayList<Font>(updatedFonts)), nextVersion);
    }

    /**
     * 获取只读字体列表。
     *
     * @return 字体列表
     */
    public List<Font> getFonts() {
        return snapshot.getFonts();
    }

    /**
     * 按目录索引获取字体。
     *
     * @param index 字体索引
     * @return 字体，索引无效时返回 null
     */
    public Font getFont(int index) {
        return snapshot.getFont(index);
    }

    /**
     * 获取字体数量。
     *
     * @return 字体数量
     */
    public int size() {
        return snapshot.getFonts().size();
    }

    /**
     * 获取字体目录快照版本。
     *
     * @return 目录版本
     */
    public int getVersion() {
        return snapshot.getVersion();
    }

    /**
     * 判断是否为空。
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return snapshot.getFonts().isEmpty();
    }

    /**
     * 获取字体目录的不可变快照。
     *
     * @return 字体目录快照
     */
    public Snapshot snapshot() {
        return snapshot;
    }

    /**
     * 字体目录不可变快照。
     */
    public static final class Snapshot {

        private final List<Font> fonts;
        private final int version;

        private Snapshot(List<Font> fonts, int version) {
            this.fonts = fonts;
            this.version = version;
        }

        /**
         * 获取快照内字体列表。
         *
         * @return 字体列表
         */
        public List<Font> getFonts() {
            return fonts;
        }

        /**
         * 按目录索引获取快照内字体。
         *
         * @param index 字体索引
         * @return 字体，索引无效时返回 null
         */
        public Font getFont(int index) {
            if (index < 0 || index >= fonts.size()) {
                return null;
            }
            return fonts.get(index);
        }

        /**
         * 获取快照版本。
         *
         * @return 快照版本
         */
        public int getVersion() {
            return version;
        }
    }
}
