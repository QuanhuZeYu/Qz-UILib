package club.heiqi.uilib.font.util;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.font.latex.layout.MathGlyphRef;

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
        publish(prepareSnapshot(updatedFonts));
    }

    /**
     * 构造但不发布下一份目录快照。
     *
     * @param updatedFonts 新字体列表
     * @return 可在 generation commit 时发布的快照
     */
    public synchronized Snapshot prepareSnapshot(List<Font> updatedFonts) {
        int nextVersion = snapshot.getVersion() + 1;
        if (updatedFonts == null || updatedFonts.isEmpty()) {
            return new Snapshot(Collections.<Font>emptyList(), nextVersion);
        }
        return new Snapshot(Collections.unmodifiableList(new ArrayList<Font>(updatedFonts)), nextVersion);
    }

    /**
     * 生产数学候选入口：字体顺序与数学 face/数据同一快照发布。
     * 旧 prepareSnapshot/replaceAll 保持 legacy，无数学能力。
     */
    public synchronized Snapshot prepareSnapshotWithMath(List<Font> updatedFonts, BundledMathFont mathFont) {
        if (mathFont == null) { throw new IllegalArgumentException("mathFont must not be null"); }
        Snapshot legacy = prepareSnapshot(updatedFonts);
        return new Snapshot(legacy.getFonts(), legacy.getVersion(), mathFont);
    }

    /** 当前注册的数学能力；手动 legacy 目录返回 null。 */
    public BundledMathFont getMathFontSupport() { return snapshot.getMathFontSupport(); }

    /** 绘制侧仍必须使用 generation owner 的任务/计划屏障。 */
    public Font getMathPhysicalFont(MathGlyphRef glyph, int sizePx) {
        return snapshot.getMathPhysicalFont(glyph, sizePx);
    }

    /**
     * 原子发布预先构造的下一份目录快照。
     *
     * @param preparedSnapshot 已准备的快照
     */
    public synchronized void publish(Snapshot preparedSnapshot) {
        validate(preparedSnapshot);
        snapshot = preparedSnapshot;
    }

    /** 仅供 generation owner 在已完成 {@link #validate(Snapshot)} 且仍持串行 commit 所有权时调用。 */
    synchronized void publishValidated(Snapshot preparedSnapshot) {
        snapshot = preparedSnapshot;
    }

    /**
     * 验证 candidate 仍是当前目录的直接下一版。
     *
     * @param preparedSnapshot candidate 快照
     */
    public synchronized void validate(Snapshot preparedSnapshot) {
        if (preparedSnapshot == null || preparedSnapshot.getVersion() != snapshot.getVersion() + 1) {
            throw new IllegalStateException("字体目录 candidate 已过期");
        }
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
        private final BundledMathFont mathFont;

        private Snapshot(List<Font> fonts, int version) {
            this(fonts, version, null);
        }

        private Snapshot(List<Font> fonts, int version, BundledMathFont mathFont) {
            this.fonts = fonts;
            this.version = version;
            this.mathFont = mathFont;
        }

        /** 平台对象留在 util 层，layout 仅通过 MathFontSupport 读取值。 */
        public BundledMathFont getMathFontSupport() { return mathFont; }

        /** 只解析本 candidate 注册的相同内容身份，不将未知/过期 face 偷换。 */
        public Font getMathPhysicalFont(MathGlyphRef glyph, int sizePx) {
            return mathFont == null ? null : mathFont.getPhysicalFont(glyph, sizePx);
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
