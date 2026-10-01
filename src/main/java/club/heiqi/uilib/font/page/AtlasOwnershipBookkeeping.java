package club.heiqi.uilib.font.page;

import club.heiqi.uilib.font.FontType;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * atlas 页所有权簿记：驻留/保留页计数、纹理所有权集合、退役重试队列与 atlas 压力位图。
 * 纯数据结构与判定原语；页分配/quarantine/回滚事务仍由 {@link GlyphPageManager} 编排。
 */
final class AtlasOwnershipBookkeeping {

    private final List<GlyphPage> retiredPageRetries = new ArrayList<GlyphPage>();
    private final Set<GlyphPage> retainedOwnerships = new HashSet<GlyphPage>();
    /** 每个字面（{@link FontType#ordinal()}）一份压力位图与压力标记。 */
    private final BitSet[] pressureGlyphs = new BitSet[FontType.values().length];
    private final boolean[] pressure = new boolean[FontType.values().length];
    private int residentPageCount;
    private int retainedPageCount;

    AtlasOwnershipBookkeeping() {
        for (int index = 0; index < pressureGlyphs.length; index++) {
            pressureGlyphs[index] = new BitSet(GlyphRuntimeTables.CODEPOINT_COUNT);
        }
    }

    boolean hasRetiredRetries() { return !retiredPageRetries.isEmpty(); }

    /** generation reset 时清空驻留计数与压力位图（保留退役重试队列）。 */
    void resetResidency() {
        residentPageCount = 0;
        for (int index = 0; index < pressure.length; index++) {
            pressure[index] = false;
            pressureGlyphs[index].clear();
        }
    }

    private int faceIndex(FontType fontType) {
        return fontType == null ? FontType.NORMAL.ordinal() : fontType.ordinal();
    }

    BitSet pressureGlyphs(FontType fontType) {
        return pressureGlyphs[faceIndex(fontType)];
    }

    void setPressure(FontType fontType, boolean value) {
        pressure[faceIndex(fontType)] = value;
    }

    boolean isPressure(FontType fontType) {
        return pressure[faceIndex(fontType)];
    }

    /** 至少一个字面处于压力态。 */
    boolean anyPressure() {
        for (boolean value : pressure) {
            if (value) {
                return true;
            }
        }
        return false;
    }

    /** 处于压力态的字面名（{@code +} 连接），全空返回 NONE。 */
    String pressureName() {
        StringBuilder builder = new StringBuilder();
        for (FontType fontType : FontType.values()) {
            if (pressure[fontType.ordinal()]) {
                if (builder.length() > 0) {
                    builder.append('+');
                }
                builder.append(fontType.name());
            }
        }
        return builder.length() == 0 ? "NONE" : builder.toString();
    }

    void clearPressureGlyphs() {
        for (BitSet glyphs : pressureGlyphs) {
            glyphs.clear();
        }
    }

    int pressureGlyphCount() {
        int count = 0;
        for (BitSet glyphs : pressureGlyphs) {
            count += glyphs.cardinality();
        }
        return count;
    }

    int ownedPageCount() { return residentPageCount + retainedPageCount; }
    int residentCount() { return residentPageCount; }
    int retainedCount() { return retainedPageCount; }
    void incrementResident() { residentPageCount++; }
    void decrementResident() {
        if (residentPageCount > 0) {
            residentPageCount--;
        }
    }

    Iterator<GlyphPage> retiredRetriesIterator() { return retiredPageRetries.iterator(); }
    boolean containsRetired(GlyphPage page) { return retiredPageRetries.contains(page); }
    void addRetired(GlyphPage page) { retiredPageRetries.add(page); }

    /** 记录纹理所有权；首次记录返回 true。 */
    boolean addRetained(GlyphPage page) {
        if (retainedOwnerships.add(page)) {
            retainedPageCount++;
            return true;
        }
        return false;
    }

    /** 移除纹理所有权；确实移除返回 true。 */
    boolean removeRetained(GlyphPage page) {
        if (retainedOwnerships.remove(page)) {
            retainedPageCount--;
            return true;
        }
        return false;
    }

    /** 遍历保留所有权集合（diagnostics 只读快照）。 */
    Set<GlyphPage> retainedOwnershipsSnapshot() { return new HashSet<GlyphPage>(retainedOwnerships); }
}
