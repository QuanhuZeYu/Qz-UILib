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
    private final BitSet normalPressureGlyphs = new BitSet(GlyphRuntimeTables.CODEPOINT_COUNT);
    private final BitSet boldPressureGlyphs = new BitSet(GlyphRuntimeTables.CODEPOINT_COUNT);
    private int residentPageCount;
    private int retainedPageCount;
    private boolean normalPressure;
    private boolean boldPressure;

    boolean hasRetiredRetries() { return !retiredPageRetries.isEmpty(); }

    /** generation reset 时清空驻留计数与压力位图（保留退役重试队列）。 */
    void resetResidency() {
        residentPageCount = 0;
        normalPressure = false;
        boldPressure = false;
        normalPressureGlyphs.clear();
        boldPressureGlyphs.clear();
    }

    BitSet pressureGlyphs(FontType fontType) {
        return fontType == FontType.BOLD ? boldPressureGlyphs : normalPressureGlyphs;
    }

    void setPressure(FontType fontType, boolean pressure) {
        if (fontType == FontType.BOLD) {
            boldPressure = pressure;
        } else {
            normalPressure = pressure;
        }
    }

    boolean isPressure(FontType fontType) {
        return fontType == FontType.BOLD ? boldPressure : normalPressure;
    }

    boolean bothPressures() { return normalPressure && boldPressure; }
    boolean normalPressure() { return normalPressure; }
    boolean boldPressure() { return boldPressure; }

    void clearPressureGlyphs() {
        normalPressureGlyphs.clear();
        boldPressureGlyphs.clear();
    }

    int pressureGlyphCount() { return normalPressureGlyphs.cardinality() + boldPressureGlyphs.cardinality(); }

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
